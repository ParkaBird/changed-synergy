# Changed: Synergy 1.1.5-Beta.2

Changes since **1.1.5-Beta.1**.

### New species and structures

- Added **Latex Eagle Owl**, a playable mountain species with natural spawns, matching transfur items and an archive entry. It has slightly stronger Crow attributes, four extra jumps with wing beats, gliding, the existing Wing Flap ability when Addon Plus is installed, toggleable night vision, monochrome sight (with color restored on full-moon nights), 270-degree head movement and responsive ear tufts. Airborne grabs extend its talons forward, then draw caught targets back beneath its body, carrying them face-down by the back before blending into an ordinary hold on landing.
- Added the **Aviator Outfit** with raised/lowered goggles, native style sounds and support for bipedal, tail, legless and juvenile bodies; juveniles wear only goggles. Lowered goggles restore Owl color vision; the recipe uses amethyst shards. New owls have a configurable 25% chance to wear it. Aviator and Trailcloth outfits fit either the inner or outer clothing slot, retaining clothing conflicts; Trailcloth connects its back strap on non-hyena wearers.
- Added standing and horizontal two-block bird perches using the supplied sleeping pose. The stand sleeps one bird; the rail sleeps two at opposite ends. Both face the placing player, preserve native sleep/wake behavior and let camp residents use them as beds; Eagle Owls sleep by day and Crows by night. Updated beam ends, transparent decorations and the centered rail item icon.

- Added a forest treehouse with its canopy Silk Moth wearing the Canopy Outfit and a group of Forest-population creatures. It generates in ordinary forests with a level foundation, protected building space and a small terrain transition around its clearing.
- Added the supplied forest watchtower, retaining its loot and decorations without creature residents. It uses the same forest-only placement and terrain checks.
- Added male and female **Latex Spotted Hyenas**, with playable origins, spawn eggs and matching transfur syringes, flasks and arrows. The synthesizer selects their sex. They belong to the savanna population, and their ears react to jumping, landing and quick head turns.
- Added **Hyena's Hideout** and three smaller camps, **built by Crossader**. Hideouts generate in savannas; tropical camps in jungle and savanna biomes; open-country camps in deserts, badlands and plains (including sunflower plains); snowy camps in snowy plains. Their supplies, paintings and other authored contents are retained, except for the removed draft-book chest beside the hideout well.
- Spotted Hyenas spawn normally in jungle and savanna biomes, including their hideout. In other biomes, they spawn only within their camps. Each small camp starts with two persistent males; the hideout starts with four males and one female. Structures replenish males only, while tropical wilderness can still spawn either sex. Both sexes fit the camps' doors and passages.

- Removed the draft-book chests beside the treehouse campfire and the hideout well, preserving the rest of each structure.

### Gameplay and fixes

- Added the **Canopy Outfit** with authored four-arm, bipedal and female styles, native inner/outer clothing slots and Curios support. Tail, legless and taur bodies wear only the upper section; juveniles wear its headband. Improved Canopy and Aviator fitting for classic and slim human skins.
- **Forgive** in transfur negotiations grants affection once and ends the negotiation while keeping the current form. Absorbed creatures retain their identity and relationship for later external separation.
- Updated the treehouse canopy resident to an outfitted Silk Moth and matched its lectern journal, continuing long passages on additional pages without shortening the prose.
- Addon Plus's Diffusion Syringe now restores the saved individual after absorption, preserving identity and relationships without duplicating it. Assimilation still produces a fresh creature through Addon's default split.

- Airborne arm releases keep their target at the carrier's current location, clear stale tracking interpolation and prevent reacquisition for one second. Normal and friendly holds share this release protection. Server-rejected Owl grabs now immediately clear the client's predicted hold instead of leaving a ghost catch that snaps back on release.
- Bird perches can be registered as player-camp beds and discovered inside natural camps. Eligible structure residents prefer free perches to ordinary beds. Unassigned wild NPCs do not seek nearby perches; player-camp members use bound beds. Eagle Owls sleep by day and wake at night, with corrected upright placement on the beam. Perch sleep uses third person and restores the previous view after waking.

- Fixed native-origin archive pages briefly flashing back before settling. Page curls now finish with the page handoff, and their animation starts after the source snapshot is ready.

- Wheels with more than eight options now use continuous mouse-wheel scrolling, with fading incoming/outgoing icons. Shift+scroll switches paired wheels; smaller wheels retain plain scrolling to switch. Removed arrow-key paging and shortened control hints.
- Applied closed-eye base textures to all supplied Synergy species and hide their eye overlays while sleeping. Updated eight Chinese advancement titles without changing their criteria or IDs.

