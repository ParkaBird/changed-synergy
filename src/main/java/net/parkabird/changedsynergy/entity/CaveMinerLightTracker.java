package net.parkabird.changedsynergy.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.item.CaveMinerOutfitItem;

/** Maintains transient vanilla LightBlock illumination around wearers with an active Cave Miner headlamp. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class CaveMinerLightTracker {
    private static final int LIGHT_LEVEL = 14;
    private static final Map<UUID, LightRecord> ACTIVE = new ConcurrentHashMap<>();

    private record LightRecord(ResourceKey<Level> dimension, BlockPos pos) {}

    private CaveMinerLightTracker() {}

    public static void syncNow(LivingEntity wearer, boolean active) {
        if (!(wearer.level() instanceof ServerLevel level)) return;
        if (active && isEligibleWearer(wearer)) updateLight(wearer, level);
        else clear(wearer.getUUID(), level.getServer());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!(entity instanceof Player) && !(entity instanceof ChangedEntity)) return;
        UUID id = entity.getUUID();
        boolean tracked = ACTIVE.containsKey(id);
        if (!tracked && (entity.tickCount & 1) != 0) return;
        if (isEligibleWearer(entity) && hasLitOutfit(entity)) {
            if ((entity.tickCount & 1) == 0 || !tracked) updateLight(entity, level);
        } else if (tracked) {
            clear(id, level.getServer());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) clear(event.getEntity().getUUID(), level.getServer());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && ACTIVE.containsKey(event.getEntity().getUUID())) {
            clear(event.getEntity().getUUID(), level.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) clear(event.getEntity().getUUID(), server);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) clear(event.getEntity().getUUID(), server);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (UUID id : ACTIVE.keySet().toArray(UUID[]::new)) {
            clear(id, server);
        }
        ACTIVE.clear();
    }

    private static boolean isEligibleWearer(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved() && !entity.isSpectator();
    }

    private static boolean hasLitOutfit(LivingEntity entity) {
        return AccessorySlots.getForEntity(entity).map(slots ->
                isLitInSlot(slots, ChangedAccessorySlots.BODY.get())
                        || isLitInSlot(slots, ChangedAccessorySlots.FULL_BODY.get())
                        || isLitInSlot(slots, ChangedAccessorySlots.HEAD.get())
                        || isLitInSlot(slots, ChangedAccessorySlots.FACE.get()))
                .orElse(false);
    }

    private static boolean isLitInSlot(AccessorySlots slots, AccessorySlotType slot) {
        if (!slots.hasSlot(slot)) return false;
        ItemStack stack = slots.getItem(slot).orElse(ItemStack.EMPTY);
        return CaveMinerOutfitItem.lit(stack);
    }

    private static void updateLight(LivingEntity entity, ServerLevel level) {
        BlockPos target = findPlaceablePos(entity, level);
        UUID id = entity.getUUID();
        LightRecord prev = ACTIVE.get(id);
        ResourceKey<Level> dim = level.dimension();
        if (target == null) {
            clear(id, level.getServer());
            return;
        }
        if (prev != null && prev.dimension().equals(dim) && prev.pos().equals(target)) {
            ensureLightBlock(level, target);
            return;
        }
        clear(id, level.getServer());
        if (ensureLightBlock(level, target)) {
            ACTIVE.put(id, new LightRecord(dim, target.immutable()));
        }
    }

    private static BlockPos findPlaceablePos(LivingEntity entity, ServerLevel level) {
        BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        BlockPos[] candidates = {
                eye,
                eye.above(),
                entity.blockPosition(),
                eye.relative(entity.getDirection())
        };
        for (BlockPos candidate : candidates) {
            if (!level.isLoaded(candidate) || !level.isInWorldBounds(candidate)) continue;
            BlockState state = level.getBlockState(candidate);
            if (isReplaceableForLight(level, candidate, state)) return candidate;
        }
        return null;
    }

    private static boolean isTrackedPosition(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        for (LightRecord record : ACTIVE.values()) {
            if (record.dimension().equals(dimension) && record.pos().equals(pos)) return true;
        }
        return false;
    }

    private static boolean isReplaceableForLight(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir()
                || (state.is(Blocks.LIGHT) && isTrackedPosition(level.dimension(), pos))
                || (state.is(Blocks.WATER) && state.getFluidState().isSource());
    }

    private static boolean ensureLightBlock(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState current = level.getBlockState(pos);
        if (!isReplaceableForLight(level, pos, current)) return false;
        boolean waterlogged = current.is(Blocks.LIGHT)
                ? current.getValue(BlockStateProperties.WATERLOGGED)
                : current.is(Blocks.WATER);
        if (current.is(Blocks.LIGHT)
                && current.getValue(LightBlock.LEVEL) == LIGHT_LEVEL
                && current.getValue(BlockStateProperties.WATERLOGGED) == waterlogged) {
            return true;
        }
        BlockState placed = Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, LIGHT_LEVEL)
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
        return level.setBlock(pos, placed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void clear(UUID id, MinecraftServer server) {
        LightRecord removed = ACTIVE.remove(id);
        if (removed == null || server == null) return;
        for (LightRecord other : ACTIVE.values()) {
            if (other.equals(removed)) return;
        }
        ServerLevel oldLevel = server.getLevel(removed.dimension());
        if (oldLevel == null || !oldLevel.isLoaded(removed.pos())) return;
        BlockState state = oldLevel.getBlockState(removed.pos());
        if (state.is(Blocks.LIGHT)) {
            BlockState restored = state.getValue(BlockStateProperties.WATERLOGGED)
                    ? Blocks.WATER.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            oldLevel.setBlock(removed.pos(), restored, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
