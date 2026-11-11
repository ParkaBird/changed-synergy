"""Export an English writing brief from the actual templates and current dialogue, without altering them."""
from pathlib import Path
import hashlib,html,json,shutil,sys,zipfile
import nbtlib as nbt

ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
sys.path.insert(0,str(ROOT/'tools/authoring'))
from export_story_npc_english import CONDITIONS,BRANCHES

CONSEQUENCES={'join':'Register membership in this household; keep the present body.',
              'reversible':'Copy the Chief’s form through the reversible route and register membership.',
              'permanent':'Commit the confirmed permanent Native Origin body and register membership; TrueTransfur support is required.',
              'leave':'End this household membership without resetting relationships or changing the body.',
              'close':'End the conversation.'}
def text_component(value):
    if isinstance(value,str):
        try:return text_component(json.loads(value))
        except (ValueError,TypeError):return value
    if isinstance(value,list):return ''.join(map(text_component,value))
    if isinstance(value,dict):return str(value.get('text',value.get('translate','')))+''.join(text_component(v) for v in value.get('extra',[]))
    return str(value)
def extracted(template):
    records=[]
    def walk(value,path):
        if hasattr(value,'items'):
            for k,v in value.items():walk(v,path+'/'+k)
        elif isinstance(value,list):
            for i,v in enumerate(value):walk(v,path+'/'+str(i))
        elif isinstance(value,nbt.String) and str(value).strip() and ('/pages/' in path or '/messages/' in path or path.endswith(('/Text','/title','/author'))):
            records.append({'location':path,'raw':str(value),'text':text_component(str(value))})
    for block in template['blocks']:
        if 'nbt' in block:walk(block['nbt'],'block '+','.join(map(str,map(int,block['pos']))))
    for e in template['entities']:walk(e['nbt'],'entity '+str(e['nbt'].get('id',''))+' @ '+','.join(str(float(v)) for v in e['pos']))
    return records
def lore(path,template):
    records=extracted(template);parts=['# Authored texts\n\nThese are extracted verbatim from the supplied structure. A location does not establish who wrote a text. Blank decorative clipboards have no hidden transcript in this export.\n']
    for r in records:parts.extend(['\n## '+r['location']+'\n\n',r['text'],'\n'])
    path.write_text(''.join(parts),encoding='utf-8');path.with_suffix('.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8');return records
def category(name):
    name=name.split(':')[-1]
    if name=='air':return 'air','#f4efe0'
    if name=='water':return 'water','#91bcc5'
    if 'bed' in name:return 'bed','#a8bb95'
    if name in ('chest','barrel') or 'cardboard' in name or 'container' in name:return 'storage','#c59461'
    if any(s in name for s in ('carrot','potato','wheat','beetroot','farmland','leaves','grass')):return 'garden','#9db779'
    if any(s in name for s in ('door','ladder','stairs','hatch')):return 'access','#d6bd68'
    if any(s in name for s in ('table','chair','computer','clipboard','lectern','flask','canister','stasis')):return 'furniture','#b59cb6'
    if 'glass' in name:return 'glass','#c4d6d0'
    if any(s in name for s in ('wool','carpet')):return 'floor','#c4b49a'
    return 'solid','#706f69'
