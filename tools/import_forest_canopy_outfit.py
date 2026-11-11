"""Import the author's Ranger redesign, keeping the existing clothing item and fitting paths."""
from copy import deepcopy
from pathlib import Path
import base64
import hashlib
import io
import json
import sys
from PIL import Image
from import_hyena_trailcloth import layer

ROOT = Path(__file__).resolve().parents[1]
LOWER = {'Ranger_WaistBelt', 'Ranger_BeltBuckle', 'Ranger_BeltTongue',
         'Ranger_HipPouch', 'Ranger_HipSkirt', 'Ranger_LeftThighPanel', 'Ranger_RightThighPanel',
         'Canopy_WaistBelt', 'Canopy_BeltBuckle', 'Canopy_CocoonLantern',
         'Canopy_HerbSatchel', 'Canopy_HipPeplum', 'Canopy_LeftThighWrap', 'Canopy_RightThighWrap'}
SOURCES = {'fourArm': 'LatexSilkMoth_ForestOutfit', 'bipedal': 'LatexDeer_ForestOutfit',
           'female': 'LightLatexWolfFemale_ForestOutfit'}


def garment(source):
    data = deepcopy(source)
    groups = {g['uuid']: g for g in data['groups']}
    included = set()

    def visit(nodes, clothing=False):
        result = []
        for node in nodes:
            if isinstance(node, dict):
                node['children'] = visit(node.get('children', []), clothing or groups[node['uuid']]['name'].startswith('Outfit_'))
                if node['children']:
                    result.append(node)
            elif clothing:
                included.add(node)
                result.append(node)
        return result

    data['outliner'] = visit(data['outliner'])
    data['elements'] = [c for c in data['elements'] if c['uuid'] in included]
    assert data['elements'] and all(c['name'].startswith('Ranger_') for c in data['elements'])
    assert all({f.get('texture') for f in c['faces'].values()} == {0} for c in data['elements'])
    # Lift the band and medallion together above the native Tall eye's upper row.
    for cube in data['elements']:
        if cube['name'] in ('Ranger_CelticCirclet', 'Ranger_CircletMedallion'):
            for key in ('from', 'to', 'origin'):
                if key in cube:
                    cube[key][1] += 1.25
    return data


def synergy_female(bipedal):
    from adapt_synergy_female_outfits import piece, replace
    data = deepcopy(bipedal)
    # Keep a flat chest, the curved torso seam and fitted sleeves on Synergy's own females.
    for name, top_inflate, waist_inflate in [('Ranger_Bodice', .42, -.08), ('Ranger_Harness3Pt', .48, -.02)]:
        cube = next(c for c in data['elements'] if c['name'] == name)
        top = piece(cube, '_Upper', 18.5, cube['to'][1], top_inflate)
        waist = piece(cube, '_Waist', cube['from'][1], 19.5, waist_inflate)
        top['synergy_hidden_faces'], waist['synergy_hidden_faces'] = ['down'], ['up']
        replace(data, name, [top, waist])
    next(c for c in data['elements'] if c['name'] == 'Ranger_CapeletCollar')['inflate'] = .5
    return data


def female_reference(source):
    data = deepcopy(source)
    used = set()
    # The original female's torso and thighs differ from the moth's canonical fitting body.
    for node in data['outliner']:
        node['children'] = [child for child in node.get('children', []) if isinstance(child, str)]
        used.update(node['children'])
    data['elements'] = [c for c in data['elements'] if c['uuid'] in used]
    for cube in data['elements']:
        cube['uv_offset'] = [0, 0]
    assert {'Torso', 'TorsoLower', 'Waist'} <= {c['name'] for c in data['elements']}
    return data


def compile_section(data, section):
    code, count = layer(data, section, LOWER)
    size = data['resolution']
    return code.replace('LayerDefinition.create(mesh, 128, 128)',
                        f'LayerDefinition.create(mesh, {size["width"]}, {size["height"]})'), count


def main():
    folder = Path(sys.argv[1])
    output = folder.parent
    methods, report = [], {'style': 'ranger'}
    models = {}
    for variant, name in SOURCES.items():
        path = folder/(name+'.bbmodel')
        source_bytes = path.read_bytes()
        source = json.loads(source_bytes.decode('utf-8-sig'))
        assert source['meta']['model_format'] == 'modded_entity' and source['modded_entity_flip_y']
        data = models[variant] = garment(source)
        if variant == 'female':
            code, _ = layer(female_reference(source), 'Upper', set())
            methods.append(f'    public static LayerDefinition femaleReference() {{\n{code}\n    }}')
        count = 0
        for section in ('Upper', 'Lower'):
            code, total = compile_section(data, section)
            methods.append(f'    public static LayerDefinition {variant}{section}() {{\n{code}\n    }}')
            count += total
        assert count == len(data['elements'])
        texture = base64.b64decode(source['textures'][0]['source'].split(',', 1)[1])
        image = Image.open(io.BytesIO(texture))
        size = source['resolution']
        assert image.width/size['width'] == image.height/size['height']
        texture_name = {'fourArm': 'forest_canopy_four_arm', 'bipedal': 'forest_canopy_outfit',
                        'female': 'forest_canopy_female'}[variant]+'.png'
        (ROOT/'src/main/resources/assets/changed_synergy/textures/models'/texture_name).write_bytes(texture)
        report[variant] = {'source': path.name, 'source_sha256': hashlib.sha256(source_bytes).hexdigest(),
                           'cubes': count, 'texture': texture_name, 'texture_size': size,
                           'texture_sha256': hashlib.sha256(texture).hexdigest()}
        assert path.read_bytes() == source_bytes

    fitted = synergy_female(models['bipedal'])
    for section in ('Upper', 'Lower'):
        code, _ = compile_section(fitted, section)
        methods.append(f'    public static LayerDefinition synergyFemale{section}() {{\n{code}\n    }}')
    fitted_path = output/'RangerOutfit_SynergyFemale.bbmodel'
    fitted_path.write_text(json.dumps(fitted, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    report['synergyFemale'] = {'source': fitted_path.name, 'cubes': len(fitted['elements']),
                               'source_sha256': hashlib.sha256(fitted_path.read_bytes()).hexdigest()}
    java = '''package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Ranger clothing imported from the author's live projects, preserving their UVs and pivots. */
public final class ForestCanopyLayers {
    private ForestCanopyLayers() {}
'''+ '\n'.join(methods)+'\n}\n'
    (ROOT/'src/main/java/net/parkabird/changedsynergy/client/model/ForestCanopyLayers.java').write_text(java, encoding='utf-8')
    icon_path = Path(sys.argv[2])
    icon = icon_path.read_bytes()
    icon_image = Image.open(io.BytesIO(icon))
    assert icon_image.mode == 'RGBA' and icon_image.getextrema()[3][0] == 0
    (ROOT/'src/main/resources/assets/changed_synergy/textures/item/forest_canopy_outfit.png').write_bytes(icon)
    report['icon'] = {'source': icon_path.name, 'sha256': hashlib.sha256(icon).hexdigest()}
    (ROOT/'tools/forest_canopy_manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
