"""Verify reviewed prose and puzzle clues. Optional --baseline permits only the reviewed document and template repairs."""
import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import sys

import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from apply_research_documents import DOCUMENTS, apply_documents
from adapt_research_structures import adapt_green_crystal, adapt_lab_dependencies, validate_dependencies

folder = ROOT / 'src/main/resources/data/changed_synergy'


def documents():
    templates = {name: nbt.load(folder / 'structures' / (name + '.nbt'))
                 for name in ('white_lab_tower', 'red_lab_tower', 'green_lab', 'water_tower_changed')}
    assert len(DOCUMENTS) == 19
    assert sum(len(d['pages']) for d in DOCUMENTS if 'title' in d) == 27
    book_hashes = json.loads((ROOT / 'tools/tests/research_book_text_hashes.json').read_text(encoding='utf-8'))
    for document in DOCUMENTS:
        if 'title' in document:
            assert hashlib.sha256(''.join(document['pages']).encode('utf-8')).hexdigest() == book_hashes[document['id']], (document['id'], 'pagination must preserve every character')
    for document in DOCUMENTS:
        if 'loot_table' in document:
            data = json.loads((folder / 'loot_tables/chests/swordfish_research.json').read_text(encoding='utf-8'))
            assert data['pools'][0]['entries'][0]['name'] == 'changed:chests/decayed_lab_aqua'
            item = data['pools'][1]['entries'][0]
            assert item['name'] == 'changed:compact_disc'
            tag = nbt.parse_nbt(item['functions'][0]['tag'])
            assert str(tag['Text']) == document['pages'][0]
            continue
        template = templates[document['structure']]
        entity = document['holder'].endswith('item_frame')
        record = next(r for r in template['entities' if entity else 'blocks']
                      if list(map(float, r['pos'])) == document['position'])
        tag = record['nbt']
        for key in document.get('item_path', []):
            tag = tag[int(key)] if key.isdigit() else tag[key]
        if 'item_slot' in document:
            tag = next(i for i in tag if int(i['Slot']) == document['item_slot'])
        if 'title' in document:
            assert str(tag['id']) == 'minecraft:written_book'
            tag = tag['tag']
            assert str(tag['title']) == document['title']
            assert str(tag['author']) == document['author'] == 'Oroshiagito'
            pages = [json.loads(str(page))['text'] for page in tag['pages']]
            assert len(pages) == len(document['pages'])
            assert pages == document['pages']
        elif 'field' in document:
            assert str(tag[document['field']]) == document['pages'][0]
        else:
            assert str(tag['tag']['Text']) == document['pages'][0]
    for name, template in templates.items():
        validate_dependencies(template)
        assert not any(adapt_lab_dependencies(deepcopy(template), name).values()), 'lab fixes must be idempotent'
        reapplied = deepcopy(template)
        apply_documents(reapplied, name)
        assert reapplied == template, (name, 'text patches must be idempotent')
    assert adapt_green_crystal(deepcopy(templates['green_lab'])) == 0
    assert next(d for d in DOCUMENTS if d['id'] == 'GL-02')['pages'][0].startswith('Crystal Status:')
    aq6 = next(d for d in DOCUMENTS if d['id'] == 'AQ-06')
    assert "'pull' our transformed colleagues back out" in ''.join(aq6['pages'])
    assert 'outside their territory' in ''.join(aq6['pages']) and 'grow eyes' in ''.join(aq6['pages'])
    assert next(d for d in DOCUMENTS if d['id'] == 'AQ-05')['pages'][0].startswith('# Project No. 125690')
    for document in DOCUMENTS:
        assert all('[cite:' not in page and '建议标题' not in page for page in document['pages'])
    return templates


def puzzles(templates):
    def codes(name):
        return {tuple(map(int, b['pos'])): ''.join(map(str, map(int, b['nbt']['Code'])))
                for b in templates[name]['blocks'] if 'Code' in b.get('nbt', {})}
    expected = {
        'white_lab_tower': {(11, 1, 19): '1989', (3, 22, 21): '3579', (5, 22, 18): '3579', (19, 22, 30): '9812'},
        'red_lab_tower': {(7, 10, 23): '3579', (34, 11, 27): '3579', (19, 41, 19): '5555'},
        'green_lab': {(27, 3, 41): '202020'},
        'water_tower_changed': {(38, 6, 47): '10293590', (50, 8, 32): '10293590', (47, 6, 49): '125690',
            (56, 6, 37): '125690', (63, 8, 39): '125690', (62, 16, 31): '125690', (41, 17, 18): '125690',
            (41, 8, 20): '23546798', (19, 16, 32): '2386', (44, 16, 7): '2386', (26, 16, 32): '123654',
            (6, 18, 33): '9999', (33, 18, 8): '9999', (49, 18, 8): '9999'},
    }
    for name in expected:
        assert codes(name) == expected[name], (name, codes(name))
    assert sum(map(len, expected.values())) == 22


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path)
    args = parser.parse_args()
    templates = documents()
    puzzles(templates)
    manifest = json.loads((ROOT / 'tools/research_structures_manifest.json').read_text(encoding='utf-8'))
    for name in templates:
        assert hashlib.sha256((folder / 'structures' / (name + '.nbt')).read_bytes()).hexdigest() == manifest[name]['output_sha256']
        if args.baseline:
            before = nbt.load(args.baseline / (name + '.nbt'))
            if name == 'green_lab':
                old = next(b for b in before['blocks'] if tuple(map(int, b['pos'])) == (38, 13, 28))['nbt']['Items'][0]['tag']['pages'][3]
                clue = ''.join(next(d for d in DOCUMENTS if d['id'] == 'GL-06')['pages'])
                original = json.loads(str(old))
                assert (original if isinstance(original, str) else original['text']) in clue
                adapt_green_crystal(before)
            apply_documents(before, name)
            adapt_lab_dependencies(before, name)
            assert before == templates[name], (name, 'unexpected geometry, item, entity, loot or keypad change')
    print('RESEARCH_DOCUMENTS_PASS: 19 reviewed records, 27 book pages, lossless book-text hashes, 22 numeric keypad codes, signatures, Markdown and idempotent reimport')
    if args.baseline:
        print('ONLY_APPROVED_CHANGES: reviewed documents and crystal, dependency/furniture/painting repairs, authored red-tower layout and entity cleanup; remaining inventories, traps and hints unchanged')


if __name__ == '__main__':
    main()
