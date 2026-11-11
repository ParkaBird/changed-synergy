package net.parkabird.changedsynergy.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.UniversalDist;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.network.*;

/** Player preference and validation; the native ability remains the sole owner of the actual grab. */
@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class PlayerHugService {
    private static final String MODE = "ChangedSynergyPlayerFriendlyHug";
    private static final Map<UUID, PlayerHugStatePacket> CLIENT = new HashMap<>();
    private static final java.util.Set<UUID> CLIENT_PENDING = new java.util.HashSet<>();
    private static final Map<UUID, GrabEntityAbilityInstance> SERVER = new HashMap<>();
    private PlayerHugService() {}

    public static void receive(PlayerHugStatePacket state) {
        CLIENT.entrySet().removeIf(entry -> !entry.getKey().equals(state.carrier()) && entry.getValue().owner().equals(state.owner()));
        CLIENT_PENDING.retainAll(CLIENT.keySet());
        var previous = CLIENT.put(state.carrier(), state);
        CLIENT_PENDING.add(state.carrier());
        var local = UniversalDist.getLocalPlayer();
        if (local != null && previous != null && previous.targetOwner() != null
                && !previous.targetOwner().equals(state.targetOwner())) {
            var player = local.level().getPlayerByUUID(previous.targetOwner());
            if (player instanceof LivingEntityDataExtension ext && ext.getGrabbedBy() != null
                    && ext.getGrabbedBy().getUUID().equals(state.carrier())) ext.setGrabbedBy(null);
        }
        reconcile(state);
    }
    public static void updateClient() { CLIENT.values().forEach(PlayerHugService::reconcile); }
    private static void reconcile(PlayerHugStatePacket state) {
        if (!CLIENT_PENDING.contains(state.carrier())) return;
        var local = UniversalDist.getLocalPlayer();
        if (local == null || !(local.level().getEntity(state.carrierId()) instanceof LivingEntity carrier)
                || !carrier.getUUID().equals(state.carrier())) return;
        if (carrier instanceof ChangedEntity body && !unsupported(body)) {
            // Only an authoritative state for an eligible player can mirror their existing ability.
            BondedSuitService.ensureGrabAbility(body);
        }
        var grab = AbstractAbility.getAbilityInstance(carrier, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (grab == null) return;
        ChangedAddonCompat.configureFriendlyGrab(grab, state.friendly(), state.friendly());
        ((PlayerHugAccess)grab).changedSynergy$setUprightCarry(state.uprightCarry());
        if (state.target() != null && local.level().getEntity(state.targetId()) instanceof LivingEntity held
                && held.getUUID().equals(state.target())) {
            if (grab.grabbedEntity != null && grab.grabbedEntity != held) grab.releaseEntity(false);
            if (grab.grabbedEntity == null && !grab.grabEntity(held)) return;
            // The native ARMS packet may have arrived before the friendly-state packet.
            began(grab);
            if (active(grab)) linkEscapePlayer(grab);
            CLIENT_PENDING.remove(state.carrier());
        } else if (state.target() == null) {
            if (active(grab)) grab.releaseEntity(false);
            CLIENT_PENDING.remove(state.carrier());
        }
    }
    public static void clearClient() { CLIENT.clear(); CLIENT_PENDING.clear(); }
    public static PlayerHugStatePacket clientState(UUID carrier) { return CLIENT.get(carrier); }
    public static PlayerHugStatePacket clientState(GrabEntityAbilityInstance grab) {
        var entity = grab.entity.getEntity();
        var state = CLIENT.get(entity.getUUID());
        if (state == null && entity instanceof Player)
            state = CLIENT.values().stream().filter(value -> value.owner().equals(entity.getUUID())).findFirst().orElse(null);
        return state;
    }
    @Nullable public static Player escapePlayer(GrabEntityAbilityInstance grab) {
        if (grab.grabbedEntity instanceof Player player) return player;
        if (!active(grab) || grab.grabbedEntity == null) return null;
        if (!grab.entity.getLevel().isClientSide()) return owner(grab.grabbedEntity);
        var state = CLIENT.get(grab.entity.getEntity().getUUID());
        return state == null || state.targetOwner() == null ? null : grab.entity.getLevel().getPlayerByUUID(state.targetOwner());
    }
    public static String form(LivingEntity entity) {
        if (entity instanceof Player player) return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(value -> value.getParent().getFormId().toString()).orElse("human");
        return entity.getType().toString();
    }

    @Nullable public static ServerPlayer owner(LivingEntity carrier) {
        return carrier instanceof ServerPlayer player ? player : carrier instanceof ChangedEntity body ? TrueTransfurCompat.bodyOwner(body) : null;
    }
    @Nullable public static GrabEntityAbilityInstance ability(ServerPlayer player) {
        var nativeAbility = AbstractAbility.getAbilityInstance(player, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (nativeAbility == null || unsupported(nativeAbility.entity.getChangedEntity())) return nativeAbility;
        var physical = TrueTransfurCompat.interactionBody(player);
        if (physical instanceof ChangedEntity body && physical != player) {
            // Mirror an existing player capability, never add one to a form that lacks it.
            BondedSuitService.ensureGrabAbility(body);
            return body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        }
        return nativeAbility;
    }
    public static boolean unsupported(ChangedEntity body) {
        var id = body == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(body.getType());
        return id != null && id.getNamespace().equals("changed") && id.getPath().equals("latex_shark_feral");
    }
    public static boolean mode(GrabEntityAbilityInstance grab) {
        if (grab.entity.getLevel().isClientSide()) {
            var state = clientState(grab);
            return state != null && state.friendly();
        }
        var player = owner(grab.entity.getEntity());
        return player != null && ChangedSynergyConfig.COMMON.playerFriendlyHugs.get()
                && !unsupported(grab.entity.getChangedEntity()) && player.getPersistentData().getBoolean(MODE);
    }
    public static boolean active(GrabEntityAbilityInstance grab) {
        return grab instanceof PlayerHugAccess state && state.changedSynergy$isPlayerHug();
    }
    public static boolean held(LivingEntity entity) {
        return GrabEntityAbility.getGrabberSafe(entity).map(body -> body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get()))
                .map(PlayerHugService::active).orElse(false);
    }
    public static boolean validTarget(GrabEntityAbilityInstance grab, LivingEntity target) {
        var carrier = grab.entity.getEntity();
        if (target == carrier || !carrier.isAlive() || !target.isAlive() || carrier.isRemoved() || target.isRemoved()
                || carrier.level() != target.level() || carrier.isPassenger() || carrier.isVehicle() || carrier.isSleeping()
                || target.isPassenger() || target.isVehicle() || target.isSleeping() || target.isSpectator()
                || target instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed()
                || !(EagleOwlTraits.airborne(carrier)?EagleOwlTraits.reach(carrier):carrier.getBoundingBox().inflate(.65, .3, .65)).intersects(target.getBoundingBox()) || !carrier.hasLineOfSight(target)) return false;
        if (carrier.getPersistentData().getLong("ChangedSynergyFriendlyHugAfter") > carrier.level().getGameTime()
                || target.getPersistentData().getLong("ChangedSynergyFriendlyHugAfter") > target.level().getGameTime()) return false;
        var carrierOwner = owner(carrier); var targetOwner = owner(target);
        if (carrierOwner != null && carrierOwner.getPersistentData().getLong("ChangedSynergyFriendlyHugAfter") > carrier.level().getGameTime()) return false;
        if (carrierOwner != null && targetOwner == carrierOwner) return false;
        if (targetOwner != null && (TakeoverService.active(targetOwner) || TrueTransfurCompat.isRebirthing(targetOwner))) return false;
        if (InvoluntaryTransfurNegotiation.isReleaseParticipant(target)
                || target instanceof ChangedEntity body && TakeoverService.carrying(body)) return false;
        if (!(target instanceof Player || target instanceof ChangedEntity || target.getType().is(ChangedTags.EntityTypes.HUMANOIDS))) return false;
        if (target instanceof ChangedEntity creature && (CreatureSocialProfile.isPermanentlyExcluded(creature)
                || creature.getUnderlyingPlayer() != null)) return false;
        if ((target instanceof Player || owner(target) != null) && (!ChangedSynergyConfig.COMMON.playerFriendlyHugPlayers.get()
                || !Changed.config.server.isGrabEnabled.get())) return false;
        if (target instanceof Player player && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(value -> value.isTemporaryFromSuit()).orElse(false)) return false;
        if (GrabEntityAbility.getGrabberSafe(target).isPresent()) return false;
        var heldAbility = AbstractAbility.getAbilityInstance(target, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        return heldAbility == null || heldAbility.grabbedEntity == null;
    }
    public static void action(ServerPlayer player, int action, int targetId) {
        var grab = ability(player);
        if (grab == null || unsupported(grab.entity.getChangedEntity()) || !player.isAlive() || player.isSpectator()
                || owner(grab.entity.getEntity()) != player) return;
        if (action == PlayerHugActionPacket.SYNC) { sync(grab); return; }
        if (action == PlayerHugActionPacket.RELEASE) {
            if (active(grab)) release(grab);
            return;
        }
        if (TakeoverService.active(player) || InvoluntaryTransfurNegotiation.isReleaseParticipant(player)
                || TrueTransfurCompat.isRebirthing(player)) return;
        if (action == PlayerHugActionPacket.TOGGLE) {
            if (grab.grabbedEntity != null || GrabEntityAbility.getGrabberSafe(grab.entity.getEntity()).isPresent()) {
                player.displayClientMessage(Component.translatable("message.changed_synergy.player_hug.release_first"), true); return;
            }
            if (!ChangedSynergyConfig.COMMON.playerFriendlyHugs.get()) return;
            long now = player.serverLevel().getGameTime();
            if (player.getPersistentData().contains(MODE + "Toggle") && now - player.getPersistentData().getLong(MODE + "Toggle") < 5) return;
            player.getPersistentData().putLong(MODE + "Toggle", now);
            player.getPersistentData().putBoolean(MODE, !mode(grab));
            ChangedAddonCompat.configureFriendlyGrab(grab, mode(grab), mode(grab));
            sync(grab);
            player.displayClientMessage(Component.translatable(mode(grab) ? "message.changed_synergy.player_hug.enabled" : "message.changed_synergy.player_hug.disabled"), true);
        } else if (action == PlayerHugActionPacket.GRAB && mode(grab) && grab.grabbedEntity == null
                && GrabEntityAbility.getGrabberSafe(grab.entity.getEntity()).isEmpty()
                && player.serverLevel().getEntity(targetId) instanceof LivingEntity target && validTarget(grab, target)) {
            if (grab.grabEntity(target)) {
                Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(grab.entity::getEntity),
                        new GrabEntityPacket(grab.entity.getEntity(), target, GrabEntityPacket.GrabType.ARMS));
                ChangedSounds.broadcastSound(grab.entity.getEntity(), ChangedSounds.LATEX_GRAB_ENTITY, .7F, 1.05F);
            }
        }
    }
    public static void began(GrabEntityAbilityInstance grab) {
        if (grab.grabbedEntity == null || !mode(grab)) return;
        if (!active(grab)) {
            var state = grab.entity.getLevel().isClientSide() ? clientState(grab) : null;
            ((PlayerHugAccess)grab).changedSynergy$setUprightCarry(state == null
                    ? grab.entity.getEntity().getRandom().nextBoolean() : state.uprightCarry());
        }
        ((PlayerHugAccess)grab).changedSynergy$setPlayerHug(true);
        grab.suited = grab.grabbedHasControl = false;
        grab.suitTransition = grab.suitTransitionO = 0;
        ChangedAddonCompat.configureFriendlyGrab(grab, true, true);
        if (!grab.entity.getLevel().isClientSide()) {
            var player = owner(grab.entity.getEntity());
            if (player != null) SERVER.put(player.getUUID(), grab);
            sync(grab);
        }
        linkEscapePlayer(grab);
    }
    public static void linkEscapePlayer(GrabEntityAbilityInstance grab) {
        var player = escapePlayer(grab);
        if (player instanceof LivingEntityDataExtension ext) ext.setGrabbedBy(grab.entity.getEntity());
    }
    public static void unlinkEscapePlayer(GrabEntityAbilityInstance grab) {
        var player = escapePlayer(grab);
        if (player instanceof LivingEntityDataExtension ext && ext.getGrabbedBy() == grab.entity.getEntity()) ext.setGrabbedBy(null);
        // Manual releases and NPC escapes need the same protection as a player's QTE escape.
        if (!grab.entity.getLevel().isClientSide()) {
            long until = grab.entity.getLevel().getGameTime() + 40;
            grab.entity.getEntity().getPersistentData().putLong("ChangedSynergyFriendlyHugAfter", until);
            var holder = owner(grab.entity.getEntity());
            if (holder != null) holder.getPersistentData().putLong("ChangedSynergyFriendlyHugAfter", until);
            if (player != null) player.getPersistentData().putLong("ChangedSynergyFriendlyHugAfter", until);
            if (grab.grabbedEntity != null) grab.grabbedEntity.getPersistentData().putLong("ChangedSynergyFriendlyHugAfter", until);
        }
    }
    public static void ended(GrabEntityAbilityInstance grab) {
        // End this held-key activation: holding the release key must not start another hug.
        grab.getController().deactivateAbility();
        // TT bodies do not tick native ability cooldowns; use the same clock on both sides.
        grab.entity.getEntity().getPersistentData().putLong("ChangedSynergyFriendlyHugAfter", grab.entity.getLevel().getGameTime() + 40);
        ((PlayerHugAccess)grab).changedSynergy$setPlayerHug(false);
        ChangedAddonCompat.configureFriendlyGrab(grab, mode(grab), mode(grab));
        if (!grab.entity.getLevel().isClientSide()) {
            SERVER.values().removeIf(value -> value == grab);
            if (!(grab.entity.getEntity() instanceof Player))
                ChangedSounds.broadcastSound(grab.entity.getEntity(), ChangedSounds.LATEX_UNGRAB_ENTITY, .6F, 1);
            if (owner(grab.entity.getEntity()) != null) sync(grab);
        }
    }
    public static void release(GrabEntityAbilityInstance grab) {
        var target = grab.grabbedEntity;
        grab.releaseEntity(false);
        if (target != null && !grab.entity.getLevel().isClientSide())
            Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(grab.entity::getEntity),
                    new GrabEntityPacket(grab.entity.getEntity(), target, GrabEntityPacket.GrabType.RELEASE));
    }
    public static void sync(GrabEntityAbilityInstance grab) {
        var owner = owner(grab.entity.getEntity());
        if (owner == null) return;
        boolean friendly = active(grab) || mode(grab);
        ChangedAddonCompat.configureFriendlyGrab(grab, friendly, friendly);
        var packet = state(grab, owner);
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(grab.entity::getEntity), packet);
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), packet);
        if (escapePlayer(grab) instanceof ServerPlayer heldPlayer)
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> heldPlayer), packet);
    }
    private static PlayerHugStatePacket state(GrabEntityAbilityInstance grab, ServerPlayer owner) {
        var held = active(grab) ? grab.grabbedEntity : null;
        var heldOwner = held == null ? null : PlayerHugService.owner(held);
        return new PlayerHugStatePacket(grab.entity.getEntity().getUUID(), grab.entity.getEntity().getId(), owner.getUUID(),
                held == null ? null : held.getUUID(), held == null ? -1 : held.getId(), heldOwner == null ? null : heldOwner.getUUID(), mode(grab), ((PlayerHugAccess)grab).changedSynergy$isUprightCarry());
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof LivingEntity living)) return;
        var owner = owner(living); var grab = owner == null ? null : ability(owner);
        if (grab != null) ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                state(grab, owner));
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        event.getEntity().getPersistentData().putBoolean(MODE, event.getOriginal().getPersistentData().getBoolean(MODE));
    }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var grab = SERVER.get(player.getUUID());
            if (grab != null && active(grab)) release(grab);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var grab = SERVER.get(player.getUUID());
        if (grab != null && (!player.isAlive() || ability(player) != grab || grab.grabbedEntity == null
                || !grab.entity.getEntity().isAlive() || grab.entity.getEntity().isRemoved()
                || !ChangedSynergyConfig.COMMON.playerFriendlyHugs.get())) release(grab);
    }
    @SubscribeEvent public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity body) || body.getUnderlyingPlayer() != null) return;
        var grab = body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (grab != null && active(grab)) ((GrabAbilityTick)grab).changedSynergy$tickHoldIfNeeded();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var grab = ability(player);
            if (grab != null && active(grab)) event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var grab = ability(player);
            if (grab != null && active(grab)) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME); event.setCanceled(true);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attacked(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            var grab = AbstractAbility.getAbilityInstance(attacker, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            if (grab != null && active(grab)) { event.setCanceled(true); return; }
        }
        var carriedBy = GrabEntityAbility.getGrabberSafe(event.getEntity()).map(body -> body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get())).orElse(null);
        if (carriedBy != null && active(carriedBy)) release(carriedBy);
        var carrying = AbstractAbility.getAbilityInstance(event.getEntity(), ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (carrying != null && active(carrying)) release(carrying);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVER.clear(); }
}