- Fixed transparent perch decorations appearing black, Eagle Owl eyes flickering and inventory-head orientation. Airborne talons now acquire eligible contacted targets without mouse aiming while preserving normal/friendly target restrictions and independent wing animation. Monochrome vision now includes first-person hands and has a client option beside Pure White vision settings.
- Entering **Talk about this transfur** from an NPC social wheel now keeps the wheel on the right and opens the negotiation page immediately, without sliding through the center or replaying its opening animation.
- Fixed human-start players being unable to invite an NPC bond after transforming normally. Existing closeness, token, confirmation and ownership requirements remain; TT Instinct interactions use the controlled body for proximity and the player inventory for offerings.

- Creatures that can open doors can also open wooden trapdoors and fence gates along their route, then close them once the opening is clear. Iron trapdoors remain redstone-operated. Fixed a missing ground column beside the hideout's well chest.
- Authored treehouse and hyena-camp residents now maintain their own camps: they collect and store supplies, harvest and replant crops, hunt wild prey, share campfire meals and sleep in their structure's beds at night. Beds are reserved individually, and residents wake at dawn or when interrupted. Camp storage uses existing reputation reactions, livestock are protected, and residents do not build another outpost. Added a separate routine setting.
- The hideout's female Hyena starts in the orange-bed room wearing Trailcloth. The canopy Silk Moth and green laboratory's Gas Wolf also retain their structure boundaries; the Gas Wolf wears a lab coat and red necktie. Home and clothing state survive reload, preserving later player changes.
- Player and creature clothing uses genuine Curios equipment slots and storage when installed, replacing the original clothing slots while retaining native anatomy, effects and clothing conflicts. Equipped garments retain right-click style changes, and Curios visibility toggles control their rendering. Existing clothing migrates on load; creature storage remains accessible. The original clothing interface remains when Curios is absent.
- Applied the author's updated male/female Trailcloth models and textures without green dots, and shortened its Chinese name to **行猎装**. Tail and taur bodies retain the upper outfit only.
- Added carpet seating between native seats and bare ground in rest-place preferences.
- Native-origin players can occasionally make animal calls while chatting, with a separate setting and server cooldown. Both global and local role chat use the controlled body's position.
- Hyenas can eat leather for brief Resistance and selected positive brewing ingredients for their corresponding effects. Negative ingredients provide food without effects. Rotten flesh restores six food points without Hunger, and toxic food does not apply new harmful effects.
- Fixed Muntjac charge input preventing creative players from taking off with a double tap; charging and normal flight controls remain available.
- Added the **Trailcloth Outfit**, using the supplied icon, geometry and gender-specific styles. It fits adult humanoid forms, with only the upper section on tail and taur bodies; juvenile and pure feral bodies cannot wear it. Includes native clothing rules, a crafting recipe and first-person sleeves.
- Newly generated Spotted Hyenas and camp residents have a configurable **25% chance** to wear Trailcloth. Existing clothing and saved residents are preserved.
- Spotted Hyenas can chew bones for a little food and eight seconds of a random level-I effect: Speed, Haste, Strength, Resistance or Regeneration. Friendly and bonded hyenas can eat stored bones when injured, and outpost residents can include them in shared meals.
- Female Spotted Hyenas have slightly higher health (26 versus 24), movement speed (1.13 versus 1.075) and jump strength (1.05 versus 1), for both NPCs and transformed players.
- Fixed female Spotted Hyena eyes disappearing when looking down, and applied the updated eyebrow textures.
- Fixed hyena structures appearing above their surroundings because their existing soil layers and hideout basement were combined with terrain adaptation.
- Male and female **Latex Muntjacs** can charge a higher jump by holding the jump key, with a horse-style charge meter replacing the experience bar while charging. A short press gives a normal jump. This passive also works when a human controls a Muntjac's wrapping body. Updated their names to follow Changed's male/female naming convention.
- Fixed the infection bar remaining at 100% after leaving a bonded wrap or completing a companion's revival.
- Fixed friendly social holds showing the wrong pose or missing their held pose when synchronization arrived before the creature, including for players who began watching an existing hold.
- Friendly play hugs and core companion grab, rescue, evacuation and combat-policy handling now work independently of **Changed Addon Plus**. Addon-specific items and features retain their optional integration.
- Fixed pursuing creatures being removed or leaving pursuit while still carrying a player.
- Continued the overflowing passage in the aquatic laboratory's **Homogeneity** book onto another page, preserving its text and signature.
- Updated the bond advancement's description to reflect that White Latex binding pillars are no longer dangerous. The Chinese Spin Silk advancement is now named **自给自足**.

### Compatibility

- Fixed Swordfish transfur and world-loading crashes involving **Entity Model Features / Fresh Moves** during the transformation animation.
- Fixed a startup crash in Swordfish Rush's armor-penetration handling when another mod adds armor-damage method overloads, as reported with **RosmontisLib**.

Servers and clients must both update to Beta.2.
