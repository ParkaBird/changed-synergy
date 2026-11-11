"""Apply the approved English refuge script through the existing dialogue compilers."""
from copy import deepcopy
from pathlib import Path
import hashlib,html,json,re,sys
import build_story_npc_dialogue as story_compiler
import build_camp_life_dialogue as camp_compiler

ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
LANGS=('en_us','zh_cn','ru_ru')
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def tr(en,zh,ru):return dict(zip(LANGS,(en,zh,ru)))
def choice(id,next,en,zh,ru,condition='always',effect='none',requires=()):return dict(id=id,next=next,text=tr(en,zh,ru),condition=condition,effect=effect,requires=list(requires))
BACK=choice('back','root','Something else…','聊点别的……','О другом…')
BYE=choice('goodbye','goodbye',"I'll let you get back to your desk.",'不打扰你忙了。','Не буду отвлекать тебя от дел.')
def node(lines,choices=None):return dict(lines=lines,choices=deepcopy(choices if choices is not None else [BACK,BYE]))
MARKERS={
 0:[('Oh—mind the step','哦——小心门边的台阶','Ох — осторожнее со ступенькой','impact','surprised',False)],
 4:[('Pathfinder','领路人','Проводником','wobble','shy',False)],
 6:[('Oasis Pathfinder Outfit','领航装','наряд проводника оазиса','wave','curious',False),('As for being a Jerboa','至于跳鼠','Тушканчики','emphasis','calm',True)],
 8:[('orange tree','橙树','апельсиновое дерево','bounce','happy',False)],
 10:[('shared reserve chest','公共储备箱','общий сундук','emphasis','determined',False)],
 12:[('Subject X-1221','X-1221','X-1221','droop','sad',False),('Our job is','我们要做的','Нам важнее','pulse','determined',True)],
 14:[('568712','568712','568712','wave','curious',False)],
 16:[('never','从不','не нужно','emphasis','determined',False)],
 18:[('If we keep it reversible','如果选可逆','Обратимый вариант','emphasis','calm',True),('If we make it permanent','如果选永久','Постоянный вариант','emphasis','determined',True)],
 19:[('for good','真正属于你','станет твоим','pulse','determined',False),('Leaving the refuge','离开避难所','Уход из убежища','none','calm',True),('You can still choose','你现在仍可以','Ты всё ещё можешь','none','calm',True)],
 20:[('welcome home','欢迎回家','добро пожаловать домой','bounce','happy',False)],
 22:[('welcome to the refuge','欢迎来到避难所','добро пожаловать в убежище','bounce','happy',False)],
 23:[('let your tail balance your weight','让尾巴帮你站稳','пусть хвост помогает держать равновесие','float','dreamy',False)],
 35:[('Oh!','哦！','Ой!','impact','surprised',False)],
 36:[('Do not look down!','千万别往下看！','Только не вниз!','tremble','fearful',False)]}
def sentence_markers(texts,effect,emotion,which='first'):
    phrases=[]
    for lang in LANGS:
        sentences=[m.group() for m in re.finditer(r'[^.!?。！？]+(?:[.!?。！？]+|$)',texts[lang]) if m.group().strip()]
        phrases.append(sentences[-1 if which=='last' else 0].strip())
    return [(*phrases,effect,emotion,False)]

def line(texts,channel='spoken',condition='always',markers=(),emotion=None):
    base_emotion=emotion or ('whisper' if channel=='mental' else 'calm')
    parts=[];cursor=[0,0,0];values=[texts[l] for l in LANGS]
    def part(strings,effect='none',emotion=None,beat=False):
        if any(strings):parts.append(dict(text=dict(zip(LANGS,strings)),effect=effect,emotion=emotion or base_emotion,pause=140 if effect!='none' else 0,speed=1,intensity=(.9 if effect=='impact' else .65) if effect!='none' else .2,beat=beat))
    for en,zh,ru,effect,emotion,beat in markers:
        needles=(en,zh,ru);positions=[v.find(n,start) for v,n,start in zip(values,needles,cursor)]
        assert all(p>=0 for p in positions),(needles,positions)
        part([v[a:b] for v,a,b in zip(values,cursor,positions)])
        part(list(needles),effect,emotion,beat);cursor=[p+len(n) for p,n in zip(positions,needles)]
    part([v[a:] for v,a in zip(values,cursor)])
    assert all(''.join(p['text'][l] for p in parts)==texts[l] for l in LANGS)
    return dict(channel=channel,condition=condition,parts=parts)
