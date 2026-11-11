package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class RadialWheelScrollClientEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event){
        if(event.getScrollDelta()!=0&&event.getScreen() instanceof AbstractRadialScreen<?> screen&&screen.getCount()>8&&!Screen.hasShiftDown()){
            event.setCanceled(true);
            if(screen instanceof RadialWheelAnimationAccess access)access.changedSynergy$scrollOptions(event.getScrollDelta());
        }
    }
}
