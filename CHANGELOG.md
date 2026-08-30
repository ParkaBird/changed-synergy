# Changelog

This project follows semantic versioning while it is practical. Beta releases may still change saved-data details or interaction balance.

## 0.1.0-beta.3

Beta 3 focuses on provisioner reliability, community and facility stability, reverse-transfur fixes, radial-menu animation improvements, and expanded Golden Orange compatibility.

### Added

- Added the **Allow Mindless Mob Transfur** configuration option.
  - When enabled, latex creatures can fully transfur mindless mobs such as zombies and skeletons.
  - Disabled by default; mindless mobs will continue to be absorbed to reduce the number of persistent entities created.
- Added full Golden Orange compatibility for both **Changed Addon Plus** and **Changed Additions**.
  - Golden Oranges can be given as enhanced gifts.
  - They provide additional friendship and faction-reputation benefits.
  - They can be used as improved food offerings during negotiations.
  - Receiving creatures gain temporary regeneration, absorption, and movement-speed effects.

### Provisioners and resource gathering

- Increased the priority of provisioner work and resource delivery.
- Provisioners carrying resources will no longer be repeatedly interrupted by:
  - Idle and comfort behavior
  - Social approaches
  - Player-following behavior
  - Companion fallback work
  - Other low-priority routines
- Provisioners now resume delivery after combat or temporary path interruption.
- Added distance-aware delivery time limits, allowing provisioners to complete longer return journeys.
- Added temporary resource claims so multiple provisioners do not target the same dropped item or fish.
- Resource and fishing claims are now released correctly when a task is canceled or completed.
- Gathering and fishing behavior now respects the `mobGriefing` gamerule.
- Improved handling of fishing results so valid cargo is retained and unused catches are returned to the world.
- Provisioner cargo, resource origin, hunting cooldown, facility assignment, and storage memory are now preserved when a creature changes form.
- Improved container insertion:
  - Container slot restrictions are now respected.
  - Container stack-size limits are handled correctly.
  - Containers are only marked as changed when an item was actually inserted.

### Facility provisioners

- Facility populations now use stable colored-section assignments.
- Transition rooms no longer cause adjacent facility populations to merge or switch identities.
- Facility provisioners now remain within their assigned section while patrolling.
- Provisioners can locate the correct work room even if they spawned elsewhere in the same facility section.
- Improved facility storage discovery and reuse.
- Remembered storage containers are no longer discarded merely because their chunks are temporarily unloaded.
- Provisioners carrying cargo may return through transition spaces after being displaced by combat.
- Increased the number of facility path candidates considered when searching for a valid route.
- Maintenance-section provisioners no longer search for nonexistent gathering resources, but may still occasionally offer fish products to friendly humans.

### Communities and outposts

- Fixed multiple provisioners near the same gathering area creating or retaining separate community records for one physical outpost.
- Communities sharing the same compatible cache are now merged into one authoritative record.
- Added persistent community aliases so unloaded creatures remain connected after duplicate communities are merged.
- Added migration handling for older community records and regional identifiers.
- Stabilized Light faction regional identities, including cave and Deep Dark detection.
- Facility community identity is now preserved when combat pushes a creature across a section boundary.
- Existing outpost blueprints are now authoritative and cannot be replaced by a visiting creature from another regional population.
- Fixed established outposts repeatedly changing between forest, cave, or other regional layouts.
- Improved cache validation when an outpost or storage chunk is temporarily unloaded.
- Improved habitat validation for existing outposts.

### Reverse transfur

- Fixed cases where a latex creature would grab the player briefly, release them immediately, and fail to reverse the transfur.
- Fixed reverse transfur becoming unreliable when Changed Addon Plus was installed.
- A successful negotiation is now treated as committed even if the Addon grab animation loses its target.
- Pending reverse-transfur completion is now recovered correctly after skipped ticks, chunk unloading, or server restarts.
- Failed presentation animations no longer restore old negotiation progress after success has already been confirmed.

### Radial menus

- Radial menus can now reverse directly from their current opening progress when closed early.
- Closing a partially opened wheel no longer forces it to finish opening first.
- The relationship information panel now remains stationary while fading during a switch to the ability wheel.

### Compatibility and documentation

- Updated Changed Addon Plus and Changed Additions compatibility documentation to describe Golden Orange support.
- Updated the README and third-party notices to reflect the expanded integrations.

### Notes

This is still a beta release. Back up important worlds before updating.

Please include your `latest.log`, exact mod versions, and installed optional integrations when reporting reproducible issues.

## 0.1.0-beta.2

Second public beta update.

- Reworked reverse transfur recovery for assimilation, absorption and fusion, including safer source restoration and interrupted-session cleanup.
- Prevented release from wrapping or absorption while airborne or near hostile non-latex mobs; release becomes available again once the area is safe.
- Added controlled gliding for winged latex creatures using their native flight animation.
- Fixed provisioners failing to resume resource delivery after combat.
- Kept the final orange in a placed orange pile so the pile remains available for restocking.
- Added a minimum spacing claim around community outposts to prevent adjacent settlements from replacing or crowding one another.
- Added basic Changed Additions compatibility and documented the optional integration.
- Excluded Addon boss entities and Foxyas from Synergy social, relationship, hypnosis and companion systems.

## 0.1.0-beta.1

First public beta candidate.

- Added persistent creature identities, personalities, social memory, friendship and voluntary bonds.
- Added social, relationship and companion wheels with animated transitions.
- Added contextual English and Simplified Chinese dialogue, emotes and configurable telepathy presentation.
- Added faction reputation, territory presentation, white hive cooperation and relationship-aware combat safety.
- Kept friendship recoverable at low faction standing, with reduced gains and continued progress from repeated peaceful interaction.
- Added negotiation for involuntary assimilation, absorption and fusion, with motive- and personality-dependent choices.
- Added grab and hypnosis QTE work, swimming pursuit, firearm reactions, disguise behavior and organic combat rules.
- Added creature roles, resource gathering, fishing, hunting, outposts, guards, gifts and environment interactions.
- Added optional Changed Addon Plus integration and basic Changed Additions compatibility.
- Added accessibility and visual work for wrapping, organic assimilation and pure white forms.

This list describes the public baseline rather than every development revision.
