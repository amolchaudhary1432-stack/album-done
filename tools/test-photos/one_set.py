"""One real event's photos as a phone folder, for the real-phone test: never a mix of events.

    python3 one_set.py SETS_JSONL SET_ID OUT_DIR SIZE [FOLDERS] [LIMIT]

SET_ID is a set found by the harvest (sets.jsonl on harvest-data), e.g. Goutam1962_2023-11-23 (a
Garhwali wedding day, 813 photos). SIZE is "small" (the 1280 px copy, made at most 1400 px, about
what the app reads) or "full" (the photographer's original file, byte for byte, to time the app
on camera-size photos). FOLDERS splits the photos into the photographer's event folders by words in
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
import urllib.parse
import urllib.request

from PIL import Image

UA = "AlbumDoneTest/1.0 (phone test; freely licensed photos only)"
LONG_SIDE = 1400


def original(url):
    """The original file of a Wikimedia thumbnail; other addresses as they are."""
    m = re.match(r"https://(?:thumb|upload)\.wikimedia\.org/wikipedia/commons/thumb/(\w)/(\w\w)/([^/]+)/[^/]+$", url)
    return f"https://upload.wikimedia.org/wikipedia/commons/{m[1]}/{m[2]}/{m[3]}" if m else url


def fetch(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=120) as r:
                return r.read()
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
        else:
            img = Image.open(io.BytesIO(data))
            exif = exif_with_time(img, f.get("taken"))
            img = img.convert("RGB")
            img.thumbnail((LONG_SIDE, LONG_SIDE))
            img.save(path, quality=88, exif=exif)
    print(n, "photos of", set_id, "in", out)


if __name__ == "__main__":
    main(*sys.argv[1:7])
