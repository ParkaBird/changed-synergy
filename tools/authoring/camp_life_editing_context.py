"""English scene context for literary edits; no presentation parameters."""

VOICES = {
    'weaver': (
        'Weaver — Forest Treehouse',
        'A calm, polite male Silk Moth and the treehouse household’s Chief. His own Research No. 1 describes retaining his human self, learning to live peacefully with the moth voice, and caring for the treehouse together. He is thoughtful and practical; he still has unanswered questions about the wider catastrophe. Neighbors, the upper walkways, woven comforts and the hive beneath the roots are familiar parts of his life. He speaks for this household, not every forest creature.'),
    'matriarch': (
        'Matriarch — Hyena’s Hideout',
        'The protective, polite female Spotted Hyena who leads this hideout. Hyena’s Diary is her account of wandering with companions, building a home and choosing a simpler life with less technology. Keep her open, hearty manner and occasional competitive streak in ordinary camp activities. Protectiveness should sound like concern for actual neighbors. The central fire, cushions, well, fences and savanna trails belong to her daily surroundings; she has no authority over all Hyenas.'),
    'archivist': (
        'Archivist — Observatory Camp',
        'A curious, polite male Eagle Owl and the observatory household’s Chief. Use the existing observatory records and current story tree as background. He lives in the room with the lectern, knows the snowy ridge and neighboring residents, and is at ease talking about observations and small practical matters. His kettle, notes, dome and icy steps give him concrete things to mention. He is active at night. Unsettled ideas such as Dust and an orbital tether are not established history.'),
    'resident': (
        'Ordinary camp residents',
        'Any eligible ordinary resident of the treehouse, hideout or observatory. These lines are not assigned to a new named character. The resident belongs to one household, may have an everyday request, and retains normal species and personality behavior. They can discuss supplies and accept help but do not enroll players or change their formal home. Spoken versions require the player to understand them; merely having this conversation does not teach a language or unlock telepathy.'),
    'status': (
        'Shared camp replies',
        'These replies are spoken by the current Chief or understood resident during a status report. They are shared factual statements, not a fifth NPC’s voice. Read them beside that speaker’s food report, safety report and optional recollection of previous help.'),
}

