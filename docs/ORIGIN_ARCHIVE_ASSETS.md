# Origin Archive Pixel-Art Assets (`changed_synergy:textures/gui/origin_archive/`)

All Native Origin Archive UI elements have been redrawn as hand-crafted pixel-art assets in **Aseprite** (multi-layer `.aseprite` source files + exported `.png` textures), preserving the `1920 × 1080` logical canvas, `774 × 1095` A4 paper coordinate system, dynamic Minecraft font rendering, and runtime entity portrait/paper-doll rendering (`ArchiveOriginModels`).

## 1. Pixel Grid & Filtering Rules

- **Logical Reference Canvas**: `1920 × 1080` (`ArchiveGeometry.fit(width, height)`), A4 paper sheet `774 × 1095` (`258 × 365` pixel-art blocks at `3×` integer scale).
- **Sampling Mode**: Nearest-neighbor (`GL_NEAREST`, no `.mcmeta` blur) across both `ArchivePaperRenderer` and `NativeOriginScreen`.
- **Content Separation**: Zero language text or character portraits are baked into any texture; all labels, titles, parameters, and page numbers are rendered dynamically via Minecraft's `Font`, and all species portraits/paper dolls are rendered live via `ArchiveOriginModels`.

---

## 2. Asset Mapping & 9-Slice / State Specifications

