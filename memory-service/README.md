# 大肥鱼 × Agent 拟人记忆：可选开发联调

**0.4.0 起，记忆引擎已内置在手机，普通使用不需要本服务。** 本页保留Python对照方式；高级入口为「陪伴设置 → 记忆引擎 → 开发联调」。下文0.3.0启动方法适用于开发对照，不能再当作当前版本的前置安装要求。

0.3.0 将 `agent-anthropomorphic-memory` 的真实 Python 引擎接入 Android runtime。不是另一套仿写规则，也不是在 APK 中安装 Python。完整引擎运行在你自己的电脑或 HTTPS 服务上；Android 保持独立，不依赖 DSH / OpenClaw。

## 先在手机和电脑上体验

1. 安装 `dafeiyu-android-0.3.0.apk`。与 0.2.0 使用相同体验签名，可覆盖安装保留数据。不要卸载旧版来升级。
2. 电脑准备 Python 3.10+、Android platform-tools（adb）；手机启用 USB 调试并连接、授权电脑。
3. 在本目录执行：

```powershell
./start.ps1 -Adb 'D:/Android/Sdk/platform-tools/adb.exe'
```

脚本生成本次访问令牌，设置 `adb reverse tcp:8787 tcp:8787`，启动服务。令牌只显示在当前终端，不写入仓库。多设备时用 `-Serial '设备序列号'`。若不传 `-Adb`，请自行运行上述 `adb reverse`。

4. 手机进入 **陪伴设置 → 记忆引擎**，开启“连接完整记忆引擎”，填写 `http://127.0.0.1:8787` 和终端显示的令牌，点击“保存并测试连接”。
5. 在聊天中依次发送：`记住：我对花生过敏，很严重`、`记住：更正，我对花生不过敏了`。手账应显示当前说法，旧版本位于“共同经历与记忆变化”。这是测试台词，不是健康建议。
6. 普通聊天还需在“模型连接”配置聊天模型。`记住：` 路径可以不接聊天模型，直接验证两项目的记忆联调。

电脑服务停止、USB 断开、地址或令牌无效时，手机回退本地原话记忆；手账显示本轮状态。完整服务默认关闭，安装 APK 本身不会自动连接任何开发者服务器。

## 启用语义检索与智能写入

界面的两个开关是请求开关，模型由服务端配置。Python 的模型配置与手机的聊天模型配置互相独立；手机不会把聊天 Key 转发给记忆服务。下列变量应在启动服务的同一终端设置，示例没有真实凭据：

```powershell
# 用于判断 ADD / DUPLICATE / SUPERSEDE 的聊天模型
$env:OPENAI_BASE_URL='https://你的服务商/v1'
$env:OPENAI_API_KEY='在本机填写真实密钥'
$env:MEMORY_LLM_JUDGE_MODEL='你的聊天模型名称'

# 可选：独立的 embeddings 接口，不能假设聊天接口也支持它
$env:MEMORY_EMBEDDING_PROVIDER='openai'
$env:MEMORY_EMBEDDING_BASE_URL='https://你的向量服务商/v1'
$env:MEMORY_EMBEDDING_API_KEY='在本机填写向量服务密钥'
$env:MEMORY_EMBEDDING_MODEL='你的向量模型名称'
./start.ps1 -Adb 'D:/Android/Sdk/platform-tools/adb.exe'
```

也可使用上游支持的本地 ONNX 模型，在电脑安装 `onnxruntime`、`tokenizers`、`numpy`，设置：

```powershell
$env:MEMORY_EMBEDDING_PROVIDER='onnx'
$env:MEMORY_EMBEDDING_MODEL_DIR='D:/Models/your-onnx-model'
```

目录需包含模型匹配的 `model.onnx` 和 `tokenizer.json`，具体要求见 `upstream/src/embeddings.py`。模型权重未打包，本次未验证真实 ONNX 权重效果。没有配置模型时，时间解析、更正词识别、字面去重和关键词检索仍可运行。LLM 判断失败时保留规则路径；真实模型的误判仍需要持续评测。

## 数据与连接

- 手机是主存储：用户原话、来源、活跃记忆、历史归档、整理记录和向量缓存随对话保存。手机内置引擎上限为500条活跃记忆、1000条归档、512条整理记录，工作状态最多4MiB；本页Python对照协议仍限120条活跃记忆、240条归档。超过协议容量时手机使用内置引擎，不发送无法接受的请求。
- 请求只携带记忆快照、本轮查询和待记原话，不携带人格、聊天模型 Key、完整聊天记录或动画资源。模型调用由自己的服务配置决定。
- 服务每次在独立子进程和临时目录处理，正常完成与工作进程超时均由父进程清理，不维护另一个持久用户记忆库。它不会自动同步你其他 CLI/OpenClaw 用户档案。
- 向量缓存回到手机，下次请求复用。删除记忆会清除关联版本、直接来源对话、相应整理记录、整个向量缓存和恢复备份；已经自行导出的文件、外部模型服务商的数据不由 App 删除。
- 停止请求、清空记忆或更改配置后，迟到结果不能提交。服务响应须通过原话、来源、ID、容量和协议校验，失败就保留本地数据。
- HTTP 仅限手机 loopback，经 USB 转发。其他地址必须 HTTPS；服务器绑定非 loopback 必须提供 `--certfile` 与 `--keyfile`。手机要求系统信任的有效证书，不绕过 TLS 校验。

## 复现两侧测试

```powershell
python -X utf8 upstream/tests/test_companion_api.py
python -X utf8 upstream/eval/run_companion_eval.py --json protocol-result.json
```

Android 测试：构建 APK 后运行仓库 `tests/robolectric/run-tests.ps1`。其中 `MemoryIntegrationTest` 会启动真实 Python HTTP 服务，用 Android 的 `MemoryBridge` 重放同一批 84 个场景，并与 `protocol-baseline.json` 逐项核对。

规则模式目前为 **145/166** 个产品期望满足；其余 **21 项是公开的已知不足**。跨端一致性通过不等于 166 项产品期望全部通过。模拟模型接口测试只验证接线、错误处理和状态转换，不是云模型质量评测。

## 同步上游

`UPSTREAM.json` 记录源码快照版本和文件 SHA-256；`upstream/LICENSE` 保留 MIT 许可。快照包含新增的 Android 服务适配器与评测入口。更新时先检出明确的上游提交，再运行：

```powershell
python tools/sync-memory-service.py --source '../agent-anthropomorphic-memory'
```

随后重跑 Python、Android 的共享场景和生命周期回归，再更新基线与来源清单。不要为了让测试通过修改原始场景的预期。

上游 `9862c93` 报告语义检索 + DeepSeek 实测达到164/166，并将 `MEMORY_LLM_JUDGE_MAX_TOKENS` 默认值调整为2048；本包已纳入该修复。164/166 是上游报告，本次未用真实云模型独立复测，详见随包 `upstream/eval/README.md`。
