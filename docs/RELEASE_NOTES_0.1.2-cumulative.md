# Changed: Synergy 0.1.2

Thank you to every tester who ran development builds, shared screenshots and
logs, reported bugs, and suggested improvements. This release exists because
of that time and feedback.

This is a cumulative summary of the major completed features added after the
0.1.0 release. It omits internal refactors and one-off implementation work.

## Takeover and transfur

- Added the complete body-takeover sequence with temporary control, escape,
  sleep recovery, safe release, server validation, and cleanup for overlapping
  captures.
- Added an independent Exoskeleton takeover path that preserves its native
  movement style and speed.
- Added the default-off **Transfur After Sleep** option, which merges the
  player with the carrier and applies the carrier's form, eyes, and temporary
  name until human restoration.
- Added configurable randomized selection between assimilation and absorption
  for qualifying creatures.
- Added post-transfur ceasefires and improved protection from immediate
  re-targeting after release or takeover.

## Bonds and companions

- Added bonded dark latex revival through broken and repaired Dark Latex Masks,
  with crafting-table repair, rebuilding transfur, companion-specific tooltip
  data, and glowing item outlines.
- Added personality-based **Protective Release Requests** after repeated
  dangerous rescues.
- Added armor and compatible melee-weapon equipment for close friends and
  bonded companions.
- Preserved the Dark Latex Wolf Pup's original radial-menu functions while
  applying Synergy's wheel presentation.

## Communities and ecology

- Added provisioner outposts in compatible Changed ruins and ordinary
  non-facility structures, including a bee-hive-themed latex-bee outpost.
- Restored regional white/dark territory structure and liquid-pool generation,
  expanded white-territory creature spawning, and added lab-table pathfinding
  avoidance.
- Added real orange piles to indoor, ruin, and bee-hive outposts and removed
  the old invisible display-frame oranges. Orange leaves regrow after harvest.
- Added Pure White adult adaptation into male White Latex Wolves outside the
  White Latex Forest, with automatic return and preserved Pure White faction
  identity.
- Improved Badlands creature activity and Changed Vanilla animal protection
  around humans respected by their territory.

## Trading

- Rebuilt provisioner trading around oranges, berries, and diet-appropriate
  fish as player-facing currency.
- Added independent material offers, community-stock-backed buyback offers,
  return delivery to the outpost, relationship-gated high-value collectibles,
  and persistent exhausted offers.
- Updated the interaction flow to follow the familiar villager trade pattern,
  with clearer offer selection, payment slots, result collection, and stock
  feedback.

## Social interaction and presentation

- Added smooth, continuous pat-speed control through mouse-wheel input for both
  H-key and radial patting, with speed-sensitive probabilistic dialogue.
- Added species-specific pre-telepathy vocalizations, including chirp, tweet,
  and hiss-style reptile communication with mood variants.
- Added an aggressive-but-social creature profile that can show mercy,
  hesitate, reduce damage, or be briefly stunned by a reasoned appeal.
- Improved relationship-roster navigation, follower greeting timing, pure
  white visual-mask configuration, and white-liquid merge/release presentation.

## Performance and compatibility

- Added distributed latex-AI scheduling, a soft AI time budget, diagnostic
  readings, and a dedicated Performance and Diagnostics configuration section.
- Added optional Changed Extras compatibility with relationship-aware target
  handling and preserved exclusions for creatures deliberately stripped from
  Synergy.
- Added optional Domestication Innovation 1.7.1 compatibility. Synergy-owned
  friends and bonded companions participate in DI's collar, pet-bed, recall,
  owner-alliance, and same-owner checks without making wild creatures tame.
- Added the default-on **Peaceful Village Relations** option so villagers,
  wandering traders, iron golems, and tagged village protectors remain peaceful
  with Synergy creatures in both directions.

Thank you again to everyone who tested, criticized, translated, reproduced,
and explained problems throughout development. Please continue to report
reproducible issues with the exact mod list and `latest.log`.
