package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.parkabird.changedsynergy.client.EagleOwlAviatorRenderer;
import net.parkabird.changedsynergy.entity.LatexJerboa;

final class FollowupRenderChecks {
    static void seat(Minecraft mc,LatexJerboa mob)throws Exception{
        if(Math.abs(mob.getMyRidingOffset()-(-.475+2.7/16))>.0001)throw new AssertionError("small-body riding offset");
        var vehicle=(net.ltxprogrammer.changed.entity.SeatEntity)mob.getVehicle();var pos=vehicle.getAttachedBlockPos();var state=mc.level.getBlockState(pos);
        if(!(state.getBlock() instanceof net.ltxprogrammer.changed.block.Pillow))throw new AssertionError("tracked native cushion position");
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(1280,720,true,Minecraft.ON_OSX);var mv=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();mv.pushPose();
        try{
            mv.setIdentity();mv.translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,640,360,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.11F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
            g.pose().translate(270,300,150);g.pose().scale(130,-130,130);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(8));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(30));
            mc.getBlockRenderer().renderSingleBlock(state,g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            mc.getBlockEntityRenderDispatcher().renderItem(mc.level.getBlockEntity(pos),g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            mc.getEntityRenderDispatcher().render(mob,mob.getX()-pos.getX(),mob.getY()-pos.getY(),mob.getZ()-pos.getZ(),0,1,g.pose(),mc.renderBuffers().bufferSource(),15728880);g.flush();
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("jerboa-native-cushion.png"));}
        }finally{mv.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
        System.out.println("JERBOA_NATIVE_SEAT_PASS: tracked vanilla seat and corrected small-body passenger offset");
    }
    static void perchCloseup(Minecraft mc)throws Exception{
        var pos=new net.minecraft.core.BlockPos(0,150,0);var block=net.parkabird.changedsynergy.init.ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get();var state=block.defaultBlockState();mc.level.setBlock(pos,state,3);block.setPlacedBy(mc.level,pos,state,null,net.minecraft.world.item.ItemStack.EMPTY);
        var owl=net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(mc.level);owl.setNoAi(true);owl.tickCount=100;
        net.ltxprogrammer.changed.entity.AccessoryEntities.INSTANCE.forceReloadAccessories(owl);net.ltxprogrammer.changed.data.AccessorySlots.getForEntity(owl).orElseThrow().quickMoveStack(new net.minecraft.world.item.ItemStack(net.parkabird.changedsynergy.init.ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get()));owl.startSleeping(pos);
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(1920,1080,true,Minecraft.ON_OSX);var mv=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();mv.pushPose();
        try{
            mv.setIdentity();mv.translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,960,540,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.11F,.12F,.15F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int i=0;i<2;i++){
                g.pose().pushPose();g.pose().translate(270+440*i,490,150);g.pose().scale(145,-145,145);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(8));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(i==0?205:110));
                mc.getBlockRenderer().renderSingleBlock(state,g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                g.pose().pushPose();g.pose().translate(0,1,0);mc.getBlockRenderer().renderSingleBlock(state.setValue(net.parkabird.changedsynergy.block.PerchBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.pose().popPose();
                var point=net.parkabird.changedsynergy.ai.PerchSleepService.position(mc.level,pos);mc.getEntityRenderDispatcher().render(owl,point.x-pos.getX(),point.y-pos.getY(),point.z-pos.getZ(),0,1,g.pose(),mc.renderBuffers().bufferSource(),15728880);g.pose().popPose();
            }
            g.flush();try(var image=net.minecraft.client.Screenshot.takeScreenshot(target)){image.writeToFile(mc.gameDirectory.toPath().resolve("owl-folded-trousers-front-side.png"));}
        }finally{mv.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);owl.stopSleeping();}
    }
}
