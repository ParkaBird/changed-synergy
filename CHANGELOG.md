# Changelog

This project follows semantic versioning while it is practical. Beta releases may still change saved-data details or interaction balance.

## 1.1.4

**TrueTransfur integration:** Changed: Synergy 1.1.4 pairs with **TrueTransfur 2.0.0** for native latex origins, roleplay and Instinct-mode social interactions. Backward compatibility with **1.0.1** is retained; the expanded Instinct features require 2.0.0. TrueTransfur remains optional for other Synergy features.

Changes since **1.1.3**, including the features introduced during Alpha and Beta testing. This release focuses on **TrueTransfur integration**, native latex roleplay, friendship progression, and expanded player camps and travelling crews.

Thank you to everyone who tested the mod, reported problems and shared ideas after 1.1.3. Special thanks to Nic, Crossader and Sword for their dialogue and localization contributions, and to TrueTransfur's author for collaboration, testing and Russian localization!

### Native latex origins and TrueTransfur

- Begin as a human or a native latex creature with TrueTransfur, including eligible juvenile and fusion forms. Supports TrueTransfur **2.0.0**, with backward compatibility for **1.0.1**; TrueTransfur remains optional for other Synergy features.
- Native players belong to their species' faction, begin at Recognized standing and receive greetings from compatriots. Their origin, citizenship and permanent creature identity persist through death and rejoining.
- Birth placement searches for safe territory suited to the chosen species, including previously unloaded terrain. If no suitable home is found, choose another species or approve a safe birthplace near world spawn.
- The **Starting Origin Selection** gamerule controls this mode. While enabled, TrueTransfur's permanent-identity features apply only to native players; disabling it restores TrueTransfur's normal applicability. Ordinary Changed transformations remain intact under either policy.
- A default-off **Human-Origin Instinct Control** setting lets ordinarily transformed human-origin players use TrueTransfur 2.0's Instinct mode without granting permanent identity, lethal reversal, native rebirth or native roleplay features.
- TrueTransfur 2.0's visible Instinct body represents the real player for social interaction, companion following, combat and faction reputation. The hidden technical player is ignored by creature AI. The visible body has no persistent nameplate, duplicate NPC identity or wild-community membership.
- Native players are protected from hostile secondary transfur. A separate setting can extend this protection to other transformed players, causing enemies to attack instead. Attacking or killing native players can affect faction standing, with its own gamerule and self-defense exemption.
- Operators can inspect origins or reopen selection with `/changedsynergy origin`. Optional re-selection can be cancelled with Escape; choosing human safely restores the human body. Players are protected from mob targeting while selecting.
- A default-off option adds eligible playable forms from other installed mods. Servers can disable individual starting forms; bosses and unsuitable special forms remain excluded, and existing origins are preserved.

### Native identity, player friendship and roleplay

- Native players receive a persistent creature name from their species' naming pool. Change it through the relationship wheel or `/changedsynergy social name`; account names, UUIDs and permissions remain unchanged.
- A six-expression wheel lets native players show short-lived expressions on their visible body in either control mode. Relationship-wheel navigation hints share one line, and the chat-mode switch stays beneath command suggestions.
- Native players can speak through nearby floating dialogue or switch to normal global chat. Role speech follows the same understanding rules as NPC dialogue: listeners who cannot understand the species hear creature sounds, while the speaker sees their original message. Clients with floating dialogue disabled receive the corresponding text in chat.
- Right-click a player or their Instinct body to greet, pat, transfer a gift or invite them to walk together. Player friendship requires mutual consent, has shared familiarity, and remains separate from NPC bonds. Gifts still transfer during familiarity cooldowns.
- Confirmed friends' walking invitations are accepted automatically in Instinct mode; strangers need consent. Manually controlled players retain control and see the inviter highlighted. Either side can end the arrangement.
- With TrueTransfur 2.0, under server PvP and alliance rules, an eligible native Instinct player can pursue a hostile human using ordinary Changed transfur attacks. Only completed transfur progress and physical contact permit temporary takeover; approaching the human alone does not capture them.
- The human can negotiate or attempt WASD escape. Timed release, control-mode changes and interrupted sessions release them safely at the current location, without permanent transformation. Other latex creatures cannot attack or recapture a contained human, and release grants a protection period.
- Fixed camera crashes when entering Instinct, including Latex Human forms.
- Human takeover countdowns keep their top-centre position and time/outcome text, using Changed's release-bar appearance and the captor's species colour. Instinct captors also see the native release bar above their own hotbar.
- Players can look around freely in third person during takeover while first-person view follows the carrier. Temporary suits remain synchronized throughout movement, and Instinct pursuit retains continuous melee behavior at close range. Worn exoskeleton controllers follow terrain with collision, falling and native jumps, without pausing at each path node.

### Archive character selection

- Replaced initial selection with an animated archive adapted from the author's Godot UI: a human record and five creature sections, White, Dark, Variant, Organic and Aquatic. The welcome page introduces **Synergy × TrueTransfur** together.
- Species cards show portraits from their actual models. Dossiers include body parameters, faction and rival faction, plus a rotatable, zoomable paper doll with a crouching pose. The human record uses the player's account and skin.
- Added animated page turns, paper-corner navigation, keyboard controls and Minecraft book sounds. Long localized confirmations and birthplace notices can be scrolled while their action buttons stay visible.
- A note on the archive's opening cover offers a fully random origin, including human. Each creature section also offers a random species from that section. Both use the visible, server-approved catalog and respect form restrictions.
- Male and female Phage Wolves belong to the Dark faction and appear in its archive section. Dossiers show the species' actual regional faction and opposition.

