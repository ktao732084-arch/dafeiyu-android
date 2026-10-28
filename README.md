# 大肥鱼 · 独立安卓陪伴版 0.2.0
[下载 0.2.0 体验 APK](https://github.com/ktao732084-arch/dafeiyu-android/releases/tag/v0.2.0) · [逐模块来源](SOURCE_MAP.md) · [逐文件溯源](PROVENANCE.json) · [验证记录](VALIDATION.md)

> 这是基于社区项目整合的独立安卓版，不是全部从零原创，也不是 DeepSeek 官方产品。代码和第三方素材采用不同许可：代码见 [LICENSE](LICENSE)，动画与角色资源见 [ASSET_LICENSE.md](ASSET_LICENSE.md)。当前仅供非商业体验。

> 已执行 14 个 Robolectric 界面/运行时测试场景（含33项人格检查），另外48项核心和13项接口检查通过。尚未完成真机/完整模拟器安装、悬浮动画、系统 Keystore 或真实模型接口验收。具体边界见 [VALIDATION.md](VALIDATION.md)。

基于小小桌宠 Android 源码的非商业体验版本。安装 APK 后直接连接自己配置的模型接口，无需 DSH、Node.js、服务器或电脑常开。

## 安装体验

1. 在 Android 8.0 或更新版本的手机打开 `dafeiyu-android-0.2.0.apk`，按系统提示允许来自该来源的安装。
2. 首次打开「大肥鱼」，从5种初始性格中手动选一种，或选择「交给缘分」随机抽取一次。确认后固定，后期不提供重选或编辑人格入口。随后点「模型连接」，填写 HTTPS OpenAI Chat Completions 兼容接口、模型名称和自己的 API Key，测试连接后保存。测试连接也会产生一次模型请求。
3. 点「和她聊聊」可在应用内对话，不需要先授予悬浮权限。
4. 点「让她住到屏幕上」，按提示允许显示在其他应用上层和通知，再回到应用点击开启。离开主界面后宠物显示。
5. 轻触有动作反馈；拖动可移动，快速甩动会弹跳；长按打开对话、动作和大小菜单。拖到屏幕边缘可以收起。

首次可试：`记住：我喜欢下雨天。` → 打开「记忆手账」查看 → 重新打开 App 后再询问。显式记忆命令不用联网；普通 AI 聊天需要模型接口。未配置模型时会明确提示，不用预设台词冒充模型回复。

升级0.1版时，已有聊天/记忆或自定义人设会沿用并冻结原人格，不强行重选。清空聊天或手账不改变已确定的性格。

## 运行时

- **人格**：默认人设改编自 MIT 的 dafeiyu-persona，提供经典傲娇、温柔陪伴、元气搭子、冷静可靠、古灵精怪5种本版新增风格；只在初次相遇手选或随机一次，冻结完整人设，之后只能改名字与称呼；口吻自然，不虚构实际权限或经历。
- **一轮执行**：真实用户/定时事件 → 近期对话与相关记忆 → 模型请求 → 校验回复结构 → 原子保存 → 情绪动画与气泡。运行时不执行模型输出的任意代码或设备指令。
- **流式输出**：只展示 JSON `reply` 字段，隐藏情绪与记忆元数据；兼容非流式、普通文本回复。支持停止、超时、HTTP 错误和截断处理，不自动重试付费请求。
- **长期记忆**：模型仅可建议逐字摘录本轮用户原话，执行端核验来源、去重并过滤明显密钥/证件类内容；不把推测直接当事实。模型判断仍可能不准，手账可查看、编辑、固定、删除。
- **检索与容量**：最多120条长期记忆，自动记录满额时替换最早的非固定条目；按相关度、固定标记和时效选最多10条。保留最多160轮对话，总状态以4MiB UTF-8字节限额为准，超出时整轮淘汰最旧记录；发送最近最多8轮（合计不超过约10000字符）和最多3条相关旧对话用户原话。这不是向量数据库，也不承诺无限记忆。
- **一致性**：聊天、情绪和自动记忆一次落盘；原子替换和上一版备份；修改/清空记忆会使旧请求失效。删除某条记忆时同步删除其直接来源轮次，其他轮次如重复提及仍可能被回忆；可清空全部记录。
- **表现**：平静、开心、害羞、好奇、关切、俏皮和困倦七种表达映射到现有动画。情绪20分钟后回到平静；角色动画是演绎，不代表真实意识。
- **主动互动**：默认关闭。开启后只在屏幕亮起、未锁定、宠物可见、主界面/聊天关闭时考虑；距最近对话或交互至少30分钟，请求间隔至少90分钟，每个本地日期最多3次尝试（失败也计入）。23:00–08:00、系统勿扰和手动安静时段不发起。没有适合的话题时允许保持安静。
- **数据**：App 私有目录本地存储；关闭系统备份；Key 用 Android Keystore AES-GCM 加密。手账导出不含 Key。用户消息、选中的对话与记忆会发往用户配置的模型服务商；无开发者中转服务器。

## 本版边界

本版包含单只大肥鱼、文字对话、可追溯记忆、情绪动作和有限主动陪伴。暂不包含语音输入/合成、联网搜索、跨 App 读取、手机自动操作、云同步、人格自主训练、工具市场或自动升级。后台被系统清理后需手动重新开启；不同厂商的悬浮和后台策略需要真机确认。

采用独立应用 ID `com.dafeiyu.companion`，不会覆盖原版小小桌宠。体验签名保存在开发工作区；源码包不含签名私钥。后续覆盖安装需保持应用 ID 和签名一致。

## 构建

需要 JDK 17、Android SDK Platform 34、Build Tools 36.0.0、PowerShell 7。无需 Gradle。先用 `keytool` 创建自己的签名，然后：

```powershell
$env:XIAOXIAO_STORE_PASS = '你的签名密码'
./build.ps1 -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17' -KeyStore 'D:/Keys/fish.keystore'
```

产物：`build/dafeiyu-0.2.0.apk`。脚本仅打包 `assets/main` 与角色目录、人设；其他角色不打包。用自己的签名构建的 APK 不能直接覆盖本次体验签名版本。

## 检查

JVM 核心检查使用 `org.json:json:20240303`（只用于测试，不进入 APK）：

```powershell
./tests/run-core.ps1 -Jdk 'C:/Program Files/Java/jdk-17' -JsonJar 'D:/test-libs/json.jar'
```

Android 运行时端到端测试放在 `instrumentation/`，使用受控模型验证真实 Android Handler、Keystore、记忆和调度；不冒充真实云模型测试。先构建主 APK，再运行 `instrumentation/build-tests.ps1`，用 adb 安装主 APK 和测试 APK，然后：

```powershell
adb shell am instrument -w com.dafeiyu.companion.tests/com.xiaoxiao.pet.AgentInstrumentation
```

实际已在 Robolectric Android 14/API34 测试环境执行14个界面/运行时场景（含33项人格子检查），0失败。源码与可移植执行脚本在 `tests/robolectric`。这是JVM Android测试环境，不是完整模拟器，也不证明真机安装通过。模型与密钥存储在该测试中使用替身；边界详见 `VALIDATION.md`。

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
