# Building Changed: Synergy

This source snapshot targets Minecraft 1.20.1, Forge 47.4.x, and Changed
0.15.7. Java 17 is required.

## Basic build

From the project root, run:

```text
gradlew.bat clean build
```

The distributable JAR is written to `build/libs/`. The `-all.jar` file is the
release artifact; the `-sources.jar` file contains the generated source JAR.

To build and install into a local test profile, close Minecraft and run:

```powershell
./build_and_copy.ps1 -ModsDirectory '<profile>/mods' -JavaHome '<Java 17 directory>'
```

The build wrapper uses `tools/install_test_build.ps1` for checksum verification,
atomic replacement and removal of obsolete Synergy JARs. It skips backups; when
installing an already built JAR, invoke the installer directly to keep a backup:

```powershell
./tools/install_test_build.ps1 -JarPath '<release-all.jar>' -ModsDirectory '<profile>/mods'
```

Run `./tools/tests/Test-InstallBuild.ps1` in PowerShell 7 to check installation in
an isolated temporary directory without building the mod or changing a game profile.

Use `--offline` when the Gradle and Minecraft dependencies are already cached:

```text
gradlew.bat clean build --offline --no-daemon
```

## Optional Domestication Innovation test run

Domestication Innovation 1.7.1 and Citadel are optional and are not bundled in
the release. If their CurseMaven artifacts are available locally, the
development client/server can load them with:

```text
gradlew.bat runClient -PwithDomesticationInnovation --no-daemon
gradlew.bat runServer -PwithDomesticationInnovation --no-daemon
```

## Runtime requirements

Install Changed 0.15.7 alongside the Synergy JAR. Optional integrations listed
in `COMPATIBILITY.md` can be added separately. The server and every connecting
client must use the same Synergy version.

## Source-release hygiene

The source release intentionally excludes Gradle caches, generated build and
run directories, local logs, crash reports, decompiled third-party files,
third-party mod JARs, screenshots, and private test data. Do not add those
directories to a public archive.
