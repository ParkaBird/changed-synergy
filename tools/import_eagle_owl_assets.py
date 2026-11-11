"""Import the supplied owl, aviator clothing and two-block perches; sources stay untouched."""
from pathlib import Path
from copy import deepcopy
import base64, hashlib, json, math

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/changed_synergy'
JAVA = ROOT / 'src/main/java/net/parkabird/changedsynergy/client/model'
EDITABLE = ROOT.parent / 'outputs/eagle-owl-integration-20261101'

def num(n):
    return f'{0 if abs(n)<1e-7 else n:.6f}'.rstrip('0').rstrip('.') + ('F' if n % 1 else '.0F')

def vec(v): return ', '.join(num(n) for n in v)

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')

def prepare(data):
    data=deepcopy(data); seen=set(); remove=set(); repairs=[]
    for cube in data['elements']:
        key=json.dumps({k:v for k,v in cube.items() if k not in ('uuid','name','color')},sort_keys=True)
        if key in seen: remove.add(cube['uuid']); continue
        seen.add(key)
        if any(abs(a-b)<1e-6 for a,b in zip(cube['from'],cube['to'])) and cube.get('inflate',0)<.0125:
            cube['inflate']=.0125;repairs.append(cube['name'])
    data['elements']=[c for c in data['elements'] if c['uuid'] not in remove]
    cubes={c['uuid']:c for c in data['elements']}
    groups={g['uuid']:g for g in data['groups']}
    def visit(nodes):
        for node in list(nodes):
            if isinstance(node,str):
                if node in remove:nodes.remove(node)
                continue
            visit(node.get('children',[]))
            if groups[node['uuid']]['name']=='Feathers':
                for side,sign in [('Left',-1),('Right',1)]:
                    children=[c for c in node['children'] if isinstance(c,str) and cubes[c]['name'].startswith(side+'EarTuft')]
                    if not children:continue
                    import uuid
                    uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'changed_synergy:eagle_owl/'+side+'EarTuftPivot'))
                    data['groups'].append({'uuid':uid,'name':side+'EarTuftPivot','origin':[sign*2.5,31.25,-3.65],'export':True})
                    node['children']=[c for c in node['children'] if c not in children]+[{'uuid':uid,'children':children}]
    visit(data['outliner'])
    return data,{'separated_flat_surfaces':repairs,'duplicate_cubes_removed':len(remove)}

def layer(data, clothing):
    groups={g['uuid']:g for g in data['groups']};cubes={c['uuid']:c for c in data['elements']}
    lines=['        MeshDefinition mesh = new MeshDefinition();','        PartDefinition root = mesh.getRoot();'];count=0
    def visit(nodes,parent,pivot,outfit=False):
        nonlocal count
        for node in nodes:
            if isinstance(node,dict):
                group=groups[node['uuid']]; name=group['name'];origin=group.get('origin',[0,0,0])
                delta=[pivot[0]-origin[0],pivot[1]-origin[1],origin[2]-pivot[2]]
                rot=group.get('rotation',[0,0,0]);angles=[math.radians(-rot[0]),math.radians(-rot[1]),math.radians(rot[2])]
                lines.append(f'        PartDefinition {name} = {parent}.addOrReplaceChild("{name}", CubeListBuilder.create(), PartPose.offsetAndRotation({vec(delta+angles)}));')
                visit(node.get('children',[]),name,origin,outfit or name.startswith('Outfit_'))
            else:
                c=cubes[node]
                if outfit!=clothing or not c.get('export',True):continue
                assert c.get('box_uv',data['meta'].get('box_uv',False)),c['name']
                origin=c.get('origin',pivot);lo,hi=c['from'],c['to'];rot=c.get('rotation',[0,0,0]);uv=c.get('uv_offset',[0,0])
                delta=[pivot[0]-origin[0],pivot[1]-origin[1],origin[2]-pivot[2]]
                angles=[math.radians(-rot[0]),math.radians(-rot[1]),math.radians(rot[2])]
                box=[origin[0]-hi[0],origin[1]-hi[1],lo[2]-origin[2]]+[hi[i]-lo[i] for i in range(3)]
                count+=1;name=c['name']+'_r'+str(count);mirror='.mirror()' if c.get('mirror_uv') else ''
                builder=f'CubeListBuilder.create().texOffs({int(uv[0])}, {int(uv[1])}){mirror}.addBox({vec(box)}, new CubeDeformation({num(c.get("inflate",0))}))'
                if c.get('synergy_hidden_faces'):
                    directions=', '.join('net.minecraft.core.Direction.'+face.upper() for face in c['synergy_hidden_faces'])
                    builder=f'((net.ltxprogrammer.changed.client.CubeListBuilderExtender){builder}).removeLastFaces({directions}).finish()'
                lines.append(f'        {parent}.addOrReplaceChild("{name}", {builder}, PartPose.offsetAndRotation({vec(delta+angles)}));')
    visit(data['outliner'],'root',[0,24,0])
    lines.append(f'        return LayerDefinition.create(mesh, {data["resolution"]["width"]}, {data["resolution"]["height"]});')
    return '\n'.join(lines),count

