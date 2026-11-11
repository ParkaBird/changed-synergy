package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import net.parkabird.changedsynergy.block.PerchBlock;
import net.parkabird.changedsynergy.client.LatexEagleOwlRenderer;
import net.parkabird.changedsynergy.init.*;
import org.joml.*;

final class PerchSleepClientChecks {
    private static int checks;
    static void run(Minecraft mc)throws Exception {
        var owl=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(mc.level);owl.setNoAi(true);owl.setCustomName(Component.literal("Perch sleeper"));owl.setCustomNameVisible(true);
        var renderer=(LatexEagleOwlRenderer)mc.getEntityRenderDispatcher().getRenderer(owl);
        var origin=mc.player.blockPosition().offset(3,0,0);var blocks=List.of(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get());
        for(var block:blocks)for(var facing:Direction.Plane.HORIZONTAL) {
            var state=block.defaultBlockState().setValue(PerchBlock.FACING,facing);mc.level.setBlock(origin,state,3);block.setPlacedBy(mc.level,origin,state,null,ItemStack.EMPTY);
            owl.startSleeping(origin);owl.tickCount=100;var pose=new PoseStack();pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
            var model=renderer.getModel();model.prepareMobModel(owl,0,0,1);model.setupAnim(owl,0,0,100,0,0);
            var head=new Vertices();model.getHead().render(pose,head,15728880,0);
            var feet=new Vertices();
            for(var side:HumanoidArm.values()) {
                pose.pushPose();var leg=model.getLeg(side);leg.translateAndRotate(pose);String name=side==HumanoidArm.LEFT?"Left":"Right";
                var lower=leg.getChild(name+"LowerLeg");lower.translateAndRotate(pose);var foot=lower.getChild(name+"Foot");foot.translateAndRotate(pose);foot.getChild(name+"Pad").render(pose,feet,15728880,0);pose.popPose();
            }
            check(java.lang.Math.abs(feet.minY)<.01,"authored claws meet beam surface: "+block+facing+" offset="+feet.minY);
            var labels=new Vertices();var body=new Vertices();
            var method=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.client.renderer.entity.EntityRenderer.class,"m_7649_",net.minecraft.world.entity.Entity.class,Component.class,PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class);
            method.invoke(renderer,owl,owl.getDisplayName(),new PoseStack(),(net.minecraft.client.renderer.MultiBufferSource)(type->labels),15728880);
            check(labels.count>0,"native name label rendered");
            check(labels.minY>head.maxY+.03,"label clears sleeping head/ear tufts: label="+labels.minY+" head="+head.maxY);
            owl.stopSleeping();mc.level.removeBlock(origin,false);mc.level.removeBlock(block.other(origin,state),false);
        }
        owl.setCustomName(null);owl.setCustomNameVisible(false);
        var second=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(mc.level);second.setNoAi(true);second.tickCount=100;
        for(var bird:List.of(owl,second)) {
            net.ltxprogrammer.changed.entity.AccessoryEntities.INSTANCE.forceReloadAccessories(bird);
            check(net.ltxprogrammer.changed.data.AccessorySlots.getForEntity(bird).orElseThrow().quickMoveStack(new ItemStack(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get())),"pair wears Aviator clothing");
        }
        var rail=blocks.get(1);
        for(var facing:Direction.Plane.HORIZONTAL) {
            var state=rail.defaultBlockState().setValue(PerchBlock.FACING,facing);mc.level.setBlock(origin,state,3);rail.setPlacedBy(mc.level,origin,state,null,ItemStack.EMPTY);
            owl.startSleeping(origin);second.startSleeping(rail.other(origin,state));
            var firstVertices=new Vertices();var secondVertices=new Vertices();
            renderer.render(owl,0,1,new PoseStack(),type->firstVertices,15728880);
            renderer.render(second,0,1,new PoseStack(),type->secondVertices,15728880);
            boolean x=facing.getAxis()==Direction.Axis.Z;
            double firstExtent=x?firstVertices.maxX-firstVertices.minX:firstVertices.maxZ-firstVertices.minZ;
            double secondExtent=x?secondVertices.maxX-secondVertices.minX:secondVertices.maxZ-secondVertices.minZ;
            check(java.lang.Math.max(firstExtent,secondExtent)<1.25-.015625,"two clothed sleeping owl models fit with a visible gap: "+facing+" widths="+firstExtent+","+secondExtent);
            owl.stopSleeping();second.stopSleeping();mc.level.removeBlock(origin,false);mc.level.removeBlock(rail.other(origin,state),false);
        }
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try {mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);target.setClearColor(.12F,.2F,.16F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int i=0;i<3;i++) {
                var block=blocks.get(i==0?0:1);var state=block.defaultBlockState();mc.level.setBlock(origin,state,3);block.setPlacedBy(mc.level,origin,state,null,ItemStack.EMPTY);owl.startSleeping(origin);
                g.pose().pushPose();g.pose().translate((i*2+1)*w/6-15,h-30,100);g.pose().scale(50,-50,50);g.pose().mulPose(Axis.XP.rotationDegrees(8));g.pose().mulPose(Axis.YP.rotationDegrees(25));
                mc.getBlockRenderer().renderSingleBlock(state,g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                g.pose().pushPose();g.pose().translate(i==0?0:1,i==0?1:0,0);mc.getBlockRenderer().renderSingleBlock(state.setValue(PerchBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.pose().popPose();
                var point=PerchSleepService.position(mc.level,origin);mc.getEntityRenderDispatcher().render(owl,point.x-origin.getX(),point.y-origin.getY(),point.z-origin.getZ(),0,1,g.pose(),mc.renderBuffers().bufferSource(),15728880);
                if(i==2){var mate=block.other(origin,state);second.startSleeping(mate);var otherPoint=PerchSleepService.position(mc.level,mate);mc.getEntityRenderDispatcher().render(second,otherPoint.x-origin.getX(),otherPoint.y-origin.getY(),otherPoint.z-origin.getZ(),0,1,g.pose(),mc.renderBuffers().bufferSource(),15728880);second.stopSleeping();}
                g.pose().popPose();owl.stopSleeping();mc.level.removeBlock(origin,false);mc.level.removeBlock(block.other(origin,state),false);
            }
            g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("owl-perch-support-and-label.png"));}
        } finally {mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);owl.discard();second.discard();}
        System.out.println("PERCH_SLEEP_CLIENT_PASS: "+checks+" claw contact and native name label checks over both perches and all directions");
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static final class Vertices implements VertexConsumer {
        int count;double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        public VertexConsumer vertex(double x,double y,double z){minY=java.lang.Math.min(minY,y);maxY=java.lang.Math.max(maxY,y);minX=java.lang.Math.min(minX,x);maxX=java.lang.Math.max(maxX,x);minZ=java.lang.Math.min(minZ,z);maxZ=java.lang.Math.max(maxZ,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}public VertexConsumer uv(float u,float v){return this;}public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){count++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
}
