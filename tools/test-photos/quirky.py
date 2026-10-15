"""Makes a replica of a photographer's raw dump from a clean wedding set, with an answer key.

Wikimedia uploads are already hand-picked. A real pendrive is not: bursts of near-identical
frames, shaky and out-of-focus shots, dark and flash-blown frames, crooked frames, faces cut by
the edge, black-and-white copies, accidental shots. This adds realistic versions of those, made
from the real photos, each at the right moment of the day, and writes TRUTH.json saying what is
wrong with each one, so the app's choices can be scored.

  python3 quirky.py WEDDING_DIR OUT_DIR YUNET_MODEL
"""
import datetime
import json
import math
import os
import random
import shutil
import sys

import numpy as np
import onnxruntime as ort
import piexif
from PIL import Image, ImageEnhance, ImageFilter, ImageOps

random.seed(7)


def faces(session, img):
    """YuNet face boxes (0..1), biggest first."""
    w0, h0 = img.size
    s = 640 / max(w0, h0)
    w, h = max(1, round(w0 * s)), max(1, round(h0 * s))
    pw, ph = (w + 31) // 32 * 32, (h + 31) // 32 * 32
    arr = np.zeros((ph, pw, 3), np.float32)
    arr[:h, :w] = np.asarray(img.resize((w, h)), np.float32)[:, :, ::-1]
    out = dict(zip([o.name for o in session.get_outputs()], session.run(None, {"input": arr.transpose(2, 0, 1)[None]})))
    found = []
    for st in (8, 16, 32):
        cols = pw // st
        cls, obj, bb = out[f"cls_{st}"].reshape(-1), out[f"obj_{st}"].reshape(-1), out[f"bbox_{st}"].reshape(-1, 4)
        score = np.sqrt(np.clip(cls, 0, 1) * np.clip(obj, 0, 1))
        for i in np.nonzero(score >= 0.7)[0]:
            r, c = divmod(int(i), cols)
            cx, cy = (c + bb[i, 0]) * st, (r + bb[i, 1]) * st
            bw, bh = math.exp(bb[i, 2]) * st, math.exp(bb[i, 3]) * st
            found.append((float(score[i]), (cx - bw / 2) / w, (cy - bh / 2) / h, (cx + bw / 2) / w, (cy + bh / 2) / h))
    found.sort(key=lambda f: -(f[3] - f[1]))
    kept = []
    for f in found:
        if all(min(f[3], k[3]) - max(f[1], k[1]) <= 0 or min(f[4], k[4]) - max(f[2], k[2]) <= 0 for k in kept):
            kept.append(f)
    return kept


