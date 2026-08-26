# Contributing

Bug reports and focused pull requests are welcome.

## Bug reports

Include:

- Minecraft, Forge, Changed and Synergy versions.
- Whether Changed Addon and Curios are installed.
- A crash report or `latest.log` when one exists.
- Steps that reproduce the problem in a small test setup.
- Whether the same issue appears without unrelated mods.

Do not paste a full crash report into an issue body. Attach the file or use a readable log service.

## Code changes

Use Java 17. Run `./gradlew clean build` and `python tools/validate_release.py` before opening a pull request. Keep optional-mod types inside their compatibility packages. Do not bundle third-party JARs or copied repositories.

New mixins need a narrow target, a reason that cannot be handled through a Forge event or public API, and a failure mode that is understandable when an upstream version changes.

Both `en_us.json` and `zh_cn.json` must contain the same keys. Keep physical descriptions species-aware. In Chinese dialogue, `同类` means the same species and `同胞` means the same broader category.

## License

Contributions are accepted under GPL-3.0-or-later, the license of this repository.
