package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.ImmediateTransfurDecision;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.entity.ai.NonLatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.ai.TakeoverSession;

/** Real exposure decisions and block collisions against a genuine temporary takeover suit. */
final class TakeoverHazardRegression {
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "HazardCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var players = (Map<UUID, ServerPlayer>)ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_")
                .get(server.getPlayerList());
        var carrier = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var base = server.getPlayerList().getPlayers().get(0).blockPosition().offset(80, 0, 80).atY(90);
        var config = ChangedSynergyConfig.COMMON;
        var oldMethod = config.creatureTransfurMethod.get();
        boolean oldPunitive = config.takeoverPunitive.get(), oldFinale = config.takeoverHostileFinale.get();
        boolean oldRescue = config.bondedEmergencyRescue.get();
        try {
            players.put(player.getUUID(), player);
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-6, -1, -6), base.offset(6, 2, 6)))
                level.setBlockAndUpdate(pos, pos.getY() == base.getY() - 1
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
            carrier.setPos(player.getX() + 1.5, player.getY(), player.getZ());
            level.addFreshEntity(carrier);
            config.creatureTransfurMethod.set(ChangedSynergyConfig.CreatureTransfurMethod.TAKEOVER);
            config.takeoverPunitive.set(true); config.takeoverHostileFinale.set(true);
            config.bondedEmergencyRescue.set(false);
            FactionReputation.set(carrier, player, 0);
            check(TakeoverService.authorizeOrdinary(carrier, player, InvoluntaryTransfurNegotiation.Reason.HOST_SEEKING)
                    && TakeoverService.beginOrdinary(carrier, player), "Ordinary unbonded takeover starts");
            var entries = TakeoverService.class.getDeclaredField("ACTIVE"); entries.setAccessible(true);
            var entry = ((Map<UUID, ?>)entries.get(null)).get(player.getUUID());
            var field = entry.getClass().getDeclaredField("session"); field.setAccessible(true);
            var session = (TakeoverSession)field.get(entry);
            var suit = ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow();
            check(suit.isTemporaryFromSuit(), "Capture uses Changed's real temporary form");
            for (int phase = 0; phase < 2; phase++) {
                if (phase == 1) {
                    ((ServerLevelData)level.getLevelData()).setGameTime(session.getNextBorrowAt());
                    check(session.requestBorrow(level.getGameTime(), TakeoverSession.Blocker.NONE)
                            == TakeoverSession.Result.ACCEPTED, "Borrowed control starts normally");
                    check(!TakeoverService.hidesPlayer(player) && TakeoverService.active(player),
                            "Borrowed-control protection does not depend on hiding the host");
                }
                for (var cause : new TransfurCause[]{TransfurCause.LATEX_PUDDLE,
                        TransfurCause.LATEX_SYRINGE_FLOOR, TransfurCause.WHITE_LATEX, TransfurCause.CRYSTAL}) {
                    for (int kind = 0; kind < 3; kind++) {
                        var event = decision(player, cause, kind);
                        Changed.postModEvent(event);
                        check(event.isCanceled(), "Protected exposure route " + kind + ", " + cause + ", phase " + phase);
                    }
                }
                for (var block : new net.minecraft.world.level.block.Block[]{ChangedBlocks.WHITE_LATEX_PUDDLE_MALE.get(),
                        ChangedBlocks.DROPPED_SYRINGE.get()}) {
                    level.setBlockAndUpdate(base, block.defaultBlockState());
                    if (level.getBlockEntity(base) instanceof net.ltxprogrammer.changed.block.entity.DroppedSyringeBlockEntity syringe)
                        syringe.setVariant(ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get());
                    float progress = ProcessTransfur.getPlayerTransfurProgress(player);
                    block.entityInside(level.getBlockState(base), level, base, player);
                    check(ProcessTransfur.getPlayerTransfurProgress(player) == progress
                                    && ProcessTransfur.getPlayerTransfurVariant(player) == suit
                                    && TakeoverService.active(player) && level.getBlockState(base).is(block),
                            "Real " + block + " collision preserves suit, progress, session and hazard");
                    level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
                }
                var scripted = decision(player, TransfurCause.GRAB_ABSORB, 2);
                Changed.postModEvent(scripted);
                check(!scripted.isCanceled(), "Capture protection leaves scripted non-environmental conversion available");
            }
            check(TakeoverService.forceReleaseByCommand(player), "Protected suit can still be released");
            check(!TakeoverService.active(player) && !TakeoverService.blocksPlayerInput(player)
                    && ProcessTransfur.getPlayerTransfurVariant(player) == null, "Release restores the original human normally");
            for (int kind = 0; kind < 3; kind++) {
                var exposed = decision(player, TransfurCause.LATEX_SYRINGE_FLOOR, kind);
                Changed.postModEvent(exposed);
                check(!exposed.isCanceled(), "Exposure resumes after release, route " + kind);
            }
            carrier.setPos(player.getX() + 1.5, player.getY(), player.getZ());
            FactionReputation.set(carrier, player, -20);
            check(FactionReputation.standing(carrier, player) == FactionReputation.Standing.DISTRUSTED,
                    "Punitive transfur fixture has Distrusted standing");
            check(TakeoverService.authorizeOrdinary(carrier, player, InvoluntaryTransfurNegotiation.Reason.SELF_DEFENSE)
                    && TakeoverService.beginOrdinary(carrier, player), "Punitive takeover can still start");
            entry = ((Map<UUID, ?>)entries.get(null)).get(player.getUUID());
            session = (TakeoverSession)field.get(entry);
            check(session.getSleepOutcome() == TakeoverSession.SleepOutcome.TRANSFUR, "Original permanent outcome remains selected");
            var expected = carrier.getSelfVariant();
            ((ServerLevelData)level.getLevelData()).setGameTime(session.getDeadline() + TakeoverSession.SLEEP_TICKS + 1);
            TakeoverService.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            var result = ProcessTransfur.getPlayerTransfurVariantSafe(player).orElseThrow();
            check(!TakeoverService.active(player) && result.getParent() == expected && !result.isTemporaryFromSuit(),
                    "The genuine takeover finale still completes its permanent form");
            System.out.println("TAKEOVER_ENVIRONMENTAL_PROTECTION_PASS");
        } finally {
            TakeoverService.forceReleaseByCommand(player);
            config.creatureTransfurMethod.set(oldMethod); config.takeoverPunitive.set(oldPunitive);
            config.takeoverHostileFinale.set(oldFinale); config.bondedEmergencyRescue.set(oldRescue);
            carrier.discard(); players.remove(player.getUUID()); player.discard();
        }
    }

    private static TransfurEvents.AssimilationDecisionEvent decision(ServerPlayer player, TransfurCause cause, int kind) {
        var form = ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get();
        return switch (kind) {
            case 0 -> new TransfurEvents.LatexAssimilationDecisionEvent(player, LatexAssimilationDecision.strong(
                    LatexAssimilationDecision.Method.REPLICATION, form, TransfurContext.hazard(cause), 6));
            case 1 -> new TransfurEvents.NonLatexAssimilationDecisionEvent(player,
                    NonLatexAssimilationDecision.fromBlockOrItem(form, cause, 6, 0));
            default -> new TransfurEvents.ImmediateTransfurDecisionEvent(player, ImmediateTransfurDecision.unsafe(form, cause));
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("TAKEOVER_HAZARD_CHECK: " + message);
    }
}
