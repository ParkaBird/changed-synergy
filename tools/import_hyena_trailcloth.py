"""Import the two supplied clothing projects and their exact embedded textures; sources stay untouched."""
from pathlib import Path
import base64
import hashlib
import json
import math
import sys
import io
from PIL import Image
from import_spotted_hyena_models import number,vector

ROOT=Path(__file__).resolve().parents[1]
LOWER={'Trailcloth_WaistBelt','Trailcloth_IvoryClasp','Trailcloth_HipPouch','Trailcloth_LeftSplitWrap','Trailcloth_RightSplitWrap'}

def layer(data,section,lower=LOWER):
    groups={g['uuid']:g for g in data['groups']};cubes={e['uuid']:e for e in data['elements']}
    lines=['        MeshDefinition mesh = new MeshDefinition();','        PartDefinition root = mesh.getRoot();']
    count=0
    def visit(nodes,parent,pivot):
        nonlocal count
        for node in nodes:
            if isinstance(node,dict):
                g=groups[node['uuid']];name=g['name'];origin=g.get('origin',[0,0,0]);rotation=g.get('rotation',[0,0,0])
                offset=[pivot[0]-origin[0],pivot[1]-origin[1],origin[2]-pivot[2]]
                angles=[math.radians(-rotation[0]),math.radians(-rotation[1]),math.radians(rotation[2])]
                lines.append(f'        PartDefinition {name} = {parent}.addOrReplaceChild("{name}", CubeListBuilder.create(), PartPose.offsetAndRotation({vector(offset+angles)}));')
                visit(node.get('children',[]),name,origin)
            else:
                cube=cubes[node]
                if (cube['name'] in lower)!=(section=='Lower'):continue
                assert cube['type']=='cube' and cube.get('box_uv',data['meta']['box_uv'])
                origin=cube.get('origin',pivot);rotation=cube.get('rotation',[0,0,0]);low,high=cube['from'],cube['to']
                offset=[pivot[0]-origin[0],pivot[1]-origin[1],origin[2]-pivot[2]]
                angles=[math.radians(-rotation[0]),math.radians(-rotation[1]),math.radians(rotation[2])]
                box=[origin[0]-high[0],origin[1]-high[1],low[2]-origin[2]]+[high[i]-low[i] for i in range(3)]
                uv=cube.get('uv_offset',[0,0]);mirror='.mirror()' if cube.get('mirror_uv',False) else ''
                stretch=cube.get('stretch',[1,1,1]);deformation=[cube.get('inflate',0)+(high[i]-low[i])*(stretch[i]-1)/2 for i in range(3)]
                deform=number(deformation[0]) if deformation.count(deformation[0])==3 else vector(deformation)
                builder=f'CubeListBuilder.create().texOffs({uv[0]}, {uv[1]}){mirror}.addBox({vector(box)}, new CubeDeformation({deform}))'
                if cube.get('synergy_hidden_faces'):
                    directions=', '.join('net.minecraft.core.Direction.'+face.upper() for face in cube['synergy_hidden_faces'])
                    builder=f'((net.ltxprogrammer.changed.client.CubeListBuilderExtender){builder}).removeLastFaces({directions}).finish()'
                lines.append(f'        {parent}.addOrReplaceChild("{cube["name"]}_r1", {builder}, PartPose.offsetAndRotation({vector(offset+angles)}));')
                count+=1
    visit(data['outliner'],'root',[0,24,0])
    lines.append('        return LayerDefinition.create(mesh, 128, 128);')
    return '\n'.join(lines),count

def main():
    methods=[];report={}
    for sex,source in zip(['male','female'],map(Path,sys.argv[1:3])):
        data=json.loads(source.read_text(encoding='utf-8-sig'))
        assert data['meta']['model_format']=='modded_entity' and data['modded_entity_flip_y']
        assert data['resolution']=={'width':128,'height':128}
        count=0
        for section in ['Upper','Lower']:
            body,parts=layer(data,section);count+=parts
            methods.append(f'    public static LayerDefinition {sex}{section}() {{\n{body}\n    }}')
        assert count==len(data['elements'])
        texture=base64.b64decode(data['textures'][0]['source'].split(',',1)[1]);assert texture.startswith(b'\x89PNG\r\n\x1a\n')
        (ROOT/f'src/main/resources/assets/changed_synergy/textures/models/hyena_trailcloth_{sex}.png').write_bytes(texture)
        # Hyenas keep the mane clearance; other wearers get the missing back-strap pixels.
        image=Image.open(io.BytesIO(texture)).convert('RGBA')
        bridge=[(21,8),(20,9),(21,9)] if sex=='male' else [(x,y) for x in range(20,22) for y in range(5,10)]
        for pixel in bridge:image.putpixel(pixel,(146,65,44,255))
        derived=ROOT/f'src/main/resources/assets/changed_synergy/textures/models/hyena_trailcloth_{sex}_other.png'
        image.save(derived)
        report[sex]={'source':source.name,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'cubes':count,'texture_sha256':hashlib.sha256(texture).hexdigest(),'other_texture_sha256':hashlib.sha256(derived.read_bytes()).hexdigest(),'back_strap_bridge':bridge}
    java='''package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Imported from the supplied bbmodels with original pivots, geometry and UVs. */
public final class HyenaTrailclothLayers {
    private HyenaTrailclothLayers() {}
'''+ '\n'.join(methods)+'\n}\n'
    (ROOT/'src/main/java/net/parkabird/changedsynergy/client/model/HyenaTrailclothLayers.java').write_text(java,encoding='utf-8')
    (ROOT/'tools/hyena_trailcloth_manifest.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
