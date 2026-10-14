# Settings guide

This guide lists server settings, client preferences and world rules for
Changed: Synergy 1.1.4, with their defaults and allowed values.

Server settings live in `config/changed_synergy-common.toml`; client preferences
live in `config/changed_synergy-client.toml`. World rules are saved in world data.

The configuration page separates **Server Settings**, **World Rules** and
**Client Visuals**. Multiplayer server settings display the server's values and
are read-only. Operators use `/changedsynergy config categories`,
`list [category] [page]`, `get <key>`, `set <key> <value>` or `reset <key>`.
Operators can edit world rules through their own tab and press Done; these
changes use the server's ordinary `/gamerule` permission checks. Client settings
are local. See [server configuration](SERVER_CONFIGURATION.md) for syntax and
dependencies, and [world rules](GAMERULES.md) for their detailed scope.

Use the keys below with configuration commands or in the corresponding file.
Settings are grouped here in the same categories used by the menu.

Camp production requires Creature Life, Player Outposts and Supply Work.
Individual gathering/hunting/farming/cooking/hauling switches apply beneath
these gates; composting also requires farming. Disabling hauling leaves each
worker's own output collection intact. Vanilla `mobGriefing` remains applicable
to block-modifying work. Offline work does not load chunks.

Companion work requires the Bond System, Creature Life and Companion Work AI.
Hostility pathfinding uses NpcAI and Enhanced Hunt AI, independently of creature
life. Pausing friendship or bonds retains memories and matching camp members.
Following, combat and emergency rescue have separate controls.

Reputation initialization affects new accounts. Capture-kind, duration and
final-outcome settings are evaluated at encounter creation; changing them does
not rewrite an existing takeover's outcome. Disabling the takeover master
switch safely ends active sessions. Token requirements can be turned off to
refund unused reservations once. Display settings do not erase world or social
data. TrueTransfur-specific origins require a compatible installed TrueTransfur.

Player role speech and names require an active native origin. Expressions use
the existing emote rule; player friendship uses the friendship rule and shares
the care and gift reward settings. Instinct-player takeover also requires PvP,
takeover and grabbing permission. Disabling it releases active victims.
Human-Origin Instinct Control is a separate default-off Behaviour option: with
origin selection enabled, it permits TT 2.0 control for ordinarily transformed
human origins without permanent transfur, lethal reversal or native rebirth.

Formation, relationship damage filtering and companion orders are saved
separately for each player and companion.
These orders remain subordinate to camp duty, server policy and safety. Mount
configuration is equipment management (saddle/chest), not a global AI setting.

Personal formation offers Close, Standard (default) and Loose. Damage filtering
offers Friends (default), All creatures or None. Companion controls select
following/waiting, target group, kill/transfur preference, attack conditions and
fishing/mining favors; their choices are saved for that companion. Camp role, primary/backup job, shared meals and raw-food/seed reserves
are saved per camp and can be managed through its menu or `/changedsynergy outpost`.
Composting is part of Farming, not a separate job. No companion or camp setting grants
permission to ignore a disabled server feature.


## Server settings

### Performance and Diagnostics

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `PERFORMANCE.Diagnostics` | `true` | true / false | Performance Diagnostics [Low impact]: Shows updated server and creature performance readings once per second. |
| `PERFORMANCE.StaggerBackgroundAi` | `true` | true / false | Stagger Background AI [High impact]: Spreads optional creature scans across different ticks to remove population-wide lag spikes. Recommended on. |
| `PERFORMANCE.BackgroundScanInterval` | `10` | 1 ~ 40 | Background Scan Interval [High impact]: Ticks between expensive idle decisions. Higher values improve server smoothness but make ambient reactions slower. |
| `PERFORMANCE.DistantAiThrottling` | `true` | true / false | Throttle Distant AI [High impact]: Runs optional AI less often when no player is nearby. Combat targets and bonded companions remain responsive. |
| `PERFORMANCE.DistantAiRange` | `48.0` | 16.0 ~ 128.0 | Active AI Range [Medium impact]: Within this distance of any player, optional AI runs at its normal configured interval. Smaller ranges reduce CPU use. |
| `PERFORMANCE.DistantAiIntervalMultiplier` | `4` | 1 ~ 12 | Distant Interval Multiplier [High impact]: Multiplies optional AI intervals outside the active range. Higher values save more CPU in loaded background areas. |
| `PERFORMANCE.AdaptiveAiBudget` | `true` | true / false | Adaptive AI Budget [High impact]: Defers non-critical background decisions after latex AI exceeds the soft tick budget. Deferred entities rotate fairly; combat and companion safety are never deferred. |
| `PERFORMANCE.LatexAiBudgetMs` | `8.0` | 1.0 ~ 40.0 | Latex AI Budget [High impact]: Soft milliseconds per server tick for all loaded Changed-creature AI. Lower values protect tick rate more aggressively; this cannot limit Changed's mandatory base AI. |

