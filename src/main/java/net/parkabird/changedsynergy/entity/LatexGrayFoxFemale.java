package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.TransfurMode;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LatexGrayFoxFemale extends AbstractLatexGrayFox {
    public LatexGrayFoxFemale(EntityType<? extends LatexGrayFoxFemale> type, Level level) {
        super(type, level);
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.ABSORPTION;
    }
}
