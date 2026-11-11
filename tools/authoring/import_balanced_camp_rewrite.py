"""Apply the supplied partial trilingual edit by stable ID, preserving graph behavior."""
from copy import deepcopy
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
LANGUAGES = ('en_us', 'zh_cn', 'ru_ru')
sha = lambda text: hashlib.sha256(text.encode('utf-8')).hexdigest()


def parse(path):
    raw = path.read_text(encoding='utf-8-sig')
    text = re.sub(r'\\([!_`*\[\]{}()#+\-.>])', r'\1', raw)
    heads = list(re.finditer(r'^## \*\*((?:speaker|story|camp|dialogue)\.[A-Za-z0-9_.]+)', text, re.M))
    entries = {}
    for i, head in enumerate(heads):
        block = text[head.end():heads[i+1].start() if i+1 < len(heads) else len(text)]
        values = re.findall(r'^\* \*\*(EN|ZH|RU):\*\* (.*)$', block, re.M)
        assert [lang for lang, value in values] == ['EN', 'ZH', 'RU'], head[1]
        assert head[1] not in entries, head[1]
        entries[head[1]] = dict(zip(LANGUAGES, (value.rstrip() for lang, value in values)))
    assert entries and len(entries) == len(heads)
    return entries


def target(story, camp, key):
    if key.startswith('speaker.'):
        return story[key.split('.')[1]], 'title'
    if key.startswith('story.'):
        _, profile, node, kind, index = key.split('.', 4)
        record = story[profile]['nodes'][node]
        return (record['lines'][int(index)], 'parts') if kind == 'line' else (next(c for c in record['choices'] if c['id'] == index), 'text')
    if key.startswith('camp.word.'):
        return camp['words'], key[len('camp.word.'):]
    if key.startswith('camp.'):
        return camp['lines'][key[5:]], 'parts'
    raise AssertionError('Unknown editing ID: ' + key)


def text_value(record, field):
    return ({lang: ''.join(part['text'][lang] for part in record[field]) for lang in LANGUAGES}
            if field == 'parts' else record[field])


def parts(key, texts, mental):
    topic = key.rsplit('.', 1)[-1] if key.startswith('camp.') else key.split('.')[2]
    emotion = ('excited' if mental else 'determined' if topic in ('danger', 'watch', 'confirm_permanent')
               else 'happy' if topic.startswith('playful_') or topic in ('thanks', 'outfit', 'watch_thanks', 'welcome_permanent', 'greeting')
               else 'curious' if topic in ('requests', 'choose_item') else 'calm')
    # Emphasize one meaningful phrase, leaving the surrounding speech steady.
    cues = {
        'story.canopy_chief.confirm_permanent.line.0': ('permanently?', '永久成为一只蚕蛾吗？', 'шелкопрядом навсегда?'),
        'story.hyena_chief.confirm_permanent.line.0': ('permanent change?', '永久转化吗？', 'измениться навсегда?'),
        'story.observatory_chief.confirm_permanent.line.0': ('permanent?', '永久保持雕鸮形态吗？', 'постоянным?'),
        'story.canopy_chief.welcome_permanent.line.0': ('take a slow breath', '慢一点深呼吸', 'сделай медленный вдох'),
        'story.hyena_chief.welcome_permanent.line.0': ("you're home!", '你到家了！', 'ты дома!'),
        'story.observatory_chief.welcome_permanent.line.0': ('Welcome home.', '欢迎回家。', 'Добро пожаловать домой.'),
        'story.canopy_chief.welcome_permanent.line.1': ('SO FLUFFY!', '太蓬松了！', 'ТАКОЙ ПУШИСТЫЙ!'),
        'story.hyena_chief.welcome_permanent.line.1': ('GREAT SPOTS!', '好漂亮的斑点！', 'ОТЛИЧНЫЕ ПЯТНА!'),
        'story.observatory_chief.welcome_permanent.line.1': ('where did my colors go?!', '我的颜色去哪了？！', 'куда делись мои цвета?!'),
    }
    cue = cues.get(key)
    if cue is None and topic in ('outfit', 'remember', 'thanks'):
        for candidate in [('Thank you', '谢谢', 'Спасибо'), ('familiar face', '熟面孔', 'свой человек')]:
            if all(candidate[i] in texts[lang] for i, lang in enumerate(LANGUAGES)):
                cue = candidate
                break
    ranges = {lang: [(texts[lang], False)] for lang in LANGUAGES}
    if cue and all(cue[i] in texts[lang] for i, lang in enumerate(LANGUAGES)):
        for i, lang in enumerate(LANGUAGES):
            before, after = texts[lang].split(cue[i], 1)
            ranges[lang] = [(before, False), (cue[i], True), (after, False)]
    result = []
    for i in range(len(ranges['en_us'])):
        highlight = ranges['en_us'][i][1]
        value = {'text': {lang: ranges[lang][i][0] for lang in LANGUAGES}, 'emotion': emotion,
                 'effect': ('bounce' if mental else 'emphasis') if highlight else 'none',
                 'speed': 1, 'intensity': .8 if highlight else 0, 'pause': 110 if highlight and not mental else 0}
        result.append(value)
    # Break long consent speeches at the alternative, then the final question.
    if '.confirm_permanent.line.' in key:
        pivots = {
            'canopy_chief': [('You can still choose', '你依然可以', 'Ты все еще можешь'), ('Are you sure', '你确定要', 'Ты уверен')],
            'hyena_chief': [('You can still borrow', '你完全可以', 'Ты все еще можешь'), ('Are you sure', '你确定要', 'Ты точно хочешь')],
            'observatory_chief': [('You may still choose', '你依然可以', 'Ты все еще можешь'), ('Do you want', '你确定要', 'Сделать облик')],
        }
        for pivot in pivots[key.split('.')[1]]:
            for i, part in enumerate(result):
                if all(pivot[n] in part['text'][lang] for n, lang in enumerate(LANGUAGES)):
                    left, right = deepcopy(part), deepcopy(part)
                    for n, lang in enumerate(LANGUAGES):
                        before, after = part['text'][lang].split(pivot[n], 1)
                        left['text'][lang], right['text'][lang] = before, pivot[n] + after
                    right['beat'] = True
                    result[i:i+1] = [left, right]
                    break
    assert all(''.join(p['text'][lang] for p in result) == texts[lang] for lang in LANGUAGES)
    # Camp substitutions are currently injected into the first part only.
    if key.startswith('camp.') and '%s' in texts['en_us']:
        result = [dict(result[0], text=texts, effect='none', intensity=0)]
    return result