### AI Feature Switches

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `PERFORMANCE.CommunityAi` | `true` | true / false | Community Activity AI [High impact]: Controls autonomous community roles, routine destination searches, and comfort activities. Disable to isolate settlement simulation during lag diagnosis. |
| `PERFORMANCE.CompanionWorkAi` | `true` | true / false | Companion Work AI [High impact]: Controls native Changed, Addon and Synergy companion fishing, mining and cave lighting. Requires the Bond System and Creature Life world rules. Following, combat, rescue and camp production have separate controls. |
| `PERFORMANCE.AmbientSocialAi` | `true` | true / false | Ambient Social Scans [Medium impact]: Controls unfamiliar-human approaches, ambient encounters, and non-bonded friend defence scans. Direct interaction, bonds, and companion safety remain active. |
| `PERFORMANCE.EnhancedHuntAi` | `true` | true / false | Enhanced Hunt AI [Medium impact]: Controls Synergy sight memory, searching, noise alerts, underwater pursuit, and firearm evasion. Changed's native target AI remains available. |

### Friendship and Bond Growth

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `RELATIONSHIPS.BalancedRelationships` | `true` | true / false | Balanced Relationship Growth: Patting and feeding build initial familiarity; a wheel gift is needed to become acquainted. Turning this off restores legacy growth without deleting relationships. Bond tokens are controlled separately. |
| `RELATIONSHIPS.StrangerAffectionLimit` | `8` | 2 ~ 10 | Stranger Affection Limit: Maximum affection before becoming acquainted. Reach this amount, then give a suitable wheel gift to establish the relationship and receive a name. Requires balanced growth. |
| `RELATIONSHIPS.CareAffectionAllowance` | `8` | 0 ~ 60 | Daily Interaction Allowance: After becoming acquainted, each accepted wheel gift refills this shared allowance for patting, feeding, play and shared activities. Unused allowance does not stack. Requires balanced growth. |
| `RELATIONSHIPS.CareAffectionCooldownSeconds` | `60` | 1 ~ 3600 | Care Affection Cooldown (seconds): Shared affection cooldown for patting, direct feeding, play and shared activities. Healing and interaction animations still work during cooldown. Gift cooldown is independent. Requires balanced growth. |
| `RELATIONSHIPS.GiftCooldownSeconds` | `300` | 1 ~ 7200 | Gift Cooldown (seconds): Time between accepted wheel gifts, including bond tokens. Failed or unsuitable gifts consume nothing. Direct feeding has no action cooldown. Requires balanced growth. |
| `RELATIONSHIPS.BondTokensRequired` | `true` | true / false | Require Bond Tokens: First-time bonds require 60 affection and the species-group token. Former companions can reunite without another token. Turning this off returns unused reserved tokens once; balanced growth still requires full affection. |
| `RELATIONSHIPS.BondTokens` | `["dark=changed:dark_latex_crystal_fragment,4","white=minecraft:quartz,1","variant=minecraft:amethyst_shard,1","organic=minecraft:golden_apple,1","aquatic=minecraft:heart_of_the_sea,1"]` | list | Bond Tokens: Entries: group=namespace:item,count (count 1–64). Groups: aquatic, organic, dark, white, variant; one entry per group. Separate entries with semicolons. Invalid syntax is rejected. Missing groups or unavailable optional-mod items use default tokens. |

### Relationships and Companions

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `RELATIONSHIPS.TamedCompanionRegeneration` | `0` | 0 ~ 5 | Native Pet Regeneration: Regeneration level for native Changed pets. Zero disables it. |
| `RELATIONSHIPS.OrdinaryTransfurReversal` | `true` | true / false | Ordinary Transfur Reversal: Allow Synergy transfur reversal through negotiation, bonded companions, and sleep. Takeover separation and the /untf and /untransfur recovery commands remain available when disabled. |
| `RELATIONSHIPS.ProtectiveReleaseRequests` | `true` | true / false | Protective Release Requests: After repeated danger rescues, cautious, protective, and sensitive companions may ask the player to request release more than once. Each request closes the radial menu; reopen it to ask again. |
| `RELATIONSHIPS.BondedEmergencyRescue` | `true` | true / false | Bonded Emergency Rescue: Allow bonded latex companions to wrap endangered owners, organic companions to evacuate them, and aquatic friends to rescue drowning players when enabled separately. Manual wrapping and ordinary defense remain available. |
| `RELATIONSHIPS.AquaticFriendDrowningRescue` | `true` | true / false | Aquatic Friend Drowning Rescue: Let aquatic friends wrap a player after actual drowning damage. Bonded Emergency Rescue must also be enabled. |
| `RELATIONSHIPS.BondedKinConfinement` | `true` | true / false | Bonded Kin Confinement: Let bonded companions confine owners after repeated witnessed same-faction kills. |
| `RELATIONSHIPS.BondedKinKillBaseLimit` | `3` | 1 ~ 20 | Bonded Kin Kill Threshold: Base number of witnessed same-faction kills before confinement. Higher affection increases tolerance. |
| `RELATIONSHIPS.ChangedVanillaRelationshipAnimalProtection` | `true` | true / false | Changed Vanilla Relationship Animal Protection: Prevent Changed Vanilla creatures with established relationships from infecting animals. Respected-human farm protection remains separate. |
| `RELATIONSHIPS.CompanionMonsterAssistOnly` | `false` | true / false | Monster Combat Assistance Only: Friends and companions assist only against hostile monsters when you hit them or they hit you. |
| `RELATIONSHIPS.SelfDefensePenaltyProtection` | `true` | true / false | Protect Self-defence from Relationship Penalties: Fighting a creature that attacked first does not reduce faction reputation or trigger witnesses' relationship and confinement penalties. Starting the fight yourself still counts. |
| `RELATIONSHIPS.AllowMultipleBonds` | `false` | true / false | Multiple Bonds: Allow several bonded companions. When off, voluntary transfur still works without creating another bond. |

