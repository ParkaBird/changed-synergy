# Player Camps: Setup, Work and Shared Meals

This guide covers player camps in **Changed: Synergy 1.1.4**. All camp commands begin with `/changedsynergy outpost`. Use `/changedsynergy outpost help` for clickable help, or `/changedsynergy outpost` to open camp management. Commands and the menu manage your own camp. New invitations require a trusted adult creature at **Close friendship (28 affection)**. Existing eligible members can remain in camp after a bond is ended. See [the social guide](SOCIAL_GUIDE.md) for friendship, gift cooldowns and bonds.

## Getting Started

1. Place a campfire, look at it and run `/changedsynergy outpost create`. Linked facilities stay within 32 blocks of the centre; marked work areas can extend up to 64 blocks from it. Camps established with bells in earlier versions remain valid.
2. Place a chest or another supported storage container, look at it and run `/changedsynergy outpost link`. Workers use the storage explicitly linked to your camp.
3. Look at each bed and run `/changedsynergy outpost link` to add it. Use the same command on pillows, office chairs and tall cardboard boxes to add separate rest points, or on a campfire or composter to bind that facility.
4. Bring an eligible adult creature at Close friendship into camp, look at them and run `/changedsynergy outpost invite`. You can also use `invite <entity selector>`.
5. Look at a recruited member and use `work gather|hunt|farm|cook|haul` to assign supply work. For example, `/changedsynergy outpost work farm` assigns farming, including composting when no crop task is available.
6. Sneak and right-click the camp centre, or run `/changedsynergy outpost`, to open management. Select a member to assign the Supply role, main work and backup work, and view work status and linked facilities.

The campfire used to establish a new camp also serves as its cooking campfire. Use `link campfire` if you want cooking and meals to take place at another campfire within camp range. Link commands bind the block you are looking at; they do not place blocks for you.

The relationship wheel provides the same shortcuts. If you have no camp, look at an unbound campfire: **Manage Outpost** becomes **Establish Camp**, with a small grayscale plus on its campfire icon. Once you have a camp, looking at an unlinked facility inside it changes the option to **Link Facility**, with a grayscale plus icon. Looking elsewhere opens camp management. Beds and rest points are added to their respective lists; linking another storage container replaces the camp's current storage.

When you aim at an eligible adult creature inside your camp, the relationship wheel's management option changes to **Invite to Camp**. Close friendship is required. Creatures already assigned to a camp, player-controlled bodies and creatures with another owner cannot be recruited. The invitation must meet the same conditions as the command.

Wild creature outposts are separate from player camps. A server can disable new wild construction with `COMMUNITIES.CreatureOutposts` while leaving player camps and existing wild storage intact. Opening a container at any wild creature outpost awards **Mysterious Foraging Spot**; merely attempting to open a guarded container does not count.

## Command Reference

Add `/changedsynergy outpost` before each command below. Replace text in `<angle brackets>` with an actual value. Square brackets indicate an optional argument; do not type the brackets themselves.

