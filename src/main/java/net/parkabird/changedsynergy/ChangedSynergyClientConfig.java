package net.parkabird.changedsynergy;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Client-only presentation options. The class itself contains no client classes. */
public final class ChangedSynergyClientConfig {
    public static final Client CLIENT;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Client, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ChangedSynergyClientConfig() {
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue legacyTransfurScreenEffect;
        public final ForgeConfigSpec.BooleanValue legacyTransfurSkinEffect;
        public final ForgeConfigSpec.DoubleValue legacyTransfurScreenOpacity;
        public final ForgeConfigSpec.BooleanValue friendlySuitVignette;
        public final ForgeConfigSpec.DoubleValue friendlySuitVignetteOpacity;
        public final ForgeConfigSpec.BooleanValue telepathicDanmaku;
        public final ForgeConfigSpec.EnumValue<DialogueDisplayMode>
                dialogueDisplayMode;
        public final ForgeConfigSpec.BooleanValue territoryHud;
        public final ForgeConfigSpec.BooleanValue mechanicHints;
        public final ForgeConfigSpec.BooleanValue qteAnimations;
        public final ForgeConfigSpec.BooleanValue reducedQteMotion;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Optional Changed 0.13-style transfur feedback").push("LEGACY TRANSFUR VISUALS");
            legacyTransfurScreenEffect = builder
                    .comment("Restore the colored transfur mask that grows inward as progress rises.",
                            "While active, it replaces Changed's current partial-progress indicator.",
                            "Disabled by default so Changed keeps its current presentation.")
                    .define("LegacyTransfurScreenEffect", false);
            legacyTransfurSkinEffect = builder
                    .comment("Restore the ten-stage colored latex coat on partially transfurred player skins and first-person arms.",
                            "Organic assimilation intentionally uses only its screen mask.",
                            "Disabled by default so Changed keeps its current presentation.")
                    .define("LegacyTransfurSkinEffect", false);
            legacyTransfurScreenOpacity = builder
                    .comment("Maximum opacity multiplier for the legacy screen vignette.")
                    .defineInRange("LegacyTransfurScreenOpacity", 1.0, 0.0, 1.0);
            builder.pop();

            builder.comment("Friendly wrapping feedback").push("FRIENDLY SUIT VISUALS");
            friendlySuitVignette = builder
                    .comment("Show a soft, texture-free theme-colored veil while wrapped by a pet or bonded creature.")
                    .define("FriendlySuitVignette", true);
            friendlySuitVignetteOpacity = builder
                    .comment("Opacity of the friendly wrapping veil.")
                    .defineInRange("FriendlySuitVignetteOpacity", 0.25, 0.0, 0.5);
            builder.pop();

            builder.comment("Telepathic dialogue presentation").push("TELEPATHY");
            telepathicDanmaku = builder
                    .comment("Show ordinary creature speech in the lightweight on-screen overlay.",
                            "When disabled, those lines fall back to the chat box.")
                    .define("TelepathicDanmaku", true);
            dialogueDisplayMode = builder
                    .comment("How the on-screen dialogue overlay is arranged.",
                            "AUTO uses popups for English and danmaku for CJK languages.")
                    .defineEnum(
                            "DialogueDisplayMode",
                            DialogueDisplayMode.AUTO);
            territoryHud = builder
                    .comment("Show biome entry subtitles and the persistent facility room readout.")
                    .define("TerritoryHud", true);
            builder.pop();

            builder.comment("One-time vanilla-style mechanic hints").push("TUTORIAL HINTS");
            mechanicHints = builder
                    .comment("Show contextual top-right hints for Synergy interactions.",
                            "Each hint is remembered after it has been shown once.")
                    .define("MechanicHints", true);
            builder.pop();

            builder.comment("Grab and hypnosis QTE presentation").push("QTE VISUALS");
            qteAnimations = builder
                    .comment("Enable animated grab and hypnosis QTE interfaces.",
                            "Disabling this restores the simpler static presentation.")
                    .define("QteAnimations", true);
            reducedQteMotion = builder
                    .comment("Keep fades and input feedback while removing UI shake, drift and large scaling motion.")
                    .define("ReducedQteMotion", false);
            builder.pop();
        }
    }

    public enum DialogueDisplayMode {
        AUTO,
        POPUP,
        DANMAKU
    }
}
