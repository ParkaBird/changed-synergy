package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.entity.GenderedEntity;
import net.ltxprogrammer.changed.entity.beast.LatexDeer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LatexMuntjacFemale extends LatexDeer implements GenderedEntity {
    public LatexMuntjacFemale(EntityType<? extends LatexMuntjacFemale> type, Level level) { super(type, level); }
    @Override protected void setAttributes(net.minecraft.world.entity.ai.attributes.AttributeMap attributes) {
        super.setAttributes(attributes);
        attributes.getInstance(net.ltxprogrammer.changed.init.ChangedAttributes.JUMP_STRENGTH.get()).setBaseValue(1.25);
    }

    @Override public Gender getGender() { return Gender.FEMALE; }
}
