package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.beast.LatexCrow;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Mountain bird; inherits Crow locomotion, replication and powder-snow handling. */
public final class LatexEagleOwl extends LatexCrow {
    public LatexEagleOwl(EntityType<? extends ChangedEntity> type, Level level) {super(type,level);}
    @Override protected void registerGoals(){super.registerGoals();goalSelector.addGoal(0,new net.parkabird.changedsynergy.ai.EagleOwlAirGrabGoal(this));}
    @Override protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        attributes.getInstance(Attributes.MAX_HEALTH).setBaseValue(26);
        attributes.getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(1.05);
        var attack=attributes.getInstance(Attributes.ATTACK_DAMAGE);attack.setBaseValue(attack.getBaseValue()*1.08);
    }
    private long wingFlapStart=Long.MIN_VALUE;
    public void flapWings(){wingFlapStart=level().getGameTime();}
    public float wingFlapTime(float partial){return wingFlapStart==Long.MIN_VALUE?Float.POSITIVE_INFINITY:level().getGameTime()-wingFlapStart+partial;}
    @Override public int getMaxHeadYRot(){return 135;}
    @Override public Color3 getTransfurColor(TransfurCause cause){return Color3.fromInt(0x9B784E);}
}
