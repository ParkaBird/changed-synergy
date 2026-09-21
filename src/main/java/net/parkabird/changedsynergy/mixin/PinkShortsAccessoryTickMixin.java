package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.item.AccessoryItem;
import net.ltxprogrammer.changed.item.PinkShorts;
import org.spongepowered.asm.mixin.Mixin;

/** Connects PinkShorts' existing wear logic to Changed's accessory tick. */
@Mixin(value = PinkShorts.class, remap = false)
public abstract class PinkShortsAccessoryTickMixin implements AccessoryItem {
    @Override
    public void accessoryTick(AccessorySlotContext<?> context) {
        ((PinkShorts)(Object)this).wearTick(
                context.stack(), context.wearer());
    }
}
