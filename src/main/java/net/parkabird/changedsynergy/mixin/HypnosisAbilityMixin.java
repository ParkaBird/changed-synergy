package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.HypnosisAbility;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HypnosisAbility.class, remap = false)
public abstract class HypnosisAbilityMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void changedSynergy$playerContest(IAbstractChangedEntity entity, CallbackInfo callback) {
        HypnosisQteService.handlePlayerAbilityTick(entity);
    }

    // Keep native mob calming and non-player hypnosis; only player targets enter the shared contest.
    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getNearbyEntities(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 0, remap = false)
    private <T extends LivingEntity> List<T> changedSynergy$mappedContestTargets(
            Level level, Class<T> type, TargetingConditions conditions, LivingEntity source, AABB bounds) {
        return changedSynergy$nativeTargets(level, type, conditions, source, bounds);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_45971_(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 0, remap = false)
    private <T extends LivingEntity> List<T> changedSynergy$srgContestTargets(
            Level level, Class<T> type, TargetingConditions conditions, LivingEntity source, AABB bounds) {
        return changedSynergy$nativeTargets(level, type, conditions, source, bounds);
    }

    @org.spongepowered.asm.mixin.Unique
    private static <T extends LivingEntity> List<T> changedSynergy$nativeTargets(
            Level level, Class<T> type, TargetingConditions conditions, LivingEntity source, AABB bounds) {
        List<T> targets = level.getNearbyEntities(type, conditions, source, bounds);
        return HypnosisQteService.usesSharedPlayerContest(source)
                ? targets.stream().filter(target -> !(target instanceof Player)).toList() : targets;
    }
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void changedSynergy$observeHypnosis(
            IAbstractChangedEntity entity,
            CallbackInfo callback) {
        if (!HypnosisQteService.usesSharedPlayerContest(entity.getEntity())) {
            HypnosisQteService.observeAbilityTick(entity);
        }
    }
}
