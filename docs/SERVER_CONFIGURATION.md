# Server, world and client settings

Gameplay, creature AI, communities, takeover, trading and performance options
are controlled by the **server**. Edit `config/changed_synergy-common.toml` in
the server's game directory, then restart the server for file edits to load.
For new installations, launch the server once to generate the file. The
separate `config/changed_synergy-client.toml` controls only each player's
visual and UI preferences.

Operators (permission level 2) and the server console can also change the
existing gameplay options without a restart:

```text
/changedsynergy config list
/changedsynergy config list 2
/changedsynergy config categories
/changedsynergy config list COMMUNITIES 1
/changedsynergy config get TAKEOVER.Enabled
/changedsynergy config set TAKEOVER.Enabled false
/changedsynergy config reset TAKEOVER.Enabled
/changedsynergy config set RELATIONSHIPS.IndependentFactionReputation true
/changedsynergy config set COMMUNITIES.PlayerWildOutpostMinimumSpacing 64
```

Tab completion lists the actual TOML keys, such as
`PERFORMANCE.AdaptiveAiBudget` and `RELATIONSHIPS.AllowMultipleBonds`.
`get` shows the current value, default, allowed range and description. Keys and
categories are case-insensitive. `set` checks that the value is allowed and saves it.
Use `true`/`false` for switches and numbers for numeric settings.

The configuration screen has three separate tabs:

- **Server Settings**: the server's gameplay values, grouped by purpose.
  In multiplayer these are read-only; operators use the command or server TOML.
  Single-player and the main menu edit the local settings file.
- **World Rules**: the current world's 14 Synergy gamerules. Players without
  operator permission can inspect them. Operators can edit them and press
  **Done**, which submits ordinary `/gamerule` commands with the server's usual
  permission checks. Main-menu world rules are read-only defaults.
- **Client Visuals**: each player's local UI and presentation preferences.

The Exoskeleton Hypnosis Visual setting is a
client preference there; any older value under `[TAKEOVER]` in the common file
no longer controls it, so players who disabled it should turn it off in their
own client settings.

World-wide enable/disable switches remain available through `/gamerule`; see
[GAMERULES.md](GAMERULES.md). Both server files and game rules should be
backed up with the rest of the server configuration.

The **Player roleplay** category adds four server-owned settings:

| TOML key | Default | Purpose |
| --- | --- | --- |
| `PLAYER_ROLEPLAY.SpeechEnabled` | `true` | Nearby role speech for native players; global chat remains available. |
| `PLAYER_ROLEPLAY.SpeechCooldownSeconds` | `2.0` | Independent player sending interval, from 0.5 to 10 seconds. |
| `PLAYER_ROLEPLAY.InstinctConfinement` | `true` | Allow native Instinct players to temporarily take over eligible humans after completing transfur progress, subject to PvP and physical reach. |
| `PLAYER_ROLEPLAY.InstinctConfinementSeconds` | `30` | Initial countdown, from 5 to 120 seconds; failed negotiation adds ten seconds up to a 1.5× cap. |

**Behaviour** also includes `BEHAVIOUR.AllowHumanOriginInstinct` (default `false`).
When origin selection is enabled, it lets ordinarily transformed human-origin
players use TrueTransfur 2.0's Instinct control without its permanent-identity,
lethal-reversal or native-rebirth features. Turning it off restores PLAYER control
while retaining the Changed form. Disabling origin selection instead restores
TrueTransfur's ordinary eligibility rules.

Names and role speech apply to enabled native origins. Player familiarity reuses
friendship, relationship-balance, care-allowance and gift-cooldown settings;
expressions reuse the emote world rule. Player takeover also respects the takeover
and Changed grab switches, with negotiation and escape controls.
Turning player takeover off releases active victims. These settings do not grant
NPCs new authority to restrain native players.

See the [settings guide](SETTINGS_GUIDE.md) for the full catalog, defaults
and settings that apply only to new encounters.

List values can use a JSON array:

```text
/changedsynergy config set BEHAVIOUR.DisabledOriginForms ["changed:form_pure_white_latex_wolf"]
/changedsynergy config set BEHAVIOUR.DisabledOriginForms []
/changedsynergy config set RELATIONSHIPS.BondTokens dark=changed:dark_latex_crystal_fragment,4;white=minecraft:quartz,1
```

