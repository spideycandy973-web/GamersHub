"""GamersHub intro film — built entirely from the emblem's own exported layers.

Sequence: the mark's machined pieces streak in from off-frame, slam together in
waves with impact sparks and shockwave rings, a rim light sweeps the finished
metal, the visor ignites and breathes, then the mark charges up, burns away
upward into embers and collapses to a single point of light.

Rendering is a small float32 compositing engine (premultiplied alpha, additive
light buffer, bloom, chromatic aberration, grain, vignette); the soundtrack is
synthesised in the same pass (drone bed, servo whirs, metal clanks, ignition
chord, breathing pad, charge riser, sub boom, reverb tail).

    python3 tools/intro/make_intro.py preview   # half res, quick look
    python3 tools/intro/make_intro.py final     # 1080x1920 into app res/raw
"""
import math
import os
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.environ.get("GH_INTRO_PARTS", os.path.join(HERE, "parts"))
OUT_RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw", "intro_1080_portrait_comp.mp4")

FPS = 30
T_LOCK = 1.32          # last plate has landed
T_IGNITE = 1.56        # visor lights up
T_BREATH = 1.78        # breathing starts
T_CHARGE = 3.42        # the mark starts to overload
T_END_BURN = 3.78      # disintegration starts
BURN_LEN = 1.24
T_COLLAPSE = 5.34      # everything draws into one point
T_BURN_DONE = T_END_BURN + BURN_LEN
T_END = 5.98
EMB_Y0, EMB_Y1 = 656/1920, 1260/1920

W, H = 1080, 1920
LW, LH = 540, 960          # light buffer (glows, sparks, dust) resolution

FULL = "New Project 11 [CCD7880].png"

# name -> (label, side) ; side -1 = arrives from the left, +1 from the right,
# 0 = from above, 2 = from below.  Landings are in seconds.
LAYERS = [
    # back to front; the direction each sub-assembly comes from
    ("New Project 10 [06543AE].png", "grey core", "grey", 0.34, (0.0, -1.0)),
    ("New Project 10 [A68F9BC].png", "upper plate", "plate", 0.46, (-1.0, -0.35)),
    ("New Project 10 [72BE9F0].png", "core v", "corev", 0.58, (1.0, -0.35)),
    ("New Project 10 [E202690].png", "wings", "wings", 0.70, (0.0, 1.0)),
    ("New Project 10 [BCD7E75].png", "lower wings", "lower", 0.82, (-0.9, 0.45)),
    ("New Project 10 [1AFBFB2].png", "left shard", "lshard", 0.94, (-1.0, 0.15)),
    ("New Project 10 [97305A2].png", "right shard", "rshard", 0.94, (1.0, 0.15)),
    ("New Project 10 [CF00388].png", "bottom shard", "bottom", 1.06, (0.0, 1.0)),
    ("New Project 10 [E091E40].png", "crest", "crest", 1.20, (0.0, -1.0)),
]

FLY = 0.34          # flight time of a piece
SPLIT_MIN = 10**6   # never cut a layer: each sub-assembly lands whole



# --- the app's own palette (theme/GameCorner.kt) -------------------------------
def C(hexstr):
    h = hexstr.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], np.float32)


PAL = {
    "bg_top": C("#171512"),      # GC.BgTop
    "bg_bot": C("#090607"),      # GC.Void
    "atmosphere": C("#B3352F"),  # GC.SignalDeep
    "signal": C("#FF8A80"),      # GC.Signal
    "signal_glow": C("#FFB4B0"), # GC.SignalGlow
    "crimson": C("#FF3B30"),     # GC.Crimson
    "ember": C("#FF6A2A"),       # GC.Ember
    "gold": C("#FFB03A"),        # GC.Gold
    "hot": C("#FFEDE9"),         # SignalGlow pushed to white
}

# ----------------------------------------------------------------------------- helpers
def clamp(x, a=0.0, b=1.0):
    return a if x < a else (b if x > b else x)


def smoothstep(a, b, x):
    t = clamp((x - a) / (b - a) if b != a else 0.0)
    return t * t * (3 - 2 * t)


def ease_out(x):
    x = clamp(x)
    return 1 - (1 - x) ** 3


def ease_in(x):
    x = clamp(x)
    return x * x * getattr(x, "__pow__")(1)


def ease_in_out(x):
    x = clamp(x)
    return 3 * x * x - 2 * x * x * x


def spring_settle(dt, dur=0.16, amp=0.045):
    """Scale multiplier for the overshoot right after a piece locks."""
    if dt < 0 or dt > dur:
        return 1.0
    p = dt / dur
    return 1.0 + amp * math.sin(p * math.pi * 2.0) * (1 - p) ** 2


def box_blur(a, r, passes=3):
    """Fast separable box blur on a float32 HxWxC array."""
    if r < 1:
        return a
    out = a
    for _ in range(passes):
        k = 2 * r + 1
        pad = np.pad(out, ((r, r), (0, 0), (0, 0)), mode="edge")
        c = np.cumsum(pad, axis=0, dtype=np.float32)
        out = (c[k:] - c[:-k]) / k
        pad = np.pad(out, ((0, 0), (r, r), (0, 0)), mode="edge")
        c = np.cumsum(pad, axis=1, dtype=np.float32)
        out = (c[:, k:] - c[:, :-k]) / k
    return out


def resize_f(a, w, h):
    """Resize a float32 HxWx3 array with PIL (assumes 0..1 range)."""
    img = Image.fromarray(np.clip(a * 255.0, 0, 255).astype(np.uint8))
    return np.asarray(img.resize((w, h), Image.BILINEAR), dtype=np.float32) / 255.0


def upsample(a, w, h):
    return resize_f(a, w, h)


def radial(w, h, cx, cy, rx, ry, power=2.0):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2)
    return np.clip(1.0 - d, 0, 1) ** power


# ----------------------------------------------------------------------------- assets
def premultiplied(path, box=None):
    img = Image.open(path).convert("RGB")
    arr = np.asarray(img, dtype=np.int32)
    bright = arr.max(axis=2)
    alpha = np.clip((bright - 8) * (255.0 / 12.0), 0, 255).astype(np.uint8)
    if box is not None:
        alpha[: max(0, box[1]), :] = 0
        alpha[box[3]:, :] = 0
        alpha[:, : max(0, box[0])] = 0
        alpha[:, box[2]:] = 0
    rgba = np.dstack([arr.astype(np.uint8), alpha])
    pm = rgba.astype(np.float32) / 255.0
    pm[..., :3] *= pm[..., 3:4]          # premultiply -> clean resampling
    return Image.fromarray((pm * 255.0 + 0.5).astype(np.uint8), "RGBA")


