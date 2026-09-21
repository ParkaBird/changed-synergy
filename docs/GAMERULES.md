# Changed: Synergy game rules

Boolean rules use `/gamerule <name> true|false`. The initial-reputation rule accepts an integer.

| Rule | What it controls |
| --- | --- |
| `changedSynergyNpcAI` | Investigation, coordinated pursuit, water pursuit and other Synergy AI extensions. Relationship safety remains active when this is off. |
| `changedSynergyNpcDialogue` | Contextual creature dialogue. |
| `changedSynergyNpcEmotes` | Contextual emotion bubbles. |
| `changedSynergyFactionReputation` | Faction standing changes and their effects on creature behaviour. |
| `changedSynergyInitialFactionReputation` | Reputation assigned when a player first encounters an uninitialized faction account. Default `0`; values are clamped to `-100..100`. Existing records are never overwritten. |
| `changedSynergyBondSystem` | Bond creation, bonded following, rescue, wrapping and companion controls. Existing bonds are kept while the rule is off. |
| `changedSynergyFriendshipSystem` | Familiarity, friendship, friend following and combat assistance. Existing memories are kept while the rule is off. |
| `changedSynergyTerritoryDisplay` | Territory and facility presentation on the HUD. It does not alter world generation or spawning. |
| `changedSynergyPersonalitySystem` | Stable individual traits and their AI and dialogue effects. Stored traits are not deleted when disabled. |
| `changedSynergyCreatureLife` | Persistence for related creatures, role-driven activities, community memory, resource gathering, outposts and pure-white reformation. It also respects vanilla `mobGriefing` when placing outposts. |
| `changedSynergyHypnosisQte` | Synergy's gaze-based hypnosis resistance, input restrictions and mesmerized penalty. Changed's original hypnosis remains when disabled. |
| `changedSynergyGrabQteEnhancements` | Server-synchronized grab prompts and the post-escape safety stun. Changed's original grab remains when disabled. |

## Consolidated rules

`changedSynergyCreatureLife` replaces the old persistence, routine, community, resource, cache, role and white-reformation switches. The separate social-interaction switch was also removed: bonded interactions follow `changedSynergyBondSystem`, while friend interactions follow `changedSynergyFriendshipSystem`.

Old worlds cannot map several conflicting switches into one value reliably, so `changedSynergyCreatureLife` starts at its default of `true`. Servers that had any of those systems disabled should set the new rule explicitly after updating.

The former opt-in persistent-search rule was removed. Its default was already off, so ordinary pursuit timing is unchanged.
