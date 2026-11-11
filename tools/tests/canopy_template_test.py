"""Resident conversion and journal pagination preserve the author's story."""
from copy import deepcopy
from pathlib import Path
import hashlib
import json
import sys
import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from adapt_forest_treehouse import update_canopy_book
from structure_residents import bind_residents

path = ROOT / 'src/main/resources/data/changed_synergy/structures/forest_treehouse.nbt'
template = nbt.load(path)
fixed = [e for e in template['entities'] if e['nbt'].get('ForgeData', {}).get('ChangedSynergyStructureResident', {}).get('Character')]
assert len(fixed) == 1
resident = fixed[0]
assert str(resident['nbt']['id']) == 'changed_synergy:latex_silk_moth'
assert list(map(float, resident['pos'])) == [14.414875221223042, 31., 18.745544768962077]
assert resident['nbt']['ForgeData']['ChangedSynergyStructureResident']['Outfit'] == 'changed_synergy:forest_canopy_outfit'
assert int(resident['nbt']['LocalVariantInfo']['eyeStyle']) == 2
assert int(resident['nbt']['LocalVariantInfo']['irisLeft']) == int(resident['nbt']['LocalVariantInfo']['irisRight']) == 0x90B8C2
for pos in ((16,29,9), (17,29,9)):
    chest = next(b for b in template['blocks'] if tuple(map(int,b['pos'])) == pos)
    assert str(chest['nbt']['LootTable']) == 'minecraft:chests/village/village_shepherd'
    assert 'Items' not in chest['nbt']
for entity in template['entities']:
    tag = entity['nbt']
    assert not any(key in tag for key in ('UUID','Brain','ForgeCaps'))
    assert not any(key.startswith(('SynergyEditor', 'ChangedSynergyLastSocialSight')) for key in tag.get('ForgeData',{}))
    if str(tag['id']).startswith(('changed:', 'changed_synergy:')):
        assert not any(bool(tag.get(key)) for key in ('NoAI','NoGravity','Invulnerable'))
assert len(template['entities']) == 12
assert sum(str(e['nbt']['id']) == 'changed:latex_bee' for e in template['entities']) == 3
assert any('Latex Silk Moth' in str(b.get('nbt', {}).get('Text', '')) for b in template['blocks'])
assert not any('Latex Leaf' in str(b.get('nbt', {}).get('Text', '')) for b in template['blocks'])
assert sum(str(e['nbt']['id']) == 'changed_synergy:latex_silk_moth' for e in template['entities']) == 2
before = deepcopy(template)
bind_residents(template, 'forest_treehouse')
assert template == before, 'Metadata import is idempotent'
books = [b['nbt']['Book'] for b in template['blocks'] if 'Book' in b.get('nbt', {})]
assert len(books) == 1 and str(books[0]['id']) == 'minecraft:writable_book'
pages = books[0]['tag']['pages']
text = ''.join(map(str, pages))
assert text.count('Latex Silk Moth') == 2 and 'Latex Leaf' not in text
assert 'author' not in books[0]['tag']
assert update_canopy_book(template) == 0 and template == before
# Reconstruct an unsplit old journal to test the conversion, including whitespace.
books[0]['tag']['pages'] = nbt.List[nbt.String]([text.replace('Latex Silk Moth', 'Latex Leaf')])
assert update_canopy_book(template) == 1
assert ''.join(map(str, books[0]['tag']['pages'])) == text, 'Only species wording and page boundaries may change'
assert all(len(str(p)) <= 165 for p in books[0]['tag']['pages'])
manifest = json.loads((ROOT / 'tools/forest_treehouse_manifest.json').read_text(encoding='utf-8'))
assert manifest['output_sha256'] == hashlib.sha256(path.read_bytes()).hexdigest()
print('CANOPY_TEMPLATE_PASS: one dressed canopy moth, original position, seven creatures, story preservation, pagination and manifest')
