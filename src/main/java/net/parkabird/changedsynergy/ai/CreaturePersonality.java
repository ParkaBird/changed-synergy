package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/**
 * Stable per-creature traits and compact long-term memories of individual
 * players. All data lives on the creature so it survives saves and dimension
 * changes without a separate capability.
 */
public final class CreaturePersonality {
    private static final String ROOT = "ChangedSynergyPersonality";
    private static final String DATA_VERSION = "Version";
    private static final String TRAIT_MASK = "Traits";
    private static final String DOMINANT_TRAIT = "DominantTrait";
    private static final String MEMORIES = "PlayerMemories";
    private static final String ENCOUNTERS = "Encounters";
    private static final String PATS_RECEIVED = "PatsReceived";
    private static final String PATS_GIVEN = "PatsGiven";
    private static final String GIFT_FAVOR = "GiftFavor";
    private static final String PLAYS_TOGETHER = "PlaysTogether";
    private static final String LAST_PLAYED = "LastPlayed";
    private static final String HARM = "Harm";
    private static final String AFFECTION_ADJUSTMENT = "AffectionAdjustment";
    private static final String POSITIVE_GAIN_REMAINDER =
            "PositiveGainRemainder";
    private static final String WITNESSED_KIN_KILLS = "WitnessedKinKills";
    private static final String RELATIONSHIP = "Relationship";
    private static final String RELATIONSHIP_SINCE = "RelationshipSince";
    private static final String SOCIAL_PARTNER = "SocialPartner";
    private static final String LAST_COUNTED_ENCOUNTER = "LastCountedEncounter";
    private static final String LAST_INTERACTION = "LastInteraction";
    private static final String RESCUE_CONCERN = "RescueConcern";
    private static final String LAST_RESCUE_CONCERN = "LastRescueConcern";
    private static final String RESCUE_CONCERN_UPDATED = "RescueConcernUpdated";
    private static final int CURRENT_VERSION = 5;
    private static final int MAX_PLAYER_MEMORIES = 64;
    private static final long ENCOUNTER_INTERVAL = 1200L;
    private static final long RESCUE_CONCERN_INTERVAL = 1200L;
    private static final long RESCUE_CONCERN_DECAY_INTERVAL = 72000L;

    private CreaturePersonality() {
    }

    public enum Trait {
        CURIOUS,
        CAUTIOUS,
        PROTECTIVE,
        SHOW_OFF,
        CALM,
        COMPETITIVE,
        PLAYFUL,
        SENSITIVE,
        POLITE;

        public String dialogueKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Assigns two or three unique traits if the creature has no valid personality yet. */
    public static void ensure(ChangedEntity mob) {
        if (!personalityEnabled(mob)) {
            return;
        }
        CompoundTag personality = data(mob);
        int storedVersion = personality.getInt(DATA_VERSION);
        int mask = personality.getInt(TRAIT_MASK);
        int validMask = (1 << Trait.values().length) - 1;
        int count = Integer.bitCount(mask & validMask);
        if (mask != (mask & validMask) || count < 2 || count > 3) {
            RandomSource random = RandomSource.create(personalitySeed(mob));
            int wanted = random.nextFloat() < 0.45F ? 3 : 2;
            mask = 0;
            while (Integer.bitCount(mask) < wanted) {
                mask |= 1 << random.nextInt(Trait.values().length);
            }
            personality.putInt(TRAIT_MASK, mask);
        } else if (storedVersion < 3 && (mask & 1 << Trait.POLITE.ordinal()) == 0) {
            mask = migratePoliteTrait(mob, personality, mask);
            personality.putInt(TRAIT_MASK, mask);
        }

        personality.putInt(DATA_VERSION, CURRENT_VERSION);
        int dominant = personality.getInt(DOMINANT_TRAIT);
        if (!personality.contains(DOMINANT_TRAIT, Tag.TAG_INT)
                || dominant < 0 || dominant >= Trait.values().length
                || (mask & 1 << dominant) == 0) {
            personality.putInt(DOMINANT_TRAIT, selectDominantTrait(mob, mask));
        }
    }

    public static List<Trait> traits(ChangedEntity mob) {
        if (!personalityEnabled(mob)) {
            return List.of();
        }
        ensure(mob);
        int mask = data(mob).getInt(TRAIT_MASK);
        List<Trait> traits = new ArrayList<>(3);
        for (Trait trait : Trait.values()) {
            if ((mask & 1 << trait.ordinal()) != 0) {
                traits.add(trait);
            }
        }
        return List.copyOf(traits);
    }

    public static boolean has(ChangedEntity mob, Trait trait) {
        if (!personalityEnabled(mob)) {
            return false;
        }
        ensure(mob);
        return (data(mob).getInt(TRAIT_MASK) & 1 << trait.ordinal()) != 0;
    }

    /**
     * The strongest visible trait is fixed for the lifetime of the creature.
     * Secondary traits still shape AI values but do not compete for each line.
     */
    public static Trait dominantTrait(ChangedEntity mob) {
        if (!personalityEnabled(mob)) {
            return Trait.CALM;
        }
        ensure(mob);
        return Trait.values()[data(mob).getInt(DOMINANT_TRAIT)];
    }

