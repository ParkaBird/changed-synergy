# Social guide

[中文版](SOCIAL_GUIDE.zh-CN.md)

These rules describe the default settings in Changed: Synergy 1.1.4. Servers can change relationship growth, bond requirements and other interaction settings. The configuration screen has **Server Settings**, **World Rules** and **Client Visuals** tabs. On multiplayer servers, gameplay values are shown read-only; operators use `/changedsynergy config` to change them and can edit world rules in their own tab. See the [settings guide](SETTINGS_GUIDE.md) for the current categories and dependencies.

Spectator mode keeps Changed's ability wheel available but does not open Synergy's relationship wheel. Escape closes the wheel, including during an interrupted switch.

TrueTransfur **2.0.0** supports native roleplay and interactions with a player's visible Instinct body. **1.0.1** remains supported for its direct-player integration; features involving the separate Instinct body require 2.0.0. See the [TrueTransfur guide](TRUETRANSFUR_INSTINCT_COMPAT.md) for installation and control modes.

## First contact

A creature's willingness to approach or talk depends on its species, personality, faction standing and recent experiences with you. Patting may calm an approaching creature or answer a greeting. It does not guarantee safety after aggression, and some creatures cannot form Synergy relationships at all.

Dark and White latex are peaceful competitors. They do not attack each other, or players in the opposite latex form, simply because of their colour or faction. This includes native players and TrueTransfur's visible Instinct bodies. Visiting the other faction's territory is safe from racial aggression, but attacks, theft, betrayal or genuinely hostile faction standing can still provoke a response. Peace does not grant friendship, kinship or permission to take community supplies. Dark-latex disguises no longer provoke White observers merely by their appearance.

Their factions and communities remain separate. The usual [rival-alliance reputation rules](FACTION_DIPLOMACY.md) still apply, and mixed-species player camps remain available.

Once a stranger is calm enough to interact, use its social wheel for **Talk**, **Pat** or **Gift**. Suitable food held in your main hand can be fed directly by right-clicking. Sneak-right-click with food to open the wheel instead.

Without telepathy, many reactions are species-based sounds. Ordinary conversation and repeated encounters still matter to dialogue and memories, but do not award affection.

## Become acquainted

Patting and direct feeding can build a stranger's affection to **8 points**. They cannot establish a friendship or give the creature a name by themselves. At that limit, select **Gift** in the wheel while holding an accepted item. This first gift establishes the relationship and names the creature, keeping the affection already earned.

A premature, unsuitable, refused or cooldown-blocked gift does not consume your item. The wheel explains what is missing. Ordinary gifts are broad, but species-specific dislikes still apply.

Becoming a native pet requires neither the stranger affection threshold, a wheel gift nor a bond token. Once a tamed pup grows up, or an adult native pet is obtained (including an Exp2 companion separated after transfur), its owner is recognised at maximum affection without further gifts. This recognition happens once; reloading or changing control mode does not replenish later affection losses. Pet ownership remains distinct from a voluntarily established Synergy bond.

Juvenile pets keep their native taming interactions and cannot use adult friendship or bonded wrapping abilities before they grow up. Untamed creatures still follow the ordinary social rules. Direct feeding of an untamed Exp2 retains Addon's native taming behaviour; wheel gifts remain ordinary social gifts.

## Feeding, patting and gifting

Feeding and gifting are separate actions:

| Action | Default affection | How it works |
| --- | ---: | --- |
| Pat | +2 | Shares the care cooldown and allowance |
| Directly feed suitable food | +2 | Feeding itself has no cooldown; healing and food effects still work |
| Play or a shared activity | +2 | Shares the care cooldown and allowance |
| Give an ordinary accepted wheel gift | +2 | Accepts one item; separate gift cooldown |
| Give a preferred-food wheel gift | +4 | Same gift cooldown |
| Give a Golden Orange through the wheel | +6 | Same gift cooldown; does not create a bond |
| Talk, meet again or receive a pat | 0 | Reactions and experiences are still remembered |

Care gains have a shared **60-second cooldown**. After acquaintance, an accepted wheel gift refills an **8-point care allowance** for patting, feeding, playing and shared activities. Unused points do not stack. When the allowance is spent, give another accepted gift to replenish it.

Wheel gifts have a separate **five-minute cooldown**. A pat does not delay your next gift, and a gift does not restart the care cooldown. Care animations, pacification and feeding remain usable when they award no affection. Hearts and affection feedback appear only when a gain is awarded.

