package net.parkabird.changedsynergy.mixin;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.parkabird.changedsynergy.ai.CompanionOpenDoorGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends A* node budget for multi-floor paths, preserves range across recomputes, and retries crouch clearance. */
@Mixin(PathNavigation.class)
public abstract class PathNavigationExtensionMixin {
    @Unique
    private float changedSynergy$extendedRange;

    @Unique
    private boolean changedSynergy$crouchRetrying;

    @Unique
    private float changedSynergy$savedMult = -1.0F;

    @Inject(
            method = {
                "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
                "m_148222_(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;"
            },
            at = @At("HEAD"),
            remap = false)
    private void changedSynergy$beforeCreatePath(
            Set<BlockPos> targets,
            int regionOffset,
            boolean offsetUp,
            int accuracy,
            float followRange,
            CallbackInfoReturnable<Path> callback) {
        if (changedSynergy$crouchRetrying) return;
        PathNavigation self = (PathNavigation) (Object) this;
        if (followRange > 40.0F) {
            changedSynergy$extendedRange = followRange;
            changedSynergy$savedMult = self.maxVisitedNodesMultiplier;
            float scaled = Math.min(8.0F, (followRange / 40.0F) * 2.5F);
            self.setMaxVisitedNodesMultiplier(Math.max(changedSynergy$savedMult, scaled));
        } else {
            changedSynergy$extendedRange = 0.0F;
            changedSynergy$savedMult = -1.0F;
        }
    }

    @Inject(
            method = {
                "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
                "m_148222_(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void changedSynergy$afterCreatePath(
            Set<BlockPos> targets,
            int regionOffset,
            boolean offsetUp,
            int accuracy,
            float followRange,
            CallbackInfoReturnable<Path> callback) {
        if (changedSynergy$crouchRetrying) return;
        PathNavigation self = (PathNavigation) (Object) this;
        Path result = callback.getReturnValue();
        try {
            if ((result == null || !result.canReach())
                    && self.mob instanceof ChangedEntity creature
                    && CompanionOpenDoorGoal.canAutoCrouch(creature)) {
                Path savedPath = self.path;
                BlockPos savedTarget = self.targetPos;
                int savedReach = self.reachRange;
                changedSynergy$crouchRetrying = true;
                CompanionOpenDoorGoal.beginCrouchPlan();
                try {
                    self.path = null;
                    Path crouched = self.createPath(
                            targets, regionOffset, offsetUp, accuracy,
                            Math.max(followRange, changedSynergy$extendedRange));
                    if (crouched != null && crouched.canReach()) {
                        result = crouched;
                        callback.setReturnValue(crouched);
                    } else {
                        self.path = savedPath;
                        self.targetPos = savedTarget;
                        self.reachRange = savedReach;
                    }
                } finally {
                    CompanionOpenDoorGoal.endCrouchPlan();
                    changedSynergy$crouchRetrying = false;
                }
            }
        } finally {
            if (changedSynergy$savedMult >= 0.0F) {
                self.setMaxVisitedNodesMultiplier(changedSynergy$savedMult);
                changedSynergy$savedMult = -1.0F;
            }
            if (result == null && self instanceof WallClimberNavigation climber) {
                climber.pathToPosition = null;
            }
        }
    }

    @Inject(
            method = {"recomputePath()V", "m_26569_()V"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$preserveExtendedRangeOnRecompute(CallbackInfo callback) {
        if (changedSynergy$extendedRange <= 40.0F) return;
        PathNavigation self = (PathNavigation) (Object) this;
        if (self.targetPos == null) return;
        long now = self.level.getGameTime();
        if (now - self.timeLastRecompute > 20L) {
            self.path = null;
            self.path = self.createPath(
                    ImmutableSet.of(self.targetPos), 8, false, self.reachRange, changedSynergy$extendedRange);
            self.timeLastRecompute = now;
            self.hasDelayedRecomputation = false;
        } else {
            self.hasDelayedRecomputation = true;
        }
        callback.cancel();
    }

    @Inject(
            method = {"stop()V", "m_26573_()V"},
            at = @At("HEAD"),
            remap = false)
    private void changedSynergy$clearExtendedRangeOnStop(CallbackInfo callback) {
        changedSynergy$extendedRange = 0.0F;
        if ((Object) this instanceof WallClimberNavigation climber) {
            climber.pathToPosition = null;
        }
    }
}
