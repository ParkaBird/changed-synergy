# Compatibility

## Supported baseline

| Software | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x |
| Changed | 0.15.7 |
| Changed Addon Plus | 2.9.2c |
| Changed Extras | 1.1.4-beta3b |
| Changed Vanilla | 1.0.1 |
| Domestication Innovation | 1.7.1 |

Changed is required. Changed Addon Plus is optional unless a server pack chooses to require it.

## Multiplayer

Synergy runs on both client and server. Every connecting player must use the same Synergy build because its network protocol is exact. Server game rules and common configuration control gameplay behavior. Client configuration controls local presentation.

## Optional integrations

- Changed Addon Plus adds diet, petting, grab and other compatibility hooks.
  Its golden orange works as an enhanced orange gift and negotiation offering.
- Changed Additions creatures participate in Synergy's core identity, social,
  relationship and companion systems. Its golden orange works as the same
  enhanced orange gift and negotiation offering. This is a lightweight runtime
  integration rather than a version-pinned API dependency.
- Changed Extras 1.1.4-beta3b may keep its smart AI for unrelated wild
  creatures. When a creature becomes a friend, companion, takeover carrier or
  pursuit member, Synergy restores and owns its goals. Changed Extras target
  scans also honor Synergy relationships, truces and creature alliances.
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
- TACZ and Superb Warfare gunshots can alert creatures. These integrations use runtime detection.
- Better Combat 1.9.0 no longer treats the hidden creature providing a
  friendly wrapping as an attack target. This prevents its combat targeting
  from intercepting block mining while the player is wrapped.
- Forge-compatible config menu providers can open Synergy's native configuration screen.
- Furmutage entities are intentionally isolated from Synergy's identity,
  relationship, community, AI and takeover systems. Compatibility is limited
  to safe coexistence; Furmutage remains responsible for their behaviour.
- Changed: Survive Protocol is ordered before Synergy when present. It does not
  register a separate creature entity namespace in 1.2.0, so its ordinary
  Changed creatures retain their native ownership rather than receiving a
  source-based integration that could also exclude base Changed creatures.

## Unsupported combinations

- Changed versions other than 0.15.7 are not covered by this release.
- Changed Addon Plus versions outside the 2.9.2 line are not covered.
- Changed Extras versions outside the 1.1.4 line are not covered.
- Changed Vanilla versions outside the 1.0.x line are not covered.
- Furmutage 0.1 and Changed: Survive Protocol 1.2.0 are coexistence-only;
  Synergy gameplay support for their mechanics is not provided.
- Fabric, NeoForge and Minecraft versions other than 1.20.1 are not supported.
- Installing two Synergy JARs causes a duplicate mod ID error.
