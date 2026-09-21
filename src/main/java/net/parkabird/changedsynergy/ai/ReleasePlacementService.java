package net.parkabird.changedsynergy.ai;

import java.util.Optional;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Restores real collision space after native grab/wrapping position mirroring ends. */
public final class ReleasePlacementService {
    private ReleasePlacementService() {
    }

    @Nullable
    public static PendingRelease capture(LivingEntity actor, @Nullable LivingEntity held) {
        ChangedEntity creature;
        Player player;
        if (actor instanceof ChangedEntity changed && held instanceof Player other) {
            creature = changed;
            player = other;
        } else if (actor instanceof Player other && held instanceof ChangedEntity changed) {
            creature = changed;
            player = other;
        } else {
            return null;
        }
        if (!(creature.level() instanceof ServerLevel level)
                || player.level() != level || creature.getUnderlyingPlayer() != null
                || !CreatureSocialProfile.allowsSynergySystems(creature)) {
            return null;
        }
        // The no-physics body can have drifted below the floor. Anchor to the
        // body that actually controlled movement, before release clears that flag.
        Vec3 anchor = creature.noPhysics ? player.position() : creature.position();
        return new PendingRelease(level, creature, player, anchor);
    }

    public record PendingRelease(
            ServerLevel level, ChangedEntity creature, Player player, Vec3 anchor) {
        public void finish() {
            repair();
            // Native/Addon code can still resize or move the body later in the
            // current entity tick. Recheck after it and once in the next tick.
            SafeEntityMutationQueue.queue(creature, "release_collision", () -> {
                repair();
                SafeEntityMutationQueue.queue(creature, "release_collision", this::repair);
            });
        }

        private void repair() {
            if (!creature.isAlive() || creature.isRemoved() || creature.level() != level
                    || creature.getUnderlyingPlayer() != null || creature.noPhysics
                    || creature.isPassenger() || creature.isSleeping()
                    || creature.position().distanceToSqr(anchor) > 256.0D) {
                return;
            }
            var ability = BondedSuitService.ability(creature);
            if (ability != null && ability.grabbedEntity != null
                    || creature instanceof LivingEntityDataExtension extension
                            && extension.getGrabbedBy() != null) {
                return;
            }
            Vec3 position = creature.position();
            creature.refreshDimensions();
            creature.setPos(position.x, position.y, position.z);
            AABB body = creature.getBoundingBox();
            // Entity overlap alone is not a reason to teleport an otherwise
            // correctly placed creature. Only repair actual block penetration.
            if (!level.getBlockCollisions(creature, body.deflate(1.0E-7D)).iterator().hasNext()) {
                return;
            }
            Optional<Vec3> safe = findSurfaceLanding(level, creature, player, anchor);
            if (safe.isEmpty()) {
                safe = BondedTeleportSafety.findSafeLandingNear(
                        level, creature, BlockPos.containing(anchor), 4, 6, 3)
                        .filter(landing -> !hasHazardOrUnloadedBlock(level,
                                creature.getBoundingBox().move(landing.subtract(creature.position()))))
                        .filter(landing -> player.level() != level || !player.isAlive()
                                || !creature.getBoundingBox().move(landing.subtract(creature.position()))
                                        .intersects(player.getBoundingBox().inflate(0.05D)));
            }
            safe.ifPresent(landing -> {
                creature.teleportTo(landing.x, landing.y, landing.z);
                BondedTeleportSafety.settleAfterTeleport(creature);
                level.getChunkSource().broadcast(creature, new ClientboundTeleportEntityPacket(creature));
            });
        }
    }

    /** Uses collision-shape tops, not block Y, so slabs, stairs and paths are valid floors. */
    private static Optional<Vec3> findSurfaceLanding(
            ServerLevel level, ChangedEntity creature, Player player, Vec3 anchor) {
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int centerY = BlockPos.containing(anchor).getY();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double x = anchor.x + dx;
                double z = anchor.z + dz;
                for (int y = centerY - 2; y <= centerY + 2; y++) {
                    BlockPos floor = BlockPos.containing(x, y, z);
                    if (!level.hasChunkAt(floor)) {
                        continue;
                    }
                    var state = level.getBlockState(floor);
                    if (BondedTeleportSafety.isDangerous(state)) {
                        continue;
                    }
                    for (AABB part : state.getCollisionShape(
                            level, floor, CollisionContext.of(creature)).toAabbs()) {
                        AABB surface = part.move(floor);
                        if (x < surface.minX || x > surface.maxX
                                || z < surface.minZ || z > surface.maxZ) {
                            continue;
                        }
                        Vec3 candidate = new Vec3(x, surface.maxY, z);
                        double heightDifference = candidate.y - anchor.y;
                        double score = dx * dx + dz * dz + heightDifference * heightDifference * 4.0D;
                        if (score >= bestScore) {
                            continue;
                        }
                        AABB body = creature.getBoundingBox().move(candidate.subtract(creature.position()));
                        if (!level.getWorldBorder().isWithinBounds(body)
                                || player.level() == level && player.isAlive()
                                        && body.intersects(player.getBoundingBox().inflate(0.05D))
                                || hasHazardOrUnloadedBlock(level, body)
                                || !level.noCollision(creature, body)
                                || level.containsAnyLiquid(body)) {
                            continue;
                        }
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean hasHazardOrUnloadedBlock(ServerLevel level, AABB body) {
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX - 1.0E-7D, body.maxY - 1.0E-7D, body.maxZ - 1.0E-7D))) {
            if (!level.hasChunkAt(pos) || BondedTeleportSafety.isDangerous(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }
}
