package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.layers.DarkLatexMaskLayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets Changed's original worn-mask layer render Synergy's repaired mask. */
@Mixin(DarkLatexMaskLayer.class)
public abstract class RepairedDarkLatexMaskLayerMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z",
                    remap = false),
            require = 0)
    private boolean changedSynergy$renderRepairedMaskDev(ItemStack stack, Item originalMask) {
        return changedSynergy$isSupportedMask(stack, originalMask);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z",
                    remap = false),
            require = 0)
    private boolean changedSynergy$renderRepairedMaskProduction(ItemStack stack, Item originalMask) {
        return changedSynergy$isSupportedMask(stack, originalMask);
    }

    private static boolean changedSynergy$isSupportedMask(ItemStack stack, Item originalMask) {
        return stack.is(originalMask)
                || stack.is(ChangedSynergyItems.REPAIRED_DARK_LATEX_MASK.get());
    }
}
