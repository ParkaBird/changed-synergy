# 设置指南

这份指南覆盖 Changed: Synergy 1.1.4 的全部 **115 项公开服务器配置、16 项客户端配置、14 项世界规则**。
以下列出各项默认值、允许范围和用途。

服务器文件是 `config/changed_synergy-common.toml`；客户端显示文件是
`config/changed_synergy-client.toml`；游戏规则保存于世界数据。

配置页分为“服务器设置”“世界规则”“客户端显示”。多人游戏中，服务器设置显示服务器实际值，
管理员使用 `/changedsynergy config categories`、`list [分类] [页码]`、
`get <键>`、`set <键> <值>`、`reset <键>` 修改。设置键与命令分类不区分大小写。
世界规则可在独立页面查看；有权限的管理员点击“完成”后，通过原生 `/gamerule` 命令应用更改。
客户端显示设置由玩家自己决定，不能覆盖服务器政策。

下方标题是界面分类，并未改动旧 TOML 键的位置。范围和默认值与命令校验使用同一份定义。
详细命令语法见 [SERVER_CONFIGURATION.md](SERVER_CONFIGURATION.md)，世界规则见 [GAMERULES.md](GAMERULES.md)。

营地工作需要同时开启“胶兽生活”世界规则、玩家营地、供给工作；采集、狩猎、农耕、炊事、运输还受各自开关控制。
堆肥属于农耕，同时要求开启农耕。关闭运输不会阻止其他职能收回自己的产物。
改动方块的工作仍遵循 `mobGriefing`；离线工作只发生在已加载区块，不会强制加载。
关闭某项工作后，其已有货物仍可按原链条卸货。

伙伴钓鱼、采矿和洞穴照明需要“羁绊系统”“胶兽生活”世界规则及伙伴工作 AI。
敌意寻路则由 NpcAI 与强化追猎 AI 共同控制，不再连带关闭无关的生活行为。
暂停朋友或羁绊时保留其记忆与营地名单；跟随、战斗和紧急救援使用各自的设置。

初始阵营声望只影响尚未建立的账户。接管类型、时长和最终结果在接管开始时确定，调整配置不会改写已有会话。
关闭接管总开关会安全结束现有接管；关闭信物要求会按原退款流程退还未使用的信物，每份只退一次。
领地 HUD、台词显示等视觉开关不会删除领地或关系数据。原生胶兽出身需要兼容的 TrueTransfur。

角色发言和称呼需要启用的原生身份；表情沿用现有表情规则，玩家好友沿用朋友规则及日常／赠礼收益设置。
本能玩家临时拘束还要求服务器PvP、接管总开关和原生擒抱允许；关闭后会释放正在拘束的人类。
“人类出身可使用本能模式”是行为分类下默认关闭的独立配置。开启选角玩法时，它允许普通兽化的人类出身
使用TT2.0操控模式，不启用永久兽化、致命逆转或原生重生。

队形、关系伤害过滤、单个伙伴的战斗与工作安排属于服务端保存的个人设置，不存在第二套 servercfg 文件。
这些个人命令仍受营地职能、服务器政策与安全规则约束。坐骑配置管理鞍与储物箱，属于装备管理。

个人队形可选紧凑、标准（默认）、松散；伤害过滤可选朋友（默认）、所有胶兽、不启用。
伙伴控制包括跟随／等待、攻击目标类别、击杀／兽化偏好、攻击条件以及钓鱼／采矿安排，
各项选择会为该伙伴保存。营地职能、主要／备用工作、共餐开关、生食和种子保留量按营地保存，
通过营地界面或 `/changedsynergy outpost` 管理。堆肥属于农耕，不是独立职能。
个人或营地安排不能绕过已关闭的服务器功能。


## 服务器设置

### 性能与诊断

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `PERFORMANCE.Diagnostics` | `true` | true / false | 性能诊断【流畅度影响：低】: 统计所有已加载 Changed 胶兽的完整 AI 耗时，并每秒向玩家发送一次很小的状态样本。只有在排查监视器本身时才需要关闭。 |
| `PERFORMANCE.StaggerBackgroundAi` | `true` | true / false | 错峰执行后台 AI【流畅度影响：高】: 把可延后的胶兽扫描分散到不同游戏刻，避免大量胶兽在同一刻集中运算。建议开启。 |
| `PERFORMANCE.BackgroundScanInterval` | `10` | 1 ~ 40 | 后台扫描间隔【流畅度影响：高】: 高开销空闲决策之间的游戏刻数。数值越高越流畅，但环境反应会稍慢。 |
| `PERFORMANCE.DistantAiThrottling` | `true` | true / false | 降低远处 AI 频率【流畅度影响：高】: 附近没有玩家时降低可选 AI 的运行频率。战斗目标和羁绊同伴仍会保持响应。 |
| `PERFORMANCE.DistantAiRange` | `48.0` | 16.0 ~ 128.0 | 活跃 AI 范围【流畅度影响：中】: 任一玩家处于此距离内时，可选 AI 按正常配置间隔运行。减小范围可以降低 CPU 占用。 |
| `PERFORMANCE.DistantAiIntervalMultiplier` | `4` | 1 ~ 12 | 远处间隔倍率【流畅度影响：高】: 对活跃范围外的可选 AI 间隔应用倍率。数值越高，已加载后台区域消耗的 CPU 越少。 |
| `PERFORMANCE.AdaptiveAiBudget` | `true` | true / false | 自适应 AI 预算【流畅度影响：高】: 胶兽 AI 超过每刻软预算后，延后非关键后台决策，并轮换获得执行机会；战斗和羁绊安全不会被延后。 |
| `PERFORMANCE.LatexAiBudgetMs` | `8.0` | 1.0 ~ 40.0 | 胶兽 AI 预算【流畅度影响：高】: 所有已加载 Changed 胶兽每个服务器刻可使用的软预算毫秒数。降低数值会更积极地保护刻速，但无法限制 Changed 必须执行的基础 AI。 |

