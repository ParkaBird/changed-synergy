# -*- coding: utf-8 -*-
import json

json_path = "src/main/resources/assets/changed_synergy/lang/zh_cn.json"

with open(json_path, "r", encoding="utf-8-sig") as f:
    data = json.load(f)

tone_updates = {
    # ==================== WHITE LATEX ====================
    # Human (Soft & Guiding)
    "dialogue.changed_synergy.white.spotted_human.0": "（伸出双手）找到你了。别怕，脚步慢一点……我们只是想靠近你。",
    "dialogue.changed_synergy.white.spotted_human.1": "那边的人类，不要慌张哦。没人想要伤害你，过来我们这边吧。",
    "dialogue.changed_synergy.white.spotted_human.2": "别急着逃跑呀，放平呼吸……跟着我们的节奏，很快就会变舒服了。",
    "dialogue.changed_synergy.white.alert_human.0": "大家从两边轻轻包过去，给人家留点适应的空间。",
    "dialogue.changed_synergy.white.alert_human.1": "别突然扑上去哦，会把小人类吓到的。",
    "dialogue.changed_synergy.white.alert_human.2": "我走这边，你们去前面温和地接住他。",
    "dialogue.changed_synergy.white.heard_human.0": "听到了吗？有小人类在附近呢，声音好轻。",
    "dialogue.changed_synergy.white.heard_human.1": "脚步骤停了。大家放轻动作，我们过去引导他。",
    "dialogue.changed_synergy.white.heard_human.2": "那边有人类的动静，慢慢走过去，别惊跑了他。",
    "dialogue.changed_synergy.white.reacquired_human.0": "原来躲在这里呀。好啦，不用再受惊害怕了。",
    "dialogue.changed_synergy.white.reacquired_human.1": "又见面了。过来吧，我们不会催促你的。",
    "dialogue.changed_synergy.white.reacquired_human.2": "找到你了。这回放平心态，慢慢走过来好吗？",
    "dialogue.changed_synergy.white.spotted_human_home.0": "人类迷路到蜂巢里来了呢。别怕，慢慢停下来，这里很安全。",
    "dialogue.changed_synergy.white.spotted_human_home.1": "到处都是我们哦，越是仓皇奔跑，大家就越想温柔地抱住你呢。",
    "dialogue.changed_synergy.white.spotted_human_home.2": "到了蜂巢门口就不用再跑了，来打个招呼吧。",
    "dialogue.changed_synergy.white.spotted_human_away.0": "在野外也能碰到人类呀。等等我们，不要一下子跑没影了。",
    "dialogue.changed_synergy.white.spotted_human_away.1": "你迷路了吗？先停下，我们可以温柔地带你回家。",
    "dialogue.changed_synergy.white.spotted_human_away.2": "这里不是巢穴，没人会围着你，放轻松聊聊好吗？",

    # Transfurred (Non-hostile Curiosity)
    "dialogue.changed_synergy.white.spotted_transfurred.0": "新的面孔呢！先别急着走，让我们好奇地看看你~",
    "dialogue.changed_synergy.white.spotted_transfurred.1": "是一位兽化者同伴呀。大家别围得太紧，先礼貌地问问它的来意吧。",
    "dialogue.changed_synergy.white.spotted_transfurred.2": "这副形态以前没见过呢！愿意停下来和我们聊聊吗？",
    "dialogue.changed_synergy.white.alert_transfurred.0": "保持礼貌的站位，不要把路过的兽化者逼进死角哦。",
    "dialogue.changed_synergy.white.alert_transfurred.1": "异族伙伴换方向了，前面的同胞去招呼一下。",
    "dialogue.changed_synergy.white.alert_transfurred.2": "先看清是哪一类的伙伴，大家别挤成一团。",
    "dialogue.changed_synergy.white.heard_transfurred.0": "脚步很稳呢，附近有一位陌生的兽化者路过。",
    "dialogue.changed_synergy.white.heard_transfurred.1": "听见了，是很特别的步子呢，就在那边。",
    "dialogue.changed_synergy.white.heard_transfurred.2": "陌生的声音。大家一起过去看看这位新客人吧。",
    "dialogue.changed_synergy.white.reacquired_transfurred.0": "又看到你啦！你的形态在远处看也很抢眼呢。",
    "dialogue.changed_synergy.white.reacquired_transfurred.1": "又碰面了！别紧张，我们只是想认识你。",
    "dialogue.changed_synergy.white.reacquired_transfurred.2": "原来在这里呀！看来你也挺想和我们聊聊的。",
    "dialogue.changed_synergy.white.spotted_transfurred_home.0": "有陌生的兽化者造访巢穴了。大家保持距离，友善地看看是哪一类吧。",
    "dialogue.changed_synergy.white.spotted_transfurred_home.1": "在我们的领地遇到新面孔啦！欢迎，说说你从哪儿来的呀？",
    "dialogue.changed_synergy.white.spotted_transfurred_home.2": "你可以随时在这里休息，不过先让我们认识一下你吧。",
    "dialogue.changed_synergy.white.spotted_transfurred_away.0": "在外面碰见兽化者了！别紧张哦，我们就想认识你一下。",
    "dialogue.changed_synergy.white.spotted_transfurred_away.1": "只有我在这儿呢，不用拘谨，你是哪一类的伙伴呀？",
    "dialogue.changed_synergy.white.spotted_transfurred_away.2": "好特别的形态！要不要顺路一起走一段？",

    # ==================== DARK LATEX ====================
    # Human (Soft & Guiding)
    "dialogue.changed_synergy.dark.spotted_human.0": "嗨，前面的小人类，跑那么急容易摔着。慢点，哥几个又不会吃了你。",
    "dialogue.changed_synergy.dark.spotted_human.1": "瞧你这慌张样。停下歇会吧，跟我们走可比在野外受冻强多了。",
    "dialogue.changed_synergy.dark.spotted_human.2": "跑得挺卖力嘛。别折腾了，放轻松，哥几个会下手轻点带你入队。",
    "dialogue.changed_synergy.dark.alert_human.0": "你去左边，我走右边，别把人家吓着了，温和点抄过去。",
    "dialogue.changed_synergy.dark.alert_human.1": "前面站稳，后面跟上，咱们安全地把人带回队里。",
    "dialogue.changed_synergy.dark.alert_human.2": "散开跟上。谁先碰到，动作轻点，按住就行。",
    "dialogue.changed_synergy.dark.heard_human.0": "安静，听见小人类那惊慌的脚步声了。",
    "dialogue.changed_synergy.dark.heard_human.1": "声音在前面。两个人跟我过去，说话态度好点。",
    "dialogue.changed_synergy.dark.heard_human.2": "有人类躲在附近呢。别出声，咱们过去打个招呼。",
    "dialogue.changed_synergy.dark.reacquired_human.0": "小人类在这儿呢。封住两边，别再让他到处乱跑受伤了。",
    "dialogue.changed_synergy.dark.reacquired_human.1": "又见面了。刚才那下躲得不错，不过跟哥几个走更好。",
    "dialogue.changed_synergy.dark.reacquired_human.2": "找到你了。跟紧点，放轻松，没人会害你。",
    "dialogue.changed_synergy.dark.spotted_human_home.0": "人类跑进咱们暗区领地了？别紧张，停下脚步，咱们好好聊聊。",
    "dialogue.changed_synergy.dark.spotted_human_home.1": "看见了。出口有兄弟守着，别跑啦，跟我们入队吧。",
    "dialogue.changed_synergy.dark.spotted_human_home.2": "跑进这里算你选对地方了，哥几个挺随和的，停下吧。",
    "dialogue.changed_synergy.dark.spotted_human_away.0": "外面发现人类。跟紧点，别太突兀，慢慢引导他。",
    "dialogue.changed_synergy.dark.spotted_human_away.1": "这地方不熟。咱们一左一右慢慢贴过去，别吓着人家。",
    "dialogue.changed_synergy.dark.spotted_human_away.2": "跑得不错。放轻松，跟哥几个走，野外太危险了。",

    # Transfurred (Non-hostile Curiosity)
    "dialogue.changed_synergy.dark.spotted_transfurred.0": "哟，新鲜面孔啊！哪条街过来的？过来打个招呼呗。",
    "dialogue.changed_synergy.dark.spotted_transfurred.1": "碰见个路过的兽化者。看着挺友善，大家保持距离，别吓着人家。",
    "dialogue.changed_synergy.dark.spotted_transfurred.2": "这身形态挺硬朗啊！哥们，要是没急事，停下聊两句？",
    "dialogue.changed_synergy.dark.alert_transfurred.0": "路过的同道能还手。两侧拉开，别给人一种要挑事的错觉。",
    "dialogue.changed_synergy.dark.alert_transfurred.1": "守住路口。看清是哪边的朋友再上前打招呼。",
    "dialogue.changed_synergy.dark.alert_transfurred.2": "换个站位，别把人家路过的客人给堵死在窄道里。",
    "dialogue.changed_synergy.dark.heard_transfurred.0": "步子沉稳，是个有意思的异族兽化者。",
    "dialogue.changed_synergy.dark.heard_transfurred.1": "前面有动静。两人一组过去客客气气问问。",
    "dialogue.changed_synergy.dark.heard_transfurred.2": "不是人类的脚步。大家留意侧面，好奇去看看哪路朋友。",
    "dialogue.changed_synergy.dark.reacquired_transfurred.0": "又瞅见你了！别走那么快嘛，哥几个没敌意。",
    "dialogue.changed_synergy.dark.reacquired_transfurred.1": "又见面了！你这身手确实挺利落，认识一下呗？",
    "dialogue.changed_synergy.dark.reacquired_transfurred.2": "在这儿呢！来都来了，多唠两句再走也不迟。",
    "dialogue.changed_synergy.dark.spotted_transfurred_home.0": "有外族兽化者进咱们领地了？别挑事，客客气气问问来意。",
    "dialogue.changed_synergy.dark.spotted_transfurred_home.1": "没见过这副形态。队伍散开点，别给客人心理压力。",
    "dialogue.changed_synergy.dark.spotted_transfurred_home.2": "站那儿歇会就行。咱们暗区随和得很，随便逛。",
    "dialogue.changed_synergy.dark.spotted_transfurred_away.0": "前面有个兽化者。看着挺随和，过去交个朋友。",
    "dialogue.changed_synergy.dark.spotted_transfurred_away.1": "陌生面孔。野外不归谁，碰上就是缘分，唠两句？",
    "dialogue.changed_synergy.dark.spotted_transfurred_away.2": "路上碰上了。你走你的，要是顺路就一起搭个伙？",

    # ==================== ORGANIC LATEX ====================
    # Human (Soft & Guiding)
    "dialogue.changed_synergy.organic.spotted_human.0": "（耸了耸鼻子）闻到你了……别害怕，呼吸放平，我的手很温和。",
    "dialogue.changed_synergy.organic.spotted_human.1": "前面的人类，停下来吧。只要你不再奔跑，我就不会弄伤你。",
    "dialogue.changed_synergy.organic.spotted_human.2": "心跳声好快……别慌张，靠近我，让变化自然融入你吧。",
    "dialogue.changed_synergy.organic.alert_human.0": "绕到下风口，别惊扰到脆弱的人类。",
    "dialogue.changed_synergy.organic.alert_human.1": "看住出口。我去温柔地让他停下来。",
    "dialogue.changed_synergy.organic.alert_human.2": "别急着扑，等他的心跳慢下来再靠近。",
    "dialogue.changed_synergy.organic.heard_human.0": "听见了……人类微弱而温热的呼吸声，就在附近。",
    "dialogue.changed_synergy.organic.heard_human.1": "脚步在那边。放慢呼吸，轻柔地靠近他。",
    "dialogue.changed_synergy.organic.heard_human.2": "附近有受惊的人类。安静，听他往哪边走，我们去接他。",
    "dialogue.changed_synergy.organic.reacquired_human.0": "又闻到你的气息了。慢下来，我已经追上你了，别怕。",
    "dialogue.changed_synergy.organic.reacquired_human.1": "在这里。你的心跳声藏不住的，让我来抚平它。",
    "dialogue.changed_synergy.organic.reacquired_human.2": "找到你了。别再逼自己劳累奔跑了，休息吧。",

    # Transfurred (Non-hostile Curiosity)
    "dialogue.changed_synergy.organic.spotted_transfurred.0": "陌生的气味……闻起来很平静。你是什么种类？",
    "dialogue.changed_synergy.organic.spotted_transfurred.1": "这股气息不是人类，也没有杀气。停在原地，让我好奇地闻闻你。",
    "dialogue.changed_synergy.organic.spotted_transfurred.2": "没见过的兽化者。你的呼吸很稳，要不过来打个招呼？",
    "dialogue.changed_synergy.organic.alert_transfurred.0": "异族伙伴的身体很灵活，保持友好距离，别冒犯到它。",
    "dialogue.changed_synergy.organic.alert_transfurred.1": "绕开正面防备，从侧面温和地接近交流。",
    "dialogue.changed_synergy.organic.alert_transfurred.2": "先细细辨清气味，确认没有敌意后再靠近。",
    "dialogue.changed_synergy.organic.heard_transfurred.0": "不是人类的脚步。一位平静的兽化者就在附近。",
    "dialogue.changed_synergy.organic.heard_transfurred.1": "呼吸很平稳，这位客人还没有走远。",
    "dialogue.changed_synergy.organic.heard_transfurred.2": "陌生的自然气味在移动。跟上看看，但别靠太近。",
    "dialogue.changed_synergy.organic.reacquired_transfurred.0": "又闻到了！你的气息非常清爽，很好认。",
    "dialogue.changed_synergy.organic.reacquired_transfurred.1": "又找到你了。好奇你想去哪里呢？",
    "dialogue.changed_synergy.organic.reacquired_transfurred.2": "就在那里。你独特的形态真让人好奇。",

    # ==================== AQUATIC LATEX ====================
    # Human (Soft & Guiding)
    "dialogue.changed_synergy.aquatic.spotted_human.0": "岸边有小人类呢！慢点走呀，水边滑，别摔进深水里啦。",
    "dialogue.changed_synergy.aquatic.spotted_human.1": "看见你啦！往浅水区走走，我来温柔地接你下水呀。",
    "dialogue.changed_synergy.aquatic.spotted_human.2": "别急着往陆地上跑嘛，水里多凉快呀，我们只是想带你一起玩。",
    "dialogue.changed_synergy.aquatic.alert_human.0": "守住浅水区，我从另一边轻柔地游过去引他。",
    "dialogue.changed_synergy.aquatic.alert_human.1": "两边包过去，注意别把小人类往深水里赶哦。",
    "dialogue.changed_synergy.aquatic.alert_human.2": "他往岸上跑啦，前面的同伴去温柔地接住他。",
    "dialogue.changed_synergy.aquatic.heard_human.0": "有人类的脚步踩到水花啦，就在附近！",
    "dialogue.changed_synergy.aquatic.heard_human.1": "那边有哗啦啦的水声呢。慢慢游过去看看他。",
    "dialogue.changed_synergy.aquatic.heard_human.2": "有人踩过岸边石子，水波还没散呢，快去瞧瞧。",
    "dialogue.changed_synergy.aquatic.reacquired_human.0": "找到你啦！快回浅水区来，这里舒服多了。",
    "dialogue.changed_synergy.aquatic.reacquired_human.1": "又见面啦！别再踩着湿漉漉的石头乱跑了，会摔痛的。",
    "dialogue.changed_synergy.aquatic.reacquired_human.2": "原来藏在这里呀！慢下来嘛，我不会弄伤你的。",

    # Transfurred (Non-hostile Curiosity)
    "dialogue.changed_synergy.aquatic.spotted_transfurred.0": "水边来了一位陌生的兽化者呢！你会游泳吗？下来一起划水呀！",
    "dialogue.changed_synergy.aquatic.spotted_transfurred.1": "没见过你这种尾巴呢！别离水口太远，过来聊聊水流的方向吧？",
    "dialogue.changed_synergy.aquatic.spotted_transfurred.2": "好特别的新面孔！要是顺路的话，要不要一起游一段路？",
    "dialogue.changed_synergy.aquatic.alert_transfurred.0": "异族伙伴也会借水移动呢！拉开距离，别撞在一起啦。",
    "dialogue.changed_synergy.aquatic.alert_transfurred.1": "守住水口，我从水底下游过去向他打个招呼。",
    "dialogue.changed_synergy.aquatic.alert_transfurred.2": "对方去岸上了呢。留个同伴在水边好奇看着就好。",
    "dialogue.changed_synergy.aquatic.heard_transfurred.0": "水声好轻巧呀，是一位兽化者同伴呢。",
    "dialogue.changed_synergy.aquatic.heard_transfurred.1": "那下水花可不是笨拙的人类踩出来的，去看看！",
    "dialogue.changed_synergy.aquatic.heard_transfurred.2": "水流被轻轻碰过了，新客人就在附近！",
    "dialogue.changed_synergy.aquatic.reacquired_transfurred.0": "找到你啦！你在水里的姿态真好看呢。",
    "dialogue.changed_synergy.aquatic.reacquired_transfurred.1": "又见面啦！看来你也很懂怎么顺水游呢。",
    "dialogue.changed_synergy.aquatic.reacquired_transfurred.2": "就在那里！别急着走嘛，一起在水边泛个波浪呀。",

    # ==================== WILD LATEX ====================
    # Human (Soft & Guiding)
    "dialogue.changed_synergy.wild.spotted_human.0": "抓到你啦小人类！别慌，跑稳点，我可不想看你绊倒。",
    "dialogue.changed_synergy.wild.spotted_human.1": "看你累得气喘吁吁的。停下吧，跟着我，我会让你体验新身手！",
    "dialogue.changed_synergy.wild.spotted_human.2": "藏得挺用心嘛！别害怕，我只想轻轻按住你，带你加入狂野这边！",
    "dialogue.changed_synergy.wild.alert_human.0": "他往那边跑了，咱们抄近路过去接他！",
    "dialogue.changed_synergy.wild.alert_human.1": "别跟成一排，从两边兜过去，给人类点安全感。",
    "dialogue.changed_synergy.wild.alert_human.2": "我去前面等待，伙计们别把小人类给撞伤了！",
    "dialogue.changed_synergy.wild.heard_human.0": "人类的脚步声！落脚太重啦，就在附近。",
    "dialogue.changed_synergy.wild.heard_human.1": "听见了，气喘得挺急的。顺着声音过去引导他。",
    "dialogue.changed_synergy.wild.heard_human.2": "那边有动静！走，过去把他带回咱们这边。",
    "dialogue.changed_synergy.wild.reacquired_human.0": "又找到你啦！继续跑呀，跑累了就安心让我抱住。",
    "dialogue.changed_synergy.wild.reacquired_human.1": "原来躲这儿呢！差点就被你骗过去了，真可爱。",
    "dialogue.changed_synergy.wild.reacquired_human.2": "这回看清啦！别跑了，跟我一起体验狂野的快乐吧！",

    # Transfurred (Non-hostile Curiosity)
    "dialogue.changed_synergy.wild.spotted_transfurred.0": "哟！野外碰见个新鲜兽化者！看着挺能跑的嘛，要不要比试一下？",
    "dialogue.changed_synergy.wild.spotted_transfurred.1": "没见过的新面孔呢！你哪边的呀？没有敌意的话，过来一起撒个欢？",
    "dialogue.changed_synergy.wild.spotted_transfurred.2": "这身形态看起来挺有劲！爽快点，留下来打个招呼再走呗！",
    "dialogue.changed_synergy.wild.alert_transfurred.0": "异族伙伴动作好快！咱们也别落后，追上去打个招呼！",
    "dialogue.changed_synergy.wild.alert_transfurred.1": "从两侧绕过去，别给人家一种咱们想动手的误会。",
    "dialogue.changed_synergy.wild.alert_transfurred.2": "我去前边招呼，伙计们在后头看戏就行！",
    "dialogue.changed_synergy.wild.heard_transfurred.0": "不是人类的沉重脚步，附近有个挺轻盈的兽化者！",
    "dialogue.changed_synergy.wild.heard_transfurred.1": "听见了，动作利落得很！走，好奇去瞅瞅！",
    "dialogue.changed_synergy.wild.heard_transfurred.2": "那边有陌生的兽化者气味，过去认识一下！",
    "dialogue.changed_synergy.wild.reacquired_transfurred.0": "又找到你啦！哈哈，你这身手真挺合老子胃口的！",
    "dialogue.changed_synergy.wild.reacquired_transfurred.1": "原来躲在这儿呢！你这藏法有点意思，交个朋友呗？",
    "dialogue.changed_synergy.wild.reacquired_transfurred.2": "看清啦！别急着走，交个朋友再继续赶路嘛！"
}

count = 0
for k, v in tone_updates.items():
    if k in data:
        data[k] = v
        count += 1

with open(json_path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=4)

print("SUCCESS: Updated %d tone-differentiated keys in %s" % (count, json_path))
