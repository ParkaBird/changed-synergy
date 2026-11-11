package net.parkabird.changedsynergy.mixin;

import java.util.function.Function;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.*;
import top.theillusivec4.curios.api.SlotContext;
import net.parkabird.changedsynergy.compat.curios.NativeClothingInventory;

@Pseudo
@Mixin(targets="top.theillusivec4.curios.common.inventory.DynamicStackHandler",remap=false)
public abstract class CuriosClothingStackLimitMixin extends ItemStackHandler {
    @Shadow(remap=false) protected Function<Integer,SlotContext> ctxBuilder;
    @Override public int getSlotLimit(int slot){return NativeClothingInventory.type(ctxBuilder.apply(slot).identifier())!=null?1:super.getSlotLimit(slot);}
}