def main():
    source=Path(sys.argv[1]);raw=source.read_text(encoding='utf-8-sig');translations=read(HERE/'desert_deployment_translations.json');camp_translations=read(HERE/'desert_camp_translations.json');performance=read(HERE/'desert_dialogue_performance.json')
    quoted=list(re.finditer(r'^\s*- `\[(spoken|mental); ([^\]]+)\]` (.*)$',raw,re.M));assert len(quoted)==39
    followups=list(re.finditer(r'^- \*\*Asking ([^*]+):\*\* "(.*)"$',raw,re.M));assert len(followups)==8
    lines=[];english=[]
    conditions={'Human visitor':'human','Transformed visitor':'transformed','All visitors':'always','Transformed applicant':'transformed_join','Human applicant':'human_join','Admission currently unavailable':'cannot_join','Understood resident':'always','Attuned visitor':'always'}
    for i,m in enumerate(quoted):
        en=html.unescape(re.sub(r'^\*\(Proposal[^\n]*?\)\*\s*','',m[3])).replace('if you crouch for a second','if you hold the jump key for a moment').replace('holding a crouch lets your hind legs','holding the jump key lets your hind legs')
        texts=tr(*performance['quote_overrides'][str(i)]) if str(i) in performance['quote_overrides'] else tr(en,*translations['quotes'][i])
        english.append(en)
        style=performance['quotes'].get(str(i),['calm','none','first'])
        markers=MARKERS.get(i,()) if str(i) not in performance['quote_overrides'] else ()
        if not markers:markers=sentence_markers(texts,style[1],style[0],style[2])
        lines.append(line(texts,m[1],conditions[m[2]],markers,style[0]))
    nodes={k:node([deepcopy(lines[i]) for i in indices]) for k,indices in {'root':range(4),'about':[4,5],'form_habits':[6,7],'home':[8,9],'quarters':[10,11],'records':[12,13],'clipboards':[14],'enrol':[15,16,17],'explain_form':[18],'confirm_permanent':[19],'welcome_human':[20,21],'welcome_latex':[22],'welcome_copy':[23,24],'welcome_permanent':[23,24],'membership':[25],'confirm_leave':[26],'left':[27],'goodbye':[28]}.items()}
    nodes['root']['choices']=[
      choice('about','about','Who are you? Are you the one in charge of this bunker?','你是谁？你负责这个掩体吗？','Кто ты? Ты здесь за главного?'),
      choice('home','home','How do you and the others live down here under the sand?','你们在沙子底下怎么生活？','Как вы живёте здесь, под песками?'),
      choice('records','records','I saw the sealed lab rooms and the old clipboards downstairs. What happened in this facility?','我看见楼下的实验室和旧剪切板了。这里发生过什么？','Я видел лабораторные комнаты и старые записи внизу. Что здесь произошло?',requires=['about','home']),
      choice('enrol','enrol','Do you have room in the refuge for another resident?','避难所还能再住一个人吗？','В убежище есть место для ещё одного жильца?','stranger'),
      choice('membership','membership',"Let's talk about my place here…",'聊聊我住在这里的事……','Поговорим о моей жизни здесь…','member'),deepcopy(BYE)]
    del nodes['form_habits']
    nodes['home']['choices'].insert(0,choice('quarters','quarters','How do you organize the sleeping quarters and supplies downstairs?','楼下的床位和物资怎么安排？','Как устроены спальни и запасы внизу?'))
    nodes['records']['choices'].insert(0,choice('clipboards','clipboards',"Did anyone here write the old notes?",'那些旧便笺是这里的人写的吗？','Кто-нибудь из вас написал старые записки?'))
    join_human=choice('join_human','welcome_human',"I'll join as a human. I'd be happy to help with the garden and the refuge.",'我保持人类加入，愿意照料菜园和避难所。','Я вступлю человеком. Буду рад помогать с огородом и убежищем.','human_join','join')
    nodes['enrol']['choices']=[choice('join_latex','welcome_latex',"I'd be glad to share the chores and live here.",'我愿意分担家务，住在这里。','Я рад разделить заботы и жить здесь.','transformed_join','join'),deepcopy(join_human),choice('form','explain_form','What would changing into your form mean for me?','变成你的形态对我意味着什么？','Что для меня будет означать твоя форма?','human'),deepcopy(BACK)]
    nodes['explain_form']['choices']=[choice('reversible','welcome_copy',"Let's try the reversible transformation and join.",'我想体验可逆兽化，并加入这里。','Хочу попробовать обратимую трансформацию и вступить.','can_reversible','reversible'),choice('permanent_form','confirm_permanent',"I'm ready to make it permanent and join.",'我准备好永久兽化并加入了。','Я готов к постоянной трансформации и вступлению.','can_permanent'),deepcopy(join_human),deepcopy(BACK)]
    nodes['confirm_permanent']['choices']=[choice('permanent_confirm','welcome_permanent','Yes, I understand and choose the permanent change.','是的，我理解并选择永久改变。','Да, я понимаю и выбираю постоянную перемену.','can_permanent','permanent'),choice('reconsider','explain_form','Let me think it over again.','让我再想想。','Дай мне ещё подумать.')]
    nodes['membership']['choices']=[choice('leave','confirm_leave','I need to leave the refuge household and move on.','我得离开这里，继续上路了。','Мне нужно покинуть этот дом и идти дальше.'),deepcopy(BACK)]
    nodes['confirm_leave']['choices']=[choice('confirm_leave','left',"Yes, I'm ready to move on.",'是的，我准备继续上路。','Да, я готов идти дальше.','always','leave'),deepcopy(BACK)]
    nodes['goodbye']['choices']=[choice('close','goodbye','Safe travels.','一路平安。','Счастливого пути.','always','close')]
    nodes['return']=deepcopy(nodes['root'])
    for role,start,follows in [('dorm',29,[0,1]),('study',31,[2,3]),('garden',33,[4,5]),('lab',35,[6]),('corridor',37,[7])]:
        root='resident_'+role;resident_choices=[]
        for i in follows:
            en=followups[i][2];target=root+'_'+str(i)
            texts=tr(*performance['followup_overrides'][str(i)]) if str(i) in performance['followup_overrides'] else tr(en,*translations['followups'][i])
            emotion,effect=performance['followups'][i]
            nodes[target]=node([line(texts,markers=sentence_markers(texts,effect,emotion),emotion=emotion)],[choice('back',root,'Something else…','聊点别的……','О другом…'),choice('close',root,'See you around.','回头见。','До встречи.','always','close')])
            labels=[('What about the beds?','这些床怎么用？','Что насчёт кроватей?'),('What about the room chest?','房间里的箱子呢？','А сундук в комнате?'),('Is that double chest shared?','那个长箱是公用的吗？','Этот двойной сундук общий?'),('Who wrote the 568712 clipboard?','写着 568712 的剪切板是谁留的？','Кто написал про 568712?'),('How do the underground crops grow?','地下的作物怎么长起来的？','Как растут подземные грядки?'),('What about the oranges on your desk?','桌上的橙子呢？','А апельсины на твоём столе?'),('What were the flasks and stasis pods for?','烧瓶和休眠舱原来是做什么的？','Для чего были колбы и капсулы?'),('Why stand by the stairs?','为什么待在楼梯旁边？','Почему ты стоишь у лестницы?')]
            resident_choices.append(choice('topic_'+str(i),target,*labels[i]))
        resident_choices.append(choice('close',root,'See you around.','回头见。','До встречи.','always','close'))
        nodes[root]=node([deepcopy(lines[start]),deepcopy(lines[start+1])],resident_choices)
    story=read(HERE/'story_npc_dialogue.json');old_profiles=deepcopy(story['profiles'])
    story['profiles']['desert_chief']=dict(template='desert_refuge',species='changed_synergy:latex_jerboa',trait='CAUTIOUS',chief=True,equipment=['changed_synergy:desert_nomad_outfit'],title=tr('Pathfinder','领路人','Проводник'),nodes=nodes)
    write(HERE/'story_npc_dialogue.json',story);story_compiler.main()
    camp=read(HERE/'camp_life_dialogue.json');camp_entries=[]
    for m in re.finditer(r'^- `(camp\.[^`]+)`(?: \*\([^\n]*?\)\*)?: (.*)$',raw,re.M):
        key=m[1].split(' / ')[0];en=m[2].replace('any spare rations you can spare','any rations you can spare').replace('lower west study','middle-floor garden').replace('lower west reserve','middle-floor garden reserve');name=key.removeprefix('camp.changed_synergy.');name=re.sub(r'\.0$','',name)
        if name=='home.pathfinder':camp['words'][name]=tr(en,*camp_translations[name]);continue
        assert name in camp_translations,name
        texts=tr(en,*camp_translations[name]);assert len({t.count('%s') for t in texts.values()})==1,name
        context=name.split('.')[-1]
        emotion,effect=('calm','emphasis')
        if context in ('danger','watch','watch_wait'):emotion,effect='determined','pulse'
        elif context=='food_low':emotion,effect='calm','droop'
        elif context in ('food_plenty','handled','meal','thanks','watch_thanks','playful_thanks','playful_greeting','outfit'):emotion,effect='happy','bounce'
        elif context in ('safe','no_requests','away'):emotion,effect='calm','float'
        elif context.startswith('cautious_'):emotion,effect='shy','wobble'
        elif context in ('gesture','polite_greeting','declined'):emotion,effect='whisper','whisper'
        elif context in ('greeting','requests','donate','playful_request'):emotion,effect='curious','wave'
        camp['lines'][name]=dict(parts=line(texts,markers=sentence_markers(texts,effect,emotion),emotion=emotion)['parts']);camp_entries.append((name,en))
    camp['words']['dialogue.changed_synergy.context.desert_camp_help_thanks']=tr("Thanks for tending the garden plots. It's good to have you around.",'谢谢你照料菜园。有你在真好。','Спасибо за помощь с грядками. Хорошо, что ты рядом.')
    write(HERE/'camp_life_dialogue.json',camp);camp_compiler.main()
    roles=re.findall(r'^- \*\*(Welcoming|Caring|Organizing|Warning):\*\* (.*)$',raw,re.M);assert len(roles)==4
    role_translations=[('进来避避太阳、乘凉喘口气吧，旅人。这里都是朋友。','Заходи с солнцепёка, переведи дух в прохладе. Здесь ты среди друзей.'),('沙漠里的太阳很耗体力，去中层公共休息室喝点凉水、吃些新鲜橙子吧。','Пустынное солнце выматывает. Спустись в общую комнату за прохладной водой и свежими апельсинами.'),('腿脚歇过来后，能帮忙扫扫上层楼梯的沙、看看菜园水渠就好了。','Когда отдохнёшь, помоги смести песок с верхней лестницы и проверить канавки в огороде.'),('走过竖井边时留在黄黑护栏内侧，北边密封的实验设备就别碰了。','У шахты держись за жёлто-чёрными ограждениями и не трогай запечатанное оборудование в северных лабораториях.')]
    extra={'item.changed_synergy.desert_refuge_map':tr('Desert Refuge Explorer Map','沙漠避难所探险地图','Карта Пустынного убежища')}
    for (context,en),translated in zip(roles,role_translations):extra['dialogue.changed_synergy.context.chief.desert_chief.'+{'Welcoming':'welcome','Caring':'care','Organizing':'organize','Warning':'warning'}[context]]=tr(en,*translated)
    for lang in LANGS:
        path=ROOT/f'src/main/resources/assets/changed_synergy/lang/{lang}.json';words=read(path);words.update({k:v[lang] for k,v in extra.items()});write(path,words)
    assert all(story['profiles'][name]==value for name,value in old_profiles.items() if name!='desert_chief')
    write(HERE/'desert_dialogue_import_manifest.json',{'source':source.name,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'removed_quote_indices':[6,7],'revised_quote_indices':list(map(int,performance['quote_overrides'])),'revised_followup_indices':list(map(int,performance['followup_overrides'])),'quote_utterances':len(quoted),'followup_utterances':len(followups),'camp_utterances':len(camp_entries),'nodes':len(nodes),'mechanical_text_corrections':['crouch -> hold jump key in two passages','remove repeated spare in food-low line'],'english_utterance_hashes':[hashlib.sha256(t.encode()).hexdigest() for t in english],'scope':'new desert dialogue and existing community mechanics; the three established Chiefs are unchanged'})
    print('DESERT_DIALOGUE_IMPORT_PASS:',len(nodes),'nodes,',len(quoted)+len(followups),'authored utterances; English, Chinese and Russian')
if __name__=='__main__':main()
