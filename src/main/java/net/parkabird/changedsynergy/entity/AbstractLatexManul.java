package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.PowderSnowWalkable;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

/**
 * Cat-like latex manul: snow-leopard powder-snow handling, human-scale health,
 * slightly thicker fur armor, and a rare orange coat morph (panda-brown style).
 */
public abstract class AbstractLatexManul extends ChangedEntity implements PowderSnowWalkable {
    public static final double BASE_MAX_HEALTH = 20.0D;
    public static final double BASE_MOVEMENT_SPEED = 1.2D;
    public static final double BASE_SWIM_SPEED = 0.9D;
    public static final double BASE_AIR_CAPACITY = 7.5D;
    public static final double BASE_JUMP_STRENGTH = 1.25D;
    public static final double BASE_FALL_RESISTANCE = 2.5D;
    /** Default latex armor is 4; thick manul fur sits a step above. */
    public static final double BASE_ARMOR = 5.0D;
    /** Matches vanilla brown-panda rarity. */
    public static final int ORANGE_MORPH_CHANCE = 120;

    private static final EntityDataAccessor<Boolean> DATA_ORANGE_MORPH =
            SynchedEntityData.defineId(AbstractLatexManul.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_ORANGE_MORPH = "OrangeMorph";

    protected AbstractLatexManul(EntityType<? extends AbstractLatexManul> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ORANGE_MORPH, false);
    }

    public boolean isOrangeMorph() {
        return this.entityData.get(DATA_ORANGE_MORPH);
    }

    public void setOrangeMorph(boolean orange) {
        this.entityData.set(DATA_ORANGE_MORPH, orange);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_ORANGE_MORPH, isOrangeMorph());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOrangeMorph(tag.getBoolean(TAG_ORANGE_MORPH));
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType reason,
            @Nullable SpawnGroupData spawnData,
            @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        if (tag == null || !tag.contains(TAG_ORANGE_MORPH)) {
            if (level.getRandom().nextInt(ORANGE_MORPH_CHANCE) == 0) {
                setOrangeMorph(true);
            }
        }
        return data;
    }

    @Override
    protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        setBase(attributes, Attributes.MAX_HEALTH, BASE_MAX_HEALTH);
        setBase(attributes, Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        setBase(attributes, Attributes.ARMOR, BASE_ARMOR);
        setBase(attributes, ForgeMod.SWIM_SPEED.get(), BASE_SWIM_SPEED);
        setBase(attributes, ChangedAttributes.AIR_CAPACITY.get(), BASE_AIR_CAPACITY);
        setBase(attributes, ChangedAttributes.JUMP_STRENGTH.get(), BASE_JUMP_STRENGTH);
        setBase(attributes, ChangedAttributes.FALL_RESISTANCE.get(), BASE_FALL_RESISTANCE);
    }

    private static void setBase(AttributeMap attributes, Attribute attribute, double value) {
        AttributeInstance instance = attributes.getInstance(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public int getTicksRequiredToFreeze() {
        return 420;
    }

    @Override
    public Color3 getTransfurColor(TransfurCause cause) {
        return isOrangeMorph() ? Color3.getColor("#C48A4A") : Color3.getColor("#8E8E8E");
    }
}
