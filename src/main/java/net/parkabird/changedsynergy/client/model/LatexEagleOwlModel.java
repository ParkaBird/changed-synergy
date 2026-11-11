package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.*;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.client.renderer.animate.*;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;

public final class LatexEagleOwlModel extends AdvancedHumanoidModel<LatexEagleOwl> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("changed_synergy:latex_eagle_owl"),"main");
    private final ModelPart root,head,torso,leftArm,rightArm,leftLeg,rightLeg,leftLower,rightLower,leftFoot,rightFoot;
    private final HumanoidAnimator<LatexEagleOwl,LatexEagleOwlModel> animator;
    private final Map<LatexEagleOwl,float[]> aerial=new WeakHashMap<>();
    public LatexEagleOwlModel(ModelPart root){
        super(root);this.root=root;head=root.getChild("Head");torso=root.getChild("Torso");leftArm=root.getChild("LeftArm");rightArm=root.getChild("RightArm");
        leftLeg=root.getChild("LeftLeg");rightLeg=root.getChild("RightLeg");leftLower=leftLeg.getChild("LeftLowerLeg");rightLower=rightLeg.getChild("RightLowerLeg");leftFoot=leftLower.getChild("LeftFoot");rightFoot=rightLower.getChild("RightFoot");
        var tail=torso.getChild("Tail");var leftWing=leftArm.getChild("LeftFlight");var rightWing=rightArm.getChild("RightFlight");
        animator=new HumanoidAnimator<LatexEagleOwl,LatexEagleOwlModel>(this){
            @Override public void applyPropertyModelLimbs(net.minecraft.client.model.HumanoidModel<?> property){
                // Human pose adapters must not replace the owl's own wide-angle neck rotation.
                float pitch=head.xRot,yaw=head.yRot,roll=head.zRot;
                super.applyPropertyModelLimbs(property);
                head.setRotation(pitch,yaw,roll);
            }
        }.hipOffset(-1.5F)
                .addPreset(AnimatorPresets.birdLike(head,torso,leftArm,rightArm,tail,List.of(tail.getChild("TailPrimary")),leftLeg,leftLower,leftFoot,leftFoot.getChild("LeftPad"),rightLeg,rightLower,rightFoot,rightFoot.getChild("RightPad"),leftWing,leftWing.getChild("LeftSubFlight"),rightWing,rightWing.getChild("RightSubFlight")))
                .addAnimator(new MuntjacEarsAnimator<>(head.getChild("Feathers").getChild("LeftEarTuftPivot"),head.getChild("Feathers").getChild("RightEarTuftPivot")));
        SmoothGrabAnimator.install(animator,head,torso,leftArm,rightArm);
    }
    private static float ease(float value){return value*value*(3-2*value);}
    public float aerialBlend(LatexEagleOwl owl){var value=aerial.get(owl);return value==null?0:ease(value[1]);}
    public org.joml.Vector3f clawPosition(){
        var left=new PoseStack();leftLeg.translateAndRotate(left);leftLower.translateAndRotate(left);leftFoot.translateAndRotate(left);leftFoot.getChild("LeftPad").translateAndRotate(left);
        var right=new PoseStack();rightLeg.translateAndRotate(right);rightLower.translateAndRotate(right);rightFoot.translateAndRotate(right);rightFoot.getChild("RightPad").translateAndRotate(right);
        var anchor=left.last().pose().getTranslation(new org.joml.Vector3f()).lerp(right.last().pose().getTranslation(new org.joml.Vector3f()),.5F);
        var torsoPose=new PoseStack();torso.translateAndRotate(torsoPose);
        return new org.joml.Matrix4f(torsoPose.last().pose()).invert().transformPosition(anchor);
    }
    @Override public void setupAnim(LatexEagleOwl owl,float swing,float amount,float age,float yaw,float pitch){
        var preview=net.parkabird.changedsynergy.client.InventoryFormRenderState.entity;
        if(preview!=null){yaw=Mth.wrapDegrees(preview.yHeadRot-preview.yBodyRot);pitch=preview.getXRot();}
        root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(owl,swing,amount,age,Mth.clamp(Mth.wrapDegrees(yaw),-135,135),Mth.clamp(pitch,-90,90));
        if(PerchSleepService.perch(owl)!=null)return;
        float elapsed=owl.wingFlapTime(age-owl.tickCount);
        if(elapsed>0&&elapsed<8){float pulse=Mth.sin(elapsed/8*Mth.PI);pulse*=pulse;
            leftArm.zRot-=1.1F*pulse;rightArm.zRot+=1.1F*pulse;
            leftArm.getChild("LeftFlight").yRot-=.45F*pulse;rightArm.getChild("RightFlight").yRot+=.45F*pulse;
        }
        var physical=net.parkabird.changedsynergy.client.PlayerHugPoses.physical(owl);var grab=net.ltxprogrammer.changed.ability.AbstractAbility.getAbilityInstance(physical,net.ltxprogrammer.changed.init.ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if(grab==null)grab=CreatureGrabService.ability(owl);
        boolean active=EagleOwlTraits.airborne(physical)&&grab!=null&&!grab.suited&&(grab.grabbedEntity!=null||grab.getController().getProgressActive()>0);
        var transition=aerial.computeIfAbsent(owl,k->new float[]{age,0,0});float step=Mth.clamp(age-transition[0],0,4)/5;transition[0]=age;transition[1]=Mth.clamp(transition[1]+(active?step:-step),0,1);
        transition[2]=Mth.clamp(transition[2]+(active&&grab.grabbedEntity!=null?step:-step),0,1);
        float blend=ease(transition[1]),caught=ease(transition[2]);if(blend==0){syncPropertyModel(owl);return;}
        // Reach forward to strike, then sweep the feet back beneath the hips once the talons connect.
        for(var leg:List.of(leftLeg,rightLeg))leg.xRot=Mth.lerp(blend,leg.xRot,Mth.lerp(caught,-1.65F,.65F));
        for(var lower:List.of(leftLower,rightLower))lower.xRot=Mth.lerp(blend,lower.xRot,Mth.lerp(caught,.85F,-.35F));
        for(var foot:List.of(leftFoot,rightFoot))foot.xRot=Mth.lerp(blend,foot.xRot,Mth.lerp(caught,.45F,-.3F));
        syncPropertyModel(owl);
    }
    @Override public ModelPart getArm(HumanoidArm side){return side==HumanoidArm.LEFT?leftArm:rightArm;}
    @Override public ModelPart getLeg(HumanoidArm side){return side==HumanoidArm.LEFT?leftLeg:rightLeg;}
    @Override public ModelPart getHead(){return head;}
    @Override public ModelPart getTorso(){return torso;}
    @Override public HumanoidAnimator<LatexEagleOwl,LatexEagleOwlModel> getAnimator(LatexEagleOwl owl){return animator;}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha){root.render(pose,buffer,light,overlay,red,green,blue,alpha);}
}