| Command | What it does |
| --- | --- |
| `help` | Show clickable command help. |
| `create` | Establish a camp at the campfire you are looking at. |
| `status` | List linked facilities, work areas, members and their current work status in chat. |
| `link` | Automatically identify and bind the supported functional block you are looking at. No further argument is needed. |
| `link storage` | Bind the storage container you are looking at. |
| `link bed` | Add a bed. Both halves refer to the same bed; existing beds are retained. |
| `link rest` | Add a pillow, office chair or tall cardboard box as a rest point. |
| `link campfire` | Bind the campfire used for cooking and shared meals. |
| `link composter` | Bind a composter for farmers to use between crop tasks. |
| `invite [<entity selector>]` | Recruit the eligible creature you are looking at, or one selected by the argument. |
| `role <member UUID> <role>` | Assign a member's camp role or individual orders. See the role table below. |
| `work [<member UUID>] <work>` | Assign main supply work. Without a UUID, look at the member. |
| `crew [<member UUID>] combat\|mine\|forage\|hunt` | Assign a travelling crew job. Without a UUID, look at the member. |
| `backup <member UUID> <work or none>` | Set backup work for a supplier, or clear it with `none`. |
| `farm corner1`, `farm corner2`, `farm clear` | Mark or clear the farming area. |
| `hunt corner1`, `hunt corner2`, `hunt clear` | Mark or clear the hunting area. |
| `gather corner1`, `gather corner2`, `gather clear` | Limit species gathering to an optional marked area, or restore normal gathering. |
| `haul corner1`, `haul corner2`, `haul clear` | Limit loose-item pickup to a marked area, or restore the default 32-block camp radius. |
| `team follow`, `team hold`, `team return`, `team retreat` | Give camp members a group order. |
| `meal` | Call members to gather for a shared meal. |
| `policy <setting> <value>` | Change `meals`, `raw_reserve` or `seed_reserve`. |
| `remove <member UUID>` | Remove a member from camp, including an unloaded member. |
| `disband` | Disband your camp and clear its members' camp assignments. |

In 1.1.4, the duplicate `claim`, `storage`, `bed` and `members` commands have been removed. Use `create`, `link storage`, `link bed` and `status` instead. There is also no separate `compost` command or work assignment: use `work farm` and link a composter.

## Members and Team Orders

Newly recruited members start as residents. Change their role in camp management or use `/changedsynergy outpost role <member UUID> <role>`. Run `status` and click a member's chat entry to insert a role command containing their UUID.

| Role | Behaviour |
| --- | --- |
| `resident` | Stay around camp without an assigned supply job. |
| `guard` | Stay near the camp centre as a guard. |
| `supply` | Perform assigned main work and, when needed, backup work. |
| `crew` | Follow the owner as part of the travelling crew, with a saved combat, mining, gathering or hunting job. |
| `hold` | Wait at the member's current position. The individual command requires that member to be loaded. |
| `return` | Head back to camp. |
| `retreat` | Withdraw towards camp. |

For example, `/changedsynergy outpost work farm` gives the member you are looking at the Supply role and farming work. You do not need to assign `role supply` first.

Guards and crew assigned **Combat** can join the owner's combat, respecting their existing attack settings. Other camp members may retaliate against a creature that recently attacked them; gathering crew pause their task when the owner is hurt and resume following. This camp rule also applies to bonded companions' attack settings. Hunting food animals is a separate work assignment.

New configurations allow **16 camp members** and **six following crew members**. Server owners can change `COMMUNITIES.PlayerOutpostResidents` from 1 to 64 and `COMMUNITIES.PlayerOutpostCrew` from 1 to 32. Existing config files retain their saved values; use the server config command to raise an older six-member camp limit. Lowering a limit prevents new invitations or crew assignments without removing existing members.

### Travelling Crew Jobs

Look at a recruited member and run `/changedsynergy outpost crew combat|mine|forage|hunt`. For example, `crew mine` assigns travelling mining and the Crew role in one step. Alternatively use `crew <member UUID> <job>` for an unloaded member. In camp management, select **Crew Follow**, then click **Crew job** to cycle jobs. The roster shows the current job. `team follow` retains each member's chosen travelling job, which also survives restarts.

| Job | Behaviour |
| --- | --- |
| `combat` | Stay close and assist combat under the existing companion attack rules. This is the default for old saves. |
| `mine` | Find exposed, reachable ore nearby and work through its local vein before continuing to follow. Supplied pickaxes apply their material, Efficiency, Fortune and Silk Touch and lose durability normally. |
| `forage` (Gathering) | Collect fruiting orange leaves, ripe sweet berries and glow berries, including reachable fruit above the member's head, preserving regrowth. This does not replace the camp farmer's crop-harvesting job. |
| `hunt` | Hunt eligible adult food animals, collect that kill's real drops and continue following. A solitary wild adult is eligible for this player-assigned job. Babies, named, leashed, breeding and owned animals are protected, as are marked farmland and the camp centre. Each hunter waits 90 seconds after a successful hunt. **Keep livestock away from a travelling hunter's route.** |