### AI 功能开关

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `PERFORMANCE.CommunityAi` | `true` | true / false | 聚落活动 AI【流畅度影响：高】: 管理自主社区角色、常规目的地搜索和休息活动。卡顿排查时可关闭，以确认问题是否来自聚落模拟。 |
| `PERFORMANCE.CompanionWorkAi` | `true` | true / false | 羁绊工作 AI【流畅度影响：高】: 控制原版 Changed、Addon 和 Synergy 羁绊伙伴的钓鱼、采矿与洞穴照明。需要开启羁绊系统及胶兽生活世界规则。跟随、战斗、营救和营地生产分别控制。 |
| `PERFORMANCE.AmbientSocialAi` | `true` | true / false | 环境社交扫描【流畅度影响：中】: 管理陌生人接近、环境遭遇及非羁绊朋友的防卫扫描。直接互动、羁绊和同伴安全机制不会关闭。 |
| `PERFORMANCE.EnhancedHuntAi` | `true` | true / false | 强化狩猎 AI【流畅度影响：中】: 管理 Synergy 的视线记忆、搜索、噪声警戒、水下追击和枪械闪避。关闭后 Changed 原本的目标 AI 仍可运行。 |

### 朋友与羁绊成长

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `RELATIONSHIPS.BalancedRelationships` | `true` | true / false | 关系成长平衡: 抚摸和喂食用于初步熟悉，正式结识需要轮盘送礼。关闭后恢复原有成长方式，不删除关系。羁绊信物由独立选项控制。 |
| `RELATIONSHIPS.StrangerAffectionLimit` | `8` | 2 ~ 10 | 陌生好感上限: 正式结识前的好感上限。达到上限后，通过轮盘送出合适礼物来建立关系并获得名字。仅在关系成长平衡开启时生效。 |
| `RELATIONSHIPS.CareAffectionAllowance` | `8` | 0 ~ 60 | 日常互动好感额度: 正式结识后，每次有效轮盘赠礼补满这份额度，供抚摸、喂食、玩耍和共同活动共享。剩余额度不叠加。仅在关系成长平衡开启时生效。 |
| `RELATIONSHIPS.CareAffectionCooldownSeconds` | `60` | 1 ~ 3600 | 日常好感收益冷却（秒）: 抚摸、直接喂食、玩耍和共同活动共享的好感收益冷却。冷却期间治疗和互动动画仍有效，与送礼冷却独立。仅在关系成长平衡开启时生效。 |
| `RELATIONSHIPS.GiftCooldownSeconds` | `300` | 1 ~ 7200 | 赠礼冷却（秒）: 两次有效轮盘赠礼（包括羁绊信物）之间的等待时间。失败或不合适的礼物不消耗物品；直接喂食操作没有冷却。仅在关系成长平衡开启时生效。 |
| `RELATIONSHIPS.BondTokensRequired` | `true` | true / false | 建立羁绊需要信物: 首次建立羁绊需要 60 点好感和该物种分组的信物；复合无需再次赠送。关闭后，保留中的未使用信物只退还一次；关系成长平衡开启时仍需满好感。 |
| `RELATIONSHIPS.BondTokens` | `["dark=changed:dark_latex_crystal_fragment,4","white=minecraft:quartz,1","variant=minecraft:amethyst_shard,1","organic=minecraft:golden_apple,1","aquatic=minecraft:heart_of_the_sea,1"]` | list | 羁绊信物清单: 格式：分组=命名空间:物品,数量（1–64）。分组为 aquatic、organic、dark、white、variant，每组最多一项，各项用分号隔开。无效格式无法保存；未填写的分组或未安装模组的物品使用默认信物。 |

