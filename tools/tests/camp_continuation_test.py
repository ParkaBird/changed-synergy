"""Check every supplied continuation entry and the requested Chinese terminology."""
from pathlib import Path
import hashlib,json,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/authoring'))
from import_balanced_camp_rewrite import target,text_value

manifest=json.loads((ROOT/'tools/authoring/balanced_camp_continuation_manifest.json').read_text(encoding='utf-8'))
camp=json.loads((ROOT/'tools/authoring/camp_life_dialogue.json').read_text(encoding='utf-8'))
languages={l:json.loads((ROOT/f'src/main/resources/assets/changed_synergy/lang/{l}.json').read_text(encoding='utf-8')) for l in ('en_us','zh_cn','ru_ru')}
assert len(manifest['entries'])==126
for key,entry in manifest['entries'].items():
    if key.startswith('camp.'):
        record,field=target({},camp,key);values=text_value(record,field)
    else:values={l:d[key] for l,d in languages.items()}
    for lang,value in values.items():assert hashlib.sha256(value.encode()).hexdigest()==entry['after'][lang],(key,lang)
assert not any('共同体' in v for v in languages['zh_cn'].values())
for lang,words in languages.items():
    forbidden={'zh_cn':'木桶','en_us':'barrel','ru_ru':'боч'}[lang]
    assert not any(forbidden in v.lower() for k,v in words.items() if 'cache_' in k or '.cache_defense.' in k),lang
print('CAMP_CONTINUATION_PASS: all 126 supplied entries, matching three-language text and 社区 terminology')
