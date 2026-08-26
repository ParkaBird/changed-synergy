# Changed: Synergy

Changed: Synergy is a Forge addon for Minecraft 1.20.1 and Changed 0.15.7. It turns Changed creatures into named individuals that remember the player, form friendships, develop bonds, react through dialogue and emotes, and take part in the world around them.

The mod is in beta. Existing worlds should be backed up before installation or an update.

## Main features

- Persistent names, personalities, memories, friendships and one voluntary bond.
- Social and relationship wheels with following, combat, inventory and interaction controls.
- Contextual dialogue in English and Simplified Chinese, with separate display modes for each language.
- Faction reputation, territory presentation and species-aware reactions.
- Negotiation after involuntary assimilation or consumption, where the creature's motive and personality affect the available approach.
- Improved pursuit, swimming, grabbing, hypnosis QTEs and firearm reactions.
- Creature roles, gathering, fishing, hunting, outposts, guards, gifts and small environmental interactions.
- Pure white hive support, disguises, friendly wrapping, emergency rescue and rideable supported dual-body creatures.
- Optional integrations for Changed Addon, Curios API, TACZ, Superb Warfare and Forge configuration menu providers.

## Requirements

| Component | Version | Required |
| --- | --- | --- |
| Minecraft | 1.20.1 | Yes |
| Forge | 47.4.0 to 47.x | Yes |
| Changed | 0.15.7 | Yes |
| Changed Addon | 2.9.2c | No |
| Curios API | 5.14.1 for 1.20.1 | No |

Later Changed or Changed Addon releases are not assumed compatible. Synergy uses targeted mixins and direct integration points, so version ranges are intentionally narrow.

## Installation

1. Install Forge 47.4.x for Minecraft 1.20.1.
2. Put Changed 0.15.7 and the Synergy JAR in the instance's `mods` folder.
3. Add Changed Addon 2.9.2c and Curios only if you want their integrations.
4. Start the game and check the Forge Mods screen for dependency errors.

Do not install two Synergy JARs at the same time.

## Configuration

Synergy has a Forge configuration screen. Open it from the Mods screen or through a menu mod that exposes Forge config screens. Client and visual settings apply locally. Gameplay and AI settings are controlled by the server in multiplayer.

Major systems also have game rules. See [docs/GAMERULES.md](docs/GAMERULES.md) for names and exact behavior.

## Building from source

Install Java 17, then run:

```text
./gradlew build
```

On Windows, use `gradlew.bat build`. Dependencies are downloaded from Forge Maven and CurseMaven. The release and source JARs are written to `build/libs`.

`-PwithoutAddon` removes Changed Addon from the development runtime. Its API remains a compile-time dependency because the optional compatibility layer must compile.

## Reporting problems

Use the GitHub issue templates and include the crash report or `latest.log`, the exact Synergy, Changed and Forge versions, and whether Changed Addon or Curios is installed. Test without unrelated mods when possible.

Known release limits are listed in [KNOWN_ISSUES.md](KNOWN_ISSUES.md). Compatibility details are in [COMPATIBILITY.md](COMPATIBILITY.md).

## Credits, AI assistance and license

Changed: Synergy is developed by ParkaBird. It is an unofficial addon and is not affiliated with the creator of Changed or Minecraft.

Generative AI assisted parts of the code and bilingual text. ParkaBird reviewed, edited, integrated and tested the resulting work. Artwork, textures, models, screenshots and promotional images are not AI-generated. See [AI_DISCLOSURE.md](AI_DISCLOSURE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The project is licensed under GPL-3.0-or-later. See [LICENSE.txt](LICENSE.txt).
