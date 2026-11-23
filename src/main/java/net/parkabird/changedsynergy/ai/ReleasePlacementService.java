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
        if (held == null || !(actor.level() instanceof ServerLevel level) || held.level() != level) {
            return null;
        }
        LivingEntity subject;
        LivingEntity partner;
        if (actor instanceof ChangedEntity changed && held instanceof Player other) {
            subject = changed;
            partner = other;
        } else if (actor instanceof Player other && held instanceof ChangedEntity changed) {
            subject = changed;
            partner = other;
        } else if (actor instanceof ChangedEntity || held instanceof ChangedEntity) {
            subject = held.noPhysics || !actor.noPhysics ? held : actor;
            partner = subject == actor ? held : actor;
        } else {
            return null;
        }
        if (subject instanceof ChangedEntity changed
                && (changed.getUnderlyingPlayer() != null
                        || !CreatureSocialProfile.allowsSynergySystems(changed))) {
            return null;
        }
        if (!(subject instanceof ChangedEntity)
                && (!(partner instanceof ChangedEntity changed)
                        || !CreatureSocialProfile.allowsSynergySystems(changed))) {
            return null;
        }
        // The no-physics body can have drifted below the floor. Anchor to the
        // body that actually controlled movement, before release clears that flag.
        Vec3 anchor = subject.noPhysics && !partner.noPhysics ? partner.position() : subject.position();
        return new PendingRelease(level, subject, partner, anchor);
    }

    public record PendingRelease(
            ServerLevel level, LivingEntity subject, LivingEntity partner, Vec3 anchor) {
        public void finish() {
            repair();
            // Native/Addon code can still resize or move the body later in the
            // current entity tick. Recheck after it and once in the next tick.
            SafeEntityMutationQueue.queue(subject, "release_collision", () -> {
                repair();
                SafeEntityMutationQueue.queue(subject, "release_collision", this::repair);
            });
        }

        private void repair() {
            if (!subject.isAlive() || subject.isRemoved() || subject.level() != level
                    || subject.noPhysics || subject.isPassenger() || subject.isSleeping()
                    || subject.position().distanceToSqr(anchor) > 256.0D) {
                return;
            }
            if (subject instanceof ChangedEntity changed && changed.getUnderlyingPlayer() != null) {
                return;
            }
            var ability = subject instanceof ChangedEntity changed ? BondedSuitService.ability(changed) : null;
            if (ability != null && ability.grabbedEntity != null
                    || subject instanceof LivingEntityDataExtension extension
                            && extension.getGrabbedBy() != null) {
                return;
            }
            Vec3 position = subject.position();
            subject.refreshDimensions();
            subject.setPos(position.x, position.y, position.z);
            AABB body = subject.getBoundingBox();
            // Entity overlap alone is not a reason to teleport an otherwise
            // correctly placed creature. Only repair actual block penetration.
            if (!level.getBlockCollisions(subject, body.deflate(1.0E-7D)).iterator().hasNext()) {
                return;
            }
            Optional<Vec3> safe = findSurfaceLanding(level, subject, partner, anchor, false);
            if (safe.isEmpty() && subject instanceof ChangedEntity changed) {
                safe = BondedTeleportSafety.findSafeLandingNear(
                        level, changed, BlockPos.containing(anchor), 4, 6, 3)
                        .filter(landing -> !hasHazardOrUnloadedBlock(level,
                                subject.getBoundingBox().move(landing.subtract(subject.position()))))
                        .filter(landing -> partner.level() != level || !partner.isAlive()
                                || !subject.getBoundingBox().move(landing.subtract(subject.position()))
                                        .intersects(partner.getBoundingBox().inflate(0.05D)));
            }
            if (safe.isEmpty()) {
                safe = findSurfaceLanding(level, subject, partner, anchor, true);
            }
            safe.ifPresent(landing -> {
                subject.setDeltaMovement(Vec3.ZERO);
                subject.resetFallDistance();
                subject.teleportTo(landing.x, landing.y, landing.z);
                if (subject instanceof ChangedEntity changed) {
                    BondedTeleportSafety.settleAfterTeleport(changed);
                } else {
                    subject.setDeltaMovement(Vec3.ZERO);
                    subject.resetFallDistance();
                    subject.setOnGround(true);
                }
                level.getChunkSource().broadcast(subject, new ClientboundTeleportEntityPacket(subject));
            });
        }
    }

    /** Uses collision-shape tops, not block Y, so slabs, stairs and paths are valid floors. */
    private static Optional<Vec3> findSurfaceLanding(
            ServerLevel level, LivingEntity subject, LivingEntity partner, Vec3 anchor, boolean allowPartnerOverlap) {
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
                            level, floor, CollisionContext.of(subject)).toAabbs()) {
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
                        AABB body = subject.getBoundingBox().move(candidate.subtract(subject.position()));
                        if (!level.getWorldBorder().isWithinBounds(body)
                                || (!allowPartnerOverlap && partner.level() == level && partner.isAlive()
                                        && body.intersects(partner.getBoundingBox().inflate(0.05D)))
                                || hasHazardOrUnloadedBlock(level, body)
                                || !level.noCollision(subject, body)
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