### 关系与伙伴

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `RELATIONSHIPS.TamedCompanionRegeneration` | `0` | 0 ~ 5 | 原生宠物恢复等级: Changed 原生宠物获得的生命恢复等级；设为零则关闭。 |
| `RELATIONSHIPS.OrdinaryTransfurReversal` | `true` | true / false | 普通逆转兽化: 允许通过谈判、羁绊伙伴和睡眠解除兽化。关闭后仍可正常结束接管，并保留 /untf 与 /untransfur 故障恢复指令。 |
| `RELATIONSHIPS.OrganicSleepReversal` | `true` | true / false | 有机兽化睡眠逆转：允许在完整睡过一夜后解除有机兽化。仍需开启“普通逆转兽化”，并遵守永久兽化和原生身份保护。 |
| `RELATIONSHIPS.ProtectiveReleaseRequests` | `true` | true / false | 羁绊保护性滞留: 多次因危险被救援后，谨慎、保护欲强或敏感的羁绊可能需要玩家反复请求才肯释放。每次请求都会关闭轮盘，需要重新打开再问。 |
| `RELATIONSHIPS.BondedEmergencyRescue` | `true` | true / false | 羁绊紧急营救: 允许胶兽羁绊包裹营救、有机羁绊带主人撤离；另行开启后也允许水生朋友营救溺水玩家。关闭后仍可主动包裹或正常协助战斗。 |
| `RELATIONSHIPS.AquaticFriendDrowningRescue` | `true` | true / false | 水生朋友溺水营救: 玩家因溺水实际受到伤害后，允许水生朋友包裹营救；同时需要开启“羁绊紧急营救”。 |
| `RELATIONSHIPS.BondedKinConfinement` | `true` | true / false | 羁绊同族击杀禁闭: 羁绊目睹玩家多次击杀同阵营胶兽后，可对主人实施禁闭。 |
| `RELATIONSHIPS.BondedKinKillBaseLimit` | `3` | 1 ~ 20 | 羁绊同族击杀基础门槛: 触发羁绊禁闭所需的基础目睹击杀次数；好感度越高，容忍次数越多。 |
| `RELATIONSHIPS.ChangedVanillaRelationshipAnimalProtection` | `true` | true / false | Changed Vanilla 关系动物保护: 阻止已建立关系的 Changed Vanilla 胶兽感染动物；高威望的人类农场保护独立生效。 |
| `RELATIONSHIPS.CompanionMonsterAssistOnly` | `false` | true / false | 仅协助对抗怪物: 仅当你攻击敌对怪物或受到其攻击时，朋友与羁绊胶兽才会加入战斗。 |
| `RELATIONSHIPS.SelfDefensePenaltyProtection` | `true` | true / false | 自卫免除关系惩罚: 反击先攻击你的胶兽时，不扣除阵营声望，也不触发旁观者的关系惩罚或羁绊禁闭。主动挑起战斗仍会受到惩罚。 |
| `RELATIONSHIPS.AllowMultipleBonds` | `false` | true / false | 允许多个羁绊: 允许同时与多个胶兽建立羁绊。关闭时仍可自愿兽化，但已有羁绊时不会再建立新羁绊。 |

### 阵营与停战

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `RELATIONSHIPS.FactionPursuit` | `true` | true / false | 阵营追击小队: 声望降至 -100 后，阵营可能派出有预警和数量限制的追击小队。遵守生物生成、和平难度和兽化后停战保护。 |
| `RELATIONSHIPS.FactionPursuitOverworldOnly` | `false` | true / false | 追击小队仅限主世界: 开启后追击小队只会在主世界刷新；默认关闭，允许在所有维度刷新。 |
| `RELATIONSHIPS.IndependentFactionReputation` | `false` | true / false | 阵营声望独立: 开启后，各阵营声望独立变化；与一个阵营交好不会降低对立阵营声望，可以与所有阵营结盟。过去已损失的声望不会自动恢复。 |
| `RELATIONSHIPS.PostTransfurTruceSeconds` | `90` | 0 ~ 600 | 兽化后停战时长: 兽化或接管释放后，各胶兽阵营恢复敌意前的保护秒数；设为 0 可关闭。 |
| `RELATIONSHIPS.PeacefulVillageRelations` | `true` | true / false | 村庄单位和平相处: 阻止胶兽与村民、流浪商人、铁傀儡及兼容的村庄守卫互相攻击。默认开启。 |
| `RELATIONSHIPS.NonRivalCombatProtection` | `true` | true / false | 非宿敌胶兽互斗保护：在生命值降至20%（至少1点）时结束互斗。关闭后允许正常致命伤害；同族友伤防护、羁绊安全线和玩家战斗规则独立生效。 |

### 招呼与社交行为

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `RELATIONSHIPS.NpcAwarenessRange` | `32.0` | 8.0 ~ 96.0 | 关系感知范围: 判断附近玩家与胶兽关系时采用的最大距离。 |
| `RELATIONSHIPS.PatPacification` | `true` | true / false | 抚摸安抚: 允许玩家通过抚摸使胶兽停止追逐并进入短暂停战。关闭后，普通抚摸反应和关系进展仍然生效。 |
| `RELATIONSHIPS.PlayfulFriendTransfur` | `true` | true / false | 调皮朋友的玩闹兽化: 好感不足 28 点的成年调皮朋友偶尔会尝试友好兽化；调皮与有礼貌为互斥的行为特质，不参与台词选择。空手轻打可以中断尝试，并让对方安分五分钟，不损失声望。 |
| `RELATIONSHIPS.PoliteHumanInteraction` | `true` | true / false | 招呼机制: 有礼貌的个体可以先向陌生人打招呼，再决定是否追逐。 |
| `RELATIONSHIPS.PoliteApproachRange` | `12.0` | 4.0 ~ 32.0 | 招呼寻路范围: 有礼貌的个体开始谨慎靠近人类的最远距离。 |
| `RELATIONSHIPS.PoliteApproachSpeed` | `0.42` | 0.05 ~ 1.5 | 招呼寻路速度: 胶兽礼貌靠近人类时使用的寻路速度。 |
| `RELATIONSHIPS.PoliteResponseSeconds` | `14` | 4 ~ 60 | 招呼回应时间: 胶兽等待人类用抚摸回应招呼的秒数。 |
| `RELATIONSHIPS.PoliteUnansweredLimit` | `2` | 1 ~ 6 | 未回应招呼上限: 连续招呼没有得到回应多少次后，胶兽会恢复平常的态度。 |

