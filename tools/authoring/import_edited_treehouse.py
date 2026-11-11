"""Normalize a native capture of the treehouse workshop, preserving authored blocks and text."""
from copy import deepcopy
from pathlib import Path
import hashlib
import json
import sys
import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from adapt_forest_treehouse import save
from adapt_research_structures import clean_occupant, validate_dependencies
from structure_residents import bind_residents


def main():
    capture, original = map(Path, sys.argv[1:3])
    template, baseline = nbt.load(capture), nbt.load(original)
    assert list(template['size']) == list(baseline['size']) == [27, 41, 24]
    old = {tuple(map(int, b['pos'])): b for b in baseline['blocks']}
    restored = 0
    for block in template['blocks']:
        pos = tuple(map(int, block['pos']))
        tag = block.get('nbt')
        if tag is None:
            continue
        for key in ('ForgeCaps', 'ForgeData', 'LootTableSeed'):
            tag.pop(key, None)
        previous = old[pos].get('nbt', {})
        if 'LootTable' in previous and str(tag.get('id')) == str(previous.get('id')):
            tag.pop('Items', None)
            tag['LootTable'] = deepcopy(previous['LootTable'])
            restored += 1
        if pos in ((16,29,9), (17,29,9)):
            assert str(template['palette'][int(block['state'])]['Name']) == 'minecraft:chest'
            tag['LootTable'] = nbt.String('minecraft:chests/village/village_shepherd')
    assert restored == 12
    assert str(next(template['palette'][int(b['state'])]['Name'] for b in template['blocks']
                    if tuple(map(int,b['pos'])) == (15,29,9))) == 'minecraft:loom'
    moths = 0
    for entity in template['entities']:
        tag = entity['nbt']
        persistent = tag.get('ForgeData', {})
        flags = deepcopy(persistent.get('SynergyEditorOriginalFlags', {}))
        fixed = bool(persistent.get('ChangedSynergyStructureResident', {}).get('Character'))
        for key, value in flags.items():
            tag[key] = value
        clean_occupant(tag)
        tag['Pos'] = deepcopy(entity['pos'])
        if str(tag['id']).startswith(('changed:', 'changed_synergy:')):
            assert flags, 'Missing original authoring flags'
            tag['ForgeData'] = nbt.Compound()
            # Do not freeze the editing world's random appearance on ordinary residents.
            source = next(e['nbt'] for e in baseline['entities'] if e['pos'] == entity['pos']
                          and e['nbt']['id'] == tag['id'])
            if 'LocalVariantInfo' not in source and not fixed:
                tag.pop('LocalVariantInfo', None)
            if fixed:
                assert str(tag['id']) == 'changed_synergy:latex_silk_moth'
                tag['ForgeData']['ChangedSynergyStructureResident'] = nbt.Compound({'Character':nbt.Byte(1)})
                info = tag['LocalVariantInfo']
                info['eyeStyle'] = nbt.Int(2)  # Changed EyeStyle.TALL
                info['irisLeft'] = info['irisRight'] = nbt.Int(0xFFC100)
                moths += 1
        if 'TileX' in tag:
            for axis, value in zip('XYZ', entity['blockPos']):
                tag['Tile'+axis] = nbt.Int(value)
    assert moths == 1 and len(template['entities']) == 9
    bind_residents(template, 'forest_treehouse')
    template['latexCovers'] = nbt.List[nbt.Compound]([c for c in template.get('latexCovers', []) if any(
        str(v) == 'true' for k,v in template['latexCoverPalette'][int(c['state'])].get('Properties', {}).items()
        if k in ('up','down','north','south','east','west'))])
    validate_dependencies(template)
    # The user did not edit the journal; retain every byte of its item data.
    assert [b['nbt']['Book'] for b in template['blocks'] if 'Book' in b.get('nbt',{})] == [
        b['nbt']['Book'] for b in baseline['blocks'] if 'Book' in b.get('nbt',{})]
    destination = ROOT/'src/main/resources/data/changed_synergy/structures/forest_treehouse.nbt'
    save(template, destination)
    path = ROOT/'tools/forest_treehouse_manifest.json'
    report = json.loads(path.read_text(encoding='utf-8'))
    report['output_sha256'] = hashlib.sha256(destination.read_bytes()).hexdigest()
    report.setdefault('changes', {}).update({'imported_editing_world':'Synergy-树屋编辑-20261102',
        'loom_double_chest_loot':'minecraft:chests/village/village_shepherd',
        'canopy_eyes':{'style':'TALL','rgb':'#FFC100'}})
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('TREEHOUSE_IMPORT_PASS: edited blocks, 12 random-loot containers, shepherd double chest, golden tall eyes, unchanged journal')


if __name__ == '__main__':
    main()
