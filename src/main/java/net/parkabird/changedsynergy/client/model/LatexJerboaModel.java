package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.ltxprogrammer.changed.client.renderer.animate.*;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

public final class LatexJerboaModel<T extends ChangedEntity> extends AdvancedHumanoidModel<T> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("changed_synergy:latex_jerboa"),"main");
    private final ModelPart root,head,torso,leftArm,rightArm,leftLeg,rightLeg;
    private final HumanoidAnimator<T,LatexJerboaModel<T>> animator;
    public LatexJerboaModel(ModelPart root){
        super(root);this.root=root;head=root.getChild("Head");torso=root.getChild("Torso");leftArm=root.getChild("LeftArm");rightArm=root.getChild("RightArm");leftLeg=root.getChild("LeftLeg");rightLeg=root.getChild("RightLeg");
        var leftLower=leftLeg.getChild("LeftLowerLeg");var rightLower=rightLeg.getChild("RightLowerLeg");var leftFoot=leftLower.getChild("LeftFoot");var rightFoot=rightLower.getChild("RightFoot");
        var tail=torso.getChild("Tail");var chain=new ArrayList<ModelPart>();var joint=tail;
        for(String name:List.of("TailPrimary","TailSecondary","TailTertiary","TailQuaternary","TailQuinary","TailSenary")){
            joint=joint.getChild(name);chain.add(joint);joint=joint.getChild(name+"_bind_r1");
        }
        animator=HumanoidAnimator.<T,LatexJerboaModel<T>>of(this).hipOffset(-1.2F)
                .legLength(9.6F).torsoLength(8.8F).armLength(9.6F).torsoWidth(4F).legSpacing(-.2F)
                .addPreset(AnimatorPresets.deerLike(head,head.getChild("LeftEar"),head.getChild("RightEar"),torso,leftArm,rightArm,tail,chain,
                        leftLeg,leftLower,leftFoot,leftFoot.getChild("LeftPad"),rightLeg,rightLower,rightFoot,rightFoot.getChild("RightPad")))
                .addAnimator(new MuntjacEarsAnimator<>(head.getChild("LeftEar"),head.getChild("RightEar")));
        SmoothGrabAnimator.install(animator,head,torso,leftArm,rightArm);
    }
    @Override public void scaleForHead(PoseStack pose){
        pose.translate(head.x/16F,head.y/16F,head.z/16F);pose.scale(.8F,.8F,.8F);pose.translate(-head.x/16F,-head.y/16F,-head.z/16F);
    }
    @Override public void setupAnim(T entity,float swing,float amount,float age,float yaw,float pitch){root.getAllParts().forEach(ModelPart::resetPose);super.setupAnim(entity,swing,amount,age,yaw,pitch);}
    @Override public ModelPart getHead(){return head;}
    @Override public ModelPart getTorso(){return torso;}
    @Override public ModelPart getArm(HumanoidArm side){return side==HumanoidArm.LEFT?leftArm:rightArm;}
    @Override public ModelPart getLeg(HumanoidArm side){return side==HumanoidArm.LEFT?leftLeg:rightLeg;}
    @Override public HumanoidAnimator<T,LatexJerboaModel<T>> getAnimator(T entity){return animator;}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha){root.render(pose,buffer,light,overlay,red,green,blue,alpha);}
}
