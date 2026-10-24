"""One real event's photos as a phone folder, for the real-phone test: never a mix of events.

    python3 one_set.py SETS_JSONL SET_ID OUT_DIR SIZE [FOLDERS] [LIMIT]

SET_ID is a set found by the harvest (sets.jsonl on harvest-data), e.g. Goutam1962_2023-11-23 (a
Garhwali wedding day, 813 photos). SIZE is "small" (the 1280 px copy, made at most 1400 px, about
what the app reads), "full" (the photographer's original file, byte for byte; Wikimedia sends
these slowly, about 25 s each) or "camera" (the 1280 px copy enlarged to 6000 px with a camera's fine
grain, JPEG quality 95: about the size and file weight of a 24 MP camera's photo, 7-9 MB; for timing, since the app's work depends on
the file's pixels and bytes, not on how sharp they are). FOLDERS splits the photos into the photographer's event folders by words in
their titles, e.g. "haldi=01 Haldi,*=02 Wedding" (first match wins; * is the rest); without it
everything is one folder. LIMIT keeps the first N photos in time order. DEADLINE (seconds) stops downloading in time and
THREADS (default 2) sets how many at once; photos already in OUT_DIR are kept, so it can resume.
Photos are named IMG_0001.jpg... in time order and keep their capture time (EXIF), which the app
uses to find the big moments. Test data only, never part of the app; credits:
sets/<id>.credits.json on harvest-data.
"""
import io
import json
import threading
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image

# Wikimedia's robot policy: say who we are and how to reach us, and go slowly (new thumbnails are
# limited to a few a minute for each machine; Retry-After says how long to wait).
UA = "AlbumDoneTestBot/1.1 (https://github.com/amolchaudhary1432-stack/album-done; phone test, freely licensed photos only)"
LONG_SIDE = 1400
CAMERA_LONG_SIDE = 6000
CAMERA_GRAIN = 4.0


def original(url):
    """The original file of a Wikimedia thumbnail; other addresses as they are."""
    m = re.match(r"https://(?:thumb|upload)\.wikimedia\.org/wikipedia/commons/thumb/(\w)/(\w\w)/([^/]+)/[^/]+$", url)
    return f"https://upload.wikimedia.org/wikipedia/commons/{m[1]}/{m[2]}/{m[3]}" if m else url


_pause_until = [0.0]
_pause_lock = threading.Lock()


def _wait_turn():
    with _pause_lock:
        until = _pause_until[0]
    if until > time.time():
        time.sleep(until - time.time())


def fetch(url):
    # The same files as thumb.wikimedia.org, without the tracking query (more of them cached).
    url = re.sub(r"^https://thumb\.wikimedia\.org/", "https://upload.wikimedia.org/", url).split("?utm_")[0]
    for attempt in range(12):
        _wait_turn()
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            # Too many requests: everyone waits as long as the site asks.
            wait = min(int(e.headers.get("Retry-After") or 0) or 5 * (attempt + 1), 120)
            with _pause_lock:
                _pause_until[0] = max(_pause_until[0], time.time() + wait)
            if e.code != 429:
                print("retry", attempt, url, e, file=sys.stderr)
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


def save(data, f, n, path, size):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if size == "full":
        open(path, "wb").write(data)
        return
    img = Image.open(io.BytesIO(data))
    exif = exif_with_time(img, f.get("taken"))
    img = img.convert("RGB")
    if size == "camera":
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
        img.thumbnail((LONG_SIDE, LONG_SIDE))
        img.save(path, quality=88, exif=exif)


def main(sets_path, set_id, out, size, folders="", limit="0"):
    """SET_ID is one set, or several as "id=Folder name;id2=Folder 2" (each event its own folder,
    for a big folder made of several real events: a size test, not an album to judge)."""
    from concurrent.futures import ThreadPoolExecutor
    wanted = [x.split("=", 1) if "=" in x else [x, None] for x in set_id.split(";") if x.strip()]
    by_id = {}
    for line in open(sets_path):
        if line.strip():
            r = json.loads(line)
            if r["id"] in {w[0] for w in wanted}:
                by_id[r["id"]] = r
    rules = [tuple(x.split("=", 1)) for x in folders.split(",") if "=" in x]
    jobs, seen = [], set()
    for sid, name in wanted:
        for f in sorted(by_id[sid]["files"], key=lambda f: (f.get("taken") or "", f.get("title") or "")):
            if f["thumb"] in seen:  # the same photo found in two sets
                continue
            seen.add(f["thumb"])
            sub = name if name else (folder_for(f.get("title") or "", rules) if rules else "")
            jobs.append((f, sub))
    if int(limit) > 0:
        jobs = jobs[: int(limit)]
    urls = [original(f["thumb"]) if size == "full" else f["thumb"] for f, _ in jobs]
    # Each photo's name is its place in time order, so a download stopped part way (DEADLINE
    # seconds, for the build machine's time limit) carries on later with the photos already there.
    deadline = time.time() + float(os.environ.get("DEADLINE") or 1e9)
    paths = [os.path.join(out, sub, f"IMG_{i + 1:04d}.jpg") for i, (_, sub) in enumerate(jobs)]
    have = sum(os.path.exists(p) for p in paths)
    todo = [i for i, p in enumerate(paths) if not os.path.exists(p)]
    print(have, "photos already there,", len(todo), "to download", flush=True)

    def one(i):
        if time.time() > deadline:
            return False
        data = fetch(urls[i])
        if data is None:
            return False
        try:
            save(data, jobs[i][0], i + 1, paths[i], size)
            return True
        except Exception as e:  # not a photo after all
            print("skip", urls[i], e, file=sys.stderr)
            return False
    got = 0
    with ThreadPoolExecutor(int(os.environ.get("THREADS") or 2)) as ex:
        for k, ok in enumerate(ex.map(one, todo)):
            got += ok
            if (k + 1) % 100 == 0:
                print(f"{k + 1} of {len(todo)} tried, {got} saved", flush=True)
    total = sum(os.path.exists(p) for p in paths)
    print(total, "photos of", len(jobs), "in", out, "(" + ("stopped at the time limit" if time.time() > deadline else "done") + ")")
    with open(os.path.join(out, "STATUS"), "w") as fh:
        fh.write(f"{total} {len(jobs)}\n")

if __name__ == "__main__":
    main(*sys.argv[1:7])
