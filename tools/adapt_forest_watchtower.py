"""Import the contributed forest watchtower, retaining its authored loot and decorations."""
from copy import deepcopy
from pathlib import Path
import hashlib
import json
import sys
import nbtlib as nbt
from adapt_forest_treehouse import save
from adapt_research_structures import clean_occupant, validate_dependencies

ROOT=Path(__file__).resolve().parents[1]

def main():
    source=Path(sys.argv[1])
    template=nbt.load(source)
    for block in template['blocks']:
        if 'nbt' in block:
            block['nbt'].pop('ForgeCaps',None)
            block['nbt'].pop('ForgeData',None)
    for entity in template.get('entities',[]):
        tag=entity['nbt']
        clean_occupant(tag)
        tag['Pos']=deepcopy(entity['pos'])
        if str(tag.get('id')) in ('minecraft:painting','minecraft:item_frame'):
            for axis,coordinate in zip('XYZ',entity['blockPos']):tag['Tile'+axis]=nbt.Int(coordinate)
    covers=[cover for cover in template.get('latexCovers',[]) if any(str(v)=='true'
            for k,v in template['latexCoverPalette'][int(cover['state'])].get('Properties',{}).items()
            if k in ('up','down','north','south','east','west'))]
    template['latexCovers']=nbt.List[nbt.Compound](covers)
    validate_dependencies(template)
    assert not any(str(e['nbt'].get('id','')).startswith(('changed:','changed_synergy:')) for e in template.get('entities',[]))
    output=ROOT/'src/main/resources/data/changed_synergy/structures/forest_watchtower.nbt'
    save(template,output)
    report={'source':source.name,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
            'output_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'size':list(map(int,template['size'])),
            'ground_level':0,'residents':0,'decorations':len(template.get('entities',[]))}
    (ROOT/'tools/forest_watchtower_manifest.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
