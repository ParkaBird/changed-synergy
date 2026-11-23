# 地穴（`changed_synergy:burrow`）建筑空间分析与对话系统槽位/触发条件全览

本文档为**纯技术现状与设计位参考手册**（不包含预设剧情台词，全部由你自行填充创作），汇总自以下工程真实数据：
- 结构文件：`src/main/resources/data/changed_synergy/structures/burrow.nbt`
- 世界生成：`src/main/resources/data/changed_synergy/worldgen/structure/burrow.json`、`tags/worldgen/biome/has_structure/burrows.json`
- 对话与营地系统：`NpcDefinitions.java`、`NpcConversations.java`、`NpcTextStyle.java`、`StoryNpcService.java`、`StoryClueService.java`、`CampDialogue.java`、`CampLifeService.java`、`CampMembershipService.java`、`CampApproachGoal.java`、`NpcRites.java`、`StructureResidentGoal.java`、`StoryNpcScreen.java`

---

## 第一部分：建筑与对应位置全分析（`burrow.nbt`）

### 1.1 全局尺寸与世界生成参数
- **结构模板尺寸**：`56 × 34 × 55`（局部整数方块坐标 `X: 0..55`，`Y: 0..33`，`Z: 0..54`）。
- **生成配置（`burrow.json`）**：
  - `type`: `changed_synergy:research_lab`
  - `ground_level`: `20`（即 `Y < 20` 整体埋入地下，`Y ≥ 20` 对应地表隆起雪坡与冷杉林入口）
  - `clearing_radius`: `5`，`max_terrain_slope`: `5`
  - **生成群系**：`minecraft:snowy_plains`（积雪平原）、`minecraft:snowy_taiga`（积雪针叶林）、`minecraft:grove`（雪林）
  - **生态特征**：建筑地下部分内部共布置 **30 格空调（`changed:air_conditioner`）**、**33 个通风扇（`changed:vent_fan`）**、**27 块浮冰（`packed_ice`）** 与 **6 块蓝冰（`blue_ice`）**，全建筑外部表面与树冠覆盖 **1,788 层雪片** 与 **22 块雪块**。

### 1.2 立体动线总览（由表及里）
1. **地表入口与冷杉掩体（`Y = 14..33`）**
   - 东侧山坡草地（`X=30..54, Z=20..54`）上方有大型覆雪云杉林与石质台阶入口，入口处外设 `spruce_fence` 围栏（`[44..46,21,37]`、`[47..48,21,32]`）和孤立灯柱/灯笼（`[50,22,36]`）。
   - 由 `[37,19,34..36]` 沿橡木/深色橡木下行阶梯直降至 `[30,14,34..36]`，进入 **顶层阁楼东侧通道**。
2. **顶层阁楼（`Y = 14..18`）**
   - 位于结构中南偏上区域，是进入地穴后的第一室内落脚点。
   - 阁楼北西侧经 `[20..22, 9..16, 20..30]` 阶梯向下分流至：
     - **上层厨房吧台区 & 室内农场（`Y = 12..14`）**
     - **中层宿舍走廊、阅览角、纸箱室 & 鱼池（`Y = 6..10`）**
     - **中下层内部工坊（`Y = 8..11`）**
3. **负二层主卧与终端室（`Y = 1..4`）**
   - 由中层中央南向走廊 `[23..24, 1..6, 18..24]` 沿长楼梯直下，尽头为一扇 **双开深色橡木门（`[23..24, 1..2, 24]`）**，门后是整座地穴最深的双人主卧与旧终端工作间。

---

### 1.3 8 大功能分区精确坐标与物件清单

> 说明：以下坐标均为 `burrow.nbt` 内部局部坐标（旋转 `NONE` 下）；容器标注“带 LootTable”意味着首次开启才会随机生成战利品，在未拆包前也会被 `CampMembershipService.usableStorage` 排除在营地公共补给箱之外。

#### 分区 A：地表冷杉雪坡与东向入口阶梯
- **范围**：`X=30..54, Y=14..33, Z=20..54`
- **地形与结构**：草方块覆盖山丘、圆石/苔石砌筑的入地阶梯（`X=30..37, Z=34..36`）、高大云杉树（树干延伸至 `Y=31`，树叶至 `Y=33`）。
- **照明与标志**：入口阶梯处挂灯笼 `[50,22,36]`、矮云杉栅栏；无箱子或 NPC。
- **空间意义**：**外部与内部的唯一咽喉**，下行 10 级阶梯直接踏入灰狐所在的顶层阁楼。

#### 分区 B：顶层阁楼（Upper Loft · 迎宾与地面联络区）
- **范围**：`X=18..29, Y=14..18, Z=27..40`
- **当前驻留实体**：
  - **雌性灰狐首领 (`changed_synergy:latex_gray_fox_female`)**：`pos=[20.5, 15.0, 32.5]`，`Character=1`，`Camp=1`（目前未写入实体 `CustomName`）
  - **雌性雪豹 (`changed:latex_snow_leopard_female`)**：`pos=[19.5, 15.0, 33.5]`，`Character=0`，`Camp=1`（**注意：当前 `StructureResidentGoal` 未给她分配 `DialogueRole`，详见第二部分 2.6 节**）
- **地板与制冷**：
  - 室内中央栽有一棵装饰橡木小树（`oak_leaves` `[21..25, 16..18, 33..37]`）
  - 空调 1 组 6 格（`[19, 15..16, 34..36]`）、通风扇 2 个（`[18,15,33]`, `[18,15,37]`）、浮冰 4 块（`[24,13,32]`, `[25,13,33]`, `[22,13,37]`, `[22,14,36]`）
