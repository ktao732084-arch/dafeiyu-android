# 旧记忆兼容分支合并核对

2026-10-29，将 `fix/power-and-model-compat` 剩余提交归入主分支历史，保留现行产品实现。

此分支的省电/模型兼容基础提交已在更早的整合中采用。未进入主分支祖先链的两个提交是 `e3eee18`（日期换算与到期关心）和 `10a36b9`（手机端规则引擎）。它们所处的旧架构已由后续原生记忆实现替代，不能把旧版 Runtime、存储和提示词覆盖到当前版本。

| 分支中的内容 | 主分支现行实现 |
|---|---|
| LocalEngine、MemoryRules 与导出规则 | NativeMemoryEngine、NativeMemoryRules、generate-native-memory-rules.py；MemoryBridge 无服务时调用原生引擎 |
| TimeGround 日期换算 | NativeMemoryTime 与 UnicodeRegex，包含已修复的 Android Unicode 兼容处理 |
| 到期事项进入上下文 | MemoryLedger.dueEvents/followUps 与 PromptBuilder 的有来源摘录 |
| 主动关心后不重复询问 | AgentStore.markFollowed 与展示状态联动；继续保留 InitiativePlan 忙闲、未回应主题和调度验证 |
| 旧规则回放 | NativeReplayTest、NativeRegexTest 和 Android DeviceCoreChecks |

合并解法：所有产品代码、构建、CI、现行说明均使用当前 main；只新增本核对文档。旧的 LocalEngine/TimeGround 等重复文件不重新引入。相对 main 的差异仅此文件，因此本 PR 不改变运行行为、版本号或测试基线，也不发布安装包。
