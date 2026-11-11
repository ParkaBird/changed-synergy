"""Check the hideout's real beds and the importer's non-destructive repeat run."""
from pathlib import Path
import copy
import sys
import nbtlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from camp_rest_area import HIDEOUT_BEDS, replace_hideout_bedrolls


def main():
    template = nbtlib.load(ROOT / 'src/main/resources/data/changed_synergy/structures/hyena_hideout.nbt')
    blocks = {tuple(map(int, block['pos'])): block for block in template['blocks']}
    offsets = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}
    for head, foot, extra, facing in HIDEOUT_BEDS:
        assert tuple(a + b for a, b in zip(foot, offsets[facing])) == head
        for pos, part in ((head, 'head'), (foot, 'foot')):
            block = blocks[pos]
            state = template['palette'][int(block['state'])]
            assert state['Name'] == 'minecraft:orange_bed'
            assert state['Properties'] == {'facing': facing, 'part': part, 'occupied': 'false'}
            assert block['nbt']['id'] == 'minecraft:bed'
        assert template['palette'][int(blocks[extra]['state'])]['Name'] == 'minecraft:air'
    before = copy.deepcopy(template)
    assert replace_hideout_bedrolls(template, 'hyena_hideout') == 0 and template == before
    print('CAMP_REST_AREA_PASS: six complete orange beds, matching head/foot directions, cleared third carpets and idempotent import')


if __name__ == '__main__':
    main()
