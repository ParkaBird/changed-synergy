package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.item.LatexFlask;
import net.ltxprogrammer.changed.item.LatexSyringe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.ai.BondedRevivalService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Arms only an owner's identity-bearing vessel at the moment it is consumed. */
@Mixin(value = {LatexSyringe.class, LatexFlask.class}, remap = false)
public abstract class BondedRevivalVesselUseMixin {
    @Inject(method = {"finishUsingItem", "m_5922_"}, at = @At("HEAD"))
    private void changedSynergy$prepareRevival(ItemStack stack, Level level,
            LivingEntity user, CallbackInfoReturnable<ItemStack> callback) {
        if (user instanceof ServerPlayer player) {
            BondedRevivalService.prepareVesselUse(stack, player);
        }
    }
}
