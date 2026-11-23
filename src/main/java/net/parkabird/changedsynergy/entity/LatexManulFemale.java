package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.entity.GenderedEntity;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LatexManulFemale extends AbstractLatexManul implements GenderedEntity {
    public LatexManulFemale(EntityType<? extends LatexManulFemale> type, Level level) {
        super(type, level);
    }

    @Override
    public Gender getGender() {
        return Gender.FEMALE;
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.ABSORPTION;
    }
}