### Friends and bonds

- Friendship now progresses through initial familiarity, a deliberate first gift and continued interaction. Before acquaintance, ordinary affection stops at **8**; a suitable wheel gift at that point establishes the relationship and gives the creature a name.
- Patting, direct feeding and shared activities gain affection through a shared **60-second cooldown**. After acquaintance, each accepted wheel gift refills an **8-point care allowance**. Talking, repeated encounters and return pats retain their reactions and memories without granting affection.
- Wheel gifts have an independent **five-minute cooldown** and accept one item: ordinary gifts grant +2, preferred gifts +4 and Golden Oranges +6. Direct feeding remains separate and can heal during affection cooldowns. Rejected gifts are not consumed.
- First bonds require **60 affection** and a species-themed token: Dark Latex Crystal Fragments, Quartz, Amethyst Shard, Golden Apple or Heart of the Sea. The wheel explains the item, quantity and eligibility rules. Human players then confirm voluntary transfur; native and permanently transformed TrueTransfur players can bond without changing bodies or factions.
- Unused tokens persist while players are offline or creatures are unloaded and can be withdrawn. Ending a bond through the roster or commands preserves friendship, name, affection and memories. Former companions can reunite without another token.
- Adult **Playful** friends below Close affection may occasionally attempt friendly transfur. A light bare-handed warning interrupts the attempt without reputation loss. Playful and Polite are mutually exclusive behavior traits; other personality traits determine dialogue.
- Native pet relationships bypass ordinary acquaintance and token requirements. Tamed Dark Latex pups retain proper owner recognition when growing up, and Addon Exp2 companions enter the relationship system without extra gifts. Juveniles retain their age restrictions.
- Feline creatures accept raw and cooked cod or salmon, including without Addon Plus. Suitable direct feeding and a trusted friend's mediation can restore peace after hostility, without erasing faction reputation or relationship history.
- Added advancements for bond tokens, reuniting and settling a Playful friend's attempt, plus English and Simplified Chinese social guides.

### Player camps and working residents

- Establish camps around a campfire; existing bell-founded camps remain supported. The relationship wheel offers **Establish Camp**, **Link Facility** or **Invite to Camp** when aimed at an appropriate target. New invitations require Close friendship.
- `outpost link` identifies the targeted facility automatically. Multiple beds are assigned separately at night; pillows, office chairs and tall cardboard boxes are separate rest points. Residents use them during downtime and leave promptly for work or orders.
- Updated camp management with reliable individual and team follow, wait, return and retreat orders for residents, guards, suppliers and travelling crew. Hovering over a member's name shows its current coordinates, or its last known location when unloaded.
- Suppliers can perform gathering, hunting, farming, cooking or hauling, with a main and backup job. Real products are kept in accessible **Work Cargo**, preserved when delivery is blocked, and returned to linked containers. Chest visits have opening and closing animations and sounds.
- Farmers harvest mature crops, replant the same crop and compost surplus seeds when appropriate. Farmer's Delight cabbage, onions, tomatoes and rice are supported. Farmers collect batches before unloading and store produce before resting, including the bone meal produced by composting. Stored bone meal can then be used to grow immature crops.
- Cooks collect ingredients from storage and use all four campfire slots, including raw fish and food-only furnace recipes. Haulers collect leftover cooking outputs, combat loot and other loose items, batching collections before delivery. Servers can exclude specific items from hauling.
- Farming, hunting, gathering and hauling can use marked work areas up to **64 blocks** from camp. Unmarked hauling covers **32 blocks** around the camp. Workers stay near assigned areas during idle periods.
- Shared meals gather residents safely around the campfire, preferring nearby linked rest points. Members animate eating food from their own inventory, collect a serving from storage if needed, then rest briefly before resuming duties.
- Work, meals, sleep, combat and movement orders have coordinated priorities. Non-guard workers retaliate when attacked instead of abandoning tasks to initiate fights; guards retain their defense role. Sleeping residents do not pat players or give social reactions.
- Camps default to **16 members** and **six travelling crew**, configurable up to 64 and 32. Existing settings and memberships are preserved. Provisioners resume trading after leaving camp, retaining their used trade limits.
- Added camp advancements for establishing a home, receiving deliveries and sharing meals. With TrueTransfur installed, native origins and body-preserving bonds have their own advancements. Updated the English camp guide and in-game command help.

### Travelling crews and companion work

- Assign **Combat**, **Mining**, **Gathering** or **Hunting** through camp management or `outpost crew`. Gathering jobs follow at a looser distance, finish nearby work before catching up, and respond to danger and recall orders. They follow the owner's visible TrueTransfur body when applicable.
- Miners work through exposed, reachable ore veins, including newly exposed ore. Gatherers harvest reachable orange-tree fruit, sweet berries and glow berries while preserving regrowth. Hunters pursue eligible adult food animals and collect the actual kill drops; babies, pets and other protected animals are excluded.
- Work uses all six cargo slots and matching partial stacks. Full bags pause new collection while members continue following, and supplies are unloaded when the crew returns near camp storage. Stationary suppliers and travelling gatherers have independent work slots.
- Added travelling-work controls and an ore blocklist. Searches and pathfinding are local and bounded; crews do not dig tunnels, load distant chunks or remain behind indefinitely.
- Companion mining uses pickaxe material, Efficiency, Fortune and Silk Touch. Fishing uses Lure and Luck of the Sea. Suitable player-supplied tools in the creature's inventory take priority over default tools and lose durability normally.
- Combat assistance respects player alliances, target filters and the owner's actual visible body. Companions do not treat unprovoked neutral creatures as hostile monsters. Friendly transport grabs do not provoke rescue attacks, and wait orders and Never Attack are respected by automatic protection.