- **睡垫（地穴特色，6 个 Pillow）**：
  - 蓝睡垫：`[23,14,32]`、`[28,14,32]`
  - 青睡垫：`[21,14,38]`、`[28,14,38]`
  - 浅蓝睡垫：`[25,14,33]`、`[23,14,39]`
- **桌面物件与展示**：
  - `changed:canned_soup`（4 罐）`[19,14,37]`；`changed:canned_peaches` `[26,14,38]`
  - `changed:book_stack`：`[20,14,38]`（8 本）、`[27,14,32]`（3 本）、`[27,14,38]`（1 本）
  - 展示框 ×2：`[26.5, 14.03, 32.5]`（`sweet_berries`）、`[22.5, 14.03, 38.5]`（`sweet_berries`）
- **容器 / 故事载体空位**：本区目前 **无任何箱子、木桶、书桌、讲台或告示牌**。非常适合放墙面告示、地面便携便签，或靠 `sweet_berries` / 罐头堆 / 睡垫作为闲聊锚点。

#### 分区 C：上层厨房与 L 型吧台（Kitchen & Bar）
- **范围**：`X=20..31, Y=12..14, Z=2..9`
- **当前驻留实体**：
  - **雄性兔狲首领 (`changed_synergy:latex_manul`)**：`pos=[30.5, 13.0, 6.5]`，`Character=1`，`Camp=1`，`CustomName: npc.changed_synergy.manul_chief.title`（正对灶台与砧板）
- **灶台与炊具排（东墙 `X=31`）**：
  - 点燃中的 `farmersdelight:stove` ×2：`[31,12,5]`、`[31,12,7]`
  - 灶台上方：`skillet` 煎锅 `[31,13,5]`、`cutting_board` 砧板（上插 `iron_knife`）`[31,13,6]`、`cooking_pot` 烹饪锅 `[31,13,7]`
  - 交替 `minecraft:furnace` ×3：`[31,12,4]`、`[31,12,6]`、`[31,12,8]`
  - 地面铺 `farmersdelight:canvas_rug` ×5：`[30,12,4..8]`
  - 上方吊柜（`Y=14`）：`barrel` ×3（`[31,14,4/6/8]`）+ `farmersdelight:spruce_cabinet` ×2（`[31,14,5/7]`），**5 个均带有 `changed:chests/office_breakarea_food` LootTable**
- **吧台区（`X=22..24, Z=3..7`）**：
  - `changed:bar_top` ×7 + `changed:bar_stool` ×4
  - 吧台摆设：`potted_wither_rose` 凋灵玫瑰盆栽 `[22,13,3]`、`potted_spruce_sapling` 云杉树苗盆栽 `[22,13,8]`、`canned_peaches` `[22,13,5]`、`shepherds_pie_block` 牧羊人派（4 份）`[23,13,3]`、`gleaming_salad_block` 闪耀沙拉（4 份）`[22,13,7]`
- **社区唯一公共补给双箱**：
  - 位置：`[26,12,9]` + `[27,12,9]`（朝北双箱，厨房南入口边）
  - NBT 已标记：`ChangedSynergyPublicStorage: 1`，`CustomName: container.changed_synergy.community_supplies`
  - **这是玩家捐献物资（`camp_donate`）及成员共享存取的唯一法定公共容器。**
- **通风**：`vent_fan` 5 个（`[24,13,2]`, `[25,13,2]`, `[28,13,2]`, `[29,13,2]`, `[18,13,8]`）。

#### 分区 D：上层室内农场与封闭畜栏（Indoor Farm & Pen）
- **范围**：`X=4..25, Y=12..14, Z=8..18`
- **当前驻留实体**：
  - **农场居民 (`changed:latex_yuin`)**：`pos=[15.5, 12.0, 13.5]`，`Character=0`，`DialogueRole="farm"`（站在中央横道与畜栏门前）
- **农田（`X=14..18`）**：
  - `beetroots` ×10（`Z=9..10`）、`wheat` ×10（`Z=11..12`）、`potatoes` ×10（`Z=14..15`）、`carrots` ×10（`Z=16..17`）
  - 东侧走廊边缘 `sweet_berry_bush` ×6（`X=25, Z=11..16`），上方隔着 8 块 `oak_trapdoor`（`[25,13,10..17]`）
- **封闭畜栏（`X=5..11, Z=9..17`）**：
  - 深色橡木围栏与 3 扇栅栏门（`[11,12..13,13]`、`[19,12,13]`），墙插火把 `[7,13,11]`、`[7,13,15]`
  - 活体动物：**牛 ×2、绵羊 ×2、鸡 ×2**（`Y=12`）
- **储物容器（共 6 格）**：
  - 牧羊人木桶 ×2：`[12,12..13,9]`（`village_shepherd` LootTable）
  - 农夫大箱子 ×2 组（4 格）：`[12,12..13,16..17]`（`village_farmer` LootTable）
- **通风**：`vent_fan` 3 个（`[15,13,8]`, `[15,13,18]`, `[18,13,18]`）。

#### 分区 E：中层西侧室内恒温鱼池（Indoor Pond）
- **范围**：`X=2..14, Y=4..11, Z=23..34`
- **当前驻留实体**：
  - **鱼池居民 (`changed:latex_snow_leopard_male`)**：`pos=[5.5, 8.0, 28.5]`，`Character=0`，`DialogueRole="pond"`
- **水体与生物**：
  - 69 格水（`X=4..11, Y=4..7, Z=25..32`）、10 株海草、1 个海泡菜 `[7,4,28]`、5 片睡莲
  - 池内活鱼：**鳕鱼（`minecraft:cod`）×2、鲑鱼（`minecraft:salmon`）×3**