### Factions and Ceasefires

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `RELATIONSHIPS.FactionPursuit` | `true` | true / false | Faction Pursuit Squads: At -100 reputation, a faction may send a warned, limited pursuit squad. Respects mob spawning, Peaceful difficulty and post-transfur truces. |
| `RELATIONSHIPS.FactionPursuitOverworldOnly` | `false` | true / false | Pursuit Squads: Overworld Only: When enabled, pursuit squads spawn only in the Overworld. By default, they can spawn in any dimension. |
| `RELATIONSHIPS.IndependentFactionReputation` | `false` | true / false | Independent Faction Reputation: When enabled, gaining reputation with one faction never reduces a rival's reputation, so you can ally with every faction. Existing reputation losses are not restored. |
| `RELATIONSHIPS.PostTransfurTruceSeconds` | `90` | 0 ~ 600 | Post-transfur Ceasefire: Seconds before latex factions may become hostile again after a transfur or takeover release. Set to 0 to disable it. |
| `RELATIONSHIPS.PeacefulVillageRelations` | `true` | true / false | Peaceful Village Relations: Prevents mutual combat between Changed creatures and villagers, wandering traders, iron golems, or compatible village protectors. Enabled by default. |

### Greetings and Social Behaviour

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `RELATIONSHIPS.NpcAwarenessRange` | `32.0` | 8.0 ~ 96.0 | Relationship Awareness Range: Maximum distance used when evaluating nearby players and creature relationships. |
| `RELATIONSHIPS.PatPacification` | `true` | true / false | Pat Pacification: Allow a player's pat to stop a latex creature's pursuit and create a short ceasefire. Ordinary pat reactions and relationship progress still work when disabled. |
| `RELATIONSHIPS.PlayfulFriendTransfur` | `true` | true / false | Playful Friends’ Transfur Attempts: Adult playful friends below 28 affection may occasionally try a friendly transfur. Playful and Polite are mutually exclusive behavior traits and do not select dialogue. A light bare-handed warning interrupts the attempt and keeps them settled for five minutes without reputation loss. |
| `RELATIONSHIPS.PoliteHumanInteraction` | `true` | true / false | Greeting System: Polite individuals may greet unfamiliar humans before deciding whether to hunt. |
| `RELATIONSHIPS.PoliteApproachRange` | `12.0` | 4.0 ~ 32.0 | Greeting Pathfinding Range: Maximum distance at which a polite individual begins a cautious greeting. |
| `RELATIONSHIPS.PoliteApproachSpeed` | `0.42` | 0.05 ~ 1.5 | Greeting Pathfinding Speed: Navigation speed used while approaching a human politely. |
| `RELATIONSHIPS.PoliteResponseSeconds` | `14` | 4 ~ 60 | Greeting Response Time: Seconds a polite individual waits for the human to respond with a pat. |
| `RELATIONSHIPS.PoliteUnansweredLimit` | `2` | 1 ~ 6 | Unanswered Greeting Limit: Unanswered greetings before the individual returns to its usual attitude. |

### Player roleplay

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `PLAYER_ROLEPLAY.SpeechEnabled` | `true` | true / false | Native player role speech: Use nearby creature dialogue for native players in role-chat mode. Global chat remains available. |
| `PLAYER_ROLEPLAY.SpeechCooldownSeconds` | `2.0` | 0.5 ~ 10.0 | Player speech interval: Seconds between role messages; independent of NPC dialogue cooldown. |
| `PLAYER_ROLEPLAY.InstinctConfinement` | `true` | true / false | Instinct player takeover: Native Instinct players build normal transfur progress before temporarily suiting hostile human players under PvP rules. Release restores the human form; no fatal takeover occurs. |
| `PLAYER_ROLEPLAY.InstinctConfinementSeconds` | `30` | 5 ~ 120 | Initial takeover duration: Seconds before release. Failed negotiation adds ten seconds, capped at 1.5 times this initial duration. |

