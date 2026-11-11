"""Import authored desert garments and the live jerboa export; keep their source files intact."""
from copy import deepcopy
from pathlib import Path
import base64, hashlib, io, json, sys
from PIL import Image
from import_spotted_hyena_models import convert
from import_hyena_trailcloth import layer

ROOT=Path(__file__).resolve().parents[1]
LOWER={'Nomad_WaistBelt','Nomad_BeltBuckle','Nomad_KilimSashTab','Nomad_CompassPouch','Nomad_HipSkirt',
       'Nomad_RightThighPanel','Nomad_LeftThighPanel','Nomad_SnakeWaistBelt','Nomad_SnakeBeltBuckle',
       'Nomad_SnakeKilimSashTab','Nomad_SnakeCompassPouch','Nomad_SnakeHipSkirt',
       'Leader_WaistBelt','Leader_BeltBuckle','Leader_KilimSashTab','Leader_CompassPouch','Leader_OasisGourd',
       'Leader_HipSkirt','Leader_RightThighPanel','Leader_LeftThighPanel'}

def normalize(source):
    data=deepcopy(source);groups={g['uuid']:g for g in data.get('groups',[])}
    def visit(nodes):
        for node in nodes:
            if not isinstance(node,dict):continue
            if 'name' in node:groups[node['uuid']]={k:deepcopy(v) for k,v in node.items() if k!='children'}
            visit(node.get('children',[]))
    visit(data['outliner']);data['groups']=list(groups.values());return data

def prepare_body(source):
    data=normalize(source)
    groups={g['uuid']:g for g in data['groups']};cubes={c['uuid']:c for c in data['elements']};fixed=[]
    def visit(nodes,ear=False):
        for node in nodes:
            if isinstance(node,dict):visit(node.get('children',[]),ear or groups[node['uuid']]['name'] in ('LeftEar','RightEar'))
            elif ear and cubes[node]['name']=='Back':
                cube=cubes[node]
                # Ear-color overlays should sit against the shell rather than protrude as thick blocks.
                for point in ('from','to'):cube[point][2]-=.16
                cube['stretch'][2]=.016
                fixed.append(node)
    visit(data['outliner']);assert len(fixed)==4
    return data

def garment(source):
    data=normalize(source);allowed={c['uuid'] for c in data['elements'] if c['name'].startswith(('Nomad_','Leader_'))}
    data['elements']=[c for c in data['elements'] if c['uuid'] in allowed]
    def visit(nodes):
        return [dict(n,children=visit(n.get('children',[]))) if isinstance(n,dict) else n for n in nodes if isinstance(n,dict) or n in allowed]
    data['outliner']=visit(data['outliner']);assert data['elements'];return data