- **西墙大型旗帜拼图（`X=3, Y=9..11, Z=24..33`）**：
  - 共 **30 面墙面旗帜**（1 黑 + 5 灰 + 23 浅灰 + 1 白）拼成的洞壁黑白灰阶装饰壁画（紧贴 `X=2` 黑/灰/浅灰混凝土底墙）。
- **东岸渔具台（`X=10..14, Z=29..34`）**：
  - 渔夫木桶 ×3：`[13,9,33]`、`[14,9,33]`、`[14,10,33]`（`village_fisher` LootTable）
  - 展示框 ×3：钓鱼竿 `[13.5,9.03,31.5]`、生鲑鱼 `[13.5,10.03,33.5]`、生鳕鱼 `[14.5,9.03,32.5]`
  - 睡垫 ×2：`light_blue_pillow` `[10,8,32]`、`blue_pillow` `[13,9,29]`
- **通风**：`vent_fan` 4 个（`[4,10,23]`, `[12,10,23]`, `[4,10,34]`, `[12,10,34]`）。

#### 分区 F：中下层南侧内部熔炼工坊（Inner Workshop）
- **范围**：`X=23..30, Y=8..11, Z=24..39`
- **当前驻留实体**：
  - **工坊居民 (`changed:latex_blue_wolf`)**：`pos=[27.5, 9.0, 37.5]`，`Character=0`，`DialogueRole="workshop"`
- **门户与入口**：
  - 北口两扇云杉门（`[27..28,9..10,24]`）+ 门内 4 扇敞开活板门框 + 2 堆肥桶 + 2 盆蓝色兰花（`[26,10,27]`, `[26,10,34]`）
  - 门外走廊挂画 `minecraft:sunset`（`[29.97,10.5,23.0]`）
- **西侧加工列（`X=26`）**：
  - `blast_furnace` ×2 叠放 `[26,9..10,31]`；`furnace` ×4 叠放 `[26,9..10,29]` 与 `[26,9..10,33]`；`anvil` ×2 `[26,9,30]`、`[26,9,32]`
  - `lab_table` ×4（`[26,9,27..28]`、`[26,9,34..35]`）；`crafting_table` `[26,9,38]`（上置灯笼）
- **东侧仓储列（`X=29`）**：
  - 共 **16 格箱子（4 组上下双层大箱子）**，交替配置 `changed:chests/low_tier_lab`（`Z=28..29, 34..35`）与 `village_toolsmith`（`Z=31..32, 37..38`）LootTable
- **地板与散热**：地面镶嵌 14 块 `packed_ice` 降温；东墙 4 个 `vent_fan`（`[30,10,27/30/33/36]`）。
- **推荐放线索的空位**：`lab_table` 桌面（`[26,10,27..28]` / `[26,10,34..35]`）当前除 2 盆兰花外有 2 格桌面空位，且工具箱附近无故事书/纸条。

#### 分区 G：中层北侧宿舍走廊、阅览角与纸箱储藏室（Dorm Wing & Cardboard Storage）
- **范围**：`X=12..41, Y=6..8, Z=7..23`
- **当前驻留实体**：
  - **阅览角居民 (`changed:latex_yuin`)**：`pos=[20.5, 7.0, 9.5]`，`Character=0`，`DialogueRole="library"`
  - **走廊居民 (`changed:latex_pink_wyvern`)**：`pos=[21.5, 6.0, 10.5]`，`Character=0`，`DialogueRole="dorm"`
- **走廊公共区与挂画/植物**：
  - 东侧长廊与中部通道：`X=18..36, Z=13..23`
  - 走廊植物：3 盆 `potted_torchflower`（火把花盆栽）排在 `[17,7,18/20/22]`；3 个 `composter` 堆肥桶（`[18,6,14]`, `[30,6,16]`, `[38,6,14]`）+ 杜鹃树叶造景
  - 走廊挂画：
    - 纸箱室北门旁：`minecraft:pool` `[30.0, 7.5, 8.03]`（已左移至 `X=30`）
    - 南端下主卧楼梯旁对称双画：`minecraft:bust` `[23.03,5.0,21.0]` 与 `minecraft:match` `[24.97,5.0,21.0]`
    - 东走廊：`changed:puro_place` `[33.0, 7.0, 22.97]`
  - 东西墙面各 1 整台空调：`[14,7..8,14..16]` 与 `[40,7..8,14..16]`（共 12 格空调）+ 10 个通风扇
- **7 间卧室与 1 间纸箱储藏室明细**：

