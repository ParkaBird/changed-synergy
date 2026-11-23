package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.PowderSnowWalkable;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.beast.AbstractLatexWolf;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

/** Taiga-adapted gray fox mirroring Changed's purple fox attributes and snow stride. */
public abstract class AbstractLatexGrayFox extends AbstractLatexWolf implements PowderSnowWalkable {
    public static final double BASE_MOVEMENT_SPEED = 1.1D;
    public static final double BASE_SWIM_SPEED = 0.93D;

    protected AbstractLatexGrayFox(EntityType<? extends AbstractLatexGrayFox> type, Level level) {
        super(type, level);
    }

    @Override
    protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        AttributeInstance speed = attributes.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(BASE_MOVEMENT_SPEED);
        AttributeInstance swim = attributes.getInstance(ForgeMod.SWIM_SPEED.get());
        if (swim != null) swim.setBaseValue(BASE_SWIM_SPEED);
    }

    @Override
    public Color3 getTransfurColor(TransfurCause cause) {
        return Color3.fromInt(0x8D9196);
    }
}
