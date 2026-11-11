"""Adapt the four contributed templates; requires nbtlib, only when reimporting.

Usage: python tools/adapt_research_structures.py D:/Downloads
The source files are never modified. Normal mod builds use the saved resources.
"""
import argparse
from collections import Counter, deque
from copy import deepcopy
import gzip
import hashlib
import io
import json
import re
from pathlib import Path

import nbtlib as nbt
from apply_research_documents import apply_documents
from structure_residents import bind_residents

NAMES = ('water_tower_changed', 'white_lab_tower', 'red_lab_tower', 'green_lab')
AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
WATER = 'minecraft:water'
WHITE_REPLACEMENTS = {
    'changed_addon:reinforced_wall': 'changed:wall_white',
    'changed_addon:reinforced_wall_silver_tiled': 'changed:tiles_gray',
    'changed_addon:reinforced_wall_caution': 'changed:wall_caution',
    'changed_addon:hand_scanner': 'minecraft:stone_button',
    'changed_addon:signal_block': 'changed:wall_white',
    'changed_addon:wolf_plushy': 'changed:white_pillow',
}
DECORATION_REPLACEMENTS = {
    'immersive_weathering:frosty_glass': 'minecraft:glass',
    'immersive_weathering:soot': 'minecraft:air',
    'immersive_weathering:changed_addon/luminara_leaf_pile': 'minecraft:moss_carpet',
    'immersive_weathering:changed/orange_tree_leaf_pile': 'minecraft:moss_carpet',
    'immersive_weathering:exposed_iron_bars': 'minecraft:iron_bars',
}
LOOT = {
    'water_tower_changed': 'changed:chests/decayed_lab_aqua',
    'white_lab_tower': 'changed:chests/decayed_lab_wl',
    'red_lab_tower': 'changed:chests/decayed_lab_dl',
    'green_lab': 'changed:chests/decayed_lab_origin',
}
LOOT_ALIASES = {f'changed:{tier}_tier_lab': f'changed:chests/{tier}_tier_lab' for tier in ('low', 'high')}
SWORDFISH_RESEARCH_CHEST = (43, 34, 33)