Gathering crew normally start following beyond eight blocks and settle within six, giving them room to work. Miners search eight blocks horizontally; fruit gatherers search ten. They can find resources up to eight blocks above or below nearby ground. Hunters look up to twelve blocks horizontally and eight vertically. New work must be within 16 blocks of the owner's visible body. Miners can work exposed veins in the ground as well as walls, provided there is a reachable place to stand and a clear view of the ore. After each successful harvest, they check the nearby vein or fruit patch and continue without waiting for another full discovery interval. Miners include ore just exposed by the last break. New targets still need to be near the owner; cargo space, delivery, safety checks and the task time limit can end the batch. They finish the selected block or prey while the owner remains within 32 blocks, then resume following. They do not dig tunnels, load chunks or stay behind indefinitely. Work and pursuit use normal movement speeds. New orders, personal combat, low health, an injured owner, shared meals and loss of the owner's valid physical body interrupt work. In TrueTransfur Instinct mode, crew members follow your visible body.

Fruit gatherers prefer exposed fruit they can already reach, then look for nearby ground beside or below the canopy. An obstructed nearest block does not hide every other resource. Unreachable targets are skipped briefly, and a route that stops making progress yields back to following. Hunters approach living prey at Changed's normal melee pursuit pace, strike at most once every 0.8 seconds and follow the actual drops after the kill, including drops on a slope. Failed routes are skipped briefly. Wild outposts and stationary camp hunters retain their existing population protection; travelling hunters use the explicitly assigned hunting job and can take a solitary eligible adult.

The roster distinguishes **Following the owner**, **Travelling to work**, **Harvesting** and **Waiting for work**. A settled crew member is waiting for another local task, rather than claiming to be travelling to a workplace. The 90-second hunting cooldown appears as **Waiting for the next hunt**; **No eligible wild prey** means no nearby animal meets the protection and distance rules. A failed movement route displays **Workplace cannot be reached**.

Combat assistance follows the owner's visible body in TrueTransfur Instinct mode. Allied players' companions and their Instinct bodies retain the same player alliances. Switching a member from Combat to a gathering job stops proactive assistance; the member can still defend itself, then resume work.

Collected resources remain in the creature's **Work Cargo** row, where you can retrieve them. Travelling jobs use all six work-bag slots and continue filling matching partial stacks even when every slot is occupied. **No cargo space for this harvest** means there is no room for that product; a partial stack of a different item does not count. Full bags pause gathering while the member keeps following. Any surplus drops remain real items rather than overwriting cargo. Cargo is retained across job changes, recall and rejoining; members unload into linked storage when back within eight blocks of it. Full or unreachable storage retains the items. Removing a member or their death drops carried cargo through the existing delivery rules.

`COMMUNITIES.PlayerOutpostCrewGathering` controls travelling mining, gathering and hunting independently of stationary **Supply Work**. The existing **Hunting** switch also controls travelling hunting. All gathering respects `mobGriefing`. After a successful harvest, members look for more nearby resources before resuming following. The work search interval controls how often idle members discover new tasks. `COMMUNITIES.PlayerOutpostActiveWorkers` limits stationary suppliers and travelling gatherers separately: a busy home camp cannot occupy the crew's work slots. The default permits up to four active suppliers and four active travelling gatherers per camp. Blocked ores are **block IDs**, not item IDs:

```mcfunction
/changedsynergy config set COMMUNITIES.PlayerOutpostResidents 16
/changedsynergy config set COMMUNITIES.PlayerOutpostCrew 6
/changedsynergy config set COMMUNITIES.PlayerOutpostCrewBlockedOres minecraft:diamond_ore,minecraft:deepslate_diamond_ore
```

