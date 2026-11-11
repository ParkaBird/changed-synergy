"""Mark the personal containers identified in the author's reference screenshots."""
import nbtlib as nbt

PRIVATE_STORES = {
    'desert_refuge': [(33,22,22),(34,22,22),(2,7,16),(2,7,17),(34,7,16)],
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
        block['nbt']['ForgeData'].pop('ChangedSynergyPublicStorage',None)
        if 'community_supplies' in str(block['nbt'].get('CustomName','')):block['nbt'].pop('CustomName')
