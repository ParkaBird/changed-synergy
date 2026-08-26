package net.parkabird.changedsynergy.ai;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Prevents Changed's ordinary FloatGoal from pinning a pursuing land creature
 * to the surface while its player target is swimming below it.
 */
public final class UnderwaterPursuitGoal extends Goal {
    private static final double MIN_SWIM_SPEED = 0.08D;
    private static final double MAX_SWIM_SPEED = 0.18D;
    private static final Set<ChangedEntity> PENDING_ORIENTATION =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final ChangedEntity mob;
    private Pose previousPose;
    private Pose previousOverridePose;

    public UnderwaterPursuitGoal(ChangedEntity mob) {
        this.mob = mob;
        // FloatGoal owns only JUMP.  Taking that flag lets the native melee or
        // grab goal keep its MOVE control while suppressing the upward kicks.
        setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return target() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target() != null;
    }

    @Override
    public void start() {
        previousPose = mob.getPose();
        previousOverridePose = mob.overridePose;
        applySwimmingPose();
    }

    @Override
    public void tick() {
        ServerPlayer target = target();
        if (target == null) {
            return;
        }

        double targetY = target.getY() + target.getBbHeight() * 0.55D;
        double mobY = mob.getY() + mob.getBbHeight() * 0.5D;
        Vec3 offset = new Vec3(
                target.getX() - mob.getX(),
                targetY - mobY,
                target.getZ() - mob.getZ());
        if (offset.lengthSqr() < 1.0E-5D) {
            return;
        }

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.getMoveControl().setWantedPosition(
                target.getX(), targetY, target.getZ(), 1.0D);

        double swimSpeed = Mth.clamp(
                mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.7D,
                MIN_SWIM_SPEED,
                MAX_SWIM_SPEED);
        double horizontalLength = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        double wantedX = horizontalLength > 1.0E-4D
                ? offset.x / horizontalLength * swimSpeed
                : 0.0D;
        double wantedZ = horizontalLength > 1.0E-4D
                ? offset.z / horizontalLength * swimSpeed
                : 0.0D;
        double wantedY = Mth.clamp(offset.y * 0.12D, -swimSpeed, swimSpeed);

        Vec3 current = mob.getDeltaMovement();
        double nextY = Mth.lerp(0.35D, current.y, wantedY);
        if (offset.y < -0.35D) {
            // Counter residual buoyancy and the last queued FloatGoal jump.
            nextY = Math.min(nextY, -0.04D);
        }
        mob.setDeltaMovement(
                Mth.lerp(0.18D, current.x, wantedX),
                nextY,
                Mth.lerp(0.18D, current.z, wantedZ));
        applySwimmingPose();
        PENDING_ORIENTATION.add(mob);
    }

    @Override
    public void stop() {
        PENDING_ORIENTATION.remove(mob);
        mob.setSwimming(false);
        if (mob.overridePose == Pose.SWIMMING) {
            mob.overridePose = previousOverridePose;
        }
        if (mob.getPose() == Pose.SWIMMING) {
            mob.setPose(previousPose != null ? previousPose : Pose.STANDING);
        }
        previousPose = null;
        previousOverridePose = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void applySwimmingPose() {
        mob.overridePose = Pose.SWIMMING;
        mob.setPose(Pose.SWIMMING);
        mob.setSwimming(true);
    }

    /**
     * Runs after vanilla move/look controls so an old ground-path node cannot
     * turn the model away from the direction used by the underwater pursuit.
     */
    public static void alignSwimmingOrientation(ChangedEntity mob) {
        ServerPlayer target = target(mob);
        if (target == null) {
            return;
        }

        double targetY = target.getY() + target.getBbHeight() * 0.55D;
        double mobY = mob.getY() + mob.getBbHeight() * 0.5D;
        double dx = target.getX() - mob.getX();
        double dy = targetY - mobY;
        double dz = target.getZ() - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D && Math.abs(dy) < 1.0E-4D) {
            return;
        }

        if (horizontal >= 1.0E-4D) {
            float wantedYaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float yaw = approachDegrees(mob.getYRot(), wantedYaw, 30.0F);
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
            mob.yHeadRot = yaw;
        }

        float wantedPitch = (float)(-Mth.atan2(dy, Math.max(horizontal, 1.0E-4D))
                * Mth.RAD_TO_DEG);
        wantedPitch = Mth.clamp(wantedPitch, -75.0F, 75.0F);
        mob.setXRot(approachDegrees(mob.getXRot(), wantedPitch, 20.0F));
    }

    /** Applies queued headings after every entity in this level has finished its AI controls. */
    public static void flushSwimmingOrientations(Level level) {
        Iterator<ChangedEntity> iterator = PENDING_ORIENTATION.iterator();
        while (iterator.hasNext()) {
            ChangedEntity mob = iterator.next();
            if (mob.level() != level) {
                continue;
            }
            iterator.remove();
            if (mob.isAlive() && !mob.isRemoved()) {
                alignSwimmingOrientation(mob);
            }
        }
    }

    private static float approachDegrees(float current, float wanted, float maximumChange) {
        return current + Mth.clamp(
                Mth.wrapDegrees(wanted - current), -maximumChange, maximumChange);
    }

    private ServerPlayer target() {
        return target(mob);
    }

    private static ServerPlayer target(ChangedEntity mob) {
        if (!(mob.getTarget() instanceof ServerPlayer player)
                || !mob.isInWaterOrBubble()
                || mob.getNavigation() instanceof WaterBoundPathNavigation
                || !player.isUnderWater()
                || !HuntAIEvents.isHuntAIEnabled(mob)
                || !HuntAIEvents.isEligibleHunter(mob)
                || !HuntAIEvents.canReacquire(mob, player, true)
                || ChangedAddonCompat.isGrabberBusy(mob)
                || LatexSocialMemory.isSecondaryGrabActive(mob, player)) {
            return null;
        }
        return player;
    }
}
