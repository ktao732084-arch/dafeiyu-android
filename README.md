# 大肥鱼 · 手机内置记忆版 0.4.0

[下载 APK](https://github.com/ktao732084-arch/dafeiyu-android/releases/tag/v0.4.0) · [来源清单](SOURCE_MAP.md) · [逐文件溯源](PROVENANCE.json) · [验证记录](VALIDATION.md)

记忆引擎已移植为安卓原生代码并打进 APK。存储、过滤、时间解析、去重、更正、归档和检索都由手机进程执行，默认启用；不需要电脑、Python 服务或 ADB。

模型辅助理解直接复用手机「模型连接」里已有的接口、模型和 Key。它用于理解同义表达、隐含变化与相关记忆，每轮可能增加模型请求；没有模型或网络时继续使用本机规则。这里没有内置一个离线大语言模型。

本项目基于小小桌宠和社区大肥鱼项目整合，不是 DeepSeek 官方产品。代码采用 MIT；角色动画等第三方资源保留非商业约束，见 [ASSET_LICENSE.md](ASSET_LICENSE.md)。

## 安装与使用

1. 在 Android 8.0+ 安装 `dafeiyu-android-0.4.0.apk`。与0.2/0.3沿用同一体验签名，可覆盖安装保留数据。
2. 初次相遇时，手动选择一种性格或随机一次。确认后固定，后期没有重选人格入口。
3. 「模型连接」填写自己的 HTTPS Chat Completions 兼容接口、模型名称和 Key，即可聊天。模型测试与聊天会使用自己的额度。
4. 「记忆手账」查看、编辑、固定或忘记记忆；「共同经历与记忆变化」查看历史。无需额外设置记忆服务器。
5. 可先不配置模型，用 `记住：我喜欢海边散步` 测试手机本地记忆。时间解析、明确更正和归档离线可用。
6. 想悬浮显示时，开启显示在其他应用上层和通知权限，再点「让她住到屏幕上」。

从0.3升级时，旧电脑服务地址不会自动激活。手机内置引擎是默认运行路径；人格与已保存的记忆保持原有数据。

## 手机端能力

- **本机运行**：`NativeMemoryEngine` / `NativeMemoryTime` / `NativeMemoryRules` 执行原生记忆处理，不启动 Python、不调用本地电脑端口。
- **依据与时间**：保留用户原话与直接来源，解析文本另存；按说话时的日期与时区解释相对时间，事件结束后归档。
- **变化与历史**：明确更正使旧记录失效，重复信息合并并保留来源；历史不作为当前事实，明确询问过去时才带历史标记引用。
- **模型辅助**：`NativeMemoryIntelligence` 默认复用已有聊天模型，选择相关记忆 ID，或判断新增/重复/取代。不能自行编造记忆原文、ID或设备操作；一批判断校验通过才应用，失败退回本机规则。
- **语义检索**：手机版默认使用当前聊天模型按含义选择记忆，不要求另配向量服务。关键词检索始终可在本机运行。Python/ONNX 向量方案保留用于可选开发对照，不是手机运行条件。
- **一致性**：聊天、记忆和情绪原子保存。取消、修改配置或清空记忆后，迟到结果不能写回。删除记忆清理关联版本、直接来源对话、相关整理记录、缓存和备份。
- **容量**：最多120条活跃记忆、240条历史归档、512条整理事件、160轮对话；状态以4MiB UTF-8限额控制，不承诺无限记忆。
- **人格与表现**：5种初始化性格，7种情绪映射到动画；名字和称呼可改，冻结的人格不会被记忆判断改写。
- **主动陪伴**：默认关闭；开启后遵守亮屏、可见、勿扰、23:00–08:00安静时间、至少30分钟间隔及每天最多3次等限制。后台被系统结束后需手动重新开启。

「陪伴设置 → 记忆引擎」默认展示手机内置能力和模型辅助开关。可选的 Python 对照入口放在「开发联调」中，说明见 [memory-service/README.md](memory-service/README.md)。

## 数据与边界

记忆和对话保存在 App 私有目录，关闭系统备份，Key 使用 Android Keystore AES-GCM 加密。启用模型辅助时会将查询和相关记忆发给你配置的模型服务商；无开发者中转服务器。手账导出不含 Key。自行导出的文件和模型服务商的数据不由 App 删除。

不包含语音、跨 App 读取、设备自动操作或人格自主训练。仍需真机安装、浮窗、视频解码、系统Keystore和耗电验收。Robolectric、离线规则评测与受控模型测试的边界见 [VALIDATION.md](VALIDATION.md)。

## 构建与复现

JDK17、Android SDK Platform34、Build Tools36.0.0、PowerShell7；无需 Gradle。签名私钥不随源码分发：

```powershell
$env:XIAOXIAO_STORE_PASS='你的签名密码'
./build.ps1 -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17' -KeyStore 'D:/Keys/fish.keystore'
./tests/run-core.ps1 -Jdk 'C:/Program Files/Java/jdk-17' -JsonJar 'D:/test-libs/json.jar'
./tests/robolectric/run-tests.ps1 -AndroidRoot '.' -Sdk 'D:/Android/Sdk' -Jdk 'C:/Program Files/Java/jdk-17'
```

产物为 `build/dafeiyu-0.4.0.apk`。原生规则由 `tools/generate-native-memory-rules.py` 从已固定的上游源码 AST 生成，不执行 Python 引擎；生成后的 Java 常量随 APK 编译。

`NativeMemoryTest` 在不启动Python服务的条件下运行84个共享场景，并覆盖默认设置、旧版升级、离线更正、模型配置复用、非法输出回退、批量原子性、时区和删除竞态。`MemoryIntegrationTest` 另保留Python HTTP对照；普通用户运行App不需要这些测试依赖。