| 房间位置 | 局部坐标大致范围 | 门位 | 床位（含颜色） | 房间内配置与亮点 |
|---|---|---|---|---|
| **1. 西北单间** | `X=13..18, Z=8..12` | `[15..16,6..7,13]` | `light_blue_bed` `[14,6,11..12]` | 2 张 `lab_table`、办公椅、`book_stack`(4)、墙上拉杆 `[17,7,10]` |
| **2. 西南单间** | `X=13..17, Z=18..22` | `[15..16,6..7,17]` | `lime_bed` `[14,6,21..22]` | 2 张 `lab_table`、办公椅、墙上拉杆 `[14,7,20]` |
| **3. 中北阅览角（无门开放间）** | `X=20..22, Z=8..12` | 无门 | `pink_bed` `[20,6,8..9]` | 2 张 `lab_table` `[21..22,6,8]`，桌上摆 **`stuffed_pumpkin_block` 填馅南瓜(4份)`[21,7,8]`** 与 `book_stack`(4)，办公椅 `[20,6,12]`，墙拉杆 `[20,7,10]`（**`library` 与 `dorm` 两名居民出生站此处**） |
| **4. 东北双人/纸箱储藏室** | `X=28..31, Z=8..12` | `[29..30,6..7,13]` | `white_bed` `[28,6,8..9]` | 室内塞满 **4 个大纸箱（`lab_white_latex`）、4 个小纸箱（`orange_tree`）、2 个纸箱容器（`lab_white_latex`）、4 个 `iron_crate`**；拉杆 `[31,7,10]` |
| **5. 东北外侧单间** | `X=33..39, Z=8..12` | `[35..36,6..7,13]` | `blue_bed` `[34..35,6,8]` | 4 张 `lab_table`、办公椅、2 处 `book_stack`(3本/2本)、**4 支蜡烛 `[37,7,9]`**、挂画 **`minecraft:aztec2` `[34.5,8.5,8.03]` & `minecraft:plant` `[37.5,8.5,8.03]`**、拉杆 `[34,7,10]` |
| **6. 东南工匠单间** | `X=31..35, Z=18..22` | `[32..33,6..7,17]` | `magenta_bed` `[33..34,6,22]` | 4 张 `lab_table`、办公椅、**`smithing_table` 锻造台 `[34,6,18]` + `crafting_table` 工作台 `[34,6,20]`**、拉杆 `[34,7,20]` |
| **7. 最东南单间** | `X=37..40, Z=18..22` | `[38..39,6..7,17]` | `green_bed` `[40,6,18..19]` | 2 张 `lab_table`、办公椅、`book_stack`(3)、拉杆 `[37,7,20]` |

> 宿舍区共有 **7 张床**（对应 6 名居民，还多出 1 张空床：即纸箱室里的 `white_bed` 或任意一间空房，而 **2 名首领另有底层 2 张 `cyan_bed`**，全地穴共 **9 张床 + 6 个阁楼睡垫 = 15 个可睡点位**）。房间内的多张 `lab_table` 桌面目前绝大部分空置，是极佳的书本/便签/剪贴板放置空位。

#### 分区 H：负二层双首领主卧与旧终端室（Lower Master Suite & Terminal）
- **范围**：`X=15..30, Y=1..4, Z=22..34`
- **当前驻留实体**：无固定白昼出生实体（双首领夜间/就寝目标地）。
- **门户与制冷心脏**：
  - 双开深色橡木门：`[23..24, 1..2, 24]`
  - 门口墙挂 **两整台空调**（`[19..21,2..3,25]` 与 `[26..28,2..3,25]`，共 12 格空调）
  - 东侧冰室凹室：`X=27..29, Y=1..3, Z=25..28` 堆着 **6 块蓝冰 + 8 块浮冰**
- **左半侧（休息区 `X=18..25, Z=26..33`）**：
  - **两张紧挨着的并排青色床**：`cyan_bed` `[23,1,32..33]` + `[24,1,32..33]`，床前铺 6 块青色地毯
  - 床头正上方挂画：**`changed:creation_of_lin`**（`[24.0, 2.0, 33.97]`）
  - 床头两侧各点一簇蜡烛：`[22,2,33]`（3 支）、`[25,2,33]`（2 支）
  - 西墙书架区：办公椅 `[19,1,32]`、桌面/书架堆书 `[18,2,27]`（2 本）与 `[18,2,32]`（5 本）、3 支蜡烛 `[18,2,26]`
- **右半侧（终端区 `X=27..29, Z=31..33`）**：
  - 2 张 `lab_table`（`[28..29,1,33]`），桌上摆 **1 台 `changed:computer`**（`[28,2,33]`）
  - 旁边 2 支蜡烛 `[29,2,33]`、办公椅 `[28,1,32]`
  - 墙上挂画：**`changed:puro_doodle`**（`[29.97, 3.0, 31.0]`）
- **现状空白**：`changed:computer` 目前在 `burrow.nbt` 中没有任何内嵌 `Data` / 软盘文件数据；主卧 64 格书架与 `book_stack` 也仅是装饰方块，尚未在周围容器或讲台放置实体书本。

---

### 1.4 当前剧情碎片载体盘点（与 `StoryClueService` 扫描能力对照）

在 `burrow.nbt` 当前版本中：
- `lectern`（讲台）：**0**
- `written_book` / `writable_book`（成书/书与笔）：**0**
- 告示牌（`sign`）：**0**
- 电脑 `changed:computer` 的预置软盘/文本 NBT：**0**（仅空终端）
- 带 `ChangedSynergyPrivate`（玩家不能搜、但也不参与公共补给箱）或无 LootTable 的故事容器：**0**（除公共补给双箱外，其余 20+ 箱/桶/橱柜/纸箱均绑定原版或 Changed 战利品表 `LootTable`）

因此，如果你想在后续版本给地穴安放剧情载体，**`StoryClueService.java` 原生支持自动扫描并解锁 `clue:<id>` 的载体方式有 4 种**：
1. **`changed:computer`（或插在里面的软盘 `DiscData`）**：玩家右键电脑打开 `ComputerMenu` / 切换目录读取文件时，自动扫描整个虚拟目录树下的文件名与文件内容文本（即主卧 `[28,2,33]` 的终端天然可用）。
2. **`TextBlockEntity`（Changed 的便签/剪贴板/纸条方块）**：玩家右键方块（`RightClickBlock`）或打开 `TextMenu`（`onContainerOpen`/`Close`）时自动扫描 `Text` 字段。
3. **`minecraft:lectern`（讲台上的成书）**：右键讲台或打开 `LecternMenu` 时扫描书中 `title` / `author` / `pages` 全文。
4. **箱子/物品形式（`written_book` / `writable_book` / 带 `Text` 或 `Data` NBT 的手持物品）**：右键使用或从自定义箱子取出后触发扫描。*(注：若在固定位置放故事容器，切勿再同时保留 `LootTable`，并且记得加 `ChangedSynergyPrivate` 或让其属于可阅读非 LootTable 容器，以免和公共补给箱混淆。)*