The ore list starts empty. Include regular and deepslate versions separately; `[]` clears the list. Changes immediately stop an in-progress blocked mining task without consuming the block or tool. This list applies to travelling miners and leaves stationary species-based camp gathering unchanged.

Group orders apply to camp members, including workers. `team follow` assigns the Crew role, subject to the crew limit; `team hold`, `team return` and `team retreat` replace their current assignments with the corresponding order. After giving a group order, reassign `work` or `role` when you want members to resume their previous duties.

Commands can record new roles or work for unloaded members, but they do not load chunks or move unloaded creatures. Removing a member or disbanding a camp clears camp membership without ending an existing friendship, bond or pet ownership.

Dead members are removed from the camp roster automatically. If you have management open, its list updates to remove them; a revived creature can be invited again when eligible. Only the camp owner can manage its members.

Hover over a member's name to see their work status, dimension and coordinates. Loaded members' positions refresh from the server about once per second while management stays open, including across dimensions. Unloaded members show their last recorded position instead of an assumed live location.

With TrueTransfur's physical Instinct mode, the crew follows the owner's visible creature body. Ownership and orders still belong to the real player account. Entering or leaving Instinct updates the movement target; an unavailable body or offline owner pauses following. Without a separate Instinct body, the crew follows the player normally.

Movement orders interrupt idle sitting and sleep. Wider bodies can move away from open door and trapdoor edges when the passage fits them. Doors remain open while they cross; trapdoors are left in their existing state. Narrow passages still need enough space for the selected species.

Members belonging to scoreboard-allied owners, or to players on a server with PvP disabled, do not attack each other because of opposing factions. A guard's proactive combat respects the owner's hostility and target settings; unprovoked neutral monsters are not automatic targets.

## Farmland and Hunting Areas

Prepare the farmland, water and accessible paths first. Look at a ground block at one corner of the area and run `/changedsynergy outpost farm corner1`. Look at the opposite corner and run `farm corner2`. Both corners must be set before farming begins. Both corners and the work itself must stay within 64 blocks of the camp centre. Each area can cover up to 32 × 32 blocks, with a maximum height difference of four blocks between its corners. Use `farm clear` to remove the area assignment without destroying the farm. The same marking limits apply to hunting, gathering and hauling.

Farmers harvest mature crops only. Wheat, beetroot, carrots, potatoes and similar crops are replanted with the same crop using actual planting materials from the harvest or linked storage. Remaining produce goes to storage. Workers leave immature crops alone and pause if replanting materials are unavailable. They do not till soil, expand the farm or load new chunks.

With **Farmer's Delight 1.20.1-1.3.4** installed, cabbage and onions are replanted with the same crop. Tomatoes use the mod's fruit-picking behaviour, preserving their vines and ropes. Rice harvesting removes the mature upper section while preserving the submerged root and water source, allowing it to regrow naturally. Tomato picking and rice harvesting leave the plant in place and do not consume extra seeds. Farmer's Delight is optional.

Set a hunting area with `hunt corner1` and `hunt corner2`, or remove it with `hunt clear`. **Include only areas where you allow hunting; keep livestock pens outside the hunting area.** Hunters exclude babies, named, tamed, leashed and breeding animals, and avoid farmland and the camp centre. Unnamed adult livestock cannot always be distinguished from wild animals. Hunters preserve a nearby animal population and collect real kill drops rather than infecting prey for resources.

## Work Assignments

| Command name | Work |
| --- | --- |
| `gather` | Gather oranges, berries or minerals, fish, or collect honey according to the species' existing preferences. Deliver each batch before gathering again. |
| `hunt` | Hunt in the designated area, collect drops and return them to camp for cooking or storage. |
| `farm` | Harvest and replant designated farmland, use available bone meal, and compost surplus seeds when no crop task is available. |
| `cook` | Walk to storage, fetch ingredients and cook up to four items at a time on the linked campfire. Collect finished food and return it to storage. |
| `haul` | Collect loose items in its pickup area, including equipment and player-thrown items, then deliver batches to storage. |

