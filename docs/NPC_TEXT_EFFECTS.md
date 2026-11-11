# NPC dialogue text effects

Story conversations support emotional text staging inside the existing bottom dialogue panel. Glyphs are laid out before animation; effects do not change wrapping, page boundaries or the choice controls. The world keeps running. Click, Enter or Space completes the current page immediately and skips its entrance motion. Mental speech fades in as a whole sentence, without outer parentheses. Its steady strike line identifies it while selected phrases can carry their own emotional color and movement.

## Authoring a fragment

Text staging is declared per `parts` entry in a story NPC profile. Keep prose in the English, Chinese and Russian language entries. A fragment can carry an emotion preset, an explicit effect, a speed multiplier, an intensity multiplier and a pause after its text.

```json
{
  "text": "npc.changed_synergy.example.warning",
  "emotion": "angry",
  "effect": "shout",
  "speed": 1.1,
  "intensity": 0.8,
  "pause": 180
}
```

`emotion` defaults to `neutral`. An omitted effect, or `auto`, uses that emotion's effect. An explicit effect overrides the motion while retaining the emotion's pacing, fade and tint. `none` retains the ordinary reveal without a continuous motion effect. Use zero intensity for an entirely still narrative fragment; keep stronger effects on a few meaningful words. `speed` defaults to 1 and accepts 0.25–3; larger values speak faster. `intensity` defaults to 1 and accepts 0–2; zero removes spatial motion. Presets and multipliers are combined within the renderer's motion and pace limits. `pause` is the existing 0–2,000 ms pause after a fragment.

Unknown effects/emotions, nonfinite numbers and values outside the declared ranges reject the profile reload before live profiles are replaced. Existing fragments with only `text`, `effect` and `pause` remain valid.

## Emotion presets

| Emotion | Default effect | Speaking character |
| --- | --- | --- |
| `neutral` | `none` | Ordinary pacing and reveal |
| `calm` | `none` | Steady, slightly slower delivery and a soft green tint |
| `curious` | `wave` | A small rising/falling wave with a warm tint |
| `happy` | `bounce` | Light upward bounces and slightly quicker speech |
| `excited` | `bounce` | Faster speech and a stronger bounce |
| `angry` | `shake` | Tight agitation, faster delivery and a warm red tint |
| `fearful` | `tremble` | Small, fast tremors with slower delivery |
| `sad` | `droop` | A low resting baseline and slower, cooler text |
| `shy` | `wobble` | A small tilt and hesitant pacing |
| `whisper` | `whisper` | A slower, softer fade with readable subdued text |
| `surprised` | `impact` | A short springing entrance that settles |
| `determined` | `pulse` | A restrained breathing pulse |
| `dreamy` | `float` | Gentle drifting and slower speech |

## Explicit effects

| Effect | Behavior |
| --- | --- |
| `none` | Standard text reveal |
| `emphasis` | Theme-colored emphasis |
| `wave` | Traveling vertical wave across the characters |
| `shake` | Quick agitation on both axes |
| `tremble` | Smaller, less forceful tremors |
| `bounce` | Staggered upward bounces |
| `float` | Slow vertical drift with a little horizontal movement |
| `pulse` | Small breathing changes in glyph scale |
| `wobble` | Gentle character rotation |
| `droop` | A lowered baseline with a slow sway |
| `shout` | A springing entrance with a little sustained tension |
| `whisper` | Soft, steady text with a slower fade |
| `impact` | A brief entrance pulse that settles to still text |

Use strong motion for a short emotional beat. Neutral and calm passages should have room between those beats. Membership instructions and permanent-body confirmations retain their ordinary presentation. Avoid assigning anger or fear solely to demonstrate the effect when the speaker's words do not support it.

The supplied conversations now use restrained, context-specific staging. The canopy Chief stays calm, the Hyena's race remark gets a brief excited emphasis, the Archivist's embarrassed hoot has a small wobble, and the researcher's tail gives away his excitement. The supplied Chinese and Russian localization follows the same staging as English. Branches, conditions, effects and identities are preserved. Emotion is authored explicitly; there is no keyword-based emotion classifier.

## Motion preferences and playback

Reduced UI Motion removes all spatial displacement, rotation, scale changes and entrance pops. Static emotional colors and speaking cadence remain. Reading can always be completed with the existing controls; mental speech uses a short, whole-sentence fade. Effects use glyph-local transforms, with no screen shake, flashing backgrounds, text replacement or additional sound loop.

Understood thoughts share the preceding spoken passage, with their own paragraph and channel on each fragment. They fade as a sentence and use teal strikethrough text rather than a Thought label or a separate server page. Speech keeps its existing typewriter timing and emotion colors. Reply options appear above the bottom-anchored body frame; controls and the page/choice counter sit above the reply list, aligned to its text insets. A fragment can set `beat: true` to start the next conversational beat. The compiler preserves authored breaks across all three languages, with no text changes. These breaks drive paging; viewport overflow is a fallback that prefers full sentences or clauses and keeps an aside with its related speech whenever possible.

Clients and servers must run matching builds. The current protocol is defined in `ChangedSynergyNetwork`. Additional part metadata is supplied by the server; clients do not choose conversational effects or bypass server-side choice validation.

The revised dialogue trees are authored in `tools/authoring/story_npc_dialogue.json`. Chiefs now enroll visitors into their own local camp households, with independent trust; the researcher retains the ordinary Wanderer role. The window uses the hand-drawn social panel, larger text and content-based page height. Text is laid out before playback, so typewriter reveal never moves the frame or changes wrapping.

## Current performance pass

Story lines retain their authored prose and conversational beat markers. The supplied Chinese and Russian lines and choices use matching phrase-level emotional accents. Continuous effects are limited to phrases that need them. The Weaver uses a steady, gentle voice with brief moth distractions; the Matriarch moves between open hospitality, protective firmness and subdued memories; the Archivist balances drowsiness, embarrassment and quiet wonder; the Researcher tries to keep academic composure while his private reactions interrupt it. The Pathfinder speaks cautiously, with restrained pauses and accents on memories, concern and practical reassurance. Reused greetings and permanent welcomes retain the same direction.

Emphasis now has a readable entrance and a small resting enlargement. Exclamations have a short, bounded hit; waves, bounces and tremors are more legible at normal GUI scale and ease down after their entrance. No effect changes line wrapping. Thought colors retain a teal base with emotional tints, and their horizontal strike remains on the reserved baseline instead of shaking with the letters. Only matching outer thought brackets are hidden; spoken and nested parentheses remain. Reduced UI Motion removes movement and scale changes while keeping the reading order, timing and color.

Household dialogue uses the same renderer and localized component arguments for supplies, quantities and home names. The server sends the arguments with each text span and choice. Existing story spans have no arguments and retain their previous presentation.
