"""Import the supplied small camps, preserving their authored loot and buried geometry."""
from copy import deepcopy
from pathlib import Path
import gzip,hashlib,io,json,sys
import nbtlib as nbt
from small_camp_storage import mark_camp_store

ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources/data/changed_synergy'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def save(path,data):
    stream=io.BytesIO();data.write(stream);path.write_bytes(gzip.compress(stream.getvalue(),mtime=0))
def main():
    source=Path(sys.argv[1]);manifest={}
    for name,level,reserve,residents,biomes in [
        ('oasis',2,(11,3,17),[('latex_jerboa',9.5,3,16.5),('changed:latex_fennec_fox',11.5,3,16.5)],['desert']),
        ('muddy_campsite',4,(8,5,15),[('latex_jerboa',6.5,5,10.5),('changed:latex_red_dragon',5.5,5,13.5),('changed:green_lizard',8.5,5,13.5),('changed:latex_fennec_fox',9.5,5,13.5)],['badlands','wooded_badlands','eroded_badlands'])]:
        src=source/(name+'.nbt');data=nbt.load(src);original=deepcopy(data);palette=data['palette'];blocks={tuple(map(int,b['pos'])):b for b in data['blocks']}
        def block(p):return str(palette[int(blocks[p]['state'])]['Name'])
        assert block(reserve)=='minecraft:barrel'
        loot_id='desert_camp_oasis' if name=='oasis' else 'desert_camp_muddy'
        barrels=[]
        for b in data['blocks']:
            if block(tuple(map(int,b['pos'])))=='minecraft:barrel' and 'LootTable' in b.get('nbt',{}):
                assert str(b['nbt']['LootTable'])=='minecraft:chests/village/village_cartographer'
                b['nbt']['LootTable']=nbt.String('changed_synergy:chests/'+loot_id);barrels.append(list(map(int,b['pos'])))
        assert len(barrels)==1
        data['entities']=nbt.List[nbt.Compound](list(data.get('entities',[])))
        write(RES/f'loot_tables/chests/{loot_id}.json',{'type':'minecraft:chest','pools':[{'name':'original_supplies','rolls':1,'entries':[{'type':'minecraft:loot_table','name':'minecraft:chests/village/village_cartographer'}]}]})
        for species,x,y,z in residents:
            species=species if ':' in species else 'changed_synergy:'+species
            assert all(block((int(x),y+dy,int(z)))=='minecraft:air' for dy in [0,1]),(name,species,x,y,z)
            pos=nbt.List[nbt.Double]([x,y,z]);tag=nbt.Compound({'id':nbt.String(species),'Pos':deepcopy(pos),'Rotation':nbt.List[nbt.Float]([180,0]),'PersistenceRequired':nbt.Byte(1),'ForgeData':nbt.Compound({'ChangedSynergyLightReputationGroup':nbt.String('arid'),'ChangedSynergyRandomStructureEyes':nbt.Byte(1),'ChangedSynergyStructureResident':nbt.Compound({'Template':nbt.String(name),'LocalPos':deepcopy(pos),'Size':deepcopy(data['size']),'Yaw':nbt.Float(180),'Outfit':nbt.String('changed_synergy:desert_nomad_outfit'),'Camp':nbt.Byte(1),'Character':nbt.Byte(0),'ReserveOnly':nbt.Byte(1)})})})
            data['entities'].append(nbt.Compound({'pos':pos,'blockPos':nbt.List[nbt.Int]([int(x),y,int(z)]),'nbt':tag}))
        assert mark_camp_store(data,name)==[list(reserve)]
        for before,after in zip(original['blocks'],data['blocks']):
            p=tuple(map(int,before['pos']))
            if p==reserve:continue
            assert original['palette'][int(before['state'])]==palette[int(after['state'])]
            if list(p) not in barrels:assert before.get('nbt')==after.get('nbt')
        beds=sum(str(palette[int(b['state'])]['Name']).endswith('_bed') and str(palette[int(b['state'])].get('Properties',{}).get('part'))=='head' for b in data['blocks'])
        assert beds==len(residents),(name,beds,len(residents))
        target=RES/f'structures/{name}.nbt';save(target,data)
        write(RES/f'tags/worldgen/biome/has_structure/{name}.json',{'replace':False,'values':['minecraft:'+b for b in biomes]})
        write(RES/f'worldgen/structure/{name}.json',{'type':'changed_synergy:research_lab','biomes':'#changed_synergy:has_structure/'+name,'step':'surface_structures','terrain_adaptation':'none','spawn_overrides':{'monster':{'bounding_box':'full','spawns':[{'type':'changed_synergy:latex_jerboa','weight':4,'minCount':1,'maxCount':2}]}},'template':'changed_synergy:'+name,'ground_level':level,'max_terrain_slope':2})
        manifest[name]={'source_sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'output_sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'ground_level':level,'residents':residents,'public_reserve':reserve,'original_loot_barrel':barrels[0],'original_loot_table':'minecraft:chests/village/village_cartographer','treasure_added':False}
    write(RES/'worldgen/structure_set/desert_camps.json',{'structures':[{'structure':'changed_synergy:'+s,'weight':1} for s in manifest],'placement':{'type':'minecraft:random_spread','spacing':24,'separation':8,'salt':192611101,'exclusion_zone':{'other_set':'changed_synergy:desert_refuges','chunk_count':4}}})
    write(RES/'tags/worldgen/structure/on_desert_refuge_maps.json',{'replace':False,'values':['changed_synergy:desert_refuge']})
    write(ROOT/'tools/desert_camps_manifest.json',manifest)
    print('DESERT_CAMPS_IMPORT_PASS: two preserved templates, six ordinary residents matching beds, two independent reserves; no additional treasure')
if __name__=='__main__':main()
