package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.RoutineState;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.FishingSite;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.GlowBerrySite;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.MinecartSupplyTarget;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;

/**
 * Low-priority role work. It has no clock or personal activity centre: a free
 * creature simply performs the next useful job for its stable role. Combat,
 * relationships, rescues and direct social interaction always pre-empt it.
 */
public final class CreatureRoutineGoal extends Goal {
    public static final int PRIORITY = 4;
    private static final String NEXT_PRESENTATION =
            "ChangedSynergyNextRoutinePresentation";
    private static final double PRESENTATION_RANGE_SQR = 18.0D * 18.0D;
    private static final int FISHING_SEARCH_RADIUS = 48;
    private static final int NEARSHORE_FISHING_SEARCH_RADIUS = 64;
    private static final int CLIMATE_FISHING_SEARCH_RADIUS = 56;
    private static final int FISHING_VERTICAL_RADIUS = 8;
    private static final double OFFSHORE_FISH_SEARCH_RADIUS = 32.0D;

    private final ChangedEntity mob;
    private RoutineState state = RoutineState.IDLE;
    @Nullable private Vec3 destination;
    @Nullable private ItemEntity itemTarget;
    @Nullable private BlockPos blockTarget;
    @Nullable private FishingSite fishingSite;
    @Nullable private AbstractFish fishTarget;
    @Nullable private Animal preyTarget;
    @Nullable private MinecartChest minecartTarget;
    @Nullable private BlockPos minecartStand;
    @Nullable private GlowBerrySite glowBerrySite;
    @Nullable private BlockState openedIceState;
    private boolean sweetBerryTarget;
    @Nullable private ChangedEntity peer;
    private ItemStack previousMainHand = ItemStack.EMPTY;
    private boolean toolVisible;
    private int remainingTicks;
    private int repathTicks;
    private int dwellTicks;
    private int actionTicks;
    private int shoreTransitionTicks;
    private boolean fishFromWaterFallback;
    private boolean offshoreFishing;

