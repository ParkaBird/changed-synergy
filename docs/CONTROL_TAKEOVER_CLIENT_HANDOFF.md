# 接管客户端与网络包交接（2026-09-04）

首轮仅新增文件；随后按用户要求完成网络、8 个 client mixin 和中英文 lang 注册。保留其他并行更改，未改游戏版本、未打包、未提交。

## 主线程必须接入

1. 已注册 network.TakeoverStatePacket 为 ID 20 / PLAY_TO_CLIENT，network.TakeoverActionPacket 为 ID 21 / PLAY_TO_SERVER。
   两者均提供 static encode / decode / handle，handler 自行 enqueueWork。
   注册前已检查 20/21 空闲；当前 PROTOCOL 为 29，保留双向严格版本匹配。
2. State 最终构造：
   `TakeoverStatePacket(UUID sessionId, int kind, int phase, int carrierId, int playerId, int releaseRemainingTicks, int releaseTotalTicks, int borrowCooldownTicks, boolean escapeUsed, int expectedKey, int sequence, int progress, int qteLength, float fade, String carrierName)`。
   **playerId 紧跟 carrierId**。整型均 VarInt，布尔、float、UUID 按对应类型，名字 UTF 最大 256 字符。
3. 服务端必须实现 `ai.TakeoverService.handleAction(ServerPlayer, UUID, int, int, int)`。
   Action 构造保持 `TakeoverActionPacket(UUID sessionId, int action, int key, int sequence)`。
   action 0 申请、1 确认开始、2 QTE、3 归还。非 QTE 的 key 为 -1。
4. 下列新增 mixin 已经**只注册到 client 数组**：
   - TakeoverMovementInputMixin
   - TakeoverKeyboardMixin
   - TakeoverMouseMixin
   - TakeoverMinecraftMixin
   - TakeoverLocalPlayerMixin
   - TakeoverAbilityPacketMixin
   - TakeoverHypnosisIsolationMixin
5. 无需手动注册客户端事件：TakeoverClientState 的 Forge 事件及内部 Registration 的 MOD 按键事件、TakeoverOverlay 的 MOD overlay 注册与内部 OverlayGuard 的 Forge 事件均为 Dist.CLIENT 自动订阅。
   默认 K。已查 Synergy H、Changed R/Z，无默认冲突；整合包/玩家自定义绑定冲突仍由按键设置处理。
6. 服务端需禁止同时创建普通催眠/友好包裹/接管的多个所有者；接管开始/结束清理催眠视线对抗会话和服务端 MESMERIZED 效果。客户端隔离催眠输入锁与光圈，但不擅自改服务端效果。
7. 请向所在 level 的观察者广播状态，包括开始、phase 变化、载体替换、FINISHED；新进入 level 的玩家也需收到存量活动会话。
   实体晚于 state 抵达会由客户端逐 tick 补齐；离开客户端 level 和断线清理 tracking 表。
   FINISHED 必须广播给原观察者，不能只通知被接管者或只发往转移后的新 level。

## tracking 行为

- 接收所有合法会话到 UUID tracking 表，本地 UI/输入仅由 playerId 匹配本地玩家的会话更新。
- 源实体和 player 的 persistentData 均写入布尔 `SynergyTakeoverClient`；清理写 false。
  同时写私有 UUID `SynergyTakeoverClientSession`，避免旧结束包清理后来接管的相同实体。
- 普通分支设置原生 grab：grabbedEntity=player、suited=true、grabbedHasControl=(phase==1)、
  grabStrength/O=1、suitTransition/O=SUIT_TRANSITION_MAX、攻击/使用/原生逃脱输入清零，
  LivingEntityDataExtension.setGrabbedBy(carrier)，按控制者设置原生 noPhysics。
- 每个客户端 tick END 重建以上关系，修复 Addon RELEASE 误解绑。公共原生抓取 mixin 仍需在逻辑客户端读取 persistentData，阻止该帧原生逃脱和释放逻辑。
- FINISHED 直接清理会话所属字段、反向引用、noPhysics；不调用 suitEntity/releaseEntity，避免产生新网络请求、debuff、临时形态转换或被公共释放守卫拦截。
- 机械分支只更新 tracking 标记，不创造不存在的外骨骼原生 grab 载体。
- 不切换旁观模式，不更改形态、生命、背包、游戏版本或服务器恢复事务。

## UI / 输入

