# 地穴（Burrow）对话树核心文本与剧情片段草案

> 生成日期：2026-11-20  
> 对齐基准：英文（EN）为主稿，中文（ZH）与俄文（RU）严格按英文原意与语气直译对齐；遵循 `humanizer` + `de-AI-writing` + `humanizer-zh` 去 AI 化原则，不过度修饰。  
> 范围说明：仅包含对话树核心台词分支与实景剧情片段（书、便签、剪切板、光盘），不含 JSON/NBT/Java 接入实现。

---

## 一、地穴（`burrow.nbt`）结构与角色落位总览

- **环境基调**：雪针叶林地下多层木石掩体（`56×34×55`），全屋部署 30 台空调、33 个排风扇、27 块浮冰、6 块蓝冰及大量积雪层维持低温；配有室内农田、鱼塘、冷库与大厨房，直接呼应世界观「维生悖论」（高能耗胶兽在非伊甸环境下需要大量稳定食物供给）。
- **既有正史伏笔**：普罗米修斯点主管（`aec_chief.manul`）提到 O 博士野外记录里关于「胶兽兔狲」的那页被人撕掉，仅余页边印章写着「更远处的雪针叶林」。这与灰狐首领在外支开实验室调查队、兔狲首领拔掉地下终端网线的设定直接咬合。
- **双通道对话机制**：普通人类玩家听到的是生疏的口语与动作描写（后人类长期习惯通过「尘埃」心灵感应交流，开口说话略带生疏）；兽化玩家可额外收到括号内 `(尘埃心灵感应)`，用以表现兔狲与内向居民「嘴上冷淡、心里柔软/窘迫」的反差。

| 角色 | 实体与落位 | 房间实景锚点 | 职责与性格切面 |
| :--- | :--- | :--- | :--- |
| **雌性灰狐首领** | `latex_gray_fox_female` · 上层阁楼 `(20.5, 15, 32.5)` | 5 个冷色抱枕、书架、桃子/汤罐头、展示框甜浆果 | 外向伴侣；负责外出踩点、爬松树望风、物物交换、应付访客与支开调查队 |
| **雄性兔狲首领** | `latex_manul` · 厨房吧台 `(30.5, 13, 6.5)` | 灶台、吧台与高脚凳、盆栽凋零玫瑰、馅饼/沙拉/罐头、公共双箱 | 内向伴侣；负责做饭、库存、室内修缮与控温；嘴短怕生，但默默照看所有人 |
| **阁楼雌性雪豹** | `latex_snow_leopard_female` · 上层阁楼 `(19.5, 15, 33.5)` | 与灰狐同处一室，堆满抱枕与书堆 | 窝在抱枕堆里看书，讨厌有人不敲门突然上楼，但默认灰狐随便闹 |
| **工坊雄性蓝狼** | `latex_blue_wolf` · 内工坊 `(27.5, 9, 37.5)` | 双高炉、双铁砧、实验台、盆栽兰花、浮冰 | 维护空调、排风扇与工具，干活时极度讨厌寒暄 |
| **鱼塘雄性雪豹** | `latex_snow_leopard_male` · 室内鱼塘 `(5.5, 8, 28.5)` | 活鳕鱼/鲑鱼、睡莲、唯一一根海泡菜、钓竿桶、抱枕 | 守着池塘看鱼发呆，被脚步声打扰会明显不耐烦 |
| **农田幼犬（Yuin）** | `latex_yuin` · 室内农田 `(15.5, 12, 13.5)` | 牛羊鸡栏、四种作物田、农夫箱 | 喂牲口给厨房供货，生人趴栅栏或忘关门会直接赶人 |
| **书房幼犬（Yuin）** | `latex_yuin` · 宿舍阅览角 `(20.5, 7, 9.5)` | 书架、书堆、实验台、填馅南瓜 | 躲在书堆后面，回答尽量简短只想快点结束对话 |
| **走廊粉红双足飞龙** | `latex_pink_wyvern` · 宿舍走廊与纸箱储藏室 `(21.5, 6, 10.5)` | 7 间云杉木门单间、纸箱与铁板条箱、橘子树口粮、火把花 | 守着走廊安静和纸箱堆，典型「半掩房门、别烦我」的社交回避感 |
| **（无人主卧）** | 下层主卧与终端室 `y=1..3` | 两张并排青色床、蜡烛、蓝冰冷块、老电脑 `(28, 2, 33)`、`creation_of_lin` 与 `puro_doodle` 画作 | 两位首领的共享卧室；白天空着，提供环境叙事线索 |

---

## 二、核心对话树文本：双首领

### A. 雌性灰狐首领（Gray Fox Chief · 上层阁楼 / 外务负责人）

#### 1. 初次见面 · 人类玩家 (`root_human`)
- **EN**:
  > *She hops off the pillow pile, claws clicking lightly on the spruce floor, and grins.*
  > "Whoa, hey! A real baseline human, all the way out in the pines? Hold on, let me remember how my throat works... Yeah, okay, words still come out. Welcome to the Burrow. Don't mind the chilly air or the stares downstairs. Nobody here bites. They just wish you'd texted first."
- **ZH**:
  > *她从抱枕堆上跳下来，爪子在云杉地板上轻敲了两下，咧嘴笑了笑。*
  > “哇，嗨！货真价实的原版人类，居然一路走到针叶林这儿来了？等等，让我找找声带怎么用……行，还能说出话来。欢迎来地穴。别介意屋里有点冷，也别管楼下那些眼神。这儿没人咬人，他们只是希望你来之前先发个消息。”
- **RU**:
  > *Она спрыгивает с кучи подушек, тихо стукнув когтями по еловому полу, и усмехается.*
  > «Ого, привет! Настоящий человек, аж сюда, в сосны забрёл? Подожди, дай вспомнить, как горло работает... Ага, слова вроде идут. Добро пожаловать в Нору. Не обращай внимания на холод и на взгляды внизу. Тут никто не кусается. Просто все жалеют, что ты не написал заранее».

#### 2. 初次见面 · 兽化玩家 (`root_transformed`)
- **EN**:
  > *She leans over the loft railing, ears forward, tail wagging once.*
  > *Yip!* "Hey! Someone new in the pines!" (Come on up, it's warmer by the pillows. Well, less drafty. I handle visitors around here so the rest of the house doesn't bolt into their bedrooms.)
- **ZH**:
  > *她俯身靠在阁楼栏杆上，耳朵向前支着，尾巴轻轻摇了一下。*
  > *轻吠了一声！* “嗨！针叶林里来新人啦！”（上来吧，抱枕这边暖和点。好吧，至少不怎么穿风。这儿的访客归我接待，免得屋里其他人全缩回卧室去。）
- **RU**:
  > *Она перегибается через перила лофта, навострив уши, и разок виляет хвостом.*
  > *Тяв!* «Привет! Новенький в соснах!» (Поднимайся, у подушек теплее. Ну, хотя бы меньше дует. Гостями тут занимаюсь я, чтобы остальные не разбежались по комнатам.)

#### 3. 再次对话 · 人类 / 兽化 (`return_human` / `return_transformed`)
- **EN (Human)**:
  > "Back again? Hope nobody slammed a door on you. Grab a peach can from the loft if you're hungry, or go bug my partner by the stove. Just talk quiet."
- **ZH (Human)**:
  > “又来啦？希望没人当着你的面甩门。饿了的话从阁楼拿罐桃子，或者去灶台边找我搭档。说话小点声就行。”
- **RU (Human)**:
  > «Снова зашёл? Надеюсь, никто перед носом дверью не хлопнул. Если проголодался, бери банку персиков тут в лофте или загляни к моему партнёру у плиты. Только говори потише.»