Your main-work assignment takes priority over the species' default gathering preference. Workers try their backup work when no main-work task is available. If storage is full, workers keep their cargo and pause delivery. Carried resources drop when a worker dies or leaves the assignment; revival does not restore resources already dropped at death.

Mineral gatherers and shore anglers prefer suitable pickaxes and fishing rods in their personal inventory. Supplied tools apply their material and enchantments and lose durability normally. Miners prefer the fastest suitable pickaxe, including Efficiency; Fortune and Silk Touch affect drops. Anglers prefer Lure, then Luck of the Sea. Ordinary gatherers use their default iron pickaxe or rod only when no suitable stored tool is available. You can retrieve supplied tools from the personal inventory; gathered produce remains in Work Cargo for delivery. Open-ocean fish catching retains its direct-catching method.

Gatherers and hunters collect their own produce and return it to storage before starting another work cycle. Farmers collect up to four mature plants per delivery, returning sooner when no further harvest is available or their work bag is nearly full. They unload before resting, composting or starting backup work. Hauling is the fallback for uncollected drops, including cooked food and loot left by guards. Haulers collect up to four drops per delivery, returning sooner when no further eligible drop is available or their work bag is nearly full. They unload before resting or changing work. All workers use the camp's one linked storage container, merge matching stacks and then fill empty slots; they do not sort items into separate chests by category.

Collected resources and food carried for cooking appear in the **Work Cargo** row in the creature's inventory. These are the real items awaiting use or delivery. You can take them out normally or with Shift-click, including when storage is full or the worker cannot reach it. The worker then uses or delivers only what remains. Work cargo is separate from personal belongings and equipment; unloading does not remove those. Existing saved work cargo also appears in this row after updating.

Set `gather corner1` and `gather corner2` while looking at two ground blocks to restrict species-based gathering to that area. Both corners can be up to 64 blocks from the camp centre, with the same 32 × 32 area limit as farmland. This covers oranges, berries, exposed ore, flowers and fish; fishing targets must be inside the area, while the worker may stand on the adjacent shore within the work range. Clear it with `gather clear` to restore normal gathering within 32 blocks. The area persists across restarts, and gatherers wait near it when idle. Deliveries, meals and recall orders can still take workers outside it.

Use `haul corner1` and `haul corner2` to give haulers a separate pickup area with the same marking limits. Without both corners, a newly unmarked camp covers a 32-block radius; setting only one corner pauses pickup until the second is set. `haul clear` restores the default radius. Haulers accept any item type, including tools, armour and items thrown by players, retaining item data. They leave fresh drops alone for five seconds so their producers can collect them first, and respect native pickup delays. Cargo is delivered in batches of four collections, or sooner when no eligible drops remain, a job changes or work is interrupted. Storage being full preserves the remaining cargo.

The server's `COMMUNITIES.PlayerOutpostHaulingBlockedItems` list excludes specific item IDs from hauling. It starts empty. For example, an operator can prevent collection of diamonds and nether stars with:

```mcfunction
/changedsynergy config set COMMUNITIES.PlayerOutpostHaulingBlockedItems minecraft:diamond,minecraft:nether_star
```

Use `[]` as the value to clear the list. Entries require the namespace, such as `minecraft:` or a mod's ID. Changes apply to pickups in progress as well as new searches. Farmers, hunters and cooks still return the products of their own tasks; the hauling filter does not break their delivery chains. Work uses loaded chunks and does not keep an area loaded merely because it was marked.

During production and delivery, workers pause spontaneous patting and playful transfur attempts. These interactions resume when they are idle; deliberate player interactions and emergency responses retain their existing rules. When no task is available, workers wait near their work area instead of repeatedly returning to the camp centre. They remain responsive to new work assignments and team orders. When a worker accesses a vanilla chest, its lid opens and closes with the usual sounds, as it does for a player.

