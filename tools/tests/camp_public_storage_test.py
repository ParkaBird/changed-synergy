"""Three reserved chest pairs remain empty, connected and safe through reimports."""
from copy import deepcopy
import hashlib,json,sys
from pathlib import Path
import nbtlib as nbt
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from camp_public_stores import LAYOUTS,MARKER,add_public_store
directions={'north':(1,0),'east':(0,1),'south':(-1,0),'west':(0,-1)}
for name,(positions,facing) in LAYOUTS.items():
    path=ROOT/'src/main/resources/data/changed_synergy/structures'/(name+'.nbt')
    template=nbt.load(path);original=deepcopy(template);assert add_public_store(template,name)==0 and template==original
    blocks={tuple(map(int,b['pos'])):b for b in template['blocks']}
    marked=[p for p,b in blocks.items() if b.get('nbt',{}).get('ForgeData',{}).get(MARKER)]
    assert set(marked)==set(positions)
    for p in marked:
        block=blocks[p];state=template['palette'][int(block['state'])];tag=block['nbt'];properties=state['Properties']
        assert str(state['Name'])=='minecraft:chest' and str(properties['facing'])==facing and str(properties['waterlogged'])=='false'
        dx,dz=directions[facing];sign=1 if properties['type']=='left' else -1
        assert (p[0]+dx*sign,p[1],p[2]+dz*sign) in marked
        assert not tag['Items'] and 'LootTable' not in tag
        assert json.loads(str(tag['CustomName']))=={'translate':'container.changed_synergy.community_supplies'}
        assert str(template['palette'][int(blocks[(p[0],p[1]+1,p[2])]['state'])]['Name'])=='minecraft:air'
    manifest={'forest_treehouse':'forest_treehouse_manifest.json','hyena_hideout':'hyena_structures_manifest.json','observatory':'observatory_manifest.json','desert_refuge':'desert_refuge_manifest.json'}[name]
    data=json.loads((ROOT/'tools'/manifest).read_text(encoding='utf-8'));data=data[name] if name=='hyena_hideout' else data
    assert data['output_sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
print('CAMP_PUBLIC_STORAGE_DATA_PASS: four dedicated chest pairs, matching halves, empty inventories, clear lids, idempotent imports and manifest hashes')
