# Changed: Synergy game rules

Boolean rules use `/gamerule <name> true|false`. The initial-reputation rule accepts an integer.
All 13 boolean rules default to `true`; initial reputation defaults to `0`.
The same 14 rules appear under **World Rules** in Synergy's configuration page.
Operators can apply edits with **Done**; other players can inspect server values.

| Rule | What it controls |
| --- | --- |
| `changedSynergyNativeOrigins` | Offers first-time human/native species selection. Native origins require TrueTransfur. When on, permanent-origin features apply only to native players; the separate Human-Origin Instinct Control setting can permit human control mode. When off, TrueTransfur's ordinary scope is restored. Saved origins are retained. |
| `changedSynergyNativePvPReputation` | Reputation effects for attacks on native faction players, with recent self-defense exceptions. Also requires faction reputation to be enabled. |
| `changedSynergyNpcAI` | Investigation, coordinated pursuit, water pursuit and other Synergy AI extensions. Relationship safety remains active when this is off. |
| `changedSynergyNpcDialogue` | Contextual creature dialogue. |
| `changedSynergyNpcEmotes` | Contextual creature emotions and native-player expression bubbles. |
| `changedSynergyFactionReputation` | Faction standing changes and their effects on creature behaviour. |
| `changedSynergyInitialFactionReputation` | Reputation assigned when a player first encounters an uninitialized faction account. Default `0`; values are clamped to `-100..100`. Existing records are never overwritten. |
| `changedSynergyBondSystem` | Bond creation, bonded following, rescue, wrapping and companion controls. Existing bonds are kept while the rule is off. |
| `changedSynergyFriendshipSystem` | NPC familiarity, friendship, following and assistance, plus confirmed player friendships and familiarity. Existing records are kept while the rule is off. |
| `changedSynergyTerritoryDisplay` | Territory and facility presentation on the HUD. It does not alter world generation or spawning. |
| `changedSynergyPersonalitySystem` | Stable individual traits and their AI and dialogue effects. Stored traits are not deleted when disabled. |
| `changedSynergyCreatureLife` | Persistence for related creatures, routines, wild communities, gathering, player camps and pure-white reformation. Turning it off pauses camps without deleting members. Individual work switches and relevant vanilla `mobGriefing` restrictions still apply. |
| `changedSynergyHypnosisQte` | Synergy's gaze-based hypnosis resistance, input restrictions and mesmerized penalty. Changed's original hypnosis remains when disabled. |
| `changedSynergyGrabQteEnhancements` | Server-synchronized grab prompts and the post-escape safety stun. Changed's original grab remains when disabled. |

## Scope and retained state

Gamerules are world policy, saved in world data, rather than per-client visual
preferences. Server feature switches and a permissive world rule must both
allow an activity. `changedSynergyNpcAI` controls hostility pathfinding, not
all NPC activity: creature routines and camp work use the separate Creature Life rule. Friendship/bond rules pause matching camp members without unregistering
them. The client territory switch changes presentation only.

The initial-reputation UI accepts `-100..100`. Vanilla `/gamerule` allows other
integers; these are clamped when a new reputation account is initialized.

## Updating an older world

`changedSynergyCreatureLife` replaces the old persistence, routine, community, resource, cache, role and white-reformation switches. The separate social-interaction switch was also removed: bonded interactions follow `changedSynergyBondSystem`, while friend interactions follow `changedSynergyFriendshipSystem`.

Old worlds cannot map several conflicting switches into one value reliably, so `changedSynergyCreatureLife` starts at its default of `true`. Servers that had any of those systems disabled should set the new rule explicitly after updating.
