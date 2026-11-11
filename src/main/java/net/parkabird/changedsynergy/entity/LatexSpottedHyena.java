package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.entity.GenderedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.beast.AbstractLatexWolf;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;

/** Terrestrial pack hunter; native wolf attributes, replication and humanoid abilities. */
public final class LatexSpottedHyena extends AbstractLatexWolf implements GenderedEntity {
    public LatexSpottedHyena(EntityType<? extends LatexSpottedHyena> type, Level level) {
        super(type, level);
    }

    @Override protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        if (getGender() == Gender.FEMALE) {
            attributes.getInstance(Attributes.MAX_HEALTH).setBaseValue(26.0);
            attributes.getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(1.13);
            attributes.getInstance(ChangedAttributes.JUMP_STRENGTH.get()).setBaseValue(1.05);
        }
    }

    @Override public Gender getGender() {
        return getType() == ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get() ? Gender.FEMALE : Gender.MALE;
    }

    @Override public Color3 getTransfurColor(TransfurCause cause) { return Color3.fromInt(0xB18B58); }
}
