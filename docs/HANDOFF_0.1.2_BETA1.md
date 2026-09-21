# Changed: Synergy 0.1.2-beta.1 handoff

Last updated: 2026-09-16

This handoff describes the current tree after the 0.1.2-beta.1 work. The
working tree contains the accumulated, authorized development changes from the
earlier release cycle. Do not reset or clean the tree to make it look like a
fresh checkout; unrelated work is part of this build.

## Release and build

- Minecraft: 1.20.1
- Forge: 47.4.21 (the project accepts the 47.4.x line)
- Changed: 0.15.7
- Build version: `0.1.2-beta.1`
- Final release JAR:
  `build/libs/ChangedSynergy-m1.20.1-v0.1.2-beta.1-all.jar`
- SHA-256:
  `176147AE1F59BE44960E64299B3FCB939BC6DE6380E0A2A4233678042E59AD2C`

The release JAR includes Synergy only. Changed Addon Plus, Changed Extras,
Changed Vanilla, Domestication Innovation, and Citadel remain optional runtime
dependencies.

## Validation already completed

The following commands were run with the bundled Java 17 runtime and completed
successfully:

```text
gradlew.bat clean build --offline --no-daemon
gradlew.bat runServer --offline --no-daemon
gradlew.bat runServer -PwithDomesticationInnovation --offline --no-daemon
```

Both server runs reached the normal `DedicatedServer: Done (...)` state. The
Domestication Innovation run loaded Citadel and DI and applied
`DomesticationInnovationTameableUtilsMixin` without an injection error. The
no-DI run confirms that the optional `@Pseudo` mixin does not prevent startup.
A DI-enabled client run also reached the main menu and initialized resources.

The logs still contain known warnings from optional integrations that are not
installed in the test profile (Changed Extras/Better Combat), vanilla model
lookups, and the usual offline authentication/version-check messages. They did
not produce a fatal error, an `InjectionError`, or a Synergy startup failure.

## User-facing systems in this build

### Social interaction and dialogue

- H-key and radial-wheel patting use one continuous speed value. Mouse-wheel
  deltas change it smoothly between 0.5x and 2.0x, with no preset list and no
  bottom speed hint. Phase and remaining animation time are preserved while
  changing speed.
- Pat speed bands can emit short, probability-based reactions. The same service
  handles keyboard and radial patting.
- Before telepathy is unlocked, dialogue falls back to species profiles. The
  current groups include canine, feline, aquatic, draconic, avian, herbivore,
  small mammal, insect, reptile, hybrid, and humanoid. Reptile lines include
  chirp/tweet/hiss-style communication with mood-specific variants.
- Relationship-roster pages support left-click/right-click previous/next
  navigation. The “long time no see” greeting is suppressed while a creature is
  following and requires a real multi-minute absence.
- `CreatureSocialProfile` supports an aggressive-but-social profile. Tagged
  creatures can still be reasoned with, but may refuse, hesitate, reduce their
  damage, or be briefly stunned by an appeal.

### Takeover, transfur, and safety

- The complete takeover state machine remains server authoritative, including
  observation, temporary control, escape, sleep, release, recovery, and
  cross-entity cleanup.
- Exoskeleton takeover keeps the native movement style and speed.
- The default-off `Transfur After Sleep` option merges the player and carrier;
  the carrier is removed, the player uses the carrier's form and eye appearance,
  and the player's temporary name follows the carrier until human restoration.
- Overlapping grabs and competing attackers are disengaged when a takeover
  commits. The takeover carrier is no longer treated as an ordinary enemy by
  nearby latex creatures.
- Randomized assimilation-versus-absorption selection is available through its
  common configuration option.
- White Latex hazard immunity, pillar contact handling, territory-only merge and
  release surfaces, and the rebuilt white-liquid particle presentation are
  active. Pure White visual overlays can be disabled in client configuration.

### Bonds, equipment, and companions

- Bonded dark latex companions can be revived through a broken mask, four dark
  latex crystal fragments at a crafting table, a repaired mask, and a rebuilding
  transfur. Broken and repaired masks have glowing outlines and stable generic
  item names while retaining the companion in their tooltip data.
- Protective release requests can require several release selections after
  repeated dangerous rescues; the radial menu closes after each refusal.
- Close friends and bonded companions accept compatible armor and melee weapons
  (swords, axes, tridents, and valid modded main-hand weapons).
- The original Dark Latex Wolf Pup wheel and functions remain available with
  Synergy's radial presentation.

### Communities, outposts, and ecology