---

## 第二部分：角色对话选项空位与触发条件机制全览

### 2.1 对话树 JSON 架构与数量硬上限（`NpcDefinitions.java` & `StoryNpcScreen.java`）

一个角色（或一组共用同一 JSON 的主副角色）的配置位于 `data/changed_synergy/story_npcs/<profile_id>.json`：

#### Profile 顶层空位
```json
{
  "template": "burrow",
  "species": "changed_synergy:latex_manul",
  "trait": "CALM",
  "chief": true,
  "dialogue": true,
  "equipment": [],
  "nodes": { ... }
}
```
- `trait` 合法值：`CreaturePersonality.Trait` 枚举（如 `CALM`, `CAUTIOUS`, `CURIOUS`, `PLAYFUL`, `PROTECTIVE`, `POLITE` 等）。
- `chief`: `true` 判定为首领（会拥有 `Standing` 声望显示、入籍/退出入口、首领专属 `chief` 条件）。
- `dialogue`: 若为 `false`，`nodes` 必须为空且不会打开对话 UI。
- `equipment`: 首领预设穿戴的物品 ID 列表，且当玩家与首领好感/信任 ≥ 40 且成为受保护成员后，可在 `camp_life` 中领取第 1 件作为礼物（`CampLifeService.canClaimOutfit`）。

#### 容量与 UI 渲染约束（硬校验，超限会抛 `JsonParseException`）
| 维度 | 硬性上限 | UI 显示规则与设计建议 |
|---|---|---|
| 单 Profile 的 `nodes` 总数 | **`≤ 128` 个** | 若 `dialogue=true`，必须包含 `"root"` 节点；所有 `next` 与 `requires` 目标节点必须存在。 |
| 单 Node 的 `lines` 条数 | **`1 ~ 32` 条** | 同一 `channel:"spoken"` + 紧随其后的 `channel:"mental"` 打包为**同一个“Passage（段落组）”**；多组 `spoken` 则点击鼠标左键/空格依次推进。 |
| 单 Line 的 `parts` 段数 | **`1 ~ 32` 段** | 支持分段上色/动画/停顿；若 `beat: true`，强制在此处切分页。 |
| 单 Node 的 `choices` 数量 | **`1 ~ 12` 个** | **UI 同屏一次最多显示 3 个按钮**（超过 3 个通过鼠标滚轮或方向键翻滚）。且 **`root` / `return` / `membership` / `resident_<role>` 会被服务器动态追加 1~2 个营地按钮**（见 2.5 节），因此这些节点的静态选项建议控制在 **4~9 个以内**。 |
| 单 Choice `id` 长度 | **`1 ~ 64` 字符** | 同一 Node 内不可重名。 |
| 单 Choice `requires` 数量 | **`0 ~ 16` 个** | 列举玩家此前**必须曾经阅读过的节点 ID**，全部读过后该选项才会现身。 |

---

### 2.2 `parts` 表现层空位（情绪 / 动画 / 节奏，出自 `NpcTextStyle.java`）

每段台词可配置如下呈现参数：
- **`channel`**：
  - `"spoken"`：常规口语（始终按 condition 判定显示）。
  - `"mental"`：心声 / 潜台词（**额外硬性要求** `TelepathyService.enabled()` 且玩家已兽化或拥有心灵感应能力 `canUnderstand` 才显示；UI 自动去掉首尾 `()` 或 `（）` 并以心声样式并排显示在上一句 `spoken` 下方，不占用翻页点击）。
- **`emotion`**（13 种）：
  `neutral`, `calm`, `curious`, `happy`, `excited`, `angry`, `fearful`, `sad`, `shy`, `whisper`, `surprised`, `determined`, `dreamy`
- **`effect`**（13 种，其中 `"auto"` 表示跟随上面 `emotion` 的默认动画）：
  `auto`, `none`, `emphasis`, `wave`, `shake`, `tremble`, `bounce`, `float`, `pulse`, `wobble`, `droop`, `shout`, `whisper`, `impact`
- **`speed`**：`0.25 ~ 3.0`（默认 `1.0`；最终速度 = `speed * emotion.speed`）
- **`intensity`**：`0.0 ~ 2.0`（默认 `1.0`；最终幅度夹逼到 `0 ~ 1.4`）
- **`pause`**：`0 ~ 2000`（单位毫秒；播完该段后额外停顿时长；标点符号 `,;:，；：` 自带 +150ms、`.!?。！？` 自带 +300ms、`…` 自带 +450ms）
- **`beat`**：`true / false`（为 `true` 时，在前端非 `mental` 文本处强制新建一页）

---

### 2.3 全量触发条件（`condition` 枚举）与前置门控机制

`line.condition` 与 `choice.condition` 共用同一套服务端条件函数（`NpcConversations.available`）。**以下 17 个内置条件 + 任意 `clue:<id>` 是全部可用触发方式**：