| `asset_key` | Texture Resource (`textures/gui/origin_archive/...`) | Aseprite Source | Native Size (px) | States & UV / 9-Slice Specification | Code Hook |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `folder_front` | `folder_front.png`, `folder_front_hover.png`, `folder_inner.png` | `folder_front.aseprite`, `folder_front_hover.aseprite`, `folder_inner.aseprite` | `843 × 1242` (`281 × 414` @ `3×`) | `default` (`folder_front.png`), `hover` (`folder_front_hover.png`), `inner` (`folder_inner.png`). Anchored at spine `x = 0`; top ear `[616..820, -20..0]`, cover body `[0..842, 0..1221]`. | `ArchivePaperRenderer.cover` |
| `folder_back` | `folder_back.png` | `folder_back.aseprite` | `867 × 1242` (`289 × 414` @ `3×`) | `default` (100% opaque board `[-24..842, -26..1195]` + spine-side top ear `[-2..136, -46..-26]`). | `ArchivePaperRenderer.shell` |
| `paper` | `paper_surface.png`, `paper_back.png` | `paper.aseprite`, `paper_back.aseprite` | `774 × 1095` (`258 × 365` @ `3×`) | `front` (`paper_surface.png`, warm cream `#F6F2E8` with pixel fiber grain & aged border) and `back` (`paper_back.png`, reverse stock `#EAE4D6`). 100% opaque. | `ArchivePaperRenderer.paper/paperBack/curl` |
| `divider_priority` | `divider_priority.png`, `divider_priority_hover.png` | `divider_priority.aseprite`, `divider_priority_hover.aseprite` | `834 × 1095` (`278 × 365` @ `3×`) | `default` / `hover`. Sheet `[0..774, 0..1095]` (`#9B9C99`) + protruding ear `[774..832, 82..228]`. | `ArchivePaperRenderer.divider/curlEar` |
| `divider_white` | `divider_white.png`, `divider_white_hover.png` | `divider_white.aseprite`, `divider_white_hover.aseprite` | `834 × 1095` (`278 × 365` @ `3×`) | `default` / `hover`. Sheet `[0..774, 0..1095]` (`#F4F4F2`) + protruding ear `[774..828, 300..420]`. | `ArchivePaperRenderer.divider/curlEar` |
| `divider_dark` | `divider_dark.png`, `divider_dark_hover.png` | `divider_dark.aseprite`, `divider_dark_hover.aseprite` | `834 × 1095` (`278 × 365` @ `3×`) | `default` / `hover`. Sheet `[0..774, 0..1095]` (`#71659C`) + protruding ear `[774..828, 442..562]`. | `ArchivePaperRenderer.divider/curlEar` |
| `divider_variant` | `divider_variant.png`, `divider_variant_hover.png` | `divider_variant.aseprite`, `divider_variant_hover.aseprite` | `834 × 1095` (`278 × 365` @ `3×`) | `default` / `hover`. Sheet `[0..774, 0..1095]` (`#DCCB73`) + protruding ear `[774..828, 584..704]`. | `ArchivePaperRenderer.divider/curlEar` |
| `divider_organic` | `divider_organic.png`, `divider_organic_hover.png` | `divider_organic.aseprite`, `divider_organic_hover.aseprite` | `834 × 1095` (`278 × 365` @ `3×`) | `default` / `hover`. Sheet `[0..774, 0..1095]` (`#A2524F`) + protruding ear `[774..828, 726..846]`. | `ArchivePaperRenderer.divider/curlEar` |
| `divider_aquatic` | `divider_aquatic.png`, `divider_aquatic_hover.png` | `divider_aquatic.aseprite`, `divider_aquatic_hover.aseprite` | `834 × 1095` (`278 × 365` @ `3×`) | `default` / `hover`. Sheet `[0..774, 0..1095]` (`#4C88B2`) + protruding ear `[774..828, 868..988]`. | `ArchivePaperRenderer.divider/curlEar` |
| `binding` | `binding.png` | `binding.aseprite` | `96 × 96` | Spine channel `[0..24, 0..96]`; `default` closed binder ring & punch holes `[24..96, 0..36]`; `open` flexed ring `[24..96, 36..72]`. | `ArchivePaperRenderer.binding` |
| `page_stack` | `page_stack.png` | `page_stack.aseprite` | `128 × 64` | `default` stacked sheet strip `[0..128, 0..32]`; `hover` fanned strip `[0..128, 32..64]`. Mirror-safe, clipped at spine `[-22..22]`. | `ArchivePaperRenderer.stack` |
| `page_shadow` | `page_shadow.png` | `page_shadow.aseprite` | `128 × 64` | `default` pixel-dithered drop shadow box `[0..64, 0..64]` + curl shadow strip `[64..128, 0..64]`. | `ArchivePaperRenderer.shell/stack`, `NativeOriginScreen` |
| `random_note` | `random_note.png` | `random_note.aseprite` | `550 × 780` | Raffle slip `default` `[0..310, 0..158]`, `hover` `[0..310, 160..318]`; masking tape `[320..492, 0..26]`; Silk Moth field note `[0..550, 330..772]`. | `NativeOriginScreen.randomSlip/mothNotes` |
| `button` | `button.png` | `button.aseprite` | `96 × 144` | 4 states (`96 × 36` each, 9-slice margin `10px`): `default` (`v=0`), `hover` (`v=36`), `pressed` (`v=72`), `disabled` (`v=108`). | `NativeOriginScreen.button` |
| `portrait_frame` | `portrait_frame.png` | `portrait_frame.aseprite` | `96 × 384` | 4 states (`96 × 96` each, 9-slice margin `12px`): `default` (`v=0`), `hover` (`v=96`), `selected` (`v=192`), `disabled` (`v=288`). | `NativeOriginScreen.roster` |
| `doll_frame` | `doll_frame.png` | `doll_frame.aseprite` | `128 × 128` | `default` 9-slice specimen chamber frame (margin `16px`) with corner measurement brackets and uniform `#E6E4DE` interior. | `NativeOriginScreen.dossier` |
| `parameter_frame` | `parameter_frame.png` | `parameter_frame.aseprite` | `128 × 32` | Row divider rule `[0..128, 0..6]`; stat bar trough `[0..128, 8..16]`; segmented stat fill `[0..128, 18..26]`. | `NativeOriginScreen.dossier/status` |
| `scrollbar` | `scrollbar.png` | `scrollbar.aseprite` | `32 × 64` | Track `[0..8, 0..64]`; thumb `default` `[8..16, 0..64]`, `hover` `[16..24, 0..64]`, `drag` `[24..32, 0..64]`. | `NativeOriginScreen.dossier/scrollableText` |
| `dialog_frame` | `dialog_frame.png` | `dialog_frame.aseprite` | `128 × 128` | `default` 9-slice triple-bordered modal panel frame (margin `16px`) with brass corner seals. | `NativeOriginScreen.confirmation/notice/status` |
| `tooltip` | `tooltip.png` | `tooltip.aseprite` | `96 × 38` | `default` 9-slice category hover tag frame (margin `10px`). | `NativeOriginScreen.tooltip` |
| `welcome_decoration` | `welcome_decoration.png` | `welcome_decoration.aseprite` | `360 × 24` | `default` ornamental pixel-art divider rule with central archive seal. | `NativeOriginScreen.welcome/desktop` |
| `navigation` | `navigation.png` | `navigation.aseprite` | `72 × 72` | Left (`u=0..36`) and right (`u=36..72`) arrow badges (`36 × 24` each): `default` (`v=0`), `hover` (`v=24`), `disabled` (`v=48`). | `NativeOriginScreen.roster` |

## Integration adjustments in the current mod

Applied the 31 supplied PNGs and the two rendering overlays to the current checkout. Dynamic models, origin selection, category records, the network contract and the page-turn endpoint handoff retain their current implementation. The archive-only language subsets in the handoff were used as reference and do not replace the full language files.

Removed the upper-right and lower-right corner plates from the front, hovered front and inner covers through the supplied deterministic Aseprite generator. Other texture pixels are preserved. `tools/authoring/rebuild_archive_folder.lua` regenerates the editable covers, with output paths supplied as arguments.

Cover wording remains dynamic and is localized in English, Chinese and Russian. The brand, origin-archive title and record subtitle fit within the supplied embossed title frame; the register number sits between the two lower rules. Long translations fit the available width without truncation.
