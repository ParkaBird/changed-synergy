# TrueTransfur and Instinct Mode

This guide describes **Changed: Synergy 1.1.4** with **TrueTransfur 2.0.0**, the recommended pairing for native identity, roleplay and Instinct-mode interactions. **1.0.1** remains supported for native origins and direct player control. Features involving a separate Instinct body require 2.0.0.

## Installation

- Install Changed 0.15.7 and the same Synergy build on the server and every client.
- TrueTransfur is needed for native origins. Other Synergy features work without it.
- TrueTransfur 2.0.0 also requires **Mixin Args 1.0 or newer** on the server and clients.
- Changed Addon Plus is optional for these player interactions and companion wrapping.

TrueTransfur 1.0.1 controls the player directly. Version 2.0.0 has a separate visible Instinct body and supports the expanded player-takeover interaction below.

## Identity and control

In **PLAYER**, you control movement normally. In **INSTINCT**, your creature body acts automatically. Your creature name, friendships, faction standing and interaction cooldowns remain the same when switching modes.

Other players interact with your visible body. Your Instinct body has no persistent nameplate; short-lived expressions still appear. It is not a separate NPC, cannot be recruited as a camp member and does not form its own NPC bonds.

With **Starting Origin Selection** enabled, TrueTransfur's permanent-identity features normally apply only to native players. A human-origin player's ordinary Changed transformation is retained. Servers can enable **Human-Origin Instinct Control** to permit Instinct after an ordinary transformation, without granting native identity or permanent-body features. Turning origin selection off restores TrueTransfur's ordinary eligibility rules.

TrueTransfur 2.0's rebirth feature follows the same native-origin policy. During its downed stages, social interactions are unavailable. Temporary wrapping is not a new permanent origin.

## Interacting with players

Right-click another player with an empty hand, or sneak-right-click while holding an item, to open player interactions. You can also right-click their visible Instinct body.

- **Say hello** greets them.
- **Pat** interacts with their visible body.
- **Give a gift** transfers one held item. If the recipient's inventory is full, you keep it.
- **Become friends** requires both players' agreement, with a 30-second invitation. At least one participant must be native; no familiarity threshold is required.
- **Walk together** automatically follows an already confirmed friend in Instinct mode. Other requests need acceptance. Manually controlled players see the inviter highlighted and retain movement control.

Use `/changedsynergy social stop` to end your accepted walk. Returning to PLAYER ends automatic following. Disconnection or an unavailable body also ends the walk. Friendship does not grant equipment access, a player bond or permission to control another player.

Native players can change their creature name, choose expressions and speak locally as their species. Click the label above chat or use `/changedsynergy social chat global|roleplay` to switch to normal chat. See the [social guide](SOCIAL_GUIDE.md) for language understanding and familiarity growth.

## Temporary takeover of a human

An eligible adult native Instinct player can pursue a human who is hostile towards its faction, or retaliate after that human attacks it. Low familiarity alone is not an attack reason. Confirmed friends are excluded from automatic targeting, and server PvP and player alliances still apply.

The creature must first complete ordinary transfur progress, then make unobstructed physical contact. Simply approaching cannot capture the human. Walls, floors, occupied grabs and vehicles prevent capture.

The human is temporarily wrapped, with a **30-second** countdown by default. Other latex creatures cannot attack or capture them again while contained. The human can negotiate once or attempt a WASD escape. Failed negotiation adds ten seconds, up to 1.5 times the original duration. Failed escape does not cause permanent transfur or death in this player-to-player interaction.

The victim's countdown stays at the top of the screen, with remaining time and outcome text in the carrier's species color. The carrier sees the release bar above the hotbar. A captured player can look around in third person without steering the carrier; first person follows its heading.

The human is released locally when the timer expires, the carrier returns to PLAYER, or the interaction is interrupted by logout, death or body loss. Their temporary form is removed, and they receive at least 60 seconds of protection from another temporary takeover. Ordinary NPC takeovers have their own outcomes; see [Protection and takeovers](SOCIAL_GUIDE.md#protection-and-takeovers).

## Server options

Use **Player roleplay** in Server Settings to control role speech, its sending interval, Instinct-player takeover and its initial duration. Disabling player takeover releases active victims. Friendship, expression, faction, grab and takeover settings still apply; client preferences cannot override server policy.

```mcfunction
/changedsynergy config set PLAYER_ROLEPLAY.InstinctConfinement false
/changedsynergy config set PLAYER_ROLEPLAY.InstinctConfinementSeconds 30
/changedsynergy config set BEHAVIOUR.AllowHumanOriginInstinct true
```

For the full setting catalog, see the [settings guide](SETTINGS_GUIDE.md). For choosing a species, see [Native origins](NATIVE_ORIGINS.md).