### Player Camp Management

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `COMMUNITIES.PlayerOutposts` | `true` | true / false | Player Outposts [Low impact]: Allow players to establish camps around campfires and manage trusted creatures. Requires the Creature Life world rule. Disabling it pauses camps and preserves their membership. Existing bell camps remain valid. |
| `COMMUNITIES.PlayerOutpostResidents` | `16` | 1 ~ 64 | Player Outpost Resident Limit: Maximum camp members: 16 by default, configurable from 1 to 64. Existing saved limits and members are preserved. |
| `COMMUNITIES.PlayerOutpostCrew` | `6` | 1 ~ 32 | Active Crew Limit: Maximum members following the owner at once: 6 by default, configurable from 1 to 32. |
| `COMMUNITIES.PlayerOutpostCrewGathering` | `true` | true / false | Travelling Crew Gathering: Allow crew mining, foraging and hunting near the owner. Searches share the work interval and active-worker limit, use loaded chunks and respect mobGriefing. Disabling this keeps following and saved cargo. Camp Supply Work is separate; Hunting also controls crew hunting. |
| `COMMUNITIES.PlayerOutpostCrewBlockedOres` | `[]` | list | Crew Mining Blocklist: Block IDs that travelling miners must leave alone, separated by commas. For example minecraft:diamond_ore,minecraft:deepslate_diamond_ore. Empty allows all suitable ore. Applies immediately, including mining already in progress; stationary camp gathering is unchanged. |
| `COMMUNITIES.PlayerOutpostWorkWhileOwnerOffline` | `false` | true / false | Camp Work While Owner Is Offline: Continue camp work while the owner is offline, only in chunks that are already loaded. Does not keep chunks loaded. Disabled by default. |
| `COMMUNITIES.PlayerOutpostGuardPvp` | `false` | true / false | Outpost PvP Defense [Low impact]: Let guards defend the owner against an attacking player within the outpost. Disabled by default. |

### Camp Production and Hauling

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `COMMUNITIES.PlayerOutpostSupplyWork` | `true` | true / false | Player Outpost Supply Work [Low impact]: Let suppliers perform assigned work and deliver real resources to linked storage. Haulers collect leftover drops; other workers return their own produce before working again. |
| `COMMUNITIES.PlayerOutpostHarvesting` | `true` | true / false | Player Outpost Harvesting [Low impact]: Let assigned suppliers actively gather resources suited to their species and faction. Supply Work must also be enabled. |
| `COMMUNITIES.PlayerOutpostHauling` | `true` | true / false | Camp Hauling: Haulers collect all eligible loose items in their marked area, or within 32 blocks of the camp when unmarked, and deliver batches to storage. Requires Supply Work. The pickup blocklist applies only to hauling. |
| `COMMUNITIES.PlayerOutpostHaulingBlockedItems` | `[]` | list | Hauling Pickup Blocklist: Item IDs that haulers must leave on the ground, separated by commas (for example minecraft:diamond). Empty allows all items. This does not block other workers from returning their own products. |
| `COMMUNITIES.PlayerOutpostHunting` | `true` | true / false | Camp Hunting [Moderate impact]: Camp hunters use marked hunting areas; travelling hunters hunt near their owner. Young, named, tame and leashed animals are protected. Camp hunters also need Supply Work; travelling hunters need Crew Gathering. |
| `COMMUNITIES.PlayerOutpostFarming` | `true` | true / false | Camp Farming [Low impact]: Harvest and replant mature crops, collect a batch, then store produce before resting. Supports suitable Farmer’s Delight crops. Requires Supply Work and mobGriefing. |
| `COMMUNITIES.PlayerOutpostCooking` | `true` | true / false | Campfire Cooking [Low impact]: Cooks fetch real ingredients and cook food in four campfire slots, retaining the raw-food reserve. Supports campfire and food-only smelting recipes. Requires Supply Work and mobGriefing. |
| `COMMUNITIES.PlayerOutpostComposting` | `true` | true / false | Camp Composting [Low impact]: Farmers compost surplus planting material when no mature crop needs harvesting, retaining the camp’s seed reserve. Requires Supply Work, Farming and mobGriefing; this is part of farming, not a separate job. |
| `COMMUNITIES.PlayerOutpostWorkScanInterval` | `100` | 40 ~ 1200 | Camp Work Search Interval [Performance]: Ticks between work searches. A higher value reduces background searches but makes workers react more slowly. |
| `COMMUNITIES.PlayerOutpostActiveWorkers` | `4` | 1 ~ 32 | Active Camp Workers [Performance]: Maximum simultaneous workers per group in each camp. Stationary suppliers and travelling gatherers each have this limit, so home production cannot block crew jobs. Lower limits reduce searches and pathfinding. |

