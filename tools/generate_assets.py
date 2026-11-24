#!/usr/bin/env python3
"""Generates every FishClient asset from scratch: icons, logo, cosmetic textures and sounds.

Run from the project root: python tools/generate_assets.py
Needs Pillow, plus ffmpeg on PATH for the .ogg sounds.
"""
import json
import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/fishclient"
ICONS = ASSETS / "textures/gui/icons"
GUI = ASSETS / "textures/gui"
COSMETICS = ASSETS / "textures/cosmetics"
SOUNDS = ASSETS / "sounds"
FONT = ASSETS / "font"

S = 64  # final icon size
K = 4  # supersampling factor
W = (255, 255, 255, 255)


class Icon:
    def __init__(self):
        self.img = Image.new("RGBA", (S * K, S * K), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def p(self, *xy):
        return [v * K for v in xy]

    def line(self, pts, w=5):
        flat = [v * K for pt in pts for v in pt]
        self.d.line(flat, fill=W, width=w * K, joint="curve")
        for pt in (pts[0], pts[-1]):
            self.dot(pt[0], pt[1], w / 2)

    def dot(self, x, y, r):
        self.d.ellipse(self.p(x - r, y - r, x + r, y + r), fill=W)

    def ring(self, x, y, r, w=5):
        self.d.ellipse(self.p(x - r, y - r, x + r, y + r), outline=W, width=w * K)

    def rrect(self, x0, y0, x1, y1, r=6, fill=True, w=5):
        if fill:
            self.d.rounded_rectangle(self.p(x0, y0, x1, y1), radius=r * K, fill=W)
        else:
            self.d.rounded_rectangle(self.p(x0, y0, x1, y1), radius=r * K, outline=W, width=w * K)

    def poly(self, pts):
        self.d.polygon([v * K for pt in pts for v in pt], fill=W)

    def arc(self, x, y, r, a0, a1, w=5):
        self.d.arc(self.p(x - r, y - r, x + r, y + r), a0, a1, fill=W, width=w * K)

    def erase(self, x0, y0, x1, y1, ellipse=False):
        box = self.p(x0, y0, x1, y1)
        if ellipse:
            self.d.ellipse(box, fill=(0, 0, 0, 0))
        else:
            self.d.rectangle(box, fill=(0, 0, 0, 0))

    def save(self, name):
        ICONS.mkdir(parents=True, exist_ok=True)
        self.img.resize((S, S), Image.LANCZOS).save(ICONS / f"{name}.png")


def fish_shape(ic, cx=30, cy=32, sx=1.0):
    """A flat fish facing left: the body is a lens, the tail a triangle."""
    pts = []
    for i in range(41):
        t = i / 40
        x = cx - 22 * sx + 44 * sx * t
        y = cy - 15 * sx * math.sin(math.pi * t) ** 0.8
        pts.append((x, y))
    for i in range(41):
        t = 1 - i / 40
        x = cx - 22 * sx + 44 * sx * t
        y = cy + 15 * sx * math.sin(math.pi * t) ** 0.8
        pts.append((x, y))
    ic.poly(pts)
    tx = cx + 19 * sx
    ic.poly([(tx, cy), (tx + 14 * sx, cy - 12 * sx), (tx + 10 * sx, cy), (tx + 14 * sx, cy + 12 * sx)])


def build_icons():
    ic = Icon()  # gavel for auctions
    ic.d.line(ic.p(26, 26, 52, 52), fill=W, width=7 * K)
    ic.d.polygon([v * K for v in (24, 6, 42, 24, 28, 38, 10, 20)], fill=W)
    ic.rrect(8, 52, 34, 58, 3)
    ic.save("auction")

    ic = Icon()  # clock for recent auctions
    ic.ring(32, 32, 24)
    ic.line([(32, 18), (32, 33), (42, 39)])
    ic.save("clock")

    ic = Icon()  # opposing arrows for transactions
    ic.line([(10, 22), (52, 22)])
    ic.line([(42, 12), (52, 22), (42, 32)])
    ic.line([(54, 42), (12, 42)])
    ic.line([(22, 32), (12, 42), (22, 52)])
    ic.save("swap")

    ic = Icon()  # palette for themes
    ic.d.ellipse(ic.p(8, 8, 56, 56), fill=W)
    for (x, y) in [(22, 22), (38, 18), (48, 30), (18, 36)]:
        ic.erase(x - 4, y - 4, x + 4, y + 4, ellipse=True)
    ic.erase(34, 38, 60, 60, ellipse=True)
    ic.save("palette")

    ic = Icon()  # person for skins
    ic.dot(32, 20, 12)
    ic.d.pieslice(ic.p(10, 36, 54, 84), 180, 360, fill=W)
    ic.save("person")

    ic = Icon()  # cape
    ic.poly([(16, 10), (48, 10), (56, 54), (44, 48), (32, 56), (20, 48), (8, 54)])
    ic.save("cape")

    ic = Icon()  # top hat for cosmetics
    ic.rrect(20, 10, 44, 42, 3)
    ic.rrect(8, 42, 56, 50, 4)
    ic.erase(20, 32, 44, 36)
    ic.save("hat")

    ic = Icon()  # music note
    ic.dot(18, 48, 8)
    ic.dot(44, 42, 8)
    ic.d.rectangle(ic.p(22, 14, 26, 48), fill=W)
    ic.d.rectangle(ic.p(48, 8, 52, 42), fill=W)
    ic.poly([(22, 14), (52, 8), (52, 18), (22, 24)])
    ic.save("music")

    ic = Icon()  # crosshair
    ic.ring(32, 32, 18)
    for a, b in [((32, 4), (32, 18)), ((32, 46), (32, 60)), ((4, 32), (18, 32)), ((46, 32), (60, 32))]:
        ic.line([a, b])
    ic.dot(32, 32, 3)
    ic.save("crosshair")

    ic = Icon()  # sliders for utilities
    for y, x in [(16, 42), (32, 20), (48, 36)]:
        ic.line([(8, y), (56, y)], 4)
        ic.dot(x, y, 7)
    ic.save("sliders")

    ic = Icon()  # lines for the scoreboard
    ic.rrect(8, 8, 56, 56, 8, fill=False)
    for y in (22, 32, 42):
        ic.line([(18, y), (38, y)], 4)
        ic.dot(46, y, 2.5)
    ic.save("scoreboard")

    ic = Icon()  # crescent moon and star for the sky
    ic.d.ellipse(ic.p(8, 12, 50, 54), fill=W)
    ic.erase(20, 6, 60, 46, ellipse=True)
    star(ic, 46, 18, 9)
    ic.save("sky")

    ic = Icon()  # portal: a tall frame with a swirl inside
    ic.rrect(12, 4, 52, 60, 5, fill=False, w=6)
    ic.arc(32, 32, 11, 200, 520, 4)
    ic.arc(32, 32, 5, 20, 300, 4)
    ic.save("portal")

    ic = Icon()  # map pin for waypoints
    ic.d.ellipse(ic.p(14, 6, 50, 42), fill=W)
    ic.poly([(17, 33), (47, 33), (32, 60)])
    ic.erase(25, 17, 39, 31, ellipse=True)
    ic.save("pin")

    ic = Icon()  # folded map for the minimap
    ic.poly([(8, 14), (24, 8), (24, 50), (8, 56)])
    ic.poly([(27, 8), (37, 14), (37, 56), (27, 50)])
    ic.poly([(40, 14), (56, 8), (56, 50), (40, 56)])
    ic.save("map")

    ic = Icon()  # gauge for hud elements
    ic.rrect(6, 14, 58, 50, 7, fill=False)
    for x in (16, 28, 40):
        ic.rrect(x - 3, 23, x + 5, 31, 2)
    ic.rrect(18, 36, 46, 42, 2)
    ic.save("keyboard")

    ic = Icon()  # fish for fishing
    fish_shape(ic)
    ic.erase(13, 25, 19, 31, ellipse=True)
    ic.save("fish")

    ic = Icon()  # camera for screenshots
    ic.rrect(6, 18, 58, 52, 7)
    ic.rrect(22, 10, 42, 22, 3)
    ic.erase(20, 23, 44, 47, ellipse=True)
    ic.dot(32, 35, 7)
    ic.save("camera")

    ic = Icon()  # stacked layers for profiles
    for y in (40, 28):
        ic.poly([(32, y - 14), (58, y), (32, y + 14), (6, y)])
        ic.erase(0, y + 14, 64, y + 17)
    ic.poly([(32, 2), (56, 15), (32, 28), (8, 15)])
    ic.save("layers")

    ic = Icon()  # gear for settings
    for i in range(8):
        a = i * math.pi / 4
        x, y = 32 + 22 * math.cos(a), 32 + 22 * math.sin(a)
        ic.d.line(ic.p(32, 32, x, y), fill=W, width=9 * K)
    ic.dot(32, 32, 19)
    ic.erase(24, 24, 40, 40, ellipse=True)
    ic.save("gear")

    ic = Icon()  # reset arrow
    ic.arc(32, 32, 21, -60, 250, 6)
    ic.poly([(40, 2), (52, 16), (34, 20)])
    ic.save("reset")

    ic = Icon()
    ic.line([(16, 16), (48, 48)], 6)
    ic.line([(48, 16), (16, 48)], 6)
    ic.save("close")

    ic = Icon()
    ic.line([(12, 34), (26, 48), (52, 18)], 7)
    ic.save("check")

    ic = Icon()
    ic.line([(32, 12), (32, 52)], 6)
    ic.line([(12, 32), (52, 32)], 6)
    ic.save("plus")

    ic = Icon()  # trash can
    ic.rrect(10, 14, 54, 20, 3)
    ic.rrect(24, 7, 40, 14, 2)
    ic.poly([(14, 22), (50, 22), (46, 58), (18, 58)])
    for x in (25, 32, 39):
        ic.erase(x - 1.5, 28, x + 1.5, 52)
    ic.save("trash")

    ic = Icon()
    star(ic, 32, 32, 28)
    ic.save("star")

    ic = Icon()  # upload arrow
    ic.line([(32, 44), (32, 10)], 6)
    ic.line([(16, 26), (32, 10), (48, 26)], 6)
    ic.line([(10, 54), (54, 54)], 6)
    ic.save("upload")

    ic = Icon()  # coin
    ic.ring(32, 32, 24)
    ic.line([(32, 16), (32, 48)], 4)
    ic.arc(32, 26, 9, 170, 370, 4)
    ic.arc(32, 38, 9, -10, 190, 4)
    ic.save("coin")

    ic = Icon()
    ic.d.polygon([v * K for v in (20, 10, 52, 32, 20, 54)], fill=W)
    ic.save("play")

    ic = Icon()  # copy
    ic.rrect(22, 22, 56, 56, 6)
    ic.rrect(8, 8, 42, 42, 6, fill=False)
    ic.save("copy")

    ic = Icon()  # outward arrow for exports
    ic.rrect(8, 16, 48, 56, 7, fill=False)
    ic.line([(30, 34), (56, 8)], 5)
    ic.line([(38, 8), (56, 8), (56, 26)], 5)
    ic.save("external")

    ic = Icon()  # direction arrow on the minimap
    ic.d.polygon([v * K for v in (32, 4, 54, 58, 32, 46, 10, 58)], fill=W)
    ic.save("arrow")


def star(ic, cx, cy, r):
    pts = []
    for i in range(8):
        a = i * math.pi / 4 - math.pi / 2
        rr = r if i % 2 == 0 else r * 0.28
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    ic.poly(pts)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def build_logo():
    GUI.mkdir(parents=True, exist_ok=True)
    k = 4
    n = 256
    img = Image.new("RGBA", (n * k, n * k), (0, 0, 0, 0))
    # A disc with a vertical ocean gradient.
    grad = Image.new("RGBA", img.size)
    gp = grad.load()
    top, bottom = (47, 214, 195, 255), (24, 103, 184, 255)
    for y in range(n * k):
        col = lerp(top, bottom, y / (n * k))
        for x in range(n * k):
            gp[x, y] = col
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((8 * k, 8 * k, (n - 8) * k, (n - 8) * k), fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)
    # The fish is cut in white on top of the disc.
    cx, cy = 118 * k, 128 * k
    body = []
    for i in range(61):
        t = i / 60
        body.append((cx - 72 * k + 144 * k * t, cy - 50 * k * math.sin(math.pi * t) ** 0.8))
    for i in range(61):
        t = 1 - i / 60
        body.append((cx - 72 * k + 144 * k * t, cy + 50 * k * math.sin(math.pi * t) ** 0.8))
    d.polygon(body, fill=W)
    tx = cx + 62 * k
    d.polygon([(tx, cy), (tx + 44 * k, cy - 40 * k), (tx + 32 * k, cy), (tx + 44 * k, cy + 40 * k)], fill=W)
    ex, ey = cx - 40 * k, cy - 12 * k
    d.ellipse((ex - 9 * k, ey - 9 * k, ex + 9 * k, ey + 9 * k), fill=(24, 103, 184, 255))
    # Two bubbles rising from the mouth.
    for (bx, by, br) in [(58, 82, 9), (76, 54, 6)]:
        d.ellipse(((bx - br) * k, (by - br) * k, (bx + br) * k, (by + br) * k), outline=W, width=3 * k)
    logo = img.resize((n, n), Image.LANCZOS)
    logo.save(GUI / "logo.png")
    pack_icon = ROOT / "src/main/resources/assets/fishclient/icon.png"
    logo.resize((128, 128), Image.LANCZOS).save(pack_icon)

    # Wordmark with a small fin rising from the "F".
    font = ImageFont.truetype(str(FONT / "inter-bold.ttf"), 96 * k)
    tmp = ImageDraw.Draw(Image.new("L", (1, 1)))
    box = tmp.textbbox((0, 0), "FishClient", font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    pad = 8 * k
    fin = 20 * k
    word = Image.new("RGBA", (tw + pad * 2, th + pad * 2 + fin), (0, 0, 0, 0))
    wd = ImageDraw.Draw(word)
    wd.text((pad - box[0], pad + fin - box[1]), "FishClient", font=font, fill=W)
    # A swept-back dorsal fin growing out of the bar of the F, like a shark fin leaning to the right.
    base = pad + fin + 2 * k
    fx = pad + 16 * k
    fin_w = 40 * k
    fin_h = base - pad
    outline = []
    for i in range(25):
        t = i / 24
        # The front edge rises in a convex curve up to the tip.
        outline.append((fx + fin_w * 0.8 * t ** 1.8, base - fin_h * math.sin(t * math.pi / 2)))
    for i in range(1, 13):
        t = i / 12
        # The back edge drops steeply and curls inward towards the base.
        outline.append((fx + fin_w * (0.8 + 0.2 * t ** 2.2) - fin_w * 0.1 * math.sin(t * math.pi), pad + fin_h * t))
    wd.polygon(outline, fill=W)
    word = word.resize((word.width // k, word.height // k), Image.LANCZOS)
    word.save(GUI / "wordmark.png")
    print("wordmark", word.size)


def build_cosmetics():
    """Greyscale textures: the accent colour is multiplied in at render time."""
    COSMETICS.mkdir(parents=True, exist_ok=True)
    rnd = random.Random(7)

    def speckled(name, w, h, base, band=None, spread=10):
        img = Image.new("RGBA", (w, h))
        px = img.load()
        for y in range(h):
            for x in range(w):
                v = base + rnd.randint(-spread, spread)
                if band and band[0] <= y < band[1]:
                    v = band[2] + rnd.randint(-spread // 2, spread // 2)
                v = max(0, min(255, v))
                px[x, y] = (v, v, v, 255)
        img.save(COSMETICS / f"{name}.png")

    speckled("top_hat", 64, 64, 70, band=(26, 30, 235))
    speckled("fisher_hat", 64, 64, 200, band=(27, 28, 120))
    speckled("crown", 64, 64, 225)
    speckled("headphones", 64, 64, 95)

    # Fish hat: a small fish that sits on the head.
    img = Image.new("RGBA", (64, 64))
    px = img.load()
    for y in range(64):
        for x in range(64):
            v = 215 + rnd.randint(-14, 14)
            if (x // 4 + y // 4) % 3 == 0:
                v -= 50
            px[x, y] = (v, v, v, 255)
    img.save(COSMETICS / "fish_hat.png")

    # Default cape: a dark ocean sheet in the vanilla 64x32 cape layout.
    cape = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cape)
    top, bottom = (12, 44, 74, 255), (8, 96, 128, 255)
    for y in range(32):
        cd.line((0, y, 23, y), fill=lerp(top, bottom, y / 31))
    # Waves across the front panel (u 1..11, v 1..17).
    for i, y in enumerate((6, 10, 14)):
        for x in range(1, 11):
            yy = y + int(round(math.sin(x * 0.9 + i) * 1))
            cape.putpixel((x, yy), (47, 214, 195, 255))
    # A small fish in the middle.
    for (x, y) in [(5, 3), (6, 3), (7, 3), (4, 4), (5, 4), (6, 4), (7, 4), (8, 4), (5, 5), (6, 5), (7, 5), (9, 3), (9, 5)]:
        cape.putpixel((x, y), (235, 248, 255, 255))
    cape.save(COSMETICS / "default_cape.png")


def tone(freq, dur, volume=0.5, attack=0.004, decay=None, sweep=0.0, rate=44100):
    n = int(dur * rate)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / rate
        f = freq + sweep * t
        phase += 2 * math.pi * f / rate
        env = min(1.0, t / attack) * math.exp(-t / (decay or dur / 3))
        out.append(math.sin(phase) * env * volume)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def offset(track, seconds, rate=44100):
    return [0.0] * int(seconds * rate) + track


def bubble(rnd, start, volume=0.35):
    f = rnd.uniform(380, 900)
    return offset(tone(f, rnd.uniform(0.07, 0.14), volume, attack=0.003, decay=0.03, sweep=f * rnd.uniform(3, 6)), start)


def write_ogg(name, samples):
    SOUNDS.mkdir(parents=True, exist_ok=True)
    peak = max(abs(v) for v in samples) or 1.0
    scale = min(1.0, 0.9 / peak)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "s.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(44100)
            w.writeframes(b"".join(struct.pack("<h", int(v * scale * 32767)) for v in samples))
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", str(SOUNDS / f"{name}.ogg")],
            check=True,
        )


def build_sounds():
    rnd = random.Random(11)
    write_ogg("click", tone(1250, 0.05, 0.4, decay=0.012))
    write_ogg("toggle_on", mix(tone(780, 0.09, 0.32, decay=0.03), offset(tone(1170, 0.09, 0.32, decay=0.03), 0.045)))
    write_ogg("toggle_off", mix(tone(1000, 0.09, 0.3, decay=0.03), offset(tone(680, 0.09, 0.3, decay=0.03), 0.045)))
    write_ogg("menu_open", mix(tone(420, 0.28, 0.28, attack=0.02, decay=0.1, sweep=520), offset(tone(840, 0.2, 0.14, attack=0.02, decay=0.07), 0.05)))
    write_ogg("menu_close", tone(760, 0.2, 0.24, attack=0.008, decay=0.07, sweep=-620))
    write_ogg("water_open", mix(*[bubble(rnd, rnd.uniform(0, 0.42), rnd.uniform(0.2, 0.42)) for _ in range(9)]))
    write_ogg("bubbles", mix(*[bubble(rnd, rnd.uniform(0, 1.3), rnd.uniform(0.1, 0.26)) for _ in range(14)]))
    # Live auction: a bright double note per bid, a soft tick in the last seconds, a rising chime at start and end.
    write_ogg("bid", mix(tone(990, 0.12, 0.34, decay=0.04), offset(tone(1480, 0.16, 0.3, decay=0.05), 0.06)))
    write_ogg("tick", tone(620, 0.06, 0.32, decay=0.015))
    write_ogg("auction_start", mix(tone(523, 0.3, 0.26, decay=0.1), offset(tone(659, 0.3, 0.26, decay=0.1), 0.09), offset(tone(784, 0.4, 0.26, decay=0.14), 0.18)))
    write_ogg("auction_end", mix(
        tone(784, 0.5, 0.26, decay=0.16), offset(tone(988, 0.5, 0.26, decay=0.16), 0.1),
        offset(tone(1175, 0.6, 0.26, decay=0.2), 0.2), offset(tone(1568, 0.9, 0.22, decay=0.3), 0.32),
    ))
    sounds = {
        name: {"sounds": [{"name": f"fishclient:{name}", "stream": False}]}
        for name in ("click", "toggle_on", "toggle_off", "menu_open", "menu_close", "water_open", "bubbles", "bid", "tick", "auction_start", "auction_end")
    }
    (ASSETS / "sounds.json").write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    build_icons()
    build_logo()
    build_cosmetics()
    build_sounds()
    print("assets generated")
