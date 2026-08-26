# Release checklist

## Automated checks

- [x] `python tools/validate_release.py`
- [x] `./gradlew clean build`
- [x] Release JAR contains `mods.toml`, mixin configs, access transformer and `LICENSE_changed_synergy.txt`.
- [x] Release JAR contains no third-party classes, dependency JARs, local paths or non-ParkaBird author names.
- [x] SHA-256 checksums are generated for the release and source JARs.

## Manual game checks

- [ ] Client reaches the title screen with Changed only.
- [ ] Client reaches the title screen with Changed Addon 2.9.2c and Curios 5.14.1.
- [ ] A dedicated server starts and a matching client can join.
- [ ] An existing test world loads after making a backup.
- [ ] Human and transformed relationship wheels open, switch and close correctly.
- [ ] Friendship, voluntary bond creation, release and death cleanup work after relogging.
- [ ] Assimilation and consumption negotiation can succeed, fail, cool down and release the correct individual.
- [ ] Grab and hypnosis QTEs accept the displayed keys and restore controls afterward.
- [ ] Companion inventory, Changed clothing and optional Curios slots retain items after relogging.
- [ ] Territory text, reputation color and pure white vision work above and below ground.
- [ ] Creature gathering, fishing, hunting, outposts and guards run without runaway spawning or chunk loading.
- [ ] English and Simplified Chinese layouts fit at common GUI scales.
- [ ] A world profiler check shows no repeating error spam or extreme creature count growth.

## Publishing

- [ ] Version, changelog and dependency ranges match on GitHub, Modrinth and CurseForge.
- [ ] AI assistance disclosure is present on every project page that requires it.
- [ ] Gallery images have useful alt text and none are AI-generated.
- [ ] Changed is marked required. Changed Addon and Curios are marked optional.
- [ ] Git tag and uploaded JAR version match.
- [ ] The published file checksum matches the local release candidate.
