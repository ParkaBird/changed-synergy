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
        public final ForgeConfigSpec.BooleanValue patPacification;
        public final ForgeConfigSpec.BooleanValue ordinaryTransfurReversal;
        public final ForgeConfigSpec.BooleanValue bondProtectiveReleaseRequests;
        public final ForgeConfigSpec.BooleanValue allowMultipleBonds;
        public final ForgeConfigSpec.BooleanValue peacefulVillageRelations;
        public final ForgeConfigSpec.BooleanValue factionPursuit;
        public final ForgeConfigSpec.BooleanValue independentFactionReputation;
        public final ForgeConfigSpec.IntValue postTransfurTruceSeconds;
        public final ForgeConfigSpec.IntValue settlementMinimumSpacing;
        public final ForgeConfigSpec.BooleanValue useChangedStructureOutposts;
        public final ForgeConfigSpec.BooleanValue latexBeeHiveOutposts;
        public final ForgeConfigSpec.IntValue structureOutpostSearchRadiusChunks;
        public final ForgeConfigSpec.BooleanValue provisionerTrading;
        public final ForgeConfigSpec.DoubleValue provisionerTradeReserveMultiplier;
        public final ForgeConfigSpec.BooleanValue takeoverEnabled;
        public final ForgeConfigSpec.BooleanValue takeoverPunitive;
        public final ForgeConfigSpec.BooleanValue takeoverCompetitive;
        public final ForgeConfigSpec.BooleanValue takeoverExoskeleton;
        public final ForgeConfigSpec.BooleanValue exoskeletonSleep;
        public final ForgeConfigSpec.BooleanValue takeoverBorrow;
        public final ForgeConfigSpec.BooleanValue takeoverEscape;
        public final ForgeConfigSpec.BooleanValue takeoverOranges;
        public final ForgeConfigSpec.IntValue takeoverSeconds;
        public final ForgeConfigSpec.IntValue takeoverPunitiveSeconds;
        public final ForgeConfigSpec.IntValue takeoverExoskeletonSeconds;
        public final ForgeConfigSpec.BooleanValue takeoverTransfurAfterSleep;
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
        public final ForgeConfigSpec.BooleanValue randomizeCreatureTransfurMethod;
        public final ForgeConfigSpec.IntValue behaviourConfigRevision;

        public final ForgeConfigSpec.BooleanValue performanceDiagnostics;
        public final ForgeConfigSpec.BooleanValue staggerBackgroundAi;
        public final ForgeConfigSpec.IntValue backgroundScanInterval;
        public final ForgeConfigSpec.BooleanValue distantAiThrottling;
        public final ForgeConfigSpec.DoubleValue distantAiRange;
        public final ForgeConfigSpec.IntValue distantAiIntervalMultiplier;
        public final ForgeConfigSpec.BooleanValue adaptiveAiBudget;
        public final ForgeConfigSpec.DoubleValue latexAiBudgetMs;
        public final ForgeConfigSpec.BooleanValue communityAi;
        public final ForgeConfigSpec.BooleanValue companionWorkAi;
        public final ForgeConfigSpec.BooleanValue ambientSocialAi;
        public final ForgeConfigSpec.BooleanValue enhancedHuntAi;
        public final ForgeConfigSpec.BooleanValue pureWhiteWolfAdaptation;

        public final ForgeConfigSpec.DoubleValue npcDialogueRange;
        public final ForgeConfigSpec.IntValue npcDialogueCooldownSeconds;
        public final ForgeConfigSpec.DoubleValue npcDialogueChance;
        public final ForgeConfigSpec.DoubleValue personalityDialogueChance;
        public final ForgeConfigSpec.BooleanValue npcDialogueUsesTranslator;
        public final ForgeConfigSpec.IntValue telepathyUnlockTransfurs;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.comment("NPC absorption control and forced exoskeleton attachment. Server authoritative.")
                    .push("TAKEOVER");
            takeoverEnabled = builder.define("Enabled", true);
            takeoverPunitive = builder.define("PunitiveCapture", true);
            takeoverCompetitive = builder.define("CompetitiveCapture", true);
            takeoverExoskeleton = builder
                    .comment("Allow an exoskeleton to take control of a benign-form player when its capture mechanic applies.")
                    // Keep the original serialized key so existing server configs retain their choice.
                    .define("ForcedExoskeleton", true);
            exoskeletonSleep = builder
                    .comment("End exoskeleton takeover with unconsciousness and removal.",
                            "When disabled, control returns at the deadline and the worn form/equipment remain.")
                    .define("ExoskeletonSleep", true);
            takeoverBorrow = builder.comment("Ordinary absorbers only; never applies to forced exoskeletons.")
                    .define("AllowBorrowedControl", true);
            takeoverEscape = builder.comment("One ordinary absorption escape attempt. Exoskeleton takeover NEVER allows struggle.")
                    .define("AllowEscapeAttempt", true);
            takeoverOranges = builder.comment("One compensation after non-hostile ordinary takeover sleep, with a 20-minute cooldown.")
                    .define("NonHostileOrangeCompensation", true);
            takeoverSeconds = builder.defineInRange("OrdinarySeconds", 180, 30, 300);
            takeoverPunitiveSeconds = builder.defineInRange("PunitiveSeconds", 240, 30, 300);
            takeoverExoskeletonSeconds = builder.comment("Awake time before automatic sleep, NOT a voluntary release timer.")
                    .defineInRange("ExoskeletonAwakeSeconds", 30, 5, 60);
            takeoverTransfurAfterSleep = builder
                    .comment("After an ordinary takeover sleep, permanently transfur the player into the carrier's form.",
                            "The carrier's eye colors and style are inherited. Disabled by default: sleep restores and releases the player.")
                    .define("TransfurAfterSleep", false);
            builder.pop();
            builder.comment("Relationship and companion settings").push("RELATIONSHIPS");
            factionPursuit = builder.comment("At minimum faction reputation, allow warned, limited pursuit squads.")
                    .define("FactionPursuit", true);
            independentFactionReputation = builder
                    .comment("Let every faction reputation change independently and allow all rival factions to be allied at once.",
                            "Disabled by default: rival alliances can reduce each other's reputation.",
                            "Enabling this does not restore reputation previously lost to rivalry.")
                    .define("IndependentFactionReputation", false);
            postTransfurTruceSeconds = builder
                    .comment("Seconds of faction ceasefire after a completed transfur or takeover release.",
                            "Set to 0 to disable this protection. Takeover release applies it to every latex faction.")
                    .defineInRange("PostTransfurTruceSeconds", 90, 0, 600);
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
            patPacification = builder
                    .comment("Let a player's pat calm a pursuing latex creature and create a short ceasefire.",
                            "Disabling this keeps ordinary pat reactions and relationship progress,",
                            "but hostile creatures will not stop pursuing because they were patted.")
                    .define("PatPacification", true);
            ordinaryTransfurReversal = builder
                    .comment("Allow ordinary Synergy transfur reversal through negotiation, bonded companions, and sleep.",
                            "Disabling this does not block takeover separation or the Changed /untf and /untransfur commands.")
                    .define("OrdinaryTransfurReversal", true);
            bondProtectiveReleaseRequests = builder
                    .comment("Let cautious, protective, and sensitive companions require repeated release requests",
                            "after repeatedly rescuing their owner from danger. The request count survives closing the radial menu.")
                    .define("ProtectiveReleaseRequests", true);
            allowMultipleBonds = builder
                    .comment("Allow one player to form bonds with several creatures.",
                            "When disabled, voluntary transfur remains available but creates no new bond if the player already has one.")
                    .define("AllowMultipleBonds", false);
            peacefulVillageRelations = builder
                    .comment("Keep Changed creatures, villagers, wandering traders, and village protectors out of mutual combat.",
                            "Additional civilian and protector entity types can be supplied through Changed: Synergy entity tags.")
                    .define("PeacefulVillageRelations", true);
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

            builder.comment("Community settlement and outpost placement").push("COMMUNITIES");
            settlementMinimumSpacing = builder
                    .comment("Minimum horizontal distance in blocks between independently claimed outposts.",
                            "Nearby compatible creatures still reuse an existing community outpost.")
                    .defineInRange("MinimumOutpostSpacing", 48, 16, 256);
            useChangedStructureOutposts = builder
                    .comment("Let provisioners without an outpost claim generated Changed ruins.",
                            "The facility is never claimed, and only empty space is changed.")
                    .define("UseChangedStructureOutposts", true);
            latexBeeHiveOutposts = builder
                    .comment("Let latex-bee provisioners establish an outpost beside a generated Changed beehive.")
                    .define("LatexBeeHiveOutposts", true);
            structureOutpostSearchRadiusChunks = builder
                    .comment("Loaded-chunk search radius for unclaimed Changed ruins and latex beehives.",
                            "Larger values make claims easier to find but increase occasional search cost.")
                    .defineInRange("StructureOutpostSearchRadiusChunks", 6, 2, 12);
            builder.pop();

            builder.comment("Provisioner community trade settings")
                    .push("PROVISIONER_TRADING");
            provisionerTrading = builder
                    .comment("Allow provisioners to exchange resources that were actually delivered to their community.")
                    .define("Enabled", true);
            provisionerTradeReserveMultiplier = builder
                    .comment("Multiplier applied to the share of food and materials kept out of trade.",
                            "At 1.0, communities retain about 70%. Higher values retain more supplies.")
                    .defineInRange("ReserveMultiplier", 1.0D, 0.25D, 4.0D);
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
                            "Disabled by default: they are absorbed instead to avoid creating extra persistent entities.",
                            "Dedicated conversion forms supplied by supported addons are preserved.")
                    .define("AllowMindlessMobTransfur", false);
            randomizeCreatureTransfurMethod = builder
                    .comment("Randomly choose replication or absorption for hostile latex-creature transfurs.",
                            "Only entity attacks and grabs are affected. Organic and single-method creatures keep their native behavior.")
                    .define("RandomizeCreatureTransfurMethod", false);
            behaviourConfigRevision = builder
                    .comment("Internal migration marker for perception and alert defaults.")
                    .defineInRange("BehaviourConfigRevision", 0, 0, 1);
            builder.pop();

            builder.comment("Performance scheduling and diagnostics. Server authoritative.",
                    "Feature switches are intended to isolate expensive AI while diagnosing large populations.")
                    .push("PERFORMANCE");
            performanceDiagnostics = builder
                    .comment("Measure complete Changed-creature AI time and send one compact sample per second to players.")
                    .define("Diagnostics", true);
            staggerBackgroundAi = builder
                    .comment("Distribute optional AI scans across ticks instead of letting large groups scan together.")
                    .define("StaggerBackgroundAi", true);
            backgroundScanInterval = builder
                    .comment("Ticks between expensive idle social, comfort, and community decision scans.",
                            "Lower values react sooner but cost more CPU. One restores near-every-tick evaluation.")
                    .defineInRange("BackgroundScanInterval", 10, 1, 40);
            distantAiThrottling = builder
                    .comment("Run optional AI less often when no player is within the configured range.",
                            "Combat targets and bonded companions remain foreground work.")
                    .define("DistantAiThrottling", true);
            distantAiRange = builder
                    .comment("Player distance beyond which optional AI uses the distant interval multiplier.")
                    .defineInRange("DistantAiRange", 48.0, 16.0, 128.0);
            distantAiIntervalMultiplier = builder
                    .comment("Multiplier applied to optional AI intervals outside the active range.")
                    .defineInRange("DistantAiIntervalMultiplier", 4, 1, 12);
            adaptiveAiBudget = builder
                    .comment("Defer non-critical background decisions after Changed-creature AI exceeds its per-tick budget.",
                            "Deferred work is fairly rotated across later ticks. Combat and companion safety are never deferred.")
                    .define("AdaptiveAiBudget", true);
            latexAiBudgetMs = builder
                    .comment("Soft millisecond budget per server tick for all loaded Changed-creature AI.",
                            "This is a scheduling threshold, not a hard time limit.")
                    .defineInRange("LatexAiBudgetMs", 8.0, 1.0, 40.0);
            communityAi = builder
                    .comment("Enable autonomous community roles, routine destination searches, and comfort decisions.",
                            "Disable temporarily to test whether settlement simulation is the source of lag.")
                    .define("CommunityAi", true);
            companionWorkAi = builder
                    .comment("Enable bonded companion fishing, mining, cave lighting, and related work observation.",
                            "Following, combat, rescue, and direct companion commands remain available when disabled.")
                    .define("CompanionWorkAi", true);
            ambientSocialAi = builder
                    .comment("Enable unfamiliar-human approaches, ambient encounters, and non-bonded friend defence scans.",
                            "Direct interaction, bonds, and companion safety remain available when disabled.")
                    .define("AmbientSocialAi", true);
            enhancedHuntAi = builder
                    .comment("Enable Synergy sight memory, searching, noise alerts, underwater pursuit, and firearm evasion.",
                            "Changed's native targeting remains when disabled.")
                    .define("EnhancedHuntAi", true);
            builder.pop();

            builder.comment("Creature ecology and regional adaptation").push("ECOLOGY");
            pureWhiteWolfAdaptation = builder
                    .comment("Let adult Pure White Latex Wolves develop the male White Latex Wolf appearance",
                            "after travelling far from the White Latex Forest, then return to Pure White on coming home.",
                            "Identity, relationships, faction allegiance and equipment are preserved.")
                    .define("PureWhiteWolfAdaptation", true);
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
                    .comment("Completed non-suit transformations needed to permanently understand telepathic speech.",
                            "Set to 0 to prevent transformations from unlocking telepathy.")
                    .defineInRange("TelepathyUnlockTransfurs", 3, 0, 20);
            builder.pop();
        }
    }
}
