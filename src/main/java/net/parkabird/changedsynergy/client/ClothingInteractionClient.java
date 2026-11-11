package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.parkabird.changedsynergy.compat.ClothingStyles;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import net.parkabird.changedsynergy.network.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="changed_synergy",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClothingInteractionClient {
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void initialized(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
        if(!CuriosCompat.isLoaded() || !(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen))return;
        var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("changed","textures/gui/basic_player_info.png");
        // Changed adds its button during init; the post event runs after all init injections.
        for(var widget:java.util.List.copyOf(event.getScreen().children()))if(widget instanceof net.minecraft.client.gui.components.ImageButton button
                && button.resourceLocation.equals(texture))event.removeListener(button);
    }
    public static boolean interact(Slot slot,int button,ClickType click,LivingEntity wearer,String identifier) {
        var mc=Minecraft.getInstance();
        if(slot==null||wearer==null||mc.player==null||identifier==null||CuriosCompat.clothingType(identifier)==null
                ||!mc.player.containerMenu.getCarried().isEmpty()||!ClothingStyles.available(slot.getItem()))return false;
        if(!(click==ClickType.PICKUP&&button==1 || click==ClickType.SWAP&&button==40))return false;
        ChangedSynergyNetwork.CHANNEL.sendToServer(new ClothingStylePacket(mc.player.containerMenu.containerId,wearer.getId(),identifier));
        return true;
    }
}
