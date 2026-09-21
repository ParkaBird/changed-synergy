package net.parkabird.changedsynergy.advancement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Small programmatic triggers for Synergy's relationship advancement tree. */
public final class SynergyAdvancements {
    public static final String FIRST_CONTACT = "first_contact";
    public static final String TRUSTED_FRIEND = "trusted_friend";
    public static final String CLOSE_COMPANION = "close_companion";
    public static final String SHARED_MOMENT = "shared_moment";
    public static final String PEACE_OFFERING = "peace_offering";
    public static final String BONDED_COMPANION = "bonded_companion";
    public static final String PROTECTED_BY_SYNERGY = "protected_by_synergy";
    public static final String HYPNOSIS_ESCAPE = "hypnosis_escape";
    public static final String TAKEOVER_BREAKOUT = "takeover_breakout";
    public static final String WRAPPED_SLEEP = "wrapped_sleep";
    public static final String FACTION_ALLY = "faction_ally";
    public static final String WHITE_HIVE_CONSENSUS =
            "white_hive_consensus";
    public static final String WHITE_FACTION_RESPECTED =
            "white_faction_respected";
    public static final String DARK_FACTION_RESPECTED =
            "dark_faction_respected";
    public static final String AQUATIC_FACTION_RESPECTED =
            "aquatic_faction_respected";
    public static final String LIGHT_FACTION_RESPECTED =
            "light_faction_respected";
    public static final String WHITE_FACTION_ALLIED =
            "white_faction_allied";
    public static final String DARK_FACTION_ALLIED =
            "dark_faction_allied";
    public static final String AQUATIC_FACTION_ALLIED =
            "aquatic_faction_allied";
    public static final String LIGHT_FACTION_ALLIED =
            "light_faction_allied";
    /** Legacy ID retained; now awards simultaneous respect across all political accounts. */
    public static final String ALL_FACTIONS_ALLIED =
            "all_factions_allied";
    public static final String MYSTERIOUS_FORAGING_SPOT =
            "mysterious_foraging_spot";
    public static final String NEGOTIATED_RELEASE = "negotiated_release";
    public static final String FOOD_BRIBE = "food_bribe";
    public static final String FUSION_SEPARATION = "fusion_separation";
    public static final String BOX_SURPRISE = "box_surprise";

    private SynergyAdvancements() {
    }

    public static void grant(ServerPlayer player, String path) {
        var advancement = player.server.getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID, path));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "complete");
        }
    }
}