def adapt_lab_dependencies(template, name):
    """Keep contributed labs usable with Changed alone, including future reimports."""
    changes = Counter()
    palette = template['palette']
    replaced = set()
    for index, entry in enumerate(palette):
        old = str(entry['Name'])
        if name == 'red_lab_tower' and old == 'changed_addon:dark_latex_puddle':
            replacement = state('changed:dark_latex_puddle', {'occupied': 'false'})
        elif name == 'green_lab' and old == 'changed_addon:catalyzer':
            replacement = state('minecraft:furnace', {'facing': str(entry.get('Properties', {}).get('facing', 'south')), 'lit': 'false'})
        else:
            continue
        entry.clear()
        entry.update(replacement)
        replaced.add(index)
    for block in template['blocks']:
        if int(block['state']) in replaced:
            block.pop('nbt', None)
            changes['replaced remaining Addon equipment/puddles'] += 1
    if name == 'red_lab_tower':
        revision = json.loads(Path(__file__).with_name('red_lab_layout_patch.json').read_text(encoding='utf-8'))
        blocks = {tuple(map(int, block['pos'])): block for block in template['blocks']}
        for patch in revision['blocks']:
            pos = tuple(patch['pos'])
            block = blocks.get(pos)
            current = palette[int(block['state'])] if block else state('minecraft:air')
            after = state(patch['after']['Name'], patch['after'].get('Properties'))
            if current == after:
                continue
            assert current == state(patch['before']['Name'], patch['before'].get('Properties')), (pos, 'author layout patch no longer matches')
            if block is not None and block.get('nbt'):
                assert str(after['Name']) == 'minecraft:air' and str(current['Name']) in ('minecraft:barrel', 'changed:latex_container'), (pos, 'unexpected block entity in layout cleanup')
                assert not contains_document(block['nbt']), (pos, 'cleanup must not erase story items')
                block.pop('nbt')
            if after not in palette:
                palette.append(after)
            if block is None:
                block = nbt.Compound({'pos': nbt.List[nbt.Int](pos)})
                template['blocks'].append(block)
                blocks[pos] = block
            block['state'] = nbt.Int(palette.index(after))
            changes['Crossader hall and ceiling layout revision'] += 1
        removed_covers = {tuple(pos) for pos in revision['remove_covers']}
        template['latexCovers'] = nbt.List[nbt.Compound]([cover for cover in template.get('latexCovers', [])
                if tuple(map(int, cover['pos'])) not in removed_covers])
        removed_residents = {(entry['id'], tuple(entry['pos'])) for entry in revision.get('remove_entities', [])}
        residents = [entity for entity in template['entities']
                if (str(entity['nbt'].get('id')), tuple(map(float, entity['pos']))) not in removed_residents]
        changes['Crossader extra resident cleanup'] = len(template['entities']) - len(residents)
        template['entities'] = nbt.List[nbt.Compound](residents)
        kept = [entity for entity in template['entities'] if str(entity['nbt'].get('id')) != 'minecraft:creeper']
        changes['removed authored creepers'] = len(template['entities']) - len(kept)
        template['entities'] = nbt.List[nbt.Compound](kept)
        projectiles = [entity for entity in kept if str(entity['nbt'].get('id')) not in ('minecraft:arrow', 'minecraft:spectral_arrow')]
        changes['removed captured arrows'] = len(kept) - len(projectiles)
        template['entities'] = nbt.List[nbt.Compound](projectiles)
        for entity in projectiles:
            tag = entity['nbt']
            if str(tag.get('id')) == 'minecraft:painting' and str(tag.get('variant')) == 'changed:kade_tail':
                old = tuple(map(int, entity['blockPos']))
                if old != tuple(revision['painting']['blockPos']):
                    entity['blockPos'] = nbt.List[nbt.Int](revision['painting']['blockPos'])
                    entity['pos'] = nbt.List[nbt.Double](revision['painting']['pos'])
                    tag['Pos'] = deepcopy(entity['pos'])
                    for axis, value in zip(('X', 'Y', 'Z'), entity['blockPos']):
                        tag['Tile' + axis] = nbt.Int(value)
                    changes['Crossader painting layout revision'] += 1
            repairs = {
                'changed:dr_k': ((7, 20, 24), (7, 19, 24)),
                'changed:puro_portrait': ((27, 18, 9), (27, 19, 11)),
            }
            old_new = repairs.get(str(tag.get('variant')))
            if str(tag.get('id')) == 'minecraft:painting' and old_new and tuple(map(int, entity['blockPos'])) == old_new[0]:
                old, new = old_new
                for axis in range(3):
                    entity['blockPos'][axis] = nbt.Int(new[axis])
                    entity['pos'][axis] = nbt.Double(float(entity['pos'][axis]) + new[axis] - old[axis])
                tag['Pos'] = deepcopy(entity['pos'])
                for axis, value in zip(('X', 'Y', 'Z'), entity['blockPos']):
                    tag['Tile' + axis] = nbt.Int(value)
                changes['moved unsupported paintings onto clear walls'] += 1
    if name == 'water_tower_changed':
        blocks = {tuple(map(int, block['pos'])): block for block in template['blocks']}
        for pos, replacement in {
            (27, 15, 50): state('changed:lab_table', {'state': '0'}),
            (42, 15, 43): state('changed:tiles_gray'),
        }.items():
            block = blocks[pos]
            current = palette[int(block['state'])]
            if current == replacement:
                continue
            assert str(current['Name']) == 'minecraft:air' and not block.get('nbt'), (pos, 'equipment support moved')
            if replacement not in palette:
                palette.append(replacement)
            block['state'] = nbt.Int(palette.index(replacement))
            changes['supported aquatic laboratory furniture'] += 1
    return changes


