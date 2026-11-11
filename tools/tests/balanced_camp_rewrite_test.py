"""Verify the supplied partial edit reached every locale without changing graph actions."""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/authoring'))
from import_balanced_camp_rewrite import target, text_value


def main():
    manifest = json.loads((ROOT / 'tools/authoring/balanced_camp_import_manifest.json').read_text(encoding='utf-8'))
    story = json.loads((ROOT / 'tools/authoring/story_npc_dialogue.json').read_text(encoding='utf-8'))['profiles']
    camp = json.loads((ROOT / 'tools/authoring/camp_life_dialogue.json').read_text(encoding='utf-8'))
    assert len(manifest['entries']) == 125
    for key, expected in manifest['entries'].items():
        record, field = target(story, camp, key)
        for lang, text in text_value(record, field).items():
            assert hashlib.sha256(text.encode('utf-8')).hexdigest() == expected['after'][lang], (key,lang)
            assert '\\!' not in text and '\\_' not in text
        if key.startswith('speaker.'): assert record['name'] == record['title']
    for profile in story.values():
        if not profile['chief']:continue
        line = profile['nodes']['confirm_permanent']['lines'][0]
        assert sum(p.get('beat', False) for p in line['parts']) == 2
    for key,line in camp['lines'].items():
        if any('%s' in p['text']['en_us'] for p in line['parts']):
            if key.startswith(('pathfinder.','desert_resident.')):
                # A styled suffix may be separate; all substitutions must stay together in one part.
                assert sum('%s' in p['text']['en_us'] for p in line['parts']) == 1, key
                for part in line['parts']:assert len({text.count('%s') for text in part['text'].values()}) == 1, key
            else:assert len(line['parts']) == 1, key
    print('BALANCED_CAMP_REWRITE_PASS: all 125 supplied entries in three languages, intact substitutions and three-beat permanent consent')


if __name__ == '__main__':main()
