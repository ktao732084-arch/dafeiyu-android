# 0.4.6 Android 兼容性追加检查

日期：2026-10-29。检查对象为已发布0.4.6，产品提交`4867a051be986a20a8d4af9ddf4d6cd0e783ec25`，APK SHA-256为`e08152f52db00644405568c3bd932018ce961b0713990bc8c97d8332e7dffeef`。

本轮未发现新的、类似Unicode正则参数导致消息处理中断的兼容性错误。只增加验证脚本与报告，不改产品代码、不升版本、不重发APK。结论限于下列路径和环境，不代表覆盖所有手机与系统版本。

## 实际 Android 核心回归

MuMu模拟器 Android API35 / x86_64、ART（VM名称Dalvik）。使用已发布产品对应DEX，在独立`app_process`进程里运行既有测试，而非Robolectric或桌面JVM。系统JSON、正则、时间、文件与集合实现来自Android本身。

| 套件 | 结果 |
|---|---|
| Agent核心 | 141项通过，含文件保存、重启、备份恢复、更正和删除 |
| 运动 | 动量、分离、拖动、边缘与抛掷检查通过 |
| API地址 | 13项通过 |
| 作息动作 | 10708次断言通过 |
| 分段、完整历史、时间戳 | 556项通过 |
| 共享记忆场景 | 84场景，145/166，21项既有不足无退步 |
| 按时间回忆 | 38项通过 |
| 全历史关键词回忆 | 11项通过 |
| 规则提炼 | 调参47/48、误记0/60；盲测22/25、误记2/25；共享事实113/114、隐私0，保持基线 |
| 日记 | 19项通过 |
| Unicode正则 | 83项通过 |

11组套件通过，0组失败。`tests/run-core-device.ps1`负责构建测试DEX、放入合成语料与运行；临时状态只写在该次测试的`/data/local/tmp/dafeiyu-core-*`目录。

## 实际 Android Runtime 与 Keystore

`instrumentation/build-isolated-tests.ps1`将产品classes、资源和AgentInstrumentation打进独立包`com.dafeiyu.compatchecks`，其UID、Keystore、偏好与数据空间均独立于正式App。无网络权限，模型为受控回复，Python客户端被禁止调用。

22项检查通过，包括：Android Keystore AES/GCM加解密、密文保存、人格提示加载、流式文本过滤、记忆写入与召回、关闭记忆、取消/失败不写回、离线明确记忆、原始姓名句发送完成并保存，以及AlphaVideo缓冲区初始化和头像资源读取。运行后已卸载测试包，未读取或修改正式App的Key、聊天、配置、草稿。

这不是实际云端模型质量测试，也不等同完整视频播放验收；视频验证只覆盖缓冲区初始化，未覆盖所有EGL、解码和后台恢复行为。

## API与构建产物检查

扫描产品classes的2432个不同字段/方法引用，并对照Android SDK34的`data/api-versions.xml`。高于最低API26的调用集中在通知组(API28)、窗口边距/键盘(API30)、毛玻璃(API31)、窗口移动动画(API34)，已逐处检查相应SDK_INT保护。没有发现缺少保护的上述调用。

另核对两个标准库可疑引用：

- FloatBuffer.position的宿主class引用返回FloatBuffer，但**最终DEX已由D8改写为返回Buffer**，与[Android API](https://developer.android.com/reference/java/nio/FloatBuffer#position(int))一致。独立Android测试中实际初始化AlphaVideo并操作缓冲区通过，不把class级疑点误报为已发布包缺陷。
- StringBuilder.setLength属于API清单继承/桥接表示的差异；Android核心测试中相关文本处理通过，未发现方法链接错误。

记忆Unicode参数只由兼容入口UnicodeRegex处理；日期解析中的不支持内嵌U标志已在0.4.6移除。通知组API28保护与[官方说明](https://developer.android.com/reference/android/app/NotificationChannelGroup#isBlocked())一致。

## 重跑

先构建产品，再运行：

```powershell
./tests/run-core-device.ps1 -Sdk <SDK路径> -Jdk <JDK路径> -Serial <adb设备>
./instrumentation/build-isolated-tests.ps1 -Sdk <SDK路径> -Jdk <JDK路径> -KeyStore <测试签名路径>
adb -s <设备> install instrumentation/build/isolated/isolated-checks.apk
adb -s <设备> shell am instrument -w com.dafeiyu.compatchecks/com.xiaoxiao.pet.AgentInstrumentation
adb -s <设备> uninstall com.dafeiyu.compatchecks
```

构建脚本沿用`XIAOXIAO_STORE_PASS`环境变量。仅安装/卸载上述独立测试包；不要清空正式App。首次安装前检查没有同包名的既有应用，避免覆盖他人测试环境。

## 尚未覆盖

Android API26–34的实际ART设备矩阵、实体手机、厂商后台和电池策略、完整GPU/视频播放，以及真实DeepSeek输出。本轮API35结果不能替代这些环境。既有21项记忆评测不足与盲测2/25误记继续保留，不归为已修复。
