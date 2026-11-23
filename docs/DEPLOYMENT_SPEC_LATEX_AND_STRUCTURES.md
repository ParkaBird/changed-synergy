# Changed: Synergy 新胶兽与新结构/社区部署规范 (1.20.1 / Changed 0.15.7)

> 本文档整理自 `D:/changed-synergy-1.20.1` 现有注册链路、标签体系、世界生成结构与 NPC 社区系统（以 `LatexGrizzly`、`Lush Cabin`、`Point Prometheus` 为现成参考实现）。部署新内容时按清单逐项补齐并运行发布校验脚本即可。

---

## 第一部分：如何部署一个新胶兽（Entity + TransfurVariant）

假设新增物种 ID 为 `<id>`（例如 `latex_manul` / `latex_grizzly`），变体 ID 为 `form_<id>`。

### Step 1：实体类与数值属性
1. **新建实体类**：`src/main/java/net/parkabird/changedsynergy/entity/<ClassName>.java`
   - 继承 `ChangedEntity`（或对应的 Changed 基类，如四足/水生/飞行父类）。
   - 重写 `protected void setAttributes(AttributeMap attributes)`：调用 `super.setAttributes(attributes)` 后设置 `MAX_HEALTH`、`MOVEMENT_SPEED`、`ATTACK_DAMAGE`、`KNOCKBACK_RESISTANCE`、`ForgeMod.SWIM_SPEED.get()`、`ChangedAttributes.AIR_CAPACITY.get()`（参考 [LatexGrizzly.java](file:///d:/changed-synergy-1.20.1/src/main/java/net/parkabird/changedsynergy/entity/LatexGrizzly.java)）。
   - 重写 `getTransfurMode()`（如 `TransfurMode.REPLICATION` / `TransfurMode.ABSORPTION` / `TransfurMode.NONE`）和 `getTransfurColor(TransfurCause cause)`。
   - 如有自定义生成条件（如洞穴、水中），在类中提供静态 `checkXxxSpawnRules(...)`（参考 `LatexSpidtaur`、`LatexSwordfish`）。

### Step 2：实体、变体、刷怪蛋与创造栏注册
2. **注册 `EntityType`**：`src/main/java/net/parkabird/changedsynergy/init/ChangedSynergyEntities.java`
   - 在 `REGISTRY.register("<id>", ...)` 注册实体类型（定义碰撞箱 `.sized(width, height)` 与 `.clientTrackingRange(10)`）。
   - 在 `registerAttributes(EntityAttributeCreationEvent)` 中添加 `event.put(..., ChangedEntity.createLatexAttributes().build());`（注：实例属性由实体的 `setAttributes` 覆盖）。
   - 在 `registerSpawnPlacements(SpawnPlacementRegisterEvent)` 中注册自然生成高度图与 `SpawnPlacements.Type`（`ON_GROUND` 或 `IN_WATER`）。
   - 在 `registerColors(FMLCommonSetupEvent)` 中调用 `ChangedEntities.registerEntityColor(<ENTITY>.getId(), primaryColor, secondaryColor)`（用于 Changed 注射器/药瓶配色）。
   - 在 `addSpawnEgg(BuildCreativeModeTabContentsEvent)` 中将刷怪蛋加入 `CreativeModeTabs.SPAWN_EGGS`，并将变体 ID 加入 `ChangedTabs.TAB_CHANGED_ITEMS` 循环以生成对应纯种注射器/药水瓶/药箭。
3. **注册 `TransfurVariant`**：`src/main/java/net/parkabird/changedsynergy/init/ChangedSynergyTransfurVariants.java`
   - 通过 `REGISTRY.register("form_<id>", () -> TransfurVariant.Builder.<EntityClass>of(ChangedSynergyEntities.<ENTITY>)...build())` 注册变体，按需配置 `.quadrupedal()`、`.extraJumps(...)`、`.extraHands()`、`.gills()`、`.nightVision()`、`.addAbility(...)`、`.transfurMode(...)`、`.scares(...)`。
   - 若有性别/成对变体（如 `muntjac`、`spotted_hyena`），在 `ChangedSynergyMod.onCommonSetup` 中调用 `ChangedTransfurVariants.Gendered.registerPair(...)`。
   - 若新技能涉及自定义能力，先在 `ChangedSynergyAbilities.java` 注册并通过 `ChangedSynergyMod` 确认绑定。
4. **注册刷怪蛋物品**：`src/main/java/net/parkabird/changedsynergy/init/ChangedSynergyItems.java`
   - 注册 `<ID>_SPAWN_EGG`（`ForgeSpawnEggItem`），并添加物品模型：
     `src/main/resources/assets/changed_synergy/models/item/<id>_spawn_egg.json`（通常使用 `minecraft:item/template_spawn_egg`）。

### Step 3：模型、渲染器、盔甲与配件槽适配
5. **客户端模型与贴图**：
   - 模型类：`src/main/java/net/parkabird/changedsynergy/client/model/<ClassName>Model.java`（含 `LAYER_LOCATION` 与 `createBodyLayer()`）。
   - 贴图：`src/main/resources/assets/changed_synergy/textures/<id>.png`（若有部分人形或特殊鞍具/发光层，补齐对应贴图）。
   - 盔甲层（如 `<Species>Armor`）需随体型同步注册，避免下蹲/四足/小体型穿原版甲穿模。
6. **渲染器注册**：
   - 新建渲染器 `src/main/java/net/parkabird/changedsynergy/client/<ClassName>Renderer.java`（一般继承 `AdvancedHumanoidRenderer`，按需添加 `LatexParticlesLayer`、`TransfurCapeLayer`、`CustomEyesLayer`、`GasMaskLayer` 等）。
   - 在 `src/main/java/net/parkabird/changedsynergy/client/ChangedSynergyEntityRenderers.java` 的 `registerRenderers` 与 `registerLayers` 中同时注册渲染器及所有 `ModelLayerDefinition`（含对应的 `Armor` 集合层）。
7. **Native 服装/配件槽声明与模组服装剪裁**：
   - 新增：`src/main/resources/data/changed_synergy/accessories/entities/<id>.json`（声明允许穿戴的 `changed:body`, `changed:hands`, `changed:head`, `changed:face`, `changed:full_body`, `changed:legs`, `changed:neck` 等槽位）。
   - 若属于特殊体型（如矮小/四足/半同化/多臂），检查并更新模组自带服装剪裁与适配类（如 `HyenaTrailclothItem`、`DesertOutfitRenderer`、`CaveMinerOutfitRenderer`、`FieldCoatRenderer`、`ForestCanopyRenderer` 等的 `fits`/`cut` 判定）。

### Step 4：Changed 原版与 Synergy 标签归类（决定 AI、社交、同化行为）
8. **Changed 核心标签**（`src/main/resources/data/changed/tags/entity_types/`）：
   - `latexes.json`：绝大多数胶兽必须加入（影响 Changed 判定与 Synergy 多处基础过滤）。
   - `partial_latexes.json`：仅部分同化形态加入。
   - `can_wear_exoskeleton.json`：允许穿戴外骨骼的形态加入。
9. **Synergy 阵营与社交标签**（`src/main/resources/data/changed_synergy/tags/entity_types/`）：
   - **阵营（四选一，决定聚落归属与声望）**：`white_faction.json`、`dark_faction.json`、`aquatic_faction.json`、`light_faction.json`（若 `HunterFaction.of(entity)` 识别为 `ORGANIC_LATEX` 且未进任何标签，将默认归为 `LIGHT`）。
   - **AI/狩猎细分原型**：按需加入 `feline_hunters.json`、`canine_hunters.json`、`avian_hunters.json`、`aquatic_hunters.json`、`critter_hunters.json`、`draconic_hunters.json`、`insect_hunters.json`、`royal_hunters.json`、`soldier_hunters.json`；不希望参战或非典型社交可查 `hunt_ai_excluded.json`、`emoteless_hunters.json`、`silent_hunters.json`。
   - **社交能力**：默认全部胶兽都是 `FULL`（`CreatureSocialProfile`）；若是仅可协商不可交友的敌对型加入 `aggressive_social.json`；幼崽加入 `juvenile_social.json`；彻底退出 Synergy 社交与对话系统的加入 `social_interaction_excluded.json`。
   - **同化/吸收分类**：`sentient_assimilation_targets.json` 或 `mindless_absorption_targets.json`；具备直接说话能力或特殊能力加入 `direct_speakers.json`、`hypnotic_creatures.json`、`companion_seats.json`。

### Step 5：自然生成、掉落、配方与多语言
10. **自然生成**：
    - `src/main/resources/data/changed_synergy/tags/worldgen/biome/<id>_spawns.json`（目标群系）。
    - `src/main/resources/data/changed_synergy/forge/biome_modifier/<id>_spawns.json`（`forge:add_spawns` 权重、minCount、maxCount）。
11. **掉落物与合成配方**：
    - `src/main/resources/data/changed_synergy/loot_tables/entities/<id>.json`。
    - `src/main/resources/data/changed_synergy/recipes/form_<id>.json`（注射器/变体配方，如需要玩家合成获取）。
12. **多语言键值（必须双语同步，否则 `validate_release.py` 报错）**：
    - `src/main/resources/assets/changed_synergy/lang/en_us.json` 与 `zh_cn.json`（以及如有需要更新 `ru_ru.json`）：
      - `entity.changed_synergy.<id>`
      - `item.changed_synergy.<id>_spawn_egg`
      - 任何新增技能名/描述、声望地域归属或特殊对话键。

---

## 第二部分：如何部署一个新结构或内置社区（Structure / Story Camp）

假设新结构 ID 为 `<template>`（例如 `manul_den`），若含带剧情对话的内置社区居民与首领则额外标记「社区链路」。

### Step 1：导出与核对 `.nbt` 结构模板
1. **存放路径**：`src/main/resources/data/changed_synergy/structures/<template>.nbt`（若需兼容 Farmer's Delight 可选变体，参照 `lush_cabin_fd.nbt` 并通过 `farmersdelight_template` 指定）。
2. **结构内预置实体 NBT 要求（`StructureResidentGoal` 解析锚点）**：
   - 结构内的常驻居民或剧情 NPC 需在其 `ForgeData.ChangedSynergyStructureResident` 下携带：
     - `Template` (`String`): `"<template>"`（或 `"changed_synergy:<template>"`，建议与 `ResearchLabStructure` 保持一致短名）
     - `LocalPos` (`ListTag` of 3 `Double`): 实体在模板局部坐标中的相对位置
     - `Size` (`IntArray` / `ListTag` of 3 `Int`): 整个结构边界尺寸 `[sx, sy, sz]`
     - `Yaw` (`Float`): 导出时实体朝向
     - `Camp` (`Byte`/`Boolean`): 是否作为营地/社区成员活动
     - `Character` (`Byte`/`Boolean`): 剧情固定 NPC（首席/独立角色）设为 `1b`
     - `ReserveOnly` (`Byte`/`Boolean`): 是否允许公开储备统计（非大型主社区也统计储备时设为 `1b`）
     - `DialogueRole` (`String`, 可选): 指定具体居民分支对话节点前缀（会映射到 `resident_<DialogueRole>`）
     - `Outfit` / `NeckOutfit` / `OutfitChance` (可选): 出生自动穿戴衣物 ID 与概率
   - **随机眼睛初始化**：若不希望结构内所有个体使用固定默认青瞳，可附带 `ForgeData.ChangedSynergyRandomStructureEyes = 1b`。
3. **结构内箱子归属标记**：
   - 由 `StructureStorageOwnership` 根据结构 `.nbt` 模板中的方块实体 NBT 自动恢复归属；作者导出的公用储备箱/私人箱需带有相应 `ChangedSynergyPublicStorage` / `ChangedSynergyPrivate`（参考现有社区结构模板导出脚本）。

### Step 2：Worldgen 数据包定义
4. **Structure JSON**：`src/main/resources/data/changed_synergy/worldgen/structure/<template>.json`
   使用 `"type": "changed_synergy:research_lab"`，支持字段（定义见 [ResearchLabStructure.java](file:///d:/changed-synergy-1.20.1/src/main/java/net/parkabird/changedsynergy/world/ResearchLabStructure.java) L53-L63）：
   - `biomes`: `"#changed_synergy:has_structure/<biomes_tag>"`
   - `step`: `"surface_structures"` 或地下 `"underground_structures"`
   - `terrain_adaptation`: `"none"` / `"beard_thin"` / `"bury"` 等
   - `template`: `"changed_synergy:<template>"`
   - 可选：`farmersdelight_template`, `required_mod`, `waterline` (`-1..64`), `ground_level` (`0..64`), `max_terrain_slope` (`0..16`), `clearing_radius` (`0..8`), `underground` (`true/false`)
5. **Biome Tag**：`src/main/resources/data/changed_synergy/tags/worldgen/biome/has_structure/<biomes_tag>.json`。
6. **Structure Set**：`src/main/resources/data/changed_synergy/worldgen/structure_set/<set_name>.json`（控制 `spacing`、`separation`、`salt` 与分布策略）。
7. **特殊生成规则**：若该结构需要特定地表嵌雪、地下空腔剔除、自定义偏移或特殊方阵规则，需同步修改 `ResearchLabStructure.java`（参考 `point_prometheus` 或 `desert_refuge` 分支）。

### Step 3：如果是大型内置社区 / 带剧情 NPC，需接入以下 Java 注册表
若只是普通野外无语音设施/微营地（或测试结构），可跳过；若需成为拥有**公共请求、加入营地、本地声望、对话分支与复兴/重生保护**的正式社区，必须逐一核对下列 Java 开关/列表：
8. **`CampLifeService.java`**：
   - `mainCamp(ChangedEntity)`: 把 `"<template>"` 加入主社区集合（`Set.of(...)`）。
   - `voice(ChangedEntity)`: 为该社区首领映射语音前缀。
   - `register(ChangedEntity)`: 加入公用仓库布局白名单。
   - `requests(ChangedEntity)`: 映射该社区每日公开任务需求的物资类型（`FOOD`/`WOOL`/`LEATHER`/`LIGHT`/`PAPER`）。
9. **`CampDialogue.java`**：
   - `node(...)`: 在 `residentVoice` switch 中为 `"<template>"` 映射居民口吻前缀，并确认相关对话分支触发条件。
10. **`StructureResidentGoal.java` & `StructureStorageOwnership.java`**：
    - 在 `StructureResidentGoal.installCore` 为该模板的普通居民补充 `assignDefaultRole(data, roles, localPositions)` 及默认 `Outfit`；
    - 在 `StructureStorageOwnership.owns(...)` 的硬编码模板白名单中决定是否纳入模板坐标重解析校验；
    - 如涉及加入/离开营地、安全区保护或首领对话转形，检查 `CampMembershipService.java` 与 `NaturalCampService.java`。

### Step 4：剧情对话 JSON 与多语言文本
11. **Story NPC 定义**：`src/main/resources/data/changed_synergy/story_npcs/<npc_id>.json`
    - 填写 `"template": "<template>"`, `"species": "<entity_id>"`, `"trait"`, `"chief": true/false`, `"equipment": [...]`, `"nodes": { "root": ... }`。
    - 每条对话 `parts` 的 `emotion` 与 `effect` 必须在 `NpcTextStyle` 校验白名单内，`pause` 范围 `0..2000`，`intensity` 为 `0..2` 的有限浮点。
12. **营地公共台词**：`src/main/resources/data/changed_synergy/camp_dialogue/*.json`（补齐对应 `voice` 的 `.greeting` / `.food_*` / `.requests` / `.thanks` 等键值）。
13. **探索者地图与战利品表（可选）**：
    - 若结构需可通过箱子内的探索者地图定位：更新 `world/NearbyObservatoryMapFunction.java` 目标映射与翻译键，并在 `event/LandmarkMapLoot.java` 注册生成池（参照 `lush_cabin` 与 `point_prometheus`）；结构内箱子掉落表放在 `data/changed_synergy/loot_tables/chests/<name>.json`。
14. **语言文件同步**：`en_us.json` & `zh_cn.json`（及 `ru_ru.json` 如有）。**注意**：新增所有 NPC 台词键、`container.changed_synergy.*`、地图名称 `filled_map.*` 必须在 `en_us.json` 与 `zh_cn.json` 一一对应。

---

## 第三部分：发版前固定自检流水线

每次新增胶兽或结构后，在 `D:/changed-synergy-1.20.1` 执行：

```powershell
# 1. 设置 JDK 17 并运行编译、单测、重混淆打包
$env:JAVA_HOME = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-beta'
.\gradlew.bat test build --no-daemon

# 2. 运行源码、双语键完整性、JSON 语法与 JAR 类引用自检
python tools/validate_release.py --jar .\build\libs\ChangedSynergy-m1.20.1-v<version>-all.jar
```

通过 `0 error(s)` 后，再导入 `D:/Program Files/.minecraft/versions/测试专用/mods/` 进游戏实机验证 `/locate structure`、`/summon`、注射器变身、服装穿戴及 NPC 对话交互。
