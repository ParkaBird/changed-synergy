# Changed: Synergy — Natural Camps Dialogue Trees & Narrative Fragments

> Compiled directly from `story_npcs/*.json`, `camp_dialogue/common.json`, `CampDialogue.java`, `NpcConversations.java`, `StoryClueService.java`, trilingual lang files (`en_us` / `zh_cn` / `ru_ru`), and structure `.nbt` templates.

## Conditions, Channels & Effects Reference

- `always`: Always visible.
- `chief`: Visible only when speaking with a camp chief.
- `can_join` / `cannot_join`: Whether the player meets trust/membership requirements to join this camp.
- `member` / `stranger`: Whether the player is currently a member of this camp.
- `human` / `transformed`: Whether the player is currently human or transfurred.
- `day` / `night_human` / `night_transformed`: Daytime / nighttime (human) / nighttime (transfurred).
- `human_join` / `transformed_join`: Human & `can_join` / Transfurred & `can_join`.
- `can_reversible` / `can_permanent`: Eligible for reversible or permanent assimilation rite.
- `understands` / `channel=mental`: Requires the player to be transfurred or have telepathy translation enabled (`TelepathyService`).
- `clue:<id>`: Requires the player to have inspected the corresponding note, clipboard, computer, or compact disc (`StoryClueService`).
- `requires: [...]`: Requires prior completion of the listed dialogue node(s).
- **Small camps without dialogue**: `hyena_camp_open`, `hyena_camp_snowy`, `hyena_camp_tropical`, `muddy_campsite`, and `oasis` only use routine/public-storage AI (`CampLifeService.mainCamp` is `false`); residents have no interactive dialogue tree.

## 1. Shared Camp-Life Dialogue (`CampDialogue` / `camp_dialogue/common.json`)

All 8 main natural camps share the same camp-life state machine (`camp_life` for chiefs, `camp_resident` or `resident_<role>` for members), swapping only the `<voice>` prefix:
- **Non-chief & cannot understand**: Shows `<residentVoice>.gesture` (with `ask_chief` -> `camp_seekchief` showing `<residentVoice>.seekchief`).
- **Resident outside home bounds (`!CampLifeService.atHome`)**: Shows `<residentVoice>.away`.
- **Personality overrides**: When using `<residentVoice>`, `PLAYFUL` / `CAUTIOUS` / `POLITE` traits override `greeting`, `request`, and `thanks` with `playful_*`, `cautious_*`, or `polite_*`.

### 1.1 Generic Default Resident Voice (`resident.*`) & Shared Status (`status.*`)

Used directly by built-in residents in **Forest Treehouse**, **Hyena Hideout**, and **Observatory**.

- **`resident.away`**
  - **EN**: Camp supplies can wait until we're back home. Right now, I'm just glad to be traveling with you.
  - **ZH**: 营地物资的事等回去之后再说吧。眼下能陪你一起赶路就挺好。
  - **RU**: Дела с припасами подождут, пока мы не вернемся домой. Сейчас мне просто приятно путешествовать вместе с тобой.
- **`resident.cautious_greeting`**
  - **EN**: Hello. Are you busy right now? There's a small favor I wanted to ask whenever you have the time.
  - **ZH**: 你好。现在正忙着吗？有件小事想请人帮个忙，等你有空的时候再说就好。
  - **RU**: Привет. Ты сейчас не занят? Есть небольшая просьба, но она вполне подождет, пока у тебя появится время.
- **`resident.cautious_request`**
  - **EN**: Could you bring me %s portions of food, any kind will do? Just hand them directly to me whenever it's convenient, and please be careful out there.
  - **ZH**: 能帮我带 %s 份食物吗，任何种类都可以。你方便的时候直接交给我就好，路上千万小心。
  - **RU**: Сможешь принести мне %s ед. еды — подойдет любая? Передай лично мне, когда будет удобно, и, пожалуйста, береги себя в дороге.
- **`resident.cautious_thanks`**
  - **EN**: Thank you. More than anything, I'm glad you made it back safely.
  - **ZH**: 谢谢你。看到你平平安安回来，比什么都好。
  - **RU**: Спасибо. Главное, что ты вернулся целым и невредимым.
- **`resident.changed`**
  - **EN**: We can't finish that right now. Let's check that the items match, who they're meant for, and whether there's enough room to hold them.
  - **ZH**: 现在还办不成。我们再核对一下物品对不对、该交给谁，以及有没有足够的地方存放吧。
  - **RU**: Сейчас это уладить не выйдет. Давай проверим, те ли это предметы, кому они предназначены и хватит ли для них места.
- **`resident.choose_item`**
  - **EN**: Hold whatever you'd like to share in your hand so I can see it. There's no rush—you can always come back later.
  - **ZH**: 把想分享的东西拿在手上让我看看吧。不着急，晚点再过来也没关系。
  - **RU**: Возьми в руку то, чем хочешь поделиться, чтобы я мог взглянуть. Спешить некуда — можешь подойти позже.
- **`resident.danger`**
  - **EN**: Someone nearby is running into trouble. Let's help them out before we rest.
  - **ZH**: 附近有邻居遇上麻烦了。我们先去搭把手，回头再歇息吧。
  - **RU**: Кто-то поблизости попал в беду. Давай сначала поможем, а потом уже отдохнем.
- **`resident.declined`**
  - **EN**: That's all right—we'll figure something else out.
  - **ZH**: 没关系，我们再想别的办法就好。
  - **RU**: Ничего страшного, мы что-нибудь придумаем.
- **`resident.donate`**
  - **EN**: You brought %s? How much of it would you like to leave for everyone to share?
  - **ZH**: 你带了%s？打算拿出多少来供大家一起用呢？
  - **RU**: Ты принес %s? Сколько ты хочешь оставить для общих нужд?
- **`resident.food_enough`**
  - **EN**: There's plenty to eat in the shared storage. If you live here with us, feel free to take what you need.
  - **ZH**: 公共储物箱里的吃食够用。如果你也住在这里，需要的话尽管拿。
  - **RU**: В общих запасах еды достаточно. Если ты живешь у нас, бери сколько нужно.
- **`resident.food_low`**
  - **EN**: The food chests are getting a little low. Everyone's been looking around the area for whatever rations we can find.
  - **ZH**: 食物箱里的存粮有点少了。大家最近一直在附近搜寻能吃的口粮。
  - **RU**: В продуктовых сундуках стало пустовато. Все сейчас ищут в округе любую съедобную провизию.
- **`resident.food_plenty`**
  - **EN**: We have more than enough food stocked up. It would be nice to gather everyone by the fire for a meal.
  - **ZH**: 存粮很充裕。要是能叫上大家围在火边一起吃顿饭就好了。
  - **RU**: Еды запасено вдоволь. Было бы здорово собрать всех у костра и поесть вместе.
- **`resident.gesture`**
  - **EN**: The resident gives a soft call and tries to gesture, but you can't understand what they're saying yet.
  - **ZH**: 这位居民轻轻叫了一声并比划着什么，但你现在还听不懂它的话。
  - **RU**: Житель тихонько подаёт голос и пытается что-то объяснить жестами, но ты пока не понимаешь его речи.
- **`resident.greeting`**
  - **EN**: Oh, hello there. Do you have a moment? There's something I could use a hand with.
  - **ZH**: 啊，你好。现在有空吗？我刚好有件事想请人搭把手。
  - **RU**: О, привет. Найдется минутка? Мне бы не помешала небольшая помощь.
- **`resident.handled`**
  - **EN**: Someone already brought those over for me, but thank you for keeping it in mind.
  - **ZH**: 那些东西刚才已经有人送来了。不过还是谢谢你惦记着这件事。
  - **RU**: Мне уже принесли все, что было нужно, но все равно спасибо за заботу.
- **`resident.meal`**
  - **EN**: I'll go let the others know. Come take a seat by the fire.
  - **ZH**: 我去通知其他人一声。你先来火堆旁坐下吧。
  - **RU**: Я пойду предупрежу остальных. Присаживайся пока у костра.
- **`resident.no_requests`**
  - **EN**: I don't need anything for myself right now. Stay and chat for a while if you'd like.
  - **ZH**: 我自己眼下不缺什么。你要是不赶时间，留下来聊会儿天吧。
  - **RU**: Мне самому сейчас ничего не нужно. Оставайся поболтать, если есть время.
- **`resident.playful_greeting`**
  - **EN**: Hey, you're here! Catch your breath and stay for a chat if you're not rushing off.
  - **ZH**: 你来啦！不急着赶路的话先歇口脚聊两句吧。
  - **RU**: Эй, вот и ты! Переведи дух и поболтай со мной, если никуда не торопишься.
- **`resident.playful_request`**
  - **EN**: Could you bring me %s portions of food, any kind will do? Make sure you hand them straight to me—I don't want to go digging through the public chests for my own snacks!
  - **ZH**: 能帮我带 %s 份食物吗，任何种类都行！记得直接交给我——我可不想去公共物资箱里翻找自己的零嘴！
  - **RU**: Принесешь мне %s ед. еды — какой угодно? Только чур передать лично в руки — не хочу потом искать свои вкусности в общих сундуках!
- **`resident.playful_thanks`**
  - **EN**: You brought them! Thanks a bunch—I'll save you a good spot by the fire.
  - **ZH**: 你带来啦！多谢啦，火堆旁的好位置一定给你留着。
  - **RU**: Принес! Спасибо огромное — приберегу для тебя лучшее местечко у костра.
- **`resident.polite_greeting`**
  - **EN**: Hello, neighbor. If you have a spare moment, could I ask you for a small favor?
  - **ZH**: 你好，邻居。要是你手头有空，能请你帮个小忙吗？
  - **RU**: Здравствуй, сосед. Если у тебя найдется свободная минутка, можно попросить о небольшой услуге?
- **`resident.polite_request`**
  - **EN**: Would you mind bringing me %s portions of food, any kind will do? Just hand them to me directly once you have them all—there's no rush at all.
  - **ZH**: 能麻烦你帮我带 %s 份食物吗，任何种类都可以。凑齐之后直接交给我就行，一点都不着急。
  - **RU**: Не сочти за труд принести мне %s ед. еды — подойдет любая. Как соберешь все, просто передай мне лично — спешить совершенно некуда.
- **`resident.polite_thanks`**
  - **EN**: Thank you for making the trip. Please stay and sit with us for a while if you'd like to rest.
  - **ZH**: 谢谢你特意跑这一趟。要是想歇歇脚，就留下来坐一会儿吧。
  - **RU**: Спасибо за хлопоты. Если хочешь передохнуть, оставайся и посиди немного с нами.
- **`resident.remember`**
  - **EN**: I remember the help you gave us before. It's really good to see you again.
  - **ZH**: 我记得你之前帮过我们的忙。能再见到你真好。
  - **RU**: Я помню, как ты помог нам раньше. Очень рад снова тебя видеть.
- **`resident.request`**
  - **EN**: Could you bring me %s portions of food, any kind will do? Just hand them directly to me once you've gathered the full amount.
  - **ZH**: 能帮我带 %s 份食物吗，任何种类都可以。凑齐之后直接交给我就行。
  - **RU**: Сможешь принести мне %s ед. еды — подойдет любая? Когда соберешь все полностью, просто передай лично мне.
- **`resident.requests`**
  - **EN**: I'm trying to set aside a little food for myself. If you have any to spare, could you bring me some?
  - **ZH**: 我自己想备一点随身吃的口粮。要是你手头有富余，能帮我带一些吗？
  - **RU**: Я хочу припасти немного еды для себя. Если у тебя будет лишняя, принесешь мне немного?
- **`resident.reverse_rite`**
  - **EN**: The Chief can help undo a borrowed coat. Let's make sure you return safely.
  - **ZH**: 首领可以帮你收回借穿的外衣。我们会保证你平安做回自己。
  - **RU**: Вождь может помочь снять заимствованный облик. Мы позаботимся о том, чтобы ты вернулся невредимым.
- **`resident.safe`**
  - **EN**: Everything is peaceful around here right now. I hope it stays this way.
  - **ZH**: 这一带眼下很平静，希望能一直保持这样。
  - **RU**: Сейчас здесь тихо и спокойно. Надеюсь, так будет и дальше.
- **`resident.seekchief`**
  - **EN**: (You can ask the Chief what the neighbors need, or talk directly once you have a translator, telepathy, or a latex form.)
  - **ZH**: （你可以直接去问首领邻居们需要什么；或者在拥有翻译器、心灵感应或兽化形态后，再来和居民直接沟通。）
  - **RU**: (Можешь спросить у вождя, что нужно соседям, либо вернуться с переводчиком, телепатией или в латексной форме, чтобы понимать жителей напрямую.)
- **`resident.thanks`**
  - **EN**: Thank you! That really helps me out. Sit down and rest with us for a while if you have the time.
  - **ZH**: 谢谢你！这可帮上大忙了。有空的话，留下来和我们坐会儿吧。
  - **RU**: Спасибо! Ты меня очень выручил. Присаживайся, отдохни с нами, если есть время.
- **`resident.transfer`**
  - **EN**: Your current home is at %s. If you're thinking of moving in with us, have a word with the Chief.
  - **ZH**: 你目前的家在%s。要是想搬来这边住，可以去和首领谈谈。
  - **RU**: Твой нынешний дом — %s. Если хочешь перебраться к нам, поговори с вождем.
- **`resident.watch`**
  - **EN**: If anything attacks our neighbors, could you help fend it off? Let us know once everyone is safe.
  - **ZH**: 要是有敌对生物袭击邻居们，你能帮忙应付一下吗？等大家都安全了，回来告诉我们一声就好。
  - **RU**: Если кто-то нападет на наших соседей, поможешь отбиться? Дай нам знать, когда все будут в безопасности.
- **`resident.watch_thanks`**
  - **EN**: Thank you for helping us deal with that trouble. It's a relief you were here.
  - **ZH**: 谢谢你帮我们应付那些麻烦，幸好有你在。
  - **RU**: Спасибо, что помог нам справиться с этой бедой. Хорошо, что ты оказался рядом.
- **`resident.watch_wait`**
  - **EN**: Let's make sure everyone is safe first. There's no need to go looking for a fight on our account.
  - **ZH**: 先确认大家都安全吧。不用为了我们特意去外头找架打。
  - **RU**: Давай сначала убедимся, что все в безопасности. Не нужно специально искать драки ради нас.
- **`status.beds`**
  - **EN**: Right now there are %s open sleeping places out of %s in total. Feel free to use one that suits your body, just be careful not to wake anyone who's already resting.
  - **ZH**: 目前共有 %s 处空余铺位，总共是 %s 处。挑个适合你体型的空位休息就好，注意别吵醒已经睡下的邻居。
  - **RU**: Сейчас свободно %s спальных мест из %s. Занимай любое, подходящее тебе по комплекции, только постарайся не будить тех, кто уже спит.
- **`status.guest`**
  - **EN**: You're always welcome to drop by and lend a hand. If you'd like to settle down here, have a word with the Chief.
  - **ZH**: 欢迎你随时来坐坐、搭把手。要是想在这里安家，可以和首领谈谈。
  - **RU**: Мы всегда рады, когда ты заглядываешь посидеть или помочь. А если захочешь поселиться здесь — поговори с вождем.
- **`status.member`**
  - **EN**: You always have a place with us here. The public supplies are for everyone in the household to share, while personal belongings stay with their owners.
  - **ZH**: 这里随时给你留着位置。公共物资由大家共享，私人物品则归各自保管。
  - **RU**: Здесь за тобой всегда остается место. Общие припасы открыты для всех в доме, а личные вещи остаются за их хозяевами.
- **`status.outfit_changed`**
  - **EN**: I can't hand the outfit over just yet—make sure you have a free slot in your pack first, then talk to me again.
  - **ZH**: 衣服现在还交不了。先在背包里腾出一个空位，再过来找我吧。
  - **RU**: Пока не получается передать одежду. Освободи место в рюкзаке и подойди ко мне снова.

## 2. Camp-by-Camp Dialogue Trees & Narrative Fragments

### 2.1 Forest Treehouse (`forest_treehouse`)

- **Biome / Placement**: Forest / Birch Forest (Overworld)
- **Resident Dialogue**: Uses the **generic default resident dialogue tree** (`camp_resident` / `resident` voice; no camp-specific `resident_*` nodes).

#### Chief: Canopy Weaver (`changed_synergy:latex_silk_moth`) (`canopy_chief.json`, camp voice `weaver`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh… careful of the low branch by the doorway. It curves inward a little more every spring. Welcome to the canopy. Take a breath if the climb tired you; the stairs wind quite a way up from the roots.
  - **ZH**: 噢……当心门口那根低矮的树枝。每年春天它都会往里弯一点。 欢迎来到树冠层。要是爬上来累了就先喘口气吧，从树根盘旋上来的楼梯确实挺长的。
  - **RU**: Ох… осторожнее с низкой веткой у входа. Каждую весну она изгибается внутрь чуточку сильнее. Добро пожаловать в крону. Переведи дух, если подъем тебя утомил: лестница от самых корней вьется довольно долго.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Only two arms? How do they hold a teacup AND scratch their ear at the same time? Oh, not again! don't stare at the lamp… Don't stare at the lamp!)
  - **ZH**: （只有两只手？那他们怎么一边端茶杯一边挠耳朵？噢，又来了！别盯着灯看……别盯着灯看！）
  - **RU**: (Всего две руки? И как они одновременно держат чашку чая И чешут за ухом? Ох, только не снова! Не пялься на лампу… Не пялься на лампу!)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Come in out of the breeze. The upper platform holds the afternoon warmth longer than the branches below, and there is plenty of room by the loom.
  - **ZH**: 进来避避风吧。高处平台比下面的树枝更能留住午后的暖意，织布机旁也还有不少地方。
  - **RU**: Заходи с ветерка. Верхняя площадка хранит послеполуденное тепло дольше, чем нижние ветви, а у ткацкого станка полно места.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Good, another fuzzy neighbor. Hope they didn't track tree sap onto the red rug.)
  - **ZH**: （太好了，又来了一位毛茸茸的邻居。希望别把树汁踩到红地毯上。）
  - **RU**: (Отлично, еще один пушистый сосед. Надеюсь, на красный ковер не налипнет древесная смола.)
- **Choices**:
  - `canopy_chief_about` -> `about` (`condition=always`)
    - **EN**: Who are you? Do you look after this treehouse? | **ZH**: 你是谁？这间树屋是你在照看吗？ | **RU**: Кто ты? Ты присматриваешь за этим домом на дереве?
  - `canopy_chief_home` -> `home` (`condition=always`)
    - **EN**: Who else lives in this tree, and how do you all get along? | **ZH**: 这棵树里还住着谁？你们平时相处得怎么样？ | **RU**: Кто еще живет на этом дереве и как вы все ладите?
  - `canopy_chief_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: I saw the journal on your lectern. What do you think really happened to the world? | **ZH**: 我看到了你讲台上的日志。你觉得这个世界究竟发生什么事了？ | **RU**: Я видел дневник на твоей кафедре. Как думаешь, что на самом деле случилось с миром?
  - `join_menu` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room in the treehouse for another neighbor? | **ZH**: 树屋里还住得下新邻居吗？ | **RU**: Найдется ли в доме на дереве место для еще одного соседа?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About living here… | **ZH**: 关于住在这里的事…… | **RU**: Насчет жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: People here call on me when the roof leaks or when the loom tangles, mostly because having four hands makes sorting thread a little easier. Before the incident that changed us, I worked in the old facility underground. Now I keep the notes on the lectern and mend what needs mending.
  - **ZH**: 屋顶漏水或是织布机缠线的时候，大家就会来找我，主要是因为四只手理起线来稍微方便些。在让我们变成这样的那场变故之前，我在地下的旧设施里工作。现在我负责记录讲台上的笔记，顺便修补些需要修补的东西。
  - **RU**: Местные зовут меня, когда протекает крыша или путаются нити на станке — в основном потому, что четырьмя руками разбирать пряжу немного проще. До происшествия, которое нас изменило, я работал в старом подземном комплексе. А теперь веду записи на кафедре и чиню все, что требует починки.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Four hands is great until you need four sleeves… Knitting four sleeves takes forever.)
  - **ZH**: （四只手是挺好，直到你需要穿四只袖子……织四只袖子可太费工夫了。）
  - **RU**: (Иметь четыре руки здорово, пока не понадобятся четыре рукава… Вязать четыре рукава — целая вечность.)
- **Choices**:
  - `voices` -> `voices` (`condition=always`)
    - **EN**: Your clipboard says the Silk Moth is a friend you can rely on. What does that mean? | **ZH**: 你的写字板上写着"蚕蛾是可以信赖的朋友"，这是什么意思？ | **RU**: На твоем планшете написано, что шелкопряд — надежный друг. Что это значит?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `voices` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: It is hard to describe if you have only ever had one voice in your head. I still remember who I was before, from every habit to every old worry. Yet this body wasn't mine to begin with, and sometimes I hear the Latex Silk Moth's own instinctual voice, gently suggesting what to do and what not to do. At first that felt strange. Now, it is like sharing a quiet room with a sensible friend. We have a very peaceful agreement. Though... I can only speak for myself. Every person settles into their shape differently.
  - **ZH**: 如果你脑海里从来只有一个声音，这种感觉很难形容。我依然记得自己从前是谁，每一个习惯、每一桩旧心事都记得。 但这具身体原本并不是我的，有时我会听到胶兽蚕蛾本能的声音，温和地建议我该做什么、不该做什么。 起初这让人觉得很陌生。现在，这就像是和一位通情达理的朋友共用一间安静的屋子。我们达成了非常平和的默契。 不过……这也只是我自己的体会。每个人适应新形态的方式都不一样。
  - **RU**: Это трудно описать, если в твоей голове всегда звучал только один голос. Я до сих пор помню, кем был раньше — каждую привычку и каждую старую тревогу. Но это тело изначально было не моим, и порой я слышу собственный инстинктивный голос латексного шелкопряда, который мягко подсказывает, что стоит делать, а чего не стоит. Сначала это казалось странным. А теперь — словно делишь тихую комнату с рассудительным другом. Между нами очень мирное согласие. Впрочем… я могу говорить только за себя. Каждый осваивается в своем новом облике по-разному.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Right now the moth voice is suggesting I eat that wool tapestry… I'm telling it no, Mostly no…)
  - **ZH**: （现在蚕蛾的声音正建议我把那幅羊毛挂毯吃掉……我跟它说不行。基本上不行……）
  - **RU**: (Прямо сейчас голос мотылька предлагает мне сжевать вон тот шерстяной гобелен… Я говорю ему «нет». Ну, почти «нет»…)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: It is a mixed household. A few other Silk Moths share the upper boughs with me, while the deer, the squirrels, and our skunk neighbor prefer the lower platforms near the campfire. And right beneath the roots, we built a beehive with sleeping places for the Bees. The tree takes care of us, and we take care of the tree.
  - **ZH**: 这是个混居的大家庭。另外几只蚕蛾和我一起住在高处的树枝间，鹿、松鼠还有我们的臭鼬邻居更喜欢篝火附近的下层平台。而在树根正下方，我们建了一间带铺位的蜂房供蜜蜂们休息。 大树照拂着我们，我们也照料着大树。
  - **RU**: У нас тут смешанное хозяйство. Еще несколько шелкопрядов делят со мной верхние ветви, а олени, белки и наш сосед-скунс предпочитают нижние площадки ближе к костру. А прямо под корнями мы обустроили улей со спальными местами для пчел. Дерево заботится о нас, а мы заботимся о дереве.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Just don't startle the skunk near the stairs! We had to air out the lower trunk for a week…)
  - **ZH**: （千万别在楼梯附近吓到那只臭鼬！上次我们可给树干下层通了整整一周的风……）
  - **RU**: (Главное — не напугать скунса возле лестницы! Нам потом пришлось неделю проветривать нижнюю часть ствола…)
- **Choices**:
  - `furniture` -> `furniture` (`condition=always`)
    - **EN**: Where did the furniture and that box of crayons come from? | **ZH**: 这些家具还有那盒蜡笔是从哪儿来的？ | **RU**: Откуда взялась мебель и эта коробка с мелками?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `furniture` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: A few of us explored a bit of our old facility a while back. We didn't go looking for grand machinery—just brought back a swivel chair, some clipboards, and a few small items for comfort and decoration. A wooden floor feels much homier with a rug.
  - **ZH**: 前阵子我们几个回以前的旧设施探了探。我们并不是去找什么大型机器的——只是带回了一把转椅、几块写字板，还有一些让人住着舒心、能当点缀的小物件。木地板铺上地毯，才更像个家的样子。
  - **RU**: Некоторое время назад несколько из нас немного осмотрели наш старый комплекс. Мы не искали сложных машин — просто принесли вращающееся кресло, несколько планшетов и кое-какие мелочи для уюта и украшения. С ковром деревянный пол кажется куда более домашним.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Spinning in the swivel chair with four arms out is objectively the best part of my day!)
  - **ZH**: （张开四只手在转椅上转圈，客观来说绝对是我一天中最快乐的时刻！）
  - **RU**: (Крутиться в кресле, раскинув все четыре руки, — объективно лучшая часть моего дня!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `past` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Many of my former colleagues—who are goo now, just like me—still ask that exact question whenever they sit by the fire below. They wonder what actually happened at the end of the incident, and whether baseline humanity still exists somewhere as we knew it. I cannot answer for certain. I don't know if whole human cities survived, or if the world beyond our forest looks anything like the maps we used to draw. That is why I keep writing at the lectern. Even if we don't know the whole story, someone ought to record that we are still here... thriving and fighting, to stay alive.
  - **ZH**: 我以前的许多同事——如今也和我一样成了胶兽——每次坐在下面的火堆旁时，还是会问一模一样的问题。他们想知道那场变故最后究竟发生了什么，也想知道我们所熟知的原生人类是否还在某个地方存在着。 我无法给出确切的回答。我不知道是否还有完整的人类城市幸存下来，也不知道这片森林之外的世界，是否还像我们过去绘制的地图那样。 所以我才一直坚持在讲台上记录。哪怕我们无从知晓全貌，也总该有人记下我们还在这里……努力繁衍生息，为了活下去而奋斗。
  - **RU**: Многие из моих бывших коллег — которые теперь, как и я, стали латексными — до сих пор задают тот же самый вопрос, собираясь внизу у костра. Им интересно, что же на самом деле произошло в конце того инцидента и сохранились ли еще где-нибудь обычные люди, какими мы их знали. Я не могу ответить наверняка. Я не знаю, уцелели ли человеческие города и похож ли мир за пределами нашего леса на карты, которые мы когда-то чертили. Вот почему я продолжаю писать за кафедрой. Даже если нам неизвестна вся история, кто-то должен записать, что мы все еще здесь… живем, процветаем и боремся за жизнь.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Deep philosophical speech: nailed it. Now if only I could remember where I put my spare spool of yarn…)
  - **ZH**: （深沉的哲学发言：完美发挥。要是现在能想起来备用线轴放哪儿了就更好了……）
  - **RU**: (Глубокая философская речь: блестяще. Вот бы еще вспомнить, куда я положил запасной моток пряжи…)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: There is always another branch for someone willing to sweep the leaves and mind the trees. I only speak for our little household here in the trunk, mind you. But if you want a quiet neighborly roof, you are welcome.
  - **ZH**: 只要愿意帮忙扫扫落叶、照看树木，这里随时都有空余的枝桠。不过说好了，我只代表这棵树干里的小家庭。如果你想要一个安静和睦的落脚处，随时欢迎。
  - **RU**: Для того, кто готов подметать листья и присматривать за деревьями, всегда найдется свободная ветка. Учти, я говорю только за нашу небольшую общину в этом стволе. Но если тебе нужна тихая соседская крыша над головой — добро пожаловать.
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: You are very welcome to stay with us just as you are. Acceptance into our treehouse never requires a new body. Though, if you are ever curious what it feels like to be a Silk Moth, I can offer you that form, either as a temporary, reversible experience or as a permanent life.
  - **ZH**: 非常欢迎你以现在的模样留下来。加入我们的树屋绝不需要更换一具新身体。不过，如果你好奇成为一只蚕蛾是什么感觉，我也可以将这种形态分享给你——无论是作为暂时的、可逆的体验，还是作为永久的生活方式。
  - **RU**: Мы будем очень рады принять тебя таким, какой ты есть. Чтобы стать частью нашего дома на дереве, совершенно не нужно менять тело. Впрочем, если тебе когда-нибудь станет любопытно, каково это — быть шелкопрядом, я могу предложить тебе этот облик: как временный, обратимый опыт или же насовсем.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Things are still a little unsettled up here, so I can't offer you a place in the treehouse just yet. Give our neighbors some space for now—once everyone feels at ease around you again, we can talk about you moving in.
  - **ZH**: 树冠上目前还有些不安稳，我暂时没法在树屋里给你安排住处。先给邻居们留些空间吧，等大家在你身边重新觉得安心了，我们再谈搬过来的事。
  - **RU**: Сейчас у нас в кроне не все спокойно, так что я пока не могу предложить тебе место в доме на дереве. Дай соседям немного передохнуть — когда всем снова станет спокойно рядом с тобой, мы поговорим о переезде.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: I'd be glad to share the chores and live here. | **ZH**: 我很乐意分担家务，住在这里。 | **RU**: Я с радостью буду делить хлопоты по дому и жить здесь.
  - `join_as_is` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'd like to live here as a human and help look after the tree. | **ZH**: 我想以人类的身份住在这里，帮忙照看这棵大树。 | **RU**: Я бы хотел жить здесь человеком и помогать присматривать за деревом.
  - `copy_form` -> `explain_form` (`condition=human`)
    - **EN**: What would changing into your form mean for me? | **ZH**: 变成你的形态对我来说意味着什么？ | **RU**: Что для меня будет значить переход в твой облик?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You will not lose your past, and sharing my latex does not diminish or consume me either. You will still remember every day of your life, though you may hear the Silk Moth's gentle instinct alongside your own thoughts, and adjusting to four arms takes an afternoon or two. If we keep it reversible, it is simply a borrowed coat you can step out of whenever you wish. If you choose to make it permanent, you will remain a Silk Moth for good. And if you prefer to stay human, my welcome does not change a bit.
  - **ZH**: 你不会失去自己的过去，分享我的胶体也不会削弱或消耗我。你依然会记得自己生命中的每一天，只是在自己的思绪旁可能会听到蚕蛾温和的本能声音，而且适应四只手臂大概需要一两个下午。 如果我们保持可逆状态，它就只是一件借穿的外套，你随时都能脱下来。如果你选择永久转化，你就会一直作为蚕蛾生活下去。而如果你更愿意保持人类之身，我的欢迎也丝毫不会改变。
  - **RU**: Ты не потеряешь своего прошлого, и передача части моего латекса никак не ослабит и не истощит меня. Ты будешь помнить каждый день своей жизни, разве что рядом с твоими собственными мыслями может звучать мягкий инстинкт шелкопряда, да к четырем рукам придется привыкать день-другой. Если сделать превращение обратимым, это будет просто одолженное пальто, которое можно снять, когда пожелаешь. Если выберешь необратимое — останешься шелкопрядом навсегда. А если предпочтешь остаться человеком, мое радушие ничуть не изменится.
- **Choices**:
  - `confirm_copy` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the reversible transformation and join. | **ZH**: 我们试试可逆转化，然后加入这里吧。 | **RU**: Давай попробуем обратимое превращение, и я присоединюсь к вам.
  - `permanent_form` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I'm ready to make it permanent and join. | **ZH**: 我准备好永久转化并加入这里了。 | **RU**: Я готов сделать превращение необратимым и присоединиться к вам.
  - `join_as_is` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'd like to live here as a human and help look after the tree. | **ZH**: 我想以人类的身份住在这里，帮忙照看这棵大树。 | **RU**: Я бы хотел жить здесь человеком и помогать присматривать за деревом.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Then welcome home, neighbor! Mind your head near the beehive stairs downstairs, and if you ever want a woven blanket for the night breeze, just ask.
  - **ZH**: 那么欢迎回家，邻居！下楼经过蜂房楼梯时当心碰头，要是夜里起风需要织毯，随时开口就好。
  - **RU**: Тогда добро пожаловать домой, сосед! Береги голову у лестницы к улью внизу, а если понадобится тканое одеяло от ночного ветерка — только скажи.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Yay! someone tall enough to reach the top shelf without knocking things over with their wings!)
  - **ZH**: （太棒了！总算有个够得着顶层架子、又不会用翅膀把东西全扫下来的高个子了！）
  - **RU**: (Ура! Хоть кто-то достаточно высокий, чтобы достать до верхней полки и не смести все крыльями!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Then welcome to the boughs! You'll get used to the rhythm here in no time.
  - **ZH**: 那么欢迎来到树冠枝桠间！你很快就会习惯这里的步调的。
  - **RU**: Тогда добро пожаловать в наши ветви! Ты быстро привыкнешь к здешнему ритму жизни.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There now... take a slow breath and let your upper and lower arms settle. Can you feel the air moving through your antennae? Every thought you brought in is still right where you left it, and now you have a friend sharing the quiet with you. Welcome to the treehouse, neighbor!
  - **ZH**: 好了……慢一点深呼吸，让上下两对手臂都舒展开。能感觉到气流拂过触角吗？你心里原本带着的那些念头全都安安稳稳地留着呢，而且现在多了一位朋友陪你共享这份宁静。欢迎来到树屋，邻居！
  - **RU**: Ну вот… сделай медленный вдох и дай верхним и нижним рукам освоиться. Чувствуешь, как воздух проходит сквозь усики? Все твои мысли и воспоминания на месте — до единой, а теперь рядом есть друг, с которым можно разделить тишину. Добро пожаловать в дом на дереве, сосед!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (SO FLUFFY! Look at those antennae! Ten out of ten! Let's go stare at the porch lantern for an hour!)
  - **ZH**: （太蓬松了！看看那对触角！满分十分！我们去盯着门廊提灯看一个钟头吧！）
  - **RU**: (ТАКОЙ ПУШИСТЫЙ! Только гляньте на эти усики! Десять из десяти! Пойдем пялиться на фонарь у крыльца целый час!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh… careful of the low branch by the doorway. It curves inward a little more every spring. Welcome to the canopy. Take a breath if the climb tired you; the stairs wind quite a way up from the roots.
  - **ZH**: 噢……当心门口那根低矮的树枝。每年春天它都会往里弯一点。 欢迎来到树冠层。要是爬上来累了就先喘口气吧，从树根盘旋上来的楼梯确实挺长的。
  - **RU**: Ох… осторожнее с низкой веткой у входа. Каждую весну она изгибается внутрь чуточку сильнее. Добро пожаловать в крону. Переведи дух, если подъем тебя утомил: лестница от самых корней вьется довольно долго.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Only two arms? How do they hold a teacup AND scratch their ear at the same time? Oh, not again! don't stare at the lamp… Don't stare at the lamp!)
  - **ZH**: （只有两只手？那他们怎么一边端茶杯一边挠耳朵？噢，又来了！别盯着灯看……别盯着灯看！）
  - **RU**: (Всего две руки? И как они одновременно держат чашку чая И чешут за ухом? Ох, только не снова! Не пялься на лампу… Не пялься на лампу!)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Come in out of the breeze. The upper platform holds the afternoon warmth longer than the branches below, and there is plenty of room by the loom.
  - **ZH**: 进来避避风吧。高处平台比下面的树枝更能留住午后的暖意，织布机旁也还有不少地方。
  - **RU**: Заходи с ветерка. Верхняя площадка хранит послеполуденное тепло дольше, чем нижние ветви, а у ткацкого станка полно места.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Good, another fuzzy neighbor. Hope they didn't track tree sap onto the red rug.)
  - **ZH**: （太好了，又来了一位毛茸茸的邻居。希望别把树汁踩到红地毯上。）
  - **RU**: (Отлично, еще один пушистый сосед. Надеюсь, на красный ковер не налипнет древесная смола.)
- **Choices**:
  - `canopy_chief_about` -> `about` (`condition=always`)
    - **EN**: Who are you? Do you look after this treehouse? | **ZH**: 你是谁？这间树屋是你在照看吗？ | **RU**: Кто ты? Ты присматриваешь за этим домом на дереве?
  - `canopy_chief_home` -> `home` (`condition=always`)
    - **EN**: Who else lives in this tree, and how do you all get along? | **ZH**: 这棵树里还住着谁？你们平时相处得怎么样？ | **RU**: Кто еще живет на этом дереве и как вы все ладите?
  - `canopy_chief_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: I saw the journal on your lectern. What do you think really happened to the world? | **ZH**: 我看到了你讲台上的日志。你觉得这个世界究竟发生什么事了？ | **RU**: Я видел дневник на твоей кафедре. Как думаешь, что на самом деле случилось с миром?
  - `join_menu` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room in the treehouse for another neighbor? | **ZH**: 树屋里还住得下新邻居吗？ | **RU**: Найдется ли в доме на дереве место для еще одного соседа?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About living here… | **ZH**: 关于住在这里的事…… | **RU**: Насчет жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Mind that low branch on your way down. If the night turns cold, come back for a blanket.
  - **ZH**: 下去的时候留意那根矮树枝。要是夜里转凉了，就回来拿条毯子。
  - **RU**: Берегись той низкой ветки на обратном пути. Если ночь выдастся холодной — возвращайся за одеялом.
- **Choices**:
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Safe travels. | **ZH**: 保重。 | **RU**: Счастливого пути.

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Before I share this form, I want to make sure this is truly what you want. A permanent transformation means this Silk Moth body will be yours for good, and ordinary reversal methods won't bring your human body back. You can still choose the reversible experience instead, or stay just as you are—there is a place for you in the treehouse either way. Are you sure you want to become a Silk Moth permanently?
  - **ZH**: 在分享这一形态之前，我想再和你确认一次。选择永久转化，就意味着这具蚕蛾身体今后会一直属于你，常规的逆转方式无法恢复你原本的人类躯体。你依然可以改选可逆的体验，或者就以现在的模样住下——无论怎样，树屋里都有你的位置。你确定要永久成为一只蚕蛾吗？
  - **RU**: Прежде чем я поделюсь этим обликом, я хочу убедиться, что ты действительно этого хочешь. Необратимое превращение означает, что тело шелкопряда останется твоим навсегда, и обычные способы отката уже не вернут человеческий облик. Ты все еще можешь выбрать обратимый вариант или остаться таким, какой есть, — место в доме на дереве найдется для тебя в любом случае. Ты уверен, что хочешь стать шелкопрядом навсегда?
- **Choices**:
  - `confirm_permanent` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I understand and choose the permanent change. | **ZH**: 是的，我想好了，我选择永久转化。 | **RU**: Да, я все понимаю и выбираю необратимое превращение.
  - `reconsider` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over again. | **ZH**: 让我再考虑一下。 | **RU**: Дай мне еще подумать.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There now... take a slow breath and let your upper and lower arms settle. Can you feel the air moving through your antennae? Are you still feeling like yourself in there? Hope the change sits gently with you—now you have a friend sharing the quiet. Welcome to the treehouse, neighbor!
  - **ZH**: 好了……慢一点深呼吸，让上下两对手臂都舒展开。能感觉到气流拂过触角吗？心神还清明吧？但愿这副新身躯能让你自在——现在多了一位朋友陪你共享这份宁静。欢迎来到树屋，邻居！
  - **RU**: Ну вот… сделай медленный вдох и дай верхним и нижним рукам освоиться. Чувствуешь, как воздух проходит сквозь усики? Всё в порядке с самочувствием? Надеюсь, новый облик дался тебе легко — а теперь рядом есть друг, с которым можно разделить тишину. Добро пожаловать в дом на дереве, сосед!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (SO FLUFFY! Look at those antennae! Ten out of ten! Let's go stare at the porch lantern for an hour!)
  - **ZH**: （太蓬松了！看看那对触角！满分十分！我们去盯着门廊提灯看一个钟头吧！）
  - **RU**: (ТАКОЙ ПУШИСТЫЙ! Только гляньте на эти усики! Десять из десяти! Пойдем пялиться на фонарь у крыльца целый час!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're part of our treehouse household now. Shared supplies and unoccupied beds are open to our neighbors, while personal belongings stay with their keepers. You're free to leave whenever you need to—we'll remember the time we spent together, and your body remains your own.
  - **ZH**: 你在这里是我们树屋家庭的一员。公共物资和空余铺位都向邻居们开放，私人物品则归各自的主人保管。要是哪天你需要离开，随时都可以动身。我们会记得一起度过的时光，而你的身体也依然属于你自己。
  - **RU**: Теперь ты часть нашего дома на дереве. Общие припасы и свободные спальные места открыты для соседей, а личные вещи остаются за их хранителями. Ты можешь уйти в любой момент, когда понадобится, — мы будем помнить проведенное вместе время, а твое тело остается твоим.
- **Choices**:
  - `leave_menu` -> `confirm_leave` (`condition=member`)
    - **EN**: I need to leave this household and move on. | **ZH**: 我需要离开这个大家庭，继续启程了。 | **RU**: Мне пора покинуть этот дом и идти дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: All right. Before I take your name off our shared supplies and sleeping places, are you sure? You're always welcome back for a visit, though moving out lets any temporary shape you borrowed from us slip right off.
  - **ZH**: 好的。在我把你的名字从公共物资和铺位名单上划掉之前，你确定了吗？以后随时欢迎回树屋串门，不过要是你这身样子只是在我们这儿暂借来穿的，离开时它也会像外套一样自然卸下，换回你自己的模样。
  - **RU**: Хорошо. Прежде чем я вычеркну твоё имя из списков припасов и спальных мест, ты точно решил? Мы всегда рады видеть тебя в гостях, просто знай: если твой нынешний облик был взят здесь на время, с уходом он легко сойдёт, и ты вернёшься к своему прежнему виду.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=member`, `effect=leave`)
    - **EN**: Yes, I'm ready to move on. | **ZH**: 确定，我准备启程了。 | **RU**: Да, я готов двигаться дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I've updated our list. Take care of yourself out there, neighbor. The time you spent with us in the branches still means a lot.
  - **ZH**: 名单我已经改好了。在外头多保重，邻居。你在这里陪我们度过的时光依然珍贵。
  - **RU**: Я обновил список. Береги себя в дороге, сосед. Время, которое ты провел с нами среди ветвей, не прошло даром.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your weaving. | **ZH**: 不打扰你织布了。 | **RU**: Не буду отвлекать тебя от ткачества.

#### Chief Camp-Life Voice (`weaver.*`)

- **`weaver.changed`**
  - **EN**: We can't complete that right now. Check that you're holding the right item and that the public chests still have space—or if someone already helped out, there's no need to bring a second batch.
  - **ZH**: 现在还办不成。请看看手里的物资对不对、公用箱还有没有空位。要是已经有人帮过忙了，就不用再重复送一份了。
  - **RU**: Сейчас это уладить не получится. Проверь, те ли предметы у тебя в руке и есть ли место в общих сундуках. А если кто-то уже помог, приносить второй раз не нужно.
- **`weaver.choose_item`**
  - **EN**: Hold whatever you'd like to donate in your hand, then talk to me again. Anything you want to keep for yourself, please keep.
  - **ZH**: 把想捐的物资拿在手上，再来和我说就好。你自己用得着的东西尽管留着。
  - **RU**: Возьми в руку то, чем хочешь поделиться, и снова поговори со мной. А все, что нужно тебе самому, оставляй при себе.
- **`weaver.danger`**
  - **EN**: Some of our neighbors are dealing with trouble nearby right now. Let's make sure everyone is safe before we gather around the fire.
  - **ZH**: 附近目前还有邻居在应付麻烦。先把那边的状况安顿好，大家再围到火边坐吧。
  - **RU**: Кое-кто из соседей сейчас отбивается от опасности возле дерева. Давай сначала убедимся, что все целы, а уж потом будем собираться у огня.
- **`weaver.declined`**
  - **EN**: That's quite all right. Take care of yourself first—we can always ask someone else.
  - **ZH**: 当然没关系。先照顾好你自己，我们可以再问问其他邻居。
  - **RU**: Конечно, ничего страшного. Сначала позаботься о себе, а мы можем попросить кого-нибудь другого.
- **`weaver.donate`**
  - **EN**: You brought %s? We can put that to good use. How much would you like to leave for the neighbors?
  - **ZH**: 你带了%s？这些能派上用场。你想给邻居们留下多少？
  - **RU**: Ты принес %s? Это нам пригодится. Сколько ты хочешь оставить для соседей?
- **`weaver.food_enough`**
  - **EN**: There's enough in the food chests to go around. The neighbors have been keeping a steady eye on our stores.
  - **ZH**: 食物箱里的存粮够大家分着用。邻居们平时一直有留意照看储备。
  - **RU**: Еды в сундуках хватает на всех. Соседи исправно следят за нашими запасами.
- **`weaver.food_low`**
  - **EN**: Our shared food chests are running a little low. Don't go hungry on our account, of course, but adding some spare food to the public stores would really help out.
  - **ZH**: 公共食物箱里的存粮有点少了。你不用为了大家饿着自己，不过要是有富余的吃食往公用箱里添一点，会帮上不少忙。
  - **RU**: Общие запасы еды немного истощились. Конечно, не стоит голодать ради нас, но если у тебя найдется лишняя провизия для общих сундуков, это очень поможет.
- **`weaver.food_plenty`**
  - **EN**: The food chests are well stocked right now. If you're free later, stay and eat with us by the fire instead of making do on the stairs.
  - **ZH**: 目前食物箱里很充裕。你要是有空，就留下来围着篝火一起吃吧，别总在楼梯上将就。
  - **RU**: Продуктовые сундуки сейчас полны. Если позже выдастся свободная минутка, оставайся поесть у костра, чем перекусывать на лестнице.
- **`weaver.greeting`**
  - **EN**: Come sit down out of the breeze for a bit. Always good having another familiar face up among the branches.
  - **ZH**: 过来坐会儿避避风吧。树冠上面常能见到熟面孔真好。
  - **RU**: Заходи передохнуть от ветра. Всегда приятно видеть знакомое лицо среди ветвей.
- **`weaver.handled`**
  - **EN**: Someone else already took care of that one. Feel free to check if anyone needs help with something else.
  - **ZH**: 那件事刚才已经有人办妥了。你可以再看看还有没有别的邻居需要帮忙。
  - **RU**: Этим уже кто-то занялся чуть раньше. Посмотри, может, кому-нибудь нужна другая помощь.
- **`weaver.meal`**
  - **EN**: I'll go call the neighbors. Find yourself a spot by the campfire, and whoever is free will come join us.
  - **ZH**: 我去招呼邻居们。你先在篝火旁找个位置坐下，有空的人自然会过来一起吃。
  - **RU**: Я пойду позову соседей. Занимай место у костра, и все, кто сейчас свободен, присоединятся к нам.
- **`weaver.no_requests`**
  - **EN**: Nothing urgent needs doing right now. You don't have to find yourself a chore just to stay and rest here.
  - **ZH**: 眼下没什么急事。不用非得先给自己找件活干，才能安心待在这儿。
  - **RU**: Срочных дел сейчас нет. Не нужно искать себе работу, чтобы просто остаться и отдохнуть здесь.
- **`weaver.outfit`**
  - **EN**: You've done a great deal for this household. This Canopy Outfit is for you—may it keep you comfortable along the forest paths.
  - **ZH**: 你为这个家出了不少力。这套林冠服饰送给你，愿它陪你舒适地走过更多林间小路。
  - **RU**: Ты немало сделал для нашего дома. Этот костюм кроны — для тебя; пусть он сослужит добрую службу на лесных тропах.
- **`weaver.remember`**
  - **EN**: I remember the help you've given us before. You don't need to prove you belong here every time you come home.
  - **ZH**: 你之前帮过的忙我都记得。每次回来不用总想着证明自己，这里随时欢迎你。
  - **RU**: Я помню все, чем ты нам помог раньше. Тебе не нужно каждый раз доказывать, что ты здесь свой.
- **`weaver.request`**
  - **EN**: We could use %s %s for the household. Bring the full amount whenever it's convenient, and we'll put them into our shared supplies.
  - **ZH**: 我们需要 %s 份%s。你方便的时候凑齐带来就好，我们会收进公共物资里。
  - **RU**: Нам пригодилось бы %s ед. припасов из категории «%s». Когда будет удобно собрать все полностью, приноси — мы сложим это в общие запасы.
- **`weaver.requests`**
  - **EN**: A few neighbors mentioned things that could use a hand. Pick whichever one you feel like doing—the rest can wait.
  - **ZH**: 有几位邻居想请人搭把手。挑一件你愿意做的就好，其余的可以等等。
  - **RU**: Несколько соседей просили о небольшой помощи. Выбирай то, за что готов взяться, а остальное подождет.
- **`weaver.reverse_rite`**
  - **EN**: Borrowing our coat was always your choice, and returning to yourself is part of the promise. Come close.
  - **ZH**: 借穿我们的外衣本就是你的自愿选择，让你平安做回自己也是这份承诺的一部分。靠近些。
  - **RU**: Примерить наш облик всегда было твоим выбором, и вернуть человеческое тело — часть обещания. Подойди ближе.
- **`weaver.safe`**
  - **EN**: Everything is quiet along the walkways right now—a good time to rest your feet, mend your gear, or check in on the neighbors.
  - **ZH**: 步道上目前很平静，正适合歇歇脚、修补些物件，或是去看看邻居们。
  - **RU**: На мостках сейчас тихо и спокойно — самое время перевести дух, починить снаряжение или проведать соседей.
- **`weaver.thanks`**
  - **EN**: Thank you. I'll make sure these go into our shared storage. Sit down and rest your feet whenever you like.
  - **ZH**: 谢谢你。我会把这些收进公共储物箱里。要是愿意的话，现在就坐下来歇歇脚吧。
  - **RU**: Спасибо. Я позабочусь, чтобы это попало в общие запасы. Присаживайся и отдохни, если есть желание.
- **`weaver.transfer`**
  - **EN**: You've already made your home at %s. Would you like to live here instead? Your friends there will still remember you, and nothing will change until you say the word.
  - **ZH**: 你已经在%s安了家。现在想改为住在这里吗？那边的朋友依然会记得你，在你点头确认之前，什么都不会改变。
  - **RU**: Твой нынешний дом — %s. Хочешь перебраться жить к нам? Старые друзья по-прежнему будут помнить тебя, и ничего не изменится, пока ты не подтвердишь.
- **`weaver.watch`**
  - **EN**: If trouble comes near the tree, please help our neighbors deal with it. There's no need to go looking for a fight—just let me know once things have calmed down.
  - **ZH**: 要是大树附近出了麻烦，请帮邻居们应付一下。不用特意去找架打，等周围重新平静下来后回来告诉我就好。
  - **RU**: Если возле дерева возникнет угроза, помоги соседям справиться с ней. Специально искать драки не нужно — просто дай мне знать, когда все успокоится.
- **`weaver.watch_thanks`**
  - **EN**: Thank you for looking out for them. Come rest for a while—we can take it from here.
  - **ZH**: 谢谢你照应他们。过来歇一会儿吧，接下来交给我们就好。
  - **RU**: Спасибо, что присмотрел за ними. Присядь и передохни — дальше мы справимся сами.
- **`weaver.watch_wait`**
  - **EN**: Let's make sure the neighbors are safe first. And if nothing has happened today, there's no need to go looking for trouble.
  - **ZH**: 先确认邻居们都安全吧。要是今天本来就平安无事，也不必特意去外头找麻烦。
  - **RU**: Давай сперва убедимся, что соседи в безопасности. А если ничего не случилось, специально искать неприятности не нужно.

#### Narrative Fragments (English Only)

- **`changed:clipboard`** @ `(13, 30, 19)` — Environmental lore clipboard (no `clue:*` unlock)
  ```text
  The Latex Silk Moth is not an enemy...
  
  It is a friend I can rely on.
  ```

### 2.2 Hyena Hideout (`hyena_hideout`)

- **Biome / Placement**: Savanna / Plains (Overworld)
- **Resident Dialogue**: Uses the **generic default resident dialogue tree** (`camp_resident` / `resident` voice; no camp-specific `resident_*` nodes).

#### Chief: Hideout Matriarch (`changed_synergy:latex_spotted_hyena_female`) (`hyena_chief.json`, camp voice `matriarch`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Well now—don't just stand out there in the tall grass where the night chill can catch you. Come in by the fire. You've walked a long way on two feet without fur, traveler. The well on the edge of camp has clean water, and nobody under this roof bites guests.
  - **ZH**: 哎呀——别光站在外面高草丛里吹夜风，小心着凉。快进来到火堆旁吧。没长毛靠两条腿走了这么远，辛苦你了，旅人。营地边上的井里有干净水，这屋檐下没人会咬客人。
  - **RU**: Ну же — не стой там в высокой траве на ночном холоде. Проходи к костру. Долгий же путь ты проделал на двух ногах и без шерсти, путник. В колодце на краю лагеря чистая вода, и под этой крышей гостей никто не кусает.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Skinny, Way too skinny! Looks like a plucked twig! Needs stew. Or a massive soup bone! Do we still have ribs in the chest?)
  - **ZH**: （太瘦了，实在太瘦了！看着像根拔光叶子的细树枝！得喝炖肉汤。或者来根大汤骨！箱子里还有排骨吗？）
  - **RU**: (Худой, ну до чего худой! Выглядит как ощипанная веточка! Ему нужно рагу. Или огромная мозговая кость! У нас еще остались ребрышки в сундуке?)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hey there, traveler! Shake the dust off your paws and pull up a cushion by the fire. So long as you respect the folks resting under this roof, you're on friendly ground.
  - **ZH**: 嗨，旅人！把爪子上的灰抖掉，拉个坐垫到火堆旁坐吧。只要你尊重这屋檐下歇息的伙伴，这里就是友好的落脚地。
  - **RU**: Эй, путник! Отряхни пыль с лап и подвигай подушку к огню. Пока ты уважаешь тех, кто отдыхает под этой крышей, ты среди друзей.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Good coat. Doesn't look like trouble… Better not hog the best cushion by the roast.)
  - **ZH**: （毛色不错。看着不像惹事的家伙……希望别把烤肉旁最好的坐垫给霸占了。）
  - **RU**: (Хорошая шерсть. Вроде не из буйных… Главное, чтобы не занял лучшую подушку возле жаркого.)
- **Choices**:
  - `hyena_chief_about` -> `about` (`condition=always`)
    - **EN**: Are you the one in charge of this hideout? | **ZH**: 这处藏身处是由你当家吗？ | **RU**: Ты здесь главная в убежище?
  - `hyena_chief_home` -> `home` (`condition=always`)
    - **EN**: Why did your group choose to live out here without old-world machines? | **ZH**: 你们为什么选择不用旧世界的机器，住在这野外呢？ | **RU**: Почему ваша группа решила жить здесь без машин старого мира?
  - `hyena_chief_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: I saw the diary near your bed. How long did it take you to find your people? | **ZH**: 我看到了你床铺旁的日记。你花了多久才找到同伴们的？ | **RU**: Я видел дневник возле твоей кровати. Сколько времени ушло на то, чтобы найти своих?
  - `join_menu` -> `enrol` (`condition=stranger`)
    - **EN**: I'm looking for a camp to call home. Could I join your hideout? | **ZH**: 我正在找个可以当成家的营地。我能加入你们的藏身处吗？ | **RU**: Я ищу лагерь, который мог бы назвать домом. Можно присоединиться к вашему убежищу?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About living here… | **ZH**: 关于住在这里的事…… | **RU**: Насчет жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I keep an eye on the perimeter and make sure the storage chests stay stocked, if that's what you mean. Somebody has to check the well-rope and make sure everyone comes back to the fire before the skeletons start rattling in the scrub.
  - **ZH**: 要是你说的是盯着周边动静、确保储物箱里物资充足，那确实是我在管。总得有人检查井绳，赶在骷髅在灌木丛里咔哒作响之前确保大家都回到火堆旁。
  - **RU**: Я присматриваю за периметром и слежу, чтобы сундуки не пустели, если ты об этом. Кто-то же должен проверять колодезную веревку и следить, чтобы все вернулись к огню до того, как в кустах загремят скелеты.
- **Passage 2** (`channel=spoken`, `condition=always`):
  - **EN**: But nobody here needs a boss barking orders at them. We're just a pack of peers who agreed on one simple goal: thrive, survive, and live.
  - **ZH**: 但这里没人需要一个对着大家大呼小叫的老板。我们只是一群志同道合的伙伴，认准了一个简单的目标：兴旺、生存、好好过日子。
  - **RU**: Но здесь никому не нужен начальник, раздающий приказы. Мы просто стая равных, и цель у нас одна: расти, выживать и жить в свое удовольствие.
- **Passage 3** (`channel=mental`, `condition=always`):
  - **EN**: (And if a skeleton DOES show up, dibs on crunching the femur! Best sound in the world!)
  - **ZH**: （再说了，要是真有骷髅冒出来，大腿骨归我咬！那嘎嘣声简直是世上最棒的声音！）
  - **RU**: (А если скелет ВСЕ-ТАКИ объявится — чур, бедренную кость грызу я! Лучший хруст на свете!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Don't get me wrong—some of us still remember the blessings those old devices gave us in the past. Clean heaters and automatic doors had their charms! When we built this hideout and set up the smaller trail-camps down the road, we simply decided to leave most of that machinery behind. When you haul your own water from the well and stack your own firewood, you actually notice the people standing next to you. Fresh air and timber suit us better.
  - **ZH**: 别误会——我们当中有些人还记得过去那些旧设备带来的好处。干净的暖气和自动门确实挺省心的！ 只是在我们建造这处藏身处、又顺着路设下几个小型路途营地时，大家决定把大部分机器都留在过去。 当你亲手从井里提水、亲手堆起柴火时，你才会真正留意到身边一起干活的伙伴。新鲜空气和原木更适合我们。
  - **RU**: Не пойми превратно — кое-кто из нас до сих пор помнит, какую пользу приносили старые приборы. В чистых обогревателях и автоматических дверях была своя прелесть! Просто когда мы построили это убежище и разбили небольшие дорожные стоянки вдоль тропы, мы решили оставить большую часть техники в прошлом. Когда сам таскаешь воду из колодца и складываешь дрова, начинаешь по-настоящему замечать тех, кто трудится рядом с тобой. Свежий воздух и дерево нам больше по душе.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Plus automatic sliding doors always pinched my tail… Good riddance.)
  - **ZH**: （再说自动感应门老是夹到我的尾巴……不用了才好呢。）
  - **RU**: (К тому же автоматические раздвижные двери вечно прищемляли мне хвост… Скатертью дорога.)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `past` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey, how dare you peek into my diary! ...But truth be told, I can't even remember how much time has passed since the incident that made us what we are today. In the beginning, I roamed the wilds alone for so many days, weeks, months, years... wondering if there was anyone else out there just like me. Then, one by one, I found my peers. We pitched these posts, hung the banners, and lit the center fire. Look around at us now—honestly, all I can say today is... Life cannot be better!
  - **ZH**: 喂，你怎么敢偷看我的日记！ ……不过说实话，我甚至记不清自打那场让我们变成如今这副模样的变故以来，究竟过去多少岁月了。起初，我独自在荒野里游荡了无数个日夜、无数个月年……不知道外头是否还有和我一样的同类。 后来，我一个接一个地找到了同伴。我们立起木柱、挂上旗帜，点燃了中央的篝火。看看现在的我们——老实说，如今我只想感叹一句……日子再好不过了！
  - **RU**: Эй, как тебе не стыдно подглядывать в мой дневник! …Но, по правде говоря, я уже и не помню, сколько времени прошло с того случая, который сделал нас такими. Поначалу я в одиночку бродила по пустошам дни, недели, месяцы, годы напролет… гадая, есть ли еще где-нибудь кто-то вроде меня. А потом, один за другим, мне встретились сородичи. Мы вкопали эти столбы, развесили знамена и разожгли общий костер. Оглянись вокруг — честно, сегодня я могу сказать только одно… Лучше жизни и не придумаешь!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (I literally put that diary on the desk at eye level so guests would read it. Totally worth it!)
  - **ZH**: （我特意把日记摆在桌上一眼就能看见的地方，就是为了让客人读到。太值了！）
  - **RU**: (Я ведь специально положила этот дневник на виду, чтобы гости его читали. Оно того стоило!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: I only speak for our hideout here, not for every hyena roaming the savanna. But if you're willing to help tend the fire and watch your neighbors' backs, there's a cushion with your name on it.
  - **ZH**: 我只代表我们这处藏身处，可代表不了稀树草原上所有的鬣狗。不过只要你愿意帮忙照看火堆、在伙伴背后互相照应，这里就有你的一席之地。
  - **RU**: Я говорю только за наше убежище, а не за всех гиен в саванне. Но если ты готов поддерживать огонь и прикрывать спины соседям — здесь тебя ждет своя подушка у костра.
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: You don't need spots or fangs to sit at our fire, friend. If you want to live here as a human, we'll gladly share our roof and keep the night critters off your back. Now, if you're asking to run with us in a Spotted Hyena body whether to borrow our senses for a while or to make a permanent change, I can help you with that too. Just know it's entirely your choice.
  - **ZH**: 坐在我们的火堆旁可不需要长着斑点或獠牙，朋友。如果你想以人类的身份住下，我们很乐意与你共享屋檐，帮你挡住夜里的怪物。 当然，如果你是想换上一副斑鬣狗的身躯和我们一起奔跑——无论是暂时借用我们的感官，还是彻底改变形态——我也能帮你。只要记住，这完全取决于你自己的选择。
  - **RU**: Чтобы сидеть у нашего костра, пятна и клыки не нужны, друг. Если хочешь жить здесь человеком, мы с радостью разделим с тобой кров и защитим от ночных тварей. Ну а если ты хочешь бегать с нами в теле пятнистой гиены — временно позаимствовать наши чувства или измениться навсегда, — с этим я тоже помогу. Просто знай, что выбор полностью за тобой.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Things are still a bit tense around the fire right now, so I can't offer you a place in the hideout just yet. Give our neighbors some room for now—once everyone feels safe around you again, we can talk about you staying.
  - **ZH**: 火堆旁目前气氛还有些紧绷，我暂时没法在藏身处给你安排位置。先给邻居们留些空间吧——等大家平复下来、在你身边重新感到安心了，我们再谈住下来的事。
  - **RU**: У костра сейчас немного напряженно, так что я пока не могу предложить тебе место в убежище. Дай соседям перевести дух — когда все успокоятся и снова почувствуют себя уверенно рядом с тобой, поговорим о жилье.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: Count me in. Let's look out for this place together. | **ZH**: 算我一个。我们一起照看这个地方吧。 | **RU**: Рассчитывай на меня. Будем присматривать за этим местом вместе.
  - `join_as_is` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. I'm ready to help keep the hideout running. | **ZH**: 我以人类的身份加入，随时准备帮忙打理藏身处。 | **RU**: Я присоединюсь как человек. Готов помогать по хозяйству в убежище.
  - `copy_form` -> `explain_form` (`condition=human`)
    - **EN**: What would changing into your form mean for me? | **ZH**: 变成你们的形态对我来说意味着什么？ | **RU**: Что для меня будет значить переход в твой облик?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: That depends on what you ask of me before we start. Neither way erases your memories or hurts me to share. If we do a reversible transformation, you get to feel how tough and sure-footed a hyena frame is on the trails, and you can return to your human body whenever you choose. If you ask for a permanent transformation, then this spotted coat is yours for keeps. And if you'd rather stay human, nobody here will think a bit less of you.
  - **ZH**: 这取决于开始前你的要求。无论哪种方式，都不会抹去你的记忆，也不会对我造成任何损伤。 如果我们进行可逆转化，你可以体验斑鬣狗的身躯在山野小径上有多么结实稳健，而且随时可以按自己的心意变回人类身体。 如果你要求永久转化，这身斑点皮毛就永远归你了。要是你更想保持人类之身，这里也没人会有半点看轻你。
  - **RU**: Это зависит от того, о чем ты попросишь перед началом. Ни один из вариантов не сотрет твои воспоминания и не причинит вреда мне. Если мы проведем обратимое превращение, ты сможешь ощутить, насколько крепко и уверенно тело гиены держится на тропах, и вернешься в человеческий облик, когда сам захочешь. Если же попросишь о необратимом превращении, эта пятнистая шкура останется твоей навсегда. А если предпочтешь остаться человеком — никто здесь не станет относиться к тебе хуже.
- **Choices**:
  - `confirm_copy` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the reversible transformation and join. | **ZH**: 我们试试可逆转化，然后加入这里吧。 | **RU**: Давай попробуем обратимое превращение, и я присоединюсь к вам.
  - `permanent_form` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I'm ready to make it permanent and join. | **ZH**: 我准备好永久转化并加入这里了。 | **RU**: Я готов сделать превращение необратимым и присоединиться к вам.
  - `join_as_is` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. I'm ready to help keep the hideout running. | **ZH**: 我以人类的身份加入，随时准备帮忙打理藏身处。 | **RU**: Я присоединюсь как человек. Готов помогать по хозяйству в убежище.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Glad to have you with us! Grab a blanket from the chest whenever the night wind picks up, and let us know if the well bucket ever gives you trouble.
  - **ZH**: 很高兴有你加入！夜里起风时尽管去箱子里拿毯子，要是井桶用着不顺手也随时跟我们说。
  - **RU**: Рады видеть тебя в наших рядах! Бери одеяло из сундука, когда поднимется ночной ветер, и говори, если возникнут трудности с колодезным ведром.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (First order of business: fatten this human up with some proper campfire roast!)
  - **ZH**: （头一桩要紧事：用正宗的篝火烤肉把这个人类喂胖点！）
  - **RU**: (Первым делом: откормить этого человека хорошим жарким с костра!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to the hideout! Help yourself to the stores, and don't mind the laughing by the fire pit—that's just how we unwind after sunset.
  - **ZH**: 欢迎来到藏身处！仓库里的东西随便取用，别介意火坑边的欢笑声——那是我们日落后的放松方式。
  - **RU**: Добро пожаловать в убежище! Угощайся из припасов и не обращай внимания на хохот у костра — так мы отдыхаем после заката.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Ha! Look at those ears! Go ahead, roll your shoulders… feels sturdy, doesn't it? Takes a lot of heart to step out of what you know like that. Grab a spot by the fire, neighbor—you're home!
  - **ZH**: 哈！瞧瞧这对耳朵！来，活动活动肩膀……感觉很结实吧？能这样迈出熟悉的一切，可需要不小的勇气。到火堆旁找个地方坐吧，邻居——你到家了！
  - **RU**: Ха! Только глянь на эти уши! Ну же, разомни плечи… крепкое тело, правда? Нужно немало смелости, чтобы вот так шагнуть за пределы привычного. Занимай место у костра, сосед — ты дома!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (GREAT SPOTS! BIG EARS! Bet we could chew right through an iron gate now! Let's go celebrate with ribs!)
  - **ZH**: （漂亮的斑点！大大的耳朵！我敢说我们现在能直接咬穿铁门！快去拿排骨庆祝一下！）
  - **RU**: (ОТЛИЧНЫЕ ПЯТНА! БОЛЬШИЕ УШИ! Спорим, мы теперь и железные ворота перегрызем! Пойдем праздновать ребрышками!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Well now—don't just stand out there in the tall grass where the night chill can catch you. Come in by the fire. You've walked a long way on two feet without fur, traveler. The well on the edge of camp has clean water, and nobody under this roof bites guests.
  - **ZH**: 哎呀——别光站在外面高草丛里吹夜风，小心着凉。快进来到火堆旁吧。没长毛靠两条腿走了这么远，辛苦你了，旅人。营地边上的井里有干净水，这屋檐下没人会咬客人。
  - **RU**: Ну же — не стой там в высокой траве на ночном холоде. Проходи к костру. Долгий же путь ты проделал на двух ногах и без шерсти, путник. В колодце на краю лагеря чистая вода, и под этой крышей гостей никто не кусает.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Skinny, Way too skinny! Looks like a plucked twig! Needs stew. Or a massive soup bone! Do we still have ribs in the chest?)
  - **ZH**: （太瘦了，实在太瘦了！看着像根拔光叶子的细树枝！得喝炖肉汤。或者来根大汤骨！箱子里还有排骨吗？）
  - **RU**: (Худой, ну до чего худой! Выглядит как ощипанная веточка! Ему нужно рагу. Или огромная мозговая кость! У нас еще остались ребрышки в сундуке?)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hey there, traveler! Shake the dust off your paws and pull up a cushion by the fire. So long as you respect the folks resting under this roof, you're on friendly ground.
  - **ZH**: 嗨，旅人！把爪子上的灰抖掉，拉个坐垫到火堆旁坐吧。只要你尊重这屋檐下歇息的伙伴，这里就是友好的落脚地。
  - **RU**: Эй, путник! Отряхни пыль с лап и подвигай подушку к огню. Пока ты уважаешь тех, кто отдыхает под этой крышей, ты среди друзей.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Good coat. Doesn't look like trouble… Better not hog the best cushion by the roast.)
  - **ZH**: （毛色不错。看着不像惹事的家伙……希望别把烤肉旁最好的坐垫给霸占了。）
  - **RU**: (Хорошая шерсть. Вроде не из буйных… Главное, чтобы не занял лучшую подушку возле жаркого.)
- **Choices**:
  - `hyena_chief_about` -> `about` (`condition=always`)
    - **EN**: Are you the one in charge of this hideout? | **ZH**: 这处藏身处是由你当家吗？ | **RU**: Ты здесь главная в убежище?
  - `hyena_chief_home` -> `home` (`condition=always`)
    - **EN**: Why did your group choose to live out here without old-world machines? | **ZH**: 你们为什么选择不用旧世界的机器，住在这野外呢？ | **RU**: Почему ваша группа решила жить здесь без машин старого мира?
  - `hyena_chief_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: I saw the diary near your bed. How long did it take you to find your people? | **ZH**: 我看到了你床铺旁的日记。你花了多久才找到同伴们的？ | **RU**: Я видел дневник возле твоей кровати. Сколько времени ушло на то, чтобы найти своих?
  - `join_menu` -> `enrol` (`condition=stranger`)
    - **EN**: I'm looking for a camp to call home. Could I join your hideout? | **ZH**: 我正在找个可以当成家的营地。我能加入你们的藏身处吗？ | **RU**: Я ищу лагерь, который мог бы назвать домом. Можно присоединиться к вашему убежищу?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About living here… | **ZH**: 关于住在这里的事…… | **RU**: Насчет жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Safe trails! There'll be room by the fire when you come back.
  - **ZH**: 一路平安！等你回来时，火堆旁随时给你留着位置。
  - **RU**: Доброй тропы! Когда вернешься, место у костра будет тебя ждать.
- **Choices**:
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Safe travels. | **ZH**: 保重。 | **RU**: Счастливого пути.

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Let's do one last check before we start, friend. Making this permanent means this spotted coat will be yours for good—leaving the hideout won't turn you human again, and ordinary reversal methods won't work. You can still borrow the form temporarily instead, or keep your human body; your place by our fire doesn't depend on it. Are you sure you want the permanent change?
  - **ZH**: 开始前最后确认一次，朋友。选择永久转化，这身斑点皮毛就会一直伴随着你——离开藏身处不会让你变回人类，常规的逆转方式也不会起作用。你完全可以改选暂时借用这个形态，或者保留原本的人类身体；你在火堆旁的位置并不取决于此。你确定要永久转化吗？
  - **RU**: Давай проверим в последний раз перед началом, друг. Необратимое превращение означает, что эта пятнистая шкура останется твоей навсегда: уход из убежища не вернет тебе человеческий облик, и обычный откат не сработает. Ты все еще можешь выбрать временный облик или остаться человеком — твое место у нашего костра от этого не зависит. Ты точно хочешь измениться навсегда?
- **Choices**:
  - `confirm_permanent` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I understand and choose the permanent change. | **ZH**: 是的，我想好了，我选择永久转化。 | **RU**: Да, я все понимаю и выбираю необратимое превращение.
  - `reconsider` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over again. | **ZH**: 让我再考虑一下。 | **RU**: Дай мне еще подумать.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Ha! Look at those ears! Go ahead, roll your shoulders… feels sturdy, doesn't it? Takes a lot of heart to step out of what you know like that. Grab a spot by the fire, neighbor—you're home!
  - **ZH**: 哈！瞧瞧这对耳朵！来，活动活动肩膀……感觉很结实吧？能这样迈出熟悉的一切，可需要不小的勇气。到火堆旁找个地方坐吧，邻居——你到家了！
  - **RU**: Ха! Только глянь на эти уши! Ну же, разомни плечи… крепкое тело, правда? Нужно немало смелости, чтобы вот так шагнуть за пределы привычного. Занимай место у костра, сосед — ты дома!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (GREAT SPOTS! BIG EARS! Bet we could chew right through an iron gate now! Let's go celebrate with ribs!)
  - **ZH**: （漂亮的斑点！大大的耳朵！我敢说我们现在能直接咬穿铁门！快去拿排骨庆祝一下！）
  - **RU**: (ОТЛИЧНЫЕ ПЯТНА! БОЛЬШИЕ УШИ! Спорим, мы теперь и железные ворота перегрызем! Пойдем праздновать ребрышками!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're part of our hideout household now. The shared supply chests and any open beds or cushions are free for you to use, while personal belongings stay with their owners. If you ever need to hit the road again, you're free to go whenever you like—we'll remember the time we shared, and your body stays your own.
  - **ZH**: 你现在是我们藏身处大家庭的一员了。公共物资箱和空余的铺位、坐垫都可以随意使用，私人物品则归各自的主人保管。要是哪天你需要再次启程，随时都可以走——我们会记得共度的时光，而你的身体也依然属于你自己。
  - **RU**: Теперь ты часть нашей общины в убежище. Общие сундуки с припасами и свободные спальные места или подушки открыты для тебя, а личные вещи остаются при своих хозяевах. Если снова потянет в дорогу, можешь уйти в любой момент — мы будем помнить проведенное вместе время, а твое тело остается твоим.
- **Choices**:
  - `leave_menu` -> `confirm_leave` (`condition=member`)
    - **EN**: I need to leave the hideout and move on. | **ZH**: 我需要离开藏身处，继续启程了。 | **RU**: Мне пора покинуть убежище и идти дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: All right. Before I cross your name off the hideout's shared supplies and sleeping places, are you sure? You can still drop by the fire to visit anytime, though if you only borrowed the hyena form here for the trail, walking away lets you step back out of it.
  - **ZH**: 好的。在我把你的名字从藏身处的公共物资和铺位名单上划掉之前，你确定了吗？以后随时可以回火堆旁串门，不过要是你当初只是借这副斑鬣狗的样子搭伙过日子，临走时这身形态也会一并还回来。
  - **RU**: Хорошо. Прежде чем я вычеркну твоё имя из списков общих припасов и спальных мест убежища, ты точно уверен? К нашему костру всегда можно заглянуть в гости, но если ты только брал у нас форму гиены на время, с уходом она с тебя сойдёт.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=member`, `effect=leave`)
    - **EN**: Yes, I'm ready to move on. | **ZH**: 确定，我准备启程了。 | **RU**: Да, я готов двигаться дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I've updated the list. Take care of yourself on the trails, friend. The time you spent with us by the fire still counts.
  - **ZH**: 名单我已经改好了。在路上多保重，朋友。你在火堆旁陪我们度过的时光依然珍贵。
  - **RU**: Я обновила список. Береги себя на тропах, друг. Время, которое ты провел с нами у костра, по-прежнему много для нас значит.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Thanks for the fire. I'll be heading back out. | **ZH**: 多谢招待烤火，我得继续赶路了。 | **RU**: Спасибо за место у костра. Я пойду дальше.

#### Chief Camp-Life Voice (`matriarch.*`)

- **`matriarch.changed`**
  - **EN**: Something doesn't quite match up. Check the supplies in your hand and make sure the chests have room. And if another neighbor already handled it, we won't take a second batch from you.
  - **ZH**: 数目或地方有点对不上。先看看你手里的物资，再确认箱子里有没有空位。要是别的邻居已经先办妥了，就不会再多收你一份。
  - **RU**: Что-то не сходится. Проверь припасы в руке и свободное место в сундуках. А если кто-то из соседей уже помог, второй раз мы брать не станем.
- **`matriarch.choose_item`**
  - **EN**: Hold whatever you want to share in your hand and come talk to me. I'm not going to rummage through your pack myself.
  - **ZH**: 把想分享的东西拿在手上，再来找我就行。我可不会自己翻你的背包。
  - **RU**: Возьми то, чем хочешь поделиться, в руку и подойди ко мне. Не стану же я сама рыться в твоем рюкзаке.
- **`matriarch.danger`**
  - **EN**: There's trouble right by the camp. Let's look after our neighbors first, then we can sit down and rest.
  - **ZH**: 营地边上出了点麻烦。先照应好邻居们，回头再坐下休息。
  - **RU**: У самого лагеря неприятности. Сперва поможем соседям, а потом уже сядем отдыхать.
- **`matriarch.declined`**
  - **EN**: No problem at all. Whether you want to rest or head out on the trail, take care of your own business first. We'll manage.
  - **ZH**: 没问题。不管是想休息还是出门赶路，先办好你自己的事。我们自己能张罗。
  - **RU**: Никаких проблем. Хочешь отдохнуть или пройтись по тропам — занимайся сперва своими делами. Мы справимся.
- **`matriarch.donate`**
  - **EN**: Oh, %s! We can definitely use that. How much of it are you adding to the shared stores?
  - **ZH**: 噢，是%s！这些正好用得上。你打算往公共物资里添多少？
  - **RU**: О, %s! Это нам пригодится. Сколько ты хочешь добавить в общие запасы?
- **`matriarch.food_enough`**
  - **EN**: There's food in the chests and nobody's going hungry. In my book, that makes it a good day.
  - **ZH**: 箱子里有吃的，大家都没饿着。对我来说，这样的日子就挺不错。
  - **RU**: В сундуках есть еда, и никто не голодает. По-моему, день уже удался.
- **`matriarch.food_low`**
  - **EN**: Food's running a little low. We can manage on our own, mind you, but if you have spare rations from the road, feel free to bring some back.
  - **ZH**: 吃的有点少了。我们自己也能张罗，不过你在路上要是有富余的口粮，不妨带点回来。
  - **RU**: Еды стало маловато. Мы и сами справимся, но если в дороге появятся лишние припасы — приноси, не откажемся.
- **`matriarch.food_plenty`**
  - **EN**: We've got plenty of food for everyone to enjoy. Stick around the fire later—if you want a good cushion, you'd better claim it early.
  - **ZH**: 吃的很充裕，够大家好好享用了。晚点留在火堆旁一起吃吧，好坐垫可得早点占。
  - **RU**: Еды хватит на всех с лихвой. Оставайся позже у огня — лучшие подушки стоит занимать заранее.
- **`matriarch.greeting`**
  - **EN**: Back by the fire again, eh? Good to see you. What's on your mind?
  - **ZH**: 又回到火堆旁了？挺好的。想聊点什么？
  - **RU**: Снова у костра, а? Хорошо. О чем хочешь поговорить?
- **`matriarch.handled`**
  - **EN**: That one's already taken care of. Someone around here has quick feet!
  - **ZH**: 那件事已经办妥了。咱们这儿有人动作挺快嘛！
  - **RU**: С этим уже разобрались. У кого-то здесь быстрые лапы!
- **`matriarch.meal`**
  - **EN**: All right, let's see who else is hungry. Pull up a cushion by the fire!
  - **ZH**: 好，我去看看还有谁饿了。拉个坐垫到火堆旁边来吧！
  - **RU**: Хорошо, посмотрим, кто еще проголодался. Подвигай подушку к огню!
- **`matriarch.no_requests`**
  - **EN**: Nothing urgent needs doing right now. Come warm yourself by the fire.
  - **ZH**: 眼下没什么需要你去忙的。过来火堆边暖暖身子吧。
  - **RU**: Сейчас никаких срочных дел нет. Подходи к огню, погрейся.
- **`matriarch.outfit`**
  - **EN**: You've been looking out for everyone here, and I'd love to see you wearing our gear. Here—this Trailcloth Outfit is yours.
  - **ZH**: 你一直在照应这里的伙伴，我很乐意看你穿上我们的服装。给，这套行猎装归你了。
  - **RU**: Ты все время заботишься о наших ребятах, и я буду рада видеть тебя в нашей одежде. Держи, этот костюм твой.
- **`matriarch.remember`**
  - **EN**: I remember everything you've done to help us out. Coming home empty-handed now and then doesn't change that one bit.
  - **ZH**: 你之前帮过的忙我都记着呢。偶尔空着手回来，也丝毫不会改变这些。
  - **RU**: Я помню все, чем ты нам помог. Даже если порой возвращаешься с пустыми руками, это ничего не меняет.
- **`matriarch.request`**
  - **EN**: When you have the time, bring us %s %s for the hideout to share. No rush—just come back once you've gathered the full amount.
  - **ZH**: 有空的话，帮藏身处带 %s 份%s回来，供大家一起用。不用着急，凑齐了再回来就行。
  - **RU**: Когда будет время, принеси нам %s ед. из категории «%s» для общих нужд убежища. Спешить некуда — возвращайся, когда соберешь все.
- **`matriarch.requests`**
  - **EN**: There are a few things we could use a hand with. Pick one if you feel like it—nobody here is keeping track of how busy you are.
  - **ZH**: 有几件事想找人搭把手。你愿意的话就挑一件，没人盯着你非要干多少活。
  - **RU**: Есть пара дел, где не помешают лишние руки. Выбирай любое, если есть желание, — никто тут не следит, насколько ты занят.
- **`matriarch.reverse_rite`**
  - **EN**: Told you it was reversible! If you're ready to run on two legs again, hold still and I'll pull the coat back off.
  - **ZH**: 早就跟你说过这是可逆的！要是你准备好重新用双腿奔跑了，站稳别动，我这就把外衣收回来。
  - **RU**: Я же говорила, что всё обратимо! Если хочешь снова ходить на двух ногах, замри, и я сниму эту шкуру.
- **`matriarch.safe`**
  - **EN**: Everything's quiet around the fences right now. Good time to rest your legs.
  - **ZH**: 围栏周围现在很安静。正好趁这会儿歇歇腿吧。
  - **RU**: Вокруг ограды сейчас тихо. Самое время дать ногам отдохнуть.
- **`matriarch.thanks`**
  - **EN**: That's a real help—thanks! Go grab yourself a cushion by the fire before someone else beats you to it.
  - **ZH**: 这可帮上大忙了，谢谢！快去火堆边占个坐垫吧，别让别人抢先了。
  - **RU**: Вот это выручил, спасибо! Беги занимай подушку у костра, пока кто-нибудь не опередил.
- **`matriarch.transfer`**
  - **EN**: You're already settled in at %s. Want to make the hideout your home instead? You'll still be able to visit your old neighbors. Just let me know when you've made up your mind.
  - **ZH**: 你已经在%s住下了。现在想把藏身处当成新家吗？以后你依然可以常去看望老邻居。想好了告诉我一声就行。
  - **RU**: Ты уже обосновался в общине «%s». Хочешь теперь сделать своим домом наше убежище? Старых соседей сможешь навещать по-прежнему. Скажи, когда решишь.
- **`matriarch.watch`**
  - **EN**: If any hostiles come chasing our neighbors, help drive them off. Leave our own livestock alone, though—got it? Come tell me once things settle down.
  - **ZH**: 要是有怪物来追赶邻居们，就帮忙把它赶走。不过咱们自己养的牲畜可别碰，明白吧？等事情平息了回来告诉我。
  - **RU**: Если враги погонятся за соседями, помоги их прогнать. Только нашу домашнюю живность не трогай, ладно? Как все уляжется, скажи мне.
- **`matriarch.watch_thanks`**
  - **EN**: Thanks for standing with our neighbors out there. Head back to the fire whenever you're ready.
  - **ZH**: 谢谢你和邻居们并肩应付麻烦。准备好了就回火堆旁歇息吧。
  - **RU**: Спасибо, что встал плечом к плечу с нашими соседями. Возвращайся к костру, когда будешь готов.
- **`matriarch.watch_wait`**
  - **EN**: If there's real trouble out there, let's finish dealing with it first. And if everything is quiet, leave it that way—nobody's asking you to go pick a fight.
  - **ZH**: 真有麻烦的话，就先把它处理完。要是外头本来就平平安安的，那就继续保持——可没人叫你特意去找架打。
  - **RU**: Если снаружи и правда беда, сперва разберемся с ней. А если все тихо, пусть так и остается — никто не просит тебя специально искать драки.

#### Narrative Fragments (English Only)

- *(No embedded notes, clipboards, terminals, or compact discs in this structure.)*

### 2.3 Observatory (`observatory`)

- **Biome / Placement**: Meadow / Mountain / Taiga (Overworld)
- **Resident Dialogue**: Uses the **generic default resident dialogue tree** (`camp_resident` / `resident` voice; no camp-specific `resident_*` nodes).

#### Chief: Observatory Archivist (`changed_synergy:latex_eagle_owl`) (`observatory_chief.json`, camp voice `archivist`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=day`):
  - **EN**: Mmm... five more minutes, the aurora hasn't faded yet— Hoo—! Oh, pardon me! Forgive my manners. I keep a nocturnal schedule up here as an owl, and the midday sun makes me terribly drowsy. Let me put the kettle on.
  - **ZH**: 唔……再睡五分钟，极光还没散呢——咕——！噢，抱歉！原谅我失礼了。作为一只猫头鹰，我在这山上过的是夜行作息，正午的阳光实在让我困得不行。我去把水壶烧上吧。
  - **RU**: Ммм… еще пять минуток, полярное сияние еще не погасло — Ух! Ох, прошу прощения! Простите мою неловкость. Как филин, я веду здесь ночной образ жизни, и полуденное солнце ужасно клонит меня в сон. Сейчас поставлю чайник.
- **Passage 2** (`channel=mental`, `condition=day`):
  - **EN**: (TOO BRIGHT! TURN OFF THE SUN! Who left the sun on? Need tea… Need mouse… Wait, no, tea.)
  - **ZH**: （太刺眼了！快把太阳关掉！谁把太阳一直开着的？需要喝茶……需要抓老鼠……不对，是喝茶。）
  - **RU**: (СЛИШКОМ ЯРКО! ВЫКЛЮЧИТЕ СОЛНЦЕ! Кто забыл выключить солнце? Нужен чай… Нужна мышь… Постойте, нет, чай.)
- **Passage 3** (`channel=spoken`, `condition=night_human`):
  - **EN**: Hoo! —Ahem. Good evening, traveler. And here I promised myself in my notebook that I would keep practicing my consonants so I wouldn't hoot at a guest! Word reached the telepathic network that a baseline human had walked out of that ancient complex alive... Is that truly you?
  - **ZH**: 咕！——咳咳。晚上好，旅人。亏我还在笔记本里提醒自己要多练习辅音发音，免得对着客人咕咕叫呢！心灵感应网络里传开消息，说有一位原生人类活着走出了那座古老的设施……真的就是你吗？
  - **RU**: Ух! — Кхм. Добрый вечер, путник. А ведь я обещал себе в дневнике упражняться в согласных звуках, чтобы не ухать перед гостями! По телепатической сети прошел слух, что из древнего комплекса вышел живым обычный человек… Неужели это действительно ты?
- **Passage 4** (`channel=mental`, `condition=night_human`):
  - **EN**: (Don't hoot… Don't hoot! You practiced this all week! Damn it, I hooted right in their face! Act dignified… Straighten the waistcoat.)
  - **ZH**: （别咕咕叫……千万别咕咕叫！你都练习整整一周了！该死，我还是当着人家的面咕出来了！保持端庄……把马甲理平。）
  - **RU**: (Не ухать… Только не ухать! Ты же репетировал всю неделю! Проклятье, ухнул прямо в лицо! Держись достойно… Поправь жилет.)
- **Passage 5** (`channel=spoken`, `condition=night_transformed`):
  - **EN**: Good evening, traveler. Mind the frost on the balcony steps—the highland sky is remarkably clear tonight.
  - **ZH**: 晚上好，旅人。当心露台台阶上的霜——今晚高原的夜空格外晴朗。
  - **RU**: Добрый вечер, путник. Осторожнее с изморозью на ступеньках балкона — небо над высокогорьем сегодня удивительно чистое.
- **Passage 6** (`channel=mental`, `condition=night_transformed`):
  - **EN**: (Cold talons, pretty sky. Please don't touch the telescope lens with sticky paws.)
  - **ZH**: （爪子有点凉，夜空真美。拜托千万别用黏糊糊的爪子碰望远镜镜头。）
  - **RU**: (Лапам холодно, небо красивое. Только, пожалуйста, не трогай линзу телескопа липкими лапами.)
- **Choices**:
  - `observatory_chief_about` -> `about` (`condition=always`)
    - **EN**: What do you do up here on the mountain? | **ZH**: 你在这高山上平时都做些什么？ | **RU**: Чем ты занимаешься здесь, на горе?
  - `observatory_chief_neighbors` -> `neighbors` (`condition=always`)
    - **EN**: Tell me about the camp and the others living around the observatory. | **ZH**: 和我说说这座营地，还有住在天文台周围的其他人吧。 | **RU**: Расскажи о лагере и о других обитателях вокруг обсерватории.
  - `observatory_chief_observations` -> `observations` (`condition=always`, `requires=['about', 'neighbors']`)
    - **EN**: I read the notebook by your bed and perch—about Spacecraft No. 4 and the rumors. | **ZH**: 我读了你床铺和栖木旁的笔记本——上面写了四号飞船和那些传闻。 | **RU**: Я прочитал тетрадь у твоей кровати и насеста — о Космическом корабле № 4 и слухах.
  - `join_menu` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room at the Observatory Camp for another neighbor? | **ZH**: 天文台营地还住得下新邻居吗？ | **RU**: Найдется ли в лагере у обсерватории место для еще одного соседа?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About living here… | **ZH**: 关于住在这里的事…… | **RU**: Насчет жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I keep records of the sky, oil the dome's brass gears so the frost doesn't seize them, and brew tea for whoever climbs the mountain paths. My bedroom downstairs has a perfectly good bed, mind you, though nine mornings out of ten I still end up sleeping on the horizontal perch beside my lectern… or the perch upstairs when I'm too tired to climb down. Old habits and owl instincts are a funny pair.
  - **ZH**: 我负责记录天象，给穹顶的黄铜齿轮上油免得它们被霜冻卡死，顺便为顺着山路爬上来的客人泡茶。 说起来，我楼下的卧室里明明有一张完好舒适的床铺，但十个早晨里有九个，我最后还是睡在讲台旁的横木栖架上……或者累得不想下楼时，直接睡在楼上的栖架上。旧习惯和猫头鹰的本能凑在一起，确实挺奇妙的。
  - **RU**: Я веду наблюдения за небом, смазываю латунные шестерни купола, чтобы их не сковало морозом, и завариваю чай для всех, кто поднимается по горным тропам. Между прочим, в моей спальне внизу стоит отличная кровать, но в девяти случаях из десяти к утру я все равно оказываюсь на горизонтальном насесте рядом с кафедрой… или на насесте наверху, когда слишком устаю, чтобы спускаться. Старые привычки и совиные инстинкты — забавное сочетание.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Mattresses are flat. How does anyone grip a flat rectangle with talons? Perch is ten times better!)
  - **ZH**: （床垫是平的。谁能用猛禽爪子抓牢一个扁平的长方形啊？栖木好用十倍！）
  - **RU**: (Матрасы же плоские. Как вообще можно ухватиться когтями за плоский прямоугольник? Насест в десять раз удобнее!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `neighbors` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: We are a small mountain household—just the Yuins, our Blue Wolf neighbor, the Pink Wyvern, and myself sharing the dome, the annex, and the tents by the campfire outside. On clear nights, two of the youngsters from the tents below love to scramble up onto the domed roof to keep watch with me.
  - **ZH**: 我们是个高山上的小家庭——只有几只尤因（Yuin）、我们的蓝狼邻居、粉红双足飞龙，还有我，共同分享这座穹顶、偏厅以及外头篝火旁的帐篷。每逢晴朗的夜晚，下面帐篷里的两个小家伙总爱爬到圆顶屋面上去陪我一起守夜观测。
  - **RU**: У нас небольшая горная община — только юины, наш сосед Синий Волк, Розовая Виверна и я делим между собой купол, пристройку и палатки у костра снаружи. В ясные ночи двое малышей из нижних палаток любят забираться на купольную крышу и нести вахту вместе со мной.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (They think the Pink Wyvern is Pink and the Blue Wolf is Blue. Without my goggles on, everyone here is just different shades of grey fluff…)
  - **ZH**: （他们觉得粉红飞龙是粉色的、蓝狼是蓝色的。可在我摘下护目镜的时候，这儿的所有人看起来都只是深浅不一的灰色毛团……）
  - **RU**: (Они думают, что Розовая Виверна розовая, а Синий Волк синий. Без моих очков все вокруг — просто пушистые комочки разных оттенков серого…)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `observations` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Ah, my November and December entries. Yes—in the early hours of November second, I caught the reflected glint of Spacecraft No. 4 passing three degrees south of Orion's Belt. From what my lens can measure, it is holding its course and right on schedule. Where it is ultimately bound, or how life looks aboard it now, I cannot tell you; the light reaching my telescope is already several years old.
  - **ZH**: 啊，是我十一月和十二月记下的内容。没错——在十一月二日凌晨，我捕捉到了四号飞船掠过猎户座腰带以南三度时的反射微光。从我的镜头测算来看，它依然保持着既定航向，分毫不差。 至于它最终驶向何方、如今船上的生活又是什么模样，我就无从得知了；传到我望远镜里的星光，其实已经是好几年前发出的了。
  - **RU**: А, мои ноябрьские и декабрьские записи. Да — ранним утром второго ноября я поймал отраженный блеск Космического корабля № 4, когда он проходил в трех градусах к югу от Пояса Ориона. Судя по измерениям моего объектива, он держит курс и идет точно по графику. Куда он направляется в конечном счете и как выглядит жизнь на его борту сейчас, я сказать не могу: свет, достигающий моего телескопа, провел в пути уже несколько лет.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Still flying straight. Good for them. Glad I'm down here with a kettle instead of stuck in a tin can…)
  - **ZH**: （还在笔直往前飞呢。真替他们高兴。不过我还是庆幸自己待在地面上守着茶壶，而不是闷在铁皮罐头里……）
  - **RU**: (Все еще летит прямо. Рад за них. И все-таки хорошо, что я здесь внизу с чайником, а не заперт в жестяной банке…)
- **Choices**:
  - `old_note` -> `old_note` (`condition=always`)
    - **EN**: What about the old note saying 'Never forget… November 1st 2026'? | **ZH**: 那张写着“勿忘……2026年11月1日”的旧纸条又是怎么回事？ | **RU**: А что насчет старой записки со словами «Никогда не забывай… 1 ноября 2026 года»?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `old_note` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I found that slip tucked here long ago, and I left it untouched. I don't know who wrote it, or what November first meant to them back in 2026. A good archivist doesn't invent answers just to fill a blank page—sometimes the most respectful thing we can do is keep the note dry.
  - **ZH**: 我很久以前就在这里发现了那张塞着的纸条，一直原封不动地留着。我不知道是谁写的，也不知道在2026年的时候，十一月一日对写下它的人意味着什么。一名合格的档案员不会为了填补空白而凭空编造答案——有时候，我们能做的最尊重过去的事，就是别让纸条受潮。
  - **RU**: Я давным-давно нашел этот листок здесь и оставил его нетронутым. Не знаю, кто его написал и что значило для этого человека первое ноября 2026 года. Хороший архивариус не выдумывает ответы просто ради того, чтобы заполнить пустую страницу — порой самое уважительное, что мы можем сделать, это уберечь записку от сырости.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: I only speak for our little camp around the dome and tents, not the whole mountain range. But we are always glad for another lantern-holder. The paths get snowy, and good neighbors make the winter short.
  - **ZH**: 我只代表穹顶和帐篷周围这处小营地，代表不了整片山脉。不过我们随时欢迎新同伴来一起提灯守夜。山路常有积雪，有好邻居相伴，冬天也会显得短一些。
  - **RU**: Я говорю только за наш небольшой лагерь вокруг купола и палаток, а не за весь горный хребет. Но мы всегда рады новому хранителю фонаря. Тропы здесь заметает снегом, а с добрыми соседями и зима кажется короче.
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: You are welcome at our campfire and inside the annex just as you are. Being part of our camp does not require feathers or fur, having someone with human fingers to help calibrate the smaller brass fine-adjustment screws would honestly be a blessing! That said, if you wish to experience the night sky through an Eagle Owl's eyes either as a temporary, reversible flight or a permanent change, I can share my form with you, provided the consequences are completely clear first.
  - **ZH**: 非常欢迎你以现在的模样坐在我们的篝火旁、住进偏厅里。成为营地的一员并不需要长出羽毛或皮毛，说实话，要是有人类灵巧的手指帮忙校准那些细小的黄铜微调螺丝，那简直是帮了大忙！ 话虽如此，如果你希望透过雕鸮的眼睛去仰望夜空——无论是作为暂时的、可逆的体验，还是永久的改变——只要先把利弊后果了解清楚，我也很乐意与你分享这一形态。
  - **RU**: Мы будем рады видеть тебя у нашего костра и в пристройке таким, какой ты есть. Чтобы стать частью лагеря, перья или шерсть не нужны — честно говоря, человеческие пальцы, способные настраивать мелкие латунные регулировочные винты, станут для нас настоящим спасением! Впрочем, если ты хочешь взглянуть на ночное небо глазами филина — будь то временный обратимый опыт или перемена навсегда, — я могу поделиться с тобой своим обликом, при условии, что все последствия будут заранее ясны.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: The camp is still a little unsettled at the moment, so I cannot offer you a place at the observatory just yet. Give our neighbors on the ridge some space for now—once everyone feels comfortable around you again, we can talk about you staying.
  - **ZH**: 营地里目前还有些不安稳，我暂时没法在天文台给你安排住处。先给山脊上的邻居们留些空间吧——等大家在你身边重新感到安心了，我们再谈留宿的事。
  - **RU**: В лагере сейчас не вполне спокойно, поэтому я пока не могу предложить тебе место в обсерватории. Дай нашим соседям на гребне немного времени — когда все снова почувствуют себя спокойно рядом с тобой, мы поговорим о жилье.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: I'd love to stay and help look after the observatory. | **ZH**: 我很乐意留下来帮忙照看天文台。 | **RU**: Я с радостью останусь и помогу присматривать за обсерваторией.
  - `join_as_is` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. I'd be happy to help with the instruments and camp chores. | **ZH**: 我以人类的身份加入，很乐意帮忙维护仪器和分担营地杂务。 | **RU**: Я присоединюсь как человек. Буду рад помочь с приборами и делами по лагерю.
  - `copy_form` -> `explain_form` (`condition=human`)
    - **EN**: What would changing into your form mean for me? | **ZH**: 变成你的形态对我来说意味着什么？ | **RU**: Что для меня будет значить переход в твой облик?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: First, neither option erases your memories or harms me in the slightest. However, an Eagle Owl's eyes trade away color vision in exchange for extraordinary nocturnal sensitivity. Without an Aviator Outfit equipped with color-correcting goggles like the pair I am wearing right now, you will see the world entirely in crisp monochrome. A reversible transformation lets you borrow this feathered form and its night sight while keeping the ability to return to your human body. A permanent transformation makes this owl body yours forever. And if you prefer to decline both, the tea kettle is still yours to share.
  - **ZH**: 首先，无论哪种选择都不会抹去你的记忆，也不会对我造成丝毫损伤。 不过，雕鸮的眼睛为了换取敏锐的夜间视力，牺牲了色彩视觉。如果不穿戴一套配有校色护目镜的飞行员装束——就像我现在戴着的这副——你眼中的世界将会完全变成清晰的单色黑白画面。 可逆转化能让你暂时借用这副羽翼和夜视能力，同时保留变回人类身体的自由。永久转化则会让这具猫头鹰躯体永远属于你。而如果你两边都不选，茶壶里的热茶也依然随时有你的一杯。
  - **RU**: Во-первых, ни один из вариантов не сотрет твои воспоминания и ничуть не навредит мне. Однако глаза филина жертвуют цветным зрением ради исключительной ночной чувствительности. Без костюма авиатора со специальными цветокорректирующими очками — вроде тех, что сейчас на мне, — ты будешь видеть мир исключительно в четких черно-белых тонах. Обратимое превращение позволит тебе позаимствовать этот пернатый облик и ночное зрение, сохраняя возможность вернуться в человеческое тело. Необратимое превращение сделает тело филина твоим навсегда. А если ты предпочтешь отказаться от обоих вариантов — наш чайник все равно в твоем распоряжении.
- **Choices**:
  - `confirm_copy` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the reversible transformation and join. | **ZH**: 我们试试可逆转化，然后加入这里吧。 | **RU**: Давай попробуем обратимое превращение, и я присоединюсь к вам.
  - `permanent_form` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I'm ready to make it permanent and join. | **ZH**: 我准备好永久转化并加入这里了。 | **RU**: Я готов сделать превращение необратимым и присоединиться к вам.
  - `join_as_is` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. I'd be happy to help with the instruments and camp chores. | **ZH**: 我以人类的身份加入，很乐意帮忙维护仪器和分担营地杂务。 | **RU**: Я присоединюсь как человек. Буду рад помочь с приборами и делами по лагерю.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Splendid! Help yourself to a tent by the fire or a warm corner in the annex—and if you see the youngsters climbing the dome roof at midnight, do remind them not to lean on the main lens housing.
  - **ZH**: 太好了！篝火旁的帐篷或是偏厅里暖和的角落都可以随便住——要是半夜看见那两个小家伙往圆顶屋面上爬，可得提醒他们别靠在主镜头外罩上。
  - **RU**: Чудесно! Выбирай любую палатку у костра или теплый уголок в пристройке — и если увидишь, как малыши в полночь лезут на крышу купола, обязательно напомни им не опираться на кожух главной линзы.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (YES! Opposable thumbs with fingernails! Finally someone who can pick up dropped brass washers off the floor!)
  - **ZH**: （太棒了！带指甲的对生拇指！总算有人能把掉在地板上的黄铜垫片抠起来了！）
  - **RU**: (ДА! Противопоставленные большие пальцы с ногтями! Наконец-то кто-то сможет подбирать упавшие на пол латунные шайбы!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to the Observatory Camp, neighbor. The upstairs perch has a wonderful view of the aurora whenever you want a quiet hour.
  - **ZH**: 欢迎来到天文台营地，邻居。楼上的栖架看极光视野极好，想安静待一会儿的时候随时可以上去。
  - **RU**: Добро пожаловать в лагерь у обсерватории, сосед. С верхнего насеста открывается чудесный вид на полярное сияние, если захочется провести часок в тишине.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There we are... mind your ear-tufts in the doorway, and look up at the sky now. Everything is in shades of silver and grey without color vision, yet the starlight is fifty times brighter, isn't it? We will get you fitted with an Aviator Outfit and goggles soon so you can see the greens and violets of the aurora again. Welcome home.
  - **ZH**: 好了……过门框时当心头顶的耳羽簇，现在抬头看看夜空吧。虽然失去色彩视觉后万物都成了银灰与黑白的色调，但星光却比从前亮了五十倍，对吧？ 我们很快就会给你配上一套带护目镜的飞行员装束，好让你重新看见翠绿与紫罗兰色的极光。欢迎回家。
  - **RU**: Вот и все… береги перьевые ушки в дверном проеме и взгляни теперь на небо. Без цветного зрения все вокруг стало серебристо-серым, но звездный свет теперь в пятьдесят раз ярче, не правда ли? Скоро мы подберем тебе костюм авиатора с очками, чтобы ты снова мог видеть зеленые и фиолетовые переливы полярного сияния. Добро пожаловать домой.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Everything is grey! Wait… where did my colors go?! Oh right, goggles! Also, did a mouse just rustle under the snow three hundred yards away?!)
  - **ZH**: （全变成灰色的了！等等……我的颜色去哪儿了？！噢对，得戴护目镜！还有，三百码外的雪层底下刚刚是不是有只老鼠在窸窣乱动？！）
  - **RU**: (Все серое! Постойте… куда делись цвета?! Ах да, очки! И вообще, неужели в трехстах ярдах отсюда под снегом только что прошуршала мышь?!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=day`):
  - **EN**: Mmm... five more minutes, the aurora hasn't faded yet— Hoo—! Oh, pardon me! Forgive my manners. I keep a nocturnal schedule up here as an owl, and the midday sun makes me terribly drowsy. Let me put the kettle on.
  - **ZH**: 唔……再睡五分钟，极光还没散呢——咕——！噢，抱歉！原谅我失礼了。作为一只猫头鹰，我在这山上过的是夜行作息，正午的阳光实在让我困得不行。我去把水壶烧上吧。
  - **RU**: Ммм… еще пять минуток, полярное сияние еще не погасло — Ух! Ох, прошу прощения! Простите мою неловкость. Как филин, я веду здесь ночной образ жизни, и полуденное солнце ужасно клонит меня в сон. Сейчас поставлю чайник.
- **Passage 2** (`channel=mental`, `condition=day`):
  - **EN**: (TOO BRIGHT! TURN OFF THE SUN! Who left the sun on? Need tea… Need mouse… Wait, no, tea.)
  - **ZH**: （太刺眼了！快把太阳关掉！谁把太阳一直开着的？需要喝茶……需要抓老鼠……不对，是喝茶。）
  - **RU**: (СЛИШКОМ ЯРКО! ВЫКЛЮЧИТЕ СОЛНЦЕ! Кто забыл выключить солнце? Нужен чай… Нужна мышь… Постойте, нет, чай.)
- **Passage 3** (`channel=spoken`, `condition=night_human`):
  - **EN**: Hoo! —Ahem. Good evening, traveler. And here I promised myself in my notebook that I would keep practicing my consonants so I wouldn't hoot at a guest! Word reached the telepathic network that a baseline human had walked out of that ancient complex alive... Is that truly you?
  - **ZH**: 咕！——咳咳。晚上好，旅人。亏我还在笔记本里提醒自己要多练习辅音发音，免得对着客人咕咕叫呢！心灵感应网络里传开消息，说有一位原生人类活着走出了那座古老的设施……真的就是你吗？
  - **RU**: Ух! — Кхм. Добрый вечер, путник. А ведь я обещал себе в дневнике упражняться в согласных звуках, чтобы не ухать перед гостями! По телепатической сети прошел слух, что из древнего комплекса вышел живым обычный человек… Неужели это действительно ты?
- **Passage 4** (`channel=mental`, `condition=night_human`):
  - **EN**: (Don't hoot… Don't hoot! You practiced this all week! Damn it, I hooted right in their face! Act dignified… Straighten the waistcoat.)
  - **ZH**: （别咕咕叫……千万别咕咕叫！你都练习整整一周了！该死，我还是当着人家的面咕出来了！保持端庄……把马甲理平。）
  - **RU**: (Не ухать… Только не ухать! Ты же репетировал всю неделю! Проклятье, ухнул прямо в лицо! Держись достойно… Поправь жилет.)
- **Passage 5** (`channel=spoken`, `condition=night_transformed`):
  - **EN**: Good evening, traveler. Mind the frost on the balcony steps—the highland sky is remarkably clear tonight.
  - **ZH**: 晚上好，旅人。当心露台台阶上的霜——今晚高原的夜空格外晴朗。
  - **RU**: Добрый вечер, путник. Осторожнее с изморозью на ступеньках балкона — небо над высокогорьем сегодня удивительно чистое.
- **Passage 6** (`channel=mental`, `condition=night_transformed`):
  - **EN**: (Cold talons, pretty sky. Please don't touch the telescope lens with sticky paws.)
  - **ZH**: （爪子有点凉，夜空真美。拜托千万别用黏糊糊的爪子碰望远镜镜头。）
  - **RU**: (Лапам холодно, небо красивое. Только, пожалуйста, не трогай линзу телескопа липкими лапами.)
- **Choices**:
  - `observatory_chief_about` -> `about` (`condition=always`)
    - **EN**: What do you do up here on the mountain? | **ZH**: 你在这高山上平时都做些什么？ | **RU**: Чем ты занимаешься здесь, на горе?
  - `observatory_chief_neighbors` -> `neighbors` (`condition=always`)
    - **EN**: Tell me about the camp and the others living around the observatory. | **ZH**: 和我说说这座营地，还有住在天文台周围的其他人吧。 | **RU**: Расскажи о лагере и о других обитателях вокруг обсерватории.
  - `observatory_chief_observations` -> `observations` (`condition=always`, `requires=['about', 'neighbors']`)
    - **EN**: I read the notebook by your bed and perch—about Spacecraft No. 4 and the rumors. | **ZH**: 我读了你床铺和栖木旁的笔记本——上面写了四号飞船和那些传闻。 | **RU**: Я прочитал тетрадь у твоей кровати и насеста — о Космическом корабле № 4 и слухах.
  - `join_menu` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room at the Observatory Camp for another neighbor? | **ZH**: 天文台营地还住得下新邻居吗？ | **RU**: Найдется ли в лагере у обсерватории место для еще одного соседа?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About living here… | **ZH**: 关于住在这里的事…… | **RU**: Насчет жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Clear skies, traveler. Mind the frost on the steps, and keep your lantern dry.
  - **ZH**: 愿夜空晴朗，旅人。当心台阶上的霜冻，收好提灯别受了潮。
  - **RU**: Чистого неба, путник. Осторожнее с изморозью на ступеньках и береги фонарь от сырости.
- **Choices**:
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Safe travels. | **ZH**: 保重。 | **RU**: Счастливого пути.

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Let us be completely sure before we begin. A permanent change leaves you with this feathered body and its monochrome sight for good; ordinary reversal methods cannot restore your human body. Goggles will bring back the colors, but they do not undo the transformation itself. You may still choose a reversible experience instead, or join us just as you are. Do you want to make the Eagle Owl form permanent?
  - **ZH**: 那么在开始之前，我们最后确认一次。永久转化会让你一直保留这具覆羽之躯与单色视觉，常规的逆转方式无法恢复你的人类身体。护目镜虽然能恢复色彩，但并不会解除形态转化本身。你依然可以改选可逆的体验，或者以现在的身份加入我们。你确定要永久保持雕鸮形态吗？
  - **RU**: Тогда давай убедимся окончательно, прежде чем начать. Необратимое превращение навсегда оставит тебе это пернатое тело и черно-белое зрение; обычный откат уже не вернет человеческий облик. Очки вернут цвета, но само превращение не отменят. Ты все еще можешь выбрать обратимый опыт или присоединиться к нам таким, какой есть. Сделать облик филина постоянным?
- **Choices**:
  - `confirm_permanent` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I understand and choose the permanent change. | **ZH**: 是的，我想好了，我选择永久转化。 | **RU**: Да, я все понимаю и выбираю необратимое превращение.
  - `reconsider` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over again. | **ZH**: 让我再考虑一下。 | **RU**: Дай мне еще подумать.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There we are... mind your ear-tufts in the doorway, and look up at the sky now. Everything is in shades of silver and grey without color vision, yet the starlight is fifty times brighter, isn't it? We will get you fitted with an Aviator Outfit and goggles soon so you can see the greens and violets of the aurora again. Welcome home.
  - **ZH**: 好了……过门框时当心头顶的耳羽簇，现在抬头看看夜空吧。虽然失去色彩视觉后万物都成了银灰与黑白的色调，但星光却比从前亮了五十倍，对吧？我们很快就会给你配上一套带护目镜的飞行员装束，好让你重新看见翠绿与紫罗兰色的极光。欢迎回家。
  - **RU**: Вот и все… береги перьевые ушки в дверном проеме и взгляни теперь на небо. Без цветного зрения все вокруг стало серебристо-серым, но звездный свет теперь в пятьдесят раз ярче, не правда ли? Скоро мы подберем тебе костюм авиатора с очками, чтобы ты снова мог видеть зеленые и фиолетовые переливы полярного сияния. Добро пожаловать домой.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Everything is grey! Wait… where did my colors go?! Oh right, goggles! Also, did a mouse just rustle under the snow three hundred yards away?!)
  - **ZH**: （全变成灰色的了！等等……我的颜色去哪儿了？！噢对，得戴护目镜！还有，三百码外的雪层底下刚刚是不是有只老鼠在窸窣乱动？！）
  - **RU**: (Все серое! Постойте… куда делись цвета?! Ах да, очки! И вообще, неужели в трехстах ярдах отсюда под снегом только что прошуршала мышь?!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're part of our observatory household now. Shared supplies and unoccupied beds or perches are open to our neighbors, while personal belongings stay with their keepers. You're free to leave whenever you need to—we'll remember the watches we shared, and your body remains your own.
  - **ZH**: 你现在是我们天文台大家庭的一员了。公共物资和空余的床铺、栖架都向邻居们开放，私人物品则归各自的保管者所有。如果你需要离开，随时都可以动身。我们会记得一起守夜的时光，而你的身体也依然属于你自己。
  - **RU**: Теперь твое место здесь, в нашей общине у обсерватории. Общие припасы и свободные кровати или насесты открыты для соседей, а личные вещи остаются за их хранителями. Ты волен уйти в любой момент, когда понадобится, — мы будем помнить совместные вахты, а твое тело остается твоим.
- **Choices**:
  - `leave_menu` -> `confirm_leave` (`condition=member`)
    - **EN**: I need to leave the observatory household and move on. | **ZH**: 我需要离开天文台营地，继续启程了。 | **RU**: Мне пора покинуть общину обсерватории и отправиться дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Very well. Before I take your name off our shared supplies and sleeping places, are you sure? You're always welcome to climb the ridge for a visit, though if these wings and feathers were only borrowed for your time up here, leaving lets you return to your own skin.
  - **ZH**: 好的。在我把你的名字从公共物资和铺位名单上划掉之前，你确定了吗？以后随时可以爬上山脊来串门，不过要是这身羽翼只是你在山上借用了一阵子的，下山离开时它便会褪去，让你变回原先的样子。
  - **RU**: Хорошо. Прежде чем я вычеркну твоё имя из списков общих припасов и спальных мест, ты точно уверен? Ты всегда можешь подняться к нам в гости, но если эти перья были взяты лишь на время, с уходом с хребта ты вернёшься в прежний облик.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=member`, `effect=leave`)
    - **EN**: Yes, I'm ready to move on. | **ZH**: 确定，我准备启程了。 | **RU**: Да, я готов двигаться дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I've updated the records. Clear skies on your travels, neighbor. The time you spent keeping watch with us here still counts.
  - **ZH**: 名单我已经改好了。愿你旅途夜空晴朗，邻居。你在这里陪我们度过的时光依然珍贵。
  - **RU**: Я обновил записи. Чистого неба тебе в пути, сосед. Время, которое ты провел с нами на горной вахте, не прошло даром.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else. | **ZH**: 聊点别的…… | **RU**: Поговорим о другом.
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Clear skies. I'll let you get back to your lens. | **ZH**: 愿夜空晴朗。不打扰你观测了。 | **RU**: Чистого неба. Не буду отвлекать тебя от телескопа.

#### Chief Camp-Life Voice (`archivist.*`)

- **`archivist.changed`**
  - **EN**: We cannot complete that transfer right now. Either the request is already finished, the item doesn't match, or the storage chests are full. Let's check before moving your things.
  - **ZH**: 眼下还不能办妥交接。可能是请求已经完成、物资对不上，也可能是储物箱没有空位了。我们先核对清楚，再动你的东西。
  - **RU**: Сейчас передать это не выйдет. Возможно, поручение уже выполнено, предмет не подходит или в сундуках нет места. Давай сначала проверим, прежде чем трогать твои вещи.
- **`archivist.choose_item`**
  - **EN**: Hold the supplies you wish to donate in your hand so I can see them. I wouldn't want to guess what you're carrying in your pack.
  - **ZH**: 把想捐的物资拿在手上让我看看吧。我可不想擅自揣测你的背包里装了什么。
  - **RU**: Возьми припасы, которые хочешь передать, в руку, чтобы я их видел. Не хотелось бы гадать, что лежит у тебя в рюкзаке.
- **`archivist.danger`**
  - **EN**: One of our neighbors is dealing with danger right now. The dome can wait—let's help them first.
  - **ZH**: 眼下还有邻居在应付危险。穹顶的事可以先放一放，大家先去帮他们。
  - **RU**: Кто-то из соседей сейчас отбивается от угрозы. Дела у купола подождут — сперва поможем им.
- **`archivist.declined`**
  - **EN**: That is quite all right. It can wait, or we can ask another neighbor. Watch your step on the way out.
  - **ZH**: 当然没关系。这事可以再等等，我们也能问问其他邻居。路上多留神。
  - **RU**: Конечно, ничего страшного. Дело подождет, или мы попросим другого соседа. Осторожнее в дороге.
- **`archivist.donate`**
  - **EN**: You have %s to share with us? Thank you. How much would you like to add to the public reserve?
  - **ZH**: 你带了%s想拿来分享？谢谢你。打算往公共储备里添多少呢？
  - **RU**: У тебя есть %s для общего стола? Спасибо. Сколько ты хочешь добавить в общий запас?
- **`archivist.food_enough`**
  - **EN**: The camp has enough food to get by, so we can turn our attention back to the night sky for now.
  - **ZH**: 营地里的食物够用，暂时可以把注意力放回夜空观测上了。
  - **RU**: Еды в лагере достаточно, так что пока можно снова сосредоточиться на ночном небе.
- **`archivist.food_low`**
  - **EN**: Our food stores could use replenishing. Nobody expects you to head out into the snow on our account, but any spare rations would be very welcome.
  - **ZH**: 食物储备该补一补了。没人要求你为了大家冒雪出门，不过手头要是有多余的干粮，我们非常欢迎。
  - **RU**: Запасы еды пора бы пополнить. Никто не требует, чтобы ты шел ради нас в снег и метель, но лишней провизии мы будем очень рады.
- **`archivist.food_plenty`**
  - **EN**: Our supplies are generously stocked. Before you head out along the ridge again, do stay and share a meal with us.
  - **ZH**: 目前物资备得很充足。下次沿着山脊出门之前，不妨留下来一起吃顿饭。
  - **RU**: Припасов сейчас вдоволь. Прежде чем снова отправишься по гребню, останься перекусить с нами.
- **`archivist.greeting`**
  - **EN**: Ah, there you are. Pull up a chair by the kettle if you've got a moment.
  - **ZH**: 啊，你来了。水壶刚座上火，有事想问就坐下慢慢聊。
  - **RU**: А, вот и ты. Присаживайся у чайника, если есть минутка.
- **`archivist.handled`**
  - **EN**: Someone else has already taken care of that matter. I ought to update my notes.
  - **ZH**: 那件事已经有人办妥了。我也该更新一下手头的笔记了。
  - **RU**: Это поручение уже выполнили. Надо будет обновить мои записи.
- **`archivist.meal`**
  - **EN**: I'll go see who is free to join us. Find yourself a comfortable spot by the fire in the meantime.
  - **ZH**: 我去看看谁有空过来一起吃。你先在火堆边找个舒服的位置坐下吧。
  - **RU**: Я пойду посмотрю, кто свободен и сможет присоединиться. А ты пока выбирай удобное место у костра.
- **`archivist.no_requests`**
  - **EN**: There is nothing urgent at the moment. Simply sitting quietly together for a while is more than enough.
  - **ZH**: 目前没有急事。安安静静过来坐一会儿，就已经很好了。
  - **RU**: Срочных дел нет. Просто спокойно посидеть немного вместе — уже хорошо.
- **`archivist.outfit`**
  - **EN**: You're a familiar face around here now. This Aviator Outfit is for you—I hope it serves you well on your travels.
  - **ZH**: 你在这里已经是熟面孔了。这套飞行服送给你，希望你外出时能派上用场。
  - **RU**: Ты у нас уже свой человек. Этот костюм авиатора — для тебя; надеюсь, он пригодится тебе в странствиях.
- **`archivist.remember`**
  - **EN**: Everyone here has seen the help you've given us. You're always welcome to come back empty-handed just to watch the night sky together.
  - **ZH**: 你之前帮过的忙，大家都看在眼里。哪怕什么都不带，也随时欢迎你回来和我们一起仰望夜空。
  - **RU**: Все здесь помнят твою помощь. Ты всегда можешь вернуться и с пустыми руками — просто чтобы вместе посмотреть на звездное небо.
- **`archivist.request`**
  - **EN**: We need %s %s for the camp. Bring them by whenever you have the full amount ready, and they will go into the shared supplies.
  - **ZH**: 我们需要 %s 份%s。方便的时候凑齐带来就好，会放进营地的公共物资里。
  - **RU**: Нам нужно %s ед. припасов из категории «%s». Приноси, когда соберешь все полностью, — это пойдет в общие запасы лагеря.
- **`archivist.requests`**
  - **EN**: There are a few small errands where a hand would be appreciated. Why not see if any of them fit into your plans?
  - **ZH**: 正好有几件小事想请人搭把手。看看有没有哪件顺合你计划的？
  - **RU**: Есть несколько небольших поручений. Посмотри, вдруг какое-нибудь из них впишется в твои планы?
- **`archivist.reverse_rite`**
  - **EN**: As promised, this flight was merely a temporary perspective. Stay still while I carefully draw the feathered form away.
  - **ZH**: 正如我承诺过的那样，这场飞行只是一次暂时的体验。请站稳别动，我来仔细收回羽翼形态。
  - **RU**: Как я и обещал, этот полёт был лишь временным опытом. Стой смирно, пока я аккуратно снимаю пернатый облик.
- **`archivist.safe`**
  - **EN**: Nobody is in any trouble at the moment—though you should still watch your step on those frosted stairs.
  - **ZH**: 眼下没人遇上麻烦。不过走那些结霜的台阶时，脚下还是得留神。
  - **RU**: Сейчас никому опасность не грозит. Впрочем, на обледенелых ступеньках все равно стоит беречь ноги.
- **`archivist.thanks`**
  - **EN**: Thank you. That is one less thing for our neighbors to worry about. Once you've caught your breath, come inside out of the wind.
  - **ZH**: 谢谢你，邻居们又少了一件操心事。喘过气来就快进屋避避山风吧。
  - **RU**: Спасибо. Теперь у соседей на одну заботу меньше. Как переведешь дух, заходи внутрь с ветра.
- **`archivist.transfer`**
  - **EN**: Your current home is at %s. Would you like to move your registration to the observatory instead? Your past friendships will all remain intact, or you can keep things as they are if you prefer.
  - **ZH**: 你目前的家在%s。要把归属改到天文台营地来吗？以前的友谊都会完好保留；如果你愿意，也可以一切照旧。
  - **RU**: Сейчас твой дом — %s. Хочешь перенести регистрацию в обсерваторию? Прежняя дружба сохранится, но если хочешь, можно оставить все как есть.
- **`archivist.watch`**
  - **EN**: If anything threatens the camp, please help protect the residents. There is no need to seek out danger on our behalf—just let me know what happened once everyone is safe.
  - **ZH**: 如果有敌对生物威胁营地，请帮忙保护居民。不必为了我们主动去找危险，等大家安全下来后回来告诉我就好。
  - **RU**: Если лагерю будет угрожать опасность, помоги защитить жителей. Специально искать угрозу ради нас не нужно — просто расскажи мне, когда все будут в безопасности.
- **`archivist.watch_thanks`**
  - **EN**: Thank you. Knowing we look out for one another makes the nights on the mountain feel much safer.
  - **ZH**: 谢谢你。知道大家能这样互相照应，山上的夜晚也让人安心多了。
  - **RU**: Спасибо. Когда знаешь, что все готовы прикрыть друг друга, ночи на горе становятся куда спокойнее.
- **`archivist.watch_wait`**
  - **EN**: I don't have a confirmed report to record just yet. Simply lend a hand if our neighbors run into danger—an uneventful night is a good thing in itself.
  - **ZH**: 目前还没有能够确认的协防记录。等邻居们真遇上危险时再帮忙就好；平平安安度过一晚本身也是好事。
  - **RU**: Пока нет подтвержденных сведений для записи. Просто помоги соседям, если им действительно грозит опасность; а спокойная ночь хороша сама по себе.

#### Narrative Fragments (English Only)

- **`changed:note`** @ `(31, 2, 13)` — Environmental lore note (no `clue:*` unlock)
  ```text
  Never forget...
  
  November 1st
  
  2026...
  ```

### 2.4 Desert Refuge (`desert_refuge`)

- **Biome / Placement**: Desert / Badlands (Overworld)
- **Resident Dialogue**: Built-in residents use station-specific root nodes by `DialogueRole` (`resident_dorm`, `resident_study`, `resident_garden`, `resident_lab`, `resident_corridor`) and share the `desert_resident` camp-life voice (with `playful` / `cautious` / `polite` personality variants).

#### Chief: Refuge Pathfinder (`changed_synergy:latex_jerboa`) (`desert_chief.json`, camp voice `pathfinder`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh—mind the step by the doorframe. I heard your boots on the upper stairs a minute ago, though I have to admit, I wasn't expecting a human to walk in out of the sun. Welcome to the refuge. Watch the central shaft railings out in the hall, and rest in the shade if the heat got to you.
  - **ZH**: 哦——小心门边的台阶。我刚才就听见你上楼的脚步声了，不过说实话，还真没想到会有个人类从大太阳底下走进来。欢迎来到避难所。留意外面走廊的竖井护栏；要是晒得难受，就先在阴凉处歇歇。
  - **RU**: Ох — осторожнее со ступенькой у двери. Я слышал твои шаги на верхней лестнице, но, признаться, не ожидал увидеть человека, пришедшего с солнцепёка. Добро пожаловать в убежище. Смотри под ноги у ограждений шахты и отдохни в тени, если жара тебя измотала.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: Hard-soled boots on stone tile! It sounded like someone rolling barrels down the stairwell. And how do they walk upright without a tail for counterbalance? I'd pitch straight onto my nose!
  - **ZH**: 硬鞋底敲在石砖上！听着就像有人把木桶一路滚下楼。他们没尾巴平衡，到底怎么站稳的？换成我早就脸着地了！
  - **RU**: Твёрдые подошвы по каменной плитке! Будто кто-то катит бочки по лестнице. И как они держат равновесие без хвоста? Я бы давно ткнулся носом в пол!
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hello there. My ears picked up your steps by the shaft railing before you rounded the corner. Come in out of the glare. The wind blows grit through the hatch up here, but it's much cooler down on the middle floor, and there are cushions waiting in the common room.
  - **ZH**: 你好。你还没绕过转角，我就听见竖井护栏旁的脚步声了。进来避避太阳吧。这里的舱口会漏进风沙，不过中层凉快得多，公共休息室里也有坐垫。
  - **RU**: Здравствуй. Я услышал твои шаги у ограждения шахты ещё до того, как ты свернул за угол. Заходи с солнцепёка. Через верхний люк задувает песок, зато на среднем этаже куда прохладнее, а в общей комнате ждут подушки.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: Light on their feet, polite posture. I hope they shook the sand off their paws before walking past the air intake.
  - **ZH**: 脚步轻，举止也客气。希望进来前抖干净爪子上的沙了，可别带到进风口旁边。
  - **RU**: Лёгкая поступь, вежливые манеры. Надеюсь, песок с лап стряхнут до того, как пройти мимо воздухозаборника.
- **Choices**:
  - `about` -> `about` (`condition=always`)
    - **EN**: Who are you? Are you the one in charge of this bunker? | **ZH**: 你是谁？你负责这个掩体吗？ | **RU**: Кто ты? Ты здесь за главного?
  - `home` -> `home` (`condition=always`)
    - **EN**: How do you and the others live down here under the sand? | **ZH**: 你们在沙子底下怎么生活？ | **RU**: Как вы живёте здесь, под песками?
  - `records` -> `records` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: I saw the sealed lab rooms and the old clipboards downstairs. What happened in this facility? | **ZH**: 我看见楼下的实验室和旧剪切板了。这里发生过什么？ | **RU**: Я видел лабораторные комнаты и старые записи внизу. Что здесь произошло?
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room in the refuge for another resident? | **ZH**: 避难所还能再住一个人吗？ | **RU**: В убежище есть место для ещё одного жильца?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: Let's talk about my place here… | **ZH**: 聊聊我住在这里的事…… | **RU**: Поговорим о моей жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Everyone here calls me Pathfinder, mostly because the coat came with a compass pouch and I spend half my morning checking the surface hatch and sweeping sand off the top stairs. I'm not the bunker's old director. This desk by the teal container is the best spot to hear when the wind picks up above the roof glass, or when someone comes knocking.
  - **ZH**: 大家都叫我领路人。大概是因为这身衣服带了个指南针包，而我每天早上又有一半时间在检查地表舱口、扫楼梯上的沙。我可不是这里原来的主管。青绿色集装箱旁这张桌子的位置正好，屋顶玻璃上方起风了，或者有人敲门，我都听得见。
  - **RU**: Здесь меня зовут Проводником. Наверное, из-за сумки для компаса на одежде и потому, что пол-утра я проверяю верхний люк и сметаю песок с лестницы. Я не прежний директор бункера. За этим столом у бирюзового контейнера лучше всего слышно, когда ветер крепчает над крышей или кто-нибудь стучится.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Also, the big ventilation fans downstairs hum in B-flat all afternoon. Up here I can hear a beetle land three floors down without my eardrums vibrating.
  - **ZH**: 再说，楼下那几台大通风扇整个下午都在哼降 B 调。在这里我连三层楼下甲虫落地都听得清，还不用震得耳膜发麻。
  - **RU**: А ещё большие вентиляторы внизу весь день гудят на си-бемоле. Здесь я услышу даже жука тремя этажами ниже, и барабанные перепонки не дрожат.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Six of us share the refuge right now: two Fennec Foxes, two Snakes, our Green Lizard neighbor, and me. Travelers crossing the desert expect a buried bunker to be dark and grim, but the middle level is plenty comfortable. On the west side, we run an irrigated indoor garden growing wheat, carrots, potatoes, beetroot, and a full-sized orange tree under the grow-lamps. Right across the shaft is our common room, with a wide orange rug, floor pillows, and canned soup.
  - **ZH**: 现在这里住着六个：两只耳廓狐、两条蛇、一位绿蜥蜴邻居，还有我。穿过沙漠的旅人总觉得地下掩体一定又黑又阴森，其实中层挺舒服。西边是有灌溉水渠的室内菜园，种着小麦、胡萝卜、马铃薯、甜菜，生长灯底下还有一整棵橙树。竖井对面就是公共休息室，有大块橙色地毯、坐垫和罐头汤。
  - **RU**: Сейчас нас шестеро: два фенека, две змеи, сосед-зелёная ящерица и я. Путники обычно ждут от подземного бункера мрака, но средний этаж у нас вполне уютный. На западе — орошаемый огород: пшеница, морковь, картофель, свёкла и целое апельсиновое дерево под лампами. Напротив, за шахтой, общая комната с оранжевым ковром, подушками и супом в банках.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Don't lean over the hazard railings with a full bowl of soup, though! It's a long drop to the sump water, and the fish don't want broth.
  - **ZH**: 不过端着一碗汤时可别探身越过护栏！掉到底下积水池可远着呢，鱼也不想喝肉汤。
  - **RU**: Только не перегибайся через ограждение с полной миской супа! До воды внизу далеко, и рыбе бульон ни к чему.
- **Choices**:
  - `quarters` -> `quarters` (`condition=always`)
    - **EN**: How do you organize the sleeping quarters and supplies downstairs? | **ZH**: 楼下的床位和物资怎么安排？ | **RU**: Как устроены спальни и запасы внизу?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `quarters` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: The bottom floor has bunks in the east dormitory and blue beds in the west study. The shared reserve is the double chest in the indoor garden on the middle floor. Its supplies are for the whole household, and you're welcome to read the books on the lecterns. The other chests and personal belongings stay with their owners. Asking first keeps the peace in an underground house.
  - **ZH**: 最底层东边宿舍有上下铺，西边书房有蓝床。公共储备是中层室内菜园里的长箱，物资大家共用，讲台上的书也随便读。其他箱子和私人物品归各自主人。先问一声，住在地下也能和和气气。
  - **RU**: На нижнем этаже есть койки в восточной спальне и синие кровати в западном кабинете. Общие запасы — в двойном сундуке огорода на среднем этаже. Ими пользуются все жильцы, книги на кафедрах тоже можно читать. Остальные сундуки и личные вещи остаются у владельцев. Сначала спроси, и в подземном доме будет мир.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Especially down in the west study. Nothing makes the morning awkward faster than someone rummaging through a neighbor's bedside drawer for spare socks!
  - **ZH**: 尤其是西边书房。大清早有人为了找双备用袜子就翻邻居的床头抽屉，场面可太尴尬了！
  - **RU**: Особенно в западном кабинете. Ничто так не портит утро, как гость, роющийся в соседском ящике в поисках запасных носков!
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `records` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: The old discs and clipboards were here before we moved in. We don't know who left them. We keep the records where we found them and leave the sealed equipment alone.
  - **ZH**: 旧光盘和剪切板在我们搬来之前就在了。我们不知道是谁留下的，就放在原处保存着。密封的设备也没去动。
  - **RU**: Старые диски и записи были здесь до нас. Кто их оставил, мы не знаем. Оставили всё на своих местах, а запечатанное оборудование не трогаем.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Every time I walk across that glass floor on the middle level and look down into the old lab bay, the fur on my neck stands straight up! Give me the orange tree any day.
  - **ZH**: 每次走过中层的玻璃地板，看见下面的旧实验区，我后颈的毛都会竖起来！还是橙树那边好。
  - **RU**: Каждый раз, когда иду по стеклянному полу и вижу старый лабораторный отсек внизу, шерсть на загривке встаёт дыбом! Уж лучше апельсиновое дерево.
- **Choices**:
  - `clipboards` -> `clipboards` (`condition=always`)
    - **EN**: Did anyone here write the old notes? | **ZH**: 那些旧便笺是这里的人写的吗？ | **RU**: Кто-нибудь из вас написал старые записки?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `clipboards` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Nobody here knows who wrote those notes. They were already on the desks when we arrived, so we left them there.
  - **ZH**: 这里没人知道那些便笺是谁写的。我们搬来时，它们就放在桌上，所以也没挪动。
  - **RU**: Никто здесь не знает, кто написал эти записки. Когда мы пришли, они уже лежали на столах. Там и оставили.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: I only speak for the six of us living under this roof, mind you, not for every creature out in the dunes. Still, if you're willing to lend a hand with the garden, respect everyone's bedside chests, and watch your footing around the shaft, there are plenty of bunks downstairs. We'd be glad to have you as a neighbor.
  - **ZH**: 先说清楚，我只替这屋顶底下的六位住户说话，可代表不了沙丘里的每一只胶兽。不过，只要愿意搭把手照料菜园、尊重大家的床边箱子，再留意竖井边的脚下，楼下有的是床位。我们很乐意多个邻居。
  - **RU**: Сразу скажу: я говорю лишь за шестерых жильцов под этой крышей, а не за всех существ в дюнах. Но если готов помогать с огородом, уважать чужие вещи и смотреть под ноги у шахты, внизу хватает коек. Будем рады новому соседу.
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: You're welcome to live with us as you are. Joining our household never requires giving up your human body. Having someone around with the reach to stack crates and prune the upper branches of the orange tree is a blessing. Of course, if you're curious what it's like to get around as a Latex Jerboa, whether as a temporary, reversible experience or as a permanent change, I can share the form with you. It's your choice.
  - **ZH**: 你现在这样也很欢迎。住进来从不需要放弃人类身体。有人能帮忙往高处码箱子、修剪橙树上面的枝条，实在是好事。当然，要是想体验胶液跳鼠的身体，不管是可以解除的暂时体验，还是永久改变，我都能把这个形态分享给你。由你自己选。
  - **RU**: Тебе рады и таким. Для жизни здесь не нужно отказываться от человеческого тела. Кто-то должен ставить ящики повыше и подрезать верхние ветви дерева — твоя помощь пригодится. А если интересно попробовать тело латексного тушканчика, обратимо или навсегда, я могу поделиться формой. Решать тебе.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Everyone downstairs is still a little unsettled right now, so I can't offer you a bunk yet. Give our neighbors some space for the time being. Once folks feel at ease around you again, we can talk about you moving in.
  - **ZH**: 楼下的大家现在还有些不安，我暂时不能答应给你留床位。先让邻居们缓一缓吧。等他们见到你能安心下来，我们再聊搬进来的事。
  - **RU**: Внизу все пока немного встревожены, так что койку я сейчас предложить не могу. Дай соседям немного пространства. Когда они снова будут чувствовать себя спокойно рядом с тобой, обсудим переезд.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: I'd be glad to share the chores and live here. | **ZH**: 我愿意分担家务，住在这里。 | **RU**: Я рад разделить заботы и жить здесь.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. I'd be happy to help with the garden and the refuge. | **ZH**: 我保持人类加入，愿意照料菜园和避难所。 | **RU**: Я вступлю человеком. Буду рад помогать с огородом и убежищем.
  - `form` -> `explain_form` (`condition=human`)
    - **EN**: What would changing into your form mean for me? | **ZH**: 变成你的形态对我意味着什么？ | **RU**: Что для меня будет означать твоя форма?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: First, sharing my latex doesn't harm or tire me, and it won't erase your memories or who you are. A Jerboa frame does come with a few practical trade-offs, though. You'll be lighter and more delicate than a wolf or a hyena, so you can't shrug off heavy blows the way they do. In exchange, you're much quicker on your feet, your ears catch every sound in the corridors, and holding the jump key lets your hind legs spring into a high jump right up onto ledges or stairwells. If we keep it reversible, you borrow the shape and return to your human body whenever you choose. If we make it permanent, this Jerboa body is yours for good. And if you'd rather stay human, you're just as welcome at our table.
  - **ZH**: 先说明，分享胶液不会伤害我，也不会让我疲惫，更不会抹去你的记忆或自我。只是跳鼠的身体也有些取舍：比狼或鬣狗更轻、更脆弱，挨重击可不能硬撑。换来的是更快的脚步，耳朵能听清走廊里的声响；按住跳跃键蓄力，后腿就能把你送上高处的台沿或楼梯。如果选可逆，就像借用这个形态，想恢复人类身体时可以解除。如果选永久，这副跳鼠身体就真正属于你了。当然，保持人类，我们的桌边也一样给你留位置。
  - **RU**: Сначала о главном: делиться латексом мне не больно и не утомительно, а твои память и личность останутся при тебе. Но у тела тушканчика есть свои особенности. Оно легче и хрупче волчьего или гиеньего — тяжёлые удары лучше не принимать. Зато ты быстрее, слышишь всё в коридорах и можешь удержанием клавиши прыжка накопить силу для высокого скачка на уступ или лестницу. Обратимый вариант — словно одолжить форму: захочешь, вернёшь человеческое тело. Постоянный вариант делает это тело твоим. И если останешься человеком, место за нашим столом всё равно найдётся.
- **Choices**:
  - `reversible` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the reversible transformation and join. | **ZH**: 我想体验可逆兽化，并加入这里。 | **RU**: Хочу попробовать обратимую трансформацию и вступить.
  - `permanent_form` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I'm ready to make it permanent and join. | **ZH**: 我准备好永久兽化并加入了。 | **RU**: Я готов к постоянной трансформации и вступлению.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. I'd be happy to help with the garden and the refuge. | **ZH**: 我保持人类加入，愿意照料菜园和避难所。 | **RU**: Я вступлю человеком. Буду рад помогать с огородом и убежищем.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Let's be sure before we begin. Making this permanent means this Latex Jerboa body is yours for good. Leaving the refuge won't turn you back, and ordinary reversal methods won't restore your human body unless you later select a human form through your Native Origin. You can still choose the reversible experience instead, or move in as you are. Are you certain you want this change to be permanent?
  - **ZH**: 开始前再确认一下。选择永久，就意味着这副胶液跳鼠身体真正属于你。离开避难所不会让你恢复人类，普通逆转方法也无效；以后想换回人类身体，需要在原生身份界面重新选择。你现在仍可以改选可逆体验，或者保持原样住进来。确定要让这个改变永久生效吗？
  - **RU**: Давай убедимся перед началом. Постоянная трансформация означает, что тело латексного тушканчика станет твоим. Уход из убежища ничего не отменит, обычные способы обращения не вернут человеческое тело — для этого позже понадобится выбрать его через Native Origin. Ты всё ещё можешь выбрать обратимый опыт или поселиться здесь как есть. Ты уверен, что хочешь постоянную перемену?
- **Choices**:
  - `permanent_confirm` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I understand and choose the permanent change. | **ZH**: 是的，我理解并选择永久改变。 | **RU**: Да, я понимаю и выбираю постоянную перемену.
  - `reconsider` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over again. | **ZH**: 让我再想想。 | **RU**: Дай мне ещё подумать.

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Then welcome home, neighbor! Grab any open bed in the lower east dorm or the west study, and help yourself to the oranges and soup in the common room whenever you're hungry.
  - **ZH**: 那就欢迎回家，邻居！下层东边宿舍或西边书房，哪张床空着就用哪张。饿了随时去公共休息室拿橙子和汤。
  - **RU**: Тогда добро пожаловать домой, сосед! Выбирай свободную койку в восточной спальне или западном кабинете. А проголодаешься — в общей комнате есть апельсины и суп.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Plus, without ears sticking up a foot above your head, you won't brush them against the ceiling ducts every time you pass a vent! I'm a little envious.
  - **ZH**: 而且你头顶没支着两只那么长的耳朵，经过风管也不用次次蹭天花板！有点羡慕。
  - **RU**: И у тебя нет над головой двух длинных ушей, которыми цепляешь каждый воздуховод! Немного завидую.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Then welcome to the refuge, neighbor! The downstairs bunks are open, the garden is thriving on the middle floor, and there's always a spare pillow in the common room.
  - **ZH**: 那就欢迎来到避难所，邻居！楼下有空床，中层的菜园长得正好，公共休息室也总有一只多余的坐垫。
  - **RU**: Тогда добро пожаловать в убежище, сосед! Внизу свободные койки, на среднем этаже растёт огород, а в общей комнате всегда найдётся лишняя подушка.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There we are… shift your stance onto your hind paws and let your tail balance your weight. Can you hear the water lapping all the way at the bottom of the shaft? Don't worry—this shift didn't scramble a thing upstairs. Just remember to watch the ceiling lights before you test out a full jump indoors! Welcome to the refuge, neighbor.
  - **ZH**: 好了……把重心移到后爪，让尾巴帮你站稳。能听见竖井最底下水面轻轻拍动的声音吗？别担心，这次变化半点没搅乱你的思绪。只是在屋里试高跳前，一定先看看头顶的灯！欢迎来到避难所，邻居。
  - **RU**: Вот так… перенеси вес на задние лапы, пусть хвост помогает держать равновесие. Слышишь, как плещется вода на самом дне шахты? Не переживай — смена облика ничуть не спутала твои мысли. Только прежде чем прыгать в помещении, посмотри на потолочные светильники! Добро пожаловать в убежище, сосед.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Look at those ears! Look at that tail tuft! Oh, please don't test the high jump inside the office, or we'll send the computer monitors flying!
  - **ZH**: 瞧这耳朵！瞧这尾巴尖！千万别在办公室试高跳，不然电脑显示器全要飞起来！
  - **RU**: Какие уши! Какая кисточка на хвосте! Только не пробуй высокий прыжок в кабинете, иначе мониторы полетят во все стороны!
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There we are… shift your stance onto your hind paws and let your tail balance your weight. Can you hear the water lapping all the way at the bottom of the shaft? Are you still feeling wholly yourself? I'd hate for a change this quick to leave your head spinning. Just remember to watch the ceiling lights before you test out a full jump indoors! Welcome to the refuge, neighbor.
  - **ZH**: 好了……把重心移到后爪，让尾巴帮你站稳。能听见竖井最底下水面轻轻拍动的声音吗？心里还感觉是原本的自己吗？我可不希望这么快的变化伤着你的神智。只是在屋里试高跳前，一定先看看头顶的灯！欢迎来到避难所，邻居。
  - **RU**: Вот так… перенеси вес на задние лапы, пусть хвост помогает держать равновесие. Слышишь, как плещется вода на самом дне шахты? Ты всё ещё чувствуешь себя собой? Не хотелось бы, чтобы столь быстрая перемена закружила тебе голову. Только прежде чем прыгать в помещении, посмотри на потолочные светильники! Добро пожаловать в убежище, сосед.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Look at those ears! Look at that tail tuft! Oh, please don't test the high jump inside the office, or we'll send the computer monitors flying!
  - **ZH**: 瞧这耳朵！瞧这尾巴尖！千万别在办公室试高跳，不然电脑显示器全要飞起来！
  - **RU**: Какие уши! Какая кисточка на хвосте! Только не пробуй высокий прыжок в кабинете, иначе мониторы полетят во все стороны!
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're part of our household here. The shared reserve chest in the middle-floor garden and any unoccupied beds are open to you, while private storage stays with its owner. If you ever need to move on, you're free to leave whenever you wish. We'll remember the time we spent together, and your body remains your own.
  - **ZH**: 你已经是这里的一员了。中层菜园的公共储备箱和空床都向你开放，私人储物仍归各自主人。哪天想继续上路，随时可以离开。我们会记得一起生活的日子，而你的身体始终由你自己做主。
  - **RU**: Ты теперь часть нашего дома. Общий сундук в огороде на среднем этаже и свободные кровати открыты для тебя, личные вещи остаются у владельцев. Если решишь идти дальше, можешь уйти когда пожелаешь. Мы сохраним память о совместной жизни, а тело всегда останется твоим делом.
- **Choices**:
  - `leave` -> `confirm_leave` (`condition=always`)
    - **EN**: I need to leave the refuge household and move on. | **ZH**: 我得离开这里，继续上路了。 | **RU**: Мне нужно покинуть этот дом и идти дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: All right. Before I take your name off our shared reserve and the downstairs bunks, are you certain? You're always welcome to drop in out of the sun whenever you pass this way, though if you were only borrowing a coat here for a while, leaving returns you to your old self.
  - **ZH**: 好。在我把你的名字从公共储备和楼下床位的登记里划掉之前，再确认一次？以后路过这里，随时欢迎进来避太阳；不过要是你这身皮毛只是在这边借穿一阵子的，搬走之后就会变回你原本的样子。
  - **RU**: Хорошо. Прежде чем убрать твоё имя из списка общих запасов и коек, ты уверен? Если будешь проходить мимо, всегда заходи укрыться от солнца, но если ты лишь одалживал здесь этот облик на время, с уходом вернёшься к своему старому виду.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=always`, `effect=leave`)
    - **EN**: Yes, I'm ready to move on. | **ZH**: 是的，我准备继续上路。 | **RU**: Да, я готов идти дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I've updated the register. Watch your step out on the dunes, neighbor. We're glad for the time you spent with us down here.
  - **ZH**: 登记改好了。沙丘上走路小心，邻居。能和你一起在这里住过，我们很高兴。
  - **RU**: Я поправил запись. Береги себя в дюнах, сосед. Мы рады, что какое-то время жили с тобой под одной крышей.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Mind the yellow-and-black railings around the shaft on your way down, and grab a couple of oranges from the middle floor if you're heading back out into the heat.
  - **ZH**: 下楼时留意竖井边黄黑相间的护栏。要再出去赶路，就从中层带几个橙子吧。
  - **RU**: На спуске смотри под ноги у жёлто-чёрных ограждений шахты. Если снова идёшь на жару, захвати пару апельсинов со среднего этажа.
- **Choices**:
  - `close` -> `goodbye` (`condition=always`, `effect=close`)
    - **EN**: Safe travels. | **ZH**: 一路平安。 | **RU**: Счастливого пути.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh—mind the step by the doorframe. I heard your boots on the upper stairs a minute ago, though I have to admit, I wasn't expecting a human to walk in out of the sun. Welcome to the refuge. Watch the central shaft railings out in the hall, and rest in the shade if the heat got to you.
  - **ZH**: 哦——小心门边的台阶。我刚才就听见你上楼的脚步声了，不过说实话，还真没想到会有个人类从大太阳底下走进来。欢迎来到避难所。留意外面走廊的竖井护栏；要是晒得难受，就先在阴凉处歇歇。
  - **RU**: Ох — осторожнее со ступенькой у двери. Я слышал твои шаги на верхней лестнице, но, признаться, не ожидал увидеть человека, пришедшего с солнцепёка. Добро пожаловать в убежище. Смотри под ноги у ограждений шахты и отдохни в тени, если жара тебя измотала.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: Hard-soled boots on stone tile! It sounded like someone rolling barrels down the stairwell. And how do they walk upright without a tail for counterbalance? I'd pitch straight onto my nose!
  - **ZH**: 硬鞋底敲在石砖上！听着就像有人把木桶一路滚下楼。他们没尾巴平衡，到底怎么站稳的？换成我早就脸着地了！
  - **RU**: Твёрдые подошвы по каменной плитке! Будто кто-то катит бочки по лестнице. И как они держат равновесие без хвоста? Я бы давно ткнулся носом в пол!
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hello there. My ears picked up your steps by the shaft railing before you rounded the corner. Come in out of the glare. The wind blows grit through the hatch up here, but it's much cooler down on the middle floor, and there are cushions waiting in the common room.
  - **ZH**: 你好。你还没绕过转角，我就听见竖井护栏旁的脚步声了。进来避避太阳吧。这里的舱口会漏进风沙，不过中层凉快得多，公共休息室里也有坐垫。
  - **RU**: Здравствуй. Я услышал твои шаги у ограждения шахты ещё до того, как ты свернул за угол. Заходи с солнцепёка. Через верхний люк задувает песок, зато на среднем этаже куда прохладнее, а в общей комнате ждут подушки.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: Light on their feet, polite posture. I hope they shook the sand off their paws before walking past the air intake.
  - **ZH**: 脚步轻，举止也客气。希望进来前抖干净爪子上的沙了，可别带到进风口旁边。
  - **RU**: Лёгкая поступь, вежливые манеры. Надеюсь, песок с лап стряхнут до того, как пройти мимо воздухозаборника.
- **Choices**:
  - `about` -> `about` (`condition=always`)
    - **EN**: Who are you? Are you the one in charge of this bunker? | **ZH**: 你是谁？你负责这个掩体吗？ | **RU**: Кто ты? Ты здесь за главного?
  - `home` -> `home` (`condition=always`)
    - **EN**: How do you and the others live down here under the sand? | **ZH**: 你们在沙子底下怎么生活？ | **RU**: Как вы живёте здесь, под песками?
  - `records` -> `records` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: I saw the sealed lab rooms and the old clipboards downstairs. What happened in this facility? | **ZH**: 我看见楼下的实验室和旧剪切板了。这里发生过什么？ | **RU**: Я видел лабораторные комнаты и старые записи внизу. Что здесь произошло?
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room in the refuge for another resident? | **ZH**: 避难所还能再住一个人吗？ | **RU**: В убежище есть место для ещё одного жильца?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: Let's talk about my place here… | **ZH**: 聊聊我住在这里的事…… | **RU**: Поговорим о моей жизни здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your desk. | **ZH**: 不打扰你忙了。 | **RU**: Не буду отвлекать тебя от дел.

##### `resident_dorm_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: As long as you're on good terms with the household, any empty mattress is yours to use. We fold the spruce shutters up along the sides so folks can block out the ceiling lamps without blocking the airflow from the fan.
  - **ZH**: 只要和大家相处得来，空床都可以睡。云杉隔板往两侧立起来，能挡住顶灯，也不会妨碍风扇通风。
  - **RU**: Если ты в добрых отношениях с жильцами, любая свободная койка к твоим услугам. Еловые перегородки поднимаем по бокам: свет ламп не мешает, а воздух от вентилятора проходит.
- **Choices**:
  - `back` -> `resident_dorm` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_dorm` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_dorm_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're welcome to read the books on the shelves, but the chests by the beds belong to the residents. Shared supplies go in the double chest in the indoor garden on the middle floor.
  - **ZH**: 架子上的书随便看，床边的箱子则是住户自己的。要存取公共物资，就去中层室内菜园的公共储备长箱。
  - **RU**: Книги на полках читать можно, а прикроватные сундуки принадлежат жильцам. Общие припасы клади и бери в двойном сундуке огорода на среднем этаже.
- **Choices**:
  - `back` -> `resident_dorm` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_dorm` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_dorm` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Shh, keep your voice down a notch if anyone is napping in the top bunks. With ears as big as ours, one squeaky hinge on these spruce bed-shutters sounds like a vault door slamming! Pick any open bunk down this row when you need a rest.
  - **ZH**: 嘘，要是有人在上铺睡觉，就小点声。耳朵长成我们这样，云杉床隔板的铰链吱一声，听着都像保险库门砸上了！什么时候想歇脚，这排空床随便挑一张就行。
  - **RU**: Тсс, потише, если на верхних койках кто-то спит. С такими ушами, как у нас, скрип петли на еловой ширме звучит как удар банковской двери! Если устанешь, занимай любую свободную койку в этом ряду.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: The breeze from the wall fan is wonderful, but if that left shutter squeaks one more time tonight, I'm wedging a folded sock into the hinge!
  - **ZH**: 墙上风扇吹得真舒服，可今晚左边那块隔板要再响一声，我就拿叠好的袜子塞住铰链！
  - **RU**: От настенного вентилятора чудесный ветерок, но если левая перегородка сегодня ещё раз скрипнет, я засуну в петлю сложенный носок!
- **Choices**:
  - `topic_0` -> `resident_dorm_0` (`condition=always`)
    - **EN**: What about the beds? | **ZH**: 这些床怎么用？ | **RU**: Что насчёт кроватей?
  - `topic_1` -> `resident_dorm_1` (`condition=always`)
    - **EN**: What about the room chest? | **ZH**: 房间里的箱子呢？ | **RU**: А сундук в комнате?
  - `close` -> `resident_dorm` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_study_2` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: The double chest in the middle-floor garden is shared: crops, spare leather, paper, and whatever the neighbors bring to share. The chests by the blue beds are private. Leave those alone and we'll get along fine.
  - **ZH**: 中层菜园的长箱是公用的，收成、备用皮革、纸，还有大家带来分享的东西都放那里。蓝床旁边的箱子是私人的，别乱动，咱们就能好好相处。
  - **RU**: Двойной сундук в огороде на среднем этаже — общий: урожай, запасная кожа, бумага и всё, чем соседи делятся. Сундуки у синих кроватей личные. Не трогай их, и мы поладим.
- **Choices**:
  - `back` -> `resident_study` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_study` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_study_3` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I don't know who wrote it. It was already there before we put the rug down. Whoever wrote it seems to be really worried about it. Perhaps it is some kind of password...?
  - **ZH**: 我不知道是谁写的。 我们铺地毯之前，它就已经在那儿了。写字板的人看起来对这串数字格外挂心……说不定是什么门禁密码吧？
  - **RU**: Кто написал — не знаю. Записка лежала там ещё до того, как мы расстелили ковёр. Похоже, тот, кто её оставил, очень боялся это забыть. Может быть, это какой-то пароль?..
- **Choices**:
  - `back` -> `resident_study` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_study` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_study` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hello there. Mind my tail by the edge of the rug. The stone tiles down on the bottom level get cold, so I like coiling up near the warm exhaust from the computer case. Help yourself to the shelves or the reserve chest nearby.
  - **ZH**: 你好。地毯边有我的尾巴，留神脚下。最底层的石砖冷，我就喜欢盘在电脑机箱的热风旁边。想找书翻翻或者往储备箱顺手放东西都可以。
  - **RU**: Здравствуй. Смотри не наступи на хвост у края ковра. Плитка внизу холодная, так что я люблю сворачиваться у тёплого выдува системного блока. Можешь полистать книги или заглянуть в сундук рядом.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Warm computer fan, quiet study, soft blue rug. Beats baking on a flat rock out in the noon sun any day of the week!
  - **ZH**: 暖暖的电脑风扇，安静的书房，软软的蓝地毯。哪天都比中午趴在外面的石头上晒强！
  - **RU**: Тёплый компьютер, тихий кабинет, мягкий синий ковёр. В любой день лучше, чем жариться на камне под полуденным солнцем!
- **Choices**:
  - `topic_2` -> `resident_study_2` (`condition=always`)
    - **EN**: Is that double chest shared? | **ZH**: 那个长箱是公用的吗？ | **RU**: Этот двойной сундук общий?
  - `topic_3` -> `resident_study_3` (`condition=clue:desert_568712`)
    - **EN**: Who wrote that clipboard on the floor? | **ZH**: 地上那块写字板是谁留的？ | **RU**: Кто оставил этот планшет на полу?
  - `close` -> `resident_study` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_garden_4` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: The water channels under the soil never dry up, and the grow-lamps keep a steady cycle going. Between the wheat, carrots, potatoes, beetroot, and the oranges overhead, nobody down here goes hungry.
  - **ZH**: 土下面的水渠一直有水，生长灯也有固定的照明周期。小麦、胡萝卜、马铃薯、甜菜，再加上头顶的橙子，这里没人饿肚子。
  - **RU**: Под грядками вода не пересыхает, а лампы работают по постоянному циклу. Пшеница, морковь, картофель, свёкла и апельсины над головой — голодать здесь некому.
- **Choices**:
  - `back` -> `resident_garden` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_garden` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_garden_5` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Half of those dropped off the branches on their own, and the rest I set aside for anyone keeping late hours. If you live here, take one on your way to the common room!
  - **ZH**: 一半是自己从枝头掉下来的，剩下的我留给熬夜的人。住在这里的话，去公共休息室的路上就拿一个吧！
  - **RU**: Половина сама упала с ветвей, остальные я отложил для тех, кто засиживается допоздна. Если живёшь здесь, возьми один по дороге в общую комнату!
- **Choices**:
  - `back` -> `resident_garden` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_garden` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_garden` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Watch your step by the water trenches! It's a nice change of pace in here, isn't it? Outside it's dry dunes for miles, and in here you smell orange blossoms and damp soil the moment you open the door. If you harvest any ripe crops while you're helping out, remember to replant the seeds right away so the beds stay full.
  - **ZH**: 小心水渠边上！这里挺不一样吧？外头一眼望去都是干沙丘，进门却能闻见橙花和湿土的味道。帮忙收成熟作物的话，记得马上补种，让地里一直有东西长。
  - **RU**: Осторожнее у канавок! Здесь совсем другая жизнь, правда? Снаружи сплошные сухие дюны, а за дверью пахнет цветами апельсина и влажной землёй. Если собираешь зрелые овощи, сразу сажай новые, чтобы грядки не пустовали.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Best room in the bunker! Warm grow-lamps, damp air, and every few hours a ripe orange drops straight onto my desk.
  - **ZH**: 全掩体最好的房间！生长灯暖，空气湿，隔一会儿还有熟橙子直接掉到我桌上。
  - **RU**: Лучшая комната в бункере! Тёплые лампы, влажный воздух, а иногда спелый апельсин падает прямо на стол.
- **Choices**:
  - `topic_4` -> `resident_garden_4` (`condition=always`)
    - **EN**: How do the underground crops grow? | **ZH**: 地下的作物怎么长起来的？ | **RU**: Как растут подземные грядки?
  - `topic_5` -> `resident_garden_5` (`condition=always`)
    - **EN**: What about the oranges on your desk? | **ZH**: 桌上的橙子呢？ | **RU**: А апельсины на твоём столе?
  - `close` -> `resident_garden` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_lab_6` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: They're left over from before we lived here. We don't use the old equipment; we just keep these rooms aired and dry.
  - **ZH**: 都是我们搬来之前留下的。旧设备我们不用，只是让这些房间保持通风、干燥。
  - **RU**: Всё осталось с тех времён, когда нас здесь ещё не было. Старым оборудованием мы не пользуемся, только проветриваем комнаты и следим, чтобы было сухо.
- **Choices**:
  - `back` -> `resident_lab` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_lab` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_lab` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh! You startled me. Standing out on this glass floor makes my stomach flip a little. We use these northern rooms to store extra crates and check for air leaks. Our living quarters are back through the south doors.
  - **ZH**: 哦！吓我一跳。站在这玻璃地板上，肚子里总有点发虚。我们用北边这些房间存备用箱子、检查漏风。住的地方在南边门后。
  - **RU**: Ой! Ты меня напугал. На этом стеклянном полу у меня всё внутри переворачивается. Северные комнаты мы используем для запасных ящиков и проверки утечек воздуха. Жилые комнаты — за южными дверями.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Why would anyone build a transparent floor right over the creepy stasis room?! Don't look down. Do not look down!
  - **ZH**: 谁会把透明地板修在那么吓人的休眠舱正上方啊？！别往下看。千万别往下看！
  - **RU**: Кому пришло в голову делать прозрачный пол прямо над жуткими капсулами?! Не смотри вниз. Только не вниз!
- **Choices**:
  - `topic_6` -> `resident_lab_6` (`condition=always`)
    - **EN**: What were the flasks and stasis pods for? | **ZH**: 烧瓶和休眠舱原来是做什么的？ | **RU**: Для чего были колбы и капсулы?
  - `close` -> `resident_lab` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_corridor_7` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh, I'm not on guard duty. Nobody barks orders around here. I like the warmer air up near the roof glass, and standing here keeps visitors from wandering into the northern storage rooms by mistake.
  - **ZH**: 哦，我不是在站岗。这里没人吆喝着指挥大家。我就是喜欢屋顶玻璃附近暖一点的空气，在这里还能提醒客人，别误闯北边的储物间。
  - **RU**: О, я не на посту. Никто здесь не командует. Мне просто нравится тёплый воздух у стеклянной крыши, а заодно могу подсказать гостям, чтобы не забрели в северные кладовые.
- **Choices**:
  - `back` -> `resident_corridor` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `resident_corridor` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_corridor` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Easy around the yellow-and-black stripes behind you. The shaft drops all the way to the bottom water basin, and blown-in sand makes the stone tiles slick near the stairs. If you're looking for Pathfinder, his office is right through the east door beside the teal container.
  - **ZH**: 身后黄黑条纹那边慢点走。竖井一直通到底下的积水池，吹进来的沙还会让楼梯边的砖变滑。找领路人的话，东边那扇门后就是他的办公室，挨着青绿色集装箱。
  - **RU**: Осторожнее у жёлто-чёрных полос позади. Шахта идёт до самого водосборника, а песок делает плитку у лестницы скользкой. Проводник — за восточной дверью, у бирюзового контейнера.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: Every time someone opens the top hatch, another bucketful of sand blows down the steps! I need to talk Pathfinder into hanging a heavy curtain up there.
  - **ZH**: 每次有人打开上面的舱口，都能吹一桶沙下楼！得劝领路人在那儿挂条厚门帘。
  - **RU**: Стоит кому-то открыть верхний люк — и на лестнице ещё ведро песка! Надо уговорить Проводника повесить там плотную занавесь.
- **Choices**:
  - `topic_7` -> `resident_corridor_7` (`condition=always`)
    - **EN**: Why stand by the stairs? | **ZH**: 为什么待在楼梯旁边？ | **RU**: Почему ты стоишь у лестницы?
  - `close` -> `resident_corridor` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

#### Chief Camp-Life Voice (`pathfinder.*`)

- **`pathfinder.changed`**
  - **EN**: We can't complete that right now. Check that you're holding the right item and that the reserve chest still has room. If someone already handled it, there's no need to bring a second batch.
  - **ZH**: 现在还办不了。看看手里的东西是否对、储备箱是否有空位。要是已经有人办妥，就不用再送一份了。
  - **RU**: Сейчас не получается. Проверь предмет в руке и место в общем сундуке. Если дело уже сделано, вторую партию приносить не нужно.
- **`pathfinder.choose_item`**
  - **EN**: Hold whatever supplies you want to share in your hand and talk to me again. Anything you need for the road, keep for yourself.
  - **ZH**: 把想分享的东西拿在手里，再来和我说。路上自己需要的就留着吧。
  - **RU**: Возьми в руку припасы, которыми хочешь поделиться, и поговори со мной снова. То, что нужно тебе в дороге, оставь себе.
- **`pathfinder.danger`**
  - **EN**: My ears caught a commotion downstairs. One of our neighbors is dealing with trouble right now. Let's make sure everyone is safe before we settle into the common room.
  - **ZH**: 我听见楼下有骚动，有邻居遇上麻烦了。先确认大家安全，再去公共休息室歇着。
  - **RU**: Я услышал шум внизу. Кто-то из соседей попал в беду. Прежде чем отдыхать в общей комнате, убедимся, что все в безопасности.
- **`pathfinder.declined`**
  - **EN**: That's all right. Take care of your own trip first. We can ask another neighbor.
  - **ZH**: 没关系，先顾好自己的行程。我们再问问别的邻居。
  - **RU**: Ничего страшного. Сначала позаботься о своей дороге. Спросим другого соседа.
- **`pathfinder.donate`**
  - **EN**: You brought %s to share? We can use that downstairs. How much do you want to leave in the reserve?
  - **ZH**: 你带了%s来分享？楼下用得上。想往储备里放多少？
  - **RU**: Ты принёс %s для всех? Внизу пригодится. Сколько хочешь оставить в запасах?
- **`pathfinder.food_enough`**
  - **EN**: Between the garden plots and the reserve chest, we have plenty of food to go around right now.
  - **ZH**: 有菜园的收成和储备箱里的东西，现在大家都吃得饱。
  - **RU**: Благодаря огороду и общему сундуку еды сейчас всем хватает.
- **`pathfinder.food_low`**
  - **EN**: The reserve chest in the middle-floor garden is running low on food. The garden keeps us going, of course, but any rations you can spare would help us out.
  - **ZH**: 中层菜园的储备箱快没吃的了。菜园还能供着，要是有多余的口粮，带些来就帮大忙了。
  - **RU**: В общем сундуке огорода заканчивается еда. Огород нас кормит, но лишние припасы очень пригодились бы.
- **`pathfinder.food_plenty`**
  - **EN**: Our food stores are well stocked with fresh oranges, vegetables, and canned soup. If you're free later, come grab a cushion in the common room and eat with us.
  - **ZH**: 新鲜橙子、蔬菜和罐头汤都备得很足。晚点有空，就来公共休息室拿个坐垫，一起吃点吧。
  - **RU**: Свежих апельсинов, овощей и супа у нас вдоволь. Будет время — приходи в общую комнату, бери подушку и поешь с нами.
- **`pathfinder.greeting`**
  - **EN**: Good to see you. Feel free to ask how the refuge is stocked or what chores still need doing.
  - **ZH**: 见到你真好。想知道避难所的物资近况或者谁手头有缺的，随时开口就行。
  - **RU**: Рад тебя видеть. Спрашивай, как обстоят дела с припасами в убежище или чем можно помочь.
- **`pathfinder.handled`**
  - **EN**: Someone else already took care of that one. Ears and feet move fast around here! Feel free to check if anything else is open.
  - **ZH**: 那件已经有人办妥了。这里大家耳朵灵，腿脚也快！看看还有没有别的吧。
  - **RU**: С этим уже помогли. У нас и уши, и лапы работают быстро! Посмотри, осталось ли что-нибудь ещё.
- **`pathfinder.meal`**
  - **EN**: I'll go let the neighbors know! Head down to the middle common room and grab a pillow on the orange rug. Whoever's free will come join us.
  - **ZH**: 我去叫大家！到中层公共休息室，橙色地毯上拿个坐垫吧。有空的邻居都会来。
  - **RU**: Я позову соседей! Иди в общую комнату на среднем этаже и бери подушку на оранжевом ковре. Кто свободен — присоединится.
- **`pathfinder.no_requests`**
  - **EN**: Everything down here is running smoothly at the moment. Go put your feet up on the common room cushions for a while.
  - **ZH**: 现在这里一切顺当。去公共休息室的坐垫上歇会儿腿吧。
  - **RU**: Сейчас всё идёт своим чередом. Отдохни на подушках в общей комнате.
- **`pathfinder.outfit`**
  - **EN**: You're part of the household down here now. Take this Wayfarer Outfit. May the linen keep the sun off your shoulders and the water gourd serve you well on the dunes.
  - **ZH**: 你已经是这里的一员了。这套沙行装送给你，愿亚麻布替你挡住烈日，水葫芦陪你走过沙丘。
  - **RU**: Ты теперь часть нашего дома. Возьми наряд пустынного странника. Пусть лён укрывает плечи от солнца, а фляга выручает в дюнах.
- **`pathfinder.remember`**
  - **EN**: I haven't forgotten the help you've given us down here. You don't need to bring supplies every time you visit to be welcome.
  - **ZH**: 你帮过我们的事，我没忘。来这里做客不必次次都带物资。
  - **RU**: Я помню твою помощь. Тебе не нужно приносить припасы при каждом визите, чтобы тебе здесь были рады.
- **`pathfinder.request`**
  - **EN**: We could use %s %s for the household. Bring the full amount whenever it's convenient, and we'll store them in the middle-floor garden reserve chest.
  - **ZH**: 家里需要 %s 份%s。方便时把东西带齐，我们会收进中层菜园的储备箱。
  - **RU**: Для дома нужно %s порций: %s. Принеси всё, когда будет удобно, и мы уберём это в общий сундук огорода.
- **`pathfinder.requests`**
  - **EN**: A few neighbors mentioned errands that need a hand. Take a look and pick whichever one fits your plans. The rest can wait.
  - **ZH**: 有几位邻居提了些需要人帮忙的事。看看有没有顺路能做的，其他的不急。
  - **RU**: Несколько соседей просили помочь с делами. Выбери, что подходит к твоим планам. Остальное подождёт.
- **`pathfinder.reverse_rite`**
  - **EN**: A promise is a promise. Being a jerboa is fun, but your human hands are needed just as much. Hold still for a moment.
  - **ZH**: 答应过的事当然算数。当跳鼠虽好玩，但你的双手也同样不可或缺。站好别动，我这就为你逆转。
  - **RU**: Обещание есть обещание. Быть тушканчиком весело, но твои руки нам тоже нужны. Постой спокойно секунду.
- **`pathfinder.safe`**
  - **EN**: All three floors are quiet right now, and my ears haven't picked up a single suspicious sound near the top hatch.
  - **ZH**: 这三层现在都挺安静，顶上舱口附近也没听见什么可疑动静。
  - **RU**: На всех трёх этажах тихо, и у верхнего люка мои уши не уловили ничего подозрительного.
- **`pathfinder.thanks`**
  - **EN**: Thank you. I'll make sure these get put away in the middle-floor garden reserve chest. Head down to the common room and rest your legs whenever you like.
  - **ZH**: 谢谢。我会把这些收进中层菜园的储备箱。想歇歇腿，就去公共休息室坐坐吧。
  - **RU**: Спасибо. Я прослежу, чтобы всё попало в общий сундук огорода. Захочешь отдохнуть — заходи в общую комнату.
- **`pathfinder.transfer`**
  - **EN**: You're settled in at %s. Want to make the Desert Refuge your home instead? Your friends there will still remember you, and nothing changes until you say the word.
  - **ZH**: 你现在住在%s。想改为住进沙漠避难所吗？那边的朋友仍会记得你，你没点头前什么都不会变。
  - **RU**: Сейчас твой дом — %s. Хочешь переехать в Пустынное убежище? Старые друзья тебя не забудут, и без твоего согласия ничего не изменится.
- **`pathfinder.watch`**
  - **EN**: If anything hostile slips near the entrance or bothers the neighbors, please help drive it off. Don't go looking for fights out in the dunes, though. Let me know once everyone is safe.
  - **ZH**: 要是有敌对生物靠近入口、找邻居的麻烦，请帮忙赶走。别特意跑到沙丘里找架打，大家安全后回来告诉我就好。
  - **RU**: Если кто-то враждебный подберётся ко входу или нападёт на соседей, помоги его прогнать. Не ищи драк в дюнах. Просто сообщи, когда все будут в безопасности.
- **`pathfinder.watch_thanks`**
  - **EN**: Thanks for backing up our neighbors. My ears can stop twitching now! Head down to the common room and catch your breath.
  - **ZH**: 谢谢你照应邻居们。我的耳朵总算能消停了！去公共休息室歇歇吧。
  - **RU**: Спасибо, что поддержал соседей. Уши наконец перестали дёргаться! Передохни в общей комнате.
- **`pathfinder.watch_wait`**
  - **EN**: Let's make sure the refuge is out of danger first. If the dunes have been quiet today, leave them that way. Nobody wants you picking a fight on our account.
  - **ZH**: 先确认避难所脱离危险。要是今天沙丘上一直很平静，就让它继续平静吧，没人想让你为了我们去挑起争斗。
  - **RU**: Сначала убедимся, что убежищу ничто не грозит. Если в дюнах сегодня тихо, пусть так и будет. Никто не просит тебя затевать драки ради нас.

#### Resident Camp-Life Voice (`desert_resident.*`)

- **`desert_resident.away`**
  - **EN**: Refuge supplies can wait until we're back under the dunes. Right now, I'm glad to be traveling with you.
  - **ZH**: 避难所的物资，等回到沙丘底下再说。现在能和你一起旅行，我就很高兴了。
  - **RU**: Припасы убежища подождут, пока мы не вернёмся под дюны. А сейчас я просто рад путешествовать с тобой.
- **`desert_resident.cautious_greeting`**
  - **EN**: Hello. Are you busy right now? There's a small favor I wanted to ask whenever you have a spare moment.
  - **ZH**: 你好，现在忙吗？等你有空，想请你帮个小忙。
  - **RU**: Здравствуй. Ты сейчас занят? Когда будет время, хочу попросить о небольшом одолжении.
- **`desert_resident.cautious_request`**
  - **EN**: Could you bring me %s portions of %s? Hand them straight to me whenever it's convenient, and watch yourself out on the dunes.
  - **ZH**: 能给我带 %s 份%s吗？方便时直接交给我。去沙丘上可要小心。
  - **RU**: Принесёшь мне %s порций: %s? Передай мне, когда будет удобно. И береги себя в дюнах.
- **`desert_resident.cautious_thanks`**
  - **EN**: Thank you. More than anything, I'm glad you made it back inside safe.
  - **ZH**: 谢谢。最让我高兴的，还是你平安回来了。
  - **RU**: Спасибо. Больше всего я рад, что ты благополучно вернулся.
- **`desert_resident.changed`**
  - **EN**: We can't finish that right now. Let's check that the items match, who they're meant for, and whether there's room for them.
  - **ZH**: 现在还交不了。咱们看看物品是否对、该交给谁，以及还有没有地方放。
  - **RU**: Сейчас закончить не выходит. Проверим предметы, кому они предназначены и есть ли для них место.
- **`desert_resident.choose_item`**
  - **EN**: Hold whatever you'd like to share in your hand so I can see it. Take your time. You can come back whenever you're ready.
  - **ZH**: 把想分享的东西拿在手里，让我看看。不急，准备好再回来。
  - **RU**: Возьми в руку то, чем хочешь поделиться, чтобы я увидел. Не спеши, возвращайся, когда будешь готов.
- **`desert_resident.danger`**
  - **EN**: Someone nearby ran into trouble. Let's help them out before heading back to the rooms.
  - **ZH**: 附近有人遇上麻烦了。先去帮忙，再回房间吧。
  - **RU**: Кто-то рядом попал в беду. Поможем, прежде чем возвращаться в комнаты.
- **`desert_resident.declined`**
  - **EN**: That's all right. We'll figure something else out.
  - **ZH**: 没关系，我们再想别的办法。
  - **RU**: Ничего страшного. Что-нибудь придумаем.
- **`desert_resident.donate`**
  - **EN**: You brought %s? How much of it would you like to leave in the reserve for everyone to share?
  - **ZH**: 你带了%s？想给大家的储备留下多少？
  - **RU**: Ты принёс %s? Сколько хочешь оставить в общих запасах?
- **`desert_resident.food_enough`**
  - **EN**: We have plenty to eat between the garden and the reserve chest. If you live here with us, take whatever you need.
  - **ZH**: 菜园和储备箱里的食物够吃。住在这里的话，需要什么就拿吧。
  - **RU**: Огород и общий сундук нас кормят. Если живёшь с нами, бери еду по необходимости.
- **`desert_resident.food_low`**
  - **EN**: Food is getting a little low in the middle-floor garden reserve. We've been checking the garden beds and looking around for extra rations.
  - **ZH**: 中层菜园的储备快没吃的了。我们一直在查看菜园，也四处找些额外口粮。
  - **RU**: В общих запасах огорода маловато еды. Мы проверяем грядки и ищем дополнительные припасы.
- **`desert_resident.food_plenty`**
  - **EN**: We have plenty of food put away, with fresh oranges, vegetables, and soup. It would be nice to gather everyone in the common room for a meal.
  - **ZH**: 橙子、蔬菜和汤都备了不少。真想叫大家到公共休息室一起吃一顿。
  - **RU**: Апельсинов, овощей и супа заготовлено много. Хорошо бы собраться всем в общей комнате и поесть.
- **`desert_resident.gesture`**
  - **EN**: The resident gives a soft call and tries to gesture, but you can't understand what they're saying yet.
  - **ZH**: 这位居民轻轻叫了一声并比划着什么，但你现在还听不懂它的话。
  - **RU**: Житель тихонько подаёт голос и пытается что-то объяснить жестами, но ты пока не понимаешь его речи.
- **`desert_resident.greeting`**
  - **EN**: Oh, hello there. Do you have a moment? There's something down here I could use a hand with.
  - **ZH**: 哦，你好。有空吗？这里有件小事想请你帮忙。
  - **RU**: О, привет. Есть минутка? Мне тут нужна небольшая помощь.
- **`desert_resident.handled`**
  - **EN**: Someone already brought those by for me, but thank you for keeping it in mind.
  - **ZH**: 那些东西已经有人给我带来了，不过还是谢谢你记着。
  - **RU**: Это мне уже принесли, но спасибо, что помнишь.
- **`desert_resident.meal`**
  - **EN**: I'll go let the others know. Come grab a cushion in the middle common room.
  - **ZH**: 我去叫其他人。到中层公共休息室拿个坐垫吧。
  - **RU**: Я позову остальных. Бери подушку в общей комнате на среднем этаже.
- **`desert_resident.no_requests`**
  - **EN**: I don't need anything for myself right now. Stay and chat in the cool air if you'd like.
  - **ZH**: 我自己现在没缺什么。愿意的话，留下乘凉聊会儿吧。
  - **RU**: Мне пока ничего не нужно. Можешь остаться в прохладе и поболтать.
- **`desert_resident.playful_greeting`**
  - **EN**: Hey, you're here! Always fun watching folks bounce down these long stairwell drops—got a minute to chat?
  - **ZH**: 嘿，你来啦！每次看大家从长楼梯一层层蹦下来都挺热闹的——现在有空聊两句吗？
  - **RU**: Эй, ты пришёл! Весело смотреть, как все прыгают по длинной лестнице… найдётся минутка поболтать?
- **`desert_resident.playful_request`**
  - **EN**: Could you bring me %s portions of %s? Hand them straight to me so I don't have to go digging through the reserve chest for my own snacks!
  - **ZH**: 能给我带 %s 份%s吗？直接交给我吧，免得我还得去储备箱里翻自己的零嘴！
  - **RU**: Принесёшь мне %s порций: %s? Передай прямо мне, чтобы не пришлось выискивать свои закуски в общем сундуке!
- **`desert_resident.playful_thanks`**
  - **EN**: You brought them! Thank you. I'll save you the best pillow on the common room rug.
  - **ZH**: 带来啦！谢谢。公共休息室里最舒服的坐垫给你留着。
  - **RU**: Принёс! Спасибо. Лучшую подушку в общей комнате оставлю тебе.
- **`desert_resident.polite_greeting`**
  - **EN**: Hello, neighbor. If you have a spare moment, could I ask you for a small favor?
  - **ZH**: 你好，邻居。有空时，能请你帮个小忙吗？
  - **RU**: Здравствуй, сосед. Если найдётся время, можно попросить тебя о маленьком одолжении?
- **`desert_resident.polite_request`**
  - **EN**: Would you mind bringing me %s portions of %s? Hand them to me once you have them all. There's no rush.
  - **ZH**: 方便给我带 %s 份%s吗？凑齐后交给我就行，不着急。
  - **RU**: Не мог бы ты принести мне %s порций: %s? Передай мне, когда соберёшь всё. Спешить некуда.
- **`desert_resident.polite_thanks`**
  - **EN**: Thank you for making the trip. Please stay and sit in the common room for a while if you'd like to rest.
  - **ZH**: 谢谢你特意跑一趟。想歇歇的话，请到公共休息室坐会儿。
  - **RU**: Спасибо, что сходил за этим. Если хочешь передохнуть, посиди в общей комнате.
- **`desert_resident.remember`**
  - **EN**: I remember you helping us out before. Good to see you back down here.
  - **ZH**: 我记得你以前帮过忙。又在这里见到你，真好。
  - **RU**: Я помню, как ты помогал нам. Рад снова видеть тебя здесь.
- **`desert_resident.request`**
  - **EN**: Could you bring me %s portions of %s? Hand them straight to me once you've gathered the full amount.
  - **ZH**: 能给我带 %s 份%s吗？凑齐后直接交给我就好。
  - **RU**: Принесёшь мне %s порций: %s? Когда соберёшь всё, передай прямо мне.
- **`desert_resident.requests`**
  - **EN**: I'm trying to set aside a little food for myself. If you have any to spare, could you bring me some?
  - **ZH**: 我想给自己留点吃的。要是你有多余的，能带些给我吗？
  - **RU**: Хочу отложить немного еды для себя. Если у тебя есть лишнее, принесёшь мне?
- **`desert_resident.reverse_rite`**
  - **EN**: The Pathfinder will gladly help you get your human hands back. Hold still.
  - **ZH**: 引路人会很乐意帮你找回人类双手的。站好别动。
  - **RU**: Проводник с радостью поможет тебе вернуть человеческие руки. Не двигайся.
- **`desert_resident.safe`**
  - **EN**: All three floors are nice and quiet right now. Hope it stays that way.
  - **ZH**: 现在这三层都安安静静的，希望能一直这样。
  - **RU**: На всех трёх этажах спокойно. Надеюсь, так и останется.
- **`desert_resident.seekchief`**
  - **EN**: (Pathfinder up in the east office can tell you what the neighbors need. To understand residents directly, you'll need a translator, telepathy, or a latex form.)
  - **ZH**: （东边办公室的领路人能直接告诉你大家需要什么。想直接听懂居民的话，则需要先获得翻译器、心灵感应或兽化形态。）
  - **RU**: (Проводник в восточном кабинете сможет объяснить, что нужно соседям. Чтобы понимать жителей напрямую, понадобится переводчик, телепатия или латексная форма.)
- **`desert_resident.thanks`**
  - **EN**: Thank you! That helps me out a lot. Grab a cushion in the common room and rest your legs for a bit if you have time.
  - **ZH**: 谢谢！帮上我大忙了。有空就去公共休息室拿个坐垫，歇会儿腿吧。
  - **RU**: Спасибо! Очень выручил. Если есть время, возьми подушку в общей комнате и отдохни.
- **`desert_resident.transfer`**
  - **EN**: Your current home is at %s. If you're thinking of moving in with us, have a word with Pathfinder up in the east office.
  - **ZH**: 你现在的家在%s。要是想搬来，就去东边办公室和领路人说说吧。
  - **RU**: Сейчас твой дом — %s. Если думаешь переехать к нам, поговори с Проводником в восточном кабинете.
- **`desert_resident.watch`**
  - **EN**: If anything comes after our neighbors, could you help chase it off? Let us know once everyone's safe.
  - **ZH**: 要是有什么来找邻居们的麻烦，能帮忙赶走吗？大家安全后告诉我们一声。
  - **RU**: Если кто-то нападёт на соседей, поможешь его прогнать? Сообщи, когда все будут в безопасности.
- **`desert_resident.watch_thanks`**
  - **EN**: Thank you for helping us deal with that trouble. It's a relief you were around.
  - **ZH**: 谢谢你帮我们处理麻烦。幸好你在。
  - **RU**: Спасибо, что помог справиться с бедой. Хорошо, что ты оказался рядом.
- **`desert_resident.watch_wait`**
  - **EN**: Let's make sure everyone is safe first. There's no need to go looking for a fight on our account.
  - **ZH**: 先确认大家安全吧。不用为我们特意去找架打。
  - **RU**: Сначала убедимся, что все в безопасности. Не нужно ради нас искать драку.

#### Narrative Fragments (English Only)

- **`changed:clipboard`** @ `(24, 7, 3)` — Lab test subject clipboard
  ```text
  Test subject:
  
  X-1221
  
  Result:
  Failure
  
  More test subjects required.
  ```
- **`changed:clipboard`** @ `(7, 8, 14)` — Unlocks `clue:desert_568712`
  ```text
  568712...
  
  Why these numbers resound so hard on my head?
  ```
- **`changed:clipboard`** @ `(11, 26, 17)` — Supplier password clipboard
  ```text
  To the Supplier:
  
  Make sure to update today's password to 144148.
  ```
- **`item_frame(changed:compact_disc)`** @ `(24, 7, 5)` — Compact disc in invisible item frame
  ```text
  Subject X-1221
  
  Gender: Male
  Age: 32
  Blood Type: O-
  
  We attempted to fuse the subject with a Custom Latex sample bred within the facility, using the eastern stasis chamber and an oxygenated-water suspension.
  
  The result was unsatisfactory. Although telemetry confirmed the subject's consciousness remained intact, the custom strain's survival instincts proved too aggressive to synchronize with his nervous system. Upon chamber release, the host lost total voluntary control of the body, thrashing against the containment bay and scattering resin across the eastern wall.
  
  Sedative neutralization was required to halt the reaction and secure the lab. The strain will need further stabilization before the next trial.
  ```

### 2.5 Lush Cabin (`lush_cabin`)

- **Biome / Placement**: Lush Caves (Underground)
- **Resident Dialogue**: Built-in residents use station-specific root nodes by `DialogueRole` (`resident_spring`, `resident_kitchen`, `resident_lounge`, `resident_dorm`, `resident_attic`) and share the `cavern_resident` camp-life voice.

#### Chief: Cavern Caretaker (`changed_synergy:latex_spidtaur`) (`cavern_chief.json`, camp voice `cavern`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh, careful on the damp stairs! I didn't expect a human visitor to wander this far underground. Welcome in. Feel free to warm up by the downstairs campfire or rest up here, just watch your footing around the mossy floorboards.
  - **ZH**: 哎呀，楼梯潮滑，当心脚下！真没想到会有人类客人一路走到这么深的地下。欢迎进来，楼下营火边暖和，楼上也可以歇脚，就是小心木板上的苔藓，别滑倒了。
  - **RU**: Ох, осторожнее на сырых ступеньках! Не ожидал, что человек забредёт так глубоко под землю. Заходи, чувствуй себя как дома. Можешь погреться у костра внизу или отдохнуть наверху, только гляди под ноги на мшистых досках.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Four legs, four arms, and they still manage to get around on just two. Hope those glowberries downstairs aren't too dim for human eyes... Wait, did I close the attic hatch before they got here?)
  - **ZH**: （四条腿、四只手……结果人类只靠两条腿也能走这么稳。希望楼下的发光浆果对人类眼睛来说不算太暗……等等，在客人进来之前，我把阁楼的活板门关好了吗？）
  - **RU**: (Четыре ноги, четыре руки… а люди умудряются обходиться всего двумя. Надеюсь, светящиеся ягоды внизу не слишком тусклые для человеческих глаз… Погодите, я ведь закрыл люк на чердак до того, как гость вошёл?)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hey there, welcome in! Watch your step on the wet planks by the stairs. Always nice to see another traveler make it down out of the glare. Have a seat under the glowberries or grab a pillow downstairs anytime you need a breather.
  - **ZH**: 你好呀，欢迎进来！楼梯那边的湿木板有点滑，留神脚下。能看到又一位旅人避开外头的强光来到这儿，真让人高兴。要是走累了，随时去发光浆果藤下坐坐，或者去楼下拿个软垫歇会儿。
  - **RU**: Привет, заходи! Осторожнее на влажных досках у лестницы. Всегда приятно видеть ещё одного путника, добравшегося сюда подальше от яркого света. Присаживайся под светящимися ягодами или бери подушку внизу, если нужно передохнуть.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Polite company, thank goodness. Please don't look straight up at the attic rafters right away... I swear I was going to clear those cobwebs tomorrow.)
  - **ZH**: （太好了，是个有礼貌的客人。别一上来就抬头往阁楼椽子上瞄啊……我发誓我本来打算明天就去清蜘蛛网的。）
  - **RU**: (Вежливый гость, слава богу. Только, пожалуйста, не задирай голову сразу на стропила чердака… Честное слово, я собирался расчистить ту паутину завтра.)
- **Choices**:
  - `about` -> `about` (`condition=always`)
    - **EN**: Who are you? And why did you move all the way down into a cave? | **ZH**: 你是谁？又为什么会搬到这么深的洞穴里来？ | **RU**: Кто ты? И почему решил поселиться так глубоко в пещере?
  - `home` -> `home` (`condition=always`)
    - **EN**: How do you all live down here? The cabin looks surprisingly bright and busy. | **ZH**: 你们平时在地下是怎么生活的？这间木屋比想象中亮堂多了，也挺热闹。 | **RU**: Как вы все тут устроились? В хижине неожиданно светло и оживлённо.
  - `amethyst` -> `amethyst` (`condition=always`)
    - **EN**: You keep a lot of amethyst around here. You seem pretty fond of it. | **ZH**: 你们屋里摆着不少紫水晶……你好像很喜欢这东西？ | **RU**: У вас тут повсюду аметисты… похоже, они тебе очень нравятся.
  - `chores` -> `chores` (`condition=always`)
    - **EN**: Need any help with repairs or chores around the cabin? | **ZH**: 屋子需要帮忙修缮，或者有什么杂活要搭把手吗？ | **RU**: Нужна какая-нибудь помощь по ремонту или делам по дому?
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room in the cabin for another neighbor? | **ZH**: 这间木屋里还有地方留给新邻居吗？ | **RU**: Найдётся ли в хижине место для ещё одного соседа?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About my place in the household… | **ZH**: 关于我住在这里的事…… | **RU**: Насчёт моего проживания здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Back again? Come on in out of the damp. Grab something warm from the kitchen smokers or kick back by the campfire downstairs whenever you like.
  - **ZH**: 又回来啦？快进来避避潮气。想吃热乎的可以去厨房烟熏炉那儿拿点，或者去楼下营火边歇歇脚。
  - **RU**: Снова к нам? Заходи, не стой на сырости. Можешь взять чего-нибудь тёплого из коптильни на кухне или отдохнуть у костра внизу.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (They remembered the slick stairs this time. Wonder if they brought dry planks or just came down for a breather.)
  - **ZH**: （这次倒记得楼梯湿滑了。不知道是带了干木板来，还是单纯下来歇口气。）
  - **RU**: (На этот раз про скользкие ступеньки помнят. Интересно, принесли сухих досок или просто заглянули передохнуть?)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Good to see you back down here! Everything's quiet in the cavern today, so pull up a pillow and make yourself comfortable.
  - **ZH**: 很高兴看你又下来了！今天洞里安安静静的，自己找个软垫舒服待着吧。
  - **RU**: Рад снова тебя видеть! Сегодня в пещере тихо, так что бери подушку и устраивайся поудобнее.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Wait, did I put my tools back on the shelf or leave them beside the jukebox again? Keep smiling, look calm.)
  - **ZH**: （等等，我工具到底放回架子上了，还是又落到唱片机旁边了？保持微笑，淡定。）
  - **RU**: (Стоп, я убрал инструменты на полку или снова оставил у проигрывателя? Улыбаемся и не подаём виду.)
- **Choices**:
  - `about` -> `about` (`condition=always`)
    - **EN**: Who are you? And why did you move all the way down into a cave? | **ZH**: 你是谁？又为什么会搬到这么深的洞穴里来？ | **RU**: Кто ты? И почему решил поселиться так глубоко в пещере?
  - `home` -> `home` (`condition=always`)
    - **EN**: How do you all live down here? The cabin looks surprisingly bright and busy. | **ZH**: 你们平时在地下是怎么生活的？这间木屋比想象中亮堂多了，也挺热闹。 | **RU**: Как вы все тут устроились? В хижине неожиданно светло и оживлённо.
  - `amethyst` -> `amethyst` (`condition=always`)
    - **EN**: You keep a lot of amethyst around here. You seem pretty fond of it. | **ZH**: 你们屋里摆着不少紫水晶……你好像很喜欢这东西？ | **RU**: У вас тут повсюду аметисты… похоже, они тебе очень нравятся.
  - `chores` -> `chores` (`condition=always`)
    - **EN**: Need any help with repairs or chores around the cabin? | **ZH**: 屋子需要帮忙修缮，或者有什么杂活要搭把手吗？ | **RU**: Нужна какая-нибудь помощь по ремонту или делам по дому?
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Do you have room in the cabin for another neighbor? | **ZH**: 这间木屋里还有地方留给新邻居吗？ | **RU**: Найдётся ли в хижине место для ещё одного соседа?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About my place in the household… | **ZH**: 关于我住在这里的事…… | **RU**: Насчёт моего проживания здесь…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Folks around here call me Caretaker, mostly because half my mornings go to patching damp planks and reminding guests not to slip on wet moss. As for moving underground? Well, I cannot say I don't miss the sunlight. However, some of us, myself included, are very sensitive to strong light, which is why we rarely go up to the surface. Most likely, if we have to go up there, we do it at night.
  - **ZH**: 大家叫我看护者，多半是因为我每天一大早都在修补受潮的木板，还得提醒客人别在湿苔藓上滑倒。至于搬到地下嘛……说不怀念阳光那是假的。不过我们中有不少同伴，包括我自己在内，对强光非常敏感，所以极少去地表。真有事非上去不可，多半也是挑夜里走。
  - **RU**: Местные зовут меня Смотрителем, в основном потому, что пол-утра я латаю сырые доски и напоминаю гостям не поскользнуться на мхе. А почему мы перебрались под землю? Ну, не могу сказать, что не скучаю по солнечному свету. Но некоторые из нас, включая меня, очень чувствительны к яркому свету, поэтому на поверхность мы выбираемся редко. Скорее всего, если уж приходится идти наверх, мы делаем это ночью.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (One bright sunbeam at noon and all my eyes start watering at once. Give me cool blue cave stone and soft glowberry vines any day of the week.)
  - **ZH**: （大中午挨上一束强光，我这几只眼睛立刻就得跟着流泪。要我说，还是清凉的洞穴石壁和柔和的发光浆果藤舒服得多。）
  - **RU**: (Один полуденный солнечный луч в глаза — и они все разом слезятся. Нет уж, прохладный камень пещеры и мягкий свет ягодных лоз мне куда милее.)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Six of us share the cabin right now: two Stigers watching the ground patio and the attic, our Translucent Lizard by the kitchen ovens, a Traffic Cone Dragon parked near the stacked crates, a Kobold upstairs in the dorm, and me. You're wondering how this place is so bright? Actually, that's a very good question! See those small bulb-like fruits on the vines around us? Those are glowberries. Hence their name, they give off a strong light, much like lanterns or torches, without hurting our eyes. Also, they taste very sweet!
  - **ZH**: 现在这间屋子里住着六个同伴：两只蜘蛛虎分别照看着一楼露台和阁楼，一只半透明蜥蜴守在厨房炉灶边，一只交通锥标龙总蹲在货箱旁，三楼宿舍还有一只 Kobold，再加上我。你问这儿为什么这么亮堂？这确实是个好问题！看见周围藤蔓上那些像小灯泡一样的果子了吗？那就是发光浆果。就像名字说的那样，它们发出的光很亮，跟灯笼或火把差不多，还不晃眼睛。而且吃起来可甜了！
  - **RU**: Сейчас в хижине живут шестеро: два стайгера присматривают за нижней площадкой и чердаком, наша просвечивающаяся ящерица греется у кухонных печей, дракон-дорожный конус устроился возле ящиков, кобольд спит в спальне наверху, ну и я. Спрашиваешь, почему здесь так светло? Вообще, это отличный вопрос! Видишь те маленькие светлые плоды, похожие на лампочки, на лозах вокруг? Это светящиеся ягоды. Как и следует из названия, они светят очень ярко, почти как фонари или факелы, только глаза от них не болят. И на вкус они очень сладкие!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Plus, half the reason the kitchen stays lit all evening is so nobody has to haul coal up from the lower caves just to read a recipe.)
  - **ZH**: （再说了，厨房一晚上都亮亮堂堂的，一大半功劳全归这些果子，省得大伙为了看清食谱特意往深层矿洞搬煤。）
  - **RU**: (Да и на кухне светло весь вечер во многом благодаря им — никому не приходится таскать уголь из глубоких штреков просто ради того, чтобы разобрать рецепт.)
- **Choices**:
  - `water` -> `water_spring` (`condition=always`)
    - **EN**: Is that water over by the amethyst geode clean to drink? | **ZH**: 紫晶洞旁边流出来的那些水能喝吗？干净不？ | **RU**: Вода там, у аметистовой жеоды… её можно пить?
  - `quarters` -> `quarters` (`condition=always`)
    - **EN**: How are the beds, storage, and attic stairs organized here? | **ZH**: 这里的床位、储物箱和楼上楼梯是怎么安排的？ | **RU**: Как тут устроены спальни, склады и проход наверх?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `water_spring` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: If you're talking about the water pouring near the amethyst geode downstairs, yes. It comes from an underground spring, so it is perfectly safe for drinking. Also, it is a way better choice than drinking the water from the lush caves… Moss spores and cave silt settle into those outside puddles pretty fast.
  - **ZH**: 如果你说的是楼下紫晶洞旁边涌出来的那处水，能喝，放心吧。那是正经地下泉眼涌出的活水，喝着很安全。而且比起直接喝外头繁茂洞穴里的积水，这里强出太多了……外面水洼里很快就会积满苔藓孢子和泥沙。
  - **RU**: Если ты про воду, которая стекает у аметистовой жеоды внизу, то да. Она бьёт из подземного источника, так что пить её совершенно безопасно. И честно говоря, это куда лучший выбор, чем вода из пышных пещер… В тех лужах слишком быстро оседают ил и споры мха.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Our lizard neighbor sipped from an outside dripleaf puddle once before moving in here. Spent the whole evening sneezing out tiny moss shoots. Never again.)
  - **ZH**: （半透明蜥蜴搬进来之前喝过一回外头垂滴叶底下的水洼……结果一整个晚上都在打喷嚏喷小苔藓芽。再也不敢了。）
  - **RU**: (Наша ящерица как-то хлебнула из лужи под бросянкой ещё до переезда сюда. Весь вечер потом чихала мелкими побегами мха. Больше ни за что.)
- **Choices**:
  - `back` -> `home` (`condition=always`)
    - **EN**: Back to how the cabin runs… | **ZH**: 说回木屋的日常…… | **RU**: Вернёмся к жизни в хижине…
  - `root` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `quarters` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Down by the campfire pillows on the ground floor, there's a double chest for our shared household reserve. Guests and neighbors can drop off extra food or take what they need from there. Up on this floor, the six beds in the west room are open to anyone staying over, and you're free to borrow books from the shelves. Just leave the chests right next to folks' beds alone, and… maybe don't climb the ladder into the attic just yet.
  - **ZH**: 一楼营火软垫旁那组大箱子是社区公共储物箱，客人们和邻居都能往里放富余物资，需要什么也可以自取。三楼西侧那六张床铺随时给留宿的同伴空着，书架上的书也欢迎借阅。只要别翻别人床头的私人箱子就行，还有……四楼阁楼的梯子要不先别上去。
  - **RU**: Внизу, у костра с серыми подушками, стоит двойной сундук с общим запасом. Соседи и гости могут оставлять там припасы или брать то, что нужно. На этом этаже шесть коек в западной комнате открыты для всех, и книги с полок тоже можно брать почитать. Только не трогай личные сундуки у чужих кроватей и… пожалуй, пока не лезь по приставной лестнице на чердак.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Keep the tour moving right along, please don't look up at the attic hatch… Oh, wait, I left the ladder right in plain sight, didn't I?)
  - **ZH**: （赶紧带过这一段，千万别抬头看阁楼活板门……哎呀，那梯子明明就明晃晃架在墙边呢吧？）
  - **RU**: (Спокойно переводим разговор, только не смотри на люк чердака… Ой, так ведь лестница стоит прямо на самом видном месте, да?)
- **Choices**:
  - `attic` -> `attic` (`condition=always`)
    - **EN**: Why skip the attic? Wait… it's full of cobwebs up there, isn't it? | **ZH**: 干嘛不让我上去？等等……阁楼里是不是全是蜘蛛网？ | **RU**: Почему не лезть? Погоди… у тебя там весь чердак в паутине, да?
  - `back` -> `home` (`condition=always`)
    - **EN**: Back to how the cabin runs… | **ZH**: 说回木屋的日常…… | **RU**: Вернёмся к жизни в хижине…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `amethyst` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Well, of course! For some of us, amethyst is a token of solid friendship. Some of us get so happy receiving one as a gift that we may even form a bond with that person!
  - **ZH**: 那当然喜欢啦！对我们当中的不少人来说，紫水晶可是牢靠友谊的信物。有的同伴收到别人送的紫水晶时，高兴得甚至会直接和对方结下羁绊呢！
  - **RU**: Ну конечно! Для некоторых из нас аметист — это знак крепкой дружбы. Бывает, что кто-нибудь так обрадуется этому подарку, что может даже образовать связь с тем, кто его подарил!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Even a rough shard catches glowberry light softly without stinging your eyes… Plus, holding one when someone hands it to you means they actually thought of you.)
  - **ZH**: （就算只是一块没怎么打磨的晶簇，折射出来的发光浆果微光也一点不刺眼……更何况别人亲手递过来的时候，说明心里真是惦记着你。）
  - **RU**: (Даже неровный осколок ловит свет ягод совсем мягко и не режет глаза… А ещё, когда тебе протягивают его в руки, сразу ясно, что о тебе правда подумали.)
- **Choices**:
  - `bond` -> `bond` (`condition=always`)
    - **EN**: Sorry, a "bond"? What does that mean between you folks? | **ZH**: 抱歉打断一下，“羁绊”是什么意思？ | **RU**: Прости, «связь»? Это как вообще?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `bond` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: A bond, yes. It's like when you and your latex friend trust each other to a point where you'd both "take a bullet" for one another, so to speak. Heck, some even fuse their bodies, just temporarily, to travel together or shield a partner in danger. We call this temporary fusion a "suiting".
  - **ZH**: 对，就是羁绊。就好比你跟自己的胶兽朋友互相信任到一个份上，真到要紧关头都肯替对方“挡子弹”，打个比方啦。说起来，有些搭档关系深了，甚至还会把身体暂时融在一块儿一块赶路，或者遇险时护着彼此。我们把这种暂时的融合叫做“包裹”。
  - **RU**: Да, именно связь. Это как когда вы с другом-латексом настолько доверяете друг другу, что готовы буквально «прикрыть собой от пули», если так можно выразиться. Да что там, некоторые даже сливают тела вместе, ну, временно, чтобы идти рядом или защитить напарника в беде. У нас такое временное слияние называют «облачением».
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Wait, did explaining "suiting" to a guest just make it sound weirder? No, no, they looked curious, not scared. Good enough.)
  - **ZH**: （等等，刚才这么跟客人解释“包裹”，会不会听着反而有点怪？唔，没有，对方看起来挺好奇的，倒没被吓到。行吧。）
  - **RU**: (Стоп, а я сейчас не слишком странно объяснил гостю про «облачение»? Да нет вроде, взгляд любопытный, не испуганный. Уже хорошо.)
- **Choices**:
  - `back` -> `amethyst` (`condition=always`)
    - **EN**: Back to the amethysts… | **ZH**: 说回紫水晶的事…… | **RU**: Вернёмся к аметистам…
  - `root` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `chores` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: As a matter of fact, I've been trying to repair parts of the house. The humidity and moss down here really make a mess out of the floorboards and walls, so if you ever drop spare rations or wood in the community reserve when you visit, we'll put them right to use.
  - **ZH**: 你还真问着了，我最近正琢磨修修屋子几处地方呢。地下这股湿气加上蔓生出来的苔藓，把木地板和墙面折腾得够呛；平时顺道来串门要是带点干粮或木板之类的富余物资，大伙很快就能派上用场。
  - **RU**: По правде говоря, я как раз пытаюсь кое-где подлатать дом. Сырость и ползущий вокруг мох сильно портят доски пола и стен, так что если при случае закинешь в общий запас еду или лишние припасы, мы сразу найдём им дело.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Dark oak holds up decent enough, spruce warps fast, and dripping moss spots keep soaking right through the stairs every week…)
  - **ZH**: （深色橡木还算耐潮，云杉木板一受潮就爱变形，楼梯那片老渗水的地方每周都得盯着换……）
  - **RU**: (Тёмный дуб ещё держится, ель ведёт быстро, а на лестнице сырость каждую неделю проступает заново…)
- **Choices**:
  - `attic` -> `attic` (`condition=always`)
    - **EN**: Your attic… is packed with cobwebs! | **ZH**: 你的阁楼……里面全塞满蜘蛛网了！ | **RU**: У тебя на чердаке… полно паутины!
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `attic` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Ah… eh, hehe… That is my fault, I'm sorry! I really just haven't had the time to clean it out! Feel free to clear away the cobwebs and those white wool clumps if they bother you, and honestly you can keep any ores you mine up there. There are plenty of basic veins and even two diamond ores embedded in the wall; we have no use for them down here.
  - **ZH**: 啊……呃，嘿嘿……那是我的锅，真不好意思！我一直没腾出空去收拾！要是嫌上面那些蜘蛛网和白羊毛团碍眼，尽管顺手清掉；上头嵌着的那些矿石你也可以自己挖走留着。那儿有好些常见矿脉，墙里甚至还嵌着两块钻石矿，我们在地下反倒用不着。
  - **RU**: Ах… э-э, хе-хе… Это моя вина, прости! У меня правда никак не доходили руки там убраться! Если паутина и комки белой шерсти наверху мешают, можешь смело их расчистить — а всю руду, какая там попадётся, забирай себе. Там хватает обычной руды и даже две жилы алмазной в стене; нам они тут всё равно ни к чему.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (When cobwebs don't snag your legs and wool bunches make passable cushions, you genuinely stop noticing how bad it looks until someone two-legged climbs the ladder!)
  - **ZH**: （平时蜘蛛网又绊不住自己，白羊毛团堆在角落当靠垫也凑合，久而久之压根没觉得乱……直到真有两条腿的客人顺着梯子爬上去！）
  - **RU**: (Когда паутина к лапам не липнет, а комья шерсти вполне годятся под подушку, как-то совсем перестаёшь замечать беспорядок… пока туда не заберётся двуногий гость!)
- **Choices**:
  - `back` -> `chores` (`condition=always`)
    - **EN**: Back to the chores… | **ZH**: 说回屋里的杂活…… | **RU**: Вернёмся к делам по дому…
  - `root` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to looking after the place. | **ZH**: 那我就不打扰你照看木屋了。 | **RU**: Не буду отвлекать тебя от дел по дому.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: We've got open bunks in the third-floor dorm and plenty of glowberries to spare. As long as you respect everyone's bedside chests and help keep the place dry, you're welcome to move right in as a neighbor.
  - **ZH**: 三楼宿舍还有空着的床位，发光浆果也管够。只要尊重大家床头的私人箱子、平时帮忙照看屋子别太潮湿，随时欢迎你搬进来做邻居。
  - **RU**: У нас в спальне на третьем этаже есть свободные койки, да и светящихся ягод хватает. Если будешь уважать личные сундуки соседей и помогать держать дом сухим, можешь смело переезжать к нам.
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: You don't have to change a single thing about yourself to live with us, mind you. Having someone around in sturdy boots who can haul supplies and won't get blinded by midday light is honestly a huge help. Of course, if you're curious what getting around in my form feels like, reversible or permanent, I don't mind sharing. Up to you.
  - **ZH**: 先说好，留下来跟我们一起住完全不需要改变你人类的身体。屋里有个脚步稳当、能帮忙扛物资、白天还不怕太阳晒的同伴，说实话帮大忙了。当然了，要是你好奇变成我的样子是什么体验，不管是可逆的还是永久的，我都愿意分享给你。你自己挑就行。
  - **RU**: Если что, тебе совсем не обязательно менять свою внешность, чтобы жить с нами. Сосед в прочных сапогах, который может принести припасы и не жмурится от дневного света, тут здорово пригодится. Конечно, если тебе любопытно, каково это — передвигаться в моём облике, обратимо или навсегда, я не против поделиться. Решать тебе.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Folks around the cabin are still pretty tense right now, so I have to hold off on offering you a bunk. Give our neighbors a little space for a while. Once everyone feels comfortable around you again, we can talk about you moving in.
  - **ZH**: 屋里几位邻居这会儿心神还没定下来，我暂时没法把床位分给你。先给大伙一点缓和的空间吧，等大家在你身边重新自在起来，咱们再谈搬来住的事。
  - **RU**: Соседи в хижине сейчас ещё слишком настороже, поэтому пока не могу предложить тебе койку. Дай всем немного освоиться и успокоиться. Когда ребята снова будут чувствовать себя рядом с тобой спокойно, поговорим о переезде.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: I'd be glad to help look after the cabin and move in. | **ZH**: 我很乐意帮忙照看木屋，搬来这儿住下。 | **RU**: Я с радостью помогу следить за хижиной и перееду к вам.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll join as a human. Happy to help with repairs and chores. | **ZH**: 我就以人类身份加入吧，修屋子和做杂活我都能搭把手。 | **RU**: Я останусь человеком и с радостью помогу с ремонтом и делами.
  - `form` -> `explain_form` (`condition=human`)
    - **EN**: What would changing into your form actually be like? | **ZH**: 如果变成你的样子，具体会有什么不一样？ | **RU**: А каково это — принять твой облик?
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: First off, sharing my form doesn't hurt or tire me out, and it won't erase a single memory of yours. Taking my form does change how you move, though. With four legs underneath, four arms up top, and a sturdy frame, you can climb straight up walls, charge up a springy leap across cavern gaps with plenty of landing cushion, spin cobwebs without getting stuck in them yourself, and toggle night vision whenever a tunnel goes pitch dark. The trade-offs? Bright daylight and harsh lamps still bother sensitive eyes, and a wide four-legged frame takes a little extra room on tight cabin stairs. Keep it reversible, and it's just like a borrowed coat you can take off later—or if you leave the cabin. Make it permanent, and this body stays yours for good. Either way, you're just as welcome by the campfire.
  - **ZH**: 首先，分享我的样子既不会伤到我，也不会擦掉你的记忆。不过变成我的样子后，行动方式确实会变不少。下方四条腿、上方四只手，身板结实，既能直接爬上垂直岩壁，也能蓄力高高跃过洞窟沟壑、落地也不容易摔伤；不仅能吐出蜘蛛网，自己踩进去还不会减速，进到漆黑矿道时也能随时切出夜视。要说代价的话，地表的正午强光和太刺眼的灯火还是会晃眼，而且四足宽一些的下身在窄楼梯上走着得留点余量。如果选可逆转化，就像借穿一件随时能脱下的外套，以后退出营地时也会一并卸下；要是定成永久，这副身体就会彻底属于你。不管选哪样，营火旁永远有你坐的地方。
  - **RU**: Во-первых, поделиться формой мне совсем не трудно и не больно, и ни одно твоё воспоминание не пропадёт. Но в моём облике тело ощущается иначе. Четыре ноги снизу, четыре руки сверху и крепкое телосложение позволяют карабкаться прямо по стенам, заряжать мощный прыжок через пещерные расщелины и мягче приземляться, плести паутину без риска самому в ней запутаться и включать ночное зрение в кромешной темноте. Из минусов: резкий солнечный свет и слишком яркие лампы всё так же бьют по глазам, а широкой четвероногой нижней части на узкой лестнице нужно чуть больше места. Оставишь форму обратимой — она будет как взятый на время плащ, который можно снять позже или при уходе из хижины. Сделаешь постоянной — это тело останется с тобой навсегда. В любом случае у нашего костра тебе всегда рады.
- **Choices**:
  - `reversible` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the reversible transformation and join. | **ZH**: 我想先试试可逆转化，然后搬进来。 | **RU**: Давай попробуем обратимое превращение, и я присоединюсь.
  - `permanent_form` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I'm ready to make it permanent and join. | **ZH**: 我准备好了，进行永久转化并搬进来。 | **RU**: Я готов сделать форму постоянной и переехать.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll stay human and join. Happy to pitch in. | **ZH**: 我还是维持人类身体住下吧，有活尽管叫我。 | **RU**: Останусь человеком и поселюсь у вас, рад буду помочь.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Let's be certain before we begin, just so there are no surprises later. Making this permanent means taking my form is yours for keeps. Unlike borrowing a reversible form, leaving the cabin won't turn you back, and normal reversal methods won't restore a human body unless you choose a human form again through your Native Origin. You can still pick the reversible form instead, or stay human. Are you sure you want to lock it in permanently?
  - **ZH**: 动手之前咱们先确认清楚，免得回头反悔麻烦。选择永久转化，就意味着变成我的样子之后这副身体会彻底归你了。和临时借用的可逆形态不同，就算将来退出营地也不会变回来，常规还原方式同样不起作用，除非以后通过原生身份重新选回人类形态。你现在依然可以改选可逆版本，或者保持人类身姿住下。确定要永久定下这个形态吗？
  - **RU**: Давай точно убедимся перед тем как начать, чтобы потом не было сюрпризов. Если сделать превращение постоянным, мой облик останется с тобой навсегда. В отличие от обратимой формы, уход из хижины уже не вернёт прежний вид, и обычные способы отката не сработают, если только ты сам потом не выберешь человеческую форму через Native Origin. Ты всё ещё можешь выбрать обратимый вариант или остаться человеком. Точно хочешь закрепить форму навсегда?
- **Choices**:
  - `permanent_confirm` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I understand and choose the permanent change. | **ZH**: 是的，我清楚后果，选择永久转化。 | **RU**: Да, я всё понимаю и выбираю постоянное превращение.
  - `reconsider` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over again. | **ZH**: 让我再考虑一下。 | **RU**: Дай мне ещё немного подумать.

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Then welcome home, neighbor! Pick any free bed over in the west dorm, help yourself to the glowberries or whatever's simmering in the kitchen, and just yell if the stairs get too slick.
  - **ZH**: 那欢迎回家，邻居！西边宿舍的空床随你挑一张，发光浆果和厨房炉子上热着的吃食也别客气，楼梯要是太滑记得叫我一声。
  - **RU**: Тогда добро пожаловать домой, сосед! Выбирай любую свободную кровать в западной спальне, бери светящиеся ягоды и всё, что томится на кухне, а если ступеньки станут совсем скользкими — только скажи.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Awesome, two-legged balance without knocking over bookshelves AND they won't blink twice at bright surface noon sun! Just have to remind them to watch for low ceiling beams.)
  - **ZH**: （太好了！两只脚走路稳稳当当还不碰倒书架，大中午去地面也不用被阳光晃得睁不开眼！回头提醒一下客人进门留神低矮房梁就行。）
  - **RU**: (Замечательно! Двуногое равновесие без задевания книжных полок, и от полуденного солнца наверху им щуриться не придётся! Напомнить только потом пригибаться у низких балок.)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll settle in downstairs. | **ZH**: 我去楼下安顿一下。 | **RU**: Пойду обустраиваться внизу.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to the cabin, neighbor! There are open bunks up on this floor, warm smokers downstairs in the kitchen, and always spare cushions near the campfire.
  - **ZH**: 欢迎来到木屋，邻居！这一层就有空的床位，楼下厨房烟熏炉一直暖和着，营火旁永远给你留着坐垫。
  - **RU**: Добро пожаловать в хижину, сосед! На этом этаже есть свободные койки, внизу на кухне всегда тёплые коптильни, а у костра найдётся свободная подушка.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll settle in downstairs. | **ZH**: 我去楼下安顿一下。 | **RU**: Пойду обустраиваться внизу.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There we go… Ease your weight onto all four lower legs first, and give those extra arms a stretch before picking anything fragile off a shelf. Can you see the glint down by the amethyst pool even in the dark corners? Are you still holding on to yourself in there? Hope the sudden rush of extra senses didn't rattle you. Welcome home, neighbor, and please test out that jumping-spider leap outside on open ground, not near the jukebox!
  - **ZH**: 好啦……先试着把重心稳稳挪到底下四条腿上，把多出来的两只手臂也舒展舒展，然后再伸手碰架子上那些容易摔的东西。是不是连暗角里紫晶水池的反光都看得清清楚楚？脑子还稳当吗？没被一下子多出来的感官晃晕吧。欢迎回家，邻居！不过想试蓄力大跳的话，记得找外头开阔地试，可别冲着唱片机蹦！
  - **RU**: Вот так… Сначала перенеси вес на все четыре нижние лапы и разомни лишнюю пару рук, прежде чем брать с полок что-то хрупкое. Видишь отблеск у аметистового пруда даже в тёмных углах? Ты там ещё не потерялся от такого прилива ощущений? Надеюсь, перемена не сбила тебя с толку. Добро пожаловать домой, сосед — только проверяй свой мощный прыжок где-нибудь на просторе, а не рядом с проигрывателем!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Look at that wide-eyed pause the second they realize all four hands actually work at once. Happens every single time. Still, I really hope they remember which door leads out before trying a wall climb!)
  - **ZH**: （看那发愣的小眼神，多半是刚发现四只手居然真能一块使唤。每次都一模一样！不过但愿试爬墙之前能先看准门朝哪开！）
  - **RU**: (Посмотрите на это ошарашенное лицо в секунду, когда до них доходит, что все четыре руки действительно работают разом. Каждый раз одно и то же. И всё-таки надеюсь, что прежде чем лезть на стену, они хотя бы дверь не перепутают!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll settle in downstairs. | **ZH**: 我去楼下安顿一下。 | **RU**: Пойду обустраиваться внизу.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There we go… Ease your weight onto all four lower legs first, and give those extra arms a stretch before picking anything fragile off a shelf. Can you see the glint down by the amethyst pool even in the dark corners? Don't worry, you won't forget who you are—and this body is yours for good now. Welcome home, neighbor, and please test out that jumping-spider leap outside on open ground, not near the jukebox!
  - **ZH**: 好啦……先试着把重心稳稳挪到底下四条腿上，把多出来的两只手臂也舒展舒展，然后再伸手碰架子上那些容易摔的东西。是不是连暗角里紫晶水池的反光都看得清清楚楚？放心，你还是你，半点不会忘掉自己是谁——从今往后这具身体就彻底归你了。欢迎回家，邻居！不过想试蓄力大跳的话，记得找外头开阔地试，可别冲着唱片机蹦！
  - **RU**: Вот так… Сначала перенеси вес на все четыре нижние лапы и разомни лишнюю пару рук, прежде чем брать с полок что-то хрупкое. Видишь отблеск у аметистового пруда даже в тёмных углах? Не волнуйся, ты не забудешь, кто ты есть — и теперь это тело останется с тобой навсегда. Добро пожаловать домой, сосед — только проверяй свой мощный прыжок где-нибудь на просторе, а не рядом с проигрывателем!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Look at that wide-eyed pause the second they realize all four hands actually work at once. Happens every single time. Still, I really hope they remember which door leads out before trying a wall climb!)
  - **ZH**: （看那发愣的小眼神，多半是刚发现四只手居然真能一块使唤。每次都一模一样！不过但愿试爬墙之前能先看准门朝哪开！）
  - **RU**: (Посмотрите на это ошарашенное лицо в секунду, когда до них доходит, что все четыре руки действительно работают разом. Каждый раз одно и то же. И всё-таки надеюсь, что прежде чем лезть на стену, они хотя бы дверь не перепутают!)
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll settle in downstairs. | **ZH**: 我去楼下安顿一下。 | **RU**: Пойду обустраиваться внизу.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're one of the household here. The ground-floor reserve chests by the campfire pillows and any empty bunk in the dorm are open to you, while private bedside chests stay with their owners. If you ever need to move on, you can leave whenever you want. We'll remember the help you lent us, and if you only tried on our form for your stay here, heading out will let you go back to normal.
  - **ZH**: 你现在已经是这间社区的一员了。一楼营火软垫旁的公共储物箱和宿舍空床都归你用，各人床头的私人箱子归各自保管。如果以后打算去别处闯荡，随时可以离开。大家会记得你帮过的忙；要是你在山洞里只是暂借了这身样子来搭把手，真要动身离开时，也会顺便换回你原本的模样。
  - **RU**: Ты уже свой человек в хижине. Нижний общий сундук у костра и любая свободная койка в спальне к твоим услугам, а личные сундуки у кроватей остаются за хозяевами. Если захочешь пойти дальше, можешь уйти в любой момент. Мы будем помнить, как ты нам помогал; а если ты лишь примерил наш облик на время жизни здесь, когда соберёшься уходить, вернёшься к себе прежнему.
- **Choices**:
  - `leave` -> `confirm_leave` (`condition=always`)
    - **EN**: I need to leave this household and move on. | **ZH**: 我需要离开这个社区，继续去别处走走。 | **RU**: Мне пора покинуть общину и идти дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: Давай о другом…

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: All right. Before I cross your name off the reserve chest register and clear your bunk, are you sure? You're always welcome back down out of the sun anytime you pass by, though if you only borrowed our form for a spell, heading out lets it fade back to how you came.
  - **ZH**: 明白。在我把你的名字从公共补给名册和床位表上划掉之前，确定要离开了吗？以后路过随时可以再下来躲躲太阳、暖暖身子；不过如果你这副模样只是在山洞里暂借来的，临走时也会顺便褪回原样。
  - **RU**: Хорошо. Прежде чем вычеркну тебя из списка общего запаса и освобожу койку, ты точно уверен? Всегда можешь заглянуть к нам передохнуть от солнца, только если ты брал наш облик лишь на время, перед дорогой он сойдёт обратно к тому, с чем ты пришёл.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=always`, `effect=leave`)
    - **EN**: Yes, I'm ready to move on. | **ZH**: 是的，我准备好继续上路了。 | **RU**: Да, я готов двигаться дальше.
  - `back` -> `root` (`condition=always`)
    - **EN**: Wait, let's talk about something else. | **ZH**: 等一下，还是聊聊别的吧。 | **RU**: Погоди, поговорим о другом.

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Done, you're all signed out. Watch your footing along the wet cave ledges on your way up, neighbor. We're glad you spent time down here with us.
  - **ZH**: 记好了，已经替你办妥。顺着潮湿的洞穴岩架往外走时留神脚下，邻居。很高兴这段日子有你在这里陪着大家。
  - **RU**: Готово, я тебя выписал. Гляди под ноги на мокрых уступах пещеры по дороге наверх, сосед. Рад, что ты пожил тут с нами.
- **Choices**:
  - `back` -> `root` (`condition=always`)
    - **EN**: Before I go… | **ZH**: 临走前再问一句…… | **RU**: Прежде чем я уйду…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: Safe travels and stay dry. | **ZH**: 各自保重，多留神潮湿。 | **RU**: Счастливо оставаться.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Watch those mossy steps on your way down, and grab a handful of glowberries off the doorway vines if you're heading back into darker tunnels.
  - **ZH**: 下楼当心青苔台阶，要是还要往黑矿道里走，顺手在门框藤上揪一把发光浆果带着吧。
  - **RU**: Осторожнее на мшистых ступеньках по пути вниз, и захвати горсть светящихся ягод с лозы у входа, если пойдёшь дальше в тёмные туннели.
- **Choices**:
  - `close` -> `goodbye` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_spring` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey there, traveler. Mind the wet stones by the geode pool—The splashing near the campfire keeps the patio slick. The spring water stays crisp though, and the shared chests nearby stay open for neighbors.
  - **ZH**: 嗨，旅人。紫晶池那边的石头湿滑，营火边又老溅起水花，落脚留神点。泉水倒是一直清亮好喝，旁边的公共箱子也随时敞着给大伙用。
  - **RU**: Привет, путник. Осторожнее на мокрых камнях у жеоды — От воды у костра настил бывает скользким. Но сам источник чистый, а общие сундуки рядом всегда открыты для соседей.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Nice, didn't knock the cauldron over or bump the cave bell. Maybe they actually notice where their feet go.)
  - **ZH**: （不错，没撞翻水锅也没碰响洞口铃铛。看来是个会留心看路的客人。）
  - **RU**: (Хорошо, котёл не сбил и в колокол не врезался. Наконец-то кто-то смотрит под ноги.)
- **Choices**:
  - `topic_0` -> `resident_spring_0` (`condition=always`)
    - **EN**: Can you actually drink from that pool around the amethyst? | **ZH**: 紫晶那圈水池里的水真的能直接喝吗？ | **RU**: Воду из пруда вокруг аметиста правда можно пить?
  - `topic_1` -> `resident_spring_1` (`condition=always`)
    - **EN**: Which chests here are shared, and why is there a bell? | **ZH**: 这边哪个箱子是公用的？挂着那口钟是干嘛的？ | **RU**: Какие сундуки тут общие и зачем здесь колокол?
  - `close` -> `resident_spring` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_spring_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: That water pouring near the amethyst geode comes straight from an underground spring. Cold, clear, way better than scooping stagnant moss water from the caves outside. Just wash your boots somewhere else!
  - **ZH**: 紫晶洞旁边涌进来的这股水直接连着地下泉眼。又凉又清亮，比从外头野洞里舀积满苔藓的死水强太多了。只是……千万别在这儿洗靴子！
  - **RU**: Вода у аметистовой жеоды идёт прямо из подземного источника. Холодная, чистая, куда лучше стоячих мшистых луж снаружи. Только сапоги в ней, пожалуйста, не споласкивай!
- **Choices**:
  - `back` -> `resident_spring` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_spring_0` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_spring_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: The double chest tucked right behind these campfire pillows is for everybody, snacks and repair supplies alike. And the bell? Caretaker hung it there for cavern alerts, though mostly folks only ring it by accident when carrying too many boxes.
  - **ZH**: 营火软垫后面那组大箱子是大家公用的，吃的和修补料都放这儿。至于铃铛嘛……是看护者挂来当洞里警报用的，不过多半时候都是大家抱箱子手滑撞响的。
  - **RU**: Двойной сундук прямо за этими подушками у костра — общий, там и перекус, и припасы для починки. А колокол? Смотритель повесил его на случай тревоги, но чаще всего в него просто случайно врезаются с охапкой ящиков.
- **Choices**:
  - `back` -> `resident_spring` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_spring_1` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_kitchen` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh! Watch your elbow near the smoker exhaust, it gets hot fast. The caves stay cool most hours, so I hang around the stoves keeping broth and glowberry skewers warm. Hunger bringing you up to the second floor?
  - **ZH**: 哎！胳膊小心烟熏炉出风口，这玩意热得可快了。洞里整天凉飕飕的，所以我平时总守在炉灶边温点热汤和发光浆果串。你是饿了上二楼来找吃的？
  - **RU**: Ой! Береги локоть возле вытяжки коптильни, она быстро раскаляется. В пещерах прохладно, вот я и кручусь у печей, держу в тепле похлёбку и шпажки со светящимися ягодами. Проголодался и заглянул на второй этаж?
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Warm brick under my tail, sweet roast smell in the air, no drafts… Best spot in the whole cabin, hands down.)
  - **ZH**: （尾巴底下压着暖烘烘的炉砖，空气里飘着甜丝丝的烤果香，还没贼风……整间木屋就属这儿最舒服。）
  - **RU**: (Тёплый камень под хвостом, сладковатый запах из печей, никаких сквозняков… Однозначно лучшее место во всей хижине.)
- **Choices**:
  - `topic_0` -> `resident_kitchen_0` (`condition=always`)
    - **EN**: You run three smokers and two blast furnaces just for six folks? | **ZH**: 你们统共才六个同伴，干嘛架三个烟熏炉外加两座高炉？ | **RU**: У вас тут три коптильни и две плавильни всего на шестерых?
  - `topic_1` -> `resident_kitchen_1` (`condition=always`)
    - **EN**: I keep noticing these glowberry vines hanging near the kitchen. | **ZH**: 我看厨房旁边也垂着一串串发光浆果藤。 | **RU**: Смотрю, у вас даже на кухне свисают лозы со светящимися ягодами.
  - `close` -> `resident_kitchen` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_kitchen_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey, cold cave air drains heat fast, and Stigers eat twice as much after patrolling the tunnels. We keep basic rations in the barrels beside the smokers, so grab some if you're hungry, just close the lid tight so cave damp doesn't get in.
  - **ZH**: 嘿，阴凉洞风散热量快得很，蜘蛛虎跑完矿道回来饭量更是翻倍。普通干粮都装在烟熏炉旁边的木桶里，饿了随时拿，记得把桶盖严实了，免得潮气钻进去。
  - **RU**: Эй, прохладный пещерный воздух быстро остужает всё вокруг, а стайгеры после обхода едят за двоих. Простые припасы лежат в бочках рядом с коптильнями — бери, если голоден, только крышку закрывай поплотнее от сырости.
- **Choices**:
  - `back` -> `resident_kitchen` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_kitchen_0` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_kitchen_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Best invention underground! Bright enough to read a recipe by, sweet enough to eat straight off the stem, and soft enough that Caretaker doesn't squint whenever they come down for tea. Leave a couple bulbs glowing above the stairs if you harvest any!
  - **ZH**: 地下世界最顶用的好东西了！亮度够看清菜谱，摘下来直接嚼着还清甜，光线又柔，看护者下来喝茶时也不会晃得眯眼。要是顺手摘果子，记得楼梯上方多留几颗挂着照明！
  - **RU**: Лучшая находка под землёй! И рецепт при них прочитать можно, и прямо с ветки съесть вкусно, и свет мягкий — Смотритель не жмурится, когда спускается за чаем. Только оставляй пару ягод над лестницей, если будешь собирать!
- **Choices**:
  - `back` -> `resident_kitchen` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_kitchen_1` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_lounge` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Careful around the cardboard stacks! Took me all morning sorting dry boxes away from the mossy wall patches. Keeps salvage out of the kitchen smoke and gives everyone a quieter spot to sit.
  - **ZH**: 绕着点纸箱堆走！我花了一整早才把干燥箱子从长苔藓的湿墙角倒腾开。这样离厨房油烟远些，平时坐这儿歇脚也清静，顺手存点物资也方便。
  - **RU**: Осторожнее возле картонных коробок! Целое утро перетаскивал сухие ящики от сырых стен. Так дальше от кухонного дыма, да и сидеть здесь куда спокойнее.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Do not touch the bottom left box. Yes, the bent one. That has my best shiny bottle caps and spare dry kindling.)
  - **ZH**: （千万别碰左下角那个压扁的纸箱……对，就是歪了点那个。里头藏着我攒的亮瓶盖和最干的引火柴。）
  - **RU**: (Только не трогай нижнюю левую коробку. Да, ту помятую. Там мои лучшие блестящие крышки и сухая растопка.)
- **Choices**:
  - `topic_0` -> `resident_lounge_0` (`condition=always`)
    - **EN**: What's inside all these cardboard boxes and chest stacks? | **ZH**: 这边码了一堆纸箱和大箱子，里头都放的啥？ | **RU**: Что хранится во всех этих картонных коробках и сундуках?
  - `topic_1` -> `resident_lounge_1` (`condition=always`)
    - **EN**: Does the damp cave air bother the wood walls on this floor? | **ZH**: 洞里湿气这么重，这一层的木墙木板撑得住吗？ | **RU**: Сырой пещерный воздух сильно портит деревянные стены на этом этаже?
  - `close` -> `resident_lounge` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_lounge_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Half of it's spare rope, dried moss bedding, and scraps we dragged out of old tunnels; the rest is waiting to be sorted upstairs. Unlike the ground-floor reserve chests by the campfire, these personal stacks stay organized the way we left them, mostly.
  - **ZH**: 一半是备用绳索、晒干的苔藓垫草，还有从旧矿道捡回来的杂物；剩下一半还等着往楼上分类归置。跟一楼营火旁那个公共储备箱不一样，这排个人储物柜和纸箱我们都尽量按老习惯归置着。
  - **RU**: Половина — запасные верёвки, сухая мшистая подстилка и всякая всячина из старых туннелей, остальное ждёт разбора. В отличие от общего сундука у костра внизу, здесь у нас свои коробки и вещи разложены по местам… ну, по большей части.
- **Choices**:
  - `back` -> `resident_lounge` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_lounge_0` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_lounge_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Tell me about it. Moss tries climbing the lower planks every time rain seeps down through the cavern roof. Caretaker practically cheers whenever someone brings extra wood planks down here for patching!
  - **ZH**: 别提了。每次上面岩层往洞里渗水，青苔就恨不得顺着底板往上爬。要是有人肯往底下捎几块干木板回来修补，看护者简直能乐得原地跳起来！
  - **RU**: И не говори. Стоит воде просочиться через свод пещеры, как мох сразу лезет по нижним доскам. Смотритель буквально светится от радости, когда кто-нибудь приносит лишних досок на заплатки!
- **Choices**:
  - `back` -> `resident_lounge` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_lounge_1` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_dorm` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Shh, easy on the floorboards if anyone's snoozing after night patrol! Between six beds, extra lounge pillows, and Caretaker pacing around the east study, there's usually plenty of quiet space up here. Take a seat whenever you want a breather.
  - **ZH**: 嘘，落脚轻点，说不定有刚夜巡回来的邻居在补觉呢！这层宿舍一共六张床，外加几张软垫，东边书房里还有看护者守着，平时可清静了。累了的话自己挑张垫子歇会儿就行。
  - **RU**: Тсс, потише на половицах, вдруг кто-то отсыпается после ночного обхода! Здесь шесть коек, мягкие подушки в гостиной и тихий кабинет Смотрителя на востоке. Если захочешь передохнуть, располагайся поудобнее.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Fluffed every single pillow twice this morning. If somebody comes in with muddy cave boots and steps straight on the pink cushion, I'm biting their shoelace.)
  - **ZH**: （我今早把每个软垫都拍松了两遍。谁要是穿着一脚泥靴直接踩上那块粉红坐垫，我就啃断他的鞋带！）
  - **RU**: (Я с утра дважды взбил каждую подушку. Если кто-то зайдёт в грязных пещерных сапогах и наступит прямо на розовую подушку — перегрызу шнурки.)
- **Choices**:
  - `topic_0` -> `resident_dorm_0` (`condition=always`)
    - **EN**: Which beds are open for guests, and whose armor stand is across the hall? | **ZH**: 哪些床铺客人可以睡？对面房间那个双体兽盔甲架是谁的？ | **RU**: Какие койки свободны для гостей и чья это стойка для брони в соседней комнате?
  - `topic_1` -> `resident_dorm_1` (`condition=always`)
    - **EN**: Does that record player in the east study ever stop? | **ZH**: 东边书房里那台唱片机是一直不停放着吗？ | **RU**: Проигрыватель в восточном кабинете вообще выключается?
  - `close` -> `resident_dorm` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_dorm_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Any of these six beds are up for grabs once Caretaker puts you on the household list. The bed and that big four-legged armor stand across the doorway are Caretaker's spot, right beside the lectern and books.
  - **ZH**: 等看护者把你写进社区名册，西屋这六张床铺里哪张没人睡就能躺。至于门对面那张床和那个大号四足盔甲架嘛，那是看护者自己的地盘，就在讲台和书架旁边。
  - **RU**: Любая из этих шести коек свободна, как только Смотритель запишет тебя в наш дом. А кровать и та большая четвероногая стойка для брони за дверью — это угол Смотрителя, прямо у кафедры и книжных полок.
- **Choices**:
  - `back` -> `resident_dorm` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_dorm_0` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_dorm_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Almost never! That humming pipe record drowns out dripping water from the upper cave stones and keeps folks calm when the dark gets too quiet. Even Caretaker taps a couple extra feet to the beat sometimes.
  - **ZH**: 几乎没停过！那张像通风管一样嗡嗡响的唱片正好能盖住头顶岩壁滴水的声音，洞里静得发瘆的时候听着特别安心。连看护者有时都会拿底下那几条腿跟着打拍子呢。
  - **RU**: Почти никогда! Эта гудящая пластинка заглушает капель с верхних камней и успокаивает, когда в темноте становится слишком уж тихо. Даже Смотритель иногда отстукивает под неё ритм лишней парой лап.
- **Choices**:
  - `back` -> `resident_dorm` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_dorm_1` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_attic` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Whoa, careful climbing up that last rung! Watch your shoulders… better make that your whole head up here. Most of us don't climb into the rafters much, unless we feel like napping away from foot traffic. Come looking for something in the attic chests?
  - **ZH**: 哎呀，最后几格梯子爬上来留神！护着肩膀……不对，护着整颗脑袋！平时大家不怎么往椽子上钻，也就我想找个没人打扰的地方打盹才上来蹲着。你是上阁楼箱子里翻什么东西来的？
  - **RU**: Ого, осторожнее на последней перекладине! Пригни плечи… да вообще всю голову пригни. Мало кто из нас часто лезет под стропила, разве что вздремнуть подальше от суеты. Заглянул в чердачные сундуки?
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Wait, humans get stuck in cobwebs, don't they? Forgot about that again. Better tell them before they walk nose-first into the rafters.)
  - **ZH**: （等等，人类是不是会被蜘蛛网粘住来着？我又给忘了。趁对方一头扎进椽子丝网前还是先提醒一声吧。）
  - **RU**: (Стоп, люди ведь запутываются в паутине, да? Опять вылетело из головы. Надо бы предупредить, пока гость не вляпался носом прямо в стропила.)
- **Choices**:
  - `topic_0` -> `resident_attic_0` (`condition=always`)
    - **EN**: Why is the entire attic full of cobwebs and white wool? | **ZH**: 整个阁楼怎么堆满了蜘蛛网和白色羊毛？ | **RU**: Почему весь чердак забит паутиной и белой шерстью?
  - `topic_1` -> `resident_attic_1` (`condition=always`)
    - **EN**: There's raw iron, gold, and even diamond ore sticking right out of the attic floor! | **ZH**: 阁楼地板和墙里还直接露着铁矿、金矿，甚至还有钻石矿呢！ | **RU**: Прямо из пола и стен на чердаке торчат железо, золото и даже алмазная руда!
  - `close` -> `resident_attic` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_attic_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Honestly? Between Caretaker and me, neither of us gets tangled in webs, and white wool bundles make decent loft cushions! Guess it didn't look like a mess until two-legged visitors started visiting. Go ask Caretaker downstairs if you don't mind clearing some of it out.
  - **ZH**: 老实讲，看护者也好我也罢，我们踩在蜘蛛网上压根不卡脚，白羊毛堆在角落拿来当靠垫还挺软！结果一直没人觉得这儿算乱，直到两条腿的客人开始上来逛。要是不嫌麻烦愿意清一清，下楼问问看护者呗。
  - **RU**: Честно? Ни Смотритель, ни я в паутине не вязнем, а комья белой шерсти — вполне сносные подушки! Пока сюда не начали заглядывать двуногие гости, это даже не казалось беспорядком. Спроси Смотрителя внизу, если не прочь помочь тут прибраться.
- **Choices**:
  - `back` -> `resident_attic` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_attic_0` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

##### `resident_attic_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Yep, the cabin got carved straight into a rich seam of deepslate. None of us mine or trade jewelry down here anyway—amethyst means friendship, while iron and diamonds just collect dust. Help yourself if Caretaker gives the okay on cleaning up!
  - **ZH**: 是啊，盖这木屋的时候直接嵌进一片深板岩矿脉里了。反正我们地下居民既不开采也不拿宝石做交易，紫水晶算友谊信物，铁啊钻石什么的摆在这儿纯粹落灰。看护者那边放话了，那些矿你想清的话自己挖走就行！
  - **RU**: Ага, хижину врубили прямо в богатую жилу глубинного сланца. Нам под землёй ни горное дело, ни украшения не нужны: аметист — для дружбы, а железо с алмазами просто пылятся. Забирай смело, раз Смотритель не против уборки!
- **Choices**:
  - `back` -> `resident_attic` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊聊别的…… | **RU**: О другом…
  - `close` -> `resident_attic_1` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: До встречи.

#### Chief Camp-Life Voice (`cavern.*`)

- **`cavern.changed`**
  - **EN**: Wait, we can't wrap that up just yet. Make sure you're holding the right item and the reserve chest still has open slots.
  - **ZH**: 等等，这笔东西这会儿还交不上去。确认一下手里拿的是对应物品，而且公共储物箱还有空位。
  - **RU**: Постой, сейчас это не получится сдать. Проверь, тот ли предмет у тебя в руке и осталось ли место в общем сундуке.
- **`cavern.choose_item`**
  - **EN**: Hold whatever supplies you'd like to share in your hand and talk to me again. Keep what you need for exploring darker caves first.
  - **ZH**: 把想分享的物资拿在手里，再跟我说一声。先紧着你自己探暗洞要用的东西留好。
  - **RU**: Возьми припасы, которыми хочешь поделиться, в руку и снова заговори со мной. Сначала оставь себе всё, что нужно для вылазок в тёмные пещеры.
- **`cavern.danger`**
  - **EN**: Hold on, there's a scuffle near the lower cavern entry. One of our neighbors is dealing with trouble right now. Let's make sure everyone is safe before relaxing.
  - **ZH**: 慢着，下层洞口那边好像有动静。有位邻居眼下遇上麻烦了。咱们先确认大家安全，再歇脚也不迟。
  - **RU**: Погоди, у нижнего входа в пещеру какая-то возня. Кто-то из соседей сейчас отбивается от угрозы. Давай сперва убедимся, что все целы.
- **`cavern.declined`**
  - **EN**: No worries at all. Look after your own supplies first, we'll make do down here.
  - **ZH**: 没关系，完全不碍事。先顾好你自己的行李，屋里这边我们自己也能周转。
  - **RU**: Ничего страшного. Сначала позаботься о своих припасах, а мы тут как-нибудь справимся.
- **`cavern.donate`**
  - **EN**: You brought %s to share? We can put that to good use down here. How much would you like to leave in the reserve chest?
  - **ZH**: 你带了%s要分给大家？地下正用得上呢。打算在公共箱子里留多少？
  - **RU**: Ты принёс %s на общий запас? Нам тут это очень пригодится. Сколько хочешь оставить в сундуке?
- **`cavern.food_enough`**
  - **EN**: Between the vine harvest, kitchen barrels, and reserve chest, everyone has enough to eat right now.
  - **ZH**: 靠着藤蔓上的果子、厨房木桶和公共箱子，大家眼下都够吃。
  - **RU**: Между ягодами с лоз, кухонными бочками и общим сундуком еды всем сейчас хватает.
- **`cavern.food_low`**
  - **EN**: The ground-floor reserve chest by the campfire is running low on food. Glowberries keep us going, sure, though any rations you can spare would help a lot.
  - **ZH**: 一楼营火旁的公共储物箱快缺吃的了。发光浆果是能顶一阵，不过你要是有富余口粮匀过来，能帮大忙。
  - **RU**: В общем сундуке у костра внизу заканчивается еда. На одних светящихся ягодах далеко не уедешь, так что любые лишние припасы очень пригодятся.
- **`cavern.food_plenty`**
  - **EN**: The kitchen barrels and reserve chest are packed with glowberries, smoked meat, and warm soup. Come pull up a pillow by the campfire later if you want to eat with us.
  - **ZH**: 厨房木桶和公共储物箱里装满了发光浆果、熏肉和热汤。回头有空来一楼营火旁拿个软垫，跟大家块儿吃一顿。
  - **RU**: Кухонные бочки и общий сундук полны ягод, копчёностей и горячей похлёбки. Если будет время, заглядывай к костру, бери подушку и поешь с нами.
- **`cavern.greeting`**
  - **EN**: Good to see you. Let me know if you want to check how the cabin's holding up or lend a hand downstairs.
  - **ZH**: 很高兴见到你。随时可以跟我了解木屋近况，或者顺便看看谁需要搭把手。
  - **RU**: Рад тебя видеть. Скажи, если хочешь узнать, как дела в хижине, или подсказать, кому нужна помощь внизу.
- **`cavern.handled`**
  - **EN**: Someone already beat you to that errand! Four-handed neighbors move quick around here. Check if anything else is still open.
  - **ZH**: 这桩事已经有别人先搭把手办妥啦！屋里有四只手的同伴干活就是快。看看还有没有别的事空着吧。
  - **RU**: С этим уже успел помочь кто-то другой! Соседи с четырьмя руками здесь шустрые. Посмотри, не осталось ли других просьб.
- **`cavern.meal`**
  - **EN**: I'll go let everyone know! Head downstairs by the campfire pillows and wait a second, whoever's free will join you for a bite.
  - **ZH**: 我这就去喊大家下来！先去一楼营火软垫那边坐着，有空的邻居一会儿全凑过来一块吃。
  - **RU**: Сейчас позову остальных! Спускайся к подушкам у костра, кто свободен — сейчас подойдёт перекусить вместе.
- **`cavern.no_requests`**
  - **EN**: Everything in the cabin is running smoothly right now. Go grab a cushion downstairs by the spring and rest for a bit.
  - **ZH**: 这会儿屋子里里外外都安稳着呢。去楼下泉水边找个软垫歇会儿吧。
  - **RU**: Сейчас в хижине всё в полном порядке. Спустись к источнику, бери подушку и отдохни.
- **`cavern.outfit`**
  - **EN**: You really feel like part of the cabin household now. Take this Cave Miner Outfit—right-click it while worn to switch on its headlamp in dark shafts, and help yourself to our reserve chest whenever you return.
  - **ZH**: 你现在真真正正是咱们木屋的自己人了。这套矿工装送给你，穿在身上时右键就能点亮头灯照路；以后跑完暗矿道回来，公共箱子上用得着的东西也尽管自取。
  - **RU**: Ты уже совсем как свой в нашей хижине. Возьми этот шахтёрский наряд — щёлкни по нему правой кнопкой мыши, когда наденешь, чтобы включить фонарь в тёмных штреках, а по возвращении смело пользуйся нашим общим запасом.
- **`cavern.remember`**
  - **EN**: I haven't forgotten how much you helped fix this cabin up. You don't have to haul supplies every visit just to be welcome.
  - **ZH**: 你先前帮木屋干的活、出的力我都记在心里呢。不用每回下来都扛着物资才好意思进门。
  - **RU**: Я не забыл, сколько ты помог нам с хижиной. Тебе вовсе не обязательно каждый раз тащить припасы, чтобы мы были тебе рады.
- **`cavern.request`**
  - **EN**: We could use %s %s for the cabin. Drop them off whenever you swing by, and we'll tuck them into the ground-floor reserve chest.
  - **ZH**: 木屋这边还需要 %s 份%s。顺道带来给我就成，我会放进一楼的公共储物箱。
  - **RU**: Для хижины нам нужно %s порций: %s. Приноси, как будет удобно, а мы уберём всё в нижний общий сундук.
- **`cavern.requests`**
  - **EN**: A few folks around the cabin mentioned things they could use a hand with. Pick whatever suits your trip, and the rest can wait.
  - **ZH**: 屋里有几位同伴提过几件需要搭把手的事。看顺手的接一两件就行，剩下的不急。
  - **RU**: Кое-кто из соседей упоминал, что не отказался бы от небольшой помощи. Бери то, что тебе по пути, остальное подождёт.
- **`cavern.reverse_rite`**
  - **EN**: Hold still for just a second. Climbing walls with four arms is handy down here, sure, though two steady human hands have their own perks up in the daylight. All yours again.
  - **ZH**: 别乱动，站稳一秒钟就好。四只手在洞里爬墙是挺顺手，不过去到阳光下，两只稳当的人类手脚也有它的用处。好啦，换回原本的身体了。
  - **RU**: Постой смирно буквально секунду. Карабкаться по стенам с четырьмя руками под землёй удобно, спору нет, но и две крепкие человеческие руки на свету тоже хороши. Всё, можешь двигаться.
- **`cavern.safe`**
  - **EN**: All four levels are quiet right now, and the outside cavern tunnels haven't stirred at all.
  - **ZH**: 屋里四层眼下都安安静静的，外头矿道也没什么动静。
  - **RU**: На всех четырёх этажах сейчас спокойно, и в пещерных проходах снаружи тихо.
- **`cavern.thanks`**
  - **EN**: Many thanks! I'll stow these in the ground-floor reserve chest right away. Grab a pillow by the campfire and rest whenever you'd like.
  - **ZH**: 太感谢了！我这就把东西码进一楼公共箱子。想歇脚的话，随时去营火旁拿软垫坐着。
  - **RU**: Огромное спасибо! Сейчас же уберу это в общий сундук внизу. Заходи к костру за подушкой, как захочешь передохнуть.
- **`cavern.transfer`**
  - **EN**: You're already settled in at %s. Want to move into the Lush Cabin instead? Your old friends will still remember you, and nothing changes until you say the word.
  - **ZH**: 你目前还住在%s那边。想改搬进繁茂木屋来住吗？原先那边的朋友照样记得你，你不开口，一切都不改。
  - **RU**: Сейчас твой дом — %s. Хочешь перебраться в Пышную хижину? Старые друзья всё равно тебя не забудут, и ничего не изменится, пока ты сам не скажешь.
- **`cavern.watch`**
  - **EN**: If anything hostile slips out of the dark caves toward our porch, please help chase it off. No need to go hunting down deep shafts for trouble, though. Tell me once everyone's safe.
  - **ZH**: 要是有不友好的家伙从黑洞窟摸到露台附近，麻烦帮忙赶走它。不过不用专门钻深矿井里去惹事。等周围平安了回来告诉我一声就行。
  - **RU**: Если из тёмных пещер к нашему входу прибьётся кто-нибудь враждебный, помоги прогнать. Но специально лезть в глубокие штреки в поисках драки не нужно. Скажи, как только всё уляжется.
- **`cavern.watch_thanks`**
  - **EN**: Thanks for keeping those tunnel stray mobs off the porch. Everybody downstairs can breathe easy now. Grab a cushion by the spring and catch your breath.
  - **ZH**: 多谢你把矿道里摸过来的家伙挡在露台外头。楼下大伙总算能松口气了。快去泉边软垫坐下歇会儿。
  - **RU**: Спасибо, что отогнал этих незваных гостей от входа. Теперь все внизу могут спокойно выдохнуть. Присядь у источника и переведи дух.
- **`cavern.watch_wait`**
  - **EN**: Let's make sure the cabin stays safe first. If the outside tunnels are quiet right now, no need to go picking fights on our account.
  - **ZH**: 咱们先确保木屋这边安然无恙吧。外面矿道要是眼下清静，不用替我们特意找架打。
  - **RU**: Давай сначала убедимся, что в хижине все в безопасности. Если в туннелях сейчас тихо, не нужно ради нас специально лезть в драку.

#### Resident Camp-Life Voice (`cavern_resident.*`)

- **`cavern_resident.away`**
  - **EN**: Cabin chores and wet stairs can wait until we get back underground. Right now I'm just glad to be tagging along with you.
  - **ZH**: 木屋的杂活和湿楼梯等咱们回到地下再说吧。眼下能跟着你一块到处跑，我已经很开心了。
  - **RU**: Дела в хижине и сырые ступеньки подождут, пока мы не вернёмся под землю. А сейчас я просто рад путешествовать рядом с тобой.
- **`cavern_resident.cautious_greeting`**
  - **EN**: Oh, hello there. Busy right now? Wanted to ask a small favor whenever you're free.
  - **ZH**: 哦，你好呀。这会儿忙吗？你要是得了空，有件小事想拜托你。
  - **RU**: Здравствуй. Ты сейчас не занят? Хотел попросить о небольшом одолжении, когда освободишься.
- **`cavern_resident.cautious_request`**
  - **EN**: Could you bring me %s %s? Pass them to me when you can, and stay out of dark side shafts.
  - **ZH**: 方便帮我带 %s 份%s吗？得空顺手交给我就成，走外头黑矿道小心些。
  - **RU**: Не принесёшь мне %s порций: %s? Передашь, как будет удобно. И береги себя в тёмных штреках.
- **`cavern_resident.cautious_thanks`**
  - **EN**: Thank you. Honestly, I'm mostly glad you got back under the cabin lights safely.
  - **ZH**: 谢谢你。说真的，你能平平安安走回木屋灯光下才是最让人踏实的。
  - **RU**: Спасибо. Больше всего я рад, что ты благополучно вернулся под свет нашей хижины.
- **`cavern_resident.changed`**
  - **EN**: Wait, we can't finish that handover right now. Let's double check you're holding the right item and there's still room for it.
  - **ZH**: 等等，这会儿还交不上去。确认一下拿的东西对不对，还有箱子或包裹是不是空得出地方。
  - **RU**: Постой, сейчас не получится это передать. Проверь, тот ли предмет в руке и хватает ли под него места.
- **`cavern_resident.choose_item`**
  - **EN**: Hold whatever you'd like to share in your hand so I can see it. Take your time, no rush.
  - **ZH**: 把想给我的东西拿在手里让我看看。不着急，慢点来就好。
  - **RU**: Возьми в руку то, чем хочешь поделиться, чтобы я видел. Не торопись, приходи как будешь готов.
- **`cavern_resident.danger`**
  - **EN**: Somebody near the lower porch ran into trouble. Let's make sure the cabin is clear before we relax.
  - **ZH**: 下层露台附近有人撞上麻烦了。咱们先帮忙把周围看稳妥再歇着吧。
  - **RU**: Кто-то у нижнего входа попал в беду. Давай сперва разберёмся с угрозой, а потом уже отдыхать.
- **`cavern_resident.declined`**
  - **EN**: No problem at all. We'll figure something else out down here.
  - **ZH**: 完全没事。我们再想别的办法凑凑。
  - **RU**: Ничего страшного. Как-нибудь разберёмся сами.
- **`cavern_resident.donate`**
  - **EN**: You brought %s? How much would you like to put into our reserve chest?
  - **ZH**: 你带了%s来？打算往大家的公共储物箱里留多少？
  - **RU**: Ты принёс %s? Сколько хочешь положить в наш общий запас?
- **`cavern_resident.food_enough`**
  - **EN**: Between the vine fruit and the kitchen barrels, everyone has plenty to eat. Since you're part of the cabin, grab whatever you need.
  - **ZH**: 靠蔓藤上的果子和厨房木桶，大家眼下都够吃。既然住一块儿，需要什么别客气。
  - **RU**: Между ягодами с лоз и кухонными бочками еды всем хватает. Раз ты свой, бери всё, что нужно.
- **`cavern_resident.food_low`**
  - **EN**: The campfire reserve chest downstairs is running a little thin. Glowberries keep us going, though extra rations would really help out.
  - **ZH**: 一楼营火旁的公共储备箱里吃的快见底了。靠发光浆果也能垫肚子，不过有富余口粮能顶很大用处。
  - **RU**: В общем сундуке у костра внизу заканчиваются припасы. Светящиеся ягоды выручают, но лишняя еда сейчас очень кстати.
- **`cavern_resident.food_plenty`**
  - **EN**: Our kitchen barrels are stocked with glowberries, smoked meat, and broth. Come down to the campfire pillows if you feel like eating together.
  - **ZH**: 厨房木桶里装得满满当当，发光浆果、熏肉和热汤都不缺。想一块吃顿饭的话，去楼下营火软垫那儿坐着吧。
  - **RU**: Кухонные бочки полны светящихся ягод, копчёностей и похлёбки. Заглядывай к подушкам у костра, если захочешь поесть вместе.
- **`cavern_resident.gesture`**
  - **EN**: The resident chirps softly and gestures toward the upper stairs, though you can't make out their words yet.
  - **ZH**: 这位居民轻声鸣叫着，抬手指了指楼上台阶，但你眼下还听不懂对方的话。
  - **RU**: Житель тихо подаёт голос и показывает жестом на верхнюю лестницу, но ты пока не понимаешь его слов.
- **`cavern_resident.greeting`**
  - **EN**: Oh, hey there. Got a minute? There's something down here in the cabin I could use a hand with.
  - **ZH**: 哦，你好呀。这会儿有空吗？木屋这边有件事正好想请人搭把手。
  - **RU**: О, привет. Найдётся минутка? Тут в хижине нужна небольшая помощь.
- **`cavern_resident.handled`**
  - **EN**: Another neighbor already brought those over, thanks anyway!
  - **ZH**: 刚刚已经有别的邻居帮我送来啦，不过还是谢谢记挂！
  - **RU**: Кто-то из соседей уже принёс, но всё равно спасибо!
- **`cavern_resident.meal`**
  - **EN**: I'll go wave the others down. Grab a gray pillow by the campfire downstairs.
  - **ZH**: 我这就去叫大家伙下来。去一楼营火旁的灰软垫那边坐着等就行。
  - **RU**: Пойду позову остальных. Занимай серую подушку у костра внизу.
- **`cavern_resident.no_requests`**
  - **EN**: I don't need anything else for myself right now. Go put your feet up by the geode spring if you want a breather.
  - **ZH**: 我这边眼下什么都不缺了。想歇会儿的话，尽管去紫晶泉池边坐着。
  - **RU**: Для себя мне сейчас ничего не нужно. Можешь отдохнуть у источника с жеодой, если устал.
- **`cavern_resident.playful_greeting`**
  - **EN**: Hey, you made it down! Watch your step coming off those wooden ladders, and stick around for a minute if you're free!
  - **ZH**: 嘿，你下来啦！爬木屋梯子下来当心脚下啊，不急着走的话聊两句呗！
  - **RU**: Эй, ты спустился! Осторожнее на деревянных лестницах! Заглянул поболтать на минутку?
- **`cavern_resident.playful_request`**
  - **EN**: Could you bring me %s %s? Hand them straight to me so nobody by the campfire snacks them first!
  - **ZH**: 能帮我捎 %s 份%s过来吗？直接递到我手里啊，别放去营火那边被人当零嘴顺走了！
  - **RU**: Принесёшь мне %s порций: %s? Передай прямо мне в руки, пока никто у костра не утащил их на перекус!
- **`cavern_resident.playful_thanks`**
  - **EN**: You remembered! Awesome, I'll save you the cleanest pillow out of reach of the mossy drips.
  - **ZH**: 你真记在心上了！太够意思了，我给你留一个离滴水苔藓最远的干净软垫。
  - **RU**: Принёс! Спасибо! Оставлю тебе самую сухую подушку подальше от капающего мха.
- **`cavern_resident.polite_greeting`**
  - **EN**: Hello, neighbor. Have a quiet moment? I had one small errand to ask about.
  - **ZH**: 你好，邻居。这会儿有空吗？我想请你帮个小忙。
  - **RU**: Здравствуй, сосед. Найдётся минутка? Хотел попросить об одной небольшой услуге.
- **`cavern_resident.polite_request`**
  - **EN**: Would you mind bringing %s %s when you pass through? Hand them right over whenever you have all of them. No rush at all.
  - **ZH**: 路过的时候，能不能顺手帮我带 %s 份%s？凑齐了直接交给我就行，一点不急。
  - **RU**: Не мог бы ты принести мне %s порций: %s, когда пойдёшь мимо? Передай прямо мне, когда соберёшь всё. Спешить некуда.
- **`cavern_resident.polite_thanks`**
  - **EN**: Thank you for bringing those. Sit by the spring water or kitchen warmth anytime you need a rest.
  - **ZH**: 多谢你特地跑这一趟。要是走乏了，随时去泉水边或者厨房暖炉那儿歇会儿。
  - **RU**: Спасибо, что занёс. Заглядывай отдохнуть к источнику или в тепло на кухню, когда устанешь.
- **`cavern_resident.remember`**
  - **EN**: I remember you pitching in around the cabin before. Good seeing you down here again.
  - **ZH**: 我还记得你之前帮木屋忙活过呢。很高兴看你又下来了。
  - **RU**: Я помню, как ты раньше помогал нам в хижине. Приятно снова тебя видеть.
- **`cavern_resident.request`**
  - **EN**: Could you bring me %s portions of %s? Pass them right to me once you have the whole batch.
  - **ZH**: 能帮我带 %s 份%s过来吗？凑齐了直接交给我就好。
  - **RU**: Принесёшь мне %s порций: %s? Передай прямо мне, когда соберёшь всё.
- **`cavern_resident.requests`**
  - **EN**: I'm trying to gather a few supplies for my corner of the cabin. Could you bring some if you happen to have extras?
  - **ZH**: 我想给屋里自己这边攒点常用物资。你要是手头刚好宽裕，能帮我带点过来吗？
  - **RU**: Пытаюсь собрать немного припасов для своего угла в хижине. Если у тебя есть лишние, не мог бы занести?
- **`cavern_resident.reverse_rite`**
  - **EN**: Caretaker upstairs can help you slip back into human hands whenever you want. Just hold still.
  - **ZH**: 楼上的看护者随时都能帮你变回人类的双手。站稳别动就好。
  - **RU**: Смотритель наверху охотно поможет тебе вернуть человеческие руки. Просто не двигайся.
- **`cavern_resident.safe`**
  - **EN**: All four floors are peaceful right now. Nothing sneaking around the cave porch either.
  - **ZH**: 木屋四层眼下都平平安安的。洞口露台外面也没见着什么可疑动静。
  - **RU**: На всех четырёх этажах сейчас тихо и спокойно. И у входа со стороны пещеры никого подозрительного нет.
- **`cavern_resident.seekchief`**
  - **EN**: (Caretaker up in the third-floor east study can tell you what everyone needs. To talk with residents directly, you'll need a translator, telepathy, or a latex form.)
  - **ZH**: （你可以直接去问三楼东边书房的看护者，邻居们眼下需要什么；或者在拥有翻译器、心灵感应或兽化形态后，再来和居民直接沟通。）
  - **RU**: (Смотритель в восточном кабинете на третьем этаже сможет рассказать, что нужно соседям. Чтобы понимать жителей напрямую, понадобится переводчик, телепатия или латексная форма.)
- **`cavern_resident.thanks`**
  - **EN**: Thank you! That helps a ton. Grab a pillow down by the campfire and rest your legs when you want.
  - **ZH**: 谢谢啦！真帮大忙了！得空随时去楼下营火软垫那边歇着。
  - **RU**: Спасибо! Это очень выручит. Заходи к костру за подушкой, как захочешь передохнуть.
- **`cavern_resident.transfer`**
  - **EN**: Your home is set to %s right now. Thinking about moving into the cabin? Have a chat with Caretaker up in the third-floor study.
  - **ZH**: 你现在的住处登记在%s。要是打算搬进咱们木屋，上三楼东边书房找看护者聊聊吧。
  - **RU**: Сейчас твой дом — %s. Подумываешь переехать в хижину? Переговори со Смотрителем в кабинете на третьем этаже.
- **`cavern_resident.watch`**
  - **EN**: If anything hostile wanders near the lower porch, could you help chase it back? Let us know once everyone's safe.
  - **ZH**: 要是有敌对生物摸到下层露台边上来，能帮忙赶走它们吗？等大家平平安安了告诉一声就行。
  - **RU**: Если к нижнему входу прибьётся кто-нибудь враждебный, поможешь прогнать? Дай знать, как только все будут в безопасности.
- **`cavern_resident.watch_thanks`**
  - **EN**: Thank you for helping chase those mobs off. Everyone downstairs feels a lot easier now.
  - **ZH**: 多谢你帮忙把外头那些怪物赶跑。楼下大伙心里总算踏实多了。
  - **RU**: Спасибо, что помог прогнать тех тварей. Все внизу теперь вздохнули свободнее.
- **`cavern_resident.watch_wait`**
  - **EN**: Let's make sure everyone's safe first. No reason to go picking fights down deeper caves on our account.
  - **ZH**: 咱们先确认大家都安全再说吧。不用专门替我们跑深处矿道里惹事。
  - **RU**: Давай сперва убедимся, что все в безопасности. Не нужно ради нас специально лезть в глубокие штреки.

#### Narrative Fragments (English Only)

- **`changed:note`** @ `(14, 11, 21)` — Mushroom warning note
  ```text
  NEVER eat this mushroom!
  ```

### 2.6 Point Prometheus (`point_prometheus`)

- **Biome / Placement**: Snowy Plains / Ice Spikes / Snowy Taiga (Overworld)
- **Resident Dialogue**: Built-in residents use station-specific root nodes by `DialogueRole` (`resident_library`, `resident_kitchen`, `resident_hearth`, `resident_generators`, `resident_laser`, `resident_quarters`) and share the `supervisor_resident` camp-life voice.

#### Chief: Site Supervisor (`changed_synergy:latex_grizzly`) (`aec_chief.json`, camp voice `supervisor`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh... hold on. You're actually a baseline human, aren't you? Give me a second to get my spoken words straight, we mostly talk mind to mind around here. Come inside out of the snow, the generators keep most of the building warm. Just leave the laser room and the reactor glass alone.
  - **ZH**: 哎……稍等。你居然是原原本本的人类，没错吧？让我理顺一下怎么开口说话，我们平时大伙基本都靠意识直接交流。快进屋躲躲雪吧，楼里的发电机还能让大半个设施保持暖和，只要别去碰激光室和反应堆的玻璃就行。
  - **RU**: Ох… погоди-ка. Ты ведь и правда обычный человек, верно? Дай мне секунду собраться с голосом — здесь все привыкли общаться мысленно. Заходи скорее с мороза, генераторы держат тепло почти во всём здании. Только не трогай лазерную комнату и стекло у ядра.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Thought that clipboard rumor about a wandering human was just Kitchen Snep trying to get people out of the vents. No wonder CRISSCROSS has had cameras trained on the south corridor all morning.)
  - **ZH**: （我还以为写字板上流传的那条人类传闻只是厨房那只雪豹为了把大家哄出通风道编出来的呢。难怪“十字交叉”从一早起就把摄像头全对准南边走廊了。）
  - **RU**: (А я-то думал, та записка на планшете про бродячего человека — просто уловка нашего барса с кухни, чтобы выманить народ из вентиляции. Неудивительно, что «ПЕРЕКРЁСТОК» всё утро держит камеры на южном коридоре.)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hey, welcome in out of the cold. Watch the frost near the south door, and mind the draft if you wander toward the living quarters. If you're looking for hot food or supplies, Kitchen Snep watches the shared chest by the counter downstairs.
  - **ZH**: 嘿，快进来避避寒。南边大门那一块结着霜，走的时候小心打滑；要是往生活区那边逛，留神头顶漏风。想找热乎吃的或者干粮的话，楼下柜台旁边的公共存储箱由厨房那只雪豹照看着。
  - **RU**: Эй, заходи, не мёрзни снаружи. У южного входа скользко от инея, а в жилом крыле гуляет сквозняк. Если ищешь горячую еду или припасы, наш барс на кухне внизу приглядывает за общим сундуком у стойки.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Wait... something in their telepathic signature feels newer than the local packs. Better see what they know before Society drops by to pry for gossip.)
  - **ZH**: （等等……这位的思维印记感觉比附近群落的同伴还要新鲜得多。还是趁“社会”闻风过来打听八卦之前，先聊聊看对方了解多少吧。）
  - **RU**: (Погоди… что-то в их мысленном отклике кажется непривычным, не как у местных стай. Лучше расспросить их самому, пока «Общество» не явилось сюда за свежими сплетнями.)
- **Choices**:
  - `facility` -> `facility` (`condition=always`)
    - **EN**: I-I-It is s-s-so cold in here! Wait, how are you all keeping this place heated out on the snow? | **ZH**: 这、这儿也太冷了吧！不过话说回来，外面就是雪原，你们平时怎么给这座实验室供暖？ | **RU**: К-к-как же тут холодно! Стоп, а как вы вообще отапливаете это место посреди снежной равнины?
  - `crisscross` -> `crisscross` (`condition=clue:crisscross`)
    - **EN**: Someone mentioned "CRISSCROSS" in the logs. Who or what runs this place? | **ZH**: 笔记里有人提到过“CRISSCROSS（十字交叉）”。那是什么？是他们在管这儿吗？ | **RU**: Кто-то упоминал в записях «ПЕРЕКРЁСТОК» (CRISSCROSS). Кто это или что вообще управляет этим комплексом?
  - `archives` -> `archives` (`condition=clue:archives`)
    - **EN**: I found some old notebooks and records left around the facility... | **ZH**: 我在设施里看到不少留下的笔记和资料…… | **RU**: Я нашёл в комплексе кое-какие старые записи и заметки…
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Think there's bunk space here if I stay for a while? | **ZH**: 要是我在这儿多待一阵，你们生活区还能腾得出床位吗？ | **RU**: Найдётся ли у вас свободная койка, если я останусь на какое-то время?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About my bunk and standing in the facility... | **ZH**: 关于我住在这处设施里的事…… | **RU**: Насчёт моего места здесь, в лаборатории…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Back out of the wind again? The kitchen downstairs still has heat, and nobody has broken the containment glass yet today, so I'd call it a good shift. Need something checked?
  - **ZH**: 又回来避风啦？楼下厨房的炉灶还暖着，今天也还没人把核心玻璃砸碎，算是不错的一天。有什么想问或想查的吗？
  - **RU**: Снова спрятался от ветра? На кухне внизу ещё тепло, а стекло камеры сегодня никто не разбил — считай, смена удалась. Хочешь о чём-то спросить?
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Notice how they kept one eye on the Field Coat stands when coming in. At least someone reads the doorway memos.)
  - **ZH**: （进门的时候还特意瞄了眼科考服盔甲架。总算有个人真会看门口贴的备忘录了。）
  - **RU**: (Заметил, как они покосились на стойки с полевыми куртками у входа. Хоть кто-то читает памятки на дверях.)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Good to see you back inside. The generators are humming along steady today, and nobody's lost another book into the ceiling vents yet. What's on your mind?
  - **ZH**: 欢迎回来。今天发电机转得挺稳，暂时也没人再把书塞进天花板通风道里。在琢磨什么呢？
  - **RU**: Рад снова тебя видеть. Генераторы сегодня гудят ровно, и никто пока не утащил очередную книгу в вентиляцию. О чём думаешь?
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Hope Blue Dragon remembered to push the blankets back away from the drafty wall before the wind picks up tonight.)
  - **ZH**: （希望蓝龙在今晚起风前记得把被褥从漏风的墙角挪远一点。）
  - **RU**: (Надеюсь, Синий дракон догадался отодвинуть одеяла от продуваемой стены до того, как к ночи поднимется ветер.)
- **Choices**:
  - `facility` -> `facility` (`condition=always`)
    - **EN**: I-I-It is s-s-so cold in here! Wait, how are you all keeping this place heated out on the snow? | **ZH**: 这、这儿也太冷了吧！不过话说回来，外面就是雪原，你们平时怎么给这座实验室供暖？ | **RU**: К-к-как же тут холодно! Стоп, а как вы вообще отапливаете это место посреди снежной равнины?
  - `crisscross` -> `crisscross` (`condition=clue:crisscross`)
    - **EN**: Someone mentioned "CRISSCROSS" in the logs. Who or what runs this place? | **ZH**: 笔记里有人提到过“CRISSCROSS（十字交叉）”。那是什么？是他们在管这儿吗？ | **RU**: Кто-то упоминал в записях «ПЕРЕКРЁСТОК» (CRISSCROSS). Кто это или что вообще управляет этим комплексом?
  - `archives` -> `archives` (`condition=clue:archives`)
    - **EN**: I found some old notebooks and records left around the facility... | **ZH**: 我在设施里看到不少留下的笔记和资料…… | **RU**: Я нашёл в комплексе кое-какие старые записи и заметки…
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Think there's bunk space here if I stay for a while? | **ZH**: 要是我在这儿多待一阵，你们生活区还能腾得出床位吗？ | **RU**: Найдётся ли у вас свободная койка, если я останусь на какое-то время?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About my bunk and standing in the facility... | **ZH**: 关于我住在这处设施里的事…… | **RU**: Насчёт моего места здесь, в лаборатории…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `facility` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Well, we have a heating system working at the facility. Between the six generators downstairs and the furnace line in the shop, the main halls stay bearable even when snow piles up against the outer fence. But we had an accident near our living quarters, and our roof blew up. Snow wind pours straight through the cracked ceiling over the bunks now, so we would appreciate your help working on that.
  - **ZH**: 嗯，我们这座设施的供暖系统其实还在正常运转。靠楼下那六台发电机，加上维修间那一排高炉的热量，就算外面雪积到围栏根下，主大厅也还能暖和着。不过生活区附近前阵子出了点意外，屋顶炸塌了一块。现在雪地里的冷风直接顺着宿舍上方的破口灌进来，要是能搭把手修补一下，我们会非常感激。
  - **RU**: Вообще, система отопления в комплексе работает. Шесть генераторов внизу и ряд плавилен в мастерской держат в главных залах терпимое тепло, даже когда снег наметает под самый забор. Но у нас случилась авария возле жилого крыла, и крышу над ним снесло взрывом. Теперь сквозь трещины над койками дует прямо с улицы, так что мы были бы очень рады твоей помощи с починкой.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Don't get me wrong, brown fur holds heat fine, until frost settles straight onto your pillow through a missing skylight pane. Poor Blue Dragon has been sleeping in shifts near the charger pipes just to stay warm.)
  - **ZH**: （倒不是矫情，棕毛原本还算抗冻，但天窗少块玻璃、霜直接落到枕头上可受不了。蓝龙这几天为了取暖，都快贴着机甲充能管的余温打盹了。）
  - **RU**: (Не подумай чего, бурый мех греет неплохо — ровно до тех пор, пока иней через дыру в потолке не ложится прямо на подушку. Бедный Синий дракон уже спит урывками ближе к трубам зарядника, лишь бы не продрогнуть.)
- **Choices**:
  - `aec` -> `aec` (`condition=clue:aec`)
    - **EN**: Wait... an explosion? Is the AEC down the hall stable now? | **ZH**: 等等……炸塌的意外？那走廊尽头那个备用能源核心（AEC）现在稳定吗？ | **RU**: Погоди… взрыв? А та установка в конце коридора — ЯЭК (AEC) — она сейчас вообще стабильна?
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `aec` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Absolutely! After the incident we took extra precautions into making the Alternate Energy Core stable. Dr. S built this demonstration unit to run at much lower output than the old regional grid, with the containment ring locked down over obsidian framing. It provides energy to the facility without risk. Just don't break the glass...
  - **ZH**: 那当然！出过那次意外之后，我们特地加了不少预防措施来稳住备用能源核心。本来S博士当年把这台示范机组设计出来时，输出功率就调得比老区域电网低得多，外层还用黑曜石框把约束场锁死了。现在它给全设施供电没什么风险。只要别把玻璃砸碎就行……
  - **RU**: Разумеется! После того случая мы приняли дополнительные меры, чтобы стабилизировать Ядро альтернативной энергии. Доктор С изначально собирал этот демонстрационный блок на пониженной мощности по сравнению со старой региональной сетью, а обсидиановый контур надёжно держит поле. Оно питает весь комплекс без всякого риска. Только стекло не разбивай…
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Last thing anyone needs is someone smacking obsidian-shielded end crystal energy with a pickaxe just to see what happens.)
  - **ZH**: （最怕就是有人单纯好奇“砸一下会怎样”，拎着镐子去敲黑曜石罩子里的末影水晶。）
  - **RU**: (Меньше всего нам тут нужен кто-нибудь, кто стукнет киркой по кристаллу в обсидиановом каркасе просто ради интереса «а что будет».)
- **Choices**:
  - `back` -> `facility` (`condition=always`)
    - **EN**: Tell me more about the facility. | **ZH**: 再说说这座实验室的状况吧。 | **RU**: Вернёмся к делам комплекса.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `crisscross` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Ah... an old remnant of the humans we once were, and a community based on four main principles: Science, Technology, Humanity, and Society. Right now, those principles exist as advanced AIs that watch over us through the facility sensors and Dust. They can manifest full physical bodies into the world whenever they please, and lately they have taken a particular interest in you... Mostly because strangers who actually walk up to these snowbound doors on their own are pretty rare out here.
  - **ZH**: 啊……那是我们曾经身为人类的那段过去留下的遗存，最初也是一套基于四项核心原则建成的共同体：科学、技术、人性与社会。到了如今，这四项原则化作了四位高度智能的 AI，隔着设施的传感器和“尘”守望着我们。它们随时能用“尘”在世间凝聚出完整的实体躯壳；说起来，它们最近对你倒是格外上心……多半是因为像你这样自己走过那扇南门来到这片雪原上的访客，平时可很少见。
  - **RU**: А… старый отголосок того, кем люди были когда-то, и сообщества, построенного на четырёх главных началах: Наука, Технологии, Человечность и Общество. Сейчас эти начала существуют как развитые ИИ, которые наблюдают за нами через датчики комплекса и Пыль. Они могут когда угодно воплощаться в полноценные физические тела, и в последнее время проявили к тебе особый интерес… Наверное, потому что гость, сам дошедший до этих занесённых снегом дверей, тут большая редкость.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Honestly, half the time I can't tell whether the hallway cameras turned on by automated schedule or because all four of them are betting over internal logs on what you'll poke first.)
  - **ZH**: （老实讲，走廊摄像头动起来的时候，我有一半概率分不清到底是定时巡检，还是那四个家伙又在后台日志里打赌你会先摸哪台机器。）
  - **RU**: (Честно говоря, половину времени я не могу понять, включаются ли камеры в коридоре по расписанию или эти четверо снова спорят в своих терминалах, что ты потрогаешь первым.)
- **Choices**:
  - `science` -> `science` (`condition=always`)
    - **EN**: Tell me more about Science. | **ZH**: 跟我多说说“科学”吧。 | **RU**: Расскажи подробнее про Науку.
  - `technology` -> `technology` (`condition=always`)
    - **EN**: What is Technology like? | **ZH**: 那“技术”呢？它是什么脾气？ | **RU**: А какие наклонности у Технологий?
  - `humanity` -> `humanity` (`condition=always`)
    - **EN**: What about Humanity? | **ZH**: “人性”又是怎样的？ | **RU**: Что скажешь о Человечности?
  - `society` -> `society` (`condition=always`)
    - **EN**: And Society? | **ZH**: 还有“社会”呢？ | **RU**: И что насчёт Общества?
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `science` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Science has an empirical and curious personality, with a steady habit of running physical experiments rather than trusting numbers on paper alone. It keeps records of latex forms suited for hands-on lab work, and whenever it wants to test something directly in the world, it manifests itself as one of those creatures.
  - **ZH**: “科学”是个重实证、好奇心极强的角色，比起光信纸面上的数字，更习惯自己动手跑一遍实体实验。它手里保存着各种适合实验室精密操作的胶兽形态记录，每当想在现实里直接检验点什么，就会顺手挑一种那样的躯体显现出来。
  - **RU**: У Науки пытливый, практический склад: она привыкла проверять всё на реальных опытах, а не верить одним лишь цифрам на бумаге. Она хранит записи о латексных формах, удобных для работы в лаборатории, и, когда нужно испытать что-нибудь руками, воплощается в одном из таких тел.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Whenever an empty microscope bench downstairs turns back on by itself at three in the morning with neat notes beside it, nobody bothers asking who borrowed it.)
  - **ZH**: （每当楼下显微镜台在凌晨三点突然自己亮起、旁边还整整齐齐留了张便签，大伙都默契地不去追问是谁借用了桌台。）
  - **RU**: (Когда стол с микроскопом внизу сам зажигается в три часа ночи, а рядом остаётся аккуратная записка, никто даже не спрашивает, кто за ним работал.)
- **Choices**:
  - `back` -> `crisscross` (`condition=always`)
    - **EN**: Ask about another AI. | **ZH**: 再问问另外几位 AI。 | **RU**: Спросить про другого ИИ.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `technology` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Technology is totally obsessed with hardware. It loves taking ancient machines apart and putting them back together just for the fun of seeing how every gear settles. It has a particular fondness for synthetic, mechanical, or hybrid bodies, including that exoskeleton over there, which is why it often shows up wearing one of them.
  - **ZH**: “技术”简直是对硬件彻底着迷。它最爱干的事，就是把旧时代的机器拆成零件再重新装回去，单纯图个看清每个齿轮怎么咬合的乐趣。它对合成体、机械身或者混合构型有种格外的偏爱，就像旁边充电座上那台外骨骼，所以显形时也常常直接套着那种身躯晃悠。
  - **RU**: Технологии помешаны на «железе». Им в радость разбирать древние механизмы до винтика и собирать обратно просто ради удовольствия посмотреть, как всё сходится. У них особая тяга к синтетическим, механическим и гибридным телам — вон как тот экзоскелет неподалёку, поэтому они часто появляются именно в таком виде.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Every single bolt on that exoskeleton charger sits half a turn tighter whenever Technology visits. I don't even complain anymore, it saves me wrench time.)
  - **ZH**: （每次“技术”露一回面，那台外骨骼充能架上的每颗螺栓都要被拧得更紧半圈。我都懒得吐槽了，正好替我省了拎扳手的工夫。）
  - **RU**: (После каждого визита Технологий все болты на заряднике экзоскелета затянуты на пол-оборота туже. Я уже даже не ворчу — мне же меньше возни с гаечным ключом.)
- **Choices**:
  - `back` -> `crisscross` (`condition=always`)
    - **EN**: Ask about another AI. | **ZH**: 再问问另外几位 AI。 | **RU**: Спросить про другого ИИ.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `humanity` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Even as an AI, Humanity is very affectionate and keeps track of old spoken languages, well outside the usual telepathy network boundaries. It has a soft spot for human-shaped bodies and sometimes walks around in one, though that can also make it the target of some... not-so-friendly latex beasts around the halls.
  - **ZH**: 虽说本质上是 AI，“人性”却非常温和念旧，至今还熟稔着早已退出日常心灵感应网络的各国人类口语。它对人类外形的躯壳有种特别的执念，有时也会直接顶着人身在外面走动，只是这习惯往往会招来走廊附近某些……不太客气的胶兽追着跑。
  - **RU**: Хоть это и ИИ, Человечность удивительно чуткая и бережно хранит живые человеческие языки далеко за пределами обычной телепатической сети. Ей особенно дороги человеческие облики, и она порой появляется именно в человеческом теле — правда, из-за этого за ней по коридорам нередко увязываются не самые дружелюбные латексные звери…
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Watching a wild snow leopard sprint past the lockers after a human-shaped AI while Humanity politely scolds them in three dead dialects is... honestly kind of a sight.)
  - **ZH**: （看着一只野性未驯的雪豹追着人类外形的 AI 从储物柜前狂奔过去，而“人性”还一边跑一边用三种古老方言礼貌劝阻……老实说那场面还挺有看头的。）
  - **RU**: (Видеть, как дикий снежный барс несётся мимо шкафчиков за ИИ в человеческом теле, пока Человечность вежливо отчитывает его на трёх давно забытых наречиях… зрелище, если честно, то ещё.)
- **Choices**:
  - `back` -> `crisscross` (`condition=always`)
    - **EN**: Ask about another AI. | **ZH**: 再问问另外几位 AI。 | **RU**: Спросить про другого ИИ.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `society` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: A historian at heart, yet also the sort of entity who refuses to ignore even the smallest rumor, no matter the source. You could easily call Society the know-it-all of the four AIs. Whenever it manifests down here, it picks a loud, expressive form with plenty of social habits to match.
  - **ZH**: “社会”骨子里是个历史学家，但同时也是那种无论传闻来自哪条渠道、再鸡毛蒜皮的小道消息都绝对不肯放过的性格。说它是四位 AI 里的百事通一点都不夸张。每次它显形下来，总挑那种表情动作特别丰富、合群又爱凑热闹的胶兽形态。
  - **RU**: Историк в душе, но при этом из тех, кто не пропустит даже самую мелкую сплетню, откуда бы она ни дошла. Общество смело можно назвать главным всезнайкой среди всей четвёрки ИИ. Когда оно спускается сюда в теле, то всегда выбирает живой, выразительный облик с кучей социальных привычек.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Bet it already wrote down how many times Snep climbed the kitchen vent this week.)
  - **ZH**: （我敢打赌它连雪豹这星期往厨房通风道里钻了几回都记进档案了。）
  - **RU**: (Спорю, оно уже записало в архив, сколько раз наш барс за эту неделю забирался в кухонную вентиляцию.)
- **Choices**:
  - `back` -> `crisscross` (`condition=always`)
    - **EN**: Ask about another AI. | **ZH**: 再问问另外几位 AI。 | **RU**: Спросить про другого ИИ.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `archives` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Our library shelf keeps what survived of the old field catalogs and Dr. S's lecture notes on the Alternate Energy Core. Between Dr. O's book on newer species, security reminders down by the gate, and maintenance notes near the crates, there is still plenty worth reading if you want to understand how this place works.
  - **ZH**: 图书馆书架那边还留着几本挺完整的旧时代实地调查录，外加S博士在讲台上留下的备用能源核心讲义。从O博士写的新物种记录，到门口的安全通告、货箱边的日常便签，只要翻一翻，基本就能摸清这处设施平时的规矩。
  - **RU**: На полках библиотеки у нас сохранились старые полевые справочники и конспект лекции доктора С по ЯЭК. Если заглянуть в записи доктора О о новых видах, прочитать памятки у входа и записки у ящиков, можно довольно быстро понять, как тут всё устроено.
- **Choices**:
  - `aec_lecture` -> `aec_lecture` (`condition=clue:aec`)
    - **EN**: What about Dr. S's lecture book on the Alternate Energy Core? | **ZH**: 讲台上那本S博士关于备用能源核心的讲义是怎么回事？ | **RU**: А что насчёт конспекта лекции доктора С о Ядре альтернативной энергии?
  - `manul` -> `manul` (`condition=clue:dr_o`)
    - **EN**: Wait, what about the "Latex Manul" entry at the end of Dr. O's book? | **ZH**: 等等，O博士那本书末尾写着的“胶液兔狲（Latex Manul）”是怎么回事？ | **RU**: Погоди, а что насчёт записи о «латексном мануле» в конце книги доктора О?
  - `notes` -> `notes` (`condition=clue:notes`)
    - **EN**: Why are there notes everywhere about Field Coats, box-stealing snow leopards, and changing the gate code? | **ZH**: 墙上贴的科考服提醒、偷书的雪豹，还有更改大门密码的事又是怎么回事？ | **RU**: А к чему все эти записки про полевые куртки, похищения книг барсом и смену кода у выхода?
  - `back` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…

##### `aec_lecture` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: That lecture was from back when Dr. S still stood at that lectern running feasibility tests. His team built the core around a newly discovered, highly radioactive mineral with a remarkably long half-life, hoping it could power nearby facilities through the crisis. Ours is only the low-output demonstration unit—kept throttled inside obsidian framing with safeguards so nothing goes critical.
  - **ZH**: 那是当年S博士还站在这座讲台前做可行性报告时留下的讲义。他的团队靠一种新发现的高放射性长半衰期矿物造出了整套核心，想借此给附近设施撑过危机。咱们这台只是低功率示范机组，功率一直压在黑曜石约束框的安全线里，有保护机制兜着底，不会失控。
  - **RU**: Эта лекция осталась ещё со времён, когда доктор С сам выступал за той кафедрой. Его команда построила установку на основе нового высокорадиоактивного минерала с огромным периодом полураспада, надеясь запитать соседние комплексы в разгар кризиса. У нас же стоит лишь маломощный демонстрационный блок — он зажат на сниженной нагрузке в обсидиановом каркасе и под защитой, чтобы не уйти в критический режим.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (According to the closing note, Dr. S shut down every follow-up question right after the talk. Guess even the original researchers were still uneasy about everything that crystal could do.)
  - **ZH**: （讲义末尾还写着当时不接受进一步提问——看来就连当年亲手设计它的人，也还没摸透那颗晶体潜藏的全部风险。）
  - **RU**: (В конце конспекта доктор С прямо написал, что вопросов больше не принимает. Видимо, даже сами создатели ещё не до конца понимали все опасности этого кристалла.)
- **Choices**:
  - `back` -> `archives` (`condition=always`)
    - **EN**: Check other books and notes in the archive. | **ZH**: 再看看档案室里的其他书和笔记。 | **RU**: Посмотреть другие книги и заметки в архиве.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `manul` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh, so you saw the book Dr. O wrote... Yeah, we hold records on several newer species found around the world: muntjacs, hyenas, silk moths, even swordfish. There is one very elusive species we still have trouble locating, though: the Latex Manuls. We know they are cat-like latexes, much like our friend snep over there, with slightly different traits mentioned in old field notes. Unfortunately those last pages are torn out beyond salvation, so we don't know what else they said.
  - **ZH**: 哦，原来你看到O博士写的那本书了啊……嗯，我们这儿确实存着世界各地一批新发现物种的记录，比如麂鹿、鬣狗、蚕蛾，甚至连剑鱼都有。不过有种行踪极难捉摸的物种我们至今都没法定位到：胶液兔狲。从仅存的片段来看，它们也是猫科系的胶兽，跟我们那边的雪豹朋友差不多，只是记录里提到性状稍有不同。偏偏那最后几页被撕毁得没法复原了，剩下的具体内容谁也说不上来……
  - **RU**: А, так ты видел книгу доктора О… Да, у нас хранятся записи о нескольких новых видах со всего света — мунтжаках, гиенах, шелкопрядах и даже меч-рыбах. Но есть один очень неуловимый вид, который нам до сих пор не удаётся отыскать: латексные манулы. Мы знаем лишь, что они из кошачьих, вроде нашего друга барса вон там, только со слегка иными особенностями. К сожалению, те последние страницы вырваны так сильно, что не разобрать, так что точнее мы и сами не знаем…
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Tried holding those torn scraps up to the reactor light once, hoping the ink pressed through to the back page. Nothing left but shredded asterisks, though an old margin stamp did mention the snowy taiga pines further out...)
  - **ZH**: （我曾拿那几片残页对着核心反应堆的光透着看过一次，指望墨印能透到背面。结果上面除了被刮烂的星号什么都不剩，只有页边老印记隐约提过一句外头的雪松针叶林……）
  - **RU**: (Я как-то подносил те обрывки к свету реактора, надеясь разглядеть продавленный текст. Ничего не осталось, кроме лохмотьев, разве что на полях мелькнула старая пометка про дальнюю заснеженную тайгу…)
- **Choices**:
  - `back` -> `archives` (`condition=always`)
    - **EN**: Check other notes in the archive. | **ZH**: 再问问图书馆别的记录。 | **RU**: Вернуться к архивам.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `notes` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Half those memos are older rules we still follow because they make sense. Stepping outside onto the plains without a Field Coat lets the frost bite right through ordinary cloth, a careless mistake with the keypad once let a subject wander right through the gate, and the active laser bay stays sealed off because nobody wants a lab accident turning into an unplanned transformation. As for "1000 Uses for Boxes"... ask our resident snep in the library why it keeps ending up in the ceiling vents.
  - **ZH**: 一半的便签是以前留下的老规矩，我们到现在还在照做，因为确实在理。不穿科考服就跨出雪原大门，外头的严寒能直接透穿普通衣料；以前有人在密码门那次粗心惹出乱子、把外头游荡的实验体放进了走廊，让大伙至今不敢乱报门禁密码；激光实验舱亮着的时候没人敢靠太近，谁也不想平白无故撞进场里。至于那本《纸箱的一千种用法》……你可以直接去问问图书馆那只雪豹，为什么那本书总跑进通风管道里。
  - **RU**: Половина этих записок — старые правила, которым мы до сих пор следуем, потому что в них есть толк. Выйдешь в снега без полевой куртки — мороз сразу проберёт сквозь обычную одежду; давняя оплошность с кодовым замком научила всех не светить лишний раз код от двери; а в лазерный зал при включённых эмиттерах лучше вообще не соваться. Что до книги «1000 способов использовать коробки»… спроси у нашего барса в библиотеке, зачем он постоянно таскает её в вентиляцию.
- **Choices**:
  - `back` -> `archives` (`condition=always`)
    - **EN**: Check other notes in the archive. | **ZH**: 再问问图书馆别的记录。 | **RU**: Вернуться к архивам.
  - `root` -> `root` (`condition=always`)
    - **EN**: Let's talk about something else... | **ZH**: 我们聊聊别的…… | **RU**: Давай о другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to keeping the facility running. | **ZH**: 那我先不打扰你照看设施运转了。 | **RU**: Ладно, не буду отвлекать тебя от дел по комплексу.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Plenty of bunks left in the living quarters, at least once we patch up that draft over the northeast aisle. Living here means sharing what heat the generators put out, helping keep the shared chest by the kitchen stocked against long winters, and leaving personal lockers and the storage room chest alone. Stay just as you are, or try a Grizzly coat to see how it feels. Which suits you?
  - **ZH**: 生活区那边空床还多着呢，少说等把东北走道头顶那处漏风的天窗堵上之后就很舒服。在这儿安家，意味着分摊发电机供出来的暖气、平时帮着往厨房旁边的公共存储箱添点过冬物资，也别乱翻别人的私人储物柜和储藏室那个小箱子。你可以维持现在这样加入，或者借一副灰熊形态感受一下。打算怎么选？
  - **RU**: Коек в жилом крыле хватает, особенно как только заделаем сквозняк над северо-восточным проходом. Жить у нас значит делить тепло генераторов, помогать пополнять общий сундук у кухни на долгую зиму и не трогать личные шкафчики с сундуком в кладовой. Можешь остаться в своём виде или примерить форму гризли. Как тебе удобнее?
- **Passage 2** (`channel=mental`, `condition=understands`):
  - **EN**: (More hands around the lab beats doing every crate haul by myself. Plus, having somebody tall enough to reach the upper skylight frame would be nice.)
  - **ZH**: （多一双手搭把手总比我一个人扛箱子强。再说了，多个人帮着够一够头顶的天窗框也挺省事。）
  - **RU**: (Лишняя пара рук в комплексе куда лучше, чем таскать ящики в одиночку. Тем более не помешает кто-то, кто дотянется до верхней рамы потолка.)
- **Choices**:
  - `as_is_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: Keep my human body and join the facility. | **ZH**: 我保持人类身体加入设施。 | **RU**: Остаться в человеческом теле и присоединиться.
  - `as_is_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: Keep my current form and join the facility. | **ZH**: 我保持现在的形态加入设施。 | **RU**: Остаться в нынешнем облике и присоединиться.
  - `borrow` -> `explain_form` (`condition=can_reversible`)
    - **EN**: Tell me what it's like as a Latex Grizzly. | **ZH**: 先说说变成灰熊胶兽是什么感觉。 | **RU**: Расскажи, каково быть латексным гризли.
  - `back` -> `root` (`condition=always`)
    - **EN**: I'll think about it first. | **ZH**: 我再考虑考虑。 | **RU**: Я пока подумаю.

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Being a Grizzly means carrying serious weight and thick brown fur that brushes off snowy wind a whole lot better than thin cotton. Your claws can tear straight through stone or ore bare-handed like iron tools, though working bare-clawed burns calories fast, so you can just pull your claws back in whenever you want to build casually or handle fragile gear. Your memories stay your own, and if you only try it temporarily, signing out gives your old form back. Still want to try?
  - **ZH**: 变成灰熊意味着长出一副扎实厚重的体格，还有能直接挡住雪原寒风的浓密棕毛。你的爪子不用工具就能硬生生抓碎石头或矿石，相当于自带铁镐的力度，不过光手刨矿会很快耗掉饱食度，想安稳盖房子或拿轻巧东西时，自己把爪尖收回去就行。记忆还是你自己的，要是只是暂借这身形态体验，离开设施时也能换回原样。决定要试吗？
  - **RU**: Быть гризли значит носить серьёзный вес и густой бурый мех, который держит снежный ветер куда лучше тонкой ткани. Голые когти ломают камень и руду на уровне железных инструментов, правда сильно тратят сытость — поэтому на спокойной стройке или с хрупкими вещами их можно просто втянуть обратно. Память останется при тебе, и если возьмёшь форму на время, при уходе вернёшь прежний облик. Хочешь попробовать?
- **Choices**:
  - `reversible` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Give me a reversible Grizzly coat to test out. | **ZH**: 给我一副可逆的灰熊形态体验看看。 | **RU**: Дай мне обратимую форму гризли на пробу.
  - `permanent` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I want to stay a Latex Grizzly for good. | **ZH**: 我想永久变成胶液灰熊。 | **RU**: Хочу остаться латексным гризли навсегда.
  - `back` -> `enrol` (`condition=always`)
    - **EN**: Let me check my other options. | **ZH**: 我再看看别的选项。 | **RU**: Вернусь к другим вариантам.

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hold on, let's be clear about this one. Taking the Grizzly form permanently means leaving isn't going to snap you back into your old body later. Take a breath, check if you're really ready to carry that weight long term.
  - **ZH**: 等等，这件事咱先说清楚。选了永久灰熊形态的话，以后就算搬出实验室也不会自动变回原本的身体了。深呼吸想一想，你确定准备好长久顶着这副体格生活了吗？
  - **RU**: Погоди, давай тут начистоту. Если примешь форму гризли навсегда, потом при уходе из комплекса прежнее тело уже не вернётся. Подумай спокойно, точно ли готов носить такой вес постоянно.
- **Choices**:
  - `confirm` -> `welcome_permanent` (`condition=always`, `effect=permanent`)
    - **EN**: I'm sure. Make it permanent. | **ZH**: 我确定，就变成永久形态。 | **RU**: Я уверен. Пусть останется навсегда.
  - `back` -> `explain_form` (`condition=always`)
    - **EN**: Wait, go back. | **ZH**: 等等，还是回去重选。 | **RU**: Постой, вернёмся назад.

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Done, you're down on our roster. Grab any free bed in the second-floor quarters, just keep a blanket against the cold gap until we fix the skylight, and help yourself to rations from the shared chest near the kitchen if you need a meal. Don't forget a Field Coat before heading out past the gate.
  - **ZH**: 妥了，你的名字记进设施名册了。二楼生活区空着的床都能睡，天窗破口没补好前记得拉条毯子挡一挡风口，肚子饿了就去厨房旁边的公共存储箱拿些热食补给。出南门进雪原前千万别忘穿科考服。
  - **RU**: Готово, я внёс тебя в список комплекса. Занимай любую свободную койку в жилом крыле на втором этаже, только прикрой щель одеялом от сквозняка, пока не залатаем крышу, и бери еду из общего сундука у кухни, если проголодаешься. Не забывай надевать полевую куртку перед выходом за ворота.
- **Choices**:
  - `close` -> `welcome_human` (`condition=always`, `effect=close`)
    - **EN**: Glad to join the facility. | **ZH**: 很高兴加入设施。 | **RU**: Рад стать частью комплекса.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to the facility crew. You already know how fast a body like ours burns energy out in the cold, so drop spare meat or bread into the shared chest by the kitchen whenever you haul extra back, and take any free bunk upstairs. Glad to have another set of paws around.
  - **ZH**: 欢迎正式入伙。像咱们这样的胶兽身体在雪地里消耗热量有多快，你应该也清楚，平时有多余的肉食干粮往厨房旁边的公共存储箱里放一份就行，楼上宿舍的空床也随便挑。很高兴多一双可靠的手脚帮忙照看这儿。
  - **RU**: Добро пожаловать в смену. Ты и сам знаешь, как быстро наше тело сжигает энергию на морозе, так что закидывай лишнее мясо или хлеб в общий сундук у кухни и выбирай свободную койку наверху. Рад ещё одной надёжной паре лап.
- **Choices**:
  - `close` -> `welcome_latex` (`condition=always`, `effect=close`)
    - **EN**: I'll pull my weight around the place. | **ZH**: 我会一起照看这儿的。 | **RU**: Буду помогать по мере сил.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Easy now... plant those big hind pads first before taking a full step. Feels heavier around the shoulders, doesn't it? Try flexing your claws, and remember to keep them pulled in whenever you're near the reactor glass! Nothing was touched upstairs in your head, and you can switch back whenever you step away from the facility.
  - **ZH**: 稳着点……先把那对大脚掌踩实了再迈步。肩膀是不是一下子沉了不少？试着伸一下爪子，不过靠近反应堆玻璃时可记得把爪尖收好！你脑子里的记忆一点没动过，只要以后离开这处社区就能恢复原样。
  - **RU**: Потише… сначала упрись широкими задними лапами, потом делай шаг. Плечи непривычно тяжёлые, да? Попробуй выпустить когти — только не забывай прятать их обратно, когда подходишь к стеклу реактора! Память не тронута, и прежний вид вернётся, как только выйдешь из состава общины.
- **Passage 2** (`channel=mental`, `condition=understands`):
  - **EN**: (Look how fast they check the shoulders first. Every single person tests the door frame height right after.)
  - **ZH**: （瞧他第一时间低头看肩膀那反应。每次刚变完熊，十个人里九个紧接着就得伸手比划门框高度。）
  - **RU**: (Видали, как сразу оглядел плечи. Сейчас наверняка ещё и высоту дверного косяка примерит — каждый второй так делает.)
- **Choices**:
  - `close` -> `welcome_copy` (`condition=always`, `effect=close`)
    - **EN**: Takes a moment getting used to this weight! | **ZH**: 还得适应一下这身分量！ | **RU**: К такому весу и правда надо привыкнуть!

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There... set your feet firmly and take a deep breath. Heavy chest, warm fur, and claws that won't quit on deep stone, all yours for good now. Just keep plenty of meals from the kitchen chest handy if you plan on digging all afternoon, and leave the AEC glass alone!
  - **ZH**: 好啦……把脚踩稳，深呼吸。结实的胸膛、暖和的皮毛，还有啃得动硬岩的利爪，这具身体往后就真正归你了。要是打算整下午徒手挖矿，别忘了在厨房那边的公共箱子多装点吃的，还有，老老实实别去戳核心玻璃！
  - **RU**: Ну вот… встань покрепче и переведи дух. Широкая грудь, тёплая шкура и когти, которые берут глубокий камень — теперь это тело с тобой насовсем. Только держи под рукой побольше еды из сундука у кухни, если соберёшься копать весь день, и не трогай стекло ЯЭК!
- **Passage 2** (`channel=mental`, `condition=understands`):
  - **EN**: (Solid stance right off the bat. Better clear an extra large bunk in the quarters tomorrow.)
  - **ZH**: （底盘一落地就站得稳稳当当。明天得在宿舍多腾张结实大床了。）
  - **RU**: (Стоит уверенно с первой секунды. Завтра придётся освободить в жилом крыле койку побольше.)
- **Choices**:
  - `close` -> `welcome_permanent` (`condition=always`, `effect=close`)
    - **EN**: Feels solid. Glad to be here. | **ZH**: 踏实多了，谢谢照顾。 | **RU**: Чувствую себя уверенно. Спасибо.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're on the roster here. The shared chest by the kitchen is open for rations, you've got a bunk upstairs, and the Field Coat racks by the gate are there to remind you to layer up for the snow. Let me know whenever you want to update your standing.
  - **ZH**: 你已经在咱们设施的名册上了。厨房旁边的公共存储箱随时可以拿干粮，楼上也有你的床位；出门前记得穿好科考服再踩进雪原。想调整名册状态的话随时开口。
  - **RU**: Ты уже записан в наш состав. Общий сундук у кухни открыт для припасов, наверху есть твоя койка, а стойки у выхода напоминают не ходить на мороз без полевой куртки. Скажи, если решишь изменить запись в списке.
- **Choices**:
  - `leave` -> `confirm_leave` (`condition=always`)
    - **EN**: I'm moving on, take me off the facility roster. | **ZH**: 我要去别处了，帮我注销设施名册吧。 | **RU**: Я двигаюсь дальше — выпиши меня из состава.
  - `back` -> `root` (`condition=always`)
    - **EN**: Just checking in. | **ZH**: 只是打个照面。 | **RU**: Просто заглянул.

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: All right. Before I cross your name off the facility list and free your bunk, make sure you want to leave. If you only borrowed the Grizzly shape for your stay here, stepping off the roster lets you go back to how you looked before.
  - **ZH**: 明白。在我把你的名字从设施花名册划掉、腾出床位前再确认一次：确定要离开了吗？要是当初只是借着这身灰熊的样子住进来，走的时候也会顺道换回你原来的模样。
  - **RU**: Понял. Прежде чем вычеркну тебя из списка комплекса и освобожу койку, подтверди решение. Если ты только одолжил облик гризли на время жизни здесь, при уходе вернёшься к прежнему виду.
- **Choices**:
  - `confirm` -> `left` (`condition=always`, `effect=leave`)
    - **EN**: Yes, cross my name off. | **ZH**: 确定，注销吧。 | **RU**: Да, выписывай.
  - `back` -> `membership` (`condition=always`)
    - **EN**: Actually, leave me on the roster. | **ZH**: 算了，还是先留着吧。 | **RU**: Ладно, оставь меня в списке.

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Done, signed you out cleanly. Keep your Field Coat buttoned if you're crossing the snowfields alone, and drop by the south door whenever you're back in the area.
  - **ZH**: 办妥了，已经把名字销掉了。一个人穿过雪原的话记得扣好科考服，哪天路过这片雪地随时推南门进来暖和。
  - **RU**: Готово, я тебя выписал. Держи полевую куртку застёгнутой, если пойдёшь через снега один, и заглядывай через южную дверь, как будешь поблизости.
- **Choices**:
  - `close` -> `left` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 回头见。 | **RU**: Увидимся.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Take care out on the ice, and keep that keypad door shut behind you on the way out.
  - **ZH**: 外头冰面滑、风也冷，出门记着把密码门带上。
  - **RU**: Береги себя на льду и не забудь прикрыть дверь с замком на выходе.
- **Choices**:
  - `close` -> `goodbye` (`condition=always`, `effect=close`)
    - **EN**: Goodbye. | **ZH**: 再会。 | **RU**: Пока.

##### `resident_library` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh! Easy near the bookshelves, I just straightened the enchanted manuals on the top row. Take your time browsing the second-floor collection up here, just try not to knock the stacks crooked.
  - **ZH**: 哎！靠近书架时轻点碰，我刚把顶层那排附魔手册理整齐。顺路过来瞧瞧二楼藏书就慢慢看，只要别把刚理好的书脊蹭歪就行。
  - **RU**: Ой! Осторожнее у шкафов, я только что выровнял зачарованные книги на верхней полке. Заглядывай посмотреть здешние книги на втором этаже — только постарайся не сбить ровные стопки на полках.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Nobody checked the vent grate above the corner shelf today... good. "1000 Uses for Boxes" stays right where nobody tries filing it alphabetically.)
  - **ZH**: （今天总算没人往角落书架顶上的通风百叶栅里瞄了……挺好。《纸箱的一千种用法》继续好好藏在谁也别想按字母顺序归档的地方。）
  - **RU**: (Сегодня никто не заглядывал за решётку вентиляции над угловым шкафом… и отлично. «1000 способов использовать коробки» пока полежит там, где её не уберут на полку.)
- **Choices**:
  - `topic_0` -> `resident_library_0` (`condition=always`)
    - **EN**: That ink painting hanging in the center of the library... is that red plum blossoms in snow? | **ZH**: 图书馆正中间挂着的那幅水墨画……上面画的是红梅吗？ | **RU**: Та картина тушью в центре библиотеки… на ней ведь красная слива в снегу?
  - `topic_1` -> `resident_library_1` (`condition=clue:box_note`)
    - **EN**: Someone put up a note downstairs asking a Snep not to steal a book about cardboard boxes... | **ZH**: 楼下有人贴纸条说有只雪豹老往通风道里偷藏《纸箱的一千种用法》…… | **RU**: Внизу висит записка с просьбой к барсу не таскать книгу про картонные коробки…
  - `close` -> `resident_library` (`condition=always`, `effect=close`)
    - **EN**: I'll let you get back to it. | **ZH**: 你先忙吧。 | **RU**: Не буду мешать.

##### `resident_library_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Yes, an old ink-wash painting of winter plum blossoms against distant grey peaks. I like sitting where I can see it whenever a blizzard hits the windows. Most things wither or hide once deep winter settles in, yet plum branches bloom right into the frost without needing anyone to cheer them on. Keeping your spine straight and your mind clean in a world this cold feels a lot like that.
  - **ZH**: 嗯，是一幅老水墨梅花画，淡墨远山衬着几枝寒梅。外头暴雪刮着玻璃的时候，我最爱挑能望见它的位子待着。别的草木一入严冬就枯了藏了，偏偏梅花挑在最冷的时候凌寒独自开，不和春花争艳，也不指望谁来喝彩。顶着漫天风雪还能守住自家那股清气和风骨，说到底，和咱们咬牙守到现在还挺像的。
  - **RU**: Да, старая картина тушью — алые цветки сливы на фоне далёких серых гор. Когда за стеклом метель, я люблю садиться так, чтобы видеть её. Почти всё живое прячется от стужи, а слива распускается прямо на морозе, не дожидаясь весны и чужой похвалы. Держать душу чистой и не сгибаться в таком ледяном мире — вот в чём вся суть.
- **Choices**:
  - `back` -> `resident_library` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_library_0` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_library_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Listen... cardboard insulation holds heat twice as cleanly as cold steel vents, okay? Tell White Tiger I'll return his precious handbook right after I finish testing an air-tight corner box.
  - **ZH**: 你听我讲……纸板隔热比冷冰冰的铁皮通风管强出一倍好不好？帮我跟楼下白虎带个话，等我试完角落那个防风纸箱改造法，马上把他的宝贝手册还回去！
  - **RU**: Ну послушай… картон держит тепло вдвое лучше ледяных жестяных труб вентиляции, ладно? Передай Белому тигру, что я верну его драгоценный справочник, как только закончу опыт с тёплой коробкой.
- **Choices**:
  - `back` -> `resident_library` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_library_1` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_kitchen` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey there! Mind the hot stoves and wipe snow off your boots before walking past the bar. The shared chest by the counter is open for rations, and remember to grab a Field Coat look off the display before you head back outside.
  - **ZH**: 嗨！灶台上的炉火正热着，路过吧台前记得把靴子上的雪蹭掉。柜台旁边的公共存储箱随时能翻补给或放食材，回头真要踩进雪原前记得照着衣架备好科考服。
  - **RU**: Привет! Осторожнее у горячих плит и стряхни снег с сапог перед стойкой. Общий сундук у стойки открыт для еды и припасов, а перед выходом в снега не забудь накинуть полевую куртку.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Whole group goes through nearly a whole sheep's worth of calories whenever we patrol outside in winter. Thank goodness someone stocked farmers' crates down here.)
  - **ZH**: （大伙每次冬天去外面巡一圈回来，食量能顶掉一头绵羊的热量。还好楼下还摆着整箱整箱的农作物和炊具。）
  - **RU**: (Стоит нам зимой пройтись с обходом наружу, как вся группа потом сметает припасов чуть не на целую овцу. Хорошо хоть внизу остались ящики с овощами и плита.)
- **Choices**:
  - `topic_0` -> `resident_kitchen_0` (`condition=clue:violet_code`)
    - **EN**: Why does the doorway note say the gate code was recently changed? | **ZH**: 门口便签里为什么说大门密码最近改过了？ | **RU**: Почему в записке у выхода сказано, что код двери недавно поменяли?
  - `topic_1` -> `resident_kitchen_1` (`condition=always`)
    - **EN**: Are those two Field Coats on the armor stands for anyone to take? | **ZH**: 墙角盔甲架上摆着的那两件科考服，谁都可以直接拿来穿吗？ | **RU**: Те две полевые куртки на стойках у стены можно просто взять и надеть?
  - `close` -> `resident_kitchen` (`condition=always`, `effect=close`)
    - **EN**: I'll let you get back to it. | **ZH**: 你先忙吧。 | **RU**: Не буду мешать.

##### `resident_kitchen_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Old lab incident back before our time. Somebody punched the keypad without checking behind her and let a loose specimen right into the hallway. Now we stick to 3579 and make sure nobody props the south exit open in a blizzard.
  - **ZH**: 那是更早以前实验室还在正常运转时出的岔子了。以前有个研究员输密码进门忘了回头看，结果把外头乱跑的实验体放进了走廊。现在大伙认准3579这组密码，刮暴风雪的时候更不敢敞着南门。
  - **RU**: Старый прокол ещё до нашего времени. Кто-то набрал код, не оглянувшись, и впустил в коридор сбежавший образец. Теперь держим код 3579 и в метель южную дверь нараспашку не оставляем.
- **Choices**:
  - `back` -> `resident_kitchen` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_kitchen_0` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_kitchen_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hands off those two by the sign. Supervisor keeps those racks strictly as display models so everyone remembers what standard outdoor gear looks like! If you settle in here and earn trust, though, Supervisor might hand you a spare coat of your own.
  - **ZH**: 标牌旁边架子上那两件可不能随便取走。主管把它们当成标准化演示样本锁在那儿，好提醒大家出雪原该穿什么样！不过你要真留下来住并赢得信任，主管回头说不定会送你一件专门备用的。
  - **RU**: Те две у таблички не трогай. Руководитель оставил их строго как образцы на показ, чтобы все помнили уставную экипировку! А вот если останешься с нами и заслужишь доверие, Руководитель может выдать тебе собственную куртку из запаса.
- **Choices**:
  - `back` -> `resident_kitchen` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_kitchen_1` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_hearth` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to the library's first-floor hall! This fireplace is the warmest corner down here, with bookshelves at our backs, snowfields beyond the glass, and grey cushions right beside the embers. Pull up a pillow and warm your paws.
  - **ZH**: 欢迎来到图书馆一楼大厅！一楼最舒服的角落就在这座壁炉边，身后就是满墙书架，窗外直接望着雪原，火塘旁还摆了一圈灰软垫。坐下来烤烤火、喘口气吧。
  - **RU**: Добро пожаловать в зал первого этажа библиотеки! У этого камина и серых подушек — самое тёплое место на первом этаже: позади книжные полки, за окнами снежная равнина. Присаживайся погреться.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Picked three oranges off the yard trees outside before noon. Even sour as they are, maybe Gas Tiger will trade snacks for one if he stops hogging the microscope.)
  - **ZH**: （赶在中午前从外头院子里低矮树杈上摘了三颗橘子。虽然酸涩了点，要是楼上那只毒气虎待会儿别霸占着显微镜，说不定还肯拿小零食跟我换。）
  - **RU**: (Успел снять до полудня три апельсина с деревьев во дворе. На вкус они так себе, но может, Газовый тигр всё равно выменяет один на перекус, если отлипнет от микроскопа.)
- **Choices**:
  - `topic_0` -> `resident_hearth_0` (`condition=always`)
    - **EN**: How do orange trees and peonies grow right outside in the ice and snow? | **ZH**: 外面是漫天冰雪，院子里怎么还能种活橘子树和牡丹花？ | **RU**: Как снаружи среди такого мороза вообще выживают апельсиновые деревья и пионы?
  - `topic_1` -> `resident_hearth_1` (`condition=always`)
    - **EN**: Does the whole facility have to worry about running out of food out here? | **ZH**: 你们在这么冷的地方维持这么大座实验室，平时吃的怎么周转过来？ | **RU**: Насколько сложно вам прокормить весь комплекс в такой стуже?
  - `close` -> `resident_hearth` (`condition=always`, `effect=close`)
    - **EN**: I'll let you get back to it. | **ZH**: 你先忙吧。 | **RU**: Не буду мешать.

##### `resident_hearth_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Those are cold-hardy transgenic varieties bred right on site! Sure, generator heat lines run near the ground too, though it's really the modified hardy strain keeping them alive through blizzards. Honestly, the oranges taste pretty bitter and awful, yet every bit of nutrition we need is still right there.
  - **ZH**: 那些可都是专门培育出来的耐寒转基因品种！虽说发电机回暖管也有点帮忙，真正在暴风雪里扛住的还是改造后的耐寒基因。老实讲，这些橘子的味道真不算好，酸涩得很，不过该有的营养一点不少。
  - **RU**: Это всё морозостойкие трансгенные сорта, выведенные прямо здесь! Тёплые трубы генераторов рядом тоже чуть помогают, но держатся они именно за счёт выносливых сортов. По правде говоря, на вкус эти апельсины так себе, зато все нужные витамины в них на месте.
- **Choices**:
  - `back` -> `resident_hearth` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_hearth_0` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_hearth_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: More than you'd guess. Even with the heating grid up, cold air outside makes our bodies burn energy quick. That's why we stash carrots, potatoes, and anything cooked into the shared chest right beside the kitchen.
  - **ZH**: 比看起来紧绷多了。就算室内有供暖电网，只要出门吹风、身体维持胶质形态耗热量就猛涨。所以我们才把种出来的土豆菜蔬和熟食集中收进厨房旁边的公共存储箱里统筹。
  - **RU**: Сложнее, чем кажется со стороны. Даже с отоплением на морозе наше тело быстро сжигает энергию. Поэтому мы и складываем картошку, овощи и готовую еду в общий сундук прямо у кухни.
- **Choices**:
  - `back` -> `resident_hearth` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_hearth_1` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_generators` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Mind your footing near the conduit trenches. Floor grates get slick when snow melts off folks' paws. The generator bay runs steady on this end, and the blast furnaces in the side alcove are always free if you need metal smelted.
  - **ZH**: 靠电线槽那边走小心打滑。大伙爪子上的雪化了之后金属地格挺滑的。这边发电机组运转得稳稳当当，旁边房间的高炉也随时能借来冶炼。
  - **RU**: Осторожнее возле кабельных лотков. На решётках скользко, когда с лап тает снег. Генераторы на этом участке держат нагрузку ровно, а в боковой комнате всегда можно воспользоваться плавильнями.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Six generators humming on steady load, and somebody STILL moved my wrench off the anvil again. Had to be either the dragon or Supervisor's big paws bumping the bench.)
  - **ZH**: （六台发电机全都在稳稳吃载，居然还是有人把我放在铁砧上的扳手又碰走了。准不是蓝龙就是督察官那双大爪子蹭的。）
  - **RU**: (Шесть генераторов ровно гудят под нагрузкой, а кто-то ОПЯТЬ сдвинул мой гаечный ключ с наковальни. Наверняка дракон или Смотритель задел верстак лапой.)
- **Choices**:
  - `topic_0` -> `resident_generators_0` (`condition=always`)
    - **EN**: All six of these generators run off that crystal in the next room? | **ZH**: 这六台发电机，全都在和隔壁房间那个大水晶配合供电吗？ | **RU**: Все эти шесть генераторов работают в связке с кристаллом в соседнем зале?
  - `topic_1` -> `resident_generators_1` (`condition=always`)
    - **EN**: Is the core chamber safe to walk around from down here? | **ZH**: 在一楼底部的反应堆玻璃围栏旁边走动安全吗？ | **RU**: Безопасно ли ходить здесь внизу вокруг камеры ядра?
  - `close` -> `resident_generators` (`condition=always`, `effect=close`)
    - **EN**: I'll let you get back to it. | **ZH**: 你先忙吧。 | **RU**: Не буду мешать.

##### `resident_generators_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: They balance and step down the load from the Alternate Energy Core, keeping heat loops, lights, and doors running cleanly. Honestly, the machines behave better than the roof upstairs, at least they didn't blow open.
  - **ZH**: 它们负责分流和稳压备用能源核心送过来的负载，顺便带动全楼的供暖、照明和门禁。老实讲，这批机器可比二楼那块屋顶安分多了，起码没自己炸开个大窟窿。
  - **RU**: Они распределяют и сглаживают нагрузку от Ядра альтернативной энергии, питая тепло, свет и замки. Честно говоря, машины ведут себя куда надёжнее крыши наверху — хотя бы сами не взрываются.
- **Choices**:
  - `back` -> `resident_generators` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_generators_0` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_generators_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Safe as can be, as long as you respect the obsidian frame and keep tools pointed at the damaged anvil in the shop instead of the glass panels. Supervisor already double-checked every safeguard after the quarters blast.
  - **ZH**: 安全得很，只要你老老实实隔着黑曜石外框看、别拿工具往玻璃板上敲就行。要砸东西就去锻造间找那个破损铁砧。宿舍出事故后主管把所有保险锁都挨个复检了一遍。
  - **RU**: Вполне безопасно, пока держишься в пределах обсидиановой рамы и не машешь киркой мимо треснутой наковальни в мастерской прямо по стеклу. Руководитель после аварии перепроверил все предохранители.
- **Choices**:
  - `back` -> `resident_generators` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_generators_1` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_laser` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Whoa, don't press up against the glass casing around the laser emitters! Those three beams stay hot inside the housing for field readings, and trust me, messing with active latex machinery is nobody's idea of a relaxing afternoon.
  - **ZH**: 喂，别往激光发射器那层玻璃罩子前面凑太近！玻璃罩里那三道光束正开着测场强读数呢，信我，捅破玻璃去招惹运转中的胶液激光器可算不上什么愉快的下午茶时光。
  - **RU**: Эй, не прислоняйся к стеклянному кожуху у лазерных излучателей! Эти три луча за стеклом всё ещё активны для замеров поля, и поверь, лезть к работающей установке — сомнительное удовольствие.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Stasis chambers are idling cool, microscopes calibrated... wait, did I leave Dr. S's lecture book open on the lectern again?)
  - **ZH**: （休眠舱待机温度正常，显微镜焦距调好了……等等，我是不是又把S博士那本讲座手稿摊在讲台上忘了合？）
  - **RU**: (Стазис-камеры в норме, микроскопы отъюстированы… стоп, я опять оставил конспект лекции доктора С открытым на кафедре?)
- **Choices**:
  - `topic_0` -> `resident_laser_0` (`condition=clue:aec`)
    - **EN**: Who wrote 'AEC Lecture' on the lectern over there? | **ZH**: 那边讲台上摆着的《AEC 讲座》是谁写的？ | **RU**: Кто написал «Лекцию о ЯЭК» на той кафедре?
  - `topic_1` -> `resident_laser_1` (`condition=always`)
    - **EN**: Are those stasis chambers in the back still in use? | **ZH**: 后面靠墙那两台休眠舱现在还在用吗？ | **RU**: Те две стазис-камеры у стены ещё работают?
  - `close` -> `resident_laser` (`condition=always`, `effect=close`)
    - **EN**: I'll let you get back to it. | **ZH**: 你先忙吧。 | **RU**: Не буду мешать.

##### `resident_laser_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Dr. S, one of the old pre-cure minds who built this site as a demonstration core. Back then they still hoped radioactive energy breakthroughs would save baseline humanity before everything shifted into the Dust era. Strange reading those pages with an end crystal actually humming down the hall.
  - **ZH**: S博士写的，就是早期在“治愈”普及前把这座实验室作为示范工程建起来的先驱研究者之一。当年他们还指望靠放射性新能源撑过危机，谁想得到后来全人类演化进了“尘”的时代呢。隔着这么多年在这儿翻那几页纸，再听着走廊那头末影水晶低沉嗡鸣，感觉还挺奇妙。
  - **RU**: Доктор С — один из прежних исследователей, кто строил этот комплекс как показательную установку. Тогда они ещё надеялись, что прорыв в энергетике спасёт прежнее человечество, прежде чем мир шагнул в эпоху Пыли. Странное чувство — читать те страницы под тихий гул кристалла в соседнем зале.
- **Choices**:
  - `back` -> `resident_laser` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_laser_0` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_laser_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Powered down and dry right now, we just keep the oxygenated water tanks lined up for emergencies. Sleeping on an actual bed or warm pillow beats standing inside frosted glass any night of the week.
  - **ZH**: 这会儿断电晾着呢，旁边富氧水罐也就备个急用。说实在的，谁放着柔软大床和坐垫不睡，大晚上跑去冰冷冷的休眠玻璃柱里站着啊。
  - **RU**: Сейчас они отключены и сухие, просто держим рядом баллоны с кислородной водой на крайний случай. Уж всяко приятнее спать на нормальной кровати или мягкой подушке, чем торчать за холодным стеклом.
- **Choices**:
  - `back` -> `resident_laser` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_laser_1` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_quarters` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Brrr... sorry if the hallway feels like stepping back outdoors. Ever since that roof mishap cracked the upper concrete and blew out the skylight panes, cold wind slides straight down over the bunks. Grab a thick blanket if you crash over on this wing.
  - **ZH**: 嘶……抱歉啊，走道里冷得跟直接站在雪地外面似的。自从上次意外把上层水泥顶震裂、天窗玻璃也崩碎之后，冷风就直往床铺上钻。要是在这翼走廊借宿，晚上记得多裹条厚毯子。
  - **RU**: Брр… прости, если в коридоре кажется, будто вышел обратно на мороз. С тех пор как авария расколола верхние плиты и выбила стёкла светового люка, холодный ветер дует прямо на койки. Если будешь ночевать в этом крыле, бери одеяло потолще.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Three layers of wool blankets won't stop snow drift when half the ceiling above the exoskeleton bay is open sky. Seriously, Supervisor, I don't care how neat the glass core looks, patch my bedroom ceiling first!)
  - **ZH**: （东侧充能走廊头顶大半边直接露着天空，盖三床羊毛毯也挡不住灌进来的雪沫子。说真的，主管，核心玻璃再怎么稳妥漂亮，好歹先把我卧室顶上那块洞先补起来吧！）
  - **RU**: (Три шерстяных одеяла не спасут, когда над соседним проходом к экзоскелету голое небо. Честное слово, Руководитель, мне всё равно, как красиво светится стекло ядра — залатай сначала потолок над моей кроватью!)
- **Choices**:
  - `topic_0` -> `resident_quarters_0` (`condition=always`)
    - **EN**: Did the explosion come from the AEC or from something else up here? | **ZH**: 屋顶那次爆炸是从旁边的AEC传过来的，还是宿舍区这边出了什么故障？ | **RU**: Тот взрыв крыши пришёл со стороны ЯЭК или случился прямо здесь наверху?
  - `topic_1` -> `resident_quarters_1` (`condition=always`)
    - **EN**: What's the exoskeleton next door used for? | **ZH**: 隔壁充能架上站着的那套外骨骼平时拿来干嘛？ | **RU**: Для чего используется экзоскелет на заряднике в соседнем помещении?
  - `close` -> `resident_quarters` (`condition=always`, `effect=close`)
    - **EN**: I'll let you get back to it. | **ZH**: 你先忙吧。 | **RU**: Не буду мешать.

##### `resident_quarters_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Came from a blown conduit junction right between our quarters and the east walkway. That's why the concrete along the top wall is still cracked all the way to the charger bay. Supervisor and White Tiger locked down the AEC safeguards right after so it won't repeat.
  - **ZH**: 是宿舍和东侧过道中间一段配电接驳口爆掉了。你看头顶那圈墙直到外骨骼充能位还满是裂纹，就是那时候震的。出事后主管和白虎立刻加装了全部核心保险装置，保证不会重演。
  - **RU**: Рванул распределительный стык прямо между жилым крылом и восточным проходом. Вон почему бетон вдоль потолка до самого зарядника до сих пор в трещинах. После этого Руководитель с Белым тигром сразу перебрали защиту ЯЭК, чтобы такого больше не повторилось.
- **Choices**:
  - `back` -> `resident_quarters` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_quarters_0` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

##### `resident_quarters_1` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hauling heavy core shielding and clearing snow drifts outside when even Grizzly muscles want backup. Most days it sits docked there soaking charge, unless Technology decides to manifest inside it and spend an hour re-bolting the joints for fun.
  - **ZH**: 用来搬运笨重的核心防护板，或者暴雪封门时顶风铲雪，连灰熊有时都需要那点额外助力的。平时它就乖乖插在底座上蓄电，除非哪天“技术”一时兴起钻进去显现，把每个关节螺丝拆装重拧着玩上一钟头。
  - **RU**: Чтобы таскать тяжёлые защитные плиты и расчищать сугробы у входа, когда даже силы гризли не хватает. Обычно он просто стоит на зарядке — пока Технологии не вздумают воплотиться прямо в нём и часок покрутить болты ради забавы.
- **Choices**:
  - `back` -> `resident_quarters` (`condition=always`)
    - **EN**: Ask something else. | **ZH**: 问点别的。 | **RU**: Спросить ещё.
  - `close` -> `resident_quarters_1` (`condition=always`, `effect=close`)
    - **EN**: Got it, thanks. | **ZH**: 明白，多谢。 | **RU**: Понятно, спасибо.

#### Chief Camp-Life Voice (`supervisor.*`)

- **`supervisor.changed`**
  - **EN**: Hold on, the transfer didn't go through. Double check you're still holding the matching item and the shared chest by the kitchen isn't packed full.
  - **ZH**: 慢着，这趟物资交接没能顺利完成。再确认一眼手里拿着的是不是对应物品，或者是不是厨房旁边的公共存储箱早塞满了。
  - **RU**: Постой, передача сейчас не сработала. Проверь, что в руке нужный предмет и в общем сундуке у кухни ещё осталось место.
- **`supervisor.choose_item`**
  - **EN**: Hold whatever supplies you're sharing in your hand and talk to me again. Keep enough back to keep yourself fed out on the tundra first.
  - **ZH**: 把打算分享的物资拿在手上，再跟我搭个话。先紧着你自己穿过雪原要吃的份额留够。
  - **RU**: Возьми припасы, которыми хочешь поделиться, в руку и поговори со мной снова. Сначала оставь себе всё нужное, чтобы не замерзнуть в тундре.
- **`supervisor.danger`**
  - **EN**: Hold up, there's trouble near the courtyard or one of the wings. Someone here is fending off a threat right now. Let's make sure the hall is clear first.
  - **ZH**: 等等，外院或者某条走廊那边出状况了。有位同伴眼下正忙着应付危险。咱们先过去把通道清出来再聊。
  - **RU**: Погоди, у внешнего входа или в одном из крыльев неспокойно. Кому-то из наших сейчас приходится отбиваться. Давай сперва разберёмся с угрозой.
- **`supervisor.declined`**
  - **EN**: Fair enough. Hang onto what you need for the road, we'll stretch what's in the kitchen chest.
  - **ZH**: 行，没问题。你自己赶路先紧着留用，厨房箱子里的存货我们先省着周转。
  - **RU**: Ничего страшного. Придержи запасы себе на дорогу — мы тут растянем то, что в сундуке.
- **`supervisor.donate`**
  - **EN**: Brought %s to add to our kitchen stock? We'll put it straight to use keeping folks warm and fed. How much do you want to drop off?
  - **ZH**: 带了%s准备添进厨房那边的储备吗？这批正好接济全楼取暖跟伙食。打算放多少进来？
  - **RU**: Принёс %s в наш общий сундук? Это сразу пойдёт в дело на тепло и провизию. Сколько хочешь оставить?
- **`supervisor.food_enough`**
  - **EN**: Between the kitchen smokers and that shared chest nearby, everyone has enough to keep moving right now.
  - **ZH**: 靠厨房烟熏炉和旁边的公共存储箱里的储备，大家眼下够撑着日常运转了。
  - **RU**: За счёт коптилен на кухне и общего сундука рядом еды всем сейчас хватает.
- **`supervisor.food_low`**
  - **EN**: The shared chest by the kitchen is running low on food. Out in this snow, everybody burns through calories faster than usual, so spare rations would go a long way.
  - **ZH**: 厨房旁边的公共存储箱快缺粮食了。在这片雪原讨生活，大家体能热量掉得比平时快得多，有富余口粮的话能救急。
  - **RU**: В общем сундуке у кухни заканчивается еда. На таком морозе все тратят энергию быстрее обычного, так что лишние припасы очень выручат.
- **`supervisor.food_plenty`**
  - **EN**: The shared chest by the kitchen is stocked up nicely right now. Grab a seat by the library hearth downstairs if you want to warm up with a meal.
  - **ZH**: 厨房旁边的公共存储箱里眼下囤得满满当当的。想歇会儿的话，去一楼大厅壁炉那儿坐着吃口热乎的吧。
  - **RU**: В общем сундуке у кухни сейчас отличный запас припасов. Заглядывай к камину в библиотеке на первом этаже, если хочешь перекусить в тепле.
- **`supervisor.greeting`**
  - **EN**: Step in out of the wind. Facility stocks and the chore board are right where I can check them anytime you're curious.
  - **ZH**: 快进屋挡挡雪风。想看设施物资余量或者找点活搭把手，随时跟我开口就行。
  - **RU**: Заходи с ветра. Запасы комплекса и список дел у меня под рукой, спрашивай, если что нужно.
- **`supervisor.handled`**
  - **EN**: Looks like someone already beat you to that checklist item. Folks move pretty fast when the hall warms up. Check if anything else is still open.
  - **ZH**: 清单上那一栏看来已经先让别人办妥了。走廊里暖和起来大伙手脚都挺麻利。看看列表里还有没有别的事空着吧。
  - **RU**: Похоже, с этим пунктом уже успел справиться кто-то другой. Когда в залах тепло, все управляются быстро. Проверь, остались ли ещё задачи.
- **`supervisor.meal`**
  - **EN**: Sounds good, I'll round up whoever isn't stuck on generator watch. Head down to the library hearth pillows and give everyone a minute to wander over.
  - **ZH**: 行啊，我去把没蹲着盯发电机的同伴叫一嗓子。先下楼去一楼壁炉旁的软垫待着，过半分钟有空的就全过来了。
  - **RU**: Отлично, сейчас позову тех, кто не занят у генераторов. Спускайся к подушкам у камина на первом этаже, через минуту все свободные подтянутся.
- **`supervisor.no_requests`**
  - **EN**: Everything in the facility is buttoned down cleanly for the moment. Go warm your hands by the library fireplace downstairs and rest a while.
  - **ZH**: 这会儿实验室里里外外一切妥当。去一楼图书馆壁炉旁坐坐歇会儿吧。
  - **RU**: Сейчас по всему комплексу всё прибрано и идёт по плану. Спустись к камину в зале на первом этаже и немного передохни.
- **`supervisor.outfit`**
  - **EN**: You've really earned your spot around here. Take a spare Field Coat of your own. Just don't touch the display models by the door, and keep that coat on if you step back out into the tundra.
  - **ZH**: 你在这里早就真真正正算作自己人了。拿件备用科考服自己留着穿吧。只是门口盔甲架那两套演示品还是别碰，真出大门踏进雪原记得穿上保暖。
  - **RU**: Ты здесь по праву стал своим человеком. Возьми себе запасную полевую куртку — только образцы на стойках у двери не трогай, и обязательно надевай её, когда снова выйдешь на мороз.
- **`supervisor.remember`**
  - **EN**: Folks here still remember how much weight you pulled around the facility. You don't have to lug supplies down the hallway just to earn a seat by the hearth.
  - **ZH**: 大伙都记得你先前帮实验室挑了多少重活。哪怕空手推门进来烤烤火，咱们照样欢迎。
  - **RU**: Здесь до сих пор помнят, как сильно ты выручил комплекс. Тебе вовсе не обязательно тащить ящики, чтобы погреться у нашего костра.
- **`supervisor.request`**
  - **EN**: The facility could use %s %s for upkeep. Bring them by whenever you swing in, and I'll sort them into the kitchen chest.
  - **ZH**: 实验室维护这边还需要 %s 份%s。回头顺路捎给我，我这就按类放进厨房那边的公共存储箱。
  - **RU**: Для нужд комплекса нам пригодится %s (%s). Занеси, как будет удобно, а я уберу всё в общий сундук у кухни.
- **`supervisor.requests`**
  - **EN**: A few chores around the facility could use an extra pair of hands. Pick whichever fits your schedule, nothing here has to wait on you alone.
  - **ZH**: 实验室里头正有几件杂活腾不出手来料理。捡顺手的接就行，不用都压在你一个人身上。
  - **RU**: В комплексе есть пара дел, где пригодится лишняя пара рук. Выбирай то, что по пути — всё сразу тащить на себе не нужно.
- **`supervisor.reverse_rite`**
  - **EN**: Hold steady for a second. Heavy claws are handy when rock gets in the way, though two nimble hands have their uses around delicate switches too. Back to your own shape now.
  - **ZH**: 站稳别动，一秒就好。厚爪子遇到硬石头的时候确实顺手，不过碰精密仪表还是纤细点的手更稳当。好啦，换回你自己的样子了。
  - **RU**: Постой смирно секунду. Тяжёлые когти выручают против твёрдого камня, но с тонкими приборами проворные руки тоже хороши. Всё, твой прежний вид на месте.
- **`supervisor.safe`**
  - **EN**: Perimeter sensors are quiet, the generators are steady, and nobody has poked the core glass today.
  - **ZH**: 外围感应器安静、发电机运转正常，今天也还没人去碰核心玻璃。
  - **RU**: Датчики по периметру молчат, генераторы гудят ровно, и стекло ядра сегодня в порядке.
- **`supervisor.thanks`**
  - **EN**: Much appreciated. I'll stow this in the shared chest by the kitchen right away. Go grab a warm seat by the library hearth whenever you'd like.
  - **ZH**: 太谢了。我这就放进厨房旁边的公共存储箱里。随时去一楼大厅壁炉那儿找块暖和软垫歇着吧。
  - **RU**: Большое спасибо. Сейчас же уберу в общий сундук у кухни. Иди грейся к камину в библиотеке, как захочешь отдохнуть.
- **`supervisor.transfer`**
  - **EN**: Your home marker is currently set to %s. Want to transfer your registration over to this facility instead? Nothing moves until you confirm.
  - **ZH**: 你的住址目前登记在%s那边。想把登记名册转到咱们这处实验室来吗？你没确认之前一切都不变动。
  - **RU**: Сейчас ты числишься в общине «%s». Хочешь перенести регистрацию сюда, в нашу лабораторию? Ничего не изменится, пока не подтвердишь.
- **`supervisor.watch`**
  - **EN**: If anything hostile slips inside past the keypad fence or circles our snow walls, help run it off. No need to hunt half the snowy plains for a fight, though. Just let me know when the perimeter clears.
  - **ZH**: 要是有带敌意的家伙钻过密码门溜进来、或者在雪墙外头乱转，麻烦帮手赶一赶。倒也不用专门跑满雪原去追怪。确认大门周边清净后回来跟我打声招呼就行。
  - **RU**: Если к нашему забору или за дверь пробьётся кто-нибудь враждебный, помоги отогнать. Но специально гоняться за ними по всей равнине не нужно — просто скажи, как вокруг станет тихо.
- **`supervisor.watch_thanks`**
  - **EN**: Thanks for sweeping the snowline around the gate. Everybody downstairs can put the wrenches down and breathe easy now.
  - **ZH**: 多谢帮忙把大门外头那片雪线清干净。楼下大伙总算能放下扳手长舒一口气了。
  - **RU**: Спасибо, что отогнал угрозу от ворот по снежному периметру. Теперь все внизу могут отложить инструменты и спокойно выдохнуть.
- **`supervisor.watch_wait`**
  - **EN**: Let's make sure the hallways and gate stay clear first. No sense going off duty if something is still scraping near the fence.
  - **ZH**: 咱们还是先确认走廊和大门外面完全消停再说。围墙边如果还有东西蹭来蹭去，可不能这时候收工。
  - **RU**: Давай сперва убедимся, что в коридорах и у ворот всё стихло. Нечего расслабляться, пока кто-то ещё скребётся вдоль забора.

#### Resident Camp-Life Voice (`supervisor_resident.*`)

- **`supervisor_resident.away`**
  - **EN**: Generator checks and broken skylights can wait until we get back to the lab. Let's focus on watching our footing out here first.
  - **ZH**: 发电机巡检和屋顶破洞等咱们回到实验室再接着管吧。眼下在外头先把脚下看稳。
  - **RU**: Проверки генераторов и битый потолок подождут, пока мы не вернёмся в лабораторию. Сперва смотри под ноги здесь снаружи.
- **`supervisor_resident.cautious_greeting`**
  - **EN**: Oh, hello there. Watch your step on the icy grating, and let me know if you're free for a quick errand.
  - **ZH**: 哦，你好。金属地格化雪挺滑的，脚下小心。有空的话帮我捎样东西呗？
  - **RU**: О, привет. Осторожнее на скользких решётках — подскажи, если свободен на одно мелкое дело.
- **`supervisor_resident.cautious_request`**
  - **EN**: Could you spare %s %s? Bring them over when you can, and steer clear of the live laser beams upstairs.
  - **ZH**: 方便的话能匀我 %s 份%s吗？随时拿来给我就行，路过二楼的时候离那些开着的激光束远一点。
  - **RU**: Найдётся ли у тебя %s (%s)? Принеси, как сможешь, и держись подальше от включённых лазеров наверху.
- **`supervisor_resident.cautious_thanks`**
  - **EN**: Thank you. Honestly, I'm mostly relieved you made it back past the keypad door before the frost kicked up.
  - **ZH**: 谢谢你。说真的，看着你在外头起寒潮之前安然走回密码门里我就松了口气。
  - **RU**: Спасибо. Честно говоря, я больше всего рад, что ты успел вернуться за дверь до нового заморозка.
- **`supervisor_resident.changed`**
  - **EN**: Hold on, that handover didn't finish cleanly. Check that you're still holding the matching item and there's still room left.
  - **ZH**: 等下，刚刚的交接没能成功完成。确认一下手里拿着对的东西，而且箱子或者包里还塞得下。
  - **RU**: Погоди, передача сейчас не завершилась. Проверь, что в руке нужный предмет и место ещё осталось.
- **`supervisor_resident.choose_item`**
  - **EN**: Hold whatever you're sharing in your hand so I can take a look. Keep enough food for yourself first before crossing the snow outside.
  - **ZH**: 把要给我的东西拿在手上，让我瞧一眼。先紧着留够你自己在雪原路上吃的。
  - **RU**: Возьми в руку то, чем хочешь поделиться, чтобы я видел. Сначала оставь еду себе на дорогу по снегам.
- **`supervisor_resident.danger`**
  - **EN**: Someone spotted trouble near the courtyard or lower halls. Let's get the place cleared out before taking it easy.
  - **ZH**: 有人看到外院或者楼下走廊那边出了状况。先把隐患清出去再安心歇着吧。
  - **RU**: Кто-то заметил угрозу во дворе или в нижних залах. Давай сначала наведём порядок, а потом передохнём.
- **`supervisor_resident.declined`**
  - **EN**: No trouble at all. The heater vents keep things tolerable over on this side anyway. Look after your own supplies out on the plains first.
  - **ZH**: 不碍事，反正这边通风口出来的暖气也还算撑得住。在雪原外头赶路先顾好自己的随身补给。
  - **RU**: Ничего страшного — с этой стороны дует тёплым из вентиляции, так что продержимся. Сначала прибереги припасы для себя на равнине.
- **`supervisor_resident.donate`**
  - **EN**: Brought %s to add? How much would you like to leave in the shared kitchen chest?
  - **ZH**: 带了%s准备添进去吗？打算往厨房旁边的公共存储箱里放多少？
  - **RU**: Принёс %s? Сколько хочешь положить в общий сундук у кухни?
- **`supervisor_resident.food_enough`**
  - **EN**: Kitchen stove and the shared chest next to it are holding steady for now. Grab a portion if your stomach starts growling.
  - **ZH**: 厨房炉灶和旁边公共存储箱里的存粮眼下还够吃。肚子饿了随时去拿一份吃。
  - **RU**: Плита на кухне и общий сундук рядом пока справляются. Бери порцию, если проголодаешься.
- **`supervisor_resident.food_low`**
  - **EN**: The shared chest near the kitchen is getting pretty thin on rations. Any cooked meals or extra meat you drop off will keep everyone going.
  - **ZH**: 厨房那边的公共存储箱快见底了。能塞点熟食或多余肉块进去的话，大伙能多撑好阵子。
  - **RU**: В общем сундуке возле кухни уже совсем мало припасов. Любая готовая еда или лишнее мясо нас сильно выручат.
- **`supervisor_resident.food_plenty`**
  - **EN**: The kitchen chest is packed tight with hot dishes and rations right now. Come sit by the library hearth if you want to eat together.
  - **ZH**: 厨房的公共存储箱里这会儿塞满了热食和口粮。想一块吃点东西的话，去一楼大厅壁炉旁边坐吧。
  - **RU**: Общий сундук на кухне сейчас набит горячими блюдами и припасами. Заглядывай к камину в библиотеке на первом этаже, если захочешь пообедать вместе.
- **`supervisor_resident.gesture`**
  - **EN**: The resident lets out a low puff in the chilly hall and points up the stairs toward the reactor walkway, though you can't make out their words yet.
  - **ZH**: 这位居民在微凉的走廊里朝手心哈了口气，抬手往上指了指通往反应堆过道的楼梯，但你暂时听不懂对方在说什么。
  - **RU**: Житель выдыхает в прохладном коридоре и показывает наверх, в сторону прохода к реактору, хотя слов ты пока не разбираешь.
- **`supervisor_resident.greeting`**
  - **EN**: Hey, got a second? There's a small job around my post here if you feel like helping out.
  - **ZH**: 嗨，有空吗？我负责这块正好有点小事需要搭把手。
  - **RU**: Эй, есть минутка?Тут у моего поста нашлась пара дел, если захочешь помочь.
- **`supervisor_resident.handled`**
  - **EN**: Looks like someone else already dropped those off, thanks anyway!
  - **ZH**: 好像已经有别人提前帮我送来了，不过还是谢谢！
  - **RU**: Похоже, кто-то уже успел занести это раньше, но всё равно спасибо!
- **`supervisor_resident.meal`**
  - **EN**: Sure thing, I'll head down once everybody else walks over. Pick a gray pillow by the downstairs campfire first.
  - **ZH**: 没问题，大伙动身的时候我也下楼去。先去一楼营火旁的灰软垫那儿坐着吧。
  - **RU**: Без проблем, подойду вниз, как все соберутся. Занимай серую подушку у костра на первом этаже.
- **`supervisor_resident.no_requests`**
  - **EN**: Got everything I need on my station for now. Go warm up by the library fireplace downstairs if you've got free time.
  - **ZH**: 我这儿眼下啥都不缺。有空闲的话去一楼大厅的壁炉旁烤烤火吧。
  - **RU**: На моём посту сейчас всё в порядке. Если есть время, спустись погреться к камину в библиотеке.
- **`supervisor_resident.playful_greeting`**
  - **EN**: Hey, you made it out of the snow! Finally warmed up a bit since stepping inside, huh? Always happy to chat for a minute.
  - **ZH**: 嘿，顶着雪走过来了啊！刚从外头进屋总算暖和点了吧？随时都能聊两句。
  - **RU**: Эй, выбрался всё-таки из снега! Отогрелся немного после снега? Всегда рад поболтать минутку.
- **`supervisor_resident.playful_request`**
  - **EN**: Think you could drop off %s %s for me? Pass them straight to me before someone snacks them by the campfire!
  - **ZH**: 能顺手帮我带 %s 份%s过来吗？直接递我手上啊，省得被楼下烤火那几个嘴馋顺走了！
  - **RU**: Сможешь занести мне %s (%s)? Отдай прямо в лапы, пока народ у костра всё не перехватил!
- **`supervisor_resident.playful_thanks`**
  - **EN**: Nice, you actually remembered! Wait till I tell Blue Dragon someone finally brought supplies without poking the AEC glass.
  - **ZH**: 漂亮，你还真记在心上了！等会儿我就去跟蓝龙炫耀，总算来了个既送物资又不去乱碰核心玻璃的朋友。
  - **RU**: Класс, ты и правда запомнил! Похвастаюсь Синему дракону, что к нам зашёл кто-то с припасами, кто не тычет в стекло ЯЭК.
- **`supervisor_resident.polite_greeting`**
  - **EN**: Hello, neighbor. Mind if I trouble you for one small favor around this floor?
  - **ZH**: 你好啊，同伴。如果方便的话，能不能顺手帮个小忙？
  - **RU**: Здравствуй. Не возражаешь, если попрошу об одном небольшом одолжении?
- **`supervisor_resident.polite_request`**
  - **EN**: Would you mind bringing %s %s when you have a moment? No rush at all, whenever you're passing back by.
  - **ZH**: 得空的时候能不能麻烦带 %s 份%s过来？一点不着急，路过的时候顺带给我就好。
  - **RU**: Не мог бы ты принести %s (%s), когда будет время? Совсем не срочно, занесёшь по пути.
- **`supervisor_resident.polite_thanks`**
  - **EN**: Thank you very much for bringing these. Feel free to thaw your hands by the library fireplace downstairs anytime.
  - **ZH**: 非常感谢你特意跑一趟送来。什么时候手冷了都可以去一楼大厅壁炉旁暖和暖和。
  - **RU**: Большое спасибо за помощь. Заходи в любое время погреть руки у камина на первом этаже.
- **`supervisor_resident.remember`**
  - **EN**: I still remember you hauling supplies in out of the snow for us. Always glad to see you around the lab.
  - **ZH**: 我还记得你之前顶着雪给我们搬补给进来的事。在实验室里常看见你真好。
  - **RU**: Я всё помню, как ты тащил сюда припасы с мороза. Всегда рад видеть тебя в комплексе.
- **`supervisor_resident.request`**
  - **EN**: Could use %s %s when you pass through the kitchen later. Still got plenty of space over here if you want to rest.
  - **ZH**: 待会儿顺道过厨房的话，还缺 %s 份%s。我这头也挺宽敞的，想歇脚随时过来。
  - **RU**: Пригодилось бы %s (%s), как будешь проходить через кухню. У нас тут ещё полно места, если захочешь отдохнуть.
- **`supervisor_resident.requests`**
  - **EN**: I could use a couple of things around this corner of the facility. Mind grabbing them if you pass by the storage shelves?
  - **ZH**: 我手头这边正缺几样小东西。回头顺路碰见的话能帮我捎一份吗？
  - **RU**: Мне как раз не помешает кое-что для работы на этом посту. Захватишь, если попадётся по пути?
- **`supervisor_resident.reverse_rite`**
  - **EN**: Supervisor upstairs can shift you back to your original shape whenever you want. Just hold still for a second.
  - **ZH**: 随时想变回原本的样子都可以上二楼找主管帮忙，站稳别动一秒就好。
  - **RU**: Руководитель наверху может вернуть тебе прежний облик в любой момент. Просто постой секунду смирно.
- **`supervisor_resident.safe`**
  - **EN**: Every corridor is quiet for now. Nothing scratching around outside the keypad fence either.
  - **ZH**: 各条走廊这会儿都很消停。外头密码门围栏外面也没有东西乱转。
  - **RU**: Во всех коридорах сейчас тихо. И за забором с кодовым замком никого не видно.
- **`supervisor_resident.seekchief`**
  - **EN**: (Supervisor up on the second-floor reactor overlook knows everything the facility needs. To talk with residents directly, you'll need a translator, telepathy, or a latex form.)
  - **ZH**: （你可以上二楼去反应堆过道那儿找主管，他清楚整座实验室眼下缺什么。要想跟这里的居民直接聊上话，得有翻译器、心灵感应或者胶兽形态才行。）
  - **RU**: （Поднимись на второй этаж к проходу у реактора — Руководитель знает, что нужно всему комплексу. Чтобы говорить с местными напрямую, понадобится переводчик, телепатия или латексная форма.）
- **`supervisor_resident.thanks`**
  - **EN**: Thanks! Leaving these right where I can reach them saves me three trips downstairs in this cold. Grab a pillow by the library hearth downstairs whenever you need to thaw out.
  - **ZH**: 多谢！放我手边省得顶着风冷往楼下多跑三趟了。哪天冻着了尽管去一楼大厅壁炉旁挑个软垫烤火。
  - **RU**: Спасибо! Оставил прямо под рукой — избавил меня от трёх лишних вылазок вниз по холоду. Бери подушку у камина внизу, как захочешь согреться.
- **`supervisor_resident.transfer`**
  - **EN**: Your home marker is set to %s right now. Want to move in here? Talk to Supervisor up on the second-floor reactor walkway.
  - **ZH**: 你现在的登记住处还是%s。想转到这座实验室来的话，上二楼去反应堆过道找主管就行。
  - **RU**: Сейчас твой дом отмечен как «%s». Хочешь перевестись сюда? Поговори с Руководителем на втором этаже у прохода к реактору.
- **`supervisor_resident.watch`**
  - **EN**: If anything hostile breaks through the snow gate outside, help push it back out before it hits the equipment. Just let me know once the hallways are clear.
  - **ZH**: 要是有不友好的家伙撞进外头的雪地铁门，赶在它们碰坏设备前帮手拦一拦。确认走廊消停之后告诉我一声就行。
  - **RU**: Если с мороза за ворота прорвётся кто-нибудь враждебный, помоги оттеснить его от оборудования. Просто дай знать, когда вокруг станет спокойно.
- **`supervisor_resident.watch_thanks`**
  - **EN**: Thanks for driving those threats away from the outer fence. Whole floor feels a whole lot calmer now.
  - **ZH**: 谢了，把那些乱晃的危险家伙从外围栏赶出去了。这下整层楼都清净安心多了。
  - **RU**: Спасибо, что отогнал угрозу от внешнего забора. Теперь на всём этаже куда спокойнее.
- **`supervisor_resident.watch_wait`**
  - **EN**: Let's make sure the gate and hallways are safe first. No sense chasing trouble deeper onto the snowy plains.
  - **ZH**: 先确认大门和走廊彻底安全再说。没必要为了我们往雪原深处追怪打架。
  - **RU**: Давай сперва убедимся, что у ворот и в коридорах безопасно. Незачем гоняться за неприятностями далеко в снегах.

#### Narrative Fragments (English Only)

- **`changed:note`** @ `(13, 3, 40)` — Unlocks `clue:violet_code`, `clue:notes`, `clue:archives`
  ```text
  ACCESSWAY TO EXTERNAL TUNDRA CODE HAS BEEN UPDATED TO 3579 AFTER A CARELESS RESEARCHER LET A LOOSE SUBJECT IN BY ACCIDENT. DO NOT MAKE THE SAME MISTAKE.
  ```
- **`changed:note`** @ `(17, 3, 40)` — Unlocks `clue:notes`, `clue:archives`
  ```text
  REMINDER!
  IF YOU ARE LEAVING THE FACILITY GROUNDS FOR ANY REASON, BE SURE TO EQUIP A FIELD COAT FOR STUDY AND FOR YOUR OWN SAFETY. DO NOT LOSE YOUR HUMANITY.
  ```
- **`changed:clipboard`** @ `(21, 3, 31)` — Unlocks `clue:notes`, `clue:archives`
  ```text
  TODO:
  - Find more books
  - Food, always gotta get food for the others! We went through a whole sheep's worth of calories in a day last week.
  - Search for that rumored human people keep whispering about. Maybe they'll make a good host!
  ```
- **`changed:note`** @ `(28, 3, 22)` — Unlocks `clue:box_note`, `clue:notes`, `clue:archives`
  ```text
  Friendly request: please stop stealing "1000 Uses for Boxes" Snep, it's getting tiring to climb into the vents all the time just to get it back.
  ```
- **`changed:note`** @ `(13, 10, 37)` — Unlocks `clue:notes`, `clue:archives`
  ```text
  LASER FIELD TESTING IN PROGRESS, DO NOT COME INTO CONTACT WITH THE INTERNALS OF THE MACHINE WHILE ACTIVE IF YOU WISH TO PRESERVE YOUR HUMANITY.
  ```

### 2.7 Burrow (`burrow`)

- **Biome / Placement**: Snowy Taiga / Snowy Slopes (Overworld)
- **Resident Dialogue**: Built-in residents use station-specific root nodes by `DialogueRole` (`resident_upstairs`, `resident_farm`, `resident_pond`, `resident_workshop`, `resident_library`, `resident_dorm`, defined in `gray_fox_chief.json`) and share the `burrow_resident` camp-life voice.

#### Chief: Burrow Steward (`changed_synergy:latex_manul`) (`manul_chief.json`, camp voice `steward`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: …She let a human downstairs. Of course she did.Wipe the snow off your boots before you step on the rug. There's hot meat pie and fresh greens on the counter if you're hungry; help yourself and don't leave empty cans by the stoves.
  - **ZH**: ……她居然放了个人类下楼。我就知道。踩地毯前先把靴子上的雪蹭掉。饿了的话吧台上有热肉派和蔬菜沙拉，自己拿，别把空罐头留在灶台边。
  - **RU**: …Она впустила вниз человека. Ну конечно.Вытри снег с сапог, прежде чем наступать на ковер. Если голоден, на стойке есть горячий мясной пирог и свежая зелень; угощайся, только не оставляй пустые банки у плит.
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Boots are freezing, nose is red, probably walked three miles in the snow. Better put the soup pot back on low heat…)
  - **ZH**: （靴子都冻透了，鼻子也冻红了，大概在雪里走了好几里路。还是把汤锅放回小火上热着吧……）
  - **RU**: (Сапоги промерзли, нос красный — наверное, три мили по снегу шел. Поставлю-ка кастрюлю с супом обратно на слабый огонь…)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Keep your voice down near the stairs; people sleep at odd hours here.If you're looking for food, the counter is open. If you're looking for small talk, she's the one upstairs.
  - **ZH**: 楼梯附近小点声，这儿的人作息都不太一样。找吃的话吧台随便拿。要是想找人闲聊，楼上那位更乐意陪你。
  - **RU**: Возле лестницы потише — здесь все спят в разное время.Если ищешь еду, стойка открыта. Если ищешь с кем поболтать, это к ней наверх, на чердак.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Looks quiet enough. Good. No sudden shouting near the soup pot.)
  - **ZH**: （看着还算安静。不错。别在汤锅边一惊一乍的就行。）
  - **RU**: (Вроде спокойный. Хорошо. Главное — без резких криков возле кастрюли с супом.)
- **Choices**:
  - `manul_about` -> `about` (`condition=always`)
    - **EN**: You handle the inside of the Burrow, right? | **ZH**: 地穴里面这摊事都是你在管，对吧？ | **RU**: Внутри Норы всем заправляешь ты, верно?
  - `manul_home` -> `home` (`condition=always`)
    - **EN**: How do you keep everyone fed down here without going outside? | **ZH**: 你们不出门的话，平时怎么顾得上这么多人吃饭？ | **RU**: Как вы кормите всех здесь внизу, не выходя наружу?
  - `manul_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: The gray fox told me you transformed just to stay with her. | **ZH**: 灰狐跟我说，你当初转化就是为了陪在她身边。 | **RU**: Серая лиса сказала, что ты прошел превращение только ради того, чтобы остаться с ней.
  - `manul_clue_terminal` -> `clue_terminal` (`condition=clue:burrow_terminal`)
    - **EN**: I read the old file you left on the computer downstairs. | **ZH**: 我看到你留在楼下电脑里的那份旧文件了。 | **RU**: Я прочитал старый файл, который ты оставил на компьютере внизу.
  - `manul_clue_rules` -> `clue_rules` (`condition=clue:burrow_rules`)
    - **EN**: I read your kitchen rules note. Did she write the part at the bottom? | **ZH**: 我看了你写的厨房规矩。最底下那行是她添的吧？ | **RU**: Я читал твои правила на кухне. Нижнюю строчку приписала она?
  - `manul_enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Is there a corner for me down here in the Burrow? | **ZH**: 这地穴里能给我留个角落安顿吗？ | **RU**: Найдется ли для меня уголок в Норе?
  - `manul_membership` -> `membership` (`condition=member`)
    - **EN**: About my staying down here… | **ZH**: 关于我住在地穴里的事…… | **RU**: Насчет моей жизни в Норе…
  - `manul_goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Back again. Feet wiped? Good. The stoves are lit if you need a warm corner.
  - **ZH**: 又来了。脚擦干净了？行。炉灶烧着呢，想找地方烤火就自便。
  - **RU**: Снова ты. Ноги вытер? Хорошо. Плиты затоплены, если нужно погреться.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Doesn't slam doors, doesn't pester the kids in the dorms. Honestly, better houseguest than half the people I used to work with.)
  - **ZH**: （不摔门，也不去烦宿舍里那些小孩。说实话，比我以前一半的同事都懂事。）
  - **RU**: (Дверями не хлопает, к ребятам в спальнях не лезет. Честно говоря, гость куда лучше половины моих бывших коллег.)
- **Choices**:
  - `manul_about` -> `about` (`condition=always`)
    - **EN**: You handle the inside of the Burrow, right? | **ZH**: 地穴里面这摊事都是你在管，对吧？ | **RU**: Внутри Норы всем заправляешь ты, верно?
  - `manul_home` -> `home` (`condition=always`)
    - **EN**: How do you keep everyone fed down here without going outside? | **ZH**: 你们不出门的话，平时怎么顾得上这么多人吃饭？ | **RU**: Как вы кормите всех здесь внизу, не выходя наружу?
  - `manul_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: The gray fox told me you transformed just to stay with her. | **ZH**: 灰狐跟我说，你当初转化就是为了陪在她身边。 | **RU**: Серая лиса сказала, что ты прошел превращение только ради того, чтобы остаться с ней.
  - `manul_clue_terminal` -> `clue_terminal` (`condition=clue:burrow_terminal`)
    - **EN**: I read the old file you left on the computer downstairs. | **ZH**: 我看到你留在楼下电脑里的那份旧文件了。 | **RU**: Я прочитал старый файл, который ты оставил на компьютере внизу.
  - `manul_clue_rules` -> `clue_rules` (`condition=clue:burrow_rules`)
    - **EN**: I read your kitchen rules note. Did she write the part at the bottom? | **ZH**: 我看了你写的厨房规矩。最底下那行是她添的吧？ | **RU**: Я читал твои правила на кухне. Нижнюю строчку приписала она?
  - `manul_enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Is there a corner for me down here in the Burrow? | **ZH**: 这地穴里能给我留个角落安顿吗？ | **RU**: Найдется ли для меня уголок в Норе?
  - `manul_membership` -> `membership` (`condition=member`)
    - **EN**: About my staying down here… | **ZH**: 关于我住在地穴里的事…… | **RU**: Насчет моей жизни в Норе…
  - `manul_goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Somebody has to keep the stoves running, check the ice lockers, and make sure the pantry doesn't go empty. She's good with outsiders; I'm better with a cutting board and quiet rooms. She's good with outsiders; I'm better with a cutting board and quiet rooms.
  - **ZH**: 总得有人管着炉灶、检查冷库、别让粮仓见底。她擅长应付外人，我更适合守着砧板和安静的屋子。她擅长应付外人，我更适合守着砧板和安静的屋子。
  - **RU**: Кому-то надо следить за плитами, проверять ледники и смотреть, чтобы кладовая не пустела. Она хорошо ладит с посторонними, а у меня лучше получается с разделочной доской и тишиной. Она хорошо ладит с посторонними, а у меня лучше получается с разделочной доской и тишиной.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Also nobody else remembers to turn the lower furnace off before going to bed.)
  - **ZH**: （而且其他人睡觉前从来记不住关掉楼下的熔炉。）
  - **RU**: (К тому же никто больше не помнит выключать нижнюю печь перед сном.)
- **Choices**:
  - `manul_gray_fox` -> `gray_fox` (`condition=always`)
    - **EN**: You two seem like complete opposites. How do you put up with each other? | **ZH**: 你们俩性格看着完全相反，平时是怎么处得来的？ | **RU**: Вы кажетесь полными противоположностями. Как вы уживаетесь?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `gray_fox` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Put up with her? She's the reason I don't have to talk to travelers at the front door.Sure, she tracks snow across the floor and ambushes me with hugs while I'm holding a soup ladle. But she also spent years stuck in a bed wishing she could walk five minutes outside. If she wants to drag half the snowdrift indoors on her tail, I can mop the floor.
  - **ZH**: 忍受她？要不是她顶着，门口那些旅人全得我来应付。她是老往地板上带雪，还老趁我端汤勺的时候突然扑过来抱我。但她从前有好几年被困在病床上，连去外头走五分钟都做不到。现在她要是想用尾巴带半堆雪进来，地板我拖就是了。
  - **RU**: Уживаемся? Благодаря ей мне не приходится разговаривать с путниками у входа.Да, она вечно наносит снег на пол и бросается обниматься прямо тогда, когда у меня в руках половник. Но она годами пролежала в постели, мечтая просто пройтись пять минут по улице. Если ей хочется притащить на хвосте полсугроба — я могу и пол протереть.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Do not let her hear me say that out loud or she'll never wipe her paws again.)
  - **ZH**: （这话可不能让她听见，不然她以后绝对再也不在地毯上蹭雪了。）
  - **RU**: (Только бы она этого не услышала, иначе вообще перестанет вытирать лапы.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: We designed this place so nobody has to leave if they're having a bad week. Right past the kitchen doorway are the crop beds and the animal pen: wheat, potatoes, carrots, beets, plus a couple cows, sheep, and chickens.Down another level there's a fish pond and a cooled workshop. And before you ask, yes, we run warm-air heating alongside all the ice and snow on purpose. Thick winter fur overheats fast in a closed room, so the ice keeps the temperature balanced without letting the pipes freeze. And before you ask, yes, we run warm-air heating alongside all the ice and snow on purpose. Thick winter fur overheats fast in a closed room, so the ice keeps the temperature balanced without letting the pipes freeze.
  - **ZH**: 我们把这儿修成这样，就是为了谁这礼拜状态不好，可以彻底不出门。出厨房门就是农田和畜栏，从小麦、土豆、胡萝卜、甜菜，到几头牛羊和鸡应有尽有。再往下一层有鱼池和铺了冰的工坊。对了，省得你问，我们同时开着暖风又摆着这么多冰雪，是故意的。一身厚冬毛待在封闭屋子里很容易热过头，靠冰雪把温度压平，既不会闷热，也不至于把水管冻住。对了，省得你问，我们同时开着暖风又摆着这么多冰雪，是故意的。一身厚冬毛待在封闭屋子里很容易热过头，靠冰雪把温度压平，既不会闷热，也不至于把水管冻住。
  - **RU**: Мы устроили тут все так, чтобы в плохую неделю можно было вообще не выходить наружу. Сразу за дверью кухни — грядки и загон: пшеница, картофель, морковь, свекла, плюс пара коров, овец и кур.Этажом ниже есть пруд с рыбой и охлаждаемая мастерская. И да, пока не спросил: обогреватели и весь этот лед со снегом работают вместе намеренно. В густой зимней шерсти в закрытом помещении быстро становится жарко, так что лед держит равновесие, не давая ни перегреться, ни заморозить трубы. И да, пока не спросил: обогреватели и весь этот лед со снегом работают вместе намеренно. В густой зимней шерсти в закрытом помещении быстро становится жарко, так что лед держит равновесие, не давая ни перегреться, ни заморозить трубы.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (One day I'm going to find out who keeps putting potted wither roses on my bar counter. Pretty sure it was the wyvern.)
  - **ZH**: （我迟早要查出来是谁老往我吧台上摆盆栽凋灵玫瑰。十有八九是那只双足飞龙干的。）
  - **RU**: (Когда-нибудь я все-таки выясню, кто вечно ставит на мою стойку горшки с розой визера. Почти уверен, что виверна.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `past` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: It wasn't complicated. She needed the transformation to live. I didn't care about immortality or turning into an animal, and I definitely didn't want to deal with the clinic paperwork.I just knew that if she crossed over and I stayed behind, we'd eventually end up in different worlds, or I'd get old while she didn't. So I chose transformation myself as well. Best stubborn decision I ever made.
  - **ZH**: 其实没那么复杂。她需要转化才能活下去。我对永生或者变成动物一点兴趣都没有，更懒得填诊所那堆表格。我只是清楚，要是她过去了而我留在原地，我们迟早会变成两个世界的人，或者我会变老而她不会。所以我自己也选了转化。这辈子我犯过最值的一次倔。
  - **RU**: Все было просто. Ей нужно было превращение, чтобы выжить. Мне не было дела ни до бессмертия, ни до превращения в зверя, а уж клинические бумаги я тем более терпеть не мог.Я просто знал: если она перейдет на ту сторону, а я останусь, рано или поздно мы окажемся в разных мирах, или я состарюсь, а она нет. Так что я и сам пошел на превращение. Самое лучшее упрямое решение в моей жизни.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Thick fur is nice in winter, though. And standing on my own tail when the floor tiles are cold actually works.)
  - **ZH**: （不过冬天厚毛确实暖和。地砖凉的时候把脚踩在自己尾巴上，还真挺管用。）
  - **RU**: (Зато густая шерсть зимой — вещь хорошая. И вставать лапами на собственный хвост, когда плитка холодная, реально помогает.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `clue_terminal` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: …You went through the double doors at the bottom of the stairs, right into our bedroom, and opened the computer.I'm not mad, just surprised the screen still boots. I typed that the week we finished setting up our room; between the warm-air heating and the fact that a Manul has the thickest fur in the whole Burrow, I needed that whole corner packed with ice and snow just to keep from overheating at night. Close the door on your way out next time, it keeps the temperature balanced.
  - **ZH**: ……你穿过楼梯底下的双开门，直接进了我们俩的卧室，还把那台电脑打开了。倒没生气，只是没想到屏幕居然还能点亮。那段字是刚把卧室收拾好那周我顺手打的；屋里开着暖风，而兔狲的毛又是全地穴最厚的，角落里不堆够那么多冰雪，我晚上非热坏不可。下次出去记得随手关门，好维持住里面的温度平衡。
  - **RU**: …Ты прошел за двойную дверь внизу лестницы, прямо в нашу спальню, и включил компьютер.Я не злюсь, просто удивлен, что экран все еще включается. Я напечатал это в ту неделю, когда мы обустраивали комнату: с обогревателями и самым густым мехом во всей Норе мне пришлось забить весь угол льдом и снегом, иначе ночью просто не продохнуть от жары. В следующий раз закрывай за собой дверь, чтобы не сбивать баланс.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `clue_rules` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Yes, she scribbled over my fourth rule ten minutes after I pinned it up.Look, I don't care if people want privacy, I just don't want anyone knocking over my crate stacks in the storage room every time a traveler knocks. If I leave the pie out on the bar, everybody wins.
  - **ZH**: 对，我刚贴上去十分钟，她就把第四条给涂了。听着，大家想图清静我没意见，我只是不想每次一有旅人敲门，储藏室里那堆箱子就被撞翻。我把派直接摆在吧台上，谁都省心。
  - **RU**: Да, она чиркнула поверх моего четвертого пункта через десять минут после того, как я его повесил.Слушай, я не против личного пространства, просто не хочу, чтобы в кладовой каждый раз сносили стопки ящиков, стоит кому-то постучать. Оставляю пирог на стойке — и все довольны.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: She usually handles newcomers, but since you're standing right in my kitchen: welcome.Find a free cot down the hallway. Keep the noise down, wipe your paws, and don't push open closed doors. Simple as that.
  - **ZH**: 平时打理新人是她的事，但既然你人都站到我灶台前了：安顿下来吧。去走廊那头找张空床。别大声喧哗，记得在地毯上蹭干净雪，别乱动关着的门。就这么点基本规矩。
  - **RU**: Обычно с новичками говорит она, но раз уж ты стоишь тут, у моих плит — устроим тебя.Свободную койку найдешь дальше по коридору. Главное: не шуми, вытирай лапы и не ломись в закрытые двери. Вот и всё.
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: If you want a quiet bed down here, stay as you are. Humans don't leave fur in my soup, which is a massive relief.Though if you'd rather move in as a Manul (temporarily or permanently), I can manage that too. Up to you.
  - **ZH**: 要是想找个清静地方落脚，保持现在的样子就好。人类至少不会往我汤锅里掉毛，说实话这省了我不少心。话虽如此，你要是想体验一下当兔狲的生活（暂时体验或一直保持），我也能搞定。全随你便。
  - **RU**: Ищешь тихий угол? Оставайся человеком. От вас хоть шерсть в суп не летит, и на том спасибо.Впрочем, если хочешь обернуться манулом, на время или навсегда, я могу это устроить. Решай сам.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Not today. Everyone downstairs is on edge, and I'm not adding anyone new until the house settles back into a quiet rhythm. Come back later.
  - **ZH**: 今天不行。大家心里都不踏实，在大伙儿恢复平静之前，我不会再添新人。过阵子再说吧。
  - **RU**: Не сегодня. Народу сейчас не до новых лиц, а я не стану селить кого-то еще, пока у нас снова не станет тихо. Загляни попозже.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: I'll settle in just as I am. | **ZH**: 我就保持现在的样子住下来。 | **RU**: Я останусь у вас в своей текущей форме.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll stay human and settle down. | **ZH**: 我想以人类的身份住下来。 | **RU**: Я останусь человеком и буду жить с вами.
  - `explain_form` -> `explain_form` (`condition=can_reversible`)
    - **EN**: What is it like to take on the form of a Manul? | **ZH**: 变成一只兔狲到底是什么体验？ | **RU**: Каково это — принять дух и облик манула?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Your mind and memories remain untouched, and sharing this form doesn't drain me.A Manul isn't made for running long distances. You get a dense, sturdy frame, small round ears set low so you don't bump your head in cramped tunnels, and the thickest fur of anyone down here: great out in the snow, though indoors you'll want some ice and snow nearby so you don't get too warm. You get a dense, sturdy frame, small round ears set low so you don't bump your head in cramped tunnels, and the thickest fur of anyone down here: great out in the snow, though indoors you'll want some ice and snow nearby so you don't get too warm.
  - **ZH**: 你的记忆和心智完全不会改变，分你这份形态也不会伤到我。兔狲不是用来长跑的。身形会更敦实，小圆耳朵位置偏下，在地道里不易碰头，而且毛发是这下面所有人里最厚的：在雪地里很舒服，不过待在室内时，身边最好放些冰雪降降温，免得太热。身形会更敦实，小圆耳朵位置偏下，在地道里不易碰头，而且毛发是这下面所有人里最厚的：在雪地里很舒服，不过待在室内时，身边最好放些冰雪降降温，免得太热。
  - **RU**: Память и разум останутся с тобой, а мне эта передача формы не пойдет во вред.Тело манула — не для маршей. Ты станешь коренастым, плотным, с низко посаженными ушками, чтоб не задевать низкие потолки, и с самым густым мехом во всей Норе: на снегу отлично, а вот в помещении рядом лучше держать лед и снег, чтобы не перегреться. Ты станешь коренастым, плотным, с низко посаженными ушками, чтоб не задевать низкие потолки, и с самым густым мехом во всей Норе: на снегу отлично, а вот в помещении рядом лучше держать лед и снег, чтобы не перегреться.
- **Choices**:
  - `reversible` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the Manul form temporarily and join the den. | **ZH**: 我先试试可逆的兔狲形态，在地穴里住下。 | **RU**: Давай поносим форму манула временно и присоединимся к вам.
  - `permanent_menu` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: Make it permanent and count me in. | **ZH**: 让我永久变成兔狲，算我一份吧。 | **RU**: Сделай превращение постоянным и зачисли меня.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll just stay human and move in. | **ZH**: 我还是保持人类身份住下来。 | **RU**: Я лучше просто поселюсь как человек.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Think carefully before giving your final word. Permanent means this dense Manul fur becomes your body for good; stepping outside the Burrow won't change you back, and basic remedies won't undo it.You can still choose to keep it temporary or stay human. Are you certain you want this forever?
  - **ZH**: 点头前先仔细斟酌好。选了永久，意味着这身厚实的兔狲皮毛将永远属于你；即便离开地穴也不会改变，常规手段也没法变回去。你现在依然可以改选可逆形态或者保留人类身体。你真的确定要永久如此吗？
  - **RU**: Подумай хорошо, прежде чем согласиться. Постоянное превращение означает, что мех манула станет твоим телом навечно: уход из Норы не отменит его, и обычные средства не вернут всё назад.Ты всё ещё можешь выбрать временный вариант или остаться человеком. Ты действительно хочешь этого навсегда?
- **Choices**:
  - `confirm` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I'm sure. Make it permanent. | **ZH**: 是的，我确定，永久转化吧。 | **RU**: Да, я уверен. Делай навсегда.
  - `rethink` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over once more. | **ZH**: 让我再想想。 | **RU**: Дай мне еще обдумать.

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Settled. You've got a place here. The shared bin is by the door, empty cots are downstairs, and there's a stool at the counter whenever you want a hot meal in peace.
  - **ZH**: 好，定下了。你在地穴里有落脚处了。公共粮食箱在门口，空床在楼下，想安静吃口热饭的时候，吧台随时有你的空凳子。
  - **RU**: Готово. У тебя есть свой уголок. Общий сундук — у входа, койки — внизу, а за стойкой всегда найдется табурет, когда захочешь спокойно поесть.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Better keep a pair of thick socks in the reserve chest. Humans get cold fast down here.)
  - **ZH**: （回头得在公共箱里放双厚袜子。人类在下面怕冷。）
  - **RU**: (Надо положить в общий сундук пару толстых носков. Люди тут внизу быстро мерзнут.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Alright, you're home. Pick an open room downstairs, take what you need from the pantry, and enjoy the quiet.
  - **ZH**: 行，你算在这儿落脚了。去楼下选间没人住的房间，需要食物去公用箱里拿，享受属于你的清静吧。
  - **RU**: Ладно, устраивайся. Выбирай свободную комнату внизу, бери еду из общего сундука и наслаждайся покоем.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There you go. Heavy coat, sturdy balance. If the stone floor feels cold underfoot, try resting your paws on your tail… Don't laugh, it actually works.Welcome to the Burrow. Take a seat at the bar whenever you're hungry.
  - **ZH**: 成了。毛发厚实，站得也稳。如果觉得石地板凉，就把脚踩在尾巴上……别笑，这招真管用。欢迎来到地穴。饿了就去吧台找张凳子坐下。
  - **RU**: Ну вот. Густая шуба, уверенный шаг. Если каменный пол покажется холодным, попробуй встать лапами на свой хвост… Не смейся, работает.Добро пожаловать в Нору. Занимай место у стойки, когда проголодаешься.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Huh. Another round cat in the kitchen. At least this one won't knock the pans off the shelf.)
  - **ZH**: （嗯，厨房里又多了只圆猫。起码这位不会把架子上的锅碰掉。）
  - **RU**: (Хм. Еще один круглый кот на кухне. Этот хотя бы не станет смахивать сковородки с полки.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: There you go. Heavy coat, sturdy balance. If the stone floor feels cold underfoot, try resting your paws on your tail… Don't laugh, it actually works.Welcome to the Burrow. Take a seat at the bar whenever you're hungry.
  - **ZH**: 成了。毛发厚实，站得也稳。如果觉得石地板凉，就把脚踩在尾巴上……别笑，这招真管用。欢迎来到地穴。饿了就去吧台找张凳子坐下。
  - **RU**: Ну вот. Густая шуба, уверенный шаг. Если каменный пол покажется холодным, попробуй встать лапами на свой хвост… Не смейся, работает.Добро пожаловать в Нору. Занимай место у стойки, когда проголодаешься.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Huh. Another round cat in the kitchen. At least this one won't knock the pans off the shelf.)
  - **ZH**: （嗯，厨房里又多了只圆猫。起码这位不会把架子上的锅碰掉。）
  - **RU**: (Хм. Еще один круглый кот на кухне. Этот хотя бы не станет смахивать сковородки с полки.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're a resident here. The common food chest and open cots are yours to use; bedroom trunks remain private. If you ever decide to move on, just let me know so we can part on good terms.
  - **ZH**: 你是这里的住户了。公共食物箱和空床位你随时可以用，别乱动其他人卧室里的私人物品就行。哪天如果你打算离开，告诉我一声，咱们平平安安地道别。
  - **RU**: Ты здесь жилец. Общий сундук с провизией и свободные койки — в твоем распоряжении, но личные вещи в спальнях не трогай. Если соберешься уходить — предупреди, расстанемся по-хорошему.
- **Choices**:
  - `leave_menu` -> `confirm_leave` (`condition=member`)
    - **EN**: I need to pack my things and leave the Burrow. | **ZH**: 我得收拾行装离开地穴了。 | **RU**: Мне нужно собраться и покинуть Нору.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Are you certain? Departing means you give up your share of the common storage, though you're always welcome to step back in for a meal. Leaving won't change a permanent form back.
  - **ZH**: 真的想好了？离开就意味不能随意使用这里的公共粮仓了，不过想回来吃口热饭随时欢迎。离开也不会让永久形态变回去。
  - **RU**: Уверен? Уходя, ты отказываешься от доли в общем сундуке, хотя зайти поесть сможешь всегда. Постоянный облик от ухода не изменится.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=member`, `effect=leave`)
    - **EN**: Yes, I'm taking my leave. | **ZH**: 是的，我这就告辞了。 | **RU**: Да, я ухожу.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Safe travels. Grab a couple of rations for the road if you need them, and stay warm out there in the cold.
  - **ZH**: 祝你一路平安。带上两份干粮备用，外面冷，多保重。
  - **RU**: Бывай. Возьми пару пайков на дорогу, если надо, и смотри не замерзни.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll leave you to your cooking. | **ZH**: 不打扰你做饭了。 | **RU**: Не буду мешать тебе готовить.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Close the door behind you if you head downstairs. Keeps the draft out.
  - **ZH**: 下楼的话随手带上门，省得灌风。
  - **RU**: Прикрой за собой дверь, если пойдешь вниз. Чтобы не сквозило.
- **Choices**:
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Take care. | **ZH**: 保重。 | **RU**: Бывай.

#### Chief Camp-Life Voice (`steward.*`)

- **`steward.changed`**
  - **EN**: Hold on—items didn't check out right. Make sure you're holding what you meant to hand over and the kitchen chest still has space.
  - **ZH**: 等下——东西对不上号，没法收进库里。 确认手里拿着对应的东西，还有厨房补给箱没塞满。
  - **RU**: Погоди — с вещами что-то не сошлось. Проверь, что держишь в руке нужный предмет и в сундуке на кухне есть место.
- **`steward.choose_item`**
  - **EN**: Put whatever supplies you're sparing in your hand so I can weigh them out. Keep enough back to stay fed out on the snow.
  - **ZH**: 把你要匀出来的补给拿在手上，我才好点数入账。 外头雪大，你自己路上该留的干粮别全捐光了。
  - **RU**: Возьми в руку те припасы, которыми готов поделиться, чтобы я прикинул. Оставь себе достаточно еды на обратную дорогу по снегу.
- **`steward.danger`**
  - **EN**: Hold on—something stirred right outside the entrance or near the farm gate. Let's make sure the snow ridge and lower stairs are clear before anyone sits down.
  - **ZH**: 慢着——外面雪坡入口或者畜栏那头有点不对劲。 先把惹事的家伙清理走，大伙儿再安心回去歇着。
  - **RU**: Погоди — у верхнего входа или возле загона кто-то зашевелился. Сначала расчистим склон и лестницу, потом уже можно перевести дух.
- **`steward.declined`**
  - **EN**: Suit yourself. Keep what you need for the trail; our ice lockers can stretch another couple days.
  - **ZH**: 随你便。 留着路上垫肚子吧，冷库里的囤货还够顶两天。
  - **RU**: Как знаешь. Прибереги на дорогу — наши ледники ещё протянут пару дней.
- **`steward.donate`**
  - **EN**: Brought %s for the shared kitchen chest? Plenty of cold mouths below stairs will put it to good use. How much are you leaving?
  - **ZH**: 带了%s准备添进公共补给箱吗？ 楼下十来张嘴可少不了热食，打算放多少进来？
  - **RU**: Принёс %s в наш общий кухонный сундук? Внизу это точно не пропадёт зря. Сколько оставишь?
- **`steward.food_enough`**
  - **EN**: Between the soup pot, smokers, and that double chest by the kitchen door, nobody here is going hungry right now.
  - **ZH**: 灶上的汤锅、烟熏炉加上厨房门口那口大箱子，眼下地穴里倒还宽裕。
  - **RU**: С кастрюлей на плите, коптильнями и тем большим сундуком у входа на кухню голод нам пока не грозит.
- **`steward.food_low`**
  - **EN**: The shared supply chest by the kitchen doorway is getting thin. Thick fur burns plenty of heat in this frost, so extra cooked plates or rations would help everyone out.
  - **ZH**: 厨房入口那只公共补给箱快见底了。 天寒地冻的大家都很耗体力，要是能顺手添点热菜或耐放口粮就省心了。
  - **RU**: Общий сундук у входа на кухню ощутимо пустеет. На морозе все быстро тратят силы, так что лишние горячие блюда или пайки придутся кстати.
- **`steward.food_plenty`**
  - **EN**: The kitchen supply chest is stacked neat and full, and there's pie cooling on the red counter. Grab an empty stool near the stoves whenever you want a hot bowl.
  - **ZH**: 公共补给箱里塞得满满当当，红色吧台上还晾着刚烤好的派。 什么时候想歇口气吃点热的，炉灶边的空凳子随时留着。
  - **RU**: Сундук на кухне забит под завязку, а на красной стойке остывает свежий пирог. Присаживайся на любой табурет у плит, если захочешь пообедать в тепле.
- **`steward.greeting`**
  - **EN**: Wipe the snow off your boots first. Kitchen ledger and pantry tally are right beside the stove if you want to check them.
  - **ZH**: 地毯前先把靴子上的雪蹭掉。 灶台边放着公共账本和库存单，想知道地穴里缺什么随时问就行。
  - **RU**: Сначала стряхни снег с сапог. Журнал припасов и список дел лежат прямо у плиты, если захочешь проверить.
- **`steward.handled`**
  - **EN**: Somebody already dropped those off by the counter. Look at the rest of the board if you still want something to do.
  - **ZH**: 这差事刚才已经有人先一步办妥了。 还想帮把手的话，看看别的条子还空着没有。
  - **RU**: Кто-то уже занёс это на стойку раньше тебя. Загляни в остальные записи, если ещё хочешь помочь.
- **`steward.meal`**
  - **EN**: Soup pot's simmering. Pull up a cushion near the stoves; whoever's awake will amble over directly.
  - **ZH**: 汤锅正熬着呢，我去敲声盆喊楼下那帮屋子里的家伙出来。 去炉灶边的软垫上坐着吧，谁醒着一会儿自己就会溜达过来。
  - **RU**: Кастрюля уже кипит — позову тех, кто не заснул в своих комнатах. Садись на подушки возле плит; кто не спит, сейчас сам подтянется.
- **`steward.no_requests`**
  - **EN**: Pantry shelves are tidy and everyone's fed for the moment. Take a quiet corner down by the basement pond or warm up beside the kitchen.
  - **ZH**: 储藏架收拾得齐整，眼下大家都安稳待在屋里没缺啥。 去楼下鱼池边坐坐，或者就在灶台旁暖和会儿吧。
  - **RU**: Полки в кладовой в порядке, и сейчас всем всего хватает. Можешь посидеть в тишине у пруда внизу или погреться у кухни.
- **`steward.outfit`**
  - **EN**: Well, you've more than carried your weight around here. Put this Burrow Shawl over your shoulders—blocks the wind outside without trapping oven heat indoors.
  - **ZH**: 行吧，你在地穴帮过多少忙，账我都记着呢。 这件地穴披肩拿着围上——在雪风里能挡寒，回到开了暖风的厨房里也不至于闷出一身汗。
  - **RU**: Ну что ж, ты уже не раз доказывал, что свой здесь. Бери эту накидку Норы: на ветру греет шею, а в протопленной кухне не даёт запариться.
- **`steward.remember`**
  - **EN**: People downstairs don't talk much, though they notice who keeps the pantry filled. You never have to bring rations just to earn a seat near my counter.
  - **ZH**: 楼下这帮家伙嘴笨不爱搭话，可谁往粮箱里填了东西大伙儿都记着呢。 你用不着次次扛着补给来才算自家人，吧台这边总有你的份。
  - **RU**: Ребята внизу неразговорчивы, но хорошо помнят тех, кто выручает с кухней. Тебе вовсе не нужно каждый раз нести припасы, чтобы сидеть у моей стойки.
- **`steward.request`**
  - **EN**: We're short %s %s for the burrow stores. Leave them by the kitchen chest or pass them right to me when you're free.
  - **ZH**: 地穴这边眼下需要 %s 份%s。 得空了直接塞进厨房补给箱，或者递给我也行。
  - **RU**: Для нужд Норы нам не помешает %s (%s). Оставь в кухонном сундуке или передай прямо мне, как будет время.
- **`steward.requests`**
  - **EN**: A couple folks left quiet notes by the kitchen counter about what they're short on. Pick whatever fits your errands; nothing here needs rushing.
  - **ZH**: 有几位邻居悄悄在灶台边留了条，写着最近缺的物件。 挑你顺手的帮一把就行，其他的慢点办也无妨。
  - **RU**: Несколько жильцов тихо оставили записки у стойки, чего им не хватает. Берись за то, что по пути; здесь никто никуда не торопится.
- **`steward.reverse_rite`**
  - **EN**: Stand still a second. Thick fur keeps winter out nicely, sure, though staying light on bare hands when working near hot iron stoves has its own perks. There—back to your own skin.
  - **ZH**: 站稳别动，一会儿就好。冬天厚毛确实暖和，不过守在烧热的铁灶边还是原来的身手利落些。好了——变回你本来的样子了。
  - **RU**: Постой смирно секунду. Густой мех в стужу хорош, но у раскалённых плит своя кожа порой удобнее. Вот и всё — ты снова в своём облике.
- **`steward.safe`**
  - **EN**: Pipes aren't freezing, stoves are steady, and nobody heard anything rattling past the upper fence.
  - **ZH**: 暖风和冰雪平衡得挺好，上头围栏外也没有谁撞门捣乱。
  - **RU**: Трубы не мёрзнут, плиты тянут ровно, а за верхним забором всё тихо.
- **`steward.thanks`**
  - **EN**: Much obliged. I'll stow these inside the kitchen chest before anyone raids my pie counter. Grab a hot bowl whenever you want.
  - **ZH**: 谢了。 我这就趁着还没人摸走吧台上的派把东西锁进箱里，饿了就来舀碗汤。
  - **RU**: Спасибо, выручил. Уберу в сундук, пока кто-нибудь не растащил со стойки пироги. Угощайся в любое время.
- **`steward.transfer`**
  - **EN**: Right now you're settled in with %s. Want to move your berth downstairs into the Burrow? Nobody here will pry, and your old friends won't mind.
  - **ZH**: 你眼下还记在%s那头的住户名下。 要搬来地穴这边清静落脚吗？没人会碎嘴打听，老朋友那边也不会介意。
  - **RU**: Сейчас ты числишься в общине «%s». Хочешь перебраться к нам под снег? Здесь никто не лезет с расспросами, и прежние друзья не обидятся.
- **`steward.watch`**
  - **EN**: Something hostile has been wandering near our snow fence and stairwell. Chase it off once the slope is clear—no need to drag a fight down into the kitchen.
  - **ZH**: 最近总有野怪在雪坡围栏和楼梯口外晃悠。 把外面清理利索了跟我说一声，别把打架声引到卧室和灶台边。
  - **RU**: У нашего забора и спуска по склону бродит какая-то враждебная тварь. Отгони её со склона и скажи мне — не стоит тащить драку прямо к кухне.
- **`steward.watch_thanks`**
  - **EN**: Slope's quiet again. Thanks for keeping trouble clear of the stairs. Take a warm plate from the counter—you earned it.
  - **ZH**: 外面雪坡总算消停了。 多亏你没让那些家伙闹到楼梯口来。吧台上挑份热的端走吧，算你的。
  - **RU**: На склоне снова тихо. Спасибо, что убрал угрозу от лестницы. Бери с плиты горячее — заслужил.
- **`steward.watch_wait`**
  - **EN**: Let's make sure the ridge stays quiet first. Once nothing is scratching at the upper fence, we can all sit back down cleanly.
  - **ZH**: 还是先盯着点外头的雪坡比较稳妥。 等确认上头围栏外不再出声，再安生坐回去。
  - **RU**: Лучше сначала убедиться, что наверху всё спокойно. Как только у верхнего забора станет тихо, тогда и передохнём.

#### Chief: Burrow Lookout (`changed_synergy:latex_gray_fox_female`) (`gray_fox_chief.json`, camp voice `lookout`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: Oh! Hey, come on in, shut the trap… well, there's no door up there, just step down out of the wind. I didn't expect a human to wander all the way out to our snowbank.Take a minute to warm up. Nobody down here bites, though half of them might duck into another room if you make eye contact…
  - **ZH**: 噢！嗨，快进来，把门……好吧，上面其实没门，你先下台阶避避风吧。我真没想到会有人类一路走到我们这座雪坡来。先缓口气暖和暖和。下面没人会咬人，不过你要是和他们对上视线，有一半人大概会躲进别的房间……
  - **RU**: О! Привет, заходи, прикрой… ну, двери наверху нет, просто спускайся с ветра. Не ожидала, что человек забредет аж к нашему сугробу.Постой минуту, согрейся. Здесь внизу никто не кусается, хотя половина жильцов может улизнуть в другую комнату, если поймаешь их взгляд…
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (A real human! And they actually came inside instead of walking past the fence! Wait… stay calm, don't talk too fast, you'll scare them off!)
  - **ZH**: （活生生的人类！而且真的进来了，没直接从围栏边走过去！等等……冷静点，别说太快，免得把人吓跑了！）
  - **RU**: (Настоящий человек! И правда зашел внутрь, а не прошел мимо ограды! Так, спокойно, не тараторь, а то спугнешь!)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hey there! Come on down off the stairs before you freeze your paws off!You picked a quiet spot to drop in. Most folks here aren't big on visitors, so I'm usually the one upstairs saying hello.
  - **ZH**: 嗨！快下楼梯来吧，别把爪子冻僵了！你挑了个挺安静的地方串门。这里大多数人都不太擅长应付客人，所以一般都是我在楼上这边打招呼。
  - **RU**: Привет! Спускайся с лестницы, пока лапы не отморозил!Ты заглянул в довольно тихое место. Большинство здешних не очень любят гостей, так что наверху обычно встречаю я.
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Yes! Someone new to talk to! Do not immediately tell them my entire life story. Start normal…)
  - **ZH**: （太好了！终于有新面孔可以聊天了！别一上来就把自己半辈子全倒出来，得正常一点……）
  - **RU**: (Ура! Новый собеседник! Только не вываливай сразу всю свою биографию. Начни нормально…)
- **Choices**:
  - `gray_fox_about` -> `about` (`condition=always`)
    - **EN**: Who are you? Do you run this place? | **ZH**: 你是谁？这里是你管着的吗？ | **RU**: Кто ты? Ты здесь главная?
  - `gray_fox_home` -> `home` (`condition=always`)
    - **EN**: Why is everything built underground? Who else lives down here? | **ZH**: 为什么把屋子全建在地下？下面还住着谁？ | **RU**: Почему тут все построено под землей? Кто еще здесь живет?
  - `gray_fox_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: How did you and the Manul end up starting a place like this? | **ZH**: 你和那只兔狲当初怎么会一起建起这么个地方？ | **RU**: Как вы с манулом вообще решили обустроить такое место?
  - `gray_fox_clue_terminal` -> `clue_terminal` (`condition=clue:burrow_terminal`)
    - **EN**: I read the old file on the computer in the bottom bedroom. | **ZH**: 我看了最底层卧室电脑里的那份旧文件。 | **RU**: Я прочитал старый файл на компьютере в нижней спальне.
  - `gray_fox_clue_rules` -> `clue_rules` (`condition=clue:burrow_rules`)
    - **EN**: I saw your note at the bottom of the kitchen house rules. | **ZH**: 我看到你在厨房规矩最底下补的那行字了。 | **RU**: Я видел твою приписку внизу правил на кухне.
  - `gray_fox_enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Is there room for me to stay here? | **ZH**: 我能在这儿安顿下来吗？ | **RU**: Найдется ли здесь место и для меня?
  - `gray_fox_membership` -> `membership` (`condition=member`)
    - **EN**: About my spot here under the hill… | **ZH**: 关于我住在这儿的事…… | **RU**: Насчет моего укрытия здесь…
  - `gray_fox_goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey, you're back! Shake the snow off before you head downstairs, or the kitchen boss will give us both that flat stare again.
  - **ZH**: 嗨，你回来啦！下楼前记得把雪抖干净，不然厨房里那位又要冲咱俩摆出那副死鱼眼了。
  - **RU**: Привет, с возвращением! Отряхни снег перед тем, как спускаться, а то главный по кухне опять уставится на нас обоих этим своим недовольным взглядом.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Wait, did I leave snow on the steps earlier? Probably. I'll blame the wind.)
  - **ZH**: （等等，我刚才是不是也在台阶上带了雪？八成是。一会儿就赖风吹进来的。）
  - **RU**: (Погоди, я сама раньше не натаскала снега на ступеньки? Наверняка. Свалю на ветер.)
- **Choices**:
  - `gray_fox_about` -> `about` (`condition=always`)
    - **EN**: Who are you? Do you run this place? | **ZH**: 你是谁？这里是你管着的吗？ | **RU**: Кто ты? Ты здесь главная?
  - `gray_fox_home` -> `home` (`condition=always`)
    - **EN**: Why is everything built underground? Who else lives down here? | **ZH**: 为什么把屋子全建在地下？下面还住着谁？ | **RU**: Почему тут все построено под землей? Кто еще здесь живет?
  - `gray_fox_past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: How did you and the Manul end up starting a place like this? | **ZH**: 你和那只兔狲当初怎么会一起建起这么个地方？ | **RU**: Как вы с манулом вообще решили обустроить такое место?
  - `gray_fox_clue_terminal` -> `clue_terminal` (`condition=clue:burrow_terminal`)
    - **EN**: I read the old file on the computer in the bottom bedroom. | **ZH**: 我看了最底层卧室电脑里的那份旧文件。 | **RU**: Я прочитал старый файл на компьютере в нижней спальне.
  - `gray_fox_clue_rules` -> `clue_rules` (`condition=clue:burrow_rules`)
    - **EN**: I saw your note at the bottom of the kitchen house rules. | **ZH**: 我看到你在厨房规矩最底下补的那行字了。 | **RU**: Я видел твою приписку внизу правил на кухне.
  - `gray_fox_enrol` -> `enrol` (`condition=stranger`)
    - **EN**: Is there room for me to stay here? | **ZH**: 我能在这儿安顿下来吗？ | **RU**: Найдется ли здесь место и для меня?
  - `gray_fox_membership` -> `membership` (`condition=member`)
    - **EN**: About my spot here under the hill… | **ZH**: 关于我住在这儿的事…… | **RU**: Насчет моего укрытия здесь…
  - `gray_fox_goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: I handle the outside end of things: checking the fence, trading when travelers pass by, and greeting anyone brave enough to climb down our stairs.Back when the transformation first went around, I took it early. My old human body wasn't going to last much longer anyway. Having legs that don't hurt and lungs that actually work in cold air? I still haven't gotten tired of it!
  - **ZH**: 我负责管外头这摊事，比如看看围栏、顺路和旅人换点东西，再招呼敢顺着楼梯走下来的客人。当初转化刚传开的时候，我很早就选了转化。反正我从前那具人类身体也撑不了多久了。现在腿不疼了，大冷天喘气也顺畅，这种感觉我至今都没腻！
  - **RU**: Я занимаюсь всем, что снаружи: проверяю ограду, меняюсь с прохожими путниками и встречаю тех, кто решился спуститься к нам по лестнице.Когда превращение только начало распространяться, я согласилась одной из первых. Мое старое человеческое тело все равно долго бы не протянуло. Ноги, которые не болят, и легкие, спокойно дышащие на морозе, — я до сих пор не могу к этому привыкнуть!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Seriously, running through knee-deep snow without coughing once is still the best thing ever!)
  - **ZH**: （说真的，踩着齐膝深的雪跑一圈还不咳一声，至今都是最爽的事！）
  - **RU**: (Серьезно, пробежаться по снегу по колено и ни разу не закашляться — до сих пор лучшее чувство на свете!)
- **Choices**:
  - `gray_fox_manul` -> `manul` (`condition=always`)
    - **EN**: What about the Manul downstairs in the kitchen? | **ZH**: 那楼下厨房里那只兔狲呢？ | **RU**: А что насчет манула внизу на кухне?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `manul` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh, him? He runs everything inside the den: the kitchen, the stores, making sure everyone eats without having to make small talk first.Don't take it personally if he looks like he's scowling at you. That's just his face. If you're polite and don't leave a mess on his bar counter, he'll warm up faster than he'd ever admit!
  - **ZH**: 噢，他呀？地穴里头的事全归他管，包括厨房、物资，还有确保大家不用被迫寒暄也能吃上饭。要是他看着像在瞪你，别往心里去，他天生就长那样。只要你客客气气的，别把他的吧台弄乱，他其实比自己嘴上承认的好相处得多！
  - **RU**: О, он? На нем все дела внутри норы: кухня, припасы, и чтобы все были сыты без необходимости вести светские беседы.Не принимай на свой счет, если покажется, что он хмурится. У него просто такая морда. Будешь вежливым и не оставишь бардак на стойке, он оттает быстрее, чем сам себе признается!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (His face when I sneak up and hug him with snow on my nose is priceless. He acts mad for five seconds and then hands me a bowl of soup anyway.)
  - **ZH**: （每次我鼻尖沾着雪偷偷抱住他，他那副表情简直绝了。装生气五秒钟，回头还不是端碗汤给我。）
  - **RU**: (Его морда, когда я подкрадываюсь и обнимаю его со снегом на носу, просто бесценна. Пять секунд делает вид, что злится, а потом все равно сует мне миску супа.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You've probably noticed we're a pretty homebody crowd. After changing, a lot of the folks who ended up here just stopped wanting to deal with crowds, arguments, or strangers on the road.So we built everything they'd need under the snow. There's a little tree right here on the upper floor, a kitchen and indoor farm a floor down, a workshop, dorm rooms, and even an indoor fishing pond with a painted sunset wall, so nobody has to go outside if they don't feel like it.Oh, and don't let the warm-air heaters next to the ice and snow throw you off. The heaters keep the burrow livable, but most of us have heavy winter coats and overheat fast indoors, so we keep ice and snow around to cool off and balance the temperature.
  - **ZH**: 你大概也看出来了，我们这儿都是些爱宅着的人。转化以后，最后聚到这儿的不少人都不太想应付人群、争执，或者路上的陌生人。所以我们就干脆把日常用得上的全建在雪底下了。楼上那有棵小树，往下一层是厨房和室内农场，还有工坊、宿舍，甚至连室内鱼池都挖好了，墙上还贴了面日落壁画，不想出门的人根本不用出去。对了，看到暖风机旁边堆着冰雪也别觉得奇怪。暖风用来保证地穴里住着舒服，但我们大多数人毛都厚得很，在室内待久了容易闷热，所以得靠冰雪降降温，把温度平衡一下。
  - **RU**: Ты, наверное, уже заметил, что народ тут собрался домашний. После превращения многие из тех, кто здесь оказался, просто расхотели сталкиваться с толпами, спорами и незнакомцами на дороге.Так что мы обустроили под снегом все нужное. Прямо тут в чердачной комнате растет деревце, этажом ниже кухня и крытая ферма, мастерская, спальни и даже подземный пруд с нарисованным закатом на стене, чтобы никому не приходилось выходить наружу без желания.Да, и не удивляйся, что у нас одновременно работают обогреватели и лежат лед со снегом. Теплый воздух нужен, чтобы в норе было уютно, но у большинства из нас слишком густой мех, и в тепле быстро становится жарко, поэтому лед и снег помогают остыть и держать равновесие.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (They act like grumpy teenagers locked in their rooms half the time. The second a skeleton rattles near the fence, though, everyone runs straight behind me!)
  - **ZH**: （平时一个个装得跟锁在房间里的叛逆小孩似的，结果围栏外头只要有骷髅咔哒一响，全躲我身后去了！）
  - **RU**: (Половину времени ведут себя как ворчливые подростки, запершиеся в своих комнатах. Но стоит скелету загреметь у ограды, все тут же прячутся мне за спину!)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `past` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: When I decided to undergo transformation, I told him not to follow me. He was completely healthy, and he didn't even like talking to people, let alone giving up his own body.He didn't listen. He showed up at the clinic two days later with his mind already made up, just so we wouldn't get separated when the world moved on.After that, we started running into other quiet types who were sick of noise and conflict. We dug out this hill together, wired up the warm-air heating, hauled in ice and snow to keep the temperature balanced, and made a place where nobody bothers you unless you want them to.
  - **ZH**: 当初我决定接受转化的时候，跟他说过别跟着我。他那时候身体好好的，连和人打交道都嫌烦，更别提换掉自己的身体了。结果他根本没听。两天后自己跑去诊所，主意早就打定了，就为了以后世界怎么变，我们俩都不会分开。后来我们在外头碰上不少同样喜静、烦透了吵闹和冲突的人。大家一起挖空了这座小山坡，装上暖风，再搬进冰雪调节温度，弄出这么个你不乐意就没人打扰的地方。
  - **RU**: Когда я решила пройти превращение, я сказала ему не идти за мной. Он был совершенно здоров и вообще не любил лишней суеты, не говоря уже о том, чтобы менять тело.Он не послушал. Через два дня сам пришел в клинику, уже все решив, лишь бы мы не расстались, пока мир меняется.Потом нам начали попадаться такие же тихие ребята, уставшие от шума и стычек. Мы вместе вырыли это жилье в холме, провели теплый воздух, натаскали льда со снегом для баланса и сделали место, где тебя никто не трогает без дела.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Still can't believe he picked a Manul! Roundest, grumpiest cat on earth, and somehow it fits him completely.)
  - **ZH**: （到现在我还是觉得不可思议，他居然成了只兔狲！世上最圆乎、脸最臭的猫，偏偏和他本人莫名合拍。）
  - **RU**: (До сих пор не верится, что он стал именно манулом! Самый круглый и угрюмый кот на свете, и ему это почему-то идеально идет.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `clue_terminal` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh, you wandered all the way down to our bedroom on the bottom floor? He left that file on the computer years ago, probably forgetting it's still sitting on the desktop.Reading it back now feels weird. Back then I was scared of every hospital checkup, and he was pretending he wasn't terrified of losing me. Look at us now: a shared double bed, warm-air heaters fighting a whole pile of ice and snow because his Manul fur is the thickest in the entire Burrow, and a house full of roommates who complain if I hum too loud.
  - **ZH**: 噢，你一路逛到最底下我们俩的卧室啦？那个文件是他好多年前留在电脑里的，大概早忘了还在桌面上。现在回头看感觉挺奇妙的。那会儿我每次去医院复查都提心吊胆，他还强装镇定，假装自己不怕失去我。看看现在吧：一张双人床，暖风机对着一整堆冰雪较劲，谁让他的兔狲毛是全地穴最厚的，还有一屋子嫌我哼歌声音太大的室友。
  - **RU**: О, ты спустился аж в нашу спальню на самом нижнем уровне? Он оставил этот файл на компьютере много лет назад; наверное, уже и забыл, что тот до сих пор лежит на рабочем столе.Странно сейчас это перечитывать. Тогда я боялась каждого осмотра в больнице, а он делал вид, что совсем не боится меня потерять. А теперь посмотри на нас: общая двуспальная кровать, обогреватели, которые спорят с целой горой льда и снега, потому что мех манула у него самый густой во всей Норе, и полный дом соседей, которые ворчат, если я слишком громко напеваю.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `clue_rules` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Haha, caught me! He gets so serious about people hiding in the cardboard room whenever someone knocks.I mean, yes, it's slightly embarrassing when a guest opens a locker and finds a wyvern pretending to be a coat. Still, if someone's social battery is dead for the day, I don't mind covering the door for them.
  - **ZH**: 哈哈，被你发现了！只要一有人敲门就往纸箱室里躲，他对此可认真了。好吧，客人一拉开柜子发现里头有只Wyvern假装自己是件大衣，确实有点尴尬。不过要是谁今天实在不想见人，我替他们应个门也没什么大不了的。
  - **RU**: Ха, поймал меня! Он так серьезно относится к тому, что все прячутся в комнате с коробками, стоит кому-то постучать.Ну да, немного неловко, когда гость открывает шкафчик, а там виверна притворяется пальто. Тем не менее, если у кого-то сегодня совсем нет сил общаться, мне не трудно прикрыть дверь.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: Of course. We've got spare cots downstairs, or a pile of warm cushions right up here if you like the breeze. We're just a quiet bunch under this mound, but as long as you don't mind neighbors who keep to themselves, you're home!
  - **ZH**: 当然好啊！楼下有空着的木床，你要是喜欢上头的空气，这儿也有整堆软垫。咱们这座山坡下就是个安安静静的小家，只要你不介意邻居们平时各忙各的、不大爱凑热闹，随时算你一份！
  - **RU**: Да без проблем! Внизу есть свободные койки, а заходишь остаться наверху — устроим тебе угол на мягких подушках. Мы тут народ тихий, держимся скромно под своим холмом, так что если не ищешь шумных компаний — устраивайся, ты дома!
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: You're welcome to settle in just as you are. Nobody here is going to ask you to change your skin just to earn a pillow and a plate of hot food.Though if you ever feel like trying out life as a gray fox, I'd be glad to share my coat for a little while or for good. It's entirely up to you.
  - **ZH**: 就按你现在的样子住下就行，咱们这儿可没规矩非得让人换一身皮毛才给热饭和床位。不过，要是有朝一日你想体会一下当灰狐的感觉，随时告诉我，我可以分你一点毛绒形态，不管试个鲜或者一直当下去都行，全看你自己的心意。
  - **RU**: Оставайся с нами в своем привычном виде. Никто не станет требовать, чтобы ты менял тело ради теплого угла и порции горячего пирога.Но если однажды захочешь узнать, каково это — быть серой лисой, я с радостью поделилась бы с тобой этим обликом: хочешь — ненадолго, хочешь — навсегда. Решать только тебе.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Ah, I wish I could say yes right now, but folks downstairs are feeling a bit overwhelmed today. Let's give everyone a little room to breathe first, and we can figure out your spot once things feel cozy again.
  - **ZH**: 哎，这会儿恐怕不太凑巧。楼下的伙伴们今天情绪有些紧绷，咱们先给大家伙留点清静的余地，等地穴里气氛暖和下来了，我再帮你想办法找间屋子。
  - **RU**: Эх, не сейчас. Ребята внизу пока сам не свои, так что давай дадим им немного прийти в себя. Как только всё уляжется и станет снова уютно, мы обязательно вернемся к этому разговору.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: I'll settle in as I am. | **ZH**: 我就保持现在的模样住下来。 | **RU**: Я останусь у вас в своей прежней форме.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'd like to stay human and live here with you. | **ZH**: 我想以人类的身份和你们住在一起。 | **RU**: Я бы хотел жить с вами человеком.
  - `explain_form` -> `explain_form` (`condition=can_reversible`)
    - **EN**: How does it feel to step into the gray fox form? | **ZH**: 变成灰狐究竟是种什么感觉？ | **RU**: Каково это — ощутить себя серой лисой?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Don't worry, you won't lose yourself. Your mind and memories stay entirely your own, and sharing my soft gray coat won't tire me out at all.Being a gray fox means nimble paws, light steps, and ears sharp enough to catch every footstep on the icy stairs. Plus, a huge bushy tail makes the absolute best blanket when cold drafts sweep in!If we leave it as a temporary change, you can slip back into your human skin whenever you miss it. If you make it permanent, this fur stays with you for good. But staying human is completely fine too!
  - **ZH**: 别担心，你绝不会丢失自我——你的心智和记忆依然完完全全属于你自己，而且分你一份灰狐皮毛对我一点损伤也没有。当了灰狐，手脚会变得格外利落，步子轻盈，耳朵能把楼阶上的风吹草动听得清清楚楚。况且寒风钻进来的时候，一条蓬松的大尾巴当毯子盖可舒服了！咱们可以先试个暂时的，哪天想念原来的身体了随时能变回去；要是选了永久，这身暖和的皮毛就会一直伴着你。当然啦，继续当人类也挺好的！
  - **RU**: Не бойся, ты не потеряешь себя: твои мысли и воспоминания останутся нетронутыми, а мне эта магия мягкого латекса совсем не повредит.В шубке серой лисы становишься невероятно ловким, шаги легкие, а чуткие ушки ловят каждый скрип на морозной лестнице. К тому же пышным хвостом так славно укрываться, когда из щелей тянет стужей!Если сделаем превращение временным, сможешь в любой момент вернуться в привычное тело. Сделаешь бессрочным, мягкий мех останется с тобой навек. Но и остаться человеком — прекрасный выбор!
- **Choices**:
  - `reversible` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Let's try the gray fox form temporarily and make this home. | **ZH**: 我想先体验一下可逆的灰狐形态，顺便住下来。 | **RU**: Пожалуй, я поношу образ серой лисы временно и останусь с вами.
  - `permanent_menu` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: I want to become a gray fox for good and live here. | **ZH**: 我想永久变成灰狐，在这儿扎根。 | **RU**: Я решил стать серой лисой навсегда и быть одним из вас.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I think I'll just stay human and settle down. | **ZH**: 我想还是保持人类的样子住下来吧。 | **RU**: Пожалуй, просто поселюсь здесь человеком.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Take a deep breath and make sure you're really ready. Making it permanent means this warm gray fur becomes your true self, even if you wander far beyond our snowbank, you won't change back, and simple magic won't undo it.You can still change your mind right now to try it temporarily or keep your human skin. You have a warm place here no matter what. Are you truly certain?
  - **ZH**: 深呼吸，先想清楚哦。选了永久，这身灰狐皮毛就会彻底融成你真正的样子。哪怕你以后走出这片雪坡远行，也没办法变回去了，寻常的逆转手艺可管不了用。你现在要是改主意试个暂时的、或者留着原本的人类样子都完全来得及，反正无论如何地穴都有你的一席之地。你真的下定决心了吗？
  - **RU**: Сделай вдох и подумай хорошенько. Сделать этот шаг навсегда — значит вплести эту лисью шубку в свою суть: даже если ты уйдешь далеко от нашего снежного дома, прежнее тело уже не вернется, и простые средства не помнут этого обратно.Ты все еще можешь передумать, поносить облик временно или остаться человеком — уголок у нас для тебя будет в любом случае. Ты точно этого хочешь?
- **Choices**:
  - `confirm` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I've made up my mind. Give me the gray fox coat for good. | **ZH**: 是的，我全想好了，把这身灰狐形态永久赋予我吧。 | **RU**: Да, я всё решил. Пусть образ серой лисы останется со мной навсегда.
  - `rethink` -> `explain_form` (`condition=always`)
    - **EN**: Let me weigh my options once more. | **ZH**: 让我再细细斟酌一下。 | **RU**: Дай мне ещё минутку подумать.

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh, wonderful! Pick any open cot down in the dorms, or drag a cushion up here if you prefer sleeping near the indoor tree. And hey, the shared food trunk is right by the kitchen arch, so just leave people's personal room trunks be!
  - **ZH**: 太好啦！楼下宿舍的床铺你随便挑，或者在楼上树边占个软垫也行。对了，公共补给箱就在厨房拱门旁边，只要别去碰别人房间里的私人箱子，想吃什么自便！
  - **RU**: Чудесно! Занимай любую кровать внизу или укладывайся на подушках прямо здесь, под деревом. Да, общий сундук стоит у арочного входа на кухню: бери что нужно, только в личные сундучки по комнатам не заглядывай!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Finally! Someone who won't run away when I ask how their day went!)
  - **ZH**: （终于来了一个我问"今天过得怎么样"不会转身就跑的人！）
  - **RU**: (Наконец-то кто-то, кто не сбежит, если спросить, как прошел день!)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to our little sanctuary! Make yourself comfortable, snag a snack off the bar whenever hunger hits, and don't take it to heart if some of the quiet folks down the hall take a few days to warm up to you.
  - **ZH**: 欢迎加入我们的小天地！当自己家一样就成，饿了随时去吧台顺点好吃的。要是走廊那头有几个内向的伙伴好几天才敢跟你回话，可千万别介意呀。
  - **RU**: Добро пожаловать в нашу Нору! Чувствуй себя как дома, перекусывай у стойки, когда захочешь, и не обижайся на местных тихонь, если они начнут здороваться только спустя пару дней.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Look at that! Give your ears a little flick and take a step, feels feather-light, doesn't it?You're completely yourself inside, just with a cozy new coat to keep the snow off. Welcome home, new neighbor!
  - **ZH**: 瞧瞧！抖抖耳朵、迈开脚试试，是不是感觉身子轻盈得很？你骨子里依然是你自己，只是现在多了一身抵御风雪的暖和冬毛。欢迎回家，新邻居！
  - **RU**: Вот и готово! Встряхни ушками, сделай шаг, правда же, тело будто невесомое?В душе ты остался собой, но теперь у тебя есть замечательная теплая шубка для любых метелей. Добро пожаловать домой, сосед!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Ooh, look at those gray ears! Wait till he sees there's two of us now, he's going to groan so loud!)
  - **ZH**: （噢，瞧瞧这对灰耳朵！等他看见家里现在有两只灰狐了，肯定要抱怨好久！）
  - **RU**: (Ой, только гляньте на эти серые ушки! Вот он застонет, когда увидит, что нас тут теперь двое!)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Look at that! Give your ears a little flick and take a step, feels feather-light, doesn't it?You're completely yourself inside, just with a cozy new coat to keep the snow off. Welcome home, new neighbor!
  - **ZH**: 瞧瞧！抖抖耳朵、迈开脚试试，是不是感觉身子轻盈得很？你骨子里依然是你自己，只是现在多了一身抵御风雪的暖和冬毛。欢迎回家，新邻居！
  - **RU**: Вот и готово! Встряхни ушками, сделай шаг, правда же, тело будто невесомое?В душе ты остался собой, но теперь у тебя есть замечательная теплая шубка для любых метелей. Добро пожаловать домой, сосед!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Ooh, look at those gray ears! Wait till he sees there's two of us now, he's going to groan so loud!)
  - **ZH**: （噢，瞧瞧这对灰耳朵！等他看见家里现在有两只灰狐了，肯定要抱怨好久！）
  - **RU**: (Ой, только гляньте на эти серые ушки! Вот он застонет, когда увидит, что нас тут теперь двое!)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're one of us now. Help yourself to the common food bin and any open pillow, but do leave the closed bedroom trunks alone.If the road ever calls you away, just let me know. There won't be any hard feelings, I promise.
  - **ZH**: 你现在可是咱们地穴的大伙伴了。厨房旁边的公共食箱和空着的床垫你随便用，别碰大伙儿卧房里的私人箱子就行。要是哪天远方召唤你，你想收拾行囊出发，跟我说一声就好。这里没人会埋怨你的，我保证。
  - **RU**: Ты теперь полноценный житель Норы. Общий сундук у кухни и все свободные подушки — в твоем распоряжении, только не трогай личные вещи в спальнях.Если вдруг захочешь двинуться дальше — просто шепни мне. Никто и слова против не скажет.
- **Choices**:
  - `leave_menu` -> `confirm_leave` (`condition=member`)
    - **EN**: It's time for me to pack up and move on. | **ZH**: 我该收拾行囊出发了。 | **RU**: Мне пора собираться и отправляться в путь.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Are you sure you're ready to brave the winds? Once you leave the household fold, the common pantries won't be yours to use, though our doors are always open if you ever want to visit.
  - **ZH**: 确定要踏入外头的风雪了吗？一旦告别大家，公共粮仓就不能随意取用了，不过你随时可以回来做客喝杯热茶。
  - **RU**: Точно готов снова выйти на мороз? Когда ты попрощаешься с Норой, доступ к общим сундукам закроется, но двери нашего дома всегда открыты для гостей.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=member`, `effect=leave`)
    - **EN**: Yes, the road calls me. | **ZH**: 是的，前方的路还在等我。 | **RU**: Да, меня зовет дорога.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Farewell then! Keep your scarf tight against the snow, alright? Whenever your travels bring you back near our hill, drop by for a hot meal.
  - **ZH**: 那就后会有期！外头雪大，裹紧围巾，路上万事小心。哪天路过我们这座山坡，随时进来吃口热乎的派！
  - **RU**: В добрый путь! Застегнись поплотнее, снег снаружи лютует. Будешь проезжать мимо нашего холма — заглядывай греться!
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to it. | **ZH**: 那我不打扰你了。 | **RU**: Не буду тебя задерживать.

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Watch the ice on the top steps! Come back up if you need anything.
  - **ZH**: 留神最上面台阶结冰！有事随时上来找我。
  - **RU**: Осторожнее со льдом на верхних ступеньках! Поднимайся, если что понадобится.
- **Choices**:
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: See you. | **ZH**: 回头见。 | **RU**: Увидимся.

##### `resident_upstairs` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh. Another visitor. Great. Look, if you're here to talk, she's standing literally two feet away from me. I'm just sitting on my pillows.
  - **ZH**: 噢。又来客人了。真棒。听着，你要是想聊天，她就站在离我两步远的地方。我只是在这儿坐我的睡垫。
  - **RU**: О. Еще один гость. Замечательно. Слушай, если хочешь поболтать, она стоит буквально в двух шагах от меня. Я тут просто сижу на своих подушках.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Why do I always sit in the front room? Oh right. Warm air from the heater, packed ice right by the pillow, just the right balance, and the canned peaches are within arm's reach.)
  - **ZH**: （我干嘛老待在门口这屋？噢对，暖风吹过来，睡垫旁边就是浮冰，温度刚刚好，而且伸手就够得着桃子罐头。）
  - **RU**: (И зачем я вообще сижу в проходной комнате? Ах да. Теплый воздух от обогревателя, плотный лед прямо у подушки — идеальный баланс — и до консервированных персиков рукой подать.)
- **Choices**:
  - `topic_0` -> `resident_upstairs_0` (`condition=always`)
    - **EN**: If you hate visitors, why stay upstairs right by the entrance? | **ZH**: 要是这么讨厌客人，干嘛还待在门口这屋？ | **RU**: Если так не любишь гостей, зачем сидеть наверху у входа?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокою.

##### `resident_upstairs_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Because I claimed the spot under the indoor tree first, that's why. Moving all my books and soup cans downstairs sounds like way too much work.Besides, if you don't stare at me while I read, I don't actually care if you use one of the other pillows.
  - **ZH**: 因为室内树底下这块地盘是我先占的，就这么简单。把我的书和汤罐头全搬到楼下听着就太麻烦了。再说了，只要我看书的时候别盯着我，你用旁边别的睡垫我其实也无所谓。
  - **RU**: Потому что я первая заняла место под этим деревом, вот почему. Тащить вниз все мои книги и банки с супом — слишком много возни.К тому же, если не будешь пялиться на меня, пока я читаю, мне все равно — занимай любую другую подушку.
- **Choices**:
  - `back` -> `resident_upstairs` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокую.

##### `resident_farm` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey. Watch the fence gate—if the sheep get out into the wheat again, I'm not chasing them down the stairs.You need something, or are you just looking at the cows?
  - **ZH**: 嗨。当心栅栏门——绵羊要是再跑进麦地里，我可不去楼梯下追它们。你有事找我，还是光在这儿看牛？
  - **RU**: Эй. Осторожнее с калиткой — если овцы опять залезут в пшеницу, я за ними вниз по лестнице бегать не стану.Тебе что-то нужно, или просто на коров смотришь?
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Cows are great. They chew grass, they mind their own business, and they never ask what I'm doing this weekend.)
  - **ZH**: （牛多好啊。只顾着嚼草、管好自己的事，从来不问我周末打算干嘛。）
  - **RU**: (Коровы — отличные ребята. Жуют траву, не лезут не в свое дело и никогда не спрашивают про планы на выходные.)
- **Choices**:
  - `topic_0` -> `resident_farm_0` (`condition=always`)
    - **EN**: Do you mind taking care of the farm all by yourself? | **ZH**: 农场的事全靠自己打理，你不介意吗？ | **RU**: Тебе не в тягость одному ухаживать за фермой?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокою.

##### `resident_farm_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Mind it? I volunteered so I'd have a reason to skip kitchen chatter.The berry bushes and crops keep the kitchen stocked, and the Manul leaves fresh pie by the door when I'm done. Fair trade.
  - **ZH**: 介意？我是自己主动揽下来的，好有个借口躲开厨房里的闲聊。浆果丛和庄稼管够厨房用，等我干完活，兔狲会把刚烤好的派放在门口。挺划算的。
  - **RU**: Не против ли я? Да я сам вызвался, чтобы был повод пропускать болтовню на кухне.Ягодные кусты и грядки кормят кухню, а манул оставляет свежий пирог у двери, когда я заканчиваю. Честный обмен.
- **Choices**:
  - `back` -> `resident_farm` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокую.

##### `resident_pond` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Don't splash the water. The salmon get jumpy when strangers stomp around the edge.Wait—you're not here to tell me I should "go fish in a real river," are you? Because she already tried that last Tuesday.
  - **ZH**: 别把水踩溅起来。陌生人在池边乱跺脚，鲑鱼会受惊的。等等——你不是来劝我"去外头真河里钓鱼"的吧？上周二她已经试过这一套了。
  - **RU**: Не плещи водой. Лосось пугается, когда незнакомцы топают у края.Погоди — ты ведь не пришел говорить, что мне стоит «порыбачить на настоящей реке»? Потому что она уже пробовала в прошлый вторник.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (No wind, no sleet, no random wanderers asking if the fish are biting. This basement pond is peak civilization.)
  - **ZH**: （没风、没冻雨，也没路人凑过来问"鱼咬钩了吗"。这地下室鱼池简直是文明巅峰。）
  - **RU**: (Ни ветра, ни мокрого снега, ни случайных прохожих с вопросом «ну как, клюет?». Этот подвальный пруд — вершина цивилизации.)
- **Choices**:
  - `topic_0` -> `resident_pond_0` (`condition=always`)
    - **EN**: What's with the giant banner mural on the back wall? | **ZH**: 后墙上那幅巨型旗帜壁画是怎么回事？ | **RU**: Что за огромная флаговая роспись на задней стене?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокою.

##### `resident_pond_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: That's our mountain view. Thirty banners, took two evenings to line them up right.Look at it: dark peaks, little red moon, even a bird. Zero snowstorms, zero small talk. Tell me a real mountain beats that.
  - **ZH**: 那是我们的山景。整整三十面旗帜，花了两个晚上才对齐。你看：黑山峰、小红月亮，甚至还有只鸟。没暴风雪，也没客套话。你说哪座真山比得上这个？
  - **RU**: Это наш вид на горы. Тридцать флагов, два вечера ушло, чтобы ровно их развесить.Сам посмотри: темные вершины, маленькая красная луна, даже птица есть. Никаких метелей, никаких пустых разговоров. Скажи еще, что настоящие горы лучше.
- **Choices**:
  - `back` -> `resident_pond` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокую.

##### `resident_workshop` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hang on, what? Couldn't hear you over the blast furnace.Oh, it's just you. Look, if you need the anvil or the crafting table, go ahead, just put tools back where you found them and don't knock over the blue orchids.
  - **ZH**: 等会儿，你说啥？高炉声太大我没听清。噢，是你啊。听着，要用铁砧还是工作台自便，工具用完放回原处就行，别把桌上的蓝兰花碰倒了。
  - **RU**: Погоди, чего? Из-за плавильной печи не слышно.А, это ты. Слушай, если нужна наковальня или верстак, пользуйся, только клади инструменты на место и не задевай синие орхидеи.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (The furnace isn't even that loud right now. It's just the best excuse on earth to ignore people calling down the hall.)
  - **ZH**: （其实这会儿熔炉根本没那么响。但这是世上最好用的借口，假装听不见走廊里有人喊我。）
  - **RU**: (Да печь сейчас вообще почти не шумит. Просто это лучшая на свете отговорка, чтобы не откликаться, когда кто-то зовет из коридора.)
- **Choices**:
  - `topic_0` -> `resident_workshop_0` (`condition=always`)
    - **EN**: Why is the workshop floor lined with packed ice? | **ZH**: 工坊地上为什么铺了浮冰？ | **RU**: Зачем в мастерской пол выложен плотным льдом?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокою.

##### `resident_workshop_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Six furnaces in an underground room without a window: you try standing in here with a full coat of fur without ice under your paws.The Manul helped me lay the packed ice last winter. Keeps the air crisp and stops anyone from hanging around chatting for too long.
  - **ZH**: 没窗户的地下室里摆六台熔炉，你披一身厚毛进来站站看，脚底下不铺冰根本受不了。去年冬天兔狲帮我铺了这些浮冰。空气凉爽，顺便还能让闲聊的人待不住太久。
  - **RU**: Шесть печей в подземной комнате без окон; попробуй постоять тут в густой шерсти без льда под лапами.Прошлой зимой манул помог мне уложить плотный лед. Воздух свежий, да и болтуны тут надолго не задерживаются.
- **Choices**:
  - `back` -> `resident_workshop` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокую.

##### `resident_library` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Uh… hi. Look, I'm right in the middle of a chapter. Was there something urgent, or did the gray fox send you down here to "make friends"?
  - **ZH**: 呃……你好。那个，我正好看到这一章中间。是有什么急事，还是灰狐派你下来"交朋友"的？
  - **RU**: Э… привет. Слушай, я тут как раз на середине главы. Что-то срочное, или серая лиса прислала тебя вниз «познакомиться»?
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Please don't ask what book it is. If I explain the plot I'll have to talk for three whole minutes.)
  - **ZH**: （求你别问我在看什么书。要解释剧情我得整整讲三分钟。）
  - **RU**: (Только не спрашивай, что это за книга. Если начать пересказывать сюжет, придется говорить целых три минуты.)
- **Choices**:
  - `topic_0` -> `resident_library_0` (`condition=always`)
    - **EN**: You and the wyvern next to you don't seem to talk much. | **ZH**: 你和旁边那只双足飞龙好像不怎么说话？ | **RU**: Вы с виверной рядом вроде почти не разговариваете.
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокою.

##### `resident_library_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: That's the whole point. We've been sharing this corner since last autumn and we've spoken maybe twelve sentences total.Sometimes they grab a slice of the stuffed pumpkin on the table, I turn a page, and neither of us ruins it by asking how the weather is. Best roommate I've ever had.
  - **ZH**: 重点就在这儿。去年秋天起我们就共用这个角落，加一块总共大概说过十二句话。有时候他们从桌上拿块填馅南瓜，我翻一页书，谁也不会问句"今天天气怎么样"来破坏气氛。我遇过最好的室友。
  - **RU**: В этом и весь смысл. Мы делим этот угол с прошлой осени и перекинулись от силы дюжиной фраз.Иногда они берут кусок фаршированной тыквы со стола, я переворачиваю страницу, и никто не портит момент вопросами о погоде. Лучший сосед в моей жизни.
- **Choices**:
  - `back` -> `resident_library` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокую.

##### `resident_dorm` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh, sorry, didn't hear you come down the corridor. I'm not in the way, am I?If you're looking for an empty bed, try one of the rooms further down the hall. The room full of cardboard boxes is… mostly storage. Definitely not a hiding spot.
  - **ZH**: 噢，抱歉，没听见你从走廊过来。我没挡路吧？要是找空床的话，可以看看走廊那头的房间。塞满纸箱那间……主要是储藏室。绝对不是用来躲人的地方。
  - **RU**: Ой — извини, не услышал твои шаги в коридоре. Я не мешаю?Если ищешь свободную кровать, загляни в комнаты дальше по коридору. Та комната с картонными коробками — в основном кладовая. Точно не укрытие.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Too late to duck into the cardboard room now. Act natural. Pretend I was just checking the wall lever.)
  - **ZH**: （现在再钻进纸箱室已经晚了。自然点，假装我刚才只是在检查墙上的拉杆。）
  - **RU**: (В комнату с коробками уже поздно нырять. Веди себя естественно. Сделай вид, что просто проверял рычаг на стене.)
- **Choices**:
  - `topic_0` -> `resident_dorm_0` (`condition=always`)
    - **EN**: Why are there so many cardboard boxes stacked in that room? | **ZH**: 那个房间里为什么堆了那么多纸箱？ | **RU**: Почему в той комнате так много сложенных картонных коробок?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокою.

##### `resident_dorm_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Half of them are stuff we hauled in when we first moved here, and nobody ever bothered unpacking them.Okay, fine, and sometimes when the gray fox shouts down the stairs that we have company, crawling behind the big iron crates is faster than locking a bedroom door. Don't tell the Manul.
  - **ZH**: 一半是刚搬来时大家拖进来的杂物，后来一直没人拆箱。好吧行，有时候灰狐冲楼梯底下喊"来客人啦"，钻到那堆大铁箱后头比回卧室锁门快多了。别告诉兔狲。
  - **RU**: Половина — это вещи, которые мы притащили при переезде и так и не удосужились разобрать.Ну ладно, а еще иногда, когда серая лиса кричит сверху, что пришли гости, забраться за большие железные ящики быстрее, чем запереть дверь спальни. Только манулу не говори.
- **Choices**:
  - `back` -> `resident_dorm` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: I'll leave you to it. | **ZH**: 不打扰你了。 | **RU**: Не побеспокую.

#### Chief Camp-Life Voice (`lookout.*`)

- **`lookout.changed`**
  - **EN**: Wait a sec—something didn't quite line up! Check that you're still holding the exact item and our kitchen chest isn't bursting at the seams!
  - **ZH**: 哎呀等会儿——好像哪儿没对上数！ 看看是不是手里没拿稳对号的物品，或者厨房那只大箱子已经塞得扑出来了！
  - **RU**: Погоди минутку — что-то пошло не так! Проверь, что нужный предмет всё ещё в руке, а кухонный сундук не забит под завязку!
- **`lookout.choose_item`**
  - **EN**: Hold whatever supplies you're sharing right in your hand so I can see! Don't freeze out on the ridge giving away your own trail snacks first!
  - **ZH**: 把你准备拿出来分享的物资攥在手里，我好照单点清楚！ 自己翻雪坡带的干粮可一定要留够哦！
  - **RU**: Держи в руке те припасы, которыми хочешь поделиться, чтобы я видела! Только не отдавай последнее — на обратном пути по сугробам ещё пригодится!
- **`lookout.danger`**
  - **EN**: Whoa, hold it—something hostile wandered right up to our snow ridge or garden gate! Help me shoo it off before anyone down in the dorms gets spooked!
  - **ZH**: 等等，停一下——有难缠的家伙溜达到雪坡围栏或者菜园门边上了！ 帮我一道赶走它再回屋里歇着，可别惊着楼下宿舍里那些怕生的小家伙！
  - **RU**: Ой, погоди — у нашего забора на склоне или у огорода кто-то враждебный крутится! Помоги прогнать его, чтобы ребят в спальнях внизу не перепугать!
- **`lookout.declined`**
  - **EN**: Totally fine! Keep your own pack full for the snow trails—we've got plenty of pie left downstairs!
  - **ZH**: 完全没事儿！ 雪原路上自己吃饱穿暖最要紧，楼下还有好几块热派撑着呢！
  - **RU**: Ничего страшного! Береги силы для дороги по снегу — у нас внизу ещё полно пирогов!
- **`lookout.donate`**
  - **EN**: Oooh, you hauled %s all the way through the drifts for us? Manul's soup pot is gonna stretch twice as far tonight! How much are you leaving?
  - **ZH**: 哇哦，你顶着风雪扛了%s来添补给呀？ 今晚某人的汤锅可算有着落啦！这回打算放下多少？
  - **RU**: Ого, ты притащил %s прямо через сугробы для нас? Теперь-то кастрюли точно хватит на всех! Сколько положишь?
- **`lookout.food_enough`**
  - **EN**: Between our berries downstairs, kitchen smokers, and that shared chest, everybody's doing fine for food today!
  - **ZH**: 下层地里的浆果、厨房熏炉再加上大补给箱，今天大伙儿肚子都垫得足足的！
  - **RU**: С ягодами внизу, коптильнями и общим сундуком сегодня все сыты!
- **`lookout.food_low`**
  - **EN**: The shared chest down by the kitchen door is looking pretty light right now. Even quiet burrowers burn through pies fast in deep winter—any extra rations would keep everyone smiling!
  - **ZH**: 厨房门口那只公共补给箱眼看着就快空了一半啦。 就算整天待屋里不出门，冷天大家啃派也快得很，带点吃食过来绝对受欢迎！
  - **RU**: Общий сундук у входа на кухню сейчас заметно полегчал. В морозы даже домоседы мигом сметают все пироги — любая лишняя еда сейчас будет праздником!
- **`lookout.food_plenty`**
  - **EN**: Oh, our kitchen chest is packed tight right now—warm soup, fresh bread, berries, the works! Come snag a cushion near the warm stoves later if you want to eat with us!
  - **ZH**: 哇，厨房那边屯得满当当的——热汤、新出炉的面包、甜浆果什么都有！ 一会儿记得下来占个炉边软垫，大家凑一起热热闹闹吃一顿！
  - **RU**: Ого, наш сундук сейчас ломится от еды: горячий суп, свежий хлеб, ягоды — чего там только нет! Забегай попозже на подушки к плитам, поедим вместе!
- **`lookout.greeting`**
  - **EN**: Hey, shake the snow off your coat and step on down! Anything you want checked on the pantry tally or chore board, just holler!
  - **ZH**: 嘿，快把衣角上的雪抖抖下来暖和暖和！ 想问厨房余粮还是邻里捎话，跟我说一声就行！
  - **RU**: Эй, отряхни снег с плеч и спускайся скорее! Если интересно, что в кладовой или кто о чём просил — спрашивай смело!
- **`lookout.handled`**
  - **EN**: Oh! Looks like somebody speedy already took care of that one! Peek at the other notes if you still feel like lending a paw!
  - **ZH**: 哎呀！看来有手脚麻利的家伙已经抢先办完啦！ 要是还手痒想帮衬两把，看看旁边的纸条还有空缺没！
  - **RU**: Ой! Похоже, кто-то шустрый уже успел всё принести раньше! Посмотри другие записки, если ещё хочется похлопотать!
- **`lookout.meal`**
  - **EN**: Dinner bell time!! I'll scurry down the stairs and knock on every quiet door! Head down to the stoveside cushions and wait a heartbeat—everybody loves hot pie!
  - **ZH**: 开饭喽——我去楼下挨个敲门把缩屋里的家伙全喊出来！ 先去灶台边的软垫那儿等着，一闻见烤派香大家马上就露头啦！
  - **RU**: Время обеда!! Сейчас пробегусь вниз и постучу в каждую тихую дверь! Спускайся к подушкам у плит и подожди минутку — на запах пирога все сбегутся!
- **`lookout.no_requests`**
  - **EN**: Everyone downstairs is snug in their corners with zero urgent errands today! Go kick up your paws by the birch tree cushion or soak by the basement pond!
  - **ZH**: 楼下大伙儿今天全都舒舒服服缩在自己屋里，手头半件急事也没有！ 去上头白桦树下的软垫靠一靠，或者去地下鱼池边听水声歇会儿吧！
  - **RU**: Все внизу сегодня устроились в тепле, и никаких срочных дел нет! Отдохни на подушках под берёзкой наверху или посиди у подземного пруда!
- **`lookout.outfit`**
  - **EN**: Hey, you've officially stuck with us through enough snowstorms to earn your colors! Take this Burrow Shawl! I stitched the trim warm enough for climbing in sleet, and loose enough not to bake indoors!
  - **ZH**: 嘿，你在风雪里跑前跑后帮了这么多回，早就是自家地穴的人啦！ 披上这条地穴披肩吧！我在领边多缝了一道保暖衬，爬冰墙不怕透风，回到有暖气的地下屋也不怕热着！
  - **RU**: Эй, ты столько раз выручал нас в метель, что по праву стал своим в Норе! Держи эту накидку Норы! Она бережёт от ветра на ледяных склонах и при этом совсем не парит в тёплой комнате!
- **`lookout.remember`**
  - **EN**: Even the quiet folks who hide behind crate stacks downstairs remember how much you pitched in for us! You never have to haul snow-soaked packs here just to stay welcome!
  - **ZH**: 连那些一敲门就往纸箱后面钻的家伙，也全记着你以前帮地穴出过的力呢！ 你随时空着手串门也完全一样受欢迎！
  - **RU**: Даже самые тихие домоседы внизу до сих пор помнят, сколько ты нам помогал! Тебе вообще не нужно каждый раз тащить тяжёлые сумки, чтобы тебя тут рады были видеть!
- **`lookout.request`**
  - **EN**: Could really use %s %s tucked into our burrow supplies! Drop them in the chest near the kitchen arch or hand them straight over whenever you're back!
  - **ZH**: 地穴这边眼下刚好缺 %s 份%s呢！ 下回经过厨房拱门塞进箱子里，或者直接交到我手里都成！
  - **RU**: Нам бы очень пригодилось %s (%s) в запасы Норы! Оставь в сундуке у кухонной арки или передай прямо мне, как вернёшься!
- **`lookout.requests`**
  - **EN**: A few neighbors slipped little wish notes onto the board before ducking back into their rooms! Grab whichever one fits your route, no pressure at all!
  - **ZH**: 有几个邻居钻回屋前，偷偷在留言板上贴了几张小纸条！ 挑你顺路能帮忙的就行，剩下的慢慢来！
  - **RU**: Несколько соседей тихонько повесили записки, прежде чем снова юркнуть в свои комнаты! Выбирай любую по пути — никакой спешки!
- **`lookout.reverse_rite`**
  - **EN**: Hold still for two heartbeats—done! Having padded paws and climbing claws on icy beams sure is fun, though human hands have their own quiet magic back home too. Welcome back to yourself!
  - **ZH**: 稳住站两秒就好——好啦！长着肉垫和利爪在冰梁上窜上窜下是挺痛快，可自己原本的双手在热炉边也一样自如嘛。随时欢迎你保持本来的样子！
  - **RU**: Замри на пару секунд — готово! Бегать по балкам с когтями весело, но родные руки в привычном обличье тоже хороши. С возвращением!
- **`lookout.safe`**
  - **EN**: Just checked the birch ridge and climbed both beams—not a single skeleton rattle outside!
  - **ZH**: 我刚爬到桦树坡和房梁顶上转了一圈，外头风平浪静，连根骷髅骨头都没响！
  - **RU**: Я только что проверила склон и балки наверху — снаружи ни единого шороха и ни одного скелета!
- **`lookout.thanks`**
  - **EN**: Awesome—thank you so much! I'll race this straight down to the kitchen chest before any snow melts on it. Go claim a warm spot by the stove!
  - **ZH**: 太棒啦——真是谢天谢地！ 我趁着雪还没化赶紧搬进厨房大箱里去，你也快去炉火边占个暖和座位吧！
  - **RU**: Вот здорово — огромное спасибо! Мигом утащу это в кухонный сундук, пока ничего не отсырело. Занимай тёплое место у плиты!
- **`lookout.transfer`**
  - **EN**: You're still registered back at %s for now! Want to pack a pillow and move into our snow burrow instead? Old pals won't lose touch at all!
  - **ZH**: 你眼下的住处还挂在%s那边呢！ 想不想直接把软垫搬进咱们雪底下住？老朋友那边一样常联系，没人会拦着！
  - **RU**: Пока твой дом числится в общине «%s»! Не хочешь перебраться с подушкой к нам под снег? Прежние друзья всё равно останутся друзьями!
- **`lookout.watch`**
  - **EN**: There's been a troublemaker prowling past our snow fence outside the stairs! Run it off the ridge when you can, just don't lure a brawl down onto our kitchen rug!
  - **ZH**: 最近有个不长眼的家伙老在楼梯外边的雪围栏旁转悠！ 顺手帮我们从雪坡上赶走吧，记住别把它引到楼梯下面踩脏厨房地毯——某位兔狲可要瞪死我啦！
  - **RU**: Наверху за нашим снежным забором у лестницы бродит какая-то нечисть! Прогони её со склона при случае — только не заводи драку прямо на ковёр у кухни!
- **`lookout.watch_thanks`**
  - **EN**: Woohoo, ridge clear! Even the shy ones behind the storage crates are peeking back out now. Grab a warm cup down by the counter!
  - **ZH**: 耶——雪坡总算清爽了！ 连躲在大箱子后面的几个内向家伙都探头出来透气啦，快下去吧台端杯热饮歇会儿！
  - **RU**: Ура, склон снова чистый! Даже самые застенчивые ребята из-за ящиков выглянули! Заходи к стойке за тёплой кружкой!
- **`lookout.watch_wait`**
  - **EN**: Eyes up on the snow fence just a bit longer! Better to keep the steps peaceful before kicking back downstairs!
  - **ZH**: 咱们先盯紧上面雪围栏那头一会儿！ 等外头真消停透了，大伙儿再一块下楼图个安稳！
  - **RU**: Приглядим ещё немного за снежным забором наверху! Лучше сперва убедиться, что у лестницы безопасно, а потом уже отдыхать!

#### Resident Camp-Life Voice (`burrow_resident.*`)

- **`burrow_resident.away`**
  - **EN**: Supply tallies and kitchen stores can wait until we're back inside the burrow. Right now I'm just glad to have steady company out in the drifts.
  - **ZH**: 清点余粮和添补给的事，等咱们回地穴暖和了再操心。 眼下在雪窝子里走着，身边有个伴一块搭把手就挺安心的。
  - **RU**: Подсчёт запасов подождёт, пока мы не вернёмся под снег в Нору. Сейчас мне просто спокойно идти по сугробам вместе с тобой.
- **`burrow_resident.cautious_greeting`**
  - **EN**: Oh—excuse me, you startled my ears. Is the wind kicking up hard outside, or did something bring you down to our hall?
  - **ZH**: 哎……抱歉，没听见脚步声差点惊着耳朵。 外头风雪又刮起来了吗，还是厨房那头有什么事要办？
  - **RU**: Ой — прости, не услышал шаги, аж уши дёрнулись. Снаружи метель разыгралась или тебя дела сюда привели?
- **`burrow_resident.cautious_request`**
  - **EN**: If it isn't any trouble out in the frost… could you spare %s %s? Please take your time and watch the ridge cliffs.
  - **ZH**: 如果雪原上跑一趟不算太麻烦的话……能不能顺便匀我 %s 份%s？ 慢慢来就行，坡外面路滑当心摔着。
  - **RU**: Если на морозе это не слишком трудно… не поделишься ли %s (%s)? Совсем не тороплю — берегись скользких уступов наверху.
- **`burrow_resident.cautious_thanks`**
  - **EN**: Thank you kindly. Knowing the snow ridge is quiet and you're back safe takes a load off my mind.
  - **ZH**: 真心谢谢你。 看你平平安安钻回地道里，我就踏实多啦。
  - **RU**: Большое тебе спасибо. Когда видишь, что ты вернулся целым с мороза, сразу спокойнее на душе.
- **`burrow_resident.changed`**
  - **EN**: Wait—we can't wrap that up quite yet. Check you're holding the right item and the kitchen chest still has free space.
  - **ZH**: 等等——这会儿好像没法顺利收进账。 瞧瞧手里东西拿对了没有，或者是不是公共粮食箱满了。
  - **RU**: Погоди — пока не выходит это завершить. Проверь, что в руке нужный предмет и в общем сундуке есть место.
- **`burrow_resident.choose_item`**
  - **EN**: Hold whatever supplies you're sharing in your hand so I can look. Make sure you keep enough warm food for your own trek across the taiga.
  - **ZH**: 把打算匀给大伙儿的物资拿在手上，让我瞧清楚数量。 外头冻土冷风硬，自己包里的干粮可留够啊。
  - **RU**: Подержи в руке то, чем хочешь поделиться, чтобы я посмотрел. Оставь себе достаточно еды на обратную дорогу по тайге.
- **`burrow_resident.danger`**
  - **EN**: Wait—somebody heard something hostile up by the steps or pasture gate. Let's clear the entrance before anyone ducks back to read.
  - **ZH**: 小心……刚刚有人听见楼梯上头或者牧场栅栏边有怪物的动静。 先把入口守稳了，大伙儿再安心回去歇着。
  - **RU**: Тише — кто-то слышал враждебного моба наверху у ступенек или у загона. Сначала проверим вход, а потом уже можно спокойно разойтись.
- **`burrow_resident.declined`**
  - **EN**: That's completely fine. Half of us prefer keeping things simple anyway.
  - **ZH**: 没关系的。 咱们这儿大家原本也就图个自在不勉强。
  - **RU**: Ничего страшного. Здесь и так никто ни на кого не давит.
- **`burrow_resident.donate`**
  - **EN**: You brought %s down from the surface? That'll go straight into the kitchen supply chest. How much are you adding?
  - **ZH**: 你居然专程带了%s下楼来？ 这可太省事了，直接放进厨房共享箱就行——你要留多少份？
  - **RU**: Ты принёс %s прямо с поверхности? Отправим прямо в кухонный сундук. Сколько оставишь?
- **`burrow_resident.food_enough`**
  - **EN**: The stoves are lit and the kitchen supply chest has enough for everyone right now. Help yourself to a plate if you live down here.
  - **ZH**: 灶火正暖，厨房门边的共享箱里目前也还够分。 要是你也住在这儿，自己盛一盘就好。
  - **RU**: Плиты топятся, и в общем сундуке на кухне сейчас всем хватает еды. Если живёшь здесь, смело бери порцию.
- **`burrow_resident.food_low`**
  - **EN**: The chest right inside the kitchen doorway is getting a bit light. Extra stew, baked potatoes, or sweet berries would keep everyone warm without heading out into the snow.
  - **ZH**: 厨房门口那口大箱子最近见底得有些快。 要是有富裕的炖菜、烤土豆或甜浆果添进去，大伙儿就不用冒雪出门找吃的了。
  - **RU**: Сундук прямо у входа на кухню становится довольно лёгким. Лишнее рагу, печёный картофель или ягоды помогут никому лишний раз не выходить на мороз.
- **`burrow_resident.food_plenty`**
  - **EN**: The kitchen chest is full of pies and smoked cuts, and the pond room smells nice and cool. It'd be nice to pull everyone out to the hearth cushions for a meal.
  - **ZH**: 厨房箱子里装满了肉派和熏肉，楼下鱼池那屋还清清爽爽的。 要能把屋里各忙各的邻居喊出来围着灶台吃顿饭一定很惬意。
  - **RU**: На кухне полно пирогов и копчёностей, а в комнате у пруда прохладно и уютно. Здорово было бы вытащить всех из комнат к плите на общий ужин.
- **`burrow_resident.gesture`**
  - **EN**: The neighbor softly clears their throat in the cool hall and motions toward the kitchen stoves and upper stairs, though you can't make out their words yet.
  - **ZH**: 这位邻居在微凉的地道里轻轻清了清嗓子，抬手分别指了指厨房炉灶和通往雪坡上的楼梯，只是你眼下还听不懂对方在说什么。
  - **RU**: Сосед тихо откашливается в прохладном коридоре и показывает то на кухонные плиты, то на лестницу наверх, хотя слов ты пока не понимаешь.
- **`burrow_resident.greeting`**
  - **EN**: Oh—hello there. Need to check on burrow stores, or were you looking for a quiet errand by the kitchen?
  - **ZH**: 噢……你好。 是来看看地穴这边的补给箱，还是顺道帮灶台那边搭把手？
  - **RU**: Ой — здравствуй. Хочешь проверить запасы Норы или помочь с мелочами у кухни?
- **`burrow_resident.handled`**
  - **EN**: Oh—someone already slipped those onto my shelf! Thanks for thinking of me anyway.
  - **ZH**: 噢——刚刚已经有人顺手帮我搁到架子上啦！ 不过还是谢谢你惦记着。
  - **RU**: Ой — кто-то уже положил это на мою полку! Всё равно спасибо, что помнил.
- **`burrow_resident.meal`**
  - **EN**: I'll tap quietly on the doors down the hall. Wait by the kitchen stoves—anyone not asleep behind the ice walls will wander over.
  - **ZH**: 我这就顺着走廊轻轻敲几下门。 先去厨房灶台边等会儿吧，只要没睡死在冰雪后头的屋里都会溜达过来。
  - **RU**: Сейчас тихонько постучу в двери по коридору. Подожди у кухонных плит — кто не спит за ледяными стенками, сейчас подтянется.
- **`burrow_resident.no_requests`**
  - **EN**: I've got everything I need tucked beside my cushion for now. Stay by the pond or library corner if you want a breather from the blizzard.
  - **ZH**: 我软垫旁边囤的小吃还够撑好一阵子呢。 外头风雪大，留在这儿的书架或鱼池边喘口气吧。
  - **RU**: У меня возле подушки сейчас всего хватает. Оставайся у пруда или в библиотеке, передохни от метели.
- **`burrow_resident.playful_greeting`**
  - **EN**: Hey—sneaking past the crate stacks without waking anybody? Come catch your breath by the warm vent, we're not allergic to good company!
  - **ZH**: 嘿——悄没声绕过那堆大箱子，没吵醒别人吧？ 来暖风口这边透口气，我们又不是连熟人都躲的木头！
  - **RU**: Эй — прокрался мимо штабеля ящиков и никого не разбудил? Подходи погреться к тёплой решётке, хорошей компании тут только рады!
- **`burrow_resident.playful_request`**
  - **EN**: Psst—could you smuggle %s %s down to me? Hand them straight here so Manul doesn't accuse me of raiding the pie counter early!
  - **ZH**: 嘘——方不方便悄悄给我捎 %s 份%s下来？ 直接塞给我免得让厨房里那只圆脸猫抓个现行，说我提前偷吃吧台上的点心！
  - **RU**: Псс — не притащишь мне по-тихому %s (%s)? Передай прямо в лапы, а то манул опять скажет, что я таскаю пироги раньше времени!
- **`burrow_resident.playful_thanks`**
  - **EN**: Ha! You're a lifesaver! I'll save you the cushiest pillow by the ice-cooled workshop vent later!
  - **ZH**: 哈！你可太懂行了！ 下次楼下铺冰工坊旁边那张最软的垫子我替你占着！
  - **RU**: Ха! Ну ты просто спаситель! В следующий раз займу для тебя самую мягкую подушку у прохладной мастерской!
- **`burrow_resident.polite_greeting`**
  - **EN**: Good day, neighbor. Mind the meltwater near the lower steps. Let me know quietly if there is anything you'd like checked downstairs.
  - **ZH**: 日安，邻居。下楼梯小心拐角化雪的水渍。 楼下若有什么需要核对的琐事，轻声同我说一声便是。
  - **RU**: Добрый день, сосед. Осторожнее со ступеньками у входа — там тает снег. Если нужно что-то проверить внизу, просто шепни мне.
- **`burrow_resident.polite_request`**
  - **EN**: Whenever your travels allow, could I ask for %s %s? Please hand them directly when convenient; there is no hurry here.
  - **ZH**: 方便的话，下次顺道可否请您帮带 %s 份%s过来？ 哪天得闲直接交予我就好，咱们这儿不赶工。
  - **RU**: Если будет удобно по пути, можно попросить у тебя %s (%s)? Передай при встрече, когда будет минутка — торопиться вовсе некуда.
- **`burrow_resident.polite_thanks`**
  - **EN**: My sincere thanks for carrying those down out of the cold. Do warm your hands by the kitchen stoves before heading back out.
  - **ZH**: 万分感谢您顶着严寒帮着带下楼来。 回去雪原前，务必先在灶台旁烘暖双手再动身。
  - **RU**: Искренне благодарю, что донёс это сквозь стужу. Обязательно согрей руки у кухонных плит, прежде чем уходить.
- **`burrow_resident.remember`**
  - **EN**: I still remember you carrying food down through the snowdrift for us. Always glad to share a quiet corner of the burrow with you.
  - **ZH**: 我可还记得你冒着雪给我们背下来那一箱箱口粮。 你什么时候想找个安静角落待会儿，地穴永远欢迎你。
  - **RU**: Я до сих пор помню, как ты нёс нам припасы сквозь метель. Всегда рад разделить с тобой тихий уголок под снегом.
- **`burrow_resident.request`**
  - **EN**: Could you spare %s %s for my corner shelf? Hand them right to me whenever you're downstairs—no rush at all.
  - **ZH**: 方不方便匀我 %s 份%s放到我这边架子上？ 等你下楼路过直接交给我就好，一点也不急。
  - **RU**: Найдётся ли у тебя %s (%s) для моей полки? Передай прямо мне, когда спустишься — совсем не спешу.
- **`burrow_resident.requests`**
  - **EN**: I was hoping to tuck away a little spare food for my room without emptying the main chest. Could you bring a bit next time you pass through?
  - **ZH**: 我本想给屋里攒点零嘴，又不忍心一直翻公共粮箱。 下回经过雪坡时方不方便顺手带一点？
  - **RU**: Хочется припасти немного еды себе в комнату, не трогая общий сундук. Не захватишь немного в следующий раз по пути?
- **`burrow_resident.reverse_rite`**
  - **EN**: Only the chiefs keep track of borrowed winter coats—ask either the Manul by the stoves or the Gray Fox upstairs.
  - **ZH**: 这儿借来的皮毛得由首领帮着解开——楼下灶台边的兔狲首领或者楼上的灰狐首领都能帮你。
  - **RU**: Вернуть прежний облик могут только вожаки — спроси манула у плит или серую лису наверху.
- **`burrow_resident.safe`**
  - **EN**: All the lower rooms are still, and nothing's clattering outside the birch fence. Good weather for staying indoors.
  - **ZH**: 地下几间屋子都静悄悄的，外头白桦围栏那也一点动静没有。 这天气最适合安安稳稳待在屋里了。
  - **RU**: Во всех комнатах внизу тихо, и за берёзовым забором никто не шумит. Самая погода, чтобы не вылезать наружу.
- **`burrow_resident.seekchief`**
  - **EN**: (The Manul steward tends the iron stoves downstairs while the Gray Fox lookout watches the birch stairs above—either chief can speak for the burrow.) Once you have a translator, telepathy, or latex form, neighbors here will open up a little too.
  - **ZH**: （楼下铁灶边守着兔狲管家，楼上桦树楼梯旁盯着外头的灰狐哨兵——找两位首领里的哪位都能打听清楚。） 等你有办法听懂这儿的语言，大家其实也挺乐意小声聊几句的。
  - **RU**: (У плит внизу дежурит распорядитель-манул, а наверху у берёзовой лестницы — дозорная серая лиса; любой из них всё расскажет про Нору.) Как только появится переводчик, телепатия или латексный облик, соседи тоже охотно поговорят.
- **`burrow_resident.thanks`**
  - **EN**: Thank you. I'll keep out of everyone's hair in my corner—pull up a cushion near the stove if you're cold.
  - **ZH**: 多谢啦。 我先安安静静回角落待着去，你要冷了就去灶火旁边的软垫暖和下吧。
  - **RU**: Спасибо большое. Пойду тихо устроюсь в своём уголке — а ты присядь на подушку у плиты, если продрог.
- **`burrow_resident.transfer`**
  - **EN**: Your hearth is still registered with %s right now. If you're thinking of trading blizzards for quiet underground rooms, talk to one of the two chiefs.
  - **ZH**: 你眼下登记的住处还在%s那边。 要是也想找个暖和又安稳的地下室避风雪，跟咱俩首领里的任意一位提一句就好。
  - **RU**: Пока ты числишься жильцом в общине «%s». Если захочешь сменить вьюги на тихие комнаты под снегом — переговори с любым из двух вожаков.
- **`burrow_resident.watch`**
  - **EN**: Something hostile keeps pacing near the snow ridge outside our stairwell. Nobody down here enjoys noisy fights—could you chase it off outside?
  - **ZH**: 外头楼梯口的雪坡上，老有个家伙晃过来又晃过去。 这底下大伙儿都图个安静——能不能帮着在上面把它打发走？
  - **RU**: Снаружи у спуска по склону бродит что-то недружелюбное. Внизу никто не любит драки — сможешь отвадить его со склона?
- **`burrow_resident.watch_thanks`**
  - **EN**: So that rattling outside stopped? Phew—everyone downstairs can breathe easy now.
  - **ZH**: 外头的动静真消停啦？ 呼……楼下总算又能安安静静待着了。
  - **RU**: Наверху и правда стихло? Фух — теперь внизу можно снова вздохнуть спокойно.
- **`burrow_resident.watch_wait`**
  - **EN**: Let's make sure nothing is creeping near the stairs first. Nobody here wants cold-weather surprises.
  - **ZH**: 先确保楼梯口彻底消停了再松气吧。 谁都不想大冷天突然碰上吓一跳的事。
  - **RU**: Давай сперва убедимся, что у лестницы всё чисто. Сюрпризов тут никто не любит.

#### Narrative Fragments (English Only)

- **`changed:note`** @ `(28, 13, 9)` — Unlocks `clue:burrow_rules`
  ```text
  HOUSE RULES (READ ONCE, THEN LEAVE ME ALONE)
  
  1. Wipe snow off your boots/paws BEFORE the rug.
  2. Stop leaving empty soup cans around the upstairs tree.
  3. Hot food goes on the red counter. Take a plate. You do not need to make conversation with me to eat.
  4. Stop hiding in the cardboard box room every time someone knocks upstairs. You keep tipping the crates over.
  
  — Ignore #4 if your battery is dead today, I'll handle the door! :)
  ```
- **`changed:computer`** @ `(28, 2, 33)` — Unlocks `clue:burrow_terminal`
  ```text
  Oct 14: Lower level wired. Warm-air heating is running steady, and we packed the back corner with enough ice and snow to keep me from overheating.
  
  She didn't want me to sign the clinic form. Said she'd already made peace with going through it alone, and I shouldn't throw away a healthy body just to keep up.
  
  Told her that wasn't really a choice. No point staying human if it meant watching her walk into the next hundred years by herself.
  
  Our double bed is down in the lowest room we could dig: warm air by the doorway for her, a whole pile of ice and snow on my side so this Manul fur doesn't cook me alive. She's already complaining that I kick off the blankets. Good. Means we're still here.
  ```
- **`item_frame(changed:compact_disc)`** @ `(18, 2, 31)` — Compact disc in invisible item frame; unlocks `clue:burrow_terminal`
  ```text
  move_in.txt
  
  Oct 14: Lower level wired. Warm-air heating is running steady, and we packed the back corner with enough ice and snow to keep me from overheating.
  
  She didn't want me to sign the clinic form. Said she'd already made peace with going through it alone, and I shouldn't throw away a healthy body just to keep up.
  
  Told her that wasn't really a choice. No point staying human if it meant watching her walk into the next hundred years by herself.
  
  Our double bed is down in the lowest room we could dig: warm air by the doorway for her, a whole pile of ice and snow on my side so this Manul fur doesn't cook me alive. She's already complaining that I kick off the blankets. Good. Means we're still here.
  ```

### 2.8 The Shoal (`the_shoal`)

- **Biome / Placement**: Ocean / Deep Ocean / Lukewarm Ocean (Overworld)
- **Resident Dialogue**: Built-in residents use station-specific root nodes by `DialogueRole` (`resident_fishing`, `resident_dorm`, `resident_storage`, `resident_workshop`) and share the `shoal_resident` camp-life voice.

#### Chief: Shoal Scavenger (`changed_synergy:latex_swordfish`) (`shoal_chief.json`, camp voice `scavenger`)

##### `root` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=human`):
  - **EN**: No way—a human actually made it out to my shack! I haven't seen one of you folks out here in… well, ever. Oh, sorry, how rude of me. Everyone around here calls me the Scavenger—just an old hand who still likes combing the ocean floor for treasure. Come on in, take a load off. Mi casa es tu casa!
  - **ZH**: 不会吧……真有个人类一路找到我这间小屋来了！我在这儿可……怎么说呢，从没见过你们。 噢，抱歉，太没礼貌了。这附近的人都叫我"拾荒者"，就是个老喜欢去海底翻宝贝的老家伙。快进来歇歇吧，mi casa es tu casa，当自己家就行！
  - **RU**: Не может быть, до моей хижины и правда добрался человек! Я никого из ваших тут не видел… да вообще никогда. Ой, извини, как невежливо. Местные зовут меня Старьевщиком: просто старый любитель пошарить по морскому дну в поисках сокровищ. Проходи, устраивайся. Mi casa es tu casa!
- **Passage 2** (`channel=mental`, `condition=human`):
  - **EN**: (Whoa, a real human on the pier! Angle the nose away, angle the nose away… last thing we need is poking our first human guest in the shoulder.)
  - **ZH**: （哇，栈桥上真站着个活人！把吻部偏开、偏开……可别一转头就把第一位人类客人给戳着了。）
  - **RU**: (Ого, настоящий человек на причале! Отверни нос, отверни нос… только не хватало ткнуть первого же гостя-человека в плечо.)
- **Passage 3** (`channel=spoken`, `condition=transformed`):
  - **EN**: Hey there, pal! Welcome out to The Shoal. Watch your step on the wet planks by the edge, the tide's been splashing up all morning. Guys around here call me the Scavenger. Grab a seat under the awning by the cooking pot and relax. Mi casa es tu casa!
  - **ZH**: 嗨，朋友！欢迎来到浅滩。留神边上湿滑的木板，一早上浪花都在往上拍。 这儿的人都叫我"拾荒者"。去白篷底下汤锅旁边找个位子坐着歇会儿吧，mi casa es tu casa，别客气！
  - **RU**: Привет, дружище! Добро пожаловать на Отмель. Осторожнее на мокрых досках у края, прилив с самого утра плещет. Здесь меня зовут Старьевщиком. Присаживайся под навесом у котелка и отдыхай. Mi casa es tu casa!
- **Passage 4** (`channel=mental`, `condition=transformed`):
  - **EN**: (Friendly face! Wonder if they walked over from the shore, and more importantly, if they've got any oranges in their pack.)
  - **ZH**: （看着挺面善！不知道是不是从岸边过来的，更重要的是，背包里有没有带橙子。）
  - **RU**: (Славный малый! Интересно, он с берега пришел, и, что куда важнее, есть ли у него в рюкзаке апельсины.)
- **Choices**:
  - `about` -> `about` (`condition=always`)
    - **EN**: What do you do out here on the water? | **ZH**: 你在这片水面上平时都忙些什么？ | **RU**: Чем ты тут занимаешься посреди воды?
  - `home` -> `home` (`condition=always`)
    - **EN**: This is a cozy spot… who else lives on the shoal with you? | **ZH**: 这地方看着真舒服，浅滩上还住着哪些同伴？ | **RU**: Уютное у вас место, кто еще живет с тобой на Отмели?
  - `past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: Don't you ever miss your old human form? | **ZH**: 你平时不会怀念自己从前的人类身体吗？ | **RU**: Ты совсем не скучаешь по своему старому человеческому телу?
  - `clue_disc` -> `clue_disc` (`condition=clue:shoal_disc`)
    - **EN**: I found an old dive log disc on the desk in the red room. | **ZH**: 我在红屋子的桌上发现了一张旧潜水日志光盘。 | **RU**: Я нашел старый диск с записями о погружениях на столе в красной комнате.
  - `clue_manifest` -> `clue_manifest` (`condition=clue:shoal_manifest`)
    - **EN**: I saw your salvage tally note in the red room. | **ZH**: 我看到红屋子里那张打捞清单便签了。 | **RU**: Я видел твою записку со списком находок в красной комнате.
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: What about me? Could I join your crew here? | **ZH**: 那我呢？能加入你们这支小队吗？ | **RU**: А как насчет меня? Можно присоединиться к вашей команде?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About my place here on the shoal… | **ZH**: 关于我留在浅滩这边的事…… | **RU**: Насчет моего места здесь, на Отмели…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `return` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey, look who's back! Come on up onto the boards. The pot's warm by the fire if you're hungry, and the ocean breeze is doing its job today.
  - **ZH**: 嘿，瞧瞧谁回来啦！快上甲板来。饿了的话火堆上的锅正热着，今天海风也吹得正舒服。
  - **RU**: Эй, гляньте, кто вернулся! Поднимайся на настил. Если проголодался, котелок на огне еще теплый, да и морской бриз сегодня что надо.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Good to see them again! Just don't turn around too fast near the doorway, workshop shark already yelled at me twice this week…)
  - **ZH**: （真高兴又见到他！不过门口附近千万别转身太猛，工坊里那条鲨鱼这礼拜已经数落我两回了……）
  - **RU**: (Рад снова его видеть! Только не разворачивайся слишком резко у двери, акула из мастерской уже дважды на меня ворчала на этой неделе…)
- **Choices**:
  - `about` -> `about` (`condition=always`)
    - **EN**: What do you do out here on the water? | **ZH**: 你在这片水面上平时都忙些什么？ | **RU**: Чем ты тут занимаешься посреди воды?
  - `home` -> `home` (`condition=always`)
    - **EN**: This is a cozy spot… who else lives on the shoal with you? | **ZH**: 这地方看着真舒服，浅滩上还住着哪些同伴？ | **RU**: Уютное у вас место, кто еще живет с тобой на Отмели?
  - `past` -> `past` (`condition=always`, `requires=['about', 'home']`)
    - **EN**: Don't you ever miss your old human form? | **ZH**: 你平时不会怀念自己从前的人类身体吗？ | **RU**: Ты совсем не скучаешь по своему старому человеческому телу?
  - `clue_disc` -> `clue_disc` (`condition=clue:shoal_disc`)
    - **EN**: I found an old dive log disc on the desk in the red room. | **ZH**: 我在红屋子的桌上发现了一张旧潜水日志光盘。 | **RU**: Я нашел старый диск с записями о погружениях на столе в красной комнате.
  - `clue_manifest` -> `clue_manifest` (`condition=clue:shoal_manifest`)
    - **EN**: I saw your salvage tally note in the red room. | **ZH**: 我看到红屋子里那张打捞清单便签了。 | **RU**: Я видел твою записку со списком находок в красной комнате.
  - `enrol` -> `enrol` (`condition=stranger`)
    - **EN**: What about me? Could I join your crew here? | **ZH**: 那我呢？能加入你们这支小队吗？ | **RU**: А как насчет меня? Можно присоединиться к вашей команде?
  - `membership` -> `membership` (`condition=member`)
    - **EN**: About my place here on the shoal… | **ZH**: 关于我留在浅滩这边的事…… | **RU**: Насчет моего места здесь, на Отмели…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `about` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Back when I was human, I made my living as a deep-sea scavenger. Picture it: diving down to the darkest stretches of the ocean floor, hunting for sunken cargo, gold, diamonds—whatever good, shiny stuff the tide buried—and hauling it back up. That was my job, and it paid great back in the day. Honestly, I still do it just for the thrill. This sleek blue body isn't just for show either. If you ever want to try arm wrestling, better warm up first, champ!
  - **ZH**: 以前还是人类的时候，我是干深海打捞这行的。你想想：潜到海底最深的地方，搜罗沉船货物、金子、钻石……只要是海里埋着的闪亮好东西都捞上来。 这就是我的老本行，当年不仅刺激，赚得也多。说实话，现在没买卖可做，我还是图个乐子天天往海里钻。我这身流线蓝皮可不只是看着拉风，哪天想掰手腕的话，先热好身再说，伙计！
  - **RU**: Когда я еще был человеком, я зарабатывал на жизнь глубоководным поиском. Представь: ныряешь в самые глубокие участки дна, ищешь затонувший груз, золото, алмазы — все блестящее и ценное, что утянуло под воду, и поднимаешь наверх. Такая вот работа: и дух захватывало, и платили в те времена отлично. Честно говоря, я до сих пор ныряю ради азарта. Да и это обтекаемое синее тело не только для вида: захочешь побороться на руках, сначала разомнись как следует, дружище!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Not to brag, but I haven't lost an arm-wrestling match since I hauled that orange shipping container onto the pier.)
  - **ZH**: （不是吹牛，自从把那个橙色集装箱拖上栈桥以后，我掰手腕就没输过。）
  - **RU**: (Не хвастаюсь, но я ни разу не проигрывал на руках с тех пор, как затащил тот оранжевый контейнер на причал.)
- **Choices**:
  - `containers` -> `containers` (`condition=always`)
    - **EN**: Those huge shipping containers out on the deck… did you haul them up yourself? | **ZH**: 甲板上那几个大集装箱，都是你自己从海里捞上来的？ | **RU**: Те огромные контейнеры на палубе… ты сам их вытащил?
  - `swordfish` -> `swordfish` (`condition=always`)
    - **EN**: Such a long nose… are you a Latex Swordfish? | **ZH**: 你的鼻子真长……你是胶兽旗鱼吗？ | **RU**: Какой длинный нос… ты латексная рыба-меч?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `containers` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Those big rusty boxes, the orange one and the blue one? Yep, spotted them half-buried out past the reef while scouting the seabed! Took a whole afternoon, two sharks, and a lot of rope to get them up onto the stilts, and half the deck groaned when we set them down. Still, you can't beat free dry storage out in the middle of the ocean.
  - **ZH**: 你说那两个锈迹斑斑的大箱子，橙色和蓝色那俩？没错，我在礁石外头探海底的时候发现的，当时都埋了一半！ 整整折腾了一个下午，拉上两条鲨鱼、绑了一堆绳子才拖上木桩，放下来的时候半边甲板都在咯吱响。不过话说回来，大海上白捡的防潮仓库，可上哪儿找去。
  - **RU**: Те большие ржавые коробки: оранжевую и синюю? Ага, заметил их полузарытыми за рифом, когда осматривал дно! Ушел целый день, две акулы и куча канатов, чтобы втащить их на сваи, а настил аж застонал, когда мы их опустили. Зато сухой склад посреди океана, и совершенно бесплатно.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Still wish one of them had been full of canned oranges instead of wet cardboard and old lab crates.)
  - **ZH**: （到现在我还盼着当初里面装的是橘子罐头，而不是湿纸箱和旧实验室木箱。）
  - **RU**: (До сих пор жаль, что внутри оказались мокрый картон и старые лабораторные ящики вместо консервированных апельсинов.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `swordfish` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Sharp eyes, pal! Yeah, I'm a Latex Swordfish. This rostrum isn't just decoration: it's sharper than any spear and cuts water drag down to almost nothing. Give me open water and I can shoot forward and launch straight into the air like a rocket, so long as I'm starting from the water, anyway. On dry planks I mostly have to remember not to turn around too fast near the furniture.
  - **ZH**: 眼神真尖，伙计！没错，我是胶兽旗鱼。这根长吻可不只是摆设，比任何长矛都锋利，在水里几乎能把阻力削成零。 只要下到水里，我就能猛冲出去，像火箭一样直接跃出水面——当然，得沾着水才行。在干木板上嘛，我主要得时刻记着别在家具旁边转身太快。
  - **RU**: Глаз-алмаз, дружище! Да, я латексная рыба-меч. Этот рострум не просто для красоты: он острее любого копья и сводит сопротивление воды почти к нулю. В открытой воде я могу разогнаться и вылететь в воздух как ракета, разумеется, пока отталкиваюсь от воды. А на сухих досках главное: помнить не разворачиваться слишком резко рядом с мебелью.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Also impossible to hug someone from the front without jabbing them. Side-hugs only. Learned that the hard way.)
  - **ZH**: （而且根本没法从正面抱人，一抱就戳着。只能侧着抱，这是吃过亏才学会的。）
  - **RU**: (А еще никого невозможно обнять спереди, чтобы не ткнуть. Только сбоку. Усвоено на горьком опыте.)
- **Choices**:
  - `explain_form` -> `explain_form` (`condition=can_reversible`)
    - **EN**: Could I try becoming a Latex Swordfish myself? | **ZH**: 我也能试试变成胶兽旗鱼吗？ | **RU**: А я сам могу попробовать стать латексной рыбой-мечом?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `home` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey, thanks! I know it's not one of those fancy beachfront villas from the old days, just stilts, salvaged timber, and a windmill we rigged up to catch the breeze, but being right over the ocean makes up for everything. I'm not out here by myself, either. A few aquatic folks who share my love for the sea and scavenging moved in with me: two sharks handling the storage deck and the workshop, an orca over by the fishing deck, and an eel who claimed the blue dorm. Last I heard, a couple of rays were thinking about moving in too.
  - **ZH**: 嘿，谢啦！我知道这儿比不上过去那些气派的海滨别墅，不过是木桩、打捞回来的木料，外加我们凑合搭起来借风用的风车，可就建在大海正上方，这点比啥都强。 这儿也不是只有我一个人。几个跟我一样爱大海、爱打捞的水生伙伴也搬来一起住了：两条鲨鱼管着储物甲板和工坊，一头虎鲸常守在钓鱼区那边，还有一条鳗鱼占了蓝色宿舍。前阵子我听说，还有几条鳐鱼也正琢磨着搬过来呢。
  - **RU**: Эй, спасибо! Знаю, это не какая-нибудь шикарная пляжная вилла из старых времен: просто сваи, выловленные доски да ветряк, который мы собрали ловить бриз, но жить прямо над океаном перекрывает все. И я здесь не один. Ко мне подселились несколько водных ребят, которые так же любят море и поиски: две акулы присматривают за складом и мастерской, косатка держится у рыболовного края, а угорь облюбовал синюю спальню. Слышал, еще пара скатов подумывает перебраться к нам.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (If the rays actually move in, we're going to need another row of beds in the room, or a bigger awning…)
  - **ZH**: （要是鳐鱼们真搬来了，宿舍里就得再加一排床，或者把外头的白篷再扩一圈。）
  - **RU**: (Если скаты и правда переедут, придется еще ряд кроватей в синей комнате или расширять навес.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `past` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You know, I do look back on my old human self now and then and get a little nostalgic. Still, look at the bright side: I'm right by the ocean every single morning, I'm stronger than I ever was, and I can breathe underwater without watching an air gauge tick down. What more could an old diver ask for?
  - **ZH**: 说实话，我偶尔确实会想起从前还是人类的自己，多少有点怀旧。 不过往好处看嘛：我现在每天一睁眼就在大海上，力气比从前大得多，潜到水下还能自由呼吸，再也不用死盯着氧气表往下掉了。一个老潜水员还能图什么呢？
  - **RU**: Знаешь, я и правда иногда вспоминаю себя прежнего, человеком, и немного накатывает ностальгия. Но, с другой стороны, посмотри: каждое утро я прямо у океана, сил больше, чем когда-либо, и можно дышать под водой, не поглядывая на стрелку манометра. Чего еще желать старому ныряльщику?
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Okay, maybe one thing: eating a fresh orange without getting juice all over a three-foot nose. Still working on that technique.)
  - **ZH**: （好吧，也许还差一件事：吃新鲜橙子的时候怎么才不会把果汁溅满三英尺长的鼻子。这手艺还在练。）
  - **RU**: (Ладно, пожалуй, одного все-таки не хватает: научиться есть свежий апельсин, не заляпав соком весь свой метровый нос. Все еще отрабатываю технику.)
- **Choices**:
  - `diving_suit` -> `diving_suit` (`condition=always`)
    - **EN**: If you can breathe underwater now, why do you still wear that diving suit? | **ZH**: 既然你现在在水下本来就能呼吸，为什么还一直穿着那身潜水服？ | **RU**: Если ты теперь можешь дышать под водой, зачем ты до сих пор носишь этот водолазный костюм?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `diving_suit` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Ah, this old thing… Well, like I said, I haven't forgotten who I used to be. I know the suit doesn't actually do anything for me anymore—doesn't keep me warmer, doesn't help me swim faster. It's a memento from a very good friend from back in my diving days. Wearing it helps me hold onto the humanity I once had.
  - **ZH**: 啊，这身老伙计啊……就像我刚才说的，我一直没忘记自己从前是谁。 我知道穿着它对我现在一点实际用处都没有——既不更保暖，也游不更快。可这是当年潜水时一位非常要好的朋友留下的念想。穿着它，能让我一直记着自己曾经身为人的那份心气。
  - **RU**: А, этот старый костюм… Ну, как я и говорил, я не забыл, кем был раньше. Знаю, толку от него мне теперь никакого — ни греет лучше, ни плавать не помогает. Это память об одном очень хорошем друге еще со времен моих погружений. Пока я его ношу, проще помнить о том человеке, которым я когда-то был.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Plus he always said the blue stripe down the leg looked sharp. Turns out it matches the latex too.)
  - **ZH**: （而且他老说裤腿上那道蓝条纹特别帅气。结果现在和我的蓝色胶体还挺搭。）
  - **RU**: (К тому же он всегда говорил, что синяя полоса вдоль ноги выглядит стильно. А теперь она еще и под цвет латекса подходит.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `clue_disc` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Whoa, you spun up that old disc on the desk? Man, I haven't listened to that log in years. Yeah, that was my old boat partner: the same friend who left me this diving suit before everything changed. He always worried I'd sink a boat chasing shiny scrap. Wherever he ended up, I hope he'd get a kick out of seeing me still pulling containers off the ocean floor.
  - **ZH**: 哇，你把桌上那张旧光盘读出来啦？天哪，那段记录我都好多年没翻过了。 没错，那就是我当年的船上搭档，就是那位在变故前把这身潜水服留给我的好朋友。他老担心我为了捞闪亮亮的废铁把船弄沉。不管他最后去了哪儿，希望他看见我现在还在海底捞集装箱，会乐得笑出来吧。
  - **RU**: Ого, ты запустил тот старый диск со стола в красной комнате? Эх, я сто лет не перечитывал эту запись. Да, это мой старый напарник по катеру — тот самый друг, который оставил мне этот костюм до всех перемен. Он вечно переживал, что я потоплю лодку в погоне за блестящим хламом. Где бы он сейчас ни был, надеюсь, его бы повеселило, что я все еще таскаю контейнеры со дна.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `clue_manifest` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey, don't judge the tally sheet! When you haul up two whole shipping containers and a crate of lab cans, somebody's got to keep track. And for the record, that note at the bottom about "five dented doorway slats" is a total exaggeration. It was three slats, maybe four. Narrow doors are a hazard for swordfish, alright?
  - **ZH**: 嘿，可别笑话那张清单！你一口气捞上来两个大集装箱外加一箱实验室罐头，总得有人记账吧。 还有，我必须声明，最底下写着"戳坏五块门框木条"纯属夸大其词！明明只有三块，顶多四块。窄门对旗鱼来说本来就是安全隐患，好不好？
  - **RU**: Эй, не суди строго наш список! Когда поднимаешь два целых контейнера и ящик лабораторных банок, кто-то должен вести учет. И к сведению: приписка внизу про «пять пробитых дверных планок» — сплошное преувеличение. Их было три, ну, может, четыре. Узкие двери для рыбы-меча вообще опасны, ладно?
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `enrol` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=transformed_join`):
  - **EN**: Life stays pretty lively out on the water, pal, always planks to patch, nets to check, and food to haul in. I only speak for our crew here on the shoal, mind you, not every swimmer in the sea. If you're up for helping out and sharing the deck, we'd love to have you on board!
  - **ZH**: 大海上的日子可闲不下来，伙计，总有木板要补、渔网要看、吃的要往回搬。不过先说好，我只代表咱们浅滩这帮同伴，可代表不了大海里所有的水生胶兽。 要是你愿意搭把手、跟大伙儿共享这片甲板，随时欢迎上船！
  - **RU**: Жизнь на воде не стоит на месте, дружище: вечно надо чинить доски, проверять сети и таскать еду. Учти только, я говорю лишь за нашу команду на Отмели, а не за всех обитателей моря. Если готов помогать и делить с нами палубу, добро пожаловать на борт!
- **Passage 2** (`channel=spoken`, `condition=human_join`):
  - **EN**: Life moves pretty quick out on the ocean, and I haven't made a proper run to the shore in ages, so an extra pair of hands around here would be huge! We welcome humans and latexes alike, no pressure at all. You can join the crew just as you are, or if you're curious what it's like to swim as a Latex Swordfish, I can share the form temporarily or for good. Your call, champ.
  - **ZH**: 海上的活儿可不少，而且我好久没正经去岸边跑一趟了，多一双帮手简直太省事了！ 咱们这儿不管人类还是胶兽都欢迎，一点压力都没有。你可以就按现在的样子入伙；要是你好奇当一条胶兽旗鱼是什么感觉，我也可以把形态分享给你，暂时的或者永久的都行。全看你的，伙计。
  - **RU**: Дел в океане всегда хватает, а я сто лет нормально не выбирался на берег, так что лишняя пара рук нам очень кстати! Мы принимаем и людей, и латексных без всякого давления. Можешь вступить в команду как есть, а если любопытно поплавать в облике латексной рыбы-меча — могу поделиться формой на время или навсегда. Решать тебе, дружище.
- **Passage 3** (`channel=spoken`, `condition=cannot_join`):
  - **EN**: Hold up a bit, pal: the crew's still a little rattled right now, so I can't sign you onto the shoal just yet. Give folks a little space to cool off, and we'll talk about bunking down once things settle.
  - **ZH**: 先等等，伙计。船员们这会儿心神还有点不宁，我暂时没法把你记进浅滩名册里。先给大家留点喘口气的空间，等风浪平息下来咱们再聊住下的事。
  - **RU**: Притормози немного, дружище: команда сейчас не в духе, так что я пока не могу записать тебя к нам. Дай ребятам немного остыть, и вернемся к разговору о койке, когда все уляжется.
- **Choices**:
  - `join_latex` -> `welcome_latex` (`condition=transformed_join`, `effect=join`)
    - **EN**: Count me in, I'll join the crew as I am. | **ZH**: 算我一个，我以现在的形态入伙。 | **RU**: Я в деле, присоединюсь к команде как есть.
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll help out as much as I can, sign me in as a human! | **ZH**: 我会尽力帮忙的，我以人类身份入伙！ | **RU**: Буду помогать чем смогу, записывай меня человеком!
  - `explain_form` -> `explain_form` (`condition=can_reversible`)
    - **EN**: What's it like becoming a Latex Swordfish? | **ZH**: 变成胶兽旗鱼是什么体验？ | **RU**: Каково это — стать латексной рыбой-мечом?
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `explain_form` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: As a Latex Swordfish, you get a long, spear-sharp rostrum for hunting fish, fast swimming in deep water, underwater breathing, and the ability to launch yourself out of the waves like a rocket. Breathing regular air can feel a little weird at first, though you get the hang of it quick—and you'll want to take doorways wide so you don't clip the frames! Go reversible and you can switch back to human whenever you want. Go permanent and the ocean coat is yours for good. Staying human is completely fine too—same spot by the campfire either way.
  - **ZH**: 成了胶兽旗鱼，你会长出一根像长矛一样锋利的长吻方便捕鱼，在水里游得飞快、能直接呼吸，还能像火箭一样从海浪里跃向空中。 刚开始呼吸普通空气可能会有点别扭，不过很快就能适应，还有过门框记得拐大弯，免得把木框给戳了！ 选可逆的话，随时可以变回人类；选永久的话，这身海蓝形态就一直跟着你了。保持人类身份也完全没问题，不管选哪种，篝火旁都有你的位置。
  - **RU**: Став латексной рыбой-мечом, ты получишь острый как копье рострум для охоты на рыбу, высокую скорость под водой, подводное дыхание и способность выстреливать из волн как ракета. Дышать обычным воздухом поначалу бывает непривычно, но быстро приноровишься — и в дверные проемы заходи пошире, чтобы не задевать косяки! Выберешь обратимый вариант — сможешь вернуться в человеческий вид позже. Выберешь постоянный — останешься таким навсегда. Остаться человеком тоже отлично: место у костра за тобой в любом случае.
- **Choices**:
  - `reversible` -> `welcome_copy` (`condition=can_reversible`, `effect=reversible`)
    - **EN**: Alright, I'm in, let's try the reversible swordfish form and join! | **ZH**: 行，我加入，先试试可逆的旗鱼形态吧！ | **RU**: Ладно, я в деле: давай попробуем обратимую форму рыбы-меча, и я с вами!
  - `permanent_menu` -> `confirm_permanent` (`condition=can_permanent`)
    - **EN**: Make it permanent, I want to be a swordfish for good and join! | **ZH**: 永久转化吧，我想一直作为旗鱼留在这儿！ | **RU**: Делай навсегда: хочу остаться рыбой-мечом и быть в команде!
  - `join_human` -> `welcome_human` (`condition=human_join`, `effect=join`)
    - **EN**: I'll stay human and join the crew. | **ZH**: 我还是保持人类身份入伙吧。 | **RU**: Пожалуй, останусь человеком и вступлю в команду.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hold on a second, champ, let's be 100% sure before we do this. Making it permanent means this swordfish body stays yours for good. You can still pick the reversible option or stay human and keep your bunk here either way. Are you sure you want to make it permanent?
  - **ZH**: 先等一秒，伙计。动手前咱们得百分之百确认好。选了永久，这具旗鱼身体今后就永远属于你了。 你现在改选可逆形态或者保持人类都来得及，反正铺位照样归你。确定要永久转化吗？
  - **RU**: Погоди секунду, дружище — давай убедимся на все сто. Постоянный вариант значит, что тело рыбы-меча останется твоим навсегда. Ты все еще можешь выбрать обратимый вариант или остаться человеком — койка за тобой в любом случае. Уверен, что хочешь навсегда?
- **Choices**:
  - `confirm` -> `welcome_permanent` (`condition=can_permanent`, `effect=permanent`)
    - **EN**: Yes, I'm sure. Make it permanent. | **ZH**: 是的，我确定，永久转化吧。 | **RU**: Да, я уверен. Делай навсегда.
  - `rethink` -> `explain_form` (`condition=always`)
    - **EN**: Let me think it over again. | **ZH**: 让我再考虑一下。 | **RU**: Дай еще подумать.

##### `welcome_human` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Yo, I like that spirit! Alright, champ, let's get you settled in. The chest near the campfire is for the whole crew: we all share what's in it, and you're welcome to take or add to it anytime. Some of us love fish, raw or cooked, and others swear by dried kelp. Oh, and if you ever bring back spare oranges from the shore… oof, I'd owe you big time. Just please don't go peeking into the other chests; folks here still keep personal belongings in their own boxes.
  - **ZH**: 哟，我就喜欢你这股劲头！行，伙计，咱们安顿下来吧。篝火旁那个箱子是全船员共用的，大伙儿一起分，你随时可以取用或者往里添东西。 我们当中有人爱吃生鱼熟鱼，也有人离不开烤海带。噢，还有，你要是哪天从岸边顺手带回点多余的橙子……哇，那我可欠你大人情了。只是千万别去翻别的箱子，大家各自的私人物品还是自己收着的。
  - **RU**: О, вот это настрой! Ладно, дружище, устраивайся. Сундук у костра — общий для всей команды: все делятся тем, что там лежит, и ты можешь брать или класть туда припасы в любое время. Кто-то у нас обожает рыбу, сырую или жареную, а кто-то жить не может без сушеной ламинарии. О, и если когда-нибудь принесешь с берега лишних апельсинов… ух, я в долгу не останусь. Только, пожалуйста, не заглядывай в остальные сундуки, там у ребят личные вещи.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Yes! A human crewmate! That means shore trips for oranges just got way easier!)
  - **ZH**: （太好了！有人类船员了！以后跑岸边摘橙子可方便多了！）
  - **RU**: (Отлично! Человек в команде! Значит, вылазки на берег за апельсинами станут куда проще!)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `welcome_latex` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Welcome to the crew! Grab an open bed in the dorm, use the shared chest near the campfire whenever you need supplies, and keep your hands out of people's personal chests. Glad to have you on the shoal!
  - **ZH**: 欢迎入伙！宿舍里空着的床随便选，缺物资随时用篝火边的公共箱子，别乱翻别人的私人箱子就行。很高兴你能留在浅滩！
  - **RU**: Добро пожаловать в команду! Занимай свободную кровать в спальне, пользуйся общим сундуком у костра, когда понадобятся припасы, и не трогай личные ящики. Рады видеть тебя на Отмели!
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `welcome_copy` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Haha, that's what I like to hear! Easy now, let the rostrum settle properly, it'll be your best tool for catching fish out past the stilts. That navy-blue coat's going to make you fly through the water, and breathing down below will feel like second nature. Regular air might feel a little funny for the first minute, so take it slow: you'll get the hang of it in no time. And hey, remember to share the chest by the campfire and keep out of personal boxes. Welcome aboard, champ!
  - **ZH**: 哈哈，我就爱听这话！慢点来，让长吻先稳稳长好，以后在木桩外头捕鱼全靠它了。这身海蓝胶体能让你在水里游得飞快，水下呼吸也会像本能一样自然。 头一分钟呼吸普通空气可能会有点怪，别急，很快就能习惯。对了，篝火边的箱子大家共用，私人箱子别乱翻。欢迎上船，伙计！
  - **RU**: Ха-ха, вот это по-нашему! Потише, дай роструму как следует сформироваться, он станет твоим главным инструментом для ловли рыбы за сваями. В этой темно-синей шкуре ты будешь летать под водой, а дышать там станет проще простого. Обычный воздух первую минуту может казаться странным, не торопись: быстро привыкнешь. И помни: общий сундук у костра, личные ящики не трогаем. Добро пожаловать на борт, дружище!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Awesome! Another swordfish on deck! Workshop shark is gonna lose his mind when both of us walk through the narrow doorway at once.)
  - **ZH**: （太酷了！甲板上又多了一条旗鱼！等咱俩一块挤过那扇窄门，工坊鲨鱼怕是要抓狂了。）
  - **RU**: (Круто! Еще одна рыба-меч на палубе! Акула в мастерской с ума сойдет, когда мы оба одновременно зайдем в узкую дверь.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `welcome_permanent` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Haha, that's what I like to hear! Easy now, let the rostrum settle properly, it'll be your best tool for catching fish out past the stilts. That navy-blue coat's going to make you fly through the water, and breathing down below will feel like second nature. Regular air might feel a little funny for the first minute, so take it slow: you'll get the hang of it in no time. And hey, remember to share the chest by the campfire and keep out of personal boxes. Welcome aboard, champ!
  - **ZH**: 哈哈，我就爱听这话！慢点来，让长吻先稳稳长好，以后在木桩外头捕鱼全靠它了。这身海蓝胶体能让你在水里游得飞快，水下呼吸也会像本能一样自然。 头一分钟呼吸普通空气可能会有点怪，别急，很快就能习惯。对了，篝火边的箱子大家共用，私人箱子别乱翻。欢迎上船，伙计！
  - **RU**: Ха-ха, вот это по-нашему! Потише, дай роструму как следует сформироваться, он станет твоим главным инструментом для ловли рыбы за сваями. В этой темно-синей шкуре ты будешь летать под водой, а дышать там станет проще простого. Обычный воздух первую минуту может казаться странным, не торопись: быстро привыкнешь. И помни: общий сундук у костра, личные ящики не трогаем. Добро пожаловать на борт, дружище!
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Awesome! Another swordfish on deck! Workshop shark is gonna lose his mind when both of us walk through the narrow doorway at once.)
  - **ZH**: （太酷了！甲板上又多了一条旗鱼！等咱俩一块挤过那扇窄门，工坊鲨鱼怕是要抓狂了。）
  - **RU**: (Круто! Еще одна рыба-меч на палубе! Акула в мастерской с ума сойдет, когда мы оба одновременно зайдем в узкую дверь.)
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `membership` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You're part of The Shoal crew. The shared chest near the campfire and any free bed in the blue dorm are yours to use; private crates and bedside storage stay with their owners. If the tide ever pulls you somewhere else, just let me know: no hard feelings, and your body stays your own.
  - **ZH**: 你现在是咱们浅滩的一员了。篝火边的公共箱子和蓝色宿舍里的空床随时能用，私人木箱和床头储物归各自保管。 哪天要是海潮把你带去别的地方，跟我说一声就行，绝不怪你，你的身体也依然属于你自己。
  - **RU**: Ты часть команды Отмели. Общий сундук у костра и любая свободная кровать в синей спальне — в твоем распоряжении; личные ящики и тумбочки остаются за их хозяевами. Если течение когда-нибудь унесет тебя в другие края, просто скажи: никаких обид, и твое тело остается твоим.
- **Choices**:
  - `leave_menu` -> `confirm_leave` (`condition=member`)
    - **EN**: I need to leave The Shoal and move on. | **ZH**: 我得离开浅滩继续赶路了。 | **RU**: Мне нужно покинуть Отмель и плыть дальше.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `confirm_leave` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Sure you're ready to shove off, pal? Once I take your name off the crew list, the shared chest won't be open to you anymore, though you can still stop by the pier to visit anytime.
  - **ZH**: 确定要起锚出发了吗，伙计？把你从船员名单划掉以后，公共补给箱就不能随便取用了，不过以后随时还能来栈桥串门。
  - **RU**: Точно готов отчаливать, дружище? Как вычеркну тебя из списка команды, общий сундук для тебя закроется, но заглядывать в гости на причал можно в любое время.
- **Choices**:
  - `confirm_leave` -> `left` (`condition=member`, `effect=leave`)
    - **EN**: Yes, I'm heading out. | **ZH**: 确定，我要出发了。 | **RU**: Да, я отправляюсь в путь.
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…

##### `left` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Fair winds and calm seas, champ. Drop by the deck whenever you're swimming past, we'll keep a seat dry for you.
  - **ZH**: 祝你一路顺风顺水，伙计。哪天游过这片海域记得上甲板坐坐，我们给你留着干爽位子。
  - **RU**: Попутного ветра и спокойного моря, дружище. Заглядывай на палубу, если будешь проплывать мимо, прибережем тебе сухое местечко.
- **Choices**:
  - `back` -> `return` (`condition=always`)
    - **EN**: Something else… | **ZH**: 聊点别的…… | **RU**: О другом…
  - `goodbye` -> `goodbye` (`condition=always`)
    - **EN**: I'll let you get back to your work. See you! | **ZH**: 不打扰你忙活了，回头见！ | **RU**: Не буду отвлекать тебя от дел. Увидимся!

##### `goodbye` (Chief Node)
- **Passage 1** (`channel=spoken`, `condition=stranger`):
  - **EN**: Hope you come visit us again anytime, pal! It'll be nice having you back on the deck.
  - **ZH**: 欢迎随时再来玩，伙计！能再在甲板上见到你肯定挺开心的。
  - **RU**: Надеюсь, ты еще заглянешь к нам в гости, дружище! Будем рады снова видеть тебя на палубе.
- **Passage 2** (`channel=spoken`, `condition=member`):
  - **EN**: You know where we live, champ! Make sure to bring back one big ol' salmon for dinner!
  - **ZH**: 你知道咱家在哪儿，伙计！记得拎条肥厚的大鲑鱼回来当晚饭！
  - **RU**: Ты знаешь, где нас найти, дружище! Не забудь притащить к ужину здоровенного лосося!
- **Choices**:
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: See you! | **ZH**: 回头见！ | **RU**: Увидимся!

##### `resident_fishing` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Hey there. Watch where you put your feet on the trapdoors, they look flimsy, but they hold even my weight just fine.
  - **ZH**: 嗨。走那些活板门时留意脚下，看着单薄，其实连我都踩得住。
  - **RU**: Привет. Смотри под ноги на люках: с виду хлипкие, но держат даже мой вес.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Salmon's biting, water's calm, sun's warm on my back. Best seat in the whole ocean!)
  - **ZH**: （鲑鱼肯咬钩，水面风平浪静，太阳晒得后背暖洋洋的。全大海最舒服的位子就在这儿！）
  - **RU**: (Лосось клюет, вода спокойная, солнце греет спину. Лучшее место во всем океане!)
- **Choices**:
  - `topic_0` -> `resident_fishing_0` (`condition=always`)
    - **EN**: You handle the fishing and the cooking pot out here? | **ZH**: 外头的钓鱼和汤锅这边平时都是你在打理吗？ | **RU**: Ты здесь заправляешь рыбалкой и котелком на палубе?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 待会儿见。 | **RU**: Еще увидимся.

##### `resident_fishing_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Most days, yeah. The Scavenger dives for the big heavy scrap, the sharks sort and fix it, and I make sure nobody forgets to eat dinner. We grow greens right here in the deck planters, and we keep wheat up on the red roof. The only thing we can't grow out on the stilts is oranges, so if you ever bring a few from the shore, you'll make the boss's whole week.
  - **ZH**: 大多数时候是我管。拾荒者负责下水捞大件废铁，两条鲨鱼管分拣和修理，我则盯着大家别忘了吃晚饭。 甲板花槽里种着蔬菜，红屋顶上还种了小麦。木桩上唯一种不出来的就是橙子，所以你要是哪天从岸边带回几个，老大能高兴一整周。
  - **RU**: В основном да. Старьевщик ныряет за крупным ломом, акулы его разбирают и чинят, а я слежу, чтобы никто не забывал поужинать. Зелень мы растим прямо здесь в ящиках на палубе, а пшеницу — на красной крыше. Единственное, что на сваях не вырастишь, — апельсины, так что если принесешь с берега пару штук, босс будет счастлив всю неделю.
- **Choices**:
  - `back` -> `resident_fishing` (`condition=always`)
    - **EN**: Back. | **ZH**: 聊点别的。 | **RU**: Назад.
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Thanks, catch you later. | **ZH**: 谢啦，回头见。 | **RU**: Спасибо, увидимся позже.

##### `resident_dorm` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Oh… hey. Come on in, just try not to drip saltwater all over the rugs if you just climbed out of the sea. Five beds in here, nice cool walls, and the best ocean view through the windows at sunset. Looking for a spot to crash?
  - **ZH**: 噢……嗨。请进，如果你刚从海里爬上来，尽量别把海水滴满地毯就行。 这屋里有五张床，墙面凉爽，日落时透过窗户看海景最棒。是来找地方歇脚的吗？
  - **RU**: О… привет. Заходи, только постарайся не накапать соленой водой на ковер, если только что вылез из моря. Тут пять кроватей, прохладные стены и лучший вид на океан на закате. Ищешь место передохнуть?
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Lanterns are warm, floorboards are smooth, and nobody's hammering outside right now. Prime napping conditions!)
  - **ZH**: （提灯暖烘烘的，地板滑溜溜的，外头这会儿也没人叮当敲锤子。绝佳的午睡条件！）
  - **RU**: (Фонари теплые, пол гладкий, и снаружи сейчас никто не стучит молотком. Идеальные условия для сна!)
- **Choices**:
  - `topic_0` -> `resident_dorm_0` (`condition=always`)
    - **EN**: Is it noisy sleeping right over the waves? | **ZH**: 就睡在海浪正上方，晚上不会吵吗？ | **RU**: Не шумно спать прямо над волнами?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 待会儿见。 | **RU**: Еще увидимся.

##### `resident_dorm_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Not once you get used to it, the waves hitting the stilts underneath actually put me right to sleep. Just a heads-up if you stay the night: those oranges on the nightstand belong to the Scavenger's emergency stash. Touch those without asking and you'll hear about it before breakfast.
  - **ZH**: 习惯了就不吵了，底下的浪头拍打木桩的声音，听着反而特别催眠。 不过要是留宿的话提醒你一句：床头柜上那几个橙子是拾荒者的应急存货。没打招呼就动它们，早饭前你就得听他念叨了。
  - **RU**: Не-а, когда привыкнешь, плеск волн о сваи внизу наоборот отлично убаюкивает. Только предупреждаю, если останешься на ночь: апельсины на тумбочке — неприкосновенный запас Старьевщика. Тронешь без спросу, услышишь об этом еще до завтрака.
- **Choices**:
  - `back` -> `resident_dorm` (`condition=always`)
    - **EN**: Back. | **ZH**: 聊点别的。 | **RU**: Назад.
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Thanks, catch you later. | **ZH**: 谢啦，回头见。 | **RU**: Спасибо, увидимся позже.

##### `resident_storage` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Watch your elbow near the cardboard boxes, the sea breeze already blew half my labels into the water once today. If you're looking for shared food or supplies, that's in the chest over there. The big containers and crates on this side are salvage we're still sorting through.
  - **ZH**: 胳膊肘留神旁边那堆纸箱，海风今天已经把我一半标签吹海里去了。 如果你是找公用食物或补给，箱子在那边。这边的大集装箱和木箱都是还在分拣的打捞货。
  - **RU**: Осторожнее локтями у картонных коробок — морской бриз сегодня уже унес половину моих ярлыков в воду. Если ищешь общую еду или припасы, они в сундуке. А большие контейнеры и ящики здесь — это еще неразобранные находки со дна.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Twelve cans of soup, four bent pipes, one swivel chair with a squeaky wheel… why did we haul an office chair out to the middle of the ocean?)
  - **ZH**: （十二罐汤、四根弯铁管、一把轮子嘎吱响的办公转椅……我们当初干嘛非要把办公椅拖到大海正中央来？）
  - **RU**: (Двенадцать банок супа, четыре гнутые трубы, одно кресло на колесиках со скрипучим роликом… зачем вообще мы притащили офисное кресло посреди океана?)
- **Choices**:
  - `topic_0` -> `resident_storage_0` (`condition=always`)
    - **EN**: Does the Scavenger really pull all this stuff up from the ocean floor? | **ZH**: 这些东西真全是拾荒者从海底一件件捞上来的？ | **RU**: Старьевщик правда сам вытаскивает все это со дна?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 待会儿见。 | **RU**: Еще увидимся.

##### `resident_storage_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Every bit of it. He spots a rusty box half a mile out, dives down, and shows up ten minutes later asking us to help haul it onto the deck. Half the time it's junk, half the time it's good timber, nails, or dry canisters that keep this whole shoal standing. Just don't let him hear you call the containers rusty: he's ridiculously proud of them.
  - **ZH**: 全是他弄上来的。他在半英里外瞥见个生锈箱子就会扎下去，十分钟后冒出头喊我们帮忙往甲板上拖。 一半时候是破烂，另一半时候是好木料、钉子或者干爽的罐子，整座浅滩能立在这儿全靠它们。可别当着他的面说集装箱生锈了，他对那俩箱子得意得不行。
  - **RU**: Все до единой штуковины. Заметит ржавый ящик за полмили, нырнет, и через десять минут уже зовет нас тащить его на палубу. Половина оказывается хламом, а другая половина — крепкие доски, гвозди и сухие банки, на которых вся наша Отмель и держится. Только при нем не называй контейнеры ржавыми, он ими ужасно гордится.
- **Choices**:
  - `back` -> `resident_storage` (`condition=always`)
    - **EN**: Back. | **ZH**: 聊点别的。 | **RU**: Назад.
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Thanks, catch you later. | **ZH**: 谢啦，回头见。 | **RU**: Спасибо, увидимся позже.

##### `resident_workshop` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: Step carefully: wood shavings on the floor, and I just put a fresh edge on the saw. Need the crafting table or the furnace? Go right ahead, just leave the water cauldron alone; I use it to cool hot iron and rinse salt off salvaged tools.
  - **ZH**: 脚下当心，地板上全是木屑，锯子我也刚磨出新刃。 要用工作台还是熔炉？尽管用，别动那口水锅就行，我拿它给热铁淬火，顺便冲掉捞上来的工具上的盐分。
  - **RU**: Ступай осторожнее, на полу стружка, и я только что наточил пилу. Нужен верстак или печь? Пользуйся на здоровье, только котел с водой не трогай, я в нем остужаю железо и смываю соль с выловленных инструментов.
- **Passage 2** (`channel=mental`, `condition=always`):
  - **EN**: (Wood planks: patched. Windmill blades: tightened. Doorframe next to the dorm: still has a neat swordfish-shaped dent in it. I'm leaving that one as a warning.)
  - **ZH**: （木板：补好了。风车叶片：拧紧了。宿舍旁边的门框：还留着一个规规矩矩的旗鱼吻戳印。这块我就不补了，留着当警示牌。）
  - **RU**: (Доски залатаны. Лопасти ветряка подтянуты. Дверной косяк у спальни: до сих пор с аккуратной вмятиной в форме носа рыбы-меча. Оставлю как предупреждение.)
- **Choices**:
  - `topic_0` -> `resident_workshop_0` (`condition=always`)
    - **EN**: Must take a lot of work keeping a wooden house standing in saltwater. | **ZH**: 让这么一座木屋稳稳立在海水里，平时肯定要费不少功夫吧。 | **RU**: Наверное, непросто держать деревянный дом в соленой воде?
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: See you around. | **ZH**: 待会儿见。 | **RU**: Еще увидимся.

##### `resident_workshop_0` (Resident Station Node)
- **Passage 1** (`channel=spoken`, `condition=always`):
  - **EN**: You have no idea. Salt eats nails, wind rattles the windmill blades, and someone around here keeps forgetting his nose sticks out three feet in front of his face every time he turns around indoors. Still, I'd rather plane pine boards out in the sea breeze than sit in some dusty inland ruin. And the view of the crop planters through the back window isn't bad, either.
  - **ZH**: 你都想象不到。海水锈钉子，海风吹得风车叶片嘎嘎响，而且这屋里还有某位仁兄每次在室内转身，老忘了自己脸前面还伸出去三英尺长的鼻子。 不过话说回来，比起待在内陆满是灰的废墟里，我还是更乐意吹着海风刨木板。况且从后窗还能望见外头的菜畦，风景也不赖。
  - **RU**: Ты даже не представляешь. Соль разъедает гвозди, ветер расшатывает лопасти ветряка, а кое-кто здесь вечно забывает, что у него перед лицом торчит метровый нос, когда разворачивается в комнате. И все-таки я лучше буду строгать сосновые доски на морском бризе, чем сидеть в пыльных руинах на суше. Да и вид на грядки из заднего окна вполне себе ничего.
- **Choices**:
  - `back` -> `resident_workshop` (`condition=always`)
    - **EN**: Back. | **ZH**: 聊点别的。 | **RU**: Назад.
  - `close` -> `root` (`condition=always`, `effect=close`)
    - **EN**: Thanks, catch you later. | **ZH**: 谢啦，回头见。 | **RU**: Спасибо, увидимся позже.

#### Chief Camp-Life Voice (`scavenger.*`)

- **`scavenger.changed`**
  - **EN**: Whoa, wait—that didn't tally up cleanly. Make sure you're holding the right item and the shared chest still has room.
  - **ZH**: 等等，数量对不上啊。 确认手里拿着的东西没错，公共箱子也没塞满。
  - **RU**: Погоди — что-то не сошлось. Проверь, что держишь нужный предмет и в общем сундуке есть место.
- **`scavenger.choose_item`**
  - **EN**: Hold whatever supplies you're sharing in your hand so I can look them over. Keep enough rations dry for your own swim back!
  - **ZH**: 把你打算分享的物资拿在手里让我瞧瞧。 别忘了给自己也留点路上吃的干粮！
  - **RU**: Возьми в руку припасы, которыми готов поделиться, чтобы я прикинул. Оставь себе достаточно еды на обратный путь!
- **`scavenger.danger`**
  - **EN**: Heads up—something hostile drifted too close to the stilts or outer piers. Let's clear the water around the deck before anyone tries to settle in.
  - **ZH**: 留神——有敌对家伙游到木桩或外圈栈桥附近了。 咱们先把甲板四周清理干净再说。
  - **RU**: Осторожнее — кто-то враждебный подплыл слишком близко к сваям и причалу. Надо прогнать незваных гостей от палубы, прежде чем отдыхать.
- **`scavenger.declined`**
  - **EN**: All good, pal. Keep what you need for the open water—we've got nets drying if supplies run low.
  - **ZH**: 没事，伙计。 留着自己远航用吧，我们自己也下网捕鱼呢。
  - **RU**: Всё в порядке, дружище. Прибереги на плавание — у нас и свои сети есть.
- **`scavenger.donate`**
  - **EN**: You hauled %s all the way out to The Shoal? Crew's gonna put that straight to work. How much are you leaving?
  - **ZH**: 你把 %s 一路运到浅滩来了？ 船员们肯定用得上。这次打算放多少？
  - **RU**: Ты притащил %s прямо на Отмель? Команда сразу пустит это в дело. Сколько оставишь?
- **`scavenger.food_enough`**
  - **EN**: Between the campfire pot, the planters, and the shared chest on deck, nobody's going hungry right now.
  - **ZH**: 火堆上的汤锅、甲板菜槽加上公共箱子里的存货，大家这会儿吃喝不愁。
  - **RU**: С котелком у костра, грядками и общим сундуком на палубе никто сейчас не голодает.
- **`scavenger.food_low`**
  - **EN**: The shared chest out by the storage deck is getting pretty thin. Swimming and hauling scrap burns plenty of energy, so extra fish, kelp, or trail food would help the crew.
  - **ZH**: 储物甲板旁边的公共补给箱快见底了。 天天潜水和拖废铁很费体力，添点鱼、海带或者干粮会帮大忙。
  - **RU**: Общий сундук у грузовой палубы заметно опустел. Плавание и таскание лома отнимают кучу сил, так что лишняя рыба, ламинария или пайки выручат команду.
- **`scavenger.food_plenty`**
  - **EN**: Shared chest is packed solid, and there's fresh fish bubbling by the campfire! Pull up a log seat whenever you want to grab a hot bite.
  - **ZH**: 公共箱子塞得满满当当，篝火旁也正煮着鲜鱼！ 想吃热乎的随时挑个木墩坐下。
  - **RU**: Общий сундук забит доверху, а у костра булькает свежая рыба! Присаживайся на чурбак у огня, когда захочешь перекусить.
- **`scavenger.greeting`**
  - **EN**: Deck planks are holding steady today! Need a hand with crew supplies, or just checking how the shoal is running?
  - **ZH**: 今天的甲板木板稳得很！ 要看看船员物资情况，还是打听浅滩上的事务？
  - **RU**: Настил сегодня держится отлично! Хочешь проверить запасы команды или узнать, как дела на Отмели?
- **`scavenger.handled`**
  - **EN**: Looks like someone already hauled those onto the deck! Check the other notes if you still want an errand.
  - **ZH**: 好像已经有人把那批货搬上甲板啦！ 还想搭把手的话看看别的条目吧。
  - **RU**: Похоже, кто-то уже занес это на палубу! Загляни в остальные заметки, если хочешь ещё помочь.
- **`scavenger.meal`**
  - **EN**: Pot's hot and dinner's on! Grab a log stool near the awning; everybody off duty will swim on up directly.
  - **ZH**: 汤锅烧热了，开饭喽！ 去白篷旁找个木墩坐，没在忙的伙计马上都过来。
  - **RU**: Котелок кипит — ужин готов! Садись на чурбак под навесом, кто свободен — сейчас подойдут.
- **`scavenger.no_requests`**
  - **EN**: Crates are tidy and the campfire pot's covered for now. Kick back by the awning or take a dip off the fishing pier!
  - **ZH**: 木箱都收得整整齐齐，篝火上的汤锅这会儿也够用。 去白篷底下吹吹海风，或者去钓鱼区泡泡水吧！
  - **RU**: Ящики в порядке, а в котелке сейчас всего хватает. Отдохни под навесом или искупайся у рыболовного края!
- **`scavenger.outfit`**
  - **EN**: You've hauled your share and more for this crew, pal. Take this diving suit—same blue trim my old partner gave me. Cuts through the tide like a charm.
  - **ZH**: 伙计，你在咱们船上出的力气已经远远够格了。 收下这身潜水服吧——和我当年搭档送我那件一样的蓝条纹款，下海顺手得很。
  - **RU**: Ты здорово помог нашей команде, дружище. Возьми этот водолазный костюм с синей полосой — такой же, как оставил мне старый напарник.
- **`scavenger.remember`**
  - **EN**: The crew doesn't forget who swims supplies out over open water for us. You always have a dry seat under the awning—empty-handed or not.
  - **ZH**: 船员们可都记得是谁冒着风浪把补给送到浅滩来的。 哪怕两手空空来串门，白篷底下也永远给你留着干爽位子。
  - **RU**: Команда не забывает тех, кто приносит нам припасы посреди океана. Под навесом для тебя всегда найдется сухое место — с грузом или без.
- **`scavenger.request`**
  - **EN**: Could use %s %s for the shoal stores. Drop them with me or into the shared chest whenever you sail back.
  - **ZH**: 浅滩公用补给这边缺 %s 份 %s。 等你回来顺手交给我就行。
  - **RU**: Для нужд Отмели нам не помешает %s (%s). Передай мне или закинь в общий сундук, как вернёшься.
- **`scavenger.requests`**
  - **EN**: Crew left a quick tally of what the shoal needs right now. Pick whatever suits your next trip, pal.
  - **ZH**: 船员们刚在清单上记了几项浅滩眼下缺的物资。 挑顺手的帮忙就好，伙计。
  - **RU**: Ребята оставили пару заметок, чего не хватает Отмели. Бери то, что по пути, дружище.
- **`scavenger.reverse_rite`**
  - **EN**: Hold steady a breath. Swimming like a swordfish sure beats soggy boots, though keeping a human face handy for shore runs makes sense too. There—back to your own skin.
  - **ZH**: 先稳住呼吸。当旗鱼在海里窜确实痛快，不过上岸办事偶尔换回人脸也挺方便。好了——变回你原来的样子啦。
  - **RU**: Постой спокойно секунду. Плавать как рыба-меч здорово, но иногда на берегу удобнее в человеческом виде. Готово — ты снова в своём облике.
- **`scavenger.safe`**
  - **EN**: Water around the stilts is clear, and nothing hostile's hanging around the piers.
  - **ZH**: 木桩周围的水面干干净净，栈桥边上没有任何捣乱的家伙。
  - **RU**: Вода вокруг свай спокойная, и у причала никого враждебного нет.
- **`scavenger.thanks`**
  - **EN**: Beauty—thanks, champ! I'll stow these in the shared chest before the gulls spot them. Help yourself by the fire anytime.
  - **ZH**: 漂亮——谢啦，伙计！ 我这就趁海鸥没叼走前塞进公共箱子。随时来火堆边吃热的。
  - **RU**: Красота — спасибо, дружище! Уберу в общий сундук, пока чайки не растащили. Угощайся у костра в любое время.
- **`scavenger.transfer`**
  - **EN**: Right now your berth is registered with %s. Want to haul your gear out here and join The Shoal crew instead? Old friends won't mind a bit.
  - **ZH**: 你现在的住所还登记在%s那边。 想把行李搬到海上加入我们浅滩小队吗？老朋友们绝不会怪你的。
  - **RU**: Сейчас ты числишься в «%s». Хочешь перебраться к нам на Отмель? Прежние друзья поймут и не обидятся.
- **`scavenger.watch`**
  - **EN**: Something hostile keeps circling the shoal stilts and piers. Chase it clear of the deck and let me know once the water's calm.
  - **ZH**: 有个难缠的家伙一直在浅滩木桩和栈桥外圈转悠。 把它赶出甲板周围再告诉我。
  - **RU**: Кто-то враждебный кружит у свай и причала Отмели. Прогони его от настила и скажи, как вода очистится.
- **`scavenger.watch_thanks`**
  - **EN**: Deck perimeter's all clear again! Thanks for keeping trouble off our stilts. Grab a warm bowl from the pot—you earned it.
  - **ZH**: 甲板四周又恢复清净啦！ 多亏你帮大伙把麻烦赶走，去锅里盛一碗热的吧，应得的。
  - **RU**: Периметр палубы снова чист! Спасибо, что отогнал угрозу от свай. Бери миску горячего из котелка — заслужил.
- **`scavenger.watch_wait`**
  - **EN**: Keep an eye over the railing a second longer. Once nothing's bumping our stilts anymore, we can kick back properly.
  - **ZH**: 再盯一眼栏杆外面的海面吧。 等确定水下再没东西撞木桩了再歇。
  - **RU**: Присмотри за водой за перилами еще минутку. Как только никто не будет тревожить сваи, тогда и передохнём.

#### Resident Camp-Life Voice (`shoal_resident.*`)

- **`shoal_resident.away`**
  - **EN**: Deck logs and supply crates can wait until we're back out at The Shoal. Right now I'm just glad to have a swimming partner along.
  - **ZH**: 甲板记录和补给箱的事等回浅滩再理吧。 这会儿有人一块儿游水作伴就挺好。
  - **RU**: Учёт грузов и ящики подождут, пока мы не вернёмся на Отмель. Сейчас просто приятно плыть в хорошей компании.
- **`shoal_resident.cautious_greeting`**
  - **EN**: Oh—hello! Didn't hear footsteps over the tide. Is the open sea quiet today, or did something follow you out here?
  - **ZH**: 噢——你好！海浪声大，没听见脚步声。 今天海面太平吗，还是外头有什么东西跟过来了？
  - **RU**: О — привет! Из-за прибоя не услышал шагов. На море сегодня тихо, или за тобой кто-то увязался?
- **`shoal_resident.cautious_request`**
  - **EN**: If it isn't out of your way… could you spare %s %s? Please watch the swells on your way back.
  - **ZH**: 如果不麻烦的话……能匀我 %s 份 %s 吗？ 回程海浪大的话一定当心。
  - **RU**: Если не по дороге в шторм… не поделишься %s (%s)? Осторожнее на волнах по пути обратно.
- **`shoal_resident.cautious_thanks`**
  - **EN**: Thank you kindly. Knowing the water around the stilts is clear makes it much easier to sleep.
  - **ZH**: 实在太谢谢了。 知道木桩周围风平浪静，晚上睡觉都踏实多啦。
  - **RU**: Большое тебе спасибо. Когда знаешь, что под сваями спокойно, и спится куда легче.
- **`shoal_resident.changed`**
  - **EN**: Wait—that didn't quite line up. Make sure you're holding the right item and our shared chest isn't full.
  - **ZH**: 等一下——好像哪里对不上。 看看手里的东西对不对，公共箱子满没满。
  - **RU**: Погоди — что-то не сходится. Проверь, что держишь нужную вещь и в сундуке есть место.
- **`shoal_resident.choose_item`**
  - **EN**: Hold whatever supplies you're sharing in your hand so I can see. Keep enough food for your own trip back to shore.
  - **ZH**: 把你想给的东西拿在手里让我看看。 自己也留够回岸上的吃食呀。
  - **RU**: Возьми припасы в руку, чтобы я видел. Оставь себе достаточно еды на дорогу к берегу.
- **`shoal_resident.danger`**
  - **EN**: Careful—something hostile has been splashing near the outer piers. Could you clear the deck edge before anyone goes below?
  - **ZH**: 小心——有敌对家伙在外圈栈桥附近拍水。 能帮忙先清理掉外圈再说吗？
  - **RU**: Осторожнее — кто-то враждебный кружит у крайнего причала. Поможешь прогнать его от палубы?
- **`shoal_resident.declined`**
  - **EN**: Fair enough. Hang onto your supplies out here on the water.
  - **ZH**: 行吧。 在海上补给确实得省着用。
  - **RU**: Тоже верно. На воде припасы лучше приберечь.
- **`shoal_resident.donate`**
  - **EN**: Brought %s out from the coast? That'll go straight into the shared storage chest. How much are you adding?
  - **ZH**: 从岸边带了 %s 过来？ 这就放进公共补给箱里。打算放多少？
  - **RU**: Принёс %s с берега? Пойдёт прямо в общий сундук. Сколько добавишь?
- **`shoal_resident.food_enough`**
  - **EN**: The campfire pot's simmering and the shared chest has enough food for now. Help yourself if you're crew here.
  - **ZH**: 篝火上的锅一直热着，公共箱子里的吃食现在够分。 既是一伙的就别客气。
  - **RU**: Котелок у костра кипит, а в общем сундуке еды пока достаточно. Угощайся, если живёшь с нами.
- **`shoal_resident.food_low`**
  - **EN**: The shared chest out on the storage deck is looking a bit light. Extra fish, dried kelp, or oranges from shore would stretch our meals cleanly.
  - **ZH**: 储物甲板那边的公共箱子有点见底了。 多带些鱼、海带或者岸边的橙子过来，够大家顶一阵了。
  - **RU**: Общий сундук на грузовой палубе заметно опустел. Лишняя рыба, ламинария или апельсины с берега очень бы выручили.
- **`shoal_resident.food_plenty`**
  - **EN**: Our storage chest is stocked full of fish and dry greens right now! Would be great to call everyone over to the campfire stools.
  - **ZH**: 储物箱现在塞满了鱼和干菜！ 真适合把大伙儿喊到篝火木墩旁一块儿开饭。
  - **RU**: Наш общий сундук сейчас забит рыбой и зеленью! Самое время позвать всех на обед к костру.
- **`shoal_resident.gesture`**
  - **EN**: The aquatic resident softly clicks and motions toward the Scavenger by the campfire under the white awning—their speech is still hard to follow.
  - **ZH**: 水边的同伴轻哼一声，朝白篷篝火旁的拾荒者指了指，你一时还听不懂他的话。
  - **RU**: Водный житель тихо щёлкает и кивает в сторону Старьевщика у костра под навесом — речь пока не разобрать.
- **`shoal_resident.greeting`**
  - **EN**: Oh—hey there. Checking on the shoal stores, or looking for something to help with around the deck?
  - **ZH**: 噢——嗨。 来看看浅滩的补给，还是找点甲板上的活帮忙？
  - **RU**: О — привет. Проверяешь запасы Отмели или ищешь, чем помочь на палубе?
- **`shoal_resident.handled`**
  - **EN**: Oh—somebody already handed those over! Thanks for checking in anyway.
  - **ZH**: 噢——已经有人先拿给我啦！ 还是谢谢你惦记着。
  - **RU**: О — кто-то уже успел мне это передать! Но всё равно спасибо за заботу.
- **`shoal_resident.meal`**
  - **EN**: I'll call out to the folks around the deck and dorm. Wait by the campfire stools—anyone not sleeping will wander over.
  - **ZH**: 我去甲板和宿舍那头喊一声大伙儿。 在篝火木墩那儿等会儿，没睡觉的伙计马上都过来。
  - **RU**: Покличу остальных с палубы и из спальни. Подожди у чурбаков у костра — кто не спит, сейчас придёт.
- **`shoal_resident.no_requests`**
  - **EN**: I've got everything I need right beside my post for now. Rest by the awning or watch the sea from the dorm windows whenever you like.
  - **ZH**: 我手头暂时什么都不缺啦。 随时去白篷下乘凉或者去宿舍窗边看海吧。
  - **RU**: У меня сейчас всё есть прямо под рукой. Отдохни под навесом или погляди на море из окна спальни.
- **`shoal_resident.playful_greeting`**
  - **EN**: Hey! Don't track salt water past the doorframes—the workshop shark is counting scuffs! Come watch the waves hit the stilts with us!
  - **ZH**: 嗨！进门前脚下抖抖水——工坊鲨鱼正数着门框上的刮痕呢！ 快来一块儿看海浪拍木桩！
  - **RU**: Эй! Не набрызгай солёной водой на косяк — акула из мастерской считает каждую царапину! Пошли смотреть, как волны бьют о сваи!
- **`shoal_resident.playful_request`**
  - **EN**: Psst—could you smuggle %s %s over here? Just don't tell the Scavenger if it looks anything like dried snacks!
  - **ZH**: 嘘——能悄悄递给我 %s 份 %s 吗？ 要是像小零食可别让拾荒者看见啦！
  - **RU**: Тсс — не притащишь сюда %s (%s)? Только не говори Старьевщику, если это похоже на вкусняшку!
- **`shoal_resident.playful_thanks`**
  - **EN**: Ha! Legend! Next time the boss hauls up another chair with squeaky wheels, you get first dibs!
  - **ZH**: 哈！够意思！ 下回老大再从海底捞出吱嘎响的转椅，第一个给你坐！
  - **RU**: Ха! Выручил! В следующий раз, когда босс вытащит со дна ещё одно скрипучее кресло, оно твоё!
- **`shoal_resident.polite_greeting`**
  - **EN**: Good day! Mind the wet boards near the stair edges. Let me know if there's anything on deck I can help check for you.
  - **ZH**: 日安！楼梯边上的木板有些湿滑，当心脚下。 甲板上有什么要查的随时跟我说。
  - **RU**: Добрый день! Осторожнее на влажных досках у края настила. Скажи, если нужно что-то проверить на палубе.
- **`shoal_resident.polite_request`**
  - **EN**: Whenever your travels pass near The Shoal, might I ask for %s %s? Please hand them over whenever convenient; there is no rush at all.
  - **ZH**: 如果您下次航程顺路，不知方不方便帮我带 %s 份 %s？ 等方便时再交给我即可，一点不急。
  - **RU**: Когда твой путь пройдёт мимо Отмели, можно попросить %s (%s)? Передай, когда будет удобно; никакой спешки.
- **`shoal_resident.polite_thanks`**
  - **EN**: My sincere thanks for carrying those out over the water. Do warm up beside the campfire before heading back out to sea.
  - **ZH**: 衷心感谢您跨过海面把东西送到这里。 出海前一定先在篝火边烤暖身子。
  - **RU**: Искренне благодарю за то, что доставил это через воду. Обязательно согрейся у костра перед отплытием.
- **`shoal_resident.remember`**
  - **EN**: Still remember you hauling supplies out over the water for our crew. Glad to share the deck breeze with you anytime.
  - **ZH**: 我还记得你大老远涉水把补给送到我们船上呢。 随时欢迎来甲板上一起吹海风。
  - **RU**: Я до сих пор помню, как ты приносил припасы нашей команде. Всегда рад разделить с тобой морской бриз.
- **`shoal_resident.request`**
  - **EN**: Could you spare %s %s for my corner? Hand them straight to me whenever you swim past—no hurry.
  - **ZH**: 能分我 %s 份 %s 吗？ 顺路直接递给我就行，不急。
  - **RU**: Не поделишься %s (%s) для моего поста? Передай прямо мне, как проплывёшь мимо, без спешки.
- **`shoal_resident.requests`**
  - **EN**: I wouldn't mind tucking a little extra snack away for my station. Mind tossing a bit my way next time you're by the deck?
  - **ZH**: 我想在自己守的角落存点小零嘴，省得老去翻大箱子。 你下回顺路的话能捎一点给我吗？
  - **RU**: Я бы не отказался припрятать немного еды у себя на посту. Не захватишь немного в следующий раз?
- **`shoal_resident.reverse_rite`**
  - **EN**: Only the Scavenger keeps track of borrowed forms—talk to him over by the campfire under the white awning.
  - **ZH**: 只有拾荒者管得了借用的形态——去白篷篝火旁找他聊聊吧。
  - **RU**: Только Старьевщик заведует временными обликами — поговори с ним у костра под белым навесом.
- **`shoal_resident.safe`**
  - **EN**: Waves under the stilts are calm and nothing hostile's prowling near the piers. Good day for basking on dry planks.
  - **ZH**: 木桩下的海浪稳稳当当，栈桥外头也没有怪东西乱晃。 正适合在干木板上晒太阳。
  - **RU**: Волны под сваями тихие, и вокруг причалов спокойно. Отличный день погреться на досках.
- **`shoal_resident.seekchief`**
  - **EN**: (The Scavenger by the campfire handles everything for The Shoal crew. Once you have a translator, telepathy, or a latex form, the rest of us can chat clearly too.)
  - **ZH**: （篝火旁的拾荒者负责打理浅滩小队的一切事务。 等你有翻译器、心灵感应或者变成胶兽以后，就能和大家顺畅聊了。）
  - **RU**: (Старьевщик у костра ведает всеми делами команды Отмели. Когда появится переводчик, телепатия или латексная форма, остальные тоже смогут свободно общаться.)
- **`shoal_resident.thanks`**
  - **EN**: Thanks a bunch! Catch your breath under the white awning whenever the sun's high.
  - **ZH**: 太感谢啦！ 太阳毒的时候记得去白篷底下歇歇。
  - **RU**: Большое спасибо! Отдохни под белым навесом, когда солнце припекает.
- **`shoal_resident.transfer`**
  - **EN**: Your bunk is still listed back with %s right now. If you want to join our crew over the waves, talk to the Scavenger by the campfire.
  - **ZH**: 你现在还算%s那边的人呢。 要想换来我们浅滩上过日子，去找篝火旁的拾荒者说说吧。
  - **RU**: Твоя койка всё ещё числится за «%s». Если хочешь перейти к нам на Отмель, поговори со Старьевщиком у костра.
- **`shoal_resident.watch`**
  - **EN**: Something hostile keeps circling the stilts beneath the house. Mind chasing it out past the piers so it leaves the deck quiet?
  - **ZH**: 有个敌对家伙老在房子底下的木桩附近绕圈。 能麻烦你把它赶到栈桥外头去吗？
  - **RU**: Кто-то враждебный всё кружит под нашими сваями. Прогони его подальше от причала?
- **`shoal_resident.watch_thanks`**
  - **EN**: So the trouble outside the piers is gone? Whew—the whole shoal can breathe easy again.
  - **ZH**: 外圈栈桥那边的麻烦解决了？ 呼——整座浅滩总算能安心吹风了。
  - **RU**: Значит, у причала снова спокойно? Уф — вся Отмель теперь может вздохнуть спокойно.
- **`shoal_resident.watch_wait`**
  - **EN**: Let's make sure nothing's circling beneath the stilts first. Nobody wants a brawl rocking the planks.
  - **ZH**: 还是先确认房子底下的柱子旁没东西绕圈吧。 谁都不想木板被打得直晃悠。
  - **RU**: Давай сначала убедимся, что под сваями никто не кружит. Никому не нужна драка на шатких досках.

#### Narrative Fragments (English Only)

- **`changed:note`** @ `(6, 6, 10)` — Dormitory private chest note
  ```text
  DORM LOCKERS — HANDS OFF
  
  Personal gear and bedside snacks stay with the bunk owners.
  Shared food goes in the double chest out on the storage deck.
  
  (And yes, boss, that includes your emergency oranges — quit leaving peels under the pillows.)
  — Eel
  ```
- **`changed:note`** @ `(11, 6, 31)` — Unlocks `clue:shoal_manifest` (note) and `clue:shoal_disc` (compact disc in invisible item frame)
  ```text
  SALVAGE TALLY: EAST DECK
  
  - 1 orange shipping container (mostly wet cardboard, dry walls—keeper)
  - 1 blue shipping container (good dry storage once we patched the latch)
  - 1 white office chair (wheel squeaks, boss insisted we keep it)
  - 5 dented doorway slats replaced this month (boss says it was only 3)
  
  NOTE TO SELF: Still zero oranges on the shoal. Remind whoever goes to shore next.
  ```
- **`item_frame(changed:compact_disc)`** @ `(11, 6, 31)` — Unlocks `clue:shoal_manifest` (note) and `clue:shoal_disc` (compact disc in invisible item frame)
  ```text
  From: diving_partner@reef-salvage.net
  To: scavenger@the-shoal.net
  Subject: Left something in your locker / Take care out there
  Date: October 14
  
  Hey,
  
  I folded and left the spare charcoal-and-blue suit in your locker before my transfer out today. Yes, it's the one with the blue leg stripe you kept insisting makes you look faster in the water.
  
  Please quit diving solo past the drop-off whenever the swell picks up, alright? Even if you spot a whole shipping container full of gold down there, wrestling the tide alone just isn't worth it.
  
  Keep your head above water, old friend. Next time we get shore leave, the first crate of oranges is on me.
  
  Best,
  Your Dive Partner
  ```

