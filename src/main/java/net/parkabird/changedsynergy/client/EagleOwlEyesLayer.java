package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.parkabird.changedsynergy.client.model.LatexEagleOwlModel;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;

/** Two eye shapes with fixed black pupils and independently colored, shaded eye whites. */
public final class EagleOwlEyesLayer extends RenderLayer<LatexEagleOwl,LatexEagleOwlModel> {
    public EagleOwlEyesLayer(RenderLayerParent<LatexEagleOwl,LatexEagleOwlModel> parent){super(parent);}
    public static EyeStyle style(EyeStyle requested){return requested==EyeStyle.TALL?EyeStyle.TALL:EyeStyle.V1;}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,LatexEagleOwl owl,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(owl.isInvisible()||owl.isSleeping()||net.ltxprogrammer.changed.extension.ChangedCompatibility.isFirstPersonRendering())return;
        var info=owl.getBasicPlayerInfo();var settings=net.ltxprogrammer.changed.Changed.config.client;
        var style=style(settings.basicPlayerInfo.isOverrideOthersToMatchStyle()?settings.basicPlayerInfo.getEyeStyle():info.getEyeStyle());var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(EyeStyle.V1.getSclera()));
        pose.pushPose();getParentModel().scaleForHead(pose);getParentModel().getHead().translateAndRotate(pose);
        float separation=1+net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.getZFightingOffset(owl);
        pose.scale(separation,separation,separation);
        for(boolean left:new boolean[]{false,true})for(int row=0;row<2;row++)for(int col=0;col<2;col++){
            boolean pupil=col==(left?0:1)&&(row==1||style==EyeStyle.TALL);
            Color3 color=pupil?Color3.fromInt(0x050505):left?info.getLeftIrisColor():info.getRightIrisColor();
            if(settings.useBlinkingEyes.get()&&owl.isBlinking())color=Color3.fromInt(0x6D6D6D);
            float shade=!pupil&&row==0?.79F:1;
            float x=((left?1:-3)+col)/16F,y=(-5+row)/16F;
            vertex(buffer,pose.last(),x,y+1/16F,light,color,shade);
            vertex(buffer,pose.last(),x+1/16F,y+1/16F,light,color,shade);
            vertex(buffer,pose.last(),x+1/16F,y,light,color,shade);
            vertex(buffer,pose.last(),x,y,light,color,shade);
        }
        pose.popPose();
    }
    private static void vertex(VertexConsumer buffer,PoseStack.Pose pose,float x,float y,int light,Color3 color,float shade){
        // A single face samples a white texel; zero-depth boxes draw two conflicting UV faces.
        buffer.vertex(pose.pose(),x,y,-4.04F/16F).color(color.red()*shade,color.green()*shade,color.blue()*shade,1)
                .uv(9.5F/32F,11.5F/32F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),0,0,-1).endVertex();
    }
}
