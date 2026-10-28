# 0.3.0 验证记录

日期：2026-10-28。记忆源码快照：`b6718dfedcfb17a31f5c3d8d32d71ffc2665101a`；包括上游最新 `9862c93`。
APK SHA-256：`b9552608d863204c8785c8f60c7ac5db9c7d60a413cda1a2a04f93a450a2244c`。

| 检查 | 实际结果 |
|---|---|
| Android 14/API34 Robolectric | 26个场景通过，0失败/错误/跳过；含33项人格子检查 |
| Android → 真实 Python HTTP 服务 | 更正归档、持久化重启、原话来源校验、删除/清空并发保护、失败降级、设置加密、禁用记忆、只读历史均通过 |
| 两侧共享84场景 | Android通过真实HTTP逐项重放；166项结果与Python基线一致，产品期望满足145项，21项已知不足保留 |
| Python服务专项 | 16项通过；包括真实HTTP、时区/迁移、隔离、缓存、受控 embeddings/LLM 接口和降级 |
| LLM原有回归 | 9项通过，新增候选编号边界；最终答案、截断和2048-token预算另在服务专项中验证 |
| JVM核心 / 接口 / 物理 | 48项 / 13项 / 原有物理回归通过 |
| 构建与签名 | JDK17、API34、Build Tools36；APK v2/v3签名通过，与0.2.0沿用同一体验签名 |

证据：[安卓测试清单](docs/test-reports/robolectric-results.txt)、[可解析报告](docs/test-reports/robolectric-results.xml)、[共享场景逐项结果](docs/test-reports/memory-replay-android.json)、[汇总JSON](docs/test-reports/validation-result.json)。复现脚本在 `tests/` 与 `memory-service/upstream/tests/`。

## 结果的边界

- 26个场景通过是协议、界面和运行时逻辑验证。Robolectric加载真实编译类及APK资源，但不是完整模拟器，也不是APK安装证明。
- 聊天模型使用受控替身，Android Keystore使用测试内存Provider；真实AES/GCM代码被执行。Python记忆服务和HTTP连接是真实执行；额外模型接口用本地模拟服务验证。
- 规则模式仍有21项产品期望未满足，涉及隐含变化、同义表达与部分过时信息；跨端一致不等于这些能力已经解决。
- 上游 `9862c93` 报告语义检索+DeepSeek达到164/166，相关token预算修复已合入；这是上游的模型实测，本次没有独立复跑该真实模型和ONNX权重组合。
- 完整记忆需要启动自己的Python服务并配置相应模型；没有服务时仅使用手机本地原话记忆和已保存状态。服务不会自动同步其他Agent的个人档案。
- 未连接可用Android设备；真机安装、浮窗、透明视频、系统Keystore、前台服务、输入法、厂商后台策略和耗电仍待验收。此前完整模拟器因宿主硬件加速/QEMU问题未启动，详见[0.2.0记录](docs/VALIDATION-0.2.0.md)。

最终回归前曾出现一次异步测试超时；单独复跑通过后，测试夹具增加工作线程排空和显式paused looper，最终完整套件26项通过。此处保留验证过程，不将中间失败隐藏为一次全部通过。