### 玩家角色扮演

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `PLAYER_ROLEPLAY.SpeechEnabled` | `true` | true / false | 原生玩家角色发言: 原生玩家在角色模式下使用附近胶兽台词显示，仍可切换全局聊天。 |
| `PLAYER_ROLEPLAY.SpeechCooldownSeconds` | `2.0` | 0.5 ~ 10.0 | 玩家发言间隔: 角色消息的发送间隔（秒），与NPC台词冷却独立。 |
| `PLAYER_ROLEPLAY.InstinctConfinement` | `true` | true / false | 本能玩家临时接管: 原生本能玩家在PvP规则允许时，先积累兽化进度，再临时包裹接管敌对人类。释放时恢复人类身份，不触发接管死亡。 |
| `PLAYER_ROLEPLAY.InstinctConfinementSeconds` | `30` | 5 ~ 120 | 初始接管时长: 释放前的秒数。谈判失败增加10秒，单次总时长不超过初始值的1.5倍。 |

### 玩家营地管理

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `COMMUNITIES.PlayerOutposts` | `true` | true / false | 玩家据点【流畅度影响：低】: 允许玩家围绕营火建立营地并管理信任自己的胶兽。需要开启“胶兽生活”世界规则。关闭时暂停营地，保留成员与绑定信息；旧钟营地仍然有效。 |
| `COMMUNITIES.PlayerOutpostResidents` | `16` | 1 ~ 64 | 玩家据点成员上限: 每座营地的成员上限，默认16名，可设为1至64名。保留已有配置和成员。 |
| `COMMUNITIES.PlayerOutpostCrew` | `6` | 1 ~ 32 | 随行队伍人数上限: 同时随队跟随主人的成员上限，默认6名，可设为1至32名。 |
| `COMMUNITIES.PlayerOutpostCrewGathering` | `true` | true / false | 随队采集: 允许随队成员在主人附近挖矿、采集和狩猎。沿用工作搜索间隔与活跃工作者上限，仅在已加载区域作业并遵守mobGriefing。关闭后仍可跟随，保留已收集的物资。与营地供给开关独立；狩猎开关同样控制随队狩猎。 |
| `COMMUNITIES.PlayerOutpostCrewBlockedOres` | `[]` | list | 随队挖矿屏蔽名单: 随队成员不得采集的矿石方块ID，以逗号分隔。例如minecraft:diamond_ore,minecraft:deepslate_diamond_ore。留空允许采集所有可挖的矿石。即时生效，包括正在开采的矿石；不影响营地内的定点采集。 |
| `COMMUNITIES.PlayerOutpostWorkWhileOwnerOffline` | `false` | true / false | 营地主人离线时继续工作: 允许主人离线时继续工作，但只在已经加载的区块内进行，不会强制加载区块。默认关闭。 |
| `COMMUNITIES.PlayerOutpostGuardPvp` | `false` | true / false | 据点 PvP 防卫【流畅度影响：低】: 主人在据点内遭其他玩家攻击时，允许守卫反击。默认关闭。 |

### 营地生产与运输

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `COMMUNITIES.PlayerOutpostSupplyWork` | `true` | true / false | 玩家据点供给工作【流畅度影响：低】: 供给成员按安排工作，并将实际获得的资源送回绑定容器。运输负责收集遗留掉落物，其他成员先送回自己的产物，再开始下一轮工作。 |
| `COMMUNITIES.PlayerOutpostHarvesting` | `true` | true / false | 玩家据点主动采集【流畅度影响：低】: 允许供给者主动采集适合其物种和阵营的资源；同时需要开启“玩家据点供给工作”。 |
| `COMMUNITIES.PlayerOutpostHauling` | `true` | true / false | 营地运输: 运输成员收集划定区域内的各类掉落物，未划区时使用营地周围 32 格，积攒一批后送回容器。需要开启供给工作。拾取屏蔽名单仅影响运输职能。 |
| `COMMUNITIES.PlayerOutpostHaulingBlockedItems` | `[]` | list | 运输拾取屏蔽名单: 运输成员不会拾取名单中的物品，填写带命名空间的物品 ID，以逗号分隔，例如 minecraft:diamond。留空允许全部物品。其他职能仍会收集并送回自己的产物。 |
| `COMMUNITIES.PlayerOutpostHunting` | `true` | true / false | 营地狩猎 [中等性能影响]: 营地猎手在划定猎区工作，随队猎手在主人附近狩猎。保护幼崽、已命名、已驯服和被拴住的动物。营地猎手需要开启营地供给工作，随队猎手需要开启随队采集。 |
| `COMMUNITIES.PlayerOutpostFarming` | `true` | true / false | 营地农耕 [低性能影响]: 收割并补种成熟作物，收集一批后卸货，休息前先送回产物。支持农夫乐事中适用的作物。需要开启供给工作及 mobGriefing。 |
| `COMMUNITIES.PlayerOutpostCooking` | `true` | true / false | 营地篝火烹饪 [低性能影响]: 炊事成员取出真实食材，在营火四个槽位中烤制，并保留生食储备。支持营火配方及食物类熔炉配方。需要开启供给工作及 mobGriefing。 |
| `COMMUNITIES.PlayerOutpostComposting` | `true` | true / false | 营地堆肥 [低性能影响]: 没有成熟作物可收割时，农耕成员使用多余种植材料堆肥，并保留营地的种子储备。需要开启供给工作、农耕及 mobGriefing；堆肥属于农耕，不是独立职能。 |
| `COMMUNITIES.PlayerOutpostWorkScanInterval` | `100` | 40 ~ 1200 | 营地工作搜寻间隔 [性能]: 工作目标搜寻的间隔，单位为刻。数值越高，后台搜寻开销越低，但发现新任务会更慢。 |
| `COMMUNITIES.PlayerOutpostActiveWorkers` | `4` | 1 ~ 32 | 营地同时工作人数 [性能]: 营地供给者与随队采集者分别使用此人数上限，避免营地生产占满名额后随队成员无法工作。降低上限可以减少工作搜寻和寻路开销。 |

