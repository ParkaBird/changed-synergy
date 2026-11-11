package net.parkabird.changedsynergy.client;

import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.PerchSleepService;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class PerchSleepCameraClient {
    private static CameraType previous;
    public static void update(){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.player.isSleeping()&&PerchSleepService.perch(mc.player)!=null){
            if(previous==null)previous=mc.options.getCameraType();
            if(mc.options.getCameraType().isFirstPerson())mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }else restore();
    }
    private static void restore(){if(previous!=null){Minecraft.getInstance().options.setCameraType(previous);previous=null;}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.START)update();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){restore();}
}
