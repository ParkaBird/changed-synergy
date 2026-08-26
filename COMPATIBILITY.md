# Compatibility

## Supported baseline

| Software | Supported version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x |
| Changed | 0.15.7 |
| Changed Addon | 2.9.2c |
| Curios API | 5.14.1 for Minecraft 1.20.1 |

Changed is required. The other listed integrations are optional unless a server pack chooses to require them.

## Multiplayer

Synergy runs on both client and server. Every connecting player must use the same Synergy build because its network protocol is exact. Server game rules and common configuration control gameplay behavior. Client configuration controls local presentation.

## Optional integrations

- Changed Addon adds diet, petting, grab and other compatibility hooks.
- Curios adds only the slot types supplied by the installed Curios setup.
- TACZ and Superb Warfare gunshots can alert creatures. These integrations use runtime detection.
- Forge-compatible config menu providers can open Synergy's native configuration screen.

## Unsupported combinations

- Changed versions other than 0.15.7 are not covered by this release.
- Changed Addon versions outside the 2.9.2 line are not covered.
- Fabric, NeoForge and Minecraft versions other than 1.20.1 are not supported.
- Installing two Synergy JARs causes a duplicate mod ID error.
