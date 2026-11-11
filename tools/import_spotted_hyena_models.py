"""Import the supplied box-UV Java-entity projects without changing the source files.

Usage: python tools/import_spotted_hyena_models.py <folder-containing-bbmodels>
"""
from pathlib import Path
import base64
import hashlib
import json
import math
import sys

ROOT = Path(__file__).resolve().parents[1]


def number(value):
    text = f'{0 if abs(value) < 0.0000005 else value:.6f}'.rstrip('0').rstrip('.')
    return text + ('.0F' if '.' not in text else 'F')


def vector(values):
    return ', '.join(number(v) for v in values)


def convert(source, sex, texture_name=None, required_bones=None):
    data = json.loads(source.read_text(encoding='utf-8'))
    assert data['meta']['model_format'] == 'modded_entity' and data['modded_entity_flip_y']
    groups = {g['uuid']: g for g in data['groups']}
    cubes = {c['uuid']: c for c in data['elements']}
    lines = ['        MeshDefinition mesh = new MeshDefinition();', '        PartDefinition root = mesh.getRoot();']
    count = 0
    names = set()

    def visit(nodes, parent, pivot):
        nonlocal count
        for node in nodes:
            if isinstance(node, dict):
                group = groups[node['uuid']]
                if not group.get('export', True):
                    continue
                name = group['name']
                assert name not in names, f'duplicate bone: {name}'
                names.add(name)
                origin = group.get('origin', [0, 0, 0])
                offset = [pivot[0]-origin[0], pivot[1]-origin[1], origin[2]-pivot[2]]
                rotation = group.get('rotation', [0, 0, 0])
                angles = [math.radians(-rotation[0]), math.radians(-rotation[1]), math.radians(rotation[2])]
                tail_joint = name.startswith('Tail') and name != 'Tail'
                lines.append(f'        PartDefinition {name} = {parent}.addOrReplaceChild("{name}", CubeListBuilder.create(), PartPose.offsetAndRotation({vector(offset + ([0, 0, 0] if tail_joint else angles))}));')
                content = name
                if tail_joint:
                    content = name + '_bind_r1'
                    lines.append(f'        PartDefinition {content} = {name}.addOrReplaceChild("{content}", CubeListBuilder.create(), PartPose.offsetAndRotation({vector([0, 0, 0] + angles)}));')
                visit(node.get('children', []), content, origin)
            else:
                cube = cubes[node]
                if not cube.get('export', True):
                    continue
                assert cube['type'] == 'cube' and cube.get('box_uv', data['meta']['box_uv'])
                count += 1
                # Static helper suffix prevents EMF/Fresh Moves from treating geometry as an animated bone.
                name = f'cube_{count}_r1'
                origin = cube.get('origin', pivot)
                offset = [pivot[0]-origin[0], pivot[1]-origin[1], origin[2]-pivot[2]]
                rotation = cube.get('rotation', [0, 0, 0])
                angles = [math.radians(-rotation[0]), math.radians(-rotation[1]), math.radians(rotation[2])]
                low, high = cube['from'], cube['to']
                box = [origin[0]-high[0], origin[1]-high[1], low[2]-origin[2]] + [high[i]-low[i] for i in range(3)]
                uv = cube.get('uv_offset', [0, 0])
                mirror = '.mirror()' if cube.get('mirror_uv', False) else ''
                stretch = cube.get('stretch', [1, 1, 1])
                deformation = [cube.get('inflate', 0) + (high[i]-low[i])*(stretch[i]-1)/2 for i in range(3)]
                deform = number(deformation[0]) if deformation.count(deformation[0]) == 3 else vector(deformation)
                lines.append(f'        {parent}.addOrReplaceChild("{name}", CubeListBuilder.create().texOffs({uv[0]}, {uv[1]}){mirror}.addBox({vector(box)}, new CubeDeformation({deform})), PartPose.offsetAndRotation({vector(offset + angles)}));')

    visit(data['outliner'], 'root', [0, 24, 0])
    assert not any('NOT_EXPORTED' in n for n in names)
    assert (required_bones or {'Head', 'Torso', 'LeftArm', 'RightArm', 'TailPrimary', 'TailSecondary', 'TailTertiary', 'LeftEarPivot', 'RightEarPivot'}) <= names
    width, height = data['resolution']['width'], data['resolution']['height']
    lines.append(f'        return LayerDefinition.create(mesh, {width}, {height});')
    texture = base64.b64decode(data['textures'][0]['source'].split(',', 1)[1])
    assert texture.startswith(b'\x89PNG\r\n\x1a\n')
    (ROOT/f'src/main/resources/assets/changed_synergy/textures/{texture_name or "latex_spotted_hyena_" + sex}.png').write_bytes(texture)
    report = {'source': source.name, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'cubes': count, 'bones': len(names), 'texture': [width, height]}
    return '\n'.join(lines), report


def main():
    folder = Path(sys.argv[1])
    male, m = convert(folder/'LatexSpottedHyenaMaleUnified.bbmodel', 'male')
    female, f = convert(folder/'LatexSpottedHyenaFemaleUnified.bbmodel', 'female')
    code = '''package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated by tools/import_spotted_hyena_models.py; original pivots and UVs retained. */
public final class SpottedHyenaModelLayers {
    private SpottedHyenaModelLayers() {}
    public static LayerDefinition male() {
%s
    }
    public static LayerDefinition female() {
%s
    }
}
''' % (male, female)
    (ROOT/'src/main/java/net/parkabird/changedsynergy/client/model/SpottedHyenaModelLayers.java').write_text(code, encoding='utf-8')
    (ROOT/'tools/spotted_hyena_models_manifest.json').write_text(json.dumps({'male': m, 'female': f}, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'male': m, 'female': f}))


if __name__ == '__main__':
    main()
