package net.parkabird.changedsynergy.mixin;

import java.util.function.Consumer;
import net.ltxprogrammer.changed.entity.latex.SpreadingLatexType;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Diversifies the extra creatures produced directly by white-latex terrain.
 * Without this, those guaranteed wolves fill the local population while the
 * biome's rarer entries rarely receive a natural-spawn opportunity. */
@Mixin(value = SpreadingLatexType.WhiteLatex.class, remap = false)
public abstract class WhiteLatexTerritorySpawnMixin {
    @Redirect(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;"),
            require = 0,
            remap = false)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Entity changedSynergy$useMappedTerritoryPopulation(
            EntityType original,
            ServerLevel level,
            CompoundTag tag,
            Consumer consumer,
            BlockPos position,
            MobSpawnType spawnType,
            boolean alignPosition,
            boolean invertY) {
        return changedSynergy$spawnTerritoryPopulation(
                original, level, tag, consumer, position, spawnType,
                alignPosition, invertY);
    }

    @Redirect(
            method = "randomTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;m_262455_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;"),
            require = 0,
            remap = false)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Entity changedSynergy$useSrgTerritoryPopulation(
            EntityType original,
            ServerLevel level,
            CompoundTag tag,
            Consumer consumer,
            BlockPos position,
            MobSpawnType spawnType,
            boolean alignPosition,
            boolean invertY) {
        return changedSynergy$spawnTerritoryPopulation(
                original, level, tag, consumer, position, spawnType,
                alignPosition, invertY);
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Entity changedSynergy$spawnTerritoryPopulation(
            EntityType original,
            ServerLevel level,
            CompoundTag tag,
            Consumer consumer,
            BlockPos position,
            MobSpawnType spawnType,
            boolean alignPosition,
            boolean invertY) {
        if (!level.getBiome(position).is(
                LatexTerritoryBiomes.WHITE_LATEX_FOREST)) {
            return original.spawn(
                    level, tag, consumer, position, spawnType,
                    alignPosition, invertY);
        }
        int roll = level.random.nextInt(24);
        EntityType selected = roll < 11
                ? ChangedEntities.PURE_WHITE_LATEX_WOLF.get()
                : roll < 17
                        ? ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get()
                        : roll < 21
                                ? ChangedEntities.LATEX_MUTANT_BLOODCELL_WOLF.get()
                                : ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get();
        return selected.spawn(
                level, tag, consumer, position, spawnType,
                alignPosition, invertY);
    }
}
