package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.ChangedSynergyConfig;

/**
 * Short faction-wide ceasefire after a transfur or negotiated separation.
 * Ordinary grace ends on a new attack. Secondary transfur also locks player
 * damage for the same duration, so attacking cannot prematurely break it.
 */
public final class FactionHostilityGrace {
    private static final String ROOT = "ChangedSynergyFactionGrace";
    private static final String DAMAGE_LOCK = "ChangedSynergyFactionDamageLock";
    private static final String AGGRESSION = "ChangedSynergyFactionAggression";
    private static final String GLOBAL = "__all_latex_factions__";

    private FactionHostilityGrace() {
    }

    public static void begin(ChangedEntity source, ServerPlayer player) {
        begin(source, player, configuredDuration());
    }

    public static void begin(
            ChangedEntity source,
            ServerPlayer player,
            long duration) {
        if (duration <= 0L) return;
        String group = FactionReputation.groupId(source);
        graceData(player).putLong(
                group,
                Math.max(graceData(player).getLong(group),
                        player.level().getGameTime() + Math.max(1L, duration)));
        calmLoadedGroup(player, group);
    }

    /** Release protection is intentionally broader than the original capture faction. */
    public static void beginGlobal(ServerPlayer player) {
        long duration = configuredDuration();
        if (duration <= 0L) return;
        CompoundTag data = graceData(player);
        data.putLong(GLOBAL, Math.max(data.getLong(GLOBAL),
                player.level().getGameTime() + duration));
        calmAllLoaded(player);
    }

    public static void beginSecondary(ChangedEntity source, ServerPlayer player) {
        String group = FactionReputation.groupId(source);
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(DAMAGE_LOCK, Tag.TAG_COMPOUND)) {
            persisted.put(DAMAGE_LOCK, new CompoundTag());
        }
        CompoundTag locks = persisted.getCompound(DAMAGE_LOCK);
        long duration = configuredDuration();
        if (duration <= 0L) return;
        long expiry = Math.max(graceData(player).getLong(group),
                Math.max(locks.getLong(group), player.level().getGameTime() + duration));
        locks.putLong(group, expiry);
        begin(source, player, expiry - player.level().getGameTime());
    }

    public static boolean damageLocked(ChangedEntity creature, ServerPlayer player) {
        CompoundTag locks = persisted(player).getCompound(DAMAGE_LOCK);
        String group = FactionReputation.groupId(creature);
        if (locks.getLong(group) > player.level().getGameTime()) {
            return true;
        }
        locks.remove(group);
        return false;
    }

    public static boolean active(
            ChangedEntity creature,
            ServerPlayer player) {
        if (damageLocked(creature, player)) return true;
        CompoundTag data = existingData(player);
        if (data == null) {
            return false;
        }
        long globalExpiry = data.getLong(GLOBAL);
        if (globalExpiry > player.level().getGameTime()) return true;
        data.remove(GLOBAL);
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
        if (damageLocked(creature, player)) {
            return;
        }
        CompoundTag data = existingData(player);
        if (data != null) {
            data.remove(FactionReputation.groupId(creature));
            data.remove(GLOBAL);
        }
    }

    public static void copyPlayerData(
            ServerPlayer original,
            ServerPlayer clone) {
        CompoundTag source = existingData(original);
        if (source != null && !source.isEmpty()) {
            persisted(clone).put(ROOT, source.copy());
        }
        CompoundTag locks = persisted(original).getCompound(DAMAGE_LOCK);
        if (!locks.isEmpty()) {
            persisted(clone).put(DAMAGE_LOCK, locks.copy());
        }
        persisted(clone).put(AGGRESSION, persisted(original).getCompound(AGGRESSION).copy());
    }

    public static void noteAggression(ChangedEntity creature, ServerPlayer player) {
        CompoundTag root = persisted(player);
        if (!root.contains(AGGRESSION, Tag.TAG_COMPOUND)) root.put(AGGRESSION, new CompoundTag());
        root.getCompound(AGGRESSION).putLong(FactionReputation.groupId(creature),
                player.level().getGameTime() + 20L * 60L * 5L);
    }

    public static boolean wasAggressor(ChangedEntity creature, ServerPlayer player) {
        return persisted(player).getCompound(AGGRESSION).getLong(FactionReputation.groupId(creature))
                > player.level().getGameTime();
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

    private static void calmAllLoaded(ServerPlayer player) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ChangedEntity creature && creature.isAlive()) {
                    LatexSocialMemory.clearProvocation(creature, player);
                    LatexSocialEvents.clearPendingCombatReactions(creature, player);
                    LatexSocialEvents.calmTowards(creature, player);
                }
            }
        }
    }

    private static long configuredDuration() {
        return 20L * ChangedSynergyConfig.COMMON.postTransfurTruceSeconds.get();
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
