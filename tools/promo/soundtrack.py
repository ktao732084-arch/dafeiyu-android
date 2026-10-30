"""Original soundtrack for the promo, synthesised from scratch so it carries no third-party music rights.

A soft kalimba-and-pad tune in F major at 100 BPM, with bubble pops, chimes and a whale call placed on the
cues render_promo.py passes in. Only numpy is needed; the result is a stereo 44.1 kHz float array.
"""
import math

import numpy as np

SR = 44100
BAR = 2.4  # 100 BPM, 4/4
EIGHTH = BAR / 8

CHORDS = {  # pad voicing, bass note (MIDI)
    'Dm9': ([50, 53, 57, 60, 64], 38),
    'F': ([53, 57, 60, 64], 41),
    'Am': ([57, 60, 64, 67], 45),
    'Bb': ([58, 62, 65, 69], 46),
    'C': ([55, 60, 64, 67], 48),
    'Fend': ([53, 57, 60, 64, 67], 41),
}
# One bar of eighth notes per chord; None is a rest.
MELODY = {
    'F': [72, None, 69, 72, 74, None, 72, None],
    'Am': [76, None, 74, 72, 69, None, None, None],
    'Bb': [74, None, 72, 74, 77, None, 74, None],
    'C': [76, 74, 72, None, 67, None, None, None],
}
PROGRESSION = ['F', 'Am', 'Bb', 'C']


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


class Track:
    def __init__(self, seconds):
        self.n = int(seconds * SR) + SR * 3
        self.dry = np.zeros((self.n, 2))
        self.wet = np.zeros((self.n, 2))  # reverb send
        self.rng = np.random.default_rng(11)

    def add(self, start, sig, gain=1.0, pan=0.0, send=0.3):
        i = int(start * SR)
        if i >= self.n:
            return
        sig = sig[:self.n - i] * gain
        left, right = math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
        stereo = np.stack([sig * left, sig * right], axis=1) * math.sqrt(2)
        self.dry[i:i + len(sig)] += stereo
        self.wet[i:i + len(sig)] += stereo * send

    # -- voices ------------------------------------------------------------------------------------

    def kalimba(self, f, length=1.6):
        t = np.arange(int(length * SR)) / SR
        tine = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.55)
                + 0.28 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / 0.14)
                + 0.10 * np.sin(2 * np.pi * 5.4 * f * t) * np.exp(-t / 0.03))
        return tine * np.minimum(t / 0.004, 1)

    def pad(self, notes, length, attack=0.7, release=1.2):
        t = np.arange(int((length + release) * SR)) / SR
        out = np.zeros_like(t)
        for m in notes:
            f = hz(m)
            for cents in (-7, 0, 6):
                ff = f * 2 ** (cents / 1200)
                ph = self.rng.uniform(0, 6.28)
                out += np.sin(2 * np.pi * ff * t + ph) + 0.22 * np.sin(4 * np.pi * ff * t + ph) \
                    + 0.08 * np.sin(6 * np.pi * ff * t + ph)
        env = np.minimum(t / attack, 1) * np.clip((length + release - t) / release, 0, 1)
        trem = 1 + 0.06 * np.sin(2 * np.pi * 0.25 * t)
        return out * env * trem / (len(notes) * 3)

    def bass(self, f, length):
        t = np.arange(int(length * SR)) / SR
        return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)) \
            * np.minimum(t / 0.01, 1) * np.exp(-t / 0.9)

    def kick(self):
        t = np.arange(int(0.35 * SR)) / SR
        f = 45 + 75 * np.exp(-t / 0.03)
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.12)

    def shaker(self):
        n = int(0.07 * SR)
        noise = np.diff(self.rng.standard_normal(n + 1))
        t = np.arange(n) / SR
        return noise * np.minimum(t / 0.008, 1) * np.exp(-t / 0.025) * 0.5

    def bubble(self, f0):
        t = np.arange(int(0.16 * SR)) / SR
        f = f0 * (1 + 1.4 * (1 - np.exp(-t / 0.035)))
        return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.minimum(t / 0.003, 1) * np.exp(-t / 0.045)

    def swell(self, length):
        """Filtered noise rising into a cut."""
        n = int(length * SR)
        noise = self.rng.standard_normal(n)
        for width in (40, 40):
            noise = np.convolve(noise, np.ones(width) / width, mode='same')
        t = np.arange(n) / SR
        return noise * (t / length) ** 2.2 * 6

    def whale(self, length=2.6):
        t = np.arange(int(length * SR)) / SR
        f = 210 + 140 * np.sin(np.pi * t / length) ** 1.5
        f *= 1 + 0.025 * np.sin(2 * np.pi * 5 * t)
        sig = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * np.sin(4 * np.pi * np.cumsum(f) / SR)
        return sig * np.sin(np.pi * t / length) ** 1.2

    # -- mix ---------------------------------------------------------------------------------------

    def reverb(self, seconds=2.2):
        n = int(seconds * SR)
        t = np.arange(n) / SR
        out = np.zeros_like(self.wet)
        size = 1 << int(math.ceil(math.log2(self.n + n)))
        for ch in range(2):
            ir = self.rng.standard_normal(n) * np.exp(-t / 0.5)
            ir[:int(0.02 * SR)] *= 0.2
            out[:, ch] = np.fft.irfft(np.fft.rfft(self.wet[:, ch], size) * np.fft.rfft(ir, size), size)[:self.n]
        return out / np.max(np.abs(out) + 1e-9) * np.max(np.abs(self.wet) + 1e-9) * 0.9

    def master(self, seconds):
        mix = self.dry + self.reverb() * 0.55
        mix = mix[:int(seconds * SR)]
        t = np.arange(len(mix)) / SR
        mix *= np.minimum(t / 0.8, 1)[:, None] * np.clip((seconds - t) / 1.6, 0, 1)[:, None]
        mix = np.tanh(mix / np.max(np.abs(mix)) * 1.1) / math.tanh(1.1)
        return mix * 0.89


