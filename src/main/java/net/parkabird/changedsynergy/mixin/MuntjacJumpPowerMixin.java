package net.parkabird.changedsynergy.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.entity.MuntjacJumpService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MuntjacJumpPowerMixin {
    @ModifyReturnValue(method = {"getJumpPower", "m_6118_"}, at = @At("RETURN"), remap = false)
    private float changedSynergy$chargedPower(float original) {
        if (!((Object)this instanceof Player player)) return original;
        // Keep native potion height; only the ordinary species/block jump is charged.
        return original + (original - player.getJumpBoostPower()) * (MuntjacJumpService.multiplier(player) - 1);
    }
}
