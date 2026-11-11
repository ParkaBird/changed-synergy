"""Verify new cuts and prevent accidental replacement of authored legacy female styles."""
from pathlib import Path
import base64
import hashlib
import json
import sys
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from import_hyena_trailcloth import layer, LOWER
from import_forest_canopy_outfit import LOWER as CANOPY_LOWER, garment, compile_section

ROOT = Path(__file__).resolve().parents[2]
output = Path(sys.argv[1])
trail = Path(sys.argv[2])
canopy = Path(sys.argv[3])
java = ROOT/'src/main/java/net/parkabird/changedsynergy/client/model'
ranger = json.loads((ROOT/'tools/forest_canopy_manifest.json').read_text(encoding='utf-8')).get('style') == 'ranger'
for source, generated, lower in [(trail, 'HyenaTrailclothLayers.java', LOWER),
                                  (canopy, 'ForestCanopyLayers.java', CANOPY_LOWER)]:
    data = json.loads(source.read_text(encoding='utf-8-sig'))
    for section in ('Upper', 'Lower'):
        code, _ = compile_section(garment(data), section) if generated == 'ForestCanopyLayers.java' and ranger else layer(data, section, lower)
        assert f'female{section}() {{\n{code}\n    }}' in (java/generated).read_text(encoding='utf-8'), 'Legacy feminine geometry changed'
manifest = json.loads((ROOT/'tools/synergy_female_outfits_manifest.json').read_text(encoding='utf-8'))
for variant, expected in manifest.items():
    path = output/expected['source']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected['sha256']
    data = json.loads(path.read_text(encoding='utf-8'))
    assert len(data['elements']) == expected['cubes']
    assert variant == 'legacyAviator' or not any('Plantoid' in c['name'] or c['name'] == 'Trailcloth_Center' for c in data['elements'])
    assert data['resolution'] == {'width': 128, 'height': 64 if variant in ('aviator', 'legacyAviator') else 128}
    assert all(c.get('box_uv', data['meta']['box_uv']) for c in data['elements'])
    if variant == 'hyenaTrailcloth':
        straps = [c for c in data['elements'] if 'WideStrap' in c['name']]
        assert len(straps) == 2 and all(c['to'][0]-c['from'][0] == 2 for c in straps)
        assert all(c['from'][0] > 0 or c['to'][0] < 0 for c in straps), 'Keep the central mane clear'
    if variant in ('hyenaTrailcloth', 'trailcloth', 'canopy', 'aviator'):
        assert sum(len(c.get('synergy_hidden_faces', [])) for c in data['elements']) == (4 if variant == 'canopy' else 2), 'Remove only interior seam caps'
    if variant == 'canopy' and not ranger:
        ribbon = next(c for c in data['elements'] if c['name'] == 'Canopy_CircletKnotRibbon')
        male = json.loads((canopy.parent/'ForestCanopyOutfit_Bipedal.bbmodel').read_text(encoding='utf-8'))
        male_ribbon = next(c for c in male['elements'] if c['name'] == ribbon['name'])
        assert all(ribbon[k] == male_ribbon[k] for k in ('from', 'to', 'uv_offset')), 'Ribbon ends must match male geometry/UVs'
texture = base64.b64decode(json.loads(trail.read_text(encoding='utf-8-sig'))['textures'][0]['source'].split(',',1)[1])
assert texture == (ROOT/'src/main/resources/assets/changed_synergy/textures/models/hyena_trailcloth_female.png').read_bytes(), 'Keep legacy feminine material'
patched = Image.open(ROOT/'src/main/resources/assets/changed_synergy/textures/models/hyena_trailcloth_synergy_female.png').convert('RGBA')
assert all(patched.getpixel((x, 10)) != (231, 211, 174, 255) for x in range(5, 13)), 'No ivory trim across the interior seam'
assert sum(patched.getpixel((x, 6)) == (231, 211, 174, 255) for x in range(5, 13)) == 5, 'Trim moved to upper garment edge'
print('FEMALE_OUTFIT_SOURCE_PASS: legacy feminine geometry/material intact; new flat cuts, straps, atlases and exported counts verified')
