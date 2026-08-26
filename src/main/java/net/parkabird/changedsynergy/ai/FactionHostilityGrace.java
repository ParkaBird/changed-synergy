package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/**
 * Short faction-wide ceasefire after a transfur or negotiated separation.
 * It prevents an immediate re-capture loop but disappears as soon as the
 * player attacks a member of that same political group again.
 */
public final class FactionHostilityGrace {
    private static final String ROOT = "ChangedSynergyFactionGrace";
    private static final long DEFAULT_DURATION = 20L * 60L;

    private FactionHostilityGrace() {
    }

    public static void begin(ChangedEntity source, ServerPlayer player) {
        begin(source, player, DEFAULT_DURATION);
    }

    public static void begin(
            ChangedEntity source,
            ServerPlayer player,
            long duration) {
        String group = FactionReputation.groupId(source);
        graceData(player).putLong(
                group,
                player.level().getGameTime() + Math.max(1L, duration));
        calmLoadedGroup(player, group);
    }

    public static boolean active(
            ChangedEntity creature,
            ServerPlayer player) {
        CompoundTag data = existingData(player);
        if (data == null) {
            return false;
        }
        String group = FactionReputation.groupId(creature);
        long expiry = data.getLong(group);
        if (expiry <= player.level().getGameTime()) {
            data.remove(group);
            return false;
        }
        return true;
    }

    public static void clear(
            ChangedEntity creature,
            ServerPlayer player) {
        CompoundTag data = existingData(player);
        if (data != null) {
            data.remove(FactionReputation.groupId(creature));
        }
    }

    public static void copyPlayerData(
            ServerPlayer original,
            ServerPlayer clone) {
        CompoundTag source = existingData(original);
        if (source != null && !source.isEmpty()) {
            persisted(clone).put(ROOT, source.copy());
        }
    }

    private static void calmLoadedGroup(
            ServerPlayer player,
            String group) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ChangedEntity creature
                        && creature.isAlive()
                        && group.equals(FactionReputation.groupId(creature))) {
                    LatexSocialMemory.clearProvocation(creature, player);
                    LatexSocialEvents.clearPendingCombatReactions(
                            creature, player);
                    LatexSocialEvents.calmTowards(creature, player);
                }
            }
        }
    }

    private static CompoundTag graceData(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(ROOT, new CompoundTag());
        }
        return persisted.getCompound(ROOT);
    }

    private static CompoundTag existingData(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        return persisted.contains(ROOT, Tag.TAG_COMPOUND)
                ? persisted.getCompound(ROOT) : null;
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
