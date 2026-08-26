package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Persistent player standing with each faction. */
public final class FactionReputation {
    public static final int ALLIED_THRESHOLD = 70;
    public static final int RESPECTED_THRESHOLD = 40;
    public static final int RECOGNIZED_THRESHOLD = 20;
    public static final int DISTRUSTED_THRESHOLD = -15;
    public static final int HOSTILE_THRESHOLD = -40;
    private static final int MINIMUM = -100;
    private static final int MAXIMUM = 100;
    private static final String ROOT = "ChangedSynergyReputation";
    private static final String LEGACY_WILD_FACTION_KEY = "faction:wild";
    private static final String LEGACY_ORGANIC_FACTION_KEY = "faction:organic";
    private static final String LIGHT_MIGRATION_DONE =
            "migration:light_groups_v1";
    private static final String LIGHT_MIGRATION_BASELINE =
            "migration:light_groups_baseline";
    private static final String LIGHT_MIGRATION_FROM_GLOBAL =
            "migration:light_groups_from_global";
    private static final String INTERACTION_REMAINDER_PREFIX =
            "interaction_remainder:";

    private FactionReputation() {
    }

    public static int score(ChangedEntity creature, ServerPlayer player) {
        if (!enabled(player)) {
            return 0;
        }
        HunterFaction faction = HunterFaction.of(creature);
        CompoundTag reputation = data(player);
        String key = keyFor(creature);
        initializeLightScore(
                reputation, faction, key,
                creature.level(), creature.blockPosition());
        return reputation.getInt(key);
    }

    /** Reads faction standing at a territory without needing a sample creature. */
    public static int scoreAt(
            HunterFaction faction,
            ServerPlayer player,
            Level level,
            BlockPos position) {
        if (faction == null || !enabled(player)) {
            return 0;
        }
        CompoundTag reputation = data(player);
        String key = keyAt(faction, level, position);
        initializeLightScore(reputation, faction, key, level, position);
        return reputation.getInt(key);
    }

    public static boolean isRespected(
            ChangedEntity creature,
            ServerPlayer player) {
        return score(creature, player) >= RESPECTED_THRESHOLD;
    }

    public static boolean isRecognized(
            ChangedEntity creature,
            ServerPlayer player) {
        return score(creature, player) >= RECOGNIZED_THRESHOLD;
    }

    public static boolean isDistrusted(
            ChangedEntity creature,
            ServerPlayer player) {
        return score(creature, player) <= DISTRUSTED_THRESHOLD;
    }

    public static boolean isHostile(
            ChangedEntity creature,
            ServerPlayer player) {
        return score(creature, player) <= HOSTILE_THRESHOLD;
    }

    public static boolean isAllied(
            ChangedEntity creature,
            ServerPlayer player) {
        return score(creature, player) >= ALLIED_THRESHOLD;
    }

    public static Standing standing(
            ChangedEntity creature,
            ServerPlayer player) {
        return Standing.forScore(score(creature, player));
    }

    public static double detectionMultiplier(
            ChangedEntity creature,
            ServerPlayer player) {
        return switch (standing(creature, player)) {
            case HOSTILE -> 1.25D;
            case DISTRUSTED -> 1.12D;
            case NEUTRAL -> 1.0D;
            case RECOGNIZED -> 0.95D;
            case RESPECTED -> 0.90D;
            case ALLIED -> 0.82D;
        };
    }

    public static boolean isRespectedAt(
            HunterFaction faction,
            ServerPlayer player) {
        return scoreAt(
                faction,
                player,
                player.level(),
                player.blockPosition()) >= RESPECTED_THRESHOLD;
    }