def contains_document(value):
    if isinstance(value, dict):
        return (str(value.get('id')) in ('minecraft:written_book', 'minecraft:writable_book', 'changed:compact_disc')
                or any(key in ('Text', 'pages', 'title', 'author', 'Lore') and bool(child)
                       or contains_document(child) for key, child in value.items()))
    return isinstance(value, (list, nbt.List)) and any(contains_document(child) for child in value)


def validate_dependencies(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in ('id', 'Name', 'LootTable', 'variant') and isinstance(child, str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', child):
                assert child.split(':')[0] in {'minecraft', 'changed', 'changed_synergy'}, (key, child)
            validate_dependencies(child)
    elif isinstance(value, (list, nbt.List)):
        for child in value:
            validate_dependencies(child)


def add_swordfish_research(template):
    chest = next(b for b in template['blocks'] if tuple(map(int, b['pos'])) == SWORDFISH_RESEARCH_CHEST)
    assert str(template['palette'][int(chest['state'])]['Name']) == 'minecraft:chest'
    tag = chest['nbt']
    assert not tag.get('Items') and str(tag['LootTable']) in (LOOT['water_tower_changed'], 'changed_synergy:chests/swordfish_research')
    tag['LootTable'] = nbt.String('changed_synergy:chests/swordfish_research')


def clean_occupant(tag):
    for key in ('UUID', 'Attributes', 'Brain', 'HurtByTimestamp', 'HurtTime', 'DeathTime',
                'ForgeCaps', 'ForgeData', 'CitadelData', 'BalmData', 'alphaScale', 'isAlpha'):
        tag.pop(key, None)
    tag['Motion'] = nbt.List[nbt.Double]([0, 0, 0])
    tag['FallDistance'] = nbt.Float(0)
    tag['Fire'] = nbt.Short(-1)
    from structure_residents import mark_default_eyes
    mark_default_eyes(tag)
    for passenger in tag.get('Passengers', []):
        clean_occupant(passenger)


def state(name, properties=None):
    result = nbt.Compound({'Name': nbt.String(name)})
    if properties:
        result['Properties'] = nbt.Compound({k: nbt.String(v) for k, v in properties.items()})
    return result


def adapt_green_crystal(template):
    """Inset the support into the floor so both halves fit beneath the original glass cap."""
    blocks = {tuple(map(int, b['pos'])): b for b in template['blocks']}
    replacements = {
        (31, 1, 12): ('minecraft:glowstone', state('changed:dark_latex_block')),
        (31, 2, 12): ('minecraft:budding_amethyst', state('changed:dark_latex_crystal_large', {'half': 'lower'})),
        (31, 3, 12): ('minecraft:amethyst_cluster', state('changed:dark_latex_crystal_large', {'half': 'upper'})),
    }
    changed = 0
    for pos, (old, replacement) in replacements.items():
        block = blocks[pos]
        current = template['palette'][int(block['state'])]
        assert not block.get('nbt'), (pos, 'unexpected block entity in the display')
        if current == replacement:
            continue
        assert str(current['Name']) == old, (pos, 'crystal display moved or changed')
        if replacement not in template['palette']:
            template['palette'].append(replacement)
        block['state'] = nbt.Int(template['palette'].index(replacement))
        changed += 1
    return changed


def adapt(source, name, destination):
    template = nbt.load(source)
    size = tuple(map(int, template['size']))
    palette = template['palette']
    changes = adapt_lab_dependencies(template, name)
    state_counts = Counter(int(b['state']) for b in template['blocks'])
    for index, entry in enumerate(palette):
        old = str(entry['Name'])
        if name == 'white_lab_tower' and old in WHITE_REPLACEMENTS:
            properties = entry.get('Properties', {})
            new = WHITE_REPLACEMENTS[old]
            props = None
            if old.endswith('hand_scanner'):
                props = {'face': 'wall', 'facing': str(properties['facing']), 'powered': 'false'}
            elif old.endswith('wolf_plushy'):
                props = {'facing': str(properties.get('facing', 'north'))}
            entry.clear()
            entry.update(state(new, props))
            changes[old + ' -> ' + new] += state_counts[index]
        elif old == 'terramity:mossy_dungeon_block':
            entry.clear()
            entry.update(state('minecraft:mossy_stone_bricks'))
            changes['Terramity -> mossy stone bricks'] += state_counts[index]
        elif old in DECORATION_REPLACEMENTS:
            new = DECORATION_REPLACEMENTS[old]
            props = {k: str(v) for k, v in entry.get('Properties', {}).items()
                     if k in ('north', 'south', 'east', 'west', 'waterlogged')} if new == 'minecraft:iron_bars' else None
            entry.clear()
            entry.update(state(new, props))
            changes[old + ' -> ' + new] += state_counts[index]
        elif old == 'minecraft:structure_block':
            entry.clear()
            entry.update(state('minecraft:grass_block' if name == 'green_lab' else 'minecraft:air'))
            changes['removed structure editor'] += state_counts[index]
        if old.startswith('minecraft:') and 'weathering' in entry.get('Properties', {}):
            del entry['Properties']['weathering']
            if not entry['Properties']:
                del entry['Properties']
            changes['removed external weathering property'] += state_counts[index]
    names = [str(entry['Name']) for entry in palette]
    blocks = {tuple(map(int, block['pos'])): block for block in template['blocks']}

    # Saved sea water is not part of the building. Keep sealed aquarium water.
    outside_water = set()
    if name == 'water_tower_changed':
        water = {p for p, b in blocks.items() if names[int(b['state'])] == WATER}
        queue = deque(p for p in water if any(p[i] in (0, size[i] - 1) for i in range(3)))
        outside_water.update(queue)
        while queue:
            x, y, z = queue.popleft()
            for pos in ((x-1,y,z), (x+1,y,z), (x,y-1,z), (x,y+1,z), (x,y,z-1), (x,y,z+1)):
                if pos in water and pos not in outside_water:
                    outside_water.add(pos)
                    queue.append(pos)

    tops = {}
    for (x, y, z), block in blocks.items():
        block_name = names[int(block['state'])]
        if block_name not in AIR | {WATER, 'minecraft:kelp', 'minecraft:kelp_plant', 'minecraft:seagrass', 'minecraft:tall_seagrass'}:
            tops[x, z] = max(tops.get((x, z), -1), y)
    covers = [c for c in template.get('latexCovers', [])
              if any(str(v) == 'true' for k, v in template['latexCoverPalette'][int(c['state'])].get('Properties', {}).items()
                     if k in ('up', 'down', 'north', 'south', 'east', 'west'))]
    covered_positions = {tuple(map(int, c['pos'])) for c in covers}
    kept = []
    for pos, block in blocks.items():
        x, y, z = pos
        block_name = names[int(block['state'])]
        if pos not in covered_positions and (pos in outside_water or (block_name in AIR and y > tops.get((x, z), -1))):
            changes['omitted exterior padding'] += 1
            continue
        tag = block.get('nbt')
        if tag is not None:
            if str(tag.get('id', '')).startswith('changed_addon:') and name == 'white_lab_tower':
                del block['nbt']
                if block_name == 'changed:white_pillow':
                    block['nbt'] = nbt.Compound({'id': nbt.String('changed:pillow')})
                tag = None
            elif str(tag.get('id', '')) == 'minecraft:structure_block':
                del block['nbt']
                tag = None
            if tag is not None:
                tag.pop('ForgeCaps', None)
                table = str(tag.get('LootTable', ''))
                if table in LOOT_ALIASES:
                    tag['LootTable'] = nbt.String(LOOT_ALIASES[table])
                    changes['corrected tiered loot table path'] += 1
                if 'scheduledCommands' in tag:
                    assert not tag['scheduledCommands'], 'Unexpected stasis commands'
                # Do not overwrite authored books, passwords or dispenser traps.
                if str(tag.get('id')) in ('minecraft:chest', 'minecraft:trapped_chest', 'minecraft:barrel') and not tag.get('Items') and 'LootTable' not in tag:
                    tag.pop('Items', None)
                    tag['LootTable'] = nbt.String(LOOT[name])
                    changes['empty chest/barrel receives loot'] += 1
                scrub_item_owners(tag)
        kept.append(block)
    template['blocks'] = nbt.List[nbt.Compound](kept)
    template['latexCovers'] = nbt.List[nbt.Compound](covers)
    height = max(int(b['pos'][1]) for b in kept) + 1
    template['size'][1] = nbt.Int(height)
    entities = []
    occupants = []
    for entity in template['entities']:
        tag = entity['nbt']
        if not str(tag.get('id', '')):
            changes['removed empty entity record'] += 1
        elif str(tag.get('id')) == 'changed:seat_entity':
            # Editor seats retain absolute old-world anchors; keep their occupants at the local seat position.
            for passenger in tag.get('Passengers', []):
                record = deepcopy(entity)
                record['nbt'] = passenger
                occupants.append(record)
            changes['replaced saved seat with its occupants'] += 1
        else:
            occupants.append(entity)
    for entity in occupants:
        tag = entity['nbt']
        # Captured death drops are temporary world debris, not authored container loot.
        if str(tag.get('id')) in ('minecraft:item', 'minecraft:experience_orb'):
            changes['removed loose world drops'] += 1
            continue
        # Recreate occupants with their species' actual stats and no old-world AI.
        clean_occupant(tag)
        tag['Pos'] = deepcopy(entity['pos'])
        scrub_item_owners(tag)
        entities.append(entity)
    template['entities'] = nbt.List[nbt.Compound](entities)
    if name == 'green_lab':
        changes['dark latex crystal display'] = adapt_green_crystal(template)
    if name == 'water_tower_changed':
        add_swordfish_research(template)
        changes['swordfish research disc chest'] += 1
    changes['reviewed structure documents'] = apply_documents(template, name)
    namespaces = {n.split(':')[0] for n in names}
    allowed = {'minecraft', 'changed', 'changed_synergy'}
    assert namespaces <= allowed, (name, namespaces)
    bind_residents(template, name)
    validate_dependencies(template)
    assert len({tuple(map(int, b['pos'])) for b in kept}) == len(kept)
    assert all(all(0 <= int(b['pos'][i]) < int(template['size'][i]) for i in range(3)) for b in kept)
    buffer = io.BytesIO()
    template.write(buffer)
    destination.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))
    return {'source_filename': source.name, 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'size': list(map(int, template['size'])),
            'blocks': len(kept), 'covers': len(covers), 'entities': len(template['entities']), 'changes': dict(changes)}


