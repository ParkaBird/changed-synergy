package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.TransfurMode;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class LatexGrayFox extends AbstractLatexGrayFox {
    public LatexGrayFox(EntityType<? extends LatexGrayFox> type, Level level) {
        super(type, level);
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.REPLICATION;
    }
}
