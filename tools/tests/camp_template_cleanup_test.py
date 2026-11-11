"""The two requested draft chests stay removed through future imports."""
from pathlib import Path
import hashlib,json,sys
import nbtlib
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from adapt_forest_treehouse import remove_campfire_draft_chest
from adapt_hyena_structures import remove_well_draft_chest
for name,pos,remove,manifest in [('forest_treehouse',(26,1,0),remove_campfire_draft_chest,'forest_treehouse_manifest.json'),('hyena_hideout',(1,4,0),remove_well_draft_chest,'hyena_structures_manifest.json')]:
    path=ROOT/'src/main/resources/data/changed_synergy/structures'/(name+'.nbt')
    template=nbtlib.load(path)
    block=next(b for b in template['blocks'] if tuple(map(int,b['pos']))==pos)
    assert str(template['palette'][int(block['state'])]['Name'])=='minecraft:air' and 'nbt' not in block
    assert remove(template)==0
    data=json.loads((ROOT/'tools'/manifest).read_text(encoding='utf-8'))
    assert (data if name=='forest_treehouse' else data[name])['output_sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
print('CAMP_DRAFT_CHESTS_PASS: two exact positions, absent inventories, idempotent import cleanup and manifest hashes')
