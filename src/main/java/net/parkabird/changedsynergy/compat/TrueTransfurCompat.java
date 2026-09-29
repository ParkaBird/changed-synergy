package net.parkabird.changedsynergy.compat;

import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

/** Optional guard for TrueTransfur's lethal reverse-transfur interception. */
public final class TrueTransfurCompat {
    private TrueTransfurCompat() {}

    public static boolean blocksReversal(ServerPlayer player) {
        if (!ModList.get().isLoaded("true_transfur")) return false;
        // TrueTransfur 1.0.1 explicitly exempts temporary suit variants from
        // its lethal reverse-transfur hook, even when its player flag is set.
        if (ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(TransfurVariantInstance::isTemporaryFromSuit)
                .orElse(false)) return false;
        try {
            Class<?> config = Class.forName(
                    "com.lugert.true_transfur.config.TrueTransfurConfig");
            Object deathSetting = config.getField("ENABLE_REVERSE_TRANSFUR_DEATH").get(null);
            if (!(deathSetting instanceof ForgeConfigSpec.BooleanValue flag))
                return ProcessTransfur.isPlayerTransfurred(player);
            if (!flag.get()) return false;
            Class<?> controller = Class.forName(
                    "com.lugert.true_transfur.transfur.TransfurController");
            return Boolean.TRUE.equals(controller
                    .getMethod("isTrueTransfurActive", ServerPlayer.class)
                    .invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError exception) {
            // A changed TrueTransfur API must never turn a safe release into death.
            return ProcessTransfur.isPlayerTransfurred(player);
        }
    }

    /** TrueTransfur 1.0.1 allows temporary suits to end normally. */
    public static void clearTemporarySuit(ServerPlayer player) {
        TransfurVariantInstance<?> current = ProcessTransfur.getPlayerTransfurVariant(player);
        if (current == null || !current.isTemporaryFromSuit()) return;
        if (blocksReversal(player)) keepForm(player, current);
        else ProcessTransfur.removePlayerTransfurVariant(player);
    }

    /** Used only when no prior form can be restored safely. */
    public static void removeOrKeepForm(ServerPlayer player) {
        if (blocksReversal(player)) {
            ProcessTransfur.getPlayerTransfurVariantSafe(player)
                    .ifPresent(current -> keepForm(player, current));
        } else if (ProcessTransfur.isPlayerTransfurred(player)) {
            ProcessTransfur.removePlayerTransfurVariant(player);
        }
    }

    private static void keepForm(ServerPlayer player, TransfurVariantInstance<?> current) {
        if (!current.isTemporaryFromSuit()) return;
        current.setTemporaryForSuit(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                SyncTransfurPacket.Builder.of(player));
    }
}
