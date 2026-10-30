#!/usr/bin/env python3
"""Render the README promo for 大肥鱼 from the character clips already in assets/main.

Each clip is 1280x480: the left half is colour, the right half is the alpha mask. Scenes are drawn
with Pillow over a shared deep-sea background, piped into ffmpeg, then downscaled to an animated
image for the README.

    python3 tools/promo/render_promo.py --font-dir <dir with noto-sans-sc-{400,700,900}.(woff2|ttf|otf)>

Outputs docs/promo/dafeiyu-promo.mp4, docs/promo/dafeiyu-promo.webp (animated, for the README) and
docs/promo/cover.png.
Needs ffmpeg, numpy and Pillow. Fonts are not bundled; any CJK font with three weights works.
"""
import argparse
import glob
import math
import os
import random
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
CLIPS = os.path.join(ROOT, 'assets', 'main')
OUT = os.path.join(ROOT, 'docs', 'promo')
W, H, FPS = 1280, 720, 30
DURATION = 36.0

INK = (240, 248, 255)
MUTED = (178, 206, 236)
ACCENT = (126, 214, 255)
WARM = (255, 214, 140)
CARD = (12, 38, 78)


def ease(t):
    t = min(max(t, 0.0), 1.0)
    return 1 - (1 - t) ** 3


def fade(t, start, end, edge=0.45):
    """Opacity of something visible from start to end, with soft edges."""
    if t < start or t > end:
        return 0.0
    return min(ease((t - start) / edge), ease((end - t) / edge), 1.0)


class Fonts:
    def __init__(self, font_dir):
        def find(weight):
            for ext in ('woff2', 'ttf', 'otf'):
                hits = glob.glob(os.path.join(font_dir, f'*{weight}*.{ext}'))
                if hits:
                    return hits[0]
            raise SystemExit(f'no {weight} weight font in {font_dir}')
        self.paths = {w: find(w) for w in (400, 700, 900)}
        self.cache = {}

    def __call__(self, size, weight=400):
        key = (size, weight)
        if key not in self.cache:
            self.cache[key] = ImageFont.truetype(self.paths[weight], size)
        return self.cache[key]

    def check(self, texts):
        """Fail loudly instead of shipping tofu boxes."""
        font = self(40, 700)

        def glyph(c):
            img = Image.new('L', (60, 60))
            ImageDraw.Draw(img).text((5, 5), c, font=font, fill=255)
            return img.tobytes()
        tofu = glyph('')
        missing = {c for text in texts for c in text if not c.isspace() and glyph(c) == tofu}
        if missing:
            raise SystemExit('font is missing glyphs: ' + ''.join(sorted(missing)))


class Clip:
    """Streams one character animation as RGBA frames at a fixed scale, looping when it runs out."""

    def __init__(self, name, height, start=0.0):
        self.path = os.path.join(CLIPS, name + '.mp4')
        self.h = int(height)
        self.w = int(round(self.h * 640 / 480))
        self.start = start
        self.proc = None

    def _open(self):
        self.proc = subprocess.Popen(
            ['ffmpeg', '-v', 'error', '-ss', str(self.start), '-i', self.path,
             '-vf', f'fps={FPS},scale={self.w * 2}:{self.h}:flags=lanczos',
             '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'],
            stdout=subprocess.PIPE)

    def next(self):
        size = self.w * 2 * self.h * 3
        for _ in range(2):
            if self.proc is None:
                self._open()
            raw = self.proc.stdout.read(size)
            if len(raw) == size:
                frame = np.frombuffer(raw, np.uint8).reshape(self.h, self.w * 2, 3)
                rgba = np.dstack([frame[:, :self.w], frame[:, self.w:, 1]])
                return Image.fromarray(rgba, 'RGBA')
            self.proc.wait()
            self.proc = None
        raise RuntimeError('cannot read ' + self.path)

    def close(self):
        if self.proc:
            self.proc.kill()
            self.proc = None


