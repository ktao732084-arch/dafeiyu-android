# 大肥鱼 · 独立安卓陪伴版 0.3.0
[下载 0.3.0 体验 APK](https://github.com/ktao732084-arch/dafeiyu-android/releases/tag/v0.3.0) · [逐模块来源](SOURCE_MAP.md) · [逐文件溯源](PROVENANCE.json) · [验证记录](VALIDATION.md)

> 这是基于社区项目整合的独立安卓版，不是全部从零原创，也不是 DeepSeek 官方产品。代码和第三方素材采用不同许可：代码见 [LICENSE](LICENSE)，动画与角色资源见 [ASSET_LICENSE.md](ASSET_LICENSE.md)。当前仅供非商业体验。

> 新增完整记忆服务接入与双项目共享评测；测试结果及已知不足见验证记录。尚未完成真机/完整模拟器安装、悬浮动画、系统 Keystore 或真实模型接口验收。具体边界见 [VALIDATION.md](VALIDATION.md)。

基于小小桌宠 Android 源码的非商业体验版本。基础聊天与本地原话记忆可直接连接自己配置的模型接口，无需 DSH。完整拟人记忆通过你自己的 Python 服务接入，需保持该服务可达；[一键启动与手机连接说明](memory-service/README.md)。

## 安装体验

1. 在 Android 8.0 或更新版本的手机打开 `dafeiyu-android-0.3.0.apk`，按系统提示允许来自该来源的安装。
2. 首次打开「大肥鱼」，从5种初始性格中手动选一种，或选择「交给缘分」随机抽取一次。确认后固定，后期不提供重选或编辑人格入口。随后点「模型连接」，填写 HTTPS OpenAI Chat Completions 兼容接口、模型名称和自己的 API Key，测试连接后保存。测试连接也会产生一次模型请求。
3. 点「和她聊聊」可在应用内对话，不需要先授予悬浮权限。
4. 点「让她住到屏幕上」，按提示允许显示在其他应用上层和通知，再回到应用点击开启。离开主界面后宠物显示。
5. 轻触有动作反馈；拖动可移动，快速甩动会弹跳；长按打开对话、动作和大小菜单。拖到屏幕边缘可以收起。

首次可试：`记住：我喜欢下雨天。` → 打开「记忆手账」查看 → 重新打开 App 后再询问。显式记忆命令不用联网；普通 AI 聊天需要模型接口。未配置模型时会明确提示，不用预设台词冒充模型回复。

升级0.1版时，已有聊天/记忆或自定义人设会沿用并冻结原人格，不强行重选。清空聊天或手账不改变已确定的性格。

## 运行时

- **人格**：默认人设改编自 MIT 的 dafeiyu-persona，提供经典傲娇、温柔陪伴、元气搭子、冷静可靠、古灵精怪5种本版新增风格；只在初次相遇手选或随机一次，冻结完整人设，之后只能改名字与称呼；口吻自然，不虚构实际权限或经历。
- **一轮执行**：真实用户/定时事件 → 近期对话与相关记忆 → 模型请求 → 校验回复结构 → 原子保存 → 情绪动画与气泡。运行时不执行模型输出的任意代码或设备指令。
- **流式输出**：只展示 JSON `reply` 字段，隐藏情绪与记忆元数据；兼容非流式、普通文本回复（纯文本也边生成边显示），以及模型在 JSON 前多说一句的情况。支持停止、超时、HTTP 错误和截断处理，出错时显示服务商返回的错误原因（不含 Key），不自动重试付费请求。推理模型（如 `deepseek-reasoner`）使用更大的输出额度和更长的超时。
- **长期记忆**：模型仅可建议逐字摘录本轮用户原话，执行端核验来源、去重并过滤明显密钥/证件类内容；不把推测直接当事实。模型判断仍可能不准，手账可查看、编辑、固定、删除。
- **检索与容量**：最多120条长期记忆，自动记录满额时替换最早的非固定条目；本地模式按相关度、固定标记和时效选最多10条；完整模式使用服务返回的检索结果，支持关键词与可选语义检索。保留最多160轮对话，总状态以4MiB UTF-8字节限额为准，超出时整轮淘汰最旧记录；发送最近最多8轮（合计不超过约10000字符）和最多3条相关旧对话用户原话。完整记忆模式另保留最多240条归档、512条整理事件和可复用向量缓存；不承诺无限记忆。
- **一致性**：聊天、情绪和自动记忆一次落盘；原子替换和上一版备份；修改/清空记忆会使旧请求失效。删除记忆时同步清除关联历史版本、直接来源轮次、对应整理记录、向量缓存和备份；独立重复提及仍可通过清空全部记录去除。
- **表现**：平静、开心、害羞、好奇、关切、俏皮和困倦七种表达映射到现有动画。情绪20分钟后回到平静；角色动画是演绎，不代表真实意识。
- **主动互动**：默认关闭。开启后只在屏幕亮起、未锁定、宠物可见、主界面/聊天关闭时考虑；距最近对话或交互至少30分钟，请求间隔至少90分钟，每个本地日期最多3次尝试（失败也计入）。23:00–08:00、系统勿扰和手动安静时段不发起。没有适合的话题时允许保持安静。
- **数据**：App 私有目录本地存储；关闭系统备份；Key 用 Android Keystore AES-GCM 加密。手账导出不含 Key。用户消息、选中的对话与记忆会发往用户配置的模型服务商；启用完整记忆时，记忆快照和查询还会发送给你配置的 Python 服务。聊天 Key 不转发到记忆服务，手账导出不含服务令牌；无开发者托管的中转服务器。

## 完整记忆引擎

通过「陪伴设置 → 记忆引擎」连接 `agent-anthropomorphic-memory`。支持原话与解析时间分离、更正失效、历史归档、去重、来源追踪、可选 OpenAI 兼容 embeddings / 本地 ONNX、LLM 新增/重复/取代判断；服务失败保留本地能力。已失效来源不再进入日常上下文，明确询问过去时才提供带历史标记的记录。人格仍只在初始化确定。

完整流程、Python 配置和共享测试见 [memory-service/README.md](memory-service/README.md)。其中 ONNX 运行在服务端，不是手机端内置模型；也不自动同步你已有的其他 Agent 用户档案。

## 本版边界

本版包含单只大肥鱼、文字对话、可追溯记忆、情绪动作和有限主动陪伴。暂不包含语音输入/合成、联网搜索、跨 App 读取、手机自动操作、云同步、人格自主训练、工具市场或自动升级。系统因内存不足回收后会尝试自动恢复已显示的桌宠；被厂商后台清理或在系统设置中强制停止后仍需手动重新开启。不同厂商的悬浮和后台策略需要真机确认。

**省电**：息屏时停止所有定时任务和动画解码；锁屏界面、打开大肥鱼自己的主界面时暂停视频解码；亮屏期间每秒检查一次待机动作，主动陪伴检查每30秒一次且只读少量状态。息屏不会中断你已发出的聊天请求，只取消尚未显示的主动消息。

采用独立应用 ID `com.dafeiyu.companion`，不会覆盖原版小小桌宠。体验签名保存在开发工作区；源码包不含签名私钥。后续覆盖安装需保持应用 ID 和签名一致。

## 构建

需要 JDK 17、Android SDK Platform 34、Build Tools 36.0.0、PowerShell 7。无需 Gradle。先用 `keytool` 创建自己的签名，然后：

```powershell
$env:XIAOXIAO_STORE_PASS = '你的签名密码'
./build.ps1 -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17' -KeyStore 'D:/Keys/fish.keystore'
```

产物：`build/dafeiyu-0.3.0.apk`。脚本仅打包 `assets/main` 与角色目录、人设；其他角色不打包。用自己的签名构建的 APK 不能直接覆盖本次体验签名版本。

## 检查

JVM 核心检查使用 `org.json:json:20240303`（只用于测试，不进入 APK）：

```powershell
./tests/run-core.ps1 -Jdk 'C:/Program Files/Java/jdk-17' -JsonJar 'D:/test-libs/json.jar'
```

Linux/macOS：`tests/run-core.sh path/to/json.jar`。另有 `tests/compile-check.sh path/to/android-all.jar`，用 Maven Central 上 Robolectric 的 Android 14 框架类编译全部源码，无需 Android SDK；它只检查 Java/API 编译，不生成 APK。GitHub Actions（`.github/workflows/check.yml`）在每次推送时运行这两项。

Android 运行时端到端测试放在 `instrumentation/`，使用受控模型验证真实 Android Handler、Keystore、记忆和调度；不冒充真实云模型测试。先构建主 APK，再运行 `instrumentation/build-tests.ps1`，用 adb 安装主 APK 和测试 APK，然后：

```powershell
adb shell am instrument -w com.dafeiyu.companion.tests/com.xiaoxiao.pet.AgentInstrumentation
```

Android 界面与运行时测试、真实 Python HTTP 联调、84场景跨端重放的结果见 [VALIDATION.md](VALIDATION.md)。Robolectric 是 JVM Android 测试环境，不是完整模拟器；聊天模型与 Android Keystore 使用受控测试替身。原话/归档/检索的服务流程则调用真实 Python 引擎。

## 文件入口

`AgentRuntime.java`：请求生命周期、上下文、定时互动、动作映射。
`AgentPolicy.java`：可独立测试的检索、节奏、记忆过滤及流式字段提取。
`AgentStore.java`：原子持久化、记忆管理、历史检索、版本隔离。
`AgentTransport.java`：HTTPS、SSE、超时、停止和兼容接口。
`AgentReply.java`：模型输出校验。
`AgentPanel.java`：记忆手账和陪伴设置。
`PersonaManager.java` / `PersonaPanel.java`：一次性人格初始化、冻结、旧数据迁移及只读展示。
`ModelSettings.java` / `ApiEndpoint.java`：配置原子保存、每请求一致快照、统一地址校验。
`DialogLifecycle.java`：Activity销毁时关闭所属对话窗口。
`MainActivity.java` / `Chat.java`：主界面、模型配置与聊天。
`PetService.java` / `AlphaVideo.java`：上游悬浮窗口与透明动画实现。

## 来源与许可

详见 [SOURCE_MAP.md](SOURCE_MAP.md)、[NOTICE-DAFEIYU.md](NOTICE-DAFEIYU.md) 和 [PROVENANCE.json](PROVENANCE.json)。代码 MIT 不等于动画和角色形象可商用，此 APK 供非商业体验。

`MemoryBridge.java` / `MemoryLedger.java` / `MemoryPanel.java`：完整引擎协议、手机数据所有权、来源与历史、设置和联调。
`memory-service/upstream`：固定提交的 Python 源码与 MIT 许可；`UPSTREAM.json` 记录逐文件哈希。
