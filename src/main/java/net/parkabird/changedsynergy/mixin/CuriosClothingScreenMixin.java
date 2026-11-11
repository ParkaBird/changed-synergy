package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.parkabird.changedsynergy.client.ClothingInteractionClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets={"top.theillusivec4.curios.client.gui.CuriosScreen","top.theillusivec4.curios.client.gui.CuriosScreenV2"},remap=false)
public abstract class CuriosClothingScreenMixin {
    @Inject(method={"slotClicked","m_6597_"},at=@At("HEAD"),cancellable=true)
    private void changedSynergy$interact(Slot slot,int index,int button,ClickType click,CallbackInfo ci) {
        if(slot instanceof top.theillusivec4.curios.common.inventory.CurioSlot curio && ClothingInteractionClient.interact(slot,button,click,Minecraft.getInstance().player,curio.getIdentifier()))ci.cancel();
    }
}