### Camp Rest and Shared Meals

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `COMMUNITIES.PlayerOutpostRest` | `true` | true / false | Player Outpost Rest [Low impact]: Let residents sleep in separate linked beds at night. Latex bees can also use a linked yellow hive pillow. |
| `COMMUNITIES.PlayerOutpostIdleRest` | `true` | true / false | Camp Idle Rest [Low impact]: Idle residents may use linked pillows, office chairs and tall cardboard boxes. New work, meals and movement orders interrupt their rest; suppliers choose points near their work area. |
| `COMMUNITIES.PlayerOutpostMeals` | `true` | true / false | Camp Shared Meals [Low impact]: Members sit around the campfire, preferring linked rest points. They eat food from their own inventory, fetching a serving from camp storage if needed, then rest briefly. |

### Wild Communities and Outposts

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `COMMUNITIES.MinimumOutpostSpacing` | `48` | 16 ~ 256 | Minimum Wild Outpost Spacing: Minimum horizontal distance in blocks between independently claimed wild outposts. Compatible nearby creatures still share an existing outpost. |
| `COMMUNITIES.PlayerWildOutpostMinimumSpacing` | `48` | 16 ~ 256 | Player / Wild Outpost Spacing: Minimum horizontal distance from a player camp marker to a new wild outpost cache. Existing outposts stay in place. |
| `COMMUNITIES.CreatureOutposts` | `true` | true / false | Autonomous Creature Outposts: Allow wild creatures to build or claim new outposts, including ruins, hives and aquatic caches. Off preserves existing outposts, storage and deliveries. Player camps and existing facility storage are unaffected. |
| `COMMUNITIES.UseChangedStructureOutposts` | `true` | true / false | Claim Changed Ruins [Low impact]: Provisioners without an outpost can claim generated Changed ruins and place a compact community store in empty space. The facility is always excluded. |
| `COMMUNITIES.LatexBeeHiveOutposts` | `true` | true / false | Latex Bee Hive Outposts [Low impact]: Latex-bee provisioners can establish a small community store beside a generated Changed beehive. |
| `COMMUNITIES.StructureOutpostSearchRadiusChunks` | `6` | 2 ~ 12 | Structure Search Radius [Low impact]: Loaded-chunk search radius for unclaimed Changed ruins and beehives. Larger values increase the occasional search cost. |

### Provisioner Trading

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `PROVISIONER_TRADING.Enabled` | `true` | true / false | Enable Provisioner Trading: Enable food-currency trades for materials, exchanges backed by community stock, and relationship-gated collectibles. |
| `PROVISIONER_TRADING.ReserveMultiplier` | `1.0` | 0.25 ~ 4.0 | Community Reserve Multiplier: Scales the share kept out of trade. At 1.0, the community retains about 70% of each supply category. |

### Creature Ecology

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `ECOLOGY.PureWhiteWolfAdaptation` | `true` | true / false | Pure White Wolf Regional Adaptation: Adult Pure White Latex Wolves adapt to a male White Latex Wolf form when far from their forest, and return to Pure White on coming home. This also changes Pure White players' appearance and vision, while preserving their actual species, abilities and faction. |

### Starting Species List

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `BEHAVIOUR.IncludeModdedOriginSpecies` | `false` | true / false | Include Other Mods' Origin Species: Add playable transfur species from other installed mods to starting origin selection. Disabled by default. Bosses and excluded, partial, custom, benign or special-character forms remain unavailable. The starting origin gamerule still controls whether selection is enabled. |
| `BEHAVIOUR.AllowHumanOriginInstinct` | `false` | true / false | Instinct mode for human origins: Allow transformed human-origin players to use TrueTransfur 2.0 Instinct while starting origins are enabled. Default off; does not enable permanent transfur, lethal reversal or native rebirth. |
| `BEHAVIOUR.DisabledOriginForms` | `[]` | list | Disabled Starting Forms: Exclude starting forms by their full transfur form ID, separated by commas (e.g. changed:form_dark_latex_wolf/male). Leave empty for no additional exclusions. Applies to original and modded forms; existing origins are retained. On servers, edit BEHAVIOUR.DisabledOriginForms in changed_synergy-common.toml or use the server config command. |