def motion_blur(img, length):
    k = np.zeros((length, length), np.float32)
    k[length // 2, :] = 1.0 / length
    angle = random.uniform(-25, 25)
    a = np.asarray(img, np.float32)
    # Shift-and-average along a direction: a hand shake during a slow shutter.
    out = np.zeros_like(a)
    dx, dy = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    for t in range(length):
        o = t - length // 2
        out += np.roll(np.roll(a, int(round(o * dy)), axis=0), int(round(o * dx)), axis=1)
    return Image.fromarray(np.clip(out / length, 0, 255).astype(np.uint8))


def burst_frame(img, n):
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
    # Crop the empty corners away, as a camera held crooked would show none.
    m = math.sin(math.radians(abs(deg)))
    return r.crop((m * h * 0.6, m * w * 0.6, w - m * h * 0.6, h - m * w * 0.6)).resize((w, h))


def face_cut(img, f):
    """Frame shifted so the main face is half outside the photo."""
    w, h = img.size
    _, l, t, r, b = f
    fw = (r - l) * w
    if (l + r) / 2 < 0.5:      # face on the left: cut it at the left edge
        x0 = l * w + fw * 0.5
        box = (x0, 0, min(w, x0 + 0.75 * w), h)
    else:
        x1 = r * w - fw * 0.5
        box = (max(0, x1 - 0.75 * w), 0, x1, h)
    return img.crop(tuple(int(v) for v in box))


def main(src, out, model):
    session = ort.InferenceSession(model, providers=["CPUExecutionProvider"])
    credits = json.load(open(os.path.join(src, "CREDITS.json")))
    items = []
    for c in credits:
        path = os.path.join(src, c["file"])
        folder = os.path.dirname(c["file"])
        taken = datetime.datetime.strptime(c["taken"][:19], "%Y-%m-%d %H:%M:%S")
        items.append({"path": path, "folder": folder, "taken": taken, "credit": c})
    with_faces = []
    for it in items:
        img = ImageOps.exif_transpose(Image.open(it["path"])).convert("RGB")
        it["faces"] = faces(session, img)
        if it["faces"] and it["faces"][0][3] - it["faces"][0][1] >= 0.08:
            with_faces.append(it)
    random.shuffle(with_faces)
    pool = iter(with_faces)

    made = []   # (folder, taken, image, truth)
    def take(n):
        return [next(pool) for _ in range(min(n, len(with_faces)))]

    def load(it):
        return ImageOps.exif_transpose(Image.open(it["path"])).convert("RGB")

    for it in take(20):
        img = load(it)
        for k in (1, 2):
            made.append((it, k, burst_frame(img, k), {"defect": "burst", "expect": "DUPLICATE"}))
    for it in take(10):
        made.append((it, 3, motion_blur(load(it), random.randint(18, 30)), {"defect": "shaky", "expect": "BLURRY"}))
    for it in take(8):
        made.append((it, 3, load(it).filter(ImageFilter.GaussianBlur(random.uniform(4, 7))), {"defect": "out of focus", "expect": "BLURRY"}))
    for it in take(6):
        img = ImageEnhance.Brightness(load(it)).enhance(random.uniform(0.18, 0.28))
        made.append((it, 3, img, {"defect": "too dark", "expect": "TOO_DARK"}))
    for it in take(6):
        img = ImageEnhance.Brightness(load(it)).enhance(random.uniform(2.4, 3.2))
        made.append((it, 3, img, {"defect": "flash blown out", "expect": "TOO_BRIGHT"}))
    for it in take(6):
        made.append((it, 3, tilted(load(it)), {"defect": "crooked", "expect": "TILTED"}))
    for it in take(6):
        made.append((it, 3, face_cut(load(it), it["faces"][0]), {"defect": "face cut by the edge", "expect": "FACE_CUT_OFF"}))
    for it in take(6):
        made.append((it, 0, ImageOps.grayscale(load(it)).convert("RGB"), {"defect": "black-and-white copy", "expect": "DUPLICATE"}))
    # Accidental shots: lens covered, camera pointing at the floor while walking.
    for it in random.sample(items, 3):
        img = load(it)
        w, h = img.size
        floor = img.crop((0, int(h * 0.7), w, h)).resize((w, h)).filter(ImageFilter.GaussianBlur(9))
        made.append((it, 4, floor, {"defect": "accidental shot", "expect": "NOT_PICKED"}))
    covered = ImageEnhance.Brightness(load(items[0])).enhance(0.03)
    made.append((items[0], 4, covered, {"defect": "accidental shot", "expect": "NOT_PICKED"}))

    # Everything in time order, numbered like a camera, in the photographer's folders.
    entries = [(it["folder"], it["taken"], None, it, {"defect": None, "expect": "OK"}) for it in items]
    for it, sec, img, truth in made:
        entries.append((it["folder"], it["taken"] + datetime.timedelta(seconds=sec), img, it, truth))
    entries.sort(key=lambda e: (e[1], e[2] is not None))
    if os.path.exists(out):
        shutil.rmtree(out)
    answer = {}
    for n, (folder, taken, img, it, truth) in enumerate(entries, start=1):
        os.makedirs(os.path.join(out, folder), exist_ok=True)
        name = f"IMG_{n:04d}.jpg"
        dest = os.path.join(out, folder, name)
        if img is None:
            shutil.copyfile(it["path"], dest)
        else:
            img.save(dest, quality=88)
            stamp = taken.strftime("%Y:%m:%d %H:%M:%S")
            piexif.insert(piexif.dump({"0th": {piexif.ImageIFD.Orientation: 1},
                                       "Exif": {piexif.ExifIFD.DateTimeOriginal: stamp.encode()}}), dest)
        answer[f"{folder}/{name}"] = dict(truth, source=it["credit"]["file"], title=it["credit"]["title"])
    json.dump(answer, open(os.path.join(out, "TRUTH.json"), "w"), indent=1)
    json.dump([dict(c, file=k) for k, v in answer.items() for c in [v]], open(os.path.join(out, "CREDITS.json"), "w"), indent=1)
    with open(os.path.join(out, "README.md"), "w") as fh:
        fh.write("# Replica photographer's dump\n\nThe real wedding in ../wedding plus realistic bad versions of its\n"
                 "photos (bursts, shaky, out of focus, dark, blown out, crooked, face cut, black-and-white copies,\n"
                 "accidental shots). TRUTH.json says what is wrong with each. Test data only, never in the app.\n"
                 "Made from freely licensed photos; see ../wedding/CREDITS.json for authors and licences.\n")
    counts = {}
    for v in answer.values():
        counts[v["defect"] or "original"] = counts.get(v["defect"] or "original", 0) + 1
    print(len(answer), "photos:", counts)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