Faction standing can reduce positive gains. Affection reaches **Familiar** at 12, **Close** at 28 and its maximum at 60. New camp invitations require a trusted adult friend at Close friendship.

Feline creatures accept raw or cooked cod and salmon, including without Changed Addon Plus. They still dislike oranges. Other species retain their own suitable-food rules.

## Restore peace and respond as a group

If you have upset a creature, stop attacking for at least ten seconds, then offer suitable food directly with right-click. A successful offering clears personal hostility and starts a temporary truce, while retaining the creature's name, friendship and memories. It does not erase your faction reputation or skip the first-gift requirement. Food rejected during the recent-attack period is not consumed. You cannot make this offering during an active grab or takeover.

A trusted friend's mediation can also calm nearby members of its faction. Already acquainted creatures can recover from personal hostility; hostile strangers may still refuse mediation. Attacking again ends the peace and can carry the usual penalties.

Successful peace or negotiated release ends the current chase, including a wild cache guard's pursuit. A temporary grudge is extended by fresh player aggression, not by the creature's own attacks. Calming one player's conflict does not interrupt a separate fight with another player.

After an organic creature turns you into compatible latex kin, it will not continue attacking you when temporary protection ends. New provocation, betrayal or hostile faction standing can still cause a fight. Organic creatures keep their low physical swipe damage against humans; genuinely hostile transformed players receive ordinary damage.

The relationship wheel's group-play option calls available contacts within fourteen blocks to briefly approach and react. Sneaking selects a quiet shared rest instead. The activity has its own thirty-second cooldown, and play's affection gains still use the care cooldown and allowance. Social signals prompt a brief reaction from available contacts within twelve blocks and line of sight. Sleeping, occupied or fighting creatures do not interrupt those activities to respond.

## Tools for companion work

Companion mining uses the actual pickaxe's mining speed and Efficiency. Fortune and Silk Touch affect the harvested items; successful mining wears the stored tool normally. Fishing uses Lure to shorten the wait and Luck of the Sea to affect catches. Under default timing, a cast takes eight seconds without Lure, or two seconds with Lure III.

Friends gathering minerals or fishing first use a suitable pickaxe or rod from their personal inventory. Where several tools qualify, miners prefer the faster pickaxe and anglers prefer higher Lure, then Luck of the Sea. Without a suitable stored tool, ordinary gatherers retain their own default iron pickaxe or fishing rod. Pet assistance still requires its supplied tools. Work in a player camp follows its assigned role and delivers produce through the camp's existing cargo system.

## Patting controls

Use **H** or the wheel's **Pat** option. While patting, scroll the mouse wheel to smoothly speed up or slow down the strokes; each new pat starts at normal speed. Speed and the part you touch can prompt different reactions. Body-part lines have an eight-second cooldown.

Third-person strokes blend into and out of the pose on models with usable arms. An absent arm skips the animation without cancelling the interaction. Feral NPCs, pups and Milk Pudding do not pat players back, while armed species such as latex snakes and mermaid sharks can. Camp workers finish their current duties before initiating return pats.

## Playful friends

**Playful** and **Polite** are mutually exclusive behavior traits. Other personality traits determine how the creature speaks.

An adult Playful friend below Close affection may occasionally try a friendly assimilation or absorption of a healthy human friend. Attempts become less likely as affection rises and stop at 28. The creature waits at least five minutes after an attempt. Combat, danger, existing captures and recent release protection prevent a new attempt.

A light, empty-handed warning interrupts that creature's attempt and settles them for five minutes, without reputation loss or betrayal. This exception belongs to the creature currently making the attempt; it does not excuse unrelated attacks on friends. Native and already-transformed players are not targets for this behavior.

## Establish a bond

Full affection alone does not unlock a first bond. At **60 affection**, wait for the gift cooldown to finish, then offer the required token through **Gift**:

| Species group | Default token |
| --- | --- |
| Aquatic | 1 Heart of the Sea |
| Organic | 1 Golden Apple |
| Dark latex | 4 Dark Latex Crystal Fragments |
| White latex | 1 Quartz |
| Other variants | 1 Amethyst Shard |

Aquatic classification takes priority, followed by Organic; the other groups use the creature's faction. This does not change its faction. The wheel shows the required item, quantity and any unmet conditions, including adulthood, hostility, ownership or the server's bond limit.

Once accepted, the token reserves that NPC for you. It remains reserved while you or the creature are offline or unloaded.

