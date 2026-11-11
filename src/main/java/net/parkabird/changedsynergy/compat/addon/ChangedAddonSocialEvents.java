package net.parkabird.changedsynergy.compat.addon;

import java.lang.reflect.Field;
import java.util.List;
import static net.parkabird.changedsynergy.ai.BondedPetSettings.*;
import net.foxyas.changedaddon.ChangedAddonMod;
import net.foxyas.changedaddon.ability.api.GrabEntityAbilityExtensor;
import net.foxyas.changedaddon.entity.ai.LatexAttackCondition;
import net.foxyas.changedaddon.entity.ai.LatexAttackType;
import net.foxyas.changedaddon.entity.ai.LatexCaveHarvestGoal;
import net.foxyas.changedaddon.entity.ai.LatexCaveTorchingGoal;
import net.foxyas.changedaddon.entity.ai.LatexFavor;
import net.foxyas.changedaddon.entity.ai.LatexFishingGoal;
import net.foxyas.changedaddon.entity.ai.LatexSuitOwnerGoal;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.foxyas.changedaddon.entity.api.IAlphaAbleEntity;
import net.foxyas.changedaddon.entity.api.TamableLatexEntityFavors;
import net.foxyas.changedaddon.entity.defaults.AbstractLuminarcticLeopard;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayDropGrabbedEntityGoal;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayCauseGrabDamageGoal;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayGrabTargetGoal;
import net.foxyas.changedaddon.network.packet.SyncGrabControlState;
import net.foxyas.changedaddon.process.features.ProcessPatFeature;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondedOwnerDefenseGoal;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.OrganicOwnerEvacuationGoal;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.ai.PatAnimationService;
import net.parkabird.changedsynergy.ai.SafeEntityMutationQueue;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/** Loaded reflectively only while Changed Addon is installed. */
public final class ChangedAddonSocialEvents {

    private ChangedAddonSocialEvents() {
    }

    public static boolean isSecondaryAbilitySelected(net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance<?> variant,
            net.ltxprogrammer.changed.ability.AbstractAbility<?> ability) {
        return variant instanceof net.foxyas.changedaddon.variant.TransfurVariantInstanceExtensor extra
                && extra.getSecondSelectedAbility()==ability;
    }

    public static float alphaRenderScale(ChangedEntity creature) {
        return creature instanceof IAlphaAbleEntity alpha && alpha.isAlpha()
                ? alpha.alphaScaleForRender() : 1.0F;
    }

    /** Luminarctic Leopard Male uses one entity type for both its normal and boss forms. */
    public static boolean isSocialSystemExcludedBoss(ChangedEntity creature) {
        return creature instanceof AbstractLuminarcticLeopard leopard
                && leopard.isBoss();
    }

