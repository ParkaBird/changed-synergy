"""Import the authored observatory; preserve its blocks, stories and loot."""
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


def main():
    source = Path(sys.argv[1])
    template = nbt.load(source)
    assert list(map(int, template['size'])) == [41, 26, 27]
    assert not template['entities'], 'Revisit residents before importing an occupied template'
    template['entities'] = nbt.List[nbt.Compound]()
    original = deepcopy(template['blocks'])
    for block in template['blocks']:
        if 'nbt' in block:
            block['nbt'].pop('ForgeCaps', None)
            block['nbt'].pop('ForgeData', None)
    residents = [
        ('changed_synergy:latex_eagle_owl', (5.5, 2, 14.5), True),
        ('changed:latex_yuin', (32.5, 1, 5.5), False),
        ('changed:latex_yuin', (28.5, 1, 16.5), False),
        ('changed:latex_blue_wolf', (32.5, 1, 9.5), False),
        ('changed:latex_pink_wyvern', (29.5, 1, 6.5), False),
    ]
    blocks = {tuple(map(int, b['pos'])): str(template['palette'][int(b['state'])]['Name']) for b in template['blocks']}
    for species, pos, chief in residents:
        xyz = [int(p) for p in pos]
        assert all(blocks[(xyz[0], xyz[1]+dy, xyz[2])] == 'minecraft:air' for dy in (0, 1, 2)), pos
        assert blocks[(xyz[0], xyz[1]-1, xyz[2])] in ('minecraft:snow_block', 'changed:orange_lab_carpeting'), pos
        tag = nbt.Compound({'id': nbt.String(species), 'Pos': nbt.List[nbt.Double](pos),
            'Rotation': nbt.List[nbt.Float]([90, 0]), 'PersistenceRequired': nbt.Byte(1),
            'ForgeData': nbt.Compound({'ChangedSynergyLightReputationGroup': nbt.String('mountain')})})
        if chief:
            tag['CustomName'] = nbt.String(json.dumps({'translate': 'npc.changed_synergy.observatory_chief.title'}))
            tag['LocalVariantInfo'] = nbt.Compound({'eyeStyle': nbt.Int(0), 'irisLeft': nbt.Int(0xF59A23),
                'irisRight': nbt.Int(0xF59A23), 'sclera': nbt.Int(0xFFFFFF), 'scale': nbt.Float(1)})
        template['entities'].append(nbt.Compound({'pos': nbt.List[nbt.Double](pos),
            'blockPos': nbt.List[nbt.Int](xyz), 'nbt': tag}))
    bind_residents(template, 'observatory')
    # Apart from empty/editor capabilities, every authored block entity stays intact.
    for before, after in zip(original, template['blocks']):
        if 'nbt' in before:
            before['nbt'].pop('ForgeCaps', None)
            before['nbt'].pop('ForgeData', None)
        assert before == after or tuple(map(int, before['pos'])) in LAYOUTS['observatory'][0]
    validate_dependencies(template)
    buffer = io.BytesIO()
    template.write(buffer)
    target = ROOT/'src/main/resources/data/changed_synergy/structures/observatory.nbt'
    target.write_bytes(gzip.compress(buffer.getvalue(), mtime=0))
    report = {'source': str(source), 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'output_sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'size': [41, 26, 27],
        'ground_level': 0, 'residents': [{'species': s, 'position': p, 'chief': c} for s, p, c in residents],
        'authored_blocks_stories_loot_preserved': True}
    (ROOT/'tools/observatory_manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