| `condition` 关键字 | 服务端真实判定逻辑 | 典型用法空位 |
|---|---|---|
| `"always"` | 恒为真 | 常规分支、返回/告别选项 |
| `"human"` | 玩家未兽化，且交互本体是玩家自身（未处在使用合体身躯状态） | 人类视角的开场白、人类专属吐槽 |
| `"transformed"` | 玩家已兽化，或当前交互本体为非自身（合体/借体） | 胶兽/同类视角的开场白、心声反应 |
| `"understands"` | `TelepathyService.enabled()` 且（玩家已兽化 或 具备心灵感应理解条件） | 控制某些心声/读心分支是否可见 |
| `"day"` | 当前维度处于白天（`level().isDay()`） | 昼夜作息不同的台词分支 |
| `"night_human"` | 夜晚 且 `"human"` | 深夜人类来访专属开场/话题 |
| `"night_transformed"` | 夜晚 且 `"transformed"` | 深夜胶兽来访专属开场/话题 |
| `"stranger"` | 玩家**尚未加入**当前这一个营地（允许已加入过别的营地） | `root`/`return` 里的【请求加入本营地（`enrol`）】入口 |
| `"member"` | 玩家**当前归属家（`HomeKey`）就是本营地** | `root`/`return` 里的【成员管理（`membership`）】入口 |
| `"chief"` | 说话的 NPC 属于 `chief = true` | 可用于同一 Profile 复用时区分首领/非首领线路 |
| `"can_join"` | 首领活着、无仇恨、未激怒/背叛、在营地内、信任度 `>= 0` | 允许显示入籍选项 |
| `"cannot_join"` | `!can_join`（例如好感为负或正在战斗/不在家） | 拒绝加入提示或安抚分支 |
| `"human_join"` | `"human"` 且 `"can_join"` | 人类“保持人形直接加入”选项 |
| `"transformed_join"` | `"transformed"` 且 `"can_join"` | 已兽化“以当前形态直接加入”选项 |
| `"can_reversible"` | `"can_join"` 且玩家是纯人类未变身、未受制、NPC 拥有合法 `SelfVariant` | 触发“借用/可逆转形体加入”菜单（`explain_form` / `reversible`） |
| `"can_permanent"` | `"can_reversible"` 且服务端支持永久原生体（True Transfur `NativeOrigin`） | 触发“永久成为该物种加入”二次确认链路 |
| `"clue:<线索ID>"` | 检查玩家在 `StoryNpcData.clues` 中是否已解锁对应 `<线索ID>` | 在 `root`/`return` 或深层节点中动态刷出秘密调查/剧情追问选项！ |

#### 其他系统级自动分流触发规则
1. **初次见面 vs 再次对话自动分流（`NpcConversations.open` L45-46）**：
   - 仅对**拥有独立 `Profile` 的角色（如首领）**生效：只要玩家在 `root` 节点完整点过至少一次非翻页选项（`complete(s)` 记录 `Progress.<uuid>.root = true`），**下次右键对话会自动跳过 `root`，直接从 `"return"` 节点开场！**
   - 因此首领 Profile 若想做“初见 vs 老熟人”，**建议同时配置 `"root"` 和 `"return"` 两个入口节点**（若不配 `"return"` 则永远走 `"root"`）。
   - **普通建筑居民（`Character=0`）**：每次都会直接进入 `resident_<DialogueRole>`（不走 `"return"`），由其内部设计 `topic_0` / `topic_1` 等分支。
2. **前置阅读解锁（`choice.requires`）**：
   - 例如设置 `"requires": ["about", "home"]`，则玩家只有先后点进并完成阅读 `"about"` 和 `"home"` 节点后，该选项（如 `"past"` 深层往事）才会出现在选项栏中。
3. **打开对话的前置物理门禁（`NpcConversations.valid`）**：
   - 玩家与 NPC 需距离 `≤ 8 格`（`distanceToSqr ≤ 64`）、彼此有视线（`hasLineOfSight`）、双方均未睡眠/被抓/夺舍中/100 tick 内交战。
   - **无法理解语言时的降级分支（`CampDialogue.node` L49-55）**：对于**普通居民（非 Chief）**，若玩家既未兽化也没有心灵感应理解能力（`!NpcDialogue.canUnderstand(npc, player)`），点对话**不会**打开 `resident_<role>` 节点，而是直接进 `camp_resident` 的哑语手势分支（`.gesture` + 唯一的“去找首领谈谈 `camp_seekchief`”按钮）。而**首领（`chief=true`）无视语言障碍，人类状态也可直接展开完整对话树。**

---

### 2.4 选项动作副作用（`choice.effect` 枚举）

每个选项点击后，除跳转到 `"next"` 节点外，服务端会执行以下 `effect`（共 **7 个**合法值）：

| `effect` 值 | 执行行为 | 成功 / 失败跳转机制 |
|---|---|---|
| `"none"` | 纯对话推进，无世界状态修改 | 记录当前节点已读 → 跳至 `next` |
| `"close"` | 记录当前节点已读，关闭对话界面 | 无 |
| `"join"` | 将玩家加入本营地（保留当前人形或当前兽化形态），按形态给予初始信任奖励（已兽化 `+2`，人类 `+0`），并让营地所有居民认识玩家 | 若玩家原本隶属**另一个营地**，会先自动拦截跳入公共节点 `camp_transfer` 询问是否搬家；否则成功后跳 `next`（通常为 `welcome_human`/`welcome_latex`） |
| `"reversible"` | 执行可逆转形仪式（变成该首领物种，保留人形身份，支持之后反悔还原）并加入营地（初始信任 `+5`） | 若跨营地搬家先经 `camp_transfer`；仪式失败则跳回 `root` |
| `"permanent"` | 执行不可逆永久仪式（通过 True Transfur 转换原生体）并加入营地（初始信任 `+8`） | 同上；建议前端多加一层 `"confirm_permanent"` 节点防误触 |
| `"leave"` | 让玩家退出当前营地（若身上挂着可逆仪式形态会自动还原回人类） | 成功后跳 `next`（通常为 `left`） |
| `"reverse_rite"` | 发起仪式逆转（对处于本营地可逆转形形态的成员解除兽化形态） | 自动关闭对话并启动动画/解除流程（通常仅由 `CampDialogue` 在 `membership` 节点自动挂载，无需手动加到 `root`） |

