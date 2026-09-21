package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.world.inventory.InfuserMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ai.BondedRevivalService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses Changed's real Infuser recipes, restricting identity samples to their own form. */
@Mixin(value = InfuserMenu.class, remap = false)
public abstract class BondedInfuserRecipeMixin {
    @Shadow @Final private SimpleContainer copyContainer;
    @Shadow @Final public net.minecraft.world.entity.player.Player entity;
    @Shadow public abstract Slot getResultSlot();

    @Inject(method = {"slotsChanged", "m_6199_"}, at = @At("TAIL"))
    private void changedSynergy$bindIdentitySample(Container changed, CallbackInfo callback) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        ItemStack sample = ItemStack.EMPTY;
        for (int slot = 0; slot < copyContainer.getContainerSize(); slot++) {
            ItemStack candidate = copyContainer.getItem(slot);
            if (!BondedRevivalService.isRevivalSample(candidate)) {
                continue;
            }
            if (!sample.isEmpty()) {
                getResultSlot().set(ItemStack.EMPTY);
                return;
            }
            sample = candidate;
        }
        if (sample.isEmpty()) {
            return;
        }
        ItemStack result = getResultSlot().getItem();
        if (!BondedRevivalService.bindInfuserOutput(sample, result, player)) {
            getResultSlot().set(ItemStack.EMPTY);
        } else {
            getResultSlot().setChanged();
        }
    }
}
