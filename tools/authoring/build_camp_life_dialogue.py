"""Compile household dialogue without rewriting the supplied story conversations."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path(__file__).with_name('camp_life_dialogue.json')

def main():
    source=json.loads(SOURCE.read_text(encoding='utf-8'))
    languages={lang:{} for lang in ('en_us','zh_cn','ru_ru')}
    lines={}
    for name,entry in source['lines'].items():
        parts=[]
        for i,part in enumerate(entry['parts']):
            key=f'camp.changed_synergy.{name}.{i}'
            for lang,words in languages.items():words[key]=part['text'][lang]
            parts.append(dict(text=key,emotion=part.get('emotion','calm'),effect=part.get('effect','none'),pause=part.get('pause',120),intensity=part.get('intensity',0)))
        lines[name]=parts
    for name,entry in source['words'].items():
        key=name if name.startswith('dialogue.') else 'camp.changed_synergy.'+name
        for lang,words in languages.items():words[key]=entry[lang]
    target=ROOT/'src/main/resources/data/changed_synergy/camp_dialogue/common.json'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps({'lines':lines},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for lang,entries in languages.items():
        path=ROOT/f'src/main/resources/assets/changed_synergy/lang/{lang}.json'
        words=json.loads(path.read_text(encoding='utf-8'))
        words={k:v for k,v in words.items() if not k.startswith('camp.changed_synergy.')}
        words.update(entries)
        path.write_text(json.dumps(words,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'CAMP_DIALOGUE_COMPILE_PASS: {len(lines)} spoken entries, {len(source["words"])} choices/labels, three languages')

if __name__=='__main__':main()
