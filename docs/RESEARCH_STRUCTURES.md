# Research structures

Four contributed buildings generate in newly explored Overworld chunks. Existing chunks are left alone. Normal world structure generation must be enabled.

All four buildings were constructed by **Crossader**.

| Structure ID | Location | Optional dependency |
| --- | --- | --- |
| `changed_synergy:water_tower_changed` | Oceans with sufficient depth; the original waterline aligns with sea level | None |
| `changed_synergy:white_lab_tower` | Relatively level terrain in Changed's decayed-lab biomes and the restored latex territory biomes | None |
| `changed_synergy:red_lab_tower` | Same land biome selection | None |
| `changed_synergy:green_lab` | Jungle-family biomes (`#minecraft:is_jungle`): jungle, sparse jungle and bamboo jungle | None |

Use `/locate structure <ID>` to find a naturally generated building. Each structure can also be located through its vanilla registry entry. The three land buildings share a random-spread placement set, with 40-chunk spacing and 12-chunk separation. The ocean building has its own placement set. Both avoid candidate locations within six chunks of Changed's decayed-lab placement set. A candidate can be rejected if the terrain is too steep or too shallow. These values are data-pack resources under `worldgen/structure_set` and `tags/worldgen/biome/has_structure`.

## Template adaptations

- The original Downloads files are untouched. Runtime copies live under `data/changed_synergy/structures`.
- Structure-editor blocks and unused air above the buildings are removed. Exterior sea water is omitted so the ocean laboratory does not paste an artificial rectangular body of water. Enclosed aquarium water and the authored interior air remain.
- Changed's painted latex coverings, occupants, books, keypad codes, doors, furnishings and dispenser ammunition remain. Old entity UUIDs, saved AI memories and third-party attribute snapshots are removed; spawned occupants use their actual species' attributes.
- Empty vanilla chests, trapped chests and barrels use the matching Changed decayed-lab loot table. The green facility's trapped chest keeps its trap and now has loot. Authored inventory slots, password clues and bookshelf contents are retained; reviewed document text is applied separately.
- Crossader's latest `white_lab_tower.nbt`, `red_lab_tower.nbt` and `green_lab_tower_fix.nbt` provide revised vaults, loot and lore. All four templates use only Minecraft, Changed and Synergy content. The red tower's remaining Addon puddles use Changed's native block; the green facility's catalyzer is replaced with a furnace. Neither building requires Addon Plus. The green resource remains `green_lab`, with jungle-family generation.
- Authored high- and low-tier loot choices are preserved. Missing `chests/` prefixes in seven table references are corrected to the existing Changed tables. The green trapped chest retains its author's low-tier laboratory loot. Invalid empty entity records are omitted, and the scientist saved inside an old-world seat is restored as a normal gas-wolf occupant at that local position. Occupant compatibility snapshots and old identities are removed, including those on saved passengers.
- The ocean laboratory retains its waterline, internal water, coverings and residents. The upper research-room chest at local `(43, 34, 33)` uses a new loot table: its original aquatic loot plus one guaranteed **Swordfish Research** compact disc. Its reviewed discovery record retains the supplied Markdown formatting. Two missing furniture supports are restored.
- Multipart furnishings retain their saved shapes while adjoining chunks are placed, preventing premature breakage. Crossader's latest red-tower hall, ceiling, misplaced-painting and extra cleanup changes are applied through `tools/red_lab_layout_patch.json`, preserving the existing story, retained inventories, dependency substitutions and unrelated repairs. The red tower retains eight wolves and twelve displays; its creepers, skeletons, old arrows and Yufeng are removed along with the author's cleared lower-floor furnishings. The unsupported berry display frame above the cleared furnishings is also removed to prevent item drops. Air conditioners and their normal ventilation spaces remain intact.
- Surface buildings reject large height differences and have short supports below their actual foundations. The ocean laboratory is anchored to its original waterline and requires adequate seabed clearance. Templates retain their rotation when a saved structure start reloads.

`tools/adapt_research_structures.py` reproduces the import using `nbtlib`. It is an offline maintenance tool, not a build or runtime dependency. `tools/research_structures_manifest.json` records source hashes, dimensions and adaptation counts.

To import the revised filenames, pass `--source-suffix '(1)'`. `--output <folder>` stages templates for inspection or contributor export. The exported green file may be named `green_lab_tower.nbt`; the runtime resource remains `green_lab.nbt`.

For a partial update, use `--source white_lab_tower=white_lab_tower.nbt --source red_lab_tower=red_lab_tower.nbt --source green_lab=green_lab_tower_fix.nbt`. This preserves the current ocean resource and its manifest entry.

## Reviewed documents

`tools/research_structure_documents.json` stores the approved 2026-10-27 revision: fifteen revised records and four filled blank carriers. Book authors and the existing signed note remain; `Last Will` is now titled `Farewell Note`. Homogeneity page three also describes retrieving transformed staff from the white-latex pool, and page four explains the eyes grown outside their territory. GL-02 reads `Crystal Status`; the nearby display contains a two-block Dark Latex Crystal on a Dark Latex Block inset into the original floor, preserving the glass case and roof height. Door codes and puzzle clues are not rewritten.

Notes, clipboards and disc-writing screens adapt to their content, with scrolling for longer records. Records previously shortened for the old readers are restored. The housekeeping heading no longer has a following newline. Data-disc Markdown remains unchanged. The English story-fragment export gives each record its structure, a prose description of its location, medium and existing attribution.

Written books keep the vanilla reader and its font size. All five structure books are checked against the native 114-pixel text width and 128-pixel text height. The overflowing third page of `Homogeneity` continues onto an additional page, increasing the book from four pages to five. No prose or signatures are removed; the books contain 27 pages in total. Existing books already saved in worlds retain their saved contents.

`python tools/apply_research_documents.py` applies this prose to the current resources, including the loot-generated Swordfish disc, and refreshes the output hashes. The template importer applies the same document patches after adaptation, locating containers by local position and inventory slot so later imports do not silently restore old lore. A missing or changed holder fails the import for review. `python tools/tests/research_documents_test.py` verifies the text and all 22 numeric keypad codes. These tools require `nbtlib` only for maintenance.

Previously generated buildings and the user's editing save are not rewritten when the mod updates.
