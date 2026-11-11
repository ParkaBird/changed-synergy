package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.beast.LatexFennecFox;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;

/** A light desert rodent: fragile in a fight, quick on its feet. */
public final class LatexJerboa extends LatexFennecFox {
    public LatexJerboa(EntityType<? extends LatexJerboa> type,Level level){super(type,level);}
    // Its hip is 2.7 model pixels lower than a full-size digitigrade body's hip.
    @Override public double getMyRidingOffset(){return super.getMyRidingOffset()+2.7/16;}
    @Override protected void setAttributes(AttributeMap attributes){
        super.setAttributes(attributes);
        attributes.getInstance(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get()).setBaseValue(1.25);
        attributes.getInstance(Attributes.MAX_HEALTH).setBaseValue(16);
        attributes.getInstance(Attributes.ATTACK_DAMAGE).setBaseValue(2);
        attributes.getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(1.18);
    }
}
