# 宣传片

`render_promo.py` 用 `assets/main` 里的角色动画合成宣传片，`soundtrack.py` 现场合成配乐与音效（不使用第三方音乐）。

```bash
python3 tools/promo/render_promo.py --font-dir <字体目录> [--phone-dir <真机录屏目录>]
```

- 字体目录里放同一套中文字体的 400 / 700 / 900 三个字重（文件名含 `400`、`700`、`900`，woff2/ttf/otf 均可），例如 Noto Sans SC。缺字会直接报错。
- 输出到 `docs/promo/`：`dafeiyu-promo.mp4`（1280×720，带声音）、`dafeiyu-promo.webp`（README 用的无声动图）、`cover.png`。
- 依赖 ffmpeg、numpy、Pillow。

## 加入真机录屏

`--phone-dir` 指向一个文件夹，放 1～2 段手机录屏（`*.mp4`，按文件名排序），会在标题之后插入 7 秒「真机实拍」，并排显示在手机外框里，后面的段落和配乐自动顺延。

录制建议：

- 竖屏、系统自带录屏即可；每段剪成 6～8 秒，片头片尾不要停在录屏控制条上。
- 推荐镜头：她浮在桌面或其他 App 上待机、轻点后的回应动作、长按菜单喂食、拖动她到屏幕边缘、聊天页短气泡逐条出现。
- **不要拍到真实聊天、API Key、余额、通知内容和个人信息**（见 [AGENTS.md](../../AGENTS.md)「真实聊天截图与Key不放公开仓库」）。建议开勿扰模式，用全新安装或专门的演示对话。
