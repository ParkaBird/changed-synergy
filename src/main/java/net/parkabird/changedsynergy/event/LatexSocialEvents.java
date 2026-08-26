package net.parkabird.changedsynergy.event;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexWolfFemale;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexWolfMale;
import net.ltxprogrammer.changed.entity.beast.DarkLatexWolfPup;
import net.ltxprogrammer.changed.block.CardboardBoxTall;
import net.ltxprogrammer.changed.block.entity.CardboardBoxTallBlockEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.ai.BondedEmergencySuitGoal;
import net.parkabird.changedsynergy.ai.BondedCreatureLifecycle;
import net.parkabird.changedsynergy.ai.BondedFollowGoal;
import net.parkabird.changedsynergy.ai.BondedPetSettings;
import net.parkabird.changedsynergy.ai.BondedOwnerDefenseGoal;
import net.parkabird.changedsynergy.ai.BondedOwnerDefenseGoal.Mode;
import net.parkabird.changedsynergy.ai.BondedOwnerGrabRescueGoal;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.FallbackBondedTargetGoal;
import net.parkabird.changedsynergy.ai.FallbackBondedWorkGoal;
import net.parkabird.changedsynergy.ai.CompanionWorkDialogue;
import net.parkabird.changedsynergy.ai.CacheGuardReturnGoal;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.CreaturePersonality.RelationshipProgress;
import net.parkabird.changedsynergy.ai.CreatureIdentity;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureMorphAliasData;
import net.parkabird.changedsynergy.ai.CreatureMorphContinuity;
import net.parkabird.changedsynergy.ai.CreatureRoleService;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.ai.PureWhiteReformationGoal;
import net.parkabird.changedsynergy.ai.CreatureRoutineGoal;
import net.parkabird.changedsynergy.ai.CreatureComfortGoal;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.DarkLatexDisguise;
import net.parkabird.changedsynergy.ai.DarkLatexDisguise.Observation;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.FactionHostilityGrace;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.ai.HumanIntent;
import net.parkabird.changedsynergy.ai.HuntMemory;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexSocialMemory.FriendlyHitStage;
import net.parkabird.changedsynergy.ai.LatexSocialRelation;
import net.parkabird.changedsynergy.ai.OrganicCombatRules;
import net.parkabird.changedsynergy.ai.PoliteHumanApproachGoal;
import net.parkabird.changedsynergy.ai.PoliteHumanInteraction;
import net.parkabird.changedsynergy.ai.PlayerRelationshipSettings;
import net.parkabird.changedsynergy.ai.PatAnimationService;
import net.parkabird.changedsynergy.ai.ProvisionerGiftService;
import net.parkabird.changedsynergy.ai.ProvisionerFishingFocus;
import net.parkabird.changedsynergy.ai.RelationshipFavorService;
import net.parkabird.changedsynergy.ai.RelationshipFavorService.Result;
import net.parkabird.changedsynergy.ai.SocialAudienceGoal;
import net.parkabird.changedsynergy.ai.SocialFollowGoal;
import net.parkabird.changedsynergy.ai.SocialFriendDefenseGoal;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.dialogue.NpcEmoteState;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.mixin.DarkLatexWolfPupAgeAccessor;
import net.parkabird.changedsynergy.world.inventory.BondedLatexMenu;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexSocialEvents {
    private static final String NEXT_PAT_PLAYER = "ChangedSynergyNextPatPlayer";
    private static final String NEXT_RECEIVE_PAT = "ChangedSynergyNextReceivePat";
    private static final long BASE_PAT_TRUCE = 160L;
    private static final long ACTIVE_PAT_MIN_COOLDOWN = 900L;
    private static final int ACTIVE_PAT_RANDOM_COOLDOWN = 901;
    private static final long RETURN_PAT_DELAY = 240L;
    private static final long PLAYER_PAT_RECEPTION_COOLDOWN = 300L;
    private static final String NEXT_FRIENDLY_FIRE_DENIAL =
            "ChangedSynergyNextFriendlyFireDenial";
    private static final String BOX_OPEN_COUNT =
            "ChangedSynergyBoxOpenCount";
    private static final long FRIENDLY_HIT_MERGE_TICKS = 10L;
    private static final Map<FriendlyWarningKey, PendingFriendlyWarning> PENDING_FRIENDLY_WARNINGS = new HashMap<>();
    private static final Map<FriendlyWarningKey, PendingDamageReaction> PENDING_DAMAGE_REACTIONS =
            new HashMap<>();
    private static final Map<UUID, PendingBondDeath> PENDING_BOND_DEATHS = new HashMap<>();
    private static final Map<UUID, UUID> RECENT_WRAPPED_OWNER_DEATHS = new HashMap<>();

    private LatexSocialEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ChangedEntity mob)
                || !LatexSocialMemory.isSocialLatex(mob)
                || !CreatureSocialProfile.allowsSynergySystems(mob)) {
            return;
        }
        CreaturePersonality.ensure(mob);
        CreatureIdentity.ensure(mob);
        CreatureLifeMemory.ensure(mob);
        CreatureCommunityData.bind(mob);
        NpcEmoteState.reset(mob);
        PoliteHumanInteraction.resetTransientState(mob);
        LatexSocialMemory.applyPendingManualReleases(mob);
        boolean personalSocial =
                CreatureSocialProfile.allowsPersonalRelationship(mob);
        if (!LatexSocialMemory.bondedPlayerUuids(mob).isEmpty()) {
            BondedCreatureLifecycle.track(mob);
            LatexSocialMemory.suppressBondedAvoidanceGoals(mob);
        }
        long now = mob.level().getGameTime();
        if (personalSocial
                && mob.getPersistentData().getLong(NEXT_PAT_PLAYER) <= now) {
            scheduleNextActivePat(mob, now);
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof BondedFollowGoal)) {
            // Highest ordinary MOVE priority. Only forced owner rescue and
            // emergency wrapping are allowed to outrank following.
            mob.goalSelector.addGoal(BondedFollowGoal.PRIORITY, new BondedFollowGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal()
                        instanceof FallbackBondedWorkGoal)) {
            mob.goalSelector.addGoal(
                    FallbackBondedWorkGoal.PRIORITY,
                    new FallbackBondedWorkGoal(mob));
        }
        if (personalSocial
                && mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal()
                        instanceof PoliteHumanApproachGoal)) {
            mob.goalSelector.addGoal(
                    PoliteHumanApproachGoal.PRIORITY,
                    new PoliteHumanApproachGoal(mob));
        }
        if (personalSocial
                && mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof SocialFollowGoal)) {
            mob.goalSelector.addGoal(
                    SocialFollowGoal.PRIORITY,
                    new SocialFollowGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof SocialAudienceGoal)) {
            mob.goalSelector.addGoal(
                    SocialAudienceGoal.PRIORITY,
                    new SocialAudienceGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof CreatureRoutineGoal)) {
            mob.goalSelector.addGoal(
                    CreatureRoutineGoal.PRIORITY,
                    new CreatureRoutineGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof CreatureComfortGoal)) {
            mob.goalSelector.addGoal(
                    CreatureComfortGoal.PRIORITY,
                    new CreatureComfortGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal()
                        instanceof CacheGuardReturnGoal)) {
            mob.goalSelector.addGoal(
                    CacheGuardReturnGoal.PRIORITY,
                    new CacheGuardReturnGoal(mob));
        }
        if (HunterFaction.of(mob) == HunterFaction.WHITE
                && mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal()
                        instanceof PureWhiteReformationGoal)) {
            mob.goalSelector.addGoal(
                    PureWhiteReformationGoal.PRIORITY,
                    new PureWhiteReformationGoal(mob));
        }
        if (!LatexSocialMemory.isOrganic(mob)
                && mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof BondedEmergencySuitGoal)) {
            mob.goalSelector.addGoal(-3, new BondedEmergencySuitGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal()
                        instanceof BondedOwnerGrabRescueGoal)) {
            // A live hostile grab must outrank both combat and emergency-suit goals.
            mob.goalSelector.addGoal(-4, new BondedOwnerGrabRescueGoal(mob));
        }
        if (!LatexSocialMemory.usesNativePetMenu(mob)
                && mob.targetSelector.getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal() instanceof BondedOwnerDefenseGoal)) {
            mob.targetSelector.addGoal(1, new BondedOwnerDefenseGoal(mob, Mode.OWNER_HURT_BY));
            mob.targetSelector.addGoal(2, new BondedOwnerDefenseGoal(mob, Mode.OWNER_HURT_TARGET));
        }
        if (mob.targetSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal()
                        instanceof FallbackBondedTargetGoal)) {
            mob.targetSelector.addGoal(4, new FallbackBondedTargetGoal(mob));
        }
        if (mob.targetSelector.getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal()
                                instanceof SocialFriendDefenseGoal)) {
            mob.targetSelector.addGoal(3, new SocialFriendDefenseGoal(mob));
        }

        ServerPlayer owner = LatexSocialMemory.getPetOwner(mob);
        if (owner != null) {
            if (LatexSocialMemory.isBonded(mob, owner)) {
                LatexSocialMemory.addBond(mob, owner);
            } else {
                LatexSocialMemory.promoteNativePet(mob, owner);
            }
        }
    }

    /** A bonded white latex wolf must never be consumed by Changed's knight-fusion recipe. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBondedWhiteWolfFusion(
            TransfurEvents.ChangedEntityFusionWithChangedEntityDecisionEvent event) {
        if (isProtectedWhiteLatexWolf(event.getSourceEntity())
                || isProtectedWhiteLatexWolf(event.getTargetEntity())) {
            event.setCanceled(true);
        }
    }

    /** Changed represents NPC fusion as an entity replacement; retain the initiating individual. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangedCreatureFusion(
            TransfurEvents.ChangedEntityFusionWithChangedEntityEvent event) {
        if (!(event.getSourceEntity() instanceof ChangedEntity previous)) {
            return;
        }
        if (event.getFusionEntity().getEntity() instanceof ChangedEntity replacement) {
            CreatureMorphContinuity.transfer(previous, replacement);
            return;
        }
        var previousPlayerForm = event.getAbstractedTargetEntity().getSelfVariant();
        if (event.getFusionEntity().getEntity() instanceof ServerPlayer player
                && LatexFusionIntent.mayCapture(previous, previousPlayerForm)) {
            if (LatexFusionIntent.isWhiteKnight(previous)
                    && LatexFusionIntent.isOrdinaryWhiteLatexWolf(
                            previousPlayerForm)) {
                NpcDialogue.trigger(
                        previous, player, Cue.WHITE_KNIGHT_FUSION_COMPLETE);
            } else {
                NpcDialogue.trigger(previous, player, Cue.FUSION_COMPLETE);
            }
            InvoluntaryTransfurNegotiation.completeFusionCapture(
                    previous,
                    player,
                    event.getFusionEntity(),
                    previousPlayerForm);
        }
    }

    /** The same replacement path is used when a creature absorbs an ordinary/undead mob. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangedCreatureMobFusion(
            TransfurEvents.ChangedEntityFusionWithMobEvent event) {
        if (event.getSourceEntity() instanceof ChangedEntity previous
                && event.getFusionEntity().getEntity() instanceof ChangedEntity replacement) {
            CreatureMorphContinuity.transfer(previous, replacement);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCreatureDeathClearEmote(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob
                && !mob.level().isClientSide
                && LatexSocialMemory.isSocialLatex(mob)) {
            InvoluntaryTransfurNegotiation.sourceDied(mob);
            CreatureSettlementService.dropCargo(mob);
            NpcEmoteState.clear(mob);
            PENDING_FRIENDLY_WARNINGS.keySet().removeIf(
                    key -> key.mobUuid().equals(mob.getUUID()));
            PENDING_DAMAGE_REACTIONS.keySet().removeIf(
                    key -> key.mobUuid().equals(mob.getUUID()));
        }
    }

    /** Marks a final-damage transaction before any ordinary damage reaction can speak. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotentiallyLethalCreatureDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob
                && !mob.level().isClientSide
                && LatexSocialMemory.isSocialLatex(mob)
                && event.getAmount() > 0.0F
                && event.getAmount() >= mob.getHealth()) {
            NpcDialogue.suppressForCurrentTick(mob);
            PENDING_FRIENDLY_WARNINGS.keySet().removeIf(
                    key -> key.mobUuid().equals(mob.getUUID()));
            PENDING_DAMAGE_REACTIONS.keySet().removeIf(
                    key -> key.mobUuid().equals(mob.getUUID()));
        }
    }

    /** The agreed release hold cannot be interrupted by damage to either participant. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNegotiatedReleaseAttacked(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide
                && InvoluntaryTransfurNegotiation.isReleaseParticipant(
                        event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Also blocks damage injected after Forge's initial attack stage. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNegotiatedReleaseHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide
                && InvoluntaryTransfurNegotiation.isReleaseParticipant(
                        event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNegotiatedReleaseDamage(LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide
                && InvoluntaryTransfurNegotiation.isReleaseParticipant(
                        event.getEntity())) {
            event.setAmount(0.0F);
        }
    }

    /**
     * A companion used as a suit no longer exposes a second damageable body.
     * Hits on that body are resolved against the host and never reach the
     * Changed entity, so the native grab code cannot drop the suit on hurt.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSuitedCreatureAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity pet)
                || pet.level().isClientSide
                || event.getAmount() <= 0.0F) {
            return;
        }
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner == null || !BondedSuitService.isSuitingOwner(pet, owner)) {
            return;
        }

        event.setCanceled(true);
        pet.setLastHurtByMob(null);
        pet.setLastHurtByPlayer(null);

        var responsible = event.getSource().getEntity();
        if (responsible instanceof Mob attacker
                && LatexCreatureCombatRules.mustRejectTarget(attacker, pet)) {
            LatexCreatureCombatRules.disengage(attacker, pet);
            return;
        }
        if (responsible == owner
                || event.getSource().getDirectEntity() == owner
                || responsible == pet
                || responsible instanceof ChangedEntity ally
                        && LatexSocialMemory.hasSamePetOwner(pet, ally)) {
            calmTowards(pet, owner);
            return;
        }

        // Re-entering LivingEntity#hurt for the player preserves armor,
        // resistance, shields, invulnerability frames and ordinary death.
        if (owner.isAlive()) {
            owner.hurt(event.getSource(), event.getAmount());
        }
    }

    /**
     * Exact species and same-category creatures do not fight each other. A
     * monster also stops before finishing a bonded creature that is already at
     * its low-health safety threshold.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCreatureAttacksCreature(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity target)
                || !(event.getSource().getEntity() instanceof Mob attacker)
                || target.level().isClientSide
                || !LatexCreatureCombatRules.mustRejectTarget(attacker, target)) {
            return;
        }
        event.setCanceled(true);
        LatexCreatureCombatRules.disengage(attacker, target);
    }

    /** Keeps village civilians and Changed creatures out of each other's combat loops. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVillagePeaceAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Mob attacker)
                || target.level().isClientSide
                || !LatexCreatureCombatRules.mustRejectVillageTarget(
                        attacker, target)) {
            return;
        }
        event.setCanceled(true);
        LatexCreatureCombatRules.disengage(attacker, target);
    }

    /** Bonded creatures and native pets cannot be hurt by their own player. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBondedCreatureAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || mob.level().isClientSide) {
            return;
        }
        boolean protectedOwner = LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)
                || mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame()
                        && player.getUUID().equals(nativePet.getOwnerUUID());
        boolean protectedFriend =
                CreaturePersonality.hasEstablishedRelationship(mob, player)
                        && !LatexSocialMemory.hasRelationshipBetrayal(mob, player);
        boolean filtered = switch (PlayerRelationshipSettings.damageFilter(player)) {
            case FRIENDS -> protectedFriend;
            case ALL -> true;
            case NONE -> false;
        };
        if (!protectedOwner && !filtered) {
            return;
        }

        event.setCanceled(true);
        var grabbedTarget = ChangedAddonCompat.socialCombatGrabTarget(mob, player);
        if (grabbedTarget.isPresent()) {
            grabbedTarget.get().hurt(event.getSource(), event.getAmount());
            return;
        }
        calmTowards(mob, player);
        long now = mob.level().getGameTime();
        if (mob.getPersistentData().getLong(NEXT_FRIENDLY_FIRE_DENIAL) <= now) {
            mob.getPersistentData().putLong(NEXT_FRIENDLY_FIRE_DENIAL, now + 30L);
            NpcDialogue.emoteOnly(mob, Emote.DENY);
        }
    }

    /** Protects neutral players and switches low-health organic combat to grapple-only. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSocialCreatureAttacksPlayer(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(mob)) {
            return;
        }
        if (CreatureCacheGuardService.isDefendingAgainst(mob, player)) {
            return;
        }
        if (LatexFusionIntent.mayInitiate(mob, player)) {
            return;
        }

        boolean protectedBySocialRules = LatexSocialMemory.shouldRemainNeutral(mob, player);
        boolean neutralOrganicHuman = LatexSocialMemory.isNeutralOrganicHumanContact(mob, player);
        boolean lowHealthOrganicSwipe = OrganicCombatRules.isOrdinaryMelee(
                        event.getSource(), mob)
                && OrganicCombatRules.shouldUseOnlyGrapple(mob, player);
        if (!protectedBySocialRules && !neutralOrganicHuman && !lowHealthOrganicSwipe) {
            LatexSocialMemory.refreshProvocation(mob, player);
            return;
        }
        event.setCanceled(true);
        // A low-health hostile target must remain selected so the grapple goal
        // can take over. Only genuinely protected contacts are calmed here.
        if (protectedBySocialRules || neutralOrganicHuman) {
            calmTowards(mob, player);
        }
    }

    /** Supplies the native swipe pipeline that Addon omits from non-latex organics. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNonLatexOrganicSwipe(LivingAttackEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(mob)
                || !LatexSocialMemory.isOrganic(mob)
                || !OrganicCombatRules.isOrdinaryMelee(event.getSource(), mob)
                || OrganicCombatRules.usesNativeLatexSwipe(mob)
                || OrganicCombatRules.isApplyingCombinedSwipe()
                || LatexSocialMemory.isSecondaryGrabActive(mob, player)) {
            return;
        }

        event.setCanceled(true);
        OrganicCombatRules.applyCombinedNonLatexSwipe(mob, player);
    }

    /** Caps fallback melee damage when an organic swipe cannot apply transfur progress. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOrganicSwipeHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(mob)
                || !LatexSocialMemory.isOrganic(mob)
                || !OrganicCombatRules.isOrdinaryMelee(event.getSource(), mob)
                || LatexSocialMemory.isSecondaryGrabActive(mob, player)) {
            return;
        }

        boolean defendingCache =
                CreatureCacheGuardService.isDefendingAgainst(mob, player);
        if (!defendingCache
                && (LatexSocialMemory.shouldRemainNeutral(mob, player)
                        || LatexSocialMemory.isNeutralOrganicHumanContact(mob, player))
                || OrganicCombatRules.shouldUseOnlyGrapple(mob, player)) {
            event.setCanceled(true);
            return;
        }

        float limitedDamage = OrganicCombatRules.limitSwipeDamage(player, event.getAmount());
        if (limitedDamage <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(limitedDamage);
        }
    }

    /** Adds a small physical wound before an ordinary organic swipe advances transfur. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOrganicSwipeAssimilationDamage(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSourceEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(mob)
                || !LatexSocialMemory.isOrganic(mob)
                || !OrganicCombatRules.usesNativeLatexSwipe(mob)
                || LatexSocialMemory.isSecondaryGrabActive(mob, player)
                || OrganicCombatRules.shouldUseOnlyGrapple(mob, player)) {
            return;
        }
        if (!CreatureCacheGuardService.isDefendingAgainst(mob, player)
                && (LatexSocialMemory.shouldRemainNeutral(mob, player)
                        || LatexSocialMemory.isNeutralOrganicHumanContact(mob, player))) {
            return;
        }
        OrganicCombatRules.applySwipeDamage(mob, player);
    }

    /** Captures the final source and method after all combat rules have edited the decision. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNegotiableTransfurDecision(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        InvoluntaryTransfurNegotiation.observeDecision(event);
    }

    /** Stop target assignment before close-range attack and grab goals can run. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onSocialTargetChange(LivingChangeTargetEvent event) {
        LivingEntity proposedTarget = event.getNewTarget();
        if (event.getEntity() instanceof Mob attacker
                && proposedTarget != null
                && !attacker.level().isClientSide
                && LatexCreatureCombatRules.mustRejectVillageTarget(
                        attacker, proposedTarget)) {
            event.setCanceled(true);
            LatexCreatureCombatRules.disengage(attacker, proposedTarget);
        } else if (event.getEntity() instanceof Mob attacker
                && event.getNewTarget() instanceof ChangedEntity target
                && !attacker.level().isClientSide
                && LatexCreatureCombatRules.mustRejectTarget(attacker, target)) {
            event.setCanceled(true);
            LatexCreatureCombatRules.disengage(attacker, target);
        } else if (event.getEntity() instanceof ChangedEntity mob
                && event.getNewTarget() instanceof ServerPlayer player
                && !mob.level().isClientSide
                && LatexSocialMemory.isSocialLatex(mob)
                && !LatexSocialMemory.isPetDefenseAuthorized(mob, player)
                && NpcDispositionEvents.mustRejectTarget(mob, player)) {
            event.setCanceled(true);
            LatexSocialMemory.clearRevengeMemoryToward(mob, player);
        }
    }

    /**
     * A monster hit may bring a bonded creature down to the safety threshold,
     * but never below it. Reaching that threshold clears both sides' combat
     * memory so the attacker does not immediately resume.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMonsterDamagesBondedCreature(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity target)
                || !(event.getSource().getEntity() instanceof Mob attacker)
                || target.level().isClientSide
                || event.getAmount() <= 0.0F) {
            return;
        }
        if (attacker instanceof ChangedEntity changed
                && LatexCreatureCombatRules.areCompatriots(changed, target)) {
            LatexCreatureCombatRules.disengage(attacker, target);
            event.setAmount(0.0F);
            return;
        }
        event.setAmount(LatexCreatureCombatRules.limitDamageToBond(
                target, attacker, event.getAmount()));
    }

    /** Bonded pets and their owner are on the same side, including friendly-fire rules. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBondedPetAttacks(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof ChangedEntity pet)
                || pet.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(pet)) {
            return;
        }

        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        boolean sameSide = victim == owner
                || victim instanceof Player player && !owner.canHarmPlayer(player)
                || victim instanceof ChangedEntity other
                        && LatexSocialMemory.hasSamePetOwner(pet, other);
        if (sameSide) {
            event.setCanceled(true);
            pet.setTarget(null);
        }
    }

    /** Opens either the owner wheel or an acquaintance's dedicated social wheel. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBondedPetInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ChangedEntity creature)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (InvoluntaryTransfurNegotiation.canNegotiate(player, creature)) {
            calmTowards(creature, player);
            // The claim now lives beside the ordinary social actions.  This
            // keeps a peaceful assimilator's new friendship usable while the
            // player decides whether to ask for their human form back.
            openSocialMenu(player, creature);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        long negotiationCooldown = InvoluntaryTransfurNegotiation
                .negotiationCooldownTicks(player);
        if (negotiationCooldown > 0L
                && InvoluntaryTransfurNegotiation
                        .isNegotiationSourceFor(player, creature)) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.negotiation.cooldown",
                    Math.max(1L, (negotiationCooldown + 19L) / 20L)), true);
            // The lockout belongs to negotiation, not to the relationship.
            // Peaceful assimilators remain available for ordinary social
            // interaction while the failed conversation cools down.
            if (CreaturePersonality.hasTrustedRelationship(
                    creature, player)) {
                calmTowards(creature, player);
                openSocialMenu(player, creature);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // This is Changed's native pet interaction, not a Synergy gift. Keep
        // the explicit bypass in addition to the juvenile profile so a tag
        // override from another datapack cannot steal the orange interaction.
        if (creature instanceof DarkLatexWolfPup pup
                && !pup.isTame()
                && RelationshipFavorService.isOrange(
                        player.getItemInHand(event.getHand()))) {
            return;
        }

        boolean owner = LatexSocialMemory.isPetOwner(creature, player);
        boolean socialInteractions = ChangedSynergyGameRules.enabled(
                player.level(), owner
                        ? ChangedSynergyGameRules.BOND_SYSTEM
                        : ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
        boolean emptyHand = player.getItemInHand(event.getHand()).isEmpty();
        boolean openingCentaurConfig = player.isShiftKeyDown()
                && CentaurMountService.isCentaur(creature)
                && (emptyHand
                        || player.getItemInHand(event.getHand()).is(Items.SADDLE)
                        || player.getItemInHand(event.getHand()).is(Items.CHEST));
        if (openingCentaurConfig) {
            if (!CentaurMountService.hasAllowedRelationship(creature, player)) {
                player.displayClientMessage(
                        Component.translatable(
                                "message.changed_synergy.centaur_mount.requires_close"),
                        true);
            } else if (!CentaurMountService.isDefaultState(creature)) {
                player.displayClientMessage(
                        Component.translatable(
                                "message.changed_synergy.centaur_mount.busy"),
                        true);
            } else {
                calmTowards(creature, player);
                CentaurMountService.openConfiguration(player, creature);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        LatexSocialRelation foodRelation = LatexSocialRelation.between(creature, player);
        boolean lowReputationFoodOffering = FactionReputation.isDistrusted(creature, player)
                && foodRelation != LatexSocialRelation.RIVAL
                && !hasPersonalFactionProtection(creature, player, foodRelation);
        Result foodResult = socialInteractions
                ? RelationshipFavorService.offerHeldFood(creature, player)
                : Result.NOT_APPLICABLE;
        if (foodResult != Result.NOT_APPLICABLE) {
            if (foodResult.isAccepted()) {
                calmTowards(creature, player);
                creature.getNavigation().stop();
                creature.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
            Cue foodCue = lowReputationFoodOffering && foodResult.isAccepted()
                    ? foodResult.isDedicatedDiet()
                            ? Cue.LOW_REPUTATION_DIET_FOOD
                            : Cue.LOW_REPUTATION_FOOD
                    : switch (foodResult) {
                        case ESTABLISHED -> Cue.RELATIONSHIP_ESTABLISHED;
                        case DIET_ESTABLISHED ->
                                Cue.RELATIONSHIP_DIET_ESTABLISHED;
                        case EXISTING -> Cue.SOCIAL_GIFT;
                        case DIET_EXISTING -> Cue.SOCIAL_DIET_GIFT;
                        case DIET_BUILDING -> Cue.RELATIONSHIP_DIET_GIFT;
                        case CAT_ORANGE_REFUSED -> Cue.CAT_ORANGE_REFUSED;
                        case REJECTED -> Cue.SOCIAL_GIFT_REFUSED;
                        default -> Cue.RELATIONSHIP_GIFT;
                    };
            NpcDialogue.trigger(
                    creature,
                    player,
                    foodCue,
                    foodCue == Cue.RELATIONSHIP_ESTABLISHED
                                    || foodCue == Cue.RELATIONSHIP_DIET_ESTABLISHED
                            ? CreatureIdentity.introductionName(creature)
                            : creature.getDisplayName());
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        // Changed's native taur interaction mounts any saddled taur. Keep that
        // fallback behind Synergy's relationship gate so strangers cannot
        // bypass the close-friend/bond requirement with an ordinary right click.
        if (CentaurMountService.isCentaur(creature)
                && CentaurMountService.hasSaddle(creature)
                && !CentaurMountService.hasAllowedRelationship(creature, player)) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.changed_synergy.centaur_mount.requires_close"),
                    true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        boolean openingGiftWheel = player.isShiftKeyDown() && !emptyHand;
        if (owner && openingGiftWheel && socialInteractions) {
            calmTowards(creature, player);
            openBondedSocialMenu(player, creature);
        } else if (owner && (emptyHand
                || BondedSuitService.isSuitingOwner(creature, player))) {
            calmTowards(creature, player);
            openBondedPetMenu(player, creature);
        } else if (socialInteractions
                && (emptyHand || openingGiftWheel)
                && CreatureSocialProfile.allowsSocialWheel(creature)
                && CreaturePersonality.hasTrustedRelationship(creature, player)
                && !LatexSocialMemory.isProvoked(creature, player)
                && !LatexSocialMemory.hasBetrayedPatTruce(creature, player)
                && !(creature instanceof TamableLatexEntity nativePet
                        && nativePet.isTame())) {
            calmTowards(creature, player);
            openSocialMenu(player, creature);
        } else {
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Each look inside an occupied tall box gets a fresh visible reaction. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTallCardboardBoxOpened(
            PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockState state = event.getLevel().getBlockState(clicked);
        if (!(state.getBlock() instanceof CardboardBoxTall)
                || !state.hasProperty(CardboardBoxTall.HALF)) {
            return;
        }
        BlockPos holderPos = state.getValue(CardboardBoxTall.HALF)
                        == DoubleBlockHalf.LOWER
                ? clicked.above() : clicked;
        if (!(event.getLevel().getBlockEntity(holderPos)
                        instanceof CardboardBoxTallBlockEntity box)
                || !(box.getSeatedEntity() instanceof ChangedEntity creature)
                || !CreatureSocialProfile.allowsSynergySystems(creature)) {
            return;
        }

        int opens = creature.getPersistentData().getInt(BOX_OPEN_COUNT) + 1;
        creature.getPersistentData().putInt(BOX_OPEN_COUNT, opens);
        box.ticksSinceChange = 0;
        box.setChanged();
        creature.getNavigation().stop();
        creature.getLookControl().setLookAt(player, 30.0F, 30.0F);
        CreatureComfortGoal.delayAfterBoxDiscovery(creature);
        if (opens == 1) {
            NpcDialogue.trigger(creature, player, Cue.COMFORT_BOX_DISCOVERED);
        }
        Emote reaction = switch (opens) {
            case 1 -> Emote.STARTLED;
            case 2 -> Emote.CONFUSED;
            case 3 -> Emote.PAUSE;
            case 4 -> Emote.DENY;
            case 5 -> Emote.ANGRY;
            default -> switch (Math.floorMod(opens - 6, 4)) {
                case 0 -> Emote.NERVOUS;
                case 1 -> Emote.DENY;
                case 2 -> Emote.SLEEPY;
                default -> Emote.ANGRY;
            };
        };
        NpcDialogue.emoteTarget(creature, creature, reaction);
        SynergyAdvancements.grant(player, SynergyAdvancements.BOX_SURPRISE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * Leaves orange feeding and its taming roll entirely to Changed. Synergy
     * only advances the same native pup age counter before the original
     * interaction runs, so failed taming attempts still help it grow.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNativeDarkPupOrangeFeeding(
            PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof DarkLatexWolfPup pup)
                || pup.level().isClientSide
                || pup.isTame()
                || !RelationshipFavorService.isOrange(
                        player.getItemInHand(event.getHand()))) {
            return;
        }
        DarkLatexWolfPupAgeAccessor age = (DarkLatexWolfPupAgeAccessor)pup;
        age.changedSynergy$setAge(Math.min(
                72000,
                age.changedSynergy$getAge() + 6000));
    }

    public static void openBondedPetMenu(ServerPlayer player, ChangedEntity pet) {
        SocialAudienceGoal.begin(pet, player);
        int[] state = BondedLatexMenu.stateFor(pet);
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) -> new BondedLatexMenu(id, inventory, pet),
                        pet.getDisplayName()),
                extraData -> {
                    extraData.writeVarInt(pet.getId());
                    extraData.writeBoolean(LatexSocialMemory.isFollowingOwner(pet));
                    extraData.writeVarInt(state[0]);
                    extraData.writeVarInt(state[1]);
                    extraData.writeVarInt(state[2]);
                    extraData.writeVarInt(state[3]);
                    extraData.writeBoolean(ProcessTransfur.isPlayerTransfurred(player));
                    extraData.writeBoolean(
                            InvoluntaryTransfurNegotiation
                                    .canBondedReversal(pet, player));
                    extraData.writeBoolean(BondedSuitService.isSuitingOwner(pet, player));
                });
    }

    public static void openSocialMenu(
            ServerPlayer player,
            ChangedEntity creature) {
        openSocialMenu(player, creature, false);
    }

    public static void openNegotiationMenu(
            ServerPlayer player,
            ChangedEntity creature) {
        if (!InvoluntaryTransfurNegotiation.canNegotiate(player, creature)) {
            return;
        }
        SocialAudienceGoal.begin(creature, player);
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) ->
                                new SocialInteractionMenu(
                                        id, inventory, creature, false, true),
                        creature.getDisplayName()),
                extraData -> SocialInteractionMenu.writeOpeningData(
                        extraData, player, creature, false, true));
    }

    /** Opens a devouring negotiation without inventing an external NPC body. */
    public static void openAbsorptionNegotiationMenu(ServerPlayer player) {
        if (!InvoluntaryTransfurNegotiation
                .canNegotiateAbsorption(player)) {
            return;
        }
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) ->
                                new SocialInteractionMenu(
                                        id, inventory, true),
                        InvoluntaryTransfurNegotiation
                                .absorptionSourceName(player)),
                extraData -> SocialInteractionMenu
                        .writeAbsorptionOpeningData(extraData, player));
    }

    public static void openBondedSocialMenu(
            ServerPlayer player,
            ChangedEntity creature) {
        if (!ChangedSynergyGameRules.enabled(
                        player.level(), ChangedSynergyGameRules.BOND_SYSTEM)
                || !LatexSocialMemory.isPetOwner(creature, player)
                || !CreatureSocialProfile.allowsSocialWheel(creature)) {
            return;
        }
        openSocialMenu(player, creature, true);
    }

    private static void openSocialMenu(
            ServerPlayer player,
            ChangedEntity creature,
            boolean bonded) {
        boolean negotiationAccess = !bonded
                && InvoluntaryTransfurNegotiation.canNegotiate(
                        player, creature);
        if (!negotiationAccess
                && !ChangedSynergyGameRules.enabled(
                        player.level(), bonded
                                ? ChangedSynergyGameRules.BOND_SYSTEM
                                : ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)) {
            return;
        }
        SocialAudienceGoal.begin(creature, player);
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (id, inventory, viewer) ->
                                new SocialInteractionMenu(
                                        id, inventory, creature, bonded),
                        creature.getDisplayName()),
                extraData -> SocialInteractionMenu.writeOpeningData(
                        extraData, player, creature, bonded));
    }

    /** Friendly visitors receive two escalating warnings before becoming a personal enemy. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCreatureHurt(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || mob.level().isClientSide
                || event.getAmount() <= 0.0F
                || !LatexSocialMemory.isSocialLatex(mob)) {
            return;
        }

        FriendlyWarningKey warningKey = new FriendlyWarningKey(
                mob.getUUID(), player.getUUID());
        if (event.getAmount() >= mob.getHealth()) {
            PENDING_FRIENDLY_WARNINGS.remove(warningKey);
            return;
        }
        // A completed punitive secondary transfur ends the previous incident,
        // but any new attack must immediately open a fresh one.
        LatexSocialMemory.clearSecondaryTransfurSettlement(mob, player);
        CreaturePersonality.rememberHarm(mob, player, event.getAmount());
        RelationshipFavorService.showNegativeFeedback(mob, 3);
        boolean politeContact = PoliteHumanInteraction.notePlayerAttack(mob, player);
        LatexSocialMemory.refreshProvocation(mob, player);
        boolean personallyProtected = LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)
                || mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame()
                        && player.getUUID().equals(nativePet.getOwnerUUID());
        boolean betrayedPatTruce = !personallyProtected
                && LatexSocialMemory.isPatTruced(mob, player);
        boolean interruptedTruce = LatexSocialMemory.clearTruce(mob, player);
        if (betrayedPatTruce) {
            LatexSocialMemory.markPatTruceBetrayal(mob, player);
        }

        if (DarkLatexDisguise.isDarkLatex(mob)
                && DarkLatexDisguise.isImpersonatingDarkLatex(player)
                && !DarkLatexDisguise.isRevealed(mob, player)
                && !LatexSocialMemory.isBonded(mob, player)
                && !LatexSocialMemory.isPetOwner(mob, player)) {
            DarkLatexDisguiseEvents.revealAndAlert(
                    mob, player, Cue.DISGUISE_DARK_BETRAYAL);
            return;
        }

        LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
        if (betrayedPatTruce) {
            PENDING_FRIENDLY_WARNINGS.remove(warningKey);
            LatexSocialMemory.clearWarningGrace(mob, player);
            LatexSocialMemory.markProvoked(mob, player);
            mob.setTarget(player);
            HuntMemory.seeTarget(mob, player);
            queueDamageReaction(mob, player, Cue.BETRAYED);
            return;
        }
        boolean trustedPersonalRelationship = hasPersonalFactionProtection(
                mob, player, relation);
        if (FactionReputation.isHostile(mob, player)
                && !trustedPersonalRelationship) {
            PENDING_FRIENDLY_WARNINGS.remove(warningKey);
            LatexSocialMemory.clearWarningGrace(mob, player);
            mob.setTarget(player);
            HuntMemory.seeTarget(mob, player);
            return;
        }
        if (LatexSocialMemory.isOrganic(mob)
                && !ProcessTransfur.isPlayerTransfurred(player)
                && !LatexSocialMemory.isPetDefenseAuthorized(mob, player)) {
            boolean firstProvocation = LatexSocialMemory.markProvoked(mob, player);
            if (!LatexSocialMemory.isProvoked(mob, player)) {
                calmTowards(mob, player);
                return;
            }
            LatexSocialMemory.clearWarningGrace(mob, player);
            mob.setTarget(player);
            HuntMemory.seeTarget(mob, player);
            if (firstProvocation) {
                queueDamageReaction(
                        mob,
                        player,
                        Cue.HOSTILITY_CONFIRMED,
                        Component.translatable(relation.translationKey()));
            }
            return;
        }
        if (relation.isNormallyNeutral()
                || CreaturePersonality.hasEstablishedRelationship(mob, player)
                || politeContact
                || PoliteHumanInteraction.hasCourtesyHistory(mob, player)) {
            if (LatexSocialMemory.isProvoked(mob, player)) {
                mob.setTarget(player);
                HuntMemory.seeTarget(mob, player);
                return;
            }

            FriendlyHitStage stage = LatexSocialMemory.recordFriendlyHit(
                    mob, player, event.getAmount());
            if (stage == FriendlyHitStage.HOSTILE) {
                PENDING_FRIENDLY_WARNINGS.remove(warningKey);
                LatexSocialMemory.clearWarningGrace(mob, player);
                mob.setTarget(player);
                HuntMemory.seeTarget(mob, player);
                PENDING_FRIENDLY_WARNINGS.put(warningKey, new PendingFriendlyWarning(
                        mob,
                        player,
                        Cue.HOSTILITY_CONFIRMED,
                        mob.level().getGameTime() + 1L));
            } else {
                LatexSocialMemory.beginWarningGrace(mob, player, 80L);
                calmTowards(mob, player);
                PENDING_FRIENDLY_WARNINGS.put(warningKey, new PendingFriendlyWarning(
                        mob,
                        player,
                        stage == FriendlyHitStage.CONFUSED ? Cue.HIT_CONFUSED : Cue.HIT_WARNING,
                        mob.level().getGameTime() + FRIENDLY_HIT_MERGE_TICKS));
            }
        } else if (relation == LatexSocialRelation.RIVAL) {
            mob.setTarget(player);
            queueDamageReaction(mob, player, Cue.ATTACKED_BY_RIVAL);
        } else if (interruptedTruce && !player.isCreative() && !player.isSpectator()) {
            mob.setTarget(player);
        }
    }

    /** Any real health damage to an owner mobilizes every loaded bonded creature. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBondedOwnerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || owner.level().isClientSide
                || event.getAmount() <= 0.0F) {
            return;
        }
        BondedOwnerGrabRescueGoal.alertBondedCreatures(owner, attacker);
    }

    /** Transfur progress has no vanilla hurt timestamp, so signal it explicitly. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBondedOwnerTransfurDamage(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        LivingEntity attacker = event.getSourceEntity();
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || owner.level().isClientSide
                || event.getDecision() == null
                || event.getDecision().transfurProgress() <= 0.0F) {
            return;
        }
        if (attacker != null) {
            BondedOwnerGrabRescueGoal.alertBondedCreatures(owner, attacker);
        }
        if (BondedSuitService.protectOrRequestTransfurRescue(
                owner, event.getDecision().transfurProgress())) {
            event.setCanceled(true);
        }
    }

    /** Non-latex assimilation can also push a reverted owner over the threshold. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBondedOwnerNonLatexTransfurDamage(
            TransfurEvents.NonLatexAssimilationDecisionEvent event) {
        LivingEntity attacker = event.getSourceEntity();
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || owner.level().isClientSide
                || event.getDecision() == null
                || event.getDecision().transfurProgress() <= 0.0F) {
            return;
        }
        if (attacker != null) {
            BondedOwnerGrabRescueGoal.alertBondedCreatures(owner, attacker);
        }
        if (BondedSuitService.protectOrRequestTransfurRescue(
                owner, event.getDecision().transfurProgress())) {
            event.setCanceled(true);
        }
    }

    /** Enforces bonds, neutral first contact and target-specific pat truces across all target goals. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof IronGolem golem
                && !golem.level().isClientSide
                && golem.getTarget() instanceof ChangedEntity target) {
            LatexCreatureCombatRules.disengage(golem, target);
            return;
        }
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || mob.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(mob)
                || !CreatureSocialProfile.allowsSynergySystems(mob)) {
            return;
        }
        if (mob.getTarget() instanceof AbstractVillager villager) {
            LatexCreatureCombatRules.disengage(mob, villager);
        }
        NpcEmoteState.tick(mob);
        CreatureRoleService.tick(mob);
        CreatureSettlementService.tickCargoIndicator(mob);
        CreatureCacheGuardService.tick(mob);
        ProvisionerFishingFocus.tick(mob);
        ProvisionerGiftService.tick(mob);

        if (mob.tickCount % 40 == 0) {
            CreatureIdentity.reconcilePersistence(mob);
            if (!LatexSocialMemory.bondedPlayerUuids(mob).isEmpty()) {
                BondedCreatureLifecycle.track(mob);
                LatexSocialMemory.suppressBondedAvoidanceGoals(mob);
            }
            ServerPlayer owner = LatexSocialMemory.getPetOwner(mob);
            if (owner != null
                    && LatexSocialMemory.isFriendlySuitActive(mob, owner)
                    && !BondedSuitService.isSuitingOwner(mob, owner)) {
                LatexSocialMemory.endFriendlySuit(mob, owner);
                ChangedAddonCompat.configureFriendlyGrab(BondedSuitService.ability(mob), false);
            }
            double dialogueRange = ChangedSynergyConfig.COMMON.npcDialogueRange.get();
            if (owner != null
                    && mob.distanceToSqr(owner) <= dialogueRange * dialogueRange
                    && LatexSocialMemory.claimJealousFormGreeting(mob, owner)) {
                NpcDialogue.trigger(mob, owner, Cue.BOND_JEALOUS_NEW_FORM);
            }
        }

        ServerPlayer owner = LatexSocialMemory.getPetOwner(mob);
        CompanionWorkDialogue.tick(mob, owner);
        if (owner != null && mob.tickCount % 20 == 0
                && ChangedAddonCompat.supportsBondedPetBackend(mob)
                && BondedPetSettings.hasWorkFavor(mob)) {
            ChangedAddonCompat.refreshBondedWorkItem(mob);
        }
        if (owner != null && LatexSocialMemory.hasHostilityToward(mob, owner)) {
            LatexSocialMemory.clearOwnerHostility(mob, owner);
        }
        if (owner != null) {
            // Addon entities can install goals after joining. Reconcile every
            // tick; the actual selector mutation remains deferred and deduplicated.
            LatexSocialMemory.suppressBondedAvoidanceGoals(mob);
            LivingEntity target = mob.getTarget();
            if (target != null
                    && LatexSocialMemory.rejectsFollowingCombatTarget(mob, target)) {
                LatexSocialMemory.clearRejectedFollowingTarget(mob, target);
            }
        }
        if (mob.getTarget() instanceof ChangedEntity other
                && LatexCreatureCombatRules.mustRejectTarget(mob, other)) {
            LatexCreatureCombatRules.disengage(mob, other);
        }
        if (mob.getTarget() instanceof ServerPlayer player
                && !LatexSocialMemory.isPetDefenseAuthorized(mob, player)
                && NpcDispositionEvents.mustRejectTarget(mob, player)) {
            calmTowards(mob, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        BondedSuitService.tickNativeOwnerSuit(player);
        BondedSuitService.tickFriendlySuit(player);
        InvoluntaryTransfurNegotiation.tickPlayer(player);
        if (player.tickCount % 100 == 0) {
            BondedCreatureLifecycle.audit(player);
        }
        if (player.tickCount % 10 != 0
                || !player.isAlive() || player.isSpectator()
                || !player.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                || !ChangedSynergyGameRules.socialSystemsEnabled(player.level())) {
            return;
        }
        if (!ProcessTransfur.isPlayerTransfurred(player)
                && !LatexSocialMemory.hasNearbyBondedCreature(player)
                && !CreaturePersonality.hasNearbyRelationship(player, 4.0D)
                && !PoliteHumanInteraction.hasNearbyAcquaintance(player, 4.0D)) {
            return;
        }
        tryPatPlayer(player);
    }

    /**
     * Release a native pet before Changed removes the temporary suit form. This
     * keeps the original pet entity alive and lets it separate from its owner.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerUntransfur(TransfurEvents.UntransfurPlayerEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        InvoluntaryTransfurNegotiation.onExternalUntransfur(player);
        if (!event.getVariantInstance().isTemporaryFromSuit()) {
            return;
        }
        ChangedEntity pet = BondedSuitService.getWrappingPet(player);
        if (pet != null && BondedSuitService.isNativeOwnerSuit(pet, player)) {
            BondedSuitService.separateNativeOwnerSuit(pet, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreatureMorphAliasData.get(player.server).apply(player);
            LatexSocialMemory.applyPendingBondDeaths(player);
            player.server.execute(() -> BondedCreatureLifecycle.audit(player));
            player.server.execute(() ->
                    InvoluntaryTransfurNegotiation.onPlayerReady(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.server.execute(() -> BondedCreatureLifecycle.audit(player));
            player.server.execute(() ->
                    InvoluntaryTransfurNegotiation.onPlayerReady(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.server.execute(() -> BondedCreatureLifecycle.audit(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer clone) {
            LatexSocialMemory.copyPlayerBondData(original, clone);
            BondedCreatureLifecycle.copyPlayerData(original, clone);
            InvoluntaryTransfurNegotiation.copyPlayerData(original, clone);
            FactionHostilityGrace.copyPlayerData(original, clone);
        }
    }

    /** Called from the optional Changed Addon pat event bridge. */
    public static void onPatted(ChangedEntity mob, ServerPlayer player) {
        if (!LatexSocialMemory.isSocialLatex(mob)
                || !mob.isAlive()
                || !ChangedSynergyGameRules.socialSystemsEnabled(player.level())
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return;
        }
        boolean wasHostile = LatexSocialMemory.hasHostilityToward(mob, player)
                || LatexSocialMemory.isProvoked(mob, player)
                || FactionReputation.isHostile(mob, player);
        CreaturePersonality.rememberPatReceived(mob, player);
        boolean personallyProtected = LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)
                || mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame()
                        && player.getUUID().equals(nativePet.getOwnerUUID());
        LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
        boolean lowReputationCalming = FactionReputation.isDistrusted(mob, player)
                && relation != LatexSocialRelation.RIVAL
                && !hasPersonalFactionProtection(mob, player, relation);
        if (!personallyProtected
                && LatexSocialMemory.hasRelationshipBetrayal(mob, player)) {
            NpcDialogue.trigger(mob, player, Cue.FRIEND_BETRAYAL_PAT_REFUSED);
            delayNextActivePat(mob, mob.level().getGameTime() + RETURN_PAT_DELAY);
            return;
        }
        if (!personallyProtected
                && LatexSocialMemory.hasBetrayedPatTruce(mob, player)) {
            // The touch still happened, but this creature no longer treats it
            // as proof that the chase is over.
            NpcDialogue.trigger(mob, player, Cue.PAT_TRUCE_REFUSED);
            delayNextActivePat(mob, mob.level().getGameTime() + RETURN_PAT_DELAY);
            return;
        }

        if (HypnosisQteService.interruptByPat(mob, player)) {
            LatexSocialMemory.beginPatTruce(mob, player, BASE_PAT_TRUCE);
            calmTowards(mob, player);
            rewardCalmingPat(player, wasHostile);
            delayNextActivePat(mob, mob.level().getGameTime() + RETURN_PAT_DELAY);
            return;
        }

        if (!personallyProtected
                && DarkLatexDisguise.appearsAsDarkRivalToWhite(mob, player)) {
            LatexSocialMemory.beginPatTruce(
                    mob, player, Math.max(20L, BASE_PAT_TRUCE / 4L));
            calmTowards(mob, player);
            rewardCalmingPat(player, wasHostile);
            NpcDialogue.trigger(mob, player, Cue.PAT_DISGUISED_WHITE_RIVAL);
            return;
        }
        if (!personallyProtected
                && DarkLatexDisguise.isDarkLatex(mob)
                && DarkLatexDisguise.isImpersonatingDarkLatex(player)) {
            if (DarkLatexDisguise.isRevealed(mob, player)
                    || LatexSocialMemory.isProvoked(mob, player)) {
                LatexSocialMemory.beginPatTruce(
                        mob, player, Math.max(20L, BASE_PAT_TRUCE / 4L));
                calmTowards(mob, player);
                rewardCalmingPat(player, wasHostile);
                NpcDialogue.trigger(mob, player, Cue.PAT_DISGUISED_DARK_CAUGHT);
                return;
            }

            Observation inspection = DarkLatexDisguise.inspectFromPat(mob, player);
            if (inspection == Observation.REVEALED) {
                DarkLatexDisguiseEvents.revealAndAlert(
                        mob, player, Cue.PAT_DISGUISED_DARK_CAUGHT);
                LatexSocialMemory.beginPatTruce(
                        mob, player, Math.max(20L, BASE_PAT_TRUCE / 4L));
                calmTowards(mob, player);
                rewardCalmingPat(player, wasHostile);
                return;
            }

            LatexSocialMemory.beginPatTruce(mob, player, BASE_PAT_TRUCE * 11L / 8L);
            calmTowards(mob, player);
            rewardCalmingPat(player, wasHostile);
            NpcDialogue.trigger(
                    mob,
                    player,
                    inspection == Observation.INSPECTED_AND_ACCEPTED
                                    || inspection == Observation.ALREADY_ACCEPTED
                            ? Cue.PAT_DISGUISED_DARK_INSPECTED
                            : Cue.PAT_DISGUISED_DARK);
            return;
        }

        boolean acceptedGreeting = PoliteHumanInteraction.acceptPlayerPat(mob, player);
        if (!personallyProtected
                && relation == LatexSocialRelation.HUMAN
                && !LatexSocialMemory.isProvoked(mob, player)) {
            RelationshipProgress progress =
                    CreaturePersonality.advanceRelationship(mob, player);
            LatexSocialMemory.beginPatTruce(mob, player, BASE_PAT_TRUCE * 4L);
            calmTowards(mob, player);
            rewardCalmingPat(player, wasHostile);
            delayNextActivePat(mob, mob.level().getGameTime() + RETURN_PAT_DELAY);
            if (mob.level() instanceof ServerLevel level) {
                int count = progress == RelationshipProgress.ESTABLISHED ? 5 : 2;
                level.sendParticles(
                        ParticleTypes.HEART,
                        mob.getX(),
                        mob.getY(0.75D),
                        mob.getZ(),
                        count,
                        0.25D,
                        0.22D,
                        0.25D,
                        0.02D);
            }
            Cue responseCue = lowReputationCalming
                    ? Cue.LOW_REPUTATION_PAT
                    : progress == RelationshipProgress.ESTABLISHED
                            ? Cue.RELATIONSHIP_ESTABLISHED
                            : progress == RelationshipProgress.EXISTING
                                    ? Cue.SOCIAL_PAT
                                    : acceptedGreeting
                                            ? Cue.POLITE_RESPONSE
                                            : Cue.RELATIONSHIP_PROGRESS;
            NpcDialogue.trigger(
                    mob,
                    player,
                    responseCue,
                    responseCue == Cue.RELATIONSHIP_ESTABLISHED
                            ? CreatureIdentity.introductionName(mob)
                            : mob.getDisplayName());
            return;
        }

        boolean bonded = LatexSocialMemory.isBonded(mob, player);
        long truceTicks = bonded ? BASE_PAT_TRUCE * 2L : switch (relation) {
            case FORMER_BONDED -> BASE_PAT_TRUCE * 2L;
            case FORMER_RESPECTED, FRIEND_RESPECTED,
                    SAME_SPECIES -> BASE_PAT_TRUCE * 3L / 2L;
            case SAME_CATEGORY -> BASE_PAT_TRUCE * 11L / 8L;
            case FRIENDLY_OTHER -> BASE_PAT_TRUCE * 5L / 4L;
            case OUTSIDER -> BASE_PAT_TRUCE;
            case HUMAN -> BASE_PAT_TRUCE * 3L / 4L;
            case RIVAL -> Math.max(20L, BASE_PAT_TRUCE / 4L);
        };

        LatexSocialMemory.beginPatTruce(mob, player, truceTicks);
        calmTowards(mob, player);
        rewardCalmingPat(player, wasHostile);
        delayNextActivePat(mob, mob.level().getGameTime() + RETURN_PAT_DELAY);
        boolean needsVouch = !FactionReputation.isRecognized(mob, player);
        Cue cue = lowReputationCalming ? Cue.LOW_REPUTATION_PAT
                : bonded && LatexSocialMemory.isBondedOwnerInOtherForm(mob, player)
                ? Cue.PAT_BONDED_NEW_FORM
                : relation == LatexSocialRelation.FORMER_BONDED ? Cue.PAT_FORMER_BONDED
                : relation == LatexSocialRelation.FORMER_RESPECTED && needsVouch
                        ? Cue.PAT_FORMER_RESPECT
                : relation == LatexSocialRelation.FRIEND_RESPECTED && needsVouch
                        ? Cue.PAT_FRIEND_RESPECT
                : (relation == LatexSocialRelation.FORMER_RESPECTED
                        || relation == LatexSocialRelation.FRIEND_RESPECTED)
                        ? Cue.PAT_FRIEND
                : bonded ? Cue.PAT_BONDED : switch (relation) {
            case SAME_SPECIES -> Cue.PAT_KIN;
            case SAME_CATEGORY -> Cue.PAT_CATEGORY;
            case FRIENDLY_OTHER -> Cue.PAT_FRIEND;
            case OUTSIDER -> Cue.PAT_OUTSIDER;
            case HUMAN -> Cue.PAT_HUMAN;
            case RIVAL -> Cue.PAT_RIVAL;
            case FORMER_BONDED -> Cue.PAT_FORMER_BONDED;
            case FORMER_RESPECTED -> Cue.PAT_FORMER_RESPECT;
            case FRIEND_RESPECTED -> Cue.PAT_FRIEND_RESPECT;
        };
        NpcDialogue.trigger(mob, player, cue);
    }

    private static void rewardCalmingPat(
            ServerPlayer player,
            boolean wasHostile) {
        if (wasHostile) {
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.PEACE_OFFERING);
        }
    }

    public static void calmTowards(ChangedEntity mob, ServerPlayer player) {
        LatexSocialMemory.clearHostilityToward(mob, player);
    }

    /** Removes delayed warning/declaration lines left by a combat episode that has ended. */
    public static void clearPendingCombatReactions(
            ChangedEntity mob,
            ServerPlayer player) {
        FriendlyWarningKey key = new FriendlyWarningKey(
                mob.getUUID(), player.getUUID());
        PENDING_FRIENDLY_WARNINGS.remove(key);
        PENDING_DAMAGE_REACTIONS.remove(key);
    }

    private static void tryPatPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (BondedSuitService.getWrappingPet(player) != null) {
            return;
        }
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(NEXT_RECEIVE_PAT) > now) {
            return;
        }

        List<ChangedEntity> candidates = level.getEntitiesOfClass(
                        ChangedEntity.class, player.getBoundingBox().inflate(3.5),
                        mob -> mob.isAlive()
                                && LatexSocialMemory.isSocialLatex(mob)
                                && CreatureSocialProfile.allowsPersonalRelationship(mob))
                .stream()
                .filter(mob -> mob.getTarget() == null && mob.hasLineOfSight(player)
                        && mob.distanceToSqr(player) <= 12.25
                        && mob.getPersistentData().getLong(NEXT_PAT_PLAYER) <= now
                        && !ProvisionerFishingFocus.isFocusedFishing(mob)
                        && LatexSocialMemory.shouldRemainNeutral(mob, player))
                .filter(mob -> {
                    LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
                    return relation != LatexSocialRelation.RIVAL
                            && (relation != LatexSocialRelation.HUMAN
                                    || CreaturePersonality.hasTrustedRelationship(mob, player)
                                    || PoliteHumanInteraction.isAcquainted(mob, player));
                })
                .filter(mob -> PlayerRelationshipSettings.allowsActivePat(
                        player, mob))
                .sorted(Comparator
                        .comparingInt((ChangedEntity mob) -> socialPriority(mob, player))
                        .thenComparingInt(mob -> -CreaturePersonality.familiarity(mob, player))
                        .thenComparingDouble(mob -> mob.distanceToSqr(player)))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        ChangedEntity mob = candidates.get(0);
        LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
        boolean bonded = LatexSocialMemory.isBonded(mob, player);
        boolean needsVouch = !FactionReputation.isRecognized(mob, player);
        Cue cue = bonded && LatexSocialMemory.isBondedOwnerInOtherForm(mob, player)
                ? Cue.PAT_PLAYER_BONDED_NEW_FORM
                : relation == LatexSocialRelation.FORMER_BONDED ? Cue.PAT_PLAYER_FORMER_BONDED
                : relation == LatexSocialRelation.FORMER_RESPECTED && needsVouch
                        ? Cue.PAT_PLAYER_FORMER_RESPECT
                : relation == LatexSocialRelation.FRIEND_RESPECTED && needsVouch
                        ? Cue.PAT_PLAYER_FRIEND_RESPECT
                : (relation == LatexSocialRelation.FORMER_RESPECTED
                        || relation == LatexSocialRelation.FRIEND_RESPECTED)
                        ? (ProcessTransfur.isPlayerTransfurred(player)
                                ? Cue.PAT_PLAYER_OTHER
                                : Cue.PAT_PLAYER_HUMAN_FRIEND)
                : bonded ? Cue.PAT_PLAYER_BONDED
                : DarkLatexDisguise.foolsDarkObserver(mob, player)
                        ? Cue.PAT_PLAYER_DISGUISED_DARK
                : relation == LatexSocialRelation.SAME_SPECIES ? Cue.PAT_PLAYER_KIN
                : relation == LatexSocialRelation.SAME_CATEGORY ? Cue.PAT_PLAYER_CATEGORY
                : relation == LatexSocialRelation.FRIENDLY_OTHER ? Cue.PAT_PLAYER_FRIEND
                : relation == LatexSocialRelation.HUMAN ? Cue.PAT_PLAYER_HUMAN_FRIEND
                : Cue.PAT_PLAYER_OTHER;

        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        PatAnimationService.startFixed(mob, player, 4);
        if (player.level() instanceof ServerLevel patLevel) {
            patLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.HEART,
                    player.getX(), player.getY(0.85D), player.getZ(),
                    4, 0.18D, 0.24D, 0.18D, 0.01D);
        }
        player.displayClientMessage(Component.translatable(
                "message.changed_synergy.social.patted_by",
                mob.getDisplayName()), true);
        NpcDialogue.emoteTarget(mob, player, Emote.HEART);
        NpcDialogue.trigger(mob, player, cue);
        CreaturePersonality.rememberPatGiven(mob, player);
        scheduleNextActivePat(mob, now, player);
        player.getPersistentData().putLong(
                NEXT_RECEIVE_PAT, now + PLAYER_PAT_RECEPTION_COOLDOWN);
    }

    private static void scheduleNextActivePat(ChangedEntity mob, long now) {
        scheduleNextActivePat(mob, now, null);
    }

    private static void scheduleNextActivePat(
            ChangedEntity mob,
            long now,
            ServerPlayer familiarPlayer) {
        double multiplier = CreaturePersonality.activePatCooldownMultiplier(mob, familiarPlayer);
        long minimum = Math.max(1L, Math.round(ACTIVE_PAT_MIN_COOLDOWN * multiplier));
        int randomRange = Math.max(1,
                (int)Math.round(ACTIVE_PAT_RANDOM_COOLDOWN * multiplier));
        mob.getPersistentData().putLong(
                NEXT_PAT_PLAYER,
                now + minimum + mob.getRandom().nextInt(randomRange));
    }

    private static void delayNextActivePat(ChangedEntity mob, long earliestTick) {
        mob.getPersistentData().putLong(
                NEXT_PAT_PLAYER,
                Math.max(mob.getPersistentData().getLong(NEXT_PAT_PLAYER), earliestTick));
    }

    private static int socialPriority(ChangedEntity mob, ServerPlayer player) {
        if (LatexSocialMemory.isBonded(mob, player)) {
            return 0;
        }
        return switch (LatexSocialRelation.between(mob, player)) {
            case FORMER_RESPECTED, FRIEND_RESPECTED, SAME_SPECIES -> 1;
            case SAME_CATEGORY -> 2;
            case FRIENDLY_OTHER -> 3;
            case OUTSIDER, HUMAN -> 4;
            default -> 5;
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBondedOwnerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)) {
            return;
        }
        ChangedEntity pet = BondedSuitService.getWrappingPet(owner);
        if (pet != null && LatexSocialMemory.isBonded(pet, owner)) {
            RECENT_WRAPPED_OWNER_DEATHS.put(pet.getUUID(), owner.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBondedCreatureDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity pet)
                || !(pet.level() instanceof ServerLevel level)) {
            return;
        }
        Set<UUID> bondedPlayers = LatexSocialMemory.bondedPlayerUuids(pet);
        if (bondedPlayers.isEmpty()) {
            return;
        }

        UUID recentWrappedOwner = RECENT_WRAPPED_OWNER_DEATHS.remove(pet.getUUID());
        UUID ownerUuid = recentWrappedOwner != null && bondedPlayers.contains(recentWrappedOwner)
                ? recentWrappedOwner
                : LatexSocialMemory.petOwnerUuid(pet)
                .filter(bondedPlayers::contains)
                .orElseGet(() -> bondedPlayers.iterator().next());
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        boolean wasWrapping = recentWrappedOwner != null
                || owner != null && (BondedSuitService.isWrappingOwner(pet, owner)
                        || LatexSocialMemory.isFriendlySuitActive(pet, owner));
        PENDING_BOND_DEATHS.put(pet.getUUID(),
                new PendingBondDeath(pet, ownerUuid, wasWrapping));
    }

    /** Delays one phase so simultaneous owner/pet deaths can be distinguished. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // GoalSelector is no longer iterating at this point, so queued companion
        // movement-goal changes cannot invalidate its LinkedHashMap iterator.
        LatexCreatureCombatRules.flushPendingDisengagements(event.getServer());
        LatexSocialMemory.flushPendingGoalMutations(event.getServer());
        flushDamageReactions();
        flushFriendlyWarnings();
        if (!PENDING_BOND_DEATHS.isEmpty()) {
            for (PendingBondDeath pending : List.copyOf(PENDING_BOND_DEATHS.values())) {
                if (pending.pet().isAlive()
                        || !(pending.pet().level() instanceof ServerLevel level)) {
                    continue;
                }
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pending.ownerUuid());
                if (pending.wasWrapping() && owner != null) {
                    BondedSuitService.breakFriendlySuitOnDeath(pending.pet(), owner);
                }
                LatexSocialMemory.breakBondsOnDeath(pending.pet());
                PENDING_BOND_DEATHS.remove(pending.pet().getUUID());
                if (owner == null) {
                    continue;
                }
                Cue cue = !pending.wasWrapping()
                        ? Cue.BOND_DEATH_DIRECT
                        : owner.isDeadOrDying()
                                ? Cue.BOND_DEATH_TOGETHER
                                : Cue.BOND_DEATH_WRAPPING;
                NpcDialogue.triggerBondFarewell(pending.pet(), owner, cue);
            }
        }
        RECENT_WRAPPED_OWNER_DEATHS.clear();
    }

    private static void flushFriendlyWarnings() {
        if (PENDING_FRIENDLY_WARNINGS.isEmpty()) {
            return;
        }
        for (Map.Entry<FriendlyWarningKey, PendingFriendlyWarning> entry
                : List.copyOf(PENDING_FRIENDLY_WARNINGS.entrySet())) {
            PendingFriendlyWarning pending = entry.getValue();
            ChangedEntity mob = pending.mob();
            ServerPlayer player = pending.player();
            if (!mob.isAlive() || !player.isAlive() || player.isSpectator()
                    || mob.level() != player.level()) {
                PENDING_FRIENDLY_WARNINGS.remove(entry.getKey());
                continue;
            }
            if (mob.level().getGameTime() < pending.dueTick()) {
                continue;
            }

            PENDING_FRIENDLY_WARNINGS.remove(entry.getKey());
            LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
            if (FactionReputation.isHostile(mob, player)
                    && !hasPersonalFactionProtection(mob, player, relation)) {
                LatexSocialMemory.clearWarningGrace(mob, player);
                continue;
            }
            if (pending.cue() == Cue.HOSTILITY_CONFIRMED) {
                if (LatexSocialMemory.isProvoked(mob, player)
                        && !LatexSocialMemory.shouldRemainNeutral(mob, player)) {
                    NpcDialogue.trigger(
                            mob,
                            player,
                            Cue.HOSTILITY_CONFIRMED,
                            Component.translatable(relation.translationKey()));
                }
                continue;
            }
            if (LatexSocialMemory.isProvoked(mob, player) || !relation.isNormallyNeutral()) {
                continue;
            }
            NpcDialogue.trigger(
                    mob,
                    player,
                    pending.cue(),
                    Component.translatable(relation.translationKey()));
        }
    }

    private static void queueDamageReaction(
            ChangedEntity mob,
            ServerPlayer player,
            Cue cue,
            Object... lineArguments) {
        FriendlyWarningKey key = new FriendlyWarningKey(
                mob.getUUID(), player.getUUID());
        PENDING_DAMAGE_REACTIONS.put(
                key,
                new PendingDamageReaction(
                        mob,
                        player,
                        cue,
                        mob.level().getGameTime() + 1L,
                        lineArguments));
    }

    private static void flushDamageReactions() {
        if (PENDING_DAMAGE_REACTIONS.isEmpty()) {
            return;
        }
        for (Map.Entry<FriendlyWarningKey, PendingDamageReaction> entry
                : List.copyOf(PENDING_DAMAGE_REACTIONS.entrySet())) {
            PendingDamageReaction pending = entry.getValue();
            ChangedEntity mob = pending.mob();
            ServerPlayer player = pending.player();
            if (!mob.isAlive() || !player.isAlive() || player.isSpectator()
                    || mob.level() != player.level()) {
                PENDING_DAMAGE_REACTIONS.remove(entry.getKey());
                continue;
            }
            if (mob.level().getGameTime() < pending.dueTick()) {
                continue;
            }
            PENDING_DAMAGE_REACTIONS.remove(entry.getKey());
            NpcDialogue.trigger(
                    mob,
                    player,
                    pending.cue(),
                    pending.lineArguments());
        }
    }

    private static boolean hasPersonalFactionProtection(
            ChangedEntity mob,
            ServerPlayer player,
            LatexSocialRelation relation) {
        return LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)
                || mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame()
                        && player.getUUID().equals(nativePet.getOwnerUUID())
                || CreaturePersonality.hasTrustedRelationship(mob, player)
                        && !LatexSocialMemory.hasRelationshipBetrayal(mob, player)
                        && relation != LatexSocialRelation.RIVAL;
    }

    private static boolean isProtectedWhiteLatexWolf(LivingEntity entity) {
        if (!(entity instanceof ChangedEntity wolf)
                || (!(wolf instanceof WhiteLatexWolfMale)
                        && !(wolf instanceof WhiteLatexWolfFemale))) {
            return false;
        }
        return LatexSocialMemory.petOwnerUuid(wolf).isPresent()
                || LatexSocialMemory.hasActiveBond(wolf)
                || wolf instanceof TamableLatexEntity nativePet && nativePet.isTame();
    }

    private record FriendlyWarningKey(UUID mobUuid, UUID playerUuid) {
    }

    private record PendingFriendlyWarning(
            ChangedEntity mob,
            ServerPlayer player,
            Cue cue,
            long dueTick) {
    }

    private record PendingDamageReaction(
            ChangedEntity mob,
            ServerPlayer player,
            Cue cue,
            long dueTick,
            Object[] lineArguments) {
    }

    private record PendingBondDeath(
            ChangedEntity pet,
            UUID ownerUuid,
            boolean wasWrapping) {
    }
}