    public static int adjust(
            ChangedEntity creature,
            ServerPlayer player,
            int amount) {
        if (!enabled(player)) {
            return 0;
        }
        if (amount == 0) {
            return score(creature, player);
        }
        int previous = score(creature, player);
        CompoundTag data = data(player);
        String key = keyFor(creature);
        Standing oldStanding = Standing.forScore(previous);
        int updated = Math.max(
                MINIMUM,
                Math.min(MAXIMUM, previous + amount));
        data.putInt(key, updated);
        Standing newStanding = Standing.forScore(updated);
        if (newStanding != oldStanding) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.reputation.changed",
                    Component.translatable(
                            displayTranslationKey(creature)),
                    Component.translatable(newStanding.translationKey()),
                    updated));
        }
        if (previous < ALLIED_THRESHOLD && updated >= ALLIED_THRESHOLD) {
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.FACTION_ALLY);
            grantFactionAlliedAdvancement(
                    player, HunterFaction.of(creature));
            grantAllFactionsAdvancementIfEligible(player, data);
        }
        if (previous < RESPECTED_THRESHOLD
                && updated >= RESPECTED_THRESHOLD) {
            grantFactionAdvancement(
                    player, HunterFaction.of(creature));
        }
        return updated;
    }

    /**
     * Positive social gestures earn trust more slowly while a faction already
     * distrusts the player. Fractional progress is retained, so repeated
     * one-point gestures can still eventually improve the relationship.
     */
    public static int adjustFromInteraction(
            ChangedEntity creature,
            ServerPlayer player,
            int amount) {
        if (amount <= 0 || !enabled(player)) {
            return adjust(creature, player, amount);
        }
        CompoundTag reputation = data(player);
        String remainderKey = INTERACTION_REMAINDER_PREFIX + keyFor(creature);
        int scaledUnits = amount * positiveInteractionPercent(creature, player)
                + reputation.getInt(remainderKey);
        int granted = scaledUnits / 100;
        reputation.putInt(remainderKey, scaledUnits % 100);
        return granted > 0
                ? adjust(creature, player, granted)
                : score(creature, player);
    }

    /** Percentage of ordinary peaceful-interaction gains currently retained. */
    public static int positiveInteractionPercent(
            ChangedEntity creature,
            ServerPlayer player) {
        return switch (standing(creature, player)) {
            // Poor standing makes trust noticeably slower to rebuild without
            // turning ordinary peaceful gestures into a practical dead end.
            case HOSTILE -> 60;
            case DISTRUSTED -> 80;
            default -> 100;
        };
    }

    public static int set(
            ChangedEntity creature,
            ServerPlayer player,
            int value) {
        return adjust(creature, player, value - score(creature, player));
    }

    public static void copyPlayerData(
            ServerPlayer original,
            ServerPlayer clone) {
        CompoundTag source = data(original);
        if (!source.isEmpty()) {
            persisted(clone).put(ROOT, source.copy());
        }
    }

    /** Restores threshold advancements for reputation earned before an update. */
    public static void restoreAdvancements(ServerPlayer player) {
        if (!enabled(player)) {
            return;
        }
        CompoundTag reputation = data(player);
        migrateSharedLightBaseline(reputation);
        int white = reputation.getInt(factionKey(HunterFaction.WHITE));
        int dark = reputation.getInt(factionKey(HunterFaction.DARK));
        int aquatic = reputation.getInt(factionKey(HunterFaction.AQUATIC));
        int light = highestLightScore(reputation);

        restoreFactionAdvancement(
                player, HunterFaction.WHITE, white);
        restoreFactionAdvancement(
                player, HunterFaction.DARK, dark);
        restoreFactionAdvancement(
                player, HunterFaction.AQUATIC, aquatic);
        restoreFactionAdvancement(
                player, HunterFaction.LIGHT, light);
        restoreFactionAlliedAdvancement(
                player, HunterFaction.WHITE, white);
        restoreFactionAlliedAdvancement(
                player, HunterFaction.DARK, dark);
        restoreFactionAlliedAdvancement(
                player, HunterFaction.AQUATIC, aquatic);
        restoreFactionAlliedAdvancement(
                player, HunterFaction.LIGHT, light);
        if (Math.max(Math.max(white, dark), Math.max(aquatic, light))
                >= ALLIED_THRESHOLD) {
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.FACTION_ALLY);
        }
        grantAllFactionsAdvancementIfEligible(player, reputation);
    }

    private static void restoreFactionAdvancement(
            ServerPlayer player,
            HunterFaction faction,
            int score) {
        if (score >= RESPECTED_THRESHOLD) {
            grantFactionAdvancement(player, faction);
        }
    }

    private static void grantFactionAdvancement(
            ServerPlayer player,
            HunterFaction faction) {
        String advancement = switch (faction) {
            case WHITE -> SynergyAdvancements.WHITE_FACTION_RESPECTED;
            case DARK -> SynergyAdvancements.DARK_FACTION_RESPECTED;
            case AQUATIC -> SynergyAdvancements.AQUATIC_FACTION_RESPECTED;
            case LIGHT -> SynergyAdvancements.LIGHT_FACTION_RESPECTED;
        };
        SynergyAdvancements.grant(player, advancement);
    }

    private static void restoreFactionAlliedAdvancement(
            ServerPlayer player,
            HunterFaction faction,
            int score) {
        if (score >= ALLIED_THRESHOLD) {
            grantFactionAlliedAdvancement(player, faction);
        }
    }

    private static void grantFactionAlliedAdvancement(
            ServerPlayer player,
            HunterFaction faction) {
        String advancement = switch (faction) {
            case WHITE -> SynergyAdvancements.WHITE_FACTION_ALLIED;
            case DARK -> SynergyAdvancements.DARK_FACTION_ALLIED;
            case AQUATIC -> SynergyAdvancements.AQUATIC_FACTION_ALLIED;
            case LIGHT -> SynergyAdvancements.LIGHT_FACTION_ALLIED;
        };
        SynergyAdvancements.grant(player, advancement);
    }

    private static void grantAllFactionsAdvancementIfEligible(
            ServerPlayer player,
            CompoundTag reputation) {
        if (reputation.getInt(factionKey(HunterFaction.WHITE))
                        >= ALLIED_THRESHOLD
                && reputation.getInt(factionKey(HunterFaction.DARK))
                        >= ALLIED_THRESHOLD
                && reputation.getInt(factionKey(HunterFaction.AQUATIC))
                        >= ALLIED_THRESHOLD
                && highestLightScore(reputation) >= ALLIED_THRESHOLD) {
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.ALL_FACTIONS_ALLIED);
        }
    }

    private static int highestLightScore(CompoundTag reputation) {
        int highest = reputation.getInt(LIGHT_MIGRATION_BASELINE);
        for (String key : reputation.getAllKeys()) {
            if (key.startsWith("faction:light:")) {
                highest = Math.max(highest, reputation.getInt(key));
            }
        }
        return highest;
    }

    private static String legacyWildBiomeKey(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos position) {
        ResourceLocation biome = level.registryAccess()
                .registry(Registries.BIOME)
                .map(registry -> registry.getKey(
                        level.getBiome(position).value()))
                .orElse(null);
        return "wild:" + (biome == null ? "unknown" : biome);
    }

    /**
     * Splits the former shared Light score once. Every regional account starts
     * from the player's existing shared score, then changes independently.
     * Saves old enough to lack a shared score may still recover the matching
     * per-biome Wild value.
     */
    private static void initializeLightScore(
            CompoundTag reputation,
            HunterFaction faction,
            String key,
            Level level,
            BlockPos position) {
        if (faction != HunterFaction.LIGHT
                || reputation.contains(key, Tag.TAG_INT)) {
            return;
        }
        migrateSharedLightBaseline(reputation);
        int initial = reputation.getInt(LIGHT_MIGRATION_BASELINE);
        if (!reputation.getBoolean(LIGHT_MIGRATION_FROM_GLOBAL)) {
            String legacyKey = legacyWildBiomeKey(level, position);
            if (reputation.contains(legacyKey, Tag.TAG_INT)) {
                initial = reputation.getInt(legacyKey);
            }
        }
        reputation.putInt(key, initial);
    }

    private static void migrateSharedLightBaseline(CompoundTag reputation) {
        if (reputation.getBoolean(LIGHT_MIGRATION_DONE)) {
            return;
        }
        String sharedKey = factionKey(HunterFaction.LIGHT);
        boolean fromGlobal = reputation.contains(sharedKey, Tag.TAG_INT)
                || reputation.contains(LEGACY_WILD_FACTION_KEY, Tag.TAG_INT);
        int baseline = reputation.contains(sharedKey, Tag.TAG_INT)
                ? reputation.getInt(sharedKey)
                : reputation.getInt(LEGACY_WILD_FACTION_KEY);
        reputation.putInt(LIGHT_MIGRATION_BASELINE, baseline);
        reputation.putBoolean(LIGHT_MIGRATION_FROM_GLOBAL, fromGlobal);
        reputation.putBoolean(LIGHT_MIGRATION_DONE, true);
        reputation.remove(sharedKey);
        reputation.remove(LEGACY_WILD_FACTION_KEY);
    }

    private static String factionKey(HunterFaction faction) {
        return "faction:" + faction.id();
    }

    private static String keyFor(ChangedEntity creature) {
        HunterFaction faction = HunterFaction.of(creature);
        return faction == HunterFaction.LIGHT
                ? lightKey(LightFactionGroup.of(creature))
                : factionKey(faction);
    }

    private static String keyAt(
            HunterFaction faction,
            Level level,
            BlockPos position) {
        return faction == HunterFaction.LIGHT
                ? lightKey(LightFactionGroup.at(level, position))
                : factionKey(faction);
    }

    private static String lightKey(String group) {
        return "faction:light:" + LightFactionGroup.normalize(group);
    }

    /** Stable political account ID used by faction reputation matching. */
    public static String groupId(ChangedEntity creature) {
        HunterFaction faction = HunterFaction.of(creature);
        return faction == HunterFaction.LIGHT
                ? "light:" + LightFactionGroup.of(creature)
                : faction.id();
    }

    public static String displayTranslationKey(ChangedEntity creature) {
        HunterFaction faction = HunterFaction.of(creature);
        return faction == HunterFaction.LIGHT
                ? LightFactionGroup.translationKey(
                        LightFactionGroup.of(creature))
                : faction.translationKey();
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) {
            persisted.put(ROOT, new CompoundTag());
        }
        CompoundTag reputation = persisted.getCompound(ROOT);
        // Organic construction is no longer a political faction. There is no
        // reliable way to divide one historical score among several local
        // affiliations, so discard the dormant key instead of guessing.
        reputation.remove(LEGACY_ORGANIC_FACTION_KEY);
        return reputation;
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean enabled(ServerPlayer player) {
        return ChangedSynergyGameRules.enabled(
                player.level(), ChangedSynergyGameRules.FACTION_REPUTATION);
    }

    public enum Standing {
        HOSTILE,
        DISTRUSTED,
        NEUTRAL,
        RECOGNIZED,
        RESPECTED,
        ALLIED;

        public static Standing forScore(int score) {
            if (score <= HOSTILE_THRESHOLD) return HOSTILE;
            if (score <= DISTRUSTED_THRESHOLD) return DISTRUSTED;
            if (score >= ALLIED_THRESHOLD) return ALLIED;
            if (score >= RESPECTED_THRESHOLD) return RESPECTED;
            if (score >= RECOGNIZED_THRESHOLD) return RECOGNIZED;
            return NEUTRAL;
        }

        public String translationKey() {
            return "reputation.changed_synergy."
                    + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
