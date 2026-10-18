package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.origin.NativeOriginData;

final class OrganicSleepReversalRegression {
    static void run(MinecraftServer server) {
        var config = ChangedSynergyConfig.COMMON;
        boolean organic = config.organicSleepReversal.get(), ordinary = config.ordinaryTransfurReversal.get();
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "OrganicSleep"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var species = ChangedEntities.GAS_WOLF_MALE.get().create(server.overworld()).getSelfVariant();
        var identity = new NativeOriginData.Identity(); identity.state = "human";
        var origins = NativeOriginData.get(server); origins.players.put(player.getUUID(), identity);
        try {
            if (!config.organicSleepReversal.getDefault()) throw new AssertionError("Existing worlds must retain sleep reversal by default");
            config.ordinaryTransfurReversal.set(true);
            var form = ProcessTransfur.setPlayerTransfurVariant(player, species, null, 1F, true);
            set(server, false);
            if (InvoluntaryTransfurNegotiation.releaseOrganicAfterSleep(player)
                    || ProcessTransfur.getPlayerTransfurVariant(player) != form
                    || !InvoluntaryTransfurNegotiation.ordinaryReversalEnabled())
                throw new AssertionError("Disabling sleep reversal changed the body or disabled other reversal paths");
            set(server, true);
            if (!InvoluntaryTransfurNegotiation.releaseOrganicAfterSleep(player) || ProcessTransfur.isPlayerTransfurred(player))
                throw new AssertionError("Re-enabling sleep reversal did not restore the existing organic release");
            form = ProcessTransfur.setPlayerTransfurVariant(player, species, null, 1F, true);
            config.ordinaryTransfurReversal.set(false);
            if (InvoluntaryTransfurNegotiation.releaseOrganicAfterSleep(player) || ProcessTransfur.getPlayerTransfurVariant(player) != form)
                throw new AssertionError("The organic switch bypassed the general reversal setting");
            config.ordinaryTransfurReversal.set(true);
            identity.state = "native";
            if (InvoluntaryTransfurNegotiation.releaseOrganicAfterSleep(player) || ProcessTransfur.getPlayerTransfurVariant(player) != form)
                throw new AssertionError("Sleep reversal bypassed native-origin protection");
            System.out.println("ORGANIC_SLEEP_REVERSAL_CONFIG_PASS: default, operator command, disabled/enabled body outcome, general gate and native protection");
        } finally {
            config.organicSleepReversal.set(organic); config.ordinaryTransfurReversal.set(ordinary);
            origins.players.remove(player.getUUID()); player.discard();
        }
    }

    private static void set(MinecraftServer server, boolean enabled) {
        if (server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
                "changedsynergy config set RELATIONSHIPS.OrganicSleepReversal " + enabled) != 1)
            throw new AssertionError("The operator cannot configure organic sleep reversal");
    }
}
