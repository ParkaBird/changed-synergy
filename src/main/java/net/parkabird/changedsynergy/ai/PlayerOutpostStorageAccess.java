package net.parkabird.changedsynergy.ai;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Brief real storage visits join the native opener count, alongside human viewers. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class PlayerOutpostStorageAccess {
    private static final Map<ServerLevel, Map<BlockPos, Visit>> VISITS = new WeakHashMap<>();
    private record Actor(BlockPos origin, long until) {}
    private record Visit(WeakReference<BlockEntity> container, Map<UUID, Actor> actors) {}

    private PlayerOutpostStorageAccess() {}

    public static void touch(ChangedEntity creature, BlockPos position) {
        if (!(creature.level() instanceof ServerLevel level) || position == null || !creature.isAlive()
                || !level.hasChunkAt(position) || creature.distanceToSqr(Vec3.atCenterOf(position)) > 16.0D) return;
        BlockState state = level.getBlockState(position);
        register(level, position, creature, position);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return;
        BlockPos other = position.relative(ChestBlock.getConnectedDirection(state));
        if (!level.hasChunkAt(other)) return;
        BlockState counterpart = level.getBlockState(other);
        if (counterpart.getBlock() == state.getBlock()
                && counterpart.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
                && counterpart.getValue(ChestBlock.TYPE) == (state.getValue(ChestBlock.TYPE) == ChestType.LEFT
                        ? ChestType.RIGHT : ChestType.LEFT)) register(level, other, creature, position);
    }

    private static void register(ServerLevel level, BlockPos position, ChangedEntity creature, BlockPos origin) {
        BlockEntity container = level.getBlockEntity(position);
        if (!(container instanceof ChestBlockEntity) && !(container instanceof BarrelBlockEntity)) return;
        var visits = VISITS.computeIfAbsent(level, ignored -> new HashMap<>());
        Visit visit = visits.get(position);
        if (visit == null || visit.container.get() != container) {
            visit = new Visit(new WeakReference<>(container), new HashMap<>());
            visits.put(position.immutable(), visit);
        }
        visit.actors.put(creature.getUUID(), new Actor(origin.immutable(), level.getGameTime() + 20L));
        recheck(container);
    }

    public static int openers(Level level, BlockPos position) {
        if (!(level instanceof ServerLevel server)) return 0;
        var visits = VISITS.get(server);
        Visit visit = visits == null ? null : visits.get(position);
        return visit == null || !level.hasChunkAt(position)
                || visit.container.get() != level.getBlockEntity(position) ? 0 : (int)visit.actors.values().stream()
                .filter(actor -> actor.until > level.getGameTime()).count();
    }

    private static void recheck(BlockEntity container) {
        if (container instanceof ChestBlockEntity chest) chest.recheckOpen();
        else if (container instanceof BarrelBlockEntity barrel) barrel.recheckOpen();
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var visits = VISITS.get(level);
        if (visits == null) return;
        visits.entrySet().removeIf(entry -> {
            BlockEntity container = entry.getValue().container.get();
            if (!level.hasChunkAt(entry.getKey()) || container == null
                    || container != level.getBlockEntity(entry.getKey())) return true;
            int before = entry.getValue().actors.size();
            entry.getValue().actors.entrySet().removeIf(actor -> actor.getValue().until <= level.getGameTime()
                    || !(level.getEntity(actor.getKey()) instanceof ChangedEntity creature) || !creature.isAlive()
                    || creature.distanceToSqr(Vec3.atCenterOf(actor.getValue().origin)) > 25.0D);
            if (entry.getValue().actors.size() != before) recheck(container);
            return entry.getValue().actors.isEmpty();
        });
        if (visits.isEmpty()) VISITS.remove(level);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var visits = VISITS.remove(level);
            if (visits != null) for (var entry : visits.entrySet()) {
                BlockEntity container = entry.getValue().container.get();
                if (level.hasChunkAt(entry.getKey()) && container != null
                        && container == level.getBlockEntity(entry.getKey())) recheck(container);
            }
        }
    }
}
