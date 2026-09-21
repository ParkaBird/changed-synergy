package net.parkabird.changedsynergy.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity;
import net.ltxprogrammer.changed.entity.robot.Exoskeleton;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.TakeoverActionPacket;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;

/** Server authority for temporary body-control sessions. Players are never killed or cloned. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class TakeoverService {
    private static final String SAVE = "SynergyTakeover";
    private static final String CLIENT = "SynergyTakeoverClient";
    private static final String RELEASE = "SynergyTakeoverReleasePermit";
    private static final String ORANGE_AT = "SynergyTakeoverOrangeAt";
    private static final String ORANGE_PENDING = "SynergyTakeoverOrangePending";
    private static final String INTENT = "SynergyTakeoverIntent";
    // The decision is made before Changed's absorption threshold is crossed.
    // Keep authorization for the whole realistic grab, not just two seconds.
    private static final long INTENT_TICKS = 600L;
    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> PRONE_UNTIL = new HashMap<>();
    private static final Map<UUID, PendingIntent> PENDING_INTENTS = new HashMap<>();
    private static final Set<UUID> STARTING_PLAYERS = new HashSet<>();
    private TakeoverService() {}

    private static final class Entry {
        final UUID id = UUID.randomUUID();
        final UUID playerId;
        @Nullable final UUID carrierId;
        final TakeoverSession session;
        final String originalForm;
        final String originDimension;
        final Vec3 origin;
        final CompoundTag exoskeleton;
        final String targetForm;
        final CompoundTag inheritedEyes;
        final String carrierName;
        boolean transfurApplied;
        boolean confinement;
        long lastSync = Long.MIN_VALUE;
        boolean recoveryPermit;
        long nextReleaseAttempt;
        int releaseAttempts;
        TakeoverSession.Phase announcedPhase;
        long nextAmbientLine;
        long nextBystanderLine;

        Entry(ServerPlayer player, @Nullable ChangedEntity carrier, TakeoverSession session,
                String originalForm, CompoundTag exoskeleton) {
            this.playerId = player.getUUID();
            this.carrierId = carrier == null ? null : carrier.getUUID();
            this.session = session;
            this.originalForm = originalForm;
            this.originDimension = player.level().dimension().location().toString();
            this.origin = player.position();
            this.exoskeleton = exoskeleton.copy();
            this.targetForm = carrier != null && carrier.getSelfVariant() != null
                    ? carrier.getSelfVariant().getFormId().toString() : "";
            this.inheritedEyes = carrier == null
                    ? new CompoundTag() : InheritedEyeAppearance.capture(carrier);
            this.carrierName = carrier == null
                    ? "" : Component.Serializer.toJson(carrier.getDisplayName());
            this.announcedPhase = session.getPhase();
            long now = player.level().getGameTime();
            this.nextAmbientLine = now + 240L + player.getRandom().nextInt(241);
            this.nextBystanderLine = now + 400L + player.getRandom().nextInt(401);
        }

        /** Recovery-only entry: never resumes a challenge or grants restart rewards. */
        Entry(ServerPlayer player, CompoundTag saved) {
            this.playerId = player.getUUID();
            this.carrierId = saved.hasUUID("Carrier") ? saved.getUUID("Carrier") : null;
            this.exoskeleton = saved.getCompound("Exoskeleton").copy();
            this.targetForm = saved.getString("TargetForm");
            this.inheritedEyes = saved.getCompound("InheritedEyes").copy();
            this.carrierName = saved.getString("CarrierName");
            this.transfurApplied = saved.getBoolean("TransfurApplied");
            this.originalForm = saved.getString("OriginalForm");
            this.originDimension = saved.getString("OriginDimension");
            Vec3 point = new Vec3(saved.getDouble("OriginX"), saved.getDouble("OriginY"), saved.getDouble("OriginZ"));
            this.origin = Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)
                    ? point : player.position();
            this.releaseAttempts = Math.max(0, saved.getInt("ReleaseAttempts"));
            long tick = now(player.server);
            this.session = saved.contains("Exoskeleton") ? TakeoverSession.exoskeleton(tick)
                    : TakeoverSession.ordinary(tick, 600, false, false);
            this.session.interrupt(tick);
            this.announcedPhase = session.getPhase();
        }
    }

    private record PendingIntent(UUID playerId, long until) {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static boolean active(Player player) { return player != null && ACTIVE.containsKey(player.getUUID()); }

    /** Administrative escape used by Changed's /untf and /untransfur aliases. */
    public static boolean forceReleaseByCommand(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null) return false;
        entry.session.emergencyRelease(now(player.server));
        tryRelease(entry, player, carrier(player.server, entry), true);
        return !ACTIVE.containsKey(player.getUUID());
    }

    /** A temporarily returned body may use a bed without reopening ordinary gameplay input. */
    public static boolean allowsBedUse(Player player) {
        Entry e = player == null ? null : ACTIVE.get(player.getUUID());
        return e != null && e.session.getKind() == TakeoverSession.Kind.ORDINARY
                && e.session.getPhase() == TakeoverSession.Phase.BORROWED;
    }

    /** Called only after the level has accepted and completed a real night skip. */
    public static void onCompletedBedSleep(ServerPlayer player) {
        Entry e = player == null ? null : ACTIVE.get(player.getUUID());
        if (e == null || e.session.getKind() != TakeoverSession.Kind.ORDINARY
                || e.session.getPhase() != TakeoverSession.Phase.BORROWED) return;
        ChangedEntity carrier = carrier(player.server, e);
        if (carrier != null && carrier.isAlive()) {
            if (e.confinement) player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.bond_kin_confinement_sleep",
                    carrier.getDisplayName()));
            else NpcDialogue.trigger(carrier, player, e.session.isStrictBorrow()
                    ? Cue.TAKEOVER_BED_SLEEP_REACTIVE
                    : Cue.TAKEOVER_BED_SLEEP_PROACTIVE);
        }
    }

    /** Safe only on the logical server thread; packet mixins check that precondition. */
    public static boolean blocksPlayerInput(Player player) {
        Entry e = player == null ? null : ACTIVE.get(player.getUUID());
        return e != null && !e.session.hasPlayerControl();
    }

    public static boolean carrying(ChangedEntity carrier) {
        if (carrier == null) return false;
        UUID id = carrier.getUUID();
        return ACTIVE.values().stream().anyMatch(e -> id.equals(e.carrierId));
    }

    /** True only for the hidden player currently inside this ordinary carrier. */
    public static boolean contains(ChangedEntity carrier, Player player) {
        if (carrier == null || player == null) return false;
        Entry entry = find(carrier);
        return entry != null && entry.playerId.equals(player.getUUID());
    }

    public static boolean allowsWork(ChangedEntity carrier) {
        Entry e = find(carrier);
        return e != null && e.session.getKind() == TakeoverSession.Kind.ORDINARY
                && e.session.getPhase() == TakeoverSession.Phase.CONTROLLED;
    }

    public static boolean allowsNativeRelease(ChangedEntity carrier) {
        Entry e = find(carrier);
        return e == null || e.recoveryPermit || carrier.getPersistentData().getBoolean(RELEASE);
    }

    @Nullable private static Entry find(ChangedEntity carrier) {
        UUID id = carrier.getUUID();
        return ACTIVE.values().stream().filter(e -> id.equals(e.carrierId)).findFirst().orElse(null);
    }

    /**
     * The negotiation reason is the canonical takeover cause. Keeping the enum in
     * the intent makes the routing table easy to extend without recreating a
     * second, subtly different set of hostility checks at absorption time.
     */
    private record Intent(InvoluntaryTransfurNegotiation.Reason reason) {
        boolean punitive() {
            return switch (reason) {
                case SELF_DEFENSE, FACTION_RETALIATION, CACHE_DEFENSE -> true;
                default -> false;
            };
        }
    }

    /**
     * Pins the event-stage decision to this exact source/player pair. Assimilation
     * completion may run after target and provocation flags have changed, so it must
     * not re-decide whether the already-authorized absorption is a takeover.
     */
    public static boolean authorizeOrdinary(ChangedEntity carrier, ServerPlayer player,
            InvoluntaryTransfurNegotiation.Reason reason) {
        if (!canAuthorizeOrdinary(carrier, player, reason)) {
            ChangedSynergyMod.LOGGER.debug("Takeover route rejected for {} -> {} (reason={})",
                    carrier.getUUID(), player.getGameProfile().getName(), reason);
            return false;
        }
        CompoundTag intent = new CompoundTag();
        intent.putUUID("Player", player.getUUID());
        intent.putLong("Until", carrier.level().getGameTime() + INTENT_TICKS);
        intent.putString("Reason", reason.name());
        carrier.getPersistentData().put(INTENT, intent);
        PENDING_INTENTS.put(carrier.getUUID(), new PendingIntent(player.getUUID(), intent.getLong("Until")));
        ChangedSynergyMod.LOGGER.debug("Authorized takeover for {} -> {} (reason={}, until={})",
                carrier.getUUID(), player.getGameProfile().getName(), reason,
                intent.getLong("Until"));
        return true;
    }

    @Nullable
    private static Intent intent(ChangedEntity carrier, ServerPlayer player) {
        CompoundTag data = carrier.getPersistentData().getCompound(INTENT);
        if (!data.hasUUID("Player") || !player.getUUID().equals(data.getUUID("Player"))
                || data.getLong("Until") < carrier.level().getGameTime()) {
            carrier.getPersistentData().remove(INTENT);
            PENDING_INTENTS.remove(carrier.getUUID());
            return null;
        }
        try {
            return new Intent(InvoluntaryTransfurNegotiation.Reason.valueOf(
                    data.getString("Reason")));
        } catch (IllegalArgumentException ignored) {
            carrier.getPersistentData().remove(INTENT);
            PENDING_INTENTS.remove(carrier.getUUID());
            return null;
        }
    }

    private static void clearIntent(ChangedEntity carrier) {
        carrier.getPersistentData().remove(INTENT);
        PENDING_INTENTS.remove(carrier.getUUID());
    }

    /** Revokes only this player pair, so an unrelated target cannot clear a live authorization. */
    public static void revokeOrdinaryAuthorization(ChangedEntity carrier, ServerPlayer player) {
        CompoundTag data = carrier.getPersistentData().getCompound(INTENT);
        if (data.hasUUID("Player") && player.getUUID().equals(data.getUUID("Player"))) {
            clearIntent(carrier);
        }
    }

    private static boolean baseOrdinaryEligibility(
            ChangedEntity carrier, ServerPlayer player) {
        if (!ChangedSynergyConfig.COMMON.takeoverEnabled.get() || !Changed.config.server.isGrabEnabled.get()
                || active(player) || carrying(carrier)
                || !player.isAlive() || !carrier.isAlive() || player.isCreative() || player.isSpectator()
                || carrier.getUnderlyingPlayer() != null || !CreatureSocialProfile.allowsSynergySystems(carrier)
                || CreatureSocialProfile.isPermanentlyExcluded(carrier)
                || CreatureSocialProfile.isJuvenile(carrier)
                || BondedSuitService.ability(carrier) == null) return false;
        return true;
    }

    /** Routes the already-classified negotiation cause into takeover. */
    public static boolean canAuthorizeOrdinary(ChangedEntity carrier, ServerPlayer player,
            InvoluntaryTransfurNegotiation.Reason reason) {
        if (reason == null || !baseOrdinaryEligibility(carrier, player)) return false;
        boolean punitive = switch (reason) {
            case SELF_DEFENSE, FACTION_RETALIATION, CACHE_DEFENSE ->
                    ChangedSynergyConfig.COMMON.takeoverPunitive.get();
            default -> false;
        };
        boolean competitive = ChangedSynergyConfig.COMMON.takeoverCompetitive.get()
                && (CreaturePersonality.has(carrier, CreaturePersonality.Trait.COMPETITIVE)
                    || CreaturePersonality.has(carrier, CreaturePersonality.Trait.SHOW_OFF));
        if (!punitive && !competitive) return false;
        return punitive || !(LatexSocialMemory.isBonded(carrier, player)
                || LatexSocialMemory.isPetOwner(carrier, player));
    }

    public static boolean canBeginOrdinary(ChangedEntity carrier, ServerPlayer player) {
        Intent authorized = intent(carrier, player);
        return authorized != null && baseOrdinaryEligibility(carrier, player);
    }

    /**
     * Final-absorption fallback for grab implementations which replace their
     * decision after Forge's decision event has already returned.
     */
    public static void ensureFinalAbsorptionAuthorization(
            ChangedEntity carrier, ServerPlayer player) {
        if (intent(carrier, player) != null) return;
        authorizeOrdinary(carrier, player,
                InvoluntaryTransfurNegotiation.reasonForFinalAbsorption(
                        carrier, player));
    }

    /** The living carrier whose body currently contains this player. */
    @Nullable
    public static ChangedEntity controllingCarrier(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return null;
        Entry entry = ACTIVE.get(player.getUUID());
        return entry == null ? null : carrier(serverPlayer.server, entry);
    }

    /** Identifies an absorption caused by the player's hostility or a faction response. */
    public static boolean isRetaliatoryCapture(ChangedEntity carrier, ServerPlayer player) {
        return !HumanBoundaryService.isChallenged(carrier, player)
                && (TakeoverConflictMemory.initiated(carrier, player)
                    || FactionHostilityGrace.wasAggressor(carrier, player)
                    || LatexSocialMemory.isProvoked(carrier, player)
                    || FactionReputation.isHostile(carrier, player)
                    || CreatureCacheGuardService.isDefendingAgainst(carrier, player)
                    || FactionPursuitService.isPursuer(carrier));
    }

    public static boolean beginOrdinary(ChangedEntity carrier, ServerPlayer player) {
        if (!canBeginOrdinary(carrier, player)) {
            ChangedSynergyMod.LOGGER.debug("Takeover completion had no valid authorization for {} -> {}",
                    carrier.getUUID(), player.getGameProfile().getName());
            return false;
        }
        return beginOrdinaryAuthorized(carrier, player, false);
    }

    /** A bonded creature can intervene after repeated witnessed kin kills. */
    public static boolean beginBondedConfinement(ChangedEntity carrier, ServerPlayer player) {
        if (!baseOrdinaryEligibility(carrier, player)
                || !LatexSocialMemory.isBonded(carrier, player)
                || LatexSocialMemory.isOrganic(carrier)
                || carrier.distanceToSqr(player) > 9.0D) return false;
        return beginOrdinaryAuthorized(carrier, player, true);
    }

    private static boolean beginOrdinaryAuthorized(ChangedEntity carrier,
            ServerPlayer player, boolean confinement) {
        GrabEntityAbilityInstance ability = BondedSuitService.ability(carrier);
        if (ability == null) {
            clearIntent(carrier);
            ChangedSynergyMod.LOGGER.warn("Takeover could not resolve the active grab ability for {} -> {}",
                    carrier.getUUID(), player.getGameProfile().getName());
            return false;
        }
        Intent authorized = intent(carrier, player);
        if (!confinement && authorized == null) return false;
        boolean punitive = !confinement && authorized.punitive();
        boolean hostile = !confinement && (punitive || !HumanBoundaryService.isChallenged(carrier, player)
                && LatexSocialMemory.isProvoked(carrier, player)
                || FactionReputation.isHostile(carrier, player)
                || CreatureCacheGuardService.isDefendingAgainst(carrier, player)
                || FactionPursuitService.isPursuer(carrier));
        String form = currentForm(player);
        // Absorption normally completes from a plain grab. Promote the existing
        // reference through Changed's own suit path so temporary-form bookkeeping
        // and onSuitOther hooks remain consistent.
        // End a prior creature's hold before Changed checks whether this grab can
        // be stolen. Keeping both references alive lets the old holder reclaim the
        // player as soon as the newer takeover ends.
        releasePreviousGrabbers(player, carrier);
        STARTING_PLAYERS.add(player.getUUID());
        boolean suited;
        try {
            suited = ability.suitEntity(player);
        } finally {
            STARTING_PLAYERS.remove(player.getUUID());
        }
        if (!suited) {
            clearIntent(carrier);
            ChangedSynergyMod.LOGGER.warn("Takeover could not promote the active absorption for {} -> {}",
                    carrier.getUUID(), player.getGameProfile().getName());
            return false;
        }
        clearIntent(carrier);
        long ticks = confinement ? 1200L : 20L * (punitive
                ? ChangedSynergyConfig.COMMON.takeoverPunitiveSeconds.get()
                : ChangedSynergyConfig.COMMON.takeoverSeconds.get());
        TakeoverSession session = TakeoverSession.ordinary(now(player.server), ticks,
                !confinement && !hostile && ChangedSynergyConfig.COMMON.takeoverOranges.get(),
                punitive || confinement,
                !confinement && ChangedSynergyConfig.COMMON.takeoverTransfurAfterSleep.get()
                        ? TakeoverSession.SleepOutcome.TRANSFUR
                        : TakeoverSession.SleepOutcome.RELEASE);
        Entry entry = new Entry(player, carrier, session, form, new CompoundTag());
        entry.confinement = confinement;
        ACTIVE.put(player.getUUID(), entry);
        InvoluntaryTransfurNegotiation.abandonForTakeover(player);
        ability.suited = true;
        ability.grabbedHasControl = false;
        ability.attackDown = false;
        ability.useDown = false;
        ability.grabStrength = 1.0F;
        if (carrier.getTarget() == player) carrier.setTarget(null);
        SocialAudienceGoal.end(carrier, player);
        PatAnimationService.stop(carrier);
        carrier.getNavigation().stop();
        FactionHostilityGrace.beginGlobal(player);
        LatexSocialEvents.calmTakeoverThreats(player, carrier);
        player.getFoodData().setFoodLevel(20);
        if (player instanceof LivingEntityDataExtension extension) extension.setGrabbedBy(carrier);
        player.getPersistentData().putBoolean(CLIENT, true);
        carrier.getPersistentData().putBoolean(CLIENT, true);
        InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(player);
        ProcessTransfur.setPlayerTransfurProgress(player, 0.0F);
        Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> carrier),
                new GrabEntityPacket(carrier, player, GrabType.SUIT));
        ChangedSounds.broadcastSound(carrier, ChangedSounds.LATEX_SUIT_ENTITY, 1.0F, 1.0F);
        if (carrier.getSelfVariant() != null)
            ChangedSounds.broadcastSound(player, carrier.getSelfVariant().sound, 1.0F, 1.0F);
        sync(entry, player, carrier, true);
        if (confinement) {
            String tone = switch (CreaturePersonality.dominantTrait(carrier)) {
                case PROTECTIVE, SENSITIVE -> "protective";
                case CALM, POLITE, CAUTIOUS -> "calm";
                default -> "firm";
            };
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.bond_kin_confinement_start." + tone,
                    carrier.getDisplayName()));
        } else {
            NpcDialogue.trigger(carrier, player,
                    punitive ? Cue.TAKEOVER_REACTIVE_START : Cue.TAKEOVER_PROACTIVE_START);
        }
        save(entry, player);
        ChangedSynergyMod.LOGGER.info("Started {} takeover for {} by {} (reason={})",
                confinement ? "bonded confinement" : punitive ? "punitive" : "competitive",
                player.getGameProfile().getName(), carrier.getUUID(),
                confinement ? "witnessed kin kills" : authorized.reason());
        return true;
    }

    public static boolean beginExoskeleton(ServerPlayer player, Exoskeleton source) {
        if (!ChangedSynergyConfig.COMMON.takeoverEnabled.get()
                || !ChangedSynergyConfig.COMMON.takeoverExoskeleton.get() || active(player)
                || !player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        CompoundTag state = ExoskeletonTakeoverAdapter.capture(source, player);
        if (state.isEmpty()) return false;
        TakeoverSession session = TakeoverSession.exoskeleton(now(player.server),
                20L * ChangedSynergyConfig.COMMON.takeoverExoskeletonSeconds.get());
        Entry entry = new Entry(player, null, session, currentForm(player), state);
        ACTIVE.put(player.getUUID(), entry);
        player.getFoodData().setFoodLevel(20);
        player.getPersistentData().putBoolean(CLIENT, true);
        sync(entry, player, null, true);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "takeover.changed_synergy.notice.exoskeleton"), false);
        save(entry, player);
        return true;
    }

    public static void handleAction(ServerPlayer player, UUID sessionId, int action, int key, int sequence) {
        Entry e = ACTIVE.get(player.getUUID());
        if (e == null || !e.id.equals(sessionId) || !player.isAlive()) return;
        long tick = now(player.server);
        TakeoverSession.Phase phaseBeforeAction = e.session.getPhase();
        TakeoverSession.Result result = null;
        TakeoverSession.Blocker requestBlocker = TakeoverSession.Blocker.NONE;
        ChangedEntity carrier = carrier(player.server, e);
        switch (action) {
            case TakeoverActionPacket.REQUEST_CONTROL -> {
                if (ChangedSynergyConfig.COMMON.takeoverBorrow.get()) {
                    requestBlocker = blocker(player, carrier);
                    result = e.session.requestBorrow(tick, requestBlocker);
                } else result = TakeoverSession.Result.DISABLED;
            }
            case TakeoverActionPacket.START_STRUGGLE -> {
                if (ChangedSynergyConfig.COMMON.takeoverEscape.get())
                    result = e.session.startStruggle(tick,
                            player.serverLevel().getSeed() ^ e.id.getMostSignificantBits(), 8);
                else result = TakeoverSession.Result.DISABLED;
            }
            case TakeoverActionPacket.CONFIRM_STRUGGLE -> {
                if (carrier != null && e.session.getKind() == TakeoverSession.Kind.ORDINARY
                        && e.session.hasStruggleOpportunity()) {
                    NpcDialogue.trigger(carrier, player, e.session.isStrictBorrow()
                            ? Cue.TAKEOVER_ESCAPE_CONFIRM_REACTIVE
                            : Cue.TAKEOVER_ESCAPE_CONFIRM_PROACTIVE);
                }
                return;
            }
            case TakeoverActionPacket.QTE_INPUT -> result = e.session.submitQte(tick, sequence, key);
            case TakeoverActionPacket.RETURN_CONTROL -> {
                if (e.session.returnControl(tick)) result = TakeoverSession.Result.ACCEPTED;
            }
            default -> { return; }
        }
        if (result != null && result != TakeoverSession.Result.ACCEPTED
                && action != TakeoverActionPacket.QTE_INPUT) {
            if (carrier != null && e.session.getKind() == TakeoverSession.Kind.ORDINARY)
                speakRefusal(e, carrier, player, result, requestBlocker);
            else player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "takeover.changed_synergy.notice.refused." + result.name().toLowerCase()), true);
        }
        // Apply breakout stun and its short re-grab protection in this same
        // packet task. Waiting for the next server tick left a window in which
        // the carrier could establish a malformed second grab.
        announceTransition(e, player, phaseBeforeAction);
        updateControl(e, player, carrier);
        sync(e, player, carrier, true);
        save(e, player);
    }

    private static TakeoverSession.Blocker blocker(ServerPlayer player, @Nullable ChangedEntity carrier) {
        // A suited player does not reliably inherit the carrier's on-ground flag.
        // Judge the body that is actually moving, or every ordinary request can be
        // misclassified as airborne while the player is safely standing still.
        Entity body = carrier == null ? player : carrier;
        if (body.isInLava()) return TakeoverSession.Blocker.UNSAFE_WATER;
        // Aquatic bodies are normally suspended rather than onGround while swimming.
        // Their native controller is safe to lend to the player in water.
        boolean aquaticSwimming = carrier != null
                && (carrier instanceof AbstractAquaticEntity
                        || HunterFaction.isAquatic(carrier))
                && (body.isInWaterOrBubble() || player.isInWaterOrBubble());
        if (body.isInWaterOrBubble() && !aquaticSwimming)
            return TakeoverSession.Blocker.UNSAFE_WATER;
        if (!body.onGround() && !aquaticSwimming) return TakeoverSession.Blocker.AIRBORNE;
        if (TakeoverSafety.threatened(player, body.position(), carrier)) return TakeoverSession.Blocker.COMBAT;
        if (carrier != null && carrier.getTarget() != null)
            return TakeoverSession.Blocker.CRITICAL_WORK;
        return TakeoverSession.Blocker.NONE;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long tick = now(server);
        tickReleasePoses(server, tick);
        deliverPendingOranges(server);
        prunePendingIntents(server, tick);
        if (ACTIVE.isEmpty()) return;
        for (Entry e : ACTIVE.values().toArray(Entry[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.playerId);
            if (player == null) continue;
            ChangedEntity carrier = carrier(server, e);
            if (e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                    && !ChangedSynergyConfig.COMMON.exoskeletonSleep.get()
                    && tick >= e.session.getDeadline()) {
                // This is a return of control, not a release: retain the benign
                // form and worn exoskeleton exactly as they are.
                e.session.interrupt(tick);
                e.session.finish(tick);
                sync(e, player, null, true);
                clear(e, player, null);
                continue;
            }
            TakeoverSession.Phase before = e.session.getPhase();
            if (e.session.getPhase() == TakeoverSession.Phase.RELEASING) {
                e.session.tick(tick);
            } else if (!ChangedSynergyConfig.COMMON.takeoverEnabled.get() || !player.isAlive()
                    || player.isCreative() || player.isSpectator()) {
                e.session.interrupt(tick);
            } else if (e.session.getKind() == TakeoverSession.Kind.ORDINARY && (carrier == null || !carrier.isAlive())) {
                e.session.emergencyRelease(tick);
            } else {
                e.session.tick(tick);
            }
            announceTransition(e, player, before);
            tickDialogue(e, player, carrier, tick);
            if (e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                    && e.session.getPhase() != TakeoverSession.Phase.RELEASING
                    && ExoskeletonTakeoverAdapter.tick(player, e.exoskeleton, e.id)
                        == ExoskeletonTakeoverAdapter.Motion.NEEDS_RECOVERY)
                e.session.emergencyRelease(tick);
            updateControl(e, player, carrier);
            if (e.session.getPhase() == TakeoverSession.Phase.RELEASING) tryRelease(e, player, carrier);
            if (ACTIVE.get(e.playerId) == e) {
                if (tick - e.lastSync >= 5) sync(e, player, carrier, false);
                save(e, player);
            }
        }
    }

    private static void announceTransition(Entry e, ServerPlayer player, TakeoverSession.Phase before) {
        TakeoverSession.Phase phase = e.session.getPhase();
        if (phase == e.announcedPhase) return;
        e.announcedPhase = phase;
        ChangedEntity carrier = carrier(player.server, e);
        if (phase == TakeoverSession.Phase.RELEASING
                && e.session.getReleaseReason() == TakeoverSession.ReleaseReason.BREAKOUT
                && carrier != null) {
            GrabEscapeStunService.stun(carrier, player);
        }
        if (carrier != null && e.confinement) {
            String line = switch (phase) {
                case BORROWED -> "borrow";
                case STRUGGLE -> "struggle";
                case SLEEPING -> "sleep";
                default -> before == TakeoverSession.Phase.BORROWED ? "returned" : null;
            };
            if (line != null) player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.bond_kin_confinement_" + line,
                    carrier.getDisplayName()));
        } else if (carrier != null && e.session.getKind() == TakeoverSession.Kind.ORDINARY) {
            boolean reactive = e.session.isStrictBorrow();
            Cue cue = switch (phase) {
                case BORROWED -> reactive ? Cue.TAKEOVER_BORROW_GRANTED_REACTIVE
                        : Cue.TAKEOVER_BORROW_GRANTED_PROACTIVE;
                case STRUGGLE -> reactive ? Cue.TAKEOVER_STRUGGLE_REACTIVE
                        : Cue.TAKEOVER_STRUGGLE_PROACTIVE;
                case SLEEPING -> e.session.getSleepOutcome() == TakeoverSession.SleepOutcome.TRANSFUR
                        ? e.session.isStruggleUsed()
                                ? reactive ? Cue.TAKEOVER_TRANSFUR_SLEEP_FAILED_REACTIVE
                                        : Cue.TAKEOVER_TRANSFUR_SLEEP_FAILED_PROACTIVE
                                : reactive ? Cue.TAKEOVER_TRANSFUR_SLEEP_EXPIRED_REACTIVE
                                        : Cue.TAKEOVER_TRANSFUR_SLEEP_EXPIRED_PROACTIVE
                        : reactive ? Cue.TAKEOVER_SLEEP_REACTIVE
                                : Cue.TAKEOVER_SLEEP_PROACTIVE;
                default -> before == TakeoverSession.Phase.BORROWED
                        ? reactive ? Cue.TAKEOVER_CONTROL_RETURNED_REACTIVE
                            : Cue.TAKEOVER_CONTROL_RETURNED_PROACTIVE
                        : null;
            };
            if (cue != null) NpcDialogue.trigger(carrier, player, cue);
        }
        String key = switch (phase) {
            case BORROWED, STRUGGLE, SLEEPING -> null;
            case RELEASING -> e.session.getReleaseReason() == TakeoverSession.ReleaseReason.BREAKOUT
                    ? "breakout" : e.session.getReleaseReason() == TakeoverSession.ReleaseReason.EMERGENCY
                            ? "emergency" : null;
            default -> null;
        };
        if (key != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "takeover.changed_synergy.notice." + key), false);
    }

    private static void speakRefusal(Entry e, ChangedEntity carrier, ServerPlayer player,
            TakeoverSession.Result result, TakeoverSession.Blocker blocker) {
        if (e.confinement) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.bond_kin_confinement_refuse",
                    carrier.getDisplayName()));
            return;
        }
        boolean reactive = e.session.isStrictBorrow();
        Cue cue = switch (result) {
            case TOO_EARLY -> reactive ? Cue.TAKEOVER_BORROW_EARLY_REACTIVE
                    : Cue.TAKEOVER_BORROW_EARLY_PROACTIVE;
            case COOLDOWN -> reactive ? Cue.TAKEOVER_BORROW_COOLDOWN_REACTIVE
                    : Cue.TAKEOVER_BORROW_COOLDOWN_PROACTIVE;
            case BLOCKED -> switch (blocker) {
                case AIRBORNE, UNSAFE_WATER -> Cue.TAKEOVER_BORROW_AIRBORNE;
                case COMBAT -> Cue.TAKEOVER_BORROW_DANGER;
                case CRITICAL_WORK -> Cue.TAKEOVER_BORROW_BUSY;
                default -> reactive ? Cue.TAKEOVER_BORROW_EARLY_REACTIVE
                        : Cue.TAKEOVER_BORROW_EARLY_PROACTIVE;
            };
            default -> null;
        };
        if (cue != null) NpcDialogue.trigger(carrier, player, cue);
        else player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "takeover.changed_synergy.notice.refused." + result.name().toLowerCase()), true);
    }

    private static void tickDialogue(Entry e, ServerPlayer player,
            @Nullable ChangedEntity carrier, long tick) {
        if (carrier == null || e.session.getKind() != TakeoverSession.Kind.ORDINARY
                || e.session.getPhase() == TakeoverSession.Phase.SLEEPING
                || e.session.getPhase() == TakeoverSession.Phase.RELEASING
                || e.session.getPhase() == TakeoverSession.Phase.FINISHED) return;
        if (e.confinement) {
            if (tick >= e.nextAmbientLine) {
                player.sendSystemMessage(Component.translatable(
                        "message.changed_synergy.bond_kin_confinement_ambient",
                        carrier.getDisplayName()));
                e.nextAmbientLine = tick + 500L + carrier.getRandom().nextInt(301);
            }
            return;
        }
        if (tick >= e.nextAmbientLine) {
            NpcDialogue.trigger(carrier, player, e.session.isStrictBorrow()
                    ? Cue.TAKEOVER_REACTIVE_AMBIENT : Cue.TAKEOVER_PROACTIVE_AMBIENT);
            e.nextAmbientLine = tick + 300L + carrier.getRandom().nextInt(301);
        }
        if (tick < e.nextBystanderLine) return;
        var nearby = player.serverLevel().getEntitiesOfClass(ChangedEntity.class,
                carrier.getBoundingBox().inflate(10.0D), other -> other != carrier
                    && other.isAlive() && !carrying(other) && other.getTarget() == null
                    && CreatureSocialProfile.allowsDialogue(other));
        if (!nearby.isEmpty()) {
            ChangedEntity speaker = nearby.get(carrier.getRandom().nextInt(nearby.size()));
            NpcDialogue.trigger(speaker, player, Cue.TAKEOVER_BYSTANDER,
                    carrier.getDisplayName());
        }
        e.nextBystanderLine = tick + 500L + carrier.getRandom().nextInt(501);
    }

    private static void updateControl(Entry e, ServerPlayer player, @Nullable ChangedEntity carrier) {
        if (carrier == null) return;
        if (carrier.getTarget() == player) carrier.setTarget(null);
        GrabEntityAbilityInstance ability = BondedSuitService.ability(carrier);
        if (e.session.getPhase() == TakeoverSession.Phase.RELEASING) {
            if (ability != null) {
                ability.grabbedHasControl = false;
                ability.attackDown = ability.useDown = false;
            }
            return;
        }
        if (ability == null || ability.grabbedEntity != player) {
            e.session.emergencyRelease(now(player.server));
            return;
        }
        ability.suited = true;
        ability.grabbedHasControl = e.session.hasPlayerControl();
        if (!ability.grabbedHasControl) {
            ability.attackDown = false;
            ability.useDown = false;
            ability.grabStrength = 1.0F;
            Vec3 motion = carrier.getDeltaMovement();
            if (motion.horizontalDistanceSqr() > 1.0E-4D) {
                carrier.getLookControl().setLookAt(
                        carrier.getX() + motion.x * 4.0D,
                        carrier.getEyeY(),
                        carrier.getZ() + motion.z * 4.0D,
                        20.0F, 20.0F);
            }
        }
    }

    /** Ends every older hold before a new suit is promoted, preventing two grab owners. */
    private static void releasePreviousGrabbers(ServerPlayer player, ChangedEntity carrier) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ChangedEntity other) || other == carrier) continue;
                GrabEntityAbilityInstance old = BondedSuitService.ability(other);
                if (old != null && old.grabbedEntity != null
                        && player.getUUID().equals(old.grabbedEntity.getUUID())) {
                    old.releaseEntity(false);
                    Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> other),
                            new GrabEntityPacket(other, player, GrabType.RELEASE));
                    if (other.getTarget() != null
                            && player.getUUID().equals(other.getTarget().getUUID()))
                        other.setTarget(null);
                    HuntMemory.clear(other);
                    other.getNavigation().stop();
                }
            }
        }
    }

    private static void tryRelease(Entry e, ServerPlayer player, @Nullable ChangedEntity carrier) {
        tryRelease(e, player, carrier, false);
    }

    private static void tryRelease(Entry e, ServerPlayer player, @Nullable ChangedEntity carrier, boolean force) {
        long tick = now(player.server);
        if (!force && tick < e.nextReleaseAttempt) return;
        e.nextReleaseAttempt = tick + 10;
        e.releaseAttempts++;
        boolean transfurOutcome = e.session.getReleaseReason()
                == TakeoverSession.ReleaseReason.TRANSFUR;
        boolean transfurFailed = false;
        TransfurVariant<?> targetVariant = targetVariant(e);
        if (transfurOutcome && (carrier == null || targetVariant == null
                || !InheritedEyeAppearance.isValid(e.inheritedEyes))) {
            e.session.fallbackTransfur(tick);
            transfurOutcome = false;
            transfurFailed = true;
        }
        if (transfurOutcome) {
            // Durable intent precedes native separation; APPLIED is persisted as
            // soon as the permanent form exists.
            save(e, player);
        }
        boolean emergency = e.session.getReleaseReason() == TakeoverSession.ReleaseReason.EMERGENCY
                || e.session.getReleaseReason() == TakeoverSession.ReleaseReason.INTERRUPTED;
        boolean directRelease = e.session.getReleaseReason() == TakeoverSession.ReleaseReason.BREAKOUT
                || e.session.getReleaseReason() == TakeoverSession.ReleaseReason.EMERGENCY;
        Optional<Vec3> safe = directRelease ? Optional.of(player.position())
                : e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                        ? findRelease(e, player, localOrigin(e, player), carrier, 8, !emergency)
                        : preferredOrdinaryRelease(e, player, carrier, !emergency);
        if (safe.isEmpty() && !directRelease) {
            // Random fallback is deliberately last. Prefer an outdoor surface so
            // ocean and cave captures cannot select a hidden seabed cave or crack.
            safe = TakeoverSafety.findSurface(player, localOrigin(e, player), carrier,
                    32, false, restoredWidth(e), restoredHeight(e));
            if (safe.isEmpty() && (force || emergency || e.session.needsReleaseFallback()))
                safe = findRelease(e, player, localOrigin(e, player), carrier, 16, false);
        }
        // Once normal navigation has had time to work, widen only the placement
        // search. A release must have a finite end even in dense structures.
        if (safe.isEmpty() && (force || e.releaseAttempts >= 12)) {
            safe = emergencyReleasePosition(e, player, carrier);
        }
        if (safe.isEmpty()) { save(e, player); return; }
        Vec3 destination = safe.get();
        if (e.session.getKind() == TakeoverSession.Kind.EXOSKELETON) {
            // Recovery is performed beside the chosen wake location. The adapter's
            // item fallback deliberately rejects positions over four blocks away.
            player.teleportTo(destination.x, destination.y, destination.z);
            var result = e.session.hasSlept()
                    ? ExoskeletonTakeoverAdapter.releaseAway(player, e.exoskeleton)
                    : ExoskeletonTakeoverAdapter.release(player, e.exoskeleton);
            if (!e.session.hasSlept()
                    && (result == ExoskeletonTakeoverAdapter.Release.UNSAFE
                        || result == ExoskeletonTakeoverAdapter.Release.RETRY
                        || result == ExoskeletonTakeoverAdapter.Release.NOT_FOUND))
                result = ExoskeletonTakeoverAdapter.releaseToGround(player, e.exoskeleton, destination);
            if ((result == ExoskeletonTakeoverAdapter.Release.UNSAFE
                    || result == ExoskeletonTakeoverAdapter.Release.RETRY)
                    && e.releaseAttempts < 12) {
                save(e, player);
                return;
            }
            // NOT_FOUND/ALREADY_RELEASED are not proof that the body slot is empty.
            // Preserve the actual item rather than finishing with equipment still worn.
            if (!ExoskeletonTakeoverAdapter.detachRemainingEquipment(player)) {
                save(e, player);
                return;
            }
            ExoskeletonTakeoverAdapter.syncEquipment(player);
        } else if (carrier != null) {
            GrabEntityAbilityInstance ability = BondedSuitService.ability(carrier);
            e.recoveryPermit = true;
            carrier.getPersistentData().putBoolean(RELEASE, true);
            try {
                // Player cloning replaces the ServerPlayer instance while preserving
                // its UUID. Release whichever stale entity reference Changed retained
                // for this session carrier, then restore the current player below.
                if (ability != null && ability.grabbedEntity != null) ability.releaseEntity(false);
            } finally {
                carrier.getPersistentData().remove(RELEASE);
                e.recoveryPermit = false;
            }
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            ChangedAddonCompat.syncFriendlySuitControl(carrier, player, false);
            Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> carrier),
                    new GrabEntityPacket(carrier, player, GrabType.RELEASE));
            // A successful QTE breaks the hold where it happens. Native release
            // has already selected the immediate separation position, so do not
            // reroute the player through the normal safe-location search.
            if (directRelease) destination = player.position();
        }
        if (e.session.getKind() == TakeoverSession.Kind.ORDINARY
                && transfurOutcome && !e.transfurApplied) {
            STARTING_PLAYERS.add(player.getUUID());
            try {
                if (!applySleepTransfur(player, carrier, targetVariant, e)) {
                    e.session.fallbackTransfur(tick);
                    transfurOutcome = false;
                    transfurFailed = true;
                    restoreForm(player, e);
                }
            } finally {
                STARTING_PLAYERS.remove(player.getUUID());
            }
        } else if (!e.transfurApplied) {
            STARTING_PLAYERS.add(player.getUUID());
            try {
                restoreForm(player, e);
            } finally {
                STARTING_PLAYERS.remove(player.getUUID());
            }
        }
        if (e.transfurApplied) {
            MergedPlayerIdentity.apply(player, mergedCarrierName(e, carrier));
        }
        if (e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                && (ProcessTransfur.isPlayerTransfurred(player)
                    || ExoskeletonTakeoverAdapter.isWearingExoskeleton(player))) {
            save(e, player);
            return;
        }
        player.teleportTo(destination.x, destination.y, destination.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.noPhysics = false;
        if (player instanceof LivingEntityDataExtension extension) extension.setGrabbedBy(null);
        // A takeover can outlast the truce granted when absorption began. Refresh
        // it at wake-up so a player released near the carrier's community is not
        // immediately targeted and transfurred again. A deliberate new attack still
        // clears this ordinary grace through FactionReputationEvents.
        if (e.session.getKind() == TakeoverSession.Kind.ORDINARY)
            FactionHostilityGrace.beginGlobal(player);
        if (e.session.getReleaseReason() == TakeoverSession.ReleaseReason.SLEEP
                || e.session.getReleaseReason() == TakeoverSession.ReleaseReason.TRANSFUR)
            beginProneRelease(player, tick);
        e.session.finish(tick);
        if (e.session.claimAchievement()) SynergyAdvancements.grant(player, SynergyAdvancements.TAKEOVER_BREAKOUT);
        CompoundTag rewards = persisted(player);
        long lastOrange = rewards.getLong(ORANGE_AT);
        boolean orangeCooldownReady = !rewards.contains(ORANGE_AT) || tick < lastOrange
                || tick - lastOrange >= TakeoverSession.ORANGE_COOLDOWN_TICKS;
        if (ChangedSynergyConfig.COMMON.takeoverOranges.get()
                && e.session.claimOranges(orangeCooldownReady)) {
            int remainder = addOrangesToInventory(player, 3);
            if (remainder > 0) player.drop(
                    new net.minecraft.world.item.ItemStack(ChangedItems.ORANGE.get(), remainder), false);
            rewards.putLong(ORANGE_AT, tick);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "takeover.changed_synergy.notice.compensation_items"), false);
        }
        if (e.transfurApplied) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "takeover.changed_synergy.notice.transfur_after_sleep_complete",
                    mergedCarrierName(e, carrier)), false);
        } else if (transfurFailed) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "takeover.changed_synergy.notice.transfur_after_sleep_failed"), false);
        }
        if (e.confinement && carrier != null && carrier.isAlive()) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.bond_kin_confinement_end",
                    carrier.getDisplayName()));
        }
        sync(e, player, carrier, true);
        clear(e, player, carrier);
        if (e.session.getKind() == TakeoverSession.Kind.ORDINARY
                && e.transfurApplied && carrier != null && carrier.isAlive()) {
            PatAnimationService.stop(carrier);
            carrier.discard();
        }
    }

    private static Component mergedCarrierName(
            Entry entry,
            @Nullable ChangedEntity carrier) {
        if (!entry.carrierName.isEmpty()) {
            try {
                Component saved = Component.Serializer.fromJson(entry.carrierName);
                if (saved != null) return saved;
            } catch (RuntimeException ignored) {
                // Older or damaged recovery data can still use the live entity.
            }
        }
        return carrier == null ? Component.translatable(
                "takeover.changed_synergy.carrier.unknown") : carrier.getDisplayName();
    }

    private static boolean applySleepTransfur(
            ServerPlayer player,
            ChangedEntity carrier,
            TransfurVariant<?> targetVariant,
            Entry entry) {
        try {
            TransfurContext context = TransfurContext.npcLatexHazard(
                    carrier, TransfurCause.GRAB_ABSORB);
            var instance = ProcessTransfur.setPlayerTransfurVariant(
                    player, targetVariant, context, 1.0F, false,
                    candidate -> InheritedEyeAppearance.attach(
                            candidate, entry.inheritedEyes));
            if (instance == null
                    || !InheritedEyeAppearance.attach(instance, entry.inheritedEyes)) {
                return false;
            }
            instance.transfurContext = context;
            instance.transfurProgressionO = 1.0F;
            instance.transfurProgression = 1.0F;
            instance.setTemporaryForSuit(false);
            ProcessTransfur.setPlayerTransfurProgress(player, 1.0F);
            ProcessTransfur.onNewlyTransfurred(
                    IAbstractChangedEntity.forPlayerWithVariant(player, instance));
            Changed.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    SyncTransfurPacket.Builder.of(player));
            ChangedSounds.broadcastSound(player, targetVariant.sound, 1.0F, 1.0F);
            player.refreshDimensions();
            entry.transfurApplied = true;
            save(entry, player);
            return true;
        } catch (RuntimeException exception) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not complete takeover sleep transfur for {}",
                    player.getGameProfile().getName(), exception);
            return false;
        }
    }

    private static int addOrangesToInventory(ServerPlayer player, int count) {
        var stack = new net.minecraft.world.item.ItemStack(ChangedItems.ORANGE.get(), count);
        player.getInventory().add(stack);
        return stack.getCount();
    }

    private static void deliverPendingOranges(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CompoundTag rewards = persisted(player);
            int pending = Math.max(0, rewards.getInt(ORANGE_PENDING));
            if (pending == 0) continue;
            int remainder = addOrangesToInventory(player, pending);
            if (remainder > 0) player.drop(
                    new net.minecraft.world.item.ItemStack(ChangedItems.ORANGE.get(), remainder), false);
            rewards.remove(ORANGE_PENDING);
        }
    }

    private static Optional<Vec3> preferredOrdinaryRelease(Entry e, ServerPlayer player,
            @Nullable ChangedEntity carrier, boolean avoidThreats) {
        if (e.session.getKind() != TakeoverSession.Kind.ORDINARY) return Optional.empty();
        if (carrier != null && carrier.level() == player.level()) {
            Optional<CreatureCommunityData.Snapshot> community = CreatureCommunityData.snapshot(carrier);
            if (community.isPresent()) {
                Optional<BlockPos> cache = community.get().cache();
                if (cache.isPresent()) {
                    Optional<Vec3> point = findRelease(e, player, cache.get().getCenter(), carrier, 8, avoidThreats);
                    if (point.isPresent()) return point;
                }
                Optional<Vec3> point = findRelease(e, player,
                        community.get().center().getCenter(), carrier, 8, avoidThreats);
                if (point.isPresent()) return point;
            }
            Optional<BlockPos> nearby = CreatureSettlementService.nearestCompatibleCache(carrier, 128.0D);
            if (nearby.isPresent()) {
                Optional<Vec3> point = findRelease(e, player, nearby.get().getCenter(), carrier, 8, avoidThreats);
                if (point.isPresent()) return point;
            }
        }
        BlockPos bed = player.getRespawnPosition();
        if (bed != null && player.getRespawnDimension().equals(player.level().dimension())) {
            Optional<Vec3> point = findRelease(e, player, bed.getCenter(), carrier, 5, avoidThreats);
            if (point.isPresent()) return point;
        }
        return Optional.empty();
    }

    private static Vec3 localOrigin(Entry e, ServerPlayer player) {
        return player.level().dimension().location().toString().equals(e.originDimension)
                ? e.origin : player.position();
    }

    private static Optional<Vec3> findRelease(Entry e, ServerPlayer player, Vec3 anchor,
            @Nullable ChangedEntity carrier, int radius, boolean avoidThreats) {
        double width = 0.6, height = 1.8;
        ResourceLocation id = ResourceLocation.tryParse(resultForm(e));
        var variant = id == null || e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        if (variant != null) {
            var dimensions = variant.getEntityType().getDimensions();
            width = Math.max(width, dimensions.width);
            height = Math.max(height, dimensions.height);
        }
        Entity localCarrier = carrier != null && carrier.level() == player.level() ? carrier : null;
        return TakeoverSafety.find(player, anchor, localCarrier, radius, avoidThreats,
                width, height, e.session.hasSlept() && localCarrier != null ? 10.0 : 0.0);
    }

    private static Optional<Vec3> emergencyReleasePosition(Entry e, ServerPlayer player,
            @Nullable ChangedEntity carrier) {
        Entity localCarrier = carrier != null && carrier.level() == player.level() ? carrier : null;
        double width = restoredWidth(e);
        double height = restoredHeight(e);
        for (Vec3 anchor : new Vec3[]{player.position(), localOrigin(e, player),
                player.serverLevel().getSharedSpawnPos().getCenter()}) {
            Optional<Vec3> found = TakeoverSafety.findEmergency(
                    player, anchor, localCarrier, width, height);
            if (found.isPresent()) return found;
            BlockPos column = BlockPos.containing(anchor);
            int surfaceY = player.serverLevel().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            found = TakeoverSafety.findEmergency(player,
                    new Vec3(column.getX() + 0.5, surfaceY, column.getZ() + 0.5),
                    localCarrier, width, height);
            if (found.isPresent()) return found;
        }
        // Absolute terminal fallback: use the top motion-blocking surface near the
        // current position. This is intentionally reached only after bounded safe
        // searches, and prevents a permanent black overlay/session lock.
        ServerLevel level = player.serverLevel();
        BlockPos at = BlockPos.containing(player.position());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        y = Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 2, y));
        return Optional.of(new Vec3(at.getX() + 0.5, y, at.getZ() + 0.5));
    }

    private static double restoredWidth(Entry e) {
        ResourceLocation id = ResourceLocation.tryParse(resultForm(e));
        var variant = id == null || e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        return variant == null ? 0.6 : Math.max(0.6, variant.getEntityType().getDimensions().width);
    }

    private static double restoredHeight(Entry e) {
        ResourceLocation id = ResourceLocation.tryParse(resultForm(e));
        var variant = id == null || e.session.getKind() == TakeoverSession.Kind.EXOSKELETON
                ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        return variant == null ? 1.8 : Math.max(1.8, variant.getEntityType().getDimensions().height);
    }

    private static String resultForm(Entry entry) {
        return entry.transfurApplied
                || entry.session.getReleaseReason() == TakeoverSession.ReleaseReason.TRANSFUR
                ? entry.targetForm : entry.originalForm;
    }

    @Nullable
    private static TransfurVariant<?> targetVariant(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.targetForm);
        return id == null ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
    }

    private static void beginProneRelease(ServerPlayer player, long tick) {
        // Let the original blackout clear, followed by three fully visible seconds.
        PRONE_UNTIL.put(player.getUUID(), tick
                + TakeoverSession.EYE_OPEN_TICKS + 60L);
        player.setForcedPose(Pose.SWIMMING);
        player.setPose(Pose.SWIMMING);
    }

    private static void tickReleasePoses(MinecraftServer server, long tick) {
        var iterator = PRONE_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            var pose = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(pose.getKey());
            if (player == null || !player.isAlive() || tick >= pose.getValue()) {
                if (player != null && player.getForcedPose() == Pose.SWIMMING) player.setForcedPose(null);
                iterator.remove();
            } else {
                player.setForcedPose(Pose.SWIMMING);
                player.setPose(Pose.SWIMMING);
            }
        }
    }

    private static void restoreForm(ServerPlayer player, Entry e) {
        if (e.session.getKind() == TakeoverSession.Kind.EXOSKELETON || e.originalForm.isEmpty()) {
            ProcessTransfur.removePlayerTransfurVariant(player);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(e.originalForm);
        var variant = id == null ? null : ChangedRegistry.TRANSFUR_VARIANT.get().getValue(id);
        if (variant == null) ProcessTransfur.removePlayerTransfurVariant(player);
        else ProcessTransfur.setPlayerTransfurVariant(player, variant);
    }

    private static String currentForm(ServerPlayer player) {
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(v -> v.getParent().getFormId().toString()).orElse("");
    }

    private static void clear(Entry e, ServerPlayer player, @Nullable ChangedEntity carrier) {
        ACTIVE.remove(e.playerId);
        persisted(player).remove(SAVE);
        player.getPersistentData().remove(CLIENT);
        if (carrier != null) carrier.getPersistentData().remove(CLIENT);
    }

    private static void save(Entry e, ServerPlayer player) {
        CompoundTag tag = TakeoverSessionNbt.write(e.session);
        tag.putUUID("Session", e.id);
        if (e.carrierId != null) tag.putUUID("Carrier", e.carrierId);
        tag.putString("OriginalForm", e.originalForm);
        tag.putString("TargetForm", e.targetForm);
        if (!e.inheritedEyes.isEmpty()) tag.put("InheritedEyes", e.inheritedEyes.copy());
        if (!e.carrierName.isEmpty()) tag.putString("CarrierName", e.carrierName);
        tag.putBoolean("TransfurApplied", e.transfurApplied);
        tag.putString("OriginDimension", e.originDimension);
        tag.putDouble("OriginX", e.origin.x); tag.putDouble("OriginY", e.origin.y); tag.putDouble("OriginZ", e.origin.z);
        tag.putInt("ReleaseAttempts", e.releaseAttempts);
        if (!e.exoskeleton.isEmpty()) tag.put("Exoskeleton", e.exoskeleton.copy());
        persisted(player).put(SAVE, tag);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    @Nullable private static ChangedEntity carrier(MinecraftServer server, Entry e) {
        if (e.carrierId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(e.carrierId);
            if (entity instanceof ChangedEntity changed) return changed;
        }
        return null;
    }

    private static void sync(Entry e, ServerPlayer player, @Nullable ChangedEntity carrier, boolean force) {
        long tick = now(player.server);
        if (!force && tick == e.lastSync) return;
        e.lastSync = tick;
        long end = e.session.getPhase() == TakeoverSession.Phase.BORROWED
                || e.session.getPhase() == TakeoverSession.Phase.SLEEPING
                ? e.session.getPhaseUntil() : e.session.getDeadline();
        int remaining = (int)Math.max(0, Math.min(Integer.MAX_VALUE, end - tick));
        int releaseRemaining = (int)Math.max(0, Math.min(Integer.MAX_VALUE,
                e.session.getDeadline() - tick));
        int releaseTotal = (int)Math.max(1, Math.min(Integer.MAX_VALUE,
                e.session.getDeadline() - e.session.getStartedAt()));
        int cooldown = (int)Math.max(0, Math.min(Integer.MAX_VALUE, e.session.getNextBorrowAt() - tick));
        float fade;
        if (e.session.getPhase() == TakeoverSession.Phase.SLEEPING) {
            long elapsed = Math.max(0L, TakeoverSession.SLEEP_TICKS - remaining);
            fade = Math.min(1.0F, elapsed / (float)TakeoverSession.EYE_CLOSE_TICKS);
        } else {
            boolean sleepingRelease = (e.session.getPhase() == TakeoverSession.Phase.RELEASING
                    || e.session.getPhase() == TakeoverSession.Phase.FINISHED)
                    && (e.session.getReleaseReason() == TakeoverSession.ReleaseReason.SLEEP
                        || e.session.getReleaseReason() == TakeoverSession.ReleaseReason.TRANSFUR);
            fade = sleepingRelease ? 1.0F : 0.0F;
        }
        int phase = switch (e.session.getPhase()) {
            case CONTROLLED -> TakeoverStatePacket.CONTROLLED;
            case BORROWED -> TakeoverStatePacket.BORROWED;
            case STRUGGLE -> TakeoverStatePacket.STRUGGLE;
            case SLEEPING -> TakeoverStatePacket.SLEEPING;
            case RELEASING -> TakeoverStatePacket.RELEASING;
            case FINISHED -> TakeoverStatePacket.FINISHED;
        };
        var packet = new TakeoverStatePacket(e.id, e.session.getKind().ordinal(), phase,
                carrier == null ? -1 : carrier.getId(), player.getId(),
                releaseRemaining, releaseTotal, cooldown,
                e.session.isStruggleUsed(), e.session.getExpectedKey(), e.session.getQteIndex(),
                e.session.getQteIndex(), e.session.getQteLength(), fade,
                carrier == null ? "Exoskeleton" : carrier.getDisplayName().getString());
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entry e = ACTIVE.get(player.getUUID());
        if (e == null) return;
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) {
            e.session.interrupt(now(player.server));
            tryRelease(e, player, carrier(player.server, e), true);
            return;
        }
        // A forced exoskeleton has no separate living carrier to protect. While it
        // controls the body, ordinary combat/environment damage must not turn into
        // an early escape route. Administrative kill and lifecycle recovery above
        // remain available for guaranteed cleanup.
        if (e.session.getKind() == TakeoverSession.Kind.EXOSKELETON) {
            event.setCanceled(true);
            return;
        }
        if (event.getAmount() >= player.getHealth() || player.getY() < player.level().getMinBuildHeight() + 2) {
            e.session.emergencyRelease(now(player.server));
            event.setCanceled(true);
            return;
        }
        if (!e.session.hasPlayerControl()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCarrierHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity carrier)) return;
        Entry e = find(carrier);
        if (e != null && carrier.getHealth() - event.getAmount() <= carrier.getMaxHealth() * 0.25F)
            e.session.emergencyRelease(now(carrier.getServer()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Entry e = ACTIVE.get(player.getUUID());
            if (e != null) e.session.interrupt(now(player.server));
        } else if (event.getEntity() instanceof ChangedEntity carrier) {
            Entry e = find(carrier);
            if (e != null) e.session.emergencyRelease(now(carrier.getServer()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PRONE_UNTIL.remove(player.getUUID());
        if (player.getForcedPose() == Pose.SWIMMING) player.setForcedPose(null);
        Entry e = ACTIVE.get(player.getUUID());
        if (e == null) return;
        e.session.interrupt(now(player.server));
        tryRelease(e, player, carrier(player.server, e), true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entry e = ACTIVE.get(player.getUUID());
        if (e == null) return;
        e.session.interrupt(now(player.server));
        tryRelease(e, player, carrier(player.server, e), true);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entry e = ACTIVE.get(player.getUUID());
        if (e == null) return;
        e.session.interrupt(now(player.server));
        tryRelease(e, player, carrier(player.server, e), true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entry pending = ACTIVE.get(player.getUUID());
        if (pending != null) {
            pending.session.interrupt(now(player.server));
            tryRelease(pending, player, carrier(player.server, pending), true);
            if (ACTIVE.get(player.getUUID()) == pending) sync(pending, player, carrier(player.server, pending), true);
            return;
        }
        CompoundTag root = persisted(player);
        if (!root.contains(SAVE)) return;
        Entry recovery = new Entry(player, root.getCompound(SAVE));
        ACTIVE.put(player.getUUID(), recovery);
        tryRelease(recovery, player, carrier(player.server, recovery), true);
        if (ACTIVE.get(player.getUUID()) == recovery) sync(recovery, player, carrier(player.server, recovery), true);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ChangedEntity carrier)
                || !carrier.getPersistentData().getBoolean(CLIENT) || carrying(carrier)) return;
        // A carrier can load before its former player after a clean process restart.
        // Such a session is deliberately recovered rather than resumed, so discard
        // the stale native grab reference instead of letting it survive indefinitely.
        GrabEntityAbilityInstance ability = BondedSuitService.ability(carrier);
        carrier.getPersistentData().putBoolean(RELEASE, true);
        try {
            if (ability != null && ability.grabbedEntity != null) ability.releaseEntity(false);
        } finally {
            carrier.getPersistentData().remove(RELEASE);
            carrier.getPersistentData().remove(CLIENT);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNewlyTransfurred(TransfurEvents.NewlyTransfurredEntityEvent event) {
        if (!(event.entity.getEntity() instanceof ServerPlayer player)
                || STARTING_PLAYERS.contains(player.getUUID())) return;
        Entry interrupted = ACTIVE.get(player.getUUID());
        if (interrupted != null) interruptForExternalTransfur(player, interrupted);
        cancelPendingForPlayer(player.server, player.getUUID());
    }

    /** A different creature completed the transfur. Drop the old native hold and
     * session locks without restoring the form that the new transfur just selected. */
    private static void interruptForExternalTransfur(ServerPlayer player, Entry entry) {
        ChangedEntity oldCarrier = carrier(player.server, entry);
        if (oldCarrier != null) {
            GrabEntityAbilityInstance ability = BondedSuitService.ability(oldCarrier);
            entry.recoveryPermit = true;
            oldCarrier.getPersistentData().putBoolean(RELEASE, true);
            try {
                if (ability != null && ability.grabbedEntity != null
                        && player.getUUID().equals(ability.grabbedEntity.getUUID()))
                    ability.releaseEntity(false);
            } finally {
                oldCarrier.getPersistentData().remove(RELEASE);
                entry.recoveryPermit = false;
            }
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            ChangedAddonCompat.syncFriendlySuitControl(oldCarrier, player, false);
            Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> oldCarrier),
                    new GrabEntityPacket(oldCarrier, player, GrabType.RELEASE));
            if (player instanceof LivingEntityDataExtension extension
                    && extension.getGrabbedBy() == oldCarrier) {
                extension.setGrabbedBy(null);
                player.noPhysics = false;
            }
        }
        entry.session.interrupt(now(player.server));
        entry.session.finish(now(player.server));
        sync(entry, player, oldCarrier, true);
        clear(entry, player, oldCarrier);
    }

    private static void prunePendingIntents(MinecraftServer server, long tick) {
        for (var pending : new HashMap<>(PENDING_INTENTS).entrySet()) {
            PendingIntent intent = pending.getValue();
            if (intent.until() >= tick) continue;
            ChangedEntity carrier = findCarrier(server, pending.getKey());
            if (carrier != null) clearIntent(carrier);
            else PENDING_INTENTS.remove(pending.getKey());
        }
    }

    private static void cancelPendingForPlayer(MinecraftServer server, UUID playerId) {
        if (server == null) return;
        for (var pending : new HashMap<>(PENDING_INTENTS).entrySet()) {
            PendingIntent intent = pending.getValue();
            if (!playerId.equals(intent.playerId())) continue;
            ChangedEntity carrier = findCarrier(server, pending.getKey());
            if (carrier != null) {
                clearIntent(carrier);
                if (carrier.getTarget() != null && playerId.equals(carrier.getTarget().getUUID()))
                    carrier.setTarget(null);
                HuntMemory.clear(carrier);
                carrier.getNavigation().stop();
            } else PENDING_INTENTS.remove(pending.getKey());
        }
    }

    @Nullable
    private static ChangedEntity findCarrier(MinecraftServer server, UUID carrierId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(carrierId);
            if (entity instanceof ChangedEntity changed) return changed;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        PRONE_UNTIL.clear();
        PENDING_INTENTS.clear();
        STARTING_PLAYERS.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (Entry e : ACTIVE.values().toArray(Entry[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.playerId);
            if (player == null) continue;
            e.session.interrupt(now(server));
            tryRelease(e, player, carrier(server, e), true);
        }
    }

    private static boolean serverInputLocked(Player player) {
        return !player.level().isClientSide() && active(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (serverInputLocked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent event) {
        boolean bedUse = event instanceof PlayerInteractEvent.RightClickBlock block
                && allowsBedUse(event.getEntity())
                && event.getEntity().level().getBlockState(block.getPos()).getBlock() instanceof BedBlock;
        if (serverInputLocked(event.getEntity()) && !bedUse) {
            event.setCanceled(true);
            return;
        }
        if (event instanceof PlayerInteractEvent.EntityInteract interaction
                && (interaction.getTarget() instanceof ChangedEntity carrier && carrying(carrier)
                    || interaction.getTarget() instanceof Player controlled && active(controlled)))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        if (serverInputLocked(event.getPlayer())) event.setCanceled(true);
    }
}
