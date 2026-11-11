"""Dedicated flat/curved Synergy cuts. Legacy female projects and runtime layers remain untouched."""
from copy import deepcopy
from pathlib import Path
import base64
import hashlib
import json
import sys
import uuid

from import_hyena_trailcloth import layer, LOWER as TRAIL_LOWER
from import_forest_canopy_outfit import LOWER as CANOPY_LOWER
from import_eagle_owl_assets import layer as owl_layer

ROOT = Path(__file__).resolve().parents[1]


def remove(data, predicate):
    removed = {c['uuid'] for c in data['elements'] if predicate(c)}
    data['elements'] = [c for c in data['elements'] if c['uuid'] not in removed]
    def visit(nodes):
        nodes[:] = [n for n in nodes if not isinstance(n, str) or n not in removed]
        for n in nodes:
            if isinstance(n, dict):
                visit(n.get('children', []))
    visit(data['outliner'])


def replace(data, name, replacements):
    old = next(c for c in data['elements'] if c['name'] == name)
    def visit(nodes):
        for i, n in enumerate(nodes):
            if isinstance(n, dict):
                visit(n.get('children', []))
            elif n == old['uuid']:
                nodes[i:i+1] = [c['uuid'] for c in replacements]
                break
    visit(data['outliner'])
    data['elements'].remove(old)
    data['elements'].extend(replacements)


def piece(cube, suffix, low, high, inflate):
    c = deepcopy(cube)
    c['name'] += suffix
    c['uuid'] = str(uuid.uuid5(uuid.NAMESPACE_URL, 'changed_synergy:female/' + c['name']))
    c['from'][1], c['to'][1] = low, high
    c['inflate'] = inflate
    # Keep the original front/side texels at the same height after splitting.
    c['uv_offset'][1] += round(cube['to'][1] - high)
    return c


