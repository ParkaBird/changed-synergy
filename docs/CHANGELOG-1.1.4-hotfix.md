# Changed: Synergy 1.1.4-hotfix

- Fixed unresolved introduction-dialogue placeholders after wheel gifting. Applied Goldfinder's English dialogue corrections from PR #12 and corrected additional scent-related typos.
- Restored Partial Dark Latex Wolf to starting-origin selection. Partial Snow Leopard is also available with Changed Addon Plus when modded starting species are enabled.
- Friendly arm holds now allow harmless WASD escape, retain escape progress and reliably clear expired or interrupted hugs without hurting or stunning the creature.
- Travel invitations can recall distant, loaded followers to a safe nearby position even when their current area is not ticking creature AI.
- Stasis-chamber modification, growth and absorption retain names, affection, friendship, bonds, camp assignments and carried work cargo across form changes.
- Improved emergency rescue when a companion is resting or the hostile grabber belongs to the same faction. Waiting orders and attack settings remain respected, and automatic wrapping requires unobstructed bodily contact.

- Added compatibility for JEI 15.70.0.221 screen rendering, keyboard/mouse input and item-display interactions, including clicking, dragging and releasing carried items. JEI remains optional.
- Fixed provisioner bulk trades producing unpaid goods when combat, grabbing or takeover interrupted a transaction. Invalid purchases are rejected before goods leave the result slot, and unused payment is returned when trading closes.
- Companions can navigate powered facility doors and briefly crouch under low frames when their body fits. Normal power and creature-access restrictions are preserved.
- Fixed "Invalid player data" disconnects when transformed players rejoin existing worlds. Saved forms and valid absorption-negotiation state are preserved.
- Fixed Superb Warfare suppressors being ignored by latex hearing. Suppressed shots have a shorter detection range and reduced alert reactions; nearby creatures can still hear them.
- Fixed organic latex retaining obsolete hostility after transforming a player, while preserving normal damage against valid transformed opponents.
- Applied the latest community Russian localization.

**Faction behavior:** This hotfix keeps the original hostility between Dark and White latex. The peaceful-competition rework is not included.

---

# Changed: Synergy 1.1.4

**TrueTransfur integration:** Changed: Synergy 1.1.4 pairs with **TrueTransfur 2.0.0** for native latex origins, roleplay and Instinct-mode social interactions. Backward compatibility with **1.0.1** is retained; the expanded Instinct features require 2.0.0. TrueTransfur remains optional for other Synergy features.

Thank you to everyone who tested the mod, reported problems and shared ideas after 1.1.3. Special thanks to Nic, Crossader and Sword for their dialogue and localization contributions, and to TrueTransfur's author for collaboration, testing and Russian localization!

### Native latex origins and TrueTransfur

*   Begin as a human or a native latex creature with TrueTransfur, including eligible juvenile and fusion forms. Supports TrueTransfur **2.0.0**, with backward compatibility for **1.0.1**; TrueTransfur remains optional for other Synergy features.
*   Native players belong to their species' faction, begin at Recognized standing and receive greetings from compatriots. Their origin, citizenship and permanent creature identity persist through death and rejoining.
*   Birth placement searches for safe territory suited to the chosen species, including previously unloaded terrain. If no suitable home is found, choose another species or approve a safe birthplace near world spawn.
*   The **Starting Origin Selection** gamerule controls this mode. While enabled, TrueTransfur's permanent-identity features apply only to native players; disabling it restores TrueTransfur's normal applicability. Ordinary Changed transformations remain intact under either policy.
*   A default-off **Human-Origin Instinct Control** setting lets ordinarily transformed human-origin players use TrueTransfur 2.0's Instinct mode without granting permanent identity, lethal reversal, native rebirth or native roleplay features.
*   TrueTransfur 2.0's visible Instinct body represents the real player for social interaction, companion following, combat and faction reputation. The hidden technical player is ignored by creature AI. The visible body has no persistent nameplate, duplicate NPC identity or wild-community membership.
*   Native players are protected from hostile secondary transfur. A separate setting can extend this protection to other transformed players, causing enemies to attack instead. Attacking or killing native players can affect faction standing, with its own gamerule and self-defense exemption.
*   Operators can inspect origins or reopen selection with `/changedsynergy origin`. Optional re-selection can be cancelled with Escape; choosing human safely restores the human body. Players are protected from mob targeting while selecting.
*   A default-off option adds eligible playable forms from other installed mods. Servers can disable individual starting forms; bosses and unsuitable special forms remain excluded, and existing origins are preserved.