### 营地休息与共餐

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `COMMUNITIES.PlayerOutpostRest` | `true` | true / false | 玩家据点夜间休息【流畅度影响：低】: 让营地成员夜间分别使用已绑定的空床。胶液蜜蜂也可使用绑定的黄色蜂巢抱枕。 |
| `COMMUNITIES.PlayerOutpostIdleRest` | `true` | true / false | 营地空闲休息 [低开销]: 空闲成员有概率到绑定的抱枕、办公椅或高纸箱休息；新工作、共餐和行动指令会打断休息。供给者会选择工作区域附近的休息点。 |
| `COMMUNITIES.PlayerOutpostMeals` | `true` | true / false | 营地共餐 [低性能影响]: 成员围坐在营火四周，优先使用绑定的休息点。先吃自己物品栏里的食物，不足时到营地容器领取一份，餐后稍作休息。 |

### 野外群落与据点

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `COMMUNITIES.MinimumOutpostSpacing` | `48` | 16 ~ 256 | 野生据点最小间距: 两个独立野生据点之间允许的最小水平距离（格）。附近相容的胶兽仍会共用已有据点。 |
| `COMMUNITIES.PlayerWildOutpostMinimumSpacing` | `48` | 16 ~ 256 | 玩家与野生据点间距: 新建玩家据点或野生据点时，两者标记与仓库之间的最小水平距离。已有据点保持原位。 |
| `COMMUNITIES.CreatureOutposts` | `true` | true / false | 胶兽自主建立据点: 允许野外胶兽新建或认领据点，包括遗址、蜂巢和水生据点。关闭后保留已有据点、物资及送货行为，不影响玩家营地和设施内已有仓库。 |
| `COMMUNITIES.UseChangedStructureOutposts` | `true` | true / false | 认领 Changed 遗址【流畅度影响：低】: 尚无据点的供给者可以认领已生成的 Changed 遗址，并在空位布置紧凑的社区仓库。设施始终不会被认领。 |
| `COMMUNITIES.LatexBeeHiveOutposts` | `true` | true / false | 胶液蜜蜂蜂巢据点【流畅度影响：低】: 胶液蜜蜂供给者可以在已生成的 Changed 蜂巢旁建立小型社区仓库。 |
| `COMMUNITIES.StructureOutpostSearchRadiusChunks` | `6` | 2 ~ 12 | 建筑搜索半径【流畅度影响：低】: 在已加载区块内搜索未认领遗址和蜂巢的半径。数值越大，偶发搜索的开销越高。 |

### 供给者交易

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `PROVISIONER_TRADING.Enabled` | `true` | true / false | 启用供给者交易: 开启以食物为货币的材料交易、依照社区库存进行的物资兑换，以及达到关系门槛后解锁的收藏品交易。 |
| `PROVISIONER_TRADING.ReserveMultiplier` | `1.0` | 0.25 ~ 4.0 | 社区储备倍率: 调整不参与交易的储备比例。设为 1.0 时，社区会保留每类物资的大约 70%。 |

### 生物生态

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `ECOLOGY.PureWhiteWolfAdaptation` | `true` | true / false | 纯白胶狼地域适应: 纯白胶狼、幼崽、三头犬和血细胞胶狼远离白胶领地后显现眼睛，返回后隐藏眼睛。适用于胶兽和玩家（包括本能模式），不改变物种、身体或能力。 |

### 开局物种名单

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `BEHAVIOUR.IncludeModdedOriginSpecies` | `false` | true / false | 出身选择包含其他模组物种: 将其他已安装模组提供的可兽化物种加入出身选择，默认关闭。Boss、已排除的生物、局部转化、Custom 容器、benign 形态和特殊角色仍不可选。是否启用出身选择，仍由对应的游戏规则控制。 |
| `BEHAVIOUR.AllowHumanOriginInstinct` | `false` | true / false | 人类出身可使用本能模式: 开局身份选择开启时，允许已兽化的人类出身玩家使用TrueTransfur 2.0本能模式。默认关闭；不启用永久兽化、致命逆转或原生重生机制。 |
| `BEHAVIOUR.DisabledOriginForms` | `[]` | list | 禁用的初始形态: 填写不允许选择的完整兽化形态 ID，用逗号分隔，例如 changed:form_dark_latex_wolf/male；留空则不额外禁用。对原模组和其他模组的形态均生效，不影响已确认的出身。服务器请修改 changed_synergy-common.toml 中的 BEHAVIOUR.DisabledOriginForms，或使用服务器配置指令。 |