### Abilities, takeover and creature ecology

- Dark and White latex are now peaceful competitors. NPCs and players in opposite latex forms, including TrueTransfur Instinct bodies, no longer become enemies on sight. Faction-alliance competition remains, and genuine aggression, theft or hostile standing can still provoke a response.

- Hypnotic creature players and human players share a PvP hypnosis QTE, with the same presentation and movement-key controls on both sides. Player contests last longer than NPC contests; a human's pat prevents the hypnotist from adding control for three seconds.
- Pure White players adapt their appearance and vision outside their territory while retaining their species, abilities and faction. The existing regional-adaptation setting controls this behavior.
- Transformed players have third-person pat animations where their model supports them. Models without usable arms safely skip the animation; feral NPCs, Milk Puddings and quadrupedal pups do not pat players back.
- Companion wrapping and rescue work without Addon Plus, including organic emergency evacuation. Release protection prevents repeated rewrapping. Bonded-kin confinement requires physical reach and cannot begin through walls or floors; ordinary takeover carriers retain their navigation and permitted combat behavior.
- Creature players can manually release wrapped creatures through Changed's grab controls. Human takeover releases stay near the carrier rather than unexpectedly returning to spawn. Organic creatures retain low physical swipe damage against humans and ordinary damage against creature players.
- Organic creatures no longer continue attacking friendly kin after completing transfur. Accepted peace and negotiated release consistently end the current pursuit, including wild cache guards. Fresh player aggression can renew temporary grudges; the creature's own attacks cannot. Calming one player does not cancel a separate fight with another.
- Superb Warfare vehicle occupants cannot be grabbed or wrapped, while pursuit can continue after they enter a vehicle.
- Superb Warfare suppressors reduce the distance at which latex creatures hear gunshots and use the same reduced-alert behavior as suppressed TaCZ shots.
- Placed soup cans and Addon's stackable drinks, including Snepsi and Foxta, can be taken one at a time with an empty hand.
- Ordinary Synergy creatures remain on Peaceful difficulty; bosses and excluded species keep their existing behavior. Dark and White territories spawn their intended adult species as well as pups, and Latex Pup Crystals retain their faction-aware contact transfur damage.
- Tamed and bonded companions remain outside autonomous wild communities. **Mysterious Foraging Spot** is earned by opening a container at any wild outpost.

### Settings, localization and server support

- Applied the latest Russian localization contributed by TrueTransfur's author, including updated interface text and localized creature names.

- Updated compatibility with **Changed Extras 1.1.8b**: unrelated wild creatures retain its smart AI, while camp members, companions and TrueTransfur Instinct players retain their own behavior and orders. Camp members keep their jobs and assigned beds when their owner sleeps; idle pets outside camps retain Extras' normal cuddling. **Artist** is excluded from Synergy social systems, camp recruitment and origin selection like other bosses.

- Added compatibility with **Changed: Survive Protocol 1.2.0** for temporary takeover and confirmed origin re-selection, preserving its ordinary infection rules. Nether releases choose safe locations below the bedrock roof.
- Configuration is organized into **Server Settings**, **World Rules** and **Client Visuals**. Gameplay settings show authoritative server values; operators can manage categories, defaults, ranges and descriptions through configuration commands and edit Synergy gamerules in their own tab.
- Added controls for relationship growth, tokens, Playful behavior, telepathy, native role speech, Instinct-player takeover, camp limits and work restrictions. An independent option disables new autonomous wild outposts while preserving existing outposts and player camps.
- Player camps have a configurable buffer against new wild outposts, separate from wild-outpost spacing. It defaults to 48 blocks and does not move existing outposts.
- **Self-Defense Penalty Protection**, enabled by default, exempts legitimate self-defense from faction and bonded-kin penalties. Relationship debugging defaults to the creature under the crosshair and the executing player, with explicit administrator targets; `relationship establish` creates a maximum-affection friendship.
- Added Russian localization from TrueTransfur's author, the community White-faction rewrite and the Organic dialogue pack in English, Spanish and Simplified Chinese. Corrected Chinese Black/White latex terminology and updated English and Chinese social and settings guides.
- Improved server behavior around wild communities, outpost placement, blocked routes and resource searches. Exp009 lightning and aquatic-outpost searches avoid forcing unvisited chunks to load; large searches are spread across bounded batches.
- Fixed "Invalid player data" disconnects when transformed players rejoin existing worlds, preserving their saved form and valid absorption-negotiation state.
- Addressed dedicated-server Exp009 and biome failures, startup compatibility without Addon Plus, grab/QTE crashes, repeated transfur cleanup and spectator wheel lockups. Relationship wheels support left/right navigation when more options are available.

Use the same **1.1.4** build on the server and clients. TrueTransfur 2.0.0 also requires its declared **Mixin Args** dependency; the supplied TrueTransfur 1.0.1 release does not. Thank you again to every tester, writer, translator and player who sent feedback. Include your mod list and `latest.log` when reporting a reproducible problem.

## 1.1.3

Thank you to everyone who tested the mod, reported problems, and shared ideas for this update. Special thanks to Nic, Crossader, and Sword for their dialogue contributions!

### Player outposts