- **Human players:** select and confirm **Voluntary transfur** to establish the bond.
- **Adult native players and supported permanently transformed TrueTransfur players:** select and confirm **Invite to Bond**. Neither participant changes bodies, and your control mode stays the same.

If you already have a bond or another ownership or bond-count limit prevents a new one, eligible full-affection friends may offer **Friendly transfur** instead. That option creates no new bond and requires no token.

## Withdraw, part or reunite

Before the bond is completed, withdraw a reserved token through the creature wheel or `/changedsynergy bond token withdraw <creature UUID>`. Creature death or disabling token requirements returns unused tokens once. Offline refunds wait for you; if your inventory is full, the item drops beside your visible body.

Open the relationship roster through camp management or `/changedsynergy relationship`. True bonded companions appear as **Name [Bonded]**. Select **End bond** and confirm to part. Wrapping, bonded following and exclusive protection end safely; friendship, name, affection and memories remain. Eligible camp members can keep their jobs.

You can also end a bond while the companion is unloaded. Former companions can reunite at 60 affection when ownership and bond-count rules allow, without a second token.

**Remove relationship** for an ordinary friend is a separate action that clears the contact. Ending a bond does not automatically forget the creature.

Stasis-chamber modifications, growth and absorption-related form changes preserve the creature's name, friendship, affection and bond. Camp assignments and carried work cargo follow the new body too.

**Travel together** invites available loaded contacts to follow. Distant followers can return to a safe spot near you even when their current area is not running creature AI. It does not search arbitrary unloaded terrain or summon dead creatures; confirmed deaths are removed from the roster. Busy companions and members assigned to stationary camp duties keep their current commitments.

Following friends, bonded companions, pets and travelling camp members can still return when a combat target would otherwise keep them behind. Once you move beyond their normal teleport distance, they regroup at a safe spot near you and stop pursuing the old target. Nearby fights continue normally; waiting, stationed, restrained and wrapping companions are not recalled this way.

## Protection and takeovers

Automatic companion protection respects following or waiting orders and the creature's attack settings. Waiting or distant companions do not teleport in to intervene. **Never attack** prevents defensive attacks while still allowing local, nonviolent emergency shelter. A peaceful grab by a friend or another player is not treated as an assault.

The **Monsters** target filter excludes neutral creatures such as unprovoked zombified piglins unless they are actually threatening the owner or have attacked the companion. **Always attack** does not override the chosen target filter, waiting orders or camp duties. Companions belonging to scoreboard allies, or to players on a server with PvP disabled, do not treat each other as enemies merely because their species or factions differ.

**Self-Defense Penalty Protection** is enabled by default on the server. Fighting a creature that initiated the attack does not incur the related faction or bonded-kin penalties. Starting the fight, or attacking again after accepting a truce, is not covered. Operators can change this through `RELATIONSHIPS.SelfDefensePenaltyProtection`.

Bonded emergency wrapping and organic evacuation can be disabled separately from manual wrapping and ordinary defense. Aquatic friends offer drowning shelter only after actual drowning damage, with aquatic friend rescue also enabled. After release, automatic shelter has a short grace period and waits for the same emergency to subside before trying again. You can still request manual wrapping. With protective release requests enabled, a concerned companion may ask you to request release again before letting go.

Companion wrapping and rescue work without Changed Addon Plus. Organic companions carry their wounded owner out of danger rather than assimilating them; this also preserves an already transformed owner's body. Species excluded from grabbing and juvenile pets retain their restrictions.

Friendly arm holds also allow the normal WASD escape. Escaping a friendly hold does not hurt or stun the creature or apply escape debuffs to you. Ordinary social hugs end automatically; protective wrapping still uses its own release controls.

When using Changed's own grab ability as a creature player, hold its assigned ability key to unwrap a suited creature into an arm hold, then hold it again to release. This is separate from the locked NPC takeover interface.

A bonded companion may confine an owner after repeated **witnessed** kills of its own faction; the default threshold is three, with greater tolerance at high affection. The creature must physically approach through an open route before confinement starts. Walls, floors and other collision barriers do not permit remote capture.

Takeover consequences depend on the encounter:

Takeovers require Changed's **Keep Conscious** rule (`changed:keepConscious`).
With this rule off, forced transfur causes death through the normal conversion
instead, including attacks from Instinct-mode players. Confirmed voluntary
friendship/bond transfur and chosen starting origins remain safe; protective
wrapping does not count as forced transfur.

