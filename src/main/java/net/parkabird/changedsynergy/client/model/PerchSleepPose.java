package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.*;

/** Bone transforms from the supplied sleeping project, relative to the beam top. */
public final class PerchSleepPose {
    private PerchSleepPose(){}
    public static void apply(AdvancedHumanoidModel<?> model){
        var upper=new PoseStack();upper.translate(0,17.49344F/16,3.15832F/16);upper.mulPose(new Quaternionf().rotationX((float)java.lang.Math.toRadians(16)));
        root(model.getTorso(),upper,0,-12,0,0,0,0);
        root(model.getHead(),upper,0,-10.5F,0,-30,0,10);
        for(var side:HumanoidArm.values()){
            boolean left=side==HumanoidArm.LEFT;String prefix=left?"Left":"Right";
            var arm=model.getArm(side);root(arm,upper,left?5:-5,-10,0,0,0,left?-3:3);
            child(arm,prefix+"Flight",left?3:-3,3,0,0,left?-100:100,0);
            if(arm.hasChild(prefix+"Flight"))child(arm.getChild(prefix+"Flight"),prefix+"SubFlight",left?4:-3,-2,0,0,left?-40:40,0);
            var leg=model.getLeg(side);set(leg,left?2.5F:-2.5F,16.49344F,3.15832F,-30,0,0);
            child(leg,prefix+"LowerLeg",0,5.85778F,-1.29864F,110,0,0);
            if(leg.hasChild(prefix+"LowerLeg")){
                var lower=leg.getChild(prefix+"LowerLeg");child(lower,prefix+"Foot",.01F,2.14355F,2.83101F,-80,0,0);
                if(lower.hasChild(prefix+"Foot"))child(lower.getChild(prefix+"Foot"),prefix+"Pad",-.01F,3.52367F,-1.90737F,0,0,0);
            }
        }
        var torso=model.getTorso();child(torso,"Tail",0,10.5F,0,-15,0,0);
        if(torso.hasChild("Tail"))child(torso.getChild("Tail"),"TailPrimary",0,0,0,7.5F,0,0);
    }
    private static void child(ModelPart parent,String name,float x,float y,float z,float rx,float ry,float rz){if(parent.hasChild(name))set(parent.getChild(name),x,y,z,rx,ry,rz);}
    private static void set(ModelPart p,float x,float y,float z,float rx,float ry,float rz){p.setPos(x,y,z);p.setRotation((float)java.lang.Math.toRadians(rx),(float)java.lang.Math.toRadians(ry),(float)java.lang.Math.toRadians(rz));}
    private static void root(ModelPart p,PoseStack parent,float x,float y,float z,float rx,float ry,float rz){
        var matrix=new Matrix4f(parent.last().pose()).translate(x/16,y/16,z/16).rotateZYX((float)java.lang.Math.toRadians(rz),(float)java.lang.Math.toRadians(ry),(float)java.lang.Math.toRadians(rx));
        var pos=matrix.getTranslation(new Vector3f()).mul(16);var rot=matrix.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
        p.setPos(pos.x,pos.y,pos.z);p.setRotation(rot.x,rot.y,rot.z);
    }
}
