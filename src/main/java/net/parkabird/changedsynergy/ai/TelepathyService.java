package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Permanent, save-local understanding of the latex telepathic network. */
public final class TelepathyService {
    private static final String ROOT = "ChangedSynergyTelepathy";
    private static final String TRANSFUR_COUNT = "CompletedTransfurs";
    private static final String UNLOCKED = "UnderstandsNetwork";
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "telepathy");

    private TelepathyService() {
    }

    public static boolean canUnderstand(ServerPlayer player) {
        return data(player).getBoolean(UNLOCKED);
    }

    public static void recordCompletedTransfur(
            ServerPlayer player,
            TransfurVariantInstance<?> instance) {
        if (instance == null || instance.isTemporaryFromSuit()) {
            return;
        }
        CompoundTag data = data(player);
        int count = Math.min(1_000_000, data.getInt(TRANSFUR_COUNT) + 1);
        data.putInt(TRANSFUR_COUNT, count);
        int required = ChangedSynergyConfig.COMMON.telepathyUnlockTransfurs.get();
        if (required > 0 && count >= required) {
            unlock(player);
        }
    }

    public static void restoreAdvancement(ServerPlayer player) {
        if (canUnderstand(player)) {
            grantAdvancement(player);
        }
    }

    public static void copyPlayerData(
            ServerPlayer original,
            ServerPlayer clone) {
        CompoundTag source = data(original);
        if (!source.isEmpty()) {
            persisted(clone).put(ROOT, source.copy());
        }
    }

    private static void unlock(ServerPlayer player) {
        CompoundTag data = data(player);
        boolean firstUnlock = !data.getBoolean(UNLOCKED);
        data.putBoolean(UNLOCKED, true);
        grantAdvancement(player);
        if (firstUnlock) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.changed_synergy.telepathy.unlocked"));
        }
    }

    private static void grantAdvancement(ServerPlayer player) {
        var advancement =
                player.server.getAdvancements().getAdvancement(ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "understand");
        }
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(ROOT, new CompoundTag());
        }
        return persisted.getCompound(ROOT);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