### Native identity, player friendship and roleplay

*   Native players receive a persistent creature name from their species' naming pool. Change it through the relationship wheel or `/changedsynergy social name`; account names, UUIDs and permissions remain unchanged.
*   A six-expression wheel lets native players show short-lived expressions on their visible body in either control mode. Relationship-wheel navigation hints share one line, and the chat-mode switch stays beneath command suggestions.
*   Native players can speak through nearby floating dialogue or switch to normal global chat. Role speech follows the same understanding rules as NPC dialogue: listeners who cannot understand the species hear creature sounds, while the speaker sees their original message. Clients with floating dialogue disabled receive the corresponding text in chat.
*   Right-click a player or their Instinct body to greet, pat, transfer a gift or invite them to walk together. Player friendship requires mutual consent, has shared familiarity, and remains separate from NPC bonds. Gifts still transfer during familiarity cooldowns.
*   Confirmed friends' walking invitations are accepted automatically in Instinct mode; strangers need consent. Manually controlled players retain control and see the inviter highlighted. Either side can end the arrangement.
*   With TrueTransfur 2.0, under server PvP and alliance rules, an eligible native Instinct player can pursue a hostile human using ordinary Changed transfur attacks. Only completed transfur progress and physical contact permit temporary takeover; approaching the human alone does not capture them.
*   The human can negotiate or attempt WASD escape. Timed release, control-mode changes and interrupted sessions release them safely at the current location, without permanent transformation. Other latex creatures cannot attack or recapture a contained human, and release grants a protection period.
*   Fixed camera crashes when entering Instinct, including Latex Human forms.
*   Human takeover countdowns keep their top-centre position and time/outcome text, using Changed's release-bar appearance and the captor's species colour. Instinct captors also see the native release bar above their own hotbar.
*   Players can look around freely in third person during takeover while first-person view follows the carrier. Temporary suits remain synchronized throughout movement, and Instinct pursuit retains continuous melee behavior at close range. Worn exoskeleton controllers follow terrain with collision, falling and native jumps, without pausing at each path node.

### Archive character selection

*   Replaced initial selection with an animated archive adapted from the author's Godot UI: a human record and five creature sections, White, Dark, Variant, Organic and Aquatic. The welcome page introduces **Synergy × TrueTransfur** together.
*   Species cards show portraits from their actual models. Dossiers include body parameters, faction and rival faction, plus a rotatable, zoomable paper doll with a crouching pose. The human record uses the player's account and skin.
*   Added animated page turns, paper-corner navigation, keyboard controls and Minecraft book sounds. Long localized confirmations and birthplace notices can be scrolled while their action buttons stay visible.
*   A note on the archive's opening cover offers a fully random origin, including human. Each creature section also offers a random species from that section. Both use the visible, server-approved catalog and respect form restrictions.
*   Male and female Phage Wolves belong to the Dark faction and appear in its archive section. Dossiers show the species' actual regional faction and opposition.

### Friends and bonds

*   Friendship now progresses through initial familiarity, a deliberate first gift and continued interaction. Before acquaintance, ordinary affection stops at **8**; a suitable wheel gift at that point establishes the relationship and gives the creature a name.
*   Patting, direct feeding and shared activities gain affection through a shared **60-second cooldown**. After acquaintance, each accepted wheel gift refills an **8-point care allowance**. Talking, repeated encounters and return pats retain their reactions and memories without granting affection.
*   Wheel gifts have an independent **five-minute cooldown** and accept one item: ordinary gifts grant +2, preferred gifts +4 and Golden Oranges +6. Direct feeding remains separate and can heal during affection cooldowns. Rejected gifts are not consumed.
*   First bonds require **60 affection** and a species-themed token: Dark Latex Crystal Fragments, Quartz, Amethyst Shard, Golden Apple or Heart of the Sea. The wheel explains the item, quantity and eligibility rules. Human players then confirm voluntary transfur; native and permanently transformed TrueTransfur players can bond without changing bodies or factions.
*   Unused tokens persist while players are offline or creatures are unloaded and can be withdrawn. Ending a bond through the roster or commands preserves friendship, name, affection and memories. Former companions can reunite without another token.
*   Adult **Playful** friends below Close affection may occasionally attempt friendly transfur. A light bare-handed warning interrupts the attempt without reputation loss. Playful and Polite are mutually exclusive behavior traits; other personality traits determine dialogue.
*   Native pet relationships bypass ordinary acquaintance and token requirements. Tamed Dark Latex pups retain proper owner recognition when growing up, and Addon Exp2 companions enter the relationship system without extra gifts. Juveniles retain their age restrictions.
*   Feline creatures accept raw and cooked cod or salmon, including without Addon Plus. Suitable direct feeding and a trusted friend's mediation can restore peace after hostility, without erasing faction reputation or relationship history.
*   Added advancements for bond tokens, reuniting and settling a Playful friend's attempt, plus English and Simplified Chinese social guides.