def main(path):
    entries = parse(path)
    story_path = ROOT / 'tools/authoring/story_npc_dialogue.json'
    camp_path = ROOT / 'tools/authoring/camp_life_dialogue.json'
    story_root = json.loads(story_path.read_text(encoding='utf-8')); story = story_root['profiles']
    camp = json.loads(camp_path.read_text(encoding='utf-8'))
    before_story, before_camp = deepcopy(story), deepcopy(camp)
    manifest_path = ROOT / 'tools/authoring/balanced_camp_import_manifest.json'
    prior = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else {}
    changes = {}
    for key, texts in entries.items():
        supplied = deepcopy(texts)
        texts['zh_cn'] = texts['zh_cn'].replace('共同体','社区')
        if '.membership.line.' in key:
            for lang in LANGUAGES:
                for a,b in [('personal belongings and the old notes','personal belongings'),('personal belongings and my diary','personal belongings'),('personal belongings and the old records','personal belongings'),('私人物品和讲台上的旧笔记','私人物品'),('私人物品和我的日记','私人物品'),('私人物品和观测记录','私人物品'),('личные вещи и старые записи','личные вещи'),('личные вещи и мой дневник','личные вещи')]:
                    texts[lang] = texts[lang].replace(a,b)
        record, field = target(story, camp, key)
        previous = text_value(record, field)
        assert all(previous[lang].count('%s') == texts[lang].count('%s') for lang in LANGUAGES), key
        if field == 'parts':
            record[field] = parts(key, texts, record.get('channel') == 'mental')
        else:
            record[field] = texts
        if key.startswith('speaker.'):
            record['name'] = deepcopy(texts)
        changes[key] = {'before': {lang: sha(previous[lang]) for lang in LANGUAGES}, 'after': {lang: sha(texts[lang]) for lang in LANGUAGES}}
        if texts != supplied:
            changes[key]['supplied'] = {lang: sha(supplied[lang]) for lang in LANGUAGES}
            changes[key]['adjustment'] = 'Later user request: story carriers are freely readable; retain personal-storage privacy only.'
        if prior.get('source_sha256') == hashlib.sha256(path.read_bytes()).hexdigest():
            changes[key]['before'] = prior['entries'][key]['before']
    # Text replacement never changes routes, language gates, eligibility or effects.
    for profile, original in before_story.items():
        for node_id, node in original['nodes'].items():
            after = story[profile]['nodes'][node_id]
            for i, line in enumerate(node['lines']):
                assert {k:v for k,v in line.items() if k != 'parts'} == {k:v for k,v in after['lines'][i].items() if k != 'parts'}
                if f'story.{profile}.{node_id}.line.{i}' not in entries: assert line == after['lines'][i]
            for a,b in zip(node['choices'], after['choices']):
                assert {k:v for k,v in a.items() if k != 'text'} == {k:v for k,v in b.items() if k != 'text'}
                if f'story.{profile}.{node_id}.choice.{a["id"]}' not in entries: assert a == b
    for key, line in before_camp['lines'].items():
        if 'camp.' + key not in entries: assert line == camp['lines'][key]
    assert before_camp['words'] == camp['words']
    story_path.write_text(json.dumps(story_root, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    camp_path.write_text(json.dumps(camp, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    manifest = {'source': path.name, 'source_sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'entries': changes,
                'unprovided_content_preserved': True, 'story_routes_and_conditions_preserved': True,
                'pacing_breaks': sum(p.get('beat',False) for profile in story.values() for node in profile['nodes'].values() for line in node['lines'] for p in line['parts'])}
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'BALANCED_CAMP_IMPORT_PASS: {len(entries)} entries in three languages; unprovided writing and all graph behavior preserved')


if __name__ == '__main__':
    main(Path(sys.argv[1]))
