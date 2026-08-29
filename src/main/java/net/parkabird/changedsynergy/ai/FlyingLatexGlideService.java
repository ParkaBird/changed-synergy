package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.minecraft.world.phys.Vec3;

/** Gives Changed forms with native flight support a safe wing-assisted descent. */
public final class FlyingLatexGlideService {
    private static final String GLIDING = "ChangedSynergyWingGliding";
    private static final String WAS_FLYING = "ChangedSynergyWasFlyingBeforeGlide";
    private static final int FALL_FLYING_FLAG = 7;
    private static final double START_FALL_SPEED = -0.34D;
    private static final double MAX_DESCENT_SPEED = -0.16D;
    private static final float START_FALL_DISTANCE = 3.0F;

    private FlyingLatexGlideService() {
    }

    public static void tick(ChangedEntity creature) {
        if (creature.level().isClientSide || !creature.isAlive()) {
            return;
        }
        TransfurVariant<?> variant = creature.getSelfVariant();
        boolean canGlide = variant != null && variant.canGlide;
        boolean grounded = creature.onGround()
                || creature.isInWaterOrBubble()
                || creature.isPassenger();
        boolean active = creature.getPersistentData().getBoolean(GLIDING);

        if (!canGlide || grounded) {
            stopGliding(creature, active);
            return;
        }

        Vec3 motion = creature.getDeltaMovement();
        boolean shouldStart = active
                || motion.y < START_FALL_SPEED
                        && creature.fallDistance >= START_FALL_DISTANCE;
        if (!shouldStart) {
            return;
        }

        if (!active) {
            creature.getPersistentData().putBoolean(GLIDING, true);
            creature.getPersistentData().putBoolean(WAS_FLYING, creature.isFlying());
        }

        /*
         * Changed's wing-flap animations are driven by its own synchronized
         * FLAG_IS_FLYING state. The vanilla fall-flying flag only selects the
         * static glide pose and may be cleared again by LivingEntity, which is
         * why the old implementation slowed the fall without visibly flapping.
         * Reassert the native flag every tick so clients enter CREATIVE_FLY and
         * run the form's original wing animator throughout the descent.
         */
        creature.setChangedEntityFlag(ChangedEntity.FLAG_IS_FLYING, true);
        if (creature.isFallFlying()) {
            creature.setSharedFlag(FALL_FLYING_FLAG, false);
        }
        if (motion.y < MAX_DESCENT_SPEED) {
            creature.setDeltaMovement(
                    motion.x * 0.985D,
                    MAX_DESCENT_SPEED,
                    motion.z * 0.985D);
            creature.hasImpulse = true;
        }
        creature.fallDistance = 0.0F;
    }

    private static void stopGliding(ChangedEntity creature, boolean active) {
        if (!active) {
            return;
        }
        boolean wasFlying = creature.getPersistentData().getBoolean(WAS_FLYING);
        creature.getPersistentData().remove(GLIDING);
        creature.getPersistentData().remove(WAS_FLYING);
        creature.setChangedEntityFlag(ChangedEntity.FLAG_IS_FLYING, wasFlying);
        // Clean up the vanilla flag left by builds that used the old glide path.
        if (creature.isFallFlying()) {
            creature.setSharedFlag(FALL_FLYING_FLAG, false);
        }
        creature.fallDistance = 0.0F;
    }
}