def build_pieces():
    """Every layer, split into halves where it is wide, with its home position."""
    pieces = []
    for fname, label, key, landing, direction in LAYERS:
        img = premultiplied(os.path.join(SRC, fname))
        a = np.asarray(img, dtype=np.int32)
        ys, xs = np.nonzero(a[..., 3] > 4)
        x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
        crop = img.crop((x0, y0, x1, y1))
        if crop.width > SPLIT_MIN and key != "crest":
            mid = crop.width // 2
            halves = [(crop.crop((0, 0, mid, crop.height)), -1),
                      (crop.crop((mid, 0, crop.width, crop.height)), 1)]
        else:
            halves = [(crop, 0)]
        for sub, side in halves:
            sa = np.asarray(sub, dtype=np.int32)
            if (sa[..., 3] > 4).sum() < 40:
                continue
            pieces.append({
                "img": sub,
                "home": (x0 + (crop.width // 2 if len(halves) == 2 and side > 0 else 0), y0),
                "side": side,
                "dir": direction,
                "label": label,
                "key": key,
                "landing": landing,
            })
    return pieces


def build_full():
    img = premultiplied(os.path.join(SRC, FULL))
    a = np.asarray(img, dtype=np.int32)
    ys, xs = np.nonzero(a[..., 3] > 4)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    alpha = (a[..., 3].astype(np.float32) / 255.0)
    return img, box, alpha


def build_masks(full_box):
    """Eye light and circuit light, sampled from the artwork itself."""
    img = Image.open(os.path.join(SRC, FULL)).convert("RGB")
    arr = np.asarray(img, dtype=np.float32) / 255.0
    r, g, b = arr[..., 0], arr[..., 1], arr[..., 2]
    lum = arr.max(axis=2)
    warm = np.clip(r - 0.55 * b, 0, 1) * np.clip(lum * 1.4, 0, 1)
    yy, xx = np.mgrid[0:arr.shape[0], 0:arr.shape[1]]
    eye_box = (xx > 400) & (xx < 690) & (yy > 640) & (yy < 800)
    eye = (warm * eye_box).astype(np.float32)
    return warm.astype(np.float32), eye


# ----------------------------------------------------------------------------- drawing
def stamp(buf, img, cx, cy, scale, rot=0.0, alpha=1.0):
    """Composite a premultiplied RGBA image (centre cx,cy) into a float32 buffer."""
    w = max(1, int(round(img.width * scale)))
    h = max(1, int(round(img.height * scale)))
    im = img.resize((w, h), Image.BILINEAR)
    if abs(rot) > 0.05:
        im = im.rotate(rot, resample=Image.BILINEAR, expand=True)
    x0 = int(round(cx - im.width / 2.0))
    y0 = int(round(cy - im.height / 2.0))
    x1, y1 = x0 + im.width, y0 + im.height
    bx0, by0 = max(0, x0), max(0, y0)
    bx1, by1 = min(buf.shape[1], x1), min(buf.shape[0], y1)
    if bx1 <= bx0 or by1 <= by0:
        return
    a = np.asarray(im, dtype=np.float32) / 255.0
    a = a[by0 - y0:by1 - y0, bx0 - x0:bx1 - x0]
    a = a * alpha if alpha != 1.0 else a
    reg = buf[by0:by1, bx0:bx1]
    reg *= (1.0 - a[..., 3:4])
    reg += a[..., :3]


def splat(buf, x, y, radius, colour, intensity):
    """Soft additive dot on the (low-res) light buffer."""
    r = max(1, int(radius))
    x0, x1 = int(x) - r, int(x) + r + 1
    y0, y1 = int(y) - r, int(y) + r + 1
    if x1 <= 0 or y1 <= 0 or x0 >= buf.shape[1] or y0 >= buf.shape[0]:
        return
    bx0, by0 = max(0, x0), max(0, y0)
    bx1, by1 = min(buf.shape[1], x1), min(buf.shape[0], y1)
    yy, xx = np.mgrid[by0:by1, bx0:bx1].astype(np.float32)
    d2 = ((xx - x) ** 2 + (yy - y) ** 2) / (r * r)
    fall = np.clip(1.0 - d2, 0, 1) ** 2
    buf[by0:by1, bx0:bx1] += fall[..., None] * np.array(colour, np.float32) * intensity


def ring(buf, cx, cy, radius, thickness, colour, intensity, squash=1.0):
    if intensity <= 0.002 or radius < 1:
        return
    r = int(radius + thickness * 3)
    x0, x1 = int(cx) - r, int(cx) + r + 1
    y0, y1 = int(cy) - r, int(cy) + r + 1
    if x1 <= 0 or y1 <= 0 or x0 >= buf.shape[1] or y0 >= buf.shape[0]:
        return
    bx0, by0 = max(0, x0), max(0, y0)
    bx1, by1 = min(buf.shape[1], x1), min(buf.shape[0], y1)
    yy, xx = np.mgrid[by0:by1, bx0:bx1].astype(np.float32)
    d = np.sqrt((xx - cx) ** 2 + ((yy - cy) / squash) ** 2)
    edge = np.exp(-((d - radius) / max(1.0, thickness)) ** 2)
    buf[by0:by1, bx0:bx1] += edge[..., None] * np.array(colour, np.float32) * intensity


# ----------------------------------------------------------------------------- timeline
PIECES = None
FULLIMG = None
FULLBOX = None
EMBALPHA = None
CIRC = None
EYE = None
RNG = np.random.default_rng(7)

# camera zoom over time
ZOOM_KEYS = [(0.00, 1.28), (1.30, 1.50), (3.40, 1.58), (3.95, 1.70), (5.20, 1.82)]


def zoom_at(t):
    for (ta, za), (tb, zb) in zip(ZOOM_KEYS, ZOOM_KEYS[1:]):
        if t <= tb:
            p = ease_in_out((t - ta) / (tb - ta)) if tb > ta else 0.0
            return za + (zb - za) * p
    return ZOOM_KEYS[-1][1]


def breath_level(t):
    """0 before ignition, then a slow swell-and-shrink."""
    if t < T_IGNITE:
        return 0.0
    if t < T_IGNITE + 0.30:
        return smoothstep(T_IGNITE, T_IGNITE + 0.30, t) * 1.30
    phase = (t - T_IGNITE - 0.30) / 1.70
    return 0.55 + 0.45 * (0.5 - 0.5 * math.cos(2 * math.pi * phase))


def world(srcx, srcy, zoom, shake=(0.0, 0.0)):
    """Source-art coordinate -> screen coordinate."""
    ecx, ecy = 541.5, 958.0
    return (W / 2.0 + (srcx - ecx) * zoom + shake[0],
            H / 2.0 + (srcy - ecy) * zoom + shake[1])


def impact_shake(t):
    sx = sy = 0.0
    for p in PIECES:
        dt = t - p["landing"]
        if 0 <= dt < 0.22:
            amp = (1 - dt / 0.22) ** 2 * (2.6 if p["key"] in ("crest", "grey") else 1.6)
            sx += math.sin(dt * 90) * amp
            sy += math.cos(dt * 77) * amp
    if T_CHARGE <= t < T_END_BURN:
        amp = (t - T_CHARGE) / (T_END_BURN - T_CHARGE) * 3.4
        sx += math.sin(t * 61) * amp
        sy += math.cos(t * 53) * amp
    return sx, sy


def make_sparks():
    sparks = []
    for p in PIECES:
        al = np.asarray(p["img"], dtype=np.float32)[..., 3] / 255.0
        edge = np.abs(np.gradient(al)[0]) + np.abs(np.gradient(al)[1])
        ey, ex = np.nonzero(edge > 0.25)
        if len(ex) == 0:
            continue
        cx = p["img"].width / 2.0
        cy = p["img"].height / 2.0
        n = 22 if p["key"] in ("crest", "grey", "wings") else 14
        for _ in range(n):
            i = int(RNG.integers(0, len(ex)))
            lx, ly = float(ex[i]), float(ey[i])
            sx, sy = p["home"][0] + lx, p["home"][1] + ly
            vx, vy = lx - cx, ly - cy
            norm = math.hypot(vx, vy) or 1.0
            spd = RNG.uniform(90, 420)
            sparks.append({
                "t0": p["landing"] + RNG.uniform(0.0, 0.025),
                "x": sx, "y": sy,
                "vx": vx / norm * spd + p["dir"][0] * 120,
                "vy": vy / norm * spd + p["dir"][1] * 120 - 40,
                "life": RNG.uniform(0.22, 0.55),
                "g": RNG.uniform(600, 1100),
            })
    return sparks


SPARKS = []


def dust_field(n=170):
    return [{
        "x": RNG.uniform(0, LW), "y": RNG.uniform(0, LH),
        "v": RNG.uniform(4, 26), "p": RNG.uniform(0, math.tau),
        "r": RNG.uniform(0.8, 3.2), "i": RNG.uniform(0.05, 0.30),
    } for _ in range(n)]


DUST = []


def embers_field(n=520):
    out = []
    for _ in range(n):
        y0 = RNG.uniform(0.0, 1.0)
        out.append({
            "t0": T_END_BURN + (1.06 - y0) / 1.20 * BURN_LEN + RNG.uniform(-0.03, 0.05),
            "x": RNG.uniform(0.0, 1.0),
            "y": y0,
            "vx": RNG.uniform(-26, 26),
            "vy": RNG.uniform(-190, -60),
            "life": RNG.uniform(0.7, 1.5),
            "r": RNG.uniform(1.2, 4.0),
            "i": RNG.uniform(0.4, 1.3),
        })
    return out


EMBERS = []


def burn_field():
    """Smooth noise used to erode the emblem during the burn."""
    small = RNG.random((28, 18)).astype(np.float32)
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((256, 256), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255.0


BURNN = None


# ----------------------------------------------------------------------------- frame
def render_frame(t):
    zoom = zoom_at(t) * (1.0 + 0.012 * breath_level(t))
    sx, sy = impact_shake(t)
    breath = breath_level(t)

    # ---- background -----------------------------------------------------------------
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None]
    bg_top = PAL["bg_top"]
    bg_bot = PAL["bg_bot"]
    frame = (bg_top[None, None, :] * (1 - y[..., None]) + bg_bot[None, None, :] * y[..., None])
    frame = np.repeat(frame, W, axis=1)

    # warm atmosphere behind the mark, breathing with it
    atm = radial(W, H, W / 2 + sx, H * 0.50 + sy, W * 0.86, H * 0.42, 2.4)
    atm_strength = (0.16 + 0.55 * breath + 0.10 * smoothstep(0.6, 2.0, t)) * (1 - smoothstep(T_END_BURN + 0.35, T_END - 0.30, t))
    frame += atm[..., None] * PAL["atmosphere"] * atm_strength * 0.20

    light = np.zeros((LH, LW, 3), np.float32)

    # dust
    cam = smoothstep(0.0, 2.2, t)
    for d in DUST:
        yy = (d["y"] - d["v"] * t) % LH
        xx = d["x"] + math.sin(t * 0.7 + d["p"]) * 8
        tw = 0.55 + 0.45 * math.sin(t * 2.6 + d["p"] * 3)
        splat(light, xx, yy, d["r"] * 2.2, PAL["signal_glow"], d["i"] * tw * (0.5 + 0.5 * cam))

    # ---- emblem ---------------------------------------------------------------------
    emissive = np.zeros((H, W, 3), np.float32)

    if t < T_LOCK + 0.04:
        for p in PIECES:
            start = p["landing"] - FLY
            if t < start:
                continue
            prog = clamp((t - start) / FLY)
            e = ease_out(prog)
            hx, hy = p["home"][0] + p["img"].width / 2.0, p["home"][1] + p["img"].height / 2.0
            dx, dy = p["dir"]
            dist = 0.95 * W
            offx = dx * dist * (1 - e)
            offy = dy * dist * (1 - e)
            sc = (1.16 - 0.16 * e) * spring_settle(t - p["landing"], amp=0.030)
            rot = (1 - e) * 4.5 * (1 if dx >= 0 else -1) * (0.6 if dx == 0 else 1.0)
            cx = W / 2 + (hx - 541.5) * zoom + offx + sx
            cy = H / 2 + (hy - 958.0) * zoom + offy + sy
            # speed streaks
            if prog < 0.94:
                for k, (a, s) in enumerate(((0.16, 1.0), (0.09, 1.0))):
                    bx = cx - dx * 90 * (k + 1) * (1 - e) - dy * 0
                    by = cy - dy * 90 * (k + 1) * (1 - e)
                    stamp(frame, p["img"], bx, by, sc, rot, a)
            stamp(frame, p["img"], cx, cy, sc, rot, 1.0)
            # arrival flash on the piece edges
            dt = t - p["landing"]
            if 0 <= dt < 0.20:
                flash = (1 - dt / 0.20) ** 2
                glow = Image.fromarray(np.asarray(p["img"])[..., 3])
                glow = glow.resize((max(1, int(p["img"].width * sc)), max(1, int(p["img"].height * sc))), Image.BILINEAR)
                gb = np.asarray(glow.filter(ImageFilter.GaussianBlur(9)), np.float32) / 255.0
                gh, gw = gb.shape
                gx0, gy0 = int(cx - gw / 2), int(cy - gh / 2)
                ax0, ay0 = max(0, gx0), max(0, gy0)
                ax1, ay1 = min(W, gx0 + gw), min(H, gy0 + gh)
                if ax1 > ax0 and ay1 > ay0:
                    patch = gb[ay0 - gy0:ay1 - gy0, ax0 - gx0:ax1 - gx0][..., None]
                    emissive[ay0:ay1, ax0:ax1] += patch * PAL["ember"] * flash * 0.50

    else:
        x0, y0, x1, y1 = FULLBOX
        fcx, fcy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        sc = zoom
        fw, fh = int(FULLIMG.width * sc), int(FULLIMG.height * sc)
        if t < T_END_BURN:
            stamp(frame, FULLIMG, W / 2 + sx, H / 2 + sy, sc)
        else:
            # ---- burn away: the mark disintegrates from the bottom up ----------
            im = FULLIMG.resize((fw, fh), Image.BILINEAR)
            arr = np.asarray(im, dtype=np.float32) / 255.0
            yn = np.linspace(0, 1, fh, dtype=np.float32)[:, None]
            yn_emb = (yn - EMB_Y0) / (EMB_Y1 - EMB_Y0)
            prog = clamp((t - T_END_BURN) / BURN_LEN)
            # three octaves of noise so the front eats the artwork unevenly
            nz = np.zeros((fh, fw), np.float32)
            for oct_, weight in ((24, 0.50), (86, 0.32), (300, 0.18)):
                grid = Image.fromarray((RNG.random((max(2, oct_ * 2 // 3), oct_)) * 255).astype(np.uint8))
                nz += weight * (np.asarray(grid.resize((fw, fh), Image.BICUBIC), dtype=np.float32) / 255.0)
            field = yn_emb * 0.46 + nz * 0.54
            field /= float(field.max()) + 1e-6
            field = np.clip((field - 0.5) * 2.1 + 0.5, 0, 1)
            front = prog * 1.06 - 0.03
            keep = np.clip((front - field) * -28.0, 0, 1)      # 1 where field < front is false
            keep = np.clip((field - front) / 0.030, 0, 1)
            alpha0 = arr[..., 3].copy()
            arr[..., 3] *= keep
            arr[..., :3] *= keep[..., None]
            gx = int(W / 2 + sx - fw / 2)
            gy = int(H / 2 + sy - fh / 2)
            ax0, ay0 = max(0, gx), max(0, gy)
            ax1, ay1 = min(W, gx + fw), min(H, gy + fh)
            if ax1 > ax0 and ay1 > ay0:
                sub = arr[ay0 - gy:ay1 - gy, ax0 - gx:ax1 - gx]
                reg = frame[ay0:ay1, ax0:ax1]
                a = sub[..., 3:4]
                reg *= (1.0 - a)
                reg += sub[..., :3]
                # molten edges: whatever is within a hair of vanishing glows
                keepv = keep[ay0 - gy:ay1 - gy, ax0 - gx:ax1 - gx]
                line = np.exp(-((field - front) / 0.038) ** 2)[ay0 - gy:ay1 - gy, ax0 - gx:ax1 - gx]
                line = line * (1.0 - keepv) * alpha0[ay0 - gy:ay1 - gy, ax0 - gx:ax1 - gx]
                emissive[ay0:ay1, ax0:ax1] += line[..., None] * PAL["ember"] * 0.16
                lip = np.exp(-((field - front + 0.035) / 0.05) ** 2)[ay0 - gy:ay1 - gy, ax0 - gx:ax1 - gx]
                lip = lip * keepv * alpha0[ay0 - gy:ay1 - gy, ax0 - gx:ax1 - gx]
                emissive[ay0:ay1, ax0:ax1] += lip[..., None] * PAL["crimson"] * 0.05

    # ---- emissive light ------------------------------------------------------------
    if t >= T_LOCK + 0.12:
        # the artwork's own red circuitry and visor, scaled into place
        presence = 1.0 - smoothstep(T_END_BURN + 0.02, T_END_BURN + 0.40, t)
        for mask, gain, colour in ((CIRC, (0.075 + 0.075 * breath) * presence, PAL["crimson"]),
                                   (EYE, 0.70 * breath * presence, PAL["signal"])):
            if gain <= 0.001:
                continue
            m = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR),
                           dtype=np.float32) / 255.0
            # crop to the emblem bbox then scale around the centre
            bx0, by0, bx1, by1 = FULLBOX
            crop = m[by0:by1, bx0:bx1]
            sc = zoom
            im = Image.fromarray((crop * 255).astype(np.uint8)).resize(
                (int(crop.shape[1] * sc), int(crop.shape[0] * sc)), Image.BILINEAR)
            g = np.asarray(im, dtype=np.float32) / 255.0
            gh, gw = g.shape
            px, py = int(W / 2 + sx - gw / 2), int(H / 2 + sy - gh / 2)
            ax0, ay0 = max(0, px), max(0, py)
            ax1, ay1 = min(W, px + gw), min(H, py + gh)
            if ax1 > ax0 and ay1 > ay0:
                patch = g[ay0 - py:ay1 - py, ax0 - px:ax1 - px][..., None]
                emissive[ay0:ay1, ax0:ax1] += patch * np.array(colour, np.float32) * gain
                # bigger halo for the visor, in exactly the same place
                if mask is EYE:
                    gw0, gh0 = gw, gh
                    sw0, sh0 = max(1, gw0 // 5), max(1, gh0 // 5)
                    sm = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize(
                        (sw0, sh0), Image.BILINEAR), dtype=np.float32) / 255.0
                    pd = max(3, sw0 // 6)
                    smp = np.pad(sm, ((pd, pd), (pd, pd)), mode="constant")
                    hb = box_blur(smp[..., None], 5, 2)[..., 0][pd:pd + sh0, pd:pd + sw0]
                    hb = np.clip(hb * 2.4, 0, 1)
                    halo = np.asarray(Image.fromarray((hb * 255).astype(np.uint8)).resize(
                        (gw0, gh0), Image.BILINEAR), dtype=np.float32) / 255.0
                    emissive[ay0:ay1, ax0:ax1] += halo[ay0 - py:ay1 - py, ax0 - px:ax1 - px][..., None] \
                        * PAL["signal"] * (gain * 0.30)

    # soft outer halo hugging the silhouette
    if T_LOCK - 0.1 <= t < T_END_BURN + 0.55:
        em = EMBALPHA
        eim = Image.fromarray((em * 255).astype(np.uint8)).crop(FULLBOX)
        ew, eh = int(eim.width * zoom), int(eim.height * zoom)
        sw, sh = max(1, ew // 6), max(1, eh // 6)
        small = eim.resize((sw, sh), Image.BILINEAR)
        sm = np.asarray(small, dtype=np.float32) / 255.0
        pad = max(4, sw // 5)
        sm = np.pad(sm, ((pad, pad), (pad // 2, pad // 2)), mode="constant")
        halo = box_blur(sm[..., None], 4, 2)[..., 0][pad:pad + sh, pad // 2:pad // 2 + sw]
        halo = np.clip(halo * 1.35, 0, 1)
        hb = np.asarray(Image.fromarray((halo * 255).astype(np.uint8)).resize((ew, eh), Image.BILINEAR),
                        dtype=np.float32) / 255.0
        px0, py0 = int(W / 2 + sx - ew / 2), int(H / 2 + sy - eh / 2)
        ax0, ay0 = max(0, px0), max(0, py0)
        ax1, ay1 = min(W, px0 + ew), min(H, py0 + eh)
        if ax1 > ax0 and ay1 > ay0:
            emissive[ay0:ay1, ax0:ax1] += hb[ay0 - py0:ay1 - py0, ax0 - px0:ax1 - px0][..., None] \
                * PAL["atmosphere"] * (0.07 + 0.09 * breath) \
                * (1.0 - smoothstep(T_END_BURN, T_END_BURN + 0.5, t))

    # ignition flash
    ig = smoothstep(T_IGNITE, T_IGNITE + 0.14, t) * (1 - smoothstep(T_IGNITE + 0.14, T_IGNITE + 0.42, t))
    if ig > 0:
        ex, ey = world(541, 712, zoom, (sx, sy))
        splat(light, ex / 2, ey / 2, 36, PAL["signal"], ig * 2.0)
        splat(light, ex / 2, ey / 2, 13, PAL["hot"], ig * 2.4)

    # ---- sparks, rings --------------------------------------------------------------
    for sp in SPARKS:
        dt = t - sp["t0"]
        if dt < 0 or dt > sp["life"]:
            continue
        k = dt / sp["life"]
        x = sp["x"] + sp["vx"] * dt
        y = sp["y"] + sp["vy"] * dt + 0.5 * sp["g"] * dt * dt
        wx, wy = world(x, y, zoom, (sx, sy))
        splat(light, wx / 2, wy / 2, 3.4 * (1 - 0.45 * k), PAL["gold"], (1 - k) ** 1.3 * 2.3)

    for p in PIECES:
        dt = t - p["landing"]
        if 0 <= dt < 0.35:
            cx, cy = p["home"][0] + p["img"].width / 2.0, p["home"][1] + p["img"].height / 2.0
            wx, wy = world(cx, cy, zoom, (sx, sy))
            if p["key"] in ("crest", "grey", "wings", "bottom"):
                ring(light, wx / 2, wy / 2, 10 + 120 * (dt / 0.35) ** 0.7, 3.0,
                     PAL["ember"], 0.22 * (1 - dt / 0.35) ** 2)

    # final lock shockwave
    if T_LOCK <= t < T_LOCK + 1.0:
        dt = t - T_LOCK
        pulse = math.exp(-dt / 0.35)
        splat(light, LW / 2 + sx / 2, LH / 2 + sy / 2, 200 + 120 * dt, PAL["atmosphere"], 0.55 * pulse)

    # rim light sweep across the metal once it is together
    # power-on pulse across the finished mark
    if T_LOCK <= t < T_LOCK + 0.55:
        pulse = math.exp(-((t - T_LOCK - 0.05) / 0.09) ** 2)
        em = EMBALPHA
        eim = Image.fromarray((em * 255).astype(np.uint8)).crop(FULLBOX)
        ew, eh = int(eim.width * zoom), int(eim.height * zoom)
        ea = np.asarray(eim.resize((ew, eh), Image.BILINEAR), dtype=np.float32) / 255.0
        px0, py0 = int(W / 2 + sx - ew / 2), int(H / 2 + sy - eh / 2)
        ax0, ay0 = max(0, px0), max(0, py0)
        ax1, ay1 = min(W, px0 + ew), min(H, py0 + eh)
        if ax1 > ax0 and ay1 > ay0:
            emissive[ay0:ay1, ax0:ax1] += ea[ay0 - py0:ay1 - py0, ax0 - px0:ax1 - px0][..., None] \
                * np.array([1.0, 0.40, 0.22], np.float32) * (0.13 * pulse)

    # soft outer halo hugging the silhouette
    if T_LOCK - 0.1 <= t < T_END_BURN + 0.55:
        em = EMBALPHA
        eim = Image.fromarray((em * 255).astype(np.uint8)).crop(FULLBOX)
        ew, eh = int(eim.width * zoom), int(eim.height * zoom)
        sw, sh = max(1, ew // 6), max(1, eh // 6)
        small = eim.resize((sw, sh), Image.BILINEAR)
        sm = np.asarray(small, dtype=np.float32) / 255.0
        pad = max(4, sw // 5)
        sm = np.pad(sm, ((pad, pad), (pad // 2, pad // 2)), mode="constant")
        halo = box_blur(sm[..., None], 4, 2)[..., 0][pad:pad + sh, pad // 2:pad // 2 + sw]
        halo = np.clip(halo * 1.35, 0, 1)
        hb = np.asarray(Image.fromarray((halo * 255).astype(np.uint8)).resize((ew, eh), Image.BILINEAR),
                        dtype=np.float32) / 255.0
        px0, py0 = int(W / 2 + sx - ew / 2), int(H / 2 + sy - eh / 2)
        ax0, ay0 = max(0, px0), max(0, py0)
        ax1, ay1 = min(W, px0 + ew), min(H, py0 + eh)
        if ax1 > ax0 and ay1 > ay0:
            emissive[ay0:ay1, ax0:ax1] += hb[ay0 - py0:ay1 - py0, ax0 - px0:ax1 - px0][..., None] \
                * PAL["atmosphere"] * (0.07 + 0.09 * breath) \
                * (1.0 - smoothstep(T_END_BURN, T_END_BURN + 0.5, t))

    # ignition flash
    ig = smoothstep(T_IGNITE, T_IGNITE + 0.14, t) * (1 - smoothstep(T_IGNITE + 0.14, T_IGNITE + 0.42, t))
    if ig > 0:
        ex, ey = world(541, 712, zoom, (sx, sy))
        splat(light, ex / 2, ey / 2, 36, PAL["signal"], ig * 2.0)
        splat(light, ex / 2, ey / 2, 13, PAL["hot"], ig * 2.4)

    # ---- sparks, rings --------------------------------------------------------------
    for sp in SPARKS:
        dt = t - sp["t0"]
        if dt < 0 or dt > sp["life"]:
            continue
        k = dt / sp["life"]
        x = sp["x"] + sp["vx"] * dt
        y = sp["y"] + sp["vy"] * dt + 0.5 * sp["g"] * dt * dt
        wx, wy = world(x, y, zoom, (sx, sy))
        splat(light, wx / 2, wy / 2, 3.4 * (1 - 0.45 * k), PAL["gold"], (1 - k) ** 1.3 * 2.3)

    for p in PIECES:
        dt = t - p["landing"]
        if 0 <= dt < 0.35:
            cx, cy = p["home"][0] + p["img"].width / 2.0, p["home"][1] + p["img"].height / 2.0
            wx, wy = world(cx, cy, zoom, (sx, sy))
            if p["key"] in ("crest", "grey", "wings", "bottom"):
                ring(light, wx / 2, wy / 2, 10 + 120 * (dt / 0.35) ** 0.7, 3.0,
                     PAL["ember"], 0.22 * (1 - dt / 0.35) ** 2)

    # final lock shockwave
    if T_LOCK <= t < T_LOCK + 1.0:
        dt = t - T_LOCK
        pulse = math.exp(-dt / 0.35)
        splat(light, LW / 2 + sx / 2, LH / 2 + sy / 2, 200 + 120 * dt, PAL["atmosphere"], 0.55 * pulse)

    # rim light sweep across the metal once it is together
    if 1.70 <= t < 2.60:
        p = (t - 1.70) / 0.90
        sb = -0.30 + 1.62 * p
        em = EMBALPHA
        eim = Image.fromarray((em * 255).astype(np.uint8))
        eim = eim.crop(FULLBOX)
        ew, eh = int(eim.width * zoom), int(eim.height * zoom)
        eim = eim.resize((ew, eh), Image.BILINEAR)
        ea = np.asarray(eim, dtype=np.float32) / 255.0
        xs = np.linspace(-0.35, 1.35, ew, dtype=np.float32)[None, :]
        band = np.exp(-((xs - sb) / 0.15) ** 2) * (ea ** 0.7) * 0.24
        px0, py0 = int(W / 2 + sx - ew / 2), int(H / 2 + sy - eh / 2)
        ax0, ay0 = max(0, px0), max(0, py0)
        ax1, ay1 = min(W, px0 + ew), min(H, py0 + eh)
        if ax1 > ax0 and ay1 > ay0:
            band = band[ay0 - py0:ay1 - py0, ax0 - px0:ax1 - px0]
            emissive[ay0:ay1, ax0:ax1] += band[..., None] * np.array([1.0, 0.62, 0.42], np.float32)

    # ---- vanish aftermath ------------------------------------------------------------
    if t >= T_END_BURN:
        # embers rising out of the burn front
        for em in EMBERS:
            dt = t - em["t0"]
            if dt < 0 or dt > em["life"]:
                continue
            k = dt / em["life"]
            bx0, by0, bx1, by1 = FULLBOX
            x = bx0 + em["x"] * (bx1 - bx0)
            y = by0 + em["y"] * (by1 - by0)
            x += em["vx"] * dt
            y += em["vy"] * dt
            wx, wy = world(x, y, zoom, (sx, sy))
            splat(light, wx / 2, wy / 2, em["r"] * (1.4 - 0.4 * k), PAL["ember"],
                  em["i"] * (1 - k) ** 1.1 * 1.5)

        # ghost of the circuitry lingers, then everything collapses to a point
        if T_BURN_DONE - 0.02 <= t < min(T_COLLAPSE, T_BURN_DONE + 0.22):
            g = (1 - smoothstep(T_BURN_DONE, T_BURN_DONE + 0.24, t)) ** 1.4
            m = np.asarray(Image.fromarray((CIRC * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR),
                           dtype=np.float32) / 255.0
            bx0, by0, bx1, by1 = FULLBOX
            crop = m[by0:by1, bx0:bx1]
            sc0 = zoom
            im2 = Image.fromarray((crop * 255).astype(np.uint8)).resize(
                (int(crop.shape[1] * sc0), int(crop.shape[0] * sc0)), Image.BILINEAR)
            gg = np.asarray(im2, dtype=np.float32) / 255.0
            gh, gw = gg.shape
            px, py = int(W / 2 + sx - gw / 2), int(H / 2 + sy - gh / 2)
            ax0, ay0 = max(0, px), max(0, py)
            ax1, ay1 = min(W, px + gw), min(H, py + gh)
            if ax1 > ax0 and ay1 > ay0:
                emissive[ay0:ay1, ax0:ax1] += gg[ay0 - py:ay1 - py, ax0 - px:ax1 - px][..., None] \
                    * PAL["atmosphere"] * (0.13 * g)

        if T_COLLAPSE <= t < T_COLLAPSE + 0.30:
            k = (t - T_COLLAPSE) / 0.30
            ex, ey = world(541, 940, zoom, (sx, sy))
            splat(light, ex / 2, ey / 2, 9 + 7 * k, PAL["hot"], 3.4 * (1 - k) ** 1.3)
            ring(light, ex / 2, ey / 2, 8 + 240 * k ** 0.5, 13.0, PAL["ember"], 0.26 * (1 - k) ** 2.4)
        if T_COLLAPSE + 0.02 <= t < T_COLLAPSE + 0.16:
            fl = 1 - smoothstep(T_COLLAPSE + 0.02, T_COLLAPSE + 0.16, t)
            frame += fl * np.array([0.050, 0.026, 0.020], np.float32)
        if T_COLLAPSE + 0.10 <= t < T_COLLAPSE + 0.30:
            fl = 1 - smoothstep(T_COLLAPSE + 0.10, T_COLLAPSE + 0.30, t)
            frame += fl * np.array([0.022, 0.012, 0.010], np.float32)

    # ---- add light, bloom -----------------------------------------------------------
    frame += emissive
    frame += upsample(light, W, H)

    small = resize_f(frame, W // 3, H // 3)
    bright = np.clip((small - 0.30) / 0.70, 0, None)
    b1 = box_blur(bright, 4, 3)
    b2 = box_blur(bright, 12, 3)
    frame += upsample(b1, W, H) * 0.36 + upsample(b2, W, H) * 0.26

    # chromatic aberration pulls apart with light intensity
    strength = 1.0 + 2.4 * clamp(float(np.clip(frame[..., 0].max() - 0.7, 0, 1)))
    if strength > 1.05:
        sh = int(round(clamp(strength, 1, 3.4)))
        frame[..., 0] = np.roll(frame[..., 0], sh, axis=1)
        frame[..., 2] = np.roll(frame[..., 2], -sh, axis=1)

    # vignette
    vy = radial(W, H, W / 2, H / 2, W * 0.78, H * 0.62, 1.1)
    frame *= (0.55 + 0.45 * vy)[..., None]

    # grain
    frame += RNG.normal(0, 0.0055, (H, W, 1)).astype(np.float32)

    # fade in/out
    fade = smoothstep(0.0, 0.30, t) * (1 - smoothstep(T_END - 0.35, T_END, t))
    frame *= fade

    # tone map + gamma
    frame = np.clip(frame, 0, None)
    frame = frame / (1.0 + frame * 0.55)
    frame = np.clip(frame, 0, 1) ** (1 / 1.12)
    return (np.clip(frame, 0, 1) * 255).astype(np.uint8)


# ----------------------------------------------------------------------------- audio
SR = 44100


def env_adsr(n, a=0.005, d=0.05, s=0.0, r=0.2):
    t = np.linspace(0, 1, n, endpoint=False)
    e = np.minimum(t / max(a, 1e-4), 1.0)
    e *= np.exp(-np.maximum(t - a, 0) / max(d, 1e-4))
    return e


def noise(n):
    return np.random.default_rng(11).normal(0, 1, n).astype(np.float32)


def bandpass(sig, lo, hi):
    spec = np.fft.rfft(sig)
    freqs = np.fft.rfftfreq(len(sig), 1 / SR)
    m = ((freqs > lo) & (freqs < hi)).astype(np.float32)
    k = 24
    m = np.convolve(np.pad(m, (k, k), mode="edge"), np.ones(2 * k + 1) / (2 * k + 1), "same")[k:-k]
    return np.fft.irfft(spec * m, len(sig)).astype(np.float32)


def reverb(sig, tail=1.4, mix=0.32):
    n = int(SR * tail)
    ir = noise(n) * np.exp(-np.linspace(0, 6, n)).astype(np.float32)
    ir = bandpass(ir, 120, 5200)
    ir /= np.abs(ir).max() + 1e-6
    out = np.zeros_like(sig)
    L = len(sig) + n
    fir = np.fft.rfft(ir, L)
    for c in range(sig.shape[1]):
        wet = np.fft.irfft(np.fft.rfft(sig[:, c], L) * fir, L)[: len(sig)]
        wet /= np.abs(wet).max() + 1e-6
        out[:, c] = sig[:, c] * (1 - mix) + wet * mix
    return out


def stereo(x, pan=0.0):
    l = x * math.sqrt((1 - pan) / 2) if pan > 0 else x * math.sqrt((1 + pan) / 2)
    r = x * math.sqrt((1 + pan) / 2) if pan > 0 else x * math.sqrt((1 - pan) / 2)
    return np.stack([l, r], axis=1).astype(np.float32)


def clank(dur=0.5, base=520.0, pan=0.0):
    n = int(SR * dur)
    t = np.arange(n) / SR
    out = np.zeros(n, np.float32)
    for ratio, amp, dec in ((1.0, 0.55, 0.10), (2.41, 0.32, 0.07), (3.86, 0.20, 0.05), (5.31, 0.12, 0.035)):
        out += amp * np.sin(2 * math.pi * base * ratio * t) * np.exp(-t / dec)
    hit = bandpass(noise(n), 1400, 7000) * np.exp(-t / 0.012) * 0.55
    out += hit
    out += np.sin(2 * math.pi * 62 * t) * np.exp(-t / 0.09) * 0.5
    return stereo(out.astype(np.float32), pan)


def whoosh(dur=0.45, f0=300, f1=2600, amp=0.2, pan=0.0):
    n = int(SR * dur)
    t = np.arange(n) / SR
    nz = noise(n)
    chirp = np.sin(2 * math.pi * (f0 * t + (f1 - f0) * t ** 2 / (2 * dur)))
    sig = bandpass(nz, 500, 6000) * 0.6 + chirp * 0.25
    sig *= (np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.6) * amp
    return stereo(sig.astype(np.float32), pan)


def build_audio(dur=T_END):
    n = int(SR * dur)
    t = np.arange(n) / SR
    L = np.zeros(n, np.float32)

    def add(x, at=0.0, gain=1.0):
        i = int(at * SR)
        if i < 0:
            x = x[-i:]
            i = 0
        if i >= n or len(x) == 0:
            return
        m = min(len(x), n - i)
        L_add = x[:m]
        if L_add.ndim == 2:
            G[0][i:i + m] += L_add[:, 0] * gain
            G[1][i:i + m] += L_add[:, 1] * gain
        else:
            G[0][i:i + m] += L_add * gain
            G[1][i:i + m] += L_add * gain

    G = [np.zeros(n, np.float32), np.zeros(n, np.float32)]

    # drone bed
    bed = (0.55 * np.sin(2 * math.pi * 41 * t) + 0.30 * np.sin(2 * math.pi * 61.5 * t)
           + 0.16 * np.sin(2 * math.pi * 82.3 * t + 1.1))
    bed *= 0.16 * (0.55 + 0.45 * np.sin(2 * math.pi * 0.13 * t - 1.4))
    bed = bandpass(bed.astype(np.float32), 25, 700)
    G[0] += bed
    G[1] += bed * 0.96
    air = bandpass(noise(n), 300, 2400) * 0.045 * (0.5 + 0.5 * np.sin(2 * math.pi * 0.09 * t))
    G[0] += air
    G[1] += air * 0.9

    # assembly: servo whir then a metal lock, panned by the side it came from
    for p in PIECES:
        pan = -0.5 if p["side"] == -1 else (0.5 if p["side"] == 1 else 0.0)
        heavy = p["key"] in ("crest", "grey", "wings")
        add(whoosh(0.42, 260, 2200, 0.20 if heavy else 0.14, pan * 0.8), p["landing"] - 0.44)
        add(clank(0.55, 420 if heavy else 560, pan), p["landing"], 0.85 if heavy else 0.62)

    # final lock: big hit + short shimmer
    add(clank(1.1, 180, 0.0), T_LOCK, 1.0)
    add(whoosh(0.7, 200, 4200, 0.22, 0.0), T_LOCK - 0.04)

    # ignition chord
    ig_n = int(SR * 1.3)
    it = np.arange(ig_n) / SR
    chord = sum(a * np.sin(2 * math.pi * f * it) for f, a in ((440, 0.30), (660, 0.20), (880, 0.13), (1320, 0.07)))
    chord *= np.exp(-it / 0.55) * (1 - np.exp(-it / 0.02)) * 0.35
    add(chord.astype(np.float32), T_IGNITE)
    add(whoosh(0.5, 900, 5200, 0.12, 0.0), T_IGNITE - 0.04)

    # breathing pad
    bp = int(SR * max(0.6, T_END_BURN - T_CHARGE + 0.4))
    bt = np.arange(bp) / SR
    pad = (0.5 * np.sin(2 * math.pi * 110 * bt) + 0.28 * np.sin(2 * math.pi * 165 * bt + 0.7)
           + 0.16 * np.sin(2 * math.pi * 220 * bt + 1.9))
    pad *= (0.5 - 0.5 * np.cos(2 * math.pi * bt / 1.70)) ** 1.4
    pad *= 0.22 * (1 - np.exp(-bt / 0.3))
    add(pad.astype(np.float32), T_IGNITE + 0.30)
    breathe_noise = bandpass(noise(bp), 200, 1400) * (0.5 - 0.5 * np.cos(2 * math.pi * bt / 1.70)) * 0.05
    add(breathe_noise.astype(np.float32), T_IGNITE + 0.30)

    # charge riser into the burn
    ch = int(SR * max(0.4, T_END_BURN - T_CHARGE))
    ct = np.arange(ch) / SR
    swipe = float(max(0.35, T_END_BURN - T_CHARGE))
    sweep = np.sin(2 * math.pi * (180 * ct + (720 - 180) * ct ** 2 / (2 * swipe)))
    riser = bandpass(noise(ch), 400, 8000) * 0.35 + sweep * 0.22
    riser *= np.linspace(0, 1, ch) ** 1.8 * 0.6
    add(riser.astype(np.float32), T_CHARGE)

    # the burn: a huge sub + a long tail
    bn = int(SR * 1.8)
    btt = np.arange(bn) / SR
    boom = (np.sin(2 * math.pi * (70 * btt - 26 * btt ** 2)) * np.exp(-btt / 0.30) * 0.85
            + np.sin(2 * math.pi * 39 * btt) * np.exp(-btt / 0.55) * 0.5)
    add(boom.astype(np.float32), T_END_BURN, 1.0)
    add(clank(1.4, 130, 0.0), T_END_BURN, 0.55)
    add(whoosh(1.2, 1600, 180, 0.30, 0.0), T_END_BURN - 0.02)

    # collapse: a small bright point, then silence
    cn = int(SR * 0.5)
    ctt = np.arange(cn) / SR
    ping = np.sin(2 * math.pi * 1760 * ctt) * np.exp(-ctt / 0.06) * 0.16
    add(ping.astype(np.float32), T_COLLAPSE)

    mixdown = np.stack(G, axis=1)
    mixdown = np.nan_to_num(mixdown)
    mixdown = reverb(mixdown, 1.5, 0.26)
    mixdown = np.nan_to_num(mixdown)
    # gentle limiter
    peak = np.abs(mixdown).max()
    if peak > 0.98:
        mixdown *= 0.98 / peak
    mixdown *= np.linspace(1, 1, n)[:, None]
    fade = np.clip(np.minimum(np.arange(n) / (0.25 * SR), (n - np.arange(n)) / (0.35 * SR)), 0, 1)[:, None]
    mixdown *= fade
    return mixdown


def write_wav(path, data):
    pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ----------------------------------------------------------------------------- main
def main():
    global PIECES, FULLIMG, FULLBOX, CIRC, EYE, SPARKS, DUST, EMBERS, BURNN, EMBALPHA
    mode = sys.argv[1] if len(sys.argv) > 1 else "preview"
    scale = 1.0 if mode == "final" else 0.5
    out = OUT_RAW if mode == "final" else "/home/user/gamershub-intro-preview.mp4"

    PIECES = build_pieces()
    FULLIMG, FULLBOX, EMBALPHA = build_full()
    CIRC, EYE = build_masks(FULLBOX)
    SPARKS = make_sparks()
    DUST = dust_field()
    EMBERS = embers_field()
    BURNN = burn_field()

    silent = "/tmp/gh_intro_silent.mp4"
    audio = "/tmp/gh_intro_audio.wav"
    w, h = int(W * scale) // 2 * 2, int(H * scale) // 2 * 2
    fps = FPS if mode == "final" else 20
    frames = int(round(T_END * fps))

    import imageio_ffmpeg
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [exe, "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{w}x{h}",
           "-r", str(fps), "-i", "-", "-an", "-c:v", "libx264", "-preset", "medium",
           "-crf", "17" if mode == "final" else "23", "-pix_fmt", "yuv420p", silent]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for i in range(frames):
        t = i / fps
        img = render_frame(t)
        im = Image.fromarray(img).resize((w, h), Image.LANCZOS)
        proc.stdin.write(np.asarray(im, dtype=np.uint8).tobytes())
        if i % 20 == 0:
            print(f"frame {i}/{frames}", flush=True)
    proc.stdin.close()
    proc.wait()

    data = build_audio(T_END)
    write_wav(audio, data)
    subprocess.run([exe, "-y", "-i", silent, "-i", audio, "-c:v", "copy", "-c:a", "aac",
                    "-b:a", "192k", "-shortest", "-movflags", "+faststart", out],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("wrote", out)


if __name__ == "__main__":
    main()
