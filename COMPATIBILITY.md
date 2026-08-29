# Compatibility

## Supported baseline

| Software | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x |
| Changed | 0.15.7 |
| Changed Addon Plus | 2.9.2c |

Changed is required. Changed Addon Plus is optional unless a server pack chooses to require it.

## Multiplayer

Synergy runs on both client and server. Every connecting player must use the same Synergy build because its network protocol is exact. Server game rules and common configuration control gameplay behavior. Client configuration controls local presentation.

## Optional integrations

- Changed Addon Plus adds diet, petting, grab and other compatibility hooks.
- Changed Additions creatures participate in Synergy's core identity, social,
  relationship and companion systems. Its golden orange is also recognised as
  an orange gift. This is a lightweight runtime integration rather than a
  version-pinned API dependency.
- TACZ and Superb Warfare gunshots can alert creatures. These integrations use runtime detection.
- Forge-compatible config menu providers can open Synergy's native configuration screen.

## Unsupported combinations

- Changed versions other than 0.15.7 are not covered by this release.
- Changed Addon Plus versions outside the 2.9.2 line are not covered.
- Fabric, NeoForge and Minecraft versions other than 1.20.1 are not supported.
- Installing two Synergy JARs causes a duplicate mod ID error.