- **EN (Transformed)**:
  > *Chirp!* (Oh, hey! Still hanging around the Burrow? Good. Means the cold didn't chase you off yet.)
- **ZH (Transformed)**:
  > *轻叫一声！*（哦，嗨！还在地穴附近转悠呢？挺好，说明这儿的冷气还没把你吓跑。）
- **RU (Transformed)**:
  > *Фырк!* (О, привет! Всё ещё ошиваешься у Норы? Хорошо. Значит, холод тебя пока не прогнал.)

#### 4. 玩家选项分支
- **Option 1 (`about_burrow`)**: "What is this place?" / “这里是什么地方？” / «Что это за место?»
  - **EN**:
    > "Just a hole under the snow we dug out and wired up. Thirty air units, an indoor farm, a fish pond, and seven bedrooms with solid locks. I run the outside stuff: scouting ridges, trading when we need parts, climbing pines to see who's wandering too close. My partner downstairs runs everything under the roof. Keeps us fed, keeps the vents clear, keeps everyone from killing each other over missing mugs."
  - **ZH**:
    > “就是在雪底下挖出来、接上电的一个地洞。三十台空调，一片室内农田，一个鱼塘，还有七间锁得很牢的卧室。外面的事归我管：去山脊踩点、缺零件时换点东西、爬上松树看看有谁靠得太近。楼下我搭档管屋顶底下的所有事，管大家吃饱，管通风口不堵，也免得大家为了不见的杯子吵起来。”
  - **RU**:
    > «Просто нора под снегом, которую мы вырыли и обжили. Тридцать кондиционеров, крытая ферма, пруд с рыбой и семь спален с крепкими замками. На мне всё снаружи: осмотреть хребты, выменять детали, залезть на сосну и глянуть, не подошёл ли кто слишком близко. На моём партнёре внизу всё под крышей. Кормит нас, следит за вентиляцией и не даёт всем переругаться из-за пропавших кружек.»

- **Option 2 (`about_manul`)**: "Tell me about the Manul downstairs." / “跟我说说楼下那只兔狲吧。” / «Расскажи про манула внизу.»
  - **EN**:
    > *She laughs and flicks an ear toward the stairs.*
    > "Best cook in the taiga, worst small-talker on the continent. If he looks mad when you walk into the kitchen, he isn't. That's just his face when someone interrupts his stew. Also, if I hug him without warning, his eyes go wide as saucers and his tail puffs straight up. Don't tell him I told you that."
    > (Seriously though, he does half the worrying for everyone in this building. If he shoves a plate toward you without making eye contact, it means he likes you.)
  - **ZH**:
    > *她笑出声，耳朵朝楼梯方向甩了一下。*
    > “整个针叶林最好的厨子，也是整片大陆最不会寒暄的家伙。你要是走进厨房看见他一脸不高兴，别当真，有人打断他炖菜时他就长那样。还有，我要是不打招呼突然抱住他，他眼睛会瞪得跟盘子一样圆，尾巴直接炸起来。别跟他说是我告诉你这事的。”
    > （说真的，这栋屋子里一半的心都是他在操。如果他不看你眼睛，直接把盘子推到你手边，那就是他对你有好感了。）
  - **RU**:
    > *Она смеётся и дёргает ухом в сторону лестницы.*
    > «Лучший повар в тайге и худший собеседник на всём континенте. Если ты зайдёшь на кухню, а у него злой вид, не бери в голову. У него всегда такое лицо, когда кто-то сбивает его с готовки. А ещё, если обнять его без предупреждения, у него глаза становятся как блюдца, а хвост сразу трубой. Только не говори ему, что я это рассказала.»
    > (Но если серьёзно, он переживает за всех в этом доме за двоих. Если он молча подвинет тебе тарелку, не глядя в глаза, значит, ты ему понравился.)

- **Option 3 (`story_clinic`)**: "How did you two end up starting this place?" / “你们俩当初怎么建起这里的？” / «Как вы вдвоём вообще основали это место?»
  - **EN**:
    > *Her grin softens a notch. She taps a claw against a peach can.*
    > "Way back, when the cure first rolled out, I didn't have much time left on my old body. Bad lungs, worse odds. My doctors stopped giving me calendars. So I took the goo early, before most people even trusted it. Thought I'd have to say goodbye to him and figure the rest out alone."
    > "Next morning, he walked into the same clinic, signed the form, and sat in the chair next to mine. Didn't even have a cough. Just said he wasn't going to watch me walk off into a new century by myself. We haven't spent a week apart since."
  - **ZH**:
    > *她的笑容放轻了些，用爪尖轻轻敲着桃子罐头。*
    > “很久以前，疗法刚推开那会儿，我原来的身体已经剩不了多少时间了。肺不行了，希望更渺茫，医生都不再跟我提明年。所以我很早就选了胶兽化，那时大多数人还不敢信这东西。我本来以为得跟他告别，剩下的路自己走。”
    > “结果第二天早上，他走进同一家诊所，签了字，直接坐到了我旁边的椅子上。他连咳嗽都没有。只说不想看着我一个人走进下一个世纪。从那以后，我们分开的时间再没超过过一个星期。”
  - **RU**:
    > *Её усмешка становится мягче. Она постукивает когтем по банке с персиками.*
    > «Давно, когда лекарство только начали применять, моему старому телу оставалось недолго. Плохие лёгкие, ещё хуже прогнозы. Врачи перестали говорить со мной о следующем годе. Так что я пошла на это рано, когда большинство ещё боялось. Думала, придётся с ним попрощаться и дальше разбираться самой.»
    > «На следующее утро он зашёл в ту же клинику, подписал бланк и сел в кресло рядом со мной. У него даже кашля не было. Просто сказал, что не собирается смотреть, как я ухожу в новый век одна. С тех пор мы больше чем на неделю не расставались.»

- **Option 4 (`story_exodus`)** *(接在 `story_clinic` 下)*: "Why didn't you leave with the ships?" / “你们当时怎么没跟着飞船走？” / «Почему вы не улетели на кораблях?»
  - **EN**:
    > "Oh, we looked at the boarding lists. Thousands of people packed into metal tubes, committee meetings every Tuesday, everyone talking over each other for decades? He took one look at the passenger brochure and looked ready to climb out a window. Besides, I like real pine trees. You can't dig your claws into hull plating."
  - **ZH**:
    > “哦，我们看过登船名单。几千号人挤在铁管子里，每周二开委员会，一吵就是几十年？他看了一眼乘客手册，那表情看着恨不得直接翻窗。再说了，我喜欢真正的松树，船壳钢板可没法让你抠爪子。”
  - **RU**:
    > «О, мы смотрели списки на посадку. Тысячи людей в металлических трубах, собрания комитетов каждый вторник, и все перебивают друг друга десятилетиями? Он один раз глянул на брошюру и уже выглядел так, будто готов вылезти в окно. К тому же я люблю настоящие сосны. В обшивку корпуса когтями не вцепишься.»

- **Option 5 (`about_residents`)**: "Why does everyone downstairs act like I stepped on their tail?" / “楼下的人怎么都像我踩了他们尾巴一样？” / «Почему все внизу ведут себя так, будто я им на хвост наступил?»
  - **EN**:
    > *She snorts.*
    > "Because you showed up unannounced and you're breathing in their hallway. Look, we didn't recruit go-getters. We ended up with folks who got tired of crowds, tired of labs, tired of someone always wanting something from them. After they changed, they just leaned into it. Short answers, shut doors, headphones-on energy. Give them elbow room and they'll leave spare iron or extra fish in the shared chest for you. Corner them for a chat and they'll hiss like wet cats."
  - **ZH**:
    > *她轻嗤了一声。*
    > “因为你没打招呼就冒出来了，还在他们走廊里喘气。听着，我们招的本来就不是社交达人。最后聚到这儿的，都是受够了人挤人、受够了实验室、受够了总有人找自己办事的家伙。兽化之后，他们干脆顺着性子活了。说话短，房门常关，一副戴着耳机别理我的架势。你给他们留够空间，他们会在公共箱子里给你留多余的铁锭或者鱼；你要是把人堵在墙角拉着闲聊，他们就会像淋湿的猫一样朝你哈气。”
  - **RU**:
    > *Она фыркает.*
    > «Потому что ты явился без предупреждения и дышишь в их коридоре. Слушай, мы тут не активистов набирали. Сюда собрались те, кто устал от толпы, от лабораторий и от вечных просьб. После превращения они просто перестали притворяться. Короткие ответы, закрытые двери, вид вроде "на мне наушники, отстань". Дай им личное пространство, и они оставят тебе лишнее железо или рыбу в общем сундуке. Зажмёшь в углу ради болтовни — зашипят, как мокрые коты.»

- **Option 6 (`outside_runs`)**: "What do you actually run into out in the taiga?" / “你在针叶林外面平时都会碰到什么？” / «Что ты обычно встречаешь снаружи в тайге?»
  - **EN**:
    > "Snow, wolves, the occasional lost trader, and every few years some lab survey crew trying to map 'unregistered taiga latexes.' Those are my favorite. I climb a spruce above their camp, drop a pine cone near their compass, and lead them three ridges east until they give up. Some old doctor's field book supposedly had a page on Manuls once. Pretty sure it's missing now. Wonder how that happened."
  - **ZH**:
    > “雪，狼，偶尔迷路的商队，还有每隔几年就来一趟、想给‘未登记针叶林胶兽’画地图的实验室调查队。那帮人我最喜欢应付了。我爬到他们营地上头的云杉树上，往他们罗盘旁边丢个松果，再把他们往东遛过三道山脊，直到他们自己放弃。听说以前有个老医生的野外笔记里记过兔狲那一页，现在八成已经缺页了吧。真好奇是怎么没的。”
  - **RU**:
    > «Снег, волков, иногда заблудившихся торговцев, а раз в пару лет — какую-нибудь лабораторную группу, которая пытается нанести на карту "неучтённых таёжных латексов". Этих я особенно люблю. Залезаю на ель над их лагерем, роняю шишку рядом с компасом и увожу их на три хребта восточнее, пока им не надоест. Говорят, в полевом дневнике какого-то старого врача когда-то была страница про манулов. Почти уверена, что её там больше нет. Интересно, куда делась.»

#### 5. 招募与转化分支 (`enrol` / `explain_form` / `welcome_*` / `goodbye`)
- **Option (`enrol`)**: "Can I join the Burrow?" / “我能加入地穴吗？” / «Можно мне присоединиться к Норе?»
  - **EN**: "Sure, if you can handle quiet hallways and cold floors. You don't have to change to stay with us, by the way. Keep your own skin, keep whatever form you've got, or take a gray fox coat if you want to climb trees with me. Up to you."
  - **ZH**: “当然，只要你能受得了安静的走廊和冰凉的地板。顺便说一句，跟我们一块住不一定要变身。留着你原本的样子，保持你现在的形态，或者换一身灰狐皮毛跟我一起爬树，都随你。”
  - **RU**: «Конечно, если тебя не пугают тихие коридоры и холодный пол. Кстати, для этого не обязательно меняться. Оставайся собой, сохраняй свою нынешнюю форму или бери шкуру серой лисы, если хочешь лазить со мной по деревьям. Решать тебе.»
- **Option (`explain_form`)**: "What's the Gray Fox form like?" / “灰狐形态是什么感觉？” / «Каково быть серой лисой?»
  - **EN**: "Light on your feet, warm coat, and cat-sharp claws that actually hook into bark and stone. Most canines can't climb straight up a pine trunk; I can. Comes in handy when you want to see who's coming down the trail before they see you."
  - **ZH**: “脚步轻，毛暖和，爪子像猫科一样锋利，能直接钩进树皮和石头里。大多数犬科没法顺着松树干直着往上爬，我可以。想在路上的来客看见你之前先瞧清他们是谁时，这本事特别顺手。”
  - **RU**: «Лёгкий шаг, тёплая шерсть и острые кошачьи когти, которыми реально цепляться за кору и камень. Большинство псовых не умеют лезть вверх по стволу сосны, а я умею. Очень удобно, когда хочешь разглядеть гостя на тропе раньше, чем он заметит тебя.»
- **`welcome_human`**:
  - **EN**: "Works for me! Grab a thick jacket from the storage room if the ACs get to you. And remember: knock before opening bedroom doors."
  - **ZH**: “没问题！空调吹得受不了就去储藏室拿件厚外套。还有记住，开卧室门前先敲门。”
  - **RU**: «Меня устраивает! Если от кондиционеров продрогнешь, возьми тёплую куртку в кладовке. И помни: в спальни сначала стучись.»
- **`welcome_latex`**:
  - **EN**: "Great! One more face around the kitchen bar. (Welcome in. Don't worry if the others take a week to say hi.)"
  - **ZH**: “太好了！厨房吧台边又多一张熟面孔。（欢迎加入。其他人要是花一星期才肯跟你打招呼，别往心里去。）”
  - **RU**: «Отлично! Ещё одно знакомое лицо у кухонной стойки. (Добро пожаловать. Не переживай, если остальные решатся поздороваться только через неделю.)»
- **`welcome_copy`**:
  - **EN**: "Nice choice. Give your claws a test flex before you jump onto the loft rafters, alright? (Feels pretty sharp, huh? Come find me when you want to run the ridge.)"
  - **ZH**: “选得好。跳上阁楼横梁前先活动下爪子，好吗？（挺利索的吧？想跑山脊时来找我。）”
  - **RU**: «Хороший выбор. Только разомни когти, прежде чем прыгать на балки лофта, ладно? (Ощущается шустро, правда? Захочешь пробежаться по хребту — ищи меня.)»
- **`welcome_permanent`**:
  - **EN**: "All settled in, then. No rushing out in the snow before you get your footing. (My partner's going to pretend he didn't notice and make an extra pie tonight anyway.)"
  - **ZH**: “那就这么定啦。没站稳之前别急着往雪地里冲。（我搭档嘴上肯定装作没注意，今晚还是会多烤一个馅饼的。）”
  - **RU**: «Тогда добро пожаловать всерьёз. Только не выбегай сразу в снег, пока не привыкнешь к лапам. (Мой партнёр сделает вид, что ничего не заметил, но всё равно испечёт сегодня лишний пирог.)»
- **`goodbye`**:
  - **EN**: "See ya! Watch your step on the stairs, the snow melts there."
  - **ZH**: “回见！下楼梯当心脚下，那儿积雪容易化水。”
  - **RU**: «Увидимся! Смотри под ноги на лестнице, там снег подтаивает.»

---

### B. 雄性兔狲首领（Manul Chief · 厨房吧台 / 内务负责人）

#### 1. 初次见面 · 人类玩家 (`root_human`)
- **EN**:
  > *He freezes with a wooden spoon halfway to the pot, ears flattening slightly. After a beat, he clears his throat, voice dry and rough from disuse.*
  > "...Did the fox send you down? Fine. Wipe snow off your boots by the stairs. If you're hungry, there's pie on the counter and extra rations in the double chest. Don't touch the stove dials."
- **ZH**:
  > *他举着木勺停在锅边，耳朵微微压平。过了一拍，他清了清嗓子，太久没开口，声音有些干哑。*
  > “……是狐狸叫你下来的？行吧。在楼梯口把靴子上的雪蹭干净。饿了的话吧台上有馅饼，双人大箱子里还有备用口粮。别乱动灶台旋钮。”
- **RU**:
  > *Он замирает с деревянной ложкой на полпути к кастрюле, слегка прижав уши. Спустя секунду откашливается — голос сухой и хриплый с непривычки.*
  > «...Это лиса тебя вниз отправила? Ладно. Отряхни снег с ботинок у лестницы. Если голоден, на стойке есть пирог, а в двойном сундуке запасные пайки. Ручки плиты не трогай.»

#### 2. 初次见面 · 兽化玩家 (`root_transformed`)
- **EN**:
  > *He looks up from the stove, eyes wide for half a second before narrowing back into a flat stare. His tail curls neatly over his paws.*
  > *Low grumble.* "You're standing in the walkway." (Sorry. I'm bad with surprise guests. There's warm food on the bar if you came in from the snow. Just... take what you need.)
- **ZH**:
  > *他从灶台边抬起头，眼睛瞪圆了半秒，很快又眯成平平的一道视线。尾巴整整齐齐卷在了脚面上。*
  > *低低的咕噜声。* “你挡着过道了。”（抱歉。我应付不来突然上门的客人。要是刚从雪地里进来，吧台上有热饭。自己……拿够就行。）
- **RU**:
  > *Он поднимает взгляд от плиты, на полсекунды широко распахивает глаза, но тут же снова хмурится. Хвост аккуратно ложится поверх лап.*
  > *Тихое ворчание.* «Ты стоишь в проходе.» (Извини. Я плохо переношу неожиданных гостей. Если пришёл с мороза, на стойке тёплая еда. Просто... бери, сколько нужно.)

#### 3. 再次对话 · 人类 / 兽化 (`return_human` / `return_transformed`)
- **EN (Human)**:
  > "Back at the counter. Still hungry, or do you actually need something?"
- **ZH (Human)**:
  > “又回吧台了。还是饿，还是真有事？”
- **RU (Human)**:
  > «Снова у стойки. Всё ещё голоден или правда что-то нужно?»

- **EN (Transformed)**:
  > *Short blink.* "Plates are by the left cabinet." (Oh, it's you. Good timing, the soup just stopped boiling over.)
- **ZH (Transformed)**:
  > *很快眨了下眼。* “盘子在左边橱柜旁。”（哦，是你啊。来得正好，汤刚没扑锅。）
- **RU (Transformed)**:
  > *Коротко моргает.* «Тарелки у левого шкафчика.» (А, это ты. Вовремя, суп только перестал убегать.)

#### 4. 玩家选项分支
- **Option 1 (`about_kitchen`)**: "You do all the cooking for everyone here?" / “这里所有人的饭都是你一个人做？” / «Ты тут на всех один готовишь?»
  - **EN**:
    > "Somebody has to. Latex bodies burn through food fast when it's freezing out, and half the people downstairs forget to eat until they get cranky. The Yuin brings meat and crops from the pen, the snow leopard drops fish in the ice barrel, and I turn it into stuff people can grab without having to sit through a group dinner. Works better that way."
    > (And yes, I still leave a full plate outside the locked doors at night. Don't make it weird.)
  - **ZH**:
    > “总得有人做。外头天冷，胶兽身体消耗热量快，楼下一半人忙起来能忘掉吃饭，直到饿得发脾气。农田那只幼犬会把肉和菜送过来，雪豹会把鱼丢进冰桶里，我把这些做成随手就能拿的饭，大家不用硬凑一桌也能吃上。这样挺好。”
    > （对，晚上我还是会把盛满的盘子放在关着的房门外头。别说得那么别扭。）
  - **RU**:
    > «Кому-то надо. В мороз латексные тела быстро сжигают еду, а половина народу внизу забывает поесть, пока не начнёт огрызаться. Юин приносит мясо и овощи из загона, снежный барс скидывает рыбу в бочку со льдом, а я делаю из этого то, что можно схватить и уйти, без общих застолий. Так всем спокойнее.»
    > (И да, я всё равно оставляю полную тарелку у закрытых дверей на ночь. Не делай из этого событие.)

- **Option 2 (`about_fox`)**: "You and the Gray Fox upstairs seem like total opposites." / “你和楼上那只灰狐性格看着完全相反。” / «Вы с серой лисой наверху кажетесь полными противоположностями.»
  - **EN**:
    > *His ears twitch back, and he stares hard at the soup pot.*
    > "She talks enough for two. Saves me the trouble. She brings half the forest in on her paws, leaves berry stems on the stairs, and hugs me when my hands are covered in flour. Terrible habits."
    > (...If she stays out past dusk without pinging me, I end up pacing by the ladder for an hour. She knows it too.)
  - **ZH**:
    > *他耳朵往后一抖，死死盯着汤锅。*
    > “她一个人能说两个人的份，正好省我的事。每次回来爪子上能带进半片林子的雪，楼梯上掉一溜浆果梗，还老趁我满手面粉的时候突然抱过来。坏毛病一堆。”
    > （……要是天黑了她还没给我传消息，我就得在梯子边来回踱一个小时。她也知道我这样。）
  - **RU**:
    > *Его уши дёргаются назад, и он упорно смотрит в кастрюлю.*
    > «Она болтает за двоих. Мне же проще. Притаскивает на лапах половину леса, бросает веточки от ягод на лестнице и обнимает меня, когда у меня руки в муке. Ужасные привычки.»
    > (...Если она задерживается после сумерек и не откликается, я потом час хожу кругами у лестницы. И она прекрасно это знает.)

- **Option 3 (`story_clinic`)**: "She told me you followed her into the clinic." / “她告诉我，当初是你跟着她进了诊所。” / «Она рассказала, что ты пошёл за ней в клинику.»
  - **EN**:
    > *He sets the spoon down neatly on the edge of the counter.*
    > "She tells strangers too much. Look, it wasn't some grand speech. She was sick. The cure was going to change her into something that wouldn't age the way I would. Waiting around as a human while she outlived me by centuries sounded stupid. So I signed the paper. That's all."
    > (I'd sign it again tomorrow. Just don't make me say it out loud in front of people.)
  - **ZH**:
    > *他把木勺整整齐齐搁在吧台边上。*
    > “她跟外人说得太多了。听着，没什么大道理。她生病了，疗法会把她变成不会跟我一起变老的样子。我作为一个人类干等着，看她比我多活几百年，听着就蠢透了。所以我签了字，就这么简单。”
    > （就算重来一遍，明天我还是会签。只是别逼我当着大家的面说出口。）
  - **RU**:
    > *Он аккуратно кладёт ложку на край стойки.*
    > «Слишком много она чужим рассказывает. Послушай, там не было никаких громких речей. Она была больна. Лекарство должно было сделать её такой, которая не стареет, как я. Сидеть человеком и ждать, пока она переживёт меня на века, казалось глупостью. Вот я и подписал бумагу. Всё.»
    > (Я бы и завтра подписал снова. Только не заставляй говорить это вслух при людях.)

- **Option 4 (`about_cold`)**: "Why do you run thirty air conditioners in a snowy taiga?" / “雪针叶林里本来就冷，你们怎么还装了三十台空调？” / «Зачем вам тридцать кондиционеров посреди снежной тайги?»
  - **EN**:
    > "Stoves, blast furnaces, eight bodies under one roof. Seal a wooden burrow tight enough to keep blizzards out and it turns into a sauna in two hours. Half of us have mountain coats built for blizzards, not kitchen steam. Blue Wolf keeps the fans humming, I haul fresh blue ice down to the lower room. If you're shivering, stand near the oven for five minutes."
  - **ZH**:
    > “灶台、高炉，屋顶底下还挤着八个活物。木头地穴封得严实到能挡暴风雪，不出两小时里头就能闷成桑拿房。我们一半人长的是扛风雪的高山厚毛，扛不住厨房热气。蓝狼负责让排风扇别停，我负责往楼下卧室搬新的蓝冰。要是冷得发抖，去炉子边站五分钟。”
  - **RU**:
    > «Плиты, плавильные печи, восемь тел под одной крышей. Закупорь деревянную нору от метели, и через два часа внутри баня. У половины из нас горная шуба под буран, а не под кухонный пар. Синий волк следит за вентиляторами, я таскаю свежий синий лёд в нижнюю комнату. Если замёрз, постой пять минут у печки.»

- **Option 5 (`about_downstairs`)**: "What's with the unplugged computer in the room at the bottom?" / “最下面那间卧室里，那台拔了线的电脑是怎么回事？” / «А что за отключённый компьютер в самой нижней комнате?»
  - **EN**:
    > *His eyes narrow.*
    > "You went into our bedroom? ...Never mind, forgot we never put a lock on the stairs. That terminal used to catch old facility pings from whoever calls themselves 'Society' down south. Census requests, health surveys, polite reminders to check in. I pulled the cable out three winters ago. Nobody in this house wants a clipboard showing up at the door."
  - **ZH**:
    > *他眯起了眼睛。*
    > “你进我们卧室了？……算了，忘了楼梯口一直没装锁。那台终端以前老收到南边自称‘社会’的旧设施信号，什么人口普查、健康回访，客客气气提醒我们去报到。三个冬天前我把线拔了。这屋里没人想看到拿着写字板的人敲门。”
  - **RU**:
    > *Он прищуривается.*
    > «Ты заходил в нашу спальню? ...Ладно, забыл, что мы так и не поставили замок на лестницу. Этот терминал раньше ловил старые сигналы с юга от тех, кто зовёт себя "Обществом". Перепись, опросы о здоровье, вежливые просьбы отметиться. Три зимы назад я выдернул кабель. Никто в этом доме не хочет видеть у порога кого-то с планшетом.»

#### 5. 招募与转化分支 (`enrol` / `explain_form` / `welcome_*` / `goodbye`)
- **Option (`enrol`)**: "Can I stay with the Burrow?" / “我能留在你们地穴吗？” / «Можно мне остаться в Норе?»
  - **EN**: "If the fox cleared you, I'm not arguing. Wipe your feet, wash your bowls, don't wake people up for small talk. Keep your form or take mine if you want something heavy and quiet for the cold."
  - **ZH**: “既然狐狸放你过了，我没意见。进门蹭脚，用完碗自己洗，别为闲聊把人吵醒。保持你现在的样子，或者要我的形态也行——耐寒，也安静。”
  - **RU**: «Если лиса тебя пропустила, спорить не стану. Вытирай ноги, мой за собой миски, не буди людей ради болтовни. Можешь остаться в своей форме или взять мою, если нужна плотная и спокойная шкура для холода.»
- **Option (`explain_form`)**: "What's the Manul form like?" / “兔狲形态是什么感觉？” / «Каково быть манулом?»
  - **EN**: "Thick fur, short legs, low center of gravity. Good for sitting still in the snow without freezing. When your paws get cold, you just crouch and tuck them onto your tail. Not built for sprinting across open plains, and honestly, why would you want to?"
  - **ZH**: “毛厚，腿短，重心低。适合在雪地里一动不动待着还不冷。爪子冻着了，蹲下来踩在尾巴上就行。不是拿来在平原上狂奔的身板，说实话，谁没事想狂奔啊？”
  - **RU**: «Густая шерсть, короткие лапы, низкий центр тяжести. Удобно сидеть в снегу и не мёрзнуть. Когда лапы стынут, просто приседаешь и ставишь их на хвост. Не для бега по открытой равнине, да и, честно говоря, зачем бы это.»
- **`welcome_human`**:
  - **EN**: "Alright. Grab a spare bed in the dorm wing. There's extra stew in the left pot."
  - **ZH**: “行吧。宿舍区随便挑张空床。左边锅里还剩着炖菜。”
  - **RU**: «Ладно. Выбирай свободную койку в жилом крыле. В левой кастрюле ещё осталось рагу.»
- **`welcome_latex`**:
  - **EN**: "Fine by me. (Glad you're staying. Don't tell the fox I said that, she'll gloat.)"
  - **ZH**: “我没意见。（你能留下挺好的。别告诉狐狸我说过这话，她会得意坏的。）”
  - **RU**: «Я не против. (Рад, что остаёшься. Только не говори лисе, она зазнается.)»
- **`welcome_copy`**:
  - **EN**: "Steady now. Your tail's heavier than it looks, don't knock the flower pot off the bar. (Fits you well enough. Go rest by the ice blocks if the kitchen gets too hot.)"
  - **ZH**: “站稳点。尾巴比看着沉，别把吧台上的花盆扫下去了。（倒挺合身的。厨房要是太热，去蓝冰块边上歇会儿。）”
  - **RU**: «Поаккуратнее. Хвост тяжелее, чем кажется, не смахни горшок со стойки. (Сидит вполне неплохо. Если на кухне станет жарко, отдохни у блоков льда.)»
- **`welcome_permanent`**:
  - **EN**: "Done. Take a minute to get used to the fur. (If you want a bowl of soup before you go back upstairs, just sit on the stool. No need to talk.)"
  - **ZH**: “好了。花一分钟习惯下这身毛吧。（上楼前要是想喝碗汤，直接坐高脚凳上就行，不用说话。）”
  - **RU**: «Готово. Привыкни минуту к шерсти. (Если захочешь миску супа, прежде чем идти наверх, просто сядь на табурет. Говорить необязательно.)»
- **`goodbye`**:
  - **EN**: "Close the trapdoor on your way out."
  - **ZH**: “出去时把活板门关上。”
  - **RU**: «Прикрой люк, когда пойдёшь.»

---

## 三、核心对话树文本：6 位社区居民

> 统一语气：带有类似「叛逆青少年式的社交回避」——嘴短、怕生、讨厌突发寒暄、喜欢半关门待在自己角落；绝无恶意，真有需要时仍会默默搭把手。

### 1. 阁楼雌性雪豹 (`resident_loft` · 与灰狐首领同住上层阁楼)
- **开场 (`root`)**:
  - **EN (Human)**: *She pulls a light-blue pillow closer to her chest and glances at you over the top of a book.* "...Oh. Thought you were the fox. Did you need something, or are you just standing there?"
  - **ZH (Human)**: *她把浅蓝抱枕往怀里抱紧了点，从书沿上方瞥了你一眼。* “……哦。还以为是狐狸。你有事，还是就站这儿看？”
  - **RU (Human)**: *Она прижимает к груди голубую подушку и смотрит на тебя поверх книги.* «...А. Думала, это лиса. Тебе что-то нужно или ты просто так стоишь?»
  - **EN (Transformed)**: *Ear flicks.* "Hey." (Great, another visitor. Look, if you want a pillow, take the dark blue one in the corner. Just don't talk while I'm reading.)
  - **ZH (Transformed)**: *耳朵动了一下。* “嗨。”（行吧，又来个访客。想要抱枕就拿角落那个深蓝的，我看书时别搭话就行。）
  - **RU (Transformed)**: *Ухо дёргается.* «Привет.» (Отлично, ещё один гость. Слушай, если нужна подушка, бери тёмно-синюю в углу. Только не болтай, пока я читаю.)
- **Q1**: "Don't you mind sharing the loft with the Gray Fox?" / “你不介意跟灰狐合住阁楼吗？” / «Тебе не мешает делить лофт с серой лисой?»
  - **EN**: "She's out half the day anyway. When she's back, she drops sweet berries by the shelf and passes out on her side. I can deal with that. Better than downstairs where people walk past my door."
  - **ZH**: “她反正半天都在外面。回来也就是往书架边丢点甜浆果，然后倒在自己那边睡。这我能接受，总比住楼下老有人从门口路过强。”
  - **RU**: «Её всё равно полдня нет. А когда возвращается, скидывает сладкие ягоды у полки и вырубается на своей половине. С этим я могу жить. Лучше, чем внизу, где постоянно шастают мимо двери.»
- **Q2**: "What are you reading?" / “你在看什么书？” / «Что ты читаешь?»
  - **EN**: "Old weather almanac from before the snows got this heavy. Boring stuff. That's the point. Nobody tries to start a debate with you over rainfall charts."
  - **ZH**: “以前雪还没下这么厚时的旧气象年鉴。挺无聊的，我要的就是无聊——没人会拉着你争论降雨量表格。”
  - **RU**: «Старый метеосправочник ещё до того, как пошли такие снегопады. Скукота. В этом и смысл. Никто не полезет спорить с тобой из-за таблиц осадков.»

### 2. 内工坊雄性蓝狼 (`resident_workshop` · 内工坊)
- **开场 (`root`)**:
  - **EN (Human)**: *He doesn't look up from the anvil right away.* "Stand back from the blast furnace. If something's broken, leave it on the bench. If you're here to chat, go upstairs."
  - **ZH (Human)**: *他没立刻从铁砧前抬头。* “离高炉远点。有东西坏了就放工作台上；要是来找人聊天的，上楼去。”
  - **RU (Human)**: *Он не сразу отрывается от наковальни.* «Отойди от плавильной печи. Если что-то сломалось, клади на верстак. Если пришёл поболтать — иди наверх.»
  - **EN (Transformed)**: *Short huff.* "Yeah?" (Please don't ask me how my day is going. I've got three vent fans rattling in the dorm wing.)
  - **ZH (Transformed)**: *短促地哼了一声。* “干嘛？”（求你别问我今天过得怎么样，宿舍区还有三个排风扇在响呢。）
  - **RU (Transformed)**: *Короткий выдох.* «Да?» (Только не спрашивай, как прошёл день. У меня в жилом крыле три вентилятора гремят.)
- **Q1**: "You handle all the cooling gear in here?" / “这里的制冷设备都是你在维护？” / «Вся эта охлаждающая техника на тебе?»
  - **EN**: "Thirty AC units, thirty-three vents, and a bunch of people who panic if the hallway warms up two degrees. Somebody had to learn the wiring. The fox brings back spare parts from ruined labs, I splice them in before they rust."
  - **ZH**: “三十台空调、三十三个排风扇，外加一群走廊升温两度就要炸毛的家伙。总得有人懂接线。狐狸从废实验室带回备用零件，我趁它们生锈前接上去。”
  - **RU**: «Тридцать кондиционеров, тридцать три вентилятора и куча народу, которые впадают в панику от лишних двух градусов в коридоре. Кому-то пришлось разобраться в проводке. Лиса тащит запчасти из заброшенных лабораторий, я впаиваю их, пока не сгнили.»
- **Q2**: "Why keep blue orchids next to an anvil?" / “铁砧旁边怎么还摆着兰花？” / «Зачем держать орхидеи рядом с наковальней?»
  - **EN**: "...Manul put them there so the room wouldn't smell like hot iron all the time. I haven't thrown them out yet. Don't stare at them."
  - **ZH**: “……兔狲放的，说免得屋里整天一股烫铁味。我还没来得及扔。别老盯着看。”
  - **RU**: «...Манул поставил, чтобы в комнате не воняло всё время горелым железом. Я просто ещё не выкинул. Не пялься на них.»

### 3. 鱼塘雄性雪豹 (`resident_pond` · 室内鱼塘)
- **开场 (`root`)**:
  - **EN (Human)**: *He sits on a blue pillow beside the water, watching a salmon drift past a lily pad.* "Shh. You're making ripples. What?"
  - **ZH (Human)**: *他坐在水边的蓝抱枕上，盯着一条鲑鱼从睡莲下划过去。* “嘘，你把水震出波纹了。什么事？”
  - **RU (Human)**: *Он сидит на синей подушке у воды, глядя, как лосось проплывает под кувшинкой.* «Тшш. От тебя рябь по воде. Чего тебе?»
  - **EN (Transformed)**: *Tail twitches once against the stone.* "Keep your boots off the edge." (Finally got the pond quiet and someone clomps in. Great.)
  - **ZH (Transformed)**: *尾巴在石地上拍了一下。* “靴子别踩池边。”（池塘好不容易静下来，又有人咚咚咚闯进来了。真行。）
  - **RU (Transformed)**: *Хвост разок бьёт по камню.* «Ботинки с края убери.» (Только пруд затих, и кто-то притопал. Прекрасно.)
- **Q1**: "How's the fishing in an indoor pond?" / “在室内鱼塘钓鱼怎么样？” / «Как вообще рыбалка в крытом пруду?»
  - **EN**: "Predictable. Which is why I like it. Cod, salmon, no freezing wind in my ears, no strangers asking if anything's biting. When the barrel fills up, I haul it to the kitchen and come right back."
  - **ZH**: “很省心，我就图这个。鳕鱼、鲑鱼，没冷风往耳朵里灌，也没生人凑过来问咬没咬钩。桶装满了我就拎去厨房，拎完立刻回来。”
  - **RU**: «Предсказуемо. За это и люблю. Треска, лосось, никакой метели в уши и никаких прохожих с вопросом "ну что, клюёт?". Как бочка наполняется, отношу на кухню и сразу назад.»
- **Q2**: "Why is there just one sea pickle at the bottom?" / “池底怎么只放了一根海泡菜？” / «Почему на дне всего один морской огурец?»
  - **EN**: "Enough light to see the fish, not enough to give me a headache. The fox wanted to put lanterns all around the rim. I told her no."
  - **ZH**: “够看清鱼，又不会亮得头疼。狐狸本来想绕着池边摆一圈灯笼，我没让。”
  - **RU**: «Света хватает, чтобы видеть рыбу, и не болит голова. Лиса хотела обставить весь край фонарями. Я не дал.»

### 4. 农田幼犬 (`resident_farm` · 室内农田与畜栏)
- **开场 (`root`)**:
  - **EN (Human)**: *They shut the dark oak fence gate firmly behind them.* "Hey, latch the gate if you walk through. Chickens get into the hallway once and I'm chasing feathers for an hour."
  - **ZH (Human)**: *它把深色橡木栅栏门在身后牢牢扣上。* “喂，进出记得扣好门栓。鸡只要溜进走廊一次，我就得满屋追着鸡毛跑一小时。”
  - **RU (Human)**: *Он плотно захлопывает за собой калитку из тёмного дуба.* «Эй, закрывай засов, если проходишь. Стоит курам выскочить в коридор — потом час за перьями гоняться.»
  - **EN (Transformed)**: *Quick glance over the fence.* "Don't lean on the rails." (Ugh, people watching me work makes me drop the wheat. Just grab a carrot from the barrel if you want one.)
  - **ZH (Transformed)**: *隔着栅栏扫了你一眼。* “别趴在栏杆上。”（啧，有人盯着干活我老掉麦子。想吃胡萝卜自己去桶里拿一根就行。）
  - **RU (Transformed)**: *Быстрый взгляд через ограду.* «Не облокачивайся на забор.» (Терпеть не могу, когда над душой стоят — сразу пшеница из рук валится. Хочешь морковку, бери из бочки.)
- **Q1**: "Looks like a lot of work for one person." / “这些地和牲口一个人照看挺累吧。” / «Похоже, работы тут многовато для одного.»
  - **EN**: "Not really. Cows don't make small talk, sheep don't knock on my door at midnight, and carrots stay where I plant them. Way easier than dealing with people."
  - **ZH**: “还好。牛不跟我寒暄，羊不会半夜敲我门，胡萝卜种在哪儿就老实待在哪儿。比跟人打交道轻松多了。”
  - **RU**: «Да не особо. Коровы не лезут с болтовнёй, овцы не стучат в дверь в полночь, а морковка сидит там, куда посадишь. Куда проще, чем с людьми.»
- **Q2**: "Do you ever go up to the kitchen?" / “你平时会上厨房去吗？” / «Ты вообще заходишь на кухню?»
  - **EN**: "Only to drop crates by the double chest. Manul leaves a plate of salad on the corner for me so we don't have to stand around chatting. Best boss I've ever had."
  - **ZH**: “也就往双人大箱子旁边放货箱的时候去一趟。兔狲会把一盘沙拉留在我常去的角落，省得我俩站那儿干聊。我遇到过最好的老板。”
  - **RU**: «Только скинуть ящики у двойного сундука. Манул оставляет для меня тарелку салата на углу, чтобы нам не приходилось стоять и разговаривать. Лучший начальник на свете.»

### 5. 书房幼犬 (`resident_library` · 宿舍阅览角)
- **开场 (`root`)**:
  - **EN (Human)**: *They peek around a stack of books on the lab table.* "Um. Occupied. The hallway goes around behind you."
  - **ZH (Human)**: *它从实验台上的书堆后面探出半个头。* “嗯。这儿有人了。走廊在你身后绕过去。”
  - **RU (Human)**: *Он выглядывает из-за стопки книг на лабораторном столе.* «Эм. Занято. Коридор идёт вон там, сзади.»
  - **EN (Transformed)**: *Pulls a book higher to hide their muzzle.* "Hi." (Please just be passing through. Please don't ask me to explain the bookshelves.)
  - **ZH (Transformed)**: *把书举高了些，挡住鼻尖。* “你好。”（拜托只是路过吧，千万别让我挨个介绍书架。）
  - **RU (Transformed)**: *Поднимает книгу повыше, пряча морду.* «Привет.» (Пожалуйста, будь просто проездом. Только не проси рассказывать про полки.)
- **Q1**: "Why is there a stuffed pumpkin sitting on the study desk?" / “书桌上怎么摆着一个填馅南瓜？” / «Почему на письменном столе стоит фаршированная тыква?»
  - **EN**: "Because Manul dropped it off before I finished a chapter and I forgot to eat it. Still smells fine. No, you can't have half... okay, fine, take a scoop if it makes you leave faster."
  - **ZH**: “因为兔狲在我看完这一章前送过来的，我忘吃了。闻着还挺香。不行，不能分你一半……行吧行吧，能让你早点走的话，舀一勺拿走。”
  - **RU**: «Потому что Манул принёс её раньше, чем я дочитал главу, а я забыл поесть. Пахнет ещё нормально. Нет, половину не дам... ну ладно, зачерпни ложку, если от этого уйдёшь быстрее.»
- **Q2**: "Do people in the Burrow ever hang out together?" / “地穴里的人平时会一起聚着玩吗？” / «Жители Норы вообще собираются вместе?»
  - **EN**: "Sometimes we all sit in the kitchen at the same time and look at different walls while eating. Counts as a party around here. Gray Fox tries to get us talking once a month and usually gives up after five minutes."
  - **ZH**: “有时候大家会同时坐在厨房里，边吃饭边各自盯着不同的墙。在这儿这就够算聚会了。灰狐每个月都想拉大家聊一次天，通常五分钟就放弃了。”
  - **RU**: «Иногда мы все одновременно сидим на кухне, едим и смотрим в разные стены. По местным меркам это уже вечеринка. Серая лиса раз в месяц пытается нас разговорить и обычно сдаётся через пять минут.»

### 6. 走廊粉红双足飞龙 (`resident_dorm` · 宿舍走廊与纸箱储藏室)
- **开场 (`root`)**:
  - **EN (Human)**: *They lean against the spruce frame near the cardboard room, wings tucked tight.* "Whoa, slow down. Bedrooms are down this hall. Unless one of them has your name on the door, keep moving."
  - **ZH (Human)**: *它靠在纸箱室旁的云杉门框边，翅膀紧紧收着。* “哎，慢着点。这条走廊过去全是卧室。除非哪扇门上写着你名字，不然继续往前走。”
  - **RU (Human)**: *Опирается на еловый косяк у кладовки с коробками, плотно прижав крылья.* «Эй, потише. Дальше по коридору спальни. Если на двери нет твоего имени, проходи мимо.»
  - **EN (Transformed)**: *Rolls eyes, flicks tail.* "Yeah, yeah, new guy." (Not in the mood for a tour. Just don't kick my cardboard boxes.)
  - **ZH (Transformed)**: *翻了个白眼，甩了甩尾巴。* “行吧行吧，新来的。”（没心情当导游。别踢我的纸箱子就行。）
  - **RU (Transformed)**: *Закатывает глаза и дёргает хвостом.* «Ага, ага, новенький.» (Нет настроения водить экскурсии. Только коробки мои не пинай.)
- **Q1**: "What's in all those cardboard boxes and crates?" / “那一堆纸箱和板条箱里装的都是什么？” / «Что во всех этих картонных коробках и ящиках?»
  - **EN**: "Stuff the fox hauled back from old labs, extra rations, dry fruit, wool blankets, and my own box in the back corner that says 'DO NOT OPEN.' That means you too. I like having a stack of boxes around. Blocks the draft."
  - **ZH**: “狐狸从废实验室拖回来的杂物、备用口粮、干果、羊毛毯，还有最里角我自己的那个写着‘禁止打开’的箱子。对你也一样有效。我喜欢屋里堆着纸箱，挡风。”
  - **RU**: «Всякое, что лиса притащила из старых лабораторий, запасные пайки, сухофрукты, шерстяные одеяла и моя личная коробка в дальнем углу с надписью "НЕ ОТКРЫВАТЬ". Тебя это тоже касается. Мне нравится, когда вокруг коробки — от сквозняка закрывают.»
- **Q2**: "You don't seem too thrilled about guests." / “你看着好像不太欢迎客人。” / «Похоже, гостям ты не особо рад.»
  - **EN**: "Nothing personal. I just moved underground so I wouldn't have to smile at strangers before noon. Look, you're not loud, and you didn't touch the paintings in the hall, so you're already doing better than the last wanderer. Just... give me ten feet of space, alright?"
  - **ZH**: “不是针对你。我搬到地下住，就是为了中午之前不用冲陌生人假笑。听着，你脚步不吵，也没乱摸走廊里的画，已经比上一个路人强多了。只是……离我三米远点，行吗？”
  - **RU**: «Ничего личного. Я просто перебрался под землю, чтобы не улыбаться незнакомцам до полудня. Слушай, ты не шумишь и не трогал картины в коридоре, так что уже лучше прошлого залётного. Просто... держи дистанцию метра три, ладно?»

---

## 四、地穴剧情片段文本（光盘、成书、剪切板、便签）

### 1. 电脑光盘 / 终端日志（`LOCAL_LOG_14809.DSK` · 下层主卧电脑 `(28, 2, 33)`）
- **EN**:
  > `[BURROW-01 LOCAL MONITOR // COOLING LOOP]`
  > `UPPER LOFT: 6.2°C  |  KITCHEN: 14.8°C  |  LOWER SUITE: 2.1°C`
  > `BLUE ICE RESERVES: OK (LAST SWAPPED BY M.)`
  >
  > `[EXTERNAL PING - RELAY 04 // CRISSCROSS-SOCIETY]`
  > `> Automated census check: Taiga Sector 7-North.`
  > `> Missing entry flagged in Dr. O. field archive ("Manul, bipedal colloidal").`
  > `> Please confirm resident headcount and supply status.`
  >
  > `[LOCAL ENTRY - MANUAL OVERRIDE]`
  > `> Link severed physically at wall jack.`
  > `> Note (M.): Left the fan monitor plugged in. Pulled the outside wire. They can count trees instead.`
- **ZH**:
  > `[BURROW-01 本地监控 // 制冷循环]`
  > `上层阁楼：6.2°C  |  厨房：14.8°C  |  下层主卧：2.1°C`
  > `蓝冰储备：正常（上次更换：M.）`
  >
  > `[外部信号 - 中继 04 // 十字路口-社会部门]`
  > `> 自动人口普查核验：针叶林 7-北区。`
  > `> 标记到 O 博士野外档案缺失条目（“兔狲，双足胶体”）。`
  > `> 请确认常住人数与补给状态。`
  >
  > `[本地记录 - 手动覆盖]`
  > `> 已于墙面接口处物理断开连接。`
  > `> 备注（M.）：风扇监控还插着，外线拔了。让他们去数树吧。`
- **RU**:
  > `[BURROW-01 ЛОКАЛЬНЫЙ МОНИТОР // КОНТУР ОХЛАЖДЕНИЯ]`
  > `ВЕРХНИЙ ЛОФТ: 6.2°C  |  КУХНЯ: 14.8°C  |  НИЖНЯЯ СПАЛЬНЯ: 2.1°C`
  > `ЗАПАС СИНЕГО ЛЬДА: НОРМА (ПОСЛЕДНЯЯ ЗАМЕНА: М.)`
  >
  > `[ВНЕШНИЙ ЗАПРОС - РЕТРАНСЛЯТОР 04 // ПЕРЕКРЁСТОК-ОБЩЕСТВО]`
  > `> Автоматическая сверка переписи: Таёжный сектор 7-Север.`
  > `> Зафиксирована пропажа записи в полевом архиве д-ра О. («Манул, прямоходящий коллоидный»).`
  > `> Просьба подтвердить число жильцов и состояние запасов.`
  >
  > `[ЛОКАЛЬНАЯ ЗАПИСЬ - РУЧНОЕ ВМЕШАТЕЛЬСТВО]`
  > `> Линия физически отключена у настенного разъёма.`
  > `> Примечание (М.): Монитор вентиляторов оставил. Внешний кабель выдернул. Пусть лучше деревья считают.`

### 2. 成书 1：《雪落之前》（*Before the Snow* · 下层主卧书架 / 床头）
- **EN**:
  > **Page 1**
  > Clinic room 4 smelled like bleach and cheap oranges. She was already half asleep in the cot when the nurse handed me the clipboard. My hands were shaking, mostly because the hallway air conditioning was too high.
  >
  > The clerk asked twice if I had symptoms. I told him no. He looked at her chart, looked back at me, and stopped asking.
  >
  > **Page 2**
  > First month after, neither of us could figure out stairs with new paws. She climbed onto the clinic roof on day three anyway and yelled down until security brought a ladder.
  >
  > When the exodus notices went up on every screen in the district, we packed two pans, a wool quilt, and a box of canned peaches and walked north until the roads ran out.
  >
  > **Page 3** *(scrawled in lighter ink at the bottom)*
  > *You forgot to write down that you tried to tuck your new tail into your coat pocket for a whole week. — F.*
  > *Stop reading my notebook. — M.*
- **ZH**:
  > **第 1 页**
  > 诊所 4 号房有一股漂白水和廉价橘子的味道。护士把写字板递给我时，她已经在病床上睡了一半。我的手一直在抖，多半是因为走廊空调开得太足。
  >
  > 登记员问了我两遍有没有症状。我说没有。他看了看她的病历，又回头看了看我，就没再问了。
  >
  > **第 2 页**
  > 那之后的第一个月，我们俩长了新爪子都不会走楼梯。可她第三天还是爬上了诊所屋顶，在上面一直往下喊，直到保安搬来梯子。
  >
  > 等区里每块屏幕都挂出远航通知时，我们装了两口平底锅、一床羊毛被、一箱桃子罐头，一直往北走到没路为止。
  >
  > **第 3 页** *（页底用浅色墨水潦草补写）*
  > *你漏写了一件事：你整整一个星期都在试图把新尾巴塞进外套口袋里。——F.*
  > *别偷看我本子。——M.*
- **RU**:
  > **Страница 1**
  > В четвёртой палате пахло хлоркой и дешёвыми апельсинами. Когда медсестра протянула мне планшет, она уже почти спала на койке. У меня дрожали руки — в основном потому, что в коридоре слишком сильно работал кондиционер.
  >
  > Регистратор дважды спросил, есть ли у меня симптомы. Я сказал, что нет. Он глянул на её карту, потом снова на меня и больше не спрашивал.
  >
  > **Страница 2**
  > В первый месяц мы оба не могли нормально ходить по лестницам на новых лапах. Но на третий день она всё равно залезла на крышу клиники и кричала оттуда, пока охрана не притащила лестницу.
  >
  > Когда на всех экранах района появились объявления об исходе, мы сложили две сковородки, шерстяное одеяло и коробку консервированных персиков и пошли на север, пока не кончились дороги.
  >
  > **Страница 3** *(нацарапано более светлыми чернилами внизу)*
  > *Ты забыл написать, что целую неделю пытался спрятать новый хвост в карман куртки. — Л.*
  > *Хватит читать мою тетрадь. — М.*

### 3. 成书 2：《地穴住户守则》（*Burrow House Rules* · 厨房公共双箱 / 走廊）
- **EN**:
  > **Page 1**
  > 1. Knock before opening any bedroom door. If nobody answers, walk away.
  > 2. Knock snow off your paws at the entry ladder. Blue Wolf is tired of mopping ice near the wiring.
  > 3. Do not turn off the air conditioners. Put on a sweater instead.
  > 4. Take what you need from the shared kitchen chest. Leave at least two cans of peaches alone.
  >
  > **Page 2**
  > 5. Farm gate stays latched. Every time.
  > 6. If you do not want to eat around other people, leave your bowl outside your door before dusk. I will refill it when the hallway is empty.
  >
  > *(scribbled underneath in messy handwriting)*
  > 7. If a strange human wanders down the stairs, send them up to the loft before everyone hisses at them. — Fox
- **ZH**:
  > **第 1 页**
  > 1. 开任何卧室门前先敲门。没人应就走开。
  > 2. 在入口梯子处抖干净爪子上的雪。蓝狼已经拖烦了电线旁边的冰水。
  > 3. 不准关空调。冷了自己穿毛衣。
  > 4. 厨房公共箱子里的东西按需自取。至少留两罐桃子别动。
  >
  > **第 2 页**
  > 5. 农田栅栏门必须扣死。每一次。
  > 6. 要是不想跟别人一块吃饭，天黑前把碗放在房门外。走廊没人时我会给你盛满。
  >
  > *（下面歪歪扭扭加了一行）*
  > 7. 要是有陌生人类顺着楼梯晃下来，趁大家朝他哈气之前先打发到阁楼上来。——狐狸
- **RU**:
  > **Страница 1**
  > 1. Стучитесь перед тем, как открыть любую спальню. Если не отвечают — уходите.
  > 2. Отряхивайте снег с лап у входной лестницы. Синий волк устал вытирать лужи рядом с проводкой.
  > 3. Не выключайте кондиционеры. Просто наденьте свитер.
  > 4. Из общего сундука на кухне берите сколько нужно. Оставьте нетронутыми хотя бы две банки персиков.
  >
  > **Страница 2**
  > 5. Калитку в загоне закрывать на засов. Всегда.
  > 6. Если не хочется есть при людях, оставь миску у своей двери до сумерек. Я наполню её, когда в коридоре никого не будет.
  >
  > *(приписано ниже неровным почерком)*
  > 7. Если вниз по лестнице забредёт незнакомый человек, отправьте его на лофт, пока все на него не нашипели. — Лиса

### 4. 剪切板：工坊巡检单（*Vent & Cooling Check* · 内工坊实验台）
- **EN**:
  > `BURROW MAINTENANCE CHECK — BLUE WOLF`
  > `[x] Kitchen exhaust fan — oiled, stopped screeching`
  > `[x] Dorm AC #14 — swapped fuse`
  > `[ ] Loft vent grate — clogged with sweet berry skins AGAIN`
  > `[x] Lower suite blue ice — replaced 2 blocks`
  >
  > `Note: Whoever keeps leaning wet fishing rods against the anvil, stop. You know who you are.`
- **ZH**:
  > `地穴维护巡检表 — 蓝狼`
  > `[x] 厨房排风扇 — 上油了，不尖叫了`
  > `[x] 宿舍区 14 号空调 — 换了保险丝`
  > `[ ] 阁楼通风栅栏 — 又被甜浆果皮堵住了`
  > `[x] 下层主卧蓝冰 — 换了 2 块`
  >
  > `备注：谁老把湿钓竿往铁砧上靠，别放了。你自己心里有数。`
- **RU**:
  > `ПРОВЕРКА ОБСЛУЖИВАНИЯ НОРЫ — СИНИЙ ВОЛК`
  > `[x] Вытяжка на кухне — смазал, перестала визжать`
  > `[x] Кондиционер №14 в спальнях — поменял предохранитель`
  > `[ ] Решётка вентиляции в лофте — ОПЯТЬ забита шкурками от ягод`
  > `[x] Синий лёд в нижней спальне — заменил 2 блока`
  >
  > `Примечание: Кто постоянно прислоняет мокрые удочки к наковальне — хватит. Ты знаешь, о ком я.`

### 5. 便签 1：厨房吧台留言条（*Kitchen Counter Note* · 厨房吧台）
- **EN**:
  > `M.:`
  > `Went out along the west ridge. Saw two lab drones circling the far pines, threw snowballs at one until it turned south. Back before dark. Save me the crispy corner of the pie.`
  > `P.S. I didn't track snow past the first step this time. Look!`
  >
  > *(written underneath in neat small letters)*
  > `You tracked snow to the third step. Pie is in the oven. — M.`
- **ZH**:
  > `M.：`
  > `沿着西山脊出去一趟。看见两架实验室无人机在远处松林转悠，朝其中一架扔雪球直到它往南飞走了。天黑前回来，给我留馅饼烤脆的那一角。`
  > `附：这次我没把雪踩过第一级台阶，你看！`
  >
  > *（下面工整的小字回复）*
  > `你踩到了第三级台阶。馅饼在炉子里温着。——M.`
- **RU**:
  > `М.:`
  > `Ушла вдоль западного хребта. Видела два лабораторных дрона у дальних сосен, закидала один снежками, пока не свернул на юг. Вернусь до темноты. Оставь мне хрустящий уголок пирога.`
  > `P.S. В этот раз я не натаскала снег дальше первой ступеньки. Глянь!`
  >
  > *(приписано ниже мелким аккуратным почерком)*
  > `Ты натаскала до третьей ступеньки. Пирог греется в печке. — М.`

### 6. 便签 2：纸箱储藏室清单（*Storage Room Tally* · 走廊纸箱储藏室）
- **EN**:
  > `BOXES — DO NOT MOVE`
  > `- 3 crates canned fruit (from the old warehouse east of the river)`
  > `- 2 boxes wool blankets (dry, check for moths)`
  > `- 1 crate dried orange rations (tastes like chalk, Yuin likes them anyway)`
  > `- 1 taped box behind the iron crate — MINE. DO NOT OPEN. (No, it isn't snacks. It's thick socks and three comic books. Still don't touch it.)`
- **ZH**:
  > `纸箱清单 — 别乱搬`
  > `- 3 箱水果罐头（河东边旧仓库搬来的）`
  > `- 2 箱羊毛毯（干的，注意防蛀）`
  > `- 1 箱干橘子口粮（嚼着像粉笔，不过幼犬挺爱吃）`
  > `- 铁板条箱后面贴胶带的那个箱子 — 我的，禁止打开。（不是零食，是厚袜子和三本漫画。那也不准碰。）`
- **RU**:
  > `КОРОБКИ — НЕ ПЕРЕСТАВЛЯТЬ`
  > `- 3 ящика фруктовых консервов (со старого склада к востоку от реки)`
  > `- 2 коробки шерстяных одеял (сухие, проверять на моль)`
  > `- 1 ящик сушёных апельсиновых пайков (на вкус как мел, но юину нравится)`
  > `- 1 заклеенная скотчем коробка за железным ящиком — МОЯ. НЕ ОТКРЫВАТЬ. (Нет, там не еда. Там толстые носки и три комикса. Всё равно не трогай.)`

---

## 五、待确认项

1. **两位首领称呼**：保持头衔（`灰狐首领 / 兔狲首领`），还是起固定名字？
2. **入驻与转化入口**：保持双入口（灰狐给灰狐形态、兔狲给兔狲形态，两位均可办理加入），还是由其中一位统一负责？
3. **社交回避强度**：当前居民台词偏「怕生、懒得客套、半关门、别扭的善意」，如需往更冲或更温和的方向微调，确认后可直接改稿。
