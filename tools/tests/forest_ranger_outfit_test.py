"""Check that the author's dressed reference bodies are not imported as clothing geometry."""
from pathlib import Path
import base64
import hashlib
import io
import json
import sys
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from import_forest_canopy_outfit import garment, synergy_female, SOURCES, LOWER

ROOT = Path(__file__).resolve().parents[2]
folder = Path(sys.argv[1])
manifest = json.loads((ROOT/'tools/forest_canopy_manifest.json').read_text(encoding='utf-8'))
checks = 0
for variant, source in SOURCES.items():
    path = folder/(source+'.bbmodel')
    data = json.loads(path.read_text(encoding='utf-8-sig'))
    assert hashlib.sha256(path.read_bytes()).hexdigest() == manifest[variant]['source_sha256']
    clothes = garment(data)
    assert len(clothes['elements']) == manifest[variant]['cubes'] < len(data['elements'])
    assert {c['name'] for c in clothes['elements'] if c['name'] in LOWER} == {n for n in LOWER if n.startswith('Ranger_')}
    texture = Image.open(io.BytesIO(base64.b64decode(data['textures'][0]['source'].split(',', 1)[1])))
    size = data['resolution']
    assert texture.width/size['width'] == texture.height/size['height']
    head = next(g for g in data['groups'] if g['name'] == 'Head')
    for cube in clothes['elements']:
        if cube['name'] in ('Ranger_CelticCirclet', 'Ranger_CircletMedallion'):
            assert cube['from'][1]-cube.get('inflate', 0) >= head['origin'][1]+5.5, 'Tall eyes stay fully below the headband and medallion'
    for cube in clothes['elements']:
        assert cube['name'].startswith('Ranger_') and all(f['texture'] == 0 for f in cube['faces'].values())
        # The complete uninflated box UV net must fit its own atlas.
        width, height, depth = [cube['to'][i]-cube['from'][i] for i in range(3)]
        u, v = cube['uv_offset']
        assert 0 <= u and u+2*(width+depth) <= size['width']
        assert 0 <= v and v+depth+height <= size['height']
        checks += 1
    if variant == 'bipedal':
        fitted = synergy_female(clothes)
        assert not any('Plantoid' in c['name'] for c in fitted['elements'])
        assert sum(len(c.get('synergy_hidden_faces', [])) for c in fitted['elements']) == 4
        assert {c['name']: c for c in fitted['elements'] if 'Sleeve' in c['name']} == {c['name']: c for c in clothes['elements'] if 'Sleeve' in c['name']}
        for name in ('Ranger_Bodice', 'Ranger_Harness3Pt'):
            upper = next(c for c in fitted['elements'] if c['name'] == name+'_Upper')
            waist = next(c for c in fitted['elements'] if c['name'] == name+'_Waist')
            assert upper['from'][1] < waist['to'][1], 'Curved torso sections overlap instead of leaving a gap'
print(f'RANGER_SOURCE_PASS: {checks} clothing cubes; no reference-body geometry, valid variant UVs, curved flat cut and unchanged authored sources')
