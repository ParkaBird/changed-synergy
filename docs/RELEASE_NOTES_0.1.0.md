# Changed: Synergy 0.1.0

The first stable release of Changed: Synergy builds on the RC1 stability, compatibility, and movement work, with a final round of social, riding, and quality-of-life fixes.

## Changed Vanilla compatibility

- Added official optional compatibility with **Changed Vanilla 1.0.1**.
- Changed Vanilla creatures now receive appropriate Synergy factions and species roles.
- Natural and converted Changed Vanilla creatures can participate correctly in identity, relationship, community, and companion systems.
- Preserved Changed Vanilla's dedicated zombie and skeleton conversions even when Synergy's mindless-mob transfur option is disabled.

## Stability fixes

- Fixed ticking-entity crashes caused by modifying AI goal selectors while they were being processed.
- Added a safe end-of-tick queue for Addon Plus goal changes and inherited equipment updates.
- Improved equipment inheritance when a creature absorbs an equipped mob.
- Community membership is now saved, restored, and merged correctly.
- Improved cleanup of stale wrapping and temporary transfur states after player death, cloning, and respawning.
- Provisioners now reliably put away temporary fishing rods, pickaxes, and navigation items when gathering is completed or interrupted.

## Companion movement

- Added shared land-and-water navigation for bonded and friendly followers.
- Fixed aquatic companions swimming in place when attempting to follow a player on land.
- Aquatic creatures can now locate nearby shorelines and leave the water to approach their owner.
- Added stalled-path detection and safe recovery for following, social approaches, rescue behavior, and emergency wrapping.
- Improved bonded rescue behavior when an owner is grabbed or otherwise unreachable.
- Bonded creatures attempting to prevent their owner's transfur now approach at a normal running speed instead of moving unnaturally fast.
- Other urgent rescue situations retain their faster emergency movement.

## Final release improvements

- Friendly, non-hostile latex creatures near a bed no longer trigger Minecraft's “monsters nearby” sleep restriction.
- Added automatic sideways seating for players riding supported taur creatures.
  - The camera now follows the sideways pose and uses a collision-safe offset to keep the taur's head from blocking the rider's view.
  - The pose and camera handling work for both human and transfurred player models.
- Added a **Pat Pacification** configuration option.
  - Disabling it prevents pats from stopping pursuit or creating a temporary ceasefire.
  - Ordinary pat reactions and relationship progress remain available.
- Dead latex creatures are now removed promptly from player contact lists.
- Wild latex sharks, milk puddings, and headless knights can now participate in Synergy's social systems.
  - They remain excluded from Synergy's grab mechanics.

## Compatibility and networking

- Improved Changed Addon Plus goal installation and removal safety.
- Updated compatibility documentation and optional dependency metadata.
- Uses the updated 0.1.0 release-line network protocol. Clients and servers must use compatible Synergy builds.

## Supported versions

- Minecraft 1.20.1
- Forge 47.4.x
- Changed 0.15.7
- Changed Addon Plus 2.9.2c or Changed Additions 0.1.0-V4-Fix (optional)
- Changed Vanilla 1.0.1 (optional)

Thank you to everyone in the **Changed: Synergy Discord server** who tested development builds, reproduced bugs, shared logs and crash reports, and helped polish this release.

As always, back up important worlds before updating and report reproducible issues with the relevant crash report or `latest.log`.
