# Latex Silk Moth — Synergy 1.1.5

Latex Silk Moth (胶液蚕蛾) is a new species owned by Synergy. It keeps the original Latex Moth's behaviour and six extra jump charges, adds Changed's extra-hands ability, and uses the edited four-arm silkmoth model. The original moth is unchanged.

## Trying the species

**Spin Silk / 吐丝:** select it from the ability wheel and hold the ability key for one second to produce four string. Like the Spider Tiger's cobweb ability, it requires more than six hunger points, uses food energy and has a one-second cooldown. A full inventory drops the produced string nearby. Creative mode does not spend food. The icon uses native string with a small plus badge in the lower-right corner.

- Entity: `changed_synergy:latex_silk_moth`
- Player form: `changed_synergy:form_latex_silk_moth`
- Spawn egg: `changed_synergy:latex_silk_moth_spawn_egg`, also available in the Spawn Eggs creative tab.
- Summon: `/summon changed_synergy:latex_silk_moth ~ ~ ~`
- Give an egg: `/give @s changed_synergy:latex_silk_moth_spawn_egg`

The form is available in Synergy's native-origin catalog without enabling third-party species. Starting-origin selection still requires its usual game rule and optional TrueTransfur integration. The disabled-forms list still excludes the form when configured.

Its species dossier includes a Crisscross log-note slip on the left page, with colored remarks from Science, Technology, Humanity and Society. The note turns with the paper and is covered when the archive closes.

Silk Moths belong to the Light faction's forest population and spawn naturally in forest biomes, in groups of one or two. White Latex Forest retains its own population. Their forest affiliation remains stable after moving away.

The Changed Items tab includes the Silk Moth latex syringe, flask and tipped arrow. These use Changed's normal variant-bearing items and extraction/crafting behavior, recolored with the Silk Moth palette rather than separate duplicate items. Syringes, thrown flasks, placed flasks and arrows carry the Silk Moth form.

## Animation and equipment

Walking, swimming, crouching, four-arm actions and tail motion use Changed's existing animation components. Tail motion uses the same unmodified `dragonTail` preset as the original moth and bee, without an extra idle offset. Antenna roots use Changed's `BeeAntennaeInitAnimator`; the two-segment tips add entity-owned spring response in the shafts' local coordinates. Small breathing and wing-idle offsets are additional Java animators.

All four wrist fur cuffs extend one model unit farther up the arms. Their original jagged tips and two-pixel-per-unit texture density are preserved in a separate atlas tile shared by the four cuffs.

Each successful extra jump briefly flaps all four wings, with a slight delay on the lower pair, then returns to the normal wing pose. This listens to the original Changed jump event without changing its six-charge allowance or jump physics. Ground jumps and exhausted inputs do not trigger it. The local client predicts the flap; a server-to-client packet informs other tracking players. Network protocol 49 requires matching Synergy builds on both sides.

The four dedicated flap joints are at the upper ends of the shortest wing attachment stems. Wings lift outward from those points; the original outer wing roots continue to handle Changed's resting fold and the existing gentle idle. Reparenting preserves the original rest geometry and UVs, and flap joints reset to their bind pose every frame.

The renderer includes the normal Changed equipment layers, a second pair of held-item hands, and Changed's existing four-arm bee armour model. The new body and two texture maps are shipped in Synergy; it does not load a `.bbmodel` at runtime.

All four arms blend into Changed's original reach/hold poses over four ticks and blend back after cancellation or release. The controller's key-down state also starts the transition before its first hold tick. Transition state belongs to each rendered entity, so shared model instances do not transfer the pose between creatures.

## Isolated checks

The fixtures are excluded from ordinary release builds:

```text
gradlew.bat -I tools/tests/silk-moth.init.gradle runServer -PwithoutAddon -x downloadAssets --no-daemon
gradlew.bat -I tools/tests/silk-moth.init.gradle runClient -PwithoutAddon -x downloadAssets --no-daemon
```

The tests use `.codex-tmp/silk-moth-server` and `.codex-tmp/silk-moth-client`, separate from normal game profiles. Server checks cover registration, attributes, species tags, extra hands, saved entities, origin filtering and player transfur. Client checks cover the actual model renderer, animation cycle, equipment and screenshots. Motion checks tick Changed's entity-owned springs through turning, walking, jumping, crouching and swimming blends: all three tail joints are compared with the original moth and bee, antenna root yaw with the original bee, and 3,000 repeated renders check for accumulated tail rotation.
