# 来源与许可

- 0.3.0 完整记忆引擎：[agent-anthropomorphic-memory](https://github.com/ktao732084-arch/agent-anthropomorphic-memory)，MIT。固定提交和逐文件来源见 `memory-service/UPSTREAM.json`，许可原文为 `memory-service/upstream/LICENSE`。服务适配器及部分校验增强为本次集成新增，已回提 PR #4。

- Android 底座：[yshuang722-spec/xiaoxiao-zhuochong](https://github.com/yshuang722-spec/xiaoxiao-zhuochong)，保留原 MIT 声明，见 `THIRD-PARTY-LICENSE.txt`。本次修改包括独立应用身份、主界面、人格、记忆、网络流式和运行时。
- 桌宠引擎与蓝毛动画上游：[PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet)。其声明区分：代码 MIT；动画、提示词与源视频允许开源使用、禁止商用。公开二创应附原仓库地址。
- 透明视频实现路线参考（由安卓底座标注）：[OTFiles/dsh-pet-inAndroid](https://github.com/OTFiles/dsh-pet-inAndroid)。
- 人格改编来源：[ERUIHNIYHBKBNF/dafeiyu-persona](https://github.com/ERUIHNIYHBKBNF/dafeiyu-persona)，MIT。原文保留为 `PERSONA-UPSTREAM.md`，新安装实际从 `assets/personas.json` 初始化并冻结人格；`assets/persona-default.txt` 与目录中的原人设用于原版默认风格及旧数据迁移；未执行上游 Skill，也未加载任何 DSH 运行环境。
- 形象为社区二创，不代表 DeepSeek 官方。上游将原型归于 B 站「上善无形」、女仆鲸鱼娘设计归于「ZipZipPipe」，并注明 CC BY-NC-SA 4.0；以对应作者原始声明为准。不要把本包代码许可套用于第三方角色素材。
- 新增的5种初始性格由本版编写，并非5个独立开源插件；初始化后固定。新增向量图标与独立运行时代码随代码采用 MIT。此组合包保留素材非商业约束，不宣称整个包均为 MIT。

人格项目 MIT 许可原文：

MIT License

Copyright (c) 2026

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