### Player camps and working residents

*   Establish camps around a campfire; existing bell-founded camps remain supported. The relationship wheel offers **Establish Camp**, **Link Facility** or **Invite to Camp** when aimed at an appropriate target. New invitations require Close friendship.
*   `outpost link` identifies the targeted facility automatically. Multiple beds are assigned separately at night; pillows, office chairs and tall cardboard boxes are separate rest points. Residents use them during downtime and leave promptly for work or orders.
*   Updated camp management with reliable individual and team follow, wait, return and retreat orders for residents, guards, suppliers and travelling crew. Hovering over a member's name shows its current coordinates, or its last known location when unloaded.
*   Suppliers can perform gathering, hunting, farming, cooking or hauling, with a main and backup job. Real products are kept in accessible **Work Cargo**, preserved when delivery is blocked, and returned to linked containers. Chest visits have opening and closing animations and sounds.
*   Farmers harvest mature crops, replant the same crop and compost surplus seeds when appropriate. Farmer's Delight cabbage, onions, tomatoes and rice are supported. Farmers collect batches before unloading and store produce before resting, including the bone meal produced by composting. Stored bone meal can then be used to grow immature crops.
*   Cooks collect ingredients from storage and use all four campfire slots, including raw fish and food-only furnace recipes. Haulers collect leftover cooking outputs, combat loot and other loose items, batching collections before delivery. Servers can exclude specific items from hauling.
*   Farming, hunting, gathering and hauling can use marked work areas up to **64 blocks** from camp. Unmarked hauling covers **32 blocks** around the camp. Workers stay near assigned areas during idle periods.
*   Shared meals gather residents safely around the campfire, preferring nearby linked rest points. Members animate eating food from their own inventory, collect a serving from storage if needed, then rest briefly before resuming duties.
*   Work, meals, sleep, combat and movement orders have coordinated priorities. Non-guard workers retaliate when attacked instead of abandoning tasks to initiate fights; guards retain their defense role. Sleeping residents do not pat players or give social reactions.
*   Camps default to **16 members** and **six travelling crew**, configurable up to 64 and 32. Existing settings and memberships are preserved. Provisioners resume trading after leaving camp, retaining their used trade limits.
*   Added camp advancements for establishing a home, receiving deliveries and sharing meals. With TrueTransfur installed, native origins and body-preserving bonds have their own advancements. Updated the English camp guide and in-game command help.

### Travelling crews and companion work

*   Assign **Combat**, **Mining**, **Gathering** or **Hunting** through camp management or `outpost crew`. Gathering jobs follow at a looser distance, finish nearby work before catching up, and respond to danger and recall orders. They follow the owner's visible TrueTransfur body when applicable.
*   Miners work through exposed, reachable ore veins, including newly exposed ore. Gatherers harvest reachable orange-tree fruit, sweet berries and glow berries while preserving regrowth. Hunters pursue eligible adult food animals and collect the actual kill drops; babies, pets and other protected animals are excluded.
*   Work uses all six cargo slots and matching partial stacks. Full bags pause new collection while members continue following, and supplies are unloaded when the crew returns near camp storage. Stationary suppliers and travelling gatherers have independent work slots.
*   Added travelling-work controls and an ore blocklist. Searches and pathfinding are local and bounded; crews do not dig tunnels, load distant chunks or remain behind indefinitely.
*   Companion mining uses pickaxe material, Efficiency, Fortune and Silk Touch. Fishing uses Lure and Luck of the Sea. Suitable player-supplied tools in the creature's inventory take priority over default tools and lose durability normally.
*   Combat assistance respects player alliances, target filters and the owner's actual visible body. Companions do not treat unprovoked neutral creatures as hostile monsters. Friendly transport grabs do not provoke rescue attacks, and wait orders and Never Attack are respected by automatic protection.