class Ocean:
    """Deep-sea backdrop that runs across every scene so the cuts feel like one dive."""

    def __init__(self):
        y = np.linspace(0, 1, H)[:, None, None]
        top, mid, bottom = np.array([34, 108, 176]), np.array([10, 50, 104]), np.array([3, 14, 38])
        grad = np.where(y < 0.45, top + (mid - top) * (y / 0.45), mid + (bottom - mid) * ((y - 0.45) / 0.55))
        self.base = Image.fromarray(np.repeat(grad, W, axis=1).astype(np.uint8), 'RGB')
        rng = random.Random(7)
        self.bubbles = [(rng.uniform(0, W), rng.uniform(0, H + 200), rng.uniform(3, 13),
                         rng.uniform(30, 90), rng.uniform(0, 6.28)) for _ in range(46)]
        self.rays = [(rng.uniform(80, W - 80), rng.uniform(60, 160), rng.uniform(0, 6.28)) for _ in range(5)]

    def frame(self, t, depth):
        """depth 1 = the dark sea floor of the opening, 0 = near the surface."""
        img = self.base.copy()
        light = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(light)
        for x, width, phase in self.rays:
            sway = math.sin(t * 0.35 + phase) * 60
            a = int(26 + 12 * math.sin(t * 0.8 + phase))
            d.polygon([(x - width / 2 + sway, -10), (x + width / 2 + sway, -10),
                       (x + width * 1.6 + sway * 2, H), (x - width * 0.4 + sway * 2, H)], fill=(170, 225, 255, a))
        light = light.filter(ImageFilter.GaussianBlur(28))
        img.paste(light, (0, 0), light)
        bub = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(bub)
        for x, y0, r, speed, phase in self.bubbles:
            y = (y0 - t * speed) % (H + 200) - 100
            bx = x + math.sin(t * 1.3 + phase) * 10
            d.ellipse([bx - r, y - r, bx + r, y + r], outline=(200, 236, 255, 110), width=2)
            d.ellipse([bx - r * 0.45, y - r * 0.6, bx - r * 0.05, y - r * 0.2], fill=(230, 247, 255, 120))
        img.paste(bub, (0, 0), bub)
        if depth > 0:
            img = Image.blend(img, Image.new('RGB', (W, H), (2, 8, 22)), depth * 0.82)
        return img.convert('RGBA')


class Canvas:
    def __init__(self, img, fonts):
        self.img = img
        self.fonts = fonts

    def layer(self, alpha, draw_fn):
        if alpha <= 0.01:
            return
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        draw_fn(ImageDraw.Draw(layer), layer)
        if alpha < 0.999:
            a = layer.getchannel('A').point(lambda v: int(v * alpha))
            layer.putalpha(a)
        self.img.alpha_composite(layer)

    def text(self, xy, s, size, weight=400, fill=INK, alpha=1.0, anchor='la', shadow=True, dy=0.0):
        x, y = xy
        y += (1 - alpha) * dy

        def draw(d, _):
            f = self.fonts(size, weight)
            if shadow:
                d.text((x, y + 3), s, font=f, fill=(0, 10, 30, 150), anchor=anchor)
            d.text((x, y), s, font=f, fill=fill, anchor=anchor)
        self.layer(alpha, draw)

    def sprite(self, frame, foot_xy, alpha=1.0, scale=1.0):
        """Place a clip frame so her feet (about 400/480 down, centred) land on foot_xy."""
        if alpha <= 0.01:
            return
        if scale != 1.0:
            frame = frame.resize((int(frame.width * scale), int(frame.height * scale)), Image.LANCZOS)
        if alpha < 0.999:
            frame = frame.copy()
            frame.putalpha(frame.getchannel('A').point(lambda v: int(v * alpha)))
        x = int(foot_xy[0] - frame.width / 2)
        y = int(foot_xy[1] - frame.height * 400 / 480)
        self.img.alpha_composite(frame, (x, y)) if x >= 0 and y >= 0 and x + frame.width <= W and y + frame.height <= H \
            else self._clipped(frame, x, y)

    def _clipped(self, frame, x, y):
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        layer.paste(frame, (x, y), frame)
        self.img.alpha_composite(layer)

    def card(self, box, alpha=1.0, radius=22, fill=CARD, opacity=200, outline=(120, 190, 255, 70)):
        def draw(d, _):
            d.rounded_rectangle(box, radius, fill=fill + (opacity,), outline=outline, width=2)
        self.layer(alpha, draw)

    def bubble(self, x, y, s, mine, alpha, size=26, max_w=420):
        """A chat bubble; mine = the user's (right-aligned) side."""
        f = self.fonts(size, 400)
        lines, cur = [], ''
        for ch in s:
            if f.getlength(cur + ch) > max_w - 36:
                lines.append(cur)
                cur = ch
            else:
                cur += ch
        lines.append(cur)
        lh = int(size * 1.45)
        bw = int(max(f.getlength(l) for l in lines)) + 36
        bh = lh * len(lines) + 22
        bx = x - bw if mine else x
        y += (1 - alpha) * 14

        def draw(d, _):
            fill = (92, 176, 255, 235) if mine else (240, 247, 255, 235)
            color = (255, 255, 255) if mine else (20, 44, 80)
            d.rounded_rectangle([bx, y, bx + bw, y + bh], 20, fill=fill)
            for i, l in enumerate(lines):
                d.text((bx + 18, y + 11 + i * lh), l, font=f, fill=color)
        self.layer(alpha, draw)
        return bh


