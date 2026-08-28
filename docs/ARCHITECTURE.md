# Changed: Synergy architecture

## Dependency direction

The common module depends on Changed. Changed Addon Plus is optional and is reached through `compat.ChangedAddonCompat`; classes whose signatures contain Addon types live below `compat.addon` and are loaded only when that mod is present.

No external integration may become a required type in the main entry point, API, relationship model, menus, packets, or client state.

## Main areas

- `api`: stable integration surface.
- `ai`: relationship memory, bonds, following, emergency rescue, search and water pursuit.
- `event`: relationship reconciliation, social events, disguise reactions and visual-state synchronization.
- `dialogue`: contextual line and emote selection.
- `world.inventory`: companion inventory and radial-menu containers.
- `network`: packets owned by the `changed_synergy` channel.
- `compat`: optional-mod bridges.
- `mixin`: narrowly scoped patches to Changed behaviour.

## Relationship rules

`LatexSocialRelation` classifies a player as human, formerly bonded, formerly respected, same species, same category, friendly outsider, unrelated outsider, or rival. `LatexSocialMemory` adds per-creature facts such as bond ownership, provocation, warnings, truces and active wrapping. `NpcDispositionEvents` combines those facts into the final target decision.

Bonded creatures and native pets cannot target their host. Friendly transformed players remain neutral until repeated or severe attacks cross the provocation threshold. Organic creatures ignore unprovoked humans as targets and do not approach or assimilate them. If a human damages one and it survives, that creature records the provocation and may pursue the attacker. Its ordinary swipes deal at most one point of health damage while also advancing Changed's native transfur progress. At 30% health or below, swipe damage and swipe transfur are disabled without clearing the target, leaving the dedicated grapple as the only assimilation attack. Transfurred outsiders remain valid combat and grapple targets. Disguises can temporarily alter the perceived relation.

## Persistence

Creature social state is stored under the `ChangedSynergySocial` compound. Player bond indices and last-known locations use `ChangedSynergy`-prefixed persistent compounds. Death and unloaded-release tombstones are server `SavedData` entries in the overworld data storage.

## Encounter context

White and dark latex first-contact dialogue checks nearby blocks through Synergy-owned block tags. It also reads Changed's active facility piece and spawn-table data: dedicated white or dark zones count directly, while other zones count when their tagged faction spawns have enough weight and capacity to dominate the rival faction. This recognizes areas such as the facility's dark-latex-heavy office zone even when the room has few latex blocks. These checks happen only when an eligible encounter line is selected.

Ordinary factions share a sighting locally. White latex uses the same safety gate over a wider link: neutral, bonded, tamed, truced and warning-grace creatures are not recruited into a pursuit. Linked members without sight investigate alternating flank points; members with sight may acquire the target normally.

## Faction and biology

Political affiliation and biological construction are separate. The reputation factions are White, Dark, Aquatic and Light. Organic creatures do not have a shared faction merely because they are organic.

An organic type declares its political affiliation through the normal faction entity tags. The mapping is derived from that species' natural spawn region, never from the place where an individual happens to be standing. Dark dragons and both crystal-wolf types are explicitly Dark. Mirror white tigers inherit the Light affiliation of their native taiga population, while retaining their taiga community identity. The beach-native pooltoy wolf belongs politically to the Aquatic population. The other naturally spawning organic species from Changed are mapped the same way from their spawn tables. A form with no natural spawn-region record uses neutral Light as a documented fallback. Travel, teleporting and commands cannot change the result. Organic combat rules, visuals, radial style and dialogue voice continue to use the biological organic classification.

## Runtime switches

Major runtime systems are controlled by game rules. Closely related creature-life features share one rule so the game-rule screen does not expose implementation-level switches. Exact defaults, boundaries and data-retention behavior are documented in [GAMERULES.md](GAMERULES.md).

Disabling enhanced AI does not disable relationship safety. Disabling a relationship subsystem makes its records inactive without deleting them, so worlds can safely turn features back on later.