---

### 2.5 营地公共生活系统自动注入槽位（`CampDialogue.java` & `CampLifeService.java`）

在已注册为 `mainCamp`（包括 `"burrow"`）的建筑里，只要角色在家且能沟通，服务端会自动在以下位置**追加选项**（占用节点总 12 格上限与首屏展示顺序，请在设计选项空位时留出余量）：

#### 自动注入规则
1. **在首领的 `root`、`return`、`membership` 以及普通居民的 `resident_<role>` 节点末尾**：
   - 自动追加 **`camp_life`（“营地事务 / 聊聊这里的生活”）** 按钮。
2. **在首领的 `membership` 节点末尾（当玩家是成员时）**：
   - 若玩家处于可逆仪式形态，追加 `camp_reverse_rite`（`reverse_rite`）；否则追加 `camp_body`（跳转 `explain_form`，允许已入籍人类补做形态仪式）。
3. **点击 `camp_life` 后进入的公共菜单空位（无需写进 `story_npcs/*.json`，但需在 `camp_dialogue/common.json` 配置对应文本）**：
   - `camp_status`：查看食物储备（`low` / `enough` / `plenty`）、空床/总床数（地穴默认搜 `restBounds` 内床与睡垫）、安全状况、身份与上次帮忙记忆。
   - `camp_requests`：查看委托（**首领显示公共委托**，**普通居民显示私人饥饿委托**）：
     - 地穴（`"burrow"`）每日公共委托生成规则已在 `CampLifeService.java` L84-85 写定：**食物短缺时加 `FOOD`，常规每日固定刷 `FOOD` + `WATCH`（守卫击退威胁）**，最多同时保留 3 条未完成公共委托。
     - **主动靠近提醒（`CampApproachGoal`）**：若某个已入籍成员的居民身上挂着私人食物委托，且玩家进入 16 格视线内，该居民会主动小跑靠近并弹出 `camp_request_invite` 提示对话。
   - `camp_donate`：手持属于 `camp_food` / `camp_wool` / `camp_leather` / `camp_paper` / `camp_light` 标签的物品时，可捐 1 / 8 / 64 个入公共双箱 `[26..27,12,9]`（每日信誉获取上限 8 点）。
   - `camp_meal`（仅受保护成员可见）：召唤聚餐！触发前提：`CampLifeService.safe(npc)`、公共食物 ≥ 1、距离上次聚餐 ≥ 6000 tick（5 分钟），**且 `NaturalCampService.mealPoints(npc)` 非空**！
     - ⚠️ **重要建筑级警告**：`NaturalCampService.mealPoints(npc)` 目前只扫描建筑内的**已点燃营火（`minecraft:campfire`）**（只有 `desert_refuge` 有额外硬编码聚餐点）。**而 `burrow.nbt` 经检查目前只有 `farmersdelight:stove` 炉灶，0 个 `minecraft:campfire`！** 如果你想保留首领菜单里的“一起吃点东西”正常触发，必须在建筑里补 1 个营火，或者之后代码给 `burrow` 的厨房吧台加 `mealPoints` 坐标。
   - `camp_outfit`（仅首领 + 成员 + 信任 ≥ 40 + Profile `equipment` 非空 + 未领过）：领取首领同款装备第 1 件。

#### `camp_dialogue/common.json` 语音空位清单
- 若给地穴配置**专属首领语音**（当前 `CampLifeService.voice` 把 `"burrow"` 暂时落到了 `"resident"`），共需 **23 条文本空位**：
  `greeting`, `food_low`, `food_enough`, `food_plenty`, `safe`, `danger`, `remember`, `requests`, `no_requests`, `handled`, `watch`, `request`, `choose_item`, `donate`, `transfer`, `thanks`, `declined`, `changed`, `meal`, `watch_thanks`, `watch_wait`, `outfit`, `reverse_rite`。
- 若不单独区分居民方言，地穴普通居民默认复用 `camp.changed_synergy.resident.*`（共 **34 条**，已存在）；若要给地穴居民做专属方言（如像 `desert_resident` / `cavern_resident`），同样需要这 34 条（含 `playful_` / `cautious_` / `polite_` 三性格前缀变体及 `gesture` / `seekchief` / `away`）。

---

### 2.6 线索触发器（`StoryClueService.java`）清单与扩展方式

#### 现有全局已注册 `clue:<id>` 列表（目前可直接用于 `condition`，无需改代码）
`crisscross`, `dr_o`, `archives`, `aec`, `violet_code`, `box_note`, `notes`, `desert_568712`, `green_doctors_note`, `green_farewell`, `green_muntjac`, `green_reports`, `green_crystal`, `green_botany`

#### 为地穴新增专属线索的触发方式
`StoryClueService.inspectText(player, rawText)` 会把玩家读到的**书名/作者/书页/便签文本/终端软盘文件名与正文**转为全小写，做关键字子串匹配：
- 只要你在代码 `StoryClueService.inspectText` 中加一条 `if (text.contains("某个地穴关键词")) unlock(player, "burrow_xxx");`
- 并在建筑对应位置（例如主卧终端 `[28,2,33]`、厨房便签、阅览角讲台、纸箱室箱内书卷）放入含该关键词的文本载体，玩家**只需右键打开查看一次**，服务器即永久为该玩家记录 `"burrow_xxx"`，之后任何 NPC 对话中带 `"condition": "clue:burrow_xxx"` 的隐藏对话选项/台词就会立刻解锁显示！

---

### 2.7 地穴全部 8 个角色空位现状与待定架构决策