def build(seconds, scenes, cues):
    """scenes: dict of scene start times (intro, title, beat, whale, end).
    cues: list of (time, kind, index) for pops and chimes."""
    tr = Track(seconds)
    title, beat, whale, end = scenes['title'], scenes['beat'], scenes['whale'], scenes['end']

    # Intro: the dark sea. Low pad, a few far-off notes, then a rising shimmer towards the light.
    tr.add(0.0, tr.pad(*CHORDS['Dm9'][:1], length=title - 0.2, attack=2.0, release=1.5), 0.4, send=0.6)
    for k, (at, m) in enumerate([(0.9, 62), (2.1, 57), (3.4, 60)]):
        tr.add(at, tr.kalimba(hz(m), 2.5), 0.22, pan=(-0.4, 0.3, -0.1)[k], send=0.8)
    for k, m in enumerate([65, 69, 72, 76, 77, 81, 84]):
        tr.add(scenes['light'] + k * 0.26, tr.kalimba(hz(m), 1.8), 0.16 + 0.02 * k, pan=-0.6 + 0.2 * k, send=0.7)
    tr.add(title - 1.6, tr.swell(1.6), 0.35, send=0.4)

    # Bars from the title card to the whale: pad, bass, then melody and a light groove from 'beat'.
    bars = []
    at = title
    while at < whale - 0.05:
        bars.append(at)
        at += BAR
    for k, b in enumerate(bars):
        name = PROGRESSION[k % 4]
        notes, root = CHORDS[name]
        length = min(BAR, whale - b)
        tr.add(b, tr.pad(notes, length), 0.42, send=0.45)
        tr.add(b, tr.bass(hz(root), BAR * 0.5), 0.34, send=0.05)
        tr.add(b + BAR / 2, tr.bass(hz(root), BAR * 0.5), 0.26, send=0.05)
        if b < beat - 0.05:
            continue
        lift = 12 if k % 8 >= 4 and k >= 8 else 0
        for j, m in enumerate(MELODY[name]):
            if m is not None and b + j * EIGHTH < whale - 0.1:
                tr.add(b + j * EIGHTH, tr.kalimba(hz(m + lift)), 0.24, pan=0.15, send=0.35)
        for j in range(4):
            s = b + j * BAR / 4
            if s < whale - 0.1:
                tr.add(s, tr.kick(), 0.30 if j in (0, 2) else 0.0, send=0.0)
                tr.add(s + BAR / 8, tr.shaker(), 0.12, pan=0.35, send=0.05)
                if scenes['busy'][0] <= s < scenes['busy'][1]:
                    tr.add(s + BAR / 16, tr.shaker(), 0.07, pan=-0.35, send=0.05)

    # Whale: everything drops away to a held chord and her call.
    tr.add(whale - 1.2, tr.swell(1.2), 0.3, send=0.5)
    tr.add(whale, tr.pad([50, 57, 62, 64, 65], end - whale, attack=0.5, release=1.0), 0.5, send=0.7)
    tr.add(whale, tr.bass(hz(38), 2.5), 0.3, send=0.2)
    tr.add(whale + 0.3, tr.whale(), 0.22, pan=-0.2, send=0.9)
    tr.add(whale + 1.2, tr.whale(2.0) * 0.8, 0.12, pan=0.3, send=1.0)

    # End card: home chord and a little rising arpeggio.
    tr.add(end, tr.pad(*CHORDS['Fend'][:1], length=seconds - end, attack=0.15, release=1.5), 0.55, send=0.6)
    tr.add(end, tr.bass(hz(41), 3.0), 0.4, send=0.1)
    for k, m in enumerate([77, 81, 84, 88]):
        tr.add(end + 0.12 * k, tr.kalimba(hz(m), 2.5), 0.22, pan=-0.3 + 0.2 * k, send=0.6)

    # Cues from the picture.
    for at, kind, k in cues:
        if kind == 'pop':
            tr.add(at, tr.bubble(520 + 90 * (k % 5)), 0.20, pan=(-0.3, 0.3)[k % 2], send=0.35)
        elif kind == 'tick':
            tr.add(at, tr.kalimba(hz(84 + (0, 2, 4, 7, 9)[k % 5]), 0.8), 0.10, pan=0.4, send=0.4)
        elif kind == 'chime':
            tr.add(at, tr.kalimba(hz((77, 79, 81, 84, 86)[k % 5]), 1.8), 0.24, send=0.5)
    return tr.master(seconds)
