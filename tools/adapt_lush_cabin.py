"""Import the authored lush cabin; add ordinary camp residents (no chief yet)."""
from copy import deepcopy
from pathlib import Path
import gzip
import hashlib
import io
import json
import sys
import nbtlib as nbt
from adapt_research_structures import validate_dependencies
from structure_residents import bind_residents
from camp_public_stores import LAYOUTS

ROOT = Path(__file__).resolve().parents[1]

# Optional editor/mod blocks → vanilla stand-ins so the cabin has no hard soft-deps.
BLOCK_ALIASES = {
    'farmersdelight:stove': 'minecraft:smoker',
    'farmersdelight:skillet': 'minecraft:campfire',
    'farmersdelight:carrot_crate': 'minecraft:barrel',
    'farmersdelight:beetroot_crate': 'minecraft:barrel',
    'farmersdelight:potato_crate': 'minecraft:barrel',
    'quark:apple_crate': 'minecraft:barrel',
    'structure_gel:empty': 'minecraft:air',
}


def remap_palette(template):
    for entry in template['palette']:
        name = str(entry['Name'])
        if name in BLOCK_ALIASES:
            entry['Name'] = nbt.String(BLOCK_ALIASES[name])
            # Drop incompatible properties (crate facing, stove lit, etc.).
            props = entry.get('Properties')
            if props is None:
                continue
            if BLOCK_ALIASES[name] == 'minecraft:smoker':
                entry['Properties'] = nbt.Compound({
                    'facing': nbt.String(str(props.get('facing', 'north'))),
                    'lit': nbt.String(str(props.get('lit', 'false'))),
                })
            elif BLOCK_ALIASES[name] == 'minecraft:campfire':
                entry['Properties'] = nbt.Compound({
                    'facing': nbt.String(str(props.get('facing', 'north'))),
                    'lit': nbt.String('true'),
                    'signal_fire': nbt.String('false'),
                    'waterlogged': nbt.String('false'),
                })
            elif BLOCK_ALIASES[name] == 'minecraft:barrel':
                entry['Properties'] = nbt.Compound({
                    'facing': nbt.String('up'),
                    'open': nbt.String('false'),
                })
            elif BLOCK_ALIASES[name] == 'minecraft:air':
                entry.pop('Properties', None)


def scrub_foreign_ids(value):
    """Drop captured forge/caelus attribute names from entity trees."""
    if isinstance(value, dict):
        for key in list(value.keys()):
            child = value[key]
            if key in ('Name', 'AttributeName', 'id') and isinstance(child, str):
                ns = child.split(':', 1)[0]
                if ns in ('forge', 'caelus', 'structure_gel', 'farmersdelight', 'quark'):
                    value.pop(key, None)
                    continue
            scrub_foreign_ids(child)
    elif isinstance(value, (list, nbt.List)):
        for child in list(value):
            scrub_foreign_ids(child)


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r'D:\Downloads\lush_cabin.nbt')
    template = nbt.load(source)
    assert list(map(int, template['size'])) == [30, 30, 32]
    remap_palette(template)
    # Keep paintings / item frames / armor stands; strip any prior latex residents.
    kept = []
    for entity in template.get('entities', []):
        kind = str(entity['nbt'].get('id', ''))
        if kind.startswith(('changed:', 'changed_synergy:')) and 'armor_stand' not in kind:
            continue
        if entity['nbt'].get('ForgeData', {}).get('ChangedSynergyStructureResident'):
            continue
        # Captured world attributes can name forge: modifiers; recreate cleanly at spawn.
        entity['nbt'].pop('Attributes', None)
        entity['nbt'].pop('ForgeCaps', None)
        scrub_foreign_ids(entity['nbt'])
        kept.append(entity)
    template['entities'] = nbt.List[nbt.Compound](kept)
    for block in template['blocks']:
        if 'nbt' in block:
            block['nbt'].pop('ForgeCaps', None)
            block['nbt'].pop('ForgeData', None)
            scrub_foreign_ids(block['nbt'])
    original = deepcopy(template['blocks'])
    # Ordinary household only — chief / story NPC comes later.
    # Species from Changed's cave_spawners (overworld cave natural set).
    residents = [
        ('changed:latex_stiger', (21.5, 6, 7.5), False),
        ('changed:latex_traffic_cone_dragon', (7.5, 10, 19.5), False),
        ('changed:latex_translucent_lizard', (6.5, 10, 12.5), False),
        ('changed:latex_kobold', (8.5, 15, 9.5), False),
        ('changed:latex_stiger', (8.5, 20, 22.5), False),
    ]
    blocks = {tuple(map(int, b['pos'])): str(template['palette'][int(b['state'])]['Name']) for b in template['blocks']}

    def airish(name):
        return name in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air') or 'carpet' in name

    for species, pos, chief in residents:
        xyz = [int(p) for p in pos]
        assert airish(blocks[(xyz[0], xyz[1], xyz[2])]) and airish(blocks[(xyz[0], xyz[1] + 1, xyz[2])]), pos
        assert not airish(blocks[(xyz[0], xyz[1] - 1, xyz[2])]), pos
        tag = nbt.Compound({
            'id': nbt.String(species),
            'Pos': nbt.List[nbt.Double](pos),
            'Rotation': nbt.List[nbt.Float]([0, 0]),
            'PersistenceRequired': nbt.Byte(1),
            'ForgeData': nbt.Compound({'ChangedSynergyLightReputationGroup': nbt.String('cave')}),
        })
        template['entities'].append(nbt.Compound({
            'pos': nbt.List[nbt.Double](pos),
            'blockPos': nbt.List[nbt.Int](xyz),
            'nbt': tag,
        }))
    bind_residents(template, 'lush_cabin')
    allowed = set(LAYOUTS['lush_cabin'][0]) | set(__import__('camp_public_stores').PREVIOUS_POSITIONS.get('lush_cabin', []))
    for before, after in zip(original, template['blocks']):
        if 'nbt' in before:
            before['nbt'].pop('ForgeCaps', None)
            before['nbt'].pop('ForgeData', None)
        assert before == after or tuple(map(int, before['pos'])) in allowed
    validate_dependencies(template)
    buffer = io.BytesIO()
    template.write(buffer)
    target = ROOT / 'src/main/resources/data/changed_synergy/structures/lush_cabin.nbt'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))
    report = {
        'source': str(source),
        'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'output_sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
        'size': [30, 30, 32],
        'underground': True,
        'ground_level': 6,
        'chief': False,
        'residents': [{'species': s, 'position': list(p), 'chief': c} for s, p, c in residents],
        'community_storage': {'positions': LAYOUTS['lush_cabin'][0], 'facing': LAYOUTS['lush_cabin'][1]},
        'authored_blocks_stories_loot_preserved': True,
    }
    (ROOT / 'tools/lush_cabin_manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