### Abilities, takeover and creature ecology

*   Hypnotic creature players and human players share a PvP hypnosis QTE, with the same presentation and movement-key controls on both sides. Player contests last longer than NPC contests; a human's pat prevents the hypnotist from adding control for three seconds.
*   Pure White players adapt their appearance and vision outside their territory while retaining their species, abilities and faction. The existing regional-adaptation setting controls this behavior.
*   Transformed players have third-person pat animations where their model supports them. Models without usable arms safely skip the animation; feral NPCs, Milk Puddings and quadrupedal pups do not pat players back.
*   Companion wrapping and rescue work without Addon Plus, including organic emergency evacuation. Release protection prevents repeated rewrapping. Bonded-kin confinement requires physical reach and cannot begin through walls or floors; ordinary takeover carriers retain their navigation and permitted combat behavior.
*   Creature players can manually release wrapped creatures through Changed's grab controls. Human takeover releases stay near the carrier rather than unexpectedly returning to spawn. Organic creatures retain low physical swipe damage against humans and ordinary damage against creature players.
*   Superb Warfare vehicle occupants cannot be grabbed or wrapped, while pursuit can continue after they enter a vehicle.
*   Placed soup cans and Addon's stackable drinks, including Snepsi and Foxta, can be taken one at a time with an empty hand.
*   Ordinary Synergy creatures remain on Peaceful difficulty; bosses and excluded species keep their existing behavior. Dark and White territories spawn their intended adult species as well as pups, and Latex Pup Crystals retain their faction-aware contact transfur damage.
*   Tamed and bonded companions remain outside autonomous wild communities. **Mysterious Foraging Spot** is earned by opening a container at any wild outpost.

### Settings, localization and server support

*   Updated compatibility with **Changed Extras 1.1.8b**: unrelated wild creatures retain its smart AI, while camp members, companions and TrueTransfur Instinct players retain their own behavior and orders. Camp members keep their jobs and assigned beds when their owner sleeps; idle pets outside camps retain Extras' normal cuddling. **Artist** is excluded from Synergy social systems, camp recruitment and origin selection like other bosses.

*   Added compatibility with **Changed: Survive Protocol 1.2.0** for temporary takeover and confirmed origin re-selection, preserving its ordinary infection rules. Nether releases choose safe locations below the bedrock roof.

*   Configuration is organized into **Server Settings**, **World Rules** and **Client Visuals**. Gameplay settings show authoritative server values; operators can manage categories, defaults, ranges and descriptions through configuration commands and edit Synergy gamerules in their own tab.

*   Added controls for relationship growth, tokens, Playful behavior, telepathy, native role speech, Instinct-player takeover, camp limits and work restrictions. An independent option disables new autonomous wild outposts while preserving existing outposts and player camps.

*   Player camps have a configurable buffer against new wild outposts, separate from wild-outpost spacing. It defaults to 48 blocks and does not move existing outposts.

*   **Self-Defense Penalty Protection**, enabled by default, exempts legitimate self-defense from faction and bonded-kin penalties. Relationship debugging defaults to the creature under the crosshair and the executing player, with explicit administrator targets; `relationship establish` creates a maximum-affection friendship.

*   Added Russian localization from TrueTransfur's author, the community White-faction rewrite and the Organic dialogue pack in English, Spanish and Simplified Chinese. Corrected Chinese Black/White latex terminology and updated English and Chinese social and settings guides.

*   Improved server behavior around wild communities, outpost placement, blocked routes and resource searches. Exp009 lightning and aquatic-outpost searches avoid forcing unvisited chunks to load; large searches are spread across bounded batches.

*   Addressed dedicated-server Exp009 and biome failures, startup compatibility without Addon Plus, grab/QTE crashes, repeated transfur cleanup and spectator wheel lockups. Relationship wheels support left/right navigation when more options are available.


Use the same **1.1.4** build on the server and clients. Thank you again to every tester, writer, translator and player who sent feedback. Include your mod list and `latest.log` when reporting a reproducible problem.
