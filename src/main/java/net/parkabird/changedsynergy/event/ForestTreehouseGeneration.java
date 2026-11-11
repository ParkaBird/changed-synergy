package net.parkabird.changedsynergy.event;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

/** Finish after neighboring forest features, once, before the new chunk is played in. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class ForestTreehouseGeneration {
    private static final Set<ServerLevel> ACTIVE_LEVELS = ConcurrentHashMap.newKeySet();
    private static final Map<ServerLevel, Set<ChunkPos>> PENDING = new ConcurrentHashMap<>();
    private static final class Repairs extends SavedData {
        final Set<ChunkPos> chunks = ConcurrentHashMap.newKeySet();
        static Repairs load(CompoundTag tag) {
            var data = new Repairs();
            for (long pos : tag.getLongArray("Pending")) data.chunks.add(new ChunkPos(pos));
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("Pending", chunks.stream().mapToLong(ChunkPos::toLong).toArray());
            return tag;
        }
    }

    @SubscribeEvent public static void levelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var repairs = level.getDataStorage().computeIfAbsent(Repairs::load, Repairs::new, "changed_synergy_treehouse_repairs");
            if (!repairs.chunks.isEmpty()) ACTIVE_LEVELS.add(level);
        }
    }

    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk=event.getChunk();
        boolean treehouse=event.isNewChunk() && (chunk.getAllReferences().keySet().stream()
                .anyMatch(s->s instanceof ResearchLabStructure lab && lab.hasClearing())
                || chunk.getAllStarts().keySet().stream().anyMatch(s->s instanceof ResearchLabStructure lab && lab.hasClearing()));
        if(treehouse) {
            PENDING.computeIfAbsent(level, ignored -> ConcurrentHashMap.newKeySet()).add(chunk.getPos());
            ACTIVE_LEVELS.add(level);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !ACTIVE_LEVELS.contains(level)) return;
        var repairs = level.getDataStorage().computeIfAbsent(Repairs::load, Repairs::new, "changed_synergy_treehouse_repairs");
        var queued = PENDING.remove(level);
        if (queued != null && repairs.chunks.addAll(queued)) repairs.setDirty();
        var pending = repairs.chunks;
        if (pending.isEmpty()) {
            ACTIVE_LEVELS.remove(level);
            return;
        }
        int budget = 4;
        var iterator = pending.iterator();
        while (iterator.hasNext() && budget > 0) {
            var pos = iterator.next();
            var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            budget--;
            iterator.remove();
            repairs.setDirty();
            var clip = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                    pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ());
            for (var start : level.structureManager().startsForStructure(pos, s -> s instanceof ResearchLabStructure))
                for (var piece : start.getPieces())
                    if (piece instanceof ResearchLabStructure.Piece lab && lab.hasClearing() && lab.getBoundingBox().intersects(clip))
                        lab.finishLandscape(level, clip);
            chunk.setUnsaved(true);
        }
        if (pending.isEmpty()) ACTIVE_LEVELS.remove(level);
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        ACTIVE_LEVELS.remove(event.getLevel());
        PENDING.remove(event.getLevel());
    }
}