# Changed: Synergy 0.1.1

Thank you to everyone who tested this update, shared ideas, reported problems, and provided feedback! Your help has shaped these systems and made this release possible!

## Body takeover

- Expanded body takeover into a complete sequence covering observation, temporary control, escape attempts, sleep, and safe release. Movement, combat, item use, and abilities are validated by the server while the carrier is in control.
- Added a separate takeover path for the Exoskeleton while preserving its original movement behavior and speed.
- Added the default-off **Transfur After Sleep** option. When enabled, the player and carrier merge into one individual: the player continues in the carrier's form with its eye appearance and name until returning to human form.
- Added **Ordinary Transfur Reversal**, which can disable Synergy's negotiated, companion, and sleep-based reversal mechanics without affecting takeover separation or Changed's recovery commands.
- Improved overlapping captures and multi-creature encounters. A completed takeover ends any previous grab, and nearby latex creatures disengage from the player and carrier when takeover begins.

## Bonded companions

- Added a revival process for bonded dark latex creatures. A fallen companion leaves behind a Broken Dark Latex Mask, which can be repaired at a crafting table with four Dark Latex Crystal Fragments. Wearing the repaired mask and undergoing transfur allows the player to help reconstruct the companion's body.
- Broken and repaired revival masks have a glowing item outline for easier identification, while their tooltips retain the bonded companion's information.
- Added the configurable **Protective Release Requests** system. After repeatedly rescuing an owner who keeps entering dangerous situations, companions with certain personalities may require several release requests before ending their protection.

## Equipment and interaction

- Friends and bonded creatures can now be equipped with suitable armor and melee weapons. Supported weapons include swords, axes, tridents, and modded items with valid main-hand attack damage.
- Preserved the original Dark Latex Wolf Pup radial menu and its functions while applying Synergy's radial-menu effects.
- Provisioners can exchange supplies with trusted players through the social wheel. Offers use the community's real generated and delivered stock, retain a proportional reserve, and report when no surplus is available instead of opening an empty exchange.
- Outpost caches now serve as protected community stores: non-allies obtain supplies through provisioner exchanges, while allied players retain direct shared access.

## Creature ecology

- Adult Pure White Latex Wolves can adapt into male White Latex Wolves when they remain far from the White Latex Forest, then return to their Pure White form inside the biome. Their identity, relationships, equipment, and Pure White faction allegiance are preserved, and the relationship manager marks the adapted form.
- Added a configuration option for the Pure White Latex Wolf adaptation mechanic.

## Performance and diagnostics

- Reworked AI scheduling for areas containing many latex creatures. Social, community, hunting, and work decisions are distributed across ticks, while distant idle creatures perform optional updates less frequently.
- Added a soft latex-AI time budget that can defer noncritical background work under server load while keeping combat, following, and companion safety responsive.
- Added a dedicated **Performance and Diagnostics** configuration section with live client FPS, server tick time, latex-AI time, active creature count, deferred-work readings, and several diagnostic switches with performance-impact descriptions.

## Commands and information

- Reorganized `/changedsynergy` into clearer command branches with clickable help.
- `/changedsynergy reputation` now displays the player's faction standings and current regional Light-faction reputation.
- `/changedsynergy relationship` opens the full relationship manager, while the existing management commands remain available.

## Compatibility and stability

- Added optional compatibility with Changed Extras 1.1.4-beta3b. Changed Extras may retain control of unrelated wild-creature AI, while friends, bonded companions, takeover carriers, and pursuit members use Synergy's relationship-aware behavior. Its target selection now respects Synergy relationships, truces, and creature alliances.
- Fixed a production Forge startup failure in Changed-creature AI timing and hardened takeover input mappings for packaged installations.

Thank you again to every tester and to everyone who offered suggestions, criticism, reports, and other feedback throughout development! Your time and attention continue to make Changed: Synergy better.
