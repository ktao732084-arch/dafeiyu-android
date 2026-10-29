# 0.4.6 Android 正则兼容性修复验证

追加兼容性检查：同一已发布产品DEX在Android API35上通过11组核心套件；独立测试App通过22项实际Android Runtime/Keystore检查。未发现新的同类兼容性错误，未改产品或重发APK。范围和边界见[追加检查报告](docs/ANDROID_COMPATIBILITY_AUDIT.md)。

2026-10-29，versionCode 10，APK SHA-256：e08152f52db00644405568c3bd932018ce961b0713990bc8c97d8332e7dffeef。

## 原因与修复

用户发送“我叫k你以后就叫我kk吧”触发姓名记忆提取，0.4.5 的 NativeMemoryEngine.pattern 显式传入 UNICODE_CHARACTER_CLASS，Android 抛出 IllegalArgumentException。NativeMemoryTime 的静态编译与内嵌 (?sU) 同样不兼容。Android 本身默认采用 Unicode 字符类，桌面 JVM 则需显式开启。依据 [Android Pattern 文档](https://developer.android.com/reference/java/util/regex/Pattern#UNICODE_CHARACTER_CLASS)，新增 UnicodeRegex 对运行环境能力只探测一次：Android 使用默认行为，桌面 JVM 保留 Unicode 标志；日期已解析后缀改为同一兼容入口，保留 DOTALL。

此前 Robolectric 使用宿主 JVM 的正则实现，因此75项通过并未证明 Android 正则兼容。此版增加实际 Android ART 检查，不再只靠桌面测试判断该问题。

## 验证结果

- 在当前 MuMu 模拟器 Android API35 / x86_64 的 app_process 中，旧0.4.5 DEX 复现原异常；新0.4.6 DEX 的同一套83项检查通过，进程正常退出。覆盖原句、记忆写入、全部记忆规则编译、全角数字/空格、Unicode单词、大小写、敏感字段过滤、全角日期与已解析后缀。
- 同一83项检查在桌面 JVM 通过；Android不接受该标志而桌面接受，两个分支均实测。
- 当前构建43项Robolectric回归通过：NativeMemoryTest 12（含原句发送完成与保存）、HistoryRecallIntegrationTest 8、UiTest 14、ConversationTest 9。模型为受控响应，未用用户Key。
- 核心141项、完整历史/时间戳/分段556项、日期检索38项、API地址13项、动作10708次断言、往事检索11项、日记19项与物理回归通过。
- 原生共享记忆84场景保持145/166，21项已知不足未变化；规则提炼盲测仍为22/25、误记2/25。未因这个修复重复运行未改变的Python HTTP对照套件。
- APK编译与v2/v3签名校验通过，沿用旧版签名，支持覆盖安装。

Android测试只在独立临时进程使用产品DEX与合成输入，没有读取或修改已安装App的数据、Key、配置或未发送草稿。实际聊天Runtime路径由受控模型框架测试覆盖；未宣称已用真实DeepSeek完成该句聊天。实体手机及其他Android版本仍待体验。

证据：[Android结果](docs/test-reports/android-regex-results.txt)、[框架结果](docs/test-reports/robolectric-results.txt)、[汇总](docs/test-reports/validation-result.json)。之前完整验证见 [v0.4.5报告](https://github.com/ktao732084-arch/dafeiyu-android/blob/v0.4.5/VALIDATION.md)。

复现脚本：`tests/run-regex-device.ps1 -Sdk <SDK路径> -Jdk <JDK路径> -Serial <adb设备>`；先构建产品。脚本只在设备`/data/local/tmp/dafeiyu-regex-*`内放置临时DEX并运行，不安装、清空或操作用户App。
