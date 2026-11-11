package net.parkabird.changedsynergy.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.item.AccessoryItem;
import net.minecraft.world.item.ItemStack;

public final class ClothingStyles {
    private static final Map<Class<?>,Boolean> STYLES=new ConcurrentHashMap<>();
    public static boolean available(ItemStack stack) {
        if(!(stack.getItem() instanceof AccessoryItem))return false;
        return STYLES.computeIfAbsent(stack.getItem().getClass(),type->{
            try{return type.getMethod("accessoryInteract",AccessorySlotContext.class).getDeclaringClass()!=AccessoryItem.class;}
            catch(NoSuchMethodException e){return false;}
        });
    }
}
