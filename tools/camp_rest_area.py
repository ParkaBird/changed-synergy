"""Replace the hideout's six three-carpet sleeping rolls with orange beds."""
import nbtlib as nbt

HIDEOUT_BEDS = [
    ((3,4,11),(4,4,11),(5,4,11),'west'),
    ((4,4,9),(5,4,9),(6,4,9),'west'),
    ((9,4,4),(9,4,5),(9,4,6),'north'),
    ((10,4,11),(9,4,11),(8,4,11),'east'),
    ((11,4,3),(11,4,4),(11,4,5),'north'),
    ((11,4,10),(11,4,9),(11,4,8),'south'),
]

def replace_hideout_bedrolls(template,name):
    if name!='hyena_hideout' or 'blocks' not in template:return 0
    blocks={tuple(map(int,b['pos'])):b for b in template['blocks']}
    def state_id(state):
        if state not in template['palette']:template['palette'].append(state)
        return nbt.Int(template['palette'].index(state))
    changed=0
    for head,foot,extra,facing in HIDEOUT_BEDS:
        if str(template['palette'][int(blocks[head]['state'])]['Name'])=='minecraft:orange_bed':continue
        assert str(template['palette'][int(blocks[head]['state'])]['Name'])=='minecraft:white_carpet'
        for p in (foot,extra):assert str(template['palette'][int(blocks[p]['state'])]['Name'])=='minecraft:orange_carpet'
        for p,part in ((head,'head'),(foot,'foot')):
            blocks[p]['state']=state_id(nbt.Compound({'Name':nbt.String('minecraft:orange_bed'),'Properties':nbt.Compound({'facing':nbt.String(facing),'part':nbt.String(part),'occupied':nbt.String('false')})}))
            blocks[p]['nbt']=nbt.Compound({'id':nbt.String('minecraft:bed')})
        blocks[extra]['state']=state_id(nbt.Compound({'Name':nbt.String('minecraft:air')}));blocks[extra].pop('nbt',None);changed+=1
    return changed
