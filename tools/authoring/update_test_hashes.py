import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'tools/authoring/story_npc_dialogue.json'
authored = json.loads(SOURCE.read_text(encoding='utf-8'))['profiles']

src_manifest_path = ROOT / 'tools/tests/npc_dialogue_source_hashes.json'
src_manifest = json.loads(src_manifest_path.read_text(encoding='utf-8'))

found = set()
for profile in authored.values():
    for node in profile['nodes'].values():
        for line in node['lines']:
            actual = ''.join(p['text']['en_us'] for p in line['parts'])
            found.add(hashlib.sha256(actual.encode()).hexdigest())

src_manifest['utterances'] = sorted(list(found))
src_manifest_path.write_text(json.dumps(src_manifest, indent=2) + '\n', encoding='utf-8')
print('Updated npc_dialogue_source_hashes.json, count:', len(src_manifest['utterances']))

loc_manifest_path = ROOT / 'tools/tests/npc_dialogue_localization_hashes.json'
loc_manifest = json.loads(loc_manifest_path.read_text(encoding='utf-8'))

RESOURCE = ROOT / 'src/main/resources'
languages = {lang: json.loads((RESOURCE / f'assets/changed_synergy/lang/{lang}.json').read_text(encoding='utf-8'))
             for lang in ('en_us', 'zh_cn', 'ru_ru')}

for lang in ('en_us', 'zh_cn', 'ru_ru'):
    actual = {}
    for name, profile in authored.items():
        for node_id, node in profile['nodes'].items():
            for i, line in enumerate(node['lines']):
                actual[f'{name}/{node_id}/{i}'] = ''.join(p['text'][lang] for p in line['parts'])
            for choice in node['choices']:
                cid = choice['id']
                actual[f'{name}/{node_id}/choice/{cid}'] = choice['text'][lang]
    for name in ('canopy_chief', 'hyena_chief', 'observatory_chief'):
        for role in ('welcome', 'care', 'organize', 'warning'):
            key = f'dialogue.changed_synergy.context.chief.{name}.{role}'
            actual[key] = languages[lang][key]
    loc_manifest['languages'][lang] = {key: hashlib.sha256(text.encode()).hexdigest() for key, text in actual.items()}

loc_manifest_path.write_text(json.dumps(loc_manifest, indent=2) + '\n', encoding='utf-8')
print('Updated npc_dialogue_localization_hashes.json')
