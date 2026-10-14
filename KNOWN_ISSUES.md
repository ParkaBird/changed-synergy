# Known issues and release limits

- This release has a large gameplay-AI and mixin surface. Back up worlds before updating.
- Compatibility is tied closely to Changed 0.15.7 and Changed Addon Plus 2.9.2c. A small upstream update can still break a targeted mixin.
- Large modpacks may change pathfinding, spawn rules, UI layout or combat events in ways Synergy cannot predict.
- Some creature features depend on the species and abilities exposed by Changed. Not every creature supports grabbing, riding, hypnosis, social interaction or work roles.
- English and Simplified Chinese are the maintained languages. Missing translations fall back to English.
- Regression fixtures cover key relationship, camp, Instinct, settings and client paths, but cannot cover every modpack or world layout. Include a reproducible case and relevant logs when reporting an interaction with another mod.