### 感知与追逐

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `BEHAVIOUR.VisualAcquisitionRange` | `18.0` | 6.0 ~ 48.0 | 初次发现距离: 胶兽初次发现玩家的基础距离；仍需满足视线和视野角度。 |
| `BEHAVIOUR.MaximumPursuitRange` | `28.0` | 8.0 ~ 64.0 | 最大追逐距离: 胶兽继续追逐已确认目标时采用的基础距离。 |
| `BEHAVIOUR.LoseSightSeconds` | `2` | 1 ~ 30 | 丢失视野延迟: 失去视野多少秒后，追逐者会前往最后目击位置搜索。 |
| `BEHAVIOUR.SearchSeconds` | `8` | 2 ~ 120 | 搜索持续时间: 胶兽调查玩家最后目击或发声位置的秒数。 |
| `BEHAVIOUR.AlertRadius` | `12.0` | 0.0 ~ 96.0 | 共享警觉范围: 胶兽把确认的目标位置告知附近相容个体的距离。 |
| `BEHAVIOUR.SearchSpeed` | `0.45` | 0.05 ~ 3.0 | 搜索速度: 胶兽调查或搜索目标位置时使用的寻路速度。 |
| `BEHAVIOUR.FirearmGunshotRadius` | `48.0` | 0.0 ~ 128.0 | 枪声备用范围: 供无法提供枪声范围的枪械兼容使用；TACZ 会提供自己的数值。 |
| `BEHAVIOUR.FirearmEvasion` | `true` | true / false | 躲避枪械: 敌对胶兽追逐持枪玩家时可以偶尔横向闪避。 |

### 兽化方式与擒抱

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `BEHAVIOUR.HostileGrabAttemptChance` | `0.65` | 0.0 ~ 1.0 | 敌对擒抱 QTE 概率: 符合条件的敌对擒抱触发挣脱 QTE 的概率。 |
| `BEHAVIOUR.OrganicHostileGrabAttemptChance` | `0.85` | 0.0 ~ 1.0 | 有机擒抱 QTE 概率: 有机胶兽的独立擒抱概率；它们主要通过身体接触完成同化。 |
| `BEHAVIOUR.AllowMindlessMobTransfur` | `false` | true / false | 允许兽化无意识生物: 允许胶兽完整兽化僵尸、骷髅等无意识生物。默认关闭，此时它们只会被吞噬，以减少新增的持久实体数量；受支持附属模组提供的专用转换形态不会被覆盖。 |
| `BEHAVIOUR.AllowSecondaryTransfur` | `true` | true / false | 对已兽化玩家二次兽化: 允许敌对胶兽擒抱并重新兽化已兽化的玩家。关闭后，它们会改用普通攻击。原生胶兽玩家始终不受二次兽化影响。 |
| `BEHAVIOUR.CreatureTransfurMethod` | `"NATIVE"` | NATIVE, RANDOM, ASSIMILATION, ABSORPTION, TAKEOVER | 胶兽兽化方式: 选择敌对胶兽攻击和擒抱时的兽化方式：原有方式、每次遭遇随机、同化、吸收或接管。接管沿用正常的阵营声望结局，且需启用接管机制；不支持的物种、有机胶兽、防护状态和特殊融合仍按原有方式处理。 |

### 身体控制权

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `TAKEOVER.Enabled` | `true` | true / false | 吞噬接管机制: 允许符合条件的吞噬和外骨骼强制穿戴暂时接管玩家的身体。关闭后，主动攻击胶兽引发的吞噬会恢复为遭到攻击后的反击谈判。 |
| `TAKEOVER.PunitiveCapture` | `true` | true / false | 攻击行为引发接管: 允许胶兽在玩家攻击它或挑起与其阵营的冲突后接管玩家。 |
| `TAKEOVER.CompetitiveCapture` | `true` | true / false | 普通接管: 允许拥有好胜或爱表现性格的胶兽在吞噬后接管玩家。 |
| `TAKEOVER.ForcedExoskeleton` | `true` | true / false | 外骨骼接管机制: 允许外骨骼在符合条件时接管束缚狼或虎鲸形态玩家的行动。 |
| `TAKEOVER.ExoskeletonSleep` | `true` | true / false | 外骨骼昏睡机制: 外骨骼接管达到时限时使玩家昏睡并脱离外骨骼；关闭后，届时只会交还身体控制。 |
| `TAKEOVER.AllowBorrowedControl` | `true` | true / false | 暂时取回控制权: 允许普通接管中的玩家申请一小段时间的移动控制权。 |
| `TAKEOVER.AllowEscapeAttempt` | `true` | true / false | 挣脱机会: 允许玩家在普通接管中确认并进行一次挣脱尝试。外骨骼接管绝不适用。 |
| `TAKEOVER.ProneAfterFailedEscape` | `true` | true / false | 挣脱失败后醒来的倒地阶段: 挣脱接管失败而昏睡后，醒来时短暂保持倒地姿态。默认开启。 |
| `TAKEOVER.AllowNegotiation` | `true` | true / false | 接管谈判: 允许普通接管期间进行一次谈判。惩罚性接管谈判失败会缩短剩余时间。 |
| `TAKEOVER.HostileFinale` | `true` | true / false | 严重冲突接管结局: 玩家攻击后，戒备声望触发彻底接管，敌视声望触发意识消散；关闭后两者均以临时禁闭结束。仅影响新开始的接管。 |
| `TAKEOVER.NonHostileOrangeCompensation` | `true` | true / false | 友好接管橘子补偿: 性格引发的友好接管结束后提供三个橘子，每名玩家至多每 20 分钟一次。 |
| `TAKEOVER.OrdinarySeconds` | `180` | 30 ~ 300 | 普通接管时长: 非惩戒普通接管持续的秒数。 |
| `TAKEOVER.PunitiveSeconds` | `240` | 30 ~ 300 | 攻击引发接管时长: 攻击行为引发的禁闭、直接与惩罚接管的持续秒数。 |
| `TAKEOVER.ExoskeletonAwakeSeconds` | `30` | 5 ~ 60 | 外骨骼清醒时长: 外骨骼强制接管到达所配置结束方式前持续的秒数。 |

