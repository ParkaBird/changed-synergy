# Dedicated server configuration

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
/changedsynergy config get TAKEOVER.Enabled
/changedsynergy config set TAKEOVER.Enabled false
/changedsynergy config reset TAKEOVER.Enabled
/changedsynergy config set RELATIONSHIPS.IndependentFactionReputation true
```

Tab completion lists the actual TOML keys, such as
`PERFORMANCE.AdaptiveAiBudget` and `RELATIONSHIPS.AllowMultipleBonds`.
`get` shows the current value, default and allowed range. `set` validates the
value against Forge's config spec before applying it and saves the server TOML.
Use `true`/`false` for switches and a number for ranges. The internal
`BehaviourConfigRevision` migration marker cannot be changed by command.

In a multiplayer client, the in-game **Gameplay & AI** tab does not display
the client's unrelated local common settings or allow them to be saved. Use
the server command or TOML instead. The **Client & Visuals** tab remains
editable by every player. The Exoskeleton Hypnosis Visual setting is now a
client preference there; any older value under `[TAKEOVER]` in the common file
no longer controls it, so players who disabled it should turn it off in their
own client settings.

World-wide enable/disable switches remain available through `/gamerule`; see
[GAMERULES.md](GAMERULES.md). Both server files and game rules should be
backed up with the rest of the server configuration.

For testing creature behavior, operators can spawn a Changed creature with a
chosen dominant personality, for example:

```text
/changedsynergy spawn changed:dark_latex_wolf_male protective
```

Use Tab to complete the creature ID and one of the nine personality names.
The command respects the personality-system gamerule and keeps the ordinary
generated secondary traits.

`RELATIONSHIPS.IndependentFactionReputation` is off by default. When enabled,
each faction's reputation changes on its own and rival alliances may coexist.
The setting does not refund reputation lost before it was enabled; turning it
off again reconciles any rival alliances that became incompatible.