    /** Run before Addon's own feedback handler so sleeping creatures stay asleep. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSleepingPat(ProcessPatFeature.GlobalPatReactionEvent event) {
        if ((event.patter instanceof ChangedEntity actor && actor.isSleeping()
                || event.target instanceof ChangedEntity target && target.isSleeping())
                && event.isCancelable()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPat(ProcessPatFeature.GlobalPatReactionEvent event) {
        // Addon's human pat key can emit this event every tick during the same held stroke.
        if (PatAnimationService.isContinuous(event.patter, event.target)) return;
        if (event.target instanceof Player player && TakeoverService.active(player)) {
            if (event.isCancelable()) event.setCanceled(true);
            return;
        }
        if (event.target instanceof ChangedEntity mob
                && (!CreatureSocialProfile.allowsSynergySystems(mob)
                    || TakeoverService.carrying(mob))) {
            return;
        }
        if (!event.patter.level().isClientSide) {
            PatAnimationService.startFixed(event.patter, event.target, 4);
        }
        if (event.patter instanceof ServerPlayer player
                && event.target instanceof ChangedEntity mob
                && !player.level().isClientSide) {
            LatexSocialEvents.onPatted(mob, player);
        }
    }

    /** Addon already supplies the Wolfy/alpha grab goals; enable them for every supported latex NPC. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ChangedEntity mob)) {
            return;
        }

        if (mob instanceof IGrabberEntity grabber
                && CreatureSocialProfile.isGrabMechanicExcluded(mob)) {
            disableGrabMechanic(mob, grabber);
            return;
        }
        if (!LatexSocialMemory.isSocialLatex(mob)
                || !CreatureSocialProfile.allowsSynergySystems(mob)) {
            return;
        }

        if (mob instanceof IGrabberEntity grabber) {
            grabber.setCanUseGrab(true);
            ensureGrabAbility(mob, grabber);
            if (LatexSocialMemory.isOrganic(mob)) {
                if (mob.goalSelector.getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal()
                                instanceof OrganicOwnerEvacuationGoal)) {
                    mob.goalSelector.addGoal(
                            -5, new OrganicOwnerEvacuationGoal(mob));
                }
                if (mob.goalSelector.getAvailableGoals().stream()
                        .noneMatch(wrapped -> wrapped.getGoal() instanceof OrganicAssimilationGrabGoal)) {
                    mob.goalSelector.addGoal(0, new OrganicAssimilationGrabGoal(mob, grabber));
                }
            } else if (mob.goalSelector.getAvailableGoals().stream()
                    .noneMatch(wrapped -> wrapped.getGoal() instanceof HostileTransfurredGrabGoal)) {
                // This hold must outrank melee and Addon's normal grab/drop goals.
                mob.goalSelector.addGoal(0, new HostileTransfurredGrabGoal(mob, grabber));
            }

            installSocialGrabGoals(mob, grabber);
        }

        reconcileAddonWorkGoals(mob);
    }

    /** Initializes abilities enabled after construction on both logical sides. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || mob.getUnderlyingPlayer() != null) {
            return;
        }

        if (!mob.level().isClientSide
                && mob instanceof TamableLatexEntityFavors
                && LatexSocialMemory.isSocialLatex(mob)
                && CreatureSocialProfile.allowsSynergySystems(mob)
                && mob.tickCount % 40 == Math.floorMod(mob.getId(), 40)) {
            // Apply config changes after goal ticking, including to entities
            // which were already loaded when the option changed.
            SafeEntityMutationQueue.queue(
                    mob,
                    "addon_companion_work_goals",
                    () -> reconcileAddonWorkGoals(mob));
        }
        if (!(mob instanceof IGrabberEntity grabber)) {
            return;
        }

        if (!mob.level().isClientSide && (LatexSocialMemory.hasFriendlySocialHug(mob)
                || InvoluntaryTransfurNegotiation.hasReleaseHold(mob)
                || LatexSocialMemory.hasOrganicEvacuation(mob))) return; // Common hold owner maintains these sessions.

        if (CreatureSocialProfile.isGrabMechanicExcluded(mob)) {
            grabber.setCanUseGrab(false);
            if (!mob.level().isClientSide
                    && (mob.tickCount % 20 == Math.floorMod(mob.getId(), 20)
                            || grabber.getGrabAbilityInstance() != null
                                    && grabber.getGrabAbilityInstance().grabbedEntity != null)) {
                disableGrabMechanic(mob, grabber);
            }
            return;
        }
        if (!CreatureSocialProfile.allowsSynergySystems(mob)
                || !LatexSocialMemory.isSocialLatex(mob)
                || !grabber.canUseGrab()) {
            return;
        }

        GrabEntityAbilityInstance registered = BondedSuitService.ability(mob);
        if (grabber.getGrabAbilityInstance() == null
                || registered != null && registered != grabber.getGrabAbilityInstance()) {
            ensureGrabAbility(mob, grabber);
        }
        if (!mob.level().isClientSide
                && mob.tickCount % 20 == Math.floorMod(mob.getId(), 20)) {
            SafeEntityMutationQueue.queue(
                    mob,
                    "addon_social_grab_goals",
                    () -> installSocialGrabGoals(mob, grabber));
        }
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if(TakeoverService.carrying(mob)
                ||mob.level().isClientSide&&mob.getPersistentData().getBoolean("SynergyTakeoverClient"))return;
        if (mob.level().isClientSide) {
            // Changed's SUIT packet does not carry grabbedHasControl. Native
            // dark latex repairs it in its own client tick, while Addon's
            // generic ChangedEntity taming mixin only does so server-side.
            // Infer the same state from the synchronized native owner field.
            if (ability != null
                    && ability.suited
                    && ability.grabbedEntity != null
                    && mob instanceof net.ltxprogrammer.changed.entity.TamableLatexEntity pet
                    && ability.grabbedEntity == pet.getOwner()) {
                ability.grabbedHasControl = true;
            }
            return;
        }

        if (LatexSocialMemory.isOrganic(mob)) {
            stripOrganicSuitBehavior(mob);
            if (ability != null && ability.suited && ability.grabbedEntity != null) {
                LivingEntity grabbed = ability.grabbedEntity;
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                        new GrabEntityPacket(mob, grabbed, GrabType.RELEASE));
                if (grabbed instanceof ServerPlayer player) {
                    TrueTransfurCompat.clearTemporarySuit(player);
                    BondedSuitService.syncOwnerSuitState(mob, player, false);
                }
                if (ability instanceof GrabEntityAbilityExtensor extensor) {
                    extensor.setSafeModeAuthoritative(false);
                }
            }
        }

        if (ability != null && ability.grabbedEntity instanceof ServerPlayer player
                && LatexSocialMemory.isFriendlySuitActive(mob, player)) {
            var body = ProcessTransfur.getPlayerTransfurVariant(player);
            var ownerGrabber = GrabEntityAbility.getGrabber(player);
            if (body != null && body.isTemporaryFromSuit()
                    && ownerGrabber != null && ownerGrabber.getEntity() == mob) {
                // Do not classify a transient control-bit reset as a hostile
                // grab and release the authoritative friendly suit below.
                ability.suited = true;
                ability.grabbedHasControl = true;
            }
        }

        if (mob.tickCount % 20 == Math.floorMod(mob.getId(), 20)) {
            updateGenericBondedTarget(mob);
        }

        if (ability != null && ability.grabbedEntity instanceof ServerPlayer owner
                && BondedSuitService.isNativeOwnerSuit(mob, owner)) return;

        if (!mob.level().isClientSide
                && ability != null
                && ability.grabbedEntity instanceof ServerPlayer player
                && !LatexSocialMemory.mayInitiateHostileGrab(mob, player)) {
            if (LatexSocialMemory.isFriendlyArmHoldActive(mob, player)) {
                return;
            }
            boolean protectedBySocialRules =
                    LatexSocialMemory.shouldRemainNeutral(mob, player);
            if (protectedBySocialRules
                    && LatexSocialMemory.isFriendlySuitActive(mob, player)
                    && ability.suited && ability.grabbedHasControl) {
                if (ability instanceof GrabEntityAbilityExtensor extensor) {
                    extensor.setAllowGrabTransfurred(true);
                    extensor.setSafeModeAuthoritative(true);
                }
                LatexSocialEvents.calmTowards(mob, player);
                return;
            }
            ability.releaseEntity(false);
            Changed.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                    new GrabEntityPacket(mob, player, GrabType.RELEASE));
            grabber.applyGrabCooldown(20);
            if (protectedBySocialRules) {
                LatexSocialEvents.calmTowards(mob, player);
            }
        }
    }

    /** Native work keeps one goal instance; the common guard owns runtime switches. */
    private static void reconcileAddonWorkGoals(ChangedEntity mob) {
        if (!(mob instanceof TamableLatexEntityFavors favors)) return;
        if (!LatexSocialMemory.usesNativePetMenu(mob)) {
            if (mob.goalSelector.getAvailableGoals().stream().noneMatch(wrapped ->
                    net.parkabird.changedsynergy.ai.PlayerOutpostService.underlyingCompanionTask(wrapped.getGoal()) instanceof LatexFishingGoal))
                mob.goalSelector.addGoal(2, new LatexFishingGoal(favors, 0.3, 24, 3));
            if (mob.goalSelector.getAvailableGoals().stream().noneMatch(wrapped ->
                    net.parkabird.changedsynergy.ai.PlayerOutpostService.underlyingCompanionTask(wrapped.getGoal()) instanceof LatexCaveHarvestGoal))
                mob.goalSelector.addGoal(2, new LatexCaveHarvestGoal(favors, 0.3, 24, 3));
            if (mob.goalSelector.getAvailableGoals().stream().noneMatch(wrapped ->
                    net.parkabird.changedsynergy.ai.PlayerOutpostService.underlyingCompanionTask(wrapped.getGoal()) instanceof LatexCaveTorchingGoal))
                mob.goalSelector.addGoal(3, new LatexCaveTorchingGoal(favors, 0.3, 16, 3));
        }
        net.parkabird.changedsynergy.ai.PlayerOutpostService.ensureCompanionWorkControls(mob);
    }

