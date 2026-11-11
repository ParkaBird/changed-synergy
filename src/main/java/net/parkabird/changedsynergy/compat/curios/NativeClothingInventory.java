package net.parkabird.changedsynergy.compat.curios;

import java.util.*;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

/** Changed's API reads the real Curios stacks. Only pre-Curios save migration waits locally. */
public final class NativeClothingInventory extends AbstractMap<AccessorySlotType,ItemStack> {
    public static final String MIRROR="ChangedSynergyCuriosBacked";
    private static final String PENDING="ChangedSynergyPendingClothing";
    private final LivingEntity owner;
    private final Set<AccessorySlotType> keys=new LinkedHashSet<>();
    private final Map<AccessorySlotType,ItemStack> pending=new LinkedHashMap<>();
    private boolean reconciling;

    public NativeClothingInventory(LivingEntity owner,Map<AccessorySlotType,ItemStack> previous) {
        this.owner=owner;keys.addAll(previous.keySet());pending.putAll(previous);
    }
    public static String identifier(AccessorySlotType type) {
        var id=ChangedRegistry.ACCESSORY_SLOTS.getKey(type);
        return "changed_clothing_"+id.getPath();
    }
    public static AccessorySlotType type(String identifier) {
        if(!identifier.startsWith("changed_clothing_"))return null;
        return ChangedRegistry.ACCESSORY_SLOTS.get().getValue(ResourceLocation.fromNamespaceAndPath("changed",identifier.substring(17)));
    }
    private ICuriosItemHandler inventory() {return CuriosApi.getCuriosInventory(owner).orElse(null);}
    private ICurioStacksHandler handler(AccessorySlotType type) {
        var inventory=inventory();if(inventory==null)return null;
        return inventory.getStacksHandler(identifier(type)).orElse(null);
    }
    public void reconcile() {
        if(reconciling)return;
        var inventory=inventory();if(inventory==null)return;
        reconciling=true;
        try {
            var all=new LinkedHashMap<>(inventory.getCurios());boolean added=false;
            for(var type:ChangedRegistry.ACCESSORY_SLOTS.get().getValues()) {
                var id=ChangedRegistry.ACCESSORY_SLOTS.getKey(type);if(!id.getNamespace().equals("changed"))continue;
                String key=identifier(type);var handler=all.get(key);
                if(handler==null) {
                    handler=new CurioStacksHandler(inventory,key,keys.contains(type)?1:0,true,false,true,ICurio.DropRule.ALWAYS_KEEP);
                    all.put(key,handler);added=true;
                }
            }
            if(added)inventory.setCurios(all);
            for(var type:ChangedRegistry.ACCESSORY_SLOTS.get().getValues()) {
                var handler=all.get(identifier(type));if(handler==null)continue;
                int desired=keys.contains(type)?1:0;
                if(handler.getSlots()<desired)handler.grow(desired-handler.getSlots());
                if(handler.getSlots()>desired) {
                    var item=handler.getStacks().getStackInSlot(0);
                    if(!item.isEmpty()) {pending.put(type,item.copy());handler.getStacks().setStackInSlot(0,ItemStack.EMPTY);}
                    handler.shrink(handler.getSlots()-desired);
                }
                if(desired==0)continue;
                var old=pending.remove(type);
                if(old==null||old.isEmpty())continue;
                var stacks=handler.getStacks();
                if(stacks.getStackInSlot(0).isEmpty())stacks.setStackInSlot(0,old);
                else if(!owner.level().isClientSide)AccessorySlots.defaultInvalidHandler(owner).accept(old);
            }
        } finally {reconciling=false;}
    }
    @Override public boolean containsKey(Object key){return keys.contains(key);}
    @Override public ItemStack get(Object key) {
        if(!(key instanceof AccessorySlotType type)||!keys.contains(type))return null;
        var handler=handler(type);
        if(handler!=null&&handler.getSlots()>0)return handler.getStacks().getStackInSlot(0);
        return pending.getOrDefault(type,ItemStack.EMPTY);
    }
    @Override public ItemStack put(AccessorySlotType type,ItemStack stack) {
        var previous=get(type);keys.add(type);var handler=handler(type);
        if(handler!=null&&handler.getSlots()>0){pending.remove(type);handler.getStacks().setStackInSlot(0,stack);}
        else pending.put(type,stack);
        return previous;
    }
    @Override public ItemStack remove(Object key) {
        var previous=get(key);if(!(key instanceof AccessorySlotType type))return previous;
        keys.remove(type);pending.remove(type);var handler=handler(type);
        if(handler!=null&&handler.getSlots()>0)handler.getStacks().setStackInSlot(0,ItemStack.EMPTY);
        return previous;
    }
    @Override public void clear(){for(var key:List.copyOf(keys))remove(key);}
    @Override public Set<Entry<AccessorySlotType,ItemStack>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size(){return keys.size();}
            @Override public Iterator<Entry<AccessorySlotType,ItemStack>> iterator(){
                var iterator=keys.iterator();
                return new Iterator<>() {
                    private AccessorySlotType current;
                    public boolean hasNext(){return iterator.hasNext();}
                    public Entry<AccessorySlotType,ItemStack> next(){current=iterator.next();return new SimpleEntry<>(current,get(current));}
                    public void remove(){var type=current;var handler=handler(type);if(handler!=null&&handler.getSlots()>0)handler.getStacks().setStackInSlot(0,ItemStack.EMPTY);pending.remove(type);iterator.remove();}
                };
            }
        };
    }
    public void load(CompoundTag tag) {
        var legacy=tag.getBoolean(MIRROR)?tag.getCompound(PENDING):tag;
        for(String key:legacy.getAllKeys()) {
            var id=ResourceLocation.tryParse(key);if(id==null)continue;
            var type=ChangedRegistry.ACCESSORY_SLOTS.get().getValue(id);
            if(type!=null){keys.add(type);pending.put(type,ItemStack.of(legacy.getCompound(key)));}
        }
    }
    public void markSave(CompoundTag tag) {
        tag.putBoolean(MIRROR,true);
        var waiting=new CompoundTag();pending.forEach((type,item)->{if(!item.isEmpty())waiting.put(ChangedRegistry.ACCESSORY_SLOTS.getKey(type).toString(),item.save(new CompoundTag()));});
        if(!waiting.isEmpty())tag.put(PENDING,waiting);
    }
}