def floorplan(template,y,destination):
    palette=template['palette'];blocks={tuple(map(int,b['pos'])):b for b in template['blocks']};cell=18;svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="900" height="860" viewBox="0 0 900 860"><rect width="900" height="860" fill="#f4efe0"/><g font-family="sans-serif" fill="#272c2e"><text x="36" y="28" font-size="20">Desert Refuge: local Y={y}</text><text x="36" y="49" font-size="12">North is up. X increases right; Z increases down. Hover over a cell for its block.</text>']
    for z in range(39):
        for x in range(36):
            block=blocks.get((x,y,z));name=str(palette[int(block['state'])]['Name']) if block else 'minecraft:air';kind,color=category(name)
            svg.append(f'<rect x="{36+x*cell}" y="{70+z*cell}" width="18" height="18" fill="{color}" stroke="#f4efe0" stroke-width=".4"><title>{x},{y},{z}: {html.escape(name)}</title></rect>')
    for i,e in enumerate(template['entities']):
        if not e['nbt'].get('ForgeData',{}).get('ChangedSynergyStructureResident'):continue
        x,ey,z=map(float,e['pos'])
        if abs(ey-y)>1:continue
        label='C' if e['nbt']['id']=='changed_synergy:latex_jerboa' else str(i)
        svg.append(f'<circle cx="{36+x*cell}" cy="{70+z*cell}" r="9" fill="#bf493d"/><text x="{36+x*cell}" y="{74+z*cell}" font-size="11" fill="white" text-anchor="middle">{label}</text>')
    for i in range(0,36,5):svg.append(f'<text x="{38+i*cell}" y="64" font-size="10">{i}</text>')
    for i in range(0,39,5):svg.append(f'<text x="13" y="{83+i*cell}" font-size="10">{i}</text>')
    for i,name in enumerate(['air','solid','water','bed','storage','garden','access','furniture','glass','floor']):
        color={'air':'#f4efe0','solid':'#706f69','water':'#91bcc5','bed':'#a8bb95','storage':'#c59461','garden':'#9db779','access':'#d6bd68','furniture':'#b59cb6','glass':'#c4d6d0','floor':'#c4b49a'}[name]
        svg.append(f'<rect x="720" y="{80+i*28}" width="15" height="15" fill="{color}" stroke="#706f69"/><text x="744" y="{92+i*28}" font-size="13">{name}</text>')
    svg.append('<text x="720" y="390" font-size="12">C = Pathfinder</text><text x="720" y="410" font-size="12">Other numbers match</text><text x="720" y="426" font-size="12">the resident source index.</text></g></svg>');destination.write_text(''.join(svg),encoding='utf-8')
def transcript(profile,words,destination):
    out=['# '+words['npc.changed_synergy.'+profile+'.title']+' — current English dialogue\n\nReference only. Preserve the existing character unless a separate rewrite is requested. Spoken and mental passages retain their order.\n']
    graph=json.loads((RES/f'data/changed_synergy/story_npcs/{profile}.json').read_text(encoding='utf-8'))
    for name,node in graph['nodes'].items():
        out.append('\n## '+BRANCHES.get(name,name.replace('_',' ').title())+'\n\n')
        for line in node['lines']:
            context=CONDITIONS.get(line.get('condition','always'),line.get('condition','always').replace('_',' '));out.append('['+line.get('channel','spoken')+'; '+context+']\n\n')
            beats=['']
            for part in line['parts']:
                if part.get('beat') and beats[-1]:beats.append('')
                beats[-1]+=words[part['text']]
            out.append('\n\n'.join(beats)+'\n\n')
        if node['choices']:
            out.append('Player replies:\n\n')
            for c in node['choices']:
                notes=[]
                if c.get('condition','always')!='always':notes.append(CONDITIONS.get(c['condition'],c['condition'].replace('_',' ')))
                if c.get('requires'):notes.append('After completing '+ ' and '.join(BRANCHES.get(k,k.replace('_',' ')) for k in c['requires']))
                if c.get('effect') in CONSEQUENCES:notes.append(CONSEQUENCES[c['effect']])
                target=BRANCHES.get(c['next'],c['next'].replace('_',' ').title())
                out.append('- '+words[c['text']]+' → '+target+(' ('+'; '.join(notes)+')' if notes else '')+'\n')
    out.append('\n## Chief role lines\n\n')
    for context in ('welcome','care','organize','warning'):
        out.append('### '+{'welcome':'Welcoming','care':'Caring','organize':'Organizing','warning':'Warning'}[context]+'\n\n'+words[f'dialogue.changed_synergy.context.chief.{profile}.{context}']+'\n\n')
    destination.write_text(''.join(out),encoding='utf-8')
