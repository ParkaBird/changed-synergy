package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Optional idle-group measurement in the private regression world, using real server ticks. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class ServerLoadRegression {
    private static final int ENTITY_COUNT = 64;
    private static final int WARMUP = 60;
    private static final int SAMPLES = 200;
    private static Run active;

    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final List<ChangedEntity> creatures = new ArrayList<>();
        final long[] nanos = new long[SAMPLES];
        final int startTick;
        final boolean nativeOrigins;
        final boolean mobSpawning;
        final long dayTime;
        ServerPlayer observer;
        long tickStart;
        int elapsed;

        Run(MinecraftServer server) {
            this.server = server;
            this.level = server.overworld();
            startTick = server.getTickCount();
            nativeOrigins = level.getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS);
            mobSpawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            dayTime = level.getDayTime();
        }

        void cleanup() {
            for (ChangedEntity creature : creatures) creature.discard();
            if (observer != null) server.getPlayerList().remove(observer);
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(nativeOrigins, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(mobSpawning, server);
            level.setDayTime(dayTime);
        }
    }

    @SuppressWarnings("unchecked")
    public static void start(MinecraftServer server) throws ReflectiveOperationException {
        if (active != null) throw new IllegalStateException("Load measurement already running");
        Run run = new Run(server);
        active = run;
        try {
            var level = run.level;
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(6000);
            level.updateSkyBrightness();
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos center = new BlockPos((spawn.getX() >> 4) * 16 + 8, 180,
                    (spawn.getZ() >> 4) * 16 + 8);
            // A bounded local arena is prepared before timing; it never touches a user world.
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                BlockPos floor = center.offset(x, -1, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
                if (Math.abs(x) == 16 || Math.abs(z) == 16) {
                    level.setBlock(floor.above(), Blocks.GLASS.defaultBlockState(), 2);
                    level.setBlock(floor.above(2), Blocks.GLASS.defaultBlockState(), 2);
                    level.setBlock(floor.above(3), Blocks.GLASS.defaultBlockState(), 2);
                }
            }
            run.observer = new ServerPlayer(server, level,
                    new GameProfile(UUID.randomUUID(), "SynergyLoad"));
            run.observer.connection = new ServerGamePacketListenerImpl(server,
                    new Connection(PacketFlow.SERVERBOUND), run.observer);
            run.observer.setPos(center.getX() + 0.5, center.getY() + 2, center.getZ() + 0.5);
            run.observer.setGameMode(GameType.SPECTATOR);
            // Register a real player in both indexes and the level, so normal ticking/tracking remains active.
            var mapField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_");
            mapField.setAccessible(true);
            ((Map<UUID, ServerPlayer>)mapField.get(server.getPlayerList()))
                    .put(run.observer.getUUID(), run.observer);
            var listField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_");
            listField.setAccessible(true);
            ((List<ServerPlayer>)listField.get(server.getPlayerList())).add(run.observer);
            level.addNewPlayer(run.observer);
            for (int i = 0; i < ENTITY_COUNT; i++) {
                ChangedEntity creature = (i % 2 == 0 ? ChangedEntities.DARK_LATEX_WOLF_MALE.get()
                        : ChangedEntities.DARK_LATEX_WOLF_PUP.get()).create(level);
                if (creature == null) throw new AssertionError("Dark latex entity factory returned null");
                creature.setPos(center.getX() - 10.5 + (i % 8) * 3,
                        center.getY(), center.getZ() - 10.5 + (i / 8) * 3);
                creature.setPersistenceRequired();
                run.creatures.add(creature);
                if (!level.addFreshEntity(creature)) throw new AssertionError("Load entity not added");
            }
            System.out.println("SERVER_LOAD_REGRESSION_BEGIN: idle_dark_group entities=" + ENTITY_COUNT
                    + " warmup=" + WARMUP + " samples=" + SAMPLES
                    + "; not a measurement of an entire 120-mod pack");
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            active = null;
            run.cleanup();
            throw failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginTick(TickEvent.ServerTickEvent event) {
        if (active != null && event.phase == TickEvent.Phase.START && event.getServer() == active.server)
            active.tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void endTick(TickEvent.ServerTickEvent event) {
        Run run = active;
        if (run == null || event.phase != TickEvent.Phase.END || event.getServer() != run.server) return;
        long duration = System.nanoTime() - run.tickStart;
        if (run.elapsed >= WARMUP) run.nanos[run.elapsed - WARMUP] = duration;
        if (++run.elapsed != WARMUP + SAMPLES) return;
        active = null;
        try {
            int serverTicks = run.server.getTickCount() - run.startTick;
            int minimumEntityTicks = run.creatures.stream().mapToInt(entity -> entity.tickCount).min().orElse(0);
            if (serverTicks < WARMUP + SAMPLES || minimumEntityTicks < SAMPLES
                    || run.observer.tickCount < SAMPLES
                    || run.creatures.stream().anyMatch(entity -> !entity.isAlive()))
                throw new AssertionError("Observer/group did not remain alive and actually ticking: server="
                        + serverTicks + " entityMin=" + minimumEntityTicks + " observer=" + run.observer.tickCount);
            long[] ordered = run.nanos.clone();
            Arrays.sort(ordered);
            double mean = Arrays.stream(run.nanos).average().orElseThrow() / 1_000_000.0;
            double p95 = ordered[(int)Math.ceil(SAMPLES * 0.95) - 1] / 1_000_000.0;
            double max = ordered[SAMPLES - 1] / 1_000_000.0;
            System.out.printf(Locale.ROOT,
                    "SERVER_LOAD_REGRESSION_PASS: idle_dark_group entities=%d serverTicks=%d entityMinTicks=%d"
                            + " observerTicks=%d samples=%d meanMs=%.3f p95Ms=%.3f maxMs=%.3f%n",
                    ENTITY_COUNT, serverTicks, minimumEntityTicks, run.observer.tickCount, SAMPLES, mean, p95, max);
        } catch (Throwable failure) {
            System.err.println("SERVER_LOAD_REGRESSION_FAIL");
            failure.printStackTrace();
        } finally {
            try { run.cleanup(); }
            finally { run.server.halt(false); }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Run run = active;
        if (run == null || run.server != event.getServer()) return;
        active = null;
        System.err.println("SERVER_LOAD_REGRESSION_FAIL: server stopped before actual tick sampling completed");
        run.cleanup();
    }
}
