package net.parkabird.changedsynergy.regression;

import net.minecraft.client.*;
import net.minecraft.world.entity.Pose;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.client.LatexEagleOwlRenderer;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;

final class EagleOwlNeckChecks {
    private static int checks;
    static void run(Minecraft mc)throws Exception {
        var owl=(LatexEagleOwl)ProcessTransfur.getPlayerTransfurVariant(mc.player).getChangedEntity();
        var model=((LatexEagleOwlRenderer)mc.getEntityRenderDispatcher().getRenderer(owl)).getModel();
        var camera=mc.options.getCameraType();var position=owl.position();var pose=owl.getPose();int samples=0;
        try {
            for(var pov:CameraType.values())for(boolean crouch:new boolean[]{false,true})for(float yaw:new float[]{0,90,-90,135,-135,0,179,-179,720,0}) {
                mc.options.setCameraType(pov);owl.setPose(crouch?Pose.CROUCHING:Pose.STANDING);
                model.prepareMobModel(owl,0,0,.5F);model.setupAnim(owl,0,0,owl.tickCount+.5F,yaw,0);
                check(Math.abs(model.getHead().yRot-net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yaw),-135,135)*net.minecraft.util.Mth.DEG_TO_RAD)<.001F,"wide-angle turn remains bounded across POV/crouch");
                check(Math.abs(model.getHead().zRot)<.001F,"POV/crouch cannot leave a neck roll");samples++;
            }
            var property=new net.minecraft.client.model.PlayerModel<>(mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);
            for(int i=0;i<24;i++){
                float yaw=i%2==0?135:-135;model.prepareMobModel(owl,0,0,.5F);model.setupAnim(owl,0,0,owl.tickCount+.5F,yaw,25);
                var nativeHead=model.getHead().storePose();property.head.setRotation(.9F,3.2F,2.18F);property.head.z=-1;
                model.getAnimator(owl).applyPropertyModelLimbs(property);
                check(Math.abs(model.getHead().xRot-nativeHead.xRot)<.001F&&Math.abs(model.getHead().yRot-nativeHead.yRot)<.001F&&Math.abs(model.getHead().zRot-nativeHead.zRot)<.001F,"human property round-trip cannot corrupt native head rotation");
                check(Math.abs(model.getHead().z+1)<.001F,"body adapter can still translate head with torso");
                owl.setPos(owl.position().add(12,4,-18));
            }
            if(net.minecraftforge.fml.ModList.get().isLoaded("parcool")){
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);owl.setPose(Pose.STANDING);
                var playerType=net.minecraft.world.entity.player.Player.class;
                var animationType=Class.forName("com.alrex.parcool.common.capability.Animation");var animation=animationType.getMethod("get",playerType).invoke(null,mc.player);
                var parkourType=Class.forName("com.alrex.parcool.common.capability.Parkourability");var parkour=parkourType.getMethod("get",playerType).invoke(null,mc.player);
                var slideType=Class.forName("com.alrex.parcool.common.action.impl.Slide");var slide=parkourType.getMethod("get",Class.class).invoke(parkour,slideType);
                var doing=Class.forName("com.alrex.parcool.common.action.Action").getDeclaredField("doing");doing.setAccessible(true);doing.setBoolean(slide,true);
                var animatorType=Class.forName("com.alrex.parcool.client.animation.Animator");var sliding=Class.forName("com.alrex.parcool.client.animation.impl.SlidingAnimator").getConstructor().newInstance();
                var tick=animatorType.getDeclaredField("tick");tick.setAccessible(true);tick.setInt(sliding,8);animationType.getMethod("setAnimator",animatorType).invoke(animation,sliding);
                for(var pov:CameraType.values())for(boolean crouch:new boolean[]{false,true})for(float yaw:new float[]{0,90,135,-135}){
                    mc.options.setCameraType(pov);owl.setPose(crouch?Pose.CROUCHING:Pose.STANDING);owl.setPos(owl.position().add(4,0,-6));
                    model.prepareMobModel(owl,0,0,.5F);model.setupAnim(owl,0,0,owl.tickCount+.5F,yaw,0);
                    System.out.println("OWL_NECK_SLIDE "+pov+" crouch="+crouch+" input="+yaw+" actual="+angles(model.getHead()));
                    check(Math.abs(model.getHead().yRot-yaw*net.minecraft.util.Mth.DEG_TO_RAD)<.001F&&Math.abs(model.getHead().zRot)<.001F,"real external slide cannot inject yaw as neck roll");
                }
                doing.setBoolean(slide,false);animationType.getMethod("removeAnimator").invoke(animation);
                model.prepareMobModel(owl,0,0,.5F);model.setupAnim(owl,0,0,owl.tickCount+.5F,0,0);
                System.out.println("OWL_NECK_AFTER_SLIDE "+angles(model.getHead()));
            }
            check(Math.abs(model.getHead().zRot)<.001F,"ending an action leaves no tilt");
            System.out.println("OWL_NECK_PROBE_PASS: "+checks+" checks, "+samples+" camera/crouch samples; parcool="+net.minecraftforge.fml.ModList.get().isLoaded("parcool"));
        }finally{owl.setPose(pose);owl.setPos(position);mc.options.setCameraType(camera);}
    }
    private static String angles(net.minecraft.client.model.geom.ModelPart part){return part.xRot+","+part.yRot+","+part.zRot+" at "+part.x+","+part.y+","+part.z;}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