- Players can establish an outpost around a bell, link storage and beds, invite trusted adult creatures, and assign residents, guards, suppliers, and a following crew.
- Suppliers gather resources suited to their species and faction and deliver them to the outpost. Residents can use linked beds, including hive pillows for latex bees.

### Takeover and combat

- Takeovers offer one chance to negotiate release. Friendly and confinement takeovers eventually release the player; aggression at Distrusted standing can end in a permanent merge, while Hostile punitive takeover can end in death.
- Ordinary confinement has its own dialogue, and a refused release request extends its timer. A configurable prone period follows waking from a failed escape.
- Escaping a grab no longer counts as an unprovoked attack or costs faction reputation. If another creature takes over a grabbed player, the earlier grab and its escape prompt end.
- Pursuit squads move at a normal pace. They can appear in any dimension by default; a setting can restrict them to the Overworld. Another option limits companion combat assistance to hostile monsters involved in the player's fight.
- Creature transfur method can be set to native, random, assimilation, absorption, or eligible takeover behavior.

### Companions and interactions

- Bonded rescue wrapping and organic emergency evacuation can be disabled. Aquatic friends can shelter players after drowning damage.
- Bonded companions' tolerance for same-faction kills depends on affection; their form-reversal option appears only for forms of the same faction.
- Patting different body areas can prompt different reactions, with an eight-second cooldown between body-part lines. Alpha creatures stop leaping after a grab and can settle onto the ground when resting together.
- Bonded companions drop equipped armor and weapons on death. Revived companions no longer regain items dropped on death; species using Synergy's fallback inventory now drop its stored items too.

### Trading, dialogue, and compatibility

- Exhausted trades can no longer be refreshed through Shift-click trading and reopening the screen.
- Bonded Changed Vanilla creatures no longer try to infect animals. Suppressed TaCZ and Superb Warfare shots no longer prompt gunshot dialogue.
- Updated Light- and Dark-faction dialogue and improved missing-line handling so untranslated keys do not appear as speech. Newer takeover and patting lines have also been rewritten in English and Chinese.
- Changed Addon's hazard suit again blocks assimilation while still allowing absorption.
- Fixed a game freeze when Exp009's thunder dash or an Alpha's leap is used during voluntary suiting.
- With TrueTransfur 1.0.1 installed, temporary companion wraps end without making the player permanently transfurred. Companions still refuse unsafe reversal of a genuinely permanent form with dedicated dialogue.

Thank you again to every tester, writer, translator, and player who sent feedback! Please include your mod list and latest.log when reporting a reproducible problem.

## 0.1.2

Thank you to everyone who tested the builds after 0.1.1, reported problems, shared ideas, and helped shape this update!

Special thanks to Nic for contributing the personality dialogue rewrite and to Crossader for contributing the special-mechanics dialogue rewrite. We also thank both of them for their localization work. This release includes contributed German and Spanish lines.

These are the player-facing changes since 0.1.1.

## Companions and relationships

- Revival now extends to bonded, non-organic latex creatures beyond dark latex. A fallen companion can leave an identity-bearing latex sample. Use it in the Latex Infuser with that species' recipe to make a syringe or flask, then transfur to help rebuild the companion.
- Repaired Dark Latex Masks now use Changed's original mask transfur behavior when worn, including gradual progress and a completed transformation.
- Completing a bonded companion revival now awards the **I'll Be Back** advancement.
- Added an optional Multiple Bonds setting. Voluntary transfur remains available even when an existing bond prevents a new one.
- Added Rest Together. Bonded companions can rest nearby or lead the player to a usable bed and sleep together at a walking pace while the player remains free to look around.
- Tamed juvenile pets keep their ordinary pet behavior and cannot use bonded-companion wrapping until they mature.
- Aquatic companions can now be told to Wait on Shore.
- Bonded companions may confine an owner who repeatedly kills members of their species, with personality-dependent responses.
- The relationship roster now supports left and right page navigation, shows last known locations, and allows confirmed relationship removal even when a creature is unloaded.
- Players can retrieve an equipped melee weapon from a friend or companion.

## Social interaction and transfur

- Patting now has smooth mouse-wheel speed control through both the H key and radial menu, speed-dependent reactions, and a third-person arm animation for players and Changed creatures. The arm raises and lowers smoothly.
- Before telepathy is unlocked, creatures use species-based vocalizations. Reptile-like species can chirp, tweet, or hiss according to their mood.
- Some aggressive creatures can be reasoned with without immediately becoming friendly. An appeal may make them hesitate, show mercy, or briefly lose their momentum.
- Negotiation's next-step suggestion can be hidden in settings. When shown, it can be mistaken; offering suitable food makes it more reliable.
- Added an optional setting to vary qualifying encounters between assimilation and absorption.
- Exoskeleton takeover now displays a scrolling status log beside the player view.
- Native fusion now takes priority when takeover or hostile grab behavior could otherwise interrupt it. Creatures keep a stable fusion approach through brief target changes instead of repeatedly restarting.

## Territories and communities

- Provisioners can establish outposts in suitable Changed ruins and other non-facility structures. Latex bees also have a hive-themed outpost inside their hive.
- Indoor and hive outposts now use physical orange piles. Orange leaves regrow after harvest; players can shear fruiting leaves for dropped oranges or take oranges from a pile by hand.
- Restored additional white and dark territory buildings and latex pools, and expanded the creatures that can appear in white territory.
- White-faction reputation protection now covers the appropriate white-latex environmental hazards. White-latex creatures merge into and emerge from valid surfaces within their territory, with updated liquid effects.
- Improved Badlands creature activity and navigation around laboratory tables. A client setting can remove Pure White visual overlays.

