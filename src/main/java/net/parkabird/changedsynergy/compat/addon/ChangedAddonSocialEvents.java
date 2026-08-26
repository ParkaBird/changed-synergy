package net.parkabird.changedsynergy.compat.addon;

import java.lang.reflect.Field;
import java.util.List;
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
import net.foxyas.changedaddon.entity.api.TamableLatexEntityFavors;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayDropGrabbedEntityGoal;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayCauseGrabDamageGoal;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayGrabTargetGoal;
import net.foxyas.changedaddon.network.packet.SyncGrabControlState;
import net.foxyas.changedaddon.process.features.ProcessPatFeature;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondedOwnerDefenseGoal;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.ai.PatAnimationService;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySocialHugSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/** Loaded reflectively only while Changed Addon is installed. */
public final class ChangedAddonSocialEvents {
    private static final String SYNCED_NATIVE_SUIT_OWNER =
            "ChangedSynergySyncedNativeSuitOwner";
    private static final String SYNCED_NATIVE_SUIT_WAS_BONDED =
            "ChangedSynergySyncedNativeSuitWasBonded";
    private static final String ANNOUNCED_COMBAT_GRAB_TARGET =
            "ChangedSynergyAnnouncedCombatGrabTarget";

    private ChangedAddonSocialEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPat(ProcessPatFeature.GlobalPatReactionEvent event) {
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
        if (!LatexSocialMemory.isSocialLatex(mob)) {
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
                            -5, new OrganicOwnerEvacuationGoal(mob, grabber));
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

        if (!LatexSocialMemory.usesNativePetMenu(mob)
                && mob instanceof TamableLatexEntityFavors favors) {
            if (mob.goalSelector.getAvailableGoals().stream()
                    .noneMatch(wrapped -> wrapped.getGoal() instanceof LatexFishingGoal)) {
                mob.goalSelector.addGoal(2, new LatexFishingGoal(favors, 0.3, 24, 3));
            }
            if (mob.goalSelector.getAvailableGoals().stream()
                    .noneMatch(wrapped -> wrapped.getGoal() instanceof LatexCaveHarvestGoal)) {
                mob.goalSelector.addGoal(2, new LatexCaveHarvestGoal(favors, 0.3, 24, 3));
            }
            if (mob.goalSelector.getAvailableGoals().stream()
                    .noneMatch(wrapped -> wrapped.getGoal() instanceof LatexCaveTorchingGoal)) {
                mob.goalSelector.addGoal(3, new LatexCaveTorchingGoal(favors, 0.3, 16, 3));
            }
        }
    }

