# 代码许可与素材许可分开

本仓库不是“所有内容统一 MIT”。[LICENSE](LICENSE) 保存代码的 MIT 声明；视觉素材、形象及上游提示词的独立声明优先。

## 蓝毛角色动画和形象

- 本次直接获取路径：小小桌宠固定快照 [`ccdeb610.../android/assets/main`](https://github.com/yshuang722-spec/xiaoxiao-zhuochong/tree/ccdeb610972723ad11e9b4d4794292be138d21b6/android/assets/main)。
- 上游动画项目：[PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet)。其 README 许可部分区分代码 MIT 与素材非商业条款：动画、提示词、源视频允许开源使用、禁止商用，并要求公开衍生项目保留原仓库地址。
- 上游列出的形象出处：原型“溟月”为 [上善无形](https://space.bilibili.com/4456176)，女仆鲸鱼娘设计为 [ZipZipPipe](https://space.bilibili.com/4168597)。上游引述的形象许可为 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)，并链接[原始声明](https://www.bilibili.com/opus/1247156596114456581)。
- 本项目保留这些署名和限制，不主张第三方角色、动画的原创或额外授权；再分发时应同时保留本文件和来源说明。

因此本组合包仅供非商业使用，不应直接用于售卖、广告变现或付费分发。这里转述的是相关项目与作者的许可声明，不是授予超出原声明的新权利。

## 原有安卓界面图像

`res` 下原有 PNG、角色预览图等来自小小桌宠基线，按各自原作者/来源说明使用，不因本仓库有 MIT 文件就另行改授 MIT。新增的代码原生矢量图标 `res/drawable/fish_mark.xml` 则随新增代码采用 MIT。

## 人格文本

- dafeiyu-persona 的原始文本和改编来源按该项目 MIT 声明标注，原文见 `PERSONA-UPSTREAM.md`，许可保留于 `NOTICE-DAFEIYU.md`。
- 本版5种初始性格是新增表达预设，参考共享社区角色风格；不是对第三方角色形象商用权的授权。

本项目是非官方社区二创，与 DeepSeek 无官方隶属或背书关系。若是相关素材权利人，请通过仓库 Issue 标明具体文件和权利依据，便于核实并修正或移除。


## 新增后腰悬吊姿态

`assets/main/waist-lift-v1.png` 是本轮使用内置 image_gen、参考原角色预览图生成的衍生姿态。它不属于原上游106段视频，不标成原动画作者的新作品；角色原有署名与非商业/相同方式共享说明继续保留。生成提示词、引用素材和定位信息见 `docs/WAIST_LIFT_ASSET.md`。其配套 `WaistLiftView.java` 是本项目新增渲染代码。
