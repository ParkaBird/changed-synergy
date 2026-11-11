# Natural camps and the forest treehouse

Natural camps are authored structures with their own residents. They are separate from player-owned camps and the outposts that roaming creatures build. They include the forest treehouse, Hyena's Hideout, the three hyena camps and the mountain observatory. See `OBSERVATORY.md` for the new camp's terrain checks and fixed Chief.

## Forest treehouse

- Template: `changed_synergy:forest_treehouse`; natural generation is restricted to `minecraft:forest`.
- The supplied 27 × 41 × 24 building keeps its complete ground layer at the natural surface. The trunk, rooms and internal routes do not move independently.
- Sites must be dry and gently sloping. A three-block clearing keeps trees out; its outer ground steps toward the surrounding terrain, at most two blocks above or below the foundation. The first outer row stays level with the building.
- Neighboring forest decoration finishes before the final chunk repair. That repair runs once and restores no entities; it does not keep overwriting player changes. Pending repairs survive an unload or restart.
- The fixed canopy resident is a Silk Moth wearing the Canopy Outfit. Two deer, two squirrels, one gas skunk and another Silk Moth join the Forest population on safe ground tiles. The lectern journal's species references match its new resident, with long passages continued on extra pages. Other authored books, containers and loot tables remain intact.
- Imported the completed editing-world changes, including the loom and flattened entrance ground. Both halves of the adjacent double chest use vanilla shepherd loot. The canopy character has golden (`#FFC100`) irises and Changed's Tall eye style. Editing-only NPC restrictions and world-local relationships are removed; the journal's complete text is preserved.

The supplied forest watchtower also generates only in `minecraft:forest`. Its 20 × 29 × 20 template uses a two-block clearing, the same slope limit and a flush foundation, retains the authored chests and three item frames, and adds no creature residents. Locate it with `/locate structure changed_synergy:forest_watchtower`.

## Landmark rarity and explorer maps

Treehouses use woodland-mansion placement parameters: 80-chunk spacing, 20-chunk separation and triangular spread. Hyena hideouts use ocean-monument parameters: 32 / 5 and triangular spread. Existing biome restrictions, foundation checks and exclusion zones still apply, so actual density depends on terrain. Only new structure starts use these settings.

Forest watchtower chests have a 25% chance to add a treehouse explorer map in ordinary forests. Tropical hyena-camp storage has a 25% chance to add a hideout map in jungle or savanna biomes. Original supplies are retained. Eligible vanilla dungeon, mineshaft, ruined-portal, jungle-temple, pillager-outpost, savanna-house, fletcher and shepherd loot tables add the corresponding biome's map at 5%. Nested supply tables do not add a second roll. Opened containers keep their contents.

Maps use native explorer-map locating and saved map data, translated names and mansion-style target markers. Destination structure tags support datapack overrides; known landmarks remain valid destinations. No background searches, ticking entities or chunk tickets are added.

## Residents

The hideout's female Spotted Hyena starts beside the orange bed in Trailcloth. The green laboratory's Gas Wolf starts beside its original office chair and wears Changed's native lab coat and red necktie. Default garments are applied once; subsequent player changes are preserved. These characters and the canopy Silk Moth retain their home boundaries, identity and persistence after reload. They walk home when displaced, with bounded path retries and a safe fallback if the route fails; carrying, grabbing and player-controlled bodies are not teleported.

The laboratory character does not gather, hunt or establish a wild outpost. Camp residents can pick up unclaimed drops, harvest and replant mature crops, gather berries, hunt wild adult animals, deposit real cargo and share finite food at a lit campfire or in the Desert Refuge common room. Authored livestock are excluded from hunting. The main communities and new desert camps use their marked public reserves only; private and loot containers remain excluded after opening. Small camps use their built-in supply containers as public reserves, preserving and initializing their original loot once. Double chests and inventories exposed through the existing modded-storage adapter remain supported. Full or unloaded storage retains cargo and stops more gathering.

Residents use the existing faction-reputation reactions when players open or break camp storage. The fixed landmark characters stay within their structure; ordinary residents' player-follow and player-camp commands take priority over camp routines.

Both natural-camp and wild-outpost storage require a live, awake eyewitness within 16 blocks of the player, with native line of sight. A blocked or cancelled click is not an opening: the actual opened menu must match the clicked storage, including double chests and item-handler containers. Existing allied access, respected visitors' two warnings, reputation costs and creative/spectator exemptions remain. Natural-camp eyewitnesses can defend through native combat AI without taking wild-outpost guard jobs, then end pursuit at the existing defence leash and return to their posts. Warnings and cooldowns belong to each natural camp separately. Wild-outpost guard recruitment and action allocation remain unchanged.