    /** Initializes abilities enabled after construction on both logical sides. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(mob instanceof IGrabberEntity grabber)) {
            return;
        }

        if (!mob.level().isClientSide
                && InvoluntaryTransfurNegotiation.hasReleaseHold(mob)) {
            grabber.setCanUseGrab(true);
            if (grabber.getGrabAbilityInstance() == null) {
                ensureGrabAbility(mob, grabber);
            }
            tickNegotiatedReleaseHold(
                    mob, grabber, grabber.getGrabAbilityInstance());
            return;
        }

        if (CreatureSocialProfile.isGrabMechanicExcluded(mob)) {
            grabber.setCanUseGrab(false);
            if (!mob.level().isClientSide
                    && (mob.tickCount % 20 == 0
                            || grabber.getGrabAbilityInstance() != null
                                    && grabber.getGrabAbilityInstance().grabbedEntity != null)) {
                disableGrabMechanic(mob, grabber);
            }
            return;
        }
        if (!LatexSocialMemory.isSocialLatex(mob) || !grabber.canUseGrab()) {
            return;
        }

        if (grabber.getGrabAbilityInstance() == null) {
            ensureGrabAbility(mob, grabber);
        }
        if (!mob.level().isClientSide && mob.tickCount % 20 == 0) {
            installSocialGrabGoals(mob, grabber);
        }
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
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

        if (LatexSocialMemory.hasOrganicEvacuation(mob)
                && tickOrganicEvacuationHold(mob, grabber, ability)) {
            return;
        }

        if (LatexSocialMemory.hasFriendlySocialHug(mob)) {
            tickFriendlySocialHug(mob, grabber, ability);
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
                    TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
                    if (variant != null && variant.isTemporaryFromSuit()) {
                        ProcessTransfur.removePlayerTransfurVariant(player);
                    }
                    BondedSuitService.syncOwnerSuitState(mob, player, false);
                }
                if (ability instanceof GrabEntityAbilityExtensor extensor) {
                    extensor.setSafeModeAuthoritative(false);
                }
            }
        }

        announceBondedCombatGrab(mob, ability);
        clearStaleNativeSuitSync(mob, ability);

        if (mob.tickCount % 20 == 0) {
            updateGenericBondedTarget(mob);
        }

        if (ability != null
                && ability.grabbedEntity instanceof ServerPlayer player
                && BondedSuitService.isNativeOwnerSuit(mob, player)) {
            boolean firstSync = !mob.getPersistentData().hasUUID(SYNCED_NATIVE_SUIT_OWNER);
            boolean bondedSuit = LatexSocialMemory.isFriendlySuitActive(mob, player);
            ability.grabbedHasControl = true;
            ability.suited = true;
            ability.attackDown = false;
            ability.useDown = false;
            if (LatexSocialMemory.isFriendlySuitActive(mob, player)
                    && ability instanceof GrabEntityAbilityExtensor extensor) {
                extensor.setAllowGrabTransfurred(true);
                extensor.setSafeModeAuthoritative(true);
            }
            if (mob.tickCount % 20 == 0) {
                BondedSuitService.syncOwnerSuitState(mob, player, true);
            }
            mob.getPersistentData().putUUID(SYNCED_NATIVE_SUIT_OWNER, player.getUUID());
            mob.getPersistentData().putBoolean(
                    SYNCED_NATIVE_SUIT_WAS_BONDED, bondedSuit);
            if (firstSync && !bondedSuit) {
                NpcDialogue.trigger(mob, player, Cue.BOND_WRAP_REVERTED);
            }
            LatexSocialEvents.calmTowards(mob, player);
            return;
        }

        if (!mob.level().isClientSide
                && ability != null
                && ability.grabbedEntity instanceof ServerPlayer player
                && !LatexSocialMemory.mayInitiateHostileGrab(mob, player)) {
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
                favors.getTargetType().ordinal(),
                favors.getAttackType().ordinal(),
                favors.getAttackCondition().ordinal(),
                favors.getCurrentFavor().ordinal()
        };
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

    public static boolean allowsBondedDefense(ChangedEntity pet, LivingEntity target) {
        if (LatexSocialMemory.isPetDefenseForced(pet, target)) {
            return true;
        }
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (!(pet instanceof TamableLatexEntityFavors favors)) {
            return owner != null
                    && BondedOwnerDefenseGoal.hasRecentConflict(pet, owner, target);
        }
        return favors.getAttackCondition() != LatexAttackCondition.NEVER
                && (favors.getAttackCondition() != LatexAttackCondition.OWNER_IS_HOSTILE
                        || owner != null
                                && BondedOwnerDefenseGoal.hasRecentConflict(
                                        pet, owner, target))
                && favors.getTargetType().test(favors, target);
    }

    public static boolean suppressBondedTransfurAttack(ChangedEntity pet) {
        return !LatexSocialMemory.usesNativePetMenu(pet)
                && LatexSocialMemory.hasActiveBond(pet)
                && pet instanceof TamableLatexEntityFavors favors
                && favors.getAttackType() == LatexAttackType.ALWAYS_KILL;
    }

    public static boolean isGrabberBusy(ChangedEntity entity) {
        if (!(entity instanceof IGrabberEntity grabber)) {
            return false;
        }
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        return ability != null && ability.grabbedEntity != null;
    }

    /**
     * Uses Addon's normal arm-grab pose for a brief social-wheel hug while
     * keeping the shared ability in safe mode. No hostile memory, QTE session
     * or transfur pulse is created.
     */
    public static boolean tryStartSocialHug(
            ChangedEntity mob,
            ServerPlayer player,
            int durationTicks) {
        if (mob.level().isClientSide
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()
                || mob.distanceToSqr(player) > 3.0D * 3.0D
                || HypnosisProfile.isHypnoticCreature(mob)
                || LatexSocialMemory.isProvoked(mob, player)
                || !(LatexSocialMemory.isPetOwner(mob, player)
                        || CreaturePersonality.hasTrustedRelationship(mob, player))
                || !(mob instanceof IGrabberEntity grabber)
                || !grabber.canUseGrab()
                || !grabber.canEntityGrab(mob.getType(), mob.level())
                || grabber.getGrabCooldown() > 0
                || GrabEntityAbility.getGrabber(player) != null) {
            return false;
        }

        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (!(ability instanceof GrabEntityAbilityExtensor extensor)
                || ability.grabbedEntity != null) {
            return false;
        }

        int safeDuration = Math.max(36, Math.min(80, durationTicks));
        LatexSocialMemory.beginFriendlySocialHug(mob, player, safeDuration);
        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(player)) {
            LatexSocialMemory.endFriendlySocialHug(mob);
            extensor.setSafeModeAuthoritative(false);
            return false;
        }

        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, player, GrabType.ARMS));
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FriendlySocialHugSyncPacket(
                        mob.getId(),
                        player.getId(),
                        true,
                        safeDuration + 10));
        grabber.applyGrabCooldown(0);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, player);
        ChangedSounds.broadcastSound(
                mob, ChangedSounds.LATEX_GRAB_ENTITY, 0.7F, 1.12F);
        return true;
    }

    /**
     * Starts the release agreed through negotiation. This is a scripted arm
     * hold, so it deliberately overrides ordinary grab exclusions and
     * cooldowns without enabling those creatures' combat grab AI.
     */
    public static boolean tryStartNegotiatedRelease(
            ChangedEntity mob,
            ServerPlayer player,
            int durationTicks) {
        if (mob.level().isClientSide
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()
                || mob.distanceToSqr(player) > 4.0D * 4.0D
                || !InvoluntaryTransfurNegotiation.isReleaseHoldTarget(
                        mob, player)
                || !(mob instanceof IGrabberEntity grabber)) {
            return false;
        }

        grabber.setCanUseGrab(true);
        if (grabber.getGrabAbilityInstance() == null) {
            ensureGrabAbility(mob, grabber);
        }
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (!(ability instanceof GrabEntityAbilityExtensor extensor)) {
            return false;
        }
        if (ability.grabbedEntity == player) {
            configureNegotiatedReleaseHold(mob, grabber, ability, extensor, player);
            return true;
        }
        if (ability.grabbedEntity != null
                || GrabEntityAbility.getGrabber(player) != null) {
            return false;
        }

        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(player)) {
            extensor.setSafeModeAuthoritative(false);
            return false;
        }

        int safeDuration = Math.max(36, Math.min(80, durationTicks));
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, player, GrabType.ARMS));
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FriendlySocialHugSyncPacket(
                        mob.getId(), player.getId(), true, safeDuration + 10));
        configureNegotiatedReleaseHold(mob, grabber, ability, extensor, player);
        ChangedSounds.broadcastSound(
                mob, ChangedSounds.LATEX_GRAB_ENTITY, 0.7F, 0.94F);
        return true;
    }

    /**
     * Supplies the held enemy for owner attacks that first collide with the
     * bonded creature's body. Friendly targets and targets disallowed by the
     * creature's combat wheel are never returned.
     */
    public static LivingEntity getSocialCombatGrabTarget(
            ChangedEntity pet,
            ServerPlayer owner) {
        boolean bonded = LatexSocialMemory.isBonded(pet, owner)
                || LatexSocialMemory.isPetOwner(pet, owner);
        boolean trustedFriend = CreaturePersonality.hasTrustedRelationship(
                        pet, owner)
                && !LatexSocialMemory.hasRelationshipBetrayal(pet, owner)
                && !LatexSocialMemory.isProvoked(pet, owner);
        if (!(pet instanceof IGrabberEntity grabber)
                || !bonded && !trustedFriend) {
            return null;
        }
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (bonded) {
            return bondedCombatGrabTarget(pet, owner, ability);
        }
        LivingEntity target = validCombatGrabTarget(
                pet, owner, ability);
        if (target == null) {
            return null;
        }
        return LatexSocialMemory.isPetDefenseAuthorized(pet, target)
                        || pet.getTarget() == target
                        || target.getLastHurtByMob() == pet
                ? target : null;
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
        List<Goal> suitGoals = mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(LatexSuitOwnerGoal.class::isInstance)
                .toList();
        suitGoals.forEach(mob.goalSelector::removeGoal);
        if (mob instanceof TamableLatexEntityFavors favors
                && favors.getCurrentFavor() == LatexFavor.SUIT_OWNER) {
            favors.setFavor(LatexFavor.NONE);
        }
    }

    private static void clearStaleNativeSuitSync(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability) {
        var data = mob.getPersistentData();
        if (!data.hasUUID(SYNCED_NATIVE_SUIT_OWNER)) {
            return;
        }
        java.util.UUID ownerUuid = data.getUUID(SYNCED_NATIVE_SUIT_OWNER);
        boolean wasBonded = data.getBoolean(SYNCED_NATIVE_SUIT_WAS_BONDED);
        boolean stillActive = ability != null
                && ability.grabbedEntity instanceof ServerPlayer current
                && current.getUUID().equals(ownerUuid)
                && BondedSuitService.isNativeOwnerSuit(mob, current);
        if (stillActive) {
            return;
        }

        if (mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                BondedSuitService.syncOwnerSuitState(mob, owner, false);
                if (!wasBonded) {
                    NpcDialogue.trigger(mob, owner, Cue.BOND_RELEASE_REVERTED);
                }
            }
        }
        data.remove(SYNCED_NATIVE_SUIT_OWNER);
        data.remove(SYNCED_NATIVE_SUIT_WAS_BONDED);
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
        if (LatexSocialMemory.isPetDefenseForced(pet, target)) {
            return true;
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

    private static LivingEntity bondedCombatGrabTarget(
            ChangedEntity pet,
            ServerPlayer owner,
            GrabEntityAbilityInstance ability) {
        LivingEntity target = validCombatGrabTarget(
                pet, owner, ability);
        if (target == null) {
            return null;
        }
        if (!LatexSocialMemory.usesNativePetMenu(pet)
                && pet instanceof TamableLatexEntityFavors favors) {
            return allowsGenericTarget(pet, favors, target) ? target : null;
        }
        return BondedOwnerDefenseGoal.allowsConfiguredDefense(
                pet, owner, target) ? target : null;
    }

    /** Common friendly-fire exclusions shared by bonded and friend grabs. */
    private static LivingEntity validCombatGrabTarget(
            ChangedEntity pet,
            ServerPlayer owner,
            GrabEntityAbilityInstance ability) {
        if (ability == null || ability.suited
                || ability.grabbedEntity == null) {
            return null;
        }

        LivingEntity target = ability.grabbedEntity;
        if (target == pet || target == owner || !target.isAlive()
                || target.isRemoved() || target.isAlliedTo(owner)
                || pet.isAlliedTo(target) || !pet.canAttack(target)) {
            return null;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator()
                        || !owner.canHarmPlayer(player))) {
            return null;
        }
        if (target instanceof ServerPlayer player
                && LatexSocialMemory.shouldRemainNeutral(pet, player)) {
            return null;
        }
        if (target instanceof ChangedEntity other
                && (LatexSocialMemory.hasSamePetOwner(pet, other)
                        || LatexSocialMemory.isBonded(other, owner)
                        || LatexSocialMemory.isPetOwner(other, owner)
                        || CreaturePersonality.hasTrustedRelationship(
                                other, owner)
                        || LatexCreatureCombatRules.mustRejectTarget(
                                pet, other))) {
            return null;
        }
        return target;
    }

    private static void announceBondedCombatGrab(
            ChangedEntity pet,
            GrabEntityAbilityInstance ability) {
        var data = pet.getPersistentData();
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        LivingEntity target = owner == null
                ? null
                : bondedCombatGrabTarget(pet, owner, ability);
        if (target == null) {
            data.remove(ANNOUNCED_COMBAT_GRAB_TARGET);
            return;
        }
        if (data.hasUUID(ANNOUNCED_COMBAT_GRAB_TARGET)
                && data.getUUID(ANNOUNCED_COMBAT_GRAB_TARGET)
                        .equals(target.getUUID())) {
            return;
        }

        double range = ChangedSynergyConfig.COMMON.npcDialogueRange.get();
        if (owner.level() != pet.level()
                || owner.distanceToSqr(pet) > range * range) {
            return;
        }
        data.putUUID(ANNOUNCED_COMBAT_GRAB_TARGET, target.getUUID());
        NpcDialogue.trigger(pet, owner, Cue.BOND_COMBAT_GRAB_ASSIST);
    }

    private static boolean grabberIsBusy(TamableLatexEntityFavors favors) {
        GrabEntityAbilityInstance ability = favors.getGrabAbility();
        return ability != null && ability.grabbedEntity != null;
    }

    private static void tickFriendlySocialHug(
            ChangedEntity mob,
            IGrabberEntity grabber,
            GrabEntityAbilityInstance ability) {
        ServerPlayer player =
                ability != null
                                && ability.grabbedEntity instanceof ServerPlayer held
                                && LatexSocialMemory.isFriendlySocialHugTarget(
                                        mob, held)
                        ? held
                        : null;
        if (!(ability instanceof GrabEntityAbilityExtensor extensor)
                || player == null
                || !LatexSocialMemory.isFriendlySocialHugActive(mob, player)
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()) {
            if (player != null) {
                ability.attackDown = false;
                ability.useDown = false;
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                        new GrabEntityPacket(mob, player, GrabType.RELEASE));
                ChangedSynergyNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new FriendlySocialHugSyncPacket(
                                mob.getId(), player.getId(), false, 0));
                if (mob.isAlive()
                        && player.isAlive()
                        && mob.level() == player.level()) {
                    NpcDialogue.trigger(
                            mob,
                            player,
                            LatexSocialMemory.isPetOwner(mob, player)
                                    ? NpcDialogue.Cue.SOCIAL_PLAY_HUG_RELEASE_BONDED
                                    : NpcDialogue.Cue.SOCIAL_PLAY_HUG_RELEASE_FRIEND);
                }
            }
            if (ability instanceof GrabEntityAbilityExtensor staleExtensor
                    && (ability.grabbedEntity == null || player != null)) {
                staleExtensor.setSafeModeAuthoritative(false);
            }
            LatexSocialMemory.endFriendlySocialHug(mob);
            grabber.applyGrabCooldown(30);
            return;
        }

        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, player);
    }

    private static void tickNegotiatedReleaseHold(
            ChangedEntity mob,
            IGrabberEntity grabber,
            GrabEntityAbilityInstance ability) {
        ServerPlayer player = InvoluntaryTransfurNegotiation
                .releaseHoldPlayer(mob);
        if (!(ability instanceof GrabEntityAbilityExtensor extensor)
                || player == null
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()
                || ability.grabbedEntity != player) {
            releaseNegotiatedHold(mob, grabber, ability, player);
            InvoluntaryTransfurNegotiation.abortReleaseHold(mob, player);
            return;
        }

        configureNegotiatedReleaseHold(mob, grabber, ability, extensor, player);
        InvoluntaryTransfurNegotiation.ReleaseHoldStep step =
                InvoluntaryTransfurNegotiation.advanceReleaseHold(mob, player);
        if (step == InvoluntaryTransfurNegotiation.ReleaseHoldStep.COMPLETE) {
            releaseNegotiatedHold(mob, grabber, ability, player);
            InvoluntaryTransfurNegotiation.finishReleaseHold(mob, player);
        } else if (step == InvoluntaryTransfurNegotiation.ReleaseHoldStep.ABORT) {
            releaseNegotiatedHold(mob, grabber, ability, player);
            InvoluntaryTransfurNegotiation.abortReleaseHold(mob, player);
        }
    }

    private static void configureNegotiatedReleaseHold(
            ChangedEntity mob,
            IGrabberEntity grabber,
            GrabEntityAbilityInstance ability,
            GrabEntityAbilityExtensor extensor,
            ServerPlayer player) {
        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        grabber.applyGrabCooldown(0);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, player);
    }

    private static void releaseNegotiatedHold(
            ChangedEntity mob,
            IGrabberEntity grabber,
            GrabEntityAbilityInstance ability,
            ServerPlayer player) {
        LivingEntity held = ability == null ? null : ability.grabbedEntity;
        if (ability != null) {
            ability.attackDown = false;
            ability.useDown = false;
            if (held != null) {
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                        new GrabEntityPacket(mob, held, GrabType.RELEASE));
            }
            if (ability instanceof GrabEntityAbilityExtensor extensor) {
                extensor.setSafeModeAuthoritative(false);
            }
        }
        if (player != null) {
            ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new FriendlySocialHugSyncPacket(
                            mob.getId(), player.getId(), false, 0));
        }
        grabber.applyGrabCooldown(30);
    }

    /** Keeps the owner's evacuation hold safe without stopping the carrier's navigation. */
    private static boolean tickOrganicEvacuationHold(
            ChangedEntity mob,
            IGrabberEntity grabber,
            GrabEntityAbilityInstance ability) {
        ServerPlayer player = ability != null
                        && ability.grabbedEntity instanceof ServerPlayer held
                        && LatexSocialMemory.isOrganicEvacuationTarget(mob, held)
                ? held
                : null;
        if (!(ability instanceof GrabEntityAbilityExtensor extensor)
                || player == null
                || !LatexSocialMemory.isOrganicEvacuationActive(mob, player)
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()) {
            if (player != null && ability != null) {
                ability.attackDown = false;
                ability.useDown = false;
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                        new GrabEntityPacket(mob, player, GrabType.RELEASE));
                ChangedSynergyNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new FriendlySocialHugSyncPacket(
                                mob.getId(), player.getId(), false, 0));
            }
            if (ability instanceof GrabEntityAbilityExtensor stale) {
                stale.setSafeModeAuthoritative(false);
            }
            LatexSocialMemory.endOrganicEvacuation(mob);
            return false;
        }

        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        mob.setTarget(null);
        mob.setAggressive(false);
        LatexSocialEvents.calmTowards(mob, player);
        return true;
    }

    private static void ensureGrabAbility(ChangedEntity mob, IGrabberEntity grabber) {
        GrabEntityAbilityInstance preferred = grabber.getGrabAbilityInstance();
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
        List<Goal> grabGoals = mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof MayGrabTargetGoal
                        || goal instanceof MayDropGrabbedEntityGoal
                        || goal instanceof MayCauseGrabDamageGoal
                        || goal instanceof HostileTransfurredGrabGoal
                        || goal instanceof OrganicAssimilationGrabGoal
                        || goal instanceof OrganicOwnerEvacuationGoal)
                .toList();
        grabGoals.forEach(mob.goalSelector::removeGoal);

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
                        -5, new OrganicOwnerEvacuationGoal(mob, grabber));
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
