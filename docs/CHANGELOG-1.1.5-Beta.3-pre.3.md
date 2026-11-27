# Changed: Synergy 1.1.5-Beta.3-pre.3

Preview release since **1.1.5-Beta.3-pre.1**.

#### Species and Clothing

* **Latex Manuls and Latex Gray Foxes**: Added male and female **Latex Manuls** (`latex_manul`, `latex_manul_female`) and **Latex Gray Foxes** (`latex_gray_fox`, `latex_gray_fox_female`), complete with native origins, spawn eggs, dialogue themes, and adapted facial layers.
* **Burrow Shawl (`burrow_shawl`)**: Added fitted Burrow Shawl outerwear across Manul, Gray Fox, female, and bipedal cuts, with the raised face/snout gaiter bound to the wearer's `Head` and the neck/shoulder wrap fixed on `Torso` to avoid clipping while turning the head.
* **Placeable Dark Latex Mask Block**: Sneak + Right-Click with `changed:dark_latex_mask` or `changed_synergy:repaired_dark_latex_mask` places a flat 16-angle mask block preserving the item's NBT; empty-hand interaction equips it onto human players or picks it up when transfurred.

#### Communities and World Generation

* **The Burrow and The Shoal**: Added the **Burrow** (`the_burrow` / `burrow`) subterranean community led by the Manul Steward and Gray Fox Lookout, and **The Shoal** (`the_shoal`) ocean platform community led by the Swordfish Scavenger (with light-blue Tall eyes), explorer maps, and authored supply/boss loot.
* **Subterranean Stiger Hut**: Added the deep-cave **Spidtaur Hut** (`stiger_hut`) small camp with a built-in Spidtaur resident and shared camp supply chest.
* **Farmer's Delight Optional Structure Variants**: Split Farmer's Delight structure templates into optional variants (`burrow_fd`, `point_prometheus_fd`, `the_shoal_fd`) with vanilla/Changed fallbacks when Farmer's Delight is absent, alongside **Point Prometheus** map and chimney refinements.

#### AI, Takeover, and Visual Fixes

* **Wrapped Pursuit Targeting**: Fixed pursuit squads stalling ~3 blocks away and flickering between aggressive and passive when pursuing a player wrapped inside a bonded pet (e.g. Pure White Latex squads vs a Dark Latex Wolf shelter).
* **Two-Block Passage Navigation & Animations**: Extended latex crouching traversal to general tight two-block doorways/passages, added villager-style crop-harvest particles, fixed third-person head-pat animation display, and resolved aquatic ground pathfinding and sleeping-lock edge cases.
* **Takeover & Bond Parity**: Choosing to surrender control during friendly or confinement takeover grants `+12` affinity (excluding punitive fatal takeovers); fixed bonded-merge greeting firing unexpectedly and ensured mobs newly spawned from player assimilation inherit initial player relationships.

Network protocol version: **69** (`Changed: Synergy` server and clients must match).
