package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.entity.GenderedEntity;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LatexManul extends AbstractLatexManul implements GenderedEntity {
    public LatexManul(EntityType<? extends LatexManul> type, Level level) {
        super(type, level);
    }

    @Override
    public Gender getGender() {
        return Gender.MALE;
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.REPLICATION;
    }
}
