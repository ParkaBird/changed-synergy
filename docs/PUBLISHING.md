# Publishing notes

Use the same version, supported dependency table and tested release JAR on GitHub, Modrinth and CurseForge. Website descriptions can be edited independently; preserve the author's latest platform text when refreshing repository templates.

## Project identity

- Name: Changed: Synergy
- Slug: `changed-synergy`
- Author: ParkaBird
- License: GPL-3.0-or-later
- Environment: client and server
- Loader: Forge
- Game version: Minecraft 1.20.1
- Source: <https://github.com/ParkaBird/changed-synergy>
- Issues: <https://github.com/ParkaBird/changed-synergy/issues>

## Description and guides

The [platform description](PLATFORM_DESCRIPTION.md) covers current gameplay and links to [player guides](README.md). The CurseForge and Modrinth copies use the same repository text; online pages may contain newer author edits. Check before replacing them.

Suggested short description: Friendships, communities and camps for Changed, with native latex origins and Instinct-mode roleplay through TrueTransfur.

Chinese short description: 让 Changed 胶兽成为有名字、有记忆的邻居，结交朋友、建立羁绊、加入社区，或经营自己的营地。

## Dependency declarations

- Required: Changed 0.15.7.
- Optional: TrueTransfur 2.x; needed for native origins, permanent dialogue rituals and Instinct features. TrueTransfur 1.0.1 compatibility is no longer maintained or tested.
- Required with the supported TrueTransfur integration: Mixin Args 1.0 or newer.
- Optional: Changed Addon Plus 2.9.2c and Curios API 5.14.1 for Forge.
- Other integrations and tested versions: [compatibility](../COMPATIBILITY.md).

Loader acceptance ranges in `mods.toml` can be broader than the versions maintained here; acceptance does not establish support for every version in a range. Minecraft and Forge ranges remain defined by that metadata.

## AI assistance disclosure

Use the current [AI assistance disclosure](../AI_DISCLOSURE.md). A short form for platform fields is:

> AI-assisted tools support parts of the development workflow. ParkaBird directs the project and reviews, edits, integrates and tests the resulting work.

Third-party credits and licensing remain in [the notices](../THIRD_PARTY_NOTICES.md).

## Current release

- Version: `1.1.5-Beta.2-hotfix.1`.
- Release type: Beta / GitHub prerelease.
- Display title: `Changed: Synergy 1.1.5-Beta.2-hotfix.1`.
- Release notes: [Beta.2-hotfix.1](CHANGELOG-1.1.5-Beta.2-hotfix.1.md), describing player-visible changes since Beta.2.
- Main artifact: `ChangedSynergy-m1.20.1-v1.1.5-Beta.2-hotfix.1-all.jar`.
- Attach checksums for the exact uploaded artifact. A source JAR may be attached separately; it is not the playable mod.

## Gallery and attribution

Choose images that show the current build, give them useful alt text and retain appropriate creator credits. Check licenses and permissions for contributed or upstream material. Avoid screenshots containing private paths, access tokens or other unintended personal information.

## Release procedure

1. Complete the [release checklist](../RELEASE_CHECKLIST.md), distinguishing automated results from actual gameplay checks.
2. Build the candidate once and record its commit, version and SHA-256.
3. Tag the tested code and create the matching GitHub release, marking Beta builds as prereleases.
4. Verify the uploaded JAR checksum, then upload the same artifact to CurseForge and Modrinth with matching dependencies and release status.
5. Preserve author edits to platform descriptions and include current guide links.

Documentation-only corrections can be committed after the code tag without rebuilding or replacing the tested JAR. Correct a factual error in release text explicitly; do not move a published tag or replace an attachment merely to update documentation. Keep historic release records scoped to their original versions.