To manage an unloaded member, use `/changedsynergy outpost work <member UUID> <work>`. Use `status` to list members. Clicking a member's chat entry fills in a management command, and the UUID argument for `work` supports completion. Set backup work with `/changedsynergy outpost backup <member UUID> <work>`, or use `none` to clear it. Assign the Supply role before setting backup work.

### Composting as Part of Farming

Assign farming after linking storage and marking a farm area. You can then link an optional composter to let the farmer process seeds above the reserve when no crop task is available:

```text
/changedsynergy outpost link composter
/changedsynergy outpost work farm
```

Run the first command while looking at the composter, then run the second while looking at a recruited member. Composting is part of farming, with no separate work command.

Workers use real surplus seeds from linked storage and leave the configured seed reserve intact. Their personal inventory can be empty while storage supplies the material. Change the reserve with `/changedsynergy outpost policy seed_reserve <count>`, using a value from 1 to 64. Native composting chances still apply: an input may be consumed without raising the fill level. Workers stop when no surplus remains and wait while a full composter ripens. A composter produces bone meal through the vanilla process; it does not generate seeds or skip composting stages. The farmer collects that output into Work Cargo and delivers it to storage before starting another task. If carrying space or storage is full, the real item remains available for recovery rather than being silently lost.

Once delivered, bone meal is also available for automatic fertilization. A farmer may take it back out of storage on the next task to grow immature crops. Check for **Fertilizing crops** in camp management when the stored amount decreases. If no eligible crop needs fertilizer, the bone meal remains in storage. A full container keeps undelivered bone meal in **Work Cargo**; the farmer does not use that cargo directly on crops before delivery.

## Cooking and Shared Meals

Assign `work cook` to an eligible member. Cooks walk to storage to fetch actual ingredients, then fill up to four slots on the linked campfire. They support native campfire recipes and smelting recipes whose output is food, including raw fish, potatoes and kelp. Native recipes retain their cooking times; food-only smelting recipes take three times their furnace cooking time. This does not allow cooks to smelt ores.

Finished food appears as real item drops. The cook collects it for storage; haulers can collect food left behind. Cooking pauses when the campfire is unlit or full, while an unlit campfire still serves as a valid camp centre.

Fish cooking is part of the Cook job, with no separate switch. New camps reserve no raw food by default. Existing camps retain their saved reserve; set `raw_reserve 0` if you want all eligible stock available for cooking. Shared meals can be toggled in the management menu. For example:

```text
/changedsynergy outpost policy meals true
/changedsynergy outpost policy raw_reserve 0
/changedsynergy outpost policy seed_reserve 16
```

Raw-food reserves apply to cooking. Seed reserves also protect carrots and potatoes from communal meal withdrawals, keeping stock available for replanting. Reserves apply to each relevant item type and do not create resources.

Residents, guards and suppliers within camp can gather for a meal each evening. You can also click **Call Meal** or run `/changedsynergy outpost meal`. Members currently following, holding position or retreating do not join. The linked campfire must be lit and not waterlogged. Members spread out around it, preferring nearby linked pillows, office chairs and tall cardboard boxes; otherwise they sit on safe ground. Seating allows room for larger bodies. Creatures in combat, grabbing, interacting or carrying cargo wait until they are available.

Members first choose suitable food from their own inventory. If none is available, they walk to linked storage to fetch one serving. They hold the food and play its normal use animation before consuming it, at most once per Minecraft day. Empty bowls, bottles and similar containers go into the member's inventory, or drop if it is full. Calling another meal that day lets them gather again without consuming another serving. A lack of food does not reduce affection.

After eating, members rest for about seven to twelve seconds before returning to their duties. Combat, movement orders or disabling meals interrupts the gathering, releases its seats and returns unconsumed food without losing the member's equipment. Members accidentally touching a lit campfire try to step away at an ordinary walking pace.

## Advancements

The camp branch includes **A Place to Call Home** for establishing a camp, **Many Paws Make Light Work** for a worker's first delivery to linked storage, and **Pull Up a Seat** for a member finishing a campfire meal. Deliveries and meals award progress to the camp owner while they are online.