    public static int[] getBondedMenuState(ChangedEntity pet) {
        if (!(pet instanceof TamableLatexEntityFavors favors)) {
            return new int[]{0, 1, 2, 0};
        }
        if (LatexSocialMemory.isOrganic(pet)
                && favors.getAttackType() != LatexAttackType.ALWAYS_KILL) {
            favors.setAttackType(LatexAttackType.ALWAYS_KILL);
            favors.updateHeldItemChoice();
        }
        return new int[]{
                switch (favors.getTargetType()) {
                    case TRANSFURABLE_ENTITIES -> TARGET_TRANSFURABLE;
                    case MONSTERS -> TARGET_MONSTERS;
                    case HOSTILE_TO_OWNER -> TARGET_HOSTILE_TO_OWNER;
                },
                favors.getAttackType() == LatexAttackType.ALWAYS_KILL ? ATTACK_ALWAYS_KILL : ATTACK_TRY_TRANSFUR,
                switch (favors.getAttackCondition()) {
                    case NEVER -> CONDITION_NEVER;
                    case ALWAYS -> CONDITION_ALWAYS;
                    case OWNER_IS_HOSTILE -> CONDITION_OWNER_HOSTILE;
                },
                switch (favors.getCurrentFavor()) {
                    case NONE -> FAVOR_NONE;
                    case FISHING -> FAVOR_FISHING;
                    case CAVING -> FAVOR_CAVING;
                    case SUIT_OWNER -> FAVOR_SUIT_OWNER;
                }
        };
    }

