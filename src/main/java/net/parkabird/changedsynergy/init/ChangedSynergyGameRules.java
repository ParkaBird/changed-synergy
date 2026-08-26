package net.parkabird.changedsynergy.init;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * Runtime switches for Synergy features.
 *
 * <p>Feature rules deliberately hide or pause existing state instead of
 * deleting it. A server can therefore turn one system off temporarily and
 * resume it later without losing relationships or reputation.
 */
public final class ChangedSynergyGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> NPC_AI =
            GameRules.register("changedSynergyNpcAI", GameRules.Category.MOBS,
                    GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> NPC_DIALOGUE =
            GameRules.register("changedSynergyNpcDialogue", GameRules.Category.MOBS,
                    GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> NPC_EMOTES =
            GameRules.register("changedSynergyNpcEmotes", GameRules.Category.MOBS,
                    GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> FACTION_REPUTATION =
            enabledByDefault("changedSynergyFactionReputation", GameRules.Category.PLAYER);
    public static final GameRules.Key<GameRules.BooleanValue> BOND_SYSTEM =
            enabledByDefault("changedSynergyBondSystem", GameRules.Category.MOBS);
    public static final GameRules.Key<GameRules.BooleanValue> FRIENDSHIP_SYSTEM =
            enabledByDefault("changedSynergyFriendshipSystem", GameRules.Category.MOBS);
    public static final GameRules.Key<GameRules.BooleanValue> TERRITORY_DISPLAY =
            enabledByDefault("changedSynergyTerritoryDisplay", GameRules.Category.MISC);
    public static final GameRules.Key<GameRules.BooleanValue> PERSONALITY_SYSTEM =
            enabledByDefault("changedSynergyPersonalitySystem", GameRules.Category.MOBS);
    public static final GameRules.Key<GameRules.BooleanValue> CREATURE_LIFE =
            enabledByDefault("changedSynergyCreatureLife", GameRules.Category.MOBS);
    public static final GameRules.Key<GameRules.BooleanValue> HYPNOSIS_QTE =
            enabledByDefault("changedSynergyHypnosisQte", GameRules.Category.PLAYER);
    public static final GameRules.Key<GameRules.BooleanValue> GRAB_QTE_ENHANCEMENTS =
            enabledByDefault("changedSynergyGrabQteEnhancements", GameRules.Category.PLAYER);

    private ChangedSynergyGameRules() {
    }

    public static void bootstrap() {
        // Static initialization performs registration.
    }

    public static boolean enabled(
            Level level,
            GameRules.Key<GameRules.BooleanValue> rule) {
        return level == null || level.getGameRules().getBoolean(rule);
    }

    /** Social contact remains available when either relationship system is active. */
    public static boolean socialSystemsEnabled(Level level) {
        return enabled(level, BOND_SYSTEM)
                || enabled(level, FRIENDSHIP_SYSTEM);
    }

    private static GameRules.Key<GameRules.BooleanValue> enabledByDefault(
            String name,
            GameRules.Category category) {
        return GameRules.register(
                name, category, GameRules.BooleanValue.create(true));
    }
}
