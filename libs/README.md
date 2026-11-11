# Development dependencies

The standard Gradle build resolves Changed 0.15.7, Changed Addon Plus and Curios compile dependencies from CurseMaven using the file IDs in `gradle.properties`. Manual copying of mod JARs into this directory is not part of the normal build.

Addon Plus is compile-only with an optional development runtime. Use `-PwithoutAddon` to omit it at runtime, including for Synergy data generation. Curios is a compile dependency; add its supported runtime to an isolated test environment when testing that integration. Third-party JARs are excluded from Git and are not bundled.

See [building](../docs/BUILDING.md) for Java 17, validation and optional runtime setup.
