package net.parkabird.changedsynergy.mixin;

import java.lang.reflect.Field;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Locks sleeping ChangedEntity NPCs horizontally without freezing animation decay or fighting bed height. */
@Mixin(LivingEntity.class)
public abstract class SleepingLivingLockMixin {
    @Unique
    private static final Field CHANGED_SYNERGY$MOVE_OP_FIELD =
            ObfuscationReflectionHelper.findField(MoveControl.class, "f_24981_");
    @Unique
    private static final Object CHANGED_SYNERGY$MOVE_WAIT = changedSynergy$resolveWaitOperation();

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object changedSynergy$resolveWaitOperation() {
        Class<?> type = CHANGED_SYNERGY$MOVE_OP_FIELD.getType();
        return type.isEnum() ? Enum.valueOf((Class) type, "WAIT") : null;
    }

    @Inject(method = {"isPushable", "m_6094_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$unpushableWhileSleeping(CallbackInfoReturnable<Boolean> cir) {
        if (changedSynergy$isSleepingNpc()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = {"travel", "m_7023_"}, at = @At("HEAD"), argsOnly = true, remap = false)
    private Vec3 changedSynergy$zeroTravelInputWhileSleeping(Vec3 travelVector) {
        if (changedSynergy$isSleepingNpc()) {
            ChangedEntity entity = (ChangedEntity) (Object) this;
            changedSynergy$clearMoveInputs(entity);
            Vec3 dm = entity.getDeltaMovement();
            if (dm.x != 0.0D || dm.z != 0.0D) {
                entity.setDeltaMovement(0.0D, dm.y, 0.0D);
            }
            return Vec3.ZERO;
        }
        return travelVector;
    }

    @Inject(method = {"travel", "m_7023_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$freezePerchTravelWhileSleeping(Vec3 travelVector, CallbackInfo ci) {
        if (changedSynergy$isSleepingNpc()) {
            ChangedEntity entity = (ChangedEntity) (Object) this;
            if (PerchSleepService.perch(entity) != null) {
                entity.setDeltaMovement(Vec3.ZERO);
                entity.walkAnimation.setSpeed(0.0F);
                ci.cancel();
            }
        }
    }

    @Inject(method = {"travel", "m_7023_"}, at = @At("TAIL"), remap = false)
    private void changedSynergy$clearWalkAfterBedTravel(Vec3 travelVector, CallbackInfo ci) {
        if (changedSynergy$isSleepingNpc()) {
            ChangedEntity entity = (ChangedEntity) (Object) this;
            Vec3 dm = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0D, dm.y, 0.0D);
            entity.walkAnimation.setSpeed(0.0F);
        }
    }

    @Inject(method = {"tick", "m_8119_"}, at = @At("TAIL"), remap = false)
    private void changedSynergy$anchorSleepingPosition(CallbackInfo ci) {
        if (!changedSynergy$isSleepingNpc()) {
            return;
        }
        ChangedEntity entity = (ChangedEntity) (Object) this;
        changedSynergy$clearMoveInputs(entity);
        entity.walkAnimation.setSpeed(0.0F);

        if (entity.level().isClientSide) {
            return;
        }
        if (!entity.getNavigation().isDone()) {
            entity.getNavigation().stop();
        }
        entity.getSleepingPos().ifPresent(bedPos -> {
            if (PerchSleepService.perch(entity) != null) {
                Vec3 perchPos = PerchSleepService.position(entity.level(), bedPos);
                if (entity.position().distanceToSqr(perchPos) > 1.0E-4D) {
                    entity.setPos(perchPos.x, perchPos.y, perchPos.z);
                }
                entity.setDeltaMovement(Vec3.ZERO);
                return;
            }
            double targetX = bedPos.getX() + 0.5D;
            double targetZ = bedPos.getZ() + 0.5D;
            double dx = entity.getX() - targetX;
            double dz = entity.getZ() - targetZ;
            double y = entity.getY();
            boolean outOfVerticalBounds = y < bedPos.getY() - 0.1D || y > bedPos.getY() + 1.2D;
            if (dx * dx + dz * dz > 0.01D || outOfVerticalBounds) {
                double targetY = (y >= bedPos.getY() + 0.4D && y <= bedPos.getY() + 0.9D)
                        ? y
                        : bedPos.getY() + 0.6875D;
                entity.setPos(targetX, targetY, targetZ);
            }
            Vec3 dm = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0D, Math.min(0.0D, dm.y), 0.0D);
        });
    }

    @Unique
    private boolean changedSynergy$isSleepingNpc() {
        return (Object) this instanceof ChangedEntity entity
                && entity.getUnderlyingPlayer() == null
                && entity.isSleeping()
                && entity.getSleepingPos().isPresent();
    }

    @Unique
    private static void changedSynergy$clearMoveInputs(ChangedEntity entity) {
        entity.setSpeed(0.0F);
        entity.setZza(0.0F);
        entity.setXxa(0.0F);
        entity.setYya(0.0F);
        if (!entity.level().isClientSide && CHANGED_SYNERGY$MOVE_WAIT != null) {
            MoveControl moveControl = entity.getMoveControl();
            try {
                if (CHANGED_SYNERGY$MOVE_OP_FIELD.get(moveControl) != CHANGED_SYNERGY$MOVE_WAIT) {
                    CHANGED_SYNERGY$MOVE_OP_FIELD.set(moveControl, CHANGED_SYNERGY$MOVE_WAIT);
                }
            } catch (IllegalAccessException ignored) {
                // Fallback handled by zeroing speeds every tick.
            }
        }
    }
}
