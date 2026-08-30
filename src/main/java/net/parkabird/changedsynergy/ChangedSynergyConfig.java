package net.parkabird.changedsynergy;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

/** Server-side settings for social AI and companion behaviour. */
public final class ChangedSynergyConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ChangedSynergyConfig() {
    }

    /** Rebalances installations that still use the original over-wide awareness defaults. */
    public static void migrateBehaviourBalance(ModConfig config) {
        if (COMMON.behaviourConfigRevision.get() >= 1) {
            return;
        }
        if (Math.abs(COMMON.npcAwarenessRange.get() - 40.0D) < 1.0E-9D) {
            COMMON.npcAwarenessRange.set(32.0D);
        }
        if (COMMON.huntSearchSeconds.get() == 12) {
            COMMON.huntSearchSeconds.set(8);
        }
        if (Math.abs(COMMON.huntAlertRadius.get() - 20.0D) < 1.0E-9D) {
            COMMON.huntAlertRadius.set(12.0D);
        }
        COMMON.behaviourConfigRevision.set(1);
        config.save();
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue respectPacifiedLatexes;
        public final ForgeConfigSpec.BooleanValue pacifyTamedCompanions;
        public final ForgeConfigSpec.IntValue tamedCompanionRegeneration;
        public final ForgeConfigSpec.DoubleValue npcAwarenessRange;
        public final ForgeConfigSpec.BooleanValue politeHumanInteraction;
        public final ForgeConfigSpec.DoubleValue politeApproachRange;
        public final ForgeConfigSpec.DoubleValue politeApproachSpeed;
        public final ForgeConfigSpec.IntValue politeResponseSeconds;
        public final ForgeConfigSpec.IntValue politeUnansweredLimit;

        public final ForgeConfigSpec.DoubleValue visualAcquisitionRange;
        public final ForgeConfigSpec.DoubleValue maximumPursuitRange;
        public final ForgeConfigSpec.IntValue huntLoseSightSeconds;
        public final ForgeConfigSpec.IntValue huntSearchSeconds;
        public final ForgeConfigSpec.DoubleValue huntAlertRadius;
        public final ForgeConfigSpec.DoubleValue huntSearchSpeed;
        public final ForgeConfigSpec.DoubleValue firearmGunshotRadius;
        public final ForgeConfigSpec.BooleanValue firearmEvasion;
        public final ForgeConfigSpec.DoubleValue hostileGrabAttemptChance;
        public final ForgeConfigSpec.DoubleValue organicHostileGrabAttemptChance;
        public final ForgeConfigSpec.BooleanValue allowMindlessMobTransfur;
        public final ForgeConfigSpec.IntValue behaviourConfigRevision;

        public final ForgeConfigSpec.DoubleValue npcDialogueRange;
        public final ForgeConfigSpec.IntValue npcDialogueCooldownSeconds;
        public final ForgeConfigSpec.DoubleValue npcDialogueChance;
        public final ForgeConfigSpec.DoubleValue personalityDialogueChance;
        public final ForgeConfigSpec.BooleanValue npcDialogueUsesTranslator;
        public final ForgeConfigSpec.IntValue telepathyUnlockTransfurs;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Relationship and companion settings").push("RELATIONSHIPS");
            respectPacifiedLatexes = builder
                    .comment("Unarmed latex creatures with the Pacified effect remain neutral.")
                    .define("RespectPacifiedLatexes", true);
            pacifyTamedCompanions = builder
                    .comment("Keep eligible native latex pets pacified around their host.")
                    .define("PacifyTamedCompanions", true);
            tamedCompanionRegeneration = builder
                    .comment("Regeneration level for native latex pets; zero disables it.")
                    .defineInRange("TamedCompanionRegeneration", 0, 0, 5);
            npcAwarenessRange = builder
                    .comment("Maximum radius used to evaluate player and latex-creature relationships.")
                    .defineInRange("NpcAwarenessRange", 32.0, 8.0, 96.0);
            politeHumanInteraction = builder
                    .comment("Let polite individuals approach unfamiliar humans before deciding to hunt.")
                    .define("PoliteHumanInteraction", true);
            politeApproachRange = builder
                    .comment("Maximum range at which a polite individual begins a cautious greeting.")
                    .defineInRange("PoliteApproachRange", 12.0, 4.0, 32.0);
            politeApproachSpeed = builder
                    .comment("Navigation speed used while a polite individual approaches a human.")
                    .defineInRange("PoliteApproachSpeed", 0.42, 0.05, 1.5);
            politeResponseSeconds = builder
                    .comment("Seconds a polite individual waits for the human to return its pat.")
                    .defineInRange("PoliteResponseSeconds", 14, 4, 60);
            politeUnansweredLimit = builder
                    .comment("Unanswered greeting attempts before the individual falls back to its normal attitude.")
                    .defineInRange("PoliteUnansweredLimit", 2, 1, 6);
            builder.pop();

            builder.comment("Search and pursuit behaviour").push("BEHAVIOUR");
            visualAcquisitionRange = builder
                    .comment("Base range for first visually noticing a player.",
                            "Line of sight and the creature's field of view are also required.")
                    .defineInRange("VisualAcquisitionRange", 18.0, 6.0, 48.0);
            maximumPursuitRange = builder
                    .comment("Base range at which a creature keeps pursuing an already confirmed target.")
                    .defineInRange("MaximumPursuitRange", 28.0, 8.0, 64.0);
            huntLoseSightSeconds = builder
                    .comment("Seconds without line of sight before a pursuer searches its last sighting.")
                    .defineInRange("LoseSightSeconds", 2, 1, 30);
            huntSearchSeconds = builder
                    .comment("Seconds a creature investigates the last seen or heard player position.")
                    .defineInRange("SearchSeconds", 8, 2, 120);
            huntAlertRadius = builder
                    .comment("Radius used to share a confirmed sighting with compatible nearby creatures.")
                    .defineInRange("AlertRadius", 12.0, 0.0, 96.0);
            huntSearchSpeed = builder
                    .comment("Navigation speed used only while investigating or searching.")
                    .defineInRange("SearchSpeed", 0.45, 0.05, 3.0);
            firearmGunshotRadius = builder
                    .comment("Fallback gunshot radius for firearm integrations without sound-range data.",
                            "TACZ uses its own attachment-adjusted sound distance instead.")
                    .defineInRange("FirearmGunshotRadius", 48.0, 0.0, 128.0);
            firearmEvasion = builder
                    .comment("Hostile creatures occasionally move laterally while pursuing an armed player.")
                    .define("FirearmEvasion", true);
            hostileGrabAttemptChance = builder
                    .comment("Chance that an eligible hostile grab starts its escape QTE.",
                            "A failed roll briefly pauses further grab attempts from that creature.")
                    .defineInRange("HostileGrabAttemptChance", 0.65, 0.0, 1.0);
            organicHostileGrabAttemptChance = builder
                    .comment("Separate grab-attempt chance for organic creatures.",
                            "Organic assimilation relies on physical grabs, so its default is higher.")
                    .defineInRange("OrganicHostileGrabAttemptChance", 0.85, 0.0, 1.0);
            allowMindlessMobTransfur = builder
                    .comment("Allow mindless mobs such as zombies and skeletons to be fully transfurred.",
                            "Disabled by default: they are absorbed instead to avoid creating extra persistent entities.")
                    .define("AllowMindlessMobTransfur", false);
            behaviourConfigRevision = builder
                    .comment("Internal migration marker for perception and alert defaults.")
                    .defineInRange("BehaviourConfigRevision", 0, 0, 1);
            builder.pop();

            builder.comment("Contextual speech and emotes").push("DIALOGUE");
            npcDialogueRange = builder
                    .comment("Maximum range at which a player can receive creature dialogue.")
                    .defineInRange("Range", 32.0, 4.0, 96.0);
            npcDialogueCooldownSeconds = builder
                    .comment("Minimum cooldown per speaker and listener between messages.")
                    .defineInRange("CooldownSeconds", 12, 2, 120);
            npcDialogueChance = builder
                    .comment("Chance that an eligible state change produces a line.")
                    .defineInRange("Chance", 0.72, 0.0, 1.0);
            personalityDialogueChance = builder
                    .comment("Chance that a suitable cue uses the creature's dominant-trait line.")
                    .defineInRange("PersonalityLineChance", 0.68, 0.0, 1.0);
            npcDialogueUsesTranslator = builder
                    .comment("Let an enabled Changed Addon Translator bypass the permanent telepathy unlock.")
                    .define("UsesTranslator", true);
            telepathyUnlockTransfurs = builder
                    .comment("Completed non-suit transformations needed to permanently understand telepathic speech.")
                    .defineInRange("TelepathyUnlockTransfurs", 3, 1, 20);
            builder.pop();
        }
    }
}
