![Changed: Synergy](https://media.forgecdn.net/attachments/1899/302/screenshot-png.png)

# Changed: Synergy

Changed: Synergy turns aggressive latex creatures into neighbors you can get along with. An orange or a friendly pat can help with first contact; gifts and time turn that first meeting into a friendship.

Transfur is not always the end. Sometimes you can negotiate your way back out of it. In other situations, the creature may keep control for a while. Some of them may still be a little too ~~EXCITED~~ to meet a human again…

Synergy is an unofficial Forge addon for Changed on Minecraft 1.20.1. Creatures have persistent names, personalities, memories, dialogue and communities. Befriend them, form a companion bond, build a camp or take a working crew on your travels.

**1.1.4 focuses on integration with TrueTransfur 2.0.0.** Begin as a native latex creature, choose a species through an animated archive and keep your identity while switching between PLAYER and INSTINCT control. TrueTransfur remains optional for Synergy's NPC relationships, camps and companion features.

## Guides and quick links

- [All player guides](https://github.com/ParkaBird/changed-synergy/blob/main/docs/README.md)
- [Social interactions, friends and bonds](https://github.com/ParkaBird/changed-synergy/blob/main/docs/SOCIAL_GUIDE.md)
- [Player camps and travelling crews](https://github.com/ParkaBird/changed-synergy/blob/main/docs/PLAYER_CAMP_GUIDE.md)
- [Native origins and the species archive](https://github.com/ParkaBird/changed-synergy/blob/main/docs/NATIVE_ORIGINS.md)
- [TrueTransfur and Instinct mode](https://github.com/ParkaBird/changed-synergy/blob/main/docs/TRUETRANSFUR_INSTINCT_COMPAT.md)
- [Server settings and commands](https://github.com/ParkaBird/changed-synergy/blob/main/docs/SERVER_CONFIGURATION.md)
- [Full settings reference](https://github.com/ParkaBird/changed-synergy/blob/main/docs/SETTINGS_GUIDE.md)
- [What's new in 1.1.4](https://github.com/ParkaBird/changed-synergy/blob/main/docs/CHANGELOG-1.1.4.md)

The guide directory also links to Chinese social and settings guides. For feedback, discussion and development updates, join the [Changed: Synergy Discord](https://discord.gg/3yscxe2YgN).

## Native origins and TrueTransfur

- Choose a human or an eligible native latex species, including supported juvenile and fusion forms.
- Browse front portraits and species dossiers showing body parameters, faction and rival faction. Rotate or zoom the body preview, choose a random species within a category, or pick fully at random from the available archive records.
- Native creatures begin with their species' citizenship and Recognized faction standing. Identity and a generated creature name persist through death and rejoining; the name can be changed without changing the account.
- Use a nearby role-speech mode or ordinary global chat. Players who understand your species see your words; others hear creature sounds. Expressions appear on your visible body.
- Right-click another player or their Instinct body to greet, pat, transfer a gift or invite them to walk together. Player friendships require mutual consent and are separate from NPC bonds.
- Instinct bodies remain real players for social interactions, faction reputation and companion following. They do not become duplicate social NPCs or show persistent nameplates.
- Eligible native Instinct players can pursue hostile humans and temporarily take over after completing transfur under the server's PvP rules. Release, negotiation and escape remain available where permitted.
- Server owners control origin selection, allowed starting forms, role speech and Instinct-player takeover. A default-off option permits ordinarily transformed human-origin players to use Instinct without granting native or permanent-body features.

**TrueTransfur 2.0.0 is recommended.** Native origins and direct-player integration remain compatible with 1.0.1; features involving the separate Instinct body require 2.0.0.

## Creatures, friends and bonds

- Persistent names, personalities, emotions and remembered encounters.
- Friendship, personal affection and faction reputation, with one bond by default and an optional multiple-bond setting.
- Social, relationship, creature-function and body-control radial menus.
- Patting and direct feeding build initial familiarity. Reaching the stranger limit does not establish friendship by itself: a suitable gift through the social wheel completes acquaintance and gives the creature a name.
- Care interactions share an affection cooldown. Wheel gifts have their own cooldown and replenish a limited care allowance. Direct feeding can still heal during affection cooldowns.
- A first bond requires maximum affection and a species-themed token. Humans confirm voluntary transfur; native and permanently transformed TrueTransfur players can bond without changing bodies or factions.
- Bonds appear in the relationship roster. Ending one preserves friendship and memories, and former companions can reunite without a second token.
- Playful friends may attempt friendly transfur at lower affection; a light, bare-handed warning can stop the attempt. Playful and Polite are mutually exclusive behavior traits.
- Creature inventories and compatible armor or melee weapons for trusted friends and companions. Supplied mining and fishing tools are preferred over built-in tools.
- Friendly wrapping, emergency rescue, Rest Together, shore waiting and riding for supported species. Wrapping and rescue work without Changed Addon Plus.
- Revival for supported bonded companions through repaired Dark Latex Masks or identity-bearing samples processed in the Latex Infuser.
- English and Simplified Chinese dialogue, Russian localization, and community German and Spanish translations for some text.

## Your own camp

Establish a camp around a **placed campfire** through the relationship wheel or `/changedsynergy outpost create`. Look at a suitable functional block and use `/changedsynergy outpost link`; it identifies the facility automatically. Existing bell-founded camps remain supported.

Invite trusted adult creatures at **Close friendship**. Beds are sleeping places; pillows, office chairs and tall cardboard boxes are separate rest points. Link containers, a cooking campfire, a composter and work areas, then assign residents, guards, suppliers or travelling crew through camp management.

- **Gathering:** harvest suitable oranges and berries, with an optional marked area.
- **Hunting:** hunt suitable adult prey and collect real drops, while respecting exclusions and preserving a nearby animal population.
- **Farming:** harvest and replant mature crops in a marked field. Compost surplus planting material when appropriate and collect the resulting bone meal. Farmer's Delight crops are supported.
- **Cooking:** take suitable raw food from linked storage, cook several items on the campfire and collect cooked products.
- **Hauling:** collect missed drops in a marked area, or within 32 blocks of the camp by default. A server item blacklist can exclude particular drops.
- **Shared meals:** sit around the campfire, prefer nearby rest points, eat carried food or fetch a serving from camp storage, then rest before returning to work.

Farmers and haulers collect batches before unloading and store their products before resting. Work Cargo contains the real items awaiting use or delivery and can be recovered through the creature's inventory when storage is full or unreachable. Linked chests open, close and play their sounds when visited.

Members respond to follow, wait, return and retreat orders. Multiple beds are assigned separately, and work or orders interrupt idle rest. The roster shows assignments and current or last-known coordinates.

Camp data survives unloaded areas, but camps do not force chunks to load. Work pauses while the owner is offline by default. Server owners can adjust resident and crew limits, work areas, job switches, search intervals and worker limits. A separate setting controls how far new wild outposts must stay from player camps.

## Travelling crews

Assign **Combat**, **Mining**, **Gathering** or **Hunting** before setting a member to follow as crew.

Gathering workers follow at a looser distance, finish reachable nearby tasks and then catch up. Miners collect exposed veins rather than digging tunnels; fruit gatherers look for suitable oranges and berries; hunters pursue eligible prey and collect loot. They keep cargo while travelling and unload when they return to linked storage. Supplied tool quality and supported enchantments affect mining or fishing.

Danger, recall and changed orders interrupt work safely. Gathering respects `mobGriefing`, loaded chunks, work limits and the server's blocked-ore list. See the [camp guide](https://github.com/ParkaBird/changed-synergy/blob/main/docs/PLAYER_CAMP_GUIDE.md) for setup, search ranges and restrictions.

## Transfur, takeover and hypnosis

- Negotiate after eligible involuntary assimilation, absorption or fusion. Transfur and takeover outcomes depend on the encounter, faction standing and server settings.
- Temporary control, escape attempts and safe release for supported takeover interactions. Reversible organic forms can be removed by sleeping where permitted.
- Exoskeleton takeover for Benign Wolf and Benign Orca players. During takeover, third-person camera rotation remains available.
- Protective wrapping, rescue and post-release protection to prevent immediate repeated capture.
- Native player protection against hostile secondary transfur, with an optional extension to other transformed players.
- Organic creatures retain low physical swipe damage against humans and ordinary damage against creature players.
- Superb Warfare vehicle occupants cannot be grabbed or wrapped; pursuit can continue after they enter a vehicle.
- Ordinary NPC hypnosis uses gaze resistance. Player hypnosis contests use directional resistance controls, with a longer timer and larger meter; human pats briefly interrupt the caster's input.
- Optional legacy-style transfur masks and skin coating, disabled by default.

## Factions and wild communities

Regional factions have their own communities, rivalries and dialogue. Reputation changes greetings, hostility, trades and pursuit. Rival reputations are linked by default; an optional setting permits independent standings and simultaneous alliances.

Wild provisioners gather resources and store them at community outposts in suitable outdoor sites, ruins, hives and aquatic locations. Their trades use oranges, berries or appropriate fish, with special items becoming available as relationships improve. Player-owned companions stay outside autonomous wild-community jobs.

Servers can disable new wild outposts while keeping existing ones, tune spacing between wild outposts, and set a separate buffer around player camps. Searches use bounded work and loaded terrain for routine community tasks. Aquatic followers can find shorelines, and Pure White Latex Wolves can adapt away from their home region without losing their identity or allegiance.

## Settings and multiplayer

Configuration is divided into **Server Settings**, **World Rules** and **Client Visuals**. Gameplay values come from the server; local visuals do not override them. Operators can use `/changedsynergy config` to inspect or change settings and `/changedsynergy outpost help` for camp commands.

Relationship balance, telepathy, takeover, origin selection, camp limits, work restrictions, wild outposts and AI scheduling can be tuned separately. Performance diagnostics show server tick time and creature AI activity, and optional background work can be reduced when a server is busy.

The server and every connecting client must use the same Synergy build and the relevant dependencies.

## Requirements and optional integrations

| Component | Supported version | Required |
| --- | --- | --- |
| Minecraft | 1.20.1 | Yes |
| Forge | 47.4.x | Yes |
| Changed | 0.15.7 | Yes |
| TrueTransfur | 2.0.0; backward compatibility with 1.0.1 | For native origins and TrueTransfur features |
| Mixin Args | 1.0 or newer | With TrueTransfur 2.0.0 |
| Changed Addon Plus | 2.9.2c | No |
| Changed Vanilla | 1.0.1 | No |
| Changed Extras | 1.1.8b | No |
| Farmer's Delight | 1.3.4 | No |
| Domestication Innovation | 1.7.1 | No |

- **Changed Addon Plus:** supported creatures, diets, pet relationships, equipment and Golden Orange interactions.
- **Changed Additions 0.1.0-V4-Fix:** basic creature and Golden Orange integration.
- **Changed Vanilla:** creature relationships, communities and companions, preserving its dedicated conversion behavior.
- **Changed Extras 1.1.8b:** unrelated wild creatures can retain its smart AI. Synergy companions and camp members keep their assigned behavior; automatic bedside cuddling does not interrupt camp work. Artist remains excluded from Synergy social systems.
- **Farmer's Delight:** camp farmers support cabbage, onions, tomatoes and rice while preserving their appropriate replanting or regrowth behavior.
- **Domestication Innovation:** companion equipment, beds, recall and owner-alliance features.
- **Better Combat 1.9.0:** compatible targeting around friendly wrapping.
- **TaCZ and Superb Warfare:** gunshots can alert nearby creatures; supported vehicle occupancy prevents grabs.
- **Changed: Survive Protocol 1.2.0:** preserves its ordinary infection rules while supporting authorized Synergy takeover, release and origin re-selection.
- **Furmutage 0.1:** coexistence only; its creatures are excluded from Synergy systems.

Optional mods are not bundled. See the [compatibility guide](https://github.com/ParkaBird/changed-synergy/blob/main/COMPATIBILITY.md) for the supported scope.

## Installation and support

1. Install Forge 47.4.x for Minecraft 1.20.1.
2. Put Changed 0.15.7 and one Synergy JAR in `mods`.
3. Add TrueTransfur 2.0.0 and Mixin Args for native origins and Instinct gameplay, plus any other optional integrations you want.
4. Install the same Synergy build and relevant dependencies on the server and every client.

Back up important worlds before updating. Do not install multiple Synergy JARs at once. Report reproducible problems through the [GitHub issue tracker](https://github.com/ParkaBird/changed-synergy/issues), with a crash report or `latest.log`, exact versions and reproduction steps. See [known limitations](https://github.com/ParkaBird/changed-synergy/blob/main/KNOWN_ISSUES.md).

## Content statement

Synergy is intended for a general audience. It focuses on creature personalities, relationships, community life and reversible interactions. As a personal creative boundary, I chose not to reproduce or expand the original Changed game's more suggestive material; this describes Synergy's direction rather than judging what others enjoy. The addon does not add content centered on the original game's main characters.

Synergy does not take a position on controversies surrounding Changed or its creator. Readers are encouraged to consult reliable, first-hand sources and reach their own conclusions.

## Credits, AI assistance and license

Changed: Synergy is developed by ParkaBird. Thanks to Nic, Crossader and Sword for dialogue and localization, and to TrueTransfur's author for collaboration, testing and Russian localization. This is an unofficial addon, not affiliated with the creators of Changed or Mojang Studios.

Chinese is my native language. Generative AI assisted with parts of the English and Chinese text and code; I reviewed, edited, integrated and tested the results. Please report awkward wording or reproducible bugs. No generative AI was used for artwork, textures, models, screenshots or promotional images. See [AI disclosure](https://github.com/ParkaBird/changed-synergy/blob/main/AI_DISCLOSURE.md).

Licensed under [GPL-3.0-or-later](https://github.com/ParkaBird/changed-synergy/blob/main/LICENSE.txt). Find the project on [Modrinth](https://modrinth.com/mod/changed-synergy), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/changed-synergy) or [GitHub](https://github.com/ParkaBird/changed-synergy).
