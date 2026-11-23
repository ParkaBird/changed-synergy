"""Parse Burrow dialogue markdown into story_npc_dialogue.json profiles."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MD_PATH = Path(r"D:\Downloads\Synergy_ The Burrow — Core Dialogue Tree & Narrative Fragments (EN _ ZH _ RU).md")
STORY_PATH = ROOT / "tools/authoring/story_npc_dialogue.json"


def clean_md(text: str) -> str:
    text = text.strip()
    text = re.sub(r"\\([!_*?])", r"\1", text)
    return text


def part(
    text_en: str,
    text_zh: str,
    text_ru: str,
    emotion: str = "calm",
    effect: str = "none",
    beat: bool = False,
    intensity: float = 0,
    pause: int = 0,
):
    p = {
        "text": {"en_us": text_en, "zh_cn": text_zh, "ru_ru": text_ru},
        "emotion": emotion,
        "effect": effect,
        "speed": 1,
        "intensity": intensity,
        "pause": pause,
    }
    if beat:
        p["beat"] = True
    return p


def spoken_block(paras: list[tuple[str, str, str]], emotion: str = "calm", condition: str = "always"):
    """Raw spoken line; hyena/canopy timing is applied later by polish_profile."""
    parts = [part(en, zh, ru, emotion=emotion, beat=i > 0) for i, (en, zh, ru) in enumerate(paras)]
    return {"channel": "spoken", "condition": condition, "parts": parts}


def mental_block(text_en: str, text_zh: str, text_ru: str, condition: str = "always", emotion: str = "curious"):
    en = text_en if text_en.startswith("(") else f"({text_en})"
    zh = text_zh if text_zh.startswith("（") else f"（{text_zh}）"
    ru = text_ru if text_ru.startswith("(") else f"({text_ru})"
    return {
        "channel": "mental",
        "condition": condition,
        "parts": [part(en, zh, ru, emotion=emotion if emotion != "whisper" else "curious")],
    }


def _style(part: dict, effect: str = "none", intensity: float = 0, pause: int = 0):
    part["effect"] = effect
    part["intensity"] = intensity
    part["pause"] = pause


def polish_profile(nodes: dict, voice: str) -> dict:
    """Restyle Burrow graphs like hyena (fox) / canopy (manul): sparse peaks, calm rests, mental waves."""
    for node_id, node in nodes.items():
        for line in node["lines"]:
            parts = line["parts"]
            n = len(parts)
            channel = line["channel"]
            for i, p in enumerate(parts):
                if i > 0:
                    p["beat"] = True
                emo = p["emotion"]
                _style(p)

                if channel == "mental":
                    if emo == "curious":
                        _style(p, "wave", 0.85 if voice == "fox" else 0.9)
                    elif emo == "happy":
                        _style(p, "pulse" if voice == "fox" else "bounce", 0.8)
                    elif emo == "shy":
                        _style(p, "whisper", 0.8)
                    elif emo == "determined":
                        _style(p, "whisper" if voice == "fox" else "tremble", 0.85)
                    elif emo == "surprised":
                        _style(p, "impact", 0.9)
                    continue

                if voice == "fox":
                    if node_id in ("root", "return") and i == 0 and emo == "happy":
                        _style(p, "impact", 0.8, 100)
                    elif node_id in ("root", "return") and i == n - 1:
                        _style(p, "emphasis", 0.8, 160)
                    elif node_id in ("root", "return") and emo == "happy":
                        _style(p, "bounce", 0.85, 140)
                    elif node_id == "past" and i == 0:
                        _style(p, "impact", 0.9, 220)
                    elif node_id == "past" and i == 1 and n >= 3:
                        _style(p, "droop", 0.7, 150)
                    elif node_id == "past" and i == n - 1:
                        _style(p, "pulse", 0.85, 170)
                    elif node_id in ("about", "home", "manul") and i == n - 1:
                        _style(p, "pulse" if emo == "happy" else "emphasis", 0.8, 150)
                    elif node_id.startswith(("enrol", "welcome", "explain", "confirm")) and i == n - 1:
                        _style(p, "pulse", 0.85, 140)
                    elif node_id.startswith("clue") and i == 0:
                        _style(p, "impact", 0.75, 140)
                    elif node_id.startswith("clue") and i == n - 1:
                        _style(p, "pulse", 0.8, 130)
                    elif node_id.startswith("resident") and emo == "shy":
                        _style(p, "wobble", 0.8, 140)
                    elif emo == "happy" and i == 0:
                        _style(p, "bounce", 0.85, 130)
                    elif emo == "happy" and i == n - 1:
                        _style(p, "pulse", 0.8, 120)
                    elif emo == "determined":
                        _style(p, "emphasis", 0.85, 160)
                    elif emo == "shy":
                        _style(p, "wobble", 0.8, 140)
                    elif n > 1 and i == n - 1 and emo == "calm":
                        _style(p, "emphasis", 0.7, 140)
                else:
                    if node_id in ("root", "return") and i == 1 and n > 2:
                        _style(p, "float", 0.7, 120)
                    elif node_id in ("root", "return") and i == n - 1:
                        _style(p, "emphasis", 0.75, 160)
                    elif node_id == "past" and i == 1 and n >= 2:
                        _style(p, "droop", 0.65, 180)
                    elif node_id == "past" and i == n - 1:
                        _style(p, "emphasis", 0.85, 200)
                    elif node_id in ("about", "home", "gray_fox") and i == n - 1:
                        _style(p, "emphasis", 0.75, 150)
                    elif node_id.startswith(("enrol", "welcome", "explain", "confirm")) and i == n - 1:
                        _style(p, "emphasis", 0.8, 150)
                    elif node_id.startswith("clue") and i == n - 1:
                        _style(p, "emphasis", 0.7, 140)
                    elif emo == "determined":
                        _style(p, "emphasis", 0.85, 160)
                    elif n > 1 and i == n - 1:
                        _style(p, "emphasis", 0.6, 130)
    return nodes


def choice(cid: str, en: str, zh: str, ru: str, nxt: str, cond: str = "always", effect: str = "none", requires=None):
    c = {
        "id": cid,
        "text": {"en_us": en, "zh_cn": zh, "ru_ru": ru},
        "next": nxt,
        "condition": cond,
        "effect": effect,
    }
    if requires:
        c["requires"] = requires
    return c


def slug_id(prefix: str, label: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_")
    return f"{prefix}_{s}"[:64] or f"{prefix}_choice"


# ---------------------------------------------------------------------------
# Gray Fox chief (Part I) + residents (Part III)
# ---------------------------------------------------------------------------

GRAY_FOX_ROOT_RETURN_CHOICES = [
    choice("gray_fox_about", "Who are you? Do you run this place?", "你是谁？这里是你管着的吗？", "Кто ты? Ты здесь главная?", "about"),
    choice("gray_fox_home", "Why is everything built underground? Who else lives down here?", "为什么把屋子全建在地下？下面还住着谁？", "Почему тут все построено под землей? Кто еще здесь живет?", "home"),
    choice(
        "gray_fox_past",
        "How did you and the Manul end up starting a place like this?",
        "你和那只兔狲当初怎么会一起建起这么个地方？",
        "Как вы с манулом вообще решили обустроить такое место?",
        "past",
        requires=["about", "home"],
    ),
    choice(
        "gray_fox_clue_terminal",
        "I read the old file on the computer in the bottom bedroom.",
        "我看了最底层卧室电脑里的那份旧文件。",
        "Я прочитал старый файл на компьютере в нижней спальне.",
        "clue_terminal",
        cond="clue:burrow_terminal",
    ),
    choice(
        "gray_fox_clue_rules",
        "I saw your note at the bottom of the kitchen house rules.",
        "我看到你在厨房规矩最底下补的那行字了。",
        "Я видел твою приписку внизу правил на кухне.",
        "clue_rules",
        cond="clue:burrow_rules",
    ),
    choice(
        "gray_fox_enrol",
        "Is there room for me to stay here?",
        "我能在这儿安顿下来吗？",
        "Найдется ли здесь место и для меня?",
        "enrol",
        cond="stranger",
    ),
    choice(
        "gray_fox_membership",
        "About my spot here under the hill…",
        "关于我住在这儿的事……",
        "Насчет моего укрытия здесь…",
        "membership",
        cond="member",
    ),
    choice(
        "gray_fox_goodbye",
        "I'll let you get back to it.",
        "那我不打扰你了。",
        "Не буду тебя задерживать.",
        "goodbye",
    ),
]

BACK_GOODBYE_FOX = [
    choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
    choice("goodbye", "I'll let you get back to it.", "那我不打扰你了。", "Не буду тебя задерживать.", "goodbye"),
]

def gray_fox_nodes():
    nodes = {}

    nodes["root"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Oh! Hey, come on in, shut the trap… well, there's no door up there, just step down out of the wind. I didn't expect a human to wander all the way out to our snowbank.",
                        "噢！嗨，快进来，把门……好吧，上面其实没门，你先下台阶避避风吧。我真没想到会有人类一路走到我们这座雪坡来。",
                        "О! Привет, заходи, прикрой… ну, двери наверху нет, просто спускайся с ветра. Не ожидала, что человек забредет аж к нашему сугробу.",
                    ),
                    (
                        "Take a minute to warm up. Nobody down here bites, though half of them might duck into another room if you make eye contact…",
                        "先缓口气暖和暖和。下面没人会咬人，不过你要是和他们对上视线，有一半人大概会躲进别的房间……",
                        "Постой минуту, согрейся. Здесь внизу никто не кусается, хотя половина жильцов может улизнуть в другую комнату, если поймаешь их взгляд…",
                    ),
                ],
                emotion="happy",
                condition="human",
            ),
            mental_block(
                "A real human! And they actually came inside instead of walking past the fence! Wait… stay calm, don't talk too fast, you'll scare them off!",
                "活生生的人类！而且真的进来了，没直接从围栏边走过去！等等……冷静点，别说太快，免得把人吓跑了！",
                "Настоящий человек! И правда зашел внутрь, а не прошел мимо ограды! Так, спокойно, не тараторь, а то спугнешь!",
                condition="human",
            ),
            spoken_block(
                [
                    (
                        "Hey there! Come on down off the stairs before you freeze your paws off!",
                        "嗨！快下楼梯来吧，别把爪子冻僵了！",
                        "Привет! Спускайся с лестницы, пока лапы не отморозил!",
                    ),
                    (
                        "You picked a quiet spot to drop in. Most folks here aren't big on visitors, so I'm usually the one upstairs saying hello.",
                        "你挑了个挺安静的地方串门。这里大多数人都不太擅长应付客人，所以一般都是我在楼上这边打招呼。",
                        "Ты заглянул в довольно тихое место. Большинство здешних не очень любят гостей, так что наверху обычно встречаю я.",
                    ),
                ],
                emotion="happy",
                condition="transformed",
            ),
            mental_block(
                "Yes! Someone new to talk to! Do not immediately tell them my entire life story. Start normal…",
                "太好了！终于有新面孔可以聊天了！别一上来就把自己半辈子全倒出来，得正常一点……",
                "Ура! Новый собеседник! Только не вываливай сразу всю свою биографию. Начни нормально…",
                condition="transformed",
                emotion="happy",
            ),
        ],
        "choices": GRAY_FOX_ROOT_RETURN_CHOICES,
    }

    nodes["return"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Hey, you're back! Shake the snow off before you head downstairs, or the kitchen boss will give us both that flat stare again.",
                        "嗨，你回来啦！下楼前记得把雪抖干净，不然厨房里那位又要冲咱俩摆出那副死鱼眼了。",
                        "Привет, с возвращением! Отряхни снег перед тем, как спускаться, а то главный по кухне опять уставится на нас обоих этим своим недовольным взглядом.",
                    ),
                ],
                emotion="happy",
            ),
            mental_block(
                "Wait, did I leave snow on the steps earlier? Probably. I'll blame the wind.",
                "等等，我刚才是不是也在台阶上带了雪？八成是。一会儿就赖风吹进来的。",
                "Погоди, я сама раньше не натаскала снега на ступеньки? Наверняка. Свалю на ветер.",
            ),
        ],
        "choices": GRAY_FOX_ROOT_RETURN_CHOICES,
    }

    nodes["about"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "I handle the outside end of things: checking the fence, trading when travelers pass by, and greeting anyone brave enough to climb down our stairs.",
                        "我负责管外头这摊事，比如看看围栏、顺路和旅人换点东西，再招呼敢顺着楼梯走下来的客人。",
                        "Я занимаюсь всем, что снаружи: проверяю ограду, меняюсь с прохожими путниками и встречаю тех, кто решился спуститься к нам по лестнице.",
                    ),
                    (
                        "Back when the transformation first went around, I took it early. My old human body wasn't going to last much longer anyway. Having legs that don't hurt and lungs that actually work in cold air? I still haven't gotten tired of it!",
                        "当初转化刚传开的时候，我很早就选了转化。反正我从前那具人类身体也撑不了多久了。现在腿不疼了，大冷天喘气也顺畅，这种感觉我至今都没腻！",
                        "Когда превращение только начало распространяться, я согласилась одной из первых. Мое старое человеческое тело все равно долго бы не протянуло. Ноги, которые не болят, и легкие, спокойно дышащие на морозе, — я до сих пор не могу к этому привыкнуть!",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Seriously, running through knee-deep snow without coughing once is still the best thing ever!",
                "说真的，踩着齐膝深的雪跑一圈还不咳一声，至今都是最爽的事！",
                "Серьезно, пробежаться по снегу по колено и ни разу не закашляться — до сих пор лучшее чувство на свете!",
                emotion="happy",
            ),
        ],
        "choices": [
            choice(
                "gray_fox_manul",
                "What about the Manul downstairs in the kitchen?",
                "那楼下厨房里那只兔狲呢？",
                "А что насчет манула внизу на кухне?",
                "manul",
            ),
            *BACK_GOODBYE_FOX,
        ],
    }

    nodes["manul"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Oh, him? He runs everything inside the den: the kitchen, the stores, making sure everyone eats without having to make small talk first.",
                        "噢，他呀？地穴里头的事全归他管，包括厨房、物资，还有确保大家不用被迫寒暄也能吃上饭。",
                        "О, он? На нем все дела внутри норы: кухня, припасы, и чтобы все были сыты без необходимости вести светские беседы.",
                    ),
                    (
                        "Don't take it personally if he looks like he's scowling at you. That's just his face. If you're polite and don't leave a mess on his bar counter, he'll warm up faster than he'd ever admit!",
                        "要是他看着像在瞪你，别往心里去，他天生就长那样。只要你客客气气的，别把他的吧台弄乱，他其实比自己嘴上承认的好相处得多！",
                        "Не принимай на свой счет, если покажется, что он хмурится. У него просто такая морда. Будешь вежливым и не оставишь бардак на стойке, он оттает быстрее, чем сам себе признается!",
                    ),
                ],
                emotion="happy",
            ),
            mental_block(
                "His face when I sneak up and hug him with snow on my nose is priceless. He acts mad for five seconds and then hands me a bowl of soup anyway.",
                "每次我鼻尖沾着雪偷偷抱住他，他那副表情简直绝了。装生气五秒钟，回头还不是端碗汤给我。",
                "Его морда, когда я подкрадываюсь и обнимаю его со снегом на носу, просто бесценна. Пять секунд делает вид, что злится, а потом все равно сует мне миску супа.",
                emotion="happy",
            ),
        ],
        "choices": BACK_GOODBYE_FOX,
    }

    nodes["home"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "You've probably noticed we're a pretty homebody crowd. After changing, a lot of the folks who ended up here just stopped wanting to deal with crowds, arguments, or strangers on the road.",
                        "你大概也看出来了，我们这儿都是些爱宅着的人。转化以后，最后聚到这儿的不少人都不太想应付人群、争执，或者路上的陌生人。",
                        "Ты, наверное, уже заметил, что народ тут собрался домашний. После превращения многие из тех, кто здесь оказался, просто расхотели сталкиваться с толпами, спорами и незнакомцами на дороге.",
                    ),
                    (
                        "So we built everything they'd need under the snow. There's a little tree right here on the upper floor, a kitchen and indoor farm a floor down, a workshop, dorm rooms, and even an indoor fishing pond with a painted sunset wall, so nobody has to go outside if they don't feel like it.",
                        "所以我们就干脆把日常用得上的全建在雪底下了。楼上那有棵小树，往下一层是厨房和室内农场，还有工坊、宿舍，甚至连室内鱼池都挖好了，墙上还贴了面日落壁画，不想出门的人根本不用出去。",
                        "Так что мы обустроили под снегом все нужное. Прямо тут в чердачной комнате растет деревце, этажом ниже кухня и крытая ферма, мастерская, спальни и даже подземный пруд с нарисованным закатом на стене, чтобы никому не приходилось выходить наружу без желания.",
                    ),
                    (
                        "Oh, and don't let the warm-air heaters next to the ice and snow throw you off. The heaters keep the burrow livable, but most of us have heavy winter coats and overheat fast indoors, so we keep ice and snow around to cool off and balance the temperature.",
                        "对了，看到暖风机旁边堆着冰雪也别觉得奇怪。暖风用来保证地穴里住着舒服，但我们大多数人毛都厚得很，在室内待久了容易闷热，所以得靠冰雪降降温，把温度平衡一下。",
                        "Да, и не удивляйся, что у нас одновременно работают обогреватели и лежат лед со снегом. Теплый воздух нужен, чтобы в норе было уютно, но у большинства из нас слишком густой мех, и в тепле быстро становится жарко, поэтому лед и снег помогают остыть и держать равновесие.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "They act like grumpy teenagers locked in their rooms half the time. The second a skeleton rattles near the fence, though, everyone runs straight behind me!",
                "平时一个个装得跟锁在房间里的叛逆小孩似的，结果围栏外头只要有骷髅咔哒一响，全躲我身后去了！",
                "Половину времени ведут себя как ворчливые подростки, запершиеся в своих комнатах. Но стоит скелету загреметь у ограды, все тут же прячутся мне за спину!",
                emotion="happy",
            ),
        ],
        "choices": BACK_GOODBYE_FOX,
    }

    nodes["past"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "When I decided to undergo transformation, I told him not to follow me. He was completely healthy, and he didn't even like talking to people, let alone giving up his own body.",
                        "当初我决定接受转化的时候，跟他说过别跟着我。他那时候身体好好的，连和人打交道都嫌烦，更别提换掉自己的身体了。",
                        "Когда я решила пройти превращение, я сказала ему не идти за мной. Он был совершенно здоров и вообще не любил лишней суеты, не говоря уже о том, чтобы менять тело.",
                    ),
                    (
                        "He didn't listen. He showed up at the clinic two days later with his mind already made up, just so we wouldn't get separated when the world moved on.",
                        "结果他根本没听。两天后自己跑去诊所，主意早就打定了，就为了以后世界怎么变，我们俩都不会分开。",
                        "Он не послушал. Через два дня сам пришел в клинику, уже все решив, лишь бы мы не расстались, пока мир меняется.",
                    ),
                    (
                        "After that, we started running into other quiet types who were sick of noise and conflict. We dug out this hill together, wired up the warm-air heating, hauled in ice and snow to keep the temperature balanced, and made a place where nobody bothers you unless you want them to.",
                        "后来我们在外头碰上不少同样喜静、烦透了吵闹和冲突的人。大家一起挖空了这座小山坡，装上暖风，再搬进冰雪调节温度，弄出这么个你不乐意就没人打扰的地方。",
                        "Потом нам начали попадаться такие же тихие ребята, уставшие от шума и стычек. Мы вместе вырыли это жилье в холме, провели теплый воздух, натаскали льда со снегом для баланса и сделали место, где тебя никто не трогает без дела.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Still can't believe he picked a Manul! Roundest, grumpiest cat on earth, and somehow it fits him completely.",
                "到现在我还是觉得不可思议，他居然成了只兔狲！世上最圆乎、脸最臭的猫，偏偏和他本人莫名合拍。",
                "До сих пор не верится, что он стал именно манулом! Самый круглый и угрюмый кот на свете, и ему это почему-то идеально идет.",
                emotion="curious",
            ),
        ],
        "choices": BACK_GOODBYE_FOX,
    }

    nodes["clue_terminal"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Oh, you wandered all the way down to our bedroom on the bottom floor? He left that file on the computer years ago, probably forgetting it's still sitting on the desktop.",
                        "噢，你一路逛到最底下我们俩的卧室啦？那个文件是他好多年前留在电脑里的，大概早忘了还在桌面上。",
                        "О, ты спустился аж в нашу спальню на самом нижнем уровне? Он оставил этот файл на компьютере много лет назад; наверное, уже и забыл, что тот до сих пор лежит на рабочем столе.",
                    ),
                    (
                        "Reading it back now feels weird. Back then I was scared of every hospital checkup, and he was pretending he wasn't terrified of losing me. Look at us now: a shared double bed, warm-air heaters fighting a whole pile of ice and snow because his Manul fur is the thickest in the entire Burrow, and a house full of roommates who complain if I hum too loud.",
                        "现在回头看感觉挺奇妙的。那会儿我每次去医院复查都提心吊胆，他还强装镇定，假装自己不怕失去我。看看现在吧：一张双人床，暖风机对着一整堆冰雪较劲，谁让他的兔狲毛是全地穴最厚的，还有一屋子嫌我哼歌声音太大的室友。",
                        "Странно сейчас это перечитывать. Тогда я боялась каждого осмотра в больнице, а он делал вид, что совсем не боится меня потерять. А теперь посмотри на нас: общая двуспальная кровать, обогреватели, которые спорят с целой горой льда и снега, потому что мех манула у него самый густой во всей Норе, и полный дом соседей, которые ворчат, если я слишком громко напеваю.",
                    ),
                ],
                emotion="curious",
            ),
        ],
        "choices": BACK_GOODBYE_FOX,
    }

    nodes["clue_rules"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Haha, caught me! He gets so serious about people hiding in the cardboard room whenever someone knocks.",
                        "哈哈，被你发现了！只要一有人敲门就往纸箱室里躲，他对此可认真了。",
                        "Ха, поймал меня! Он так серьезно относится к тому, что все прячутся в комнате с коробками, стоит кому-то постучать.",
                    ),
                    (
                        "I mean, yes, it's slightly embarrassing when a guest opens a locker and finds a wyvern pretending to be a coat. Still, if someone's social battery is dead for the day, I don't mind covering the door for them.",
                        "好吧，客人一拉开柜子发现里头有只Wyvern假装自己是件大衣，确实有点尴尬。不过要是谁今天实在不想见人，我替他们应个门也没什么大不了的。",
                        "Ну да, немного неловко, когда гость открывает шкафчик, а там виверна притворяется пальто. Тем не менее, если у кого-то сегодня совсем нет сил общаться, мне не трудно прикрыть дверь.",
                    ),
                ],
                emotion="happy",
            ),
        ],
        "choices": BACK_GOODBYE_FOX,
    }

    nodes["enrol"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Of course. We've got spare cots downstairs, or a pile of warm cushions right up here if you like the breeze. We're just a quiet bunch under this mound, but as long as you don't mind neighbors who keep to themselves, you're home!",
                        "当然好啊！楼下有空着的木床，你要是喜欢上头的空气，这儿也有整堆软垫。咱们这座山坡下就是个安安静静的小家，只要你不介意邻居们平时各忙各的、不大爱凑热闹，随时算你一份！",
                        "Да без проблем! Внизу есть свободные койки, а заходишь остаться наверху — устроим тебе угол на мягких подушках. Мы тут народ тихий, держимся скромно под своим холмом, так что если не ищешь шумных компаний — устраивайся, ты дома!",
                    ),
                ],
                emotion="happy",
                condition="transformed_join",
            ),
            spoken_block(
                [
                    (
                        "You're welcome to settle in just as you are. Nobody here is going to ask you to change your skin just to earn a pillow and a plate of hot food.",
                        "就按你现在的样子住下就行，咱们这儿可没规矩非得让人换一身皮毛才给热饭和床位。",
                        "Оставайся с нами в своем привычном виде. Никто не станет требовать, чтобы ты менял тело ради теплого угла и порции горячего пирога.",
                    ),
                    (
                        "Though if you ever feel like trying out life as a gray fox, I'd be glad to share my coat for a little while or for good. It's entirely up to you.",
                        "不过，要是有朝一日你想体会一下当灰狐的感觉，随时告诉我，我可以分你一点毛绒形态，不管试个鲜或者一直当下去都行，全看你自己的心意。",
                        "Но если однажды захочешь узнать, каково это — быть серой лисой, я с радостью поделилась бы с тобой этим обликом: хочешь — ненадолго, хочешь — навсегда. Решать только тебе.",
                    ),
                ],
                emotion="calm",
                condition="human_join",
            ),
            spoken_block(
                [
                    (
                        "Ah, I wish I could say yes right now, but folks downstairs are feeling a bit overwhelmed today. Let's give everyone a little room to breathe first, and we can figure out your spot once things feel cozy again.",
                        "哎，这会儿恐怕不太凑巧。楼下的伙伴们今天情绪有些紧绷，咱们先给大家伙留点清静的余地，等地穴里气氛暖和下来了，我再帮你想办法找间屋子。",
                        "Эх, не сейчас. Ребята внизу пока сам не свои, так что давай дадим им немного прийти в себя. Как только всё уляжется и станет снова уютно, мы обязательно вернемся к этому разговору.",
                    ),
                ],
                emotion="calm",
                condition="cannot_join",
            ),
        ],
        "choices": [
            choice(
                "join_latex",
                "I'll settle in as I am.",
                "我就保持现在的模样住下来。",
                "Я останусь у вас в своей прежней форме.",
                "welcome_latex",
                cond="transformed_join",
                effect="join",
            ),
            choice(
                "join_human",
                "I'd like to stay human and live here with you.",
                "我想以人类的身份和你们住在一起。",
                "Я бы хотел жить с вами человеком.",
                "welcome_human",
                cond="human_join",
                effect="join",
            ),
            choice(
                "explain_form",
                "How does it feel to step into the gray fox form?",
                "变成灰狐究竟是种什么感觉？",
                "Каково это — ощутить себя серой лисой?",
                "explain_form",
                cond="can_reversible",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["explain_form"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Don't worry, you won't lose yourself. Your mind and memories stay entirely your own, and sharing my soft gray coat won't tire me out at all.",
                        "别担心，你绝不会丢失自我——你的心智和记忆依然完完全全属于你自己，而且分你一份灰狐皮毛对我一点损伤也没有。",
                        "Не бойся, ты не потеряешь себя: твои мысли и воспоминания останутся нетронутыми, а мне эта магия мягкого латекса совсем не повредит.",
                    ),
                    (
                        "Being a gray fox means nimble paws, light steps, and ears sharp enough to catch every footstep on the icy stairs. Plus, a huge bushy tail makes the absolute best blanket when cold drafts sweep in!",
                        "当了灰狐，手脚会变得格外利落，步子轻盈，耳朵能把楼阶上的风吹草动听得清清楚楚。况且寒风钻进来的时候，一条蓬松的大尾巴当毯子盖可舒服了！",
                        "В шубке серой лисы становишься невероятно ловким, шаги легкие, а чуткие ушки ловят каждый скрип на морозной лестнице. К тому же пышным хвостом так славно укрываться, когда из щелей тянет стужей!",
                    ),
                    (
                        "If we leave it as a temporary change, you can slip back into your human skin whenever you miss it. If you make it permanent, this fur stays with you for good. But staying human is completely fine too!",
                        "咱们可以先试个暂时的，哪天想念原来的身体了随时能变回去；要是选了永久，这身暖和的皮毛就会一直伴着你。当然啦，继续当人类也挺好的！",
                        "Если сделаем превращение временным, сможешь в любой момент вернуться в привычное тело. Сделаешь бессрочным, мягкий мех останется с тобой навек. Но и остаться человеком — прекрасный выбор!",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice(
                "reversible",
                "Let's try the gray fox form temporarily and make this home.",
                "我想先体验一下可逆的灰狐形态，顺便住下来。",
                "Пожалуй, я поношу образ серой лисы временно и останусь с вами.",
                "welcome_copy",
                cond="can_reversible",
                effect="reversible",
            ),
            choice(
                "permanent_menu",
                "I want to become a gray fox for good and live here.",
                "我想永久变成灰狐，在这儿扎根。",
                "Я решил стать серой лисой навсегда и быть одним из вас.",
                "confirm_permanent",
                cond="can_permanent",
            ),
            choice(
                "join_human",
                "I think I'll just stay human and settle down.",
                "我想还是保持人类的样子住下来吧。",
                "Пожалуй, просто поселюсь здесь человеком.",
                "welcome_human",
                cond="human_join",
                effect="join",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["confirm_permanent"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Take a deep breath and make sure you're really ready. Making it permanent means this warm gray fur becomes your true self, even if you wander far beyond our snowbank, you won't change back, and simple magic won't undo it.",
                        "深呼吸，先想清楚哦。选了永久，这身灰狐皮毛就会彻底融成你真正的样子。哪怕你以后走出这片雪坡远行，也没办法变回去了，寻常的逆转手艺可管不了用。",
                        "Сделай вдох и подумай хорошенько. Сделать этот шаг навсегда — значит вплести эту лисью шубку в свою суть: даже если ты уйдешь далеко от нашего снежного дома, прежнее тело уже не вернется, и простые средства не помнут этого обратно.",
                    ),
                    (
                        "You can still change your mind right now to try it temporarily or keep your human skin. You have a warm place here no matter what. Are you truly certain?",
                        "你现在要是改主意试个暂时的、或者留着原本的人类样子都完全来得及，反正无论如何地穴都有你的一席之地。你真的下定决心了吗？",
                        "Ты все еще можешь передумать, поносить облик временно или остаться человеком — уголок у нас для тебя будет в любом случае. Ты точно этого хочешь?",
                    ),
                ],
                emotion="determined",
            ),
        ],
        "choices": [
            choice(
                "confirm",
                "Yes, I've made up my mind. Give me the gray fox coat for good.",
                "是的，我全想好了，把这身灰狐形态永久赋予我吧。",
                "Да, я всё решил. Пусть образ серой лисы останется со мной навсегда.",
                "welcome_permanent",
                cond="can_permanent",
                effect="permanent",
            ),
            choice(
                "rethink",
                "Let me weigh my options once more.",
                "让我再细细斟酌一下。",
                "Дай мне ещё минутку подумать.",
                "explain_form",
            ),
        ],
    }

    welcome_back = BACK_GOODBYE_FOX

    nodes["welcome_human"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Oh, wonderful! Pick any open cot down in the dorms, or drag a cushion up here if you prefer sleeping near the indoor tree. And hey, the shared food trunk is right by the kitchen arch, so just leave people's personal room trunks be!",
                        "太好啦！楼下宿舍的床铺你随便挑，或者在楼上树边占个软垫也行。对了，公共补给箱就在厨房拱门旁边，只要别去碰别人房间里的私人箱子，想吃什么自便！",
                        "Чудесно! Занимай любую кровать внизу или укладывайся на подушках прямо здесь, под деревом. Да, общий сундук стоит у арочного входа на кухню: бери что нужно, только в личные сундучки по комнатам не заглядывай!",
                    ),
                ],
                emotion="happy",
            ),
            mental_block(
                "Finally! Someone who won't run away when I ask how their day went!",
                "终于来了一个我问\"今天过得怎么样\"不会转身就跑的人！",
                "Наконец-то кто-то, кто не сбежит, если спросить, как прошел день!",
                emotion="happy",
            ),
        ],
        "choices": welcome_back,
    }

    nodes["welcome_latex"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Welcome to our little sanctuary! Make yourself comfortable, snag a snack off the bar whenever hunger hits, and don't take it to heart if some of the quiet folks down the hall take a few days to warm up to you.",
                        "欢迎加入我们的小天地！当自己家一样就成，饿了随时去吧台顺点好吃的。要是走廊那头有几个内向的伙伴好几天才敢跟你回话，可千万别介意呀。",
                        "Добро пожаловать в нашу Нору! Чувствуй себя как дома, перекусывай у стойки, когда захочешь, и не обижайся на местных тихонь, если они начнут здороваться только спустя пару дней.",
                    ),
                ],
                emotion="happy",
            ),
        ],
        "choices": welcome_back,
    }

    nodes["welcome_copy"] = nodes["welcome_permanent"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Look at that! Give your ears a little flick and take a step, feels feather-light, doesn't it?",
                        "瞧瞧！抖抖耳朵、迈开脚试试，是不是感觉身子轻盈得很？",
                        "Вот и готово! Встряхни ушками, сделай шаг, правда же, тело будто невесомое?",
                    ),
                    (
                        "You're completely yourself inside, just with a cozy new coat to keep the snow off. Welcome home, new neighbor!",
                        "你骨子里依然是你自己，只是现在多了一身抵御风雪的暖和冬毛。欢迎回家，新邻居！",
                        "В душе ты остался собой, но теперь у тебя есть замечательная теплая шубка для любых метелей. Добро пожаловать домой, сосед!",
                    ),
                ],
                emotion="happy",
            ),
            mental_block(
                "Ooh, look at those gray ears! Wait till he sees there's two of us now, he's going to groan so loud!",
                "噢，瞧瞧这对灰耳朵！等他看见家里现在有两只灰狐了，肯定要抱怨好久！",
                "Ой, только гляньте на эти серые ушки! Вот он застонет, когда увидит, что нас тут теперь двое!",
                emotion="happy",
            ),
        ],
        "choices": welcome_back,
    }

    nodes["membership"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "You're one of us now. Help yourself to the common food bin and any open pillow, but do leave the closed bedroom trunks alone.",
                        "你现在可是咱们地穴的大伙伴了。厨房旁边的公共食箱和空着的床垫你随便用，别碰大伙儿卧房里的私人箱子就行。",
                        "Ты теперь полноценный житель Норы. Общий сундук у кухни и все свободные подушки — в твоем распоряжении, только не трогай личные вещи в спальнях.",
                    ),
                    (
                        "If the road ever calls you away, just let me know. There won't be any hard feelings, I promise.",
                        "要是哪天远方召唤你，你想收拾行囊出发，跟我说一声就好。这里没人会埋怨你的，我保证。",
                        "Если вдруг захочешь двинуться дальше — просто шепни мне. Никто и слова против не скажет.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice(
                "leave_menu",
                "It's time for me to pack up and move on.",
                "我该收拾行囊出发了。",
                "Мне пора собираться и отправляться в путь.",
                "confirm_leave",
                cond="member",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["confirm_leave"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Are you sure you're ready to brave the winds? Once you leave the household fold, the common pantries won't be yours to use, though our doors are always open if you ever want to visit.",
                        "确定要踏入外头的风雪了吗？一旦告别大家，公共粮仓就不能随意取用了，不过你随时可以回来做客喝杯热茶。",
                        "Точно готов снова выйти на мороз? Когда ты попрощаешься с Норой, доступ к общим сундукам закроется, но двери нашего дома всегда открыты для гостей.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice(
                "confirm_leave",
                "Yes, the road calls me.",
                "是的，前方的路还在等我。",
                "Да, меня зовет дорога.",
                "left",
                cond="member",
                effect="leave",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["left"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Farewell then! Keep your scarf tight against the snow, alright? Whenever your travels bring you back near our hill, drop by for a hot meal.",
                        "那就后会有期！外头雪大，裹紧围巾，路上万事小心。哪天路过我们这座山坡，随时进来吃口热乎的派！",
                        "В добрый путь! Застегнись поплотнее, снег снаружи лютует. Будешь проезжать мимо нашего холма — заглядывай греться!",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
            choice("goodbye", "I'll let you get back to it.", "那我不打扰你了。", "Не буду тебя задерживать.", "goodbye"),
        ],
    }

    nodes["goodbye"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Watch the ice on the top steps! Come back up if you need anything.",
                        "留神最上面台阶结冰！有事随时上来找我。",
                        "Осторожнее со льдом на верхних ступеньках! Поднимайся, если что понадобится.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice("close", "See you.", "回头见。", "Увидимся.", "root", effect="close"),
        ],
    }

    # --- Residents (Part III) ---
    residents = [
        (
            "resident_upstairs",
            "resident_upstairs_0",
            "If you hate visitors, why stay upstairs right by the entrance?",
            "要是这么讨厌客人，干嘛还待在门口这屋？",
            "Если так не любишь гостей, зачем сидеть наверху у входа?",
            [
                (
                    "Oh. Another visitor. Great. Look, if you're here to talk, she's standing literally two feet away from me. I'm just sitting on my pillows.",
                    "噢。又来客人了。真棒。听着，你要是想聊天，她就站在离我两步远的地方。我只是在这儿坐我的睡垫。",
                    "О. Еще один гость. Замечательно. Слушай, если хочешь поболтать, она стоит буквально в двух шагах от меня. Я тут просто сижу на своих подушках.",
                ),
            ],
            (
                "Why do I always sit in the front room? Oh right. Warm air from the heater, packed ice right by the pillow, just the right balance, and the canned peaches are within arm's reach.",
                "我干嘛老待在门口这屋？噢对，暖风吹过来，睡垫旁边就是浮冰，温度刚刚好，而且伸手就够得着桃子罐头。",
                "И зачем я вообще сижу в проходной комнате? Ах да. Теплый воздух от обогревателя, плотный лед прямо у подушки — идеальный баланс — и до консервированных персиков рукой подать.",
            ),
            [
                (
                    "Because I claimed the spot under the indoor tree first, that's why. Moving all my books and soup cans downstairs sounds like way too much work.",
                    "因为室内树底下这块地盘是我先占的，就这么简单。把我的书和汤罐头全搬到楼下听着就太麻烦了。",
                    "Потому что я первая заняла место под этим деревом, вот почему. Тащить вниз все мои книги и банки с супом — слишком много возни.",
                ),
                (
                    "Besides, if you don't stare at me while I read, I don't actually care if you use one of the other pillows.",
                    "再说了，只要我看书的时候别盯着我，你用旁边别的睡垫我其实也无所谓。",
                    "К тому же, если не будешь пялиться на меня, пока я читаю, мне все равно — занимай любую другую подушку.",
                ),
            ],
        ),
        (
            "resident_farm",
            "resident_farm_0",
            "Do you mind taking care of the farm all by yourself?",
            "农场的事全靠自己打理，你不介意吗？",
            "Тебе не в тягость одному ухаживать за фермой?",
            [
                (
                    "Hey. Watch the fence gate—if the sheep get out into the wheat again, I'm not chasing them down the stairs.",
                    "嗨。当心栅栏门——绵羊要是再跑进麦地里，我可不去楼梯下追它们。",
                    "Эй. Осторожнее с калиткой — если овцы опять залезут в пшеницу, я за ними вниз по лестнице бегать не стану.",
                ),
                (
                    "You need something, or are you just looking at the cows?",
                    "你有事找我，还是光在这儿看牛？",
                    "Тебе что-то нужно, или просто на коров смотришь?",
                ),
            ],
            (
                "Cows are great. They chew grass, they mind their own business, and they never ask what I'm doing this weekend.",
                "牛多好啊。只顾着嚼草、管好自己的事，从来不问我周末打算干嘛。",
                "Коровы — отличные ребята. Жуют траву, не лезут не в свое дело и никогда не спрашивают про планы на выходные.",
            ),
            [
                (
                    "Mind it? I volunteered so I'd have a reason to skip kitchen chatter.",
                    "介意？我是自己主动揽下来的，好有个借口躲开厨房里的闲聊。",
                    "Не против ли я? Да я сам вызвался, чтобы был повод пропускать болтовню на кухне.",
                ),
                (
                    "The berry bushes and crops keep the kitchen stocked, and the Manul leaves fresh pie by the door when I'm done. Fair trade.",
                    "浆果丛和庄稼管够厨房用，等我干完活，兔狲会把刚烤好的派放在门口。挺划算的。",
                    "Ягодные кусты и грядки кормят кухню, а манул оставляет свежий пирог у двери, когда я заканчиваю. Честный обмен.",
                ),
            ],
        ),
        (
            "resident_pond",
            "resident_pond_0",
            "What's with the giant banner mural on the back wall?",
            "后墙上那幅巨型旗帜壁画是怎么回事？",
            "Что за огромная флаговая роспись на задней стене?",
            [
                (
                    "Don't splash the water. The salmon get jumpy when strangers stomp around the edge.",
                    "别把水踩溅起来。陌生人在池边乱跺脚，鲑鱼会受惊的。",
                    "Не плещи водой. Лосось пугается, когда незнакомцы топают у края.",
                ),
                (
                    'Wait—you\'re not here to tell me I should "go fish in a real river," are you? Because she already tried that last Tuesday.',
                    "等等——你不是来劝我\"去外头真河里钓鱼\"的吧？上周二她已经试过这一套了。",
                    "Погоди — ты ведь не пришел говорить, что мне стоит «порыбачить на настоящей реке»? Потому что она уже пробовала в прошлый вторник.",
                ),
            ],
            (
                "No wind, no sleet, no random wanderers asking if the fish are biting. This basement pond is peak civilization.",
                "没风、没冻雨，也没路人凑过来问\"鱼咬钩了吗\"。这地下室鱼池简直是文明巅峰。",
                "Ни ветра, ни мокрого снега, ни случайных прохожих с вопросом «ну как, клюет?». Этот подвальный пруд — вершина цивилизации.",
            ),
            [
                (
                    "That's our mountain view. Thirty banners, took two evenings to line them up right.",
                    "那是我们的山景。整整三十面旗帜，花了两个晚上才对齐。",
                    "Это наш вид на горы. Тридцать флагов, два вечера ушло, чтобы ровно их развесить.",
                ),
                (
                    "Look at it: dark peaks, little red moon, even a bat. Zero snowstorms, zero small talk. Tell me a real mountain beats that.",
                    "你看：黑山峰、小红月亮，甚至还有只蝙蝠。没暴风雪，也没客套话。你说哪座真山比得上这个？",
                    "Сам посмотри: темные вершины, маленькая красная луна, даже летучая мышь есть. Никаких метелей, никаких пустых разговоров. Скажи еще, что настоящие горы лучше.",
                ),
            ],
        ),
        (
            "resident_workshop",
            "resident_workshop_0",
            "Why is the workshop floor lined with packed ice?",
            "工坊地上为什么铺了浮冰？",
            "Зачем в мастерской пол выложен плотным льдом?",
            [
                (
                    "Hang on, what? Couldn't hear you over the blast furnace.",
                    "等会儿，你说啥？高炉声太大我没听清。",
                    "Погоди, чего? Из-за плавильной печи не слышно.",
                ),
                (
                    "Oh, it's just you. Look, if you need the anvil or the crafting table, go ahead, just put tools back where you found them and don't knock over the blue orchids.",
                    "噢，是你啊。听着，要用铁砧还是工作台自便，工具用完放回原处就行，别把桌上的蓝兰花碰倒了。",
                    "А, это ты. Слушай, если нужна наковальня или верстак, пользуйся, только клади инструменты на место и не задевай синие орхидеи.",
                ),
            ],
            (
                "The furnace isn't even that loud right now. It's just the best excuse on earth to ignore people calling down the hall.",
                "其实这会儿熔炉根本没那么响。但这是世上最好用的借口，假装听不见走廊里有人喊我。",
                "Да печь сейчас вообще почти не шумит. Просто это лучшая на свете отговорка, чтобы не откликаться, когда кто-то зовет из коридора.",
            ),
            [
                (
                    "Six furnaces in an underground room without a window: you try standing in here with a full coat of fur without ice under your paws.",
                    "没窗户的地下室里摆六台熔炉，你披一身厚毛进来站站看，脚底下不铺冰根本受不了。",
                    "Шесть печей в подземной комнате без окон; попробуй постоять тут в густой шерсти без льда под лапами.",
                ),
                (
                    "The Manul helped me lay the packed ice last winter. Keeps the air crisp and stops anyone from hanging around chatting for too long.",
                    "去年冬天兔狲帮我铺了这些浮冰。空气凉爽，顺便还能让闲聊的人待不住太久。",
                    "Прошлой зимой манул помог мне уложить плотный лед. Воздух свежий, да и болтуны тут надолго не задерживаются.",
                ),
            ],
        ),
        (
            "resident_library",
            "resident_library_0",
            "You and the wyvern next to you don't seem to talk much.",
            "你和旁边那只双足飞龙好像不怎么说话？",
            "Вы с виверной рядом вроде почти не разговариваете.",
            [
                (
                    'Uh… hi. Look, I\'m right in the middle of a chapter. Was there something urgent, or did the gray fox send you down here to "make friends"?',
                    "呃……你好。那个，我正好看到这一章中间。是有什么急事，还是灰狐派你下来\"交朋友\"的？",
                    "Э… привет. Слушай, я тут как раз на середине главы. Что-то срочное, или серая лиса прислала тебя вниз «познакомиться»?",
                ),
            ],
            (
                "Please don't ask what book it is. If I explain the plot I'll have to talk for three whole minutes.",
                "求你别问我在看什么书。要解释剧情我得整整讲三分钟。",
                "Только не спрашивай, что это за книга. Если начать пересказывать сюжет, придется говорить целых три минуты.",
            ),
            [
                (
                    "That's the whole point. We've been sharing this corner since last autumn and we've spoken maybe twelve sentences total.",
                    "重点就在这儿。去年秋天起我们就共用这个角落，加一块总共大概说过十二句话。",
                    "В этом и весь смысл. Мы делим этот угол с прошлой осени и перекинулись от силы дюжиной фраз.",
                ),
                (
                    "Sometimes they grab a slice of the stuffed pumpkin on the table, I turn a page, and neither of us ruins it by asking how the weather is. Best roommate I've ever had.",
                    "有时候他们从桌上拿块填馅南瓜，我翻一页书，谁也不会问句\"今天天气怎么样\"来破坏气氛。我遇过最好的室友。",
                    "Иногда они берут кусок фаршированной тыквы со стола, я переворачиваю страницу, и никто не портит момент вопросами о погоде. Лучший сосед в моей жизни.",
                ),
            ],
            "shy",
        ),
        (
            "resident_dorm",
            "resident_dorm_0",
            "Why are there so many cardboard boxes stacked in that room?",
            "那个房间里为什么堆了那么多纸箱？",
            "Почему в той комнате так много сложенных картонных коробок?",
            [
                (
                    "Oh, sorry, didn't hear you come down the corridor. I'm not in the way, am I?",
                    "噢，抱歉，没听见你从走廊过来。我没挡路吧？",
                    "Ой — извини, не услышал твои шаги в коридоре. Я не мешаю?",
                ),
                (
                    "If you're looking for an empty bed, try one of the rooms further down the hall. The room full of cardboard boxes is… mostly storage. Definitely not a hiding spot.",
                    "要是找空床的话，可以看看走廊那头的房间。塞满纸箱那间……主要是储藏室。绝对不是用来躲人的地方。",
                    "Если ищешь свободную кровать, загляни в комнаты дальше по коридору. Та комната с картонными коробками — в основном кладовая. Точно не укрытие.",
                ),
            ],
            (
                "Too late to duck into the cardboard room now. Act natural. Pretend I was just checking the wall lever.",
                "现在再钻进纸箱室已经晚了。自然点，假装我刚才只是在检查墙上的拉杆。",
                "В комнату с коробками уже поздно нырять. Веди себя естественно. Сделай вид, что просто проверял рычаг на стене.",
            ),
            [
                (
                    "Half of them are stuff we hauled in when we first moved here, and nobody ever bothered unpacking them.",
                    "一半是刚搬来时大家拖进来的杂物，后来一直没人拆箱。",
                    "Половина — это вещи, которые мы притащили при переезде и так и не удосужились разобрать.",
                ),
                (
                    "Okay, fine, and sometimes when the gray fox shouts down the stairs that we have company, crawling behind the big iron crates is faster than locking a bedroom door. Don't tell the Manul.",
                    "好吧行，有时候灰狐冲楼梯底下喊\"来客人啦\"，钻到那堆大铁箱后头比回卧室锁门快多了。别告诉兔狲。",
                    "Ну ладно, а еще иногда, когда серая лиса кричит сверху, что пришли гости, забраться за большие железные ящики быстрее, чем запереть дверь спальни. Только манулу не говори.",
                ),
            ],
            "shy",
        ),
    ]

    for (
        open_id,
        follow_id,
        topic_en,
        topic_zh,
        topic_ru,
        open_spoken,
        mental_triple,
        follow_spoken,
        *rest,
    ) in residents:
        open_emotion = rest[0] if rest else "calm"
        mental_en, mental_zh, mental_ru = mental_triple
        nodes[open_id] = {
            "lines": [
                spoken_block(open_spoken, emotion=open_emotion),
                mental_block(mental_en, mental_zh, mental_ru, emotion="curious"),
            ],
            "choices": [
                choice("topic_0", topic_en, topic_zh, topic_ru, follow_id),
                choice("close", "I'll leave you to it.", "不打扰你了。", "Не побеспокою.", "root", effect="close"),
            ],
        }
        nodes[follow_id] = {
            "lines": [spoken_block(follow_spoken, emotion=open_emotion)],
            "choices": [
                choice("back", "Something else…", "聊点别的……", "О другом…", open_id),
                choice("close", "I'll leave you to it.", "不打扰你了。", "Не побеспокую.", "root", effect="close"),
            ],
        }

    return nodes


MANUL_ROOT_RETURN_CHOICES = [
    choice(
        "manul_about",
        "You handle the inside of the Burrow, right?",
        "地穴里面这摊事都是你在管，对吧？",
        "Внутри Норы всем заправляешь ты, верно?",
        "about",
    ),
    choice(
        "manul_home",
        "How do you keep everyone fed down here without going outside?",
        "你们不出门的话，平时怎么顾得上这么多人吃饭？",
        "Как вы кормите всех здесь внизу, не выходя наружу?",
        "home",
    ),
    choice(
        "manul_past",
        "The gray fox told me you transformed just to stay with her.",
        "灰狐跟我说，你当初转化就是为了陪在她身边。",
        "Серая лиса сказала, что ты прошел превращение только ради того, чтобы остаться с ней.",
        "past",
        requires=["about", "home"],
    ),
    choice(
        "manul_clue_terminal",
        "I read the old file you left on the computer downstairs.",
        "我看到你留在楼下电脑里的那份旧文件了。",
        "Я прочитал старый файл, который ты оставил на компьютере внизу.",
        "clue_terminal",
        cond="clue:burrow_terminal",
    ),
    choice(
        "manul_clue_rules",
        "I read your kitchen rules note. Did she write the part at the bottom?",
        "我看了你写的厨房规矩。最底下那行是她添的吧？",
        "Я читал твои правила на кухне. Нижнюю строчку приписала она?",
        "clue_rules",
        cond="clue:burrow_rules",
    ),
    choice(
        "manul_enrol",
        "Is there a corner for me down here in the Burrow?",
        "这地穴里能给我留个角落安顿吗？",
        "Найдется ли для меня уголок в Норе?",
        "enrol",
        cond="stranger",
    ),
    choice(
        "manul_membership",
        "About my staying down here…",
        "关于我住在地穴里的事……",
        "Насчет моей жизни в Норе…",
        "membership",
        cond="member",
    ),
    choice(
        "manul_goodbye",
        "I'll leave you to your cooking.",
        "不打扰你做饭了。",
        "Не буду мешать тебе готовить.",
        "goodbye",
    ),
]

BACK_GOODBYE_MANUL = [
    choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
    choice("goodbye", "I'll leave you to your cooking.", "不打扰你做饭了。", "Не буду мешать тебе готовить.", "goodbye"),
]


def manul_chief_nodes():
    nodes = {}

    nodes["root"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "…She let a human downstairs. Of course she did.",
                        "……她居然放了个人类下楼。我就知道。",
                        "…Она впустила вниз человека. Ну конечно.",
                    ),
                    (
                        "Wipe the snow off your boots before you step on the rug. There's shepherd's pie and salad on the counter if you're hungry; help yourself and don't leave empty cans by the stoves.",
                        "踩地毯前先把靴子上的雪蹭掉。饿了的话吧台上有牧羊人派和沙拉，自己拿，别把空罐头留在灶台边。",
                        "Вытри снег с сапог, прежде чем наступать на ковер. Если голоден, на стойке есть пастуший пирог и салат; угощайся, только не оставляй пустые банки у плит.",
                    ),
                ],
                emotion="calm",
                condition="human",
            ),
            mental_block(
                "Boots are freezing, nose is red, probably walked three miles in the snow. Better put the soup pot back on low heat…",
                "靴子都冻透了，鼻子也冻红了，大概在雪里走了好几里路。还是把汤锅放回小火上热着吧……",
                "Сапоги промерзли, нос красный — наверное, три мили по снегу шел. Поставлю-ка кастрюлю с супом обратно на слабый огонь…",
                condition="human",
            ),
            spoken_block(
                [
                    (
                        "Keep your voice down near the stairs; people sleep at odd hours here.",
                        "楼梯附近小点声，这儿的人作息都不太一样。",
                        "Возле лестницы потише — здесь все спят в разное время.",
                    ),
                    (
                        "If you're looking for food, the counter is open. If you're looking for small talk, she's the one upstairs.",
                        "找吃的话吧台随便拿。要是想找人闲聊，楼上那位更乐意陪你。",
                        "Если ищешь еду, стойка открыта. Если ищешь с кем поболтать, это к ней наверх, на чердак.",
                    ),
                ],
                emotion="calm",
                condition="transformed",
            ),
            mental_block(
                "Looks quiet enough. Good. No sudden shouting near the soup pot.",
                "看着还算安静。不错。别在汤锅边一惊一乍的就行。",
                "Вроде спокойный. Хорошо. Главное — без резких криков возле кастрюли с супом.",
                condition="transformed",
            ),
        ],
        "choices": MANUL_ROOT_RETURN_CHOICES,
    }

    nodes["return"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Back again. Feet wiped? Good. The stoves are lit if you need a warm corner.",
                        "又来了。脚擦干净了？行。炉灶烧着呢，想找地方烤火就自便。",
                        "Снова ты. Ноги вытер? Хорошо. Плиты затоплены, если нужно погреться.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Doesn't slam doors, doesn't pester the kids in the dorms. Honestly, better houseguest than half the people I used to work with.",
                "不摔门，也不去烦宿舍里那些小孩。说实话，比我以前一半的同事都懂事。",
                "Дверями не хлопает, к ребятам в спальнях не лезет. Честно говоря, гость куда лучше половины моих бывших коллег.",
            ),
        ],
        "choices": MANUL_ROOT_RETURN_CHOICES,
    }

    nodes["about"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Somebody has to keep the stoves running, check the ice lockers, and make sure the pantry doesn't go empty. She's good with outsiders; I'm better with a cutting board and quiet rooms.",
                        "总得有人管着炉灶、检查冷库、别让粮仓见底。她擅长应付外人，我更适合守着砧板和安静的屋子。",
                        "Кому-то надо следить за плитами, проверять ледники и смотреть, чтобы кладовая не пустела. Она хорошо ладит с посторонними, а у меня лучше получается с разделочной доской и тишиной.",
                    ),
                    (
                        "Most of the people living here hate being asked how they're feeling. Slide a hot plate across the red counter, nod once, and leave them alone—that works way better than speeches.",
                        "住在这儿的大多数人都烦别人追着问\"你心情怎么样\"。把热饭往红色吧台上一推，点个头就走开——比说一堆大道理管用多了。",
                        "Большинство здешних терпеть не могут расспросы о самочувствии. Пододвинешь горячую тарелку по красной стойке, кивнешь раз и оставишь в покое — работает куда лучше любых речей.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Also nobody else remembers to turn the lower furnace off before going to bed.",
                "而且其他人睡觉前从来记不住关掉楼下的熔炉。",
                "К тому же никто больше не помнит выключать нижнюю печь перед сном.",
            ),
        ],
        "choices": [
            choice(
                "manul_gray_fox",
                "You two seem like complete opposites. How do you put up with each other?",
                "你们俩性格看着完全相反，平时是怎么处得来的？",
                "Вы кажетесь полными противоположностями. Как вы уживаетесь?",
                "gray_fox",
            ),
            *BACK_GOODBYE_MANUL,
        ],
    }

    nodes["gray_fox"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Put up with her? She's the reason I don't have to talk to travelers at the front door.",
                        "忍受她？要不是她顶着，门口那些旅人全得我来应付。",
                        "Уживаемся? Благодаря ей мне не приходится разговаривать с путниками у входа.",
                    ),
                    (
                        "Sure, she tracks snow across the floor and ambushes me with hugs while I'm holding a soup ladle. But she also spent years stuck in a bed wishing she could walk five minutes outside. If she wants to drag half the snowdrift indoors on her tail, I can mop the floor.",
                        "她是老往地板上带雪，还老趁我端汤勺的时候突然扑过来抱我。但她从前有好几年被困在病床上，连去外头走五分钟都做不到。现在她要是想用尾巴带半堆雪进来，地板我拖就是了。",
                        "Да, она вечно наносит снег на пол и бросается обниматься прямо тогда, когда у меня в руках половник. Но она годами пролежала в постели, мечтая просто пройтись пять минут по улице. Если ей хочется притащить на хвосте полсугроба — я могу и пол протереть.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Do not let her hear me say that out loud or she'll never wipe her paws again.",
                "这话可不能让她听见，不然她以后绝对再也不在地毯上蹭雪了。",
                "Только бы она этого не услышала, иначе вообще перестанет вытирать лапы.",
            ),
        ],
        "choices": BACK_GOODBYE_MANUL,
    }

    nodes["home"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "We designed this place so nobody has to leave if they're having a bad week. Right past the kitchen doorway are the crop beds and the animal pen: wheat, potatoes, carrots, beets, plus a couple cows, sheep, and chickens.",
                        "我们把这儿修成这样，就是为了谁这礼拜状态不好，可以彻底不出门。出厨房门就是农田和畜栏，从小麦、土豆、胡萝卜、甜菜，到几头牛羊和鸡应有尽有。",
                        "Мы устроили тут все так, чтобы в плохую неделю можно было вообще не выходить наружу. Сразу за дверью кухни — грядки и загон: пшеница, картофель, морковь, свекла, плюс пара коров, овец и кур.",
                    ),
                    (
                        "Down another level there's a fish pond and a cooled workshop. And before you ask, yes, we run warm-air heating alongside all the ice and snow on purpose. Thick winter fur overheats fast in a closed room, so the ice keeps the temperature balanced without letting the pipes freeze.",
                        "再往下一层有鱼池和铺了冰的工坊。对了，省得你问，我们同时开着暖风又摆着这么多冰雪，是故意的。一身厚冬毛待在封闭屋子里很容易热过头，靠冰雪把温度压平，既不会闷热，也不至于把水管冻住。",
                        "Этажом ниже есть пруд с рыбой и охлаждаемая мастерская. И да, пока не спросил: обогреватели и весь этот лед со снегом работают вместе намеренно. В густой зимней шерсти в закрытом помещении быстро становится жарко, так что лед держит равновесие, не давая ни перегреться, ни заморозить трубы.",
                    ),
                    (
                        "The double chest right by the kitchen entrance is our shared supply chest. Put extra food there, take what you need if you live here, and leave people's bedroom boxes alone.",
                        "厨房入口边上那个大箱子是公共补给箱。有多余食物就放那儿，住在这儿的缺啥自己拿，别动别人卧室里的箱子就行。",
                        "Двойной сундук прямо у входа на кухню — общий запас. Лишнюю еду клади туда, жильцы берут по нужде, а ящики по спальням трогать не надо.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "One day I'm going to find out who keeps putting potted wither roses on my bar counter. Pretty sure it was the wyvern.",
                "我迟早要查出来是谁老往我吧台上摆盆栽凋灵玫瑰。十有八九是那只双足飞龙干的。",
                "Когда-нибудь я все-таки выясню, кто вечно ставит на мою стойку горшки с розой визера. Почти уверен, что виверна.",
            ),
        ],
        "choices": BACK_GOODBYE_MANUL,
    }

    nodes["past"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "It wasn't complicated. She needed the transformation to live. I didn't care about immortality or turning into an animal, and I definitely didn't want to deal with the clinic paperwork.",
                        "其实没那么复杂。她需要转化才能活下去。我对永生或者变成动物一点兴趣都没有，更懒得填诊所那堆表格。",
                        "Все было просто. Ей нужно было превращение, чтобы выжить. Мне не было дела ни до бессмертия, ни до превращения в зверя, а уж клинические бумаги я тем более терпеть не мог.",
                    ),
                    (
                        "I just knew that if she crossed over and I stayed behind, we'd eventually end up in different worlds, or I'd get old while she didn't. So I chose transformation myself as well. Best stubborn decision I ever made.",
                        "我只是清楚，要是她过去了而我留在原地，我们迟早会变成两个世界的人，或者我会变老而她不会。所以我自己也选了转化。这辈子我犯过最值的一次倔。",
                        "Я просто знал: если она перейдет на ту сторону, а я останусь, рано или поздно мы окажемся в разных мирах, или я состарюсь, а она нет. Так что я и сам пошел на превращение. Самое лучшее упрямое решение в моей жизни.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Thick fur is nice in winter, though. And standing on my own tail when the floor tiles are cold actually works.",
                "不过冬天厚毛确实暖和。地砖凉的时候把脚踩在自己尾巴上，还真挺管用。",
                "Зато густая шерсть зимой — вещь хорошая. И вставать лапами на собственный хвост, когда плитка холодная, реально помогает.",
            ),
        ],
        "choices": BACK_GOODBYE_MANUL,
    }

    nodes["clue_terminal"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "…You went through the double doors at the bottom of the stairs, right into our bedroom, and opened the computer.",
                        "……你穿过楼梯底下的双开门，直接进了我们俩的卧室，还把那台电脑打开了。",
                        "…Ты прошел за двойную дверь внизу лестницы, прямо в нашу спальню, и включил компьютер.",
                    ),
                    (
                        "I'm not mad, just surprised the screen still boots. I typed that the week we finished setting up our room; between the warm-air heating and the fact that a Manul has the thickest fur in the whole Burrow, I needed that whole corner packed with ice and snow just to keep from overheating at night. Close the door on your way out next time, it keeps the temperature balanced.",
                        "倒没生气，只是没想到屏幕居然还能点亮。那段字是刚把卧室收拾好那周我顺手打的；屋里开着暖风，而兔狲的毛又是全地穴最厚的，角落里不堆够那么多冰雪，我晚上非热坏不可。下次出去记得随手关门，好维持住里面的温度平衡。",
                        "Я не злюсь, просто удивлен, что экран все еще включается. Я напечатал это в ту неделю, когда мы обустраивали комнату: с обогревателями и самым густым мехом во всей Норе мне пришлось забить весь угол льдом и снегом, иначе ночью просто не продохнуть от жары. В следующий раз закрывай за собой дверь, чтобы не сбивать баланс.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": BACK_GOODBYE_MANUL,
    }

    nodes["clue_rules"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Yes, she scribbled over my fourth rule ten minutes after I pinned it up.",
                        "对，我刚贴上去十分钟，她就把第四条给涂了。",
                        "Да, она чиркнула поверх моего четвертого пункта через десять минут после того, как я его повесил.",
                    ),
                    (
                        "Look, I don't care if people want privacy, I just don't want anyone knocking over my crate stacks in the storage room every time a traveler knocks. If I leave the pie out on the bar, everybody wins.",
                        "听着，大家想图清静我没意见，我只是不想每次一有旅人敲门，储藏室里那堆箱子就被撞翻。我把派直接摆在吧台上，谁都省心。",
                        "Слушай, я не против личного пространства, просто не хочу, чтобы в кладовой каждый раз сносили стопки ящиков, стоит кому-то постучать. Оставляю пирог на стойке — и все довольны.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": BACK_GOODBYE_MANUL,
    }

    nodes["enrol"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "She usually handles newcomers, but since you're standing right in my kitchen: welcome.",
                        "平时打理新人是她的事，但既然你人都站到我灶台前了：安顿下来吧。",
                        "Обычно с новичками говорит она, но раз уж ты стоишь тут, у моих плит — устроим тебя.",
                    ),
                    (
                        "Find a free cot down the hallway. Keep the noise down, wipe your paws, and don't push open closed doors. Simple as that.",
                        "去走廊那头找张空床。别大声喧哗，记得在地毯上蹭干净雪，别乱动关着的门。就这么点基本规矩。",
                        "Свободную койку найдешь дальше по коридору. Главное: не шуми, вытирай лапы и не ломись в закрытые двери. Вот и всё.",
                    ),
                ],
                emotion="calm",
                condition="transformed_join",
            ),
            spoken_block(
                [
                    (
                        "If you want a quiet bed down here, stay as you are. Humans don't leave fur in my soup, which is a massive relief.",
                        "要是想找个清静地方落脚，保持现在的样子就好。人类至少不会往我汤锅里掉毛，说实话这省了我不少心。",
                        "Ищешь тихий угол? Оставайся человеком. От вас хоть шерсть в суп не летит, и на том спасибо.",
                    ),
                    (
                        "Though if you'd rather move in as a Manul (temporarily or permanently), I can manage that too. Up to you.",
                        "话虽如此，你要是想体验一下当兔狲的生活（暂时体验或一直保持），我也能搞定。全随你便。",
                        "Впрочем, если хочешь обернуться манулом, на время или навсегда, я могу это устроить. Решай сам.",
                    ),
                ],
                emotion="calm",
                condition="human_join",
            ),
            spoken_block(
                [
                    (
                        "Not today. Everyone downstairs is on edge, and I'm not adding anyone new until the house settles back into a quiet rhythm. Come back later.",
                        "今天不行。大家心里都不踏实，在大伙儿恢复平静之前，我不会再添新人。过阵子再说吧。",
                        "Не сегодня. Народу сейчас не до новых лиц, а я не стану селить кого-то еще, пока у нас снова не станет тихо. Загляни попозже.",
                    ),
                ],
                emotion="calm",
                condition="cannot_join",
            ),
        ],
        "choices": [
            choice(
                "join_latex",
                "I'll settle in just as I am.",
                "我就保持现在的样子住下来。",
                "Я останусь у вас в своей текущей форме.",
                "welcome_latex",
                cond="transformed_join",
                effect="join",
            ),
            choice(
                "join_human",
                "I'll stay human and settle down.",
                "我想以人类的身份住下来。",
                "Я останусь человеком и буду жить с вами.",
                "welcome_human",
                cond="human_join",
                effect="join",
            ),
            choice(
                "explain_form",
                "What is it like to take on the form of a Manul?",
                "变成一只兔狲到底是什么体验？",
                "Каково это — принять дух и облик манула?",
                "explain_form",
                cond="can_reversible",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["explain_form"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Your mind and memories remain untouched, and sharing this form doesn't drain me.",
                        "你的记忆和心智完全不会改变，分你这份形态也不会伤到我。",
                        "Память и разум останутся с тобой, а мне эта передача формы не пойдет во вред.",
                    ),
                    (
                        "A Manul isn't made for running long distances. You get a dense, sturdy frame, small round ears set low so you don't bump your head in cramped tunnels, and the thickest fur of anyone down here: great out in the snow, though indoors you'll want some ice and snow nearby so you don't get too warm.",
                        "兔狲不是用来长跑的。身形会更敦实，小圆耳朵位置偏下，在地道里不易碰头，而且毛发是这下面所有人里最厚的：在雪地里很舒服，不过待在室内时，身边最好放些冰雪降降温，免得太热。",
                        "Тело манула — не для маршей. Ты станешь коренастым, плотным, с низко посаженными ушками, чтоб не задевать низкие потолки, и с самым густым мехом во всей Норе: на снегу отлично, а вот в помещении рядом лучше держать лед и снег, чтобы не перегреться.",
                    ),
                    (
                        "Reversible means you can return to human form later. Permanent means this fur stays with you for good. Staying human is just fine too; the kitchen rules are the same either way.",
                        "选择可逆，日后想变回人类随时可以；选择永久，这身厚皮毛就一直跟着你。当然，保持人类也挺好——反正厨房规矩一模一样。",
                        "Обратимый вариант позволит вернуть человеческий вид потом. Постоянный — оставит эту шубу с тобой навсегда. Остаться человеком — тоже нормальный выбор, правила на кухне от этого не меняются.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice(
                "reversible",
                "Let's try the Manul form temporarily and join the den.",
                "我先试试可逆的兔狲形态，在地穴里住下。",
                "Давай поносим форму манула временно и присоединимся к вам.",
                "welcome_copy",
                cond="can_reversible",
                effect="reversible",
            ),
            choice(
                "permanent_menu",
                "Make it permanent and count me in.",
                "让我永久变成兔狲，算我一份吧。",
                "Сделай превращение постоянным и зачисли меня.",
                "confirm_permanent",
                cond="can_permanent",
            ),
            choice(
                "join_human",
                "I'll just stay human and move in.",
                "我还是保持人类身份住下来。",
                "Я лучше просто поселюсь как человек.",
                "welcome_human",
                cond="human_join",
                effect="join",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["confirm_permanent"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Think carefully before giving your final word. Permanent means this dense Manul fur becomes your body for good; stepping outside the Burrow won't change you back, and basic remedies won't undo it.",
                        "点头前先仔细斟酌好。选了永久，意味着这身厚实的兔狲皮毛将永远属于你；即便离开地穴也不会改变，常规手段也没法变回去。",
                        "Подумай хорошо, прежде чем согласиться. Постоянное превращение означает, что мех манула станет твоим телом навечно: уход из Норы не отменит его, и обычные средства не вернут всё назад.",
                    ),
                    (
                        "You can still choose to keep it temporary or stay human. Are you certain you want this forever?",
                        "你现在依然可以改选可逆形态或者保留人类身体。你真的确定要永久如此吗？",
                        "Ты всё ещё можешь выбрать временный вариант или остаться человеком. Ты действительно хочешь этого навсегда?",
                    ),
                ],
                emotion="determined",
            ),
        ],
        "choices": [
            choice(
                "confirm",
                "Yes, I'm sure. Make it permanent.",
                "是的，我确定，永久转化吧。",
                "Да, я уверен. Делай навсегда.",
                "welcome_permanent",
                cond="can_permanent",
                effect="permanent",
            ),
            choice(
                "rethink",
                "Let me think it over once more.",
                "让我再想想。",
                "Дай мне еще обдумать.",
                "explain_form",
            ),
        ],
    }

    welcome_back = BACK_GOODBYE_MANUL

    nodes["welcome_human"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Settled. You've got a place here. The shared bin is by the door, empty cots are downstairs, and there's a stool at the counter whenever you want a hot meal in peace.",
                        "好，定下了。你在地穴里有落脚处了。公共粮食箱在门口，空床在楼下，想安静吃口热饭的时候，吧台随时有你的空凳子。",
                        "Готово. У тебя есть свой уголок. Общий сундук — у входа, койки — внизу, а за стойкой всегда найдется табурет, когда захочешь спокойно поесть.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Better keep a pair of thick socks in the reserve chest. Humans get cold fast down here.",
                "回头得在公共箱里放双厚袜子。人类在下面怕冷。",
                "Надо положить в общий сундук пару толстых носков. Люди тут внизу быстро мерзнут.",
            ),
        ],
        "choices": welcome_back,
    }

    nodes["welcome_latex"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Alright, you're home. Pick an open room downstairs, take what you need from the pantry, and enjoy the quiet.",
                        "行，你算在这儿落脚了。去楼下选间没人住的房间，需要食物去公用箱里拿，享受属于你的清静吧。",
                        "Ладно, устраивайся. Выбирай свободную комнату внизу, бери еду из общего сундука и наслаждайся покоем.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": welcome_back,
    }

    nodes["welcome_copy"] = nodes["welcome_permanent"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "There you go. Heavy coat, sturdy balance. If the stone floor feels cold underfoot, try resting your paws on your tail… Don't laugh, it actually works.",
                        "成了。毛发厚实，站得也稳。如果觉得石地板凉，就把脚踩在尾巴上……别笑，这招真管用。",
                        "Ну вот. Густая шуба, уверенный шаг. Если каменный пол покажется холодным, попробуй встать лапами на свой хвост… Не смейся, работает.",
                    ),
                    (
                        "Welcome to the Burrow. Take a seat at the bar whenever you're hungry.",
                        "欢迎来到地穴。饿了就去吧台找张凳子坐下。",
                        "Добро пожаловать в Нору. Занимай место у стойки, когда проголодаешься.",
                    ),
                ],
                emotion="calm",
            ),
            mental_block(
                "Huh. Another round cat in the kitchen. At least this one won't knock the pans off the shelf.",
                "嗯，厨房里又多了只圆猫。起码这位不会把架子上的锅碰掉。",
                "Хм. Еще один круглый кот на кухне. Этот хотя бы не станет смахивать сковородки с полки.",
            ),
        ],
        "choices": welcome_back,
    }

    nodes["membership"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "You're a resident here. The common food chest and open cots are yours to use; bedroom trunks remain private. If you ever decide to move on, just let me know so we can part on good terms.",
                        "你是这里的住户了。公共食物箱和空床位你随时可以用，别乱动其他人卧室里的私人物品就行。哪天如果你打算离开，告诉我一声，咱们平平安安地道别。",
                        "Ты здесь жилец. Общий сундук с провизией и свободные койки — в твоем распоряжении, но личные вещи в спальнях не трогай. Если соберешься уходить — предупреди, расстанемся по-хорошему.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice(
                "leave_menu",
                "I need to pack my things and leave the Burrow.",
                "我得收拾行装离开地穴了。",
                "Мне нужно собраться и покинуть Нору.",
                "confirm_leave",
                cond="member",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["confirm_leave"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Are you certain? Departing means you give up your share of the common storage, though you're always welcome to step back in for a meal. Leaving won't change a permanent form back.",
                        "真的想好了？离开就意味不能随意使用这里的公共粮仓了，不过想回来吃口热饭随时欢迎。离开也不会让永久形态变回去。",
                        "Уверен? Уходя, ты отказываешься от доли в общем сундуке, хотя зайти поесть сможешь всегда. Постоянный облик от ухода не изменится.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice(
                "confirm_leave",
                "Yes, I'm taking my leave.",
                "是的，我这就告辞了。",
                "Да, я ухожу.",
                "left",
                cond="member",
                effect="leave",
            ),
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
        ],
    }

    nodes["left"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Safe travels. Grab a couple of rations for the road if you need them, and stay warm out there in the cold.",
                        "祝你一路平安。带上两份干粮备用，外面冷，多保重。",
                        "Бывай. Возьми пару пайков на дорогу, если надо, и смотри не замерзни.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice("back", "Something else…", "聊点别的……", "О другом…", "return"),
            choice("goodbye", "I'll leave you to your cooking.", "不打扰你做饭了。", "Не буду мешать тебе готовить.", "goodbye"),
        ],
    }

    nodes["goodbye"] = {
        "lines": [
            spoken_block(
                [
                    (
                        "Close the door behind you if you head downstairs. Keeps the draft out.",
                        "下楼的话随手带上门，省得灌风。",
                        "Прикрой за собой дверь, если пойдешь вниз. Чтобы не сквозило.",
                    ),
                ],
                emotion="calm",
            ),
        ],
        "choices": [
            choice("close", "Take care.", "保重。", "Бывай.", "root", effect="close"),
        ],
    }

    return nodes


def main():
    if not MD_PATH.is_file():
        print(f"Warning: MD not found at {MD_PATH} (using embedded text only)")

    story = json.loads(STORY_PATH.read_text(encoding="utf-8"))

    story["profiles"]["gray_fox_chief"] = {
        "template": "burrow",
        "species": "changed_synergy:latex_gray_fox_female",
        "trait": "PLAYFUL",
        "chief": True,
        "equipment": [],
        "title": {"en_us": "Lookout", "zh_cn": "瞭望者", "ru_ru": "Дозорная"},
        "nodes": polish_profile(gray_fox_nodes(), "fox"),
    }

    story["profiles"]["manul_chief"] = {
        "template": "burrow",
        "species": "changed_synergy:latex_manul",
        "trait": "CALM",
        "chief": True,
        "equipment": [],
        "title": {"en_us": "Steward", "zh_cn": "管家", "ru_ru": "Дворецкий"},
        "nodes": polish_profile(manul_chief_nodes(), "manul"),
    }

    fox_count = len(story["profiles"]["gray_fox_chief"]["nodes"])
    manul_count = len(story["profiles"]["manul_chief"]["nodes"])

    STORY_PATH.write_text(json.dumps(story, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"gray_fox_chief: {fox_count} nodes")
    print(f"manul_chief: {manul_count} nodes")
    print(f"Wrote {STORY_PATH}")


if __name__ == "__main__":
    main()