### 台词频率

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `DIALOGUE.Range` | `32.0` | 4.0 ~ 96.0 | 台词接收范围: 玩家能够收到胶兽台词的最大距离。 |
| `DIALOGUE.CooldownSeconds` | `12` | 2 ~ 120 | 台词冷却: 同一胶兽再次对同一玩家说话前的最短间隔秒数。 |
| `DIALOGUE.Chance` | `0.72` | 0.0 ~ 1.0 | 台词触发概率: 符合条件的状态变化触发台词的概率。 |
| `DIALOGUE.PersonalityLineChance` | `0.68` | 0.0 ~ 1.0 | 性格台词概率: 合适事件采用该个体显性性格专属台词的概率。 |

### 语言理解与叫声

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `DIALOGUE.UsesTranslator` | `true` | true / false | Addon 翻译器兼容: 启用的 Changed Addon Plus 翻译器可以翻译胶兽台词，即使关闭了心灵感应。关闭此项会停用翻译器的绕过功能，不会让所有玩家直接听懂台词。 |
| `DIALOGUE.AddonAnimalCalls` | `true` | true / false | Addon 动物叫声: 让有对应叫声的胶兽在部分情绪反应中使用 Addon 的动物叫声。 |
| `DIALOGUE.TelepathyEnabled` | `true` | true / false | 启用心灵感应: 允许通过已解锁的心灵感应、兽化形态、催眠和接管听懂台词。关闭后暂停解锁进度，改用物种叫声，但保留原有进度。受支持且已启用的翻译器仍可翻译；催眠和接管玩法照常生效。 |
| `DIALOGUE.TelepathyUnlockTransfurs` | `3` | 0 ~ 20 | 心灵感应解锁次数: 永久解锁心灵感应所需的完整兽化次数。设为 0 只会阻止新解锁，已解锁的能力仍然有效。如需连同已有能力一起关闭，请关闭“启用心灵感应”。 |

### Changed Addon Plus 兼容

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `RELATIONSHIPS.RespectPacifiedLatexes` | `true` | true / false | 遵守安抚效果: 带有 Changed Addon Plus“pacified”状态且未持械的胶兽不会主动追逐或攻击玩家 |
| `RELATIONSHIPS.PacifyTamedCompanions` | `true` | true / false | 持续安抚原生宠物: 持续为 Changed 原生宠物刷新“pacified”状态，避免其错误锁定目标。需要安装 Changed Addon Plus。 |

## 客户端设置

### 兽化反馈

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `LEGACY TRANSFUR VISUALS.LegacyTransfurScreenEffect` | `false` | true / false | 兽化进度画面效果: 可选：显示随兽化进度从屏幕边缘向内扩张的主题色遮罩。关闭时保留 Changed 现行显示方式。 |
| `LEGACY TRANSFUR VISUALS.LegacyTransfurSkinEffect` | `false` | true / false | 兽化进度皮肤覆盖: 可选：在部分兽化的皮肤和第一人称手臂上显示分阶段胶液覆盖。关闭时保留 Changed 现行显示方式。 |
| `LEGACY TRANSFUR VISUALS.LegacyTransfurScreenOpacity` | `1.0` | 0.0 ~ 1.0 | 兽化画面不透明度: 兽化进度画面效果的最大不透明度。 |

### 纯白胶视觉

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `PURE WHITE VISION.DisablePureWhiteVisionOverlays` | `false` | true / false | 移除纯白胶视觉遮罩: 移除视野受限白色遮罩和 Synergy 的纯白胶共识屏幕效果。目标轮廓及相关玩法机制仍然保留。 |

### 友好包裹

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `FRIENDLY SUIT VISUALS.FriendlySuitVignette` | `true` | true / false | 友好包裹视觉遮罩: 被宠物或羁绊胶兽包裹时，在屏幕边缘显示柔和的主题色效果。 |
| `FRIENDLY SUIT VISUALS.FriendlySuitVignetteOpacity` | `0.25` | 0.0 ~ 0.5 | 包裹遮罩不透明度: 友好包裹视觉遮罩的不透明度。 |

