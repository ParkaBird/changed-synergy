package net.parkabird.changedsynergy.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.NativeRoleplayStatePacket;

@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID,value=Dist.CLIENT)
public final class NativeRoleplayClient {
    private static final Map<UUID,NativeRoleplayStatePacket> PLAYERS=new HashMap<>();
    private NativeRoleplayClient() {}
    public static void receive(NativeRoleplayStatePacket packet) {
        if(packet.nativeRole())PLAYERS.put(packet.player(),packet);else PLAYERS.remove(packet.player());var mc=Minecraft.getInstance();
        if(mc.level!=null&&mc.level.getPlayerByUUID(packet.player())!=null)mc.level.getPlayerByUUID(packet.player()).refreshDisplayName();
    }
    public static Component name(UUID id) {var state=PLAYERS.get(id);return state!=null&&state.nativeRole()?state.name():null;}
    public static boolean active() {var player=Minecraft.getInstance().player;return player!=null&&name(player.getUUID())!=null;}
    private static NativeRoleplayStatePacket self() {var player=Minecraft.getInstance().player;return player==null?null:PLAYERS.get(player.getUUID());}
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {PLAYERS.clear();}
    @SubscribeEvent public static void chatLabel(ScreenEvent.Render.Pre event) {
        var state=self();if(!(event.getScreen() instanceof ChatScreen)||state==null||!state.nativeRole())return;
        var mc=Minecraft.getInstance();Component label=Component.translatable(state.global()?"menu.changed_synergy.roleplay.chat_global":"menu.changed_synergy.roleplay.chat_local");
        event.getGuiGraphics().fill(2,event.getScreen().height-36,mc.font.width(label)+10,event.getScreen().height-23,0xAA202020);
        event.getGuiGraphics().drawString(mc.font,label,5,event.getScreen().height-33,0xF2B458);
    }
    @SubscribeEvent public static void toggleChat(ScreenEvent.MouseButtonPressed.Post event) {
        var state=self();var mc=Minecraft.getInstance();
        if(event.wasHandled()||!(event.getScreen() instanceof ChatScreen)||state==null||!state.nativeRole()||event.getButton()!=0||mc.player==null)return;
        Component label=Component.translatable(state.global()?"menu.changed_synergy.roleplay.chat_global":"menu.changed_synergy.roleplay.chat_local");
        if(event.getMouseX()>=2&&event.getMouseX()<=mc.font.width(label)+10&&event.getMouseY()>=event.getScreen().height-36&&event.getMouseY()<=event.getScreen().height-23) {
            mc.player.connection.sendCommand("changedsynergy social chat "+(state.global()?"roleplay":"global"));event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        }
    }
}
