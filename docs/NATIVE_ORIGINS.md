# Native Origins — 1.1.4

Pure White Wolves, pups, Cerberuses and Bloodcell Wolves retain their species and body during regional adaptation. Away from White territory, their individual eyes become visible; Cerberuses gain eyes on all three heads, with stable individual colours and styles. Returning home hides them again. The same applies in Instinct mode.

Start as a human or a native latex creature. Native species choices require **TrueTransfur 2.x** for the supported native roleplay and Instinct experience. Starting with Synergy 1.1.5, TrueTransfur **1.0.1** compatibility is no longer maintained or tested.

The archive has a human record and five creature sections: White, Dark, Variant, Organic and Aquatic. A section is a browsing category; check the species dossier for its actual faction and rival faction. Eligible juvenile and fusion forms are included. Some special forms and bosses are unavailable.

## Choose an origin

1. Open the folder and select a colored section tab.
2. Click a portrait to open its dossier. Check the faction and body information before choosing.
3. Drag the body preview to rotate it, scroll over it to zoom, and right-click to toggle crouching. Scroll over a long text or parameter panel to see the rest.
4. Select **Start as this species** or the human option, then confirm your choice.
5. Wait for a safe birthplace. If suitable territory cannot be found, choose another species or approve the offered safe location near world spawn.

The **Completely random** note is attached to the opening cover. It includes the human record and selectable species in your archive. Each creature section also has **Choose a random species from this category** as its first card. Both options require confirmation and respect server restrictions.

Use the paper corners, mouse wheel or left/right arrow keys to turn register pages. Tab and Shift+Tab change sections; up/down selects a card and Enter opens it. A dossier's left corner returns to the register. Escape backs out of pages during first selection; leaving its welcome screen selects human and keeps you connected. The **Continue as human** footer does the same. During an operator-opened re-selection, Escape cancels without changing your previous origin; close an open confirmation first.

While selecting, you are temporarily invisible and protected from mob targeting. Selecting an origin does not grant friendship or a bond automatically.

## Life as a native creature

Native players begin at **Recognized faction standing (20)**. Their birth faction stays the same even if their body later changes. Unprovoked compatriots recognize them, but attacking them can still cause hostility.

Your creature name persists through death and reconnecting. Edit it through the relationship wheel or `/changedsynergy social name <name>`. The wheel also offers expressions. Your account name and permissions remain unchanged. See the [social guide](SOCIAL_GUIDE.md) for role speech, player friendship and language understanding.

Native players are protected from hostile secondary transfur. This does not prevent voluntary interactions or eligible fusion. Pure White players can adapt their appearance and vision outside their home territory without changing their actual species or faction. White latex coverings on ordinary blocks also count as home territory. Adapted eyes retain their individual colours and style.

With native origins enabled, your permanent creature form is retained after death. Normal respawn points take priority; a safe birthplace can serve as a fallback. Ordinary reversal cannot restore a human origin that you never had.

Adult native players can bond with an eligible adult friend at **60 affection** after giving the required token. Use **Invite to Bond** and confirm when prompted; neither participant changes bodies or factions. Juvenile players must first grow into an eligible adult form. See [Friends, gifts and bonds](RELATIONSHIP_GROWTH.md).

## TrueTransfur control modes

With **Starting Origin Selection** enabled, TrueTransfur's permanent-identity features normally apply only to native players. Ordinary Changed transformations still work for human-origin players. Turning the world rule off restores TrueTransfur's usual eligibility rules and retains saved origins.

**Human-Origin Instinct Control** is off by default. A server can enable it to let ordinarily transformed human-origin players use TrueTransfur 2.0's Instinct mode. This does not grant native identity, permanent transfur, native rebirth or native role speech.

TrueTransfur 2.0's visible Instinct body remains a player for social interactions. It has no persistent nameplate; expressions can still appear. A native Instinct player can temporarily take over an eligible hostile human under server PvP rules. See the [TrueTransfur guide](TRUETRANSFUR_INSTINCT_COMPAT.md).

## Server controls

World rules are available when creating a world and in Synergy's **World Rules** tab. Operators can also use:

```mcfunction
/gamerule changedSynergyNativeOrigins true
/gamerule changedSynergyNativePvPReputation true
/changedsynergy origin
/changedsynergy origin choose PlayerName
```

`origin choose` reopens selection for an online player. It requires operator permission, retains inventory and relationships, and cannot run during a grab, takeover or another selection. Confirming human safely restores the human body; cancelling preserves your current identity.

Under **Starting Species List**, **Include Other Mods' Origin Species** is off by default. Enable it to add eligible playable forms from installed mods. **Disabled Starting Forms** excludes specific choices. Use full transfur form IDs, including any gender suffix:

```mcfunction
/changedsynergy config set BEHAVIOUR.IncludeModdedOriginSpecies true
/changedsynergy config set BEHAVIOUR.DisabledOriginForms changed:form_dark_latex_wolf/male,changed:form_dark_latex_wolf/female
/changedsynergy config set BEHAVIOUR.DisabledOriginForms []
/changedsynergy config set BEHAVIOUR.AllowHumanOriginInstinct true
```

Form restrictions affect new choices, not existing origins. Reopen the archive after a server list change. Random choices follow the same restrictions as direct choices.

With **Changed: Survive Protocol 1.2.0**, confirmed re-selection replaces the previous settled strain. Choosing human clears it; cancelling leaves it intact. Ordinary infection still follows Survival Protocol's rules.

Install the same Synergy build on the server and clients. TrueTransfur 2.0.0 also requires **Mixin Args** on both sides. See [server configuration](SERVER_CONFIGURATION.md) for permissions and settings.
