# Latex Eagle Owl

The supplied owl and Aviator Outfit projects are imported without overwriting the originals. `tools/import_eagle_owl_assets.py` rebuilds the native Java layers, embedded textures and perch assets. The manifest records source hashes, duplicate beak removal and thin-surface separation. Editable authoring projects are separate from the public runtime resources; reimporting requires the supplied source projects.

## Species

- Entity: `changed_synergy:latex_eagle_owl`; variant: `changed_synergy:form_latex_eagle_owl`.
- Mountain population, naturally spawning in mountain biomes, groves, snowy slopes and peaks. Spawn weight 5, groups of 1–2; native Changed ground-spawn conditions still apply.
- Crow attributes with 26 health, movement 1.05 and an 8% attack increase. Retains four extra jumps, gliding and powder-snow walking. Air jumps produce a single eight-tick wing beat. When Addon Plus is present, the owl uses its existing `changed_addon:wing_flap`, including the original charge, cooldown, sound and food cost. Successful flaps retain the owl wing beat. No separate Synergy flight ability is registered.
- Native toggleable night vision; the world is grayscale except on full-moon nights or when equipped Aviator goggles are lowered. First-person hands share the grayscale effect; GUI colors are preserved. The client-only EagleOwlMonochromeVision option appears with Pure White vision settings. Controlled owl wrapping uses the carrier's equipped goggles.
- Original and Tall eye styles only. Pupils remain black; colored eye whites use each eye's iris setting, with a darker upper row. Other requested styles fall back to Original. Native style override and blinking settings are respected.
- Head movement extends to 135° on either side. Ear tufts use the existing per-entity spring response to vertical motion and rapid head turns.

## Talon grabs

Airborne acquisition reaches farther to the sides and below, without requiring crosshair alignment, while retaining line-of-sight and the normal/friendly target permissions. Wing and arm movement stays independent of talon acquisition. The legs extend forward during the attempt, then sweep back beneath the body after a catch. The target lies horizontally, face down, with its upper back held between the talons. Acquisition, cancellation, catch and landing blend over five ticks with eased motion; target placement follows the animated feet. Landing returns to the ordinary arm hold.

This changes the rendering and acquisition range of the existing grab. Native hold physics, escape QTE, release and friendly-mode safety remain in their existing systems. NPC aerial grabs use an existing combat target and have a four-second acquisition cooldown.

## Aviator Outfit

`changed_synergy:eagle_owl_aviator_outfit` uses Changed's apparel inventory, or its genuine Curios-backed slots when Curios is installed. Adults can equip it as inner Body clothing or Full Body outer clothing. It reserves conflicting face/leg clothing, and cannot be equipped twice.

The model follows native head, torso and limb poses. Tail and taur forms retain their own lower body; legless Milk Puddings receive a fitted upper outfit and goggles. Juveniles wear only goggles. Right-click the equipped garment to lower or raise them, using the native equip sound and synchronized clothing-style state. Lowering them restores an Eagle Owl's color vision without disabling night vision. The recipe uses an amethyst shard.

Natural, chunk-generated and structure-spawned owls have one configurable outfit roll, defaulting to 25% (`EagleOwlOutfitSpawnChance`). Existing gear, saved choices, spawn eggs and command spawns are preserved.

## Bird sleeping perches

`changed_synergy:eagle_owl_perch_stand` occupies two vertically adjacent blocks; `changed_synergy:eagle_owl_perch_rail` occupies two horizontally adjacent blocks. Both have four facing directions, paired placement/removal and one item drop. They are available in the functional creative tab. They use native sleep and wake state for Crow/Owl players and NPCs, including wrapped players. The stand shares one sleeping place across its halves; the rail has two independent, slightly inset sleeping places with 1.25 blocks between their centers. NPC sleep is limited to natural-camp residents using beds inside their own structure, or player-camp members using bound beds. Unassigned wild birds do not search for nearby perches. Eagle Owls sleep by day and wake at night; Crows retain nighttime sleep. Combat, following and orders interrupt camp sleep. Sleeping uses the supplied folded pose; Synergy eye overlays are hidden to expose the updated closed-eye textures.

Airborne arm releases use the carrier's current position and clear old interpolation, then block new grabs for 20 ticks. This applies to normal and friendly holds, including other airborne forms; native suits and control transfer are unchanged.

Perches can be bound as player-camp beds. A rail registers two distinct positions, a stand one. Natural-camp discovery uses loaded section palettes and prefers eligible free perches before ordinary beds. Non-birds do not reserve them. Sleep switches a first-person player to third-person rear view, preserves an existing third-person view, and restores the original camera on wake or disconnect.

Rejected player Owl grab requests are reconciled after the native server handler: release the predicted hold and restore the target's authoritative position. A moving or invalid target cannot remain visually attached only on the client.

Human-model animation adapters retain the Owl's native head rotation instead of replacing its yaw, pitch or roll. Their body translations still apply. This prevents human animation offsets from exceeding the Owl's neck range or turning yaw into a persistent sideways tilt; native look, sleeping and friendly-hold animations retain control. The model normalizes look yaw before clamping to ±135 degrees. No ParCool action integration or runtime dependency is added.

栖木睡眠的落点与可见横梁、碰撞面顶部一致，避免实体开始睡眠时陷入碰撞体后向下滑落。名字框采用站立身高并保留文字高度间距，保持在折翼睡姿的头部和耳羽之上。
