"""A full-size folder for the phone test: about 1,000 real, freely licensed photos, the most one
album takes (plan/AlbumSize.kt). It checks that the app gets through a folder that big on a
phone (time, memory, the background job), not how good the album is: the photos come from several
weddings.

    python3 big_folder.py TEST_PHOTOS_DIR SETS_JSONL OUT_DIR [TARGET]

TEST_PHOTOS_DIR is the test-photos branch (the Bengali wedding as a raw dump, the Garhwali and
the Rajasthani weddings); the rest are downloaded from Flickr photo sets found by the harvest
(SETS_JSONL from the harvest-data branch), wedding sets first, each in its own sub-folder.
Photos are made at most 1400 px on the long side, as the app reads them at about 1280 px.
Test data only: never part of the app. Credits stay with their sources (CREDITS.json on
test-photos, sets/<id>.credits.json on harvest-data).
"""
import io
import json
import os
import sys
import time
import urllib.request

from PIL import Image, ImageOps

UA = "AlbumDoneTest/1.0 (phone test; freely licensed photos only)"
LONG_SIDE = 1400


def save(img, path):
    img = ImageOps.exif_transpose(img).convert("RGB")
    img.thumbnail((LONG_SIDE, LONG_SIDE))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, quality=85)


def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
                return Image.open(io.BytesIO(r.read()))
        except Exception as e:  # a missing photo is skipped, the folder just gets the next one
            print("retry", attempt, url, e, file=sys.stderr)
            time.sleep(2 * (attempt + 1))
    return None


def main(test_dir, sets_path, out, target=1000):
    target = int(target)
    n = 0
    # The local weddings first: the Bengali one as a raw dump (its own three folders), then two more.
    local = [("quirky", None), ("garhwali", "04 Garhwali wedding"), ("rajasthan", "05 Rajasthani wedding")]
    for name, folder in local:
        root = os.path.join(test_dir, name)
        for dirpath, _, files in sorted(os.walk(root)):
            for fn in sorted(files):
                if not fn.lower().endswith((".jpg", ".jpeg")) or n >= target:
                    continue
                sub = folder or os.path.relpath(dirpath, root)
                save(Image.open(os.path.join(dirpath, fn)), os.path.join(out, sub, fn))
                n += 1
    print(n, "local photos", file=sys.stderr)
    sets = [json.loads(line) for line in open(sets_path) if line.strip()]
    sets.sort(key=lambda s: (s["occasion"] != "WEDDING", -len(s["files"])))
    k = 6
    for s in sets:
        if n >= target:
            break
        files = [f for f in s["files"] if "staticflickr.com" in (f.get("thumb") or "")]
        if len(files) < 10:
            continue
        folder = f"{k:02d} {s['user'][:24]}"
        got = 0
        for i, f in enumerate(files):
            if n >= target:
                break
            img = fetch(f["thumb"])
            if img is None:
                continue
            save(img, os.path.join(out, folder, f"IMG_{i + 1:04d}.jpg"))
            n += 1
            got += 1
        if got:
            k += 1
            print(folder, got, file=sys.stderr)
    print(n, "photos in", out)


if __name__ == "__main__":
    main(*sys.argv[1:5])
