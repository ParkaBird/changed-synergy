# Canopy Outfit / 林冠装

`changed_synergy:forest_canopy_outfit` imports the author's three live Blockbench clothing projects: four arms, bipedal and female. The snapshots are retained under `outputs/forest-canopy-outfit-20261102`; `tools/import_forest_canopy_outfit.py` regenerates the layers, shared texture and item icon without editing those sources.

The outfit uses Changed's inner or outer clothing slot and the existing Curios integration. Adult humanoid bodies wear the full set; tail, legless and taur bodies omit its lower section. Juveniles wear only the circlet in a head/face slot. The headband reserves the head slot, and leg wraps retain native trousers conflicts. It cannot be equipped twice. Unsupported adult feral anatomy is rejected rather than forcing a humanoid outfit onto it.

The renderer follows the final native body pose, including the Silk Moth's second pair of arms. Humans use straight legs, native waist alignment and clearance above both classic and slim skin layers. The same human fitting is applied to the Aviator Outfit. Milk Pudding uses its native stacked body hierarchy. No armor attributes or additional AI task are added.

Crafting: `SES / LWL / L L`, using string (S), emerald (E), leather (L) and green wool (W). String unlocks the recipe. The item appears in Changed's clothing tab.

New forest treehouses replace the fixed canopy Leaf Dragon with a Silk Moth wearing this outfit. Its original location, home bounds and natural-camp routines remain. The unsigned lectern journal changes its two species references to Latex Silk Moth and continues long passages on extra pages; the rest of its text remains. Already generated residents and books keep their saved state.

The transfur negotiation wheel now offers **Forgive**. It grants eight affection points through the existing relationship system, cancels that claim and retains the player's present form. Repeated requests cannot award it again. Absorption retains the swallowed individual's identity and updated relationship snapshot for a later external reversal, while its negotiation remains disabled after reload.

Addon Plus's Diffusion Syringe also separates an absorbed individual with their saved identity, clothing and relationships, without granting a negotiated-release reward or spawning a second copy. Assimilation retains Addon Plus's default fresh-creature split; the external assimilator and its relationships remain separate. Existing safe-separation and permanent-form policies still apply.

The item icon uses the author's supplied `canopy_silk_outfit_icon.png` unchanged, including transparency. The importer retains this source instead of regenerating an icon from a model preview.
