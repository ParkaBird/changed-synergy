"""Residents share their authored supplies; players receive private-storage reactions."""
import nbtlib as nbt

CAMPS={'oasis','muddy_campsite','hyena_camp_tropical','hyena_camp_open','hyena_camp_snowy'}

def mark_camp_store(template,name):
    if name not in CAMPS:return []
    stores=[]
    for block in template['blocks']:
        state=template['palette'][int(block['state'])]
        if str(state['Name']) not in ('minecraft:barrel','minecraft:chest'):continue
        tag=block['nbt'];flags=tag.setdefault('ForgeData',nbt.Compound());flags['ChangedSynergyPublicStorage']=nbt.Byte(1);flags['ChangedSynergyPrivate']=nbt.Byte(1)
        tag['CustomName']=nbt.String('{"translate":"container.changed_synergy.camp_supplies"}')
        stores.append(list(map(int,block['pos'])))
    assert len(stores)==1,(name,stores)
    for entity in template.get('entities',[]):
        resident=entity['nbt'].get('ForgeData',{}).get('ChangedSynergyStructureResident')
        if resident is not None:
            resident['ReserveOnly']=nbt.Byte(1)
            resident['Outfit']=nbt.String('changed_synergy:hyena_trailcloth_outfit' if name.startswith('hyena_camp_') else 'changed_synergy:desert_nomad_outfit')
            resident['OutfitChance']=nbt.Float(.25)
    return stores
