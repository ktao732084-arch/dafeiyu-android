# 0.4.3 模型配置验证

日期：2026-10-28。Android版本0.4.3 / versionCode 7。APK SHA-256：`79e0a8583cdf6764fc623468aaf4293d6cd70a7943c6d668ca0bfdf6d7c62e97`。

新安装默认使用DeepSeek V4.1 Flash，只填写Key，测试成功才保存。已有自定义配置原样保留。地址、模型别名与非思考参数依据[官方接入文档](https://api-docs.deepseek.com/zh-cn/)及[思考模式文档](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)。没有使用真实Key调用付费模型。

| 检查 | 结果 |
|---|---|
| Android14/API34 Robolectric | 58个场景通过，0失败/错误/跳过，含33项人格子检查 |
| 配置专项14场景 | 新手只显示Key及官方入口；测试后加密保存预设；失败保留旧配置并隐藏Key；服务商切换隔离Key；保留旧自定义配置；关闭窗口禁止迟到保存；45秒超时可重试；Flash非思考参数与错误提示；余额显示/刷新/缓存清理、查询失败不阻断已验证的连接、第三方Key隔离、余额不足不保存、取消防回写、多币种与异常金额解析 |
| 原生记忆 / Python HTTP 对照 | 共享84场景均保持145/166，21项既有不足保留；手机正常运行不需要Python |
| JVM核心 / 接口 / 作息 | 124项 / 13项 / 10708次作息断言通过；物理回归通过 |
| APK | 编译与v2/v3验签通过；与旧版证书一致，可覆盖安装 |

证据：[完整测试清单](docs/test-reports/robolectric-results.txt)、[XML](docs/test-reports/robolectric-results.xml)、[汇总](docs/test-reports/validation-result.json)。

连接检测共用实际聊天与记忆的传输代码；预设测试使用受控模型替身，HTTP传输使用本地测试服务。Key加密测试使用Keystore测试Provider。框架测试不能代替真机验证。

未验证：用户真实DeepSeek账号/余额/Key、真实服务回复质量、真机安装与视频、系统Keystore、厂商后台和耗电。API需要可用余额，模型辅助记忆也会增加请求；不提供内置离线大模型。

上版作息/人格/记忆验证保留在 [0.4.1记录](docs/VALIDATION-0.4.1.md)。
