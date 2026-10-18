package net.parkabird.changedsynergy;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

/** Server-side settings for social AI and companion behaviour. */
public final class ChangedSynergyConfig {
    public enum CreatureTransfurMethod {
        NATIVE, RANDOM, ASSIMILATION, ABSORPTION, TAKEOVER
    }

    public static final Common COMMON;
    public static final ForgeConfigSpec SPEC;
    private static final java.util.Map<String, ForgeConfigSpec.ConfigValue<?>> OPTIONS;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
        var options = new java.util.TreeMap<String, ForgeConfigSpec.ConfigValue<?>>();
        collectOptions(SPEC.getValues(), options);
        OPTIONS = java.util.Collections.unmodifiableMap(options);
    }

    private ChangedSynergyConfig() {
    }

    /** Public server settings; migration bookkeeping is deliberately excluded. */
    public static java.util.Map<String, ForgeConfigSpec.ConfigValue<?>> options() { return OPTIONS; }

    private static void collectOptions(com.electronwill.nightconfig.core.UnmodifiableConfig node,
            java.util.Map<String, ForgeConfigSpec.ConfigValue<?>> result) {
        for (Object child : node.valueMap().values()) {
            if (child instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) collectOptions(nested, result);
            else if (child instanceof ForgeConfigSpec.ConfigValue<?> value && value != COMMON.behaviourConfigRevision)
                result.put(String.join(".", value.getPath()), value);
        }
    }

    /** Check list syntax without requiring optional mods to be loaded. */
    public static boolean validBondTokens(Object value) {
        if (!(value instanceof java.util.List<?> entries)) return false;
        var categories = new java.util.HashSet<String>();
        for (Object entry : entries) {
            if (!(entry instanceof String text) || text.length() > 256) return false;
            String[] pair = text.split("=", -1);
            if (pair.length != 2 || !java.util.Set.of("dark", "white", "variant", "organic", "aquatic").contains(pair[0].trim())
                    || !categories.add(pair[0].trim())) return false;
            String[] item = pair[1].split(",", -1);
            if (item.length < 1 || item.length > 2 || !validResourceId(item[0].trim())
                    || "minecraft:air".equals(item[0].trim())) return false;
            try {
                int count = item.length == 1 ? 1 : Integer.parseInt(item[1].trim());
                if (count < 1 || count > 64) return false;
            } catch (NumberFormatException invalid) { return false; }
        }
        return true;
    }

    private static boolean validResourceId(Object value) {
        return value instanceof String id && id.length() <= 256 && id.contains(":")
                && net.minecraft.resources.ResourceLocation.tryParse(id) != null;
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
        public final ForgeConfigSpec.BooleanValue balancedRelationships;
        public final ForgeConfigSpec.IntValue strangerAffectionLimit;
        public final ForgeConfigSpec.IntValue careAffectionAllowance;
        public final ForgeConfigSpec.IntValue careAffectionCooldownSeconds;
        public final ForgeConfigSpec.IntValue giftCooldownSeconds;
        public final ForgeConfigSpec.BooleanValue bondTokensRequired;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> bondTokens;
        public final ForgeConfigSpec.BooleanValue playfulFriendTransfur;
        public final ForgeConfigSpec.BooleanValue ordinaryTransfurReversal;
        public final ForgeConfigSpec.BooleanValue organicSleepReversal;
        public final ForgeConfigSpec.BooleanValue bondProtectiveReleaseRequests;
        public final ForgeConfigSpec.BooleanValue bondedEmergencyRescue;
        public final ForgeConfigSpec.BooleanValue aquaticFriendDrowningRescue;
        public final ForgeConfigSpec.BooleanValue bondedKinConfinement;
        public final ForgeConfigSpec.IntValue bondedKinKillBaseLimit;
        public final ForgeConfigSpec.BooleanValue changedVanillaRelationshipAnimalProtection;
        public final ForgeConfigSpec.BooleanValue companionMonsterAssistOnly;
        public final ForgeConfigSpec.BooleanValue selfDefensePenaltyProtection;
        public final ForgeConfigSpec.BooleanValue allowMultipleBonds;
        public final ForgeConfigSpec.BooleanValue peacefulVillageRelations;
        public final ForgeConfigSpec.BooleanValue nonRivalCombatProtection;
        public final ForgeConfigSpec.BooleanValue factionPursuit;
        public final ForgeConfigSpec.BooleanValue factionPursuitOverworldOnly;
        public final ForgeConfigSpec.BooleanValue independentFactionReputation;
        public final ForgeConfigSpec.IntValue postTransfurTruceSeconds;
        public final ForgeConfigSpec.IntValue settlementMinimumSpacing;
        public final ForgeConfigSpec.IntValue playerWildOutpostMinimumSpacing;
        public final ForgeConfigSpec.BooleanValue creatureOutposts;
        public final ForgeConfigSpec.BooleanValue playerOutposts;
        public final ForgeConfigSpec.IntValue playerOutpostResidents;
        public final ForgeConfigSpec.IntValue playerOutpostCrew;
        public final ForgeConfigSpec.BooleanValue playerOutpostCrewGathering;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> playerOutpostCrewBlockedOres;
        public final ForgeConfigSpec.BooleanValue playerOutpostSupplyWork;
        public final ForgeConfigSpec.BooleanValue playerOutpostHarvesting;
        public final ForgeConfigSpec.BooleanValue playerOutpostHauling;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> playerOutpostHaulingBlockedItems;
        public final ForgeConfigSpec.BooleanValue playerOutpostHunting;
        public final ForgeConfigSpec.BooleanValue playerOutpostFarming;
        public final ForgeConfigSpec.BooleanValue playerOutpostCooking;
        public final ForgeConfigSpec.BooleanValue playerOutpostComposting;
        public final ForgeConfigSpec.BooleanValue playerOutpostMeals;
        public final ForgeConfigSpec.IntValue playerOutpostWorkScanInterval;
        public final ForgeConfigSpec.IntValue playerOutpostActiveWorkers;
        public final ForgeConfigSpec.BooleanValue playerOutpostWorkWhileOwnerOffline;
        public final ForgeConfigSpec.BooleanValue playerOutpostRest;
        public final ForgeConfigSpec.BooleanValue playerOutpostIdleRest;
        public final ForgeConfigSpec.BooleanValue playerOutpostGuardPvp;
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
        public final ForgeConfigSpec.BooleanValue takeoverWakeProne;
        public final ForgeConfigSpec.BooleanValue takeoverNegotiation;
        public final ForgeConfigSpec.BooleanValue takeoverHostileFinale;
        public final ForgeConfigSpec.BooleanValue takeoverOranges;
        public final ForgeConfigSpec.IntValue takeoverSeconds;
        public final ForgeConfigSpec.IntValue takeoverPunitiveSeconds;
        public final ForgeConfigSpec.IntValue takeoverExoskeletonSeconds;
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
        public final ForgeConfigSpec.BooleanValue allowSecondaryTransfur;
        public final ForgeConfigSpec.BooleanValue includeModdedOriginSpecies;
        public final ForgeConfigSpec.BooleanValue allowHumanOriginInstinct;
        public final ForgeConfigSpec.BooleanValue playerRoleSpeech;
        public final ForgeConfigSpec.DoubleValue playerRoleSpeechCooldown;
        public final ForgeConfigSpec.BooleanValue instinctConfinement;
        public final ForgeConfigSpec.IntValue instinctConfinementSeconds;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> disabledOriginForms;
        public final ForgeConfigSpec.EnumValue<CreatureTransfurMethod> creatureTransfurMethod;
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
        public final ForgeConfigSpec.BooleanValue addonAnimalCalls;
        public final ForgeConfigSpec.BooleanValue telepathyEnabled;
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
            takeoverWakeProne = builder.comment("Keep the player prone briefly after waking from a failed takeover escape.")
                    .define("ProneAfterFailedEscape", true);
            takeoverNegotiation = builder.comment("Allow one negotiation attempt during an ordinary takeover.",
                            "A failed punitive negotiation shortens the remaining timer.")
                    .define("AllowNegotiation", true);
            takeoverHostileFinale = builder.comment("Use direct permanent takeover at Distrusted standing and fatal takeover at Hostile standing after aggression.",
                            "When disabled, both standings use nonfatal confinement instead. Applies to new takeovers.")
                    .define("HostileFinale", true);
            takeoverOranges = builder.comment("One compensation after non-hostile ordinary takeover sleep, with a 20-minute cooldown.")
                    .define("NonHostileOrangeCompensation", true);
            takeoverSeconds = builder.defineInRange("OrdinarySeconds", 180, 30, 300);
            takeoverPunitiveSeconds = builder.defineInRange("PunitiveSeconds", 240, 30, 300);
            takeoverExoskeletonSeconds = builder.comment("Awake time before automatic sleep, NOT a voluntary release timer.")
                    .defineInRange("ExoskeletonAwakeSeconds", 30, 5, 60);
            builder.pop();
            builder.comment("Relationship and companion settings").push("RELATIONSHIPS");
            balancedRelationships = builder.comment("Require a deliberate gift to become acquainted; daily care uses a shared allowance and cooldown.")
                    .define("BalancedRelationships", true);
            strangerAffectionLimit = builder.comment("Maximum preliminary affection before a wheel gift establishes a relationship.")
                    .defineInRange("StrangerAffectionLimit", 8, 2, 10);
            careAffectionAllowance = builder.comment("Daily-care affection allowance replenished, not stacked, by an accepted wheel gift.")
                    .defineInRange("CareAffectionAllowance", 8, 0, 60);
            careAffectionCooldownSeconds = builder.comment("Shared affection reward cooldown for patting, direct feeding, play and shared activities. Actions and healing remain available.")
                    .defineInRange("CareAffectionCooldownSeconds", 60, 1, 3600);
            giftCooldownSeconds = builder.comment("Independent cooldown for accepted wheel gifts and bond tokens while BalancedRelationships is enabled.")
                    .defineInRange("GiftCooldownSeconds", 300, 1, 7200);
            bondTokensRequired = builder.comment("Require full affection and a species-themed token before a new bond. Unused tokens are refunded when disabled.")
                    .define("BondTokensRequired", true);
            bondTokens = builder.comment("Bond token entries: category=item_id,count. Categories: dark, white, variant, organic, aquatic. Counts: 1-64; no duplicate categories. Missing categories or unavailable optional-mod items use the default.")
                    .define("BondTokens", java.util.List.of("dark=changed:dark_latex_crystal_fragment,4", "white=minecraft:quartz,1",
                            "variant=minecraft:amethyst_shard,1", "organic=minecraft:golden_apple,1", "aquatic=minecraft:heart_of_the_sea,1"),
                            ChangedSynergyConfig::validBondTokens);
            playfulFriendTransfur = builder.comment("Adult playful friends below Close affection may occasionally attempt assimilation or absorption. Playful and Polite are mutually exclusive behavior traits. A light unarmed warning stops them.")
                    .define("PlayfulFriendTransfur", true);
            factionPursuit = builder.comment("At minimum faction reputation, allow warned, limited pursuit squads.")
                    .define("FactionPursuit", true);
            factionPursuitOverworldOnly = builder.comment("Limit pursuit squad spawning to the Overworld. Disabled by default: squads can spawn in any dimension.")
                    .define("FactionPursuitOverworldOnly", false);
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
            organicSleepReversal = builder
                    .comment("Allow a completed night's sleep to reverse organic transfur.",
                            "OrdinaryTransfurReversal must also be enabled. Permanent TrueTransfur and native-origin protections still apply.")
                    .define("OrganicSleepReversal", true);
            bondProtectiveReleaseRequests = builder
                    .comment("Let cautious, protective, and sensitive companions require repeated release requests",
                            "after repeatedly rescuing their owner from danger. The request count survives closing the radial menu.")
                    .define("ProtectiveReleaseRequests", true);
            bondedEmergencyRescue = builder
                    .comment("Allow bonded latex companions to wrap endangered owners, organic companions to evacuate them,",
                            "and aquatic friends to rescue drowning players when their separate option is enabled.",
                            "Disabling this keeps manual wrapping and ordinary companion defense available.")
                    .define("BondedEmergencyRescue", true);
            aquaticFriendDrowningRescue = builder
                    .comment("Let aquatic friends wrap a player after actual drowning damage.",
                            "BondedEmergencyRescue must also be enabled.")
                    .define("AquaticFriendDrowningRescue", true);
            bondedKinConfinement = builder.comment("Let bonded companions confine owners after repeated witnessed same-faction kills.")
                    .define("BondedKinConfinement", true);
            bondedKinKillBaseLimit = builder.comment("Base number of witnessed same-faction kills before bonded confinement; affection adds tolerance.")
                    .defineInRange("BondedKinKillBaseLimit", 3, 1, 20);
            changedVanillaRelationshipAnimalProtection = builder
                    .comment("Prevent Changed Vanilla creatures with established relationships from infecting animals.",
                            "The separate respected-human farm protection remains active when this is disabled.")
                    .define("ChangedVanillaRelationshipAnimalProtection", true);
            companionMonsterAssistOnly = builder
                    .comment("Restrict companion and friend combat assistance to hostile monsters that the player hits or is hit by.")
                    .define("CompanionMonsterAssistOnly", false);
            selfDefensePenaltyProtection = builder
                    .comment("Do not lower faction reputation or punish witnessed kin kills when a creature initiated the fight.",
                            "Attacking a peaceful creature first never qualifies, even after it retaliates.")
                    .define("SelfDefensePenaltyProtection", true);
            allowMultipleBonds = builder
                    .comment("Allow one player to form bonds with several creatures.",
                            "When disabled, voluntary transfur remains available but creates no new bond if the player already has one.")
                    .define("AllowMultipleBonds", false);
            peacefulVillageRelations = builder
                    .comment("Keep Changed creatures, villagers, wandering traders, and village protectors out of mutual combat.",
                            "Additional civilian and protector entity types can be supplied through Changed: Synergy entity tags.")
                    .define("PeacefulVillageRelations", true);
            nonRivalCombatProtection = builder
                    .comment("End fights between non-rival latex NPCs at 20% health (at least 1 health) instead of allowing lethal damage.",
                            "Disabling this does not change compatriot friendly-fire prevention, bonded safety or player combat rules.")
                    .define("NonRivalCombatProtection", true);
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
            creatureOutposts = builder.comment("Allow wild latex creatures to build or claim new autonomous outposts, including ruins, hives and aquatic caches.",
                            "Disabling preserves existing outposts, their storage and deliveries. Player camps and existing facility storage remain available.")
                    .define("CreatureOutposts", true);
            settlementMinimumSpacing = builder
                    .comment("Minimum horizontal distance in blocks between independently claimed wild outposts.",
                            "Nearby compatible creatures still reuse an existing community outpost.")
                    .defineInRange("MinimumOutpostSpacing", 48, 16, 256);
            playerWildOutpostMinimumSpacing = builder
                    .comment("Minimum horizontal distance in blocks between a player's camp marker and a wild outpost cache when either is established.",
                            "Only affects new placements. Existing outposts are not moved or removed.")
                    .defineInRange("PlayerWildOutpostMinimumSpacing", 48, 16, 256);
            playerOutposts = builder.comment("Allow players to claim a campfire as a home outpost. Existing bell-founded outposts remain supported.")
                    .define("PlayerOutposts", true);
            playerOutpostResidents = builder.comment("Maximum recruited residents per player outpost.")
                    .defineInRange("PlayerOutpostResidents", 16, 1, 64);
            playerOutpostCrew = builder.comment("Maximum residents assigned to follow as a crew.")
                    .defineInRange("PlayerOutpostCrew", 6, 1, 32);
            playerOutpostCrewGathering = builder.comment("Allow travelling crew members assigned mining, foraging or hunting to gather near their owner.",
                            "Uses loaded chunks, mobGriefing, the work scan interval and active-worker limit. Camp Supply Work is independent.")
                    .define("PlayerOutpostCrewGathering", true);
            playerOutpostCrewBlockedOres = builder.comment("Block registry IDs that travelling miners must not mine, for example minecraft:diamond_ore.",
                            "List deepslate and regular variants separately. Does not affect stationary camp gatherers.")
                    .defineListAllowEmpty("PlayerOutpostCrewBlockedOres", java.util.List.<String>of(),
                            value -> validResourceId(value) && ((String)value).contains(":"));
            playerOutpostSupplyWork = builder.comment("Let assigned suppliers collect dropped supplies and harvest nearby resources for a linked chest.")
                    .define("PlayerOutpostSupplyWork", true);
            playerOutpostHarvesting = builder.comment("Let gatherers actively harvest resources suited to their species and faction.",
                            "Haulers still collect loose drops when Supply Work is enabled.")
                    .define("PlayerOutpostHarvesting", true);
            playerOutpostHauling = builder.comment("Let assigned haulers collect loose items in their marked area, or within 32 blocks of the camp when unmarked, and deliver batches to linked storage.",
                            "Supply Work must also be enabled. Farmers, hunters and cooks still collect their own outputs.")
                    .define("PlayerOutpostHauling", true);
            playerOutpostHaulingBlockedItems = builder.comment("Item registry IDs that camp haulers must not pick up, for example minecraft:diamond or changed:orange.",
                            "Applies to hauling only; workers can still collect and return the products of their own jobs.")
                    .defineListAllowEmpty("PlayerOutpostHaulingBlockedItems", java.util.List.<String>of(),
                            value -> validResourceId(value) && ((String)value).contains(":"));
            playerOutpostHunting = builder.comment("Let camp hunters use marked hunting areas and travelling hunters hunt near their owner.",
                            "Named, tame, leashed and baby animals are protected. Camp hunters also need Supply Work; travelling hunters need Crew Gathering.")
                    .define("PlayerOutpostHunting", true);
            playerOutpostFarming = builder.comment("Let assigned farmers harvest mature crops and replant the same crop inside linked farmland.",
                            "Uses actual drops and planting material and respects mobGriefing. Supply Work must also be enabled.")
                    .define("PlayerOutpostFarming", true);
            playerOutpostCooking = builder.comment("Let assigned cooks use campfire recipes and food-only smelting recipes in real campfire slots.",
                            "Supply Work must also be enabled. No food is generated without consuming real ingredients.")
                    .define("PlayerOutpostCooking", true);
            playerOutpostComposting = builder.comment("Let farmers compost surplus planting material when no crop task is available, using a linked composter.",
                            "Supply Work and Farming must also be enabled.")
                    .define("PlayerOutpostComposting", true);
            playerOutpostMeals = builder.comment("Let members sit around the campfire and eat their own food, fetching one from linked storage if needed.",
                            "Prefers nearby linked pillows, chairs and tall cardboard boxes; uses native eating animation.")
                    .define("PlayerOutpostMeals", true);
            playerOutpostWorkScanInterval = builder.comment("Ticks between player-outpost work searches. Higher values lower background AI cost but delay task discovery.")
                    .defineInRange("PlayerOutpostWorkScanInterval", 100, 40, 1200);
            playerOutpostActiveWorkers = builder.comment("Maximum simultaneous active workers per group in one player outpost. Stationary suppliers and travelling gatherers each have this limit, so home production cannot starve crew jobs. Lower values reduce AI and pathfinding load.")
                    .defineInRange("PlayerOutpostActiveWorkers", 4, 1, 32);
            playerOutpostWorkWhileOwnerOffline = builder.comment("Allow camp work while the owner is offline, only in already loaded chunks. Never force-loads chunks.")
                    .define("PlayerOutpostWorkWhileOwnerOffline", false);
            playerOutpostRest = builder.comment("Let player-outpost residents use linked beds at night. Bees can also sleep on linked yellow hive pillows.")
                    .define("PlayerOutpostRest", true);
            playerOutpostIdleRest = builder.comment("Let idle camp residents use linked pillows, office chairs and tall cardboard boxes. Work and orders interrupt this rest. Low performance impact.")
                    .define("PlayerOutpostIdleRest", true);
            playerOutpostGuardPvp = builder.comment("Allow outpost guards to defend against hostile players. Off by default.")
                    .define("PlayerOutpostGuardPvp", false);
            useChangedStructureOutposts = builder
                    .comment("Let provisioners without an outpost claim generated Changed ruins.",
                            "The facility is never claimed, and only empty space is changed.")
                    .define("UseChangedStructureOutposts", true);
            latexBeeHiveOutposts = builder
                    .comment("Let latex-bee provisioners establish a hive-themed outpost inside a generated Changed beehive.")
                    .define("LatexBeeHiveOutposts", true);
            structureOutpostSearchRadiusChunks = builder
                    .comment("Loaded-chunk search radius for unclaimed Changed ruins and latex beehives.",
                            "Larger values make claims easier to find but increase occasional search cost.")
                    .defineInRange("StructureOutpostSearchRadiusChunks", 6, 2, 12);
            builder.pop();

            builder.comment("Provisioner community trade settings")
                    .push("PROVISIONER_TRADING");
            provisionerTrading = builder
                    .comment("Allow provisioner trading: food currency purchases, community-stock-backed food exchanges and relationship-gated collectibles.")
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
            allowSecondaryTransfur = builder
                    .comment("Allow hostile latex creatures to grab and transfur players who are already transfurred.",
                            "Native-origin players are always excluded, regardless of this setting.",
                            "When disabled, those creatures keep fighting the player with ordinary attacks instead.")
                    .define("AllowSecondaryTransfur", true);
            includeModdedOriginSpecies = builder
                    .comment("Include playable transfur species from other mods in starting origin selection.",
                            "Disabled by default. Bosses, excluded creatures, partial/custom/benign forms and special characters remain unavailable.",
                            "This only expands the catalog; starting origin selection is still controlled by its gamerule.")
                    .define("IncludeModdedOriginSpecies", false);
            allowHumanOriginInstinct = builder.comment("Allow transformed human-origin players to use TrueTransfur 2.0 Instinct control while origin selection is enabled.",
                    "Disabled by default. Does not enable TrueTransfur permanent identity, lethal reversal or native rebirth for human origins.")
                    .define("AllowHumanOriginInstinct", false);
            disabledOriginForms = builder
                    .comment("Transfur form IDs excluded from starting origin selection (not entity IDs).",
                            "Example: [\"changed:form_dark_latex_wolf/male\"]. Empty means no additional exclusions.",
                            "Applies to original and modded forms; existing committed origins are retained.")
                    .defineListAllowEmpty("DisabledOriginForms", java.util.List.<String>of(), ChangedSynergyConfig::validResourceId);
            creatureTransfurMethod = builder
                    .comment("Method for hostile latex-creature attacks and grabs: NATIVE, RANDOM, ASSIMILATION, ABSORPTION, or TAKEOVER.",
                            "TAKEOVER uses absorption and the usual faction-standing outcomes; other takeover settings still apply.",
                            "Organic, protected, fusion, and single-method encounters keep their native behavior.")
                    .defineEnum("CreatureTransfurMethod", CreatureTransfurMethod.NATIVE);
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
                    .comment("Let Pure White Latex Wolves display their individual eyes away from White territory",
                            "and hide those eyes on coming home, without replacing their species or body.",
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
                    .comment("Let an enabled Changed Addon Translator interpret creature speech, including when telepathy is disabled.",
                            "Disabling translator support does not grant universal understanding.")
                    .define("UsesTranslator", true);
            addonAnimalCalls = builder
                    .comment("Play Changed Addon's species-appropriate animal calls for some creature reactions when that addon is installed.")
                    .define("AddonAnimalCalls", true);
            telepathyEnabled = builder
                    .comment("Enable understanding through permanent telepathy, transformed bodies, hypnosis and takeover.",
                            "Disabling this pauses unlock progress and uses creature vocalizations instead. Saved progress is retained.",
                            "An enabled supported Translator can still interpret speech; hypnosis and takeover gameplay remain available.")
                    .define("TelepathyEnabled", true);
            telepathyUnlockTransfurs = builder
                    .comment("Completed non-suit transformations needed to permanently understand telepathic speech.",
                            "Set to 0 to prevent transformations from unlocking telepathy.")
                    .defineInRange("TelepathyUnlockTransfurs", 3, 0, 20);
            builder.pop();
            builder.comment("Native-player role speech and safe Instinct-body restraint. Server authoritative.").push("PLAYER_ROLEPLAY");
            playerRoleSpeech = builder.comment("Route native players' role chat to nearby listeners. Global chat remains available.")
                    .define("SpeechEnabled", true);
            playerRoleSpeechCooldown = builder.comment("Seconds between accepted player role messages; independent of NPC dialogue chance and cooldown.")
                    .defineInRange("SpeechCooldownSeconds", 2.0D, 0.5D, 10.0D);
            instinctConfinement = builder.comment("After normal transfur progress, allow a native Instinct player to temporarily suit hostile human players under PvP and friendship rules. Release restores the human; the carrier retains its permanent form and TT mode.")
                    .define("InstinctConfinement", true);
            instinctConfinementSeconds = builder.comment("Initial temporary takeover duration; failed negotiation adds ten seconds, capped at 1.5 times this duration.")
                    .defineInRange("InstinctConfinementSeconds", 30, 5, 120);
            builder.pop();
        }
    }
}
