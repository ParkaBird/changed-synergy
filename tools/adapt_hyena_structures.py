"""Import Crossader's hideout and camps without changing the supplied files.

Usage: python tools/adapt_hyena_structures.py <input-folder>
Requires nbtlib only for offline maintenance, not for builds or gameplay.
"""
import argparse
from collections import Counter
from copy import deepcopy
import gzip
import hashlib
import io
import json
from pathlib import Path

import nbtlib as nbt
from adapt_research_structures import clean_occupant, validate_dependencies
from structure_residents import bind_residents
from small_camp_storage import mark_camp_store

SOURCES = {
    'hyena_hideout': 'hyena_hideout.nbt',
    'hyena_camp_tropical': 'hyena_camp1.nbt',
    'hyena_camp_open': 'hyena_camp2.nbt',
    'hyena_camp_snowy': 'hyena_camp3.nbt',
}

RESIDENTS = {
    'hyena_hideout': (4, [(8, 8), (24, 8), (8, 24), (24, 24)], [(16, 8)]),
    'hyena_camp_tropical': (1, [(5, 3), (10, 3)], []),
    'hyena_camp_open': (1, [(2, 4), (7, 6)], []),
    'hyena_camp_snowy': (1, [(3, 3), (11, 3)], []),
}


def remove_well_draft_chest(template):
    """Remove the author's draft-book chest, preserving the rest of the template."""
    block = next(b for b in template['blocks'] if tuple(map(int, b['pos'])) == (1, 4, 0))
    palette = template['palette']
    name = str(palette[int(block['state'])]['Name'])
    if name == 'minecraft:air':
        return 0
    assert name == 'minecraft:chest', f'Unexpected draft-chest block: {name}'
    items = block.get('nbt', {}).get('Items', [])
    assert items and all(str(item['id']) in ('minecraft:writable_book', 'minecraft:written_book') for item in items)
    block['state'] = nbt.Int(next(i for i, state in enumerate(palette) if str(state['Name']) == 'minecraft:air'))
    block.pop('nbt', None)
    return 1


def repair_well_ground(template):
    """Restore the export selector's missing corner beside the well chest."""
    palette = template['palette']
    blocks = {tuple(map(int, block['pos'])): block for block in template['blocks']}
    assert str(palette[int(blocks[(1, 4, 0)]['state'])]['Name']) in ('minecraft:chest', 'minecraft:air')
    changed = 0
    for y, name in enumerate(('stone', 'dirt', 'dirt', 'grass_block')):
        block = blocks[(0, y, 0)]
        expected = 'minecraft:' + name
        actual = str(palette[int(block['state'])]['Name'])
        if actual == expected:
            continue
        assert actual == 'minecraft:air', f'Unexpected well-corner block: {actual}'
        block['state'] = nbt.Int(next(i for i, state in enumerate(palette) if str(state['Name']) == expected
                                     and (name != 'grass_block' or str(state.get('Properties', {}).get('snowy')) == 'false')))
        changed += 1
    return changed


def add_residents(template, name):
    template['entities'] = nbt.List[nbt.Compound](template.get('entities', []))
    foot_y, males, females = RESIDENTS[name]
    palette = template['palette']
    blocks = {tuple(map(int, b['pos'])): str(palette[int(b['state'])]['Name']) for b in template['blocks']}
    ground = {'minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:dirt', 'minecraft:gravel', 'minecraft:dirt_path'}
    safe = [(x, z) for x in range(int(template['size'][0])) for z in range(int(template['size'][2]))
            if blocks.get((x, foot_y - 1, z)) in ground
            and all(blocks.get((x, y, z)) == 'minecraft:air' for y in (foot_y, foot_y + 1))]
    used = []
    for sex, targets in [('male', males), ('female', females)]:
        for target in targets:
            candidates = [p for p in safe if all((p[0]-q[0])**2+(p[1]-q[1])**2 >= 9 for q in used)]
            assert candidates, f'No safe authored floor for {name} residents'
            x, z = min(candidates, key=lambda p: ((p[0]-target[0])**2+(p[1]-target[1])**2, p))
            used.append((x, z))
            pos = nbt.List[nbt.Double]([x + .5, foot_y, z + .5])
            template['entities'].append(nbt.Compound({
                'pos': pos, 'blockPos': nbt.List[nbt.Int]([x, foot_y, z]),
                'nbt': nbt.Compound({'id': nbt.String(f'changed_synergy:latex_spotted_hyena_{sex}'),
                    'Pos': deepcopy(pos), 'Rotation': nbt.List[nbt.Float]([0, 0]),
                    'Health': nbt.Float(26 if sex == 'female' else 24), 'PersistenceRequired': nbt.Byte(1),
                    'Tags': nbt.List[nbt.String](['changed_synergy:hyena_structure_resident'])})}))
    return len(used)


def adapt(source, destination):
    template = nbt.load(source)
    changes = Counter()
    if destination.stem == 'hyena_hideout':
        changes['restored missing ground column beside well chest'] = repair_well_ground(template)
        changes['removed draft-book chest beside well'] = remove_well_draft_chest(template)
    palette = template['palette']
    for block in template['blocks']:
        tag = block.get('nbt')
        if tag is None:
            continue
        if str(tag.get('id')) == 'visualworkbench:crafting_table':
            assert str(palette[int(block['state'])]['Name']) == 'minecraft:crafting_table'
            assert not tag.get('Items'), 'Review occupied optional workbenches instead of deleting their items'
            del block['nbt']
            changes['removed empty Visual Workbench data'] += 1
        else:
            tag.pop('ForgeCaps', None)
            tag.pop('ForgeData', None)
    for entity in template['entities']:
        tag = entity['nbt']
        clean_occupant(tag)
        tag['Pos'] = deepcopy(entity['pos'])
        if str(tag.get('id')) in ('minecraft:painting', 'minecraft:item_frame'):
            for axis, coordinate in zip('XYZ', entity['blockPos']):
                tag['Tile' + axis] = nbt.Int(coordinate)
        changes['cleared saved occupant identity and optional snapshots'] += 1

    # Changed writes empty cover records for every voxel, including all the air.
    covers = [cover for cover in template.get('latexCovers', [])
              if any(str(value) == 'true' for key, value in template['latexCoverPalette'][int(cover['state'])]
                     .get('Properties', {}).items() if key in ('up', 'down', 'north', 'south', 'east', 'west'))]
    changes['removed empty latex-cover records'] = len(template.get('latexCovers', [])) - len(covers)
    template['latexCovers'] = nbt.List[nbt.Compound](covers)
    changes['added persistent hyena residents'] = add_residents(template, destination.stem)
    bind_residents(template, destination.stem)
    mark_camp_store(template, destination.stem)
    validate_dependencies(template)
    assert len({tuple(map(int, block['pos'])) for block in template['blocks']}) == len(template['blocks'])
    assert all(all(0 <= int(block['pos'][axis]) < int(template['size'][axis]) for axis in range(3))
               for block in template['blocks'])
    buffer = io.BytesIO()
    template.write(buffer)
    destination.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))
    return {'source_filename': source.name, 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'output_sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
            'size': list(map(int, template['size'])), 'blocks': len(template['blocks']),
            'entities': len(template['entities']), 'changes': dict(changes)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / 'src/main/resources/data/changed_synergy/structures'
    report = {name: adapt(args.input / filename, output / (name + '.nbt')) for name, filename in SOURCES.items()}
    (root / 'tools/hyena_structures_manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
