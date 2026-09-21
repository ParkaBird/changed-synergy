package net.parkabird.changedsynergy.ai;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded, loaded-chunk-only release search. Uses actual collision-shape tops. */
public final class TakeoverSafety {
    private TakeoverSafety() {}

    public static boolean threatened(ServerPlayer player, Vec3 position, @Nullable Entity carrier) {
        return !player.serverLevel().getEntitiesOfClass(Mob.class,
                new AABB(position, position).inflate(8.0, 5.0, 8.0), mob ->
                        mob != carrier && mob.isAlive()
                        && (mob.getTarget() == player
                        || mob instanceof Enemy && !(mob instanceof ChangedEntity))).isEmpty();
    }

    public static Optional<Vec3> find(ServerPlayer player, Vec3 anchor,
            @Nullable Entity carrier, int radius, boolean avoidThreats) {
        return find(player, anchor, carrier, radius, avoidThreats, 0.6, 1.8);
    }

    public static Optional<Vec3> find(ServerPlayer player, Vec3 anchor,
            @Nullable Entity carrier, int radius, boolean avoidThreats,
            double restoredWidth, double restoredHeight) {
        return find(player, anchor, carrier, radius, avoidThreats,
                restoredWidth, restoredHeight, 0.0);
    }

    public static Optional<Vec3> find(ServerPlayer player, Vec3 anchor,
            @Nullable Entity carrier, int radius, boolean avoidThreats,
            double restoredWidth, double restoredHeight, double minimumCarrierDistance) {
        return find(player, anchor, carrier, radius, avoidThreats,
                restoredWidth, restoredHeight, minimumCarrierDistance, 5);
    }

    /** Wider loaded-chunk-only fallback used after the normal release deadline. */
    public static Optional<Vec3> findEmergency(ServerPlayer player, Vec3 anchor,
            @Nullable Entity carrier, double restoredWidth, double restoredHeight) {
        return find(player, anchor, carrier, 32, false,
                restoredWidth, restoredHeight, 0.0, 32);
    }

    /** Searches only the top motion-blocking surface, never a cave beneath it. */
    public static Optional<Vec3> findSurface(ServerPlayer player, Vec3 anchor,
            @Nullable Entity carrier, int radius, boolean avoidThreats,
            double restoredWidth, double restoredHeight) {
        ServerLevel level = player.serverLevel();
        double width = Math.max(restoredWidth, player.getBbWidth());
        double height = Math.max(restoredHeight, player.getBbHeight());
        for (int ring = 0; ring <= Math.min(radius, 64); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = (int)Math.floor(anchor.x) + dx;
                    int z = (int)Math.floor(anchor.z) + dz;
                    BlockPos column = new BlockPos(x, level.getMinBuildHeight(), z);
                    if (!level.hasChunkAt(column)) continue;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    Optional<Vec3> point = validPoint(player, carrier,
                            x + 0.5D, z + 0.5D, top - 1, width, height, 0.0D, avoidThreats);
                    if (point.isPresent()) return point;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> find(ServerPlayer player, Vec3 anchor,
            @Nullable Entity carrier, int radius, boolean avoidThreats,
            double restoredWidth, double restoredHeight, double minimumCarrierDistance,
            int verticalRadius) {
        ServerLevel level = player.serverLevel();
        int baseY = BlockPos.containing(anchor).getY();
        double width = Math.max(restoredWidth, player.getBbWidth());
        double height = Math.max(restoredHeight, player.getBbHeight());
        for (int ring = 0; ring <= Math.min(radius, 32); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    double x = Math.floor(anchor.x) + dx + 0.5;
                    double z = Math.floor(anchor.z) + dz + 0.5;
                    for (int offset = 0; offset <= verticalRadius * 2; offset++) {
                        int dy = offset == 0 ? 0 : (offset + 1) / 2 * (offset % 2 == 1 ? -1 : 1);
                        Optional<Vec3> point = validPoint(player, carrier, x, z, baseY + dy - 1,
                                width, height, minimumCarrierDistance, avoidThreats);
                        if (point.isPresent()) return point;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Vec3> validPoint(ServerPlayer player, @Nullable Entity carrier,
            double x, double z, int floorY, double width, double height,
            double minimumCarrierDistance, boolean avoidThreats) {
        ServerLevel level = player.serverLevel();
        BlockPos floor = BlockPos.containing(x, floorY, z);
        if (floor.getY() < level.getMinBuildHeight() || floor.getY() >= level.getMaxBuildHeight()
                || !level.hasChunkAt(floor)) return Optional.empty();
        var state = level.getBlockState(floor);
        if (BondedTeleportSafety.isDangerous(state) || !state.getFluidState().isEmpty())
            return Optional.empty();
        for (AABB part : state.getCollisionShape(level, floor, CollisionContext.of(player)).toAabbs()) {
            AABB top = part.move(floor);
            if (x < top.minX || x > top.maxX || z < top.minZ || z > top.maxZ) continue;
            Vec3 point = new Vec3(x, top.maxY, z);
            AABB body = new AABB(x - width / 2, point.y, z - width / 2,
                    x + width / 2, point.y + height, z + width / 2).deflate(1.0E-6);
            if (!level.getWorldBorder().isWithinBounds(body)
                    || body.maxY > level.getMaxBuildHeight()
                    || carrier != null && carrier.isAlive()
                        && carrier.getBoundingBox().inflate(0.05).intersects(body)
                    || carrier != null && carrier.isAlive()
                        && carrier.distanceToSqr(point) < minimumCarrierDistance * minimumCarrierDistance
                    || !clear(level, player, carrier, body)
                    || avoidThreats && threatened(player, point, carrier)) continue;
            return Optional.of(point);
        }
        return Optional.empty();
    }

    private static boolean clear(ServerLevel level, ServerPlayer player,
            @Nullable Entity carrier, AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos) || BondedTeleportSafety.isDangerous(level.getBlockState(pos))
                    || !level.getFluidState(pos).isEmpty()) return false;
        }
        // The carrier is checked explicitly above. Do not reject a candidate twice
        // merely because the hidden wrapped player currently overlaps that carrier.
        return !level.getBlockCollisions(player, box).iterator().hasNext()
                && level.getEntities(player, box, other -> other != carrier
                        && other.isAlive() && other.isPickable()).isEmpty();
    }
}
