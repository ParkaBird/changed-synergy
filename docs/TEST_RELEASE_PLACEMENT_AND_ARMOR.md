# Release placement and armor regression checks

These are manual gameplay checks, not a record of completed in-game tests. Use a backed-up test world. Run with Changed alone, then with Changed Addon Plus 2.9.2c; repeat the player-interaction checks with a dedicated server and a second client.

## Release placement

1. Complete a negotiated reverse transfur on a full-block floor. Verify that the creature's feet remain above the surface and it resumes normal movement.
2. Repeat bonded wrapping release, both manual and after emergency recovery, on full blocks, bottom slabs, top slabs, stairs, and dirt paths.
3. Repeat beside a wall and under a low ceiling, including a large/alpha creature. A replacement position must fit its full collision box, not place it inside the wall/ceiling or overlap the player.
4. Interrupt a hold by death or ending a bond. No live creature should remain embedded; dead/removed creatures should not be teleported or revived.
5. Release in a clear space and verify that an already correctly placed creature is not moved unnecessarily. Repeat an underwater release with an aquatic creature.
6. Trigger another grab immediately after a release. The delayed collision check must not pull a creature out of its new active grab or wrapping state.
7. Complete absorption/fusion separation with a resized source creature. Its recreated body must use its restored dimensions when choosing a landing position.

## Armor interactions

1. Establish a friendship; sneak-right-click with one helmet. It should equip into an empty head slot and consume exactly one held item in survival.
2. Repeat with enchanted/damaged armor and a command-created stack. Preserve all item data, consume only one item, and retain the remainder. Creative mode must not consume the held stack.
3. Try a second helmet, including a better one. Refuse it without replacing or dropping the existing helmet and without consuming the held item.
4. Try ordinary leggings/boots on a taur and other incompatible forms. Refuse according to Changed's body-shape rules; compatible specialized/form-fitting armor must follow the same rules as the bonded inventory screen.
5. Sneak-right-click a saddled taur with accepted and rejected armor. Neither case may mount it or open the taur configuration. Non-armor interactions should retain their existing behavior.
6. Try a stranger, a hostile former friend, and a creature currently holding someone or carrying a rider. No armor should be transferred.
7. Try simultaneous attempts from two players. Only the first valid attempt may fill the slot; the second player's item must remain intact.

## Absorbed equipment

1. Give a creature any one armor piece, then let it absorb a mob wearing a full set of better armor and holding a weapon. Its armor must stay unchanged, all other armor slots must remain empty, and the existing weapon-inheritance rules must still apply.
2. Repeat with an entirely unarmored creature. It may inherit the first victim's compatible armor set; subsequent victims must not replace or fill out that set.
3. Equip armor between an absorption event and its queued equipment update, or have two transfers queued in one tick. Rechecking at execution time must preserve the first equipped set; already-transferred surplus items should be dropped rather than silently replacing it.

## Melee weapon interactions

1. Establish a friendship; sneak-right-click with a sword, axe, or trident. One exact item should move into an empty main hand and immediately affect normal melee attacks. Creative mode must keep the offered item.
2. Repeat with an enchanted or damaged weapon and a modded weapon that supplies positive main-hand attack damage. Preserve all item data. Reject bows, crossbows, shields, and items without positive melee attack damage.
3. Try to equip a creature whose main hand is occupied, including a provisioner displaying a temporary work tool. The real held item must be restored before the occupancy check, and neither item may be lost or replaced.
4. Repeat with a stranger, a hostile former friend, a grabbed or ridden creature, and two simultaneous players. Refusals must consume nothing; only the first valid interaction may fill an empty hand.
5. Verify the equipped weapon renders, persists across saving and reloading, drops on death, and remains accessible in the original black-latex inventory storage when that native inventory is present.
