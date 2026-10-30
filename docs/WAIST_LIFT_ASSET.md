# 后腰悬吊姿态素材

- 制作方式：内置 image_gen（imagegen 技能），参考本项目原角色 `assets/main/portrait.png`，新增姿态；未替换原视频。
- 项目文件：`assets/main/waist-lift-v1.png`，1448×1086 RGBA，保留生成结果的透明通道。
- 运行时选取有效精灵区域 `(356,218)–(1130,902)`，后腰围裙结锚点 `(900,430)`、脚部落点 `(933,874)`；它们是渲染定位信息，不是对原 PNG 的覆盖编辑。
- 该文件是一张独立姿态图，应用用网格轻微形变、摆动和惯性偏转做悬吊反馈；不是新增一段视频或三维骨骼模型。
- 原角色参考素材的来源与权利说明继续适用；本 PNG 为本轮 AI 辅助生成的衍生姿态，不声称出自原素材作者。

## 实际使用的生成提示词

```text
Use case: stylized-concept. Asset type: transparent full-body pose sprite for an existing Android desktop pet. Input 1 is identity/style reference: the existing tiny blue whale-girl maid chibi. Create ONE new pose of exactly this character, preserving her large blue eyes, dark indigo wavy hair, whale-like blue ear fins, white lace maid headband, dark navy maid dress with white apron and blue whale emblem, small dark shoes, original soft cel shaded 3D-chibi game-sprite look and proportions. Primary request: she is being gently lifted BY THE FABRIC AT THE BACK OF HER WAIST, as if a cursor pinches her rear waist/apron bow. The rear waist fabric is visibly gathered upward at one clear suspension point near upper center-right of the silhouette. Her torso tips FORWARD, her large head hangs lower and to the left of that back-waist point, both arms and both feet dangle down with gravity; hair falls downward. Three-quarter side view facing left/front so her recognizable mildly annoyed/cute face remains visible. This must be a distinctly bent-forward hanging pose, NOT an upright standing pose rotated diagonally, NOT lifted by the head, NOT held by neck. Her full modest maid outfit remains in place. No hand, cursor, rope, hook, extra character, text, backdrop, floor or ground shadow. SINGLE sprite, not a sheet, ample transparent margin around all hair and limbs, no cropping. True transparent RGBA background. Crisp cohesive silhouette legible at about 150 pixels tall. Keep palette and face faithfully matched to the reference rather than redesigning the character.
```