| Encounter | Outcome |
| --- | --- |
| Bonded kin confinement, a personality-led friendly takeover, or confinement following aggression at neutral/friendly standing | The countdown releases you. A failed escape can make you sleep before release; friendly takeover can include orange compensation. |
| Aggression at **Distrusted** standing | A direct takeover can permanently transform you into the carrier. |
| Aggression at **Hostile** standing | Punitive takeover can end in unconscious death. |

Release negotiation is available once per takeover. Failure extends ordinary confinement; in a fatal punitive takeover it shortens the deadline. An interrupted grab is cleared when another creature takes over. If the carrier disappears, the session releases its movement lock instead of leaving you trapped. Server takeover and final-outcome settings can change these rules.

When an ordinary confinement countdown ends while you are awake, release occurs at a safe nearby position. A failed escape that puts you to sleep uses the separate recovery rules and can take you to a recovery location.

A creature carrying a player during an ordinary takeover retains its original navigation, movement speed and permitted combat behavior. Voluntary companion wrapping keeps its owner-control rules.

During takeover, latex puddles, dropped syringes and other environmental transfur hazards cannot add transfur progress or change the host's form, including while borrowing control. After release, normal environmental rules apply again. This protection does not prevent the takeover's own permanent-transfur or consciousness-loss outcome.

During takeover you can turn the camera in either third-person view to look around without steering the carrier. First-person view continues to follow the carrier. Borrowed control and release restore ordinary input.

A controlling exoskeleton walks over terrain, falls normally and can jump onto a one-block step.

## TrueTransfur and multiplayer

Logging out, dying, changing dimensions or switching PLAYER/INSTINCT modes does not refill affection allowances or reset cooldowns. Cooldowns pause while the server is stopped.

In TrueTransfur Instinct mode, interact with the player's visible body. Their identity, friendships and faction standing stay the same; the body is not a separate NPC.

Companions can assist your visible Instinct body and respect your player alliances, including allied players' companions.

Right-click another player's visible body with an empty hand, or sneak-right-click, to use player social interactions. A following request in **PLAYER** mode needs that player's acceptance. In **INSTINCT**, confirmed friends' requests are accepted automatically; strangers still need the real player's confirmation. `/changedsynergy social stop` ends your own accepted walk. These actions do not change control mode or award NPC affection. A walk ends if its leader is offline or unavailable.

### Your native creature identity

Native-origin players receive a creature name from their species' naming pool. It survives death, reconnecting, dimension changes and replacement Instinct bodies. Your relationship wheel offers **Creature name**, or use `/changedsynergy social name <name>`. Custom names allow up to 24 characters without formatting or control codes. Social displays use your creature name; the player list also retains your account name. Permissions and administrator commands continue to use your account name.

The wheel also offers **Expressions**, opening a radial picker with six expression icons around your current body. Hover for a name, select an expression, or press Escape to return to the relationship wheel. A manual expression lasts three seconds, with a three-second cooldown. It follows your visible PLAYER or INSTINCT body. The Instinct body keeps its name label hidden.

With origin selection enabled, TrueTransfur normally remains restricted to native origins. Servers can enable **Human-Origin Instinct Control** (`BEHAVIOUR.AllowHumanOriginInstinct`, default off) to let human-origin players use TrueTransfur 2.0's Instinct mode after an ordinary transformation. This permission does not grant permanent transfur, lethal reversal, native rebirth or native role speech. Disabling it returns those players to PLAYER mode while preserving their ordinary Changed form.

### Friends between players

When at least one participant is native, **Become friends** sends a 30-second invitation. Both players must agree, but no affection or gift threshold is required. Confirmed contacts appear as **Name [Player]**. Either player can end the friendship; shared familiarity and cooldowns remain. Player friendship grants no bond, equipment access or control over another player.

Shared familiarity ranges from 0 to 60. Patting and accepted **Share a moment** invitations award +2 using the same care cooldown and allowance as NPC growth. Gift transfers award +2 using the separate gift reward cooldown and replenish the care allowance. During that cooldown, players can still exchange real items; they simply earn no additional familiarity. Greetings, chatting and changing control mode do not grant points. These records survive death, logout and server restarts.

### Speaking as a creature

Native players default to **Role speech** for ordinary chat. Messages use the creature-dialogue overlay and reach listeners within the server's dialogue range, normally 32 blocks in the same dimension. You always see your own words. Other listeners understand according to the existing telepathy, transformed-body, translator and special-species rules; otherwise they receive species-based sounds. Disabling the dialogue overlay displays the same understood or unintelligible message in chat instead.