    public static boolean isNativeTamingFood(ChangedEntity creature, ItemStack stack) {
        return creature.getUnderlyingPlayer() == null
                && CreatureSocialProfile.allowsPersonalRelationship(creature)
                && creature instanceof net.foxyas.changedaddon.entity.defaults.AbstractExp2SnepChangedEntity exp2
                && !exp2.isTame() && exp2.isTameItem(stack);
    }

    public static boolean supportsBondedPetBackend(ChangedEntity pet) {
        return pet instanceof TamableLatexEntityFavors;
    }

    public static void initializeBondedCombatCondition(ChangedEntity pet) {
        if (pet instanceof TamableLatexEntityFavors favors) {
            favors.setAttackCondition(LatexAttackCondition.OWNER_IS_HOSTILE);
            pet.setTarget(null);
        }
    }

    public static boolean handleBondedMenuCommand(
            ChangedEntity pet,
            ServerPlayer owner,
            String command) {
        if (!(pet instanceof TamableLatexEntityFavors favors)
                || !LatexSocialMemory.isPetOwner(pet, owner)) {
            return false;
        }
        switch (command) {
            case "view_inventory" -> {
                return prepareBondedInventory(pet, owner) != null;
            }
            case "cycle_target_type" -> {
                favors.setTargetType(favors.getTargetType().cycle());
                pet.setTarget(null);
            }
            case "cycle_attack_type" -> {
                favors.setAttackType(LatexSocialMemory.isOrganic(pet)
                        ? LatexAttackType.ALWAYS_KILL
                        : favors.getAttackType().cycle());
                favors.updateHeldItemChoice();
            }
            case "cycle_attack_condition" -> {
                favors.setAttackCondition(favors.getAttackCondition().cycle());
                pet.setTarget(null);
            }
            case "favor_fishing" -> {
                favors.setFavor(favors.getCurrentFavor() == LatexFavor.FISHING
                        ? LatexFavor.NONE : LatexFavor.FISHING);
                favors.updateHeldItemChoice();
            }
            case "favor_caving" -> {
                favors.setFavor(favors.getCurrentFavor() == LatexFavor.CAVING
                        ? LatexFavor.NONE : LatexFavor.CAVING);
                favors.updateHeldItemChoice();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static Container prepareBondedInventory(
            ChangedEntity pet,
            ServerPlayer owner) {
        if (!(pet instanceof TamableLatexEntityFavors favors)
                || !LatexSocialMemory.isPetOwner(pet, owner)) {
            return null;
        }

        // Generic Changed entities receive Addon's pet implementation through a
        // mixin.  A social bond alone does not guarantee that the mixin's native
        // tame flag and inventory have been initialized, while Addon's inventory
        // menu requires all three pieces of state (owner, tame flag and inventory).
        favors.setOwnerUUID(owner.getUUID());
        try {
            pet.getClass().getMethod("setTame", boolean.class).invoke(pet, true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not initialize Addon tame state before opening inventory for {}",
                    pet.getType(), exception);
        }
        favors.setFollowOwner(LatexSocialMemory.isFollowingOwner(pet));
        if (favors.getInventory() == null) {
            favors.setInventory(favors.createInventory());
        }
        if (favors.getOwner() != owner || favors.getInventory() == null) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not prepare Addon inventory for {}: native owner or inventory was unavailable",
                    pet.getType());
            return null;
        }
        return favors.getInventory();
    }

    public static Container bondedInventory(ChangedEntity pet) {
        return pet instanceof TamableLatexEntityFavors favors ? favors.getInventory() : null;
    }

    public static void clearBondedFavor(ChangedEntity pet) {
        if (pet instanceof TamableLatexEntityFavors favors
                && favors.getCurrentFavor() != LatexFavor.NONE) {
            favors.setFavor(LatexFavor.NONE);
            favors.updateHeldItemChoice();
        }
    }

    public static void refreshBondedWorkItem(ChangedEntity pet) {
        if (pet instanceof TamableLatexEntityFavors favors) {
            favors.updateHeldItemChoice();
        }
    }

    /** Stops generic Changed target selectors from bypassing the wheel settings. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGenericBondedTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof ChangedEntity pet
                && event.getNewTarget() != null
                && !pet.level().isClientSide
                && !LatexSocialMemory.usesNativePetMenu(pet)
                && pet instanceof TamableLatexEntityFavors favors
                && LatexSocialMemory.hasActiveBond(pet)
                && !allowsGenericTarget(pet, favors, event.getNewTarget())) {
            event.setCanceled(true);
        }
    }

    public static void syncFriendlySuitControl(
            ChangedEntity pet,
            ServerPlayer owner,
            boolean ownerHasControl) {
        ChangedAddonMod.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> pet),
                new SyncGrabControlState(
                        pet.getId(), owner.getId(), ownerHasControl));
    }

    private static void stripOrganicSuitBehavior(ChangedEntity mob) {
        SafeEntityMutationQueue.queue(
                mob,
                "remove_organic_suit_goals",
                () -> mob.goalSelector.getAvailableGoals().stream()
                        .map(wrapped -> wrapped.getGoal())
                        .filter(LatexSuitOwnerGoal.class::isInstance)
                        .toList()
                        .forEach(mob.goalSelector::removeGoal));
        if (mob instanceof TamableLatexEntityFavors favors
                && favors.getCurrentFavor() == LatexFavor.SUIT_OWNER) {
            favors.setFavor(LatexFavor.NONE);
        }
    }

    private static void updateGenericBondedTarget(ChangedEntity pet) {
        if (LatexSocialMemory.usesNativePetMenu(pet)
                || !(pet instanceof TamableLatexEntityFavors favors)
                || !LatexSocialMemory.hasActiveBond(pet)) {
            return;
        }
        LivingEntity current = pet.getTarget();
        if (current != null && !allowsGenericTarget(pet, favors, current)) {
            pet.setTarget(null);
            current = null;
        }
        if (current != null || favors.getAttackCondition() == LatexAttackCondition.NEVER
                || grabberIsBusy(favors)) {
            return;
        }

        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner == null) {
            return;
        }

        if (favors.getAttackCondition() == LatexAttackCondition.OWNER_IS_HOSTILE) {
            LivingEntity[] conflicts = {
                    owner.getLastHurtByMob(),
                    owner.getLastHurtMob(),
                    pet.getLastHurtByMob()
            };
            for (LivingEntity conflict : conflicts) {
                if (conflict != null
                        && BondedOwnerDefenseGoal.hasRecentConflict(
                                pet, owner, conflict)
                        && allowsGenericTarget(pet, favors, conflict)) {
                    pet.setTarget(conflict);
                    break;
                }
            }
            return;
        }

        double range = Math.max(8.0, pet.getAttributeValue(Attributes.FOLLOW_RANGE));
        pet.level().getEntitiesOfClass(
                        LivingEntity.class,
                        pet.getBoundingBox().inflate(range),
                        target -> allowsGenericTarget(pet, favors, target))
                .stream()
                .min(java.util.Comparator.comparingDouble(pet::distanceToSqr))
                .ifPresent(pet::setTarget);
    }

    private static boolean allowsGenericTarget(
            ChangedEntity pet,
            TamableLatexEntityFavors favors,
            LivingEntity target) {
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner == null || target == owner || !target.isAlive()
                || target instanceof Player player
                        && (player.isCreative() || player.isSpectator())
                || target instanceof ChangedEntity other
                        && (LatexSocialMemory.hasSamePetOwner(pet, other)
                                || LatexSocialMemory.isBonded(other, owner)
                                || LatexSocialMemory.isPetOwner(other, owner)
                                || LatexCreatureCombatRules.mustRejectTarget(pet, other))) {
            return false;
        }
        if (favors.getAttackCondition() == LatexAttackCondition.NEVER
                || favors.getAttackCondition() == LatexAttackCondition.OWNER_IS_HOSTILE
                        && !BondedOwnerDefenseGoal.hasRecentConflict(
                                pet, owner, target)
                || !favors.getTargetType().test(favors, target)) {
            return false;
        }
        return !(target instanceof ServerPlayer player)
                || !LatexSocialMemory.shouldRemainNeutral(pet, player);
    }

    private static boolean grabberIsBusy(TamableLatexEntityFavors favors) {
        GrabEntityAbilityInstance ability = favors.getGrabAbility();
        return ability != null && ability.grabbedEntity != null;
    }

    public static void applyGrabCooldown(ChangedEntity mob, int ticks) {
        if (mob instanceof IGrabberEntity grabber) grabber.applyGrabCooldown(ticks);
    }

    public static GrabEntityAbilityInstance grabAbility(ChangedEntity mob) {
        return mob instanceof IGrabberEntity grabber ? grabber.getGrabAbilityInstance() : null;
    }

    private static void ensureGrabAbility(ChangedEntity mob, IGrabberEntity grabber) {
        // Native packets and Synergy use Changed's registered ability. Some
        // Addon species also create a private instance; both backends must use
        // the same object, otherwise one can release a suit the other still owns.
        GrabEntityAbilityInstance preferred = BondedSuitService.ability(mob);
        if (preferred == null) preferred = grabber.getGrabAbilityInstance();
        try {
            for (Class<?> type = mob.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!GrabEntityAbilityInstance.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(mob);
                    if (preferred == null && value instanceof GrabEntityAbilityInstance instance) {
                        preferred = instance;
                    } else if (value instanceof GrabEntityAbilityInstance instance
                            && instance != preferred && instance.grabbedEntity != null
                            && preferred.grabbedEntity == null) {
                        // Preserve an already-loaded native hold while repairing
                        // the aliases; do not recreate a suit or replay its sounds.
                        CompoundTag saved = new CompoundTag();
                        instance.saveData(saved);
                        preferred.grabbedEntity = instance.grabbedEntity;
                        preferred.readData(saved);
                        preferred.grabStrengthO = instance.grabStrengthO;
                        preferred.suitTransitionO = instance.suitTransitionO;
                        preferred.currentEscapeKey = instance.currentEscapeKey;
                        preferred.lastEscapeKey = instance.lastEscapeKey;
                        preferred.escapeKeyRandom = instance.escapeKeyRandom;
                        preferred.ticksUnpressed = instance.ticksUnpressed;
                        preferred.escapeKeys.copyFrom(instance.escapeKeys);
                        preferred.attackDown = instance.attackDown;
                        preferred.useDown = instance.useDown;
                    }
                }
            }

            if (preferred == null) {
                preferred = grabber.createGrabAbility();
            }
            if (preferred == null) {
                return;
            }

            for (Class<?> type = mob.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (GrabEntityAbilityInstance.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        field.set(mob, preferred);
                    }
                }
            }
            if (preferred instanceof GrabEntityAbilityExtensor extensor) {
                extensor.setAllowGrabTransfurred(true);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.error(
                    "Failed to initialize universal grab ability for {}", mob.getType(), exception);
        }
    }

    /** Removes both Addon's native grab goals and Synergy's replacements. */
    private static void disableGrabMechanic(
            ChangedEntity mob,
            IGrabberEntity grabber) {
        grabber.setCanUseGrab(false);
        SafeEntityMutationQueue.queue(
                mob,
                "disable_addon_grab_goals",
                () -> mob.goalSelector.getAvailableGoals().stream()
                        .map(wrapped -> wrapped.getGoal())
                        .filter(goal -> goal instanceof MayGrabTargetGoal
                                || goal instanceof MayDropGrabbedEntityGoal
                                || goal instanceof MayCauseGrabDamageGoal
                                || goal instanceof HostileTransfurredGrabGoal
                                || goal instanceof OrganicAssimilationGrabGoal
                                || goal instanceof OrganicOwnerEvacuationGoal)
                        .toList()
                        .forEach(mob.goalSelector::removeGoal));

        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (ability == null || ability.grabbedEntity == null
                || mob.level().isClientSide) {
            return;
        }
        LivingEntity grabbed = ability.grabbedEntity;
        ability.releaseEntity(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, grabbed, GrabType.RELEASE));
    }

