package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.ai.InstinctConfinementService;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Addon's ordinary "hands occupied" gate from disabling takeover carriers. */
@Mixin(value = MeleeAttackGoal.class, priority = 900, remap = false)
public abstract class TakeoverCarrierMeleeMixin {
    // Mixin cannot alias a protected field. Resolve this field once through
    // Forge's mapping service, for both Mojmap development and SRG releases.
    @Unique private static final java.lang.reflect.Field changedSynergy$mobField =
            net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MeleeAttackGoal.class, "f_25540_");
    // Other mods' access transformers can widen even vanilla-private fields.
    // An alias then fails before our runtime Addon guard can run.
    @Unique private static final java.lang.reflect.Field changedSynergy$pathField =
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MeleeAttackGoal.class, "f_25543_");
    @Unique private static final java.lang.reflect.Field changedSynergy$lastCheckField =
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MeleeAttackGoal.class, "f_25550_");
    @Shadow(remap = false, aliases = {"m_6639_"}) protected abstract double getAttackReachSqr(LivingEntity target);

    @Inject(method={"canContinueToUse", "m_8045_"}, at=@At("RETURN"), cancellable=true, require=1, remap=false)
    private void changedSynergy$keepContactPursuit(CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue())return;
        try {
            var mob=(PathfinderMob)changedSynergy$mobField.get(this);
            if(mob instanceof ChangedEntity body && mob.getTarget() instanceof ServerPlayer human
                    && InstinctConfinementService.canTarget(body,human)
                    && !body.isNoAi() && !body.isSleeping() && !body.isPassenger()
                    && body.getSensing().hasLineOfSight(human)
                    && body.distanceToSqr(human)<=getAttackReachSqr(human)) {
                // Reaching the last path node is normal at melee range. Retain
                // this goal and its cooldown instead of stop/start arm flicker.
                callback.setReturnValue(true);
            }
        } catch(IllegalAccessException failure) {throw new IllegalStateException("Cannot access melee goal state",failure);}
    }

    @Inject(method = {"canUse", "m_8036_"}, at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void changedSynergy$keepCarrierMelee(CallbackInfoReturnable<Boolean> callback) {
        if (!ChangedAddonCompat.isLoaded()) return;
        try {
            PathfinderMob mob = (PathfinderMob)changedSynergy$mobField.get(this);
            if (!(mob instanceof ChangedEntity carrier)
                    || !TakeoverService.autonomousCarrier(carrier)) return;
            // The original 1.20.1 canUse admission, before Addon's cancellable HEAD.
            // Retain the same goal, species reach override, path, 20-tick throttle,
            // speed, attack interval and subsequent native start/continue/tick.
            long tick = mob.level().getGameTime();
            if (tick - changedSynergy$lastCheckField.getLong(this) < 20L) {
                callback.setReturnValue(false);
                return;
            }
            changedSynergy$lastCheckField.setLong(this, tick);
            LivingEntity target = mob.getTarget();
            if (target == null || !target.isAlive()) {
                callback.setReturnValue(false);
                return;
            }
            Path path = mob.getNavigation().createPath(target, 0);
            changedSynergy$pathField.set(this, path);
            callback.setReturnValue(path != null || getAttackReachSqr(target)
                    >= mob.distanceToSqr(target.getX(), target.getY(), target.getZ()));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access melee goal state", failure);
        }
    }
}