    /** Selects a visible dominant trait while retaining the generated secondary traits. */
    public static void setDominantTrait(ChangedEntity mob, Trait trait) {
        ensure(mob);
        CompoundTag personality = data(mob);
        int mask = personality.getInt(TRAIT_MASK) | 1 << trait.ordinal();
        if (Integer.bitCount(mask) > 3) {
            mask &= ~(1 << personality.getInt(DOMINANT_TRAIT));
        }
        personality.putInt(TRAIT_MASK, mask);
        personality.putInt(DOMINANT_TRAIT, trait.ordinal());
        personality.putInt(DATA_VERSION, CURRENT_VERSION);
    }

    /** Alters detection persistence without changing the entity's native melee speed. */
    public static double pursuitRangeMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CURIOUS)) value *= 1.12D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.90D;
        if (has(mob, Trait.PROTECTIVE)) value *= 1.06D;
        if (has(mob, Trait.SHOW_OFF)) value *= 1.04D;
        if (has(mob, Trait.COMPETITIVE)) value *= 1.15D;
        if (has(mob, Trait.SENSITIVE)) value *= 0.90D;
        if (has(mob, Trait.POLITE)) value *= 0.94D;
        return Mth.clamp(value, 0.78D, 1.28D);
    }

    public static double lostSightGraceMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CURIOUS)) value *= 1.20D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.78D;
        if (has(mob, Trait.CALM)) value *= 1.22D;
        if (has(mob, Trait.COMPETITIVE)) value *= 1.15D;
        if (has(mob, Trait.SENSITIVE)) value *= 0.86D;
        return Mth.clamp(value, 0.65D, 1.55D);
    }

    public static double searchDurationMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CURIOUS)) value *= 1.30D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.88D;
        if (has(mob, Trait.PROTECTIVE)) value *= 1.12D;
        if (has(mob, Trait.CALM)) value *= 1.15D;
        if (has(mob, Trait.COMPETITIVE)) value *= 1.22D;
        if (has(mob, Trait.PLAYFUL)) value *= 1.10D;
        return Mth.clamp(value, 0.72D, 1.65D);
    }

    public static double searchRadiusMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CURIOUS)) value *= 1.28D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.78D;
        if (has(mob, Trait.PROTECTIVE)) value *= 0.90D;
        if (has(mob, Trait.SHOW_OFF)) value *= 1.10D;
        if (has(mob, Trait.PLAYFUL)) value *= 1.25D;
        if (has(mob, Trait.SENSITIVE)) value *= 0.88D;
        return Mth.clamp(value, 0.65D, 1.55D);
    }

    public static double searchSpeedMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.88D;
        if (has(mob, Trait.SHOW_OFF)) value *= 1.08D;
        if (has(mob, Trait.CALM)) value *= 0.90D;
        if (has(mob, Trait.COMPETITIVE)) value *= 1.08D;
        if (has(mob, Trait.SENSITIVE)) value *= 0.92D;
        return Mth.clamp(value, 0.75D, 1.20D);
    }

    public static double searchWaypointTimeMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CAUTIOUS)) value *= 1.18D;
        if (has(mob, Trait.CALM)) value *= 1.22D;
        if (has(mob, Trait.COMPETITIVE)) value *= 0.88D;
        if (has(mob, Trait.PLAYFUL)) value *= 0.72D;
        return Mth.clamp(value, 0.65D, 1.45D);
    }

    public static double firearmEvasionCooldownMultiplier(ChangedEntity mob) {
        double value = 1.0D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.72D;
        if (has(mob, Trait.SHOW_OFF)) value *= 0.82D;
        if (has(mob, Trait.CALM)) value *= 1.28D;
        if (has(mob, Trait.PLAYFUL)) value *= 0.82D;
        if (has(mob, Trait.SENSITIVE)) value *= 0.76D;
        return Mth.clamp(value, 0.55D, 1.50D);
    }

    public static int friendlyHitLimit(ChangedEntity mob, ServerPlayer player) {
        int limit = 3;
        if (has(mob, Trait.CALM)) limit++;
        if (has(mob, Trait.PLAYFUL)) limit++;
        if (has(mob, Trait.CAUTIOUS)) limit--;
        if (has(mob, Trait.SENSITIVE)) limit--;
        if (has(mob, Trait.POLITE)) limit++;
        int familiarity = familiarity(mob, player);
        if (familiarity >= 18) limit++;
        if (familiarity <= -8) limit--;
        return Mth.clamp(limit, 2, 5);
    }

    public static double damageToleranceMultiplier(ChangedEntity mob, ServerPlayer player) {
        double value = 1.0D;
        if (has(mob, Trait.CAUTIOUS)) value *= 0.88D;
        if (has(mob, Trait.PROTECTIVE)) value *= 0.94D;
        if (has(mob, Trait.CALM)) value *= 1.32D;
        if (has(mob, Trait.COMPETITIVE)) value *= 0.90D;
        if (has(mob, Trait.PLAYFUL)) value *= 1.08D;
        if (has(mob, Trait.SENSITIVE)) value *= 0.68D;
        if (has(mob, Trait.POLITE)) value *= 1.15D;
        value *= 1.0D + Mth.clamp(familiarity(mob, player), -20, 30) * 0.01D;
        return Mth.clamp(value, 0.52D, 1.75D);
    }

    public static double activePatCooldownMultiplier(
            ChangedEntity mob,
            ServerPlayer familiarPlayer) {
        double value = 1.0D;
        if (has(mob, Trait.CURIOUS)) value *= 0.76D;
        if (has(mob, Trait.CAUTIOUS)) value *= 1.30D;
        if (has(mob, Trait.PROTECTIVE)) value *= 0.82D;
        if (has(mob, Trait.SHOW_OFF)) value *= 0.78D;
        if (has(mob, Trait.CALM)) value *= 1.12D;
        if (has(mob, Trait.COMPETITIVE)) value *= 0.90D;
        if (has(mob, Trait.PLAYFUL)) value *= 0.58D;
        if (has(mob, Trait.SENSITIVE)) value *= 1.26D;
        if (has(mob, Trait.POLITE)) value *= 0.84D;
        if (CreatureSocialProfile.isJuvenile(mob)) value *= 1.25D;
        if (familiarPlayer != null) {
            value *= 1.0D - Math.min(0.25D,
                    Math.max(0, familiarity(mob, familiarPlayer)) * 0.01D);
        }
        return Mth.clamp(value, 0.62D, 1.70D);
    }

    public static void rememberEncounter(ChangedEntity mob, ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return;
        }
        CompoundTag memory = memory(mob, player, true);
        long now = mob.level().getGameTime();
        if (!memory.contains(LAST_COUNTED_ENCOUNTER)
                || now - memory.getLong(LAST_COUNTED_ENCOUNTER) >= ENCOUNTER_INTERVAL) {
            int before = familiarity(mob, memory);
            memory.putInt(ENCOUNTERS, Math.min(100, memory.getInt(ENCOUNTERS) + 1));
            memory.putLong(LAST_COUNTED_ENCOUNTER, now);
            scalePositiveGain(mob, player, memory, before, 0);
        }
        touchMemory(mob, player, memory, now);
    }

    public static void rememberPatReceived(ChangedEntity mob, ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return;
        }
        CompoundTag memory = memory(mob, player, true);
        int before = familiarity(mob, memory);
        memory.putInt(PATS_RECEIVED, Math.min(100, memory.getInt(PATS_RECEIVED) + 1));
        scalePositiveGain(mob, player, memory, before, 1);
        touchMemory(mob, player, memory, mob.level().getGameTime());
    }

    public static void rememberPatGiven(ChangedEntity mob, ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return;
        }
        CompoundTag memory = memory(mob, player, true);
        int before = familiarity(mob, memory);
        memory.putInt(PATS_GIVEN, Math.min(100, memory.getInt(PATS_GIVEN) + 1));
        scalePositiveGain(mob, player, memory, before, 1);
        touchMemory(mob, player, memory, mob.level().getGameTime());
    }

    public static void rememberGiftReceived(
            ChangedEntity mob,
            ServerPlayer player,
            int favor) {
        if (!friendshipEnabled(mob) || favor <= 0) {
            return;
        }
        CompoundTag memory = memory(mob, player, true);
        int before = familiarity(mob, memory);
        memory.putInt(GIFT_FAVOR,
                Math.min(100, memory.getInt(GIFT_FAVOR) + favor));
        scalePositiveGain(
                mob, player, memory, before, Math.max(1, favor / 4));
        touchMemory(mob, player, memory, mob.level().getGameTime());
    }

    public static long playCooldownRemaining(
            ChangedEntity mob,
            ServerPlayer player,
            long cooldownTicks) {
        if (!friendshipEnabled(mob)) {
            return 0L;
        }
        CompoundTag memory = memory(mob, player, false);
        if (memory == null || !memory.contains(LAST_PLAYED, Tag.TAG_LONG)) {
            return 0L;
        }
        long readyAt = memory.getLong(LAST_PLAYED) + Math.max(0L, cooldownTicks);
        return Math.max(0L, readyAt - mob.level().getGameTime());
    }

    public static void rememberPlayedTogether(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return;
        }
        CompoundTag memory = memory(mob, player, true);
        int before = familiarity(mob, memory);
        memory.putInt(
                PLAYS_TOGETHER,
                Math.min(100, memory.getInt(PLAYS_TOGETHER) + 1));
        scalePositiveGain(mob, player, memory, before, 1);
        long now = mob.level().getGameTime();
        memory.putLong(LAST_PLAYED, now);
        touchMemory(mob, player, memory, now);
        SynergyAdvancements.grant(player, SynergyAdvancements.SHARED_MOMENT);
    }

    public static void rememberHarm(ChangedEntity mob, ServerPlayer player, float damage) {
        if (!friendshipEnabled(mob) || damage <= 0.0F) {
            return;
        }
        CompoundTag memory = memory(mob, player, true);
        float cap = Math.max(40.0F, mob.getMaxHealth() * 20.0F);
        memory.putFloat(HARM, Math.min(cap, memory.getFloat(HARM) + damage));
        touchMemory(mob, player, memory, mob.level().getGameTime());
    }

    /**
     * Remembers repeated danger rescues without letting one prolonged incident
     * or rapid suit cycling inflate concern. One point fades after three
     * Minecraft days without another recorded rescue.
     */
    public static int rememberDangerRescue(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return 0;
        }
        CompoundTag memory = memory(mob, player, true);
        long now = mob.level().getGameTime();
        int concern = decayedRescueConcern(memory, now);
        long last = memory.getLong(LAST_RESCUE_CONCERN);
        if (!memory.contains(LAST_RESCUE_CONCERN, Tag.TAG_LONG)
                || now < last
                || now - last >= RESCUE_CONCERN_INTERVAL) {
            concern = Math.min(6, concern + 1);
            memory.putInt(RESCUE_CONCERN, concern);
            memory.putLong(LAST_RESCUE_CONCERN, now);
            memory.putLong(RESCUE_CONCERN_UPDATED, now);
        }
        touchMemory(mob, player, memory, now);
        return concern;
    }

    public static int rescueConcern(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return 0;
        }
        CompoundTag memory = memory(mob, player, false);
        return memory == null ? 0
                : decayedRescueConcern(memory, mob.level().getGameTime());
    }

    private static int decayedRescueConcern(CompoundTag memory, long now) {
        int concern = Mth.clamp(memory.getInt(RESCUE_CONCERN), 0, 6);
        if (!memory.contains(RESCUE_CONCERN_UPDATED, Tag.TAG_LONG)) {
            if (memory.contains(LAST_RESCUE_CONCERN, Tag.TAG_LONG)) {
                memory.putLong(RESCUE_CONCERN_UPDATED,
                        memory.getLong(LAST_RESCUE_CONCERN));
            }
            return concern;
        }
        long last = memory.getLong(RESCUE_CONCERN_UPDATED);
        if (now < last) {
            memory.putLong(RESCUE_CONCERN_UPDATED, now);
            return concern;
        }
        long intervals = (now - last) / RESCUE_CONCERN_DECAY_INTERVAL;
        if (intervals <= 0L) {
            return concern;
        }
        concern = Math.max(0, concern - (int)Math.min(6L, intervals));
        memory.putInt(RESCUE_CONCERN, concern);
        memory.putLong(RESCUE_CONCERN_UPDATED,
                last + intervals * RESCUE_CONCERN_DECAY_INTERVAL);
        return concern;
    }

    public static int recordWitnessedKinKill(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob) && !LatexSocialMemory.isBonded(mob, player)) {
            return 0;
        }
        CompoundTag memory = memory(mob, player, true);
        int count = Math.min(100, memory.getInt(WITNESSED_KIN_KILLS) + 1);
        memory.putInt(WITNESSED_KIN_KILLS, count);
        touchMemory(mob, player, memory, mob.level().getGameTime());
        return count;
    }

    public static int witnessedKinKillLimit(ChangedEntity mob) {
        int limit = 3;
        if (has(mob, Trait.PROTECTIVE) || has(mob, Trait.SENSITIVE)) {
            limit--;
        }
        if (has(mob, Trait.CALM) || has(mob, Trait.POLITE)) {
            limit++;
        }
        return Mth.clamp(limit, 2, 4);
    }

    /** Positive values represent remembered trust; damage can push the score below zero. */
    public static int familiarity(ChangedEntity mob, ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return 0;
        }
        CompoundTag memory = memory(mob, player, false);
        if (memory == null) {
            return 0;
        }
        return familiarity(mob, memory);
    }

    private static int familiarity(ChangedEntity mob, CompoundTag memory) {
        int positive = Math.min(16, memory.getInt(ENCOUNTERS) * 2)
                + Math.min(30, memory.getInt(PATS_RECEIVED) * 5)
                + Math.min(20, memory.getInt(PATS_GIVEN) * 3)
                + Math.min(32, memory.getInt(GIFT_FAVOR))
                + Math.min(18, memory.getInt(PLAYS_TOGETHER) * 3);
        int harm = Math.round(memory.getFloat(HARM)
                / Math.max(1.0F, mob.getMaxHealth()) * 12.0F);
        return Mth.clamp(
                positive - Math.min(50, harm)
                        + memory.getInt(AFFECTION_ADJUSTMENT),
                -40,
                60);
    }

    private static void scalePositiveGain(
            ChangedEntity mob,
            ServerPlayer player,
            CompoundTag memory,
            int before,
            int continuedRecovery) {
        int rawGain = familiarity(mob, memory) - before;
        if (rawGain <= 0) {
            // Interaction-history fields intentionally have caps. Once one is
            // full, continued deliberate care must still be able to repair a
            // damaged relationship instead of leaving it permanently stuck.
            if (continuedRecovery > 0 && before < 60) {
                grantScaledAdjustment(
                        mob, player, memory, continuedRecovery);
            }
            return;
        }
        int percent = FactionReputation.positiveInteractionPercent(mob, player);
        if (percent >= 100) {
            return;
        }
        int scaledUnits = rawGain * percent
                + memory.getInt(POSITIVE_GAIN_REMAINDER);
        int granted = scaledUnits / 100;
        memory.putInt(POSITIVE_GAIN_REMAINDER, scaledUnits % 100);
        memory.putInt(
                AFFECTION_ADJUSTMENT,
                Mth.clamp(
                        memory.getInt(AFFECTION_ADJUSTMENT)
                                - (rawGain - granted),
                        -200,
                        200));
    }

    private static void grantScaledAdjustment(
            ChangedEntity mob,
            ServerPlayer player,
            CompoundTag memory,
            int amount) {
        int scaledUnits = amount
                        * FactionReputation.positiveInteractionPercent(mob, player)
                + memory.getInt(POSITIVE_GAIN_REMAINDER);
        int granted = scaledUnits / 100;
        memory.putInt(POSITIVE_GAIN_REMAINDER, scaledUnits % 100);
        if (granted <= 0) {
            return;
        }
        memory.putInt(
                AFFECTION_ADJUSTMENT,
                Mth.clamp(
                        memory.getInt(AFFECTION_ADJUSTMENT) + granted,
                        -200,
                        200));
    }

    /** Administrative hook that preserves the underlying interaction history. */
    public static int setFamiliarity(
            ChangedEntity mob,
            ServerPlayer player,
            int value) {
        if (!friendshipEnabled(mob)) {
            return 0;
        }
        int target = Mth.clamp(value, -40, 60);
        CompoundTag memory = memory(mob, player, true);
        int current = familiarity(mob, player);
        memory.putInt(
                AFFECTION_ADJUSTMENT,
                Mth.clamp(
                        memory.getInt(AFFECTION_ADJUSTMENT)
                                + target - current,
                        -200,
                        200));
        touchMemory(mob, player, memory, mob.level().getGameTime());
        return familiarity(mob, player);
    }

    public static int adjustFamiliarity(
            ChangedEntity mob,
            ServerPlayer player,
            int amount) {
        return setFamiliarity(mob, player, familiarity(mob, player) + amount);
    }

    /** Applies a direct peaceful-interaction gain using the faction trust multiplier. */
    public static int adjustFamiliarityFromInteraction(
            ChangedEntity mob,
            ServerPlayer player,
            int amount) {
        if (amount <= 0 || !friendshipEnabled(mob)) {
            return adjustFamiliarity(mob, player, amount);
        }
        CompoundTag memory = memory(mob, player, true);
        int scaledUnits = amount
                        * FactionReputation.positiveInteractionPercent(mob, player)
                + memory.getInt(POSITIVE_GAIN_REMAINDER);
        int granted = scaledUnits / 100;
        memory.putInt(POSITIVE_GAIN_REMAINDER, scaledUnits % 100);
        if (granted > 0) {
            memory.putInt(
                    AFFECTION_ADJUSTMENT,
                    Mth.clamp(
                            memory.getInt(AFFECTION_ADJUSTMENT) + granted,
                            -200,
                            200));
            touchMemory(mob, player, memory, mob.level().getGameTime());
        }
        return familiarity(mob, memory);
    }

    public static boolean forgetRelationship(
            ChangedEntity mob,
            ServerPlayer player) {
        if (LatexSocialMemory.isBonded(mob, player)
                || LatexSocialMemory.isPetOwner(mob, player)) {
            return false;
        }
        boolean existed = forgetRelationship(mob, player.getUUID());
        PlayerRelationshipSettings.forgetContact(player, mob.getUUID());
        return existed;
    }

    /** Clears creature-side memory when a roster entry is removed while its chunk is unloaded. */
    public static boolean forgetRelationship(ChangedEntity mob, UUID playerId) {
        if (LatexSocialMemory.bondedPlayerUuids(mob).contains(playerId)
                || LatexSocialMemory.petOwnerUuid(mob).filter(playerId::equals).isPresent()) {
            return false;
        }
        CompoundTag memories = memories(mob);
        boolean existed = memories.contains(playerId.toString(), Tag.TAG_COMPOUND);
        memories.remove(playerId.toString());
        CompoundTag personality = data(mob);
        if (personality.hasUUID(SOCIAL_PARTNER)
                && playerId.equals(personality.getUUID(SOCIAL_PARTNER))) {
            personality.remove(SOCIAL_PARTNER);
        }
        return existed;
    }

    public static boolean remembersFondly(ChangedEntity mob, ServerPlayer player) {
        return friendshipEnabled(mob) && familiarity(mob, player) >= 6;
    }

    public static MemorySummary memorySummary(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return new MemorySummary(0, 0, 0);
        }
        CompoundTag memory = memory(mob, player, false);
        if (memory == null) {
            return new MemorySummary(0, 0, 0);
        }
        return new MemorySummary(
                memory.getInt(ENCOUNTERS),
                memory.getInt(PATS_RECEIVED) + memory.getInt(PATS_GIVEN),
                memory.getInt(PLAYS_TOGETHER));
    }

    /**
     * Personal trust must be built over several peaceful contacts. Traits alter
     * the amount of reassurance an individual needs, but no ordinary creature
     * can establish a relationship from one player pat.
     */
    public static int relationshipThreshold(ChangedEntity mob) {
        int threshold = 15;
        if (has(mob, Trait.CURIOUS)) threshold--;
        if (has(mob, Trait.PLAYFUL)) threshold--;
        if (has(mob, Trait.POLITE)) threshold--;
        if (has(mob, Trait.CAUTIOUS)) threshold += 2;
        if (has(mob, Trait.SENSITIVE)) threshold += 2;
        if (CreatureSocialProfile.isJuvenile(mob)) threshold--;
        return Mth.clamp(threshold, 12, 20);
    }

    /**
     * Advances an eligible relationship once remembered trust reaches the
     * creature's stable threshold. Existing relationships are never rerolled.
     */
    public static RelationshipProgress advanceRelationship(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return RelationshipProgress.INELIGIBLE;
        }
        if (hasEstablishedRelationship(mob, player)) {
            return RelationshipProgress.EXISTING;
        }
        if (familiarity(mob, player) < relationshipThreshold(mob)) {
            return RelationshipProgress.BUILDING;
        }
        return establishRelationship(mob, player)
                ? RelationshipProgress.ESTABLISHED
                : RelationshipProgress.EXISTING;
    }

    /**
     * Turns a successful peaceful contact into a lasting personal
     * relationship. Returns true only for the first establishment.
     */
    public static boolean establishRelationship(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return false;
        }
        CompoundTag memory = memory(mob, player, true);
        boolean first = !memory.getBoolean(RELATIONSHIP);
        memory.putBoolean(RELATIONSHIP, true);
        if (!memory.contains(RELATIONSHIP_SINCE, Tag.TAG_LONG)) {
            memory.putLong(RELATIONSHIP_SINCE, mob.level().getGameTime());
        }
        if (first) {
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.FIRST_CONTACT);
        }
        touchMemory(mob, player, memory, mob.level().getGameTime());
        if (ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.CREATURE_LIFE)) {
            mob.setPersistenceRequired();
        }
        CreatureIdentity.ensure(mob);
        PlayerRelationshipSettings.rememberContact(
                player, mob, LatexSocialMemory.isBonded(mob, player));
        if (first) {
            FactionReputation.adjustFromInteraction(mob, player, 12);
        }
        return first;
    }

    public static boolean hasEstablishedRelationship(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return false;
        }
        CompoundTag memory = memory(mob, player, false);
        return memory != null && memory.getBoolean(RELATIONSHIP);
    }

    public static boolean hasAnyEstablishedRelationship(ChangedEntity mob) {
        if (!friendshipEnabled(mob)
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return false;
        }
        CompoundTag memories = memories(mob);
        for (String key : memories.getAllKeys()) {
            if (memories.contains(key, Tag.TAG_COMPOUND)) {
                if (memories.getCompound(key).getBoolean(RELATIONSHIP)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Player ids whose durable relationship cards may reference this creature. */
    public static Set<UUID> establishedRelationshipPlayerUuids(
            ChangedEntity mob) {
        Set<UUID> players = new LinkedHashSet<>();
        CompoundTag memories = memories(mob);
        for (String key : memories.getAllKeys()) {
            if (!memories.contains(key, Tag.TAG_COMPOUND)
                    || !memories.getCompound(key).getBoolean(RELATIONSHIP)) {
                continue;
            }
            try {
                players.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Malformed legacy keys cannot identify a relationship owner.
            }
        }
        return players;
    }

    /** A relationship remains remembered while its present trust may sour. */
    public static boolean hasTrustedRelationship(
            ChangedEntity mob,
            ServerPlayer player) {
        return hasTrustedRelationship(mob, player.getUUID());
    }

    /** UUID form used to align companions even when their player is elsewhere. */
    public static boolean hasTrustedRelationship(
            ChangedEntity mob,
            UUID playerId) {
        if (!friendshipEnabled(mob)
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return false;
        }
        CompoundTag memory = memory(mob, playerId, false);
        return memory != null
                && memory.getBoolean(RELATIONSHIP)
                && familiarity(mob, memory) >= 0
                && !LatexSocialMemory.hasRelationshipBetrayal(mob, playerId);
    }

    public static RelationshipTier relationshipTier(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!hasEstablishedRelationship(mob, player)) {
            return RelationshipTier.STRANGER;
        }
        int trust = familiarity(mob, player);
        if (trust < 0) {
            return RelationshipTier.STRAINED;
        }
        if (trust >= 28) {
            return RelationshipTier.CLOSE;
        }
        if (trust >= 12) {
            return RelationshipTier.FAMILIAR;
        }
        return RelationshipTier.NEW;
    }

    /** New acquaintances need time before they will leave home to follow. */
    public static boolean canFriendFollow(
            ChangedEntity mob,
            ServerPlayer player) {
        RelationshipTier tier = relationshipTier(mob, player);
        return tier == RelationshipTier.FAMILIAR
                || tier == RelationshipTier.CLOSE;
    }

    /** Familiar friends defend an invited companion; close friends act on sight. */
    public static boolean canFriendDefend(
            ChangedEntity mob,
            ServerPlayer player) {
        return canFriendFollow(mob, player);
    }

    public static double friendDefenseRange(
            ChangedEntity mob,
            ServerPlayer player) {
        return relationshipTier(mob, player) == RelationshipTier.CLOSE
                ? 24.0D : 14.0D;
    }

    public static long socialPlayCooldown(
            ChangedEntity mob,
            ServerPlayer player,
            boolean bonded) {
        return switch (relationshipTier(mob, player)) {
            case CLOSE -> bonded ? 260L : 320L;
            case FAMILIAR -> bonded ? 360L : 420L;
            case NEW, STRANGER -> bonded ? 480L : 600L;
            case STRAINED -> 900L;
        };
    }

    public static float sharedRestHealing(
            ChangedEntity mob,
            ServerPlayer player) {
        return switch (relationshipTier(mob, player)) {
            case CLOSE -> 6.0F;
            case FAMILIAR -> 4.0F;
            default -> 2.0F;
        };
    }

    public static long sharedRestCooldown(
            ChangedEntity mob,
            ServerPlayer player) {
        return switch (relationshipTier(mob, player)) {
            case CLOSE -> 400L;
            case FAMILIAR -> 600L;
            default -> 800L;
        };
    }

    /** Close bonds recover more quickly while using the protective suit state. */
    public static float bondedRecoveryFraction(
            ChangedEntity mob,
            ServerPlayer player) {
        return switch (relationshipTier(mob, player)) {
            case CLOSE -> 0.040F;
            case FAMILIAR -> 0.030F;
            default -> 0.020F;
        };
    }

    public static void setSocialFollowing(
            ChangedEntity mob,
            ServerPlayer player,
            boolean following) {
        if (!friendshipEnabled(mob)) {
            return;
        }
        CompoundTag personality = data(mob);
        if (following
                && CreatureSocialProfile.allowsSocialWheel(mob)
                && hasTrustedRelationship(mob, player)
                && canFriendFollow(mob, player)) {
            personality.putUUID(SOCIAL_PARTNER, player.getUUID());
            if (ChangedSynergyGameRules.enabled(
                    mob.level(), ChangedSynergyGameRules.CREATURE_LIFE)) {
                mob.setPersistenceRequired();
            }
        } else if (personality.hasUUID(SOCIAL_PARTNER)
                && player.getUUID().equals(personality.getUUID(SOCIAL_PARTNER))) {
            personality.remove(SOCIAL_PARTNER);
        }
    }

    public static boolean isSocialFollowing(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!friendshipEnabled(mob)) {
            return false;
        }
        CompoundTag personality = data(mob);
        return personality.hasUUID(SOCIAL_PARTNER)
                && player.getUUID().equals(personality.getUUID(SOCIAL_PARTNER))
                && hasTrustedRelationship(mob, player)
                && canFriendFollow(mob, player);
    }

    public static ServerPlayer socialPartner(ChangedEntity mob) {
        if (!friendshipEnabled(mob)) {
            return null;
        }
        CompoundTag personality = data(mob);
        if (!personality.hasUUID(SOCIAL_PARTNER)
                || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList()
                .getPlayer(personality.getUUID(SOCIAL_PARTNER));
        if (player == null || player.level() != mob.level()
                || !hasTrustedRelationship(mob, player)
                || !canFriendFollow(mob, player)) {
            return null;
        }
        return player;
    }

    public static boolean hasNearbyRelationship(
            ServerPlayer player,
            double range) {
        if (!ChangedSynergyGameRules.enabled(
                        player.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        double rangeSqr = range * range;
        return !level.getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(range),
                        mob -> mob.isAlive()
                                && mob.distanceToSqr(player) <= rangeSqr
                                && hasTrustedRelationship(mob, player))
                .isEmpty();
    }

    private static boolean personalityEnabled(ChangedEntity mob) {
        return ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.PERSONALITY_SYSTEM);
    }

    private static boolean friendshipEnabled(ChangedEntity mob) {
        return ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
    }

    private static void touchMemory(
            ChangedEntity mob,
            ServerPlayer player,
            CompoundTag memory,
            long now) {
        memory.putLong(LAST_INTERACTION, now);
        CompoundTag memories = memories(mob);
        memories.put(player.getStringUUID(), memory);
        pruneMemories(memories, player.getStringUUID());
        if (memory.getBoolean(RELATIONSHIP)) {
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.FIRST_CONTACT);
            int trust = familiarity(mob, player);
            if (trust >= 12) {
                SynergyAdvancements.grant(
                        player, SynergyAdvancements.TRUSTED_FRIEND);
            }
            if (trust >= 28) {
                SynergyAdvancements.grant(
                        player, SynergyAdvancements.CLOSE_COMPANION);
            }
            if (trust < 12) {
                CompoundTag personality = data(mob);
                if (personality.hasUUID(SOCIAL_PARTNER)
                        && player.getUUID().equals(
                                personality.getUUID(SOCIAL_PARTNER))) {
                    personality.remove(SOCIAL_PARTNER);
                    mob.getNavigation().stop();
                }
            }
        }
    }

    private static void pruneMemories(CompoundTag memories, String protectedKey) {
        while (memories.getAllKeys().size() > MAX_PLAYER_MEMORIES) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (String key : memories.getAllKeys()) {
                if (key.equals(protectedKey) || !memories.contains(key, Tag.TAG_COMPOUND)) {
                    continue;
                }
                CompoundTag memory = memories.getCompound(key);
                if (memory.getBoolean(RELATIONSHIP)) {
                    continue;
                }
                long time = memory.getLong(LAST_INTERACTION);
                if (time < oldestTime) {
                    oldestTime = time;
                    oldestKey = key;
                }
            }
            if (oldestKey == null) {
                return;
            }
            memories.remove(oldestKey);
        }
    }

    private static CompoundTag memory(
            ChangedEntity mob,
            ServerPlayer player,
            boolean create) {
        return memory(mob, player.getUUID(), create);
    }

    private static CompoundTag memory(
            ChangedEntity mob,
            UUID playerId,
            boolean create) {
        CompoundTag memories = memories(mob);
        String key = playerId.toString();
        if (!memories.contains(key, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            memories.put(key, new CompoundTag());
        }
        return memories.getCompound(key);
    }

    private static CompoundTag memories(ChangedEntity mob) {
        CompoundTag personality = data(mob);
        if (!personality.contains(MEMORIES, Tag.TAG_COMPOUND)) {
            personality.put(MEMORIES, new CompoundTag());
        }
        return personality.getCompound(MEMORIES);
    }

    private static CompoundTag data(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static int selectDominantTrait(ChangedEntity mob, int mask) {
        long seed = personalitySeed(mob) ^ 0xD1B54A32D192ED03L;
        int selected = -1;
        long strongestExpression = 0L;
        for (Trait trait : Trait.values()) {
            if ((mask & 1 << trait.ordinal()) == 0) {
                continue;
            }
            long expression = mix64(seed
                    + 0x9E3779B97F4A7C15L * (trait.ordinal() + 1L));
            if (selected < 0
                    || Long.compareUnsigned(expression, strongestExpression) > 0) {
                selected = trait.ordinal();
                strongestExpression = expression;
            }
        }
        return selected;
    }

    /**
     * Gives existing worlds the new trait without rerolling every established
     * personality. Roughly one quarter of old individuals gain it; a secondary
     * trait is replaced only when all three slots were already occupied.
     */
    private static int migratePoliteTrait(
            ChangedEntity mob,
            CompoundTag personality,
            int mask) {
        long roll = mix64(personalitySeed(mob) ^ 0xA0761D6478BD642FL);
        if (Long.remainderUnsigned(roll, 100L) >= 27L) {
            return mask;
        }

        int politeBit = 1 << Trait.POLITE.ordinal();
        if (Integer.bitCount(mask) < 3) {
            return mask | politeBit;
        }

        int dominant = personality.contains(DOMINANT_TRAIT, Tag.TAG_INT)
                ? personality.getInt(DOMINANT_TRAIT) : -1;
        int offset = (int)Long.remainderUnsigned(
                mix64(roll ^ 0xE7037ED1A0B428DBL), Trait.POLITE.ordinal());
        for (int step = 0; step < Trait.POLITE.ordinal(); step++) {
            int candidate = (offset + step) % Trait.POLITE.ordinal();
            if (candidate != dominant && (mask & 1 << candidate) != 0) {
                return mask & ~(1 << candidate) | politeBit;
            }
        }
        return mask;
    }

    private static long personalitySeed(ChangedEntity mob) {
        long typeSalt =
                String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(mob.getType())).hashCode();
        return mob.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(mob.getUUID().getLeastSignificantBits(), 23)
                ^ typeSalt * 0x9E3779B97F4A7C15L;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public enum RelationshipProgress {
        INELIGIBLE,
        BUILDING,
        ESTABLISHED,
        EXISTING
    }

    public record MemorySummary(int encounters, int pats, int plays) {
    }

    public enum RelationshipTier {
        STRANGER,
        NEW,
        FAMILIAR,
        CLOSE,
        STRAINED;

        public String translationKey() {
            return "relationship.changed_synergy."
                    + name().toLowerCase(Locale.ROOT);
        }
    }
}
