package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ai.EagleOwlTraits;
import net.parkabird.changedsynergy.item.EagleOwlAviatorItem;

/** Desaturates the world before the HUD, independently of native night vision. */
public final class EagleOwlVisionClient {
    private static PostChain chain;private static int width,height;private static boolean failed,reload;
    public static void requestReload(){reload=true;}
    public static boolean active(){var mc=Minecraft.getInstance();return net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT.eagleOwlMonochromeVision.get()&&mc.player!=null&&!fullMoonNight(mc)&&EagleOwlTraits.body(mc.player)!=null&&!EagleOwlAviatorItem.wearingLowered(EagleOwlTraits.clothingWearer(mc.player));}
    public static boolean fullMoonNight(Minecraft mc){return mc.level!=null&&mc.level.dimensionType().hasSkyLight()&&mc.level.isNight()&&mc.level.getMoonPhase()==0;}
    public static void render(float partialTick){
        var mc=Minecraft.getInstance();
        if(reload){close();failed=false;reload=false;}
        if(!active()){close();return;}if(failed)return;
        try{
            if(chain==null){chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),ResourceLocation.parse("changed_synergy:shaders/post/eagle_owl_grayscale.json"));width=height=-1;}
            if(width!=mc.getWindow().getWidth()||height!=mc.getWindow().getHeight()){width=mc.getWindow().getWidth();height=mc.getWindow().getHeight();chain.resize(width,height);}
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();chain.process(partialTick);
        }catch(java.io.IOException|RuntimeException error){close();failed=true;net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.error("Unable to render Eagle Owl grayscale vision",error);}
        finally{mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void close(){if(chain!=null){chain.close();chain=null;}}
}
