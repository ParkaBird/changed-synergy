"""Compile the authored NPC graphs into profiles and three-language translation keys."""
from copy import deepcopy
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources'
SOURCE = Path(__file__).with_name('story_npc_dialogue.json')


def main():
    authored = json.loads(SOURCE.read_text(encoding='utf-8'))['profiles']
    words = {'en_us': {}, 'zh_cn': {}, 'ru_ru': {}}

    def translated(key, text):
        for lang in words:
            words[lang][key] = text[lang]
        return key

    for name, value in authored.items():
        profile = deepcopy(value)
        prefix = 'npc.changed_synergy.' + name
        translated(prefix + '.title', profile.pop('title'))
        if 'name' in profile:
            translated(prefix + '.name', profile.pop('name'))
        for node_id, node in profile['nodes'].items():
            for line_id, line in enumerate(node['lines']):
                for part_id, part in enumerate(line['parts']):
                    part['text'] = translated(f'{prefix}.{node_id}.{line_id}.{part_id}', part['text'])
            for choice in node['choices']:
                choice['text'] = translated(f'{prefix}.{node_id}.choice.{choice["id"]}', choice['text'])
        path = ROOT / 'data/changed_synergy/story_npcs' / (name + '.json')
        path.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    common = {
        'dialogue': ('Conversation', '对话', 'Диалог'),
        'busy': ("They're talking with someone. Give them a moment.", '他正在与别人交谈，请稍等。', 'Сейчас идёт разговор. Подожди немного.'),
        'changed': ('Things have changed. Please check your choice again.', '情况发生了变化，请重新确认选择。', 'Обстоятельства изменились. Проверь свой выбор ещё раз.'),
        'continue_hint': ('Click / Enter: continue   Esc: leave', '点击 / Enter 继续   Esc 离开', 'Щелчок / Enter: далее   Esc: выйти'),
        'choose_hint': ('↑↓ Choose   Enter Select   Esc Leave', '↑↓ 选择   Enter 确认   Esc 离开', '↑↓ Выбор   Enter ОК   Esc Выход'),
        'standing': ('Local trust %s', '本地信任 %s', 'Доверие лагеря %s'),
    }
    for key, pair in common.items():
        translated('npc.changed_synergy.' + key, dict(zip(words, pair)))
    # Retire all old conversation words, including shared old faction-choice keys.
    prefixes = tuple('npc.changed_synergy.' + name + '.' for name in authored) + ('npc.changed_synergy.choice.',)
    for lang, entries in words.items():
        path = ROOT / 'assets/changed_synergy/lang' / (lang + '.json')
        data = json.loads(path.read_text(encoding='utf-8'))
        data = {key: value for key, value in data.items() if not key.startswith(prefixes)
                and key not in {'npc.changed_synergy.spoken', 'npc.changed_synergy.mental'}}
        data.update(entries)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Generated {len(authored)} NPC graphs and English/Chinese/Russian dialogue.')


if __name__ == '__main__':
    main()
