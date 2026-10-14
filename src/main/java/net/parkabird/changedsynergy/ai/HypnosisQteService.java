package net.parkabird.changedsynergy.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.CameraUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyMobEffects;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;

/** Server-authoritative hypnosis escape driven by gaze resistance. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HypnosisQteService {
    private static final int OBSERVATION_GRACE_TICKS = 12;
    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final int SUCCESS_IMMUNITY_TICKS = 70;
    private static final int FAILURE_MESMERIZED_TICKS = 80;
    private static final int INTERRUPTED_SOURCE_LOCK_TICKS = 40;
    private static final double HYPNOSIS_RANGE = 9.0D;
    public static final int HYPNOSIS_COOLDOWN_TICKS = 300;
    private static final String COOLDOWN_START_TIMES = "ChangedSynergyHypnosisTime";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Restraint> MESMERIZED_RESTRAINTS = new HashMap<>();
    private static final Map<UUID, Long> TARGET_IMMUNITY = new HashMap<>();
    private static final Map<UUID, UUID> IMMUNITY_SOURCE = new HashMap<>();
    private static final Map<UUID, Long> SOURCE_LOCK = new HashMap<>();
    private static int nextSessionId = 1;

    private HypnosisQteService() {
    }

    /** Replace Changed's immediate player hypnosis with one server-owned two-player contest. */
    public static boolean usesSharedPlayerContest(LivingEntity source) {
        return source instanceof Player && enabled(source.level());
    }

    public static boolean handlePlayerAbilityTick(IAbstractChangedEntity abstraction) {
        if (!(abstraction.getEntity() instanceof Player actor) || !enabled(actor.level())) return false;
        if (!(actor instanceof ServerPlayer source)) return true;
        if (!validPlayerHypnotist(source)) return true;
        Session existing = playerDuelForSource(source.getUUID());
        if (existing != null) {
            existing.lastAbilityTick = source.level().getGameTime();
            return true;
        }
        ((ServerLevel) source.level()).getEntitiesOfClass(ServerPlayer.class,
                source.getBoundingBox().inflate(HYPNOSIS_RANGE), target ->
                        validPlayerPair(source, target) && isPotentialTarget(source, target)
                                && hasUnobstructedEyeContact(source, target)
                                && lookingToward(source, target))
                .stream().min(java.util.Comparator.comparingDouble(source::distanceToSqr))
                .ifPresent(target -> observe(source, target));
        return true;
    }

    private static boolean validPlayerHypnotist(ServerPlayer source) {
        var body = ProcessTransfur.getPlayerTransfurVariant(source);
        return source.isAlive() && !source.isSpectator() && enabled(source.level())
                && !net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(source)
                && Changed.config.server.playerControllingAbilities.get()
                && !isPlayerControlLocked(source) && !TakeoverService.active(source)
                && !net.parkabird.changedsynergy.origin.NativeOriginService.pending(source)
                && body != null && !body.isTemporaryFromSuit()
                && body.hasAbility(ChangedAbilities.HYPNOSIS.get());
    }

    private static boolean validPlayerPair(ServerPlayer source, ServerPlayer target) {
        return source != target && source.server.isPvpAllowed() && source.canHarmPlayer(target)
                && source.level() == target.level() && target.isAlive()
                && !target.isCreative() && !target.isSpectator()
                && !ProcessTransfur.isPlayerTransfurred(target)
                && !TakeoverService.active(target)
                && !net.parkabird.changedsynergy.origin.NativeOriginService.pending(target)
                && !target.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get());
    }

    private static boolean lookingToward(LivingEntity source, LivingEntity target) {
        Vec3 toward = target.getEyePosition().subtract(source.getEyePosition());
        return toward.lengthSqr() < 1.0E-4D || source.getLookAngle().dot(toward.normalize()) >= 0.70D;
    }

    private static Session playerDuelForSource(UUID source) {
        return SESSIONS.values().stream().filter(session -> session.playerDuel && session.sourceUuid.equals(source))
                .findFirst().orElse(null);
    }

    public static void playerTap(ServerPlayer actor, int sessionId, boolean pressed) {
        Session session = SESSIONS.get(actor.getUUID());
        boolean hypnotist = session == null;
        if (hypnotist) session = playerDuelForSource(actor.getUUID());
        if (session == null || !session.playerDuel || session.id != sessionId) return;
        ServerPlayer target = actor.server.getPlayerList().getPlayer(session.victimUuid);
        ServerPlayer source = actor.server.getPlayerList().getPlayer(session.sourceUuid);
        if (source == null || target == null || !validPlayerHypnotist(source)
                || !validPlayerPair(source, target)
                || source.distanceToSqr(target) > HYPNOSIS_RANGE * HYPNOSIS_RANGE
                || !hasUnobstructedLineOfSight(source, target)) return;
        boolean wasPressed = hypnotist ? session.sourcePressed : session.targetPressed;
        if (hypnotist) session.sourcePressed = pressed; else session.targetPressed = pressed;
        if (!pressed || wasPressed) return;
        long now = target.level().getGameTime();
        if (hypnotist && now < session.sourceTapBlockedUntil) return;
        if (now >= session.endTick || now - session.lastAbilityTick > OBSERVATION_GRACE_TICKS) return;
        long previous = hypnotist ? session.lastSourceTap : session.lastTargetTap;
        if (now - previous < HypnosisGazeContest.PLAYER_TAP_INTERVAL_TICKS) return;
        if (hypnotist) session.lastSourceTap = now; else session.lastTargetTap = now;
        session.resistance = HypnosisGazeContest.playerTap(session.resistance, hypnotist);
        sync(target, session, HypnosisQteSyncPacket.ACTIVE);
    }

    /** A real close-range pat distracts only the caster of this human's current contest. */
    public static boolean reactToPlayerPat(ServerPlayer caster, ServerPlayer human) {
        Session session = SESSIONS.get(human.getUUID());
        if (session == null || !session.playerDuel || !session.sourceUuid.equals(caster.getUUID())
                || !validPlayerHypnotist(caster) || !validPlayerPair(caster, human)
                || human.distanceToSqr(caster) > 36.0D || !human.hasLineOfSight(caster)) return false;
        long now = human.level().getGameTime();
        if (now >= session.endTick || now < session.nextPatReactionTick) return false;
        session.sourceTapBlockedUntil = now + 60;
        session.nextPatReactionTick = now + 160;
        sync(human, session, HypnosisQteSyncPacket.ACTIVE);
        return true;
    }

    /** Called after Changed applies one tick of its standard hypnosis ability. */
    public static void observeAbilityTick(IAbstractChangedEntity abstraction) {
        LivingEntity source = abstraction.getEntity();
        if (!enabled(source.level())
                || !(source.level() instanceof ServerLevel level)
                || !source.isAlive()
                || !allowsSynergyHypnosis(source)
                || !HypnosisProfile.activelyUsesHypnosis(source)) {
            return;
        }

        AABB area = source.getBoundingBox().inflate(HYPNOSIS_RANGE);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (isAffectedByHypnosis(source, player)) {
                observe(source, player);
            }
        }
    }

    /** Also catches Addon bosses that reproduce Changed's hypnosis effects directly. */
    public static void observeAppliedHypnosis(
            LivingEntity source,
            ServerPlayer player,
            MobEffectInstance effect,
            boolean applied) {
        if (!enabled(player.level())
                || !applied || !isHypnosisConfusion(effect)
                || !(player.level() instanceof ServerLevel)
                || !allowsSynergyHypnosis(source)
                || !HypnosisProfile.activelyUsesHypnosis(source)
                || !isPotentialTarget(source, player)
                || !hasUnobstructedEyeContact(source, player)) {
            return;
        }
        observe(source, player);
    }

    private static void observe(LivingEntity source, ServerPlayer player) {
        long now = player.level().getGameTime();
        if (SOURCE_LOCK.getOrDefault(source.getUUID(), 0L) > now
                || TARGET_IMMUNITY.getOrDefault(player.getUUID(), 0L) > now) {
            return;
        }

        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.sourceUuid.equals(source.getUUID())) {
            session.lastObservedTick = now;
            return;
        }
        if (session != null) {
            return;
        }
        if (source instanceof ServerPlayer caster
                && (!validPlayerHypnotist(caster) || !validPlayerPair(caster, player)
                        || playerDuelForSource(caster.getUUID()) != null)) return;
        if (isHypnosisOnCooldown(source, player)) {
            return;
        }

        Session created = new Session(nextSessionId++, source, now, false);
        created.victimUuid = player.getUUID();
        SESSIONS.put(player.getUUID(), created);
        sync(player, created, HypnosisQteSyncPacket.ACTIVE);
        dialogue(source, player, Cue.HYPNOSIS_START);
    }

    /** Starts a harmless resistance game from the social wheel. */
    public static boolean startFriendlyPractice(
            ChangedEntity source,
            ServerPlayer player) {
        if (!enabled(player.level())
                || !CreatureSocialProfile.allowsSynergySystems(source)
                || !HypnosisProfile.isHypnoticCreature(source)
                || !source.isAlive() || !player.isAlive()
                || player.isCreative() || player.isSpectator()
                || source.level() != player.level()
                || source.distanceToSqr(player) > 8.0D * 8.0D
                || SESSIONS.containsKey(player.getUUID())) {
            return false;
        }

        long now = player.level().getGameTime();
        Session created = new Session(nextSessionId++, source, now, true);
        SESSIONS.put(player.getUUID(), created);
        source.setTarget(null);
        source.setAggressive(false);
        source.getNavigation().stop();
        source.getLookControl().setLookAt(player, 30.0F, 30.0F);
        sync(player, created, HypnosisQteSyncPacket.ACTIVE);
        dialogue(source, player, Cue.HYPNOSIS_PLAY_START);
        return true;
    }

    /** Blocks only the two exact effects used by Changed hypnosis during an escape window. */
    public static boolean shouldBlockHypnosisEffect(
            LivingEntity target,
            MobEffectInstance effect,
            Entity source) {
        if (!enabled(target.level())
                || !(target instanceof ServerPlayer player)
                || !(source instanceof LivingEntity hypnotist)
                || !isHypnosisEffect(effect)) {
            return false;
        }
        return shouldBlockHypnosisFrom(player, hypnotist);
    }

    /** Stops a successful escape from being immediately overwritten by another camera tug. */
    public static boolean shouldBlockCameraTug(LivingEntity target, LivingEntity hypnotist) {
        return enabled(target.level())
                && target instanceof ServerPlayer player
                && shouldBlockHypnosisFrom(player, hypnotist);
    }

    private static boolean shouldBlockHypnosisFrom(
            ServerPlayer player,
            LivingEntity hypnotist) {
        if (net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(player)) return true;
        if (isHypnotizing(hypnotist, player)) {
            return false;
        }
        long now = player.level().getGameTime();
        return TARGET_IMMUNITY.getOrDefault(player.getUUID(), 0L) > now
                || SOURCE_LOCK.getOrDefault(hypnotist.getUUID(), 0L) > now
                || isHypnosisOnCooldown(hypnotist, player)
                        && !isHypnotizing(hypnotist, player)
                || hypnotist instanceof ChangedEntity changed
                        && LatexSocialMemory.isSocialLatex(changed)
                        && LatexSocialMemory.shouldRemainNeutral(changed, player);
    }

    public static boolean isHypnotizing(LivingEntity source, LivingEntity target) {
        if (!enabled(source.level())) {
            return false;
        }
        Session session = SESSIONS.get(target.getUUID());
        return session != null && session.sourceUuid.equals(source.getUUID());
    }

    /**
     * Ends only the hypnosis session between this creature and the player who
     * reached out to pat it.
     */
    public static boolean interruptByPat(ChangedEntity source, ServerPlayer player) {
        if (!enabled(player.level())) {
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null
                || !session.sourceUuid.equals(source.getUUID())
                || session.friendlyPractice
                || !SESSIONS.remove(player.getUUID(), session)) {
            return false;
        }

        long now = player.level().getGameTime();
        SOURCE_LOCK.put(source.getUUID(), now + INTERRUPTED_SOURCE_LOCK_TICKS);
        beginCooldown(source, player);
        TARGET_IMMUNITY.put(player.getUUID(), now + INTERRUPTED_SOURCE_LOCK_TICKS);
        IMMUNITY_SOURCE.put(player.getUUID(), source.getUUID());
        releaseMindEffects(player);
        calmAfterEscape(source, player);
        dialogue(
                source,
                player,
                source.getRandom().nextBoolean()
                        ? Cue.HYPNOSIS_PAT_SURPRISED
                        : Cue.HYPNOSIS_PAT_PLEASED);
        send(player, session, 30, HypnosisQteSyncPacket.INTERRUPTED);
        return true;
    }

    public static boolean isPlayerControlLocked(ServerPlayer player) {
        return enabled(player.level())
                && (SESSIONS.containsKey(player.getUUID())
                        || player.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get()));
    }

    public static ServerPlayer getActiveVictim(ChangedEntity source) {
        if (!enabled(source.level())
                || !(source.level() instanceof ServerLevel level)) {
            return null;
        }
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (entry.getValue().sourceUuid.equals(source.getUUID())) {
                return level.getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    public static boolean shouldSuppressHypnotistAttack(Mob attacker, LivingEntity target) {
        return isHypnotizing(attacker, target);
    }

    public static boolean shouldPreventTargeting(ChangedEntity source, ServerPlayer player) {
        if (!enabled(player.level())) {
            return false;
        }
        long now = player.level().getGameTime();
        return TARGET_IMMUNITY.getOrDefault(player.getUUID(), 0L) > now
                && source.getUUID().equals(IMMUNITY_SOURCE.get(player.getUUID()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ChangedEntity creature
                && CreatureSocialProfile.allowsSynergySystems(creature)
                && HypnosisProfile.isHypnoticCreature(creature)
                && creature.goalSelector.getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal() instanceof HypnosisFocusGoal)) {
            creature.goalSelector.addGoal(0, new HypnosisFocusGoal(creature));
        }
    }

    private static boolean allowsSynergyHypnosis(LivingEntity source) {
        return !(source instanceof ChangedEntity creature)
                || CreatureSocialProfile.allowsSynergySystems(creature);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!enabled(player.level())) {
            Session disabled = SESSIONS.remove(player.getUUID());
            boolean mesmerized = player.hasEffect(
                    ChangedSynergyMobEffects.MESMERIZED.get());
            if (disabled != null || mesmerized) {
                if (disabled != null) {
                    send(player, disabled, 0, HypnosisQteSyncPacket.CLEAR);
                }
                player.removeEffect(ChangedSynergyMobEffects.MESMERIZED.get());
                releaseMindEffects(player);
            }
            MESMERIZED_RESTRAINTS.remove(player.getUUID());
            TARGET_IMMUNITY.remove(player.getUUID());
            IMMUNITY_SOURCE.remove(player.getUUID());
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            if (player.isAlive() && player.hasEffect(ChangedSynergyMobEffects.MESMERIZED.get())) {
                Restraint restraint = MESMERIZED_RESTRAINTS.get(player.getUUID());
                if (restraint == null
                        || !restraint.dimension.equals(player.level().dimension())) {
                    restraint = Restraint.capture(player);
                    MESMERIZED_RESTRAINTS.put(player.getUUID(), restraint);
                }
                suppressActions(player);
                lockView(player, restraint.yRot, restraint.xRot);
            } else {
                MESMERIZED_RESTRAINTS.remove(player.getUUID());
            }
            if (player.tickCount % 100 == 0) {
                prune(player.level().getGameTime());
            }
            return;
        }

        long now = player.level().getGameTime();
        LivingEntity source = findSource(player, session);
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || !session.friendlyPractice
                        && ProcessTransfur.isPlayerTransfurred(player)
                || source == null || !source.isAlive()
                || source.distanceToSqr(player) > HYPNOSIS_RANGE * HYPNOSIS_RANGE) {
            clear(player, session);
            return;
        }

        if (session.playerDuel && (!(source instanceof ServerPlayer caster)
                || !validPlayerHypnotist(caster) || !validPlayerPair(caster, player)
                || now - session.lastAbilityTick > OBSERVATION_GRACE_TICKS
                || !lookingToward(source, player))) {
            clear(player, session);
            return;
        }

        suppressActions(player);
        if (hasUnobstructedLineOfSight(source, player)) {
            session.lastObservedTick = now;
        }
        if (now - session.lastObservedTick > OBSERVATION_GRACE_TICKS) {
            clear(player, session);
            return;
        }

        session.gazeAlignment = gazeAlignment(source, player);
        float previousResistance = session.resistance;
        if (!session.playerDuel) session.resistance = HypnosisGazeContest.nextResistance(
                session.resistance, session.gazeAlignment);
        if (HypnosisGazeContest.isActivelyLookingAway(session.gazeAlignment)) {
            session.offGazeTicks++;
        } else {
            session.offGazeTicks = 0;
        }

        float elapsed = Mth.clamp(
                (now - session.startTick)
                        / (float)(session.endTick - session.startTick),
                0.0F,
                1.0F);
        CameraUtil.tugEntityLookDirection(
                player,
                source,
                HypnosisGazeContest.pullStrength(session.gazeAlignment, elapsed));

        if ((session.playerDuel && session.resistance <= 0.0F) || session.resistance >= 1.0F) {
            finish(player, session, session.playerDuel && session.resistance >= 1.0F
                    ? HypnosisQteSyncPacket.FAILED : HypnosisQteSyncPacket.SUCCESS);
            return;
        }
        if (now >= session.endTick) {
            finish(player, session, session.playerDuel && !HypnosisGazeContest.playerControlWins(session.resistance)
                    ? HypnosisQteSyncPacket.SUCCESS : HypnosisQteSyncPacket.FAILED);
            return;
        }

        if (now - session.lastSyncTick >= SYNC_INTERVAL_TICKS
                || Math.abs(session.resistance - previousResistance) >= 0.025F) {
            session.lastSyncTick = now;
            sync(player, session, HypnosisQteSyncPacket.ACTIVE);
        }
    }

    /** Blocks voluntary actions while leaving gravity, knockback and other physics intact. */
    private static void suppressActions(ServerPlayer player) {
        player.setSprinting(false);
        player.stopUsingItem();
    }

    /** Holds a fully mesmerized victim at the view angle where resistance ended. */
    private static void lockView(ServerPlayer player, float yRot, float xRot) {
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.setYHeadRot(yRot);
        player.setYBodyRot(yRot);
        player.yRotO = yRot;
        player.xRotO = xRot;
    }

    /** A hypnotized player cannot attack, and its hypnotist cannot attack before failure. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCombatAttempt(LivingAttackEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        boolean lockedAttacker = sourceEntity instanceof ServerPlayer attacker
                && isPlayerControlLocked(attacker);
        boolean attackingHypnotist = event.getEntity() instanceof ServerPlayer victim
                && sourceEntity instanceof LivingEntity hypnotist
                && isHypnotizing(hypnotist, victim);
        if (lockedAttacker || attackingHypnotist) {
            event.setCanceled(true);
        }
    }

    /** Cancels the player's attack packet before a client-side swing becomes combat state. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPlayerControlLocked(player)) {
            event.setCanceled(true);
        }
    }

    /** Prevents block/item/entity interactions while hypnotized or mesmerized. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPlayerControlLocked(player)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    /** Stops an item already in use and rejects new use actions from other mods. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && isPlayerControlLocked(player)) {
            event.setCanceled(true);
            player.stopUsingItem();
        }
    }

    /** Defensive server-side guard for clients or mods that bypass left-click interaction events. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isPlayerControlLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPlayerControlLocked(player)) {
            event.setCanceled(true);
        }
    }

    /** Damage from anyone else interrupts the hypnotist and gives victims time to move away. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHypnotistHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F || event.getEntity().level().isClientSide()) {
            return;
        }
        UUID sourceUuid = event.getEntity().getUUID();
        Entity attacker = event.getSource().getEntity();
        boolean attackerIsActiveVictim = attacker instanceof ServerPlayer player
                && isHypnotizing(event.getEntity(), player);
        if (!attackerIsActiveVictim) {
            interruptSource(event.getEntity(), sourceUuid);
        }
    }

    /** The successful victim cannot be reacquired during its short escape window. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof ChangedEntity source
                && event.getNewTarget() instanceof ServerPlayer player
                && shouldPreventTargeting(source, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUuid = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.remove(playerUuid);
            if (session != null) {
                beginCooldown(findSource(player, session), player);
                send(player, session, 0, HypnosisQteSyncPacket.CLEAR);
            }
            interruptSource(player, playerUuid);
        } else {
            SESSIONS.remove(playerUuid);
        }
        MESMERIZED_RESTRAINTS.remove(playerUuid);
        TARGET_IMMUNITY.remove(playerUuid);
        IMMUNITY_SOURCE.remove(playerUuid);
    }

    private static boolean isPotentialTarget(LivingEntity source, ServerPlayer player) {
        if (net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(player)) return false;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.friendlyPractice
                && session.sourceUuid.equals(source.getUUID())) {
            return true;
        }
        if (!source.isAlive() || !player.isAlive() || player.isCreative() || player.isSpectator()
                || ProcessTransfur.isPlayerTransfurred(player)
                || source.distanceToSqr(player) > HYPNOSIS_RANGE * HYPNOSIS_RANGE
                || isHypnosisOnCooldown(source, player)
                        && !isHypnotizing(source, player)) {
            return false;
        }
        return (!(source instanceof ServerPlayer caster) || validPlayerPair(caster, player))
                && !(source instanceof ChangedEntity changed
                && LatexSocialMemory.isSocialLatex(changed)
                && LatexSocialMemory.shouldRemainNeutral(changed, player));
    }

    private static boolean isAffectedByHypnosis(LivingEntity source, ServerPlayer player) {
        if (!isPotentialTarget(source, player)
                || !hasUnobstructedEyeContact(source, player)) {
            return false;
        }
        MobEffectInstance confusion = player.getEffect(MobEffects.CONFUSION);
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return confusion != null && confusion.getAmplifier() == 2 && confusion.getDuration() >= 118
                && slowness != null && slowness.getAmplifier() == 2;
    }

    private static boolean isLookingAtSource(LivingEntity source, ServerPlayer player) {
        Vec3 towardSource = source.getEyePosition().subtract(player.getEyePosition());
        return towardSource.lengthSqr() >= 1.0E-4D
                && player.getLookAngle().dot(towardSource.normalize()) >= 0.85D;
    }

    private static boolean hasUnobstructedEyeContact(
            LivingEntity source,
            ServerPlayer player) {
        return isLookingAtSource(source, player)
                && hasUnobstructedLineOfSight(source, player);
    }

    private static boolean hasUnobstructedLineOfSight(
            LivingEntity source,
            ServerPlayer player) {
        Vec3 playerEyes = player.getEyePosition();
        Vec3 sourceEyes = source.getEyePosition();
        return player.level().clip(new ClipContext(
                playerEyes,
                sourceEyes,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player)).getType() == HitResult.Type.MISS;
    }

    private static float gazeAlignment(LivingEntity source, ServerPlayer player) {
        Vec3 towardSource = source.getEyePosition().subtract(player.getEyePosition());
        if (towardSource.lengthSqr() < 1.0E-4D) {
            return 1.0F;
        }
        return HypnosisGazeContest.alignmentFromDot(
                player.getLookAngle().dot(towardSource.normalize()));
    }

    private static boolean isHypnosisConfusion(MobEffectInstance effect) {
        return effect.getEffect() == MobEffects.CONFUSION
                && effect.getAmplifier() == 2
                && effect.getDuration() == 120;
    }

    private static boolean isHypnosisEffect(MobEffectInstance effect) {
        return effect.getAmplifier() == 2
                && (effect.getEffect() == MobEffects.CONFUSION && effect.getDuration() == 120
                        || effect.getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                                && effect.getDuration() == 5);
    }

    private static LivingEntity findSource(ServerPlayer player, Session session) {
        Entity entity = ((ServerLevel)player.level()).getEntity(session.sourceUuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    private static void finish(ServerPlayer player, Session session, int result) {
        if (!SESSIONS.remove(player.getUUID(), session)) {
            return;
        }
        long now = player.level().getGameTime();
        LivingEntity source = findSource(player, session);
        beginCooldown(source, player);
        if (result == HypnosisQteSyncPacket.SUCCESS) {
            session.resistance = session.playerDuel ? 0.0F : 1.0F;
            TARGET_IMMUNITY.put(player.getUUID(), now + SUCCESS_IMMUNITY_TICKS);
            IMMUNITY_SOURCE.put(player.getUUID(), session.sourceUuid);
            releaseMindEffects(player);
            calmAfterEscape(source, player);
            if (session.friendlyPractice) {
                dialogue(source, player, Cue.HYPNOSIS_PLAY_SUCCESS);
            } else {
                pushAway(player, source);
                dialogue(source, player, Cue.HYPNOSIS_ESCAPE);
                SynergyAdvancements.grant(
                        player, SynergyAdvancements.HYPNOSIS_ESCAPE);
            }
        } else if (result == HypnosisQteSyncPacket.FAILED) {
            if (session.playerDuel) session.resistance = 1.0F;
            releaseMindEffects(player);
            if (session.friendlyPractice) {
                calmAfterEscape(source, player);
                dialogue(source, player, Cue.HYPNOSIS_PLAY_FAILED);
                if (source != null) {
                    PatAnimationService.startFixed(source, player, 4);
                }
            } else {
                player.addEffect(new MobEffectInstance(
                        ChangedSynergyMobEffects.MESMERIZED.get(),
                        FAILURE_MESMERIZED_TICKS,
                        0,
                        false,
                        false,
                        true));
                MESMERIZED_RESTRAINTS.put(player.getUUID(), Restraint.capture(player));
                startAttack(source, player);
                dialogue(source, player, Cue.HYPNOSIS_FAILED);
            }
        }
        int resultTicks = result == HypnosisQteSyncPacket.FAILED
                && !session.friendlyPractice
                ? FAILURE_MESMERIZED_TICKS : 30;
        send(player, session, resultTicks, result);
    }

    private static void interruptSource(LivingEntity source, UUID sourceUuid) {
        if (SESSIONS.values().stream().noneMatch(session -> session.sourceUuid.equals(sourceUuid))) {
            return;
        }
        long now = source.level().getGameTime();
        SOURCE_LOCK.put(sourceUuid, now + INTERRUPTED_SOURCE_LOCK_TICKS);
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            if (!session.sourceUuid.equals(sourceUuid)) {
                continue;
            }
            ServerPlayer player = source.getServer() == null
                    ? null : source.getServer().getPlayerList().getPlayer(entry.getKey());
            iterator.remove();
            if (player != null) {
                if (session.friendlyPractice) {
                    releaseMindEffects(player);
                    send(player, session, 0, HypnosisQteSyncPacket.CLEAR);
                    continue;
                }
                beginCooldown(source, player);
                TARGET_IMMUNITY.put(player.getUUID(), now + INTERRUPTED_SOURCE_LOCK_TICKS);
                IMMUNITY_SOURCE.put(player.getUUID(), sourceUuid);
                releaseMindEffects(player);
                calmAfterEscape(source, player);
                dialogue(source, player, Cue.HYPNOSIS_INTERRUPTED);
                send(player, session, 30, HypnosisQteSyncPacket.INTERRUPTED);
            }
        }
    }

    private static void clear(ServerPlayer player, Session session) {
        if (SESSIONS.remove(player.getUUID(), session)) {
            beginCooldown(findSource(player, session), player);
            releaseMindEffects(player);
            send(player, session, 0, HypnosisQteSyncPacket.CLEAR);
        }
    }

    private static boolean isHypnosisOnCooldown(LivingEntity source, ServerPlayer player) {
        CompoundTag cooldowns = source.getPersistentData().getCompound(COOLDOWN_START_TIMES);
        String playerKey = player.getStringUUID();
        if (!cooldowns.contains(playerKey)) {
            return false;
        }
        long startTick = cooldowns.getLong(playerKey);
        if (player.level().getGameTime() < startTick + HYPNOSIS_COOLDOWN_TICKS) {
            return true;
        }
        cooldowns.remove(playerKey);
        if (cooldowns.isEmpty()) {
            source.getPersistentData().remove(COOLDOWN_START_TIMES);
        } else {
            source.getPersistentData().put(COOLDOWN_START_TIMES, cooldowns);
        }
        return false;
    }

    private static void beginCooldown(LivingEntity source, ServerPlayer player) {
        if (source == null) {
            return;
        }
        CompoundTag persistentData = source.getPersistentData();
        CompoundTag cooldowns = persistentData.getCompound(COOLDOWN_START_TIMES);
        cooldowns.putLong(player.getStringUUID(), player.level().getGameTime());
        persistentData.put(COOLDOWN_START_TIMES, cooldowns);
    }

    private static void releaseMindEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        CameraUtil.resetTugData(player);
    }

    private static void calmAfterEscape(LivingEntity source, ServerPlayer player) {
        if (!(source instanceof Mob mob)) {
            return;
        }
        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }
        mob.setAggressive(false);
        mob.getNavigation().stop();
        if (mob instanceof ChangedEntity changed) {
            HuntMemory.clear(changed);
        }
    }

    private static void startAttack(LivingEntity source, ServerPlayer player) {
        if (!(source instanceof Mob mob) || !source.isAlive()) {
            return;
        }
        mob.setTarget(player);
        mob.setAggressive(true);
        if (mob instanceof ChangedEntity changed) {
            HuntMemory.seeTarget(changed, player);
        }
    }

    private static void pushAway(ServerPlayer player, LivingEntity source) {
        if (source == null) {
            return;
        }
        Vec3 away = player.position().subtract(source.position());
        if (away.horizontalDistanceSqr() > 1.0E-4D) {
            Vec3 push = new Vec3(away.x, 0.0D, away.z).normalize().scale(0.35D);
            player.push(push.x, 0.08D, push.z);
            player.hurtMarked = true;
        }
    }

    private static void dialogue(LivingEntity source, ServerPlayer player, Cue cue) {
        if (source instanceof ChangedEntity changed && source.isAlive()) {
            NpcDialogue.trigger(changed, player, cue);
        }
    }

    private static void sync(ServerPlayer player, Session session, int state) {
        int remaining = (int)Math.max(0L, session.endTick - player.level().getGameTime());
        send(player, session, remaining, state);
    }

    private static void send(ServerPlayer player, Session session, int remaining, int state) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new HypnosisQteSyncPacket(
                        session.id, session.sourceId, session.offGazeTicks,
                        session.resistance, session.gazeAlignment, remaining, state,
                        session.playerDuel, false, player.getId(),
                        (int)Math.max(0, session.sourceTapBlockedUntil - player.level().getGameTime())));
        if (session.playerDuel) {
            ServerPlayer caster = player.server.getPlayerList().getPlayer(session.sourceUuid);
            if (caster != null) ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> caster),
                    new HypnosisQteSyncPacket(session.id, session.sourceId, session.offGazeTicks,
                            session.resistance, session.gazeAlignment, remaining, state, true, true, player.getId(),
                            (int)Math.max(0, session.sourceTapBlockedUntil - player.level().getGameTime())));
        }
    }

    private static void prune(long now) {
        TARGET_IMMUNITY.entrySet().removeIf(entry -> entry.getValue() <= now);
        IMMUNITY_SOURCE.keySet().removeIf(
                uuid -> TARGET_IMMUNITY.getOrDefault(uuid, 0L) <= now);
        SOURCE_LOCK.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SESSIONS.clear(); MESMERIZED_RESTRAINTS.clear(); TARGET_IMMUNITY.clear();
        IMMUNITY_SOURCE.clear(); SOURCE_LOCK.clear();
    }

    private static boolean enabled(Level level) {
        return ChangedSynergyGameRules.enabled(
                level, ChangedSynergyGameRules.HYPNOSIS_QTE);
    }

    private static final class Session {
        private final int id;
        private final UUID sourceUuid;
        private final int sourceId;
        private int offGazeTicks;
        private float resistance;
        private float gazeAlignment;
        private final long startTick;
        private final long endTick;
        private final boolean friendlyPractice;
        private final boolean playerDuel;
        private UUID victimUuid;
        private long lastAbilityTick;
        private long lastSourceTap = Long.MIN_VALUE / 2, lastTargetTap = Long.MIN_VALUE / 2;
        private boolean sourcePressed, targetPressed;
        private long sourceTapBlockedUntil, nextPatReactionTick;
        private long lastObservedTick;
        private long lastSyncTick;

        private Session(
                int id,
                LivingEntity source,
                long now,
                boolean friendlyPractice) {
            this.id = id;
            this.sourceUuid = source.getUUID();
            this.sourceId = source.getId();
            this.offGazeTicks = 0;
            this.playerDuel = source instanceof ServerPlayer;
            this.resistance = playerDuel ? 0.5F : 0.0F;
            this.gazeAlignment = 1.0F;
            this.startTick = now;
            this.endTick = now + (playerDuel ? HypnosisGazeContest.PLAYER_DURATION_TICKS : HypnosisGazeContest.DURATION_TICKS);
            this.friendlyPractice = friendlyPractice;
            this.lastObservedTick = now;
            this.lastAbilityTick = now;
            this.lastSyncTick = now;
        }
    }

    private record Restraint(
            ResourceKey<Level> dimension,
            float yRot,
            float xRot) {
        private static Restraint capture(ServerPlayer player) {
            return new Restraint(
                    player.level().dimension(),
                    player.getYRot(),
                    player.getXRot());
        }
    }
}
