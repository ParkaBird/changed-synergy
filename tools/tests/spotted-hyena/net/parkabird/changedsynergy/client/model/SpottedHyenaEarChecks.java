package net.parkabird.changedsynergy.client.model;

public final class SpottedHyenaEarChecks {
    public static void verifyEyes(LatexSpottedHyenaModel<?> model) {
        var head=model.getHead();var original=head.storePose();
        try {
            for(float y:new float[]{-2.7F,0,3})for(float pitch:new float[]{-85,-45,0,25,45,70,89})for(float yaw:new float[]{-60,0,60}) {
                head.y=y;head.xRot=(float)Math.toRadians(pitch);head.yRot=(float)Math.toRadians(yaw);
                var base=new com.mojang.blaze3d.vertex.PoseStack();head.translateAndRotate(base);
                var eye=new com.mojang.blaze3d.vertex.PoseStack();model.scaleForHead(eye);head.translateAndRotate(eye);
                var local=new org.joml.Vector3f(0,-4F/16,-4.0025F/16).mulPosition(eye.last().pose())
                        .mulPosition(new org.joml.Matrix4f(base.last().pose()).invert());
                if(local.z> -4.1F/16)throw new AssertionError("female eyes covered at pitch="+pitch+" yaw="+yaw+" headY="+y+" localZ="+local.z);
            }
        }finally{head.loadPose(original);}
    }
    public static void verify() {
        for(var layer:java.util.List.of(SpottedHyenaModelLayers.male(),SpottedHyenaModelLayers.female())) {
            var head=layer.bakeRoot().getChild("Head");var left=head.getChild("LeftEar");var right=head.getChild("RightEar");
            float bind=left.getChild("LeftEarPivot").zRot;
            MuntjacEarsAnimator.poseEars(left,right,.45F,0,-.4F);
            if(Math.abs(left.xRot)<.02F||left.yRot>=0||left.yRot!=right.yRot)throw new AssertionError("jump and turn follow-through");
            for(int frame=0;frame<1200;frame++)MuntjacEarsAnimator.poseEars(left,right,.45F,0,-.4F);
            if(left.getChild("LeftEarPivot").zRot!=bind||Math.abs(left.xRot)>.105F)throw new AssertionError("no bind drift or accumulated rotation");
            MuntjacEarsAnimator.poseEars(left,right,0,0,0);
            if(left.xRot!=0||left.yRot!=0||left.zRot!=0)throw new AssertionError("ears settle exactly at rest");
        }
        var turn=new MuntjacEarsAnimator.TurnResponse();turn.sample(0,0,1);
        float first=turn.sample(1,70,1),rebound=0,end=0;
        for(int tick=2;tick<180;tick++){end=turn.sample(tick,70,1);rebound=Math.max(rebound,end);}
        if(first>=-.1F||rebound<.02F||Math.abs(end)>.00001F)throw new AssertionError("head-turn spring rebounds and settles");
    }
}
