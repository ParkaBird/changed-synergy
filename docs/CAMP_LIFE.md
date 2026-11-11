# Local household life

The forest treehouse, Hyena's Hideout, observatory and Desert Refuge are separate households. The player joins one as an ordinary member. Regional factions, ecological populations, birth identity, individual affection and bonds remain separate. Small hyena camps, Oasis camps and Muddy Campsites retain their own routines and supplies; the laboratory Researcher does not recruit.

## Joining and living here

Neutral local trust and no immediate hostility allow admission. Human, existing latex, reversible and permanent routes grant 0/2/5/8 local trust, topping up only the highest unpaid difference for that household. Permanent transformation still requires TT and its existing second confirmation; it registers the chosen species as Native Origin. Authorized origin re-selection can return that player to human without clearing household membership or reward history. A member can revisit the form explanation without leaving first. A different current home triggers a separate confirmation before any membership or body change is committed.

Trust remains between -100 and 100. At -15, shared rights are suspended until neutrality returns; at -40, membership ends. Departure keeps trust, relationships, reward history and the body. Members share public stores and free sleeping places. A visitor with local trust 40 can also borrow a sleeping place. Creative players bypass the sleep permission check. Vanilla bed occupancy, body suitability and sleep-time checks still apply. Existing respawn points are not erased.

Each main community has an empty, named **Community Supplies** double chest. The Desert Refuge uses a designated indoor meal point without requiring a campfire. Residents take supplies only from this reserve and put hunting, gathering and pickup proceeds into it. Donations and public requests use the same reserve. Personal requests are handed to the requesting resident’s personal inventory. A full, removed or unavailable reserve leaves cargo with the resident; other chests never become fallback storage. Loot chests stay outside this system after opening too. Small camps use their built-in supply containers, which remain private to players while residents can use them.

Explicitly marked private containers remain personal at any local trust. Lecterns and other readable story media are freely accessible. A dedicated reserve is explicitly shared, so leaving a book in it does not turn the whole reserve private. Explicit private flags still take precedence. Both halves must belong to the reserve; attaching an ordinary chest cannot expose its contents. The added blocks appear in newly generated or newly placed structures; existing saved buildings are not overwritten.

Admission establishes ordinary relationships with loaded authored residents of that household and adds one affection point per individual once. Residents loaded later are introduced to online home members too. Leaving, rejoining and NPC return cannot repeatedly pay this introduction. It grants no ordinary first-encounter reputation or extra admission trust. The roster marks current campmates with a text badge distinct from a bond, including remembered unloaded contacts. Leaving removes that badge while retaining the personal relationship.

Ordinary residents keep a six-block margin around the building for nearby gathering and hunting, reduced from twelve. Chiefs retain their original building boundary. A social follower cannot lead a resident beyond the margin; that resident can then resume returning home. Explicitly adopted companions and residents assigned to a player outpost retain their existing ownership rules.

## Conversations and contributions

Ask a Chief about camp life, supplies, requests or a shared meal. Ordinary residents can discuss household requests, with their existing language checks. Unknown speech produces gestures and an option to ask the Chief. Learned human speech is not granted to ordinary residents and telepathy is not unlocked by talking.

Donations use the held stack and an explicit quantity. Food and materials are configured with the `changed_synergy:camp_*` item tags. Named items and bound equipment are refused. The server checks the selected item, tags, count, actual demand and storage space before transfer. Native containers and Forge item handlers use the existing outpost insertion mechanism, including simulated capability capacity checks. A handler that inconsistently accepts only part of a promised transfer is charged only for the part it actually stored; a request retains the remaining amount and receives no premature reward.

Each household has at most three pending public requests, plus individual residents’ personal food requests. Chiefs offer and accept public requests; ordinary residents offer and accept only their own personal requests. A personal hand-in never enters the reserve and can succeed when that reserve is full. These include food, a household material and protection of residents. Existing pending requests have no deadline and are not replaced when the day changes. Completed slots can refresh on the next active game day. Item requests normally require the whole amount; the first successful server submission completes the shared request. There is no exclusive reservation that an absent player can hold indefinitely.

Donation assistance earns one local point; a completed material request earns two. Returning public supplies first repays that player's withdrawal record. Ordinary menu deposits reduce the outstanding record but do not award trust. Pickup, shift-click and other menu transfers are measured around the actual server click, independently of eyewitness reactions. The new contribution allowance is eight points per player, household and server-game day. It does not decay during absence and cannot accrue missed-day rewards.

Witnessed mature-crop harvesting earns a tending point only when the same crop is replanted at the same position within ten seconds. A hostile's defeat while targeting a camp resident can earn two points and records protection-request evidence. Reporting that request requires evidence and a safe camp, and adds no second trust payment. Helping and fulfilment may grant existing, cooldown-limited individual affection.

Shared-meal invitations schedule the existing resident meal routine. They require food and a lit, dry campfire or the Desert Refuge’s common-room gathering place, create no food and have a five-minute household cooldown. Residents still walk, claim a seat and consume real supplies through the original routine. The player chooses whether to sit or eat.

## Invitations and scheduling

