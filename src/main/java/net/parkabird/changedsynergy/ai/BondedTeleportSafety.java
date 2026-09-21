package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Finds a real standing/swimming space near an owner before moving a bonded
 * creature. In particular, this never falls back to the owner's exact altitude:
 * a flying owner therefore cannot pull a ground creature into open air.
 */
public final class BondedTeleportSafety {
    private static final int MIN_HORIZONTAL_RADIUS = 2;
    private static final int MAX_HORIZONTAL_RADIUS = 10;
    private static final int MAX_BELOW_OWNER = 14;
    private static final int MAX_ABOVE_OWNER = 6;

    /** A reachable water node and the adjacent dry block used to leave it. */
    public record ShoreApproach(Vec3 water, Vec3 land) {
    }

    private BondedTeleportSafety() {
    }

    public static Optional<Vec3> findSafeLanding(
            ServerLevel level,
            ChangedEntity creature,
            ServerPlayer owner) {
        int ownerY = owner.blockPosition().getY();
        int entityBlocksHigh = Math.max(1, (int)Math.ceil(creature.getBbHeight()));
        int minimumY = level.getMinBuildHeight() + 1;
        int maximumY = level.getMaxBuildHeight() - entityBlocksHigh - 1;
        int lowestY = Math.max(minimumY, ownerY - MAX_BELOW_OWNER);
        int highestY = Math.min(maximumY, ownerY + MAX_ABOVE_OWNER);
        if (lowestY > highestY) {
            return Optional.empty();
        }

        for (int radius = MIN_HORIZONTAL_RADIUS;
                radius <= MAX_HORIZONTAL_RADIUS;
                radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    Optional<Vec3> landing = findInColumn(
                            level,
                            creature,
                            owner,
                            owner.blockPosition().getX() + dx,
                            owner.blockPosition().getZ() + dz,
                            ownerY,
                            lowestY,
                            highestY,
                            entityBlocksHigh);
                    if (landing.isPresent()) {
                        return landing;
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean teleportNearOwner(
            ServerLevel level,
            ChangedEntity creature,
            ServerPlayer owner) {
        Optional<Vec3> landing = findSafeLanding(level, creature, owner);
        if (landing.isEmpty()) {
            return false;
        }
        Vec3 position = landing.get();
        creature.teleportTo(position.x, position.y, position.z);
        settleAfterTeleport(creature);
        return true;
    }

    /**
     * Finds shoreline transitions near a dry target for an aquatic companion.
     * Water navigation cannot path to the target's dry block directly, so the
     * follower first reaches {@link ShoreApproach#water()} and then uses its
     * move controller for the final short step onto {@link ShoreApproach#land()}.
     */
    public static List<ShoreApproach> findShoreApproaches(
            ServerLevel level,
            ChangedEntity creature,
            LivingEntity target,
            int horizontalRadius,
            int verticalRadius) {
        BlockPos origin = target.blockPosition();
        List<ShoreApproach> approaches = new ArrayList<>();
        int entityBlocksHigh = Math.max(1, (int)Math.ceil(creature.getBbHeight()));
        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0
                            && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    for (int step = 0; step <= verticalRadius * 2; step++) {
                        int magnitude = (step + 1) / 2;
                        int dy = step == 0 ? 0
                                : step % 2 == 1 ? -magnitude : magnitude;
                        int y = origin.getY() + dy;
                        BlockPos stand = new BlockPos(x, y, z);
                        if (!level.getFluidState(stand).isEmpty()) {
                            continue;
                        }
                        Optional<Vec3> land = validate(
                                level, creature, target, x, y, z,
                                entityBlocksHigh);
                        if (land.isEmpty()) {
                            continue;
                        }
                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            BlockPos beside = stand.relative(direction);
                            for (int drop = 0; drop <= 1; drop++) {
                                BlockPos water = beside.below(drop);
                                if (!level.getFluidState(water)
                                        .is(FluidTags.WATER)) {
                                    continue;
                                }
                                Optional<Vec3> waterPosition = validate(
                                        level, creature, target,
                                        water.getX(), water.getY(), water.getZ(),
                                        entityBlocksHigh);
                                if (waterPosition.isPresent()) {
                                    ShoreApproach approach = new ShoreApproach(
                                            Vec3.atCenterOf(water), land.get());
                                    if (!approaches.contains(approach)) {
                                        approaches.add(approach);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
            // The nearest owner-centered ring is preferable. Once it contains
            // several alternatives, do not scan a much larger area every tick.
            if (approaches.size() >= 8) {
                break;
            }
        }
        approaches.sort(Comparator
                .comparingDouble((ShoreApproach approach) ->
                        approach.land().distanceToSqr(target.position()))
                .thenComparingDouble(approach ->
                        approach.water().distanceToSqr(creature.position())));
        return List.copyOf(approaches);
    }

    /**
     * Brings a persistent creature beside a player without dropping it into
     * open air or a wall. The returned instance may differ after a dimension
     * transfer, so callers must repair UUID-backed references from it.
     */
    public static Optional<ChangedEntity> moveNearPlayer(
            ChangedEntity creature,
            ServerPlayer player) {
        ServerLevel destination = player.serverLevel();
        Optional<Vec3> safe = findSafeLanding(destination, creature, player);
        if (safe.isEmpty()) {
            return Optional.empty();
        }
        Vec3 landing = safe.get();
        ChangedEntity arrived = creature;
        if (creature.level() != destination) {
            Entity moved = creature.changeDimension(destination, new ITeleporter() {
                @Override
                public PortalInfo getPortalInfo(
                        Entity entity,
                        ServerLevel destinationLevel,
                        Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                    return new PortalInfo(
                            landing, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                }
            });
            if (!(moved instanceof ChangedEntity changed)) {
                return Optional.empty();
            }
            arrived = changed;
        } else {
            creature.teleportTo(landing.x, landing.y, landing.z);
        }
        settleAfterTeleport(arrived);
        arrived.setTarget(null);
        return Optional.of(arrived);
    }

    /** Finds a safe nearby reform position without requiring an owning player. */
    public static Optional<Vec3> findSafeLandingNear(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos center,
            int minimumRadius,
            int maximumRadius,
            int verticalRange) {
        int entityBlocksHigh = Math.max(1, (int)Math.ceil(creature.getBbHeight()));
        int minimumY = Math.max(
                level.getMinBuildHeight() + 1, center.getY() - verticalRange);
        int maximumY = Math.min(
                level.getMaxBuildHeight() - entityBlocksHigh - 1,
                center.getY() + verticalRange);
        for (int radius = Math.max(0, minimumRadius);
                radius <= Math.max(minimumRadius, maximumRadius);
                radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int delta = 0; delta <= verticalRange; delta++) {
                        int below = center.getY() - delta;
                        if (below >= minimumY && below <= maximumY) {
                            Optional<Vec3> landing = validate(
                                    level,
                                    creature,
                                    null,
                                    center.getX() + dx,
                                    below,
                                    center.getZ() + dz,
                                    entityBlocksHigh);
                            if (landing.isPresent()) {
                                return landing;
                            }
                        }
                        if (delta == 0) {
                            continue;
                        }
                        int above = center.getY() + delta;
                        if (above >= minimumY && above <= maximumY) {
                            Optional<Vec3> landing = validate(
                                    level,
                                    creature,
                                    null,
                                    center.getX() + dx,
                                    above,
                                    center.getZ() + dz,
                                    entityBlocksHigh);
                            if (landing.isPresent()) {
                                return landing;
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static void settleAfterTeleport(ChangedEntity creature) {
        creature.getNavigation().stop();
        creature.setDeltaMovement(Vec3.ZERO);
        creature.fallDistance = 0.0F;
        BlockPos feet = creature.blockPosition();
        creature.setOnGround(creature.level().getFluidState(feet).isEmpty());
    }

    private static Optional<Vec3> findInColumn(
            ServerLevel level,
            ChangedEntity creature,
            ServerPlayer owner,
            int x,
            int z,
            int ownerY,
            int lowestY,
            int highestY,
            int entityBlocksHigh) {
        int maximumDelta = Math.max(MAX_BELOW_OWNER, MAX_ABOVE_OWNER);
        for (int delta = 0; delta <= maximumDelta; delta++) {
            int below = ownerY - delta;
            if (below >= lowestY && below <= highestY) {
                Optional<Vec3> landing = validate(
                        level, creature, owner, x, below, z, entityBlocksHigh);
                if (landing.isPresent()) {
                    return landing;
                }
            }
            if (delta == 0) {
                continue;
            }
            int above = ownerY + delta;
            if (above >= lowestY && above <= highestY) {
                Optional<Vec3> landing = validate(
                        level, creature, owner, x, above, z, entityBlocksHigh);
                if (landing.isPresent()) {
                    return landing;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> validate(
            ServerLevel level,
            ChangedEntity creature,
            @Nullable Entity avoid,
            int x,
            int y,
            int z,
            int entityBlocksHigh) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.hasChunkAt(feet)
                || !level.getWorldBorder().isWithinBounds(feet)) {
            return Optional.empty();
        }

        boolean aquaticLanding = (HunterFaction.isAquatic(creature)
                || creature.getNavigation() instanceof WaterBoundPathNavigation)
                && level.getFluidState(feet).is(FluidTags.WATER);
        if (!aquaticLanding) {
            BlockPos supportPos = feet.below();
            BlockState support = level.getBlockState(supportPos);
            if (!support.isFaceSturdy(level, supportPos, Direction.UP)
                    || support.is(BlockTags.LEAVES)
                    || isDangerous(support)) {
                return Optional.empty();
            }
        }

        for (int offsetY = 0; offsetY < entityBlocksHigh; offsetY++) {
            BlockPos occupied = feet.above(offsetY);
            BlockState state = level.getBlockState(occupied);
            if (isDangerous(state)
                    || !aquaticLanding && !level.getFluidState(occupied).isEmpty()
                    || aquaticLanding
                            && !level.getFluidState(occupied).isEmpty()
                            && !level.getFluidState(occupied).is(FluidTags.WATER)) {
                return Optional.empty();
            }
        }

        Vec3 position = Vec3.atBottomCenterOf(feet);
        AABB movedBox = creature.getBoundingBox().move(position.subtract(creature.position()));
        if (avoid != null
                        && movedBox.intersects(avoid.getBoundingBox().inflate(0.2D))
                || !level.noCollision(creature, movedBox)
                || !aquaticLanding && level.containsAnyLiquid(movedBox)) {
            return Optional.empty();
        }
        return Optional.of(position);
    }

    static boolean isDangerous(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.FIRE)
                || block == Blocks.CACTUS
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.WITHER_ROSE
                || block == Blocks.POWDER_SNOW
                || block == Blocks.POINTED_DRIPSTONE;
    }
}