# ---------------------------------------------------------------------------------------------
# Story. Every line the character says comes from assets/personas.json or docs/GROWTH_DESIGN.md.

PERSONAS = [  # name, how she answers 「我回来了。」, the click reaction clip that suits her
    ('经典傲娇', '哦，回来啦。……这边给你留了点位置。', '073'),
    ('温柔陪伴', '嗯，你回来啦。想聊什么，我们慢慢说。', '075'),
    ('元气搭子', '欢迎回来！想聊两句，还是一起玩点什么？', '076'),
    ('冷静可靠', '欢迎回来。有什么想接着聊的？', '074'),
    ('古灵精怪', '欢迎回来，新的小泡泡可以开场啦。', '077'),
]
ACTIONS = [('吃 Token', '023'), ('小提琴演奏', '047'), ('放风筝', '067'), ('撸猫', '062'), ('堆雪人', '043')]
CHAPTERS = [('初遇', '让她记住你的一件事'), ('熟悉', '陪她下完一局五子棋'), ('默契', '一起见面满 14 天'),
            ('信任', '下赢她一局五子棋'), ('羁绊', '她想起自己原本的样子')]
CHAT = [
    (True, '你这条大肥鱼。'),
    (False, '哼，这叫圆润。再叫一次，零食就不分你了。'),
    (True, '我不喜欢香菜，你记错了。'),
    (False, '啊，是我记岔了。……不喜欢香菜，这回记牢了。'),
    (True, '我们上周聊过什么？'),
    (False, '上周三你说面试过了，我还让你奖励自己点好吃的。'),
]
WHALE_LINE = '原来我本来就这么大，才不是肥，只是还没长回去。'
REPO = 'github.com/ktao732084-arch/dafeiyu-android'
CREDIT = ['角色原型「溟月」：上善无形 · 女仆鲸鱼娘设计：ZipZipPipe（CC BY-NC-SA 4.0）',
          '角色动画：PC2005-cloud/dsh-pet · 安卓底座：小小桌宠 · 非官方社区二创，与 DeepSeek 无隶属关系']