### 心灵感应与领地 HUD

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `TELEPATHY.TelepathicDanmaku` | `true` | true / false | 屏幕台词显示: 将普通胶兽台词显示在屏幕上；关闭后改为发送到聊天栏。 |
| `TELEPATHY.PopupHeightOffset` | `0.0` | -0.75 ~ 0.75 | 台词弹窗高度偏移: 按屏幕高度比例调整。负数上移，正数下移，0 保持原位置。 |
| `TELEPATHY.DanmakuHeightOffset` | `0.0` | -0.75 ~ 0.75 | 横向弹幕高度偏移: 独立调整横向弹幕的位置。负数上移，正数下移，0 保持原位置。 |
| `TELEPATHY.DialogueDisplayMode` | `"AUTO"` | AUTO, POPUP, DANMAKU | 台词展示方式: 选择随机弹窗或横向弹幕；自动模式会为英文使用弹窗，为中日韩语言使用弹幕。 |
| `TELEPATHY.TerritoryHud` | `true` | true / false | 领地 HUD: 显示群系进入字幕和设施区域常驻信息。 |

### 机制提示

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `TUTORIAL HINTS.MechanicHints` | `true` | true / false | 机制提示: 为 Synergy 的重要互动显示一次性的提示。 |
| `TUTORIAL HINTS.NegotiationNextStepHint` | `true` | true / false | 谈判下一步提示: 在谈判中显示下一步建议。建议有时会判断错误；投喂后准确率会大幅提高。 |

### 对抗显示

| 设置键 | 默认值 | 范围／选项 | 作用 |
| --- | --- | --- | --- |
| `QTE VISUALS.QteAnimations` | `true` | true / false | 对抗界面动效: 启用擒抱 QTE 与催眠视线对抗的动态界面。 |
| `QTE VISUALS.ReducedQteMotion` | `false` | true / false | 降低对抗动态效果: 保留淡入淡出与输入反馈，但关闭震动、漂移和大幅缩放。 |
| `QTE VISUALS.ExoskeletonHypnosisVisual` | `true` | true / false | 外骨骼催眠视觉: 佩戴外骨骼时在本机显示催眠特效和状态日志。 |

## 世界规则

| 规则 | 默认值 | 作用 |
| --- | --- | --- |
| `changedSynergyNativeOrigins` | `true` | 开局身份选择: 新玩家可选择人类或原版Changed物种出身，原生出身需要TrueTransfur。开启时，永久身体规则只对原生玩家生效；人类本能操控可由独立配置开启。关闭后恢复TT默认适用范围，保留出身记录。 |
| `changedSynergyNativePvPReputation` | `true` | 攻击原生玩家影响声望: 成功攻击或击杀原生阵营成员玩家会影响攻击者的声望，近期自卫反击除外。竞技场或约定切磋可关闭此规则。 |
| `changedSynergyNpcAI` | `true` | 胶兽仇恨寻路改进: 启用 Synergy 的敌意寻路、视野记忆、调查和追击。还需开启服务器设置中的强化追猎 AI。胶兽生活、营地工作和关系安全分别控制。 |
| `changedSynergyNpcDialogue` | `true` | 胶兽台词: 允许生物在社交互动和 AI 状态变化时向附近玩家发送情境台词。 |
| `changedSynergyNpcEmotes` | `true` | 胶兽情绪气泡: 允许生物显示与 /emote 指令相同的情绪气泡。 |
| `changedSynergyFactionReputation` | `true` | 阵营声望: 启用阵营声望的增减、态度变化和对应收益。 |
| `changedSynergyInitialFactionReputation` | `0` | 初始阵营声望: 设置玩家首次接触尚未初始化的阵营记录时获得的声望（-100 至 100）；不会覆盖已有声望。 |
| `changedSynergyBondSystem` | `true` | Changed：协同 羁绊系统: 启用羁绊建立、伙伴跟随、营救、包裹及伙伴管理。关闭时暂停羁绊营地成员，保留已有羁绊与职能。 |
| `changedSynergyFriendshipSystem` | `true` | Changed：协同 朋友关系: 启用好感、朋友关系、朋友跟随与协助。关闭时暂停朋友营地成员，保留其记忆与职能。 |
| `changedSynergyTerritoryDisplay` | `true` | 领地信息显示: 在 HUD 中显示领地与设施区域信息；不会更改世界生成和群系刷怪。 |
| `changedSynergyPersonalitySystem` | `true` | Changed：协同 个体性格: 让稳定性格影响 AI、容忍度、社交习惯和台词选择。 |
| `changedSynergyCreatureLife` | `true` | 胶兽生活系统: 启用胶兽日常、野外群落、采集、玩家营地和纯白胶融入分出。关闭时暂停这些活动，保留营地成员。各 AI 与工作开关仍然生效；放置或采收方块遵循 mobGriefing。 |
| `changedSynergyHypnosisQte` | `true` | Changed 催眠视线对抗: 启用催眠视线抵抗、操作锁定和迷离惩罚。关闭后仍保留 Changed 原有催眠效果。 |
| `changedSynergyGrabQteEnhancements` | `true` | Changed 擒抱QTE增强: 启用服务端同步键位和挣脱后的安全僵直。关闭后仍保留 Changed 原有擒抱，挣脱后立即恢复攻击。 |