def scrub_item_owners(tag):
    if isinstance(tag, dict):
        if 'form' in tag and 'safe' in tag:
            tag.pop('owner', None)
        for value in tag.values():
            scrub_item_owners(value)
    elif isinstance(tag, (list, nbt.List)):
        for value in tag:
            scrub_item_owners(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--source-suffix', default='', help='Suffix before .nbt, e.g. "(1)" for revised Downloads')
    parser.add_argument('--source', action='append', metavar='NAME=FILE', help='Import only these named templates; repeat for a partial update')
    parser.add_argument('--output', type=Path, help='Optional staging/export directory')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    destination = args.output or root / 'src/main/resources/data/changed_synergy/structures'
    destination.mkdir(parents=True, exist_ok=True)
    sources = dict(value.split('=', 1) for value in args.source) if args.source else {
        name: ('green_lab_tower' if name == 'green_lab' else name) + args.source_suffix + '.nbt' for name in NAMES}
    assert set(sources) <= set(NAMES), 'Unknown template name'
    manifest = root / 'tools/research_structures_manifest.json'
    report = json.loads(manifest.read_text(encoding='utf-8')) if args.source and manifest.exists() else {}
    for name, filename in sources.items():
        report[name] = adapt(args.input / filename, name, destination / (name + '.nbt'))
        report[name]['output_sha256'] = hashlib.sha256((destination / (name + '.nbt')).read_bytes()).hexdigest()
    manifest.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
