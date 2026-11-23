# Social changes after the armor update

This is the implementation ledger and manual regression checklist for the current unuploaded build. It is not a record of completed in-game tests. See CHANGELOG.md / Unreleased for all major changes starting with armor and release-placement work.

## Capture and relationship gates

- Normal faction grace is now 3 minutes. Secondary involuntary transfur additionally prevents player-attributed damage to that group for the same period.
- Real aggressive attacks are remembered by faction for 5 minutes. A successful human transfur in that interval also starts the enforced 3-minute lock; an attempted but failed transfur does not.
- Capture by a pursuit squad also enforces the lock. A voluntary companion's protective wrapping does not count as punitive capture.
- Check native assimilation, absorption/respawn, both Addon secondary paths, reconnecting, death, dimension changes, and expiry. A blocked melee/projectile must not cost reputation or redirect through a grabbed victim.
- Ordinary non-enforced grace can still be broken by aggression. Forced grace cannot. Test unrelated factions independently, including Light regional groups.
- Voluntary transfur requires Close tier, including confirmation after an already-open menu becomes stale.

## Approach boundaries

- Survival human, normal newcomer approaching to greet or transfur: empty-hand hit, at most 2 health points, nonlethal. First and second hits within 60 seconds cause 30 seconds of personal retreat; the second gives a final warning.
- Third hit, a weapon/projectile, or more than 2 health points invokes normal retaliation rather than another retreat. Killing blows retain ordinary death handling.
- Competitive trait: no retreat; challenge dialogue and 90 seconds of increased grab-attempt probability (1.35 multiplier, capped at 100%, never overrides a disabled chance).
- Check no reputation penalty / capture-aggressor marker for accepted soft gestures; ordinary harmful attacks retain both.
- Already-hostile faction members, cache defenders, combatants that already hurt the player, active grabbers, and pursuit squads cannot be indefinitely pacified by this feature.
- Test a second nearby player: retreat belongs to the actual player, never creates a faction-wide truce, and must not suppress defense against a different attacker.

## Pursuit

- Server config: RELATIONSHIPS.FactionPursuit, default true. Only minimum (-100) reputation triggers. Existing saves are discovered when a representative is nearby.
- First check is delayed one minute after a warrant is recorded; warning gives at least 10 seconds. Spawn 2–3 matching creatures, 24–40 blocks away, only in already-loaded safe space and at least 20 blocks from every player.
- Player-wide and per-warrant cooldown: 10 minutes after a successful squad. Maximum pursuit: 3 minutes. A failed placement retries after a minute, without fallback spawning inside the player.
- A local population check prevents spawning another squad when more than three temporary pursuers are already within 96 blocks (at most six after one new squad).
- Check subgroup retention, Peaceful, doMobSpawning=false, creative, spectator, beds, mounts, dimension changes, and logout/reload. No continuous reinforcement waves for nearby players and no force-loading terrain.
- Check melee/grabbing at close range; pursuit navigation must relinquish MOVE to native combat goals.
- Grace, improved reputation, timeout, invalid target or disabled config retire the squad. Busy grabbers must finish safely before removal. Becoming an actual pet removes the temporary squad status.
- Confirm no ordinary mob loot/XP farming from temporary squad entities. Arrival lines differ for White/Dark/Light/Aquatic; stand-down and withdrawal do not use arrival text.

## Friend inventory

- Close-tier, trusted, non-hostile friend: social wheel opens inventory. Lower tier and stale-menu packets fail. This does not grant ownership, rescue commands, or exclusive bonding.
- Inventory click, shift-click, drag and button actions revalidate permission. Relationship loss closes access. Another player's exclusive pet is not exposed.
- Two viewers of one creature: second is denied. Confirm no duplicated stacks after reopening, reconnecting, saving/loading and becoming bonded later.
- Check equipment shape restrictions, occupied slots, clothing/Curios, and native dark versus Addon/fallback storage.

## Dialogue height

- Client TELEPATHY.PopupHeightOffset and DanmakuHeightOffset range -0.75..0.75, default 0; positive moves down, negative moves up. In-game settings use 0.05 steps.
- Change each independently in POPUP, DANMAKU and AUTO. Test multiple resolutions, GUI scales, fullscreen toggles, several overlapping popups and long four-line text. Existing motion is recreated after display geometry changes.
- No packet format change; settings affect only the local viewer.
