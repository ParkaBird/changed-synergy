package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.parkabird.changedsynergy.ai.PlayerHugService;
import net.parkabird.changedsynergy.client.model.LatexEagleOwlModel;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Changes only the held render transform; native hold physics, release and QTE remain untouched. */
public final class EagleOwlCarryClient {
    public static boolean position(PoseStack pose,LatexEagleOwl owl,GrabEntityAbilityInstance grab,float partial){
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(owl);
        if(!(renderer instanceof LivingEntityRenderer<?,?> living)||!(living.getModel() instanceof LatexEagleOwlModel model))return false;
        float blend=model.aerialBlend(owl);if(blend==0)return false;
        var ground=new PoseStack();
        if(PlayerHugService.active(grab))PlayerHugPoses.position(ground,owl,grab,partial);
        else{double difference=owl.getBbHeight()-grab.grabbedEntity.getBbHeight();if(difference>0)ground.translate(0,-Math.min(.6,difference*.35),0);}
        var flight=new PoseStack();flight.mulPose(Axis.ZP.rotationDegrees(11));flight.translate(.0625,0,.28125);
        var anchor=model.clawPosition();flight.translate(anchor.x,anchor.y+.06,anchor.z);
        // Model +Y points down: this lays the target face-down with its upper back between the talons.
        flight.mulPose(Axis.XP.rotationDegrees(90));
        flight.translate(0,-grab.grabbedEntity.getBbHeight()*.3,-grab.grabbedEntity.getBbWidth()*.25);
        var from=ground.last().pose();var to=flight.last().pose();
        var translation=from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()),blend);
        var rotation=from.getUnnormalizedRotation(new Quaternionf()).slerp(to.getUnnormalizedRotation(new Quaternionf()),blend);
        pose.translate(translation.x,translation.y,translation.z);pose.mulPose(rotation);
        return true;
    }
}
