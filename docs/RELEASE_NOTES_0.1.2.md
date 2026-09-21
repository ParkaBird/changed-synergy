# Changed: Synergy 0.1.2

Thank you to everyone who tested the builds after 0.1.1, reported problems, shared ideas, and helped shape this update!

Special thanks to Nic for rewriting the personality dialogue and to Crossader for special-mechanics dialogue rewriting! We also appreciate their localization contributions; contributed German and Spanish lines are included in this build. These are the player-facing changes since 0.1.1.

## Companions and relationships

- Revival now extends to bonded, non-organic latex creatures beyond dark latex. A fallen companion can leave an identity-bearing latex sample. Use it in the Latex Infuser with that species’ recipe to make a syringe or flask, then transfur to help rebuild the companion.
- Repaired Dark Latex Masks now use Changed's original mask transfur behavior when worn, including its gradual progress and completed transformation.
- Added an optional Multiple Bonds setting. Voluntary transfur remains available even when an existing bond prevents a new one.
- Added Rest Together. Bonded companions can rest nearby or lead the player to a usable bed and sleep together at a walking pace while the player remains free to look around.
- Tamed juvenile pets keep their ordinary pet behavior, but cannot use bonded-companion wrapping until they mature.
- Aquatic companions can now be told to Wait on Shore.
- Bonded companions may confine an owner who repeatedly kills members of their species, with personality-dependent responses.
- The relationship roster now supports left/right page navigation, shows last known locations, and allows confirmed relationship removal even when a creature is unloaded.
- Players can retrieve an equipped melee weapon from a friend or companion.

## Social interaction and transfur

- Patting now has smooth mouse-wheel speed control through both the H key and radial menu, a third-person arm animation, and speed-dependent reactions.
- Before telepathy is unlocked, creatures use species-based vocalizations. Reptile-like species can chirp, tweet, or hiss according to their mood.
- Some aggressive creatures can be reasoned with without immediately becoming friendly. An appeal may make them hesitate, show mercy, or briefly lose their momentum.
- Negotiation’s next-step suggestion can be hidden in settings. When shown, it can be mistaken; offering suitable food makes it more reliable.
- Added an optional setting to vary qualifying encounters between assimilation and absorption.
- Exoskeleton takeover now displays a scrolling status log beside the player view.

## Territories and communities

- Provisioners can establish outposts in suitable Changed ruins and other non-facility structures. Latex bees also have a hive-themed outpost inside their hive.
- Indoor and hive outposts now use physical orange piles. Orange leaves regrow after harvest; players can shear fruiting leaves for dropped oranges or take oranges from a pile by hand.
- Restored additional white and dark territory buildings and latex pools, and expanded the creatures that can appear in white territory.
- White-faction reputation protection now covers the appropriate white-latex environmental hazards. White-latex creatures merge into and emerge from valid surfaces within their territory, with updated liquid effects.
- Improved Badlands creature activity and navigation around laboratory tables. A client setting can remove Pure White visual overlays.

## Trading and compatibility

- Reworked provisioner trades around oranges, berries, and species-appropriate fish. Materials, community-stock-backed exchanges, and relationship-gated collectibles use the familiar villager trading interface. Used-up offers no longer refresh when the screen is reopened.
- Added a configurable peace rule between Synergy creatures and villagers, wandering traders, iron golems, and other tagged village civilians and protectors.
- With Changed Vanilla installed, creatures in territories that respect a human player are less likely to turn nearby animals into new threats to that player.
- Added optional Domestication Innovation compatibility for owned companions, including pet equipment, beds, recall, and owner-alliance behavior.

## Server administration

- Dedicated-server operators can inspect and change gameplay, AI, and performance settings with `/changedsynergy config`; multiplayer clients no longer present their local copy of those settings as the server's configuration.
- Operators can use `/changedsynergy spawn <creature_id> <personality>` to generate a creature with a chosen dominant personality.
- Added an optional Independent Faction Reputation setting that removes rival-faction reputation penalties and allows simultaneous alliances with every faction.

## Stability

- Fixed packaged-client startup failures in repaired-mask rendering and the Rest Together camera behavior.

Thank you again to everyone who tested, translated, reported bugs, or shared feedback. Please back up important worlds before updating and include your mod list and `latest.log` when reporting a reproducible problem.
