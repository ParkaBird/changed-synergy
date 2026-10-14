# Performance diagnostics

Synergy's performance controls are under **Server Settings → Performance and
Diagnostics** in the Forge configuration screen. A dedicated server controls
these values; the multiplayer screen shows its values without editing them.
Operators can change them with `/changedsynergy config set <key> <value>`.
Single-player settings can be saved directly. See the
[settings guide](SETTINGS_GUIDE.md) for categories and dependencies.

## Live readings

The bottom of the configuration screen displays a one-second server
sample when `PERFORMANCE.Diagnostics` is enabled:

- **Client FPS** is the local render rate. Low FPS with a healthy server value
  points toward rendering, shaders, particles, or the client rather than AI.
- **Server ms/t** is the smoothed time spent processing a server tick. The
  server must stay below 50 ms/t to maintain 20 TPS.
- **Latex AI ms/t** shows the time spent on active latex creatures. Its
  percentage is relative to the full server tick.
- **Active latex** is the smoothed number of Changed creatures that received an
  AI tick, not every creature saved in unloaded chunks.
- **Optional work** reports admitted and deferred Synergy background decisions.
  Orange text means the server exceeded 50 ms/t or the soft latex AI budget was
  reached at least once during the latest sample second.

These readings answer whether latex AI scales with the slowdown and whether
Synergy is deferring background work. They are a compact indicator rather than
a method-level profiler. If server ms/t remains high while latex AI is low, use
Spark or another server profiler to inspect chunk loading, block entities, other
mods, and garbage collection.

## Controls

| Common config key | Default | Expected smoothness impact | Behaviour |
| --- | ---: | --- | --- |
| `PERFORMANCE.Diagnostics` | `true` | Low | Shows updated performance readings once per second. |
| `PERFORMANCE.StaggerBackgroundAi` | `true` | High | Gives each creature a different scan phase so a large group does not decide on the same tick. |
| `PERFORMANCE.BackgroundScanInterval` | `10` ticks | High | Base interval for expensive idle social, comfort, and community decisions. Higher values trade reaction speed for CPU time. |
| `PERFORMANCE.DistantAiThrottling` | `true` | High | Reduces optional decisions outside the active player range. |
| `PERFORMANCE.DistantAiRange` | `48` blocks | Medium | Defines where optional decisions retain their normal frequency. |
| `PERFORMANCE.DistantAiIntervalMultiplier` | `4` | High | Multiplies optional decision intervals when no player is inside the active range. |
| `PERFORMANCE.AdaptiveAiBudget` | `true` | High | Fairly defers noncritical decisions after the soft budget is reached. |
| `PERFORMANCE.LatexAiBudgetMs` | `8.0` ms/t | High | Sets the soft admission threshold. It cannot cancel Changed's mandatory base AI. |
| `PERFORMANCE.CommunityAi` | `true` | High | Enables Synergy community roles, routine destination searches, and comfort decisions. |
| `PERFORMANCE.CompanionWorkAi` | `true` | High | Enables bonded companion fishing, mining, cave lighting, and work observation. Following, combat, rescue, and direct commands remain available. |
| `PERFORMANCE.AmbientSocialAi` | `true` | Medium | Enables unfamiliar-player approaches, ambient encounters, and non-bonded friend defence searches. |
| `PERFORMANCE.EnhancedHuntAi` | `true` | Medium | Enables Synergy pursuit memory, searching, noise alerts, underwater pursuit, and firearm reactions. Changed's native targeting remains. |

The budget delays optional background activity when the server is busy. Combat
and companion safety remain responsive. It cannot guarantee a maximum cost for
all creature activity or other mods.

## Diagnosing a crowded area

1. Open the configuration screen and record client FPS, server ms/t, latex AI
   ms/t, active latex count, and deferred work while the slowdown is present.
2. If server ms/t is high and latex AI grows with the population, disable
   `CommunityAi`, observe a fresh sample, then restore it. Repeat with
   `CompanionWorkAi`, `AmbientSocialAi`, and `EnhancedHuntAi`. Change one
   category at a time so the result identifies a subsystem.
3. If one category matters, leave it enabled and first raise
   `BackgroundScanInterval` to 20. For loaded areas far from players, reduce
   `DistantAiRange` or increase `DistantAiIntervalMultiplier`.
4. If spikes remain, lower `LatexAiBudgetMs` gradually. Frequent deferrals mean
   the scheduler is protecting tick time; delayed ambient reactions are the
   expected tradeoff.
5. If all four category switches are disabled and latex AI is still the main
   cost, the remaining time belongs primarily to Changed's native entity AI or
   another mod. Capture a profiler report before adding a
   broad AI freeze, because skipping mandatory AI can break navigation, grabs,
   swimming, and transformation state.

## Camp and origin search limits

Wild outpost construction is controlled independently by
`COMMUNITIES.CreatureOutposts` (default `true`). Disable it with:

```mcfunction
/changedsynergy config set COMMUNITIES.CreatureOutposts false
```

This stops new wild construction and new ruin/hive claims. Existing outposts,
storage and deliveries remain available; player camps and existing facility
storage are unaffected. Use `PERFORMANCE.CommunityAi` to pause the broader
community routine simulation when diagnosing activity at existing outposts.

Farmers and haulers collect batches and unload before resting. If a camp is
crowded, check that beds, fields, storage and meal seats have usable approaches
before increasing its worker limit. Offline work uses already-loaded chunks;
it does not load terrain to keep an absent owner's camp running. Individual job
switches and vanilla `mobGriefing` still apply. See the
[camp guide](PLAYER_CAMP_GUIDE.md) for orders and facilities.

Choosing a native origin can search new terrain for a suitable birthplace. If
no suitable location is found, the player can approve a safe spawn fallback or
choose another species. See [native origins](NATIVE_ORIGINS.md).

These controls cannot guarantee a particular tick rate for every modpack.
When reporting a slowdown, include the creature count, its activity, server
settings and a profiler capture of the affected area. Compare the same loaded
area before and after changing one setting.
