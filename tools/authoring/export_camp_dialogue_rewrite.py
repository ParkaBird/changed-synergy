"""Export current household writing with literary context and all three locales."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import sys

from camp_life_editing_context import VOICES, context_for, FLOW_GUIDE
from export_story_npc_english import BRANCHES, CONDITIONS

ROOT = Path(__file__).resolve().parents[2]
LOCALES = {'en_us': 'English', 'zh_cn': '简体中文', 'ru_ru': 'Русский'}
PROFILE_VOICES = {'canopy_chief': 'weaver', 'hyena_chief': 'matriarch', 'observatory_chief': 'archivist'}
SCENES = {
    'root': 'First approach. The visitor has opened Dialogue; this starts no task or admission. Body and time-of-day variants describe the visitor actually present.',
    'return': 'The visitor has spoken to this same individual before. This is a renewed conversation, not a new admission or reset of trust.',
    'about': 'The visitor asks who the speaker is. Establish the individual and their life here; Chief authority extends only to this household.',
    'home': 'The visitor asks about the building, its neighbors and daily life. Describe the authored home without promising membership or unrestricted storage access.',
    'past': 'The visitor has completed the introductory topics before asking deeper background questions. Use the speaker’s own written records; unsettled worldbuilding remains uncertain.',
    'voices': 'The visitor follows the Weaver’s account of sharing a body. His human self and moth voice coexist; this does not establish a universal experience for every transformed person.',
    'enrol': 'A guest asks to make this particular camp their home. Admission is voluntary and requires neutral local trust and no immediate hostility. Keeping the present body is a full membership route.',
    'explain_form': 'The visitor asks what a body-changing admission route means. Explanation alone changes nothing. Only the eligible choices below may start a rite.',
    'confirm_permanent': 'The player has selected the TT permanent route but has not yet consented to its final commitment. Explain permanent-body consequences and leave an explicit way to reconsider. A home-transfer confirmation, if needed afterward, is separate consent.',
    'welcome_human': 'Admission keeping the human body has succeeded. No body change or admission-trust bonus is implied. This household is now the registered home.',
    'welcome_latex': 'Admission keeping the existing transformed body has succeeded. The ordinary eligible admission amount is 2 local trust, only to the extent not already paid here.',
    'welcome_copy': 'The explicitly reversible ritual and admission have succeeded. This copies the offered species, never consumes or replaces the Chief. The eligible admission amount is 5 local trust; normal permitted reversal methods are still needed.',
    'welcome_permanent': 'The confirmed TT permanent rite and admission have succeeded. The eligible admission amount is 8 local trust; permanence survives departure. Existing paid admission history prevents repeated awards.',
    'membership': 'An existing member asks about their place here. Discuss this household’s shared rights and boundaries; personal friendship, bonds and regional reputation remain independent.',
    'confirm_leave': 'The member has asked to leave but departure is not committed. Confirmation ends this home registration; cancelling keeps it.',
    'left': 'Departure has succeeded. Local trust, personal relationships, body and reward history are retained. Membership privileges and the camp badge end.',
    'goodbye': 'The visitor is closing the exchange. No implied promise, delivery, body change or penalty should be added.',
    'neighbors': 'The visitor asks the Archivist about the mountain residents. Use the observatory and its existing journal rather than treating him as leader of every mountain creature.',
    'observations': 'The visitor asks about the Archivist’s observations and records. This is a background topic; no astronomical quest or unimplemented world event is activated.',
    'old_note': 'The visitor follows the subject of the old written note. Its interpretation belongs to this individual; preserve the uncertainty in the current text.',
    'furniture': 'The visitor asks about furniture and keepsakes in the hideout. The Matriarch’s diary, wandering companions and simpler present life are the factual background.',
    'tail': 'The visitor asks the Researcher about his changed ears or tail. This is a personal, lightly comic exchange rather than a recruiting topic.',
    'farewell': 'The visitor asks about the Researcher’s farewell note. Looking for companions was an earlier episode; he has returned to sort out his records.',
    'returned': 'The visitor follows why the Researcher came back. Connect his earlier departure to his present work without making him a Chief.',
    'experiment': 'The visitor asks about experiments in the lower laboratory. Follow the existing records and the Researcher’s curious, self-mocking voice.',
    'muntjacs': 'The visitor asks about the Muntjac research report. This is commentary on written research, not an automatic transformation or unlock.',
    'crystal': 'The visitor asks about crystal notes. Preserve what the existing records actually establish and do not invent a new usable mechanic.',
    'initials': 'The visitor asks about initials on the records. Book signatures are author credits, not an instruction to rename the NPC.',
}
EFFECTS = {
    'none': 'Opens the listed topic; no items, body or membership change.',
    'close': 'Ends the conversation.',
    'join': 'On successful recheck, registers this home while keeping the body. If another home is registered, asks separately before replacing it. Existing paid rewards are retained.',
    'reversible': 'On successful recheck, performs the expressly reversible rite and registers this home. Another home first requires transfer confirmation. Failure grants no admission or reward.',
    'permanent': 'After explicit permanent consent and backend recheck, performs the TT permanent rite and registers this home. Another home also requires transfer confirmation. Failure grants no admission or reward.',
    'leave': 'Confirms departure from the current home; preserves body, trust, relationships and claim history.',
}
REPLIES = {
    'life': 'Added to a Chief’s story menu. Opens the everyday household greeting; accepts no work.',
    'status': 'Opens the report in this order: food, free/total sleeping places, current safety, member/guest position, remembered help if any. Changes no rights.',
    'requests': 'Legacy shared-list wording retained in the text source. The live menu now uses separate public_requests and personal_requests replies.',
    'public_requests': 'Chief only: opens public household requests; item hand-ins go to Community Supplies. Reading does not reserve or accept work.',
    'personal_requests': 'Ordinary resident: opens only that individual’s personal requests; no request is accepted yet.',
    'donate': 'Opens a voluntary offer based on the current main-hand stack. No items move until the player confirms a quantity. Donations, including those discussed with ordinary residents, go to the reserve.',
    'meal': 'Eligible home member asks to gather for food. Actual food, a usable lit fire, safety and the shared cooldown are checked. Success opens the speaker’s meal reply; failure opens changed.',
    'membership': 'Current member asks the Chief about their place here. Opens the existing membership discussion without ending it.',
    'body': 'Current member revisits the Chief’s explanation of available forms without leaving first. This reply by itself changes no body.',
    'request': 'A list entry inserts quantity first, then supply category or protection description. Opens details, not a commitment.',
    'watch_done': 'Chief’s public protection request: reports real assistance against a threat to a resident. Requires recorded evidence and a currently safe camp. Leads to watch_thanks or watch_wait; does not pay defense trust twice.',
    'submit': 'Chief’s public item request: rechecks and transfers the full matching held amount into Community Supplies. Leads to thanks if successful, changed if stale or blocked.',
    'personal_submit': 'Hands the full matching quantity directly to this requesting resident’s personal inventory. Rechecked on the server; a full reserve does not block it.',
    'decline': 'Leaves the unfinished request available without taking goods or deducting trust. Opens declined.',
    'give': 'Confirms an actual public donation. Insertions are quantity then item name. Rechecks main-hand stack, demand and reserve capacity; succeeds with thanks or fails with changed.',
    'confirm_transfer': 'Replaces the existing registered home and continues only the admission/ritual route already selected. Does not replace the independent permanent-body consent. Failure leaves the action uncommitted.',
    'keep_home': 'Cancels the pending home transfer; preserves the previous home and returns to ordinary conversation.',
    'back': 'Returns to the Chief’s story root or the ordinary resident greeting. Does not accept a request or donation.',
    'later': 'Ends the conversation without a refusal penalty. Unfinished requests remain available.',
    'ask_chief': 'After an uncomprehended resident’s gestures, shows seekchief. The player must go find the Chief; no translation, teleport or request acceptance occurs.',
    'outfit': 'Chief only: member in good standing, local trust at least 40, outfit not yet claimed in this home. Grants one ordinary outfit; a full player inventory allows a later retry.',
}


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def prose(parts, locale):
    # Parts split a sentence for visual treatment; export only authored reading beats.
    result = ['']
    for part in parts:
        if part.get('beat') and result[-1]:
            result.append('')
        result[-1] += part['text'][locale]
    return '\n\n'.join(p for p in result if p)


def supplementary(key):
    return (key.startswith('dialogue.changed_synergy.role.')
            or key.startswith('dialogue.changed_synergy.context.chief.')
            or key.startswith('dialogue.changed_synergy.context.player_outpost.meal.')
            or key.startswith('dialogue.changed_synergy.context.camp_private_storage')
            or key.startswith('dialogue.changed_synergy.negotiation.cache_defense.')
            or key.startswith('dialogue.changed_synergy.') and '.cache_' in key)


def ambient_context(key):
    if '.chief.' in key:
        topic = key.rsplit('.', 1)[1]
        return {'welcome': 'The Chief welcomes a visitor near home; no admission is implied.',
                'care': 'The Chief notices a visitor’s comfort and offers ordinary hospitality.',
                'organize': 'The Chief mentions household work as an everyday invitation, not a mandatory quest.',
                'warning': 'The Chief gives a practical warning about this authored home; no player offence or punishment is implied.'}[topic]
    if '.camp_private_storage' in key:
        return 'A nearby awake camp resident sees a player search explicitly private storage. Warn politely; repeated witnessed access can reduce local trust but does not start a storage-defence attack. Reading a lectern or another public story carrier does not trigger this line.'
    if '.player_outpost.meal.' in key:
        return 'Shared campfire meal invitation, selected by personality. This is reused by existing outpost routines; it is not a new Chief-specific branch or a guarantee every resident attends.'
    if '.cache_defense.' in key:
        return 'Shared storage-defence negotiation after a witnessed offence. The key identifies the player’s explanation or response. Existing negotiation checks still decide release; the line alone does not pardon theft or transfer supplies.'
    if '.cache_' in key:
        return 'Shared container reaction for the indicated species family and offence stage. A relevant awake eyewitness and the existing access rules determine use. These are also used by wild outposts, so references to provisioners or regional leaders are existing shared wording, not new household rights. Ordinary camp dialogue donations use the dedicated reserve.'
    return 'Shared ordinary profession line. Natural-camp residents retain normal assigned-role behavior; these lines are not exclusive biographies or new requests. The laboratory Researcher has the ordinary Wanderer role. Chief is preset only and never randomly assigned.'


def export(destination):
    destination.mkdir(parents=True, exist_ok=True)
    story_path = ROOT / 'tools/authoring/story_npc_dialogue.json'
    camp_path = ROOT / 'tools/authoring/camp_life_dialogue.json'
    profiles = load(story_path)['profiles']
    camp = load(camp_path)
    words = {locale: load(ROOT / f'src/main/resources/assets/changed_synergy/lang/{locale}.json') for locale in LOCALES}
    records = []

    def add(key, section, context, texts):
        assert set(texts) == set(LOCALES), key
        assert all(isinstance(v, str) and v.strip() for v in texts.values()), key
        records.append({'id': key, 'section': section, 'context': context, 'text': texts})

    for pid, profile in profiles.items():
        overview = VOICES[PROFILE_VOICES[pid]][1] if profile['chief'] else (
            'Researcher — green laboratory attic. A curious, polite Gas Wolf with the ordinary Wanderer role, a lab coat and red tie. His farewell note, claw-written clipboard and self-transformation report are his background. He has returned after looking for companions. This is an optional background character, not a recruiting Chief.')
        add('speaker.' + pid, pid, overview, profile['title'])
        for node_id, node in profile['nodes'].items():
            assert node_id in SCENES, (pid, node_id)
            incoming = [(origin, c) for origin, n in profile['nodes'].items() for c in n['choices'] if c['next'] == node_id and c.get('effect') != 'close']
            outgoing = [f"{c['id']} → {c['next']}" for c in node['choices']]
            scene = SCENES[node_id]
            if incoming:
                scene += '\n\nReached from: ' + '; '.join(f"{origin} / {c['id']}" for origin, c in incoming) + '.'
            if outgoing:
                scene += '\n\nAvailable follow-up branches (subject to their conditions): ' + '; '.join(outgoing) + '.'
            for index, line in enumerate(node['lines']):
                condition = CONDITIONS.get(line.get('condition', 'always'), line.get('condition', 'always'))
                channel = ('Thought accompanies the preceding spoken passage only if the player understands mental speech. It does not teach telepathy.' if line['channel'] == 'mental' else 'Spoken aloud; these fixed NPCs have learned human speech.')
                add(f'story.{pid}.{node_id}.line.{index}', pid, scene + '\n\nThis variant: ' + condition + '. ' + channel,
                    {locale: prose(line['parts'], locale) for locale in LOCALES})
            for choice in node['choices']:
                condition = CONDITIONS.get(choice.get('condition', 'always'), choice.get('condition', 'always'))
                requirement = ' Must first complete: ' + ', '.join(choice['requires']) + '.' if choice.get('requires') else ''
                effect = choice.get('effect', 'none')
                assert effect in EFFECTS, effect
                add(f'story.{pid}.{node_id}.choice.{choice["id"]}', pid,
                    scene + f'\n\nPlayer reply. Availability: {condition}.{requirement}\n\nNext: {choice["next"]}. ' + EFFECTS[effect], choice['text'])

    for key, line in camp['lines'].items():
        before, purpose, response, outcome, facts = context_for(key)
        voice = key.split('.', 1)[0]
        context = VOICES[voice][1] + f'\n\nBefore: {before}\n\nPurpose: {purpose}\n\nPlayer response: {response}\n\nAfter: {outcome}\n\nKeep accurate: {facts}'
        if any('%s' in p['text']['en_us'] for p in line['parts']):
            meanings = ('the held item’s display name' if key.endswith('.donate') else 'the currently registered home’s display name' if key.endswith('.transfer') else 'free sleeping places, then total places' if key == 'status.beds' else 'required quantity, then requested supply category')
            context += '\n\nInsertions, in order: ' + meanings + '.'
        add('camp.' + key, 'camp.' + voice, context, {locale: prose(line['parts'], locale) for locale in LOCALES})
    for key, text in camp['words'].items():
        if key.startswith('choice.'):
            context = 'Player reply. ' + REPLIES[key.split('.', 1)[1]]
        elif key.startswith('dialogue.'):
            context = ('An ordinary resident with their own unfinished request approaches an idle home member and hails them. The player may open dialogue or continue; no screen is forced.' if key.endswith('camp_request_invite') else 'Brief language-aware acknowledgment of witnessed mature-crop harvesting followed by prompt replanting at the same position. Not a new task.')
        else:
            context = 'Inserted household, supply or interface wording. Keep placeholders and their order; these are labels rather than an additional speaking character.'
        add('camp.word.' + key, 'camp.words', context, text)

    supplemental = sorted(k for k in words['en_us'] if supplementary(k))
    missing = []
    for key in supplemental:
        texts = {}
        for locale in LOCALES:
            if key not in words[locale]:
                missing.append({'id': key, 'locale': locale})
            texts[locale] = words[locale].get(key, '[English fallback currently used]\n' + words['en_us'][key])
        add(key, 'shared.reactions', ambient_context(key), texts)
    assert len(records) == len({r['id'] for r in records}), 'Duplicate editing ID'
    paths = []
    for locale, label in LOCALES.items():
        output = [f'# Camp and NPC dialogue — {label}', '', 'Current game text for rewriting. Context notes are editorial and are not spoken. Stable entry IDs map revisions back to the game. Read README and CONTEXT-AND-FLOW first.', '']
        section = None
        for record in records:
            if record['section'] != section:
                section = record['section']
                output += ['## ' + section, '']
            output += ['### ' + record['id'], '', record['context'], '', '**Current text / 当前文本 / Текущий текст**', '', record['text'][locale], '']
        path = destination / f'DIALOGUE-{locale}.md'
        path.write_text('\n'.join(output), encoding='utf-8')
        # Verifies the delivered copy includes every complete passage verbatim.
        actual = path.read_text(encoding='utf-8')
        assert all(r['text'][locale] in actual and '### ' + r['id'] in actual for r in records), locale
        paths.append(path)
    guide = destination / 'CONTEXT-AND-FLOW.md'
    guide.write_text(FLOW_GUIDE, encoding='utf-8'); paths.append(guide)
    readme = destination / 'README.md'
    readme.write_text('''# 营地与固定 NPC 对话改写包

三个 DIALOGUE 文件分别是当前英语、简体中文和俄语全文，条目 ID 完全对应。每条附有前因、人物身份、可选回应及后果；说明采用英文，方便交给其他 agent。正文按原文导出，没有重新翻译、缩写或改写。心语保留源稿写法，明确标注为心语；游戏中仍按现有规则处理括号和横线。

范围包括织者、女族长、档案员与研究员的完整剧情树、玩家选项、入会与永久兽化二次确认，三个主营地的近况、贡献、公共请求、个人请求、捐赠、聚餐、迁居、服装领取、居民邀谈与感谢，以及首领职能台词。补充部分收录居民共用的普通职能、聚餐、容器警告及相关交涉台词；它们也可能用于其他据点，不能当作某一首领的专属经历。研究员不办理入会。

本轮当前实现：公共请求交首领并存进公共储备；个人请求交给提出请求的居民并留在其个人物品栏。信任 40 且为本营正常成员时，可向首领领一次对应服装。当前入会奖励为 0/2/5/8，旧稿中 0/5/25/35 已过时。三个营地独立，不代表整个大阵营。普通居民可同时是同营伙伴和羁绊，固定首领保留禁止羁绊随行和身体接管的限制。

改写时保留条目 ID、条件含义、同意/拒绝关系，以及 `%s` 等占位符的个数和顺序。可以改变语气、句式和节奏；不要把尚未提交的选择写成已发生的结果。阅读 CONTEXT-AND-FLOW.md 了解整个流程。无需编辑颜色、速度、停顿或动画参数；包中没有这些配置。

manifest.json 只记录覆盖范围、源稿摘要与文件校验，供交回时核对；不属于文学内容。若某条俄语或中文没有独立翻译，文档会明确标注当前英语回退，不会伪造译文。
''', encoding='utf-8'); paths.append(readme)
    manifest = {'locales': list(LOCALES), 'entries_per_locale': len(records), 'story_lines': sum(len(n['lines']) for p in profiles.values() for n in p['nodes'].values()),
                'story_choices': sum(len(n['choices']) for p in profiles.values() for n in p['nodes'].values()), 'camp_lines': len(camp['lines']), 'camp_words': len(camp['words']),
                'shared_role_and_reaction_lines': len(supplemental), 'missing_localizations': missing,
                'sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in (story_path, camp_path)},
                'files': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
    manifest_path = destination / 'manifest.json'
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8'); paths.append(manifest_path)
    archive = destination.parent / (destination.name + '.zip')
    with ZipFile(archive, 'w', ZIP_DEFLATED) as bundle:
        for path in paths: bundle.write(path, path.name)
    with ZipFile(archive) as bundle:
        assert bundle.testzip() is None and set(bundle.namelist()) == {p.name for p in paths}
        for path in paths: assert bundle.read(path.name) == path.read_bytes()
    print(json.dumps({'archive': str(archive), **{k: v for k, v in manifest.items() if k not in ('sources', 'files')}}, ensure_ascii=False))


if __name__ == '__main__':
    export(Path(sys.argv[1]))