- 普通清醒 CONTROLLED/BORROWED 可按 K 打开；每会话在无其他 screen 时自动打开一次，不抢聊天或菜单。
- 挣脱按钮先进入后果确认页。确认请求绑定创建界面时的 session UUID；不会误操作后来的会话。
- 仅普通 BORROWED 允许移动/跳跃/潜行/冲刺及原生额外跳跃包。全阶段拦截攻击/使用/丢弃、能力激活和原生擒抱包。
- QTE 使用实际前/后/左/右 KeyMapping（含鼠标重绑），只响应 GLFW_PRESS，不处理 GLFW_REPEAT。
  每个服务端 sequence 最多发送一次；服务器必须每题推进 sequence，即使连续两题方向相同。
  expectedKey=-1 为准备期，不发输入；聊天、菜单、失焦不发 QTE。
- 不在客户端判胜负/失败、不因倒计时为零清理控制、不产生新的 QTE 题目。
- int progress 未约定总题数/百分比，现展示原始完成计数，不虚构进度条分母。
- releaseRemainingTicks 和 releaseTotalTicks 用于显示总释放进度并在非暂停 tick 间插值；fade 完全使用服务端值 [0,1]。
  当前 API 没有原因、活动详情、独立借用剩余时间或拒绝原因；未伪造这些数据，由服务器消息补充。
- 机械分支没有动作 Screen、挣扎/QTE、机会计数或清醒倒计时，只显示强制控制状态。
- 机械光圈独立绘制 #FFE84C / #F53E69，以约 80 tick 完整周期柔和交叉淡化；使用现有 QTE 动画/减少动态设置切换静态低强度环。
  服务端 fade 遮黑时仍显示安全恢复提示；图层位于菜单/聊天之下。
- 接管时屏蔽 changed:grabbed、changed:ability、changed_synergy:hypnosis_qte；
  机械分支额外屏蔽 changed:variant_blindness。不会启动旧 HypnosisQteClientState。
- 服务器仍需验证所有移动、容器、能力、攻击、丢弃和伪造包。客户端拦截不构成反作弊权限边界。

## 已合并 lang

除第一行外，表中 key 均加前缀 `takeover.changed_synergy.`。
现有 `key.categories.changed_synergy` 已复用。以下 22 个 key 均已追加 en_us.json 和 zh_cn.json。

| key | zh_cn | en_us |
| --- | --- | --- |
| key.changed_synergy.takeover | 身体控制权 | Body Control |
| title | 身体控制权 | Body Control |
| carrier | 控制者：%s | Controller: %s |
| cooldown | 借用冷却：%s 秒 | Control request cooldown: %s s |
| borrow | 让我控制一会儿 | Let me take over for a moment |
| escape | 尝试强行挣脱（本次仅一次） | Force your way out (one attempt) |
| return | 归还控制权 | Return control |
| confirm | 确认使用唯一的挣脱机会 | Use my one escape attempt |
| warning | 本次只有一次强行挣脱机会。失败后你会昏睡，并在安全处醒来。 | You have one escape attempt. If it fails, you'll lose consciousness and wake somewhere safe. |
| used | 本次挣脱机会已使用 | Escape attempt already used |
| available | 还剩一次挣脱机会 | One escape attempt available |
| controlled | 身体正由对方控制 | Your carrier is controlling the body |
| borrowed | 你暂时拥有移动控制权 | You temporarily control movement |
| struggle | 正在尝试挣脱 | Attempting to break free |
| sleeping | 你的意识逐渐模糊 | Your awareness is fading |
| releasing | 正在安全脱离 | Separating safely |
| mechanical | 手动控制已锁定，自动控制启动 | Manual control locked. Automatic control engaged. |
| recovering | 正在恢复到安全位置 | Recovering to a safe position |
| qte | 按下 %s | Press %s |
| prepare | 准备…… | Get ready… |
| progress | 已完成：%s | Completed: %s |
| open | 按 %s 打开身体控制权 | Press %s for Body Control |

## 验证与局限

- 使用实际 Forge/Changed 编译依赖编译本任务全部 Java 文件。
- 临时编译 fixture 在 .codex-tmp/takeover-client-review/stubs，只提供 handleAction 签名，明确抛异常，绝不能用于运行/打包。
- 独立测试入口 .codex-tmp/takeover-client-review/compile.init.gradle：
  `gradlew.bat testTakeoverPackets --offline --console=plain -I .codex-tmp/takeover-client-review/compile.init.gradle`。
  使用现有 build/classes/java/main 中的项目依赖；不触发 jar/reobfJar。
- codec 往返用例覆盖 2 种 kind、7 个 phase（含醒来后仍被包裹的 RETAINED）、expectedKey -1..3、4 种 action、UUID、中文名字、fade 与独立 playerId。
- 原版 mixin 的 Mojmap/SRG 方法名已对照本地 1.20.1 映射检查；默认 require=1，关键拦截失配时不会静默放行。
- 尚未启动游戏做双客户端 tracking、重连、实际 mixin 应用、GUI 缩放和视觉验收；完整集成需主线程注册/服务完成后验证。
