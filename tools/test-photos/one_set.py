"""One real event's photos as a phone folder, for the real-phone test: never a mix of events.

    python3 one_set.py SETS_JSONL SET_ID OUT_DIR SIZE [FOLDERS] [LIMIT]

SET_ID is a set found by the harvest (sets.jsonl on harvest-data), e.g. Goutam1962_2023-11-23 (a
Garhwali wedding day, 813 photos). SIZE is "small" (the 1280 px copy, made at most 1400 px, about
what the app reads), "full" (the photographer's original file, byte for byte; Wikimedia sends
these slowly, about 25 s each) or "camera" (the 1280 px copy enlarged to 6000 px with a camera's fine
grain, JPEG quality 95: about the size and file weight of a 24 MP camera's photo, 7-9 MB; for timing, since the app's work depends on
the file's pixels and bytes, not on how sharp they are). FOLDERS splits the photos into the photographer's event folders by words in
their titles, e.g. "haldi=01 Haldi,*=02 Wedding" (first match wins; * is the rest); without it
everything is one folder. LIMIT keeps the first N photos in time order.
Photos are named IMG_0001.jpg... in time order and keep their capture time (EXIF), which the app
uses to find the big moments. Test data only, never part of the app; credits:
sets/<id>.credits.json on harvest-data.
"""
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image

UA = "AlbumDoneTest/1.0 (phone test; freely licensed photos only)"
LONG_SIDE = 1400
CAMERA_LONG_SIDE = 6000
CAMERA_GRAIN = 4.0


def original(url):
    """The original file of a Wikimedia thumbnail; other addresses as they are."""
    m = re.match(r"https://(?:thumb|upload)\.wikimedia\.org/wikipedia/commons/thumb/(\w)/(\w\w)/([^/]+)/[^/]+$", url)
    return f"https://upload.wikimedia.org/wikipedia/commons/{m[1]}/{m[2]}/{m[3]}" if m else url


def fetch(url):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            # Too many requests: wait as long as the site asks (Wikimedia slows big downloads).
            wait = int(e.headers.get("Retry-After") or 0) or 5 * (attempt + 1)
            print("retry", attempt, url, e, file=sys.stderr)
            time.sleep(min(wait, 60))
        except Exception as e:  # a missing photo is skipped
            print("retry", attempt, url, e, file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    return None


def exif_with_time(img, taken):
    """The photo's EXIF, with the capture time from the photo's page when the copy lost it."""
    exif = img.getexif()
    if taken and len(taken) >= 19 and not exif.get_ifd(0x8769).get(0x9003):
        stamp = taken[:19].replace("-", ":", 2)
        exif.get_ifd(0x8769)[0x9003] = stamp  # DateTimeOriginal
        exif[0x0132] = stamp
    return exif


def folder_for(title, rules):
    t = title.lower()
    for word, name in rules:
        if word == "*" or word in t:
            return name
    return ""


def main(sets_path, set_id, out, size, folders="", limit="0"):
    s = next(json.loads(l) for l in open(sets_path) if l.strip() and json.loads(l)["id"] == set_id)
    rules = [tuple(x.split("=", 1)) for x in folders.split(",") if "=" in x]
    files = sorted(s["files"], key=lambda f: (f.get("taken") or "", f.get("title") or ""))
    if int(limit) > 0:
        files = files[: int(limit)]
    n = 0
    for f in files:
        url = original(f["thumb"]) if size == "full" else f["thumb"]
        data = fetch(url)
        if data is None:
            continue
        n += 1
        sub = folder_for(f.get("title") or "", rules) if rules else ""
        path = os.path.join(out, sub, f"IMG_{n:04d}.jpg")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if size == "full":
            open(path, "wb").write(data)
        elif size == "camera":
            img = Image.open(io.BytesIO(data))
            exif = exif_with_time(img, f.get("taken"))
            img = img.convert("RGB")
            scale = CAMERA_LONG_SIDE / max(img.size)
            img = img.resize((round(img.width * scale), round(img.height * scale)), Image.BICUBIC)
            # A camera's photo carries fine grain, which is what makes it 8-10 MB; an enlarged copy
            # is smooth and far lighter, so grain like a camera's is added.
            import numpy as np
            a = np.asarray(img).astype(np.int16)
            grain = np.random.default_rng(n).normal(0, CAMERA_GRAIN, a.shape[:2] + (1,)).astype(np.int16)
            img = Image.fromarray(np.clip(a + grain, 0, 255).astype(np.uint8))
            img.save(path, quality=95, exif=exif)
        else:
            img = Image.open(io.BytesIO(data))
            exif = exif_with_time(img, f.get("taken"))
            img = img.convert("RGB")
            img.thumbnail((LONG_SIDE, LONG_SIDE))
            img.save(path, quality=88, exif=exif)
    print(n, "photos of", set_id, "in", out)


if __name__ == "__main__":
    main(*sys.argv[1:7])
