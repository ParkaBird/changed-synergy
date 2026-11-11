"""Dedicated, empty community reserves, separate from authored loot."""
from copy import deepcopy
import gzip
import hashlib
import io
import json
from pathlib import Path
import nbtlib as nbt

MARKER = 'ChangedSynergyPublicStorage'
LAYOUTS = {
    'forest_treehouse': ([(20, 1, 6), (20, 1, 7)], 'east'),
    'hyena_hideout': ([(11, 4, 18), (11, 4, 19)], 'east'),
    'observatory': ([(31, 1, 5), (31, 1, 6)], 'east'),
    'desert_refuge': ([(8, 14, 15), (8, 14, 16)], 'west'),
}
PREVIOUS_POSITIONS = {
    'forest_treehouse': [(26,1,4),(26,1,5)],
    'hyena_hideout': [(18,4,18),(19,4,18)],
    'observatory': [(37,1,4),(37,1,5)],
}

def add_public_store(template, name):
    if name not in LAYOUTS or 'blocks' not in template:
        return 0
    positions, facing = LAYOUTS[name]
    blocks = {tuple(map(int, b['pos'])): b for b in template['blocks']}
    changed = 0
    if name in PREVIOUS_POSITIONS:
        for pos in PREVIOUS_POSITIONS[name]:
            block = blocks[pos]
            if not block.get('nbt', {}).get('ForgeData', {}).get(MARKER): continue
            assert not block['nbt'].get('Items') and 'LootTable' not in block['nbt']
            air = nbt.Compound({'Name': nbt.String('minecraft:air')})
            if air not in template['palette']: template['palette'].append(air)
            block['state'] = nbt.Int(template['palette'].index(air))
            block.pop('nbt'); changed += 1
    for pos, side in zip(positions, ('right', 'left') if facing == 'west' else ('left', 'right')):
        block = blocks[pos]
        existing = bool(block.get('nbt', {}).get('ForgeData', {}).get(MARKER))
        if existing:
            assert str(template['palette'][int(block['state'])]['Name']) == 'minecraft:chest'
        else:
            converted = name == 'desert_refuge' and str(template['palette'][int(block['state'])]['Name']) == 'minecraft:chest'
            assert converted or str(template['palette'][int(block['state'])]['Name']) == 'minecraft:air', (name, pos, 'occupied public-store site')
            assert converted or 'nbt' not in block, (name, pos, 'existing block entity')
        assert str(template['palette'][int(blocks[(pos[0],pos[1]+1,pos[2])]['state'])]['Name']) == 'minecraft:air', (name, pos, 'blocked lid')
        state = nbt.Compound({'Name': nbt.String('minecraft:chest'), 'Properties': nbt.Compound({
            'facing': nbt.String(facing), 'type': nbt.String(side), 'waterlogged': nbt.String('false')})})
        if state not in template['palette']:
            template['palette'].append(state)
        if existing and template['palette'][int(block['state'])] == state:
            continue
        block['state'] = nbt.Int(template['palette'].index(state))
        if existing:
            changed += 1
            continue
        block['nbt'] = nbt.Compound({'id': nbt.String('minecraft:chest'),
            'Items': nbt.List[nbt.Compound](), 'CustomName': nbt.String(json.dumps({'translate': 'container.changed_synergy.community_supplies'})),
            'ForgeData': nbt.Compound({MARKER: nbt.Byte(1)})})
        changed += 1
    return changed

def main():
    root = Path(__file__).resolve().parents[1]
    for name, (positions, facing) in LAYOUTS.items():
        path = root/'src/main/resources/data/changed_synergy/structures'/(name+'.nbt')
        template = nbt.load(path)
        before = deepcopy(template)
        assert add_public_store(template, name) in (0, 2, 4)
        assert add_public_store(template, name) == 0
        for old, new in zip(before['blocks'], template['blocks']):
            assert old == new or tuple(map(int, old['pos'])) in positions + PREVIOUS_POSITIONS.get(name, [])
        for key in before:
            if key not in ('blocks', 'palette'):
                assert before[key] == template[key], (name, key)
        buffer = io.BytesIO(); template.write(buffer)
        path.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))
        manifest = root/'tools'/({'forest_treehouse':'forest_treehouse_manifest.json', 'hyena_hideout':'hyena_structures_manifest.json', 'observatory':'observatory_manifest.json','desert_refuge':'desert_refuge_manifest.json'}[name])
        data = json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else {}
        record = data[name] if name == 'hyena_hideout' else data
        record['output_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        record['community_storage'] = {'positions':positions, 'facing':facing, 'empty':True, 'loot_table':False}
        manifest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(name+': dedicated community double chest; all original blocks, stories, loot and residents preserved')

if __name__ == '__main__':
    main()
