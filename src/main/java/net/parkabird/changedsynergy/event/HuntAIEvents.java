package net.parkabird.changedsynergy.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.DarkLatexDisguise;
import net.parkabird.changedsynergy.ai.FirearmEvasionGoal;
import net.parkabird.changedsynergy.ai.FirearmThreatService;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.HuntMemory;
import net.parkabird.changedsynergy.ai.HuntState;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexSocialRelation;
import net.parkabird.changedsynergy.ai.SmartSearchGoal;
import net.parkabird.changedsynergy.ai.UnderwaterPursuitGoal;
import net.parkabird.changedsynergy.ai.VoluntaryBondTransfurService;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HuntAIEvents {
    private static final UUID CHASE_SPEED_MODIFIER = UUID.fromString("30a86f91-3890-44ea-b362-e78c5cf2774e");
    private static final String LAST_NOISE_TICK = "ChangedSynergyLastNoiseTick";
    private static final String NEXT_SOCIAL_TICK = "ChangedSynergyNextSocialTick";
    private static final String COMPATRIOT_RALLY_UNTIL =
            "ChangedSynergyCompatriotRallyUntil";
    private static final TagKey<EntityType<?>> LATEXES = tag("changed", "latexes");
    private static final TagKey<EntityType<?>> ORGANIC_LATEX = tag("changed", "organic_latex");
    private static final TagKey<EntityType<?>> BENIGN_LATEXES = tag("changed", "benign_latexes");
    private static final TagKey<EntityType<?>> HUNT_AI_EXCLUDED = tag("changed_synergy", "hunt_ai_excluded");

    private HuntAIEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            UnderwaterPursuitGoal.flushSwimmingOrientations(event.level);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ChangedEntity mob)) {
            return;
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof SmartSearchGoal)) {
            mob.goalSelector.addGoal(1, new SmartSearchGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof UnderwaterPursuitGoal)) {
            mob.goalSelector.addGoal(0, new UnderwaterPursuitGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof FirearmEvasionGoal)) {
            mob.goalSelector.addGoal(0, new FirearmEvasionGoal(mob));
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide || mob.tickCount % 5 != 0) {
            return;
        }

        if (!isHuntAIEnabled(mob) || !isEligibleHunter(mob)) {
            removeChaseBoost(mob);
            if (HuntMemory.getState(mob) != HuntState.IDLE) {
                HuntMemory.clear(mob);
            }
            return;
        }

        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget instanceof ServerPlayer player) {
            if (CreatureCacheGuardService.isDefendingAgainst(mob, player)) {
                // Cache guards deliberately use the entity's native melee
                // pursuit. Enhanced hunt AI must neither boost nor clear it.
                removeChaseBoost(mob);
                HuntMemory.clear(mob);
                return;
            }
            tickPlayerPursuit(mob, player);
        } else {
            removeChaseBoost(mob);
            if (currentTarget != null) {
                HuntMemory.clear(mob);
            } else if (HuntMemory.getState(mob) == HuntState.CHASING
                    && HuntMemory.getPosition(mob).isPresent()) {
                beginSearch(mob, rememberedPlayer(mob), true);
            }
        }
    }

    /**
     * Changed exposes the final assimilation callback through its decision
     * event. The listener appended here runs only when the player's
     * transformation actually completes, never for mere partial progress.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAssimilationDecision(TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSourceEntity() instanceof ChangedEntity speaker)
                || speaker.level().isClientSide || !LatexSocialMemory.isSocialLatex(speaker)) {
            return;
        }
        if (VoluntaryBondTransfurService.isCompleting(speaker, victim)) {
            return;
        }

        if (LatexSocialMemory.isOrganic(speaker)) {
            LatexAssimilationDecision<?> replication = speaker.makeLatexAssimilationDecision(
                    TransfurCause.GRAB_REPLICATE, victim);
            if (replication == null) {
                event.setCanceled(true);
                return;
            }
            event.setDecision(replication.withTransfurProgress(
                    event.getDecision().transfurProgress()));
            if (LatexSocialMemory.hasOtherBondedCreature(victim, speaker)) {
                event.appendTransfurListener(newForm ->
                        celebrateOrganicBondConflict(speaker, victim));
            } else {
                event.appendTransfurListener(newForm ->
                        celebrateSuccess(speaker, victim, Cue.SUCCESS_ASSIMILATE));
            }
            return;
        }

        if (LatexSocialMemory.hasOtherBondedCreature(victim, speaker)) {
            LatexAssimilationDecision<?> absorption = speaker.makeLatexAssimilationDecision(
                    TransfurCause.GRAB_ABSORB, victim);
            if (absorption != null) {
                // Keep Changed's NPC absorption path: the player is consumed
                // and the attacking individual survives in its resulting form.
                event.setDecision(absorption);
                event.appendTransfurListener(newForm ->
                        celebrateBondConflictAbsorption(speaker, victim));
                return;
            }
        }

        Cue successCue = event.getDecision().method() == LatexAssimilationDecision.Method.ABSORPTION
                ? Cue.SUCCESS_ABSORB
                : Cue.SUCCESS_ASSIMILATE;
        if (InvoluntaryTransfurNegotiation.eligibleSource(speaker, victim)) {
            event.appendTransfurListener(newForm -> {
                ChangedEntity completed = newForm.getEntity() instanceof ChangedEntity changed
                        ? changed : speaker;
                celebrateInvoluntarySuccess(completed, victim, successCue);
            });
        } else {
            event.appendTransfurListener(
                    newForm -> celebrateSuccess(speaker, victim, successCue));
        }
    }

    @SubscribeEvent
    public static void onHunterDamaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(victim.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || player.isCreative()
                || player.isSpectator()
                || !isHuntAIEnabled(victim)
                || !isHunterKind(victim)
                || isOwnedBy(victim, player)) {
            return;
        }

        List<ChangedEntity> nearbyKin = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        victim.getBoundingBox().inflate(18.0),
                        ally -> ally.isAlive()
                                && LatexSocialMemory.isSocialLatex(ally))
                .stream()
                .filter(ally -> ally != victim
                        && LatexCreatureCombatRules.areCompatriots(victim, ally))
                .sorted(Comparator
                        .comparingInt((ChangedEntity ally) ->
                                LatexSocialRelation.sameSpecies(victim, ally) ? 0 : 1)
                        .thenComparingDouble(ally -> ally.distanceToSqr(victim)))
                .toList();
        List<ChangedEntity> responders = nearbyKin.stream()
                .filter(HuntAIEvents::isEligibleHunter)
                .filter(ally -> !isOwnedBy(ally, player))
                .filter(ally -> !CreaturePersonality.hasEstablishedRelationship(ally, player)
                        || LatexSocialMemory.hasRelationshipBetrayal(ally, player))
                .limit(6)
                .toList();
        List<ChangedEntity> bondWitnesses = nearbyKin.stream()
                .filter(ally -> LatexSocialMemory.isBonded(ally, player)
                        && (ally.hasLineOfSight(victim)
                                || ally.hasLineOfSight(player)))
                .limit(2)
                .toList();
        List<ChangedEntity> friendWitnesses = nearbyKin.stream()
                .filter(ally -> !isOwnedBy(ally, player)
                        && CreaturePersonality.hasTrustedRelationship(ally, player)
                                && (ally.hasLineOfSight(victim)
                                        || ally.hasLineOfSight(player)))
                .limit(2)
                .toList();
        if (responders.isEmpty()
                && bondWitnesses.isEmpty()
                && friendWitnesses.isEmpty()) {
            return;
        }

        for (ChangedEntity ally : responders) {
            LatexSocialMemory.clearTruce(ally, player);
            LatexSocialMemory.clearWarningGrace(ally, player);
            LatexSocialMemory.markProvoked(ally, player);
            HuntMemory.seeTarget(ally, player);
            ally.setTarget(player);
            ally.setAggressive(true);
        }

        long now = level.getGameTime();
        if (PureWhiteHiveEvents.hasActiveDialogueLock(victim, now)) {
            responders.forEach(ally ->
                    NpcDialogue.emoteOnly(ally, Cue.COMPATRIOT_DEFENSE));
            bondWitnesses.forEach(ally ->
                    NpcDialogue.emoteOnly(
                            ally,
                            Cue.BOND_WITNESS_COMPATRIOT_ATTACK));
            friendWitnesses.forEach(ally ->
                    NpcDialogue.emoteOnly(
                            ally,
                            Cue.FRIEND_WITNESS_COMPATRIOT_ATTACK));
            return;
        }
        if (victim.getPersistentData().getLong(COMPATRIOT_RALLY_UNTIL) > now) {
            responders.forEach(ally ->
                    NpcDialogue.emoteOnly(ally, Cue.COMPATRIOT_DEFENSE));
            bondWitnesses.forEach(ally ->
                    NpcDialogue.emoteOnly(
                            ally,
                            Cue.BOND_WITNESS_COMPATRIOT_ATTACK));
            friendWitnesses.forEach(ally ->
                    NpcDialogue.emoteOnly(
                            ally,
                            Cue.FRIEND_WITNESS_COMPATRIOT_ATTACK));
            return;
        }
        victim.getPersistentData().putLong(COMPATRIOT_RALLY_UNTIL, now + 100L);
        if (!bondWitnesses.isEmpty()) {
            ChangedEntity witness = bondWitnesses.get(0);
            witness.getLookControl().setLookAt(player, 30.0F, 30.0F);
            NpcDialogue.trigger(
                    witness,
                    player,
                    Cue.BOND_WITNESS_COMPATRIOT_ATTACK,
                    kinshipLabel(victim, witness));
            bondWitnesses.stream().skip(1).forEach(ally ->
                    NpcDialogue.emoteOnly(
                            ally,
                            Cue.BOND_WITNESS_COMPATRIOT_ATTACK));
        }
        if (!friendWitnesses.isEmpty()) {
            ChangedEntity witness = friendWitnesses.get(0);
            NpcDialogue.trigger(
                    witness,
                    player,
                    Cue.FRIEND_WITNESS_COMPATRIOT_ATTACK,
                    kinshipLabel(victim, witness));
        } else if (!responders.isEmpty()) {
            NpcDialogue.trigger(
                    responders.get(0),
                    player,
                    Cue.COMPATRIOT_DEFENSE,
                    kinshipLabel(victim, responders.get(0)));
        }
        responders.stream()
                .filter(ally -> friendWitnesses.isEmpty()
                        ? ally != responders.get(0)
                        : ally != friendWitnesses.get(0))
                .forEach(ally ->
                        NpcDialogue.emoteOnly(
                                ally, Cue.COMPATRIOT_DEFENSE));
    }

    @SubscribeEvent
    public static void onHunterDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity fallen)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(fallen.level() instanceof ServerLevel level)
                || !isHunterKind(fallen)) {
            return;
        }

        List<ChangedEntity> nearbyKin = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        fallen.getBoundingBox().inflate(18.0),
                        ally -> ally.isAlive()
                                && LatexSocialMemory.isSocialLatex(ally))
                .stream()
                .filter(ally -> ally != fallen
                        && LatexCreatureCombatRules.areCompatriots(fallen, ally)
                        && (ally.hasLineOfSight(fallen)
                                || ally.hasLineOfSight(player)))
                .sorted(Comparator.comparingDouble(ally -> ally.distanceToSqr(fallen)))
                .toList();
        if (nearbyKin.isEmpty()) {
            return;
        }

        List<ChangedEntity> friendWitnesses = nearbyKin.stream()
                .filter(ally -> !isOwnedBy(ally, player)
                        && CreaturePersonality.hasTrustedRelationship(ally, player))
                .limit(4)
                .toList();
        List<ChangedEntity> betrayedFriends = new ArrayList<>();
        List<ChangedEntity> warnedFriends = new ArrayList<>();
        for (ChangedEntity friend : friendWitnesses) {
            int kills = CreaturePersonality.recordWitnessedKinKill(friend, player);
            if (kills >= CreaturePersonality.witnessedKinKillLimit(friend)) {
                LatexSocialMemory.markRelationshipBetrayal(friend, player);
                HuntMemory.seeTarget(friend, player);
                friend.setTarget(player);
                friend.setAggressive(true);
                betrayedFriends.add(friend);
            } else {
                warnedFriends.add(friend);
            }
        }
        if (!betrayedFriends.isEmpty()) {
            ChangedEntity speaker = betrayedFriends.get(0);
            NpcDialogue.trigger(
                    speaker,
                    player,
                    Cue.FRIEND_KIN_KILL_BETRAYAL,
                    kinshipLabel(fallen, speaker));
            betrayedFriends.stream().skip(1).forEach(friend ->
                    NpcDialogue.emoteOnly(friend, Cue.FRIEND_KIN_KILL_BETRAYAL));
        } else if (!warnedFriends.isEmpty()) {
            ChangedEntity speaker = warnedFriends.get(0);
            NpcDialogue.trigger(
                    speaker,
                    player,
                    Cue.FRIEND_KIN_KILL_WARNING,
                    kinshipLabel(fallen, speaker));
            warnedFriends.stream().skip(1).forEach(friend ->
                    NpcDialogue.emoteOnly(friend, Cue.FRIEND_KIN_KILL_WARNING));
        }

        List<ChangedEntity> witnesses = nearbyKin.stream()
                .filter(HuntAIEvents::isEligibleHunter)
                .filter(ally -> !isOwnedBy(ally, player))
                .filter(ally -> !CreaturePersonality.hasEstablishedRelationship(ally, player)
                        || LatexSocialMemory.hasRelationshipBetrayal(ally, player))
                .limit(4)
                .toList();
        if (witnesses.isEmpty()) {
            return;
        }

        if (fallen.getPersistentData().getLong(COMPATRIOT_RALLY_UNTIL)
                > level.getGameTime()) {
            witnesses.forEach(ally ->
                    NpcDialogue.emoteOnly(ally, Cue.ALLY_FALLEN));
            return;
        }
        NpcDialogue.trigger(witnesses.get(0), player, Cue.ALLY_FALLEN);
        witnesses.stream().skip(1).forEach(ally -> NpcDialogue.emoteOnly(ally, Cue.ALLY_FALLEN));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (player.tickCount % 40 == 0) {
            socialEncounter(player);
            FirearmThreatService.observeHeldFirearm(player);
        }
        if (player.tickCount % 20 != 0 || !player.isSprinting()
                || player.getDeltaMovement().horizontalDistanceSqr() < 0.004) {
            return;
        }
        alertByNoise(player, player.position(), 12.0);
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            alertByNoise(player, Vec3.atCenterOf(event.getPos()), 18.0);
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            alertByNoise(player, Vec3.atCenterOf(event.getPos()), 10.0);
        }
    }

    @SubscribeEvent
    public static void onDoorUsed(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        var block = player.level().getBlockState(pos).getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            alertByNoise(player, Vec3.atCenterOf(pos), 9.0);
        }
    }

    @SubscribeEvent
    public static void onPlayerSound(PlayLevelSoundEvent.AtEntity event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide || event.getOriginalVolume() < 0.35F) {
            return;
        }
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(LAST_NOISE_TICK) + 12L > now) {
            return;
        }
        player.getPersistentData().putLong(LAST_NOISE_TICK, now);
        alertByNoise(player, player.position(), Math.min(20.0, 7.0 + event.getOriginalVolume() * 8.0));
    }

    public static boolean isHuntAIEnabled(ChangedEntity mob) {
        return mob.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI);
    }

    public static boolean isEligibleHunter(ChangedEntity mob) {
        if (!mob.isAlive() || mob.isNoAi() || !isHunterKind(mob)) {
            return false;
        }
        return !ChangedSynergyConfig.COMMON.respectPacifiedLatexes.get()
                || ChangedAddonCompat.pacifiedEffect()
                        .map(effect -> !mob.hasEffect(effect))
                        .orElse(true);
    }

    public static boolean canReacquire(ChangedEntity mob, ServerPlayer player, boolean confirmedTarget) {
        if (CreatureCacheGuardService.isDefendingAgainst(mob, player)) {
            return mob.isAlive() && !mob.isNoAi()
                    && player.isAlive()
                    && !player.isCreative()
                    && !player.isSpectator();
        }
        boolean organicTransformedTarget = LatexSocialMemory.isOrganic(mob)
                && ProcessTransfur.isPlayerTransfurred(player);
        boolean disguisedDarkRival =
                DarkLatexDisguise.appearsAsDarkRivalToWhite(mob, player);
        boolean playfulFusion = LatexFusionIntent.mayInitiate(mob, player);
        if (!isEligibleHunter(mob) || !player.isAlive() || player.isCreative() || player.isSpectator()
                || HypnosisQteService.shouldPreventTargeting(mob, player)
                || LatexSocialMemory.isNeutralOrganicHumanContact(mob, player)
                || !playfulFusion && mob.isAlliedTo(player)
                        && !LatexSocialMemory.isProvoked(mob, player)
                        && !organicTransformedTarget
                        && !disguisedDarkRival
                || !playfulFusion && LatexSocialMemory.shouldRemainNeutral(mob, player)) {
            return false;
        }

        if (!NpcDispositionEvents.isWithinPursuitRange(mob, player)) {
            return false;
        }
        return confirmedTarget || NpcDispositionEvents.canTarget(mob, player);
    }

    private static void tickPlayerPursuit(ChangedEntity mob, ServerPlayer player) {
        if (!canReacquire(mob, player, true)) {
            removeChaseBoost(mob);
            HuntMemory.clear(mob);
            mob.setTarget(null);
            return;
        }

        HuntState oldState = HuntMemory.getState(mob);
        boolean sameTarget = HuntMemory.targets(mob, player);
        boolean visible = mob.hasLineOfSight(player);
        int previousLostSight = HuntMemory.getLostSightTicks(mob);
        boolean playfulFusion = LatexFusionIntent.mayInitiate(mob, player);

        if (!sameTarget || oldState == HuntState.IDLE) {
            HuntMemory.seeTarget(mob, player);
            NpcDialogue.trigger(
                    mob,
                    player,
                    playfulFusion
                            ? (LatexFusionIntent.isWhiteKnightWolfPair(mob, player)
                                    ? Cue.WHITE_KNIGHT_FUSION_APPROACH
                                    : Cue.FUSION_APPROACH)
                            : acquisitionCue(mob, player));
            if (!playfulFusion) {
                shareAlert(mob, player, true);
            }
        } else if (visible) {
            HuntMemory.seeTarget(mob, player);
            if (!playfulFusion
                    && (oldState == HuntState.SEARCHING
                            || oldState == HuntState.INVESTIGATING
                            || previousLostSight >= 20)) {
                NpcDialogue.trigger(mob, player, Cue.REACQUIRED);
            }
            if (!playfulFusion
                    && HunterFaction.of(mob) == HunterFaction.WHITE
                    && mob.tickCount % 20 == 0) {
                shareAlert(mob, player, false);
            }
        }

        applyChaseBoost(mob);
        if (visible) {
            HuntMemory.setLostSightTicks(mob, 0);
            return;
        }

        int lostSight = previousLostSight + 5;
        HuntMemory.setLostSightTicks(mob, lostSight);
        int graceTicks = Math.max(1, (int)Math.round(
                ChangedSynergyConfig.COMMON.huntLoseSightSeconds.get() * 20.0D
                        * CreaturePersonality.lostSightGraceMultiplier(mob)));
        if (lostSight >= graceTicks) {
            beginSearch(mob, player, !playfulFusion);
            mob.setTarget(null);
        }
    }

    private static void beginSearch(ChangedEntity mob, ServerPlayer player, boolean announce) {
        if (!(mob.level() instanceof ServerLevel level) || HuntMemory.getPosition(mob).isEmpty()) {
            HuntMemory.clear(mob);
            return;
        }
        long until = level.getGameTime() + Math.max(1L, Math.round(
                ChangedSynergyConfig.COMMON.huntSearchSeconds.get() * 20.0D
                        * CreaturePersonality.searchDurationMultiplier(mob)));
        HuntMemory.beginSearch(mob, until);
        removeChaseBoost(mob);
        if (announce && player != null) {
            NpcDialogue.trigger(mob, player, Cue.LOST);
        }
    }

    private static void shareAlert(ChangedEntity caller, ServerPlayer target, boolean announce) {
        if (!(caller.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = ChangedSynergyConfig.COMMON.huntAlertRadius.get();
        if (radius <= 0.0) {
            return;
        }
        if (CreaturePersonality.has(caller, Trait.PROTECTIVE)) {
            radius = Math.min(48.0D, radius * 1.15D);
        }

        HunterFaction faction = HunterFaction.of(caller);
        boolean whiteHive = faction == HunterFaction.WHITE;
        if (whiteHive) {
            radius = Math.min(32.0D, radius * 1.5D);
        }
        int allyLimit = whiteHive ? 8 : 4;
        double radiusSqr = radius * radius;
        List<ChangedEntity> allies = level.getEntitiesOfClass(
                 ChangedEntity.class, caller.getBoundingBox().inflate(radius), HuntAIEvents::isEligibleHunter)
                .stream()
                .filter(ally -> ally != caller && HunterFaction.of(ally) == faction
                        && ally.distanceToSqr(caller) <= radiusSqr
                        && ally.getTarget() == null
                        && canJoinSharedAlert(ally, target))
                .sorted(Comparator
                        .comparingInt((ChangedEntity ally) ->
                                CreaturePersonality.has(ally, Trait.PROTECTIVE) ? 0 : 1)
                        .thenComparingDouble(ally -> ally.distanceToSqr(target)))
                .limit(allyLimit)
                .toList();

        Vec3 direction = target.position().subtract(caller.position());
        if (direction.horizontalDistanceSqr() < 0.001) {
            direction = new Vec3(1.0, 0.0, 0.0);
        } else {
            direction = new Vec3(direction.x, 0.0, direction.z).normalize();
        }
        Vec3 perpendicular = new Vec3(-direction.z, 0.0, direction.x);
        for (int index = 0; index < allies.size(); index++) {
            ChangedEntity ally = allies.get(index);
            long until = level.getGameTime() + Math.max(1L, Math.round(
                    ChangedSynergyConfig.COMMON.huntSearchSeconds.get() * 20.0D
                            * CreaturePersonality.searchDurationMultiplier(ally)));
            double side = index % 2 == 0 ? 1.0 : -1.0;
            double distance = 3.0 + (index / 2) * 1.5;
            Vec3 flankPoint = target.position().add(perpendicular.scale(side * distance));
            if (whiteHive) {
                HuntMemory.investigateConfirmed(ally, target, flankPoint, until);
            } else {
                HuntMemory.investigate(ally, target, flankPoint, until);
            }

            if (ally.hasLineOfSight(target) && canReacquire(ally, target, true)) {
                HuntMemory.seeTarget(ally, target);
                ally.setTarget(target);
            }
            if (announce && index == 0) {
                NpcDialogue.trigger(ally, target, Cue.ALERT);
            }
        }
    }

    private static boolean canJoinSharedAlert(ChangedEntity ally, ServerPlayer target) {
        return !LatexSocialMemory.shouldRemainNeutral(ally, target)
                && (isHostilePlayerRelation(ally, target)
                        || DarkLatexDisguise.appearsAsDarkRivalToWhite(ally, target));
    }

    private static void alertByNoise(ServerPlayer player, Vec3 position, double radius) {
        if (!(player.level() instanceof ServerLevel level)
                || player.isCreative() || player.isSpectator()
                || !level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)) {
            return;
        }

        AABB area = new AABB(position, position).inflate(radius);
        double radiusSqr = radius * radius;
        List<ChangedEntity> listeners = level.getEntitiesOfClass(
                        ChangedEntity.class, area, HuntAIEvents::isEligibleHunter)
                .stream()
                .filter(mob -> mob.distanceToSqr(position) <= radiusSqr
                        && mob.getTarget() == null
                        && HuntMemory.getState(mob) != HuntState.SEARCHING
                        && canInvestigate(mob, player))
                .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(position)))
                .limit(6)
                .toList();
        if (listeners.isEmpty()) {
            return;
        }

        for (ChangedEntity listener : listeners) {
            long until = level.getGameTime() + Math.max(1L, Math.round(
                    ChangedSynergyConfig.COMMON.huntSearchSeconds.get() * 20.0D
                            * CreaturePersonality.searchDurationMultiplier(listener)));
            HuntMemory.investigate(listener, player, position, until);
        }
        NpcDialogue.trigger(listeners.get(0), player, Cue.HEARD);
    }

    /** Prevents a TACZ shot from being counted again as an unrelated generic player sound. */
    public static void markFirearmNoiseHandled(ServerPlayer player) {
        player.getPersistentData().putLong(
                LAST_NOISE_TICK, player.level().getGameTime());
    }

    private static boolean canInvestigate(ChangedEntity mob, ServerPlayer player) {
        return isHostilePlayerRelation(mob, player);
    }

    /** Player noises matter only to creatures that would currently treat that player as prey or a rival. */
    public static boolean isHostilePlayerRelation(ChangedEntity mob, ServerPlayer player) {
        if (LatexSocialMemory.shouldRemainNeutral(mob, player)) {
            return false;
        }
        if (LatexSocialMemory.isNeutralOrganicHumanContact(mob, player)) {
            return false;
        }
        if (LatexSocialMemory.isProvoked(mob, player)
                || LatexSocialMemory.isOrganic(mob)
                        && ProcessTransfur.isPlayerTransfurred(player)) {
            return true;
        }
        LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
        return relation == LatexSocialRelation.HUMAN
                || relation == LatexSocialRelation.RIVAL;
    }

    private static boolean isHunterKind(ChangedEntity mob) {
        return (mob.getType().is(LATEXES) || mob.getType().is(ORGANIC_LATEX))
                && !mob.getType().is(BENIGN_LATEXES)
                && !mob.getType().is(HUNT_AI_EXCLUDED)
                && LatexSocialMemory.petOwnerUuid(mob).isEmpty()
                && !LatexSocialMemory.hasActiveBond(mob)
                && !(mob instanceof TamableLatexEntity tamable && tamable.isTame());
    }

    private static boolean isOwnedBy(
            ChangedEntity mob,
            ServerPlayer player) {
        return LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)
                || mob instanceof TamableLatexEntity tamable
                        && tamable.isTame()
                        && player.getUUID().equals(tamable.getOwnerUUID());
    }

    private static Component kinshipLabel(
            ChangedEntity victim,
            ChangedEntity witness) {
        return Component.translatable(
                LatexSocialRelation.sameSpecies(victim, witness)
                        ? "relation.changed_synergy.same_species_short"
                        : "relation.changed_synergy.same_category_short");
    }

    private static Cue acquisitionCue(ChangedEntity mob, ServerPlayer player) {
        if (DarkLatexDisguise.foolsDarkObserver(mob, player)) {
            return Cue.DISGUISE_DARK_ACCEPTED;
        }
        if (DarkLatexDisguise.appearsAsDarkRivalToWhite(mob, player)) {
            return Cue.DISGUISE_WHITE_RIVAL;
        }
        if (LatexSocialMemory.isProvoked(mob, player)) {
            return Cue.REACQUIRED;
        }
        if (LatexSocialMemory.isBondedOwnerInOtherForm(mob, player)) {
            return Cue.BOND_NEW_FORM_WELCOME;
        }
        LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
        boolean needsVouch = !FactionReputation.isRecognized(mob, player);
        Cue relationshipCue = switch (relation) {
            case FORMER_BONDED -> Cue.FORMER_BOND_WELCOME;
            case FORMER_RESPECTED -> needsVouch
                    ? Cue.FORMER_RESPECT_WELCOME : null;
            case FRIEND_RESPECTED -> needsVouch
                    ? Cue.FRIEND_RESPECT_WELCOME : null;
            case RIVAL -> Cue.MEET_RIVAL;
            default -> null;
        };
        if (relationshipCue != null) {
            return relationshipCue;
        }
        Cue reputationCue = reputationEncounterCue(mob, player);
        if (reputationCue != null) {
            return reputationCue;
        }
        return switch (relation) {
            case SAME_SPECIES -> Cue.MEET_SPECIES;
            case SAME_CATEGORY -> Cue.MEET_CATEGORY;
            case FRIENDLY_OTHER -> Cue.MEET_FRIENDLY;
            case OUTSIDER -> Cue.MEET_OUTSIDER;
            case HUMAN -> Cue.SPOTTED;
            case FORMER_BONDED, FORMER_RESPECTED,
                    FRIEND_RESPECTED, RIVAL -> throw new IllegalStateException(
                            "Relationship cue should have returned above");
        };
    }

    /** Returns null while the faction still regards the player as a near-stranger. */
    public static Cue reputationEncounterCue(
            ChangedEntity mob,
            ServerPlayer player) {
        return switch (FactionReputation.standing(mob, player)) {
            case HOSTILE -> Cue.REPUTATION_HOSTILE;
            case DISTRUSTED -> Cue.REPUTATION_DISTRUSTED;
            case RECOGNIZED -> Cue.REPUTATION_RECOGNIZED;
            case RESPECTED -> Cue.REPUTATION_RESPECTED;
            case ALLIED -> Cue.REPUTATION_ALLIED;
            case NEUTRAL -> null;
        };
    }

    private static void socialEncounter(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || !player.isAlive() || player.isSpectator()) {
            return;
        }

        long now = level.getGameTime();
        List<ChangedEntity> candidates = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(14.0),
                        mob -> mob.isAlive() && LatexSocialMemory.isSocialLatex(mob))
                .stream()
                .filter(mob -> mob.getTarget() != player && mob.hasLineOfSight(player)
                        && mob.getPersistentData().getLong(NEXT_SOCIAL_TICK) <= now
                        && !LatexSocialMemory.isBonded(mob, player)
                        && (LatexSocialRelation.between(mob, player)
                                        != LatexSocialRelation.HUMAN
                                || reputationEncounterCue(mob, player) != null))
                .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        ChangedEntity speaker = candidates.get(0);
        long delay = 400L + speaker.getRandom().nextInt(201);
        speaker.getPersistentData().putLong(NEXT_SOCIAL_TICK, now + delay);
        NpcDialogue.trigger(speaker, player, acquisitionCue(speaker, player));
    }

    private static void celebrateSuccess(ChangedEntity speaker, ServerPlayer player, Cue cue) {
        if (!(speaker.level() instanceof ServerLevel level) || !speaker.isAlive()) {
            return;
        }

        // A combat or environmental transfur never creates a bond. Native
        // absorption can consume an already bonded source, so remove only that
        // stale reference while leaving replication relationships untouched.
        if (cue == Cue.SUCCESS_ABSORB) {
            LatexSocialMemory.unregisterBond(speaker, player);
        }
        LatexSocialEvents.calmTowards(speaker, player);
        removeChaseBoost(speaker);
        Cue welcomeCue = HypnosisProfile.activelyUsesHypnosis(speaker)
                ? Cue.HYPNOSIS_WELCOME
                : cue;
        NpcDialogue.trigger(speaker, player, welcomeCue);

        long now = level.getGameTime();
        level.getEntitiesOfClass(
                ChangedEntity.class, speaker.getBoundingBox().inflate(16.0), HuntAIEvents::isEligibleHunter)
                .stream()
                .filter(ally -> ally != speaker
                        && LatexSocialRelation.between(ally, player).isFriendlyTransformed())
                .sorted(Comparator.comparingDouble(ally -> ally.distanceToSqr(player)))
                .limit(4)
                .forEach(ally -> {
                    ally.getPersistentData().putLong(NEXT_SOCIAL_TICK, now + 160L);
                    NpcDialogue.emoteOnly(ally, Cue.MEET_ALLY);
                });
    }

    private static void celebrateInvoluntarySuccess(
            ChangedEntity speaker,
            ServerPlayer player,
            Cue cue) {
        if (!(speaker.level() instanceof ServerLevel) || !speaker.isAlive()) {
            return;
        }
        LatexSocialMemory.unregisterBond(speaker, player);
        LatexSocialEvents.calmTowards(speaker, player);
        removeChaseBoost(speaker);
        NpcDialogue.trigger(speaker, player, cue);
    }

    /** Called by both ordinary assimilation and Addon's transformed-player hold. */
    public static void celebrateBondConflictAbsorption(
            ChangedEntity speaker,
            ServerPlayer player) {
        if (!speaker.isAlive()) {
            return;
        }

        // Deliberately do not add a bond to the second creature.  Its new kinship
        // can still make it neutral, while ownership remains with the first pet.
        LatexSocialMemory.unregisterBond(speaker, player);
        LatexSocialEvents.calmTowards(speaker, player);
        removeChaseBoost(speaker);
        NpcDialogue.trigger(speaker, player, Cue.BOND_RIVAL_ABSORPTION);

        double dialogueRange = ChangedSynergyConfig.COMMON.npcDialogueRange.get();
        for (ChangedEntity bonded : LatexSocialMemory.loadedBondedCreatures(player)) {
            if (bonded == speaker || bonded.level() != player.level()
                    || bonded.distanceToSqr(player) > dialogueRange * dialogueRange) {
                continue;
            }
            if (LatexSocialMemory.claimJealousFormGreeting(bonded, player)) {
                NpcDialogue.trigger(bonded, player, Cue.BOND_JEALOUS_NEW_FORM);
            }
        }
    }

    /** Organic creatures keep their body and replicate a form without creating a second bond. */
    public static void celebrateOrganicBondConflict(
            ChangedEntity speaker,
            ServerPlayer player) {
        if (!speaker.isAlive()) {
            return;
        }

        LatexSocialMemory.unregisterBond(speaker, player);
        LatexSocialMemory.beginTruce(speaker, player, 200L);
        LatexSocialEvents.calmTowards(speaker, player);
        removeChaseBoost(speaker);
        NpcDialogue.trigger(speaker, player, Cue.ORGANIC_BOND_CONFLICT_ASSIMILATION);

        double dialogueRange = ChangedSynergyConfig.COMMON.npcDialogueRange.get();
        for (ChangedEntity bonded : LatexSocialMemory.loadedBondedCreatures(player)) {
            if (bonded == speaker || bonded.level() != player.level()
                    || bonded.distanceToSqr(player) > dialogueRange * dialogueRange) {
                continue;
            }
            if (LatexSocialMemory.claimJealousFormGreeting(bonded, player)) {
                NpcDialogue.trigger(bonded, player, Cue.BOND_JEALOUS_NEW_FORM);
            }
        }
    }

    private static ServerPlayer rememberedPlayer(ChangedEntity mob) {
        return mob.level() instanceof ServerLevel level
                ? HuntMemory.getTargetPlayer(mob, level).orElse(null)
                : null;
    }

    private static void applyChaseBoost(ChangedEntity mob) {
        // Search and alert behavior remain active, but pursuit now uses the
        // entity's own movement attribute and original melee-goal speed.
        removeChaseBoost(mob);
    }

    private static void removeChaseBoost(ChangedEntity mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(CHASE_SPEED_MODIFIER) != null) {
            speed.removeModifier(CHASE_SPEED_MODIFIER);
            // Clear sprinting only while migrating an entity that still carries
            // the old Synergy chase modifier. Native sprint state is otherwise untouched.
            mob.setSprinting(false);
        }
    }

    private static TagKey<EntityType<?>> tag(String namespace, String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
