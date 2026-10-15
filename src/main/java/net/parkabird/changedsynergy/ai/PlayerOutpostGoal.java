package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.FishingSite;
import net.parkabird.changedsynergy.ai.CreatureSettlementService.GlowBerrySite;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Follows the owner's job order and delivers real foraged resources to the linked chest. */
public final class PlayerOutpostGoal extends Goal {
    private static final double SPEED = 0.35D;
    private static final String NEXT_NECTAR = "ChangedSynergyPlayerOutpostNextNectar";
    private enum Harvest { ORANGE, ORE, GLOW_BERRIES, SWEET_BERRIES, FISH, SEA_FISH, NECTAR }
    private final ChangedEntity mob;
    private final CompanionFollowNavigation crewNavigation;
    private Outpost outpost;
    private Member member;
    private Role startedRole;
    private Harvest harvest;
    private BlockPos harvestBlock;
    private FishingSite fishingSite;
    private AbstractFish fish;
    private BlockState openedIce;
    private int harvestTicks;
    private float miningProgress;
    private BlockPos destination;
    private ServerPlayer crewOwner;
    private LivingEntity crewTarget;
    private int repath;
    private int remainingTicks;
    private long nextAttemptTick;
    private long nextSupplyScan;
    private long nextIdleOrderScan;
    private boolean supplyScanInitialized;
    private float oldWaterCost;
    private long nextCrewTeleport;

    public PlayerOutpostGoal(ChangedEntity mob) {
        this.mob = mob;
        this.crewNavigation = new CompanionFollowNavigation(mob);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        outpost = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        if (outpost == null) return false;
        member = outpost.members.get(mob.getUUID());
        if (member == null || !available()) return false;
        int interval = member.role == Role.SUPPLY
                ? Math.max(20, ChangedSynergyConfig.COMMON.playerOutpostWorkScanInterval.get()) : 20;
        if (member.role == Role.SUPPLY) {
            if (!supplyScanInitialized) {
                supplyScanInitialized = true;
                nextSupplyScan = level.getGameTime() + Math.floorMod(mob.getId(), interval);
            }
            if (level.getGameTime() < nextSupplyScan) return false;
            nextSupplyScan = level.getGameTime() + interval;
        } else if (member.role == Role.RESIDENT || member.role == Role.GUARD) {
            if (level.getGameTime() < nextIdleOrderScan) return false;
            nextIdleOrderScan = level.getGameTime() + interval;
        }
        if (member.role == Role.SUPPLY && (!PlayerOutpostWorkGoal.canWork(mob, outpost)
                || PlayerOutpostWorkGoal.hasCargo(mob)
                || !PlayerOutpostWorkGoal.allowsGather(member))) return false;
        if (member.role == Role.SUPPLY && level.getGameTime() < nextAttemptTick) return false;
        startedRole = member.role;
        crewOwner = null;
        crewTarget = null;
        if (member.role == Role.CREW) {
            crewOwner = level.getServer().getPlayerList().getPlayer(outpost.owner);
            crewTarget = crewBody();
            if (crewTarget == null) return false;
            destination = crewTarget.blockPosition();
            boolean follow = mob.distanceToSqr(crewTarget) > (member.crewJob == PlayerOutpostData.CrewJob.COMBAT ? 16.0D : 64.0D);
            if (follow) member.workStatus = "following";
            else if (member.workStatus.equals("following") || member.workStatus.equals("travelling")) member.workStatus = "waiting";
            return follow;
        }
        if (!outpost.active(level)) return false;
        clearHarvest();
        destination = null;
        if (member.role == Role.SUPPLY && ChangedSynergyConfig.COMMON.playerOutpostSupplyWork.get()
                && validStorage(level) && (!mob.isPassenger() || PlayerOutpostRestGoal.isResting(mob))) {
            if (hasCargo(mob)) destination = outpost.storage;
            if (destination == null && (level.isDay() || !outpost.contains(mob.blockPosition()))) {
                if (!outpost.hasGatherArea() && outpost.marker.distSqr(mob.blockPosition()) > 18 * 18) {
                    destination = homeStand(level);
                } else if (ChangedSynergyConfig.COMMON.playerOutpostHarvesting.get()) chooseHarvest(level);
            }
        }
        if (destination == null) {
            if (member.role == Role.HOLD && member.holdPosition != null) destination = member.holdPosition;
            else if (member.role == Role.RETURN || member.role == Role.RETREAT || member.role == Role.GUARD
                    || !(member.role == Role.SUPPLY ? outpost.workContains(mob.blockPosition())
                            : outpost.contains(mob.blockPosition()))) destination = homeStand(level);
        }
        if (member.role == Role.SUPPLY) member.workStatus = harvest != null ? "harvesting" : hasCargo(mob) ? "returning" : "idle";
        if (destination == null) {
            if (member.role == Role.SUPPLY) nextAttemptTick = level.getGameTime() + 100L;
            return false;
        }
        if (member.role == Role.RETURN && mob.distanceToSqr(Vec3.atCenterOf(destination)) < 16.0D) {
            PlayerOutpostData.get(level.getServer()).role(outpost, member.id, Role.RESIDENT, mob.blockPosition());
            return false;
        }
        return mob.distanceToSqr(Vec3.atCenterOf(destination)) > 5.0D
                || harvest != null
                || hasCargo(mob);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = member != null && member.role == Role.CREW ? crewBody() : null;
        return available() && member != null && outpost != null && member.role == startedRole
                && outpost.members.get(mob.getUUID()) == member
                && (member.role != Role.SUPPLY || PlayerOutpostWorkGoal.canWork(mob, outpost)
                        && PlayerOutpostWorkGoal.allowsGather(member)
                        && !PlayerOutpostWorkGoal.hasCargo(mob))
                && (harvest == null || ChangedSynergyConfig.COMMON.playerOutpostHarvesting.get())
                && (member.role != Role.SUPPLY || ChangedSynergyConfig.COMMON.playerOutpostSupplyWork.get()
                        || harvest == null && !hasCargo(mob))
                && (member.role == Role.CREW || remainingTicks > 0)
                && destination != null && mob.level() instanceof ServerLevel level
                && (member.role == Role.CREW
                        ? target != null && mob.distanceToSqr(target) > (member.crewJob == PlayerOutpostData.CrewJob.COMBAT ? 4.0D : 36.0D)
                        : outpost.active(level))
                && (harvestBlock == null || outpost.gatherContains(harvestBlock))
                && (fishingSite == null || outpost.gatherContains(fishingSite.water()))
                && (fish == null || fish.isAlive() && outpost.gatherContains(fish.blockPosition()))
                && (member.role == Role.CREW
                        || mob.distanceToSqr(Vec3.atCenterOf(destination)) > 5.0D
                        || harvest != null || hasCargo(mob));
    }

