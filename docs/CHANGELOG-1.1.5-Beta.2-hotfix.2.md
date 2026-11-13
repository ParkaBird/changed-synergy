# Changed: Synergy 1.1.5-Beta.2-hotfix.2

Player-visible fixes and quality-of-life improvements since **1.1.5-Beta.2-hotfix.1**.

#### Communities and Story NPCs

- **Reliable Story NPC recovery**: Story NPCs now return reliably to their original home position (or the nearest safe unobstructed spot inside their building) 1 minute after confirmed death or unexpected removal. Previously generated structures also recover missing Story NPC records when their structure-start chunk loads.
- **Built-in camp resident respawn**: Built-in community and camp residents now automatically recover back at their camp home after death or removal (3 minutes for main communities, 5 minutes for smaller camps), preserving their relationships, personal requests, and bound camp outfits without duplicating drops. Built-in residents recruited as companions or assigned to a player outpost cleanly retire from camp respawning.
- **Resident fusion protection**: Protected Story NPCs and built-in camp residents from involuntary fusion that would erase them.
- **Clearer resident language-barrier prompts**: Residents who cannot yet be understood no longer glance toward storage chests or say "You haven't committed to anything yet." Instead, they indicate that you cannot understand them yet and suggest asking the Chief or returning with a translator, telepathy, or a latex form, without looping the prompt button.

#### Client and Interface

- **Separate Facility HUD toggle**: Added a standalone Facility HUD client option (also available in the Forge Config screen) separate from Territory HUD, allowing players to hide the persistent top-left facility room and zone readout while keeping biome entry subtitles active.

#### World Generation, Stability, and Performance

- **Fixed camp-approach server freezes**: Eliminated server-thread stalls and freeze loops triggered when approaching or teleporting into community structures by deferring resident camp and sleep initialization safely out of chunk entity-join hooks and avoiding recursive chunk updates on structure containers.
- **Faster explorer-map chest looting**: Extended bounded, cached landmark map searches to Forest Treehouse and Hyena's Hideout explorer maps, preventing server freezes when opening chests in watchtowers, camps, or vanilla structures that roll those maps.
- **Post-elevation biome validation**: Added final elevation biome verification for community structures so newly generated sites match their target 3D biome at actual placement height.

Servers and clients should both update to **1.1.5-Beta.2-hotfix.2**.
