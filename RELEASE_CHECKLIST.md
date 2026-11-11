# Release checklist

Copy this checklist for each release and record the build, environment and results. Unchecked items are not claims that testing has passed.

## Automated checks

- [ ] `python tools/validate_release.py`
- [ ] `./gradlew clean build`
- [ ] Release JAR contains `mods.toml`, mixin configs, access transformer and `LICENSE_changed_synergy.txt`.
- [ ] Release JAR contains no bundled third-party classes, dependency JARs or unintended private paths. Metadata and contributed-asset credits retain their correct authors.
- [ ] SHA-256 checksums are generated for the release and source JARs.

## Manual game checks

- [ ] Client reaches the title screen with Changed only.
- [ ] Client reaches the title screen with Changed Addon Plus 2.9.2c.
- [ ] A dedicated server starts and a matching client can join.
- [ ] An existing test world loads after making a backup.
- [ ] Human and transformed relationship wheels open, switch and close correctly.
- [ ] Friendship, voluntary bond creation, release and death cleanup work after relogging.
- [ ] Assimilation, absorption and fusion negotiation can succeed, fail, cool down and release the correct individual.
- [ ] Grab and hypnosis QTEs accept the displayed keys and restore controls afterward.
- [ ] Companion inventory and Changed clothing slots retain items after relogging.
- [ ] Territory text, reputation color and pure white vision work above and below ground.
- [ ] Creature gathering, fishing, hunting, outposts and guards run without runaway spawning or chunk loading.
- [ ] English, Simplified Chinese and Russian conversation layouts fit at common GUI scales.
- [ ] A world profiler check shows no repeating error spam or extreme creature count growth.

## Publishing

- [ ] Version, changelog and dependency ranges match on GitHub, Modrinth and CurseForge.
- [ ] AI assistance disclosure is present on every project page that requires it.
- [ ] Gallery images show the intended build, have useful alt text, and carry appropriate permissions and creator credits.
- [ ] Changed is marked required. Changed Addon Plus is marked optional.
- [ ] Git tag and uploaded JAR version match.
- [ ] Current guides match public configuration keys, gamerules, controls, structure resources and supported dependency versions.
- [ ] The published file checksum matches the local release candidate.
