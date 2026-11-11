package net.parkabird.changedsynergy.client.model;

import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;

/** Equipment follows the wearer's final bones, including poses applied after the native animator. */
public final class ArmorPoseFit {
    private ArmorPoseFit() {}
    public static void copy(AdvancedHumanoidModel<?> target, AdvancedHumanoidModel<?> source) {
        target.getHead().copyFrom(source.getHead());
        target.getTorso().copyFrom(source.getTorso());
        for (var side : HumanoidArm.values()) {
            target.getArm(side).copyFrom(source.getArm(side));
            copyLeg(target.getLeg(side), source.getLeg(side), side == HumanoidArm.LEFT ? "Left" : "Right");
        }
    }
    private static void copyLeg(ModelPart target, ModelPart source, String side) {
        target.copyFrom(source);
        for (String joint : new String[]{"LowerLeg", "Foot", "Pad"}) {
            if (!target.hasChild(side + joint) || !source.hasChild(side + joint)) return;
            target = target.getChild(side + joint);
            source = source.getChild(side + joint);
            target.copyFrom(source);
        }
    }
}
