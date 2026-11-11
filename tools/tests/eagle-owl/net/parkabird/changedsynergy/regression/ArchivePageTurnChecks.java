package net.parkabird.changedsynergy.regression;

import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.client.NativeOriginScreen;
import net.parkabird.changedsynergy.network.NativeOriginStatePacket;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.CLIENT)
public final class ArchivePageTurnChecks {
    private static final double[] SAMPLES={.62,.7,.85,.95,.98};
    private static NativeOriginScreen screen;
    private static int sample,round,checks;
    private static boolean aligned,finishing,finished,previousReduced;
    static boolean tick(Minecraft mc){
        if(screen==null){
            previousReduced=ChangedSynergyClientConfig.CLIENT.reducedUiMotion.get();ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(false);
            var entries=NativeOriginCatalog.entries(mc.level.registryAccess());var nonce=UUID.randomUUID();
            screen=new NativeOriginScreen(new NativeOriginStatePacket(0,nonce,true,true,Component.empty(),entries));mc.setScreen(screen);
            screen.receive(new NativeOriginStatePacket(2,nonce,true,true,Component.empty(),entries));
            screen.receive(new NativeOriginStatePacket(0,nonce,true,true,Component.empty(),entries));
            screen.keyPressed(258,0,0);check(read(screen,"turn")!=null,"forward archive turn starts");
        }
        if(finished){ChangedSynergyClientConfig.CLIENT.reducedUiMotion.set(previousReduced);mc.setScreen(null);return true;}
        return false;
    }
    @SubscribeEvent public static void pre(ScreenEvent.Render.Pre event){
        if(finished||event.getScreen()!=screen)return;var turn=read(screen,"turn");
        if(turn==null||!(Boolean)read(turn,"captured"))return;
        double duration=((Number)read(turn,"duration")).doubleValue();
        write(turn,"capturedAt",Util.getMillis()-(long)(duration*1000*(finishing?1.01:SAMPLES[sample])));aligned=true;
    }
    @SubscribeEvent public static void post(ScreenEvent.Render.Post event){
        if(finished||event.getScreen()!=screen||!aligned)return;aligned=false;
        try{
            var turn=read(screen,"turn");
            if(finishing){
                check(turn==null,"page handoff completes at the actual endpoint");
                check(((Number)read(read(screen,"paper"),"category")).intValue()==(round==0?1:0),"destination sheet is committed without exposing the old page");
                if(round++==0){sample=0;finishing=false;screen.keyPressed(258,0,1);check(read(screen,"turn")!=null,"reverse archive turn starts");}
                else{finished=true;System.out.println("ARCHIVE_PAGE_TURN_CLIENT_PASS: "+checks+" forward/reverse late-frame continuity and page handoff checks");}
                return;
            }
            check(turn!=null&&(Boolean)read(turn,"captured"),"turning sheet remains captured through the late animation");
            double progress=((Number)read(read(screen,"papers"),"turnProgress")).doubleValue();
            check(progress>0&&progress<1,"curl remains visible before the page handoff: "+SAMPLES[sample]);
            if(sample==SAMPLES.length-1)try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("archive-turn-late-"+round+".png"));}
            if(++sample==SAMPLES.length)finishing=true;
        }catch(Exception error){throw new AssertionError(error);}
    }
    private static Object read(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception error){throw new AssertionError(error);}}
    private static void write(Object object,String name,Object value){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}catch(Exception error){throw new AssertionError(error);}}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
