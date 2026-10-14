package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.network.NativeRoleplayStatePacket;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

public final class NativeRoleplayClientRegression {
    public static Map<String,Screen> prepare(Minecraft mc) {
        var result=new LinkedHashMap<String,Screen>();
        mc.getSingleplayerServer().submit(()->{
            var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(mc.player.getUUID());
            server.getPlayerList().op(player.getGameProfile());
            var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="native";
            identity.form=identity.current=net.ltxprogrammer.changed.init.ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString();identity.faction="dark";
            identity.roleName=Component.Serializer.toJson(Component.literal("River"));
            net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(),identity);
            server.overworld().getGameRules().getRule(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.NATIVE_ORIGINS).set(true,server);
            net.parkabird.changedsynergy.origin.NativeOriginService.restore(player);
            net.parkabird.changedsynergy.ai.NativeRoleplayService.sync(player);
        }).join();
        NativeRoleplayClient.receive(new NativeRoleplayStatePacket(mc.player.getUUID(),true,Component.literal("River"),false));
        net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(mc.player,
                net.ltxprogrammer.changed.init.ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,true,
                variant->variant.willSurviveTransfur=true).setTemporaryForSuit(false);
        var wheel=new PlayerRelationshipScreen(new PlayerRelationshipMenu(1,mc.player.getInventory(),List.of()),mc.player.getInventory(),PlayerRelationshipMenu.TITLE);
        mc.setScreen(wheel);check(wheel.getCount()==9,"Native wheel includes name and expressions beyond its first eight slots");
        try {
            var opened=net.ltxprogrammer.changed.client.gui.AbstractRadialScreen.class.getDeclaredField("changedSynergy$openedAtNanos");
            opened.setAccessible(true);opened.setLong(wheel,System.nanoTime()-2_000_000_000L);
            for(int i=0;i<12;i++)wheel.containerTick();
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
        check(java.util.stream.IntStream.range(0,wheel.getCount()).noneMatch(i->wheel.tooltipsFor(i).get(0).getString().equals(Component.translatable("menu.changed_synergy.relationship.action.mediate").getString())),"Creature wheel omits vouching without shifting damage-filter or native actions");
        check(wheel.tooltipsFor(6).get(0).getString().equals(Component.translatable("menu.changed_synergy.relationship.action.damage_filter").getString()),"Shifted damage-filter slot retains correct action and label");
        wheel.keyPressed(262,0,0);for(int i=0;i<8;i++)wheel.containerTick();
        check(net.parkabird.changedsynergy.client.RadialWheelAnimations.viewOffset(wheel)==1,"Right arrow reveals the hidden native option");
        wheel.keyPressed(263,0,0);for(int i=0;i<8;i++)wheel.containerTick();
        check(net.parkabird.changedsynergy.client.RadialWheelAnimations.viewOffset(wheel)==0,"Left arrow returns to the first options");
        wheel.handleClicked(7,new net.ltxprogrammer.changed.util.SingleRunnable(()->{}));check(mc.screen instanceof NativeRoleplayScreen,"Name option opens identity editor");
        var name=mc.screen;var field=(EditBox)name.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
        var done=(Button)name.children().stream().filter(widget->widget instanceof Button button&&button.getMessage().getString().equals(Component.translatable("gui.done").getString())).findFirst().orElseThrow();
        field.setValue("x".repeat(25));check(!done.active,"Invalid long name cannot be submitted from the editor");
        field.setValue("River");check(done.active,"Valid role name can be submitted");name.onClose();check(mc.screen==wheel,"Closing name editor returns to relationship wheel");
        wheel.handleClicked(8,new net.ltxprogrammer.changed.util.SingleRunnable(()->{}));check(mc.screen instanceof NativeEmoteScreen,"Expression option opens radial picker");
        var expressions=(NativeEmoteScreen)mc.screen;check(expressions.getCount()==6,"Six expression choices use radial geometry");
        try {
            var opened=net.ltxprogrammer.changed.client.gui.AbstractRadialScreen.class.getDeclaredField("changedSynergy$openedAtNanos");
            opened.setAccessible(true);opened.setLong(expressions,System.nanoTime()-2_000_000_000L);
            for(int i=0;i<12;i++)expressions.containerTick();
            for(int slot=0;slot<6;slot++) {
                double angle=(slot+.5+(slot%2==0?.04:-.04))/8*Math.PI*2;
                int x=expressions.width/2+(int)(Math.sin(angle)*90),y=expressions.height/2-(int)(Math.cos(angle)*90);
                check(expressions.getSectionAt(x,y).orElse(-1)==slot,"Pointer selects the expression beneath its icon");
                check(!expressions.tooltipsFor(slot).get(0).getString().startsWith("menu."),"Expression has a localized hover label");
            }
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
        expressions.keyPressed(256,0,0);check(mc.screen==wheel,"Escape returns to the relationship wheel instead of closing its container");
        result.put("name",name);result.put("expressions",expressions);result.put("chat",new ChatScreen("I am waiting by the campfire."));
        result.put("wheel",wheel);result.put("chat-command",new ChatScreen("/changedsynergy "));
        System.out.println("NATIVE_ROLEPLAY_CLIENT_REGRESSION_PASS checks=24");return result;
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
