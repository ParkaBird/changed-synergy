package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.ImmediateTransfurDecision;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.entity.ai.NonLatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.event.FactionReputationEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Covers each existing event entry point before and after policy consolidation. */
final class EventPolicyChecks {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "PolicyCheck"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                net.minecraft.server.players.PlayerList.class, "f_11197_");
        var players = (Map<UUID, ServerPlayer>)field.get(server.getPlayerList());
        var white = ChangedEntities.PURE_WHITE_LATEX_WOLF.get().create(level);
        var pet = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        try {
            players.put(player.getUUID(), player); level.addNewPlayer(player);
            player.setPos(level.getSharedSpawnPos().getX() + .5, 130, level.getSharedSpawnPos().getZ() + .5);
            pet.setPos(player.position()); level.addFreshEntity(pet);
            for (int standing : new int[]{100, 0}) {
                FactionReputation.set(white, player, standing);
                for (int kind = 0; kind < 3; kind++) {
                    var event = decision(player, kind, TransfurCause.WHITE_LATEX, 5);
                    protectTerritory(event);
                    check(event.isCanceled() == (standing == 100), "Territory protection, event " + kind + ", standing " + standing);
                    var attack = decision(player, kind, TransfurCause.ATTACK_REPLICATE_LEFT, 5);
                    protectTerritory(attack);
                    check(!attack.isCanceled(), "Reputation does not suppress a non-environmental attack, event " + kind);
                }
            }
            LatexSocialMemory.addBond(pet, player);
            check(BondedSuitService.suitOwner(pet, player, BondedSuitService.SuitReason.MANUAL), "A real bonded suit starts");
            for (int kind = 0; kind < 2; kind++) for (int progress : new int[]{0, 5}) {
                var event = decision(player, kind, TransfurCause.ATTACK_REPLICATE_LEFT, progress);
                if (event instanceof TransfurEvents.LatexAssimilationDecisionEvent latex)
                    LatexSocialEvents.onBondedOwnerTransfurDamage(latex);
                else LatexSocialEvents.onBondedOwnerNonLatexTransfurDamage((TransfurEvents.NonLatexAssimilationDecisionEvent)event);
                check(event.isCanceled() == (progress > 0), "Bonded suit protection, event " + kind + ", progress " + progress);
            }
            System.out.println("EVENT_POLICY_REGRESSION_PASS: " + checks + " checks");
        } finally {
            BondedSuitService.releaseForBondEnd(pet, player);
            pet.discard(); white.discard(); players.remove(player.getUUID()); player.discard();
        }
    }

    private static TransfurEvents.AssimilationDecisionEvent decision(ServerPlayer player, int kind, TransfurCause cause, int progress) {
        var variant = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get();
        return switch (kind) {
            case 0 -> new TransfurEvents.LatexAssimilationDecisionEvent(player,
                    LatexAssimilationDecision.strong(LatexAssimilationDecision.Method.REPLICATION, variant, TransfurContext.hazard(cause), progress));
            case 1 -> new TransfurEvents.NonLatexAssimilationDecisionEvent(player,
                    NonLatexAssimilationDecision.fromBlockOrItem(variant, cause, progress, 0));
            default -> new TransfurEvents.ImmediateTransfurDecisionEvent(player, ImmediateTransfurDecision.unsafe(variant, cause));
        };
    }

    private static void protectTerritory(TransfurEvents.AssimilationDecisionEvent event) {
        if (event instanceof TransfurEvents.LatexAssimilationDecisionEvent latex) FactionReputationEvents.onLatexEnvironmentalHazard(latex);
        else if (event instanceof TransfurEvents.NonLatexAssimilationDecisionEvent other) FactionReputationEvents.onNonLatexEnvironmentalHazard(other);
        else FactionReputationEvents.onImmediateEnvironmentalHazard((TransfurEvents.ImmediateTransfurDecisionEvent)event);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++; System.out.println("EVENT_POLICY_PASS: " + message);
    }
}