## Trading and compatibility

- Reworked provisioner trades around oranges, berries, and species-appropriate fish. Materials, community-stock-backed exchanges, and relationship-gated collectibles use the familiar villager trading interface. Used-up offers no longer refresh when the screen is reopened.
- Added a configurable peace rule between Synergy creatures and villagers, wandering traders, iron golems, and other tagged village civilians and protectors.
- With Changed Vanilla installed, creatures in territories that respect a human player are less likely to turn nearby animals into new threats to that player.
- Added optional Domestication Innovation compatibility for owned companions, including pet equipment, beds, recall, and owner-alliance behavior.

## Server administration

- Dedicated-server operators can inspect and change gameplay, AI, and performance settings with `/changedsynergy config`. Multiplayer clients no longer present local copies of those settings as the server's configuration.
- Operators can use `/changedsynergy spawn <creature_id> <personality>` to generate a creature with a chosen dominant personality.
- Added an optional Independent Faction Reputation setting that removes rival-faction reputation penalties and allows simultaneous alliances with every faction.

## Stability

- Fixed packaged-client startup failures in repaired-mask rendering and Rest Together camera behavior.

Thank you again to everyone who tested, translated, reported bugs, or shared feedback. Please back up important worlds before updating and include your mod list and `latest.log` when reporting a reproducible problem.

## 0.1.1

Thank you to everyone who tested this update, shared ideas, reported problems, and provided feedback. Your help has shaped these systems and made this release possible.

### Body takeover

- Expanded body takeover into a complete sequence covering observation, temporary control, escape attempts, sleep, and safe release. Movement, combat, item use, and abilities are validated by the server while the carrier is in control.
- Added a separate takeover path for the Exoskeleton while preserving its original movement behavior and speed.
- Added the default-off **Transfur After Sleep** option. When enabled, the player and carrier merge into one individual: the player continues in the carrier's form with its eye appearance and name until returning to human form.
- Added **Ordinary Transfur Reversal**, which can disable Synergy's negotiated, companion, and sleep-based reversal mechanics without affecting takeover separation or Changed's recovery commands.
- Improved overlapping captures and multi-creature encounters. A completed takeover ends any previous grab, and nearby latex creatures disengage from the player and carrier when takeover begins.

### Bonded companions

- Added a revival process for bonded dark latex creatures. A fallen companion leaves behind a Broken Dark Latex Mask, which can be repaired at a crafting table with four Dark Latex Crystal Fragments. Wearing the repaired mask and undergoing transfur allows the player to help reconstruct the companion's body.
- Broken and repaired revival masks have a glowing item outline for easier identification, while their tooltips retain the bonded companion's information.
- Added the configurable **Protective Release Requests** system. After repeatedly rescuing an owner who keeps entering dangerous situations, companions with certain personalities may require several release requests before ending their protection.

### Equipment and interaction

- Friends and bonded creatures can now be equipped with suitable armor and melee weapons. Supported weapons include swords, axes, tridents, and modded items with valid main-hand attack damage.
- Preserved the original Dark Latex Wolf Pup radial menu and its functions while applying Synergy's radial-menu effects.
- Provisioners can exchange supplies with trusted players through the social wheel. Offers use the community's real generated and delivered stock, retain a proportional reserve, and report when no surplus is available instead of opening an empty exchange.
- Outpost caches now serve as protected community stores: non-allies obtain supplies through provisioner exchanges, while allied players retain direct shared access.

### Creature ecology

- Adult Pure White Latex Wolves can adapt into male White Latex Wolves when they remain far from the White Latex Forest, then return to their Pure White form inside the biome. Their identity, relationships, equipment, and Pure White faction allegiance are preserved, and the relationship manager marks the adapted form.
- Added a configuration option for the Pure White Latex Wolf adaptation mechanic.

### Performance and diagnostics

- Reworked AI scheduling for areas containing many latex creatures. Social, community, hunting, and work decisions are distributed across ticks, while distant idle creatures perform optional updates less frequently.
- Added a soft latex-AI time budget that can defer noncritical background work under server load while keeping combat, following, and companion safety responsive.
- Added a dedicated **Performance and Diagnostics** configuration section with live client FPS, server tick time, latex-AI time, active creature count, deferred-work readings, and several diagnostic switches with performance-impact descriptions.

### Commands and information

- Reorganized `/changedsynergy` into clearer command branches with clickable help.
- `/changedsynergy reputation` now displays the player's faction standings and current regional Light-faction reputation.
- `/changedsynergy relationship` opens the full relationship manager, while the existing management commands remain available.

### Compatibility and stability

- Added optional compatibility with Changed Extras 1.1.4-beta3b. Changed Extras may retain control of unrelated wild-creature AI, while friends, bonded companions, takeover carriers, and pursuit members use Synergy's relationship-aware behavior. Its target selection now respects Synergy relationships, truces, and creature alliances.
- Fixed a production Forge startup failure in Changed-creature AI timing and hardened takeover input mappings for packaged installations.

Thank you again to every tester and to everyone who offered suggestions, criticism, reports, and other feedback throughout development. Your time and attention continue to make Changed: Synergy better.

## 0.1.1-beta.2

- Added an `OrdinaryTransfurReversal` configuration option. Disabling it removes Synergy's negotiated, bonded-companion, and sleep-based transfur reversal while preserving takeover separation and Changed's `/untf` and `/untransfur` recovery commands.

## 0.1.1-beta.1

