# Local development dependencies

Place these development jars in this directory:

- `changed-0.15.7.jar`
- `changedaddon-2.9.2c-compat.jar`

Changed is required. The compatibility build of Changed Addon Plus is needed to compile its optional integration and is omitted at runtime when Gradle is invoked with `-PwithoutAddon`.
