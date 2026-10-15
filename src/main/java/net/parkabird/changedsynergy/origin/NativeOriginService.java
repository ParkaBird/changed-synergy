package net.parkabird.changedsynergy.origin;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.NativeOriginChoosePacket;
import net.parkabird.changedsynergy.network.NativeOriginStatePacket;
import net.parkabird.changedsynergy.origin.NativeOriginData.Identity;

/** Server owns selection, citizenship, safe placement and recovery. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class NativeOriginService {
    private NativeOriginService() {}
    private static final Map<UUID, Selection> SELECTING = new HashMap<>();
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static Job active;
    private static final java.util.Set<UUID> MISSING_FORMS = new java.util.HashSet<>();
    private static net.parkabird.changedsynergy.network.NativeOriginPolicyPacket lastPolicy;

    private static net.parkabird.changedsynergy.network.NativeOriginPolicyPacket policy(net.minecraft.server.MinecraftServer server) {
        var natives = server.getPlayerList().getPlayers().stream().filter(NativeOriginService::isNative)
                .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet());
        var instinct = server.getPlayerList().getPlayers().stream().filter(NativeOriginPolicy::allowsInstinct)
                .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new net.parkabird.changedsynergy.network.NativeOriginPolicyPacket(
                server.overworld().getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS), natives,instinct);
    }
    private static final class Selection {
        final UUID nonce = UUID.randomUUID();
        final Vec3 anchor;
        final ResourceLocation dimension;
        final boolean canCancel;
        final java.util.List<NativeOriginCatalog.Entry> catalog;
        ResourceLocation failed;
        ResourceLocation randomRequest, randomResult;
        boolean busy;
        MobEffectInstance selectionInvisibility;
        int retries;
        Selection(ServerPlayer player, boolean canCancel) {
            anchor = player.position(); dimension = player.level().dimension().location();
            this.canCancel = canCancel;
            catalog = NativeOriginCatalog.entries(player.level().registryAccess());
        }
    }
    private record Job(UUID player, NativeOriginCatalog.Entry entry, OriginSpawnSearch search) {}

    public static Identity identity(ServerPlayer player) {
        return NativeOriginData.get(player.server).players.get(player.getUUID());
    }
    public static boolean isNative(Player player) {
        if (!(player instanceof ServerPlayer server)) return false;
        Identity identity = identity(server);
        return identity != null && identity.state.equals("native");
    }
    public static boolean pending(Player player) { return SELECTING.containsKey(player.getUUID()); }
    public static boolean sameFaction(ChangedEntity creature, ServerPlayer player) {
        Identity identity = identity(player);
        return identity != null && identity.state.equals("native")
                && FactionReputation.groupId(creature).equals(account(identity));
    }
    public static String account(Identity identity) {
        return identity.faction.equals("light") ? "light:" + identity.region : identity.faction;
    }
    public static boolean protectedMember(ChangedEntity creature, ServerPlayer player) {
        return sameFaction(creature, player) && !FactionReputation.isHostile(creature, player)
                && !LatexSocialMemory.isProvoked(creature, player)
                && !LatexSocialMemory.hasRelationshipBetrayal(creature, player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), policy(player.server));
        NativeOriginData data = NativeOriginData.get(player.server);
        Identity identity = data.players.get(player.getUUID());
        // Repair legacy birth-affiliation guesses using the original species,
        // never a later permanent or temporary body.
        if (identity != null && identity.state.equals("native") && !identity.faction.equals("dark")) {
            ResourceLocation birthId = ResourceLocation.tryParse(identity.form);
            var birthForm = birthId == null ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(birthId);
            var typeId = birthForm == null ? null
                    : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(birthForm.getEntityType());
            if (typeId != null && typeId.getNamespace().equals("changed")
                    && java.util.Set.of("phage_latex_wolf_male", "phage_latex_wolf_female").contains(typeId.getPath())) {
                identity.faction = "dark";
                identity.region = "";
                data.setDirty();
            } else if (birthForm != null && identity.faction.equals("light") && identity.region.equals("plains")) {
                // Older archives assigned every untagged species to Plains.
                // Correct birth allegiance without changing its current body or home.
                var birth = NativeOriginCatalog.describe(player.level().registryAccess(), birthForm);
                if (birth.faction().equals("light") && !birth.region().equals(identity.region)) {
                    identity.region = birth.region();
                    data.setDirty();
                }
            }
        }
        if (identity == null) {
            identity = new Identity();
            boolean existing = Files.exists(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getStringUUID() + ".dat"))
                    || player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0
                    || ProcessTransfur.isPlayerTransfurred(player);
            if (existing || !enabled(player) || !TrueTransfurCompat.supportsNativeOrigin()) identity.state = "human";
            data.players.put(player.getUUID(), identity); data.setDirty();
        }
        if (identity.state.equals("pending")) {
            if (enabled(player)) open(player);
            else finishHuman(player);
        } else if (enabled(player) && identity.state.equals("native") && ProcessTransfur.getPlayerTransfurVariant(player) == null) restore(player);
    }

    public static boolean enabled(ServerPlayer player) {
        return player.server.overworld().getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS);
    }

    public static void open(ServerPlayer player) {
        open(player, false);
    }

    private static void open(ServerPlayer player, boolean canCancel) {
        if (!enabled(player) || !player.isAlive() || pending(player)) return;
        TrueTransfurCompat.ensurePlayerControl(player);
        Selection selection = new Selection(player, canCancel);
        SELECTING.put(player.getUUID(), selection);
        hideSelectingPlayer(player, selection);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48))) {
            if (mob.getTarget() == player) mob.setTarget(null);
        }
        player.closeContainer();
        send(player, 0, TrueTransfurCompat.supportsNativeOrigin() ? "intro" : "missing_backend");
    }

    /** Operator-only migration: existing bodies and relationships are retained. */
    public static boolean reopen(ServerPlayer player) {
        if (!enabled(player) || pending(player) || !player.isAlive() || TakeoverService.active(player)
                || ProcessTransfur.getPlayerTransfurVariantSafe(player)
                        .map(instance -> instance.isTemporaryFromSuit()).orElse(false)) return false;
        NativeOriginData data = NativeOriginData.get(player.server);
        data.players.computeIfAbsent(player.getUUID(), ignored -> {
            Identity identity = new Identity(); identity.state = "human"; data.setDirty(); return identity;
        });
        // An optional re-selection must not overwrite the committed identity until confirmation.
        open(player, true); return true;
    }

    public static void choose(ServerPlayer player, NativeOriginChoosePacket packet) {
        Selection selection = SELECTING.get(player.getUUID());
        if (selection == null || !selection.nonce.equals(packet.nonce()) || !player.isAlive()) return;
        if (packet.cancel()) { if (selection.canCancel) abortSelection(player); return; }
        if (selection.busy) return;
        if (!enabled(player)) { abortSelection(player); return; }
        ResourceLocation form = packet.form();
        int randomCategory = NativeOriginCatalog.randomCategory(form);
        if (randomCategory >= 0) {
            if (packet.fallback()) {
                if (!form.equals(selection.randomRequest) || selection.randomResult == null
                        || !selection.randomResult.equals(selection.failed)) { send(player, 2, "invalid"); return; }
                form = selection.randomResult;
            } else {
                // Draw only from the records sent when this archive was opened. New permissions
                // cannot silently add unseen forms; restrictions still take effect immediately.
                var catalog = TrueTransfurCompat.supportsNativeOrigin() ? selection.catalog.stream()
                        .filter(entry -> NativeOriginCatalog.allowed(entry.variant())).toList()
                        : java.util.List.<NativeOriginCatalog.Entry>of();
                final int category = randomCategory;
                if (category != 0 && catalog.stream().noneMatch(entry -> NativeOriginCatalog.categoryOf(entry) == category)) {
                    send(player, 2, "no_matches"); return;
                }
                selection.randomRequest = form;
                form = selection.randomResult = NativeOriginCatalog.drawRandom(catalog, category, player.getRandom());
            }
        } else { selection.randomRequest = null; selection.randomResult = null; }
        if (form == null) { finishHuman(player, true); return; }
        if (!TrueTransfurCompat.supportsNativeOrigin()) { send(player, 2, "missing_backend"); return; }
        final ResourceLocation chosen = form;
        NativeOriginCatalog.Entry entry = selection.catalog.stream()
                .filter(record -> record.form().equals(chosen) && NativeOriginCatalog.allowed(record.variant()))
                .findFirst().orElse(null);
        if (entry == null || packet.fallback() && !form.equals(selection.failed)) {
            send(player, 2, "invalid"); return;
        }
        if (++selection.retries > 8) { send(player, 2, "search_limit"); return; }
        selection.busy = true;
        QUEUE.add(new Job(player.getUUID(), entry, new OriginSpawnSearch(player.server.overworld(), entry, packet.fallback(),
                player.level() == player.server.overworld() ? player.blockPosition() : player.server.overworld().getSharedSpawnPos())));
        send(player, 1, "searching");
    }

    private static void finishHuman(ServerPlayer player) {
        finishHuman(player, false);
    }

    private static void finishHuman(ServerPlayer player, boolean resetBody) {
        if (resetBody) TrueTransfurCompat.ensurePlayerControl(player);
        NativeOriginData data = NativeOriginData.get(player.server);
        Identity previousIdentity = data.players.get(player.getUUID());
        Identity identity = new Identity();
        identity.state = "human";
        data.players.put(player.getUUID(), identity); data.setDirty();
        if (resetBody) {
            // Commit human policy before removal so native/TrueTransfur reversal guards allow it.
            boolean changed = net.parkabird.changedsynergy.compat.SurvivalProtocolCompat.changeOrigin(player, "", () -> {
                ProcessTransfur.removePlayerTransfurVariant(player);
                return ProcessTransfur.getPlayerTransfurVariant(player) == null;
            });
            if (!changed) {
                if (previousIdentity == null) data.players.remove(player.getUUID());
                else data.players.put(player.getUUID(), previousIdentity);
                data.setDirty();
                send(player, 2, "form_failed");
                return;
            }
            player.refreshDimensions(); player.setAirSupply(player.getMaxAirSupply());
            player.refreshDisplayName();
            player.server.overworld().getDataStorage().save();
        }
        send(player, 3, "human_started"); clearSelection(player);
    }

    private static void abortSelection(ServerPlayer player) {
        Selection selection = SELECTING.get(player.getUUID());
        if (selection != null && selection.canCancel) {
            send(player, 3, "selection_cancelled"); clearSelection(player);
        } else finishHuman(player);
        cancelSearch(player.getUUID());
    }

    private static void send(ServerPlayer player, int phase, String message) {
        Selection selection = SELECTING.get(player.getUUID());
        var notice = Component.translatable("origin.changed_synergy." + message);
        if (phase == 2 && selection != null && selection.randomRequest != null && selection.failed != null) {
            var entry = selection.catalog.stream().filter(candidate -> candidate.form().equals(selection.failed)).findFirst().orElse(null);
            if (entry != null) notice.append("\n").append(Component.translatable("origin.changed_synergy.archive.random_result",
                    entry.variant().getEntityType().getDescription()));
        }
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NativeOriginStatePacket(phase, selection == null ? new UUID(0, 0) : selection.nonce,
                        TrueTransfurCompat.supportsNativeOrigin(), selection != null && selection.canCancel,
                        notice,
                        phase == 3 || selection == null ? java.util.List.of() : selection.catalog));
    }

    @SubscribeEvent public static void searchTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var policy = policy(event.getServer());
        if (!policy.equals(lastPolicy)) {
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), policy);
            event.getServer().getPlayerList().getPlayers().forEach(ServerPlayer::refreshDisplayName);
            lastPolicy = policy;
        }
        if (active == null) active = QUEUE.poll();
        if (active == null) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(active.player);
        if (player == null || !pending(player)) { active.search.cancel(); active = null; return; }
        if (!enabled(player)) { abortSelection(player); active = null; return; }
        // A server operator can change the catalog while a birthplace search is queued.
        if (!NativeOriginCatalog.allowed(active.entry.variant())) {
            active.search.cancel();
            Selection selection = SELECTING.get(player.getUUID());
            selection.busy = false;
            selection.failed = null;
            send(player, 2, "invalid");
            active = null;
            return;
        }
        try {
            active.search.tick();
            if (!active.search.finished) return;
            Selection selection = SELECTING.get(player.getUUID());
            selection.busy = false;
            if (active.search.result == null) {
                selection.failed = active.entry.form(); send(player, 2, "no_habitat");
            } else if (apply(player, active.entry.variant())) {
                Vec3 position = active.search.result;
                player.teleportTo(player.server.overworld(), position.x, position.y, position.z, player.getYRot(), 0);
                player.setAirSupply(player.getMaxAirSupply()); player.fallDistance = 0;
                Identity identity = new Identity();
                identity.state = "native"; identity.form = active.entry.form().toString();
                identity.current = identity.form; identity.faction = active.entry.faction(); identity.region = active.entry.region();
                identity.home = BlockPos.containing(position);
                NativeOriginData.get(player.server).players.put(player.getUUID(), identity);
                NativeOriginData.get(player.server).setDirty();
                FactionReputation.initializeOriginStanding(player, account(identity));
                net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(player,
                        net.parkabird.changedsynergy.advancement.SynergyAdvancements.NATIVE_ORIGIN);
                send(player, 3, "native_started"); clearSelection(player);
                // Save once at confirmation, not on every tick; a restart cannot reopen a committed identity.
                player.server.overworld().getDataStorage().save();
            } else send(player, 2, "form_failed");
        } catch (RuntimeException exception) {
            ChangedSynergyMod.LOGGER.error("Native origin placement failed for {}", player.getUUID(), exception);
            Selection selection = SELECTING.get(player.getUUID());
            if (selection != null) selection.busy = false;
            send(player, 2, "form_failed");
        }
        active = null;
    }

    private static boolean apply(ServerPlayer player, TransfurVariant<?> form) {
        if (form == null) return false;
        return net.parkabird.changedsynergy.compat.SurvivalProtocolCompat.changeOrigin(player, form.getFormId().toString(), () -> {
        // Temporary initialization skips ordinary conversion rewards and the transformer event.
        var instance = ProcessTransfur.setPlayerTransfurVariant(player, form, null, 1.0F, true,
                variant -> variant.willSurviveTransfur = true);
        if (instance == null || instance.getParent() != form) return false;
        instance.willSurviveTransfur = true;
        instance.transfurProgression = instance.transfurProgressionO = 1.0F;
        instance.setTemporaryForSuit(false);
        player.refreshDimensions();
        Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), SyncTransfurPacket.Builder.of(player));
        return true;
        });
    }

    public static void restore(ServerPlayer player) {
        Identity identity = identity(player);
        if (identity == null || !identity.state.equals("native") || !player.isAlive()) return;
        ResourceLocation id = ResourceLocation.tryParse(identity.current.isEmpty() ? identity.form : identity.current);
        TransfurVariant<?> form = id == null ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        if (form == null) {
            id = ResourceLocation.tryParse(identity.form);
            form = id == null ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        }
        if (form != null) { apply(player, form); MISSING_FORMS.remove(player.getUUID()); }
        else if (MISSING_FORMS.add(player.getUUID())) player.sendSystemMessage(Component.translatable("origin.changed_synergy.form_missing"));
    }

    private static void remember(ServerPlayer player) {
        if (!isNative(player) || TakeoverService.active(player)) return;
        var instance = ProcessTransfur.getPlayerTransfurVariant(player);
        if (instance == null || instance.isTemporaryFromSuit()) return;
        Identity identity = identity(player);
        String current = instance.getParent().getFormId().toString();
        if (!identity.current.equals(current)) { identity.current = current; NativeOriginData.get(player.server).setDirty(); }
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Selection selection = SELECTING.get(player.getUUID());
        if (selection != null) {
            if (!player.isAlive()) { abortSelection(player); return; }
            if (!enabled(player)) { abortSelection(player); return; }
            hideSelectingPlayer(player, selection);
            player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0; player.setAirSupply(player.getMaxAirSupply());
            if (!player.level().dimension().location().equals(selection.dimension)) {
                abortSelection(player); return;
            }
            if (player.position().distanceToSqr(selection.anchor) > 0.01)
                player.connection.teleport(selection.anchor.x, selection.anchor.y, selection.anchor.z, player.getYRot(), player.getXRot());
            return;
        }
        if (!isNative(player) || !player.isAlive() || !enabled(player)) return;
        if (player.tickCount % 20 == 0 && ProcessTransfur.getPlayerTransfurVariant(player) == null) restore(player);
        if (player.tickCount % 100 == 0) remember(player);
        if (TakeoverService.active(player)) TrueTransfurCompat.ensurePlayerControl(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) remember(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isNative(player) && enabled(player)) {
            if (ProcessTransfur.getPlayerTransfurVariant(player) == null) restore(player);
            if (player.getRespawnPosition() == null && player.level() == player.server.overworld()) {
                Identity identity = identity(player);
                var form = ProcessTransfur.getPlayerTransfurVariant(player);
                if (form != null && player.server.overworld().hasChunkAt(identity.home)) {
                    Vec3 home = Vec3.atBottomCenterOf(identity.home);
                    boolean water = form.getParent().breatheMode == TransfurVariant.BreatheMode.WATER
                            || identity.faction.equals("aquatic") && form.getParent().breatheMode.canBreatheWater();
                    if (OriginSpawnSearch.safe(player.server.overworld(), form.getParent(), home, water, player))
                        player.teleportTo(player.server.overworld(), home.x, home.y, home.z, player.getYRot(), 0);
                }
            }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            remember(player);
            clearSelection(player);
        }
        MISSING_FORMS.remove(event.getEntity().getUUID());
        cancelSearch(event.getEntity().getUUID());
    }
    private static void cancelSearch(UUID player) {
        QUEUE.removeIf(job -> {
            if (!job.player.equals(player)) return false;
            job.search.cancel(); return true;
        });
        if (active != null && active.player.equals(player)) { active.search.cancel(); active = null; }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (active != null) active.search.cancel();
        QUEUE.forEach(job -> job.search.cancel());
        SELECTING.clear(); QUEUE.clear(); MISSING_FORMS.clear(); active = null; lastPolicy = null;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer && pending(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hurt(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && pending(player)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void target(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player && pending(player)) event.setNewTarget(null);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interact(PlayerInteractEvent event) {
        if (event.isCancelable() && event.getEntity() instanceof ServerPlayer && pending(event.getEntity())) event.setCanceled(true);
    }

    private static void hideSelectingPlayer(ServerPlayer player, Selection selection) {
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        if (current == null || current == selection.selectionInvisibility && current.getDuration() <= 20) {
            // Short expiry also prevents a server crash from saving permanent selection invisibility.
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
            selection.selectionInvisibility = player.getEffect(MobEffects.INVISIBILITY);
        }
    }

    private static void clearSelection(ServerPlayer player) {
        Selection selection = SELECTING.remove(player.getUUID());
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        if (selection != null && current != null && current == selection.selectionInvisibility
                && current.getDuration() <= 40 && current.getAmplifier() == 0
                && !current.isVisible() && !current.showIcon()) player.removeEffect(MobEffects.INVISIBILITY);
    }
}
