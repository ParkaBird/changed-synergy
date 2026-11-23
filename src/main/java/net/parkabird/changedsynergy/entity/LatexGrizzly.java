package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

/** Heavy taiga bear; Crocodile-class health and gait with stronger melee and knockback poise. */
public final class LatexGrizzly extends ChangedEntity {
    public static final double BASE_MAX_HEALTH = 32.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.925D;
    public static final double BASE_ATTACK_DAMAGE = 6.0D;
    public static final double BASE_KNOCKBACK_RESISTANCE = 0.2D;
    public static final double BASE_SWIM_SPEED = 1.0D;
    public static final double BASE_AIR_CAPACITY = 15.0D;

    public LatexGrizzly(EntityType<? extends LatexGrizzly> type, Level level) {
        super(type, level);
    }

    @Override
    protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        setBase(attributes, Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        setBase(attributes, Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        setBase(attributes, Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE);
        setBase(attributes, Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE);
        setBase(attributes, ForgeMod.SWIM_SPEED.get(), BASE_SWIM_SPEED);
        setBase(attributes, ChangedAttributes.AIR_CAPACITY.get(), BASE_AIR_CAPACITY);
    }

    private static void setBase(AttributeMap attributes, Attribute attribute, double value) {
        AttributeInstance instance = attributes.getInstance(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.REPLICATION;
    }

    @Override
    public Color3 getTransfurColor(TransfurCause cause) {
        return Color3.fromInt(0x6E4931);
    }
}