def perches(source,name,vertical):
    data=json.loads(source.read_text(encoding='utf-8'));textures={}
    for i,t in enumerate(data['textures']):
        path=ASSETS/'textures/block'/f'{name}_{t["name"]}.png';path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(base64.b64decode(t['source'].split(',',1)[1]));textures[str(i)]='changed_synergy:block/'+path.stem
        textures[t['uuid']]='changed_synergy:block/'+path.stem
    textures['particle']=next(iter(textures.values()))
    groups={g['uuid']:g['name'] for g in data['groups']};cubes={c['uuid']:c for c in data['elements']}
    parts={}
    for node in data['outliner']:
        part=groups[node['uuid']].split('_')[-1];els=[]
        for uid in node['children']:
            c=cubes[uid];out={k:deepcopy(c[k]) for k in ('from','to')}
            axis=1 if vertical else 0;offset=16 if part in ('upper','foot') else 0
            for k in ('from','to'):out[k][axis]-=offset
            rot=c.get('rotation',[0,0,0]);nonzero=[i for i,r in enumerate(rot) if r]
            assert len(nonzero)<=1,c['name']
            if nonzero:
                origin=deepcopy(c.get('origin',[8,8,8]));origin[axis]-=offset
                out['rotation']={'origin':origin,'axis':'xyz'[nonzero[0]],'angle':rot[nonzero[0]],'rescale':c.get('rescale',False)}
            out['faces']={face:{k:v for k,v in f.items() if k in ('uv','rotation','cullface','tintindex')}|{'texture':'#'+str(f['texture'])} for face,f in c['faces'].items() if f.get('texture') is not None}
            els.append(out)
        write_json(ASSETS/'models/block'/f'{name}_{part}.json',{'render_type':'minecraft:cutout','ambientocclusion':True,'textures':textures,'elements':els});parts[part]=els
    full=[]
    for part,elements in parts.items():
        for element in deepcopy(elements):
            if part in ('upper','foot'):
                axis=1 if vertical else 0
                for key in ('from','to'):element[key][axis]+=16
                if 'rotation' in element:element['rotation']['origin'][axis]+=16
            full.append(element)
    if not vertical:
        # Java item transforms pivot about (8,8,8), while the authored rail spans 0..32.
        for element in full:
            for key in ('from','to'):element[key][0]-=8
            if 'rotation' in element:element['rotation']['origin'][0]-=8
    display=deepcopy(data.get('display',{'gui':{'rotation':[30,225,0],'translation':[0,-3,0],'scale':[.4,.4,.4]}}))
    if not vertical:display.setdefault('gui',{})['translation']=[0,0,0]
    write_json(ASSETS/'models/item'/f'{name}.json',{'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':textures,'elements':full,
            'display':display})
    variants={}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
        for half,part in zip(('lower','upper'),parts):variants[f'facing={facing},half={half}']={'model':f'changed_synergy:block/{name}_{part}','y':rotation,'uvlock':False}
    write_json(ASSETS/'blockstates'/f'{name}.json',{'variants':variants})
    return {'source':source.name,'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'parts':list(parts)}

def main():
    import sys
    source=Path(sys.argv[1] if len(sys.argv)>1 else 'D:/Downloads');EDITABLE.mkdir(parents=True,exist_ok=True)
    reports={};methods=[]
    for filename,method,clothing,texture in [('UnifiedLatexEagleOwl.bbmodel','body',False,'latex_eagle_owl'),('UnifiedLatexEagleOwl_Outfit.bbmodel','outfit',True,'eagle_owl_aviator_outfit')]:
        p=source/filename;data,report=prepare(json.loads(p.read_text(encoding='utf-8')))
        write_json(EDITABLE/filename,data);code,count=layer(data,clothing)
        methods.append(f'    public static LayerDefinition {method}() {{\n{code}\n    }}')
        out=ASSETS/'textures'/('models' if clothing else '')/(texture+'.png');out.parent.mkdir(parents=True,exist_ok=True)
        out.write_bytes(base64.b64decode(data['textures'][0]['source'].split(',',1)[1]))
        reports[method]=report|{'source':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'runtime_cubes':count}
    (JAVA/'EagleOwlModelLayers.java').write_text('package net.parkabird.changedsynergy.client.model;\n\nimport net.minecraft.client.model.geom.PartPose;\nimport net.minecraft.client.model.geom.builders.*;\n\n/** Generated from the supplied projects; UVs and pivots are preserved. */\npublic final class EagleOwlModelLayers {\n'+'\n'.join(methods)+'\n}\n',encoding='utf-8')
    for name,vertical in [('eagle_owl_perch_stand',True),('eagle_owl_perch_rail',False)]:reports[name]=perches(source/(name+'.bbmodel'),name,vertical)
    icon=source/'eagle_owl_aviator_outfit.png';(ASSETS/'textures/item/eagle_owl_aviator_outfit.png').write_bytes(icon.read_bytes())
    write_json(ROOT/'tools/eagle_owl_assets_manifest.json',reports);print(json.dumps(reports,indent=2))

if __name__=='__main__':main()
