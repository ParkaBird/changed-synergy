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

Turns Changed creatures into named social NPCs with memory, friendships, voluntary bonds, negotiation, faction reputation, dialogue, roles and world interactions.

## Project introduction

Changed: Synergy turns fierce latex creatures into neighbors you can get along with. Oranges or a friendly pat often work better than fists and blades. Transfur is no longer always the end but a reversible and harmless form of interaction. Just be careful: some may be a little too EXCITED to see you.

## Chinese short description

让 Changed 胶兽成为有名字、有记忆的社交 NPC，加入朋友、羁绊、交涉、阵营声望、台词、职能与环境互动。

## Dependency declarations

- Required: Changed 0.15.7
- Optional: Changed Addon 2.9.2c
- Optional: Curios API 5.14.1 for Minecraft 1.20.1

Forge itself and Minecraft should use the supported ranges in `mods.toml`.

## AI disclosure

Use this text where a platform requests an AI disclosure:

> Generative AI assisted parts of the code and bilingual text. ParkaBird reviewed, edited, integrated and tested the resulting work. Artwork, textures, models, screenshots and promotional images are not AI-generated.

## First file

- Version: `0.1.0-beta.1+mc1.20.1-changed0.15.7`
- Release type: Beta
- Display title: `Changed: Synergy 0.1.0 Beta 1 for Forge 1.20.1`
- Upload only the release JAR, not the sources JAR, as the main downloadable mod.
- Attach the sources JAR to the GitHub release if desired.
- Copy the relevant section of `CHANGELOG.md` into the file changelog.

## Gallery and description

Use player-made screenshots and cover art. Add alt text that states what each image shows. Do not upload debug screenshots containing personal paths, unrelated usernames, coordinates that should remain private, or a visible access token.

The README can be used as the long description. Remove GitHub-only contribution details if a platform page becomes too long, but keep installation, dependencies, beta status, AI disclosure and credits.

## Release order

1. Push the tagged source to GitHub and create the GitHub release.
2. Download the release attachment once and compare its SHA-256 checksum with the local candidate.
3. Create the Modrinth and CurseForge projects with the same metadata.
4. Upload the exact same JAR to both platforms.
5. Add the final Modrinth and CurseForge URLs to `mods.toml` and the README in the next patch release if the first upload has already been signed and tagged.

Modrinth may hold a new project for review. CurseForge also moderates new submissions. Do not rename or rebuild the JAR while one platform is reviewing it.
