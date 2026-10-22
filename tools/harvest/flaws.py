"""Bad versions of real photos, to turn an uploaded (already hand-picked) photo set into something
like a photographer's raw folder: burst frames, shaky and out-of-focus shots, dark and flash-blown
frames, crooked frames, faces cut by the edge, black-and-white copies, accidental shots.

Used by harvest.py: each planted photo is measured like any other and listed in <set>.truth.json
with what is wrong with it, so the brain can be scored on keeping them out (the tougher test).
Same recipes as tools/test-photos/quirky.py (the replica raw dump).
"""
import math
import random

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageOps

# Share of photos that get a bad twin, and how often each kind of fault happens (a raw folder
# has many more burst frames than anything else).
PLANT_SHARE = 0.4
KINDS = [("burst", 40), ("shaky", 10), ("out of focus", 9), ("too dark", 8), ("flash blown out", 6),
         ("crooked", 8), ("face cut by the edge", 7), ("black-and-white copy", 7), ("accidental shot", 5)]
# Seconds after the real photo, so the planted one sits next to it in time, as on a real card.
OFFSET = {"burst": 1, "black-and-white copy": 0}


def motion_blur(img, length):
    a = np.asarray(img, np.float32)
    out = np.zeros_like(a)
    angle = random.uniform(-25, 25)
    dx, dy = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    for t in range(length):
        o = t - length // 2
        out += np.roll(np.roll(a, int(round(o * dy)), axis=0), int(round(o * dx)), axis=1)
    return Image.fromarray(np.clip(out / length, 0, 255).astype(np.uint8))


def burst_frame(img):
    """The next frame of a burst: the camera moved a little, the light flickered."""
    w, h = img.size
    dx, dy = random.uniform(0.005, 0.02) * w, random.uniform(0.005, 0.02) * h
    frame = img.crop((dx, dy, w - (0.02 * w - dx), h - (0.02 * h - dy))).resize((w, h))
    frame = frame.rotate(random.uniform(-0.6, 0.6), resample=Image.BICUBIC)
    return ImageEnhance.Brightness(frame).enhance(random.uniform(0.95, 1.05))


def tilted(img):
    deg = random.choice([-1, 1]) * random.uniform(5, 9)
    w, h = img.size
    r = img.rotate(deg, resample=Image.BICUBIC, expand=False)
    m = math.sin(math.radians(abs(deg)))
    return r.crop((m * h * 0.6, m * w * 0.6, w - m * h * 0.6, h - m * w * 0.6)).resize((w, h))


def face_cut(img, box):
    """Frame shifted so the main face is half outside the photo. box: (left, top, right, bottom) 0..1."""
    w, h = img.size
    l, _, r, _ = box
    fw = (r - l) * w
    # The new edge runs through the middle of the face, so half of it is outside the photo.
    if (l + r) / 2 < 0.5:
        x0 = l * w + fw * 0.5
        crop = (x0, 0, min(w, x0 + 0.75 * w), h)
    else:
        x1 = r * w - fw * 0.5
        crop = (max(0, x1 - 0.75 * w), 0, x1, h)
    return img.crop(tuple(int(v) for v in crop))


def plant(img, main_face=None):
    """One bad version of img, or None: (kind, image, seconds after the original)."""
    if random.random() >= PLANT_SHARE:
        return None
    kinds = [(k, wgt) for k, wgt in KINDS if k != "face cut by the edge" or (main_face and main_face[2] - main_face[0] >= 0.08)]
    kind = random.choices([k for k, _ in kinds], [wgt for _, wgt in kinds])[0]
    img = ImageOps.exif_transpose(img).convert("RGB")
    if kind == "burst":
        bad = burst_frame(img)
    elif kind == "shaky":
        bad = motion_blur(img, max(8, int(max(img.size) * random.uniform(0.015, 0.025))))
    elif kind == "out of focus":
        bad = img.filter(ImageFilter.GaussianBlur(max(img.size) * random.uniform(0.004, 0.007)))
    elif kind == "too dark":
        bad = ImageEnhance.Brightness(img).enhance(random.uniform(0.18, 0.28))
    elif kind == "flash blown out":
        bad = ImageEnhance.Brightness(img).enhance(random.uniform(2.4, 3.2))
    elif kind == "crooked":
        bad = tilted(img)
    elif kind == "face cut by the edge":
        bad = face_cut(img, main_face)
    elif kind == "black-and-white copy":
        bad = ImageOps.grayscale(img).convert("RGB")
    else:  # accidental: camera pointing at the floor, or the lens covered
        w, h = img.size
        bad = (img.crop((0, int(h * 0.7), w, h)).resize((w, h)).filter(ImageFilter.GaussianBlur(9))
               if random.random() < 0.7 else ImageEnhance.Brightness(img).enhance(0.03))
    return kind, bad, OFFSET.get(kind, 3)
