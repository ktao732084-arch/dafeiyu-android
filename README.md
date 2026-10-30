<div align="center">

# 大肥鱼 · Dafeiyu

**住在你手机里的鲸鱼娘陪伴伙伴**

会记得你说过的话，有自己的性格，在桌面上过着自己的小日子。

[![Release](https://img.shields.io/github/v/release/ktao732084-arch/dafeiyu-android?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=2b7fc4)](https://github.com/ktao732084-arch/dafeiyu-android/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)](#快速开始)
[![Check](https://github.com/ktao732084-arch/dafeiyu-android/actions/workflows/check.yml/badge.svg)](https://github.com/ktao732084-arch/dafeiyu-android/actions/workflows/check.yml)
[![Code License](https://img.shields.io/badge/%E4%BB%A3%E7%A0%81-MIT-blue)](LICENSE)
[![Asset License](https://img.shields.io/badge/%E8%A7%92%E8%89%B2%E7%B4%A0%E6%9D%90-%E9%9D%9E%E5%95%86%E7%94%A8-orange)](ASSET_LICENSE.md)

<a href="docs/promo/dafeiyu-promo.mp4"><img src="docs/promo/dafeiyu-promo.webp" width="800" alt="大肥鱼宣传片：从深海游上来的鲸鱼娘、本机记忆、五种性格、一百多段动作、五章任务线"></a>

<sub>▲ 宣传片（55 秒，含真机实拍）· 动图无声，点击观看带原创配乐的 <a href="docs/promo/dafeiyu-promo.mp4">高清 MP4</a></sub>

[**下载 APK**](https://github.com/ktao732084-arch/dafeiyu-android/releases/latest) · [快速开始](#快速开始) · [功能细节](docs/FEATURES.md) · [更新记录](CHANGELOG.md) · [验证记录](VALIDATION.md)

</div>

---

## 简介

大肥鱼是一款 Android 桌面陪伴应用。她是一条从深海游上来的小鲸鱼，住进你的屏幕后，会把和你的共同经历攒成「回忆泡泡」。

- **记忆在手机里**：记忆引擎是安卓原生代码，存储、去重、更正、时间解析和检索都在本机完成，不需要电脑或额外服务器。
- **对话用你自己的模型**：默认接入 DeepSeek，也支持任意 OpenAI 兼容接口。Key 加密保存在本机，没有开发者中转服务器。
- **她是一个连贯的角色**：初次相遇时选定的性格会固定下来；心情、日记、对你的了解分层保存，每一条都能追溯到你说过的原话。

> 本项目是社区二创，与 DeepSeek 官方无隶属或背书关系。代码采用 MIT；角色形象与动画为非商业授权，详见 [许可与致谢](#许可与致谢)。

## 功能亮点

| | 功能 | 说明 |
|---|---|---|
| 🧠 | **本机记忆** | 自动记下你的偏好、习惯和计划，明确更正后旧记录失效；可在「记忆手账」查看、搜索、固定或忘记。 |
| 🕰️ | **记得聊过什么** | 直接问「我们上周聊过什么？」，她会在全部聊天记录里按日期检索原话再回答，不会凭空编造。 |
| 🎭 | **五种性格** | 经典傲娇、温柔陪伴、元气搭子、冷静可靠、古灵精怪，各有说话方式与立场；心情会延续并慢慢淡去。 |
| 🐋 | **桌面悬浮** | 一百多段动作随作息、季节与节日切换：清晨刷牙、午后犯困、夜里打盹、中秋赏月、冬天堆雪人。轻点有 45 种回应，拎头部或后腰会有不同的悬吊姿态。 |
| 💬 | **自然短气泡** | 回复按自然停顿一句一个气泡逐条出现；摸摸、拎起或喂食后，她会按自己的性格接一句。 |
| 💭 | **小声嘀咕与共同经历** | 回复下偶尔多一行她没说出口的小念头；每天从真实聊天和棋局里留下一件「我们的经历」。 |
| 🫧 | **五章任务线** | 共同经历攒进泡泡瓶，每章和她一起完成一件事，解锁新的动作和剧情。离开不扣分，模型不能加分。 |
| ⚫ | **五子棋** | 真正可玩的对局，支持保存继续；胜负只来自本机棋谱，她能记得累计战绩和最近的棋局。 |
| 🌙 | **主动陪伴** | 可选开启。由模型结合性格与真实记录决定说话还是安静，遵守勿扰、亮屏与聊天优先。 |
| 🔄 | **更新与反馈** | 「关于 → 检查更新」，GitHub Release 与国内 HTTPS 更新源同步，覆盖安装保留全部数据；「关于 → 问题反馈」免注册，可附截图。 |

各项功能的完整行为、上限与边界见 [功能细节](docs/FEATURES.md)。

## 快速开始

**系统要求**：Android 8.0（API 26）及以上。

1. **安装**：从 [Releases](https://github.com/ktao732084-arch/dafeiyu-android/releases/latest) 下载 `dafeiyu-android-<版本>.apk` 安装。各版本使用同一签名，覆盖安装会保留聊天、记忆、人格、模型配置和棋局。
2. **初次相遇**：手动选择一种性格或随机一次。确认后固定，之后不提供重选。
3. **连接模型**：在「模型连接」保持默认的 **DeepSeek V4.1 Flash**，点「去 DeepSeek 官网获取 Key」，创建并复制 API Key 后粘贴，点「连接并保存」。连接测试成功才会保存，并显示账户余额。
4. **让她住到屏幕上**：授予「显示在其他应用上层」和通知权限，点「让她住到屏幕上」。

不配置模型也能先试用本机记忆，例如发送 `记住：我喜欢海边散步`。其他服务商请选择「其他模型 / 自定义」。

> [!NOTE]
> API 调用使用你自己的额度；DeepSeek 网页聊天免费不代表 API 免费，收费规则见 [官方说明](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)。养成规则刻意不鼓励多聊：聊天一天只计一次羁绊。

## 隐私与数据

- 聊天、记忆和情绪保存在 App 私有目录，并关闭系统备份；API Key 使用 Android Keystore AES-GCM 加密。
- 只有你配置的模型服务商会收到对话内容和相关记忆，模型请求不经过开发者中转服务器。
- 问题反馈使用单独的自建服务，只附带 App/Android 版本和机型，不发送聊天、记忆或 Key。
- 忘记一条记忆时，会一并删除它的来源对话、整理记录、缓存和引用它的剧情回忆；清空聊天同理。
- 不包含语音、跨 App 读取、设备自动操作或人格自主训练。

## 版本

当前发布版本为 **[0.4.9.6](https://github.com/ktao732084-arch/dafeiyu-android/releases/tag/v0.4.9.6)**（更正记忆与桌宠互动），说明见 [本版更新](docs/RELEASE-0.4.9.6.md)，历次变化见 [CHANGELOG](CHANGELOG.md)。

版本号采用四段式 `主.次.功能.修订`，发布同步 GitHub 与国内更新源，规则见 [项目协作约定](AGENTS.md)。

## 构建与测试

**环境**：JDK 17、Android SDK Platform 34、Build Tools 36.0.0、PowerShell 7。不依赖 Gradle。签名私钥不随源码分发。

```powershell
$env:XIAOXIAO_STORE_PASS='你的签名密码'
./build.ps1 -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17' -KeyStore 'D:/Keys/fish.keystore'
```

产物为 `build/dafeiyu-<版本>.apk`。测试：

```powershell
# 核心检查（JVM）
./tests/run-core.ps1 -Jdk 'C:/Program Files/Java/jdk-17' -JsonJar 'D:/test-libs/json.jar'
# Robolectric
./tests/robolectric/run-tests.ps1 -AndroidRoot '.' -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17'
# 在真实 Android 系统上用产品 DEX 运行核心测试
./tests/run-core-device.ps1 -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17'
```

Linux / macOS 可运行 `tests/run-core.sh path/to/json.jar path/to/android-all.jar`，以 Android 14 框架类完成全量编译和核心检查，无需 Android SDK。CI（`.github/workflows/check.yml`）校验记忆服务与 84 个共享场景基线。

JVM 与 Robolectric 不等同于 Android ART，涉及正则、JSON、文件系统、Keystore 等路径时需在实际 Android 上复核，测试边界见 [VALIDATION.md](VALIDATION.md)。发布流程见 [双端发布](deploy/updates/RELEASING.md)。

<details>
<summary><b>项目结构</b></summary>

```
├── src/com/xiaoxiao/pet/   应用源码：桌宠、聊天、原生记忆引擎、养成、五子棋、更新
├── res/                    界面资源
├── assets/                 角色动画（main/*.mp4）、人格预设、剧情与动作目录
├── tests/                  JVM 核心测试、Robolectric、场景与记忆捕获评测
├── instrumentation/        Android 设备上的独立兼容测试包
├── memory-service/         可选的 Python 记忆服务，仅用于开发对照
├── tools/                  原生规则生成、人格盲测、发布同步、宣传片渲染
├── deploy/updates/         国内更新站点部署与发布脚本
└── docs/                   设计说明、验收与验证报告
```

</details>

<details>
<summary><b>文档索引</b></summary>

| 主题 | 文档 |
|---|---|
| 功能细节总览 | [docs/FEATURES.md](docs/FEATURES.md) |
| 性格、心情与状态分层 | [CHARACTER.md](docs/CHARACTER.md) · [CHARACTER_STATE.md](docs/CHARACTER_STATE.md) |
| 养成主线 | [GROWTH_DESIGN.md](docs/GROWTH_DESIGN.md) |
| 主动陪伴 | [PROACTIVE_COMPANION.md](docs/PROACTIVE_COMPANION.md) |
| 互动回应 | [TOUCH_REPLIES.md](docs/TOUCH_REPLIES.md) |
| 聊天记录格式 | [CHAT_HISTORY.md](docs/CHAT_HISTORY.md) |
| 称呼同步 | [NICKNAME_SYNC.md](docs/NICKNAME_SYNC.md) |
| 五子棋 | [GOMOKU.md](docs/GOMOKU.md) |
| 应用内更新 | [UPDATES.md](docs/UPDATES.md) · [更新站点部署](deploy/updates/README.md) |
| 可选 Python 记忆服务 | [memory-service/README.md](memory-service/README.md) |
| 来源与溯源 | [SOURCE_MAP.md](SOURCE_MAP.md) · [PROVENANCE.json](PROVENANCE.json) |
| 宣传片重新渲染 | [tools/promo/README.md](tools/promo/README.md) |

</details>

## 许可与致谢

本仓库的**代码**与**素材**分别授权，请勿把代码的 MIT 许可套用到角色素材上。

| 内容 | 许可 | 来源 |
|---|---|---|
| 应用代码 | [MIT](LICENSE) | 基于 [小小桌宠](https://github.com/yshuang722-spec/xiaoxiao-zhuochong)（MIT，[声明](THIRD-PARTY-LICENSE.txt)） |
| 记忆引擎 | MIT | 移植自 [agent-anthropomorphic-memory](https://github.com/ktao732084-arch/agent-anthropomorphic-memory) |
| 人设改编 | MIT | [dafeiyu-persona](https://github.com/ERUIHNIYHBKBNF/dafeiyu-persona)，见 [NOTICE](NOTICE-DAFEIYU.md) |
| 角色动画 | 允许开源使用，禁止商用 | [PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet) |
| 角色形象 | [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans) | 原型「溟月」[上善无形](https://space.bilibili.com/4456176) · 女仆鲸鱼娘设计 [ZipZipPipe](https://space.bilibili.com/4168597) |

因此本组合包**仅供非商业使用**，不得用于售卖、广告变现或付费分发。宣传片中的角色画面均取自上述动画，同样遵循非商业约束。完整说明见 [ASSET_LICENSE.md](ASSET_LICENSE.md) 与 [SOURCE_MAP.md](SOURCE_MAP.md)。

如果你是相关素材的权利人，请通过 [Issue](https://github.com/ktao732084-arch/dafeiyu-android/issues) 标明具体文件和权利依据，我们会核实并修正或移除。
