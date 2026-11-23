# Compatibility

## Supported baseline

| Software | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x |
| Changed | 0.15.7 |
| Changed Addon Plus | 2.9.2c |
| Changed Extras | 1.1.8b |
| Changed Vanilla | 1.0.1 |
| Domestication Innovation | 1.7.1 |
| TrueTransfur | 2.x |
| Changed: Survive Protocol | 1.2.0 |
| Farmer's Delight | 1.3.4 |

Changed is required. Changed Addon Plus is optional unless a server pack chooses to require it.

TrueTransfur 2.x is the supported pairing for Synergy 1.1.5. Compatibility with TrueTransfur 1.0.1 is no longer maintained or tested. See the [player guides](docs/README.md) for installation and gameplay.

## Multiplayer

Synergy runs on both client and server. Every connecting player must use the same Synergy build to use matching features and menus. Server game rules and common configuration control gameplay behavior. Client configuration controls local presentation.

## Optional integrations

- 3D Skin Layers 1.11.4 renders the retained player skin layers on Latex Human,
  Partial Dark Latex Wolf and Addon Plus's Partial Snow Leopard. Both classic
  and slim skins are supported; partial sleeves and thighs retain their original
  latex boundaries. The integration is client-only and follows the skin-layer
  mod's visibility and distance settings.

- Changed: Survive Protocol retains its ordinary infection and settled-form rules.
  Authorized Synergy takeovers use native progress and temporary suits, so infection
  processing does not erase their release dialogue. Confirmed origin re-selection
  replaces the previous settled strain; cancelling the archive preserves it.
  Nether releases search below the bedrock ceiling. The supplied 1.2.0 JAR was
  tested with TT 1.0.1, TT 2.0.0, and TT 2.0.0 with Addon Plus.

- TrueTransfur 2.x supports native origins and uses a separate Instinct body, preserving
  the real player's identity and social interactions. TrueTransfur
  2.0.0 requires Mixin Args on both sides. See
  [the integration guide](docs/TRUETRANSFUR_INSTINCT_COMPAT.md) for its scope.
- Farmer's Delight crops can be harvested and replanted by camp farmers,
  including vine tomatoes and aquatic rice. The mod remains optional.
- Changed Addon Plus adds diet, petting, grab and other compatibility hooks.
  Its golden orange works as an enhanced orange gift and negotiation offering.
  Companion wrapping and rescue use Changed's native ability and do not require Addon.
- Changed Additions creatures participate in Synergy's core identity, social,
  relationship and companion systems. Its golden orange works as the same
  enhanced orange gift and negotiation offering. The mod is optional.
- Changed Extras 1.1.8b may keep its smart AI for unrelated wild
  creatures. For friends, companions, player-camp members, TrueTransfur Instinct bodies, takeover carriers and
  pursuit members, the creature follows Synergy's relationship and companion behavior. Changed Extras target
  scans also honor Synergy relationships, truces and creature alliances. Camp members keep their jobs and assigned beds when their owner sleeps; idle pets outside camps retain Extras' normal cuddling. Artist remains outside Synergy social systems, camps and native origins.
- Changed Vanilla 1.0.1 creatures participate in Synergy's identity, social,
  relationship, community and companion systems. Its dedicated zombie and
  skeleton conversions take priority over Synergy's mindless-mob absorption
  fallback. Near a human respected by their faction, territory members leave
  Changed Vanilla's convertible cats, chickens, cows, foxes, ocelots, pigs and
  sheep alone. Changed Vanilla remains optional and is not bundled.
- Domestication Innovation 1.7.1 recognizes Synergy-owned Changed companions
  through its normal pet checks. Their existing Synergy owner is used by
  collars, pet beds, recall and owner-alliance checks; strangers are not
  treated as tame. Domestication Innovation remains optional and is not bundled.
- TACZ and Superb Warfare gunshots can alert creatures. These mods remain optional.
- Better Combat 1.9.0 no longer treats the hidden creature providing a
  friendly wrapping as an attack target. This prevents its combat targeting
  from intercepting block mining while the player is wrapped.
- Forge-compatible config menu providers can open Synergy's native configuration screen.
- Furmutage entities are intentionally isolated from Synergy's identity,
  relationship, community, AI and takeover systems. Compatibility is limited
  to safe coexistence; Furmutage remains responsible for their behaviour.
## Unsupported combinations

- Changed versions other than 0.15.7 are not covered by this release.
- Changed Addon Plus versions outside the 2.9.2 line are not covered.
- Other Changed Extras releases have not been verified for this release.
- Changed Vanilla versions outside the 1.0.x line are not covered.
- Furmutage 0.1 is coexistence-only; Synergy does not manage its gameplay systems.
- Fabric, NeoForge and Minecraft versions other than 1.20.1 are not supported.
- Installing two Synergy JARs causes a duplicate mod ID error.