    /** Reconciles goals after Addon and entity constructors have finished adding theirs. */
    private static void installSocialGrabGoals(ChangedEntity mob, IGrabberEntity grabber) {
        if (LatexSocialMemory.isOrganic(mob)) {
            if (mob.goalSelector.getAvailableGoals().stream()
                    .noneMatch(wrapped -> wrapped.getGoal()
                            instanceof OrganicOwnerEvacuationGoal)) {
                mob.goalSelector.addGoal(
                        -5, new OrganicOwnerEvacuationGoal(mob));
            }
            if (mob.goalSelector.getAvailableGoals().stream()
                    .noneMatch(wrapped -> wrapped.getGoal()
                            instanceof OrganicAssimilationGrabGoal)) {
                mob.goalSelector.addGoal(
                        0, new OrganicAssimilationGrabGoal(mob, grabber));
            }
        }
        List<Goal> normalGrabGoals = mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof MayGrabTargetGoal
                        && !(goal instanceof SocialMayGrabTargetGoal))
                .toList();
        normalGrabGoals.forEach(mob.goalSelector::removeGoal);
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof SocialMayGrabTargetGoal)) {
            mob.goalSelector.addGoal(10, new SocialMayGrabTargetGoal(mob, grabber));
        }

        List<Goal> normalDropGoals = mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof MayDropGrabbedEntityGoal
                        && !(goal instanceof SocialMayDropGrabbedEntityGoal))
                .toList();
        normalDropGoals.forEach(mob.goalSelector::removeGoal);
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof SocialMayDropGrabbedEntityGoal)) {
            mob.goalSelector.addGoal(9, new SocialMayDropGrabbedEntityGoal(mob, grabber));
        }

        List<Goal> normalDamageGoals = mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(MayCauseGrabDamageGoal.class::isInstance)
                .toList();
        normalDamageGoals.forEach(mob.goalSelector::removeGoal);
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof SocialGrabDamageGoal)) {
            mob.goalSelector.addGoal(8, new SocialGrabDamageGoal(mob, grabber));
        }
    }
}