# Before, purpose, player response, consequence, facts that an edit must retain.
BEATS = {
    'greeting': (
        'With a Chief, the player has chosen “How are things around camp?” from the existing story conversation. With an ordinary resident, the player has initiated a conversation, possibly after the resident approached and hailed them.',
        'Open an everyday exchange. The speaker is available to talk; this does not mean the player has accepted work.',
        'Ask how everyone is doing, ask who needs a hand, offer held supplies, or leave. Eligible home members may suggest a meal; the Chief also lets existing members discuss their place here.',
        'Only the selected subject opens. No supplies move, promise is recorded or membership changes at this greeting.',
        'The player chooses to speak. The resident’s approach never forces a dialogue window open.'),
    'food_low': (
        'The player asks “How is everyone getting on?” The household’s accessible public food supply is low for its current population.',
        'Give a concrete reason spare food would help, while keeping the residents capable of looking after themselves.',
        'The status report continues through sleeping places, safety, membership and any remembered help. The player can then return to the conversation or leave.',
        'Reporting a shortage does not itself start a delivery or penalize the player.',
        'Do not imply starvation is the player’s fault, or that being away causes a trust penalty.'),
    'food_enough': (
        'The player asks about camp conditions and the public food supply is adequate.',
        'Acknowledge the residents’ own work and the ordinary comfort of having enough.',
        'Continue through the rest of the status report, then return or leave.',
        'No delivery is required by this report. A separate request may still concern material supplies or protection.',
        'This is the middle supply condition, not an emergency or a promise of unlimited food.'),
    'food_plenty': (
        'The player asks about camp conditions and there is a generous public food supply.',
        'Let abundance feel like room for company or a meal.',
        'Finish the status report. The player may later choose the separate shared-meal option if entitled to use it.',
        'This line does not start the meal or create food.',
        'A meal remains a separate choice; residents join when free and eat real provisions.'),
    'safe': (
        'This follows the food and sleeping-place reports. No resident is currently engaged against a live non-livestock target.',
        'Describe the household’s present calm in the speaker’s own surroundings.',
        'The report continues with the player’s member/guest position and, if applicable, a remembered contribution.',
        'This is information only. A protection request may still be listed, waiting for actual evidence of help.',
        'Present quiet does not certify that the whole biome is permanently safe.'),
    'danger': (
        'This occupies the safety part of the status report because a resident is currently dealing with a live non-livestock threat.',
        'Put the neighbors’ immediate safety ahead of relaxing or gathering for food.',
        'The player may end the conversation to help, or finish listening and return to other topics.',
        'The line does not direct residents to abandon their existing fight. Combat may end a dialogue session.',
        'Do not invent a particular attacker, injury or casualty; the current threat varies in play.'),
    'remember': (
        'Near the end of a status report, the household has a recorded contribution from this player.',
        'Recognize earlier help without turning belonging into an endless obligation.',
        'Return to the conversation or say goodbye.',
        'Acknowledgment itself pays no new reward and imposes no fresh task.',
        'The remembered help need not have happened today. The player can come home empty-handed.'),
    'requests': (
        'The player chooses “Does anyone need a hand?”',
        'Introduce the household’s shared list of unfinished requests.',
        'Choose one listed request to hear details, return to the conversation, or leave. If none are outstanding, the no-requests reply follows.',
        'Reading the list does not reserve work or bind the player. Another player may help first.',
        'Requests have no deadline or punishment for refusal. This is not a player-issued work order.'),
    'no_requests': (
        'The requests introduction has just been shown, but there are no unfinished entries to offer.',
        'Make a quiet visit feel welcome without manufacturing a chore.',
        'Return to other conversation topics or leave.',
        'No new request is created merely because the player asked.',
        'The speaker reports this household’s current list, not the absence of all needs everywhere.'),
    'handled': (
        'The player selected a request that became unavailable before its details were delivered, usually because someone else completed it.',
        'Acknowledge that the help is already taken care of.',
        'Return to other topics or leave; the player may ask for the updated list.',
        'The player does not hand over goods and receives no duplicate completion reward.',
        'Do not accuse the player of being late or breaking a promise.'),
    'watch': (
        'The player selected a request to help protect the neighbors.',
        'Explain that help concerns a real threat to residents, not seeking combat for its own sake.',
        'Report “The neighbors are safe. I helped with the trouble,” decline for now, return, or leave.',
        'A report succeeds only after the player actually helped against a hostile targeting a camp resident and the camp is now safe. Otherwise watch_wait follows. A successful report receives watch_thanks.',
        'Ordinary livestock is protected. An uneventful visit is welcome, but cannot be claimed as a completed defense.'),
    'request': (
        'The player selected an unfinished request for food or a household material.',
        'State the full quantity and supply category and explain that it is for shared household use.',
        'Offer “I have the full amount here,” decline, return, or leave. Submission is available only while the speaker can accept help from the player.',
        'A valid hand-in moves real held items into public storage, completes that shared request and leads to thanks. Stale requests, wrong items, insufficient quantity or lack of room lead to changed.',
        'Keep the quantity and material placeholders. There is no deposit, deadline or exclusive reservation.'),
    'choose_item': (
        'The player chose to share supplies, but the held stack does not qualify as food or one of the accepted household materials.',
        'Explain how to show a specific offer without implying access to the player’s whole pack.',
        'Return or leave, put acceptable supplies in the main hand, and ask about donating again.',
        'Nothing is taken at this stage.',
        'Named items and bound character equipment are not ordinary donations.'),
    'donate': (
        'The player chose “I brought something to share” while holding an acceptable stack.',
        'Recognize the item and ask how much the player wishes to leave.',
        'Choose from the quantities offered from that held stack, return, or leave.',
        'Confirmation rechecks the actual item, quantity, need and storage room. Accepted goods go to public stores and lead to thanks; a changed offer or unsuitable state leads to changed.',
        'A quantity choice has not yet moved anything. Acknowledging the item is not a guarantee every amount can be stored.'),
    'transfer': (
        'A Chief has been asked to enroll the player, or carry out an admission ritual, while another household is still the player’s formal home. This confirmation appears before that action is committed.',
        'Name the present home and ask whether the player wants this household to replace it.',
        '“Yes. I want to make this my home” continues the pending admission. “I will keep my present home” cancels the change. Leaving also leaves the current home intact.',
        'A successful confirmation changes the formal home and completes only the route the player had already chosen. Failed eligibility leaves it uncommitted. Old friendships, local trust and paid reward history remain.',
        'Only one formal home is allowed. Do not imply migration removes the old friends, erases old trust, reverses a permanent body or lets an ordinary resident process admission.'),
    'thanks': (
        'A donation or material request has just been accepted and its real items have reached the household’s public storage.',
        'Thank the player in the speaker’s voice and allow the exchange to settle.',
        'Return to the conversation or say goodbye.',
        'A qualifying new donation can add one local trust point; a material request can add two, within the household’s daily contribution allowance. Returning borrowed supplies first repays what was taken.',
        'Do not promise a coin payment, a promotion or a reward for the same supplies twice. This reply does not itself start a meal.'),
    'declined': (
        'After hearing a specific request, the player says they cannot help right now.',
        'Accept the answer without guilt or pressure.',
        'Return to the conversation or leave.',
        'The request remains available to others. No trust is lost and nothing is taken.',
        'Do not turn the refusal into a debt or imply the player must return by a deadline.'),
    'changed': (
        'An attempted donation, request hand-in, meal invitation or home confirmation failed its current checks after the player selected an action.',
        'Explain that the exchange cannot be settled right now and invite a practical recheck.',
        'Return to the conversation or leave, then retry the relevant topic if desired.',
        'A normal rejected transfer takes no goods and completes no request. A storage provider that unexpectedly accepts only part of an offer keeps only that real part, charges only that amount and leaves the remaining request unfinished.',
        'This is a shared fallback: do not assert one specific failure cause or claim that completion, membership or a meal has definitely occurred.'),
    'meal': (
        'An eligible home member suggested eating together. There is food, a usable lit campfire, no current camp fight, and the household is ready for another meal invitation.',
        'Invite company around the fire without ordering everyone to drop what they are doing.',
        'Return or leave; the player can walk to the fire and decide whether to sit or eat.',
        'Available residents enter their existing meal routine, find seats and consume actual food. No one is teleported into place.',
        'A sleeping, busy or otherwise unavailable neighbor need not join. The player is not automatically seated or fed.'),
    'watch_thanks': (
        'The player’s protection report was accepted: their real help against a threat to a resident was recorded, and the camp is safe now.',
        'Recognize that the player stood with the neighbors.',
        'Return or leave.',
        'The shared protection request is completed. The combat assistance has already earned its eligible local trust; reporting it does not pay the trust award twice.',
        'Do not guarantee that no future danger will return or invent an injury, kill count or rescue scene.'),
    'watch_wait': (
        'The player tried to report a protection request, but the camp is still unsafe or there is no recorded qualifying help for this player.',
        'Keep the situation open without encouraging the player to manufacture a fight.',
        'Return or leave. The player may help if an actual threat occurs and report again once it is settled.',
        'No completion or additional reward is recorded.',
        'This may occur on a completely quiet day. Do not always claim someone is still fighting.'),
    'beds': (
        'Within a status report, this follows the food reply and comes before the safety reply.',
        'Report the currently free and total sleeping places and respect resting neighbors.',
        'Listen to the rest of the status report, then return or leave.',
        'The report grants no new sleeping permission. Members have shared rights when in good standing; respected visitors may also use an appropriate free place.',
        'The first placeholder is free places, the second total places. Body suitability, occupancy and the normal sleep schedule still apply.'),
    'member': (
        'Near the end of a status report, this player is registered to this household.',
        'Describe belonging and the boundary between shared goods and private possessions.',
        'Listen to any remembered-help reply, then return or leave.',
        'No new admission reward is paid. Existing rules still suspend shared rights if local trust has fallen too far.',
        'Membership in this home does not make every creature or every other camp an ally.'),
    'guest': (
        'Near the end of a status report, the player is visiting rather than registered to this household.',
        'Welcome ordinary company and point to the Chief if the player wants a formal home.',
        'Return or leave. Joining is handled through the Chief’s existing membership branch.',
        'Visiting or helping does not automatically enroll the player.',
        'Guests may contribute. Do not promise unrestricted access to private stores or beds.'),
    'gesture': (
        'The player initiated a conversation with an ordinary resident whose language they cannot understand.',
        'Describe a soft call, attention toward supplies and a pause for the player’s response.',
        'Choose to ask the Chief for an explanation, or leave.',
        'No request is accepted, translated or completed automatically.',
        'This is descriptive text for the player, not intelligible human speech from the resident.'),
    'seekchief': (
        'After seeing the resident’s gestures, the player chooses “I should ask the chief what you mean.”',
        'Give a clear way to seek an explanation without inventing newly understood speech.',
        'Leave and find the Chief to ask about the household’s requests. This does not summon or teleport the Chief.',
        'The player remains free of any commitment and retains the normal language barrier.',
        'Treat this as explanatory text. Do not teach telepathy, assign a task or move items.'),
}

