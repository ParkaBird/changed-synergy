"""Offline regression for the missing export-selector column beside the well."""
from copy import deepcopy
from pathlib import Path
import sys
import nbtlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from adapt_hyena_structures import repair_well_ground

template = nbtlib.load(ROOT / 'src/main/resources/data/changed_synergy/structures/hyena_hideout.nbt')
before = deepcopy(template)
assert repair_well_ground(template) == 0
blocks = {tuple(map(int, b['pos'])): b for b in template['blocks']}
air = next(i for i, s in enumerate(template['palette']) if str(s['Name']) == 'minecraft:air')
for y in range(4):
    blocks[(0, y, 0)]['state'] = nbtlib.Int(air)
assert repair_well_ground(template) == 4
assert template == before, 'Repair must preserve the chest, residents, lore and all other blocks'
print('HIDEOUT_WELL_GROUND_PASS: four-block repair, idempotence and preservation')
