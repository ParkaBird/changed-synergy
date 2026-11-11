"""Import the supplied Parts VI–VIII prose, ignoring surrounding editorial chatter."""
from pathlib import Path
import hashlib
import json
import re
import sys
from import_balanced_camp_rewrite import ROOT, LANGUAGES, target, text_value, parts, sha


def cache_storage_words(key, lang, text):
    if 'cache_' not in key and '.cache_defense.' not in key:return text
    replacements = {
        'zh_cn': [('木桶','箱子')],
        'en_us': [('barrels','chests'),('barrel','chest')],
        'ru_ru': [('в этой бочке','в этом сундуке'),('эту бочку','этот сундук'),
                  ('всю бочку','весь сундук'),('Бочка разбита','Сундук разбит'),
                  ('складские бочки','складские сундуки'),('наши бочки','наши сундуки'),
                  ('разбил бочки','разбил сундуки'),('открывать бочки','открывать сундуки'),
                  ('бочках','сундуках'),('бочек','сундуков'),('бочки','сундука'),('бочку','сундук')],
    }
    for old,new in replacements.get(lang,[]):text=text.replace(old,new)
    return text


def parse(path):
    source=path.read_text(encoding='utf-8-sig')
    heads=list(re.finditer(r'^\* \*\*`([A-Za-z0-9_.]+)`\*\*',source,re.M))
    result={}
    for i,head in enumerate(heads):
        block=source[head.end():heads[i+1].start() if i+1<len(heads) else len(source)]
        values=re.findall(r'\*\*(EN|ZH|RU):\*\*[ \t]*(.*?)(?=[ \t]+/[ \t]*\*\*(?:EN|ZH|RU):\*\*|[\r\n]|$)',block)
        assert [lang for lang,value in values]==['EN','ZH','RU'],(head[1],values)
        key=head[1]
        if key.startswith(('canopy_chief.','hyena_chief.','observatory_chief.')):key='dialogue.changed_synergy.context.chief.'+key
        elif key.startswith('meal.'):key='dialogue.changed_synergy.context.player_outpost.'+key
        elif key.startswith(('aquatic.','dark.','light.')):key='dialogue.changed_synergy.'+key
        assert key not in result,key
        result[key]=dict(zip(LANGUAGES,(value.rstrip() for lang,value in values)))
    return result


def main(path):
    entries=parse(path)
    camp_path=ROOT/'tools/authoring/camp_life_dialogue.json';camp=json.loads(camp_path.read_text(encoding='utf-8'))
    language_paths={lang:ROOT/f'src/main/resources/assets/changed_synergy/lang/{lang}.json' for lang in LANGUAGES}
    languages={lang:json.loads(p.read_text(encoding='utf-8')) for lang,p in language_paths.items()}
    receipt={}
    for key,values in entries.items():
        supplied=dict(values);values['zh_cn']=values['zh_cn'].replace('共同体','社区')
        values={lang:cache_storage_words(key,lang,value) for lang,value in values.items()}
        if key.startswith('camp.'):
            record,field=target({},camp,key);old=text_value(record,field)
            assert all(values[l].count('%s')==old[l].count('%s') for l in LANGUAGES),key
            record[field]=parts(key,values,False) if field=='parts' else values
        else:
            assert key.startswith('dialogue.changed_synergy.') and all(key in languages[l] for l in LANGUAGES),key
            old={l:languages[l][key] for l in LANGUAGES}
            assert all(values[l].count('%s')==old[l].count('%s') for l in LANGUAGES),key
            for lang in LANGUAGES:languages[lang][key]=values[lang]
        receipt[key]={'supplied':{l:sha(v) for l,v in supplied.items()},'after':{l:sha(v) for l,v in values.items()}}
    for lang,p in language_paths.items():
        for key,value in languages[lang].items():
            if lang=='zh_cn':languages[lang][key]=value.replace('共同体','社区')
        p.write_text(json.dumps(languages[lang],ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    camp_path.write_text(json.dumps(camp,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    manifest={'source':path.name,'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'entries':receipt,'terminology':'Use 社区 in Chinese camp descriptions.'}
    (ROOT/'tools/authoring/balanced_camp_continuation_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'CAMP_CONTINUATION_IMPORT_PASS: {len(entries)} entries, three locales, preserved placeholders and private-storage rules')


if __name__=='__main__':main(Path(sys.argv[1]))
