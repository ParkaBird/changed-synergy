package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.client.*;
import net.parkabird.changedsynergy.client.model.LatexEagleOwlModel;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;
import net.parkabird.changedsynergy.init.ChangedSynergyBlocks;
import org.joml.Matrix4f;

final class EagleOwlFeedbackChecks {
    private static int checks;
    static void firstPerson(Minecraft mc)throws Exception{
        try(var img=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int colored=0,total=0;for(int y=img.getHeight()*2/3;y<img.getHeight()*9/10;y++)for(int x=img.getWidth()*4/5;x<img.getWidth();x++){
                int p=img.getPixelRGBA(x,y),r=p&255,g=p>>8&255,b=p>>16&255;total++;if(java.lang.Math.max(r,java.lang.Math.max(g,b))-java.lang.Math.min(r,java.lang.Math.min(g,b))>3)colored++;
            }check(colored<total/100,"real first-person arm region remains monochrome: "+colored+"/"+total);img.writeToFile(mc.gameDirectory.toPath().resolve("owl-first-person-gray.png"));
        }
        var option=ChangedSynergyClientConfig.CLIENT.eagleOwlMonochromeVision;boolean previous=option.get();try{option.set(false);check(!EagleOwlVisionClient.active(),"vision option disables grayscale");option.set(true);check(EagleOwlVisionClient.active(),"vision option re-enables grayscale");}finally{option.set(previous);}
    }
    static void verify(Minecraft mc,LatexEagleOwl npc,LatexEagleOwlModel model)throws Exception{
        var renderer=(LatexEagleOwlRenderer)mc.getEntityRenderDispatcher().getRenderer(npc);var eyes=new EagleOwlEyesLayer(renderer);var vertices=new EyeVertices();
        eyes.render(new PoseStack(),type->vertices,15728880,npc,0,0,1,npc.tickCount,0,0);
        check(vertices.count==32,"eyes contain eight single quads without coplanar reverse faces");
        var perchPos=new net.minecraft.core.BlockPos(0,150,0);var perch=ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get();
        mc.level.setBlock(perchPos,perch.defaultBlockState(),3);perch.setPlacedBy(mc.level,perchPos,perch.defaultBlockState(),null,net.minecraft.world.item.ItemStack.EMPTY);
        npc.startSleeping(perchPos);var closed=new EyeVertices();eyes.render(new PoseStack(),type->closed,15728880,npc,0,0,1,npc.tickCount,0,0);
        check(closed.count==0,"sleeping owl exposes its closed-eye base texture");
        model.prepareMobModel(npc,0,0,1);model.setupAnim(npc,0,0,npc.tickCount,0,0);
        check(model.getHead().zRot>.15F&&model.getLeg(HumanoidArm.LEFT).xRot<-.5F,"supplied tilted head and crouched sleeping legs applied");
        npc.stopSleeping();mc.level.removeBlock(perchPos,false);
        long oldTime=mc.level.getDayTime();try{mc.level.setDayTime(18000);mc.level.updateSkyBrightness();check(EagleOwlVisionClient.fullMoonNight(mc)&&!EagleOwlVisionClient.active(),"full moon night restores color");mc.level.setDayTime(42000);mc.level.updateSkyBrightness();check(!EagleOwlVisionClient.fullMoonNight(mc)&&EagleOwlVisionClient.active(),"ordinary night remains monochrome");}finally{mc.level.setDayTime(oldTime);mc.level.updateSkyBrightness();}
        BondedSuitService.ensureGrabAbility(npc);var grab=BondedSuitService.ability(npc);npc.setOnGround(false);npc.tickCount+=20;float age=npc.tickCount;
        grab.grabbedEntity=null;grab.getController().exchangeKeyState(false);model.prepareMobModel(npc,0,0,1);model.setupAnim(npc,0,0,age,0,0);var base=model.getArm(HumanoidArm.RIGHT).storePose();
        grab.getController().exchangeKeyState(true);model.setupAnim(npc,0,0,age+1,0,0);var reach=model.getArm(HumanoidArm.RIGHT).storePose();
        check(java.lang.Math.abs(base.xRot-reach.xRot)<.02F&&java.lang.Math.abs(base.yRot-reach.yRot)<.02F&&java.lang.Math.abs(base.zRot-reach.zRot)<.02F,"airborne reach does not replace native wing animation");
        grab.grabbedEntity=ChangedEntities.LATEX_HUMAN.get().create(mc.level);model.setupAnim(npc,0,0,age+2,0,0);var caught=model.getArm(HumanoidArm.RIGHT).storePose();
        check(java.lang.Math.abs(base.xRot-caught.xRot)<.03F&&java.lang.Math.abs(base.yRot-caught.yRot)<.03F,"caught prey does not force an arm hold in flight");grab.grabbedEntity=null;grab.getController().exchangeKeyState(false);npc.setOnGround(true);
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();var target=new TextureTarget(mc.getWindow().getWidth(),mc.getWindow().getHeight(),true,Minecraft.ON_OSX);var mv=RenderSystem.getModelViewStack();mv.pushPose();
        try{mv.setIdentity();mv.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);target.setClearColor(.12F,.2F,.16F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            for(int col=0;col<3;col++){
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,(col*2+1)*w/6,h-40,h/5,new float[]{-500,0,500}[col],-30,mc.player);
                var proxy=(LatexEagleOwl)ProcessTransfur.getPlayerTransfurVariant(mc.player).getChangedEntity();var playerModel=((LatexEagleOwlRenderer)mc.getEntityRenderDispatcher().getRenderer(proxy)).getModel();
                System.out.println("OWL_GUI_HEAD: col="+col+" model="+playerModel.getHead().yRot+" player="+mc.player.yHeadRot+"/"+mc.player.yBodyRot+" proxy="+proxy.yHeadRot+"/"+proxy.yBodyRot+" old="+proxy.yHeadRotO+"/"+proxy.yBodyRotO);
                check(java.lang.Math.abs(playerModel.getHead().yRot)<1,"inventory head follows a bounded mouse direction");
                check(java.lang.Math.abs(((net.minecraft.client.model.HumanoidModel<?>)playerModel).head.yRot-playerModel.getHead().yRot)<.001F,"inventory property head matches rendered head");
                g.drawCenteredString(mc.font,new String[]{"Look left","Look ahead","Look right"}[col],(col*2+1)*w/6,h-20,0xFFFFFF);
            }g.flush();capture(mc,target,"owl-inventory-heads.png");target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var blocks=java.util.List.of(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get());
            for(int i=0;i<2;i++){
                var block=blocks.get(i);var state=block.defaultBlockState();var baked=mc.getBlockRenderer().getBlockModel(state);
                check(baked.getRenderTypes(state,net.minecraft.util.RandomSource.create(),net.minecraftforge.client.model.data.ModelData.EMPTY).contains(RenderType.cutout()),"perch uses cutout rendering for transparent feathers");
                g.pose().pushPose();g.pose().translate((i*2+1)*w/4,h-40,100);g.pose().scale(85,-85,85);g.pose().mulPose(Axis.XP.rotationDegrees(20));g.pose().mulPose(Axis.YP.rotationDegrees(35));
                mc.getBlockRenderer().renderSingleBlock(state,g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                g.pose().translate(i==0?0:1,i==0?1:0,0);
                mc.getBlockRenderer().renderSingleBlock(state.setValue(net.parkabird.changedsynergy.block.PerchBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                g.pose().popPose();
            }g.flush();capture(mc,target,"owl-perches-cutout.png");
            target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            for(int i=0;i<2;i++){
                var block=(net.parkabird.changedsynergy.block.PerchBlock)blocks.get(i);var state=block.defaultBlockState();var pos=new net.minecraft.core.BlockPos(i*6,150,0);
                mc.level.setBlock(pos,state,3);block.setPlacedBy(mc.level,pos,state,null,net.minecraft.world.item.ItemStack.EMPTY);
                npc.startSleeping(pos);g.pose().pushPose();g.pose().translate((i*2+1)*w/4,h-45,100);g.pose().scale(48,-48,48);g.pose().mulPose(Axis.XP.rotationDegrees(12));g.pose().mulPose(Axis.YP.rotationDegrees(25));
                mc.getBlockRenderer().renderSingleBlock(state,g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                g.pose().pushPose();g.pose().translate(i==0?0:1,i==0?1:0,0);mc.getBlockRenderer().renderSingleBlock(state.setValue(net.parkabird.changedsynergy.block.PerchBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),g.pose(),mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.pose().popPose();
                var point=PerchSleepService.position(mc.level,pos);mc.getEntityRenderDispatcher().render(npc,point.x-pos.getX(),point.y-pos.getY(),point.z-pos.getZ(),0,1,g.pose(),mc.renderBuffers().bufferSource(),15728880);
                g.pose().popPose();npc.stopSleeping();mc.level.removeBlock(pos,false);
            }g.flush();capture(mc,target,"owl-perch-sleeping.png");
        }finally{mv.popPose();RenderSystem.applyModelViewMatrix();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
        airRelease(mc,npc);
        var cameraBefore=mc.options.getCameraType();var playerPos=mc.player.position();
        var sleepBlock=ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get();var sleepState=sleepBlock.defaultBlockState();mc.level.setBlock(perchPos,sleepState,3);sleepBlock.setPlacedBy(mc.level,perchPos,sleepState,null,net.minecraft.world.item.ItemStack.EMPTY);
        try{
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.player.startSleeping(perchPos);PerchSleepCameraClient.update();check(mc.options.getCameraType()==net.minecraft.client.CameraType.THIRD_PERSON_BACK,"perch sleep switches first person to third person");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);PerchSleepCameraClient.update();check(!mc.options.getCameraType().isFirstPerson(),"perch sleep keeps a third-person view");
            mc.player.stopSleeping();PerchSleepCameraClient.update();check(mc.options.getCameraType()==net.minecraft.client.CameraType.FIRST_PERSON,"waking restores the original first-person view");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);mc.player.startSleeping(perchPos);PerchSleepCameraClient.update();check(mc.options.getCameraType()==net.minecraft.client.CameraType.THIRD_PERSON_FRONT,"existing third-person preference is preserved");
            mc.player.stopSleeping();PerchSleepCameraClient.update();
        }finally{if(mc.player.isSleeping())mc.player.stopSleeping();PerchSleepCameraClient.update();mc.options.setCameraType(cameraBefore);mc.player.setPos(playerPos);mc.level.removeBlock(perchPos,false);}
        var playerOwl=(LatexEagleOwl)ProcessTransfur.getPlayerTransfurVariant(mc.player).getChangedEntity();boolean grounded=mc.player.onGround();
        try{mc.player.setOnGround(false);net.parkabird.changedsynergy.entity.LatexSilkMoth.onExtraJump(new net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent(mc.player));check(playerOwl.wingFlapTime(0)==0,"extra jump triggers one wing beat");SilkMothWingFlapClient.receive(npc.getId());check(npc.wingFlapTime(0)==0,"observer flap packet accepts an actual owl body");}finally{mc.player.setOnGround(grounded);}
        System.out.println("EAGLE_OWL_FEEDBACK_CLIENT_PASS: "+checks+" transparency, eyes, paperdoll, independent wings, first-person vision and option checks");
    }
    private static void airRelease(Minecraft mc,LatexEagleOwl owl){
        var original=owl.position();boolean grounded=owl.onGround();var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(mc.level);target.setNoAi(true);
        var grab=new net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance(net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get(),net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEntity(owl));
        try{owl.setOnGround(false);target.setPos(owl.position());check(grab.grabEntity(target),"client air grab can acquire humanoid");grab.tickIdle();target.lerpTo(original.x,original.y,original.z,0,0,8,false);owl.setPos(original.add(12,8,12));grab.releaseEntity(false);var drop=target.position();
            for(int i=0;i<3;i++)target.tick();check(java.lang.Math.abs(target.getX()-drop.x)<.05&&java.lang.Math.abs(target.getZ()-drop.z)<.05,"released target does not interpolate back to its old location");
            check(!grab.grabEntity(target)&&EagleOwlTraits.regrabBlocked(owl),"client also blocks immediate airborne reacquisition");
        }finally{owl.setPos(original);owl.setOnGround(grounded);owl.getPersistentData().remove(EagleOwlTraits.REGRAB_AFTER);target.discard();}
    }
    private static void capture(Minecraft mc,TextureTarget target,String name)throws Exception{try(var img=net.minecraft.client.Screenshot.takeScreenshot(target)){img.writeToFile(mc.gameDirectory.toPath().resolve(name));}}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
    private static final class EyeVertices implements VertexConsumer{
        int count;public VertexConsumer vertex(double x,double y,double z){return this;}public VertexConsumer color(int r,int g,int b,int a){return this;}public VertexConsumer uv(float u,float v){return this;}public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){count++;}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
}
