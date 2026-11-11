"""Count complete, correctly paired beds against authored residents in every small camp."""
from pathlib import Path
import nbtlib as nbt

root=Path(__file__).resolve().parents[2]/'src/main/resources/data/changed_synergy/structures'
directions={'north':(0,-1),'south':(0,1),'west':(-1,0),'east':(1,0)}
for name in ['hyena_camp_tropical','hyena_camp_open','hyena_camp_snowy','oasis','muddy_campsite']:
    data=nbt.load(root/(name+'.nbt'));blocks={tuple(map(int,b['pos'])):data['palette'][int(b['state'])] for b in data['blocks']}
    beds=0
    for pos,state in blocks.items():
        if not str(state['Name']).endswith('_bed'):continue
        props=state['Properties'];dx,dz=directions[str(props['facing'])];head=str(props['part'])=='head'
        mate=(pos[0]+dx*(-1 if head else 1),pos[1],pos[2]+dz*(-1 if head else 1));other=blocks.get(mate)
        assert other is not None and other['Name']==state['Name'] and other['Properties']['facing']==props['facing'] and str(other['Properties']['part'])!=str(props['part']),(name,pos,'unpaired bed')
        beds+=head
    residents=[e for e in data['entities'] if e['nbt'].get('ForgeData',{}).get('ChangedSynergyStructureResident',{}).get('Camp')]
    assert len(residents)==beds,(name,len(residents),beds)
    print(f'SMALL_CAMP_BEDS_PASS: {name}: {len(residents)} residents, {beds} complete beds')