    public CreatureRoutineGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)
                || !CreatureLifeMemory.enabled(mob)
                || !movementAvailable()
                || mob.level().getGameTime()
                        < CreatureLifeMemory.nextDecisionTick(mob)) {
            return false;
        }
        CreatureLifeMemory.ensure(mob);
        CreatureCommunityData.bind(mob);
        clearTargets();
        state = CreatureSettlementService.hasCargo(mob)
                ? RoutineState.DELIVERING : selectActivity();
        destination = chooseDestination();
        if (destination == null) {
            state = fallbackActivity();
            destination = chooseDestination();
        }
        remainingTicks = duration(state);
        if (minecartTarget != null) {
            int travelBudget = 900 + (int)Math.ceil(
                    Math.sqrt(mob.distanceToSqr(minecartTarget)) * 14.0D);
            remainingTicks = Math.max(
                    remainingTicks, Math.min(2600, travelBudget));
        }
        return state != RoutineState.IDLE && destination != null;
    }

    @Override
    public boolean canContinueToUse() {
        return remainingTicks > 0
                && CreatureLifeMemory.enabled(mob)
                && movementAvailable()
                && (peer == null || peer.isAlive() && !peer.isRemoved())
                && (fishTarget == null
                        || fishTarget.isAlive() && !fishTarget.isRemoved())
                && (preyTarget == null
                        || preyTarget.isAlive() && !preyTarget.isRemoved())
                && (minecartTarget == null
                        || minecartTarget.isAlive()
                                && !minecartTarget.isRemoved());
    }

    @Override
    public void start() {
        repathTicks = 0;
        dwellTicks = 0;
        actionTicks = 0;
        CreatureLifeMemory.beginRoutine(mob, state, mob.level().getGameTime());
        if (state == RoutineState.FISHING && !offshoreFishing) {
            showTool(new ItemStack(Items.FISHING_ROD));
        } else if (state == RoutineState.MINING) {
            showTool(new ItemStack(Items.IRON_PICKAXE));
        } else if (state == RoutineState.GATHERING
                && minecartTarget != null) {
            showTool(new ItemStack(Items.COMPASS));
        }
        presentActivityStart();
        moveToDestination();
    }

    @Override
    public void tick() {
        remainingTicks--;
        if (destination == null) {
            remainingTicks = 0;
            return;
        }
        if (state == RoutineState.GATHERING
                && CreatureSettlementService.isFacilityCommunity(mob)
                && !CreatureSettlementService.isInsideFacilityWorkSection(mob)) {
            // A door state can make navigation recalculate a previously valid
            // gathering route. Abort rather than allowing that repath to carry
            // a worker through another coloured facility sector. Delivery is
            // deliberately exempt: combat may displace a loaded provisioner,
            // and it must be allowed to return to its remembered storage room.
            mob.getNavigation().stop();
            remainingTicks = 0;
            return;
        }
        refreshMovingTarget();
        if (advanceFromWaterOntoFishingStand()) {
            return;
        }
        double arrival = switch (state) {
            case GATHERING -> blockTarget != null
                    ? 3.6D * 3.6D : 3.1D * 3.1D;
            case MINING -> 3.1D * 3.1D;
            case TENDING, PLAYING -> 2.7D * 2.7D;
            case FISHING -> offshoreFishing
                    ? 2.7D * 2.7D : 1.9D * 1.9D;
            default -> 1.9D * 1.9D;
        };
        if (mob.distanceToSqr(destination) > arrival) {
            if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
                repathTicks = 16;
                moveToDestination();
            }
            return;
        }
        mob.getNavigation().stop();
        switch (state) {
            case DELIVERING -> finishDelivery();
            case GATHERING -> finishGathering();
            case FISHING -> tickFishing();
            case MINING -> tickMining();
            case TENDING -> finishTending();
            case SCOUTING, GUARDING, PLAYING -> tickLocalActivity();
            default -> remainingTicks = 0;
        }
    }

    @Override
    public void stop() {
        boolean resumeCargoAfterCombat = CreatureSettlementService.hasCargo(mob)
                && hasLiveCombatTarget();
        mob.getNavigation().stop();
        if (mob.level() instanceof ServerLevel level) {
            FishingVisualEffects.cancel(level, mob);
            CreatureSettlementService.restoreIceFishingHole(
                    level, fishingSite, openedIceState);
        }
        CreatureSettlementService.releaseHuntClaim(mob, preyTarget);
        clearMiningProgress();
        restoreTool();
        long now = mob.level().getGameTime();
        CreatureLifeMemory.finishRoutine(mob,
                resumeCargoAfterCombat
                        ? now + 10L
                        : now + 80L + mob.getRandom().nextInt(161));
        state = RoutineState.IDLE;
        clearTargets();
        remainingTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private RoutineState selectActivity() {
        GroupRole role = CreatureLifeMemory.role(mob);
        return switch (role) {
            case SCOUT -> RoutineState.SCOUTING;
            case GUARD -> RoutineState.GUARDING;
            case YOUNGSTER -> RoutineState.PLAYING;
            case PROVISIONER -> selectProvisionerActivity();
        };
    }

    private RoutineState selectProvisionerActivity() {
        if (findPatient() != null && mob.getRandom().nextInt(100) < 28) {
            return RoutineState.TENDING;
        }
        if (CreatureSettlementService.isFacilityCommunity(mob)) {
            return RoutineState.GATHERING;
        }
        if (CreatureSettlementService.isCaveCommunity(mob)) {
            return mob.getRandom().nextInt(100) < 82
                    ? RoutineState.MINING : RoutineState.GATHERING;
        }
        if (CreatureSettlementService.isBadlandsCommunity(mob)) {
            return CreatureSettlementService.canFish(mob)
                            && mob.getRandom().nextInt(100) < 42
                    ? RoutineState.FISHING : RoutineState.GATHERING;
        }
        if (CreatureSettlementService.usesClimateHunting(mob)) {
            return CreatureSettlementService.canFish(mob)
                            && mob.getRandom().nextInt(100) < 55
                    ? RoutineState.FISHING : RoutineState.GATHERING;
        }
        if (CreatureSettlementService.canFish(mob)
                && mob.getRandom().nextInt(100) < 75) {
            return RoutineState.FISHING;
        }
        return RoutineState.GATHERING;
    }

    private RoutineState fallbackActivity() {
        return CreatureLifeMemory.role(mob) == GroupRole.GUARD
                ? RoutineState.GUARDING : RoutineState.SCOUTING;
    }

    @Nullable
    private Vec3 chooseDestination() {
        return switch (state) {
            case DELIVERING -> HunterFaction.of(mob) == HunterFaction.WHITE
                    && !CreatureSettlementService.isFacilityCommunity(mob)
                    ? mob.position()
                    : CreatureSettlementService.ensureCachePosition(mob)
                            .map(Vec3::atCenterOf)
                            .orElse(null);
            case GATHERING -> gatheringDestination();
            case FISHING -> fishingDestination();
            case MINING -> miningDestination();
            case TENDING -> tendingDestination();
            case GUARDING -> guardDestination();
            case PLAYING -> peerDestination(true);
            case SCOUTING -> DefaultRandomPos.getPos(mob, 14, 7);
            default -> null;
        };
    }

    @Nullable
    private Vec3 gatheringDestination() {
        if (CreatureSettlementService.isFacilityCommunity(mob)) {
            // Do not harvest until real in-sector storage is available; otherwise
            // a worker could carry an orange indefinitely or fall back to
            // building a bespoke underground cache.
            if (CreatureSettlementService.ensureCachePosition(mob).isEmpty()) {
                return null;
            }
            blockTarget = CreatureSettlementService.findOrangeLeaves(mob, 20, 8)
                    .orElse(null);
            return blockTarget == null
                    ? null : Vec3.atCenterOf(blockTarget);
        } else if (CreatureSettlementService.isCaveCommunity(mob)) {
            glowBerrySite = CreatureSettlementService.findGlowBerrySite(
                            mob, 16, 12, 6)
                    .orElse(null);
            if (glowBerrySite != null) {
                return Vec3.atBottomCenterOf(glowBerrySite.stand());
            }
        } else if (CreatureSettlementService.isTaigaCommunity(mob)) {
            blockTarget = CreatureSettlementService.findSweetBerryBush(
                            mob, 16, 6)
                    .orElse(null);
            sweetBerryTarget = blockTarget != null;
            if (blockTarget != null) {
                return Vec3.atCenterOf(blockTarget);
            }
        } else if (CreatureSettlementService.isBadlandsCommunity(mob)) {
            MinecartSupplyTarget supply = CreatureSettlementService
                    .findBadlandsSupplyMinecart(mob)
                    .orElse(null);
            if (supply != null) {
                minecartTarget = supply.cart();
                minecartStand = supply.stand();
                return Vec3.atBottomCenterOf(minecartStand);
            }
            preyTarget = CreatureSettlementService.findHuntPrey(mob, 42.0D)
                    .orElse(null);
            if (preyTarget != null) {
                return preyTarget.position();
            }
        } else if (CreatureSettlementService.usesClimateHunting(mob)) {
            preyTarget = CreatureSettlementService.findHuntPrey(mob, 42.0D)
                    .orElse(null);
            if (preyTarget != null) {
                return preyTarget.position();
            }
        } else {
            blockTarget = CreatureSettlementService.findOrangeLeaves(mob, 12, 4)
                    .orElse(null);
            if (blockTarget != null) {
                return Vec3.atCenterOf(blockTarget);
            }
        }
        itemTarget = CreatureSettlementService.findResource(mob, 14.0D)
                .orElse(null);
        return itemTarget == null ? null : itemTarget.position();
    }

    @Nullable
    private Vec3 fishingDestination() {
        if (CreatureSettlementService.usesOpenOceanHarvest(mob)) {
            offshoreFishing = true;
            // Do not remove fish from the world until this population has a
            // real submerged cache beside a permitted ocean structure.
            if (CreatureSettlementService.ensureCachePosition(mob).isEmpty()) {
                return null;
            }
            fishTarget = CreatureSettlementService.findSeaFish(
                            mob, OFFSHORE_FISH_SEARCH_RADIUS)
                    .orElse(null);
            return fishTarget == null ? null : fishTarget.position();
        }
        offshoreFishing = false;
        int searchRadius = HunterArchetype.of(mob) == HunterArchetype.AQUATIC
                ? NEARSHORE_FISHING_SEARCH_RADIUS
                : CreatureSettlementService.usesClimateHunting(mob)
                        ? CLIMATE_FISHING_SEARCH_RADIUS
                        : FISHING_SEARCH_RADIUS;
        fishingSite = CreatureSettlementService.findFishingSite(
                        mob, searchRadius, FISHING_VERTICAL_RADIUS)
                .orElse(null);
        if (fishingSite == null
                && CreatureSettlementService.usesClimateHunting(mob)) {
            state = RoutineState.GATHERING;
            return gatheringDestination();
        }
        return fishingSite == null
                ? null : CreatureSettlementService.fishingApproach(mob, fishingSite);
    }

    @Nullable
    private Vec3 miningDestination() {
        blockTarget = CreatureSettlementService.findExposedOre(mob, 12, 5)
                .orElse(null);
        return blockTarget == null ? null : Vec3.atCenterOf(blockTarget);
    }

    @Nullable
    private Vec3 tendingDestination() {
        peer = findPatient();
        return peer == null ? null : peer.position();
    }

    @Nullable
    private ChangedEntity findPatient() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntitiesOfClass(
                        ChangedEntity.class,
                        mob.getBoundingBox().inflate(12.0D),
                        candidate -> candidate != mob
                                && candidate.isAlive()
                                && candidate.getHealth()
                                        < candidate.getMaxHealth() - 0.5F
                                && CreatureCommunityData.sameCommunity(mob, candidate))
                .stream()
                .min(Comparator.comparingDouble(candidate ->
                        candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);
    }

    @Nullable
    private Vec3 guardDestination() {
        BlockPos center = CreatureCacheGuardService.patrolCache(mob)
                .orElseGet(() -> CreatureCommunityData.snapshot(mob)
                        .map(CreatureCommunityData.Snapshot::center)
                        .orElse(mob.blockPosition()));
        double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 5.0D + mob.getRandom().nextDouble() * 6.0D;
        Vec3 desired = new Vec3(
                center.getX() + 0.5D + Math.cos(angle) * radius,
                center.getY(),
                center.getZ() + 0.5D + Math.sin(angle) * radius);
        Vec3 pathable = DefaultRandomPos.getPosTowards(
                mob, 10, 6, desired, Math.PI / 2.0D);
        return pathable == null ? desired : pathable;
    }

    @Nullable
    private Vec3 peerDestination(boolean youngster) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        peer = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        mob.getBoundingBox().inflate(12.0D),
                        candidate -> candidate != mob
                                && candidate.isAlive()
                                && CreatureCommunityData.sameCommunity(mob, candidate)
                                && (!youngster || CreatureLifeMemory.role(candidate)
                                        != GroupRole.YOUNGSTER))
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
        if (peer == null) {
            return DefaultRandomPos.getPos(mob, 8, 4);
        }
        return peer.position().add(
                mob.getRandom().nextDouble() * 1.6D - 0.8D,
                0.0D,
                mob.getRandom().nextDouble() * 1.6D - 0.8D);
    }

    private void refreshMovingTarget() {
        if (itemTarget != null) {
            if (!itemTarget.isAlive() || itemTarget.isRemoved()) {
                remainingTicks = 0;
            } else {
                destination = itemTarget.position();
            }
        } else if (fishTarget != null) {
            if (!fishTarget.isAlive() || fishTarget.isRemoved()) {
                remainingTicks = 0;
            } else {
                destination = fishTarget.position();
                mob.getLookControl().setLookAt(fishTarget, 28.0F, 24.0F);
            }
        } else if (preyTarget != null) {
            if (!preyTarget.isAlive() || preyTarget.isRemoved()) {
                remainingTicks = 0;
            } else {
                destination = preyTarget.position();
                mob.getLookControl().setLookAt(preyTarget, 28.0F, 24.0F);
            }
        } else if (minecartTarget != null) {
            if (!minecartTarget.isAlive() || minecartTarget.isRemoved()) {
                remainingTicks = 0;
            } else {
                if (minecartStand == null
                        || minecartTarget.distanceToSqr(
                                Vec3.atBottomCenterOf(minecartStand))
                                > 4.5D * 4.5D) {
                    remainingTicks = 0;
                    return;
                }
                destination = Vec3.atBottomCenterOf(minecartStand);
                if (mob.tickCount % 80 == 0) {
                    CreatureSettlementService.refreshMinecartClaim(
                            mob, minecartTarget);
                }
                mob.getLookControl().setLookAt(
                        minecartTarget, 28.0F, 24.0F);
            }
        } else if (peer != null) {
            destination = peer.position();
            mob.getLookControl().setLookAt(peer, 24.0F, 22.0F);
        }
    }

    private void finishDelivery() {
        if (CreatureSettlementService.depositCargo(mob)) {
            presentRoleAction(Cue.ROLE_FORAGER_STORE);
        }
        remainingTicks = 0;
    }

    private void finishGathering() {
        boolean gathered = false;
        if (minecartTarget != null) {
            mob.getLookControl().setLookAt(minecartTarget, 30.0F, 24.0F);
            if (++actionTicks == 5) {
                mob.swing(InteractionHand.MAIN_HAND);
            }
            if (actionTicks < 12) {
                return;
            }
            gathered = CreatureSettlementService.harvestBadlandsMinecartFood(
                    mob, minecartTarget);
        } else if (preyTarget != null) {
            mob.getLookControl().setLookAt(preyTarget, 30.0F, 24.0F);
            if (++actionTicks == 5) {
                mob.swing(InteractionHand.MAIN_HAND);
            }
            if (actionTicks < 12) {
                return;
            }
            gathered = CreatureSettlementService.harvestPrey(mob, preyTarget);
        } else if (glowBerrySite != null) {
            mob.getLookControl().setLookAt(
                    Vec3.atCenterOf(glowBerrySite.berries()));
            mob.swing(InteractionHand.MAIN_HAND);
            gathered = CreatureSettlementService.harvestGlowBerries(
                    mob, glowBerrySite.berries());
        } else if (blockTarget != null) {
            mob.getLookControl().setLookAt(Vec3.atCenterOf(blockTarget));
            mob.swing(InteractionHand.MAIN_HAND);
            gathered = sweetBerryTarget
                    ? CreatureSettlementService.harvestSweetBerries(
                            mob, blockTarget)
                    : CreatureSettlementService.harvestOrangeLeaves(
                            mob, blockTarget);
        } else if (itemTarget != null) {
            gathered = CreatureSettlementService.collect(mob, itemTarget);
        }
        if (gathered) {
            presentRoleAction(Cue.ROLE_FORAGER_FOUND);
            transitionToDelivery();
        } else {
            remainingTicks = 0;
        }
    }

    private void tickFishing() {
        if (offshoreFishing) {
            tickOffshoreHarvest();
            return;
        }
        if (fishingSite == null || !(mob.level() instanceof ServerLevel level)) {
            remainingTicks = 0;
            return;
        }
        mob.getLookControl().setLookAt(Vec3.atCenterOf(fishingSite.water()));
        if (actionTicks == 0 && fishingSite.iceCovered()) {
            openedIceState = CreatureSettlementService.openIceFishingHole(
                    level, fishingSite);
            if (openedIceState == null) {
                remainingTicks = 0;
                return;
            }
        }
        if (actionTicks++ == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level().playSound(null, mob.blockPosition(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    0.55F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
            FishingVisualEffects.cast(level, mob, fishingSite.water());
            FishingVisualEffects.setRodCastModel(mob, true);
        }
        FishingVisualEffects.tick(
                level, mob, fishingSite.water(), actionTicks);
        if (actionTicks < 160) {
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        FishingVisualEffects.setRodCastModel(mob, false);
        if (CreatureSettlementService.catchFish(mob, fishingSite.water())) {
            FishingVisualEffects.retrieve(
                    level, mob, fishingSite.water(),
                    CreatureSettlementService.cargo(mob));
            presentRoleAction(Cue.ROLE_FORAGER_FOUND);
            restoreIceHole(level);
            transitionToDelivery();
        } else {
            FishingVisualEffects.retrieve(
                    level, mob, fishingSite.water(), ItemStack.EMPTY);
            restoreIceHole(level);
            remainingTicks = 0;
        }
    }

    /** Pelagic communities catch nearby fish directly instead of imitating a
     * shore angler in open water. The fish remains a moving target until the
     * creature is close enough to make one short capture attempt. */
    private void tickOffshoreHarvest() {
        if (!(mob.level() instanceof ServerLevel)
                || fishTarget == null
                || !fishTarget.isAlive()
                || fishTarget.isRemoved()) {
            remainingTicks = 0;
            return;
        }
        destination = fishTarget.position();
        mob.getLookControl().setLookAt(fishTarget, 30.0F, 26.0F);
        if (mob.distanceToSqr(fishTarget) > 3.0D * 3.0D) {
            return;
        }
        actionTicks++;
        if (actionTicks == 5) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (actionTicks < 12) {
            return;
        }
        if (CreatureSettlementService.captureSeaFish(mob, fishTarget)) {
            presentRoleAction(Cue.ROLE_FORAGER_FOUND);
            transitionToDelivery();
        } else {
            remainingTicks = 0;
        }
    }

    /**
     * Changed's fully aquatic creatures swap between water and ground
     * navigation. Their water navigator cannot path to the dry shore block,
     * so approach the adjacent surface-water node first and then make a short
     * physical transition onto land. If a large model cannot clear that edge,
     * it still fishes from the shoreline water instead of abandoning the job.
     */
    private boolean advanceFromWaterOntoFishingStand() {
        if (state != RoutineState.FISHING || offshoreFishing
                || fishingSite == null
                || fishFromWaterFallback) {
            return false;
        }
        Vec3 stand = Vec3.atBottomCenterOf(fishingSite.stand());
        if (!CreatureSettlementService.approachesFishingSiteThroughWater(mob)) {
            if (shoreTransitionTicks > 0) {
                shoreTransitionTicks = 0;
                destination = stand;
                moveToDestination();
            }
            return false;
        }
        Vec3 water = Vec3.atCenterOf(fishingSite.water());
        if (mob.distanceToSqr(water) > 2.25D) {
            destination = water;
            return false;
        }
        if (++shoreTransitionTicks > 90) {
            fishFromWaterFallback = true;
            destination = mob.position();
            return false;
        }

        destination = stand;
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(stand);
        mob.getMoveControl().setWantedPosition(
                stand.x, stand.y, stand.z, 0.30D);
        Vec3 offset = stand.subtract(mob.position());
        double horizontal = Math.sqrt(
                offset.x * offset.x + offset.z * offset.z);
        if (horizontal > 0.01D) {
            double lift = offset.y > 0.2D ? 0.04D : 0.0D;
            mob.setDeltaMovement(mob.getDeltaMovement().add(
                    offset.x / horizontal * 0.028D,
                    lift,
                    offset.z / horizontal * 0.028D));
        }
        return true;
    }

    private void tickMining() {
        if (!(mob.level() instanceof ServerLevel level) || blockTarget == null) {
            remainingTicks = 0;
            return;
        }
        mob.getLookControl().setLookAt(Vec3.atCenterOf(blockTarget));
        actionTicks++;
        int stage = Math.min(9, actionTicks / 7);
        level.destroyBlockProgress(mob.getId(), blockTarget, stage);
        if (actionTicks % 10 == 1) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (actionTicks < 70) {
            return;
        }
        clearMiningProgress();
        if (CreatureSettlementService.mineOre(mob, blockTarget)) {
            presentRoleAction(Cue.ROLE_FORAGER_FOUND);
            transitionToDelivery();
        } else {
            remainingTicks = 0;
        }
    }

    private void finishTending() {
        if (peer != null && peer.isAlive()
                && peer.getHealth() < peer.getMaxHealth()) {
            CreatureRoleService.aidDuringRoutine(mob, peer);
        }
        remainingTicks = 0;
    }

    private void tickLocalActivity() {
        if (peer != null) {
            mob.getLookControl().setLookAt(peer, 24.0F, 20.0F);
        }
        if (++dwellTicks < 35 + mob.getRandom().nextInt(36)) {
            return;
        }
        if (state == RoutineState.GUARDING) {
            CreatureLifeMemory.incrementRoleStat(mob, 2);
        } else if (state == RoutineState.PLAYING) {
            CreatureLifeMemory.incrementRoleStat(mob, 2);
            mob.getJumpControl().jump();
        }
        dwellTicks = 0;
        destination = chooseDestination();
        if (destination == null) {
            remainingTicks = 0;
        } else {
            moveToDestination();
        }
    }

    private void transitionToDelivery() {
        if (mob.level() instanceof ServerLevel level) {
            restoreIceHole(level);
        }
        clearMiningProgress();
        restoreTool();
        state = RoutineState.DELIVERING;
        CreatureLifeMemory.beginRoutine(mob, state, mob.level().getGameTime());
        destination = chooseDestination();
        itemTarget = null;
        blockTarget = null;
        fishingSite = null;
        fishTarget = null;
        preyTarget = null;
        minecartTarget = null;
        minecartStand = null;
        glowBerrySite = null;
        sweetBerryTarget = false;
        actionTicks = 0;
        remainingTicks = Math.max(remainingTicks, 360);
        if (destination == null) {
            remainingTicks = 0;
        } else {
            moveToDestination();
        }
    }

    private void showTool(ItemStack tool) {
        if (toolVisible) {
            return;
        }
        previousMainHand = mob.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        mob.setItemSlot(EquipmentSlot.MAINHAND, tool);
        toolVisible = true;
    }

    private void restoreTool() {
        if (!toolVisible) {
            return;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, previousMainHand);
        previousMainHand = ItemStack.EMPTY;
        toolVisible = false;
    }

    private void clearMiningProgress() {
        if (blockTarget != null && mob.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(mob.getId(), blockTarget, -1);
        }
    }

    private void restoreIceHole(ServerLevel level) {
        CreatureSettlementService.restoreIceFishingHole(
                level, fishingSite, openedIceState);
        openedIceState = null;
    }

    private void moveToDestination() {
        if (destination != null) {
            mob.getNavigation().moveTo(
                    destination.x, destination.y, destination.z, speedFor(state));
        }
    }

    private double speedFor(RoutineState activity) {
        return switch (activity) {
            case DELIVERING -> 0.27D;
            case GUARDING -> 0.32D;
            case SCOUTING -> 0.30D;
            case GATHERING, FISHING, MINING -> 0.27D;
            case TENDING, PLAYING -> 0.25D;
            default -> 0.0D;
        };
    }

    private int duration(RoutineState activity) {
        return switch (activity) {
            case DELIVERING -> 500;
            case FISHING -> 620;
            case MINING -> 320;
            case GATHERING -> 520;
            case TENDING -> 300;
            case SCOUTING, GUARDING, PLAYING ->
                    160 + mob.getRandom().nextInt(181);
            default -> 0;
        };
    }

    private void presentActivityStart() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (mob.getPersistentData().getLong(NEXT_PRESENTATION) > now) {
            return;
        }
        ServerPlayer observer = nearestObserver(level);
        Cue cue = switch (state) {
            case SCOUTING -> Cue.ROUTINE_ROAM;
            case GUARDING -> Cue.ROUTINE_PATROL;
            case GATHERING, FISHING, MINING -> Cue.ROUTINE_FORAGE;
            case DELIVERING -> Cue.ROUTINE_RETURN_CENTER;
            case TENDING, PLAYING -> Cue.ROUTINE_SOCIALIZE;
            default -> null;
        };
        if (observer != null && cue != null) {
            NpcDialogue.trigger(mob, observer, cue);
            mob.getPersistentData().putLong(
                    NEXT_PRESENTATION,
                    now + 420L + mob.getRandom().nextInt(301));
        }
    }

    private void presentRoleAction(Cue cue) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer observer = nearestObserver(level);
        if (observer == null) {
            NpcDialogue.emoteOnly(mob, cue);
        } else {
            NpcDialogue.trigger(mob, observer, cue);
        }
    }

    @Nullable
    private ServerPlayer nearestObserver(ServerLevel level) {
        return level.players().stream()
                .filter(player -> player.isAlive()
                        && !player.isSpectator()
                        && player.distanceToSqr(mob) <= PRESENTATION_RANGE_SQR)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private void clearTargets() {
        CreatureSettlementService.releaseHuntClaim(mob, preyTarget);
        CreatureSettlementService.releaseMinecartClaim(mob, minecartTarget);
        destination = null;
        itemTarget = null;
        blockTarget = null;
        fishingSite = null;
        fishTarget = null;
        preyTarget = null;
        minecartTarget = null;
        minecartStand = null;
        glowBerrySite = null;
        openedIceState = null;
        peer = null;
        dwellTicks = 0;
        actionTicks = 0;
        shoreTransitionTicks = 0;
        fishFromWaterFallback = false;
        offshoreFishing = false;
    }

    private boolean movementAvailable() {
        LivingEntity target = mob.getTarget();
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            // Some interrupted Changed attack goals leave their defeated target
            // attached for another selector pass. Do not let that stale target
            // permanently suppress a loaded provisioner's delivery routine.
            mob.setTarget(null);
            target = null;
        }
        if (!mob.isAlive()
                || mob.isNoAi()
                || mob.isPassenger()
                || mob.isLeashed()
                || target != null
                || SocialAudienceGoal.isActive(mob)
                || ChangedAddonCompat.isGrabberBusy(mob)
                || HypnosisQteService.getActiveVictim(mob) != null) {
            return false;
        }
        if (LatexSocialMemory.hasActiveBond(mob)
                || LatexSocialMemory.petOwnerUuid(mob).isPresent()
                || CreaturePersonality.socialPartner(mob) != null) {
            return false;
        }
        return !(mob instanceof TamableLatexEntity pet && pet.isTame());
    }

    private boolean hasLiveCombatTarget() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !target.isRemoved();
    }
}