def main():
    trail, canopy, aviator, output = map(Path, sys.argv[1:5])
    output.mkdir(parents=True, exist_ok=True)
    originals = {p: p.read_bytes() for p in (trail, canopy, aviator)}
    variants = {}
    data = json.loads(originals[trail].decode('utf-8-sig'))
    remove(data, lambda c: 'Plantoid' in c['name'] or c['name'] == 'Trailcloth_Center')
    variants['trailcloth'] = deepcopy(data)
    fitted_top = next(c for c in variants['trailcloth']['elements'] if c['name'] == 'Trailcloth_Torso')
    fitted_top['from'][1] = 19.2
    fitted_top['uv_offset'][1] = 0
    fitted_top['synergy_hidden_faces'] = ['down']
    next(c for c in variants['trailcloth']['elements'] if c['name'] == 'Trailcloth_TorsoLower')['synergy_hidden_faces'] = ['up']
    top = next(c for c in data['elements'] if c['name'] == 'Trailcloth_Torso')
    # Cover the lower edge of the wider body segment before the garment narrows at the waist.
    cropped = piece(top, '_CroppedTop', 19.2, 23.7, .4)
    cropped['uv_offset'][1] = 2
    cropped['synergy_hidden_faces'] = ['down']
    straps = []
    for side, x in [('Left', -3.7), ('Right', 1.7)]:
        strap = piece(top, '_' + side + 'WideStrap', 23.7, 25.7, .4)
        strap['from'][0], strap['to'][0] = x, x + 2
        # Existing arm-wrap material keeps the original brown/orange cloth palette.
        strap['uv_offset'] = [87, 1]
        straps.append(strap)
    replace(data, top['name'], [cropped] + straps)
    lower = next(c for c in data['elements'] if c['name'] == 'Trailcloth_TorsoLower')
    hem = piece(lower, '_ShortHem', 17.7, 20.5, -.1)
    hem['synergy_hidden_faces'] = ['up']
    replace(data, lower['name'], [hem])
    hyena_texture = ROOT/'src/main/resources/assets/changed_synergy/textures/models/hyena_trailcloth_synergy_female.png'
    if hyena_texture.exists():
        data['textures'][0]['source'] = 'data:image/png;base64,'+base64.b64encode(hyena_texture.read_bytes()).decode()
    variants['hyenaTrailcloth'] = data

    data = json.loads(originals[canopy].decode('utf-8-sig'))
    remove(data, lambda c: 'Plantoid' in c['name'])
    ribbon = next(c for c in data['elements'] if c['name'] == 'Canopy_CircletKnotRibbon')
    # Match the male headband's depth; the old female offset exposed a second gold end face.
    ribbon['from'][2], ribbon['to'][2] = 3.85, 4.85
    for name, upper, lower in [('Canopy_Bodice', .42, -.08), ('Canopy_CrossBaldric', .48, -.02)]:
        c = next(c for c in data['elements'] if c['name'] == name)
        top, bottom = piece(c, '_Upper', 18.5, c['to'][1], upper), piece(c, '_Waist', c['from'][1], 19.5, lower)
        top['synergy_hidden_faces'], bottom['synergy_hidden_faces'] = ['down'], ['up']
        replace(data, name, [top, bottom])
    next(c for c in data['elements'] if c['name'] == 'Canopy_ShawlCollar')['inflate'] = .5
    variants['canopy'] = data

    data = json.loads(originals[aviator].decode('utf-8-sig'))
    # Runtime clothing export already omits the owl body, but editable derivatives should too.
    groups = {g['uuid']: g['name'] for g in data['groups']}
    garment_ids = set()
    def collect(nodes, clothing=False):
        for n in nodes:
            if isinstance(n, dict):
                collect(n.get('children', []), clothing or groups[n['uuid']].startswith('Outfit_'))
            elif clothing:
                garment_ids.add(n)
    collect(data['outliner'])
    remove(data, lambda c: c['uuid'] not in garment_ids)
    legacy = deepcopy(data)
    # The original unisex vest sat inside Changed's female chest. Keep that cut for flat bodies,
    # and add fitted panels only for the legacy feminine anatomy.
    trail_data = json.loads(originals[trail].decode('utf-8-sig'))
    panels = []
    for original in trail_data['elements']:
        if 'Plantoid' not in original['name'] and original['name'] != 'Trailcloth_Center':
            continue
        panel = deepcopy(original)
        panel['name'] = panel['name'].replace('Trailcloth_', 'Aviator_')
        panel['uuid'] = str(uuid.uuid5(uuid.NAMESPACE_URL, 'changed_synergy:legacy_aviator/' + panel['name']))
        for key in ('from', 'to', 'origin'):
            panel[key][1] -= 1.2
        panel['uv_offset'] = [96, 0]
        panel['inflate'] += .12
        panels.append(panel)
    def add_panels(nodes):
        for node in nodes:
            if isinstance(node, dict):
                if groups[node['uuid']] == 'Outfit_Vest':
                    node['children'].extend(c['uuid'] for c in panels)
                    return
                add_panels(node.get('children', []))
    add_panels(legacy['outliner'])
    legacy['elements'].extend(panels)
    variants['legacyAviator'] = legacy
    c = next(c for c in data['elements'] if c['name'] == 'Vest')
    top, bottom = piece(c, '_Upper', 18.5, 24, .5), piece(c, '_Waist', 15, 19.5, 0)
    top['synergy_hidden_faces'], bottom['synergy_hidden_faces'] = ['down'], ['up']
    replace(data, 'Vest', [top, bottom])
    variants['aviator'] = data

    methods, report = [], {}
    for variant, data in variants.items():
        data['name'] = 'Aviator_LegacyFemale' if variant == 'legacyAviator' else 'SynergyFemale_' + variant
        assert variant == 'legacyAviator' or not any('Plantoid' in c['name'] for c in data['elements'])
        if variant in ('aviator', 'legacyAviator'):
            code, count = owl_layer(data, True)
            methods.append(f'    public static LayerDefinition {variant}() {{\n{code}\n    }}')
        else:
            count = 0
            for section in ('Upper', 'Lower'):
                code, total = layer(data, section, CANOPY_LOWER if variant == 'canopy' else TRAIL_LOWER)
                count += total
                methods.append(f'    public static LayerDefinition {variant}{section}() {{\n{code}\n    }}')
        assert count == len(data['elements']), (variant, count, len(data['elements']))
        path = output / (data['name'] + '.bbmodel')
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        report[variant] = {'source': path.name, 'cubes': count, 'quads': count * 6 - sum(len(c.get('synergy_hidden_faces', [])) for c in data['elements']),
                           'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    java = '''package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated dedicated Synergy female cuts; external females retain the legacy layers. */
public final class SynergyFemaleOutfitLayers {
    private SynergyFemaleOutfitLayers() {}
''' + '\n'.join(methods) + '\n}\n'
    (ROOT/'src/main/java/net/parkabird/changedsynergy/client/model/SynergyFemaleOutfitLayers.java').write_text(java, encoding='utf-8')
    for p, content in originals.items():
        assert p.read_bytes() == content, 'Do not alter legacy source projects'
    (ROOT/'tools/synergy_female_outfits_manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