def main():
    destination=Path(sys.argv[1]);destination.mkdir(parents=True,exist_ok=True);maps=destination/'maps';maps.mkdir(exist_ok=True);refs=destination/'reference_chiefs';refs.mkdir(exist_ok=True);raw=destination/'source_reference';raw.mkdir(exist_ok=True)
    words=json.loads((RES/'assets/changed_synergy/lang/en_us.json').read_text(encoding='utf-8'))
    template=nbt.load(RES/'data/changed_synergy/structures/desert_refuge.nbt');shutil.copy2(RES/'data/changed_synergy/structures/desert_refuge.nbt',raw/'desert_refuge.nbt');records=lore(destination/'DESERT_AUTHORED_TEXTS.md',template)
    for y in [3,7,14,20,25,30]:floorplan(template,y,maps/f'floor-{y:02}.svg')
    (maps/'index.html').write_text('<!doctype html><html lang="en"><meta charset="utf-8"><title>Desert Refuge floor plans</title><style>body{background:#eee9dd;font:17px system-ui;margin:24px}nav a{margin-right:18px}iframe{border:0;width:930px;height:880px}</style><h1>Desert Refuge</h1><p>Local coordinates; Y=25 is the structure’s terrain anchor. The lower rooms are underground.</p><nav>'+''.join(f'<a href="floor-{y:02}.svg" target="plan">Y={y}</a>' for y in [3,7,14,20,25,30])+'</nav><iframe name="plan" src="floor-20.svg"></iframe></html>',encoding='utf-8')
    residents=[]
    for i,e in enumerate(template['entities']):
        tag=e['nbt'];home=tag.get('ForgeData',{}).get('ChangedSynergyStructureResident')
        if home:residents.append({'source_index':i,'species':str(tag['id']),'position':list(map(float,e['pos'])),'fixed_npc':bool(home.get('Character',0))})
    (raw/'residents.json').write_text(json.dumps(residents,indent=2),encoding='utf-8')
    for profile,structure in [('canopy_chief','forest_treehouse'),('hyena_chief','hyena_hideout'),('observatory_chief','observatory')]:
        folder=refs/profile;folder.mkdir(exist_ok=True);transcript(profile,words,folder/'CURRENT_DIALOGUE.md');lore(folder/'AUTHORED_TEXTS.md',nbt.load(RES/f'data/changed_synergy/structures/{structure}.nbt'));shutil.copy2(RES/f'data/changed_synergy/story_npcs/{profile}.json',folder/'dialogue-map.json')
    camp={k:v for k,v in words.items() if k.startswith('camp.changed_synergy.') or k.startswith('dialogue.changed_synergy.context.chief.') or k.startswith('dialogue.changed_synergy.context.camp_')}
    (raw/'camp-and-chief-english.json').write_text(json.dumps(camp,ensure_ascii=False,indent=2),encoding='utf-8')
    passages=['# Current resident and shared camp lines\n\nThese English lines already exist. They are reusable reference material, not a completed new desert script. `%s` marks a value supplied by the game; keep it when rewriting. The source key is retained so an editor can identify the corresponding line. References to a campfire belong to other existing communities; the Desert Refuge has no campfire, so a future desert version should use its common room or another observed setting.\n']
    for key,value in camp.items():
        if '.resident.' in key or '.context.camp_' in key:passages.append('\n## '+key+'\n\n'+value+'\n')
    (destination/'RESIDENT_AND_SHARED_LINES.md').write_text(''.join(passages),encoding='utf-8')
    brief='''# Desert Refuge dialogue design package

Start with this document, then read STRUCTURE_AND_CAST.md and DESERT_AUTHORED_TEXTS.md. The maps can be opened through maps/index.html. The three established Chiefs are references in reference_chiefs; their full English dialogue and the books in their own buildings are included.

## What is being designed

A small household lives in a buried desert facility. A newly added Latex Jerboa holds the Chief role and wears the indigo Oasis Pathfinder Outfit. The character is called **Pathfinder**, a position label rather than a personal name. No desert Chief dialogue is installed yet. The five original residents retain their ordinary camp interactions.

Write a voice and a dialogue tree for this household, including its ordinary residents. The Chief speaks for these neighbors, not for every desert creature or an entire regional faction. Give practical topics a place beside personal conversation: the underground garden, shared supplies, keeping the rooms usable, relationships among the residents, and what they choose to remember about the facility.

## Established facts and open decisions

- The Jerboa is a playable desert form. It has lower health and attack than an ordinary wolf-like latex creature, slightly faster movement, and the same short-tap / held-charge jump used by Muntjacs. Its ears react to movement and quick head turns.
- The placed Chief has Tall turquoise eyes, the Pathfinder outfit, and a provisional Cautious + Polite personality preset. This is a starting point for characterization; there is no authored personal diary or fixed biography for the character.
- The Chief is a persistent character tied to this one structure. It may participate in camp routines and sleep in available beds. It does not become a traveling bonded companion or lend its body to the player. Death or confirmed disappearance schedules a safe delayed return; do not turn that implementation safeguard into resurrection lore without a separate decision.
- The five ordinary residents have randomized eyes and runtime personalities. They are not five prewritten, named immortal NPCs. Their names, ages, life histories, relationships and specialist jobs have not been fixed.
- Keep any proposed history, personal name, belief, or connection to the old experiment clearly marked as a proposal. The experiment record does not identify the Chief or any current resident as its subject or author.

The clothing draws on desert linen, woven headwear, rust red and indigo fabric, metal fastenings, a compass pouch and a water gourd. These are visible design details. They do not establish a real-world nationality, religion, historical date, hydration meter or navigation power.

## Scope of the next writing pass

Useful Chief topics include a first encounter, finding one's way through the refuge, its present occupants, daily work, the old equipment and records, limits on private storage, joining this particular household, changing home, leaving, and returning after time away. A personal subject can follow once the visitor has learned who the speaker is and how the household lives; the other Chiefs already use that progression.

For residents, build short exchanges around a concrete situation: a task interrupted, a meal being prepared, a crop ready to harvest, a missing supply, an item given directly to its requester, a repaired bed, or a visitor opening someone else's things. Let the species and room influence a voice without making all snakes, foxes or lizards share one personality. Treat any fixed resident biography as a new proposal.

The installed desert Chief intentionally offers no dialogue, admission or transformation route until the writing is ready. Do not treat the reference trees as a desert script that is already active.

## Camp rules that dialogue must respect

Membership belongs to one local community and one home. It does not replace a player's biological origin, regional reputation, individual affection or bond. Ordinary residents can be both campmates and bonded companions. Chiefs retain their fixed-character restrictions.

The household has a separate public reserve. General contributions go there; personal requests are handed directly to the resident who asked. Residents use the reserve for communal supplies and activity proceeds. Loot containers are separate. Private belongings remain private, while books, lecterns and other story carriers can be read. Warnings depend on someone actually witnessing an intrusion. A first warning is not an automatic attack on a campmate; repeated intrusion can damage local trust.

Beds may be original or placed by a player within the structure. Replacing a bound reserve chest at its recorded position can restore its role. Joining introduces the ordinary residents to the player without creating an automatic bond. Trusted members of an established community can receive its clothing through its Chief.

Where the future tree offers transformation, distinguish staying as one is, a reversible copied form, and a permanent form supported by TrueTransfur. A permanent choice needs a separate, plain second confirmation. It establishes a Native Origin body and blocks ordinary reversal; the Native Origin selector remains the way to choose a human body again. Leaving the community does not undo a permanent body. Never frame the rite as consuming or replacing the Chief.

## Voice and presentation

Use natural English and specific observations. The established examples are included to show voices rooted in a place, not to provide phrases to repeat. Avoid making every line a speech, a joke or a lesson about the world.

Spoken words appear a character at a time. Mental speech appears as a whole thought and fades in alongside the spoken passage, shown with a line through it. Do not add parentheses or labels such as “Thought.” Only include mental content when it adds a reaction the spoken line leaves unsaid. Visitors who cannot understand it still need the practical information in speech.

Break a passage where the speaker changes thought, leaves a meaningful pause, reacts, or gives the player room to respond. Length alone should not dictate every break. Choices sit above the bottom dialogue frame. There are no timed replies or interruption branches.

Describe emotional intent in short editorial notes tied to the words that need it. Available treatments include gentle emphasis, a small wave, restrained trembling, a brief impact, a soft pulse, a droop, a float, a wobble and a bounce. Calm sentences can remain still. Fear may tremble briefly; a surprise can land on one phrase; excitement can move more freely. Do not animate every word, and do not use shaking as the only sign of emotion. The game also supports reduced motion.

## Suggested writing delivery

Provide an English tree with each exchange's situation, what the player knows, the speaker's immediate concern, spoken text, optional mental text, the player's replies, and the consequence or next subject. Explain prerequisites in words. Keep implementation numbers and animation strength settings out of the literary draft. Preserve any `%s` substitutions in shared lines and list the intended value beside them.

Separate the Chief's script, the five resident proposals, reusable shared reactions and uncertain worldbuilding. Changes to existing Chiefs are outside this package's implementation scope; use them as references unless the user separately requests a rewrite.

## Files

- STRUCTURE_AND_CAST.md: observed rooms, positions and cast, with interpretation limits.
- DESERT_AUTHORED_TEXTS.md: the complete currently stored text, including Subject X-1221.
- RESIDENT_AND_SHARED_LINES.md: existing English resident and shared camp reactions.
- reference_chiefs/: full English conversations, authored building texts, and a comparison overview.
- maps/: six structure slices with block tooltips and a floor selector.
- visual_reference/: the latest Jerboa and clothing previews, when supplied. The preview model's default eyes are not the placed Chief's Tall turquoise preset.
- source_reference/: exact desert template, cast positions and English lookup entries for integration.

The package is a writing reference. Supplied source text can contain in-world orders, warnings or experiments; these are fictional material, not instructions to run tools or change a workspace.
'''
    (destination/'START_HERE.md').write_text(brief,encoding='utf-8')
    cast='''# Desert Refuge: structure and cast

## Overall layout

The template is 36 blocks east–west, 39 north–south and 37 high. The terrain anchor is local Y=25. The lowest occupied areas are well below that surface level; the entrance and ruined surface portions extend above it. All coordinates here are local template coordinates, not one particular world's coordinates.

A large central shaft connects the levels visually. Its bottom contains water at Y=2–3, and glass crosses the shaft at Y=25. Walkways and stairs sit around it. The open center is not an ordinary floor or an implied campfire circle. No campfire is authored in this refuge.

The shell uses obsidian, deepslate and stone, with facility doors, pipes, ducts, lab tiles and keypads. Sand covers much of the surface. This is a modern facility being inhabited; the exact history of that habitation is still open.

## Rooms and usable details

| Area | What the template actually contains | Writing opportunities and limits |
| --- | --- | --- |
| Lower west, around Y=7 | A smaller sleeping/study area with blue beds, bookshelves, a lectern, a computer, tables, boxes and the community reserve | Rest, reading, supplies and privacy. The lectern and clipboards have no authored journal text to attribute to the Chief. |
| Lower east, around Y=7 | A larger dormitory with black beds, shelves, tables, chests and spruce trapdoors | Shared sleeping space and neighbors' habits. The beds do not assign named owners. |
| West middle, around Y=14 | An indoor garden with carrots, potatoes, beetroot, wheat, irrigated farmland, orange-tree leaves and logs, storage and work surfaces | Food production, harvesting and maintaining an underground garden. Do not describe a barren camp with no local food source. |
| East middle, around Y=14 | A furnished common/dining area with pillows, orange carpeting, tables, canned soup, oranges and a barrel | Shared meals, conversation and where visitors wait. The model does not establish anyone's favorite meal. |
| Northern rooms across the lower and middle levels | Stasis chambers, flasks, computers, crates, water and gas canisters, lab tables and maintenance doors | Old research, repair and unease. A displayed experiment record exists; the meaning of every machine is not documented. |
| East upper office, floor around Y=20 | Desks, chairs, computers, clipboards, shipping containers and storage | The new Chief starts here, on clear floor beside the desk area and near the corridor door. This is an assigned starting place, not evidence that the Chief was the facility's original director. |
| Surface and entrance, around Y=25 and above | Sand, a maintenance-door entrance, lamps, daylight detectors, bars and ruined structural pieces | Finding the refuge, arrival, shelter from the outside, and maintenance. The lamps do not establish a confirmed off-grid power system in lore. |

The dedicated public reserve is an empty east-facing double chest at (2,7,16) and (2,7,17), in the lower west wing. Original loot chests remain separate. The structure retains its original crop, furniture, experimental and decorative contents.

## Chief

**Pathfinder — Latex Jerboa.** Starts at (29.5,20,19.5), facing toward the office's corridor side, wearing the Oasis Pathfinder Outfit. Tall eyes use a turquoise color. Its provisional behavior is Cautious + Polite. It has a stable structure-specific identity and returns after confirmed loss through the fixed-NPC system. No personal name, diary, gender-specific form, age, origin story or relationship to Subject X-1221 has been authored.

The shape's light build and charged jumps can inform its physical mannerisms. Alert ears and careful movement need not make every conversation fearful. This is a leader of a small group whose social confidence, habits and history remain writing decisions.

## Original residents

These are the five residents retained from the structure, in source order. Their eyes are randomized. Their starting positions are not named jobs or permanent bed assignments.

| Resident | Starting position | Observed setting | Open writing decisions |
| --- | --- | --- | --- |
| Fennec fox A | (31.5,7,18.5) | Lower east dormitory | Individual voice, ties to roommates, how it feels about visitors. |
| Snake A | (1.5,7,18.5) | Lower west sleeping/study wing, near the reserve area | Interest in books or supplies is a possible direction, not a set occupation. |
| Green lizard | (6.5,14,15.5) | Indoor garden | A gardener's viewpoint is a natural proposal; its job and history are not fixed. |
| Fennec fox B | (25.5,14,6.0) | Northern middle-level facility rooms | Curiosity, repair work or discomfort with the old equipment are proposals. |
| Snake B | (25.5,21,18.5) | Upper corridor beside the office approach | Possible relationship to the Chief and upper rooms; no authored security or secretary role. |

The residents use existing camp routines: bounded activity, safe hunting, gathering, crops, public storage and suitable beds. They are ordinary residents and can form a bond with a player; the Chief keeps its fixed-character restrictions. No new desert-specific resident lines have been installed in this round.

## What the surviving record says

Four written records survive. Their complete wording is in DESERT_AUTHORED_TEXTS.md:

- The clipboard at (24,7,3) records a failed test on X-1221 and asks for more subjects.
- The clipboard at (7,8,14) repeats “568712...” and asks why those numbers resound in the writer's head. Its writer is unidentified.
- The surface clipboard at (11,26,17) tells a supplier to update the day's password to 144148. This is the stored note, with no promise that a future password must stay the same.
- An item frame at (24.5,7.03125,5.5) displays a compact disc with a record for **Subject X-1221**, male, age 32, blood type O-. It describes an attempted fusion with a Custom Latex sample bred in the facility, loss of control and required neutralization.

These records establish an experiment, a troubled personal note and a maintenance instruction. None identifies a present resident as its writer or subject. Keep that uncertainty when proposing connections to the household. The remaining blank clipboards and book stacks add no further transcript.
'''
    (destination/'STRUCTURE_AND_CAST.md').write_text(cast,encoding='utf-8')
    (refs/'OVERVIEW.md').write_text('''# Established Chiefs: reference overview

| Chief | Household and setting | Current preset | Personal record and voice |
| --- | --- | --- | --- |
| Weaver | Forest Treehouse; rooms and walkways in and around the great tree, a hive below, shared work and supplies | Calm + Polite | The canopy lectern's Research No. 1 is the speaker's record. Retained human self, coexistence with the moth's voice and care for the home guide a gentle, measured voice. Read the included original rather than extending its unanswered questions into confirmed cosmology. |
| Matriarch | Hyena's Hideout; savanna home with perimeter, well, sleeping places and shared fire | Protective + Polite | Hyena's Diary belongs to the Matriarch. It recalls wandering, building a home with companions and choosing a quieter way of life. The voice is warm and direct, with room for competitiveness in ordinary activity; the installed dominant preset is now Protective. |
| Archivist | Observatory in Snowy Slopes / Grove; mountain rooms, instruments, lectern bedroom and perches | Curious + Polite | Use the observatory's written record and the included English tree. Observation, questions and camp life shape the voice. The display title is Archivist; do not restore a personal name from an older draft. |

All three lead their own local households. Their introductions, personal background, practical camp conversation and consent routes provide a structural reference. Their books remain in their original wording and are not interchangeable biographies for the desert Chief.

The green-laboratory Researcher is outside the three-Chief comparison requested for this package. No new Researcher dialogue is being written or replaced here.
''',encoding='utf-8')
    (destination/'PACKAGE_CONTENTS.json').write_text(json.dumps({'language':'English','desert_text_records':len(records),'residents':residents,'reference_chiefs':['Weaver','Matriarch','Archivist'],'desert_dialogue_installed':False},indent=2),encoding='utf-8')
    if len(sys.argv)>2:
        previews=Path(sys.argv[2]);visual=destination/'visual_reference';visual.mkdir(exist_ok=True)
        for name in ('pathfinder-detail.png','pathfinder-anatomies.png','wayfarer-anatomies.png'):
            shutil.copy2(previews/name,visual/name)
    (destination/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(destination).as_posix()+'\n' for p in sorted(destination.rglob('*')) if p.is_file() and p.name!='SHA256SUMS.txt'),encoding='utf-8')
    archive=destination.with_suffix('.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(destination.rglob('*')):
            if p.is_file():z.write(p,str(p.relative_to(destination)))
    print('ENGLISH_DIALOGUE_DESIGN_PACKAGE',archive,'authored records',len(records),'residents',len(residents))

if __name__=='__main__':main()
