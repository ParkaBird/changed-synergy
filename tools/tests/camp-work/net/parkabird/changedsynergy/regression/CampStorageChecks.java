package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.parkabird.changedsynergy.ai.PlayerOutpostStorageAccess;

/** Checks actual native counters, sound events and lid packets, including a simultaneous human viewer. */
public final class CampStorageChecks {
    private record SavedBlock(BlockState state, CompoundTag data) {}

    public static void run(ServerLevel level, ChangedEntity mob) throws Exception {
        Vec3 previousPosition = mob.position();
        long previousTime = level.getGameTime();
        BlockPos single = mob.blockPosition().offset(0, 0, 8);
        BlockPos barrel = single.east(2);
        BlockPos left = single.south(3);
        BlockPos right = left.east();
        BlockPos replaced = single.south(6);
        Map<BlockPos, SavedBlock> originals = new LinkedHashMap<>();
        for (BlockPos pos : List.of(single, barrel, left, right, replaced)) {
            var entity = level.getBlockEntity(pos);
            originals.put(pos, new SavedBlock(level.getBlockState(pos),
                    entity == null ? null : entity.saveWithoutMetadata()));
        }
        List<SoundEvent> sounds = new ArrayList<>();
        List<String> levelSounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> soundListener = event -> {
            if (event.getLevel() != level || event.getSound() == null) return;
            SoundEvent sound = event.getSound().value();
            levelSounds.add(sound.getLocation() + " @ " + event.getPosition());
            // Other camp checks may still have brief visits: their close sounds are not this fixture's output.
            if (originals.keySet().stream().anyMatch(pos ->
                    event.getPosition().distanceToSqr(Vec3.atCenterOf(pos)) <= 1.0D)) sounds.add(sound);
        };
        MinecraftForge.EVENT_BUS.addListener(soundListener);
        ServerPlayer viewer = null;
        ChangedEntity dying = null;
        Map<UUID, ServerPlayer> playerMap = players(level);
        try {
            level.setBlock(single, Blocks.CHEST.defaultBlockState(), 18);
            var chest = (ChestBlockEntity)level.getBlockEntity(single);
            near(mob, single);
            int opened = count(sounds, SoundEvents.CHEST_OPEN);
            PlayerOutpostStorageAccess.touch(mob, single);
            check(ChestBlockEntity.getOpenCount(level, single) == 1, "NPC joins actual native chest counter");
            check(count(sounds, SoundEvents.CHEST_OPEN) == opened + 1, "Native chest open sound is emitted");
            check(queuedLid(level, single, 1), "Native opening lid event reaches the server packet queue");
            PlayerOutpostStorageAccess.touch(mob, single);
            check(ChestBlockEntity.getOpenCount(level, single) == 1
                    && count(sounds, SoundEvents.CHEST_OPEN) == opened + 1,
                    "Refreshing one NPC visit neither adds a viewer nor repeats the open sound");
            animate(level, single, chest, 1);
            check(chest.getOpenNess(1) > 0.999F, "Native client lid controller animates fully open");
            int closed = count(sounds, SoundEvents.CHEST_CLOSE);
            expire(level);
            System.out.println("CAMP_STORAGE_EXPIRY: native=" + ChestBlockEntity.getOpenCount(level, single)
                    + ", fixtureCloseDelta=" + (count(sounds, SoundEvents.CHEST_CLOSE) - closed)
                    + ", levelSounds=" + levelSounds);
            check(ChestBlockEntity.getOpenCount(level, single) == 0
                    && count(sounds, SoundEvents.CHEST_CLOSE) == closed + 1,
                    "Visit expiry closes the native counter with the native sound; native="
                            + ChestBlockEntity.getOpenCount(level, single) + ", fixtureCloseDelta="
                            + (count(sounds, SoundEvents.CHEST_CLOSE) - closed) + ", levelSounds=" + levelSounds);
            check(queuedLid(level, single, 0), "Native closing lid event reaches the server packet queue");
            animate(level, single, chest, 0);
            check(chest.getOpenNess(1) < 0.001F, "Native client lid controller animates fully closed");
            mob.setPos(single.getX() + 10.5, single.getY(), single.getZ() + 0.5);
            PlayerOutpostStorageAccess.touch(mob, single);
            check(ChestBlockEntity.getOpenCount(level, single) == 0, "Remote inventory reads do not open a distant chest");

            level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 18);
            near(mob, barrel);
            opened = count(sounds, SoundEvents.BARREL_OPEN);
            PlayerOutpostStorageAccess.touch(mob, barrel);
            check(level.getBlockState(barrel).getValue(BarrelBlock.OPEN)
                    && count(sounds, SoundEvents.BARREL_OPEN) == opened + 1,
                    "Native barrel opens its visual state and plays its own sound");
            closed = count(sounds, SoundEvents.BARREL_CLOSE);
            expire(level);
            check(!level.getBlockState(barrel).getValue(BarrelBlock.OPEN)
                    && count(sounds, SoundEvents.BARREL_CLOSE) == closed + 1,
                    "Native barrel closes without leaving OPEN stuck");

            var doubleState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
            level.setBlock(left, doubleState.setValue(ChestBlock.TYPE, ChestType.LEFT), 18);
            level.setBlock(right, doubleState.setValue(ChestBlock.TYPE, ChestType.RIGHT), 18);
            near(mob, left);
            opened = count(sounds, SoundEvents.CHEST_OPEN);
            PlayerOutpostStorageAccess.touch(mob, left);
            check(ChestBlockEntity.getOpenCount(level, left) == 1 && ChestBlockEntity.getOpenCount(level, right) == 1,
                    "Both physical halves of a double chest use native open counters");
            check(count(sounds, SoundEvents.CHEST_OPEN) == opened + 1,
                    "A double chest emits one native open sound instead of two");
            check(queuedLid(level, left, 1) && queuedLid(level, right, 1), "Both double-chest lid events are sent");
            closed = count(sounds, SoundEvents.CHEST_CLOSE);
            expire(level);
            check(ChestBlockEntity.getOpenCount(level, left) == 0 && ChestBlockEntity.getOpenCount(level, right) == 0
                    && count(sounds, SoundEvents.CHEST_CLOSE) == closed + 1,
                    "A double chest closes both halves with one sound");

            viewer = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ChestViewerTest"));
            viewer.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), viewer);
            viewer.setPos(single.getX() + 0.5, single.getY(), single.getZ() + 1.5);
            playerMap.put(viewer.getUUID(), viewer);
            level.addNewPlayer(viewer);
            viewer.containerMenu = ChestMenu.threeRows(1, viewer.getInventory(), chest);
            near(mob, single);
            PlayerOutpostStorageAccess.touch(mob, single);
            check(ChestBlockEntity.getOpenCount(level, single) == 2, "Human and NPC both count as native viewers");
            closed = count(sounds, SoundEvents.CHEST_CLOSE);
            expire(level);
            check(ChestBlockEntity.getOpenCount(level, single) == 1
                    && count(sounds, SoundEvents.CHEST_CLOSE) == closed,
                    "Ending an NPC visit does not close a chest still used by a human");
            viewer.containerMenu.removed(viewer);
            viewer.containerMenu = viewer.inventoryMenu;
            check(ChestBlockEntity.getOpenCount(level, single) == 0
                    && count(sounds, SoundEvents.CHEST_CLOSE) == closed + 1,
                    "The remaining human closes the chest normally");

            level.setBlock(replaced, Blocks.CHEST.defaultBlockState(), 18);
            near(mob, replaced);
            PlayerOutpostStorageAccess.touch(mob, replaced);
            level.setBlock(replaced, Blocks.AIR.defaultBlockState(), 18);
            level.setBlock(replaced, Blocks.CHEST.defaultBlockState(), 18);
            check(PlayerOutpostStorageAccess.openers(level, replaced) == 0,
                    "A replacement container never inherits the removed block entity's viewers");
            tick(level);
            check(ChestBlockEntity.getOpenCount(level, replaced) == 0, "Replacement chest stays closed after cleanup");

            near(mob, single);
            PlayerOutpostStorageAccess.touch(mob, single);
            dying = (ChangedEntity)mob.getType().create(level);
            check(dying != null, "Second real creature exists");
            near(dying, single);
            level.addFreshEntity(dying);
            PlayerOutpostStorageAccess.touch(dying, single);
            check(ChestBlockEntity.getOpenCount(level, single) == 2, "Concurrent creature visits are counted independently");
            dying.setHealth(0);
            tick(level);
            check(ChestBlockEntity.getOpenCount(level, single) == 1, "Dead creature leaves the native opener count promptly");
            mob.setPos(single.getX() + 8.5, single.getY(), single.getZ() + 0.5);
            tick(level);
            check(ChestBlockEntity.getOpenCount(level, single) == 0, "Walking away closes the remaining creature visit");
            System.out.println("CAMP_STORAGE_NATIVE_REGRESSION_PASS");
        } finally {
            if (viewer != null) {
                if (viewer.containerMenu != viewer.inventoryMenu) viewer.containerMenu.removed(viewer);
                viewer.containerMenu = viewer.inventoryMenu;
                playerMap.remove(viewer.getUUID(), viewer);
                viewer.discard();
            }
            if (dying != null) dying.discard();
            mob.setPos(previousPosition);
            expire(level);
            for (var entry : originals.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue().state, 18);
                var blockEntity = level.getBlockEntity(entry.getKey());
                if (blockEntity != null && entry.getValue().data != null) blockEntity.load(entry.getValue().data);
            }
            ((ServerLevelData)level.getLevelData()).setGameTime(previousTime);
            MinecraftForge.EVENT_BUS.unregister(soundListener);
        }
    }

    private static void near(ChangedEntity mob, BlockPos pos) {
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
    }
    private static void expire(ServerLevel level) {
        ((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime() + 20L);
        tick(level);
    }
    private static void tick(ServerLevel level) {
        PlayerOutpostStorageAccess.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true));
    }
    private static int count(List<SoundEvent> sounds, SoundEvent expected) {
        return (int)sounds.stream().filter(sound -> sound == expected).count();
    }
    private static boolean queuedLid(ServerLevel level, BlockPos position, int count) throws Exception {
        Field field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ServerLevel.class, "f_8556_");
        field.setAccessible(true);
        return ((Collection<?>)field.get(level)).stream().anyMatch(event -> event instanceof BlockEventData data
                && data.pos().equals(position) && data.paramA() == 1 && data.paramB() == count);
    }
    private static void animate(ServerLevel level, BlockPos position, ChestBlockEntity chest, int count) {
        check(chest.triggerEvent(1, count), "Native client event accepts the opening count");
        for (int tick = 0; tick < 12; tick++) ChestBlockEntity.lidAnimateTick(level, position, chest.getBlockState(), chest);
    }
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> players(ServerLevel level) throws Exception {
        Field field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class, "f_11197_");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>)field.get(level.getServer().getPlayerList());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