A resident with a request may approach an idle home member within twelve blocks and inside the structure. Only one invitation can target a player at a time. A household waits at least five minutes between invitations; each resident offers once per game day. The resident stops near the player, gives the normal species/language-aware cue and waits. No screen opens automatically. Speaking to the resident, danger, sleep, a busy container, a removed actor or a disconnected player releases the invitation. Movement never teleports the resident or clears a combat target.

Invitations respect nocturnal Owl and diurnal resident schedules. Conversation holds block camp work, and combat ends the hold. Existing gathering, farming, hunting, livestock protection, sleep and campfire routines remain autonomous. No public currency, upgrade tree, management page, player work orders or offline upkeep is added.

## Persistence and integration

`changed_synergy_memberships` continues to own player membership, local trust, ritual history and contribution accounting. Version 3 preserves existing local values and paid amounts; older faction-linked registrations retain their explicit home, initialize missing local trust to zero and convert the old permanent-reward marker without importing regional reputation.

`changed_synergy_camp_life` owns structure household records, pending requests, completion evidence and shared invitation/meal deadlines. Its key is the existing dimension-and-structure-origin home key. It survives a Chief's death or unload. Known home records retain the original dimension if an actor is transported; remote supply transfers are refused. No chunks are forced or simulated while unloaded.

The network carries the campmate flag to the relationship roster and retains localized component arguments in the existing conversation message. Choice IDs, session nonces, revisions and eligibility remain server-owned. The existing dialogue renderer, punctuation timing, thought presentation and reduced-motion behavior are retained. Clients and servers must use matching builds; `ChangedSynergyNetwork` owns the protocol definition. New writing and text styling live in `tools/authoring/camp_life_dialogue.json`; the compiler and complete EN/ZH/RU literary editing exporter are beside it.

## Focused cleanup receipt

- **C1 — regional membership coupling:** removed regional-score initialization, shared threshold ownership and dual local/regional event writes from household admission and conduct. Regional commands retain explicit regional semantics; wild factions and birth treatment remain. Migration consumes only the old stored keys and preserves ritual/body state.
- **C2 — unused profile faction:** removed the unconsumed field from the authoring source, compiled profiles and runtime profile record. Species/population faction tags remain. Runtime, generated-data and repository call-site searches found no remaining consumer of the profile member.
- **Validation:** the NPC server fixtures cover the retained rituals, migration and restart contract. Household checks exercise real menu clicks, local isolation, privacy, actual supply movement, shared-request races, consent, language handling and lifecycle. Native client checks use server-generated household states at three GUI sizes in English, Chinese and Russian.
- **Recovery:** reverting the focused source change disables the added household layer. Existing home, local trust and body markers retain their prior formats; the extra household SavedData is ignored by older builds. Old binaries may use their former admission reward values, so rolling back is not a balance-preserving migration. No unrelated subsystem or dependency was removed.

The checks do not establish prolonged live multiplayer stability or compatibility with an item handler that violates the Forge simulation contract.

## Repairing facilities and independent relationships

Once a community reserve has been registered, its original two positions remain in saved household data even while both chests are absent. Placing ordinary chests back at those exact positions restores their public designation; destroyed items are not recreated. Unregistered adjacent chests do not become shared. Placement and break events invalidate the loaded site cache.

Residents consider all suitable beds and bird perches inside the structure bounds, including player-placed replacements. Anatomy, schedule, occupancy and reservations still apply. The hideout’s six three-carpet sleeping rolls are now real orange beds, with heads on the former white carpets and feet on the neighboring orange carpets. Existing generated structures are not overwritten.

Ordinary residents retain their authored home when bonded. The roster can display Camp and Bonded together, and their social/bond chat routes into camp dialogue. Actively following the owner takes priority; at home in wander mode they may resume camp routines. Their original home dimension remains stable when transported, and away-from-home camp dialogue performs no remote supply actions. Fixed Chiefs retain their existing no-bond, no-follow and no-body-takeover restrictions.

A member in good standing at local trust 40 may receive one ordinary household outfit from that Chief. The claim is saved per player and home, survives leaving/rejoining and does not consume bound NPC equipment. Full player inventory leaves the entitlement available for retry.

## Personal storage and public reading

The author-identified personal containers are explicitly marked: seven container blocks in the treehouse, one bedside chest in the hideout and seven box/chest blocks in the observatory storage alcove. A nearby awake resident with clear sight warns visitors who search these containers. Repeated witnessed searching costs local trust with the existing cooldown and does not start a storage-defence attack. Private supplies remain separate from the community reserve. Unmarked lecterns and authored readable story carriers no longer cause access warnings or trust penalties; their written contents are unchanged.

The community reserves now use the author's pictured positions and facing, rather than their former campfire-side placements. Changes to template blocks and private flags apply to newly generated or placed structures; existing saved buildings are not rewritten.

Natural-camp sleep no longer wakes or repositions an actor from the entity tracking-loss callback. Vanilla changes chunk visibility while iterating live entity sections; waking a boundary sleeper there could modify that list and crash the server. Stale saved sleep still clears on safe entity join. Fixed NPC tracking loss with no removal reason is treated as unload, without scheduling a replacement body.