Template occupants with missing/default eye data receive native randomized eye styles and iris colors once. Authored non-default eyes, including authored Chief eye styles and colors, remain intact. Hair, sclera, body scale, outfits and identities are preserved. Existing tagged residents with untouched default eyes are repaired on their next entity load; initialized or customized eyes do not reroll on later loads.

Native chairs, pillows and boxes are preferred to carpets; carpets are preferred to bare ground. Meals reuse the existing saved cargo, native seat handling and interruptible eating lifecycle. Failed path searches and work searches are bounded, and camps do not add chunk tickets.

Small-camp initial populations match complete bed pairs: tropical, open-country and snowy Hyena camps each have two residents and two beds; Oasis has two residents and two beds; Muddy Campsite has four residents and four beds. Existing placed structures keep their saved occupants; these template populations apply to new generation or placement.

During their species' sleep period (daytime for Eagle Owls, nighttime for other residents), camp residents reserve reachable beds inside their own structure and use the native sleeping pose. Bird residents recognize built-in perches as beds and prefer free perch positions before ordinary beds; each stand has one position and each rail has two. Perches do not need block entities: loaded chunk-section palette checks locate them within the camp bounds. Other residents retain ordinary beds. Reservations are shared with player-camp rest handling so two creatures cannot claim the same bed. They wake when their active period starts or when interrupted by combat, following or player assignments. Unload/reload clears stale sleeping state and reservations. Bed searches use loaded chunks only, at most four bed candidates per attempt (with a bed-foot fallback when a low roof blocks the pillow), with a ten-second retry interval and a twenty-second walking timeout. The laboratory Researcher shares bed reservation and sleeping only within the attic level, and uses nearby seats for rest. He may eat food from his own inventory while seated but does not join resource work or use laboratory storage.

`RELATIONSHIPS.NaturalCampRoutines` defaults to `true`. Turning it off pauses camp work without deleting saved cargo or removing resident boundaries. Template residents and generation changes apply to newly generated or newly placed structures.

## Other updates in this build

- Trailcloth uses the supplied male/female Blockbench models and their embedded textures. Its Chinese name is **行猎装**. `TAIL` and taur bodies use the upper outfit only; bipedal wearers retain the original lower outfit and native leg-slot conflicts.
- When Curios is installed, native clothing uses real Curios slots and storage. Changed's clothing API reads and writes those same stacks; original player and creature clothing slots are removed. Creature storage remains usable beside the equipment sidebar. Native anatomy, effects and clothing conflicts are retained, and right-clicking a style-capable equipped garment still switches its style. Existing clothing migrates on load; compatibility snapshots allow returning to the native inventory without Curios. Without Curios, the original clothing interface remains available.
- Muntjac charging preserves the raw input edges used by creative flight. Short taps, held charging, double-tap takeoff/landing and controlled Muntjac wraps share the same passive logic.
- `DIALOGUE.NativeChatAnimalCalls` defaults to `true`. Native-origin players occasionally vocalize when speaking in global or local role chat. Calls use the actual controlled body's location, have a server cooldown, and do not change chat text. Supported Addon sounds are reused when available, with vanilla sounds for other supported animal profiles.
- Menu slot and clothing-style synchronization require both clients and servers to update; the Synergy channel protocol is **62**.

## Hyena food

The diet works for NPCs, transformed players and humans controlling a Hyena wrap. Other species keep their original item behavior. Ingredients are consumed through the normal timed eating action; creative players retain their stack.

| Food | Nutrition | Short effect |
| --- | ---: | --- |
| Bone | 2 | Random existing positive level-I effect, 8 seconds |
| Leather | 2 | Resistance I, 5 seconds |
| Rotten flesh | 6 | No Hunger penalty |
| Spider eye | 2 | No effect or food penalty |
| Pufferfish | 1 | Water Breathing, 8 seconds |
| Poisonous potato | 2 | No effect or food penalty |
| Rabbit foot | 2 | Jump Boost I, 10 seconds |
| Sugar | 1 | Speed I, 6 seconds |
| Golden carrot | 6 | Night Vision, 10 seconds |
| Blaze powder | 1 | Strength I, 5 seconds |
| Magma cream | 1 | Fire Resistance, 8 seconds |
| Glistering melon slice | 4 | Instant Health I |
| Ghast tear | 1 | Regeneration I, 8 seconds |

Food's newly applied harmful effects are removed without curing an existing enemy debuff. Friendly auto-eating uses each food's actual species-specific nutrition and saturation. Communal meals reserve precious foods and consume one real portion.

The draft-book chests beside the treehouse campfire (26, 1, 0) and hideout well (1, 4, 0) are omitted from new templates. All other blocks, containers and residents are preserved.