- Removed retaliation negotiations. Absorption caused by the player's aggression, cache defense, or a faction pursuit now resolves exclusively as punitive body takeover when the takeover system is enabled; stale retaliation claims from older saves are retired instead of reopening the negotiation UI.
- Replaced Exoskeleton takeover's custom random translation with paths selected by Changed's native `ExoskeletonWanderGoal`. A controlling Exoskeleton wanders only, never seeks a charger while worn, and now drives walking speed, body facing, and the transformed model's walk animation instead of visibly sliding.
- Fixed repeated boundary hits being misclassified as harmless warnings, which allowed punitive absorptions to fall through to negotiation instead of starting body takeover.
- Locked camera rotation while control belongs to an absorber or forced Exoskeleton. Ordinary takeover continues to use Changed's native suited-grab position and camera handoff; mechanical takeover now drives the worn body through short server-validated movements instead of only immobilizing it.
- Forced Exoskeleton takeover no longer releases in response to ordinary or otherwise lethal incoming damage. Sleeping controllers are restored outside the player's immediate wake area, while ordinary carriers must also leave a minimum separation around a sleeping player's safe release point.
- Hypnotic creatures can communicate intelligibly with humans before telepathy is unlocked, for both ordinary dialogue and contextual feedback. This does not unlock telepathy for other creatures or override disabled dialogue and excluded species.
- Removed takeover duration and sleep countdowns from the HUD and communication menu. Falling asleep now closes a soft-edged blackout inward from the screen edges; essential interaction prompts and recovery feedback remain.
- Made active body takeover mutually exclusive with absorption negotiation and stale friendly-wrapping synchronization. Observation now uses the carrier camera without automatically opening a menu, and restores the previous camera on borrowed control, release, or disconnect.
- Reused Synergy's active hypnosis-induction visual for Exoskeleton takeover with alternating yellow/pink frames, rather than the post-hypnosis Mesmerized effect. Mechanical recovery explicitly removes remaining worn equipment, synchronizes accessory slots, and verifies human restoration before completing the session.
- Fixed a production-only startup crash in takeover input validation: removed an unmapped shadow field and supplied the SRG names for all seven server packet handlers. Development startup alone did not expose this packaging error.
- Hardened takeover recovery: failed equipment release retains recovery state, reconnects use the same validated release path, and cross-dimension recovery no longer reuses coordinates from another dimension. Safe positions account for the restored form; interrupted sessions no longer grant emergency healing.
- Separated recorded player-initiated attacks from general hostility when deciding punitive takeover eligibility. Fixed mechanical-takeover synchronization between different players and aligned borrowed-control restrictions with server-side native ability checks.
- Added the first implementation of temporary body takeover after qualifying involuntary absorption. Punitive encounters and Competitive creatures can retain control while the player remains alive inside Changed's native wrapping state.
- Added server-authoritative takeover phases for observing, requesting brief control, a single escape attempt, sleep recovery, timed release, and emergency separation. Player movement, combat, item, container, and ability inputs are validated on the server while control is locked.
- Added an independent Exoskeleton/Benign takeover branch with no social negotiation, borrowed control, or escape attempt; it uses its own timed sleep recovery and yellow/pink visual treatment.
- Added safe takeover cleanup for death, respawn cloning, logout, dimension changes, carrier loss, stale carrier reloads, disabled configuration, and server shutdown. Successful ordinary breakouts grant the new Localhost Breakout advancement; orange compensation remains limited to eligible non-hostile sleep outcomes and leaves a nearby pile when protected placement permits, with item delivery as a fallback.
- Added takeover configuration, HUD and interaction screens, network synchronization, contextual English and Simplified Chinese feedback, and persistence/recovery data. This implementation remains pending full in-game and multiplayer acceptance testing before release.
- Synchronized substantive Simplified Chinese dialogue revisions back into natural English while retaining intentionally locale-specific lines.
- Added seven non-recursive reputation rivalries: Dark/White, Mountain/Cave, Aquatic/Desert, Forest/Badlands, Taiga/Jungle, Swamp/Savanna, and Plains/Snowy. Positive alliance gains can end a rival alliance while preserving friendly standing; peaceful interactions can change allegiance without attacking anyone. Existing conflicting alliances are reconciled deterministically.
- Reworked Friends in Every Corner to require simultaneous Respect (40+) with White, Dark, Aquatic and all 14 Light regional groups, rather than incompatible universal alliances. Updated allied achievement descriptions in both languages and retained saved achievement IDs.
- Added a one-second initial reaction window to ordinary hostile player grapples and slightly increased their damage cooldown multiplier (4/3 to 1.5). Organic bite/claw/pin pulses are two ticks farther apart. NPC-target damage, friendly wrapping, negotiated release, and secondary-transfur completion timers are unchanged.
- Added separate client-side height offsets for dialogue popups and scrolling dialogue, including in-game settings and screen-bound clamping. Defaults preserve the previous layout.
- Close-tier friends can open the creature inventory from the social wheel without becoming its owner. Access is revalidated for inventory actions; another player's exclusive companion remains protected, and concurrent viewers are prevented.
- Added limited human boundary reactions: up to two light, bare-handed hits during an initial approach make ordinary creatures back away for 30 seconds. Heavy hits, weapons and repeated hits provoke retaliation. Competitive creatures treat the gesture as a challenge and gain a temporary hostile-grab attempt bonus instead. Context-specific English and Chinese dialogue distinguishes each response.
- Added optional faction pursuit squads at -100 reputation, with a warning, 2–3 members, safe loaded-area spawning, a ten-minute player-wide cooldown, and a three-minute pursuit limit. Squads respect Peaceful difficulty, disabled mob spawning and post-transfur truces; reinforcements do not provide ordinary death loot or XP. Added faction-specific arrival lines and stand-down/withdrawal feedback.
- The enforced post-transfur damage lock also applies to human players captured after recently attacking that faction. Light boundary gestures do not count as initiating aggression.
- Extended post-transfur and negotiated-release faction grace from one minute to three minutes.
- After a successful involuntary secondary transfur, the player cannot damage members of the source faction for three minutes. Attacking cannot break this enforced truce; its expiry is preserved through player cloning and reconnects. Native decision callbacks and both Addon secondary-transfur paths apply the same protection.
- Voluntary transfur now requires the highest friendship tier (Close), checked both when showing the option and again on selection/confirmation.

