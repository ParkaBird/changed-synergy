package net.parkabird.changedsynergy.origin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Real Changed command and player tick lifecycle, exceeding the reported 13-second delay. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class TrueTransfurCommandStateRegression {
    private static final int TICKS_PER_STAGE = 420;
    private static MinecraftServer server;
    private static List<ServerPlayer> players = List.of();
    private static Method backendVariant, backendActive;
    private static boolean oldOrigins, oldSpawning, running;
    private static int stage, ticks, checks;
    private static int[] initialTicks;

    static void start(MinecraftServer instance) {
        server = instance;
        oldOrigins = server.overworld().getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS);
        oldSpawning = server.overworld().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            Class<?> controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
            backendVariant = controller.getMethod("getVariant", ServerPlayer.class);
            backendActive = controller.getMethod("isTrueTransfurActive", ServerPlayer.class);
            server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (BlockPos floor : BlockPos.betweenClosed(-5, 179, -5, 5, 179, 5))
                server.overworld().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            System.out.println("TT_COMMAND_ENVIRONMENT: " + ModList.get().getMods().stream()
                    .map(mod -> mod.getModId() + "=" + mod.getVersion()).sorted().toList());
            beginStage();
            running = true;
        } catch (Throwable failure) { finish(failure); }
    }

    private static void beginStage() throws Exception {
        boolean origins = stage == 1;
        server.overworld().getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(origins, server);
        players = List.of(TrueTransfurReleaseRegression.player(server, "CommandHuman" + stage),
                TrueTransfurReleaseRegression.player(server, "CommandNative" + stage));
        initialTicks = new int[players.size()];
        ticks = 0;
        for (int index = 0; index < players.size(); index++) {
            ServerPlayer player = players.get(index);
            TrueTransfurReleaseRegression.identity(player, index == 1);
            player.setPos(.5 + index * 2, 180, .5);
            initialTicks[index] = player.tickCount;
            require(ProcessTransfur.getPlayerTransfurVariantSafe(player).isEmpty(), "Fresh untransformed player");
            int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "transfur " + player.getGameProfile().getName() + " changed:form_dark_latex_wolf/male");
            require(result == 1, "Original Changed command succeeds");
            checkForm(player);
        }
        System.out.println("TT_COMMAND_STAGE: origins=" + origins + "; testing " + TICKS_PER_STAGE + " actual player ticks");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (!running) return;
        try {
            if (event.phase == TickEvent.Phase.START) {
                // These test players have no socket. Drive the normal packet listener's player
                // lifecycle, which posts real Forge START/END events to every installed mod.
                for (ServerPlayer player : players) player.connection.tick();
                return;
            }
            ticks++;
            for (ServerPlayer player : players) checkForm(player);
            if (ticks == 1 || ticks == 260 || ticks == TICKS_PER_STAGE) {
                for (int index = 0; index < players.size(); index++) {
                    ServerPlayer player = players.get(index);
                    require(player.tickCount - initialTicks[index] >= ticks, "Player lifecycle advanced every server tick");
                    Optional<?> reported = (Optional<?>) backendVariant.invoke(null, player);
                    require(reported.orElse(null) == ProcessTransfur.getPlayerTransfurVariant(player),
                            "Backend reads the original Changed variant instance");
                    boolean active = (Boolean) backendActive.invoke(null, player);
                    require(active == (stage == 0 || index == 1), "Permanent identity follows the origin policy");
                    System.out.println("TT_COMMAND_SAMPLE: origins=" + (stage == 1) + " player="
                            + player.getGameProfile().getName() + " elapsedTicks=" + ticks + " playerTicks="
                            + (player.tickCount - initialTicks[index]) + " rawOptional=present backendOptional=present"
                            + " permanentActive=" + active + " progress=" + reported.map(value ->
                                    ((net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance<?>) value).transfurProgression).orElse(-1f));
                }
            }
            if (ticks == TICKS_PER_STAGE) {
                removePlayers();
                if (stage++ == 0) beginStage();
                else finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static void checkForm(ServerPlayer player) {
        var variant = ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow(() ->
                new AssertionError("Raw Changed Optional became empty: stage=" + stage + " ticks=" + ticks
                        + " player=" + player.getGameProfile().getName()));
        require(variant.getParent() == ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), "Command form retained");
        require(!variant.isTemporaryFromSuit(), "Command transfur does not become a temporary suit");
        require(player.isAlive(), "Command-transformed player stays alive");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + "; stage=" + stage + "; ticks=" + ticks);
        checks++;
    }

    private static void removePlayers() {
        for (ServerPlayer player : players) server.getPlayerList().remove(player);
        players = List.of();
    }

    private static void finish(Throwable failure) {
        running = false;
        if (failure != null) {
            System.err.println("TRUETRANSFUR_RELEASE_REGRESSION_FAIL");
            failure.printStackTrace();
        } else {
            System.out.println("TRUETRANSFUR_RELEASE_REGRESSION_PASS: " + checks + " checks; backend="
                    + ModList.get().getModContainerById("true_transfur").orElseThrow().getModInfo().getVersion()
                    + "; command-state; 4 players x 420 ticks");
        }
        try {
            removePlayers();
            server.overworld().getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(oldOrigins, server);
            server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(oldSpawning, server);
        } finally { server.halt(false); }
    }
}
