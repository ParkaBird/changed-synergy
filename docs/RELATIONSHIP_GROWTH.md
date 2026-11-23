# Friends, gifts and bonds

Relationship growth is controlled by the server. These are the defaults with **Balanced Relationship Growth** enabled.

## Getting acquainted

Patting and feeding suitable food build initial familiarity, but an unnamed stranger cannot exceed **8 affection**. Once you reach that limit, give a suitable item through **Gift** in the creature's social wheel. This establishes your friendship and gives the creature a name, while retaining the affection you already earned.

A calm stranger can offer a basic wheel for talking, patting and gifts. If your first gift is premature, unsuitable or refused, you keep the item.

## Growing a friendship

| Interaction | Affection | Limits |
| --- | ---: | --- |
| Patting | +2 | Shared care cooldown and allowance |
| Suitable food, directly right-clicked | +2 | Feeding itself has no cooldown; healing and food effects still work without affection |
| Play and shared activities | +2 | Shared care cooldown and allowance |
| Ordinary accepted wheel gift | +2 | One item; independent gift cooldown |
| Preferred-food wheel gift | +4 | One item; independent gift cooldown |
| Golden-orange wheel gift | +6 | One item; independent gift cooldown; does not establish a bond |
| Encounters, conversation and return pats | 0 | History and reactions are still remembered |

Care interactions share a **60-second affection cooldown**. Once acquainted, they also share an **8-point allowance**, replenished by each accepted wheel gift. Unused allowance does not stack. Accepted gifts have their own **five-minute cooldown**; gifts and care do not reset each other's cooldowns. Faction standing can reduce positive gains, and total affection is capped at 60.

Familiar friendship begins at **12**; Close friendship begins at **28**. New camp invitations require a trusted adult creature at Close friendship. Existing eligible camp members are retained.

## Playful friends

Adult Playful friends below 28 affection may occasionally attempt a friendly transfur of a healthy human friend. Checks occur once a minute; the chance falls as affection approaches 28, and an actual attempt has a five-minute cooldown. Combat, danger, captures, recent release protection and existing creature forms prevent initiation.

A light, empty-handed warning interrupts that creature's attempt and settles them for five minutes, without betrayal or reputation loss. Self-defense applies to that specific creature and attempt; it does not authorize attacks on other friends. Native creatures and already-transformed players are not prank targets. Existing transfur-method and protective-suit rules apply.

**Playful** and **Polite** are mutually exclusive behavior traits. Other personality traits determine how the creature speaks.

## A bond token

A first bond needs **60 affection** and an accepted token through the wheel's Gift option. Reaching 60 does not skip the gift cooldown.

| Species group | Default token |
| --- | --- |
| Aquatic | 1 Heart of the Sea |
| Organic | 1 Golden Apple |
| Dark latex | 4 Dark Latex Crystal Fragments |
| White latex | 1 Quartz |
| Other variants | 1 Amethyst Shard |

Aquatic classification takes priority, then Organic; otherwise the Dark, White or Variant group is used. Token classification does not change the creature's faction.

An accepted token reserves the creature for you, including while either side is offline. Human players confirm **Voluntary transfur** to form a bond. Adult native players and supported permanently transformed TrueTransfur players use **Invite to Bond**, keeping their bodies and control modes.

Unused tokens can be withdrawn through the creature wheel or with `/changedsynergy bond token withdraw <creature UUID>`. Administrators can use `bond token cancel <creature UUID>`. Creature death or disabling the token requirement returns unused tokens once. Offline returns wait for the player; a full inventory causes the return to drop at the player's visible body.

When a bond-count or ownership limit prevents forming a new bond, eligible full-affection friends retain a separate **Friendly transfur** option. It explicitly creates no new bond and needs no token.

## Parting and reuniting

True bonded companions appear as **Name [Bonded]** in the relationship roster. Select **End bond** and confirm to part safely. This ends wrapping, bond following and exclusive protection while preserving friendship, name, affection and memories. You can also end a bond through commands or while the companion is unloaded.

Former companions can reunite at full affection when ownership and bond-count rules allow, without another token. Removing an ordinary friend is a separate operation that forgets the contact.

## Server settings

The Forge config screen shows actual server gameplay values as read-only on multiplayer servers. Operators and the console can use `/changedsynergy config list`, `get`, `set` and `reset`, or edit `config/changed_synergy-common.toml`.