- Added collision recovery after negotiated release and temporary wrapping. Released creatures refresh their bounds and, when embedded in a block, move to a checked nearby surface, including slabs and stairs.
- Restored absorbers refresh their actual body dimensions before a safe release position is selected.
- Sneak-right-click a friend or bonded creature while holding armor to equip one item into an empty, compatible slot. This interaction takes priority over taur riding and configuration, including when the armor is refused.
- Sneak-right-click a friend or bonded creature with a melee weapon to place one item in its empty main hand. Swords, axes, tridents, and modded weapons with positive main-hand attack damage are supported; ranged weapons and shields keep their normal interactions.
- Armor fitting follows Changed's body-shape, form-fitting enchantment, and item-specific restrictions, shared with the bonded inventory screen.
- Creatures already wearing equipment in any armor slot no longer inherit armor from absorbed mobs. Weapons are unaffected, and queued transfers recheck the creature's current equipment before applying.

## 0.1.0

The first stable release of Changed: Synergy builds on the RC1 stability, compatibility, and movement work, with a final round of social, riding, and quality-of-life fixes.

### Changed Vanilla compatibility

- Added official optional compatibility with **Changed Vanilla 1.0.1**.
- Changed Vanilla creatures now receive appropriate Synergy factions and species roles.
- Natural and converted Changed Vanilla creatures can participate correctly in identity, relationship, community, and companion systems.
- Preserved Changed Vanilla's dedicated zombie and skeleton conversions even when Synergy's mindless-mob transfur option is disabled.

### Stability fixes

- Fixed ticking-entity crashes caused by modifying AI goal selectors while they were being processed.
- Added a safe end-of-tick queue for Addon Plus goal changes and inherited equipment updates.
- Improved equipment inheritance when a creature absorbs an equipped mob.
- Community membership is now saved, restored, and merged correctly.
- Improved cleanup of stale wrapping and temporary transfur states after player death, cloning, and respawning.
- Provisioners now reliably put away temporary fishing rods, pickaxes, and navigation items when gathering is completed or interrupted.

### Companion movement

- Added shared land-and-water navigation for bonded and friendly followers.
- Fixed aquatic companions swimming in place when attempting to follow a player on land.
- Aquatic creatures can now locate nearby shorelines and leave the water to approach their owner.
- Added stalled-path detection and safe recovery for following, social approaches, rescue behavior, and emergency wrapping.
- Improved bonded rescue behavior when an owner is grabbed or otherwise unreachable.
- Bonded creatures attempting to prevent their owner's transfur now approach at a normal running speed instead of moving unnaturally fast.
- Other urgent rescue situations retain their faster emergency movement.

### Final release improvements

- Friendly, non-hostile latex creatures near a bed no longer trigger Minecraft's “monsters nearby” sleep restriction.
- Added automatic sideways seating for players riding supported taur creatures.
  - The camera now follows the sideways pose and uses a collision-safe offset to keep the taur's head from blocking the rider's view.
  - The pose and camera handling work for both human and transfurred player models.
- Added a **Pat Pacification** configuration option.
  - Disabling it prevents pats from stopping pursuit or creating a temporary ceasefire.
  - Ordinary pat reactions and relationship progress remain available.
- Dead latex creatures are now removed promptly from player contact lists.
- Wild latex sharks, milk puddings, and headless knights can now participate in Synergy's social systems.
  - They remain excluded from Synergy's grab mechanics.

### Compatibility and networking

- Improved Changed Addon Plus goal installation and removal safety.
- Updated compatibility documentation and optional dependency metadata.
- Uses the updated 0.1.0 release-line network protocol. Clients and servers must use compatible Synergy builds.

### Supported versions

- Minecraft 1.20.1
- Forge 47.4.x
- Changed 0.15.7
- Changed Addon Plus 2.9.2c or Changed Additions 0.1.0-V4-Fix (optional)
- Changed Vanilla 1.0.1 (optional)

Thank you to everyone in the **Changed: Synergy Discord server** who tested development builds, reproduced bugs, shared logs and crash reports, and helped polish this release.

As always, back up important worlds before updating and report reproducible issues with the relevant crash report or `latest.log`.

## 0.1.0-beta.3

Beta 3 focuses on provisioner reliability, community and facility stability, reverse-transfur fixes, radial-menu animation improvements, and expanded Golden Orange compatibility.

### Added

- Added the **Allow Mindless Mob Transfur** configuration option.
  - When enabled, latex creatures can fully transfur mindless mobs such as zombies and skeletons.
  - Disabled by default; mindless mobs will continue to be absorbed to reduce the number of persistent entities created.
