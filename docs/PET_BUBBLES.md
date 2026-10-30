# 桌面气泡与长按菜单 · 2026-10-29

基于 0.4.9.4 / code18（6604b7e），本轮保留版本号，未发布。以下记录为本轮第一阶段的气泡验收；随后新增点击/提起交互，最终产物与补充验证见 [PET_LIFT.md](PET_LIFT.md)。实现位于 `PetBubbles.java`、`PetService.java`、`PetMenu.java`；逐句队列使用 `SpeechQueue.java` 与 `ReplySegments.java`。

## 交互

- 普通回复、互动回应和本地问候逐句进入独立气泡；短句也分开。长内容仍保存到聊天，桌面提示不带点击入口。
- 最多三条，新句靠近宠物，旧句向外移动并降低透明度、略微缩小。满三条时最旧条用 220ms 淡出，移除后再插入新条，动画期间也不出现第四张卡。新句约 360ms 弹入；系统关闭动画时直接完成切换。
- 渐变底色、细描边、柔和阴影；最新条带指向宠物的小尾巴。短句按内容宽度收缩。顶部空间不足时显示在宠物下面；可用高度不足时提前移除旧条，优先显示最新内容。
- 普通气泡无「查看聊天记录」文案，也不点击跳转。章节剧情仍能点最新气泡继续；明信片保留独立内容与关闭行为。
- 长按显示「聊天 / 喂食 / 动作 / 更多」四宫格，44dp 关闭按钮、按压缩放与触感反馈。点外部、关闭按钮、隐藏宠物、锁屏或回到应用时关闭。重复打开不叠窗，长按成立后不再将同一手势识别成拖动。
- `segmentVersion=4` 只影响新回复，v1/v2/v3 展示计数按各自原算法解释。逐句插入后才更新展示回执；取消淡出中的待插入句子不会把它记成已展示。

## 验证

- 产品构建、DEX 转换及测试签名校验通过。气泡阶段产品 DEX SHA-256：`47dd76b80afce1cadfb45dc6b0cb45632222c196630957bf071c88b1f486e45d`。
- Android 15 / API35 模拟器，独立包 `com.dafeiyu.bubblechecks`；未清理或覆盖正式应用数据。
- `BubbleInstrumentation`：59 项通过。覆盖单句、三句、第四句淡出/替换、同一浮窗复用、取消后不插入、不虚报回执、顶部边缘、真实字体测量、24sp 放大文字与夜间配色、本地两句顺序出现、真实长按、去重、外部关闭、聊天按钮接线、关闭按钮、隐藏清理。
- `AgentInstrumentation`：61 项 Android 运行时检查通过；`TouchReplyInstrumentation`：12 项真实触摸路径检查通过，使用受控模型。
- `tests/run-core-device.ps1`：使用最终产品 DEX，19 组通过，0 失败；其中历史/分段 639 项，包括短句拆分和旧版本展示边界。
- Robolectric API34 定向回归：59 项通过，0 失败/错误/跳过。类：`ChatPolishTest`、`CompanionBehaviorTest`、`ConversationTest`、`GrowthUiTest`、`InitiativeRuntimeTest`、`TouchReplyRuntimeTest`。
- 已查看模拟器截图：三气泡、第四句替换、长按菜单、夜间放大文字。截图使用合成台词与测试背景；没有使用真实聊天或模型 Key。
- 检查最终 DEX 的新增 UI API 引用；`setFitInsetsTypes` 和 `setCanPlayMoveAnimation` 保留 API30/API34 保护，动画开关读取沿用最低 API26 支持的实现。

未覆盖实体手机、不同厂商浮窗策略、低端设备动画性能、系统字体缩放的全部档位，以及外部 Python 服务全套集成。原记忆引擎已有的语义指标限制不因本轮回归通过而消失。