Click the mode label above the chat input, or use `/changedsynergy social chat global|roleplay`. **Global** uses normal Minecraft chat. Commands, private messages and system notices retain their usual purpose. Role messages follow server chat restrictions and have a separate two-second sending interval. This does not translate between natural languages.

### Instinct player takeover

With **Changed: Survive Protocol 1.2.0**, Synergy takeovers retain their negotiation and release options. Ordinary infection still follows Survival Protocol's rules; infection alone does not guarantee a takeover negotiation. Nether releases stay below the bedrock ceiling when the player was below it.

With TrueTransfur 2.0, a native player's adult Instinct body can attack a human who is Hostile towards its birth faction, or retaliate after that human attacks it. Low shared familiarity alone is not an attack reason, confirmed friends are excluded from autonomous targets, and server PvP and scoreboard friendly-fire permissions still apply. This feature operates between the native Instinct player and the human player.

An eligible body first attacks using Changed's normal transfur progress, including its shield, armour and hit-delay rules. Reaching the human alone cannot start a takeover. When progress completes, unobstructed physical contact allows the body to wrap the human inside itself through Changed's normal wrapping interaction. Walls, floors, occupied grabs and vehicles prevent capture. The victim's suit form is temporary, and other latex creatures cannot attack, transfur or recapture the hidden human.

The default countdown is 30 seconds, with one release negotiation and a WASD escape attempt. Failed negotiation adds ten seconds, capped at 1.5 times the initial duration. Failed escape continues the remaining takeover without permanent transfur, unconscious death or a forced control-mode change. The human's countdown retains its top-centre position and time/outcome label, using Changed's native release-bar appearance and the carrier's species colour. The Instinct carrier also sees the native bottom release bar. Ordinary NPC takeovers use the same top-centre display for their human victim.

Time expiry, the carrier returning to PLAYER, logout, death or a missing/replaced body releases the human locally, removes the temporary suit and clears the movement lock. Release grants at least 60 seconds of protection against another temporary takeover. Operators can disable this feature or change its duration under **Player roleplay** in Server Settings. Normal NPC takeovers retain their existing rules.

Transformed players' own relationship wheel omits **Ask them to vouch**. When options extend beyond the eight visible slots, use the left and right arrow keys to reveal them; the wheel displays this hint only when needed. Scrolling the mouse wheel still switches to the ability wheel.

Temporary companion wrapping does not make you permanently transformed. When TrueTransfur or native origin prevents safe reversal, a companion refuses the reversal rather than attempting a lethal separation. Native origins and the first-entry choice are explained in [Native origins](NATIVE_ORIGINS.md).

With player hypnosis enabled, a player in a suitable hypnotic species can contest a human player's resistance. Both players see the same style of WASD QTE and share its progress: the creature reinforces it while the human tries to escape. Player contests last longer than NPC hypnosis. Patting the player hypnotist briefly prevents them from reinforcing the QTE.

Server owners can disable **Enable Telepathy** under Dialogue. This pauses new unlock progress and replaces telepathic speech with species vocalizations, including for players who already unlocked telepathy, transformed players, hypnosis and takeover. Saved progress returns when the setting is enabled again. A supported, enabled Addon Translator can still translate when its separate support option is enabled. This does not disable hypnosis or takeover gameplay.

Successful transfur negotiation introduces you to the creature once you have been restored. This starts an acquaintance, not a bond or an automatic friendship. Bonded-companion death notifications include the dimension and coordinates where the companion died; if you were offline, the recorded location is shown when you return. Latex samples left by bonded companions also show the companion's species after their name.

## Social advancements

The existing acquaintance, Familiar friendship, Close friendship and first-bond advancements follow your relationship progress. Three additional milestones recognise the new interactions:

- **A Token of Trust:** a full-affection friend accepts your bond token.
- **Together Again:** establish a bond again with a former companion.
- **Easy There:** interrupt a Playful friend's ongoing transfur attempt with a light, bare-handed warning.

Rejected gifts and ordinary attacks do not award these milestones.

For configuration details, token-list syntax and administrator commands, see [Friends, gifts and bonds](RELATIONSHIP_GROWTH.md). `/changedsynergy relationship help` lists the available debug commands. For camp jobs and management, see the [Player camp guide](PLAYER_CAMP_GUIDE.md).
