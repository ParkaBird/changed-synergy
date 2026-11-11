"""Check lossless dialogue compilation, source coverage and guarded ritual routes."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = ROOT / 'src/main/resources'

def main():
    authored = json.loads((ROOT / 'tools/authoring/story_npc_dialogue.json').read_text(encoding='utf-8'))['profiles']
    languages = {lang: json.loads((RESOURCE / f'assets/changed_synergy/lang/{lang}.json').read_text(encoding='utf-8')) for lang in ('en_us', 'zh_cn', 'ru_ru')}
    found, nodes, beats = set(), 0, 0
    for name, original in authored.items():
        graph = json.loads((RESOURCE / f'data/changed_synergy/story_npcs/{name}.json').read_text(encoding='utf-8'))
        assert graph['nodes'].keys() == original['nodes'].keys()
        for node_id, node in graph['nodes'].items():
            source = original['nodes'][node_id]
            nodes += 1
            assert len(node['lines']) == len(source['lines'])
            for line, baseline in zip(node['lines'], source['lines']):
                assert [p.get('beat', False) for p in line['parts']] == [p.get('beat', False) for p in baseline['parts']]
                beats += sum(p.get('beat', False) for p in line['parts']) if name != 'desert_chief' else 0
                for lang, words in languages.items():
                    actual = ''.join(words[part['text']] for part in line['parts'])
                    expected = ''.join(part['text'][lang] for part in baseline['parts'])
                    assert actual == expected, (name, node_id, lang)
                    assert actual and '**' not in actual and 'Performance Note:' not in actual
                    if lang == 'en_us': found.add(hashlib.sha256(actual.encode()).hexdigest())
            for choice in node['choices']:
                assert choice['next'] in graph['nodes']
                assert all(key in graph['nodes'] for key in choice.get('requires', []))
                assert all(choice['text'] in words for words in languages.values())
                if choice['effect'] == 'permanent': assert node_id == 'confirm_permanent'
        gated = [choice for choice in graph['nodes']['root']['choices'] if choice.get('requires')]
        assert len(gated) == 1 and len(gated[0]['requires']) == 2
        if graph['chief']:
            permanent = graph['nodes']['explain_form']['choices']
            selected = next(c for c in permanent if c['id'] == 'permanent_form')
            assert selected['effect'] == 'none' and selected['next'] == 'confirm_permanent'
            assert graph['nodes']['confirm_permanent']['choices'][0]['condition'] == 'can_permanent'
        else: assert not any(c['effect'] in ('join', 'reversible', 'permanent') for n in graph['nodes'].values() for c in n['choices'])
    manifest = json.loads((ROOT / 'tools/tests/npc_dialogue_source_hashes.json').read_text())
    assert set(manifest['utterances']) <= found, set(manifest['utterances']) - found
    assert set(json.loads((RESOURCE / 'data/changed_synergy/tags/worldgen/biome/has_structure/observatories.json').read_text())['values']) == {'minecraft:snowy_slopes', 'minecraft:grove'}
    placement = json.loads((RESOURCE / 'data/changed_synergy/worldgen/structure_set/observatories.json').read_text())['placement']
    assert (placement['spacing'], placement['separation'], placement['spread_type']) == (30, 8, 'triangular')
    assert json.loads((RESOURCE / 'data/changed_synergy/tags/worldgen/structure/on_observatory_maps.json').read_text())['values'] == ['changed_synergy:observatory']
    assert not any(key.startswith('npc.changed_synergy.choice.') for words in languages.values() for key in words)
    assert beats == json.loads((ROOT / 'tools/authoring/balanced_camp_import_manifest.json').read_text())['pacing_breaks']
    assert not any(key in words for words in languages.values() for key in ('npc.changed_synergy.spoken', 'npc.changed_synergy.mental'))
    for words in languages.values():
        for key in ('dialogue', 'busy', 'changed', 'continue_hint', 'choose_hint', 'standing'):
            assert words['npc.changed_synergy.' + key]
        assert words['npc.changed_synergy.standing'].count('%s') == 1
    localized = json.loads((ROOT / 'tools/tests/npc_dialogue_localization_hashes.json').read_text(encoding='utf-8'))
    for lang, expected in localized['languages'].items():
        actual = {}
        for name, profile in authored.items():
            for node_id, node in profile['nodes'].items():
                for i, line in enumerate(node['lines']):
                    actual[f'{name}/{node_id}/{i}'] = ''.join(p['text'][lang] for p in line['parts'])
                for choice in node['choices']:
                    actual[f'{name}/{node_id}/choice/{choice["id"]}'] = choice['text'][lang]
        for name in ('canopy_chief', 'hyena_chief', 'observatory_chief'):
            for role in ('welcome', 'care', 'organize', 'warning'):
                key = f'dialogue.changed_synergy.context.chief.{name}.{role}'
                actual[key] = languages[lang][key]
        assert {key: hashlib.sha256(text.encode()).hexdigest() for key, text in actual.items() if not key.startswith('desert_chief/')} == expected, lang
    print(f'REVISED_NPC_DATA_PASS: {len(manifest["utterances"])} complete English source utterances, 124 localized lines, 171 replies, 12 ambient lines, {nodes} three-language nodes, {beats} authored pacing breaks, guarded rituals and both observatory biomes')

if __name__ == '__main__': main()