### Perception and Pursuit

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `BEHAVIOUR.VisualAcquisitionRange` | `18.0` | 6.0 ~ 48.0 | Visual Acquisition Range: Base distance for first noticing a player. Line of sight and field of view still apply. |
| `BEHAVIOUR.MaximumPursuitRange` | `28.0` | 8.0 ~ 64.0 | Maximum Pursuit Range: Base distance at which a creature keeps pursuing a confirmed target. |
| `BEHAVIOUR.LoseSightSeconds` | `2` | 1 ~ 30 | Lose-Sight Delay: Seconds without line of sight before a pursuer searches the last sighting. |
| `BEHAVIOUR.SearchSeconds` | `8` | 2 ~ 120 | Search Duration: Seconds spent investigating the last seen or heard player position. |
| `BEHAVIOUR.AlertRadius` | `12.0` | 0.0 ~ 96.0 | Shared Alert Radius: Distance over which compatible nearby creatures share a confirmed sighting. |
| `BEHAVIOUR.SearchSpeed` | `0.45` | 0.05 ~ 3.0 | Search Speed: Navigation speed used only while investigating or searching. |
| `BEHAVIOUR.FirearmGunshotRadius` | `48.0` | 0.0 ~ 128.0 | Fallback Gunshot Radius: Used by firearm integrations without their own sound range. TACZ supplies its own value. |
| `BEHAVIOUR.FirearmEvasion` | `true` | true / false | Firearm Evasion: Hostile creatures may move laterally while pursuing an armed player. |

### Transfur Methods and Grabs

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `BEHAVIOUR.HostileGrabAttemptChance` | `0.65` | 0.0 ~ 1.0 | Hostile Grab QTE Chance: Chance that an eligible hostile grab starts its escape QTE. |
| `BEHAVIOUR.OrganicHostileGrabAttemptChance` | `0.85` | 0.0 ~ 1.0 | Organic Grab QTE Chance: Separate grab chance for organic creatures, whose assimilation relies on physical grabs. |
| `BEHAVIOUR.AllowMindlessMobTransfur` | `false` | true / false | Allow Mindless Mob Transfur: Let latex creatures fully transfur mindless mobs such as zombies and skeletons. Disabled by default so they are absorbed instead, reducing the number of persistent entities created. Dedicated conversion forms from supported addons are preserved. |
| `BEHAVIOUR.AllowSecondaryTransfur` | `true` | true / false | Secondary Transfur of Players: Allow hostile creatures to grab and transfur players who are already transfurred. When disabled, they attack instead. Native-origin players are always exempt from secondary transfur. |
| `BEHAVIOUR.CreatureTransfurMethod` | `"NATIVE"` | NATIVE, RANDOM, ASSIMILATION, ABSORPTION, TAKEOVER | Creature Transfur Method: Choose native behavior, a per-encounter random method, assimilation, absorption, or takeover for hostile creature attacks and grabs. Takeover uses normal faction-standing outcomes and requires takeover to be enabled. Unsupported, organic, protected, and fusion encounters keep their native behavior. |

### Body Control

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `TAKEOVER.Enabled` | `true` | true / false | Absorption Takeover System: Allow qualifying absorption and forced exoskeleton encounters to temporarily take control of the player. Disable to prevent Synergy takeovers; active takeover sessions end safely. |
| `TAKEOVER.PunitiveCapture` | `true` | true / false | Aggression-triggered Takeovers: Allow a latex creature to take control after the player attacks it or provokes its faction. |
| `TAKEOVER.CompetitiveCapture` | `true` | true / false | Competitive Takeovers: Allow competitive or show-off latex creatures to take control after absorption. |
| `TAKEOVER.ForcedExoskeleton` | `true` | true / false | Exoskeleton Takeover: Allow an exoskeleton to take control of a Benign Wolf or Orca player when its takeover conditions are met. |
| `TAKEOVER.ExoskeletonSleep` | `true` | true / false | Exoskeleton Unconsciousness: End an exoskeleton takeover with unconsciousness and removal. When disabled, control simply returns when the timer expires. |
| `TAKEOVER.AllowBorrowedControl` | `true` | true / false | Temporary Player Control: Allow players in ordinary takeovers to request a short period of movement control. |
| `TAKEOVER.AllowEscapeAttempt` | `true` | true / false | Escape Attempt: Allow one confirmed escape attempt during an ordinary takeover. This never applies to forced exoskeleton control. |
| `TAKEOVER.ProneAfterFailedEscape` | `true` | true / false | Prone After Failed Escape: After a failed takeover escape puts you to sleep, remain prone briefly upon waking. Enabled by default. |
| `TAKEOVER.AllowNegotiation` | `true` | true / false | Takeover Negotiation: Allow one negotiation attempt during an ordinary takeover. A failed punitive attempt shortens the remaining timer. |
| `TAKEOVER.HostileFinale` | `true` | true / false | Severe Aggression Takeover Outcomes: After player aggression, Distrusted standing leads to permanent transfur and Hostile standing to consciousness loss. When disabled, both use temporary confinement. Applies to new takeovers. |
| `TAKEOVER.NonHostileOrangeCompensation` | `true` | true / false | Friendly Takeover Orange Compensation: After a personality-driven friendly takeover ends, provide three oranges at most once every 20 minutes. |
| `TAKEOVER.OrdinarySeconds` | `180` | 30 ~ 300 | Ordinary Duration: Duration in seconds for a non-punitive ordinary takeover. |
| `TAKEOVER.PunitiveSeconds` | `240` | 30 ~ 300 | Aggression Takeover Duration: Duration in seconds for aggression-triggered confinement, direct, and punitive takeovers. |
| `TAKEOVER.ExoskeletonAwakeSeconds` | `30` | 5 ~ 60 | Exoskeleton Awake Time: Seconds before forced exoskeleton control reaches its configured ending. |

