# 按模块标注的来源与改动

本项目是开源项目整合与改造，不将上游实现或素材署为本项目原创。下面区分**直接复制、在上游代码上修改、仅参考实现路线、本版新增**。

## 直接基线与间接来源

| 来源 | 实际使用方式 | 对应内容 |
|---|---|---|
| [yshuang722-spec/xiaoxiao-zhuochong](https://github.com/yshuang722-spec/xiaoxiao-zhuochong/tree/ccdeb610972723ad11e9b4d4794292be138d21b6/android) | **直接安卓基线**，固定快照 `ccdeb610972723ad11e9b4d4794292be138d21b6` | 原生 Java App、浮窗、透明动画、物理互动、界面基础件、原始聊天/密钥逻辑、资源目录、构建脚本 |
| [PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet) | **间接上游**，经“小小桌宠”继承 | 桌宠概念与行为实现来源、蓝毛大肥鱼动画与物理逻辑。实际采用的是上述安卓快照内的版本，不声称用了该仓库最新 HEAD |
| [OTFiles/dsh-pet-inAndroid](https://github.com/OTFiles/dsh-pet-inAndroid) | **实现路线参考**，由安卓基线的 BUILD 文档标注 | Android 透明视频路线；本项目实际代码来自“小小桌宠”的 `AlphaVideo.java`，没有把该参考项目整仓另行移植 |
| [ERUIHNIYHBKBNF/dafeiyu-persona](https://github.com/ERUIHNIYHBKBNF/dafeiyu-persona/tree/5b08af8353fcfcc1378ea73f33686d669d61703f) | **文本风格参考与改编** | 蓝色鲸鱼娘、俏皮/傲娇、少量吃货梗、严肃话题收敛等角色表达。原始 Skill 文本存于 `PERSONA-UPSTREAM.md` |

不加载上述项目的 DSH 宿主。这里只是交代来源；不能把“独立运行”理解为“底层全部原创”。

## Android 代码逐模块归属

下表中的“小小桌宠基线”均指上述固定快照。每个实际文件的原路径、Git blob 和本仓库 SHA-256 另见 [PROVENANCE.json](PROVENANCE.json)。

| 本仓库部分 | 来源与关系 | 本次具体改动 |
|---|---|---|
| `src/com/xiaoxiao/pet/AlphaVideo.java` | 沿用小小桌宠；其文档注明参考 OTFiles 透明视频路线 | 保留 TextureView / MediaPlayer / EGL 透明视频播放实现 |
| `PetService.java` | 修改小小桌宠悬浮服务 | 接入独立 runtime 的触摸事件、情绪动作、流式气泡、定时检查、息屏取消；保留原浮窗与拖拽物理接线 |
| `MotionEngine.java` | 沿用小小桌宠；桌宠物理来自其 dsh-pet 上游路线 | 保留碰撞、重力、边界、抛掷等实现 |
| `MainActivity.java` | 在安卓基线 App 结构上重写主页 | 单只大肥鱼入口、初次相遇、记忆手账、模型与陪伴设置；不是原版六角色界面的直接发布 |
| `Chat.java` | 修改小小桌宠原聊天/密钥实现 | 保留并改造设置表单和 Keystore AES-GCM；对话转接新 runtime；支持停止、流式回调、错误恢复；配置改为原子保存 |
| `Data.java` | 修改小小桌宠数据与资源辅助类 | 人格提示词入口接到冻结人格；资源、设置与日志基础沿用 |
| `ProfilePage.java` | 重写原角色资料页 | 仅改名字、称呼，不再开放日常人格编辑 |
| `ActionSheet.java`、`PetMenu.java`、`SizePanel.java`、`PermissionsPanel.java` | 修改小小桌宠相应页面 | 保留交互，改应用标识、资源引用或名称 |
| `Apple.java`、`ClientPage.java`、`UiMotion.java` | 修改小小桌宠界面基础 | 改资源包名及应用称呼，补 Activity 所属 Dialog 生命周期跟踪 |
| `AmbientBackground.java`、`CompactPanel.java`、`FrostedPanel.java`、`GlassSurface.java`、`GroupedList.java`、`SpringScrollView.java`、`SwitchRow.java` | 沿用小小桌宠界面基础 | 具体是否仅格式差异见逐文件清单 |
| `ClonePanel.java` | 保留的小小桌宠代码 | 当前主界面不开放分身功能；不把该遗留实现署为新增 |
| `AgentRuntime.java`、`AgentPolicy.java`、`AgentReply.java` | **本版新增，AI辅助实现** | 独立请求生命周期、上下文组合、规则约束、模型结果校验、情绪动作与主动陪伴门禁 |
| `ActionPlan.java` | **本版新增，AI辅助实现** | 按时段、工作日/周末、季节和节日安排待机动作；情绪与触摸反应变化。农历表由 `lunardate`（香港天文台数据）生成，内置2024–2056年历表 |
| `AgentStore.java` | **本版新增，AI辅助实现** | 原子持久化、来源可追溯记忆、相关性检索、4MiB容量管理、版本隔离与备份恢复 |
| `AgentTransport.java` | **本版新增网络模块**，兼容基线原有 Chat Completions 接口约定 | HTTPS/SSE、完整回复校验、取消、超时和长度限制；并非移植一个外部 Agent 框架 |
| `ApiEndpoint.java`、`ModelSettings.java` | **本版新增**，接续原 App 配置/密钥功能 | 共用URL校验、每请求一致快照、配置与密文Key同事务保存 |
| `AgentPanel.java` | **本版新增**，使用上游界面基础件 | 记忆编辑/删除/导出，陪伴开关与安静时段 |
| `PersonaManager.java`、`PersonaPanel.java` | **本版新增**，使用上游界面基础件 | 初始化手选或随机一次、冻结整段人格、迁移旧记录、之后只读展示 |
| `DialogLifecycle.java` | **本版新增** | Activity销毁时关闭所属对话窗，避免遗留请求和窗口 |

## 人格、动画和视觉资源

| 文件或资源 | 归属与说明 |
|---|---|
| `PERSONA-UPSTREAM.md` | 保存 dafeiyu-persona 的原始 Skill 参考文本，保留来源；不是本 App 直接执行的 Skill |
| `assets/persona-default.txt` | 基于 dafeiyu-persona 风格改写，加入移动陪伴、事实边界与记忆约束 |
| `assets/personas.json` | **本版编写的5种初始表达风格**：经典傲娇、温柔陪伴、元气搭子、冷静可靠、古灵精怪。共享角色背景受上述人格参考影响；不能宣称为5个开源插件的原始人设 |
| `assets/catalog.json` | 修改小小桌宠角色目录，只保留蓝毛角色；调整名称、人设和分类。动画映射及边界/时长数据承自基线 |
| `assets/main/*` | **直接沿用小小桌宠安卓素材包**：106个动画和角色预览图；其蓝毛动画上游是 PC2005-cloud/dsh-pet。本次没有重新绘制或声称拥有这些素材 |
| `res` 下原有 PNG、原图标及相关资源 | 从小小桌宠随源码保留；部分为未使用的原版资源，不宣称本项目原创。来源和单独素材许可优先于代码许可 |
| `res/drawable/fish_mark.xml` | 本版新增的代码原生矢量鲸鱼图标，按代码 MIT 条款提供 |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | 在上游图标声明上修改，指向新增矢量图标 |

角色形象是社区二创，并非 DeepSeek 官方。dsh-pet 的说明将原型“溟月”归于 B站 [上善无形](https://space.bilibili.com/4456176)，女仆鲸鱼娘设计归于 [ZipZipPipe](https://space.bilibili.com/4168597)，并列出 CC BY-NC-SA 4.0 及非商业要求。这是对上游署名的保留，不表示本项目取得了额外商用权。详见 [ASSET_LICENSE.md](ASSET_LICENSE.md)。

## 构建和测试

| 部分 | 来源 |
|---|---|
| `build.ps1`、`AndroidManifest.xml`、既有主题资源 | 修改小小桌宠安卓基线：独立包名/版本、单角色打包、参数文件构建、签名验证 |
| `tests/com/xiaoxiao/pet/MotionEngineTest.java`、`tests/check_action_return.py` | 沿用小小桌宠测试；后者为录屏分析脚本，本次未执行其设备录屏验收 |
| `tests/com/xiaoxiao/pet/AgentCoreTest.java`、`ApiEndpointTest.java`、`tests/run-core.ps1` | 本版新增的核心逻辑与地址校验回归 |
| `instrumentation/*` | 本版新增 Android 设备测试代码；当前实际Android执行范围见 VALIDATION.md（受控模型，独立测试包） |
| `tests/robolectric/*` 中自写测试、bootstrap与runner | 本版新增。使用 [Robolectric 4.13](https://github.com/robolectric/robolectric)、[JUnit 4.13.2](https://github.com/junit-team/junit4)、[AndroidX Test](https://github.com/android/android-test)；这些依赖只用于测试，不打入应用 APK |
| JVM核心检查的 JSON 库 | 测试依赖 [JSON-java 20240303](https://github.com/stleary/JSON-java)，不打入 APK；App 中使用系统 `org.json` |
| 构建平台 | JDK17、[Android SDK](https://developer.android.com/tools) 与系统 Android APIs；不是本项目原创平台实现 |

新增业务代码、预设与测试由本次开发通过 AI 辅助编写。引用第三方接口或设计路线不等于复制其整个实现；没有实际使用的项目不列作代码来源。若有来源标注不准确，可提交 Issue 指出具体文件，我们将按可核实证据修正。

## 0.3.0 新增记忆引擎来源

| 模块 | 来源与实际使用方式 |
|---|---|
| `memory-service/upstream/src/*.py` | 直接随包分发 `ktao732084-arch/agent-anthropomorphic-memory` 的固定源码快照；提交与逐文件 SHA-256 见 `memory-service/UPSTREAM.json`，MIT 原文保留。不是自行重写的替代引擎。 |
| `companion_api.py`、`run_companion_eval.py` 与服务专项测试 | 本次为两个项目新增的无状态 API 适配、来源映射和共享评测；已提交回记忆项目 [PR #4](https://github.com/ktao732084-arch/agent-anthropomorphic-memory/pull/4)。 |
| `llm_judge.py` | 上游实现基础上增加最终答案门控、截断/容量/候选编号验证；保留上游最新可配置 token 预算修复。 |
| `MemoryBridge.java` | 本项目新增 Android HTTP 客户端、独立凭据配置、响应来源校验与降级；不加载 DSH 或 Python 到 APK。 |
| `MemoryLedger.java`、`MemoryPanel.java` | 本项目新增手机侧来源、历史、删除语义与界面。核心去重、时间推理、语义与写入判断由 Python 上游执行。 |
| `MemoryIntegrationTest.java`、`MemoryReplay.java` | 本项目新增；实际启动 Python HTTP 服务，复用未经修改的84场景。 |

记忆项目自身说明借鉴 mem0 的写入判断与相对时间、Graphiti 的时间失效等思路；本项目的直接代码来源是上述固定快照，没有另行复制这些项目的实现。该来源不改变角色动画的非商业许可要求。

## 0.4.0 安卓原生移植

| 部分 | 来源与改动 |
|---|---|
| `NativeMemoryRules.java` | 从已固定的记忆项目 `memory.py`、`noise_filter.py` 的 AST 提取规则表生成；Python的Unicode正则转为Java表达，来源文件哈希见 `docs/native-rules-source.json`。 |
| `NativeMemoryTime.java` | 将上游 `temporal.py` 的中文相对日期算法移植为 `java.time`，按手机观察时区落地，原话与解析文本分离。 |
| `NativeMemoryEngine.java` | 移植上游过滤、重要性、bigram比较、更正/去重和时间失效流程；在Android JSON存储上实现原子判断、历史、来源和检索。规则回归复用原始84场景。 |
| `NativeMemoryIntelligence.java` | 本项目新增手机模型适配：复用现有聊天配置，按ID进行语义召回和批量写入判断。参考上游LLM写入决策思路；手机默认未移植ONNX运行库或向量权重。 |
| `NativeMemoryTest.java` | 本项目新增原生执行测试，不启动Python；模型能力使用受控接口测试，不声称独立复现上游真实模型分数。 |

原生移植沿用记忆项目MIT许可，原许可保留于 `memory-service/upstream/LICENSE`。Python服务仍可作为开发对照，但手机版默认路径不依赖它。

## 0.4.1 Claude 优化分支合并

合并本仓库 `fix/power-and-model-compat` 的7个提交，终点为 [8e34e33](https://github.com/ktao732084-arch/dafeiyu-android/commit/8e34e3322565b4c5769133a1f3e3f11604e44c2a)。Git 保留原提交与合并父节点，下面这些改进来自该分支的 Claude 辅助开发，不能全部归为本轮原创。

| 部分 | 来源和合并适配 |
|---|---|
| `ActionPlan.java`、`PetService.java`、`AlphaVideo.java` | Claude 分支的时段/季节/节日动作、睡眠唤醒、互动反应、解码暂停与服务恢复。沿用原动画素材；本轮增加真实素材目录匹配与服务唤醒/锁屏框架测试。农历表生成工具按分支记录为 [lunardate](https://github.com/lidaobing/python-lunardate)，本包不引入其 Python 运行库；表数据与边界说明保留在代码。 |
| `PromptBuilder.java`、`AgentPolicy.java`、`PersonaManager.java`、`assets/personas.json` | Claude 分支的提示词重排、口吻示例、固定自我设定、关系阶段与重复风格检测。本轮明确历史自我陈述不能覆盖初始化人格。 |
| `AgentStore.java`、`AgentReply.java`、`MemoryPanel.java` | Claude 分支的自我陈述提取、落盘与查看/清空。本轮保留原生记忆事务，并修复清空时的迟到请求与备份恢复问题。 |
| `AgentTransport.java`、`MemoryBridge.java`、`MemoryLedger.java` | Claude 分支的推理模型预算、服务错误提示、可选Python检索等待与关键记忆上下文；本轮合并保留手机原生非流式参数和内容截断保护。 |
| `.github/workflows/check.yml`、`tests/run-core.sh`、`tests/compile-check.sh`、`ActionPlanTest.java` | Claude 分支新增跨平台检查与动作回归；`CompanionBehaviorTest.java` 是本轮新增的整合测试，模型请求使用本地受控HTTP服务。 |

逐文件清单额外记录 `integration_sources`，保留原有底座、人格、记忆项目和资源的来源归属。本轮没有修改这些来源的许可范围。

## 0.4.3 模型配置简化与余额（包含未发布的0.4.2开发内容）

`ModelSetup.java`、`ModelPresets.java` 与 `ModelSetupTest.java` 是本轮新增的配置界面、预设和回归；`Chat.java` 移除旧配置与重复测试传输，统一使用既有 `AgentTransport`。保留小小桌宠界面组件及此前传输模块的归属。

DeepSeek地址、模型别名、Key入口、非思考参数和错误码依据2026-10-28查阅的官方文档：[首次调用](https://api-docs.deepseek.com/zh-cn/)、[模型说明](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)、[思考开关](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)、[错误码](https://api-docs.deepseek.com/zh-cn/quick_start/error_codes/)。没有复制第三方SDK或服务端代码。`deepseek-flash` 对应官方当前 DeepSeek V4.1 Flash，App展示名与请求名分别使用；后续服务商更新可能需调整预设。

`DeepSeekBalance.java` 是本轮新增的只读HTTP客户端、金额解析与本机显示缓存，依据[DeepSeek官方余额接口](https://api-docs.deepseek.com/zh-cn/api/get-user-balance/)；仅支持官方HTTPS来源与Bearer Key，不向DeepSeek发送其他服务商凭据。总额、充值余额、赠金分别取官方字段，未复制SDK。

## 0.4.4 分段与聊天历史

ReplySegments、SpeechQueue、ChatArchive、ChatPage与新增测试由本项目编写，负责本地无损断句、可取消的展示队列、按轮归档与时间范围查询、Android聊天界面。Avatar由本项目编写，从角色立绘裁出聊天和首页使用的圆形头像。

交互思路参考既有微信客服的split_message / typing_delay / send_message_split（按句意拆分、串行发送、短暂停顿），并查阅 [AstrBot分段配置](https://github.com/AstrBotDevs/AstrBot/blob/master/docs/zh/dev/astrbot-config.md)。未复制AstrBot的AGPL代码，也未引入其Python框架、微信桌面自动化、客户数据或配置。Android实现、归档、毫秒时间戳和未展示状态与记忆整合在本项目完成；桌宠底座及素材归属保持原标注。

## 0.4.5 自动历史检索

HistoryRecall.java及新增测试由本项目实现，使用java.time解析日历范围、ChatArchive读取已保存对话、AgentPolicy做字面相关度选择；未引入额外框架、向量服务或独立分段/检索模型。无字面命中时由现有回答模型依据同一时段的候选原话判断，不声称本地字面检索等同完整语义检索。

AgentRuntime、PromptBuilder和AgentStore的改动将检索证据接入正常回复，保留已删除/未展示内容的约束，并禁止把历史引用重新提炼成当前事实。继续包含本仓库 [aab1428合并](https://github.com/ktao732084-arch/dafeiyu-android/commit/aab1428) 的原生记忆兼容与优化；上游原许可和素材约束不变。

本轮发布还合入主分支的白色界面与社交式聊天页、EpisodeIndex全历史关键词回忆、规则自动提炼、500/1000记忆容量与陪伴日记（合并点59d69c9）。它们来自本仓库既有优化分支；日期查询与普通关键词回忆共存，日期查询避免混入其他时段的回忆和日记，也不因这次查询额外生成日记。

## 0.4.6 Android 正则兼容

追加审查中的DeviceCoreChecks、run-core-device.ps1、build-isolated-tests.ps1和测试扩展由本项目编写，复用既有产品代码、语料、测试断言与资源；没有引入第三方运行框架。既有素材与记忆项目的来源、许可保持原标注。

UnicodeRegex、NativeRegexTest和设备检查脚本为本项目新增实现。Android默认Unicode字符类但不支持显式UNICODE_CHARACTER_CLASS的行为依据[Android官方文档](https://developer.android.com/reference/java/util/regex/Pattern#UNICODE_CHARACTER_CLASS)。对NativeMemoryEngine与NativeMemoryTime的兼容修正保持原记忆项目MIT归属；没有复制外部实现。

## 0.4.7 称呼同步与聊天展示

Nickname、ChatPolishRulesTest和ChatPolishTest由本项目新增，称呼识别采用本机保守规则，不引入额外模型或外部库。AgentRuntime、ProfilePage、AgentPanel、AgentPolicy、AgentReply、ReplySegments、ChatPage和PromptBuilder在既有实现上修改；保留原上游与优化分支归属。修复来自2026-10-29真实Android聊天实测反馈；用户对话截图仅保存在本地，不随源码公开。

## 0.4.8 自主陪伴与互动记录

`Initiative.java`、`InitiativeTest.java`、`InitiativeRuntimeTest.java`为本项目AI辅助新增：模型安排下次考虑、物理事件时间线、主动决定记录、防循环与错误退避。`AgentRuntime`、`AgentStore`、`AgentReply`、`PromptBuilder`、`MemoryPanel`、`AgentPanel`与测试接入这些能力，沿用各自既有来源；`PetService`在小小桌宠悬浮交互基础上增加事件接线。无新增第三方运行依赖或外部Agent框架；原有素材、人格参考和Claude协作提交归属不变。

## 0.4.9.1 忙闲调度修复

`InitiativePlan.java`为本项目AI辅助新增：验证模型调度字段、明确忙闲意图、安排来源与主动催问过滤。`Initiative`、runtime/store/prompt和设置界面在原项目模块上继续修改，未引入额外Agent框架或新第三方依赖。保留了[主分支合并的可用性优化](https://github.com/ktao732084-arch/dafeiyu-android/commit/e3a1da1)：聊天搜索、长按记住、手账搜索与设置精简；本轮另修复其中的空手账提前返回，并修正分页测试夹具。上述界面改动不署为本轮独立原创。

发布前再合并[后台存储与上手优化](https://github.com/ktao732084-arch/dafeiyu-android/commit/59c04c7)：展示记录异步写盘、归档缓存、编辑手账后台执行、首次使用提示与隐藏桌宠等。`AgentStore.shown`的合并同时保留后台写盘和本轮已展示话题记录；新增对异步写盘后主题恢复的验证。

## 0.4.9.2 轻量更新

后续 `release.ps1`、`release-sync.py`、`release-remote.py` 与同步测试为本项目新增发布编排，复用已有 prepare-update.py、GitHub CLI、OpenSSH 和 curl；不引入第三方 Python 库或服务器常驻程序。本次自动化不改变 App 版本或 APK。

AppUpdate、UpdateTransport、UpdateApk、UpdateFileProvider、UpdateRuntime、UpdatePanel、prepare-update.py、部署模板及相应测试均为本项目新增实现。使用 Android 平台网络、包管理器、ContentProvider 和系统安装界面；没有引入第三方更新 SDK、动态服务端或外部 Agent 代码。合入当前主分支后以 0.4.9.2 发布，保留原产品与素材归属。

公网 IP 接入的 Nginx 路径配置、北京构建入口与 UpdateNetworkInstrumentation 为本项目新增；复用用户已有北京服务器和 IP 证书续期配置。站点最初镜像 v0.4.7；本次以原签名发布 0.4.9.2，保留上游素材归属。UpgradeAcceptance 与 OfflineTestApplication 为本项目新增验证代码：前者仅在新建模拟器使用合成数据验证真实 APK 覆盖安装，后者阻止框架单测意外访问线上更新站点。
