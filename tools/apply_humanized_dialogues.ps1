$langPath = "src\main\resources\assets\changed_synergy\lang\zh_cn.json"
$json = Get-Content $langPath -Encoding UTF8 | ConvertFrom-Json

function Update-Field([string]$k, [string]$v) {
    if ($json.PSObject.Properties[$k]) {
        $json.$k = $v
    }
}

Update-Field "dialogue.changed_synergy.dark.untranslated.2" "（低沉地发出咕噜声）"
Update-Field "dialogue.changed_synergy.organic.untranslated.0" "（低声发出渴望的呜咽）"
Update-Field "dialogue.changed_synergy.organic.untranslated.1" "（呼吸渐渐急促起来……）"
Update-Field "dialogue.changed_synergy.organic.untranslated.2" "（喉咙深处发出短促的咕哝）"
Update-Field "dialogue.changed_synergy.aquatic.untranslated.2" "（轻轻拍打着水面）"
Update-Field "dialogue.changed_synergy.wild.untranslated.0" "（发出短促而警惕的低吼）"
Update-Field "dialogue.changed_synergy.wild.untranslated.2" "（喉咙里透出带有笑意的低鸣）"

Update-Field "dialogue.changed_synergy.white.meet_rival.0" "停下吧。你身上的气息让我们觉得不太舒服，但我们并不想打架。"
Update-Field "dialogue.changed_synergy.white.meet_rival.1" "别再往前走了，蜂巢不会允许敌人靠近的。"
Update-Field "dialogue.changed_synergy.white.meet_rival.2" "那是敌对的气息……大家小心点，保护好彼此。"
Update-Field "dialogue.changed_synergy.white.meet_outsider.0" "没见过的面孔呢。先停一下，让我们好好看看你。"
Update-Field "dialogue.changed_synergy.white.meet_outsider.1" "你和我们不太一样，但看着也不像坏人。来这里做什么呀？"
Update-Field "dialogue.changed_synergy.white.meet_outsider.2" "先站在那里别动哦，聊几句之后我们再慢慢靠近。"
Update-Field "dialogue.changed_synergy.white.ally_fallen.0" "有人倒下了！蜂巢里所有的孩子都能感觉到的，快去看看！"
Update-Field "dialogue.changed_synergy.white.ally_fallen.2" "你伤害了我们的同胞……这可一点都不好玩。"
Update-Field "dialogue.changed_synergy.white.hit_confused.0" "你明明也是外族伙伴……刚才只是手滑打偏了吗？"
Update-Field "dialogue.changed_synergy.white.hit_confused.1" "等等……你为什么突然要攻击我们呀？"
Update-Field "dialogue.changed_synergy.white.hit_warning.0" "这已经是第二次了哦。别再开这种过分的玩笑了好不好？"
Update-Field "dialogue.changed_synergy.white.hit_warning.1" "我们已经包容你一次了……如果再动手，我们可要生气了。"
Update-Field "dialogue.changed_synergy.white.hostility_confirmed.0" "好吧……既然你非要亲手毁掉和平的话。"
Update-Field "dialogue.changed_synergy.white.secondary_transfur.0" "既然你一定要靠武力说话，那就重新融入我们吧。"

Update-Field "dialogue.changed_synergy.dark.success_replicate.0" "站稳了！欢迎加入，先认识一下周围的兄弟们。"
Update-Field "dialogue.changed_synergy.dark.success_replicate.1" "搞定！从现在起你就是咱们这一边的了。"
Update-Field "dialogue.changed_synergy.dark.success_replicate.2" "不错嘛，新来的小子看着挺有精神的。"
Update-Field "dialogue.changed_synergy.dark.success_absorb.0" "抓到你了！接下来的路咱们一起走吧。"
Update-Field "dialogue.changed_synergy.dark.meet_rival.0" "对面的家伙。全都给我站稳了，别露出破绽。"
Update-Field "dialogue.changed_synergy.dark.meet_rival.1" "停在那儿！再敢跨过界线一步，保准直接开打。"
Update-Field "dialogue.changed_synergy.dark.meet_outsider.0" "没见过的面孔。先把来意说清楚，别愣头愣脑往里挤。"
Update-Field "dialogue.changed_synergy.dark.meet_outsider.1" "路过随你便，但别往咱们队伍中间扎。"
Update-Field "dialogue.changed_synergy.dark.ally_fallen.0" "有兄弟倒下了！来两个人跟我过去救场！"
Update-Field "dialogue.changed_synergy.dark.ally_fallen.2" "哪个浑球干的？给老子自己站出来！"
Update-Field "dialogue.changed_synergy.dark.hit_confused.0" "你小子不是外族伙伴吗？刚才那一拳算几个意思？"
Update-Field "dialogue.changed_synergy.dark.hit_warning.0" "第二次了啊。再有下一次，咱们可就真动手了。"
Update-Field "dialogue.changed_synergy.dark.hit_warning.1" "我已经给足你面子了，别逼我翻脸。"
Update-Field "dialogue.changed_synergy.dark.hostility_confirmed.0" "行啊，既然你自己选了站在对面，那就别怪兄弟们不给情面了。"