### Dialogue Frequency

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `DIALOGUE.Range` | `32.0` | 4.0 ~ 96.0 | Dialogue Range: Maximum distance at which a player can receive creature dialogue. |
| `DIALOGUE.CooldownSeconds` | `12` | 2 ~ 120 | Dialogue Cooldown: Minimum seconds between messages from the same speaker to the same listener. |
| `DIALOGUE.Chance` | `0.72` | 0.0 ~ 1.0 | Dialogue Chance: Chance that an eligible state change produces a line. |
| `DIALOGUE.PersonalityLineChance` | `0.68` | 0.0 ~ 1.0 | Personality Line Chance: Chance that a suitable cue uses the individual's dominant-trait line. |

### Speech Understanding and Calls

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `DIALOGUE.UsesTranslator` | `true` | true / false | Addon Translator Support: An enabled Changed Addon Plus Translator interprets creature speech, even when telepathy is disabled. Turning this off disables the item bypass; it does not grant everyone telepathy. |
| `DIALOGUE.AddonAnimalCalls` | `true` | true / false | Addon Animal Calls: Let compatible creatures use Changed Addon's animal calls for some mood-dependent reactions. |
| `DIALOGUE.TelepathyEnabled` | `true` | true / false | Enable Telepathy: Allow permanent, transformed-body, hypnosis and takeover telepathic understanding. Turning this off pauses unlock progress and uses species vocalizations, while retaining saved progress. Supported enabled Translators still work; hypnosis and takeover gameplay are unchanged. |
| `DIALOGUE.TelepathyUnlockTransfurs` | `3` | 0 ~ 20 | Telepathy Unlock Transfurs: Completed standard transfurs needed for permanent telepathy. Set to 0 to prevent new unlocks while retaining existing understanding. Use Enable Telepathy to disable all telepathic understanding, including already unlocked players. |

### Changed Addon Plus Compatibility

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `RELATIONSHIPS.RespectPacifiedLatexes` | `true` | true / false | Honor the Pacified Effect: Unarmed latex creatures with Changed Addon Plus's Pacified effect will not chase or attack players. |
| `RELATIONSHIPS.PacifyTamedCompanions` | `true` | true / false | Continuously Pacify Native Pets: Continuously refresh Pacified on native Changed pets so they do not lock onto targets by mistake. Requires Changed Addon Plus. |

## Client preferences

### Transfur Feedback

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `LEGACY TRANSFUR VISUALS.LegacyTransfurScreenEffect` | `false` | true / false | Progress Screen Effect: Optional: show the theme-coloured transfur mask that grows inward with progress. Leave this off to keep Changed's current display. |
| `LEGACY TRANSFUR VISUALS.LegacyTransfurSkinEffect` | `false` | true / false | Progress Skin Coating: Optional: show staged latex coating on partially transfurred skins and first-person arms. Leave this off to keep Changed's current display. |
| `LEGACY TRANSFUR VISUALS.LegacyTransfurScreenOpacity` | `1.0` | 0.0 ~ 1.0 | Progress Effect Opacity: Maximum opacity of the transfur progress screen effect. |

### Pure White Vision

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `PURE WHITE VISION.DisablePureWhiteVisionOverlays` | `false` | true / false | Remove Pure White Vision Overlays: Removes the reduced-vision veil and Synergy's pure-white consensus screen effect. Target outlines and gameplay mechanics remain available. |

### Friendly Wrapping

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `FRIENDLY SUIT VISUALS.FriendlySuitVignette` | `true` | true / false | Friendly Wrapping Vignette: Show a soft theme-coloured edge effect while wrapped by a pet or bonded creature. |
| `FRIENDLY SUIT VISUALS.FriendlySuitVignetteOpacity` | `0.25` | 0.0 ~ 0.5 | Wrapping Vignette Opacity: Opacity of the friendly wrapping edge effect. |