With TrueTransfur installed, the tree also includes **One of Us** for starting as a native latex creature and **Together, as We Are** for establishing a native-player bond. These two advancements are absent when TrueTransfur is not installed. Starting as a human does not award a native-origin advancement.

## Server Settings

Gathering, hunting, farming, cooking, hauling, composting and shared meals have separate server switches. Settings also control the work search interval, the number of active workers and whether work continues while the owner is offline. The camp master switch controls the whole system; the supply switch controls production. Composting also requires farming to be enabled. Shared meals have their own switch and do not require supply work to be enabled. Disabling hauling still lets farmers, hunters and cooks collect their own produce. Disabling an individual job allows already-carried cargo to be unloaded before the worker becomes idle.

Camps require both `COMMUNITIES.PlayerOutposts` and the world rule `changedSynergyCreatureLife`. Friend members require `changedSynergyFriendshipSystem`; bonded members require `changedSynergyBondSystem`. Disabling one of these rules pauses the relevant assignments and preserves membership, facility links and saved work choices. The NPC combat-AI rule does not independently disable camp production.

Work respects `mobGriefing` and supported block-modification cancellation events. Compatibility with every third-party claim plugin is not guaranteed.

Production and shared meals pause while the owner is offline by default. Enabling offline work still limits it to loaded chunks: workers do not force chunks to load or simulate work during unloaded time. The default search interval is 100 ticks (five seconds at 20 TPS), with at most four active stationary suppliers and four active travelling gatherers per camp. Searches are staggered between workers. Increasing `COMMUNITIES.PlayerOutpostWorkScanInterval` or reducing `COMMUNITIES.PlayerOutpostActiveWorkers` reduces background AI and pathfinding load, while delaying task discovery or reducing simultaneous work in each group.

Server operators can use `/changedsynergy config list COMMUNITIES`, followed by the `get`, `set` and `reset` subcommands, to manage camp settings. Configuration keys support command completion. Client settings cannot override the server's work switches. See [Server, world and client settings](SERVER_CONFIGURATION.md) for the configuration file, command syntax and synchronized settings page.

`COMMUNITIES.PlayerWildOutpostMinimumSpacing` keeps newly established wild outposts away from player camps. The default is 48 blocks horizontally, measured from the camp marker to the wild storage point; operators can choose 16–256 blocks. It does not relocate existing outposts. `COMMUNITIES.MinimumOutpostSpacing` separately controls spacing between wild outposts.

Use the same Synergy build on the server and all clients.

## Beds and Rest Points

Link each bed individually. Residents, guards and suppliers choose an empty, reachable bed at night and reserve it while walking towards it. Another resident can use a different linked bed. Repeatedly linking a bed, including its other half, does not register it twice. Destroyed beds are removed once their blocks are loaded; an unloaded chunk does not erase its registrations.

While assigned to camp, members use the camp's beds and work orders rather than their original pet sleep, fishing or caving routines. Leaving camp or disabling **Player Outposts** restores those original routines. A nearby sleeping owner does not make members abandon their work or compete for the owner's bed.

With Changed Extras 1.1.8b, camp members are excluded from automatic bedside cuddling so their work and linked beds stay in use. Ordinary idle pets outside camps retain that mod's normal behavior.

Members lying asleep in bed do not pat players, give return pats or display social dialogue and reactions. These interactions resume after waking.

Pillows, office chairs and tall cardboard boxes count as rest points. During daytime idle periods, members may use these points until new work or orders arrive. Suppliers choose points close to their assigned work area. Work, shared meals, combat and movement orders can interrupt this rest. Latex bees can also sleep on a linked yellow hive pillow at night. Previously linked pillows remain available as rest points.

**Player Outpost Rest** controls nighttime sleeping. **Camp Idle Rest** controls daytime use of rest points; both settings are enabled by default and can be changed on the server. Leaving a world, removing membership or disabling camps releases camp-owned seats and bed reservations.
