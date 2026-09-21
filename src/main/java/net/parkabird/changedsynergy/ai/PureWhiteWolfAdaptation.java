package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.PureWhiteLatexWolf;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexWolfMale;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;

/** Lets an adult pure-white wolf grow visible eyes away from its home biome. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PureWhiteWolfAdaptation {
    public static final String DATA_KEY =
            "ChangedSynergyPureWhiteWolfAdaptation";
    private static final String ELIGIBLE = "Eligible";
    private static final String HOME_DIMENSION = "HomeDimension";
    private static final String HOME_POSITION = "HomePosition";
    private static final String AWAY_SINCE = "AwaySince";
    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int DISTANCE_FROM_HOME = 64;
    private static final int UNANCHORED_GRACE_TICKS = 200;

    private PureWhiteWolfAdaptation() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity creature)
                || !(creature.level() instanceof ServerLevel level)
                || !ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.get()
                || !ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.CREATURE_LIFE)
                || !isSupportedForm(creature)
                || creature.tickCount % CHECK_INTERVAL_TICKS
                        != Math.floorMod(creature.getId(), CHECK_INTERVAL_TICKS)) {
            return;
        }

        boolean pure = creature instanceof PureWhiteLatexWolf;
        CompoundTag data = existingData(creature);
        if (!pure && (data == null || !data.getBoolean(ELIGIBLE))) {
            return;
        }
        if (data == null) {
            data = creature.getPersistentData().getCompound(DATA_KEY);
            data.putBoolean(ELIGIBLE, true);
            creature.getPersistentData().put(DATA_KEY, data);
        }

        if (inHomeBiome(creature)) {
            rememberHome(level, creature.blockPosition(), data);
            data.remove(AWAY_SINCE);
            if (!pure && safeToChange(creature)) {
                queueChange(creature, true);
            }
            return;
        }

        if (!pure || !farFromRememberedHome(level, creature.blockPosition(), data)) {
            data.remove(AWAY_SINCE);
            return;
        }
        long now = level.getGameTime();
        if (!data.contains(AWAY_SINCE, Tag.TAG_LONG)) {
            data.putLong(AWAY_SINCE, now);
            return;
        }
        if (now - data.getLong(AWAY_SINCE) >= UNANCHORED_GRACE_TICKS
                && safeToChange(creature)) {
            queueChange(creature, false);
        }
    }

    private static boolean isSupportedForm(ChangedEntity creature) {
        return creature.getClass() == PureWhiteLatexWolf.class
                || creature.getClass() == WhiteLatexWolfMale.class;
    }

    /** True only for an eyed male form that originated as a pure-white wolf. */
    public static boolean isAdaptedForm(@Nullable ChangedEntity creature) {
        CompoundTag data = creature == null ? null : existingData(creature);
        return creature != null
                && creature.getClass() == WhiteLatexWolfMale.class
                && data != null
                && data.getBoolean(ELIGIBLE);
    }

    @Nullable
    private static CompoundTag existingData(ChangedEntity creature) {
        return creature.getPersistentData().contains(DATA_KEY, Tag.TAG_COMPOUND)
                ? creature.getPersistentData().getCompound(DATA_KEY)
                : null;
    }

    private static boolean inHomeBiome(ChangedEntity creature) {
        return creature.level().getBiome(creature.blockPosition())
                .is(LatexTerritoryBiomes.WHITE_LATEX_FOREST);
    }

    private static void rememberHome(
            ServerLevel level,
            BlockPos position,
            CompoundTag data) {
        data.putString(HOME_DIMENSION, level.dimension().location().toString());
        data.putLong(HOME_POSITION, position.asLong());
    }

    /**
     * A remembered home prevents oscillation at a biome edge. A wolf first
     * created outside the forest instead receives the same short grace period.
     */
    private static boolean farFromRememberedHome(
            ServerLevel level,
            BlockPos position,
            CompoundTag data) {
        if (!data.contains(HOME_POSITION, Tag.TAG_LONG)
                || !data.contains(HOME_DIMENSION, Tag.TAG_STRING)) {
            return true;
        }
        ResourceLocation homeDimension = ResourceLocation.tryParse(
                data.getString(HOME_DIMENSION));
        if (!level.dimension().location().equals(homeDimension)) {
            return true;
        }
        BlockPos home = BlockPos.of(data.getLong(HOME_POSITION));
        long dx = (long) position.getX() - home.getX();
        long dz = (long) position.getZ() - home.getZ();
        return dx * dx + dz * dz
                > (long) DISTANCE_FROM_HOME * DISTANCE_FROM_HOME;
    }

    private static boolean safeToChange(ChangedEntity creature) {
        return creature.isAlive()
                && !creature.isRemoved()
                && !creature.isPassenger()
                && !creature.isVehicle()
                && !TakeoverService.carrying(creature);
    }

    private static void queueChange(ChangedEntity creature, boolean toPure) {
        SafeEntityMutationQueue.queue(
                creature,
                "pure_white_wolf_adaptation",
                () -> changeForm(creature, toPure));
    }

    private static void changeForm(ChangedEntity source, boolean toPure) {
        if (!safeToChange(source)
                || inHomeBiome(source) != toPure
                || toPure && source.getClass() != WhiteLatexWolfMale.class
                || !toPure && source.getClass() != PureWhiteLatexWolf.class) {
            return;
        }

        float health = source.getHealth();
        Vec3 movement = source.getDeltaMovement();
        LivingEntity target = source.getTarget();
        boolean aggressive = source.isAggressive();
        boolean sprinting = source.isSprinting();
        int fireTicks = source.getRemainingFireTicks();
        List<MobEffectInstance> effects = source.getActiveEffects().stream()
                .map(MobEffectInstance::new)
                .toList();
        CompoundTag adaptation = source.getPersistentData()
                .getCompound(DATA_KEY).copy();
        CompoundTag accessories = AccessorySlots.getForEntity(source)
                .map(AccessorySlots::save)
                .orElse(null);

        EntityType<? extends ChangedEntity> type = toPure
                ? ChangedEntities.PURE_WHITE_LATEX_WOLF.get()
                : ChangedEntities.WHITE_LATEX_WOLF_MALE.get();
        ChangedEntity replacement = source.convertTo(type, true);
        if (replacement == null) {
            return;
        }

        replacement.getPersistentData().put(DATA_KEY, adaptation);
        CreatureMorphContinuity.transferForced(source, replacement);
        replacement.getBasicPlayerInfo().copyFrom(source.getBasicPlayerInfo());
        if (accessories != null) {
            AccessorySlots.getForEntity(replacement)
                    .ifPresent(slots -> slots.load(accessories));
        }
        replacement.setHealth(Math.min(health, replacement.getMaxHealth()));
        for (MobEffectInstance effect : effects) {
            replacement.addEffect(effect);
        }
        replacement.setDeltaMovement(movement);
        replacement.setRemainingFireTicks(fireTicks);
        replacement.setAggressive(aggressive);
        replacement.setSprinting(sprinting);
        if (target != null && target.isAlive() && !target.isRemoved()) {
            replacement.setTarget(target);
        }
    }
}
