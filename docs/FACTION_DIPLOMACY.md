# Faction reputation links

These links are enabled by default. Server operators can set
`RELATIONSHIPS.IndependentFactionReputation = true` in
`config/changed_synergy-common.toml` or use
`/changedsynergy config set RELATIONSHIPS.IndependentFactionReputation true`.
When enabled, reputation
gains never reduce another faction's score, and every faction can be allied
at once. Previously lost reputation is not refunded. Re-enabling the default
links reconciles any simultaneous rival alliances.

## Rules

Respect (40) means friendly access and existing protection benefits. Alliance (70) additionally grants existing faction combat support and allied privileges. Personal friendships and exclusive creature bonds remain separate.

These political rivalries are Synergy design choices, not claims about upstream lore or biological incompatibility:

| Rival factions | Design basis |
| --- | --- |
| Dark / White | Existing competing collective loyalties |
| Mountain / Cave | Surface routes versus underground resource claims |
| Aquatic / Desert | Competing claims on scarce water and shore routes |
| Forest / Badlands | Resource stewardship versus extraction |
| Taiga / Jungle | Rival regional patrol networks |
| Swamp / Savanna | Wetland and dryland territorial claims |
| Plains / Snowy | Rival claims over seasonal migration and supply routes |

Other pairs have no automatic penalty. These links do not spawn inter-community wars or forbid mixed-species communities.

- Ordinary friendship gains below 70 do not affect another group.
- A positive gain that reaches alliance chooses that faction over its rival. The rival drops out of alliance (at most 69).
- Further gains in the chosen alliance lower the rival only when it is above `69 - floor((score - 70) / 2)`: 70 gives 69, 80 gives 64, 100 gives 54. No linked reduction alone crosses below Respect.
- These are reactions to alliance gains, not permanent reputation caps. Peaceful interactions can rebuild the rival toward 69; reaching 70 switches sides and ends the old alliance. Players need not attack anyone to change allegiance.
- A lower rival score is never raised by this calculation. Attacking an enemy gives no reputation with their rivals. Linked losses do not cascade into other links.
- Repeated gifts at a capped score cannot repeatedly subtract reputation. Notice messages are throttled; alliance loss is always announced.
- With the default links enabled, at most one faction in each rival pair can be allied at a time. Friendship with every group is achievable, as are multiple non-conflicting alliances.

## Saves and achievements

Old simultaneous rival alliances keep the higher score; ties prefer the first column of each rule's stored pair (Dark, Mountain, Aquatic, Forest, Taiga, Swamp, Plains). The other score is reduced only to a friendly level. Existing shared-Light migration is materialized before normalization so discovering a region cannot revive an old conflicting alliance.

Previously earned advancements are never revoked. The legacy `all_factions_allied` ID now means **Friends in Every Corner / 四方皆友**: simultaneously keep White, Dark, Aquatic and all 14 Light regional groups at 40+. Its parent is White Respected, not Faction Ally, so a purely diplomatic player needs no alliance to follow this achievement path.

The 14 regions are cave, taiga, swamp, jungle, savanna, desert, badlands, snowy, mountain, beach, river, ocean, forest and plains. The fallback `general` identity is not an extra required region.

Individual faction alliance advancements remain historical milestones at 70. Their English/Chinese descriptions now explain the corresponding alliance conflicts.

## Verification

`tools/tests/FactionDiplomacyTest.java` checks all seven rivalries, switching, capped gains, negative standings, deterministic migration, friendship-only completion, every required region, and 20,000 randomized reputation changes. Compile it together with `FactionDiplomacy.java` using Java 17 and run its main method.

In-game checks still required: login with an old all-allied save; gifting across 69/70 and 99/100; changing sides without violence; simultaneous players with different affiliations; clone/reconnect persistence; stale social wheel displays after a reputation change; immediate loss of faction combat/access privileges after switching; and the revised advancement's unlock at the final region's 39-to-40 transition.
