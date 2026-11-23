"""Mark the personal containers identified in the author's reference screenshots."""
import nbtlib as nbt

PRIVATE_STORES = {
    'hyena_hideout': [(10,4,10)],
    'forest_treehouse': [(13,12,19),(12,9,3),(24,7,10),(11,1,5),(16,29,9),(17,29,9),(9,29,11)],
    'observatory': [(6,2,20),(6,2,21),(7,2,20),(7,2,21),(7,3,21),(9,2,21),(9,2,22)],
}


def mark_private_stores(template, name):
    blocks = {tuple(map(int,b['pos'])): b for b in template.get('blocks', [])}
    for pos in PRIVATE_STORES.get(name, []):
        block = blocks[pos]
        assert 'nbt' in block and 'lectern' not in str(template['palette'][int(block['state'])]['Name'])
        block['nbt'].setdefault('ForgeData',nbt.Compound())['ChangedSynergyPrivate'] = nbt.Byte(1)