def context_for(key):
    voice,beat=key.split('.',1)
    tone=''
    for variant in ('playful','cautious','polite'):
        if beat.startswith(variant+'_'):
            tone=variant;beat=beat[len(variant)+1:];break
    if beat=='away':
        return ('A bonded ordinary resident is away from the original home when the player opens camp dialogue.', 'Keep the personal connection while postponing household business.', 'Say goodbye; speak again at home for supplies and requests.', 'No remote transfer, request completion or membership change occurs.', 'An adopted companion keeps camp identity; explicitly following the owner takes priority over home routines.')
    if beat=='outfit':
        return ('A member in good standing with local trust at least 40 chose to request the household outfit from its Chief.', 'Present a practical welcome gift in this Chief’s voice.', 'Return to other topics or leave.', 'One ordinary copy of the household outfit has reached the player’s inventory. This home records the claim permanently for this player.', 'Leaving, rejoining or the Chief returning cannot grant another copy. No bound equipment is removed from the Chief.')
    if beat=='outfit_changed':
        return ('The requested clothing gift could not be granted after the player selected it.', 'Allow the player to make room or recheck eligibility.', 'Return or leave and retry when possible.', 'A full inventory does not consume the unclaimed gift.', 'This line must not imply that the outfit has already been delivered.')
    result=list(BEATS[beat])
    if voice=='resident' and beat in ('requests','no_requests','request','thanks','changed'):
        result={
            'requests': ['The player asks this ordinary resident whether they personally need anything.', 'Introduce only this resident’s own unfinished request.', 'Hear its details, return or leave.', 'Opening the list accepts nothing.', 'Public household requests are handled by the Chief.'],
            'no_requests': ['This resident currently has no unfinished personal request.', 'Welcome company without inventing work.', 'Return or leave.', 'No goods move and no request is created.', 'Other residents or the household may still need help.'],
            'request': ['The player selected this resident’s personal food request.', 'Explain what this individual would like to carry for themselves.', 'Hand the full matching amount directly to this resident, decline, return or leave.', 'A successful hand-in puts real held items into the requesting resident’s personal inventory, completes their request and leads to thanks. Failure leads to changed.', 'Keep the quantity then category placeholders. These goods do not go into Community Supplies. A full public reserve does not block a personal hand-in.'],
            'thanks': ['This resident’s personal hand-in or a separate explicit public donation has just succeeded.', 'Thank the player without misidentifying the destination.', 'Return or leave.', 'Personal requested goods remain with the requesting resident; an explicit donation still goes to the reserve. Eligible trust and affection are recorded once.', 'Avoid claiming every gift is communal: this shared reply can follow either kind of help.'],
            'changed': ['This resident could not finish the selected hand-in, donation or shared meal.', 'Invite a practical recheck without asserting the wrong cause.', 'Return or leave and retry if desired.', 'No successful request completion is recorded; any inconsistent partial item-handler transfer is charged only for its real accepted portion.', 'Personal request capacity means this resident’s inventory; public donation capacity means Community Supplies.'],
        }[beat]
    if voice=='resident' and beat=='transfer':
        result[0]='Reserved wording. The current conversation routes home-transfer confirmation through Chiefs only; ordinary residents do not display this line.'
        result[2]='An ordinary resident can direct the player to the Chief, who handles the actual choice.'
        result[3]='This line currently commits no action and has no active branch.'
    if voice=='resident' and beat in ('watch','watch_thanks','watch_wait'):
        result[0]='Reserved ordinary-resident wording. Public protection requests are currently offered and reported through Chiefs only; residents offer their own personal item requests.'
        result[3]='This resident variant has no active protection-report branch. The corresponding Chief variants remain in use.'
    if tone:
        result[1]+=' This replaces the ordinary resident’s '+beat+' when their '+tone+' trait supplies the conversation tone; it adds no new branch or reward.'
    return result

