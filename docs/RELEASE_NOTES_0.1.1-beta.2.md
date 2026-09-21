# Changed: Synergy 0.1.1-beta.2 Changelog

Thank you to everyone who tested this update, shared ideas, reported problems, and provided feedback. Your help has shaped these systems and made this release possible.

This changelog covers the major changes made since the release of `0.1.1-beta.1`.

## Body takeover

- Expanded body takeover into a complete sequence covering observation, temporary control, escape attempts, sleep, and safe release. Movement, combat, item use, and abilities are validated by the server while the carrier is in control.
- Added a separate takeover path for the Exoskeleton while preserving its original movement behavior and speed.
- Added the default-off **Transfur After Sleep** option. When enabled, the player and carrier merge into one individual: the player continues in the carrier's form with its eye appearance and name until returning to human form.
- Improved overlapping captures and multi-creature encounters. A completed takeover ends any previous grab, and nearby latex creatures disengage from the player and carrier when takeover begins.

## Bonded companions

- Added a revival process for bonded dark latex creatures. A fallen companion leaves behind a Broken Dark Latex Mask, which can be repaired at a crafting table with four Dark Latex Crystal Fragments. Wearing the repaired mask and undergoing transfur allows the player to help reconstruct the companion's body.
- Broken and repaired revival masks have a glowing item outline for easier identification, while their tooltips retain the bonded companion's information.
- Added the configurable **Protective Release Requests** system. After repeatedly rescuing an owner who keeps entering dangerous situations, companions with certain personalities may require several release requests before ending their protection.

## Equipment and interaction

- Friends and bonded creatures can now be equipped with suitable armor and melee weapons. Supported weapons include swords, axes, tridents, and modded items with valid main-hand attack damage.
- Friend and bonded-creature inventories can now use the creature species' native Changed color palette, including matching apparel and Curios panels, using only existing Changed interface assets. A new client option controls the presentation, and Changed's goopy-inventory option is still respected.
- Preserved the original Dark Latex Wolf Pup radial menu and its functions while applying Synergy's radial-menu effects.

## Creature ecology

- Adult Pure White Latex Wolves can now adapt into male White Latex Wolves after remaining more than 64 blocks away from their last White Latex Forest location. Adapted wolves return to their Pure White form when they re-enter the biome, while preserving their identity, relationships, health, effects, equipment, clothing, stored items, and Pure White faction allegiance. The relationship manager identifies this away-from-forest form, and a new gameplay option controls the mechanic.

## Performance and diagnostics

- Reworked AI scheduling for areas containing many latex creatures. Social, community, hunting, and work decisions are distributed across ticks, while distant idle creatures perform optional updates less frequently.
- Added a soft latex-AI time budget that can defer noncritical background work under server load while keeping combat, following, and companion safety responsive.
- Added a dedicated **Performance and Diagnostics** configuration section with live client FPS, server tick time, latex-AI time, active creature count, deferred-work readings, and several diagnostic switches with performance-impact descriptions.

## Commands and information

- Reorganized `/changedsynergy` into clearer command branches with clickable help.
- `/changedsynergy reputation` now displays the player's faction standings and current regional Light-faction reputation.
- `/changedsynergy relationship` opens the full relationship manager, while the existing management commands remain available.

Thank you again to every tester and to everyone who offered suggestions, criticism, reports, and other feedback throughout development. Your time and attention continue to make Changed: Synergy better.