- Provisioners can claim compatible Changed ruins and ordinary non-facility
  structures as community outposts. A dedicated bee-hive cache blueprint is
  used for latex-bee communities; the hive structure sits one block lower so
  bees can cross its threshold.
- White/dark territory generation restores the missing regional structure and
  liquid-pool cases, and white territory members now use the complete spawn set.
- Indoor, ruin, and bee-hive caches use real orange piles; the old invisible
  orange display frames are removed. Orange leaves regrow after harvesting.
- Pure White adults can adapt into male White Latex Wolves outside the White
  Latex Forest and return to the Pure White form inside it. Adapted wolves keep
  Pure White faction allegiance and are marked accordingly in relationships.
- Badlands provisioner and approach behavior, lab-table pathfinding, and
  Changed Vanilla's respected-human animal protection are active.

### Trading and village relations

- Provisioner exchanges use oranges, berries, and diet-appropriate fish as
  player-facing currency. Material offers are independent of cache stock;
  buyback offers are backed by the community's stored supply, and trade returns
  are delivered back to the outpost. Exhausted offers persist across reopening
  the screen.
- The trade screen follows the familiar villager flow: select an offer, place
  inputs in the two payment slots, take the result, and receive normal disabled
  and stock feedback. The empty “Select an offer” instruction is removed.
- `PeacefulVillageRelations` is a common config option, enabled by default. It
  blocks hostile targeting and direct damage in both directions between Synergy
  creatures and villagers, wandering traders, iron golems, and entities added
  through the `changed_synergy:village_civilians` and
  `changed_synergy:village_protectors` entity tags.

### Domestication Innovation compatibility

Domestication Innovation 1.7.1 is detected at runtime and is not bundled.
Synergy-owned friends and bonded companions are exposed through DI's normal
`TameableUtils` checks, so collars, pet beds, recall, owner alliance, and
same-owner behavior can recognize them. Unowned or merely friendly wild latex
creatures are not silently converted into pets. The bridge is isolated in
`compat/DomesticationInnovationCompat.java` and
`mixin/DomesticationInnovationTameableUtilsMixin.java`.

## Important entry points

- Pat input/animation: `client/PatInputClientEvents.java`,
  `network/PatAnimationSpeedPacket.java`, `client/PatAnimationClientState.java`,
  `ai/PatAnimationService.java`, `dialogue/NpcDialogue.java`.
- Species voice grouping: `ai/CreatureIdentity.vocalizationProfile` and the
  `dialogue.changed_synergy.vocalization.*` language keys.
- Outposts and orange piles: `ai/CreatureSettlementService.java`,
  `ai/CreatureCommunityData.java`, `world/OrangeLeafRegrowthData.java`, and
  `data/changed_synergy/settlement_blueprints/`.
- Village peace: `ai/LatexCreatureCombatRules.java`,
  `event/LatexSocialEvents.java`, `mixin/SocialTargetGuardMixin.java`, and the
  two village entity tags.
- DI bridge: `compat/DomesticationInnovationCompat.java` and
  `mixin/DomesticationInnovationTameableUtilsMixin.java`.

## Configuration and tags

- Common key: `PeacefulVillageRelations` (default `true`), shown under
  Relationships in the native config screen.
- Other current user-facing switches include `RandomizeCreatureTransfurMethod`,
  `PureWhiteWolfAdaptation`, `LatexBeeHiveOutposts`, `ProvisionerTrading`,
  `TransfurAfterSleep`, `ProtectiveReleaseRequests`, and the performance
  diagnostics switches.
- Add compatible civilians to
  `data/changed_synergy/tags/entity_types/village_civilians.json` and protectors
  to `village_protectors.json`. Optional entries use `{ "id": "...",
  "required": false }` so absent addons do not block loading.

## Safe next checks for Antigravity

Keep follow-up work lightweight and reversible while the main usage window is
resetting:

1. Smoke-test the pat wheel with small, fractional scroll deltas and confirm
   that the animation does not jump at a speed-band boundary.
2. In a clean world, verify one indoor cache, one ruin cache, and one latex-bee
   hive cache, including orange-pile restocking and persistence after reload.
3. With DI 1.7.1 installed, test a bonded companion with a collar, a pet bed,
   recall, and owner-alliance behavior; confirm a wild stranger is not treated
   as tame.
4. Toggle `PeacefulVillageRelations` off and on in a test world, then check
   both a Changed creature attacking a villager and a golem targeting a Changed
   creature.
5. Check English and Simplified Chinese pat-speed and pre-telepathy species
   lines in the social wheel.

Do not change the release version or remove the optional dependency metadata
without updating the build and this handoff. Re-run the offline build after any
code or resource edit.