    @Override
    public void start() {
        PlayerOutpostRestGoal.cleanup(mob);
        repath = 0;
        remainingTicks = 400;
        harvestTicks = 0;
        miningProgress = 0;
        if (startedRole == Role.CREW) {
            crewNavigation.reset();
            oldWaterCost = mob.getPathfindingMalus(BlockPathTypes.WATER);
            mob.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        }
        if (!net.parkabird.changedsynergy.compat.ChangedVanillaCompat.equipmentChangeRebuildsGoals(mob)) {
            if (harvest == Harvest.FISH) GatheringToolPresentation.show(mob, CompanionToolEffects.gatheringRod(mob).stack());
            else if (harvest == Harvest.ORE) GatheringToolPresentation.show(mob,
                    CompanionToolEffects.gatheringPickaxe(mob, mob.level().getBlockState(harvestBlock)).stack());
        }
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || destination == null) return;
        GatheringToolPresentation.heartbeat(mob);
        // A new owner order interrupts the old harvest before the selector's next pass.
        if (member == null || member.role != startedRole || outpost.members.get(mob.getUUID()) != member) {
            remainingTicks = 0;
            mob.getNavigation().stop();
            return;
        }
        if (member.role != Role.CREW) remainingTicks--;
        if (member.role == Role.CREW) {
            LivingEntity body = crewBody();
            if (body == null) { mob.getNavigation().stop(); remainingTicks = 0; return; }
            if (body != crewTarget) { mob.getNavigation().stop(); crewNavigation.reset(); repath = 0; crewTarget = body; }
            destination = body.blockPosition();
            double distance = mob.distanceToSqr(body);
            double stopDistance = member.crewJob == PlayerOutpostData.CrewJob.COMBAT ? 4 : 36;
            crewNavigation.tickProgress(distance > stopDistance);
            if (distance <= stopDistance) { mob.getNavigation().stop(); repath = 0; return; }
            mob.getLookControl().setLookAt(body, 30, 30);
            if (--repath <= 0) {
                repath = 10;
                if ((distance > 24 * 24 || crewNavigation.isStalled() && distance >= 8 * 8)
                        && level.getGameTime() >= nextCrewTeleport) {
                    if (BondedTeleportSafety.teleportNearOwner(level, mob, crewOwner)) {
                        crewNavigation.reset(); return;
                    }
                    nextCrewTeleport = level.getGameTime() + 60;
                }
                boolean routed = crewNavigation.moveToward(body, SPEED);
                member.workStatus = routed ? "following" : "unreachable";
            }
            return;
        }
        if (fish != null) destination = fish.blockPosition();
        if (harvest != null && mob.distanceToSqr(Vec3.atCenterOf(destination)) < 8.0D) {
            if (++harvestTicks == 5) mob.swing(InteractionHand.MAIN_HAND);
            boolean ready;
            if (harvest == Harvest.ORE) {
                BlockState state = level.getBlockState(harvestBlock);
                float hardness = state.getDestroySpeed(level, harvestBlock);
                ItemStack tool = CompanionToolEffects.gatheringPickaxe(mob, state).stack();
                if (!state.is(net.minecraftforge.common.Tags.Blocks.ORES) || hardness < 0 || !tool.isCorrectToolForDrops(state)) {
                    remainingTicks = 0;
                    return;
                }
                miningProgress += hardness == 0 ? 1 : CompanionToolEffects.miningSpeed(mob, tool, state)
                        / hardness / adjustedTickDelay(30);
                level.destroyBlockProgress(mob.getId(), harvestBlock, Math.min(9, (int)(miningProgress * 10)));
                if (harvestTicks % 8 == 1) mob.swing(InteractionHand.MAIN_HAND);
                ready = miningProgress >= 1;
            } else ready = harvestTicks >= (harvest == Harvest.FISH
                    ? adjustedTickDelay(CompanionToolEffects.fishingWaitTicks(CompanionToolEffects.gatheringRod(mob).stack())) : 12);
            if (ready) {
                boolean gathered = harvest(level);
                if (fish != null) CreatureSettlementService.releaseSeaFishClaim(mob, fish);
                clearHarvest();
                if (!gathered) {
                    remainingTicks = 0;
                    return;
                }
                if (!hasCargo(mob)) {
                    setCargo(mob, CreatureSettlementService.takeCargo(mob));
                }
                destination = outpost.storage;
                if (destination == null) {
                    remainingTicks = 0;
                    return;
                }
                remainingTicks = Math.max(remainingTicks, 300);
                repath = 0;
            }
        }
        if (member.role == Role.SUPPLY && hasCargo(mob) && outpost.storage != null
                && outpost.dimension.equals(level.dimension().location().toString())
                && level.hasChunkAt(outpost.storage)
                && mob.distanceToSqr(Vec3.atCenterOf(outpost.storage)) < 7.0D) {
            Container container = PlayerOutpostData.container(level, outpost.storage);
            if (container != null) {
                ItemStack source = cargo(mob);
                ItemStack remainder = insert(container, source);
                if (remainder.getCount() < source.getCount()) {
                    PlayerOutpostStorageAccess.touch(mob, outpost.storage);
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(outpost.owner);
                    if (owner != null) net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(owner,
                            net.parkabird.changedsynergy.advancement.SynergyAdvancements.CAMP_DELIVERY);
                }
                setCargo(mob, remainder);
                if (!remainder.isEmpty()) {
                    // Keep real cargo on the creature when the linked chest is full.
                    member.workStatus = "full_storage";
                    destination = null;
                    nextAttemptTick = level.getGameTime() + 200L;
                    mob.getNavigation().stop();
                    return;
                }
            }
            destination = homeStand(level);
            nextAttemptTick = level.getGameTime() + 100L;
        }
        if (destination == null) { mob.getNavigation().stop(); return; }
        if (member.role == Role.RETURN
                && mob.distanceToSqr(Vec3.atCenterOf(destination)) < 16.0D) {
            PlayerOutpostData.get(level.getServer()).role(outpost, member.id, Role.RESIDENT, mob.blockPosition());
            return;
        }
        if (--repath <= 0) {
            repath = 10;
            boolean routed = walkTo(mob, destination, destination.getY(), SPEED);
            if (member.role == Role.CREW) member.workStatus = routed ? "following" : "unreachable";
        }
    }

    @Override
    public void stop() {
        if (remainingTicks <= 0 && mob.level() instanceof ServerLevel level) {
            nextAttemptTick = level.getGameTime() + 200L;
        }
        mob.getNavigation().stop();
        if (startedRole == Role.CREW) mob.setPathfindingMalus(BlockPathTypes.WATER, oldWaterCost);
        crewNavigation.reset();
        if (fish != null) CreatureSettlementService.releaseSeaFishClaim(mob, fish);
        if (openedIce != null && mob.level() instanceof ServerLevel level) {
            CreatureSettlementService.restoreIceFishingHole(level, fishingSite, openedIce);
        }
        clearHarvest();
        destination = null;
        crewOwner = null;
        crewTarget = null;
        startedRole = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return startedRole == Role.CREW; }

    private LivingEntity crewBody() {
        if (crewOwner == null || !crewOwner.isAlive() || crewOwner.isSpectator()
                || crewOwner.server.getPlayerList().getPlayer(crewOwner.getUUID()) != crewOwner
                || !PlayerOutpostService.authorized(mob, crewOwner)) return null;
        LivingEntity body = TrueTransfurCompat.interactionBody(crewOwner);
        // A new backend with no valid physical body must not lead the crew to its hidden shell.
        return body.isAlive() && !body.isRemoved() && body.level() == mob.level()
                && !(body == crewOwner && TrueTransfurCompat.isInstinctShell(crewOwner)) ? body : null;
    }

    private boolean available() {
        return PlayerOutpostService.operational(mob) && mob.isAlive()
                && !mob.isNoAi()
                && (mob.getTarget() == null || member != null && member.role == Role.RETREAT)
                && !mob.isSleeping() && (!mob.isPassenger() || PlayerOutpostRestGoal.isResting(mob))
                && !mob.isVehicle() && !TakeoverService.carrying(mob) && !ChangedAddonCompat.isGrabberBusy(mob)
                && GrabEntityAbility.getGrabber(mob) == null
                && !SocialAudienceGoal.isActive(mob) && HypnosisQteService.getActiveVictim(mob) == null
                && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                && !onCampfire(mob)
                && !mob.isLeashed() && !CreatureSettlementService.hasCargo(mob);
    }

    private boolean validStorage(ServerLevel level) {
        return PlayerOutpostData.container(level, outpost.storage) != null;
    }

    private void clearHarvest() {
        if (harvest == Harvest.ORE && harvestBlock != null && mob.level() instanceof ServerLevel level)
            level.destroyBlockProgress(mob.getId(), harvestBlock, -1);
        GatheringToolPresentation.restore(mob);
        miningProgress = 0;
        harvest = null;
        harvestBlock = null;
        fishingSite = null;
        fish = null;
        harvestTicks = 0;
    }

    private void chooseHarvest(ServerLevel level) {
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) { member.workStatus = "protected"; return; }
        PlayerOutpostSupplyType type = PlayerOutpostSupplyType.of(mob);
        if (type == PlayerOutpostSupplyType.CAVE) {
            if (mob.getRandom().nextInt(5) != 0
                    && setHarvest(CreatureSettlementService.findExposedOre(mob, 8, 4, outpost::gatherContains)
                            .orElse(null), Harvest.ORE, null)) return;
            GlowBerrySite berries = CreatureSettlementService.findGlowBerrySite(mob, 8, 6, 4, outpost::gatherContains)
                    .orElse(null);
            if (berries != null && setHarvest(berries.berries(), Harvest.GLOW_BERRIES,
                    berries.stand())) return;
            setHarvest(CreatureSettlementService.findExposedOre(mob, 8, 4, outpost::gatherContains)
                    .orElse(null), Harvest.ORE, null);
        } else if (type == PlayerOutpostSupplyType.BERRIES) {
            setHarvest(CreatureSettlementService.findSweetBerryBush(mob, 10, 4, outpost::gatherContains)
                    .orElse(null), Harvest.SWEET_BERRIES, null);
        } else if (type == PlayerOutpostSupplyType.FISH) {
            if (CreatureSettlementService.usesOpenOceanHarvest(mob)) {
                fish = CreatureSettlementService.findSeaFish(mob, 12.0D, outpost::gatherContains).orElse(null);
                if (fish != null && outpost.gatherContains(fish.blockPosition())) {
                    harvest = Harvest.SEA_FISH;
                    destination = fish.blockPosition();
                    return;
                }
                CreatureSettlementService.releaseSeaFishClaim(mob, fish);
                fish = null;
            }
            FishingSite site = CreatureSettlementService.findFishingSite(mob, 12, 4, outpost::gatherContains)
                    .orElse(null);
            if (site != null && outpost.gatherContains(site.water())
                    && outpost.workContains(site.stand())) {
                fishingSite = site;
                harvest = Harvest.FISH;
                destination = BlockPos.containing(CreatureSettlementService.fishingApproach(mob, site));
            }
        } else if (type == PlayerOutpostSupplyType.NECTAR) {
            if (level.getGameTime() < mob.getPersistentData().getLong(NEXT_NECTAR)) return;
            BlockPos origin = mob.blockPosition();
            BlockPos nearest = null;
            double best = Double.MAX_VALUE;
            for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-8, -3, -8),
                    origin.offset(8, 3, 8))) {
                if (level.hasChunkAt(cursor) && outpost.gatherContains(cursor)
                        && level.getBlockState(cursor).is(BlockTags.FLOWERS)
                        && cursor.distSqr(origin) < best) {
                    nearest = cursor.immutable();
                    best = cursor.distSqr(origin);
                }
            }
            setHarvest(nearest, Harvest.NECTAR, null);
        } else if (type == PlayerOutpostSupplyType.FORAGE) {
            if (setHarvest(CreatureSettlementService.findSweetBerryBush(mob, 10, 4, outpost::gatherContains)
                    .orElse(null), Harvest.SWEET_BERRIES, null)) return;
            setHarvest(CreatureSettlementService.findOrangeLeaves(mob, 10, 4, outpost::gatherContains)
                    .orElse(null), Harvest.ORANGE, null);
        } else {
            setHarvest(CreatureSettlementService.findOrangeLeaves(mob, 10, 4, outpost::gatherContains)
                    .orElse(null), Harvest.ORANGE, null);
        }
    }

    private boolean setHarvest(BlockPos block, Harvest action, BlockPos stand) {
        if (block == null || !outpost.gatherContains(block)) return false;
        BlockPos center = stand == null ? block : stand;
        BlockPos[] approaches = stand != null ? new BlockPos[]{center}
                : new BlockPos[]{center, center.below(), center.north(), center.south(),
                        center.east(), center.west(), center.above()};
        for (BlockPos approach : approaches) {
            if (!outpost.workContains(approach)) continue;
            if (mob.distanceToSqr(Vec3.atCenterOf(approach)) > 8.0D) {
                var path = mob.getNavigation().createPath(approach, 1);
                if (!workRoute(mob, approach, path)) continue;
            }
            harvest = action;
            harvestBlock = block;
            destination = approach;
            return true;
        }
        return false;
    }

    private boolean harvest(ServerLevel level) {
        if (!ForgeEventFactory.getMobGriefingEvent(level, mob)) return false;
        if (harvest == Harvest.SEA_FISH) return fish != null
                && outpost.gatherContains(fish.blockPosition())
                && CreatureSettlementService.captureSeaFish(mob, fish);
        if (harvest == Harvest.FISH) {
            if (fishingSite == null || !outpost.gatherContains(fishingSite.water())) return false;
            if (fishingSite.iceCovered()) {
                openedIce = CreatureSettlementService.openIceFishingHole(level, fishingSite);
                if (openedIce == null) return false;
            }
            boolean caught = CreatureSettlementService.catchFish(mob, fishingSite.water());
            CreatureSettlementService.restoreIceFishingHole(level, fishingSite, openedIce);
            openedIce = null;
            return caught;
        }
        if (harvestBlock == null || !outpost.gatherContains(harvestBlock)) return false;
        return switch (harvest) {
            case ORANGE -> CreatureSettlementService.harvestOrangeLeaves(mob, harvestBlock);
            case ORE -> CreatureSettlementService.mineOre(mob, harvestBlock);
            case GLOW_BERRIES -> CreatureSettlementService.harvestGlowBerries(mob, harvestBlock);
            case SWEET_BERRIES -> CreatureSettlementService.harvestSweetBerries(mob, harvestBlock);
            case NECTAR -> {
                if (!level.getBlockState(harvestBlock).is(BlockTags.FLOWERS)) yield false;
                mob.getPersistentData().putLong(NEXT_NECTAR, level.getGameTime() + 2400L);
                level.playSound(null, harvestBlock, SoundEvents.BEE_POLLINATE,
                        SoundSource.NEUTRAL, 0.7F, 1.0F);
                level.sendParticles(ParticleTypes.COMPOSTER, harvestBlock.getX() + 0.5D,
                        harvestBlock.getY() + 0.5D, harvestBlock.getZ() + 0.5D,
                        5, 0.2D, 0.2D, 0.2D, 0.02D);
                setCargo(mob, new ItemStack(Items.HONEYCOMB));
                yield true;
            }
            default -> false;
        };
    }

    private BlockPos homeStand(ServerLevel level) {
        BlockPos marker = outpost.marker;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos pos = marker.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)
                            || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                            || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                            || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                            || Idle.unsafe(level.getBlockState(pos.below()))
                            || onCampfire(level, mob.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(mob.position())))) continue;
                    double distance = mob.blockPosition().distSqr(pos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /** Distant camp tasks can be approached in native pathfinding-sized segments. */
    static boolean workRoute(ChangedEntity mob, BlockPos destination, Path path) {
        if (path == null) return false;
        if (path.canReach()) return true;
        double range = Math.max(4.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        if (mob.distanceToSqr(Vec3.atBottomCenterOf(destination)) <= range * range
                || path.getNodeCount() < 2 || path.getEndNode() == null) return false;
        BlockPos end = path.getEndNode().asBlockPos();
        return mob.level().hasChunkAt(end)
                && end.distSqr(destination) + 4.0D < mob.blockPosition().distSqr(destination);
    }

    /** Preserve usable routes; ground navigation cannot rebuild them mid-jump. */
    static boolean walkTo(ChangedEntity mob, BlockPos destination, double y, double speed) {
        var navigation = mob.getNavigation();
        if (!navigation.isDone() && destination.equals(navigation.getTargetPos())) return true;
        if (!canNavigate(mob)) return true;
        // These are standing/seat positions, not blocks to interact with from a neighbouring node.
        var path = navigation.createPath(BlockPos.containing(destination.getX() + 0.5D,
                y, destination.getZ() + 0.5D), 0);
        return navigation.moveTo(path, speed);
    }

    static boolean canNavigate(ChangedEntity mob) {
        return !(mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation)
                || mob.onGround() || mob.isInWaterOrBubble() || mob.isInLava();
    }

    public static boolean onCampfire(ChangedEntity mob) {
        return onCampfire(mob.level(), mob.getBoundingBox().inflate(0.02D, 0.1D, 0.02D));
    }

    public static boolean onCampfire(net.minecraft.world.level.Level level, AABB body) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                    && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                    && new AABB(pos).intersects(body)) return true;
        }
        return false;
    }

    /** A bounded, ordinary-speed exit from a hot campfire; never teleport or boost attributes. */
    public static final class FireEscape extends Goal {
        private final ChangedEntity mob;
        private BlockPos destination;
        private int remaining;
        private int repath;
        public FireEscape(ChangedEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isNoAi()
                    || !PlayerOutpostService.operational(mob)
                    || !PlayerOutpostService.assigned(mob) || !onCampfire(mob) || mob.isLeashed()
                    || mob.isPassenger() && !PlayerOutpostRestGoal.isResting(mob) && !PlayerOutpostMealGoal.ownsSeat(mob)
                    || mob.isVehicle() || TakeoverService.carrying(mob) || ChangedAddonCompat.isGrabberBusy(mob)
                    || GrabEntityAbility.getGrabber(mob) != null) return false;
            BlockPos origin = mob.blockPosition();
            java.util.List<BlockPos> choices = new java.util.ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -1, -3), origin.offset(3, 1, 3))) {
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        || Idle.unsafe(level.getBlockState(pos)) || Idle.unsafe(level.getBlockState(pos.below()))) continue;
                AABB body = mob.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(mob.position()));
                if (!onCampfire(level, body.inflate(0.3D, 0.1D, 0.3D)) && level.noCollision(mob, body)
                        && level.getBlockState(pos).getFluidState().isEmpty()) choices.add(pos.immutable());
            }
            choices.sort(Comparator.comparingDouble(pos -> mob.distanceToSqr(Vec3.atBottomCenterOf(pos))));
            for (BlockPos pos : choices.stream().limit(8).toList()) {
                var path = mob.getNavigation().createPath(pos, 0);
                if (path != null && path.canReach()) { destination = pos; return true; }
            }
            return false;
        }
        @Override public void start() {
            PlayerOutpostMealGoal.cleanup(mob);
            PlayerOutpostRestGoal.cleanup(mob);
            if (mob.isSleeping()) mob.stopSleeping();
            remaining = 100; repath = 0;
        }
        @Override public boolean canContinueToUse() {
            return remaining > 0 && destination != null && mob.isAlive() && PlayerOutpostService.assigned(mob)
                    && mob.distanceToSqr(Vec3.atBottomCenterOf(destination)) > 1.0D;
        }
        @Override public void tick() {
            remaining--;
            if (--repath <= 0) { repath = 10; walkTo(mob, destination, destination.getY(), SPEED); }
        }
        @Override public void stop() { mob.getNavigation().stop(); destination = null; }
    }

    public static ItemStack insert(Container container, ItemStack source) {
        ItemStack remainder = source.copy();
        for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
            for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
                ItemStack stored = container.getItem(slot);
                if (!container.canPlaceItem(slot, remainder)
                        || pass == 0 && (stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, remainder))
                        || pass == 1 && !stored.isEmpty()) continue;
                int free = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize()) - stored.getCount();
                if (free <= 0) continue;
                int added = Math.min(free, remainder.getCount());
                if (stored.isEmpty()) container.setItem(slot, remainder.copyWithCount(added));
                else stored.grow(added);
                remainder.shrink(added);
            }
        }
        if (remainder.getCount() != source.getCount()) container.setChanged();
        return remainder;
    }

    public static ItemStack cargo(ChangedEntity mob) {
        return PlayerOutpostCargoInventory.inventory(mob).getItem(PlayerOutpostCargoInventory.GATHER_SLOT).copy();
    }

    public static void setCargo(ChangedEntity mob, ItemStack stack) {
        PlayerOutpostCargoInventory.inventory(mob).setItem(PlayerOutpostCargoInventory.GATHER_SLOT, stack);
    }

    public static ItemStack takeCargo(ChangedEntity mob) {
        ItemStack stack = cargo(mob);
        setCargo(mob, ItemStack.EMPTY);
        return stack;
    }

    public static boolean hasCargo(ChangedEntity mob) {
        return PlayerOutpostCargoInventory.hasGatherItem(mob);
    }

    public static AABB workArea(Outpost camp, Member member) {
        if (member.work == Work.HAUL && camp.hasHaulArea()) return camp.haulBounds();
        if (member.work == Work.GATHER && camp.hasGatherArea())
            return new AABB(camp.gatherStart).minmax(new AABB(camp.gatherEnd)).inflate(0, 3, 0);
        if (member.work == Work.FARM && camp.farmStart != null && camp.farmEnd != null)
            return new AABB(camp.farmStart).minmax(new AABB(camp.farmEnd)).inflate(3, 3, 3);
        if (member.work == Work.HUNT && camp.huntStart != null && camp.huntEnd != null)
            return new AABB(camp.huntStart).minmax(new AABB(camp.huntEnd)).inflate(3, 3, 3);
        BlockPos anchor = switch (member.work) {
            case COOK -> camp.cooking;
            case COMPOST -> camp.composter;
            case GATHER, HAUL -> camp.storage;
            default -> camp.marker;
        };
        return new AABB(anchor == null ? camp.marker : anchor).inflate(5, 3, 5);
    }

    /** Lower than work, beds and meals, but higher than a camp member's random stroll. */
    public static final class Idle extends Goal {
        private final ChangedEntity mob;
        private Outpost camp;
        private Member member;
        private Work work;
        private BlockPos destination;
        private AABB lastArea;
        private long nextMove;
        private long nextAttempt;
        private int repath;

        public Idle(ChangedEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() {
            if (!(mob.level() instanceof ServerLevel level) || level.getGameTime() < nextAttempt) return false;
            camp = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
            member = camp == null ? null : camp.members.get(mob.getUUID());
            if (!available(level)) return false;
            work = member.work;
            lastArea = area();
            destination = safeStand(level, lastArea, true);
            if (destination == null) destination = safeStand(level, new AABB(camp.marker).inflate(4, 3, 4), false);
            if (destination == null) nextAttempt = level.getGameTime() + 100L;
            return destination != null;
        }

        @Override public boolean canContinueToUse() {
            return destination != null && mob.level() instanceof ServerLevel level
                    && available(level) && member.work == work;
        }

        @Override public void start() {
            mob.getNavigation().stop();
            nextMove = mob.level().getGameTime() + 100L;
            repath = 0;
        }

        @Override public void tick() {
            if (!(mob.level() instanceof ServerLevel level) || !available(level) || member.work != work) return;
            AABB region = area();
            if (level.getGameTime() >= nextMove || destination == null
                    || !region.equals(lastArea)) {
                lastArea = region;
                destination = safeStand(level, region, true);
                if (destination == null) destination = safeStand(level, new AABB(camp.marker).inflate(4, 3, 4), false);
                nextMove = level.getGameTime() + 100L + mob.getRandom().nextInt(101);
                repath = 0;
            }
            if (destination == null) {
                nextAttempt = level.getGameTime() + 100L;
                mob.getNavigation().stop();
                return;
            }
            if (mob.distanceToSqr(Vec3.atBottomCenterOf(destination)) < 1.5D) {
                mob.getNavigation().stop();
            } else if (--repath <= 0) {
                repath = 20;
                walkTo(mob, destination, destination.getY(), SPEED);
            }
        }

        @Override public void stop() { mob.getNavigation().stop(); destination = null; }

        private boolean available(ServerLevel level) {
            return PlayerOutpostService.operational(mob) && camp != null && member != null
                    && camp.active(level) && camp.members.get(mob.getUUID()) == member && member.role == Role.SUPPLY
                    && mob.isAlive() && !mob.isNoAi() && !mob.isSleeping() && !mob.isPassenger()
                    && !mob.isVehicle() && !mob.isLeashed() && mob.getTarget() == null
                    && !TakeoverService.carrying(mob) && !ChangedAddonCompat.isGrabberBusy(mob)
                    && GrabEntityAbility.getGrabber(mob) == null && !wrapping()
                    && !SocialAudienceGoal.isActive(mob) && HypnosisQteService.getActiveVictim(mob) == null
                    && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")
                    && !PlayerOutpostWorkGoal.hasCargo(mob)
                    && !PlayerOutpostMealGoal.wantsMeal(mob) && !onCampfire(mob);
        }

        private boolean wrapping() {
            var ability = BondedSuitService.ability(mob);
            return ability != null && ability.grabbedEntity != null;
        }

        private AABB area() { return workArea(camp, member); }

        private BlockPos safeStand(ServerLevel level, AABB region, boolean smallStep) {
            int x = net.minecraft.util.Mth.clamp(mob.blockPosition().getX(), (int)Math.ceil(region.minX), (int)Math.floor(region.maxX) - 1);
            int y = net.minecraft.util.Mth.clamp(mob.blockPosition().getY(), (int)Math.ceil(region.minY), (int)Math.floor(region.maxY) - 1);
            int z = net.minecraft.util.Mth.clamp(mob.blockPosition().getZ(), (int)Math.ceil(region.minZ), (int)Math.floor(region.maxZ) - 1);
            if (smallStep && region.contains(mob.position())) {
                x = net.minecraft.util.Mth.clamp(x + mob.getRandom().nextInt(5) - 2, (int)Math.ceil(region.minX), (int)Math.floor(region.maxX) - 1);
                z = net.minecraft.util.Mth.clamp(z + mob.getRandom().nextInt(5) - 2, (int)Math.ceil(region.minZ), (int)Math.floor(region.maxZ) - 1);
            }
            BlockPos origin = new BlockPos(x, y, z);
            int probes = 0;
            for (int radius = 0; radius <= 3; radius++) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos point = origin.offset(dx, dy, dz);
                        if (!region.contains(Vec3.atCenterOf(point)) || !camp.workContains(point) || !level.hasChunkAt(point)
                                || !level.getFluidState(point).isEmpty()
                                || !level.getBlockState(point.below()).isFaceSturdy(level, point.below(), Direction.UP)
                                || unsafe(level.getBlockState(point)) || unsafe(level.getBlockState(point.below()))) continue;
                        AABB body = mob.getBoundingBox().move(Vec3.atBottomCenterOf(point).subtract(mob.position()));
                        if (!level.noCollision(mob, body)) continue;
                        if (mob.distanceToSqr(Vec3.atBottomCenterOf(point)) < 1.5D) return point;
                        if (++probes > 8) return null;
                        var path = mob.getNavigation().createPath(point, 0);
                        if (workRoute(mob, point, path)) return point;
                    }
                }
            }
            return null;
        }

        static boolean unsafe(BlockState state) {
            return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                    || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES);
        }
    }
}
