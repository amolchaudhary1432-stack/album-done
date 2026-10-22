"""Photos for the angle model, from the photo sets the harvest found (sets.jsonl on harvest-data).

    python3 photos.py SETS_JSONL LABELS_DIR TRAIN_DIR EVAL_DIR [MAX_TRAIN] [MAX_TEST] [MINUTES]

Training photos: only CC BY, CC0 and public-domain photos (no share-alike), from sets that are not
test albums, so the trained model carries no licence condition and is never scored on what it
learned from. Their credits are written to TRAIN_DIR/../credits.json. Test photos: the albums
with a person's picks (tools/harvest/labels), any free licence, used only to score the model.
Photos are kept at most 400 px, which is all the model needs.
"""
import io
import json
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image, ImageOps

UA = "AlbumDoneAngle/1.0 (training an on-phone check for crooked photos; free licences only)"
FREE = ("CC BY 2.0", "CC BY 2.5", "CC BY 3.0", "CC BY 4.0", "CC0", "CC0 1.0", "CC CC0 1.0", "Public domain", "CC PDM 1.0", "PDM 1.0")


def small(url):
    """The same photo at about 640 px: Commons thumbnails and Flickr sizes are in the address."""
    import re
    url = re.sub(r"/(\d+)px-", "/640px-", url)
    return re.sub(r"_[bhkoc](\.jpg)$", r"_z\1", url)


DEADLINE = [0.0]


def get(job):
    url, path = job
    if os.path.exists(path):
        return True
    if time.time() > DEADLINE[0]:  # out of time: train on what is here
        return False
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
                img = ImageOps.exif_transpose(Image.open(io.BytesIO(r.read()))).convert("RGB")
            img.thumbnail((400, 400))
            img.save(path, quality=90)
            return True
        except Exception as e:
            if "404" in str(e):
                return False
            time.sleep(4 * (attempt + 1))
    return False


def main(sets_path, labels_dir, train_dir, eval_dir, max_train=8000, max_test=600, minutes=75):
    max_train, max_test = int(max_train), int(max_test)
    labelled = {f[:-4] for f in os.listdir(labels_dir) if f.endswith(".txt")}
    train, test, credits = [], [], []
    for line in open(sets_path):
        s = json.loads(line)
        for i, f in enumerate(s["files"]):
            url = f.get("thumb") or ""
            if not url:
                continue
            name = f"{s['id'][:40]}_{i:04d}.jpg"
            if s["id"] in labelled:
                test.append((small(url), os.path.join(eval_dir, name)))
            elif (f.get("licence") or "").strip() in FREE and len(train) < max_train:
                train.append((small(url), os.path.join(train_dir, name)))
                credits.append({"file": name, "page": f.get("page"), "artist": f.get("artist"), "licence": f.get("licence")})
    os.makedirs(train_dir, exist_ok=True)
    os.makedirs(eval_dir, exist_ok=True)
    # Flickr photos first (fast); Wikimedia asks for gentle use and slows bulk downloads, so the
    # downloading stops after a set time and the model trains on what has arrived.
    flickr_first = lambda jobs: sorted(jobs, key=lambda j: "staticflickr" not in j[0])
    test = flickr_first(test)[:max_test]
    train = flickr_first(train)
    with ThreadPoolExecutor(4) as ex:
        DEADLINE[0] = time.time() + 60 * float(minutes) / 4
        ok_test = sum(ex.map(get, test))
        DEADLINE[0] = time.time() + 60 * float(minutes)
        ok_train = sum(ex.map(get, train))
    credits = [c for c in credits if os.path.exists(os.path.join(train_dir, c["file"]))]
    json.dump(credits, open(os.path.join(train_dir, "..", "credits.json"), "w"), indent=0)
    print(f"{ok_train} training photos, {ok_test} test photos")


if __name__ == "__main__":
    main(*sys.argv[1:6])