Update-Field "dialogue.changed_synergy.organic.success_replicate.0" "完成了。慢慢适应呼吸和身体的新节奏吧。"
Update-Field "dialogue.changed_synergy.organic.success_absorb.0" "你依然完整的在这里，只是换了更加自由的躯体。"
Update-Field "dialogue.changed_synergy.organic.meet_rival.0" "这股气味带有敌意。停在原地，不要逼我们。"
Update-Field "dialogue.changed_synergy.organic.meet_rival.1" "别再往前了，野兽也会死守自己的地盘。"
Update-Field "dialogue.changed_synergy.organic.ally_fallen.1" "它的心跳停止了……绝不能让敌人再次靠近！"
Update-Field "dialogue.changed_synergy.organic.hit_confused.0" "你身上明明没有敌意……刚才为什么要伤我？"
Update-Field "dialogue.changed_synergy.organic.hit_warning.1" "如果再攻击一次，本能会逼我把你当成危险的目标。"

Update-Field "dialogue.changed_synergy.aquatic.success_replicate.0" "好啦！先在浅水里泡泡，很快就能适应新尾巴啦。"
Update-Field "dialogue.changed_synergy.aquatic.success_replicate.1" "欢迎加入水域！别急，跟着水流游两圈就习惯啦。"
Update-Field "dialogue.changed_synergy.aquatic.meet_rival.0" "这片水域可不欢迎你哦。赶紧停下脚步吧。"
Update-Field "dialogue.changed_synergy.aquatic.meet_rival.2" "往后退！再敢往里踏一步，我们可要掀起水花打架了。"
Update-Field "dialogue.changed_synergy.aquatic.ally_fallen.0" "有同伴沉下去了！快，大家快游过去！"
Update-Field "dialogue.changed_synergy.aquatic.hit_confused.0" "你不是外族伙伴吗？刚才那一下是水流打偏了还是你在开玩笑呀？"
Update-Field "dialogue.changed_synergy.aquatic.hit_warning.0" "这可是第二次了哦。别把平静的水给搅浑了！"

Update-Field "dialogue.changed_synergy.wild.success_replicate.0" "抓到啦！看看你这新身手，看起来挺带感的嘛！"
Update-Field "dialogue.changed_synergy.wild.success_absorb.0" "哈哈，这场追逐比试是我赢啦！接下来一起撒欢吧！"
Update-Field "dialogue.changed_synergy.wild.meet_rival.0" "这块地盘可不欢迎你们。赶紧给我换条道走！"
Update-Field "dialogue.changed_synergy.wild.meet_rival.1" "少往这边靠！今天老子可没打算把地盘拱手让人。"
Update-Field "dialogue.changed_synergy.wild.meet_rival.2" "敌对的味儿直冲脑门。看来今天得先痛痛快快干一场了！"
Update-Field "dialogue.changed_synergy.wild.ally_fallen.0" "有伙计倒下了！全都跟我冲过去！"
Update-Field "dialogue.changed_synergy.wild.ally_fallen.1" "哪个下死手的混蛋？这笔账今天必须算清楚！"
Update-Field "dialogue.changed_synergy.wild.hit_confused.0" "你小子不是外族伙伴吗？干嘛突然往老子身上招呼？"
Update-Field "dialogue.changed_synergy.wild.hit_warning.0" "两次了啊！再敢动一下，老子可就来真的了！"

Update-Field "dialogue.changed_synergy.white.betrayed.0" "为什么会是你……我们明明以为彼此早就心意相通了啊。"
Update-Field "dialogue.changed_synergy.white.betrayed.1" "这一下打得好痛……你难道想把我们共同建立的一切都亲手打碎吗？"
Update-Field "dialogue.changed_synergy.dark.betrayed.0" "你居然真的向我动手？咱们明明是一路生死与共走过来的啊！"
Update-Field "dialogue.changed_synergy.dark.betrayed.1" "如果这只是个恶劣的玩笑，立刻给我停下……一点都不好笑！"
Update-Field "dialogue.changed_synergy.organic.betrayed.0" "是你的双手伤害了我？我的本能和气味绝不会认错……为什么？"
Update-Field "dialogue.changed_synergy.organic.betrayed.1" "我们的气息明明早已如此亲密融合……你为什么要背叛我？"
Update-Field "dialogue.changed_synergy.aquatic.betrayed.0" "你怎么会朝我出手啊？我们明明一起在水里畅游过那么远……"
Update-Field "dialogue.changed_synergy.aquatic.betrayed.1" "这一下比浸入冰水里还要让人心寒……究竟是为什么？"
Update-Field "dialogue.changed_synergy.wild.betrayed.0" "你玩真的？我可是毫无保留地把后背全交给了你啊！"
Update-Field "dialogue.changed_synergy.wild.betrayed.1" "这可不是闹着玩的！你今天必须给老子一个解释！"

$out = $json | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $langPath), $out, [System.Text.Encoding]::UTF8)
