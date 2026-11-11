"""Register desert assets and apply only the reviewed structure changes."""
from pathlib import Path
from copy import deepcopy
from zipfile import ZipFile
import gzip,hashlib,io,json,sys
import nbtlib as nbt

ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def save(path,template):
    buffer=io.BytesIO();template.write(buffer);path.write_bytes(gzip.compress(buffer.getvalue(),mtime=0))
def resources():
    entity='changed_synergy:latex_jerboa'
    for slot in ['body','full_body','head']:
        path=RES/f'data/changed/tags/items/{slot}.json';data=json.loads(path.read_text(encoding='utf-8-sig'))
        for item in ['changed_synergy:desert_nomad_outfit']:
            if item not in data['values']:data['values'].append(item)
        write(path,data)
    for relative in ['data/changed/tags/entity_types/latexes.json','data/changed/tags/entity_types/can_wear_exoskeleton.json','data/changed_synergy/tags/entity_types/light_faction.json']:
        path=RES/relative;data=json.loads(path.read_text(encoding='utf-8-sig'))
        if entity not in data['values']:data['values'].append(entity)
        write(path,data)
    write(RES/'data/changed_synergy/tags/entity_types/light_reputation_desert.json',{'replace':False,'values':[entity]})
    # Keep the former tags as optional datapack inputs to the merged affiliation.
    write(RES/'data/changed_synergy/tags/entity_types/light_reputation_arid.json',{'replace':False,'values':[{'id':'#changed_synergy:light_reputation_'+region,'required':False} for region in ['desert','badlands']]})
    for name in ['native_clothing','social_creatures']:
        path=RES/f'data/changed_synergy/curios/entities/{name}.json';data=json.loads(path.read_text(encoding='utf-8-sig'))
        if entity not in data['entities']:data['entities'].append(entity)
        write(path,data)
    data=json.loads((RES/'data/changed_synergy/accessories/entities/latex_muntjac.json').read_text());data['entities']=[entity];write(RES/'data/changed_synergy/accessories/entities/latex_jerboa.json',data)
    write(RES/'assets/changed_synergy/models/item/latex_jerboa_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
    write(RES/'data/changed_synergy/forge/biome_modifier/jerboa_spawns.json',{'type':'forge:add_spawns','biomes':['minecraft:desert','minecraft:badlands','minecraft:wooded_badlands'],'spawners':[{'type':entity,'weight':4,'minCount':1,'maxCount':2}]})
    write(RES/'data/changed_synergy/loot_tables/entities/latex_jerboa.json',{'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:rabbit_hide', 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}, {'function': 'minecraft:looting_enchant', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}]}]}, {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:wheat_seeds', 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 2}}, {'function': 'minecraft:looting_enchant', 'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}]}]}, {'rolls': 1, 'conditions': [{'condition': 'minecraft:killed_by_player'}, {'condition': 'minecraft:random_chance_with_looting', 'chance': 0.1, 'looting_multiplier': 0.02}], 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:rabbit_foot'}]}, {'rolls': 1, 'entries': [{'type': 'minecraft:loot_table', 'name': 'changed_synergy:entities/common_white_latex'}]}]})
    write(RES/'data/changed_synergy/recipes/form_latex_jerboa.json',{'type':'changed:infuser','form':'changed_synergy:form_latex_jerboa','ingredients':[{'item':i} for i in ['changed:latex_base','changed:white_latex_goo','minecraft:rabbit_foot','minecraft:dead_bush','minecraft:sandstone']]})
    for item,wool,gem in [('desert_nomad_outfit','orange_wool','copper_ingot')]:
        write(RES/f'assets/changed_synergy/models/item/{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'changed_synergy:item/'+item}})
        write(RES/f'data/changed_synergy/recipes/{item}.json',{'type':'minecraft:crafting_shaped','pattern':['SGS','LWL',' L '],'key':{'S':{'item':'minecraft:string'},'G':{'item':'minecraft:'+gem},'L':{'item':'minecraft:leather'},'W':{'item':'minecraft:'+wool}},'result':{'item':'changed_synergy:'+item}})
        write(RES/f'data/changed_synergy/advancements/recipes/{item}.json',{'parent':'minecraft:recipes/root','criteria':{'has_leather':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:leather']}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'changed_synergy:'+item}}},'requirements':[['has_leather','has_the_recipe']],'rewards':{'recipes':['changed_synergy:'+item]}})
    texts={
        'en_us':['Latex Jerboa','Latex Jerboa Spawn Egg','Wayfarer Outfit','Light desert linen with a woven headband, bracers and travel pouches. Jerboas wear the pathfinder cut.','Pathfinder'],
        'zh_cn':['胶液跳鼠','胶液跳鼠刷怪蛋','沙行装','轻便的沙漠亚麻服装，配有编织头带、护腕与随行腰包。跳鼠穿着时呈现领航款。','领路人'],
        'ru_ru':['Латексный тушканчик','Яйцо призыва латексного тушканчика','Наряд пустынного странника','Лёгкая льняная одежда для пустыни с плетёной повязкой, наручами и дорожными сумками. На тушканчиках используется крой проводника.','Проводник']}
    keys=['entity.changed_synergy.latex_jerboa','item.changed_synergy.latex_jerboa_spawn_egg','item.changed_synergy.desert_nomad_outfit','item.changed_synergy.desert_nomad_outfit.desc','npc.changed_synergy.desert_chief.title']
    for lang,values in texts.items():
        path=RES/f'assets/changed_synergy/lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8-sig'));data.update(dict(zip(keys,values)));write(path,data)
    write(RES/'data/changed_synergy/story_npcs/desert_chief.json',{'template':'desert_refuge','species':entity,'trait':'CAUTIOUS','chief':True,'dialogue':False,'equipment':['changed_synergy:desert_nomad_outfit'],'nodes':{}})

def structures(bundle):
    path=RES/'data/changed_synergy/structures/desert_refuge.nbt';t=nbt.load(path);before=deepcopy(t)
    marker='changed_synergy:desert_chief';found=[e for e in t['entities'] if marker in e['nbt'].get('Tags',[])]
    if not found:
        pos=nbt.List[nbt.Double]([29.5,20,19.5]);tag=nbt.Compound({'id':nbt.String('changed_synergy:latex_jerboa'),'Pos':deepcopy(pos),'Rotation':nbt.List[nbt.Float]([90,0]),'PersistenceRequired':nbt.Byte(1),'Tags':nbt.List[nbt.String]([marker]),
            'LocalVariantInfo':nbt.Compound({'eyeStyle':nbt.Int(2),'irisLeft':nbt.Int(0x70B7A5),'irisRight':nbt.Int(0x70B7A5),'sclera':nbt.Int(0xFFFFFF)}),
            'ForgeData':nbt.Compound({'ChangedSynergyLightReputationGroup':nbt.String('arid'),'ChangedSynergyStructureEyesInitialized':nbt.Byte(1),'ChangedSynergyStructureResident':nbt.Compound({'Template':nbt.String('desert_refuge'),'LocalPos':deepcopy(pos),'Size':deepcopy(t['size']),'Yaw':nbt.Float(90),'Outfit':nbt.String('changed_synergy:desert_nomad_outfit'),'Camp':nbt.Byte(1),'Character':nbt.Byte(1)})})})
        t['entities'].append(nbt.Compound({'pos':pos,'blockPos':nbt.List[nbt.Int]([29,20,19]),'nbt':tag}))
    assert t['blocks']==before['blocks'] and t['palette']==before['palette']
    assert len(t['entities'])==len(before['entities'])+(not found)
    save(path,t)
    manifest=json.loads((ROOT/'tools/desert_refuge_manifest.json').read_text());manifest['output_sha256']=hashlib.sha256(path.read_bytes()).hexdigest();manifest['chief']={'position':[29.5,20,19.5],'yaw':90,'dialogue':False};write(ROOT/'tools/desert_refuge_manifest.json',manifest)
    # Inspect the supplied update, then transplant only its pillar states.
    with ZipFile(bundle) as z:raw=z.read('forest_watchtower_birch.nbt')
    incoming=nbt.File.parse(io.BytesIO(gzip.decompress(raw)));path=RES/'data/changed_synergy/structures/forest_watchtower_birch.nbt';old=nbt.load(path);result=deepcopy(old)
    src={tuple(map(int,b['pos'])):b for b in incoming['blocks']};changed=[]
    for b in result['blocks']:
        p=tuple(map(int,b['pos']));a=old['palette'][int(b['state'])];new=incoming['palette'][int(src[p]['state'])]
        if a==new:continue
        assert str(a['Name'])=='minecraft:stripped_birch_log' and str(new['Name'])=='minecraft:birch_log',(p,a,new)
        assert a.get('Properties')==new.get('Properties') and b.get('nbt')==src[p].get('nbt'),p
        if new not in result['palette']:result['palette'].append(deepcopy(new))
        b['state']=nbt.Int(result['palette'].index(new));changed.append(p)
    assert len(changed) in (0,13),changed
    for key in old:
        if key not in ('blocks','palette'):assert old[key]==result[key],key
    assert old['size']==incoming['size'] and old['entities']==incoming['entities']
    manifest_path=ROOT/'tools/birch_watchtower_update_manifest.json'
    if not changed and manifest_path.exists():changed=json.loads(manifest_path.read_text())['changed_positions']
    save(path,result);write(manifest_path,{'changed_positions':changed,'output_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source_bundle_sha256':hashlib.sha256(Path(bundle).read_bytes()).hexdigest()})
    print('STRUCTURES: one dressed fixed jerboa, original desert blocks/entities retained; birch pillar changes:',len(changed))

if __name__=='__main__':resources();structures(sys.argv[1])
