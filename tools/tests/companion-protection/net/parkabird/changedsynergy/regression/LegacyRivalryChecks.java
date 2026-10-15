package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** The hotfix retains the pre-rework rivalry without removing personal relationship protection. */
final class LegacyRivalryChecks {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var white = ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get().getEntityType().create(level);
        var dark = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(level);
        level.addFreshEntity(white); level.addFreshEntity(dark);
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "RivalGuest"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        var identity = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();
        identity.state = "human";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(), identity);
        int grace = ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.get();
        try {
            ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.set(0);
            check(LatexCreatureCombatRules.areRivals(white, dark)
                    && LatexCreatureCombatRules.areRivals(dark, white), "Original White/Dark rivalry is retained");
            check(!LatexCreatureCombatRules.mustRejectTarget(white, dark)
                    && !LatexCreatureCombatRules.mustRejectTarget(dark, white), "No automatic faction ceasefire remains");
            check(white.canAttack(dark) && dark.canAttack(white), "Native enemy acquisition remains available both ways");
            white.setTarget(dark); dark.setTarget(white);
            check(white.getTarget() == dark && dark.getTarget() == white, "Target guards do not silently enforce the removed ceasefire");
            ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1F, false);
            FactionReputation.set(white, player, 0);
            check(HunterRelation.between(white, player) == HunterRelation.RIVAL
                    && LatexSocialRelation.between(white, player) == LatexSocialRelation.RIVAL,
                    "Opposing player forms use the original rival relation");
            LatexSocialMemory.addBond(white, player);
            check(LatexSocialMemory.isBonded(white, player)
                    && LatexSocialMemory.shouldRemainNeutral(white, player), "Personal bonds still protect their owner across factions");
            System.out.println("LEGACY_RIVALRY_REGRESSION_PASS: 6 checks");
        } finally {
            ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.set(grace);
            white.discard(); dark.discard(); player.discard();
            net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.remove(player.getUUID());
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("LEGACY_RIVALRY_CHECK: " + message);
    }
}
