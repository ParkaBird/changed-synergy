package net.parkabird.changedsynergy.regression;

import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.item.ClothingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.compat.curios.*;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventoryMenu;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.common.inventory.CurioSlot;

final class CuriosClothingClientChecks {
    private static int stage,wait,checks;
    private static CompletableFuture<Void> task;
    static boolean tick(Minecraft mc) {
        if(stage==0) {
            var inventory=new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player);
            inventory.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            check(inventory.children().stream().noneMatch(w->w instanceof net.minecraft.client.gui.components.ImageButton button
                    && ((net.parkabird.changedsynergy.mixin.ClothingButtonTextureAccess)button).changedSynergy$texture().toString().equals("changed:textures/gui/basic_player_info.png")),"original apparel button is absent from normal inventory");
            task=CompletableFuture.runAsync(()->{
                if(Boolean.getBoolean("changed_synergy.legacyClothingTest"))top.theillusivec4.curios.common.CuriosConfig.SERVER.enableLegacyMenu.set(true);
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var nativeSlots=AccessorySlots.getForEntity(p).orElseThrow();
                nativeSlots.setItem(ChangedAccessorySlots.BODY.get(),ItemStack.EMPTY);nativeSlots.setItem(ChangedAccessorySlots.FULL_BODY.get(),ItemStack.EMPTY);
                nativeSlots.setItem(ChangedAccessorySlots.LEGS.get(),new ItemStack(ChangedItems.BLACK_PANTS.get()));
                p.getInventory().clearContent();p.getInventory().setItem(9,new ItemStack(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()));
                CuriosCompat.openClothing(p,ItemStack.EMPTY);
            },mc.getSingleplayerServer());stage=1;return false;
        }
        if(!task.isDone())return false;task.join();
        if(!(mc.screen instanceof AbstractContainerScreen<?> screen))return false;
        if(++wait<20)return false;
        var menu=mc.player.containerMenu;var nativeSlots=AccessorySlots.getForEntity(mc.player).orElseThrow();
        switch(stage) {
            case 1 -> {
                check(menu.slots.subList(46,menu.slots.size()).stream().allMatch(s->s instanceof CurioSlot),"player has only genuine Curios equipment slots");
                check(menu.slots.stream().noneMatch(s->s.container instanceof AccessorySlots),"original apparel slots were removed");
                click(mc,ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get());stage=2;
            }
            case 2 -> {
                check(!AccessorySlots.isWearing(mc.player,s->s.is(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get())),"trousers reject outfit through real Curios shift-click");
                var leg=menu.slots.stream().filter(s->s.getItem().is(ChangedItems.BLACK_PANTS.get())).findFirst().orElseThrow();
                mc.gameMode.handleInventoryMouseClick(menu.containerId,leg.index,0,ClickType.QUICK_MOVE,mc.player);stage=3;
            }
            case 3 -> {click(mc,ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get());stage=4;}
            case 4 -> {
                check(AccessorySlots.isWearing(mc.player,s->s.is(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get())),"outfit equipped through Curios");
                check(CuriosApi.getCuriosInventory(mc.player).orElseThrow(()->new AssertionError("client Curios")).findFirstCurio(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()).isPresent(),"real Curios API sees player clothing");
                click(mc,ChangedItems.BLACK_PANTS.get());stage=5;
            }
            case 5 -> {
                check(nativeSlots.getItem(ChangedAccessorySlots.LEGS.get()).orElseThrow().isEmpty(),"outfit rejects trousers in opposite order");
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"curios-real-clothing.png",mc.getMainRenderTarget(),message->{});
                task=CompletableFuture.runAsync(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var slots=AccessorySlots.getForEntity(p).orElseThrow();
                    slots.setItem(ChangedAccessorySlots.BODY.get(),ItemStack.EMPTY);slots.setItem(ChangedAccessorySlots.FULL_BODY.get(),new ItemStack(ChangedItems.LAB_COAT.get()));
                },mc.getSingleplayerServer());stage=6;
            }
            case 6 -> {
                var coat=menu.slots.stream().filter(s->s.getItem().is(ChangedItems.LAB_COAT.get())).findFirst().orElseThrow();
                check(!((ClothingItem)coat.getItem().getItem()).getClothingState(coat.getItem()).getValue(ClothingItem.CLOSED),"coat starts open");
                screen.mouseClicked(screen.getGuiLeft()+coat.x+8,screen.getGuiTop()+coat.y+8,1);stage=7;
            }
            case 7 -> {
                var coat=nativeSlots.getItem(ChangedAccessorySlots.FULL_BODY.get()).orElseThrow();
                check(((ClothingItem)coat.getItem()).getClothingState(coat).getValue(ClothingItem.CLOSED),"actual right mouse click switches coat style and syncs");
                check(menu.getCarried().isEmpty(),"style interaction leaves clothing equipped");
                task=CompletableFuture.runAsync(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var pet=net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get().create(p.serverLevel());
                    pet.moveTo(p.getX()+1,p.getY(),p.getZ());pet.setNoAi(true);p.serverLevel().addFreshEntity(pet);
                    net.parkabird.changedsynergy.ai.LatexSocialMemory.addBond(pet,p);
                    AccessorySlots.getForEntity(pet).orElseThrow().quickMoveStack(new ItemStack(ChangedItems.LAB_COAT.get()));
                    check(net.parkabird.changedsynergy.world.inventory.BondedInventoryService.open(p,pet),"real creature inventory opens");
                },mc.getSingleplayerServer());stage=8;
            }
            case 8 -> {
                if(!(menu instanceof BondedCreatureInventoryMenu pet))return false;
                check(pet.clothingSlotCount()==0,"creature has no original clothing slots");
                check(pet.hasCurios(),"creature's actual Curios clothing is exposed");
                pet.clickMenuButton(mc.player,pet.CURIOS_BUTTON_ID);mc.gameMode.handleInventoryButtonClick(menu.containerId,pet.CURIOS_BUTTON_ID);stage=9;
            }
            case 9 -> {
                var pet=(BondedCreatureInventoryMenu)menu;
                check(menu.slots.subList(41,65).stream().allMatch(s->s.isActive()),"storage remains usable");
                var coat=menu.slots.stream().filter(s->s.getItem().is(ChangedItems.LAB_COAT.get())).findFirst().orElseThrow();
                screen.mouseClicked(screen.getGuiLeft()+coat.x+8,screen.getGuiTop()+coat.y+8,1);stage=10;
            }
            case 10 -> {
                var pet=(BondedCreatureInventoryMenu)menu;
                var coat=menu.slots.stream().filter(s->s.getItem().is(ChangedItems.LAB_COAT.get())).findFirst().orElseThrow().getItem();
                check(((ClothingItem)coat.getItem()).getClothingState(coat).getValue(ClothingItem.CLOSED),"NPC right-click style syncs to the correct wearer");
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"creature-real-curios-clothing.png",mc.getMainRenderTarget(),message->{});
                System.out.println("CURIOS_NATIVE_CLOTHING_CLIENT_PASS: "+checks+" actual Curios slots, removal of original slots, API storage, two-way conflicts and player/NPC right-click styles");return true;
            }
        }
        wait=0;return false;
    }
    private static void click(Minecraft mc,net.minecraft.world.item.Item item) {
        var slot=mc.player.containerMenu.slots.stream().filter(s->s.container==mc.player.getInventory()&&s.getItem().is(item)).findFirst().orElseThrow();
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot.index,0,ClickType.QUICK_MOVE,mc.player);
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
