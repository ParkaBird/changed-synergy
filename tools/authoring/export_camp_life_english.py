"""Export the new household writing as a literature editing pack, without effect parameters."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import json,sys
from camp_life_editing_context import VOICES, context_for, FLOW_GUIDE

ROOT=Path(__file__).resolve().parents[2]

def main():
    destination=Path(sys.argv[1]);destination.mkdir(parents=True,exist_ok=True)
    source=json.loads((ROOT/'tools/authoring/camp_life_dialogue.json').read_text(encoding='utf-8'))
    text=['# Camp life: new English dialogue','', 'Read DIALOGUE-CONTEXT-AND-FLOW.md for the complete sequence and household rules. Every passage below includes its immediate scene and outcome. Existing background stories, books, transformation explanations and Chief role lines remain unchanged.','', 'Entry IDs let revised passages be matched to the game. Keep `%s` placeholders in their current order. These are spoken passages except the two resident gesture descriptions; the reserved resident transfer line is marked below.','']
    exported=[]
    for prefix,(title,overview) in VOICES.items():
        text+=['## '+title,'',overview,'']
        for key,entry in source['lines'].items():
            if not key.startswith(prefix+'.'):continue
            before,purpose,response,outcome,facts=context_for(key)
            prose=''.join(part['text']['en_us'] for part in entry['parts'])
            text+=['### '+key,'','Before this line: '+before,'','What the speaker is doing: '+purpose,'','Current text:','',prose,'','Player’s next move: '+response,'','What follows: '+outcome,'','Keep accurate: '+facts,'']
            if '%s' in prose:
                meaning='the item the player is holding' if key.endswith('.donate') else 'the player’s existing household' if key.endswith('.transfer') else 'the number of free sleeping places, then the total number of sleeping places' if key=='status.beds' else 'the required quantity, then the requested supply category'
                text+=['Inserted information: '+meaning+'.','']
            exported.append(key)
    assert set(exported)==set(source['lines']), 'Every passage needs an English scene card'
    text+=['## Player replies','']
    reply_context={
        'public_requests':'Chief only. Lists public requests; their item hand-ins go to Community Supplies.',
        'personal_requests':'Ordinary resident only. Lists this resident’s own personal requests without accepting one.',
        'personal_submit':'Hands the full held amount directly into the requesting resident’s personal inventory, after server revalidation.',
        'outfit':'Chief only. Member in good standing with local trust at least 40 may receive one outfit per player and home. A full inventory does not spend the claim.',
        'life':'In a Chief’s existing story menu. Opens the household-life greeting.',
        'status':'In the household-life greeting. Opens the successive food, bed, safety and belonging report.',
        'requests':'In the household-life greeting. Shows the shared unfinished requests without accepting one.',
        'donate':'In the household-life greeting when the speaker can accept help. Opens an offer based on the main-hand stack; no transfer happens yet.',
        'meal':'For an eligible home member. Tries to arrange the existing campfire meal; success leads to meal, failure to changed.',
        'membership':'For an existing member talking to a Chief. Returns to the existing membership discussion.',
        'body':'In the Chief’s existing membership/form discussion. Opens the retained explanation of trying that species, including the separate ritual choices.',
        'request':'One unfinished entry in the request list. The first insertion is the quantity and the second the supply category or protection description. Selecting it opens details, not a commitment.',
        'watch_done':'Under a protection request. Tries to report actual defense of a resident; it requires recorded help and a safe camp. Success leads to watch_thanks, otherwise watch_wait.',
        'submit':'Under an item request. Offers the full required amount from the held stack. Success leads to thanks; a failed or stale submission leads to changed.',
        'decline':'Under a request. Leads to declined; the request remains open and the player loses no trust.',
        'give':'Under a donation offer. Inserts the chosen count, then the item name. This is the actual transfer choice, subject to rechecking the held stack, demand and storage room.',
        'confirm_transfer':'After the Chief explains that another formal home is registered. Confirms replacement of that home and continues the previously chosen admission route. It does not replace the separate permanent-body confirmation.',
        'keep_home':'Under that same transfer question. Cancels the pending home change and returns to ordinary conversation.',
        'back':'Leaves the current camp subject for the Chief’s story root or the ordinary resident’s greeting. It accepts no request or donation by itself.',
        'later':'Ends the conversation without a refusal penalty. An unfinished request remains open.',
        'ask_chief':'After an uncomprehended resident’s gesture. Shows seekchief, then lets the player leave and find the Chief; no translation or enrollment is granted.',
    }
    for key,entry in source['words'].items():
        if key.startswith('choice.'):text+=['### '+key,'',reply_context[key.split('.',1)[1]],'','Current reply:','',entry['en_us'],'']
    text+=['## Short resident lines','']
    for key,entry in source['words'].items():
        if key.startswith('dialogue.'):
            context='A resident with an outstanding request has approached an idle home member and stopped nearby. This is a language-aware hail outside the dialogue window. The player may initiate conversation or carry on; no menu opens automatically.' if key.endswith('camp_request_invite') else 'After the player visibly harvests a mature crop and replants the same crop promptly at the same place, a resident can offer this language-aware acknowledgment. It is a brief reaction outside the dialogue window, not a new request or an order to keep farming.'
            text+=['### '+key.rsplit('.',1)[-1],'',context,'','Current line:','',entry['en_us'],'']
    text+=['## Terms inserted into replies','']
    for key,entry in source['words'].items():
        if key.startswith(('supply.','home.')):text+=['- '+key+': '+entry['en_us']]
    text+=['','## Small interface notices','']
    for key,entry in source['words'].items():
        if not key.startswith(('choice.','dialogue.','supply.','home.')):text+=['- '+key+': '+entry['en_us']]
    document=destination/'NEW-CAMP-DIALOGUE-ENGLISH.md';document.write_text('\n'.join(text)+'\n',encoding='utf-8')
    guide=destination/'DIALOGUE-CONTEXT-AND-FLOW.md';guide.write_text(FLOW_GUIDE,encoding='utf-8')
    readme=destination/'README.md';readme.write_text('''# Camp dialogue editing pack

Start with DIALOGUE-CONTEXT-AND-FLOW.md for the setting, character roles, whole conversation sequence and consequences of each kind of choice.

Edit NEW-CAMP-DIALOGUE-ENGLISH.md. It includes all new passages and player replies, with a scene card for every passage: preceding events, the speaker’s purpose, player responses, subsequent outcomes and facts that need to remain accurate. Short resident calls and inserted terms are included at the end.

Keep each entry ID and its placeholders when revising. You may freely rewrite the prose. Preserve the described consent, language, membership and real-item outcomes. Reserved wording is explicitly marked. No effect, color, speed or timing parameters are included.
''',encoding='utf-8')
    archive=destination.parent/'Camp-Life-English-20261104.zip'
    with ZipFile(archive,'w',ZIP_DEFLATED) as output:
        for path in (document,guide,readme):output.write(path,path.name)
    print(archive)

if __name__=='__main__':main()
