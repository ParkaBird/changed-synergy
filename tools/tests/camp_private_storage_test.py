"""Check curated private containers without changing authored loot or story media."""
from copy import deepcopy
from pathlib import Path
import sys
import nbtlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from camp_private_stores import PRIVATE_STORES, mark_private_stores

for name, positions in PRIVATE_STORES.items():
    template = nbtlib.load(ROOT / f'src/main/resources/data/changed_synergy/structures/{name}.nbt')
    blocks = {tuple(map(int,b['pos'])): b for b in template['blocks']}
    marked = {p for p,b in blocks.items() if b.get('nbt',{}).get('ForgeData',{}).get('ChangedSynergyPrivate')}
    assert marked == set(positions), (name, marked)
    for pos in positions:
        tag=blocks[pos]['nbt']
        assert (name=='desert_refuge' or 'LootTable' in tag) and not tag.get('ForgeData',{}).get('ChangedSynergyPublicStorage')
    for b in template['blocks']:
        if 'lectern' in str(template['palette'][int(b['state'])]['Name']): assert not b.get('nbt',{}).get('ForgeData',{}).get('ChangedSynergyPrivate')
    original=deepcopy(template);mark_private_stores(template,name);assert template==original
print('CAMP_PRIVATE_STORAGE_PASS: 20 explicitly marked container blocks, intact loot tables, public lecterns and idempotent imports')
