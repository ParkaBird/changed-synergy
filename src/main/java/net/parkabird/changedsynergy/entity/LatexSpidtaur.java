package net.parkabird.changedsynergy.entity;

import net.ltxprogrammer.changed.entity.AttributePresets;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.ltxprogrammer.changed.entity.beast.AbstractCaveEntity;
import net.ltxprogrammer.changed.entity.beast.LatexTaur;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexKnight;
import net.ltxprogrammer.changed.entity.variant.EntityShape;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.ltxprogrammer.changed.util.Color3;
import net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cave jumping-spider taur: white-latex centaur mount kit, spider-tiger climb/web,
 * bee-like four arms on the form, and feline jump / night vision on the variant.
 */
public class LatexSpidtaur extends WhiteLatexKnight implements LatexTaur<LatexSpidtaur> {
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID =
            SynchedEntityData.defineId(LatexSpidtaur.class, EntityDataSerializers.BYTE);

    public LatexSpidtaur(EntityType<? extends LatexSpidtaur> type, Level level) {
        super(type, level);
    }

    @Override
    protected void setAttributes(AttributeMap attributes) {
        super.setAttributes(attributes);
        AttributePresets.catLike(attributes);
        attributes.getInstance(Attributes.MOVEMENT_SPEED).setBaseValue(1.2D);
        attributes.getInstance(ForgeMod.SWIM_SPEED.get()).setBaseValue(0.9D);
        attributes.getInstance(Attributes.MAX_HEALTH).setBaseValue(30.0D);
        attributes.getInstance(ForgeMod.STEP_HEIGHT_ADDITION.get())
                .setBaseValue(computeStepHeightOffset(1.1D));
        attributes.getInstance(ChangedAttributes.JUMP_STRENGTH.get()).setBaseValue(1.25D);
        attributes.getInstance(ChangedAttributes.FALL_RESISTANCE.get()).setBaseValue(2.5D);
    }

    @Override
    public TransfurMode getTransfurMode() {
        return TransfurMode.REPLICATION;
    }

    /**
     * WhiteLatexKnight redirects absorption/replication to the white latex centaur form.
     * Keep this species on its own Synergy variant instead.
     */
    @Override
    protected TransfurVariant<?> getTransfurVariant() {
        return ChangedSynergyTransfurVariants.LATEX_SPIDTAUR.get();
    }

    @Override
    public Color3 getTransfurColor(TransfurCause cause) {
        return Color3.getColor("#5a534c");
    }

    @Override
    public boolean isSaddleable() {
        return false;
    }

    @Override
    public void equipSaddle(@Nullable SoundSource source) {
        equipSaddle(this, source);
    }

    @Override
    public boolean isSaddled() {
        return isSaddled(this);
    }

    protected void doPlayerRide(Player player) {
        doPlayerRide(this, player);
    }

    @Override
    public double getPassengersRidingOffset() {
        return super.getPassengersRidingOffset() + getTorsoYOffset(this) - 0.125D;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isSaddled()) {
            doPlayerRide(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isAllowedToSwim() {
        return true;
    }

    @NotNull
    @Override
    public EntityShape getEntityShape() {
        return EntityShape.TAUR;
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    public int getExperienceReward() {
        return 240;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            setClimbing(horizontalCollision);
        }
    }

    @Override
    public boolean onClimbable() {
        return isClimbing();
    }

    public boolean isClimbing() {
        return (entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setClimbing(boolean climbing) {
        byte flags = entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, climbing ? (byte) (flags | 1) : (byte) (flags & 0xFE));
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    public static <T extends ChangedEntity> boolean checkSpidtaurSpawnRules(
            EntityType<T> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            net.minecraft.core.BlockPos pos,
            RandomSource random) {
        return AbstractCaveEntity.checkEntitySpawnRules(type, level, spawnType, pos, random);
    }
}
