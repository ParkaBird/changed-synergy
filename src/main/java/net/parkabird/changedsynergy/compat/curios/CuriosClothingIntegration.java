package net.parkabird.changedsynergy.compat.curios;

import net.ltxprogrammer.changed.data.AccessorySlots;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.resources.ResourceLocation;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioEquipEvent;

/** Genuine Curios slots retain Changed's anatomy and mutual-exclusion rules. */
public final class CuriosClothingIntegration {
    public static void register() {
        CuriosApi.registerCurioPredicate(ResourceLocation.fromNamespaceAndPath("changed_synergy","clothing"),result->{
            var type=NativeClothingInventory.type(result.slotContext().identifier());
            return type!=null && (result.slotContext().entity()==null?result.stack().is(type.getItemTag()):type.canHoldItem(result.stack(),result.slotContext().entity()));
        });
        MinecraftForge.EVENT_BUS.register(CuriosClothingIntegration.class);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void equip(CurioEquipEvent event) {
        var type=NativeClothingInventory.type(event.getSlotContext().identifier());if(type==null)return;
        if(!AccessorySlots.getForEntity(event.getSlotContext().entity())
                .map(slots->slots.hasSlot(type)&&slots.isItemAllowedWithOthers(type,event.getStack())).orElse(false))event.setResult(Event.Result.DENY);
    }
}
