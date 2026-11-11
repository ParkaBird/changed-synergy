package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.entity.SpottedHyenaDiet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class SpottedHyenaBoneItemMixin {
    @Inject(method = {"use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            "m_7203_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$chewBone(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        var stack = player.getItemInHand(hand);
        if (SpottedHyenaDiet.isSpeciesFood(stack, player)) {
            player.startUsingItem(hand);
            ci.setReturnValue(InteractionResultHolder.consume(stack));
        }
    }

    @Inject(method = {"getUseDuration(Lnet/minecraft/world/item/ItemStack;)I", "m_8105_(Lnet/minecraft/world/item/ItemStack;)I"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$boneChewingTime(ItemStack stack, CallbackInfoReturnable<Integer> ci) {
        if (SpottedHyenaDiet.isSpeciesFoodItem(stack)) ci.setReturnValue(32);
    }

    @Inject(method = {"getUseAnimation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/UseAnim;",
            "m_6164_(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/UseAnim;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$boneChewingAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> ci) {
        if (SpottedHyenaDiet.isSpeciesFoodItem(stack)) ci.setReturnValue(UseAnim.EAT);
    }

    @Inject(method = {"finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            "m_5922_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$finishBone(ItemStack stack, Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> ci) {
        if (SpottedHyenaDiet.isSpeciesFood(stack, user)) ci.setReturnValue(SpottedHyenaDiet.finish(stack, user));
    }
}
