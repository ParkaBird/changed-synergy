"""Apply user requested dialogue updates:
1. Remove '橙色/orange/оранжевой' from bed descriptions across all 3 NPCs and 3 languages.
2. Update ordinary food requests for members to natural phrasing across 4 personality tones.
3. Update supply.food to '食物（种类不限）' / 'food (any kind)' / 'еды (любой)'.
4. Naturalize chiefs' request templates in en_us.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# 1. Update story_npc_dialogue.json
story_path = ROOT / 'tools/authoring/story_npc_dialogue.json'
story_data = json.loads(story_path.read_text(encoding='utf-8'))

# Hyena chief
hc = story_data['profiles']['hyena_chief']['nodes']
for choice in hc['root']['choices']:
    if choice['id'] == 'hyena_chief_past':
        choice['text']['zh_cn'] = '我看到了你床铺旁的日记。你花了多久才找到同伴们的？'
        choice['text']['en_us'] = 'I saw the diary near your bed. How long did it take you to find your people?'
        choice['text']['ru_ru'] = 'Я видел дневник возле твоей кровати. Сколько времени ушло на то, чтобы найти своих?'
for choice in hc['return']['choices']:
    if choice['id'] == 'hyena_chief_past':
        choice['text']['zh_cn'] = '我看到了你床铺旁的日记。你花了多久才找到同伴们的？'
        choice['text']['en_us'] = 'I saw the diary near your bed. How long did it take you to find your people?'
        choice['text']['ru_ru'] = 'Я видел дневник возле твоей кровати. Сколько времени ушло на то, чтобы найти своих?'

# Observatory chief
oc = story_data['profiles']['observatory_chief']['nodes']
part = oc['about']['lines'][0]['parts'][1]
part['text']['zh_cn'] = '说起来，我楼下的卧室里明明有一张完好舒适的床铺，但十个早晨里有九个，我最后还是睡在讲台旁的'
part['text']['en_us'] = 'My bedroom downstairs has a perfectly good bed, mind you, though nine mornings out of ten I still end up sleeping on the '
part['text']['ru_ru'] = 'Между прочим, в моей спальне внизу стоит отличная кровать, но в девяти случаях из десяти к утру я все равно оказываюсь на '

# Lab researcher
lr = story_data['profiles']['lab_researcher']['nodes']
part0 = lr['returned']['lines'][0]['parts'][1]
part0['text']['zh_cn'] = '我的书架和床铺的。'
part0['text']['en_us'] = 'my bookshelf and my bed.'
part0['text']['ru_ru'] = 'своему книжному шкафу и кровати.'

part1 = lr['returned']['lines'][1]['parts'][2]
part1['text']['zh_cn'] = '，而我的床铺上有'
part1['text']['en_us'] = ' and my bed has '
part1['text']['ru_ru'] = ', тогда как на моей кровати '

story_path.write_text(json.dumps(story_data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Updated story_npc_dialogue.json')

# 2. Update camp_life_dialogue.json
camp_path = ROOT / 'tools/authoring/camp_life_dialogue.json'
camp_data = json.loads(camp_path.read_text(encoding='utf-8'))

camp_data['lines']['resident.request']['parts'][0]['text'] = {
    'zh_cn': '能帮我带 %s 份食物吗，任何种类都可以。凑齐之后直接交给我就行。',
    'en_us': "Could you bring me %s portions of food, any kind will do? Just hand them directly to me once you've gathered the full amount.",
    'ru_ru': 'Сможешь принести мне %s ед. еды — подойдет любая? Когда соберешь все полностью, просто передай лично мне.'
}

camp_data['lines']['resident.playful_request']['parts'][0]['text'] = {
    'zh_cn': '能帮我带 %s 份食物吗，任何种类都行！记得直接交给我——我可不想去公共物资箱里翻找自己的零嘴！',
    'en_us': "Could you bring me %s portions of food, any kind will do? Make sure you hand them straight to me—I don't want to go digging through the public chests for my own snacks!",
    'ru_ru': 'Принесешь мне %s ед. еды — какой угодно? Только чур передать лично в руки — не хочу потом искать свои вкусности в общих сундуках!'
}

camp_data['lines']['resident.cautious_request']['parts'][0]['text'] = {
    'zh_cn': '能帮我带 %s 份食物吗，任何种类都可以。你方便的时候直接交给我就好，路上千万小心。',
    'en_us': "Could you bring me %s portions of food, any kind will do? Just hand them directly to me whenever it's convenient, and please be careful out there.",
    'ru_ru': 'Сможешь принести мне %s ед. еды — подойдет любая? Передай лично мне, когда будет удобно, и, пожалуйста, береги себя в дороге.'
}

camp_data['lines']['resident.polite_request']['parts'][0]['text'] = {
    'zh_cn': '能麻烦你帮我带 %s 份食物吗，任何种类都可以。凑齐之后直接交给我就行，一点都不着急。',
    'en_us': "Would you mind bringing me %s portions of food, any kind will do? Just hand them to me directly once you have them all—there's no rush at all.",
    'ru_ru': 'Не сочти за труд принести мне %s ед. еды — подойдет любая. Как соберешь все, просто передай мне лично — спешить совершенно некуда.'
}

# Update supply.food
camp_data['words']['supply.food'] = {
    'zh_cn': '食物（种类不限）',
    'en_us': 'food (any kind)',
    'ru_ru': 'еды (любой)'
}

# Update chiefs' request in en_us to sound natural
camp_data['lines']['weaver.request']['parts'][0]['text']['en_us'] = "We could use %s %s for the household. Bring the full amount whenever it's convenient, and we'll put them into our shared supplies."
camp_data['lines']['matriarch.request']['parts'][0]['text']['en_us'] = "When you have the time, bring us %s %s for the hideout to share. No rush—just come back once you've gathered the full amount."
camp_data['lines']['archivist.request']['parts'][0]['text']['en_us'] = "We need %s %s for the camp. Bring them by whenever you have the full amount ready, and they will go into the shared supplies."

camp_path.write_text(json.dumps(camp_data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Updated camp_life_dialogue.json')