Origin exclusions use **transfur form IDs**, not entity IDs. Bond token groups
are `aquatic`, `organic`, `dark`, `white` and `variant`, with at most one entry
per group and counts from 1 to 64. Missing groups and unavailable optional-mod
items use the built-in defaults; invalid syntax is rejected without changing
the stored value. Empty lists clear overrides. `servercfg` is not a second
configuration system; the supported command is `/changedsynergy config`.

## How switches combine

World rules determine which systems are allowed in that world. Server settings
enable or tune features within those systems. Client and personal settings
cannot re-enable a feature prohibited by the server or world.

- Hostility pathfinding requires both `changedSynergyNpcAI` and
  `PERFORMANCE.EnhancedHuntAi`. Turning these off does not disable creature
  life, camp production or relationship safety.
- Camps require `COMMUNITIES.PlayerOutposts` and
  `changedSynergyCreatureLife`. Bonded or friend members also need their
  corresponding relationship rule. Pausing these rules preserves membership,
  links and assignments instead of removing members.
- Camp production requires `COMMUNITIES.PlayerOutpostSupplyWork`. Gathering,
  hunting, farming, cooking and hauling have their own switches. Composting
  additionally requires farming; it remains part of the farming job.
  Turning hauling off does not prevent workers collecting their own output.
  Existing cargo can still be returned when an individual job is disabled.
  `COMMUNITIES.PlayerOutpostHaulingBlockedItems` lists namespaced item IDs
  haulers must not collect. It does not block other workers' own output or
  already collected deliveries. Hauling uses its own optional marked area;
  without one, its radius is 32 blocks. Marked work areas can extend 64 blocks
  from the centre, while linked facilities remain within the original range.
- `mobGriefing` controls camp block harvesting, planting, cooking placement
  and composting; it does not erase work assignments. Offline work operates
  only in already-loaded chunks.
- Companion fishing, mining and lighting use `PERFORMANCE.CompanionWorkAi`,
  `changedSynergyCreatureLife` and `changedSynergyBondSystem`, including the
  native Changed and Addon work goals. This does not replace camp job controls.
- Territory display has world and client switches. Hidden HUD information
  does not suppress the territory data used by the relationship menu.
- Dialogue, understanding and display are separate: the dialogue gamerule
  controls speech; `DIALOGUE.TelepathyEnabled` controls understanding, while
  an enabled Addon translator can still interpret it. Client display settings
  select how permitted speech is presented.

Reputation initialization affects only new faction accounts. Origin inclusion
and exclusion settings affect choices still to be confirmed; saved identities
are retained. Disabling friendship/bonds pauses their effects without erasing
memories. Capture-kind switches, takeover duration and hostile outcomes are
evaluated when an encounter starts; changing them does not rewrite an existing
session. The takeover master switch does end active takeover sessions safely,
and disabled action controls are hidden in the takeover menu. Disabling token
requirements refunds unused reservations once when the owner is next available. Personal companion orders remain subordinate to camp duty and safety.

Operators can spawn a creature with a chosen dominant personality:

```text
/changedsynergy spawn changed:dark_latex_wolf_male protective
```

Use Tab to complete the creature ID and one of the nine personality names.
The command respects the personality-system gamerule and keeps the ordinary
secondary traits.

`RELATIONSHIPS.IndependentFactionReputation` is off by default. When enabled,
each faction's reputation changes on its own and rival alliances may coexist.
The setting does not refund reputation lost before it was enabled; turning it
off again reconciles any rival alliances that became incompatible.

## Wild creature outpost construction

`COMMUNITIES.CreatureOutposts` defaults to `true`. Set it to `false` to prevent new autonomous construction and new ruin/hive claims. Existing outposts, storage and deliveries stay available. It is independent of player camps and existing facility storage. To pause existing wild community routines during lag diagnosis, use `PERFORMANCE.CommunityAi`.

```mcfunction
/changedsynergy config set COMMUNITIES.CreatureOutposts false
```