- Added full Golden Orange compatibility for both **Changed Addon Plus** and **Changed Additions**.
  - Golden Oranges can be given as enhanced gifts.
  - They provide additional friendship and faction-reputation benefits.
  - They can be used as improved food offerings during negotiations.
  - Receiving creatures gain temporary regeneration, absorption, and movement-speed effects.

### Provisioners and resource gathering

- Increased the priority of provisioner work and resource delivery.
- Provisioners carrying resources will no longer be repeatedly interrupted by:
  - Idle and comfort behavior
  - Social approaches
  - Player-following behavior
  - Companion fallback work
  - Other low-priority routines
- Provisioners now resume delivery after combat or temporary path interruption.
- Added distance-aware delivery time limits, allowing provisioners to complete longer return journeys.
- Added temporary resource claims so multiple provisioners do not target the same dropped item or fish.
- Resource and fishing claims are now released correctly when a task is canceled or completed.
- Gathering and fishing behavior now respects the `mobGriefing` gamerule.
- Improved handling of fishing results so valid cargo is retained and unused catches are returned to the world.
- Provisioner cargo, resource origin, hunting cooldown, facility assignment, and storage memory are now preserved when a creature changes form.
- Improved container insertion:
  - Container slot restrictions are now respected.
  - Container stack-size limits are handled correctly.
  - Containers are only marked as changed when an item was actually inserted.

### Facility provisioners

- Facility populations now use stable colored-section assignments.
- Transition rooms no longer cause adjacent facility populations to merge or switch identities.
- Facility provisioners now remain within their assigned section while patrolling.
- Provisioners can locate the correct work room even if they spawned elsewhere in the same facility section.
- Improved facility storage discovery and reuse.
- Remembered storage containers are no longer discarded merely because their chunks are temporarily unloaded.
- Provisioners carrying cargo may return through transition spaces after being displaced by combat.
- Increased the number of facility path candidates considered when searching for a valid route.
- Maintenance-section provisioners no longer search for nonexistent gathering resources, but may still occasionally offer fish products to friendly humans.

### Communities and outposts

- Fixed multiple provisioners near the same gathering area creating or retaining separate community records for one physical outpost.
- Communities sharing the same compatible cache are now merged into one authoritative record.
- Added persistent community aliases so unloaded creatures remain connected after duplicate communities are merged.
- Added migration handling for older community records and regional identifiers.
- Stabilized Light faction regional identities, including cave and Deep Dark detection.
- Facility community identity is now preserved when combat pushes a creature across a section boundary.
- Existing outpost blueprints are now authoritative and cannot be replaced by a visiting creature from another regional population.
- Fixed established outposts repeatedly changing between forest, cave, or other regional layouts.
- Improved cache validation when an outpost or storage chunk is temporarily unloaded.
- Improved habitat validation for existing outposts.

### Reverse transfur

- Fixed cases where a latex creature would grab the player briefly, release them immediately, and fail to reverse the transfur.
- Fixed reverse transfur becoming unreliable when Changed Addon Plus was installed.
- A successful negotiation is now treated as committed even if the Addon grab animation loses its target.
- Pending reverse-transfur completion is now recovered correctly after skipped ticks, chunk unloading, or server restarts.
- Failed presentation animations no longer restore old negotiation progress after success has already been confirmed.

### Radial menus

- Radial menus can now reverse directly from their current opening progress when closed early.
- Closing a partially opened wheel no longer forces it to finish opening first.
- The relationship information panel now remains stationary while fading during a switch to the ability wheel.

### Compatibility and documentation

- Updated Changed Addon Plus and Changed Additions compatibility documentation to describe Golden Orange support.
- Updated the README and third-party notices to reflect the expanded integrations.

### Notes

This is still a beta release. Back up important worlds before updating.

Please include your `latest.log`, exact mod versions, and installed optional integrations when reporting reproducible issues.

## 0.1.0-beta.2

Second public beta update.

- Reworked reverse transfur recovery for assimilation, absorption and fusion, including safer source restoration and interrupted-session cleanup.
- Prevented release from wrapping or absorption while airborne or near hostile non-latex mobs; release becomes available again once the area is safe.
- Added controlled gliding for winged latex creatures using their native flight animation.
- Fixed provisioners failing to resume resource delivery after combat.
- Kept the final orange in a placed orange pile so the pile remains available for restocking.
- Added a minimum spacing claim around community outposts to prevent adjacent settlements from replacing or crowding one another.
- Added basic Changed Additions compatibility and documented the optional integration.
- Excluded Addon boss entities and Foxyas from Synergy social, relationship, hypnosis and companion systems.

## 0.1.0-beta.1

First public beta candidate.

- Added persistent creature identities, personalities, social memory, friendship and voluntary bonds.
- Added social, relationship and companion wheels with animated transitions.
- Added contextual English and Simplified Chinese dialogue, emotes and configurable telepathy presentation.
- Added faction reputation, territory presentation, white hive cooperation and relationship-aware combat safety.
- Kept friendship recoverable at low faction standing, with reduced gains and continued progress from repeated peaceful interaction.
- Added negotiation for involuntary assimilation, absorption and fusion, with motive- and personality-dependent choices.
- Added grab and hypnosis QTE work, swimming pursuit, firearm reactions, disguise behavior and organic combat rules.
- Added creature roles, resource gathering, fishing, hunting, outposts, guards, gifts and environment interactions.
- Added optional Changed Addon Plus integration and basic Changed Additions compatibility.
- Added accessibility and visual work for wrapping, organic assimilation and pure white forms.

This list describes the public baseline rather than every development revision.