New options are under `RELATIONSHIPS`: `BalancedRelationships`, `StrangerAffectionLimit`, `CareAffectionAllowance`, `CareAffectionCooldownSeconds`, `GiftCooldownSeconds`, `BondTokensRequired`, `BondTokens` and `PlayfulFriendTransfur`.

The screen has **Server Settings**, **World Rules** and **Client Visuals** tabs. Server categories include friendship growth, companion behavior, factions and dialogue; the complete, current catalog is in the [settings guide](SETTINGS_GUIDE.md). Operators can edit world rules through their own tab and press Done, using ordinary `/gamerule` permissions. Care limits and cooldowns require balanced growth. Bond tokens and their item list remain independent of that switch. Use the friendship and bond gamerules to enable or pause those systems.

`DIALOGUE.TelepathyEnabled` defaults to `true`. Setting it to `false` suspends earned and temporary telepathic understanding and pauses new unlock progress without clearing saved data. Enabled Addon Translators remain available when `DIALOGUE.UsesTranslator` is enabled. Turning translator support off only removes that item bypass. `TelepathyUnlockTransfurs = 0` disables new permanent unlocks; it does not suppress an already earned ability. Client dialogue presentation options do not override these server rules.

```mcfunction
/changedsynergy config set DIALOGUE.TelepathyEnabled false
```

For token-list editing, separate entries with semicolons in the screen or command:

```mcfunction
/changedsynergy config set RELATIONSHIPS.BondTokens dark=changed:dark_latex_crystal_fragment,4;white=minecraft:quartz,1;variant=minecraft:amethyst_shard,1;organic=minecraft:golden_apple,1;aquatic=minecraft:heart_of_the_sea,1
```

Commands also accept a JSON array of strings. Invalid list syntax is rejected. Missing groups or unavailable optional-mod items use the default item for that group.

Turning off balanced growth restores legacy affection gains and automatic acquaintance, preserving saved relationships. Bond tokens are independently controlled; turning that requirement off refunds unused tokens. The friendship gamerule pauses growth without deleting it, the bond gamerule prevents new bonds, and the personality/AI rules prevent autonomous playful attempts. Existing multiple-bond, transfur-method, rescue and protection settings remain independent.

## Administrator relationship commands

Use `/changedsynergy relationship help` for clickable commands. By default, each command targets the living adult social creature under your crosshair within eight blocks and your own player. Append `<creature selector> [player]` to specify targets; the server console needs both. State changes and operations for another player require operator permission level 2. Player and Instinct bodies cannot be used as NPC targets.

The examples below assume you are looking at the creature:

| Command after `/changedsynergy relationship` | Effect |
| --- | --- |
| `inspect` | Show saved affection, active tier, acquaintance, bond history, care allowance, both cooldowns, token reservation and server rules. |
| `establish` | Formally acquaint, give a name and set affection to the maximum of 60. |
| `set 8` / `add 2` | Set or adjust affection without changing formal acquaintance. The previous `<creature> <player> <value>` syntax also works. |
| `cooldown care 0` / `cooldown gift 0` | Set the chosen cooldown in seconds. |
| `cooldown reset` | Clear both cooldowns without changing affection or allowance. |
| `allowance set 0` / `allowance refill` | Set remaining care allowance or refill it to the server's configured limit. |
| `token offer` | Offer the player's actual held token, following normal affection, cooldown and eligibility rules. |
| `token withdraw` / `token cancel` | Return the player's unused token, or cancel and refund the original owner's reservation. No refund is duplicated. |
| `bond` | Create a full-affection bond for testing, bypassing token progression while preserving ownership, adulthood, bond gamerule and bond-count limits. |
| `release` | End a bond through the normal safe release flow, retaining friendship and reunion eligibility. |
| `reconcile` | Formally acquaint and clear personal grievances without raising affection. |
| `forget` | Clear the pair's relationship record. Existing bonds and pet ownership must be released first. |

For example, `/changedsynergy relationship establish @e[type=changed:dark_latex_wolf_male,sort=nearest,limit=1] PlayerName` targets a specific player. The friendship gamerule must be enabled for growth adjustments. Inspection still shows saved growth when that rule is disabled; cleanup and refunds remain available. The UUID-based `bond release` and `bond token withdraw/cancel` commands remain available for unloaded creatures.