### Telepathy and Territory HUD

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `TELEPATHY.TelepathicDanmaku` | `true` | true / false | On-screen Dialogue: Show ordinary creature dialogue in the screen overlay. Disable it to use chat instead. |
| `TELEPATHY.PopupHeightOffset` | `0.0` | -0.75 ~ 0.75 | Dialogue Popup Height Offset: Vertical offset as a fraction of screen height. Negative moves up, positive moves down; 0 keeps the original placement. |
| `TELEPATHY.DanmakuHeightOffset` | `0.0` | -0.75 ~ 0.75 | Scrolling Dialogue Height Offset: Moves scrolling dialogue independently of popups. Negative moves up, positive moves down; 0 keeps the original placement. |
| `TELEPATHY.DialogueDisplayMode` | `"AUTO"` | AUTO, POPUP, DANMAKU | Dialogue Display Style: Choose random popups or horizontal danmaku. Auto uses popups for English and danmaku for CJK languages. |
| `TELEPATHY.TerritoryHud` | `true` | true / false | Territory HUD: Show biome entry subtitles and persistent information for facility areas. |

### Mechanic Hints

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `TUTORIAL HINTS.MechanicHints` | `true` | true / false | Mechanic Hints: Show one-time hints for Synergy's important interactions. |
| `TUTORIAL HINTS.NegotiationNextStepHint` | `true` | true / false | Negotiation Next-Step Hint: Show a suggested next argument during negotiation. Suggestions can be wrong; offering food makes them much more reliable. |

### Struggle Presentation

| Key | Default | Allowed range / choices | Purpose |
| --- | --- | --- | --- |
| `QTE VISUALS.QteAnimations` | `true` | true / false | Animated Struggle Interfaces: Enable animated grab QTE and hypnosis-struggle presentation. |
| `QTE VISUALS.ReducedQteMotion` | `false` | true / false | Reduced Struggle Motion: Keep fades and input feedback while removing shake, drift, and large scaling motion. |
| `QTE VISUALS.ExoskeletonHypnosisVisual` | `true` | true / false | Exoskeleton Hypnosis Visual: Show the hypnosis visual and status log on this client while wearing an exoskeleton. |

## World rules

| Rule | Default | Purpose |
| --- | --- | --- |
| `changedSynergyNativeOrigins` | `true` | Starting origin selection: Offer human or native Changed-species origins to new players. Native origins require TrueTransfur. While enabled, permanent-body rules apply only to native players; Human-Origin Instinct Control separately permits human control mode. Turning this off restores TrueTransfur's default scope and retains saved identities. |
| `changedSynergyNativePvPReputation` | `true` | Reputation for attacks on native players: Successful PvP attacks and kills against native faction members affect the attacker's reputation. Recent self-defense is exempt. Disable this for arenas or informal duels. |
| `changedSynergyNpcAI` | `true` | Latex Creature Hostility Pathfinding: Enable Synergy hostility pathfinding, sight memory, investigation and pursuit. Also requires Enhanced Hunt AI in server settings. Creature Life, camp work and relationship safety have separate controls. |
| `changedSynergyNpcDialogue` | `true` | Changed: Synergy Dialogue: Allows creatures to send nearby players contextual lines during social interactions and AI state changes. |
| `changedSynergyNpcEmotes` | `true` | Changed: Synergy Emotes: Allows creatures to display the same emotion bubbles used by the /emote command. |
| `changedSynergyFactionReputation` | `true` | Changed: Synergy Faction Reputation: Enables faction standing gains, losses and their gameplay benefits. |
| `changedSynergyInitialFactionReputation` | `0` | Initial Changed: Synergy Faction Reputation: Sets the reputation from -100 to 100 assigned when a player first encounters an uninitialized faction account. Existing reputation is not overwritten. |
| `changedSynergyBondSystem` | `true` | Changed: Synergy Bonds: Enable bond creation, companion following, rescue, wrapping and companion controls. Disabling pauses bonded camp members and keeps existing bonds and assignments. |
| `changedSynergyFriendshipSystem` | `true` | Changed: Synergy Friendships: Enable familiarity, friendship, friend following and assistance. Disabling pauses friend camp members and keeps their memories and assignments. |
| `changedSynergyTerritoryDisplay` | `true` | Changed: Synergy Territory Display: Shows territory and facility context on the HUD. World generation and biome spawning are not changed. |
| `changedSynergyPersonalitySystem` | `true` | Changed: Synergy Personalities: Lets stable individual traits affect AI, tolerance, social habits and dialogue selection. |
| `changedSynergyCreatureLife` | `true` | Changed: Synergy Creature Life: Enable creature routines, wild communities, gathering, player camps and Pure White reformation. Turning it off pauses these activities and keeps saved camp membership. Individual AI and work switches still apply; placing or harvesting blocks respects mobGriefing. |
| `changedSynergyHypnosisQte` | `true` | Changed: Synergy Hypnosis Gaze Struggle: Enables Synergy's gaze-resistance sequence, control lock and mesmerized penalty. Changed's base hypnosis remains when disabled. |
| `changedSynergyGrabQteEnhancements` | `true` | Changed: Synergy Grab QTE Enhancements: Enables server-synced grab prompts and the safety stun after escape. Changed's base grab remains when disabled, and creatures can attack again immediately after an escape. |
