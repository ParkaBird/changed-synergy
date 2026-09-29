![Changed: Synergy](https://media.forgecdn.net/attachments/1899/302/screenshot-png.png)

# Changed: Synergy

Changed: Synergy turns aggressive latex creatures into neighbors you can actually get along with. An orange or a friendly pat will often get you further than fists and blades.

Transfur is not always the end anymore. Sometimes you can negotiate your way back out of it. In other situations, the creature may decide to keep control for a little while. Just be careful. Some of them may be a little too ~~EXCITED~~ to meet a human again…

Changed: Synergy is an unofficial Forge addon for Changed on Minecraft 1.20.1. It gives Changed creatures persistent names, individual personalities, memories, dialogue, communities, and social behavior. You can befriend them, earn their trust, form a bond, and watch them interact with the world and one another. You can also establish an outpost of your own and give trusted creatures a place to live and work together.

Chinese is my native language. Since my experience and English ability are limited, I used generative AI to assist with parts of the English and Chinese text and code. I reviewed, edited, integrated, and tested the results, but you may still encounter awkward wording or bugs. Thank you for your understanding, and please report any problems you find.

Want to share feedback, discuss features, report a bug, or follow development? Join the [Changed: Synergy Discord server](https://discord.gg/3yscxe2YgN).

## Creatures and relationships

*   Persistent names, individual personalities, emotions, and long-term memories
*   Friendship, affection, faction reputation, and a bond system (one bond by default, with an option to allow more)
*   Social, relationship, creature-function, and body-control radial menus
*   Context-aware dialogue in English and Simplified Chinese, with contributed German and Spanish translations for some lines
*   Feeding, petting, gifting, disguises, and environmental interactions
*   Creature inventories plus compatible armor and melee weapons for close friends and bonded companions
*   Friendly wrapping, emergency rescue, and taur riding for supported companions
*   Rest Together and shore waiting for compatible companions
*   Revival for bonded latex companions: repair a fallen dark latex companion's mask, or process another supported species' identity-bearing sample in the Latex Infuser
*   Personality-based protective behavior that may require repeated release requests after several dangerous rescues
*   Named, befriended, and bonded creatures remain present on Peaceful difficulty
*   Lost or permanently removed creatures are cleared from contact lists

## Your own outpost

Claim a bell to establish a player outpost. You can link a nearby chest and a bed or suitable pillow, then invite trusted adult creatures as residents. Assign them to guard the outpost, gather supplies, or follow you as a crew. Suppliers collect resources suited to their species and faction and bring them to the linked chest; the roster shows what each one gathers. Residents can also use linked sleeping places, including hive pillows for latex bees.

Look at your claimed bell and sneak-right-click to manage the outpost. You can also open it from the relationship wheel or use `/changedsynergy outpost`. The management screen lets you see your residents and change their assignments. To get started, `/changedsynergy outpost help` lists the claim, storage, bed, and invitation commands.

Outpost records and residents persist when the area unloads. They do not keep chunks loaded, so work resumes when the area is active again. Server owners can configure whether player outposts are available, how many residents and crew members they allow, and whether suppliers can gather and harvest resources.

## Transfur and body control

*   Negotiation after involuntary assimilation, absorption, or fusion
*   Reversible transfur interactions where the situation allows
*   Organic transformations that can be reversed by sleeping
*   Configurable absorption takeover during qualifying encounters
*   Different takeover behavior for competitive creatures and creatures responding to player aggression
*   Takeover sequences with observation, temporary control, one escape attempt, and outcomes shaped by the creature's motive and your faction standing
*   A single chance to negotiate during eligible takeovers; hostile encounters can instead end in permanent transformation or fatal loss of consciousness
*   Server validation for movement, combat, item use, and abilities while the carrier has control
*   Forced release through `/untf` or `/untransfur`
*   Exoskeleton takeover support for Benign Wolf and Benign Orca players
*   Configurable post-transfur ceasefires to prevent immediate repeated attacks
*   Optional legacy-style transfur progress masks and skin coating, disabled by default

When absorption takeover is disabled, qualifying absorptions continue to use the negotiation system.

## Hypnosis

Synergy replaces its old hypnosis button prompts with a gaze-based resistance system.

Hypnotic creatures pull the player's view toward their eyes. Direct eye contact strengthens the effect, while repeatedly forcing your view away builds resistance. Fill the resistance meter in time to break free. Otherwise, the player becomes Mesmerized.

Hypnotic creatures can also communicate clearly during hypnosis before permanent telepathy has been unlocked.

## Factions and reputation

Creatures belong to regional factions with their own relationships and communities.

*   Reputation changes how faction members respond to the player
*   Peaceful relationships with every faction remain possible
*   Rival factions affect one another by default; an optional setting makes their reputations independent and allows simultaneous alliances
*   Linked rivalries connect Dark and White, Mountain and Cave, Aquatic and Desert, Forest and Badlands, Taiga and Jungle, Swamp and Savanna, and Plains and Snowy factions
*   Factions at minimum reputation may send an optional pursuit squad after the player
*   Faction-specific dialogue responds to alliances, betrayal, hostility, and pursuit
*   `/changedsynergy reputation` shows global faction standings and the current regional Light-faction reputation
*   Server owners can configure initial faction reputation and related systems

## Creature behavior and communities

*   Improved pathfinding, swimming, grappling, combat, and follower behavior
*   Shared land and water navigation for compatible companions
*   Creature gathering, fishing, hunting, and mining
*   Regional communities, resource storage, and creature-established faction outposts in the wild and suitable ruins
*   Provisioners that gather supplies and return them to their community
*   Provisioner trades using oranges, berries, or species-appropriate fish, with special collectibles available as relationships improve
*   Territory defense, group assistance, and environmental reactions
*   Persistent community membership and outpost ownership
*   Configurable minimum spacing between outposts
*   Flying creatures can slow their fall by using their wings
*   Aquatic companions can locate shorelines and follow players onto land
*   Configurable Pure White Latex Wolf adaptation: adults living far from the White Latex Forest take on a male White Latex Wolf form and change back when they return, without losing their identity, relationships, or Pure White faction allegiance

## Human interactions

Human players can use light, bare-handed hits to warn an approaching creature to back away without immediately starting a fight. Weapons, heavy damage, or repeated attacks still count as aggression.

Competitive creatures may interpret the warning as a challenge and become even more determined.

Patting can calm some approaching creatures. Server owners may disable the pacification effect without disabling ordinary patting or relationship progress.

Voluntary transfur is only available at the highest friendship tier.

## Performance and diagnostics

Synergy distributes social, community, hunting, and work decisions across server ticks. Distant idle creatures perform optional work less often, while combat, following, and companion safety remain responsive.

The Performance and Diagnostics configuration section shows client FPS, server tick time, latex AI time, active creature count, and deferred work. Server owners can disable expensive optional systems individually or use the soft AI time budget to reduce background work when the server is under load.

## Configuration

Synergy includes in-game Forge configuration screens and gamerules for its main systems.

Configurable features include:

*   Absorption takeover
*   Punitive and personality-based takeovers
*   Temporary player control and escape attempts
*   Takeover negotiation and hostile takeover outcomes
*   Ordinary transfur reversal
*   Exoskeleton hypnosis, takeover, and unconsciousness
*   Protective release requests
*   Post-transfur ceasefire duration
*   Pat pacification
*   Player outposts, resident and crew limits, supply work, harvesting, rest, and guard behavior
*   Mindless mob transfur behavior
*   Telepathy unlock requirements
*   Faction pursuit squads
*   Independent faction reputation and peaceful village relations
*   Pure White Latex Wolf adaptation
*   Spacing between creature-established outposts
*   AI scheduling and performance diagnostics
*   Dialogue popup and scrolling-text height
*   Transfur progress masks and skin coating

`/changedsynergy relationship` opens the relationship manager. The remaining command branches include clickable help and can be used to inspect or manage the relevant systems.

Client settings control local presentation. Common configuration and gamerules control server-side gameplay.

## Requirements

| Component | Supported version | Required |
| --- | --- | --- |
| Minecraft | 1.20.1 | Yes |
| Forge | 47.4.x | Yes |
| Changed | 0.15.7 | Yes |
| Changed Addon Plus | 2.9.2c | No |
| Changed Vanilla | 1.0.1 | No |
| Changed Extras | 1.1.4-beta3b | No |
| Domestication Innovation | 1.7.1 | No |

Synergy uses targeted integration points. Compatibility with newer versions of Changed or its optional integrations is not guaranteed.

## Optional compatibility

*   **Changed Addon Plus 2.9.2c:** Adds support for its creatures, grabbing mechanics, equipment, Golden Oranges, and related systems.
*   **Changed Additions 0.1.0-V4-Fix:** Provides basic creature integration and Golden Orange support.
*   **Changed Vanilla 1.0.1:** Its natural and converted creatures can participate in Synergy's identity, relationship, community, and companion systems.
*   **Changed Extras 1.1.4-beta3b:** Its unrelated wild creatures may retain Changed Extras smart AI, while friends, bonded companions, takeover carriers, and pursuit members use Synergy's relationship-aware behavior. Target selection respects Synergy relationships, truces, and creature alliances.
*   **Domestication Innovation 1.7.1:** Owned companions can use its pet equipment, beds, and recall features. Its owner-alliance checks recognize Synergy companions.
*   **Better Combat 1.9.0:** Friendly wrapping creatures are excluded from its attack targeting.
*   **TaCZ and Superb Warfare:** Gunshots can alert nearby creatures.
*   **Furmutage 0.1:** Coexistence compatibility only. Its creatures are excluded from Synergy systems.
*   **Changed: Survive Protocol 1.2.0:** Coexistence compatibility only.

Optional mods are not bundled with Synergy.

## Installation

1.  Install Forge 47.4.x for Minecraft 1.20.1.
2.  Place Changed 0.15.7 and the Synergy JAR in your `mods` folder.
3.  Add any supported optional integration mods you want to use.
4.  Launch the game and check the Forge Mods screen for dependency errors.

Do not install multiple versions of Synergy at the same time.

For multiplayer, the server and every connecting player must use the same Synergy build.

## Support and issue reporting

Please back up important worlds before installing or updating.

When reporting a reproducible problem, include:

*   The crash report or `latest.log`
*   The exact Forge, Changed, and Synergy versions
*   A list of relevant optional integration mods
*   The steps needed to reproduce the problem

When available, bugs can be submitted through the [GitHub issue tracker](https://github.com/ParkaBird/changed-synergy/issues).

Known limitations are documented in [KNOWN\_ISSUES.md](https://github.com/ParkaBird/changed-synergy/blob/main/KNOWN_ISSUES.md).

## Content statement

Synergy is intended for a general audience. It focuses on creature personalities, relationships, community life, and reversible interactions.

As a personal creative boundary, I chose not to reproduce or expand upon the original Changed game's more suggestive material. This applies only to the direction of Synergy and is not a judgment of what other people should enjoy. The addon also does not introduce content centered on the original game's main characters.

Changed: Synergy does not take a position on controversies surrounding Changed or its creator. Readers are encouraged to consult reliable, first-hand sources and reach their own conclusions.

## Credits, AI assistance, and license

Changed: Synergy is developed by ParkaBird. It is an unofficial addon and is not affiliated with the creators of Changed or Mojang Studios.

Dialogue contributions: Nic, Crossader, and Sword.

Generative AI assisted with parts of the English and Chinese text and code. ParkaBird reviewed, edited, integrated, and tested the resulting work. No generative AI was used for artwork, textures, models, screenshots, or promotional images. See [AI\_DISCLOSURE.md](https://github.com/ParkaBird/changed-synergy/blob/main/AI_DISCLOSURE.md) for details.

Changed: Synergy is licensed under [GPL-3.0-or-later](https://github.com/ParkaBird/changed-synergy/blob/main/LICENSE.txt).
