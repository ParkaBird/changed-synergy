"""Import the supplied forest treehouse without changing the source or other stories."""
from copy import deepcopy
import gzip
import hashlib
import io
import json
from pathlib import Path
import sys
import textwrap
import nbtlib as nbt
from adapt_research_structures import clean_occupant, validate_dependencies
from structure_residents import bind_residents, add_treehouse_bees

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'src/main/resources/data/changed_synergy/structures'


def save(template, path):
    buffer = io.BytesIO()
    template.write(buffer)
    path.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))


def update_canopy_book(template):
    """Keep the journal's wording while matching its new resident and fitting vanilla pages."""
    changed = 0
    def visit(value):
        nonlocal changed
        if isinstance(value, nbt.Compound):
            if str(value.get('id', '')) in ('minecraft:writable_book', 'minecraft:written_book'):
                tag = value.get('tag', {})
                pages = tag.get('pages', [])
                if not any('Latex Leaf' in str(p) for p in pages):
                    return
                assert str(value['id']) == 'minecraft:writable_book'
                updated = []
                for page in pages:
                    text = str(page).replace('Latex Leaf', 'Latex Silk Moth')
                    updated.extend(textwrap.wrap(text, width=165, replace_whitespace=False,
                                                 drop_whitespace=False, break_long_words=False,
                                                 break_on_hyphens=False) if len(text) > 165 else [text])
                tag['pages'] = nbt.List[nbt.String](updated)
                changed += 1
                return
            for child in value.values():
                visit(child)
        elif isinstance(value, nbt.List):
            for child in value:
                visit(child)
    visit(template)
    return changed


def remove_campfire_draft_chest(template):
    """Remove the author's draft-book chest, preserving the rest of the template."""
    block = next(b for b in template['blocks'] if tuple(map(int, b['pos'])) == (26, 1, 0))
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


def main():
    source = Path(sys.argv[1])
    template = nbt.load(source)
    remove_campfire_draft_chest(template)
    update_canopy_book(template)
    palette = template['palette']
    for entry in palette:
        if str(entry['Name']).endswith('_leaves'):
            entry.setdefault('Properties', nbt.Compound())['persistent'] = nbt.String('true')
    for block in template['blocks']:
        tag = block.get('nbt')
        if tag is not None:
            tag.pop('ForgeCaps', None)
            tag.pop('ForgeData', None)
    for entity in template.get('entities', []):
        tag = entity['nbt']
        clean_occupant(tag)
        tag['Pos'] = deepcopy(entity['pos'])
        if str(tag.get('id')) == 'minecraft:painting':
            for axis, coordinate in zip('XYZ', entity['blockPos']):
                tag['Tile' + axis] = nbt.Int(coordinate)
    covers = [c for c in template.get('latexCovers', []) if any(
        str(v) == 'true' for k, v in template['latexCoverPalette'][int(c['state'])].get('Properties', {}).items()
        if k in ('up', 'down', 'north', 'south', 'east', 'west'))]
    template['latexCovers'] = nbt.List[nbt.Compound](covers)
    blocks = {tuple(map(int, b['pos'])): str(palette[int(b['state'])]['Name']) for b in template['blocks']}
    safe = [(x, z) for x in range(2, int(template['size'][0])-2) for z in range(2, int(template['size'][2])-2)
            if blocks.get((x, 0, z)) in {'minecraft:grass_block', 'minecraft:dirt', 'minecraft:coarse_dirt'}
            and all(blocks.get((x, y, z)) == 'minecraft:air' for y in (1, 2))]
    used = []
    for kind, target in [('changed:latex_deer', (4, 4)), ('changed:latex_deer', (22, 4)),
                         ('changed:latex_squirrel', (4, 19)), ('changed:latex_squirrel', (22, 19)),
                         ('changed:gas_skunk', (7, 12)), ('changed_synergy:latex_silk_moth', (20, 12))]:
        candidates = [p for p in safe if all((p[0]-q[0])**2+(p[1]-q[1])**2 >= 9 for q in used)]
        assert candidates, 'No safe ground for the forest group'
        x, z = min(candidates, key=lambda p: (p[0]-target[0])**2+(p[1]-target[1])**2)
        used.append((x, z))
        pos = nbt.List[nbt.Double]([x+.5, 1, z+.5])
        template['entities'].append(nbt.Compound({'pos': pos, 'blockPos': nbt.List[nbt.Int]([x, 1, z]),
            'nbt': nbt.Compound({'id': nbt.String(kind), 'Pos': deepcopy(pos), 'Rotation': nbt.List[nbt.Float]([0, 0]),
                'PersistenceRequired': nbt.Byte(1), 'Tags': nbt.List[nbt.String](['changed_synergy:forest_treehouse_resident']),
                'ForgeData': nbt.Compound({'ChangedSynergyLightReputationGroup': nbt.String('forest')})})}))
    add_treehouse_bees(template)
    bind_residents(template, 'forest_treehouse')
    validate_dependencies(template)
    destination = OUTPUT / 'forest_treehouse.nbt'
    save(template, destination)
    report = {'source': source.name, 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'output_sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
              'size': list(map(int, template['size'])), 'forest_residents': len(used),
              'entities': len(template['entities']), 'ground_level': 0}
    (ROOT/'tools/forest_treehouse_manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    # Apply only resident metadata to the already edited runtime structures.
    for name in ('hyena_hideout', 'hyena_camp_tropical', 'hyena_camp_open', 'hyena_camp_snowy', 'green_lab'):
        path = OUTPUT / (name+'.nbt')
        current = nbt.load(path)
        bind_residents(current, name)
        save(current, path)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
