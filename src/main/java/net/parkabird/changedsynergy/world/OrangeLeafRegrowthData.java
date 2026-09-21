package net.parkabird.changedsynergy.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Persists only harvested orange leaves until their fruit grows back. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OrangeLeafRegrowthData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_orange_leaf_regrowth";
    private static final ResourceLocation ORANGE_LEAVES =
            ResourceLocation.fromNamespaceAndPath("changed", "orange_tree_leaves");
    private static final long MIN_REGROWTH_TICKS = 12_000L;
    private static final int REGROWTH_VARIANCE_TICKS = 6_001;

    private final Map<Long, PendingLeaf> pendingLeaves = new HashMap<>();

    public static void schedule(
            ServerLevel level,
            BlockPos position,
            BlockState fruitingState,
            BlockState harvestedState) {
        ResourceLocation harvestedBlock = ForgeRegistries.BLOCKS.getKey(
                harvestedState.getBlock());
        if (harvestedBlock == null) {
            return;
        }
        int distance = fruitingState.hasProperty(LeavesBlock.DISTANCE)
                ? fruitingState.getValue(LeavesBlock.DISTANCE) : 7;
        boolean persistent = fruitingState.hasProperty(LeavesBlock.PERSISTENT)
                && fruitingState.getValue(LeavesBlock.PERSISTENT);
        OrangeLeafRegrowthData data = level.getDataStorage().computeIfAbsent(
                OrangeLeafRegrowthData::load,
                OrangeLeafRegrowthData::new,
                DATA_NAME);
        long dueTick = level.getGameTime() + MIN_REGROWTH_TICKS
                + level.getRandom().nextInt(REGROWTH_VARIANCE_TICKS);
        data.pendingLeaves.put(position.asLong(), new PendingLeaf(
                dueTick, harvestedBlock, distance, persistent));
        data.setDirty();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % 20L != 0L) {
            return;
        }
        OrangeLeafRegrowthData data = level.getDataStorage().get(
                OrangeLeafRegrowthData::load,
                DATA_NAME);
        if (data != null) {
            data.regrowReadyLeaves(level);
        }
    }

    private void regrowReadyLeaves(ServerLevel level) {
        Block orangeLeaves = ForgeRegistries.BLOCKS.getValue(ORANGE_LEAVES);
        if (orangeLeaves == null) {
            return;
        }
        boolean changed = false;
        long now = level.getGameTime();
        Iterator<Map.Entry<Long, PendingLeaf>> iterator =
                pendingLeaves.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, PendingLeaf> mapEntry = iterator.next();
            PendingLeaf pending = mapEntry.getValue();
            if (pending.dueTick() > now) {
                continue;
            }
            BlockPos position = BlockPos.of(mapEntry.getKey());
            if (!level.hasChunkAt(position)) {
                continue;
            }
            BlockState current = level.getBlockState(position);
            ResourceLocation currentBlock = ForgeRegistries.BLOCKS.getKey(
                    current.getBlock());
            if (!pending.harvestedBlock().equals(currentBlock)) {
                iterator.remove();
                changed = true;
                continue;
            }
            BlockState fruiting = orangeLeaves.defaultBlockState();
            if (fruiting.hasProperty(LeavesBlock.DISTANCE)) {
                fruiting = fruiting.setValue(LeavesBlock.DISTANCE,
                        Math.max(1, Math.min(7, pending.distance())));
            }
            if (fruiting.hasProperty(LeavesBlock.PERSISTENT)) {
                fruiting = fruiting.setValue(LeavesBlock.PERSISTENT,
                        pending.persistent());
            }
            if (fruiting.hasProperty(BlockStateProperties.WATERLOGGED)
                    && current.hasProperty(BlockStateProperties.WATERLOGGED)) {
                fruiting = fruiting.setValue(BlockStateProperties.WATERLOGGED,
                        current.getValue(BlockStateProperties.WATERLOGGED));
            }
            if (level.setBlock(position, fruiting, Block.UPDATE_ALL)) {
                level.sendParticles(ParticleTypes.COMPOSTER,
                        position.getX() + 0.5D,
                        position.getY() + 0.5D,
                        position.getZ() + 0.5D,
                        4, 0.25D, 0.25D, 0.25D, 0.01D);
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private static OrangeLeafRegrowthData load(CompoundTag tag) {
        OrangeLeafRegrowthData data = new OrangeLeafRegrowthData();
        ListTag entries = tag.getList("Leaves", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation harvestedBlock = ResourceLocation.tryParse(
                    entry.getString("HarvestedBlock"));
            if (harvestedBlock == null) {
                continue;
            }
            data.pendingLeaves.put(entry.getLong("Position"), new PendingLeaf(
                    entry.getLong("DueTick"),
                    harvestedBlock,
                    entry.getInt("Distance"),
                    entry.getBoolean("Persistent")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        pendingLeaves.forEach((position, pending) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Position", position);
            entry.putLong("DueTick", pending.dueTick());
            entry.putString("HarvestedBlock", pending.harvestedBlock().toString());
            entry.putInt("Distance", pending.distance());
            entry.putBoolean("Persistent", pending.persistent());
            entries.add(entry);
        });
        tag.put("Leaves", entries);
        return tag;
    }

    private record PendingLeaf(
            long dueTick,
            ResourceLocation harvestedBlock,
            int distance,
            boolean persistent) {
    }
}
