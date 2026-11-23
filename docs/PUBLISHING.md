# Publishing notes

Use the same version number, dependency table and release JAR on GitHub, Modrinth and CurseForge.

## Project identity

- Name: Changed: Synergy
- Suggested slug: `changed-synergy`
- Author: ParkaBird
- License: GPL-3.0-or-later
- Environment: client and server
- Loader: Forge
- Game version: Minecraft 1.20.1
- Source: `https://github.com/ParkaBird/changed-synergy`
- Issues: `https://github.com/ParkaBird/changed-synergy/issues`

## Short description

Friendships, factions and camps for Changed, with native latex origins and Instinct-mode roleplay through TrueTransfur.

## Project introduction

Use [the platform description](PLATFORM_DESCRIPTION.md) for current gameplay, requirements and links to the [player guides](README.md). It can be copied to CurseForge and Modrinth. Synergy 1.1.4 is paired with TrueTransfur 2.0.0; the 1.0.1 integration remains supported with fewer Instinct features.

## Chinese short description

让 Changed 胶兽成为有名字、有记忆的社交 NPC，加入朋友、羁绊、交涉、阵营声望、台词、职能与环境互动。

## Dependency declarations

- Required: Changed 0.15.7
- Optional: TrueTransfur 2.0.0, with backward compatibility for 1.0.1
- Required with TrueTransfur 2.0.0: Mixin Args 1.0 or newer
- Optional: Changed Addon Plus 2.9.2c
- Other supported integrations: see [COMPATIBILITY.md](../COMPATIBILITY.md)

Forge itself and Minecraft should use the supported ranges in `mods.toml`.

## AI disclosure

Use this text where a platform requests an AI disclosure:

> Generative AI was used as an assistive tool during development of parts of the English and Chinese text and code. ParkaBird reviewed, edited, integrated and tested the resulting work. Artwork, textures, models, screenshots and promotional images are not AI-generated.

## Current release

- Version: `1.1.4`
- Release type: Release
- Display title: `Changed: Synergy 1.1.4 for Forge 1.20.1`
- Upload only the release JAR, not the sources JAR, as the main downloadable mod.
- Attach the sources JAR to the GitHub release if desired.
- Use [the 1.1.4 changelog](CHANGELOG-1.1.4.md) for release notes.
- Coordinate public release with TrueTransfur 2.0.0. Source and player guides can be updated ahead of the simultaneous launch.

## Gallery and description

Use player-made screenshots and cover art. Add alt text that states what each image shows. Do not upload debug screenshots containing personal paths, unrelated usernames, coordinates that should remain private, or a visible access token.

The README can be used as the long description. Remove GitHub-only contribution details if a platform page becomes too long, but keep installation, dependencies, release status, AI disclosure and credits.

## Release order

1. Push the tagged source to GitHub and create the GitHub release.
2. Download the release attachment once and compare its SHA-256 checksum with the local candidate.
3. Update the existing Modrinth and CurseForge projects with matching metadata and the guide links.
4. Upload the exact same JAR to both platforms.
5. Add the final Modrinth and CurseForge URLs to `mods.toml` and the README in the next patch release if the first upload has already been signed and tagged.

Modrinth may hold a new project for review. CurseForge also moderates new submissions. Do not rename or rebuild the JAR while one platform is reviewing it.