def main():
    package,live,output=map(Path,sys.argv[1:4]);output.mkdir(parents=True,exist_ok=True)
    models=package/'models';manifest={};methods=[];textures=ROOT/'src/main/resources/assets/changed_synergy/textures/models'
    for name,source in [('Bipedal','LatexFennecFoxUnified'),('FourArm','LatexSilkMothUnified'),('Tail','LatexSnake'),('Pup','GasWolfPup'),('Jerboa','LatexJerboa')]:
        path=models/(source+'_DesertOutfit.bbmodel');raw=json.loads(path.read_text(encoding='utf-8-sig'));data=garment(raw)
        reference=normalize(raw);reference['elements']=[c for c in reference['elements'] if not c['name'].startswith(('Nomad_','Leader_'))]
        ids={c['uuid'] for c in reference['elements']}
        def prune(nodes):return [dict(n,children=prune(n.get('children',[]))) if isinstance(n,dict) else n for n in nodes if isinstance(n,dict) or n in ids]
        reference['outliner']=prune(reference['outliner'])
        refpath=output/(name+'Reference.bbmodel');refpath.write_text(json.dumps(reference,ensure_ascii=False,indent=2),encoding='utf-8')
        texture_name='desert_'+name.lower()+'.png'
        code,_=convert(refpath,'',texture_name='models/'+texture_name.removesuffix('.png'),required_bones={'Head','Torso','LeftArm','RightArm'})
        methods.append(f'    public static LayerDefinition {name.lower()}Reference() {{\n{code}\n    }}')
        for section in ['Upper','Lower']:
            code,count=layer(data,section,LOWER);code=code.replace('LayerDefinition.create(mesh, 128, 128)','LayerDefinition.create(mesh, 256, 128)')
            methods.append(f'    public static LayerDefinition {name.lower()}{section}() {{\n{code}\n    }}')
        (output/(name+'Garment.bbmodel')).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
        manifest[name]={'source':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'garment_cubes':len(data['elements']),'reference_cubes':len(reference['elements']),'texture':texture_name}
        # Other anatomies retain their own cut, with the leader's indigo/brass palette.
        image=Image.open(io.BytesIO(base64.b64decode(raw['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
        if name!='Jerboa':
            for y in range(image.height):
                for x in range(128,image.width):
                    r,g,b,a=image.getpixel((x,y))
                    if a and r>g*1.2 and r>b*1.2:image.putpixel((x,y),(int(b*.65+24),int(g*.6+36),int(r*.4+45),a))
            image.save(textures/('desert_'+name.lower()+'_leader.png'))
    female=garment(json.loads((models/'LatexFennecFoxUnified_DesertOutfit.bbmodel').read_text(encoding='utf-8-sig')))
    from adapt_synergy_female_outfits import piece,replace
    bodice=next(c for c in female['elements'] if c['name']=='Nomad_Bodice')
    upper=piece(bodice,'_Upper',18.5,bodice['to'][1],.35);waist=piece(bodice,'_Waist',bodice['from'][1],19.5,-.1)
    upper['synergy_hidden_faces']=['down'];waist['synergy_hidden_faces']=['up'];replace(female,bodice['name'],[upper,waist])
    for section in ['Upper','Lower']:
        code,_=layer(female,section,LOWER);code=code.replace('LayerDefinition.create(mesh, 128, 128)','LayerDefinition.create(mesh, 256, 128)')
        methods.append(f'    public static LayerDefinition synergyFemale{section}() {{\n{code}\n    }}')
    legacy=garment(json.loads((models/'LatexFennecFoxUnified_DesertOutfit.bbmodel').read_text(encoding='utf-8-sig')))
    bodice=next(c for c in legacy['elements'] if c['name']=='Nomad_Bodice');drape=deepcopy(bodice)
    import uuid
    drape['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,'changed_synergy:desert_legacy_female_drape'));drape['name']='Nomad_BustDrape'
    drape['from'][1]=19;drape['to'][1]=22;drape['from'][2]-=1.25;drape['to'][2]=drape['from'][2]+1.5;drape['synergy_hidden_faces']=['up','down'];legacy['elements'].append(drape)
    def attach(nodes):
        for node in nodes:
            if isinstance(node,dict):
                if bodice['uuid'] in node['children']:node['children'].append(drape['uuid']);return True
                if attach(node['children']):return True
        return False
    assert attach(legacy['outliner'])
    for section in ['Upper','Lower']:
        code,_=layer(legacy,section,LOWER);code=code.replace('LayerDefinition.create(mesh, 128, 128)','LayerDefinition.create(mesh, 256, 128)')
        methods.append(f'    public static LayerDefinition legacyFemale{section}() {{\n{code}\n    }}')
    code='package net.parkabird.changedsynergy.client.model;\n\nimport net.minecraft.client.model.geom.PartPose;\nimport net.minecraft.client.model.geom.builders.*;\n\n/** Authored garment geometry and fitting references; generated by import_desert_content.py. */\npublic final class DesertOutfitLayers {\n    private DesertOutfitLayers() {}\n'+'\n'.join(methods)+'\n}\n'
    (ROOT/'src/main/java/net/parkabird/changedsynergy/client/model/DesertOutfitLayers.java').write_text(code,encoding='utf-8')
    body=prepare_body(json.loads(live.read_text(encoding='utf-8')));path=output/'LatexJerboa.bbmodel';path.write_text(json.dumps(body,ensure_ascii=False,indent=2),encoding='utf-8')
    code,report=convert(path,'',texture_name='latex_jerboa',required_bones={'Head','Torso','LeftArm','RightArm','LeftEarPivot','RightEarPivot','TailSenary'})
    (ROOT/'src/main/java/net/parkabird/changedsynergy/client/model/JerboaModelLayers.java').write_text('package net.parkabird.changedsynergy.client.model;\nimport net.minecraft.client.model.geom.PartPose;\nimport net.minecraft.client.model.geom.builders.*;\npublic final class JerboaModelLayers {\n private JerboaModelLayers() {}\n public static LayerDefinition body() {\n'+code+'\n }\n}\n',encoding='utf-8')
    for icon in ['desert_nomad_outfit','jerboa_leader_outfit']:
        (ROOT/f'src/main/resources/assets/changed_synergy/textures/item/{icon}.png').write_bytes((package/f'icons/item_textures/{icon}.png').read_bytes())
    manifest['liveJerboa']=report
    (ROOT/'tools/desert_content_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
