package net.parkabird.changedsynergy.ai;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.WhiteLatexWolfMale;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.util.PureWhiteVision;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;

/** Reveals the original White-faction bodies' eyes away from home territory. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PureWhiteWolfAdaptation {
    public static final String DATA_KEY =
            "ChangedSynergyPureWhiteWolfAdaptation";
    public static final String PLAYER_DATA_KEY =
            "SynergyPureWhitePlayerAdaptation";
    private static final String ADAPTED = "Adapted";
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
                || creature.getUnderlyingPlayer() != null
                || !isSupportedForm(creature)
                || creature.tickCount % CHECK_INTERVAL_TICKS
                        != Math.floorMod(creature.getId(), CHECK_INTERVAL_TICKS)) {
            return;
        }

        boolean pure = isNativeForm(creature);
        if (TrueTransfurCompat.isInstinctBody(creature)) {
            setAdaptedForm(creature, isAdaptedPlayer(TrueTransfurCompat.bodyOwner(creature)));
            return;
        }
        CompoundTag data = existingData(creature);
        if (!pure && (data == null || !data.getBoolean(ELIGIBLE))) {
            return;
        }
        if (data == null) {
            data = creature.getPersistentData().getCompound(DATA_KEY);
            data.putBoolean(ELIGIBLE, true);
            creature.getPersistentData().put(DATA_KEY, data);
        }

        // Restore only wolves converted by older Synergy versions, retaining their individual identity.
        if (!pure) {
            if (safeToChange(creature)) SafeEntityMutationQueue.queue(creature,
                    "pure_white_wolf_adaptation", () -> restoreLegacyForm(creature));
            return;
        }
        if (!ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.get()
                || !ChangedSynergyGameRules.enabled(level, ChangedSynergyGameRules.CREATURE_LIFE)) {
            data.putBoolean(ADAPTED, false);
            data.remove(AWAY_SINCE);
            setAdaptedForm(creature, false);
            return;
        }

        if (inHomeBiome(creature)) {
            rememberHome(level, creature.blockPosition(), data);
            data.remove(AWAY_SINCE);
            data.putBoolean(ADAPTED, false);
            setAdaptedForm(creature, false);
            return;
        }

        if (data.getBoolean(ADAPTED)) {
            setAdaptedForm(creature, true);
            return;
        }
        if (!farFromRememberedHome(level, creature.blockPosition(), data)) {
            data.remove(AWAY_SINCE);
            return;
        }
        long now = level.getGameTime();
        if (!data.contains(AWAY_SINCE, Tag.TAG_LONG)) {
            data.putLong(AWAY_SINCE, now);
            return;
        }
        if (now - data.getLong(AWAY_SINCE) >= UNANCHORED_GRACE_TICKS) {
            data.putBoolean(ADAPTED, true);
            setAdaptedForm(creature, true);
        }
    }

    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity wolf && isNativeForm(wolf)) {
            CompoundTag data = existingData(wolf);
            setAdaptedForm(wolf, data != null && data.getBoolean(ADAPTED)
                    && ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.get()
                    && ChangedSynergyGameRules.enabled(wolf.level(), ChangedSynergyGameRules.CREATURE_LIFE));
        }
    }

    /** Player adaptation never replaces the real variant or its abilities. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % CHECK_INTERVAL_TICKS
                        != Math.floorMod(player.getId(), CHECK_INTERVAL_TICKS)) {
            return;
        }
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.isTemporaryFromSuit()
                || !PureWhiteVision.isPureWhiteForm(variant)
                || !(variant instanceof PlayerState holder)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity visibleBody = TrueTransfurCompat.interactionBody(player);
        BlockPos position = visibleBody.blockPosition();
        CompoundTag data = holder.changedSynergy$getPureWhiteAdaptation();
        boolean wasAdapted = data.getBoolean(ADAPTED);
        if (!ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.get()
                || !ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.CREATURE_LIFE)) {
            data = new CompoundTag();
        } else if (inHomeTerritory(level, position)) {
            rememberHome(level, position, data);
            data.remove(AWAY_SINCE);
            data.putBoolean(ADAPTED, false);
        } else if (!wasAdapted) {
            if (!farFromRememberedHome(level, position, data)) {
                data.remove(AWAY_SINCE);
            } else {
                long now = level.getGameTime();
                if (!data.contains(AWAY_SINCE, Tag.TAG_LONG)) {
                    data.putLong(AWAY_SINCE, now);
                } else if (now - data.getLong(AWAY_SINCE)
                        >= UNANCHORED_GRACE_TICKS) {
                    data.putBoolean(ADAPTED, true);
                }
            }
        }
        holder.changedSynergy$setPureWhiteAdaptation(data);
        if (visibleBody instanceof ChangedEntity body && visibleBody != player)
            setAdaptedForm(body, data.getBoolean(ADAPTED));
        if (wasAdapted != data.getBoolean(ADAPTED)) {
            Changed.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF
                            .with(() -> player),
                    SyncTransfurPacket.Builder.of(player));
        }
    }

    public static boolean isAdaptedPlayer(@Nullable Player player) {
        return player != null && isAdaptedPlayer(
                ProcessTransfur.getPlayerTransfurVariant(player));
    }

    public static boolean isAdaptedPlayer(
            @Nullable TransfurVariantInstance<?> variant) {
        return variant != null && !variant.isTemporaryFromSuit()
                && PureWhiteVision.isPureWhiteForm(variant)
                && variant instanceof PlayerState holder
                && holder.changedSynergy$getPureWhiteAdaptation()
                        .getBoolean(ADAPTED);
    }

    /** Saved and synchronized inside Changed's existing variant payload. */
    public interface PlayerState {
        CompoundTag changedSynergy$getPureWhiteAdaptation();
        void changedSynergy$setPureWhiteAdaptation(CompoundTag data);
    }

    public interface NpcState {
        boolean changedSynergy$hasAdaptedEyes();
        void changedSynergy$setAdaptedEyes(boolean adapted);
    }

    public static void setAdaptedForm(ChangedEntity creature, boolean adapted) {
        if (isNativeForm(creature) && creature instanceof NpcState state)
            state.changedSynergy$setAdaptedEyes(adapted);
    }

    private static boolean isNativeForm(@Nullable ChangedEntity creature) {
        return creature != null && (creature.getType() == ChangedEntities.PURE_WHITE_LATEX_WOLF.get()
                || creature.getType() == ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get()
                || creature.getType() == ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get()
                || creature.getType() == ChangedEntities.LATEX_MUTANT_BLOODCELL_WOLF.get());
    }

    private static boolean isSupportedForm(ChangedEntity creature) {
        return isNativeForm(creature) || creature.getClass() == WhiteLatexWolfMale.class;
    }

    /** Adaptation adds eyes to the original pure-white body without replacing its species. */
    public static boolean isAdaptedForm(@Nullable ChangedEntity creature) {
        return isNativeForm(creature)
                && (isAdaptedPlayer(creature.getUnderlyingPlayer())
                    || creature instanceof NpcState state && state.changedSynergy$hasAdaptedEyes());
    }

    @Nullable
    private static CompoundTag existingData(ChangedEntity creature) {
        return creature.getPersistentData().contains(DATA_KEY, Tag.TAG_COMPOUND)
                ? creature.getPersistentData().getCompound(DATA_KEY)
                : null;
    }

    private static boolean inHomeBiome(ChangedEntity creature) {
        return inHomeTerritory(creature.level(), creature.blockPosition());
    }

    public static boolean inHomeTerritory(net.minecraft.world.level.Level level, BlockPos position) {
        if (level.getBiome(position).is(LatexTerritoryBiomes.WHITE_LATEX_FOREST)) return true;
        var covers = net.ltxprogrammer.changed.world.LatexCoverGetter.extendDefault(level);
        for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-1, -1, -1), position.offset(1, 1, 1))) {
            if (!level.hasChunkAt(nearby)) continue;
            if (covers.getLatexCover(nearby).is(net.ltxprogrammer.changed.init.ChangedLatexTypes.WHITE_LATEX.get()))
                return true;
            if (level.getBlockState(nearby).getBlock() instanceof net.ltxprogrammer.changed.block.LatexCoveringSource source
                    && source.getLatexCoverState(level.getBlockState(nearby), nearby)
                            .is(net.ltxprogrammer.changed.init.ChangedLatexTypes.WHITE_LATEX.get())) return true;
        }
        return false;
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

    private static void restoreLegacyForm(ChangedEntity source) {
        if (!safeToChange(source)
                || TrueTransfurCompat.isInstinctBody(source)
                || source.getClass() != WhiteLatexWolfMale.class
                || existingData(source) == null || !existingData(source).getBoolean(ELIGIBLE)) {
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

        ChangedEntity replacement = source.convertTo(ChangedEntities.PURE_WHITE_LATEX_WOLF.get(), true);
        if (replacement == null) {
            return;
        }

        replacement.getPersistentData().put(DATA_KEY, adaptation);
        CreatureMorphContinuity.transferForced(source, replacement);
        adaptation = replacement.getPersistentData().getCompound(DATA_KEY);
        boolean adapted = ChangedSynergyConfig.COMMON.pureWhiteWolfAdaptation.get()
                && ChangedSynergyGameRules.enabled(source.level(), ChangedSynergyGameRules.CREATURE_LIFE)
                && !inHomeBiome(replacement);
        adaptation.putBoolean(ADAPTED, adapted);
        setAdaptedForm(replacement, adapted);
        var appearance = new net.ltxprogrammer.changed.entity.BasicPlayerInfo();
        appearance.copyFrom(source.getBasicPlayerInfo());
        replacement.getEntityData().set(net.parkabird.changedsynergy.mixin.ChangedEntityAppearanceAccessor.changedSynergy$appearanceData(), appearance);
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
