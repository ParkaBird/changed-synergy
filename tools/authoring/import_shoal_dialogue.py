"""Author The Shoal story_npcs graph, camp_dialogue voices, and EN/ZH/RU translations."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORY_NPC_PATH = ROOT / "src/main/resources/data/changed_synergy/story_npcs/shoal_chief.json"
COMMON_DIALOGUE_PATH = ROOT / "src/main/resources/data/changed_synergy/camp_dialogue/common.json"
LANG_DIR = ROOT / "src/main/resources/assets/changed_synergy/lang"


def part(text: str, emotion: str = "calm", effect: str = "wave", speed: float = 1, intensity: float = 0.65, pause: int = 120, beat: bool = False):
    obj = {
        "text": text,
        "emotion": emotion,
        "effect": effect,
        "speed": speed,
        "intensity": intensity,
        "pause": pause,
    }
    if beat:
        obj["beat"] = True
    return obj


def line(channel: str, condition: str, parts: list[dict]):
    return {"channel": channel, "condition": condition, "parts": parts}


def choice(cid: str, key_prefix: str, nxt: str, condition: str = "always", effect: str = "none", requires: list[str] | None = None):
    obj = {
        "id": cid,
        "text": f"{key_prefix}.choice.{cid}",
        "next": nxt,
        "condition": condition,
        "effect": effect,
    }
    if requires:
        obj["requires"] = requires
    return obj


def cpart(text: str, emotion: str = "calm", effect: str = "wave", pause: int = 140, intensity: float = 0.65):
    return {
        "text": text,
        "emotion": emotion,
        "effect": effect,
        "pause": pause,
        "intensity": intensity,
    }


def add_tr(tr: dict, key: str, en: str, zh: str, ru: str):
    assert key not in tr, f"Duplicate translation key {key}"
    tr[key] = (en, zh, ru)


def build_shoal_chief():
    tr: dict[str, tuple[str, str, str]] = {}
    p = "npc.changed_synergy.shoal_chief"
    add_tr(tr, f"{p}.title", "The Scavenger", "拾荒者", "Старьевщик")
    add_tr(tr, f"{p}.name", "The Scavenger", "拾荒者", "Старьевщик")

    nodes: dict[str, dict] = {}

    def root_choices(kp: str):
        items = [
            ("about", "about", "always", "none", None,
             "What do you do out here on the water?",
             "你在这片水面上平时都忙些什么？",
             "Чем ты тут занимаешься посреди воды?"),
            ("home", "home", "always", "none", None,
             "This is a cozy spot… who else lives on the shoal with you?",
             "这地方看着真舒服，浅滩上还住着哪些同伴？",
             "Уютное у вас место, кто еще живет с тобой на Отмели?"),
            ("past", "past", "always", "none", ["about", "home"],
             "Don't you ever miss your old human form?",
             "你平时不会怀念自己从前的人类身体吗？",
             "Ты совсем не скучаешь по своему старому человеческому телу?"),
            ("clue_disc", "clue_disc", "clue:shoal_disc", "none", None,
             "I found an old dive log disc on the desk in the red room.",
             "我在红屋子的桌上发现了一张旧潜水日志光盘。",
             "Я нашел старый диск с записями о погружениях на столе в красной комнате."),
            ("clue_manifest", "clue_manifest", "clue:shoal_manifest", "none", None,
             "I saw your salvage tally note in the red room.",
             "我看到红屋子里那张打捞清单便签了。",
             "Я видел твою записку со списком находок в красной комнате."),
            ("enrol", "enrol", "stranger", "none", None,
             "What about me? Could I join your crew here?",
             "那我呢？能加入你们这支小队吗？",
             "А как насчет меня? Можно присоединиться к вашей команде?"),
            ("membership", "membership", "member", "none", None,
             "About my place here on the shoal…",
             "关于我留在浅滩这边的事……",
             "Насчет моего места здесь, на Отмели…"),
            ("goodbye", "goodbye", "always", "none", None,
             "I'll let you get back to your work. See you!",
             "不打扰你忙活了，回头见！",
             "Не буду отвлекать тебя от дел. Увидимся!"),
        ]
        res = []
        for cid, nxt, cond, eff, req, en, zh, ru in items:
            add_tr(tr, f"{kp}.choice.{cid}", en, zh, ru)
            res.append(choice(cid, kp, nxt, cond, eff, req))
        return res

    # root
    add_tr(
        tr, f"{p}.root.0.0",
        "No way—a human actually made it out to my shack! I haven't seen one of you folks out here in… well, ever. Oh, sorry, how rude of me.",
        "不会吧……真有个人类一路找到我这间小屋来了！我在这儿可……怎么说呢，从没见过你们。 噢，抱歉，太没礼貌了。",
        "Не может быть, до моей хижины и правда добрался человек! Я никого из ваших тут не видел… да вообще никогда. Ой, извини, как невежливо.",
    )
    add_tr(
        tr, f"{p}.root.0.1",
        " Everyone around here calls me the Scavenger—just an old hand who still likes combing the ocean floor for treasure. Come on in, take a load off. Mi casa es tu casa!",
        "这附近的人都叫我\"拾荒者\"，就是个老喜欢去海底翻宝贝的老家伙。快进来歇歇吧，mi casa es tu casa，当自己家就行！",
        " Местные зовут меня Старьевщиком: просто старый любитель пошарить по морскому дну в поисках сокровищ. Проходи, устраивайся. Mi casa es tu casa!",
    )
    add_tr(
        tr, f"{p}.root.1.0",
        "(Whoa, a real human on the pier! Angle the nose away, angle the nose away… last thing we need is poking our first human guest in the shoulder.)",
        "（哇，栈桥上真站着个活人！把吻部偏开、偏开……可别一转头就把第一位人类客人给戳着了。）",
        "(Ого, настоящий человек на причале! Отверни нос, отверни нос… только не хватало ткнуть первого же гостя-человека в плечо.)",
    )
    add_tr(
        tr, f"{p}.root.2.0",
        "Hey there, pal! Welcome out to The Shoal. Watch your step on the wet planks by the edge, the tide's been splashing up all morning.",
        "嗨，朋友！欢迎来到浅滩。留神边上湿滑的木板，一早上浪花都在往上拍。",
        "Привет, дружище! Добро пожаловать на Отмель. Осторожнее на мокрых досках у края, прилив с самого утра плещет.",
    )
    add_tr(
        tr, f"{p}.root.2.1",
        " Guys around here call me the Scavenger. Grab a seat under the awning by the cooking pot and relax. Mi casa es tu casa!",
        " 这儿的人都叫我\"拾荒者\"。去白篷底下汤锅旁边找个位子坐着歇会儿吧，mi casa es tu casa，别客气！",
        " Здесь меня зовут Старьевщиком. Присаживайся под навесом у котелка и отдыхай. Mi casa es tu casa!",
    )
    add_tr(
        tr, f"{p}.root.3.0",
        "(Friendly face! Wonder if they walked over from the shore, and more importantly, if they've got any oranges in their pack.)",
        "（看着挺面善！不知道是不是从岸边过来的，更重要的是，背包里有没有带橙子。）",
        "(Славный малый! Интересно, он с берега пришел, и, что куда важнее, есть ли у него в рюкзаке апельсины.)",
    )
    nodes["root"] = {
        "lines": [
            line("spoken", "human", [
                part(f"{p}.root.0.0", "surprised", "impact", 1, 0.85, 130),
                part(f"{p}.root.0.1", "happy", "bounce", 1, 0.75, 140, beat=True),
            ]),
            line("mental", "human", [
                part(f"{p}.root.1.0", "curious", "whisper", 1, 0.65, 0),
            ]),
            line("spoken", "transformed", [
                part(f"{p}.root.2.0", "happy", "wave", 1, 0.7, 130),
                part(f"{p}.root.2.1", "happy", "bounce", 1, 0.75, 140, beat=True),
            ]),
            line("mental", "transformed", [
                part(f"{p}.root.3.0", "curious", "whisper", 1, 0.65, 0),
            ]),
        ],
        "choices": root_choices(f"{p}.root"),
    }

    # return
    add_tr(
        tr, f"{p}.return.0.0",
        "Hey, look who's back! Come on up onto the boards. The pot's warm by the fire if you're hungry, and the ocean breeze is doing its job today.",
        "嘿，瞧瞧谁回来啦！快上甲板来。饿了的话火堆上的锅正热着，今天海风也吹得正舒服。",
        "Эй, гляньте, кто вернулся! Поднимайся на настил. Если проголодался, котелок на огне еще теплый, да и морской бриз сегодня что надо.",
    )
    add_tr(
        tr, f"{p}.return.1.0",
        "(Good to see them again! Just don't turn around too fast near the doorway, workshop shark already yelled at me twice this week…)",
        "（真高兴又见到他！不过门口附近千万别转身太猛，工坊里那条鲨鱼这礼拜已经数落我两回了……）",
        "(Рад снова его видеть! Только не разворачивайся слишком резко у двери, акула из мастерской уже дважды на меня ворчала на этой неделе…)",
    )
    nodes["return"] = {
        "lines": [
            line("spoken", "always", [part(f"{p}.return.0.0", "happy", "bounce", 1, 0.7, 140)]),
            line("mental", "always", [part(f"{p}.return.1.0", "whisper", "whisper", 1, 0.6, 0)]),
        ],
        "choices": root_choices(f"{p}.return"),
    }

    # about
    add_tr(
        tr, f"{p}.about.0.0",
        "Back when I was human, I made my living as a deep-sea scavenger. Picture it: diving down to the darkest stretches of the ocean floor, hunting for sunken cargo, gold, diamonds—whatever good, shiny stuff the tide buried—and hauling it back up.",
        "以前还是人类的时候，我是干深海打捞这行的。你想想：潜到海底最深的地方，搜罗沉船货物、金子、钻石……只要是海里埋着的闪亮好东西都捞上来。",
        "Когда я еще был человеком, я зарабатывал на жизнь глубоководным поиском. Представь: ныряешь в самые глубокие участки дна, ищешь затонувший груз, золото, алмазы — все блестящее и ценное, что утянуло под воду, и поднимаешь наверх.",
    )
    add_tr(
        tr, f"{p}.about.0.1",
        " That was my job, and it paid great back in the day. Honestly, I still do it just for the thrill. This sleek blue body isn't just for show either. If you ever want to try arm wrestling, better warm up first, champ!",
        " 这就是我的老本行，当年不仅刺激，赚得也多。说实话，现在没买卖可做，我还是图个乐子天天往海里钻。我这身流线蓝皮可不只是看着拉风，哪天想掰手腕的话，先热好身再说，伙计！",
        " Такая вот работа: и дух захватывало, и платили в те времена отлично. Честно говоря, я до сих пор ныряю ради азарта. Да и это обтекаемое синее тело не только для вида: захочешь побороться на руках, сначала разомнись как следует, дружище!",
    )
    add_tr(
        tr, f"{p}.about.1.0",
        "(Not to brag, but I haven't lost an arm-wrestling match since I hauled that orange shipping container onto the pier.)",
        "（不是吹牛，自从把那个橙色集装箱拖上栈桥以后，我掰手腕就没输过。）",
        "(Не хвастаюсь, но я ни разу не проигрывал на руках с тех пор, как затащил тот оранжевый контейнер на причал.)",
    )
    add_tr(tr, f"{p}.about.choice.containers", "Those huge shipping containers out on the deck… did you haul them up yourself?", "甲板上那几个大集装箱，都是你自己从海里捞上来的？", "Те огромные контейнеры на палубе… ты сам их вытащил?")
    add_tr(tr, f"{p}.about.choice.swordfish", "Such a long nose… are you a Latex Swordfish?", "你的鼻子真长……你是胶兽旗鱼吗？", "Какой длинный нос… ты латексная рыба-меч?")
    add_tr(tr, f"{p}.about.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.about.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["about"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.about.0.0", "happy", "wave", 1, 0.7, 130),
                part(f"{p}.about.0.1", "determined", "emphasis", 1, 0.75, 140, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.about.1.0", "happy", "whisper", 1, 0.65, 0)]),
        ],
        "choices": [
            choice("containers", f"{p}.about", "containers"),
            choice("swordfish", f"{p}.about", "swordfish"),
            choice("back", f"{p}.about", "return"),
            choice("goodbye", f"{p}.about", "goodbye"),
        ],
    }

    # containers
    add_tr(
        tr, f"{p}.containers.0.0",
        "Those big rusty boxes, the orange one and the blue one? Yep, spotted them half-buried out past the reef while scouting the seabed!",
        "你说那两个锈迹斑斑的大箱子，橙色和蓝色那俩？没错，我在礁石外头探海底的时候发现的，当时都埋了一半！",
        "Те большие ржавые коробки: оранжевую и синюю? Ага, заметил их полузарытыми за рифом, когда осматривал дно!",
    )
    add_tr(
        tr, f"{p}.containers.0.1",
        " Took a whole afternoon, two sharks, and a lot of rope to get them up onto the stilts, and half the deck groaned when we set them down. Still, you can't beat free dry storage out in the middle of the ocean.",
        " 整整折腾了一个下午，拉上两条鲨鱼、绑了一堆绳子才拖上木桩，放下来的时候半边甲板都在咯吱响。不过话说回来，大海上白捡的防潮仓库，可上哪儿找去。",
        " Ушел целый день, две акулы и куча канатов, чтобы втащить их на сваи, а настил аж застонал, когда мы их опустили. Зато сухой склад посреди океана, и совершенно бесплатно.",
    )
    add_tr(
        tr, f"{p}.containers.1.0",
        "(Still wish one of them had been full of canned oranges instead of wet cardboard and old lab crates.)",
        "（到现在我还盼着当初里面装的是橘子罐头，而不是湿纸箱和旧实验室木箱。）",
        "(До сих пор жаль, что внутри оказались мокрый картон и старые лабораторные ящики вместо консервированных апельсинов.)",
    )
    add_tr(tr, f"{p}.containers.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.containers.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["containers"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.containers.0.0", "happy", "bounce", 1, 0.7, 130),
                part(f"{p}.containers.0.1", "calm", "emphasis", 1, 0.7, 140, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.containers.1.0", "sad", "whisper", 1, 0.65, 0)]),
        ],
        "choices": [
            choice("back", f"{p}.containers", "return"),
            choice("goodbye", f"{p}.containers", "goodbye"),
        ],
    }

    # swordfish
    add_tr(
        tr, f"{p}.swordfish.0.0",
        "Sharp eyes, pal! Yeah, I'm a Latex Swordfish. This rostrum isn't just decoration: it's sharper than any spear and cuts water drag down to almost nothing.",
        "眼神真尖，伙计！没错，我是胶兽旗鱼。这根长吻可不只是摆设，比任何长矛都锋利，在水里几乎能把阻力削成零。",
        "Глаз-алмаз, дружище! Да, я латексная рыба-меч. Этот рострум не просто для красоты: он острее любого копья и сводит сопротивление воды почти к нулю.",
    )
    add_tr(
        tr, f"{p}.swordfish.0.1",
        " Give me open water and I can shoot forward and launch straight into the air like a rocket, so long as I'm starting from the water, anyway. On dry planks I mostly have to remember not to turn around too fast near the furniture.",
        " 只要下到水里，我就能猛冲出去，像火箭一样直接跃出水面——当然，得沾着水才行。在干木板上嘛，我主要得时刻记着别在家具旁边转身太快。",
        " В открытой воде я могу разогнаться и вылететь в воздух как ракета, разумеется, пока отталкиваюсь от воды. А на сухих досках главное: помнить не разворачиваться слишком резко рядом с мебелью.",
    )
    add_tr(
        tr, f"{p}.swordfish.1.0",
        "(Also impossible to hug someone from the front without jabbing them. Side-hugs only. Learned that the hard way.)",
        "（而且根本没法从正面抱人，一抱就戳着。只能侧着抱，这是吃过亏才学会的。）",
        "(А еще никого невозможно обнять спереди, чтобы не ткнуть. Только сбоку. Усвоено на горьком опыте.)",
    )
    add_tr(tr, f"{p}.swordfish.choice.explain_form", "Could I try becoming a Latex Swordfish myself?", "我也能试试变成胶兽旗鱼吗？", "А я сам могу попробовать стать латексной рыбой-мечом?")
    add_tr(tr, f"{p}.swordfish.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.swordfish.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["swordfish"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.swordfish.0.0", "happy", "emphasis", 1, 0.75, 130),
                part(f"{p}.swordfish.0.1", "calm", "wave", 1, 0.65, 140, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.swordfish.1.0", "curious", "whisper", 1, 0.65, 0)]),
        ],
        "choices": [
            choice("explain_form", f"{p}.swordfish", "explain_form", "can_reversible"),
            choice("back", f"{p}.swordfish", "return"),
            choice("goodbye", f"{p}.swordfish", "goodbye"),
        ],
    }

    # home
    add_tr(
        tr, f"{p}.home.0.0",
        "Hey, thanks! I know it's not one of those fancy beachfront villas from the old days, just stilts, salvaged timber, and a windmill we rigged up to catch the breeze, but being right over the ocean makes up for everything.",
        "嘿，谢啦！我知道这儿比不上过去那些气派的海滨别墅，不过是木桩、打捞回来的木料，外加我们凑合搭起来借风用的风车，可就建在大海正上方，这点比啥都强。",
        "Эй, спасибо! Знаю, это не какая-нибудь шикарная пляжная вилла из старых времен: просто сваи, выловленные доски да ветряк, который мы собрали ловить бриз, но жить прямо над океаном перекрывает все.",
    )
    add_tr(
        tr, f"{p}.home.0.1",
        " I'm not out here by myself, either. A few aquatic folks who share my love for the sea and scavenging moved in with me: two sharks handling the storage deck and the workshop, an orca over by the fishing deck, and an eel who claimed the blue dorm. Last I heard, a couple of rays were thinking about moving in too.",
        " 这儿也不是只有我一个人。几个跟我一样爱大海、爱打捞的水生伙伴也搬来一起住了：两条鲨鱼管着储物甲板和工坊，一头虎鲸常守在钓鱼区那边，还有一条鳗鱼占了蓝色宿舍。前阵子我听说，还有几条鳐鱼也正琢磨着搬过来呢。",
        " И я здесь не один. Ко мне подселились несколько водных ребят, которые так же любят море и поиски: две акулы присматривают за складом и мастерской, косатка держится у рыболовного края, а угорь облюбовал синюю спальню. Слышал, еще пара скатов подумывает перебраться к нам.",
    )
    add_tr(
        tr, f"{p}.home.1.0",
        "(If the rays actually move in, we're going to need another row of beds in the room, or a bigger awning…)",
        "（要是鳐鱼们真搬来了，宿舍里就得再加一排床，或者把外头的白篷再扩一圈。）",
        "(Если скаты и правда переедут, придется еще ряд кроватей в синей комнате или расширять навес.)",
    )
    add_tr(tr, f"{p}.home.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.home.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["home"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.home.0.0", "happy", "float", 1, 0.7, 130),
                part(f"{p}.home.0.1", "calm", "wave", 1, 0.65, 140, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.home.1.0", "curious", "whisper", 1, 0.65, 0)]),
        ],
        "choices": [
            choice("back", f"{p}.home", "return"),
            choice("goodbye", f"{p}.home", "goodbye"),
        ],
    }

    # past
    add_tr(
        tr, f"{p}.past.0.0",
        "You know, I do look back on my old human self now and then and get a little nostalgic.",
        "说实话，我偶尔确实会想起从前还是人类的自己，多少有点怀旧。",
        "Знаешь, я и правда иногда вспоминаю себя прежнего, человеком, и немного накатывает ностальгия.",
    )
    add_tr(
        tr, f"{p}.past.0.1",
        " Still, look at the bright side: I'm right by the ocean every single morning, I'm stronger than I ever was, and I can breathe underwater without watching an air gauge tick down. What more could an old diver ask for?",
        " 不过往好处看嘛：我现在每天一睁眼就在大海上，力气比从前大得多，潜到水下还能自由呼吸，再也不用死盯着氧气表往下掉了。一个老潜水员还能图什么呢？",
        " Но, с другой стороны, посмотри: каждое утро я прямо у океана, сил больше, чем когда-либо, и можно дышать под водой, не поглядывая на стрелку манометра. Чего еще желать старому ныряльщику?",
    )
    add_tr(
        tr, f"{p}.past.1.0",
        "(Okay, maybe one thing: eating a fresh orange without getting juice all over a three-foot nose. Still working on that technique.)",
        "（好吧，也许还差一件事：吃新鲜橙子的时候怎么才不会把果汁溅满三英尺长的鼻子。这手艺还在练。）",
        "(Ладно, пожалуй, одного все-таки не хватает: научиться есть свежий апельсин, не заляпав соком весь свой метровый нос. Все еще отрабатываю технику.)",
    )
    add_tr(tr, f"{p}.past.choice.diving_suit", "If you can breathe underwater now, why do you still wear that diving suit?", "既然你现在在水下本来就能呼吸，为什么还一直穿着那身潜水服？", "Если ты теперь можешь дышать под водой, зачем ты до сих пор носишь этот водолазный костюм?")
    add_tr(tr, f"{p}.past.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.past.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["past"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.past.0.0", "calm", "droop", 1, 0.65, 130),
                part(f"{p}.past.0.1", "happy", "wave", 1, 0.75, 140, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.past.1.0", "curious", "whisper", 1, 0.65, 0)]),
        ],
        "choices": [
            choice("diving_suit", f"{p}.past", "diving_suit"),
            choice("back", f"{p}.past", "return"),
            choice("goodbye", f"{p}.past", "goodbye"),
        ],
    }

    # diving_suit
    add_tr(
        tr, f"{p}.diving_suit.0.0",
        "Ah, this old thing… Well, like I said, I haven't forgotten who I used to be.",
        "啊，这身老伙计啊……就像我刚才说的，我一直没忘记自己从前是谁。",
        "А, этот старый костюм… Ну, как я и говорил, я не забыл, кем был раньше.",
    )
    add_tr(
        tr, f"{p}.diving_suit.0.1",
        " I know the suit doesn't actually do anything for me anymore—doesn't keep me warmer, doesn't help me swim faster. It's a memento from a very good friend from back in my diving days. Wearing it helps me hold onto the humanity I once had.",
        " 我知道穿着它对我现在一点实际用处都没有——既不更保暖，也游不更快。可这是当年潜水时一位非常要好的朋友留下的念想。穿着它，能让我一直记着自己曾经身为人的那份心气。",
        " Знаю, толку от него мне теперь никакого — ни греет лучше, ни плавать не помогает. Это память об одном очень хорошем друге еще со времен моих погружений. Пока я его ношу, проще помнить о том человеке, которым я когда-то был.",
    )
    add_tr(
        tr, f"{p}.diving_suit.1.0",
        "(Plus he always said the blue stripe down the leg looked sharp. Turns out it matches the latex too.)",
        "（而且他老说裤腿上那道蓝条纹特别帅气。结果现在和我的蓝色胶体还挺搭。）",
        "(К тому же он всегда говорил, что синяя полоса вдоль ноги выглядит стильно. А теперь она еще и под цвет латекса подходит.)",
    )
    add_tr(tr, f"{p}.diving_suit.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.diving_suit.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["diving_suit"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.diving_suit.0.0", "calm", "droop", 1, 0.65, 130),
                part(f"{p}.diving_suit.0.1", "calm", "emphasis", 1, 0.7, 150, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.diving_suit.1.0", "happy", "whisper", 1, 0.65, 0)]),
        ],
        "choices": [
            choice("back", f"{p}.diving_suit", "return"),
            choice("goodbye", f"{p}.diving_suit", "goodbye"),
        ],
    }

    # clue_disc
    add_tr(
        tr, f"{p}.clue_disc.0.0",
        "Whoa, you spun up that old disc on the desk? Man, I haven't listened to that log in years.",
        "哇，你把桌上那张旧光盘读出来啦？天哪，那段记录我都好多年没翻过了。",
        "Ого, ты запустил тот старый диск со стола в красной комнате? Эх, я сто лет не перечитывал эту запись.",
    )
    add_tr(
        tr, f"{p}.clue_disc.0.1",
        " Yeah, that was my old boat partner: the same friend who left me this diving suit before everything changed. He always worried I'd sink a boat chasing shiny scrap. Wherever he ended up, I hope he'd get a kick out of seeing me still pulling containers off the ocean floor.",
        " 没错，那就是我当年的船上搭档，就是那位在变故前把这身潜水服留给我的好朋友。他老担心我为了捞闪亮亮的废铁把船弄沉。不管他最后去了哪儿，希望他看见我现在还在海底捞集装箱，会乐得笑出来吧。",
        " Да, это мой старый напарник по катеру — тот самый друг, который оставил мне этот костюм до всех перемен. Он вечно переживал, что я потоплю лодку в погоне за блестящим хламом. Где бы он сейчас ни был, надеюсь, его бы повеселило, что я все еще таскаю контейнеры со дна.",
    )
    add_tr(tr, f"{p}.clue_disc.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.clue_disc.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["clue_disc"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.clue_disc.0.0", "surprised", "impact", 1, 0.8, 130),
                part(f"{p}.clue_disc.0.1", "calm", "wave", 1, 0.7, 150, beat=True),
            ]),
        ],
        "choices": [
            choice("back", f"{p}.clue_disc", "return"),
            choice("goodbye", f"{p}.clue_disc", "goodbye"),
        ],
    }

    # clue_manifest
    add_tr(
        tr, f"{p}.clue_manifest.0.0",
        "Hey, don't judge the tally sheet! When you haul up two whole shipping containers and a crate of lab cans, somebody's got to keep track.",
        "嘿，可别笑话那张清单！你一口气捞上来两个大集装箱外加一箱实验室罐头，总得有人记账吧。",
        "Эй, не суди строго наш список! Когда поднимаешь два целых контейнера и ящик лабораторных банок, кто-то должен вести учет.",
    )
    add_tr(
        tr, f"{p}.clue_manifest.0.1",
        " And for the record, that note at the bottom about \"five dented doorway slats\" is a total exaggeration. It was three slats, maybe four. Narrow doors are a hazard for swordfish, alright?",
        " 还有，我必须声明，最底下写着\"戳坏五块门框木条\"纯属夸大其词！明明只有三块，顶多四块。窄门对旗鱼来说本来就是安全隐患，好不好？",
        " И к сведению: приписка внизу про «пять пробитых дверных планок» — сплошное преувеличение. Их было три, ну, может, четыре. Узкие двери для рыбы-меча вообще опасны, ладно?",
    )
    add_tr(tr, f"{p}.clue_manifest.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.clue_manifest.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["clue_manifest"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.clue_manifest.0.0", "happy", "bounce", 1, 0.75, 130),
                part(f"{p}.clue_manifest.0.1", "determined", "emphasis", 1, 0.75, 140, beat=True),
            ]),
        ],
        "choices": [
            choice("back", f"{p}.clue_manifest", "return"),
            choice("goodbye", f"{p}.clue_manifest", "goodbye"),
        ],
    }

    # enrol
    add_tr(
        tr, f"{p}.enrol.0.0",
        "Life stays pretty lively out on the water, pal, always planks to patch, nets to check, and food to haul in. I only speak for our crew here on the shoal, mind you, not every swimmer in the sea.",
        "大海上的日子可闲不下来，伙计，总有木板要补、渔网要看、吃的要往回搬。不过先说好，我只代表咱们浅滩这帮同伴，可代表不了大海里所有的水生胶兽。",
        "Жизнь на воде не стоит на месте, дружище: вечно надо чинить доски, проверять сети и таскать еду. Учти только, я говорю лишь за нашу команду на Отмели, а не за всех обитателей моря.",
    )
    add_tr(
        tr, f"{p}.enrol.0.1",
        " If you're up for helping out and sharing the deck, we'd love to have you on board!",
        " 要是你愿意搭把手、跟大伙儿共享这片甲板，随时欢迎上船！",
        " Если готов помогать и делить с нами палубу, добро пожаловать на борт!",
    )
    add_tr(
        tr, f"{p}.enrol.1.0",
        "Life moves pretty quick out on the ocean, and I haven't made a proper run to the shore in ages, so an extra pair of hands around here would be huge!",
        "海上的活儿可不少，而且我好久没正经去岸边跑一趟了，多一双帮手简直太省事了！",
        "Дел в океане всегда хватает, а я сто лет нормально не выбирался на берег, так что лишняя пара рук нам очень кстати!",
    )
    add_tr(
        tr, f"{p}.enrol.1.1",
        " We welcome humans and latexes alike, no pressure at all. You can join the crew just as you are, or if you're curious what it's like to swim as a Latex Swordfish, I can share the form temporarily or for good. Your call, champ.",
        " 咱们这儿不管人类还是胶兽都欢迎，一点压力都没有。你可以就按现在的样子入伙；要是你好奇当一条胶兽旗鱼是什么感觉，我也可以把形态分享给你，暂时的或者永久的都行。全看你的，伙计。",
        " Мы принимаем и людей, и латексных без всякого давления. Можешь вступить в команду как есть, а если любопытно поплавать в облике латексной рыбы-меча — могу поделиться формой на время или навсегда. Решать тебе, дружище.",
    )
    add_tr(
        tr, f"{p}.enrol.2.0",
        "Hold up a bit, pal: the crew's still a little rattled right now, so I can't sign you onto the shoal just yet. Give folks a little space to cool off, and we'll talk about bunking down once things settle.",
        "先等等，伙计。船员们这会儿心神还有点不宁，我暂时没法把你记进浅滩名册里。先给大家留点喘口气的空间，等风浪平息下来咱们再聊住下的事。",
        "Притормози немного, дружище: команда сейчас не в духе, так что я пока не могу записать тебя к нам. Дай ребятам немного остыть, и вернемся к разговору о койке, когда все уляжется.",
    )
    add_tr(tr, f"{p}.enrol.choice.join_latex", "Count me in, I'll join the crew as I am.", "算我一个，我以现在的形态入伙。", "Я в деле, присоединюсь к команде как есть.")
    add_tr(tr, f"{p}.enrol.choice.join_human", "I'll help out as much as I can, sign me in as a human!", "我会尽力帮忙的，我以人类身份入伙！", "Буду помогать чем смогу, записывай меня человеком!")
    add_tr(tr, f"{p}.enrol.choice.explain_form", "What's it like becoming a Latex Swordfish?", "变成胶兽旗鱼是什么体验？", "Каково это — стать латексной рыбой-мечом?")
    add_tr(tr, f"{p}.enrol.choice.back", "Something else…", "聊点别的……", "О другом…")
    nodes["enrol"] = {
        "lines": [
            line("spoken", "transformed_join", [
                part(f"{p}.enrol.0.0", "calm", "wave", 1, 0.65, 130),
                part(f"{p}.enrol.0.1", "happy", "bounce", 1, 0.75, 140, beat=True),
            ]),
            line("spoken", "human_join", [
                part(f"{p}.enrol.1.0", "happy", "bounce", 1, 0.7, 130),
                part(f"{p}.enrol.1.1", "calm", "emphasis", 1, 0.7, 140, beat=True),
            ]),
            line("spoken", "cannot_join", [
                part(f"{p}.enrol.2.0", "determined", "pulse", 1, 0.75, 150),
            ]),
        ],
        "choices": [
            choice("join_latex", f"{p}.enrol", "welcome_latex", "transformed_join", "join"),
            choice("join_human", f"{p}.enrol", "welcome_human", "human_join", "join"),
            choice("explain_form", f"{p}.enrol", "explain_form", "can_reversible"),
            choice("back", f"{p}.enrol", "return"),
        ],
    }

    # explain_form
    add_tr(
        tr, f"{p}.explain_form.0.0",
        "As a Latex Swordfish, you get a long, spear-sharp rostrum for hunting fish, fast swimming in deep water, underwater breathing, and the ability to launch yourself out of the waves like a rocket.",
        "成了胶兽旗鱼，你会长出一根像长矛一样锋利的长吻方便捕鱼，在水里游得飞快、能直接呼吸，还能像火箭一样从海浪里跃向空中。",
        "Став латексной рыбой-мечом, ты получишь острый как копье рострум для охоты на рыбу, высокую скорость под водой, подводное дыхание и способность выстреливать из волн как ракета.",
    )
    add_tr(
        tr, f"{p}.explain_form.0.1",
        " Breathing regular air can feel a little weird at first, though you get the hang of it quick—and you'll want to take doorways wide so you don't clip the frames!",
        " 刚开始呼吸普通空气可能会有点别扭，不过很快就能适应，还有过门框记得拐大弯，免得把木框给戳了！",
        " Дышать обычным воздухом поначалу бывает непривычно, но быстро приноровишься — и в дверные проемы заходи пошире, чтобы не задевать косяки!",
    )
    add_tr(
        tr, f"{p}.explain_form.0.2",
        " Go reversible and you can switch back to human whenever you want. Go permanent and the ocean coat is yours for good. Staying human is completely fine too—same spot by the campfire either way.",
        " 选可逆的话，随时可以变回人类；选永久的话，这身海蓝形态就一直跟着你了。保持人类身份也完全没问题，不管选哪种，篝火旁都有你的位置。",
        " Выберешь обратимый вариант — сможешь вернуться в человеческий вид позже. Выберешь постоянный — останешься таким навсегда. Остаться человеком тоже отлично: место у костра за тобой в любом случае.",
    )
    add_tr(tr, f"{p}.explain_form.choice.reversible", "Alright, I'm in, let's try the reversible swordfish form and join!", "行，我加入，先试试可逆的旗鱼形态吧！", "Ладно, я в деле: давай попробуем обратимую форму рыбы-меча, и я с вами!")
    add_tr(tr, f"{p}.explain_form.choice.permanent_menu", "Make it permanent, I want to be a swordfish for good and join!", "永久转化吧，我想一直作为旗鱼留在这儿！", "Делай навсегда: хочу остаться рыбой-мечом и быть в команде!")
    add_tr(tr, f"{p}.explain_form.choice.join_human", "I'll stay human and join the crew.", "我还是保持人类身份入伙吧。", "Пожалуй, останусь человеком и вступлю в команду.")
    add_tr(tr, f"{p}.explain_form.choice.back", "Something else…", "聊点别的……", "О другом…")
    nodes["explain_form"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.explain_form.0.0", "happy", "emphasis", 1, 0.75, 130),
                part(f"{p}.explain_form.0.1", "curious", "wave", 1, 0.65, 120),
                part(f"{p}.explain_form.0.2", "calm", "emphasis", 1, 0.7, 150, beat=True),
            ]),
        ],
        "choices": [
            choice("reversible", f"{p}.explain_form", "welcome_copy", "can_reversible", "reversible"),
            choice("permanent_menu", f"{p}.explain_form", "confirm_permanent", "can_permanent"),
            choice("join_human", f"{p}.explain_form", "welcome_human", "human_join", "join"),
            choice("back", f"{p}.explain_form", "return"),
        ],
    }

    # confirm_permanent
    add_tr(
        tr, f"{p}.confirm_permanent.0.0",
        "Hold on a second, champ, let's be 100% sure before we do this. Making it permanent means this swordfish body stays yours for good.",
        "先等一秒，伙计。动手前咱们得百分之百确认好。选了永久，这具旗鱼身体今后就永远属于你了。",
        "Погоди секунду, дружище — давай убедимся на все сто. Постоянный вариант значит, что тело рыбы-меча останется твоим навсегда.",
    )
    add_tr(
        tr, f"{p}.confirm_permanent.0.1",
        " You can still pick the reversible option or stay human and keep your bunk here either way. Are you sure you want to make it permanent?",
        " 你现在改选可逆形态或者保持人类都来得及，反正铺位照样归你。确定要永久转化吗？",
        " Ты все еще можешь выбрать обратимый вариант или остаться человеком — койка за тобой в любом случае. Уверен, что хочешь навсегда?",
    )
    add_tr(tr, f"{p}.confirm_permanent.choice.confirm", "Yes, I'm sure. Make it permanent.", "是的，我确定，永久转化吧。", "Да, я уверен. Делай навсегда.")
    add_tr(tr, f"{p}.confirm_permanent.choice.rethink", "Let me think it over again.", "让我再考虑一下。", "Дай еще подумать.")
    nodes["confirm_permanent"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.confirm_permanent.0.0", "determined", "pulse", 1, 0.85, 160),
                part(f"{p}.confirm_permanent.0.1", "determined", "emphasis", 1, 0.8, 150, beat=True),
            ]),
        ],
        "choices": [
            choice("confirm", f"{p}.confirm_permanent", "welcome_permanent", "can_permanent", "permanent"),
            choice("rethink", f"{p}.confirm_permanent", "explain_form"),
        ],
    }

    # welcome states
    def add_welcome_choices(node_name: str):
        add_tr(tr, f"{p}.{node_name}.choice.back", "Something else…", "聊点别的……", "О другом…")
        add_tr(tr, f"{p}.{node_name}.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
        return [
            choice("back", f"{p}.{node_name}", "return"),
            choice("goodbye", f"{p}.{node_name}", "goodbye"),
        ]

    add_tr(
        tr, f"{p}.welcome_human.0.0",
        "Yo, I like that spirit! Alright, champ, let's get you settled in. The chest near the campfire is for the whole crew: we all share what's in it, and you're welcome to take or add to it anytime.",
        "哟，我就喜欢你这股劲头！行，伙计，咱们安顿下来吧。篝火旁那个箱子是全船员共用的，大伙儿一起分，你随时可以取用或者往里添东西。",
        "О, вот это настрой! Ладно, дружище, устраивайся. Сундук у костра — общий для всей команды: все делятся тем, что там лежит, и ты можешь брать или класть туда припасы в любое время.",
    )
    add_tr(
        tr, f"{p}.welcome_human.0.1",
        " Some of us love fish, raw or cooked, and others swear by dried kelp. Oh, and if you ever bring back spare oranges from the shore… oof, I'd owe you big time. Just please don't go peeking into the other chests; folks here still keep personal belongings in their own boxes.",
        " 我们当中有人爱吃生鱼熟鱼，也有人离不开烤海带。噢，还有，你要是哪天从岸边顺手带回点多余的橙子……哇，那我可欠你大人情了。只是千万别去翻别的箱子，大家各自的私人物品还是自己收着的。",
        " Кто-то у нас обожает рыбу, сырую или жареную, а кто-то жить не может без сушеной ламинарии. О, и если когда-нибудь принесешь с берега лишних апельсинов… ух, я в долгу не останусь. Только, пожалуйста, не заглядывай в остальные сундуки, там у ребят личные вещи.",
    )
    add_tr(
        tr, f"{p}.welcome_human.1.0",
        "(Yes! A human crewmate! That means shore trips for oranges just got way easier!)",
        "（太好了！有人类船员了！以后跑岸边摘橙子可方便多了！）",
        "(Отлично! Человек в команде! Значит, вылазки на берег за апельсинами станут куда проще!)",
    )
    nodes["welcome_human"] = {
        "lines": [
            line("spoken", "always", [
                part(f"{p}.welcome_human.0.0", "happy", "bounce", 1, 0.75, 130),
                part(f"{p}.welcome_human.0.1", "calm", "wave", 1, 0.7, 150, beat=True),
            ]),
            line("mental", "always", [part(f"{p}.welcome_human.1.0", "happy", "whisper", 1, 0.65, 0)]),
        ],
        "choices": add_welcome_choices("welcome_human"),
    }

    add_tr(
        tr, f"{p}.welcome_latex.0.0",
        "Welcome to the crew! Grab an open bed in the dorm, use the shared chest near the campfire whenever you need supplies, and keep your hands out of people's personal chests. Glad to have you on the shoal!",
        "欢迎入伙！宿舍里空着的床随便选，缺物资随时用篝火边的公共箱子，别乱翻别人的私人箱子就行。很高兴你能留在浅滩！",
        "Добро пожаловать в команду! Занимай свободную кровать в спальне, пользуйся общим сундуком у костра, когда понадобятся припасы, и не трогай личные ящики. Рады видеть тебя на Отмели!",
    )
    nodes["welcome_latex"] = {
        "lines": [line("spoken", "always", [part(f"{p}.welcome_latex.0.0", "happy", "bounce", 1, 0.75, 150)])],
        "choices": add_welcome_choices("welcome_latex"),
    }

    for wnode in ("welcome_copy", "welcome_permanent"):
        add_tr(
            tr, f"{p}.{wnode}.0.0",
            "Haha, that's what I like to hear! Easy now, let the rostrum settle properly, it'll be your best tool for catching fish out past the stilts. That navy-blue coat's going to make you fly through the water, and breathing down below will feel like second nature.",
            "哈哈，我就爱听这话！慢点来，让长吻先稳稳长好，以后在木桩外头捕鱼全靠它了。这身海蓝胶体能让你在水里游得飞快，水下呼吸也会像本能一样自然。",
            "Ха-ха, вот это по-нашему! Потише, дай роструму как следует сформироваться, он станет твоим главным инструментом для ловли рыбы за сваями. В этой темно-синей шкуре ты будешь летать под водой, а дышать там станет проще простого.",
        )
        add_tr(
            tr, f"{p}.{wnode}.0.1",
            " Regular air might feel a little funny for the first minute, so take it slow: you'll get the hang of it in no time. And hey, remember to share the chest by the campfire and keep out of personal boxes. Welcome aboard, champ!",
            " 头一分钟呼吸普通空气可能会有点怪，别急，很快就能习惯。对了，篝火边的箱子大家共用，私人箱子别乱翻。欢迎上船，伙计！",
            " Обычный воздух первую минуту может казаться странным, не торопись: быстро привыкнешь. И помни: общий сундук у костра, личные ящики не трогаем. Добро пожаловать на борт, дружище!",
        )
        add_tr(
            tr, f"{p}.{wnode}.1.0",
            "(Awesome! Another swordfish on deck! Workshop shark is gonna lose his mind when both of us walk through the narrow doorway at once.)",
            "（太酷了！甲板上又多了一条旗鱼！等咱俩一块挤过那扇窄门，工坊鲨鱼怕是要抓狂了。）",
            "(Круто! Еще одна рыба-меч на палубе! Акула в мастерской с ума сойдет, когда мы оба одновременно зайдем в узкую дверь.)",
        )
        nodes[wnode] = {
            "lines": [
                line("spoken", "always", [
                    part(f"{p}.{wnode}.0.0", "happy", "bounce", 1, 0.75, 130),
                    part(f"{p}.{wnode}.0.1", "happy", "wave", 1, 0.75, 150, beat=True),
                ]),
                line("mental", "always", [part(f"{p}.{wnode}.1.0", "happy", "whisper", 1, 0.65, 0)]),
            ],
            "choices": add_welcome_choices(wnode),
        }

    # membership, confirm_leave, left, goodbye
    add_tr(
        tr, f"{p}.membership.0.0",
        "You're part of The Shoal crew. The shared chest near the campfire and any free bed in the blue dorm are yours to use; private crates and bedside storage stay with their owners. If the tide ever pulls you somewhere else, just let me know: no hard feelings, and your body stays your own.",
        "你现在是咱们浅滩的一员了。篝火边的公共箱子和蓝色宿舍里的空床随时能用，私人木箱和床头储物归各自保管。 哪天要是海潮把你带去别的地方，跟我说一声就行，绝不怪你，你的身体也依然属于你自己。",
        "Ты часть команды Отмели. Общий сундук у костра и любая свободная кровать в синей спальне — в твоем распоряжении; личные ящики и тумбочки остаются за их хозяевами. Если течение когда-нибудь унесет тебя в другие края, просто скажи: никаких обид, и твое тело остается твоим.",
    )
    add_tr(tr, f"{p}.membership.choice.leave_menu", "I need to leave The Shoal and move on.", "我得离开浅滩继续赶路了。", "Мне нужно покинуть Отмель и плыть дальше.")
    add_tr(tr, f"{p}.membership.choice.back", "Something else…", "聊点别的……", "О другом…")
    nodes["membership"] = {
        "lines": [line("spoken", "always", [part(f"{p}.membership.0.0", "calm", "wave", 1, 0.65, 130)])],
        "choices": [
            choice("leave_menu", f"{p}.membership", "confirm_leave", "member"),
            choice("back", f"{p}.membership", "return"),
        ],
    }

    add_tr(
        tr, f"{p}.confirm_leave.0.0",
        "Sure you're ready to shove off, pal? Once I take your name off the crew list, the shared chest won't be open to you anymore, though you can still stop by the pier to visit anytime.",
        "确定要起锚出发了吗，伙计？把你从船员名单划掉以后，公共补给箱就不能随便取用了，不过以后随时还能来栈桥串门。",
        "Точно готов отчаливать, дружище? Как вычеркну тебя из списка команды, общий сундук для тебя закроется, но заглядывать в гости на причал можно в любое время.",
    )
    add_tr(tr, f"{p}.confirm_leave.choice.confirm_leave", "Yes, I'm heading out.", "确定，我要出发了。", "Да, я отправляюсь в путь.")
    add_tr(tr, f"{p}.confirm_leave.choice.back", "Something else…", "聊点别的……", "О другом…")
    nodes["confirm_leave"] = {
        "lines": [line("spoken", "always", [part(f"{p}.confirm_leave.0.0", "sad", "droop", 1, 0.7, 150)])],
        "choices": [
            choice("confirm_leave", f"{p}.confirm_leave", "left", "member", "leave"),
            choice("back", f"{p}.confirm_leave", "return"),
        ],
    }

    add_tr(
        tr, f"{p}.left.0.0",
        "Fair winds and calm seas, champ. Drop by the deck whenever you're swimming past, we'll keep a seat dry for you.",
        "祝你一路顺风顺水，伙计。哪天游过这片海域记得上甲板坐坐，我们给你留着干爽位子。",
        "Попутного ветра и спокойного моря, дружище. Заглядывай на палубу, если будешь проплывать мимо, прибережем тебе сухое местечко.",
    )
    add_tr(tr, f"{p}.left.choice.back", "Something else…", "聊点别的……", "О другом…")
    add_tr(tr, f"{p}.left.choice.goodbye", "I'll let you get back to your work. See you!", "不打扰你忙活了，回头见！", "Не буду отвлекать тебя от дел. Увидимся!")
    nodes["left"] = {
        "lines": [line("spoken", "always", [part(f"{p}.left.0.0", "calm", "whisper", 1, 0.65, 140)])],
        "choices": [
            choice("back", f"{p}.left", "return"),
            choice("goodbye", f"{p}.left", "goodbye"),
        ],
    }

    add_tr(
        tr, f"{p}.goodbye.0.0",
        "Hope you come visit us again anytime, pal! It'll be nice having you back on the deck.",
        "欢迎随时再来玩，伙计！能再在甲板上见到你肯定挺开心的。",
        "Надеюсь, ты еще заглянешь к нам в гости, дружище! Будем рады снова видеть тебя на палубе.",
    )
    add_tr(
        tr, f"{p}.goodbye.1.0",
        "You know where we live, champ! Make sure to bring back one big ol' salmon for dinner!",
        "你知道咱家在哪儿，伙计！记得拎条肥厚的大鲑鱼回来当晚饭！",
        "Ты знаешь, где нас найти, дружище! Не забудь притащить к ужину здоровенного лосося!",
    )
    add_tr(tr, f"{p}.goodbye.choice.close", "See you!", "回头见！", "Увидимся!")
    nodes["goodbye"] = {
        "lines": [
            line("spoken", "stranger", [part(f"{p}.goodbye.0.0", "calm", "wave", 1, 0.6, 120)]),
            line("spoken", "member", [part(f"{p}.goodbye.1.0", "happy", "bounce", 1, 0.7, 120)]),
        ],
        "choices": [choice("close", f"{p}.goodbye", "root", "always", "close")],
    }

    # Part II: Four Shoal Residents
    residents = [
        (
            "fishing",
            ("Hey there. Watch where you put your feet on the trapdoors, they look flimsy, but they hold even my weight just fine.",
             "嗨。走那些活板门时留意脚下，看着单薄，其实连我都踩得住。",
             "Привет. Смотри под ноги на люках: с виду хлипкие, но держат даже мой вес."),
            ("(Salmon's biting, water's calm, sun's warm on my back. Best seat in the whole ocean!)",
             "（鲑鱼肯咬钩，水面风平浪静，太阳晒得后背暖洋洋的。全大海最舒服的位子就在这儿！）",
             "(Лосось клюет, вода спокойная, солнце греет спину. Лучшее место во всем океане!)"),
            ("You handle the fishing and the cooking pot out here?",
             "外头的钓鱼和汤锅这边平时都是你在打理吗？",
             "Ты здесь заправляешь рыбалкой и котелком на палубе?"),
            ("Most days, yeah. The Scavenger dives for the big heavy scrap, the sharks sort and fix it, and I make sure nobody forgets to eat dinner. We grow greens right here in the deck planters, and we keep wheat up on the red roof. The only thing we can't grow out on the stilts is oranges, so if you ever bring a few from the shore, you'll make the boss's whole week.",
             "大多数时候是我管。拾荒者负责下水捞大件废铁，两条鲨鱼管分拣和修理，我则盯着大家别忘了吃晚饭。 甲板花槽里种着蔬菜，红屋顶上还种了小麦。木桩上唯一种不出来的就是橙子，所以你要是哪天从岸边带回几个，老大能高兴一整周。",
             "В основном да. Старьевщик ныряет за крупным ломом, акулы его разбирают и чинят, а я слежу, чтобы никто не забывал поужинать. Зелень мы растим прямо здесь в ящиках на палубе, а пшеницу — на красной крыше. Единственное, что на сваях не вырастишь, — апельсины, так что если принесешь с берега пару штук, босс будет счастлив всю неделю."),
        ),
        (
            "dorm",
            ("Oh… hey. Come on in, just try not to drip saltwater all over the rugs if you just climbed out of the sea. Five beds in here, nice cool walls, and the best ocean view through the windows at sunset. Looking for a spot to crash?",
             "噢……嗨。请进，如果你刚从海里爬上来，尽量别把海水滴满地毯就行。 这屋里有五张床，墙面凉爽，日落时透过窗户看海景最棒。是来找地方歇脚的吗？",
             "О… привет. Заходи, только постарайся не накапать соленой водой на ковер, если только что вылез из моря. Тут пять кроватей, прохладные стены и лучший вид на океан на закате. Ищешь место передохнуть?"),
            ("(Lanterns are warm, floorboards are smooth, and nobody's hammering outside right now. Prime napping conditions!)",
             "（提灯暖烘烘的，地板滑溜溜的，外头这会儿也没人叮当敲锤子。绝佳的午睡条件！）",
             "(Фонари теплые, пол гладкий, и снаружи сейчас никто не стучит молотком. Идеальные условия для сна!)"),
            ("Is it noisy sleeping right over the waves?",
             "就睡在海浪正上方，晚上不会吵吗？",
             "Не шумно спать прямо над волнами?"),
            ("Not once you get used to it, the waves hitting the stilts underneath actually put me right to sleep. Just a heads-up if you stay the night: those oranges on the nightstand belong to the Scavenger's emergency stash. Touch those without asking and you'll hear about it before breakfast.",
             "习惯了就不吵了，底下的浪头拍打木桩的声音，听着反而特别催眠。 不过要是留宿的话提醒你一句：床头柜上那几个橙子是拾荒者的应急存货。没打招呼就动它们，早饭前你就得听他念叨了。",
             "Не-а, когда привыкнешь, плеск волн о сваи внизу наоборот отлично убаюкивает. Только предупреждаю, если останешься на ночь: апельсины на тумбочке — неприкосновенный запас Старьевщика. Тронешь без спросу, услышишь об этом еще до завтрака."),
        ),
        (
            "storage",
            ("Watch your elbow near the cardboard boxes, the sea breeze already blew half my labels into the water once today. If you're looking for shared food or supplies, that's in the chest over there. The big containers and crates on this side are salvage we're still sorting through.",
             "胳膊肘留神旁边那堆纸箱，海风今天已经把我一半标签吹海里去了。 如果你是找公用食物或补给，箱子在那边。这边的大集装箱和木箱都是还在分拣的打捞货。",
             "Осторожнее локтями у картонных коробок — морской бриз сегодня уже унес половину моих ярлыков в воду. Если ищешь общую еду или припасы, они в сундуке. А большие контейнеры и ящики здесь — это еще неразобранные находки со дна."),
            ("(Twelve cans of soup, four bent pipes, one swivel chair with a squeaky wheel… why did we haul an office chair out to the middle of the ocean?)",
             "（十二罐汤、四根弯铁管、一把轮子嘎吱响的办公转椅……我们当初干嘛非要把办公椅拖到大海正中央来？）",
             "(Двенадцать банок супа, четыре гнутые трубы, одно кресло на колесиках со скрипучим роликом… зачем вообще мы притащили офисное кресло посреди океана?)"),
            ("Does the Scavenger really pull all this stuff up from the ocean floor?",
             "这些东西真全是拾荒者从海底一件件捞上来的？",
             "Старьевщик правда сам вытаскивает все это со дна?"),
            ("Every bit of it. He spots a rusty box half a mile out, dives down, and shows up ten minutes later asking us to help haul it onto the deck. Half the time it's junk, half the time it's good timber, nails, or dry canisters that keep this whole shoal standing. Just don't let him hear you call the containers rusty: he's ridiculously proud of them.",
             "全是他弄上来的。他在半英里外瞥见个生锈箱子就会扎下去，十分钟后冒出头喊我们帮忙往甲板上拖。 一半时候是破烂，另一半时候是好木料、钉子或者干爽的罐子，整座浅滩能立在这儿全靠它们。可别当着他的面说集装箱生锈了，他对那俩箱子得意得不行。",
             "Все до единой штуковины. Заметит ржавый ящик за полмили, нырнет, и через десять минут уже зовет нас тащить его на палубу. Половина оказывается хламом, а другая половина — крепкие доски, гвозди и сухие банки, на которых вся наша Отмель и держится. Только при нем не называй контейнеры ржавыми, он ими ужасно гордится."),
        ),
        (
            "workshop",
            ("Step carefully: wood shavings on the floor, and I just put a fresh edge on the saw. Need the crafting table or the furnace? Go right ahead, just leave the water cauldron alone; I use it to cool hot iron and rinse salt off salvaged tools.",
             "脚下当心，地板上全是木屑，锯子我也刚磨出新刃。 要用工作台还是熔炉？尽管用，别动那口水锅就行，我拿它给热铁淬火，顺便冲掉捞上来的工具上的盐分。",
             "Ступай осторожнее, на полу стружка, и я только что наточил пилу. Нужен верстак или печь? Пользуйся на здоровье, только котел с водой не трогай, я в нем остужаю железо и смываю соль с выловленных инструментов."),
            ("(Wood planks: patched. Windmill blades: tightened. Doorframe next to the dorm: still has a neat swordfish-shaped dent in it. I'm leaving that one as a warning.)",
             "（木板：补好了。风车叶片：拧紧了。宿舍旁边的门框：还留着一个规规矩矩的旗鱼吻戳印。这块我就不补了，留着当警示牌。）",
             "(Доски залатаны. Лопасти ветряка подтянуты. Дверной косяк у спальни: до сих пор с аккуратной вмятиной в форме носа рыбы-меча. Оставлю как предупреждение.)"),
            ("Must take a lot of work keeping a wooden house standing in saltwater.",
             "让这么一座木屋稳稳立在海水里，平时肯定要费不少功夫吧。",
             "Наверное, непросто держать деревянный дом в соленой воде?"),
            ("You have no idea. Salt eats nails, wind rattles the windmill blades, and someone around here keeps forgetting his nose sticks out three feet in front of his face every time he turns around indoors. Still, I'd rather plane pine boards out in the sea breeze than sit in some dusty inland ruin. And the view of the crop planters through the back window isn't bad, either.",
             "你都想象不到。海水锈钉子，海风吹得风车叶片嘎嘎响，而且这屋里还有某位仁兄每次在室内转身，老忘了自己脸前面还伸出去三英尺长的鼻子。 不过话说回来，比起待在内陆满是灰的废墟里，我还是更乐意吹着海风刨木板。况且从后窗还能望见外头的菜畦，风景也不赖。",
             "Ты даже не представляешь. Соль разъедает гвозди, ветер расшатывает лопасти ветряка, а кое-кто здесь вечно забывает, что у него перед лицом торчит метровый нос, когда разворачивается в комнате. И все-таки я лучше буду строгать сосновые доски на морском бризе, чем сидеть в пыльных руинах на суше. Да и вид на грядки из заднего окна вполне себе ничего."),
        ),
    ]

    for role, spoken, mental, q, ans in residents:
        rnode = f"resident_{role}"
        fnode = f"resident_{role}_0"
        add_tr(tr, f"{p}.{rnode}.0.0", *spoken)
        add_tr(tr, f"{p}.{rnode}.1.0", *mental)
        add_tr(tr, f"{p}.{rnode}.choice.topic_0", *q)
        add_tr(tr, f"{p}.{rnode}.choice.close", "See you around.", "待会儿见。", "Еще увидимся.")
        nodes[rnode] = {
            "lines": [
                line("spoken", "always", [part(f"{p}.{rnode}.0.0", "calm", "wave", 1, 0.65, 130)]),
                line("mental", "always", [part(f"{p}.{rnode}.1.0", "curious", "whisper", 1, 0.65, 0)]),
            ],
            "choices": [
                choice("topic_0", f"{p}.{rnode}", fnode),
                choice("close", f"{p}.{rnode}", "root", "always", "close"),
            ],
        }

        add_tr(tr, f"{p}.{fnode}.0.0", *ans)
        add_tr(tr, f"{p}.{fnode}.choice.back", "Back.", "聊点别的。", "Назад.")
        add_tr(tr, f"{p}.{fnode}.choice.close", "Thanks, catch you later.", "谢啦，回头见。", "Спасибо, увидимся позже.")
        nodes[fnode] = {
            "lines": [
                line("spoken", "always", [part(f"{p}.{fnode}.0.0", "calm", "emphasis", 1, 0.7, 140)]),
            ],
            "choices": [
                choice("back", f"{p}.{fnode}", rnode),
                choice("close", f"{p}.{fnode}", "root", "always", "close"),
            ],
        }

    profile = {
        "template": "the_shoal",
        "species": "changed_synergy:latex_swordfish",
        "trait": "PLAYFUL",
        "chief": True,
        "equipment": ["changed:wetsuit"],
        "nodes": nodes,
    }
    return profile, tr


def build_camp_voices():
    tr: dict[str, tuple[str, str, str]] = {}
    add_tr(tr, "camp.changed_synergy.home.shoal", "the Shoal Household", "浅滩社区", "община Отмели")

    lines: dict[str, list[dict]] = {}

    def add_voice_pair(voice: str, topic: str, emo0: str, eff0: str, p0: int, i0: float,
                       t0: tuple[str, str, str], t1: tuple[str, str, str] | None = None):
        k = f"camp.changed_synergy.{voice}.{topic}"
        add_tr(tr, f"{k}.0", *t0)
        parts = [cpart(f"{k}.0", emo0, eff0, p0, i0)]
        if t1 is not None:
            add_tr(tr, f"{k}.1", *t1)
            parts.append(cpart(f"{k}.1", "calm", "none", 0, 0.2))
        lines[f"{voice}.{topic}"] = parts

    # Scavenger (Chief) — 23 topics matching steward
    add_voice_pair("scavenger", "greeting", "happy", "wave", 140, 0.7,
                   ("Deck planks are holding steady today!", "今天的甲板木板稳得很！", "Настил сегодня держится отлично!"),
                   (" Need a hand with crew supplies, or just checking how the shoal is running?", " 要看看船员物资情况，还是打听浅滩上的事务？", " Хочешь проверить запасы команды или узнать, как дела на Отмели?"))
    add_voice_pair("scavenger", "food_low", "sad", "droop", 150, 0.7,
                   ("The shared chest out by the storage deck is getting pretty thin.", "储物甲板旁边的公共补给箱快见底了。", "Общий сундук у грузовой палубы заметно опустел."),
                   (" Swimming and hauling scrap burns plenty of energy, so extra fish, kelp, or trail food would help the crew.", " 天天潜水和拖废铁很费体力，添点鱼、海带或者干粮会帮大忙。", " Плавание и таскание лома отнимают кучу сил, так что лишняя рыба, ламинария или пайки выручат команду."))
    add_voice_pair("scavenger", "food_enough", "calm", "emphasis", 140, 0.65,
                   ("Between the campfire pot, the planters, and the shared chest on deck, nobody's going hungry right now.", "火堆上的汤锅、甲板菜槽加上公共箱子里的存货，大家这会儿吃喝不愁。", "С котелком у костра, грядками и общим сундуком на палубе никто сейчас не голодает."))
    add_voice_pair("scavenger", "food_plenty", "happy", "bounce", 140, 0.75,
                   ("Shared chest is packed solid, and there's fresh fish bubbling by the campfire!", "公共箱子塞得满满当当，篝火旁也正煮着鲜鱼！", "Общий сундук забит доверху, а у костра булькает свежая рыба!"),
                   (" Pull up a log seat whenever you want to grab a hot bite.", " 想吃热乎的随时挑个木墩坐下。", " Присаживайся на чурбак у огня, когда захочешь перекусить."))
    add_voice_pair("scavenger", "safe", "calm", "float", 140, 0.65,
                   ("Water around the stilts is clear, and nothing hostile's hanging around the piers.", "木桩周围的水面干干净净，栈桥边上没有任何捣乱的家伙。", "Вода вокруг свай спокойная, и у причала никого враждебного нет."))
    add_voice_pair("scavenger", "danger", "determined", "pulse", 150, 0.75,
                   ("Heads up—something hostile drifted too close to the stilts or outer piers.", "留神——有敌对家伙游到木桩或外圈栈桥附近了。", "Осторожнее — кто-то враждебный подплыл слишком близко к сваям и причалу."),
                   (" Let's clear the water around the deck before anyone tries to settle in.", " 咱们先把甲板四周清理干净再说。", " Надо прогнать незваных гостей от палубы, прежде чем отдыхать."))
    add_voice_pair("scavenger", "remember", "happy", "wave", 140, 0.65,
                   ("The crew doesn't forget who swims supplies out over open water for us.", "船员们可都记得是谁冒着风浪把补给送到浅滩来的。", "Команда не забывает тех, кто приносит нам припасы посреди океана."),
                   (" You always have a dry seat under the awning—empty-handed or not.", " 哪怕两手空空来串门，白篷底下也永远给你留着干爽位子。", " Под навесом для тебя всегда найдется сухое место — с грузом или без."))
    add_voice_pair("scavenger", "requests", "curious", "wave", 140, 0.65,
                   ("Crew left a quick tally of what the shoal needs right now.", "船员们刚在清单上记了几项浅滩眼下缺的物资。", "Ребята оставили пару заметок, чего не хватает Отмели."),
                   (" Pick whatever suits your next trip, pal.", " 挑顺手的帮忙就好，伙计。", " Бери то, что по пути, дружище."))
    add_voice_pair("scavenger", "no_requests", "calm", "float", 140, 0.65,
                   ("Crates are tidy and the campfire pot's covered for now.", "木箱都收得整整齐齐，篝火上的汤锅这会儿也够用。", "Ящики в порядке, а в котелке сейчас всего хватает."),
                   (" Kick back by the awning or take a dip off the fishing pier!", " 去白篷底下吹吹海风，或者去钓鱼区泡泡水吧！", " Отдохни под навесом или искупайся у рыболовного края!"))
    add_voice_pair("scavenger", "handled", "happy", "bounce", 140, 0.65,
                   ("Looks like someone already hauled those onto the deck!", "好像已经有人把那批货搬上甲板啦！", "Похоже, кто-то уже занес это на палубу!"),
                   (" Check the other notes if you still want an errand.", " 还想搭把手的话看看别的条目吧。", " Загляни в остальные заметки, если хочешь ещё помочь."))
    add_voice_pair("scavenger", "watch", "determined", "pulse", 140, 0.75,
                   ("Something hostile keeps circling the shoal stilts and piers.", "有个难缠的家伙一直在浅滩木桩和栈桥外圈转悠。", "Кто-то враждебный кружит у свай и причала Отмели."),
                   (" Chase it clear of the deck and let me know once the water's calm.", " 把它赶出甲板周围再告诉我。", " Прогони его от настила и скажи, как вода очистится."))
    add_voice_pair("scavenger", "request", "curious", "emphasis", 140, 0.65,
                   ("Could use %s %s for the shoal stores.", "浅滩公用补给这边缺 %s 份 %s。", "Для нужд Отмели нам не помешает %s (%s)."),
                   (" Drop them with me or into the shared chest whenever you sail back.", " 等你回来顺手交给我就行。", " Передай мне или закинь в общий сундук, как вернёшься."))
    add_voice_pair("scavenger", "choose_item", "curious", "wave", 140, 0.65,
                   ("Hold whatever supplies you're sharing in your hand so I can look them over.", "把你打算分享的物资拿在手里让我瞧瞧。", "Возьми в руку припасы, которыми готов поделиться, чтобы я прикинул."),
                   (" Keep enough rations dry for your own swim back!", " 别忘了给自己也留点路上吃的干粮！", " Оставь себе достаточно еды на обратный путь!"))
    add_voice_pair("scavenger", "donate", "happy", "wave", 140, 0.7,
                   ("You hauled %s all the way out to The Shoal?", "你把 %s 一路运到浅滩来了？", "Ты притащил %s прямо на Отмель?"),
                   (" Crew's gonna put that straight to work. How much are you leaving?", " 船员们肯定用得上。这次打算放多少？", " Команда сразу пустит это в дело. Сколько оставишь?"))
    add_voice_pair("scavenger", "transfer", "curious", "emphasis", 140, 0.7,
                   ("Right now your berth is registered with %s.", "你现在的住所还登记在%s那边。", "Сейчас ты числишься в «%s»."),
                   (" Want to haul your gear out here and join The Shoal crew instead? Old friends won't mind a bit.", " 想把行李搬到海上加入我们浅滩小队吗？老朋友们绝不会怪你的。", " Хочешь перебраться к нам на Отмель? Прежние друзья поймут и не обидятся."))
    add_voice_pair("scavenger", "thanks", "happy", "bounce", 140, 0.7,
                   ("Beauty—thanks, champ!", "漂亮——谢啦，伙计！", "Красота — спасибо, дружище!"),
                   (" I'll stow these in the shared chest before the gulls spot them. Help yourself by the fire anytime.", " 我这就趁海鸥没叼走前塞进公共箱子。随时来火堆边吃热的。", " Уберу в общий сундук, пока чайки не растащили. Угощайся у костра в любое время."))
    add_voice_pair("scavenger", "declined", "calm", "wave", 140, 0.6,
                   ("All good, pal.", "没事，伙计。", "Всё в порядке, дружище."),
                   (" Keep what you need for the open water—we've got nets drying if supplies run low.", " 留着自己远航用吧，我们自己也下网捕鱼呢。", " Прибереги на плавание — у нас и свои сети есть."))
    add_voice_pair("scavenger", "changed", "curious", "pulse", 140, 0.65,
                   ("Whoa, wait—that didn't tally up cleanly.", "等等，数量对不上啊。", "Погоди — что-то не сошлось."),
                   (" Make sure you're holding the right item and the shared chest still has room.", " 确认手里拿着的东西没错，公共箱子也没塞满。", " Проверь, что держишь нужный предмет и в общем сундуке есть место."))
    add_voice_pair("scavenger", "meal", "happy", "bounce", 140, 0.75,
                   ("Pot's hot and dinner's on!", "汤锅烧热了，开饭喽！", "Котелок кипит — ужин готов!"),
                   (" Grab a log stool near the awning; everybody off duty will swim on up directly.", " 去白篷旁找个木墩坐，没在忙的伙计马上都过来。", " Садись на чурбак под навесом, кто свободен — сейчас подойдут."))
    add_voice_pair("scavenger", "watch_thanks", "happy", "bounce", 140, 0.75,
                   ("Deck perimeter's all clear again!", "甲板四周又恢复清净啦！", "Периметр палубы снова чист!"),
                   (" Thanks for keeping trouble off our stilts. Grab a warm bowl from the pot—you earned it.", " 多亏你帮大伙把麻烦赶走，去锅里盛一碗热的吧，应得的。", " Спасибо, что отогнал угрозу от свай. Бери миску горячего из котелка — заслужил."))
    add_voice_pair("scavenger", "watch_wait", "determined", "pulse", 140, 0.7,
                   ("Keep an eye over the railing a second longer.", "再盯一眼栏杆外面的海面吧。", "Присмотри за водой за перилами еще минутку."),
                   (" Once nothing's bumping our stilts anymore, we can kick back properly.", " 等确定水下再没东西撞木桩了再歇。", " Как только никто не будет тревожить сваи, тогда и передохнём."))
    add_voice_pair("scavenger", "outfit", "happy", "bounce", 140, 0.8,
                   ("You've hauled your share and more for this crew, pal.", "伙计，你在咱们船上出的力气已经远远够格了。", "Ты здорово помог нашей команде, дружище."),
                   (" Take this diving suit—same blue trim my old partner gave me. Cuts through the tide like a charm.", " 收下这身潜水服吧——和我当年搭档送我那件一样的蓝条纹款，下海顺手得很。", " Возьми этот водолазный костюм с синей полосой — такой же, как оставил мне старый напарник."))
    add_voice_pair("scavenger", "reverse_rite", "calm", "wave", 150, 0.7,
                   ("Hold steady a breath. Swimming like a swordfish sure beats soggy boots, though keeping a human face handy for shore runs makes sense too. There—back to your own skin.", "先稳住呼吸。当旗鱼在海里窜确实痛快，不过上岸办事偶尔换回人脸也挺方便。好了——变回你原来的样子啦。", "Постой спокойно секунду. Плавать как рыба-меч здорово, но иногда на берегу удобнее в человеческом виде. Готово — ты снова в своём облике."))

    # Shoal Resident — 34 topics matching burrow_resident
    add_voice_pair("shoal_resident", "greeting", "curious", "wave", 140, 0.65,
                   ("Oh—hey there.", "噢——嗨。", "О — привет."),
                   (" Checking on the shoal stores, or looking for something to help with around the deck?", " 来看看浅滩的补给，还是找点甲板上的活帮忙？", " Проверяешь запасы Отмели или ищешь, чем помочь на палубе?"))
    add_voice_pair("shoal_resident", "food_low", "sad", "droop", 140, 0.65,
                   ("The shared chest out on the storage deck is looking a bit light.", "储物甲板那边的公共箱子有点见底了。", "Общий сундук на грузовой палубе заметно опустел."),
                   (" Extra fish, dried kelp, or oranges from shore would stretch our meals cleanly.", " 多带些鱼、海带或者岸边的橙子过来，够大家顶一阵了。", " Лишняя рыба, ламинария или апельсины с берега очень бы выручили."))
    add_voice_pair("shoal_resident", "food_enough", "calm", "emphasis", 140, 0.65,
                   ("The campfire pot's simmering and the shared chest has enough food for now.", "篝火上的锅一直热着，公共箱子里的吃食现在够分。", "Котелок у костра кипит, а в общем сундуке еды пока достаточно."),
                   (" Help yourself if you're crew here.", " 既是一伙的就别客气。", " Угощайся, если живёшь с нами."))
    add_voice_pair("shoal_resident", "food_plenty", "happy", "wave", 140, 0.7,
                   ("Our storage chest is stocked full of fish and dry greens right now!", "储物箱现在塞满了鱼和干菜！", "Наш общий сундук сейчас забит рыбой и зеленью!"),
                   (" Would be great to call everyone over to the campfire stools.", " 真适合把大伙儿喊到篝火木墩旁一块儿开饭。", " Самое время позвать всех на обед к костру."))
    add_voice_pair("shoal_resident", "safe", "calm", "float", 140, 0.65,
                   ("Waves under the stilts are calm and nothing hostile's prowling near the piers.", "木桩下的海浪稳稳当当，栈桥外头也没有怪东西乱晃。", "Волны под сваями тихие, и вокруг причалов спокойно."),
                   (" Good day for basking on dry planks.", " 正适合在干木板上晒太阳。", " Отличный день погреться на досках."))
    add_voice_pair("shoal_resident", "danger", "determined", "pulse", 140, 0.7,
                   ("Careful—something hostile has been splashing near the outer piers.", "小心——有敌对家伙在外圈栈桥附近拍水。", "Осторожнее — кто-то враждебный кружит у крайнего причала."),
                   (" Could you clear the deck edge before anyone goes below?", " 能帮忙先清理掉外圈再说吗？", " Поможешь прогнать его от палубы?"))
    add_voice_pair("shoal_resident", "remember", "happy", "wave", 140, 0.65,
                   ("Still remember you hauling supplies out over the water for our crew.", "我还记得你大老远涉水把补给送到我们船上呢。", "Я до сих пор помню, как ты приносил припасы нашей команде."),
                   (" Glad to share the deck breeze with you anytime.", " 随时欢迎来甲板上一起吹海风。", " Всегда рад разделить с тобой морской бриз."))
    add_voice_pair("shoal_resident", "requests", "curious", "wave", 140, 0.65,
                   ("I wouldn't mind tucking a little extra snack away for my station.", "我想在自己守的角落存点小零嘴，省得老去翻大箱子。", "Я бы не отказался припрятать немного еды у себя на посту."),
                   (" Mind tossing a bit my way next time you're by the deck?", " 你下回顺路的话能捎一点给我吗？", " Не захватишь немного в следующий раз?"))
    add_voice_pair("shoal_resident", "no_requests", "calm", "float", 140, 0.65,
                   ("I've got everything I need right beside my post for now.", "我手头暂时什么都不缺啦。", "У меня сейчас всё есть прямо под рукой."),
                   (" Rest by the awning or watch the sea from the dorm windows whenever you like.", " 随时去白篷下乘凉或者去宿舍窗边看海吧。", " Отдохни под навесом или погляди на море из окна спальни."))
    add_voice_pair("shoal_resident", "handled", "happy", "bounce", 140, 0.65,
                   ("Oh—somebody already handed those over!", "噢——已经有人先拿给我啦！", "О — кто-то уже успел мне это передать!"),
                   (" Thanks for checking in anyway.", " 还是谢谢你惦记着。", " Но всё равно спасибо за заботу."))
    add_voice_pair("shoal_resident", "watch", "determined", "pulse", 140, 0.7,
                   ("Something hostile keeps circling the stilts beneath the house.", "有个敌对家伙老在房子底下的木桩附近绕圈。", "Кто-то враждебный всё кружит под нашими сваями."),
                   (" Mind chasing it out past the piers so it leaves the deck quiet?", " 能麻烦你把它赶到栈桥外头去吗？", " Прогони его подальше от причала?"))
    add_voice_pair("shoal_resident", "request", "curious", "wave", 140, 0.65,
                   ("Could you spare %s %s for my corner?", "能分我 %s 份 %s 吗？", "Не поделишься %s (%s) для моего поста?"),
                   (" Hand them straight to me whenever you swim past—no hurry.", " 顺路直接递给我就行，不急。", " Передай прямо мне, как проплывёшь мимо, без спешки."))
    add_voice_pair("shoal_resident", "choose_item", "curious", "wave", 140, 0.65,
                   ("Hold whatever supplies you're sharing in your hand so I can see.", "把你想给的东西拿在手里让我看看。", "Возьми припасы в руку, чтобы я видел."),
                   (" Keep enough food for your own trip back to shore.", " 自己也留够回岸上的吃食呀。", " Оставь себе достаточно еды на дорогу к берегу."))
    add_voice_pair("shoal_resident", "donate", "happy", "wave", 140, 0.65,
                   ("Brought %s out from the coast?", "从岸边带了 %s 过来？", "Принёс %s с берега?"),
                   (" That'll go straight into the shared storage chest. How much are you adding?", " 这就放进公共补给箱里。打算放多少？", " Пойдёт прямо в общий сундук. Сколько добавишь?"))
    add_voice_pair("shoal_resident", "transfer", "calm", "emphasis", 140, 0.65,
                   ("Your bunk is still listed back with %s right now.", "你现在还算%s那边的人呢。", "Твоя койка всё ещё числится за «%s»."),
                   (" If you want to join our crew over the waves, talk to the Scavenger by the campfire.", " 要想换来我们浅滩上过日子，去找篝火旁的拾荒者说说吧。", " Если хочешь перейти к нам на Отмель, поговори со Старьевщиком у костра."))
    add_voice_pair("shoal_resident", "thanks", "happy", "bounce", 140, 0.65,
                   ("Thanks a bunch!", "太感谢啦！", "Большое спасибо!"),
                   (" Catch your breath under the white awning whenever the sun's high.", " 太阳毒的时候记得去白篷底下歇歇。", " Отдохни под белым навесом, когда солнце припекает."))
    add_voice_pair("shoal_resident", "declined", "calm", "none", 140, 0.5,
                   ("Fair enough.", "行吧。", "Тоже верно."),
                   (" Hang onto your supplies out here on the water.", " 在海上补给确实得省着用。", " На воде припасы лучше приберечь."))
    add_voice_pair("shoal_resident", "changed", "curious", "pulse", 140, 0.65,
                   ("Wait—that didn't quite line up.", "等一下——好像哪里对不上。", "Погоди — что-то не сходится."),
                   (" Make sure you're holding the right item and our shared chest isn't full.", " 看看手里的东西对不对，公共箱子满没满。", " Проверь, что держишь нужную вещь и в сундуке есть место."))
    add_voice_pair("shoal_resident", "meal", "happy", "bounce", 140, 0.65,
                   ("I'll call out to the folks around the deck and dorm.", "我去甲板和宿舍那头喊一声大伙儿。", "Покличу остальных с палубы и из спальни."),
                   (" Wait by the campfire stools—anyone not sleeping will wander over.", " 在篝火木墩那儿等会儿，没睡觉的伙计马上都过来。", " Подожди у чурбаков у костра — кто не спит, сейчас придёт."))
    add_voice_pair("shoal_resident", "gesture", "whisper", "whisper", 140, 0.65,
                   ("The aquatic resident softly clicks and motions toward the Scavenger by the campfire under the white awning—their speech is still hard to follow.", "水边的同伴轻哼一声，朝白篷篝火旁的拾荒者指了指，你一时还听不懂他的话。", "Водный житель тихо щёлкает и кивает в сторону Старьевщика у костра под навесом — речь пока не разобрать."))
    add_voice_pair("shoal_resident", "seekchief", "calm", "float", 140, 0.65,
                   ("(The Scavenger by the campfire handles everything for The Shoal crew.", "（篝火旁的拾荒者负责打理浅滩小队的一切事务。", "(Старьевщик у костра ведает всеми делами команды Отмели."),
                   (" Once you have a translator, telepathy, or a latex form, the rest of us can chat clearly too.)", " 等你有翻译器、心灵感应或者变成胶兽以后，就能和大家顺畅聊了。）", " Когда появится переводчик, телепатия или латексная форма, остальные тоже смогут свободно общаться.)"))
    add_voice_pair("shoal_resident", "playful_greeting", "happy", "bounce", 140, 0.7,
                   ("Hey! Don't track salt water past the doorframes—the workshop shark is counting scuffs!", "嗨！进门前脚下抖抖水——工坊鲨鱼正数着门框上的刮痕呢！", "Эй! Не набрызгай солёной водой на косяк — акула из мастерской считает каждую царапину!"),
                   (" Come watch the waves hit the stilts with us!", " 快来一块儿看海浪拍木桩！", " Пошли смотреть, как волны бьют о сваи!"))
    add_voice_pair("shoal_resident", "playful_request", "curious", "wave", 140, 0.7,
                   ("Psst—could you smuggle %s %s over here?", "嘘——能悄悄递给我 %s 份 %s 吗？", "Тсс — не притащишь сюда %s (%s)?"),
                   (" Just don't tell the Scavenger if it looks anything like dried snacks!", " 要是像小零食可别让拾荒者看见啦！", " Только не говори Старьевщику, если это похоже на вкусняшку!"))
    add_voice_pair("shoal_resident", "playful_thanks", "happy", "bounce", 140, 0.7,
                   ("Ha! Legend!", "哈！够意思！", "Ха! Выручил!"),
                   (" Next time the boss hauls up another chair with squeaky wheels, you get first dibs!", " 下回老大再从海底捞出吱嘎响的转椅，第一个给你坐！", " В следующий раз, когда босс вытащит со дна ещё одно скрипучее кресло, оно твоё!"))
    add_voice_pair("shoal_resident", "cautious_greeting", "curious", "wobble", 140, 0.65,
                   ("Oh—hello! Didn't hear footsteps over the tide.", "噢——你好！海浪声大，没听见脚步声。", "О — привет! Из-за прибоя не услышал шагов."),
                   (" Is the open sea quiet today, or did something follow you out here?", " 今天海面太平吗，还是外头有什么东西跟过来了？", " На море сегодня тихо, или за тобой кто-то увязался?"))
    add_voice_pair("shoal_resident", "cautious_request", "curious", "wave", 140, 0.65,
                   ("If it isn't out of your way… could you spare %s %s?", "如果不麻烦的话……能匀我 %s 份 %s 吗？", "Если не по дороге в шторм… не поделишься %s (%s)?"),
                   (" Please watch the swells on your way back.", " 回程海浪大的话一定当心。", " Осторожнее на волнах по пути обратно."))
    add_voice_pair("shoal_resident", "cautious_thanks", "happy", "float", 140, 0.65,
                   ("Thank you kindly.", "实在太谢谢了。", "Большое тебе спасибо."),
                   (" Knowing the water around the stilts is clear makes it much easier to sleep.", " 知道木桩周围风平浪静，晚上睡觉都踏实多啦。", " Когда знаешь, что под сваями спокойно, и спится куда легче."))
    add_voice_pair("shoal_resident", "polite_greeting", "calm", "wave", 140, 0.65,
                   ("Good day! Mind the wet boards near the stair edges.", "日安！楼梯边上的木板有些湿滑，当心脚下。", "Добрый день! Осторожнее на влажных досках у края настила."),
                   (" Let me know if there's anything on deck I can help check for you.", " 甲板上有什么要查的随时跟我说。", " Скажи, если нужно что-то проверить на палубе."))
    add_voice_pair("shoal_resident", "polite_request", "curious", "wave", 140, 0.65,
                   ("Whenever your travels pass near The Shoal, might I ask for %s %s?", "如果您下次航程顺路，不知方不方便帮我带 %s 份 %s？", "Когда твой путь пройдёт мимо Отмели, можно попросить %s (%s)?"),
                   (" Please hand them over whenever convenient; there is no rush at all.", " 等方便时再交给我即可，一点不急。", " Передай, когда будет удобно; никакой спешки."))
    add_voice_pair("shoal_resident", "polite_thanks", "happy", "float", 140, 0.65,
                   ("My sincere thanks for carrying those out over the water.", "衷心感谢您跨过海面把东西送到这里。", "Искренне благодарю за то, что доставил это через воду."),
                   (" Do warm up beside the campfire before heading back out to sea.", " 出海前一定先在篝火边烤暖身子。", " Обязательно согрейся у костра перед отплытием."))
    add_voice_pair("shoal_resident", "watch_thanks", "happy", "bounce", 140, 0.65,
                   ("So the trouble outside the piers is gone?", "外圈栈桥那边的麻烦解决了？", "Значит, у причала снова спокойно?"),
                   (" Whew—the whole shoal can breathe easy again.", " 呼——整座浅滩总算能安心吹风了。", " Уф — вся Отмель теперь может вздохнуть спокойно."))
    add_voice_pair("shoal_resident", "watch_wait", "determined", "pulse", 140, 0.65,
                   ("Let's make sure nothing's circling beneath the stilts first.", "还是先确认房子底下的柱子旁没东西绕圈吧。", "Давай сначала убедимся, что под сваями никто не кружит."),
                   (" Nobody wants a brawl rocking the planks.", " 谁都不想木板被打得直晃悠。", " Никому не нужна драка на шатких досках."))
    add_voice_pair("shoal_resident", "away", "calm", "float", 140, 0.65,
                   ("Deck logs and supply crates can wait until we're back out at The Shoal.", "甲板记录和补给箱的事等回浅滩再理吧。", "Учёт грузов и ящики подождут, пока мы не вернёмся на Отмель."),
                   (" Right now I'm just glad to have a swimming partner along.", " 这会儿有人一块儿游水作伴就挺好。", " Сейчас просто приятно плыть в хорошей компании."))
    add_voice_pair("shoal_resident", "reverse_rite", "calm", "wave", 140, 0.65,
                   ("Only the Scavenger keeps track of borrowed forms—talk to him over by the campfire under the white awning.", "只有拾荒者管得了借用的形态——去白篷篝火旁找他聊聊吧。", "Только Старьевщик заведует временными обликами — поговори с ним у костра под белым навесом."))

    return lines, tr


def merge_lang(path: Path, lang_idx: int, translations: dict[str, tuple[str, str, str]]):
    data = json.loads(path.read_text(encoding="utf-8"))
    for key, vals in translations.items():
        data[key] = vals[lang_idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    profile, npc_tr = build_shoal_chief()
    camp_lines, camp_tr = build_camp_voices()

    STORY_NPC_PATH.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    common = json.loads(COMMON_DIALOGUE_PATH.read_text(encoding="utf-8"))
    for k, v in camp_lines.items():
        common["lines"][k] = v
    COMMON_DIALOGUE_PATH.write_text(json.dumps(common, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    all_tr = {**npc_tr, **camp_tr}
    merge_lang(LANG_DIR / "en_us.json", 0, all_tr)
    merge_lang(LANG_DIR / "zh_cn.json", 1, all_tr)
    merge_lang(LANG_DIR / "ru_ru.json", 2, all_tr)

    print(f"Wrote {STORY_NPC_PATH.relative_to(ROOT)} ({len(profile['nodes'])} nodes)")
    print(f"Updated {COMMON_DIALOGUE_PATH.relative_to(ROOT)} (+{len(camp_lines)} voices)")
    print(f"Merged {len(all_tr)} translations into en_us/zh_cn/ru_ru")


if __name__ == "__main__":
    main()