FLOW_GUIDE = '''# Camp dialogue: context and flow

This pack contains the new household-life additions. The existing character introductions, authored books, background stories, form explanations, permanent-transformation confirmation and Chief role lines remain in the game. Read the additions as everyday conversations beside those stories. The laboratory Researcher has no new household-recruitment branch in this round.

## The player’s place

The treehouse, hideout and observatory are separate homes. Their Chiefs speak for the neighbors who live there. A player can have one formal home and still visit or help the other two. The player joins as an ordinary resident, with no management rank. The residents gather, eat, sleep and defend themselves independently. Refusing help, leaving for a trip or returning without a gift has no absence or upkeep penalty.

Local trust belongs to one household. Regional faction standing, the player’s birth background, personal affection and bonds are separate. Helping this hideout does not enroll the player in every Hyena group. Existing friendships and previous contributions survive leaving or changing homes.

## How the conversations begin

A Chief’s existing story menu gains “How are things around camp?” This opens the greeting, then offers status, requests, donations and, for eligible home members, a shared meal. Existing members can also discuss their place in the home. A member can revisit the existing explanation of trying the Chief’s form.

An ordinary resident can be approached through the social interaction menu. A resident with a request can also walk toward an idle home member, stop nearby and hail them. The player still opens the conversation. The invitation is paced so residents do not queue up around the player; fighting, sleep or another interaction interrupts it.

The Chiefs have learned human speech. Ordinary residents keep their normal language rules. If the player cannot understand one, use the gesture description and the suggestion to ask the Chief. Those descriptions do not grant understanding or silently accept a task. No new camp passage is a hidden thought. Preserve the current story tree’s existing treatment of spoken language and understood thoughts.

## Status report, in order

1. The speaker gives one of the low, enough or plentiful food replies.
2. The shared sleeping-place line gives the current free count and total count.
3. The speaker gives the current calm or danger reply.
4. The shared member or guest reply explains the player’s position.
5. If the player has helped before, the speaker may remember it.

These are successive beats of one status inquiry. They do not need to repeat a greeting or ask a new question at each step. A shortage is an observation; it is not a request the player has already accepted. “Safe” describes current resident combat, not permanent protection of the whole landscape.

## Requests and their endings

With a Chief, the requests choice opens the public household list. With an ordinary resident, it opens only that individual’s personal request. Public hand-ins go to Community Supplies; personal hand-ins go directly into that requesting resident’s real personal inventory, even if the reserve is full.

The public list contains up to three unfinished requests. One request concerns food when needed; household materials are wool at the treehouse, leather at the hideout and paper at the observatory. A protection request asks the player to help against real threats to residents. The list can be empty. Requests do not expire and are not reserved to the first person who reads them.

For an item request, the player hears the amount and category, then may claim to have the full amount, decline or leave. A real hand-in requires the matching supplies in the player’s main hand and space in the correct destination: the public reserve for public requests, or the requesting resident’s personal inventory for personal requests. Accepted goods reach that destination before the thank-you. The request then belongs to everyone’s completed history, so another player cannot hand it in again.

If the request disappears before its details are shown, the handled reply explains that someone took care of it. If a selected submission cannot be completed, changed is the general fallback. That can mean the held stack changed, the count is insufficient, the store is full or another player already helped. The fallback should not confidently name a cause the speaker cannot distinguish. An ordinary refusal transfers nothing; an unusual storage provider may have accepted only part, in which case only that part leaves the player’s hand and the remainder stays outstanding.

For protection, asking for help does not spawn an attacker or require a kill count. Actual assistance against a hostile targeting a resident records the evidence. A later report succeeds when that household is safe. watch_thanks closes it; watch_wait leaves it open if evidence is missing or danger remains. A quiet day can produce watch_wait too. Never suggest attacking livestock or picking a fight just to satisfy the request.

Declining leaves the request available to others and costs no trust. The NPC may ask another neighbor.

## Donations and contribution

“I brought something to share” opens an offer based on the player’s main-hand stack. If it qualifies, the speaker asks how much to leave and the player chooses a quantity. If it does not, choose_item asks the player to show a suitable item and come back. Accepted categories are ordinary food, wool, leather, paper and torches, subject to actual household demand. Named items and bound default character equipment are not supplies.

A donation is voluntary. The final choice rechecks the item and available space; the initial recognition of an item has not yet accepted it. Goods go to public stores. A qualifying new donation adds one local trust point and a completed material request can add two, subject to the contribution allowance. Returning supplies previously borrowed from the same household first repays that withdrawal rather than earning trust for recycling the same items. The thank-you can still be sincere when no further points are available.

Residents may also appreciate a mature crop being harvested and promptly replanted, or real defense of a neighbor. Such help can produce the short thanks line outside the dialogue window. The player does not need a management screen or an active quest to help.

## Shared meals

A home member may ask “Shall we eat together?” The invitation succeeds when there is food, a lit usable campfire, no present camp fight and the household is ready to gather again. The meal reply follows a successful invitation. Free residents use their normal meal routine; busy or sleeping neighbors need not come. Food is consumed from real supplies. The player walks over and chooses whether to sit or eat.

An unavailable meal uses changed, the shared unsuccessful-action reply. Keep that reply broad enough to cover a delayed meal as well as a changed delivery. It must not promise that everyone is already seated or that a meal has been conjured.

## Membership and changing homes

The existing Chief membership conversation remains the place to ask to stay, discuss the rules, leave or consider a form change. Neutral local trust and no immediate hostility permit admission. Keeping a human body, arriving already transformed, choosing a reversible ritual and choosing a TT permanent ritual use the retained admission rewards of 0, 2, 5 and 8 local points. Previously paid history is preserved; only an unpaid improvement can be awarded.

If the player already belongs to another home, the new transfer reply appears before admission or its chosen ritual is committed. The old household’s name fills the placeholder. Confirming continues the route the player chose. Keeping the current home cancels it. A permanent ritual still requires its existing separate, explicit spoken confirmation and TT support; transfer consent does not replace consent to a permanent body. Failed checks do not grant membership or pay a reward.

Leaving does not reverse a body or erase relationships and trust. Shared stores and suitable unoccupied beds belong to members in good standing. Private storage and bound character equipment remain protected. Lecterns and other readable story media are freely accessible. Local distrust can suspend shared rights and real hostility can end membership; neither is caused by an ordinary absence.

## Clothing, bonds and repaired home facilities

A home member in good standing with local trust at least 40 may ask that Chief for one household outfit. The grant is one ordinary item, not the Chief’s bound equipment. Full player inventory allows a later retry. Claims survive leaving and rejoining, and are separate for each player and home.

Ordinary residents may be campmates and bonded companions at the same time. Both badges can appear. An actively following companion follows the owner; at home in wander mode, camp routines may resume. Away from home, their camp conversation gives a brief personal reply without remote supply transactions. Fixed Chiefs retain their existing bond and body-takeover restrictions.

The original two reserve positions persist once registered. Replacing ordinary chests at those same positions restores their public function without recreating destroyed contents. Suitable beds and perches anywhere inside the structure bounds may be used, including player replacements. Species suitability, occupancy and sleep schedule still apply.

## Reading and editing individual entries

Each language’s dialogue document includes a scene card for every new passage: what happened before it, why the speaker says it, what the player can answer and what follows. The source IDs identify passages; they are not spoken. The resident’s transfer line is reserved wording and is marked as such rather than presented as a live branch.

Keep each entry ID and the number and order of its %s placeholders. Their meanings are explained beside the relevant text. These insert names or quantities during play. The actual prose can be rewritten freely while the described consent, language, item and membership outcomes stay accurate. There are no animation, color, speed or timing parameters in this pack.
'''
