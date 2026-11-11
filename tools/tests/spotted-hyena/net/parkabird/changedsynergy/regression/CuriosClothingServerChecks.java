package net.parkabird.changedsynergy.regression;

import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.compat.curios.*;
import net.parkabird.changedsynergy.init.*;
import top.theillusivec4.curios.api.CuriosApi;

final class CuriosClothingServerChecks {
    private static int checks;
    static void run(MinecraftServer server) {
        var level=server.overworld();var mob=ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level);
        mob.moveTo(500,180,500);mob.setNoAi(true);level.getChunk(mob.blockPosition());level.addFreshEntity(mob);
        AccessoryEntities.INSTANCE.forceReloadAccessories(mob);
        var nativeSlots=AccessorySlots.getForEntity(mob).orElseThrow();
        if(!CuriosCompat.isLoaded()) {
            var legacy=new CompoundTag();legacy.put("changed:body",new ItemStack(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()).save(new CompoundTag()));
            legacy.putBoolean("ChangedSynergyCuriosBacked",true);nativeSlots.load(legacy);
            check(AccessorySlots.isWearing(mob,s->s.is(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get())),"native compatibility snapshot restores without Curios");
            System.out.println("CURIOS_CLOTHING_CORE_PASS: "+checks);mob.discard();return;
        }
        var curios=CuriosApi.getCuriosInventory(mob).orElseThrow(()->new AssertionError("Curios capability"));
        var body=curios.getStacksHandler("changed_clothing_body").orElseThrow().getStacks();
        var legs=curios.getStacksHandler("changed_clothing_legs").orElseThrow().getStacks();
        check(body.getSlotLimit(0)==1,"real Curios clothing stack limit is one");
        var outfit=new ItemStack(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get());
        nativeSlots.setItem(ChangedAccessorySlots.BODY.get(),outfit);
        check(body.getStackInSlot(0)==outfit,"native writes target the real Curios stack, with no second inventory");
        check(curios.findFirstCurio(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()).isPresent(),"Curios API discovers worn clothing");
        check(!legs.isItemValid(0,new ItemStack(ChangedItems.BLACK_PANTS.get())),"Curios rejects trousers when outfit blocks legs");
        nativeSlots.setItem(ChangedAccessorySlots.BODY.get(),ItemStack.EMPTY);
        legs.setStackInSlot(0,new ItemStack(ChangedItems.BLACK_PANTS.get()));
        check(!body.isItemValid(0,outfit),"trousers reject the outfit in the opposite order");legs.setStackInSlot(0,ItemStack.EMPTY);
        var legacy=new CompoundTag();legacy.put("changed:body",outfit.save(new CompoundTag()));nativeSlots.load(legacy);nativeSlots.tick();
        check(body.getStackInSlot(0).is(outfit.getItem())&&body.getStackInSlot(0).getCount()==1,"old native inventory migrates into Curios");
        var saved=new CompoundTag();mob.save(saved);
        check(saved.getCompound("ChangedAccessorySlots").getBoolean("ChangedSynergyCuriosBacked"),"compatibility snapshot is marked non-authoritative");
        var clone=(net.ltxprogrammer.changed.entity.ChangedEntity)EntityType.loadEntityRecursive(saved,level,e->e);clone.setUUID(java.util.UUID.randomUUID());level.addFreshEntity(clone);
        AccessorySlots.getForEntity(clone).orElseThrow().tick();
        var reloaded=CuriosApi.getCuriosInventory(clone).orElseThrow(()->new AssertionError("reloaded Curios capability"));
        check(reloaded.findCurios(outfit.getItem()).size()==1,"entity reload has exactly one real worn outfit");
        nativeSlots.load(nativeSlots.save());nativeSlots.tick();check(body.getStackInSlot(0).getCount()==1,"compatibility snapshot does not reimport a second item");
        var renders=curios.getStacksHandler("changed_clothing_body").orElseThrow().getRenders();renders.set(0,false);
        check(!CuriosCompat.clothingVisible(mob,ChangedAccessorySlots.BODY.get()),"native rendering obeys Curios visibility toggle");renders.set(0,true);
        var wolf=ChangedEntities.GAS_WOLF_MALE.get().create(level);wolf.moveTo(510,180,500);wolf.setNoAi(true);level.addFreshEntity(wolf);
        AccessoryEntities.INSTANCE.forceReloadAccessories(wolf);var clothes=AccessorySlots.getForEntity(wolf).orElseThrow();
        check(clothes.quickMoveStack(new ItemStack(ChangedItems.LAB_COAT.get()))&&clothes.quickMoveStack(new ItemStack(ChangedItems.RED_NECK_TIE.get())),"gas wolf can wear lab coat and red tie together");
        check(CuriosApi.getCuriosInventory(wolf).orElseThrow(()->new AssertionError("wolf Curios")).findFirstCurio(ChangedItems.RED_NECK_TIE.get()).isPresent(),"NPC necktie is a real Curios item");
        var drops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> listener=event->{
            if(event.getLevel()==level&&event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                    && item.getItem().is(outfit.getItem()))drops.add(item);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            mob.die(mob.damageSources().genericKill());
            check(drops.stream().mapToInt(item->item.getItem().getCount()).sum()==1,"native and Curios death handling drops exactly one outfit");
            check(curios.findCurios(outfit.getItem()).isEmpty(),"dead NPC does not keep a duplicate Curios outfit");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
        clone.discard();wolf.discard();mob.discard();
        System.out.println("CURIOS_CLOTHING_SERVER_PASS: "+checks+" real storage, native API, mutual exclusion, migration, reload, visibility and lab coat/tie checks");
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