ALL_TEXT = ['深海又暗又安静，', '她吐出的泡泡，总是空的。', '直到她顺着一束光，', '游进了你的手机。',
            '大肥鱼', '住在你手机里的鲸鱼娘陪伴伙伴', '桌面悬浮', '本机记忆', '五种性格', '五子棋', '开源',
            '她会记得你', '记忆引擎直接跑在手机里，', '会更正、会归档、会想起往事。', '聊天、记忆、Key 都留在本机',
            '示意画面', '五种性格，相遇时选定', '你说「我回来了。」', '一百多段动作，跟着作息与节日变',
            '清晨刷牙 · 午后犯困 · 夜里打盹 · 中秋赏月 · 冬天堆雪人', '五章任务线，攒满泡泡瓶',
            '每一段真实的相处，都会变成回忆泡泡', '第', '章', '现在下载', 'Android 8.0+ · 免费 · 覆盖安装保留数据',
            WHALE_LINE, REPO] + CREDIT + [s for _, s in CHAT] + [n + r for n, r, _ in PERSONAS] + \
           [a for a, _ in ACTIONS] + [a + b for a, b in CHAPTERS] + list('一二三四五')


def render(fonts, out_mp4):
    ocean = Ocean()
    clips = {}

    def clip(key, name, height, start=0.0):
        if key not in clips:
            clips[key] = Clip(name, height, start)
        return clips[key].next()

    def drop(*keys):
        for k in keys:
            if k in clips:
                clips.pop(k).close()

    enc = subprocess.Popen(
        ['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS),
         '-i', '-', '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-pix_fmt', 'yuv420p',
         '-movflags', '+faststart', out_mp4], stdin=subprocess.PIPE)

    total = int(DURATION * FPS)
    cover = None
    for i in range(total):
        t = i / FPS
        depth = 1 - ease((t - 2.5) / 3.0)
        c = Canvas(ocean.frame(t, depth), fonts)

        # 1. 0–5.2s  the deep sea
        if t < 5.4:
            c.text((W / 2, 250), '深海又暗又安静，', 44, 700, alpha=fade(t, 0.4, 2.9), anchor='mm', dy=20)
            c.text((W / 2, 320), '她吐出的泡泡，总是空的。', 44, 700, fill=MUTED, alpha=fade(t, 1.3, 2.9), anchor='mm', dy=20)
            a = fade(t, 3.0, 5.2)
            c.text((W / 2, 150), '直到她顺着一束光，', 44, 700, alpha=a, anchor='mm', dy=20)
            c.text((W / 2, 215), '游进了你的手机。', 44, 700, fill=ACCENT, alpha=fade(t, 3.4, 5.2), anchor='mm', dy=20)
            if t >= 2.9:
                rise = ease((t - 2.9) / 1.6)
                c.sprite(clip('s1', '101', 420), (W / 2, 690 + (1 - rise) * 260), alpha=a)
        else:
            drop('s1')

        # 2. 5.2–9.6s  title
        if 5.0 < t < 9.8:
            a = fade(t, 5.2, 9.6)
            c.sprite(clip('s2', '045', 600), (330, 690), alpha=a)
            c.text((620, 190), '大肥鱼', 128, 900, alpha=a, dy=30)
            c.text((626, 378), '住在你手机里的鲸鱼娘陪伴伙伴', 34, 700, fill=MUTED, alpha=fade(t, 5.6, 9.6), dy=20)
            x = 626
            for k, tag in enumerate(['桌面悬浮', '本机记忆', '五种性格', '五子棋', '开源']):
                ta = fade(t, 6.0 + k * 0.15, 9.6)
                f = fonts(24, 700)
                tw = f.getlength(tag) + 32
                c.card([x, 450, x + tw, 494], ta, radius=22, fill=(30, 90, 160), opacity=210, outline=(140, 210, 255, 120))
                c.text((x + tw / 2, 472), tag, 24, 700, alpha=ta, anchor='mm', shadow=False)
                x += tw + 12
            if i == int(7.6 * FPS):
                cover = c.img.copy()
        else:
            drop('s2')

        # 3. 9.6–15.2s  memory
        if 9.4 < t < 15.4:
            a = fade(t, 9.6, 15.2)
            c.card([90, 70, 640, 650], a, radius=34, opacity=170)
            c.text((620, 628), '示意画面', 18, 400, fill=MUTED, alpha=a * 0.8, anchor='rs', shadow=False)
            y = 100
            for k, (mine, s) in enumerate(CHAT):
                ba = fade(t, 9.9 + k * 0.62, 15.2, 0.3)
                y += c.bubble(610 if mine else 120, y, s, mine, ba, size=23, max_w=440) + 14
            c.text((720, 150), '她会记得你', 64, 900, alpha=a, dy=24)
            c.text((722, 262), '记忆引擎直接跑在手机里，', 30, 700, fill=MUTED, alpha=fade(t, 10.2, 15.2), dy=16)
            c.text((722, 308), '会更正、会归档、会想起往事。', 30, 700, fill=MUTED, alpha=fade(t, 10.5, 15.2), dy=16)
            c.text((722, 372), '聊天、记忆、Key 都留在本机', 26, 400, fill=ACCENT, alpha=fade(t, 10.9, 15.2), dy=16)
            c.sprite(clip('s3', '099', 330), (960, 690), alpha=a)
        else:
            drop('s3')

        # 4. 15.2–21.0s  five personalities
        if 15.0 < t < 21.2:
            a = fade(t, 15.2, 21.0)
            c.text((W / 2, 92), '五种性格，相遇时选定', 56, 900, alpha=a, anchor='mm', dy=20)
            c.text((W / 2, 152), '你说「我回来了。」', 28, 700, fill=MUTED, alpha=fade(t, 15.5, 21.0), anchor='mm')
            for k, (name, reply, clip_id) in enumerate(PERSONAS):
                ka = fade(t, 15.8 + k * 0.28, 21.0)
                x0 = 50 + k * 240
                c.card([x0, 380, x0 + 220, 640], ka, radius=24, opacity=190)
                c.sprite(clip(f's4{k}', clip_id, 240), (x0 + 110, 420), alpha=ka)
                c.text((x0 + 110, 448), name, 28, 900, fill=WARM, alpha=ka, anchor='mm')
                f = fonts(22, 400)
                lines, cur = [], ''
                for ch in reply:
                    if f.getlength(cur + ch) > 178:
                        lines.append(cur)
                        cur = ch
                    else:
                        cur += ch
                lines.append(cur)
                for j, line in enumerate(lines):
                    c.text((x0 + 22, 490 + j * 34), line, 22, 400, alpha=ka, shadow=False)
        else:
            drop(*[f's4{k}' for k in range(5)])

        # 5. 21.0–26.2s  actions
        if 20.8 < t < 26.4:
            a = fade(t, 21.0, 26.2)
            c.text((W / 2, 92), '一百多段动作，跟着作息与节日变', 52, 900, alpha=a, anchor='mm', dy=20)
            c.text((W / 2, 150), '清晨刷牙 · 午后犯困 · 夜里打盹 · 中秋赏月 · 冬天堆雪人', 26, 400,
                   fill=MUTED, alpha=fade(t, 21.3, 26.2), anchor='mm')
            for k, (label, clip_id) in enumerate(ACTIONS):
                ka = fade(t, 21.4 + k * 0.2, 26.2)
                cx = 160 + k * 240
                c.sprite(clip(f's5{k}', clip_id, 400, 1.5), (cx, 560), alpha=ka)
                c.text((cx, 610), label, 28, 700, fill=WARM, alpha=ka, anchor='mm')
        else:
            drop(*[f's5{k}' for k in range(5)])

        # 6. 26.2–31.2s  quest line
        if 26.0 < t < 31.4:
            a = fade(t, 26.2, 31.2)
            c.text((90, 96), '五章任务线，攒满泡泡瓶', 56, 900, alpha=a, dy=20)
            c.text((92, 180), '每一段真实的相处，都会变成回忆泡泡', 28, 400, fill=MUTED, alpha=fade(t, 26.5, 31.2), dy=12)
            c.sprite(clip('s6', '001', 400), (1060, 380), alpha=a)
            progress = ease((t - 26.8) / 3.2)
            x0, x1, ly = 150, 1130, 470

            def line(d, _, p=progress):
                d.rounded_rectangle([x0, ly - 5, x1, ly + 5], 5, fill=(60, 100, 150, 200))
                d.rounded_rectangle([x0, ly - 5, x0 + (x1 - x0) * p, ly + 5], 5, fill=ACCENT + (255,))
            c.layer(a, line)
            for k, (name, task) in enumerate(CHAPTERS):
                cx = x0 + (x1 - x0) * k / 4
                lit = progress >= k / 4 - 1e-6
                na = fade(t, 26.6 + k * 0.18, 31.2)

                def node(d, _, cx=cx, lit=lit):
                    r = 26
                    d.ellipse([cx - r, ly - r, cx + r, ly + r], fill=(ACCENT if lit else (40, 80, 130)) + (255,),
                              outline=(220, 245, 255, 220), width=3)
                c.layer(na, node)
                c.text((cx, ly), '一二三四五'[k], 24, 900, fill=(10, 40, 80) if lit else INK, alpha=na,
                       anchor='mm', shadow=False)
                c.text((cx, ly + 66), f'第{"一二三四五"[k]}章 · {name}', 26, 900, fill=WARM if lit else INK,
                       alpha=na, anchor='mm')
                c.text((cx, ly + 108), task, 21, 400, fill=MUTED, alpha=na, anchor='mm')
        else:
            drop('s6')

        # 7. 31.2–36s  the blue whale, then the end card
        if t > 31.0:
            a = fade(t, 31.2, 33.9, 0.4)
            c.sprite(clip('s7', '089', 640, 2.2), (W / 2, 640), alpha=a)
            c.text((W / 2, 90), WHALE_LINE, 34, 700, fill=INK, alpha=fade(t, 31.6, 33.9, 0.4), anchor='mm', dy=12)
            e = fade(t, 33.9, DURATION + 1, 0.5)
            c.sprite(clip('s8', '054', 400), (330, 600), alpha=e)
            c.text((560, 180), '大肥鱼', 112, 900, alpha=e, dy=20)
            c.text((566, 350), '现在下载', 34, 700, fill=ACCENT, alpha=e, dy=12)
            c.text((566, 404), 'Android 8.0+ · 免费 · 覆盖安装保留数据', 26, 400, fill=MUTED, alpha=e)
            c.text((566, 456), REPO, 26, 700, fill=INK, alpha=e)
            c.card([180, 612, W - 180, 700], e, radius=18, fill=(2, 10, 28), opacity=150, outline=(0, 0, 0, 0))
            for k, s in enumerate(CREDIT):
                c.text((W / 2, 642 + k * 30), s, 17, 400, fill=MUTED, alpha=e * 0.9, anchor='mm', shadow=False)

        enc.stdin.write(c.img.convert('RGB').tobytes())

    enc.stdin.close()
    enc.wait()
    for k in list(clips):
        drop(k)
    return cover


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--font-dir', required=True)
    ap.add_argument('--preview-width', type=int, default=800)
    ap.add_argument('--preview-fps', type=int, default=15)
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    fonts = Fonts(args.font_dir)
    fonts.check(ALL_TEXT)
    mp4 = os.path.join(OUT, 'dafeiyu-promo.mp4')
    cover = render(fonts, mp4)
    cover.convert('RGB').save(os.path.join(OUT, 'cover.png'), optimize=True)
    # Animated WebP rather than GIF: a GIF of the same cut is three times the size and banded.
    preview = os.path.join(OUT, 'dafeiyu-promo.webp')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', mp4, '-vf',
                    f'fps={args.preview_fps},scale={args.preview_width}:-1:flags=lanczos',
                    '-c:v', 'libwebp_anim', '-quality', '62', '-compression_level', '6', '-loop', '0', preview], check=True)
    for p in (mp4, preview, os.path.join(OUT, 'cover.png')):
        print(p, round(os.path.getsize(p) / 1e6, 2), 'MB')


if __name__ == '__main__':
    main()
