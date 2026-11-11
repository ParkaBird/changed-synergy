"""Check the new camp writing, formatting parameters and optional integrations."""
from pathlib import Path
import json,re

ROOT=Path(__file__).resolve().parents[2]

def main():
    source=json.loads((ROOT/'tools/authoring/camp_life_dialogue.json').read_text(encoding='utf-8'))
    generated=json.loads((ROOT/'src/main/resources/data/changed_synergy/camp_dialogue/common.json').read_text(encoding='utf-8'))['lines']
    assert source['lines'].keys()==generated.keys()
    for lang in ('en_us','zh_cn','ru_ru'):
        words=json.loads((ROOT/f'src/main/resources/assets/changed_synergy/lang/{lang}.json').read_text(encoding='utf-8'))
        for key,line in source['lines'].items():
            assert ''.join(words[p['text']] for p in generated[key])==''.join(p['text'][lang] for p in line['parts']),key
            assert len({''.join(p['text'][l] for p in line['parts']).count('%s') for l in ('en_us','zh_cn','ru_ru')})==1,key
        for key,line in source['words'].items():
            compiled=key if key.startswith('dialogue.') else 'camp.changed_synergy.'+key
            assert words[compiled]==line[lang]
            assert len({v.count('%s') for v in line.values()})==1,key
    for path in (ROOT/'src/main/resources/data/changed_synergy/story_npcs').glob('*.json'):
        assert 'faction' not in json.loads(path.read_text(encoding='utf-8'))
    for path in (ROOT/'src/main/resources/data/changed_synergy/tags/items').glob('camp_*.json'):
        assert not any('changed_addon:' in str(v) for v in json.loads(path.read_text())['values']),path
    network=(ROOT/'src/main/java/net/parkabird/changedsynergy/network/ChangedSynergyNetwork.java').read_text()
    assert 'PROTOCOL = "67"' in network
    print(f'CAMP_DIALOGUE_DATA_PASS: {len(source["lines"])} passages and {len(source["words"])} labels in three languages; localized placeholders, native item tags, no faction metadata')

if __name__=='__main__':main()