下表列出 `burrow.nbt` 当前 8 个实体对应的精准对话槽位与引擎状态：

| 实体坐标 | 物种 ID | `Character` | 当前角色路由 / `DialogueRole` | 当前实际会打开什么对话 | 后续设计需确认/补齐项 |
|---|---|---|---|---|---|
| `[20.5, 15.0, 32.5]` | `changed_synergy:latex_gray_fox_female` | `1`（首领级） | 无专属 Profile | **目前点对话无 Story Profile！**（因 `NpcDefinitions` 尚无灰狐 `story_npcs/*.json`，`canOpen` 会直接退回或只走默认 `camp_life`） | **需决定**：灰狐做独立首领 Profile，还是双首领合治接入（见下方架构冲突说明） |
| `[30.5, 13.0, 6.5]` | `changed_synergy:latex_manul` | `1`（首领级） | 无专属 Profile（已有 `manul_chief` 标题翻译键） | **同上，尚无 `story_npcs/*.json`** | 同上 |
| `[19.5, 15.0, 33.5]` | `changed:latex_snow_leopard_female` | `0`（居民） | **缺失（空）** | 退化为通用无角色名 `camp_resident`（只有公共营地选项，没有“我是谁/我在干嘛”角色节点） | **需在 `StructureResidentGoal` 为 `"burrow"` 补写坐标 `[19.5, 15.0, 33.5]` 对应的角色名**（例如 `"loft"`），即可在 `story_npcs` 中写入 `resident_loft` 节点！ |
| `[5.5, 8.0, 28.5]` | `changed:latex_snow_leopard_male` | `0`（居民） | `"pond"`（鱼池） | `resident_pond` + `camp_life` | 直接在主 Profile 的 `nodes` 中补齐 `resident_pond`（及子分支 `resident_pond_0` 等） |
| `[20.5, 7.0, 9.5]` | `changed:latex_yuin` | `0`（居民） | `"library"`（阅览角） | `resident_library` + `camp_life` | 补齐 `resident_library` 及子分支 |
| `[21.5, 6.0, 10.5]` | `changed:latex_pink_wyvern` | `0`（居民） | `"dorm"`（宿舍/纸箱室） | `resident_dorm` + `camp_life` | 补齐 `resident_dorm` 及子分支 |
| `[27.5, 9.0, 37.5]` | `changed:latex_blue_wolf` | `0`（居民） | `"workshop"`（熔炼工坊） | `resident_workshop` + `camp_life` | 补齐 `resident_workshop` 及子分支 |
| `[15.5, 12.0, 13.5]` | `changed:latex_yuin` | `0`（居民） | `"farm"`（农田与畜栏） | `resident_farm` + `camp_life` | 补齐 `resident_farm` 及子分支 |

#### ⚠️ 给你的 4 个关键架构提醒（动手设计对话前建议先拍板）
1. **双首领路由机制目前是“单首领假设”**：
   - `StoryNpcService.profile(mob)` 会先查实体自带 `ForgeData.ChangedSynergyStoryNpc.Profile`，查不到才按 `(template, species)` 匹配；但 **`CampLifeService.profile(npc)`**（供所有 `Character=0` 居民找同一个主人 Profile 借用 `resident_*` 节点）使用的是 **`findFirst()`**！
   - 而且 `CampMembershipService.join` 允许分别以哪位首领作为入籍对象，但**只能触发点击的那一位首领物种的变身仪式（`reversible`/`permanent`）**！如果你希望两位首领**各自有独立完整对话树 + 各自的入籍/转形仪式（灰狐变灰狐、兔狲变兔狲）**，同时共用一套 `resident_*` 节点，代码里只要保证两份 `story_npcs/*.json`（或其中主 Profile 挂载 `resident_*`）让 `CampLifeService.profile` 命中那份带 `resident_*` 的 Profile 即可完美共存；唯一要确定的是：**两位首领谁负责带 `resident_*`（或者两份都挂 / 拆开），以及两人的 `camp_dialogue` 语音前缀是否要分开**。
2. **`kitchen` 与 `loft` 的角色表错位**：
   - 目前 `StructureResidentGoal.java` L132-134 里 `"burrow"` 写的是 `{"pond","library","workshop","kitchen","farm","dorm"}`，其中 `"kitchen"` 对应坐标 `(30.5, 13, 6.5)`——这原本是**修改前雌雪豹的位置，现在已经被首领兔狲（`Character=1`）占了**，而新放去阁楼的雌雪豹 `(19.5, 15, 33.5)` 反而不在这个列表里。后续配置代码时，应当把列表中废弃的 `"kitchen"` 换成 `"loft"`（坐标 `{19.5, 15, 33.5}`）。
3. **聚餐选项（`camp_meal`）与营火缺失**：
   - `burrow.nbt` 内当前没有 `minecraft:campfire`，因此 `CampDialogue` 生成的“一起吃点东西（`camp_meal`）”在运行时会因 `mealPoints` 为空返回失败（跳入 `camp_changed`）。设计饮食/聚餐相关选项时需留意是否要加一个营火，或改写 `mealPoints` 对炉灶/吧台的判断。
4. **居民站位与同框关系**：
   - 阁楼处雌雪豹（`[19.5,15,33.5]`）与灰狐首领（`[20.5,15,32.5]`）距离仅 `1.4 格`，且阅览角的小俞音（`[20.5,7,9.5]`）与粉红双足飞龙（`[21.5,6,10.5]`）也几乎肩并肩。这两对角色彼此存在天然的“低头不见抬头见”同室互动条件，设计互相吐槽或顺带提及身旁同伴的台词会非常契合实际站位。
