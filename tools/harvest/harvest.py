"""Downloads, measures and forgets a share of the crawled photo sets (see crawl.py).

    python3 harvest.py sets.jsonl MODELS OUT_DIR SHARD SHARDS MAX_PHOTOS [DONE_IDS_FILE] [SHEETS_EVERY] [SEEN_FILE]

For each set in this shard (set number % SHARDS == SHARD), until MAX_PHOTOS photos: download the
photos (1280 px), measure them with measure.py, write OUT_DIR/<id>.jsonl (measurements),
OUT_DIR/<id>.raw.txt (planner format, people not grouped yet) and OUT_DIR/<id>.credits.json, then
delete the photos. Only numbers and credits are kept, never the photos; for every SHEETS_EVERY-th
set (default every set) small numbered contact sheets are kept too (OUT_DIR/<id>.sheet-NN.jpg), so a person
can mark which photos they would put in the album (the answer key the brain learns from).

Every photo ever measured is remembered in SEEN_FILE (its web address and a fingerprint of the
picture). A photo seen before is not downloaded again, and a copy that comes back under another
name (the same picture, fingerprint at most 3 bits apart) is dropped after measuring. Each set's
new entries are written to OUT_DIR/<id>.seen.txt.
"""
import datetime, json, os, re, shutil, sys, tempfile, time, urllib.error, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import Analyzer  # noqa: E402

MAX_MINUTES = float(os.environ.get("HARVEST_MINUTES", "150"))  # then stop and keep what is measured
FIRST = ["WEDDING", "KIDS_BIRTHDAY", "CHILD_FUNCTION", "ENGAGEMENT", "BABY_SHOWER", "ANNIVERSARY", "BIRTHDAY",
         "HOUSEWARMING", "TRIP"]
STREET = re.compile(r"holi\b|immersion|procession|visarjan|rally|parade|festival in|carnival|club|pandal|street", re.I)
# Community meetups are not family albums ("Wikipedia's 25th birthday", edit-a-thons, conferences).
MEETUP = re.compile(r"wikipedia|wikidata|wikimedia|meetup|edit-?a-?thon|conference|workshop|hackathon|seminar|summit", re.I)
UA = "AlbumDoneHarvest/0.2 (measures freely licensed event photos to tune an offline album app; https://github.com/amolchaudhary1432-stack/album-done)"
TIME = re.compile(r"(\d{4})[-:](\d{2})[-:](\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?")


def taken_millis(s):
    m = TIME.search(s or "")
    if not m:
        return 0
    y, mo, d, h, mi, sec = (int(g or 0) for g in m.groups())
    try:
        return int(datetime.datetime(y, mo, d, h, mi, sec, tzinfo=datetime.timezone.utc).timestamp() * 1000)
    except ValueError:
        return 0


_pause = [0.1]  # seconds between downloads; grows when a site says "too many requests"


def download(url, dest):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=90) as r:
                open(dest, "wb").write(r.read())
            time.sleep(_pause[0])
            _pause[0] = max(0.1, _pause[0] * 0.97)
            return True
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return False
            if e.code == 429:  # too many requests: wait as asked, and go slower from now on
                _pause[0] = min(10.0, _pause[0] * 2 + 0.5)
                wait = int(e.headers.get("Retry-After") or 0) or 15 * (attempt + 1)
                print("slow down", url[-50:], f"wait {wait}s, then {_pause[0]:.1f}s between photos", file=sys.stderr)
                time.sleep(min(wait, 120))
            else:
                print("retry", attempt, url[-60:], e, file=sys.stderr)
                time.sleep(5 * (attempt + 1))
        except Exception as e:  # network hiccup: wait and try again
            print("retry", attempt, url[-60:], e, file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    return False


def small(path, side=300):
    from PIL import Image, ImageOps
    im = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    im.thumbnail((side, side))
    return im


def contact_sheets(thumbs, cols=5, rows=4, cell=310):
    """20 photos a sheet, each labelled with its number (IMG_0007 -> 7)."""
    from PIL import Image, ImageDraw
    for start in range(0, len(thumbs), cols * rows):
        sheet = Image.new("RGB", (cols * cell, rows * cell), "white")
        d = ImageDraw.Draw(sheet)
        for i, (name, im) in enumerate(thumbs[start:start + cols * rows]):
            x, y = (i % cols) * cell, (i // cols) * cell
            sheet.paste(im, (x + 5, y + 5))
            d.rectangle([x + 5, y + 5, x + 60, y + 25], fill="black")
            d.text((x + 9, y + 9), str(int(name[4:8])), fill="yellow")
        yield sheet


def photo_key(f):
    """Where a photo lives on the web: the same photo found through two searches has one key."""
    return (f.get("page") or f.get("thumb") or f.get("title") or "").strip()


class Seen:
    """Photos measured before: by web address, and by picture fingerprint (64-bit, near copies too)."""

    def __init__(self, path):
        self.keys, self.hashes, self.bands = set(), set(), {}
        if path and os.path.exists(path):
            for line in open(path):
                key, _, h = line.rstrip("\n").partition("\t")
                self.add(key, int(h) if h.lstrip("-").isdigit() else None)

    def add(self, key, h):
        if key:
            self.keys.add(key)
        if h is not None:
            self.hashes.add(h)
            for b in range(4):  # 3 bits apart share at least one of four 16-bit bands
                self.bands.setdefault((b, (h >> (16 * b)) & 0xFFFF), []).append(h)

    def copy_of(self, h):
        if h in self.hashes:
            return True
        for b in range(4):
            for other in self.bands.get((b, (h >> (16 * b)) & 0xFFFF), ()):
                if bin((h ^ other) & (2**64 - 1)).count("1") <= 3:
                    return True
        return False


def main():
    sets_path, models, out, shard, shards, max_photos = sys.argv[1:7]
    shard, shards, max_photos = int(shard), int(shards), int(max_photos)
    done = set(open(sys.argv[7]).read().split()) if len(sys.argv) > 7 and os.path.exists(sys.argv[7]) else set()
    sheets_every = int(sys.argv[8]) if len(sys.argv) > 8 else 1
    seen = Seen(sys.argv[9] if len(sys.argv) > 9 else None)
    skipped = copies = 0
    os.makedirs(out, exist_ok=True)
    analyzer = Analyzer(models)
    measured = 0
    started = time.time()
    # Family events first (what albums are made of); public festivals on the street are not albums.
    sets = [json.loads(line) for line in open(sets_path) if line.strip()]
    sets = [s for s in sets if not (s["occasion"] == "OTHER" and STREET.search(" ".join(s["categories"])))
            and not MEETUP.search(" ".join(s["categories"]))]
    sets.sort(key=lambda s: (FIRST.index(s["occasion"]) if s["occasion"] in FIRST else len(FIRST), -len(s["files"])))
    for n, s in enumerate(sets):
        if n % shards != shard:
            continue
        if s["id"] in done or os.path.exists(os.path.join(out, s["id"] + ".jsonl")):
            continue
        if measured >= max_photos or time.time() - started > MAX_MINUTES * 60:
            break
        # A camera numbers photos in the order they were taken; so do we.
        files = sorted(s["files"], key=lambda f: (taken_millis(f["taken"]) or 1 << 62, f["title"]))
        tmp = tempfile.mkdtemp(prefix="harvest-")
        rows, credits, thumbs, new_seen = [], [], [], []
        keep_sheets = sheets_every > 0 and n % sheets_every == 0
        try:
            i = 0
            for f in files:
                key = photo_key(f)
                if key in seen.keys:
                    skipped += 1
                    continue
                name = f"IMG_{i + 1:04d}.jpg"
                path = os.path.join(tmp, name)
                if not f["thumb"] or not download(f["thumb"], path):
                    continue
                row = {"name": name, "folder": ""}
                try:
                    row.update(analyzer.analyze(path, taken_at=taken_millis(f["taken"])))
                    row["failed"] = False
                except Exception as e:
                    row.update({"failed": True, "error": str(e)[:200]})
                h = row.get("hash") if not row["failed"] else None
                if h is not None and seen.copy_of(int(h)):
                    copies += 1
                    seen.add(key, None)
                    new_seen.append(f"{key}\t")
                    os.remove(path)
                    continue
                seen.add(key, int(h) if h is not None else None)
                new_seen.append(f"{key}\t{'' if h is None else int(h)}")
                i += 1
                rows.append(row)
                credits.append({"file": name, **f})
                if keep_sheets:
                    thumbs.append((name, small(path)))
                os.remove(path)
                time.sleep(0.1)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
        if new_seen:
            with open(os.path.join(out, s["id"] + ".seen.txt"), "w") as fh:
                fh.write("\n".join(new_seen) + "\n")
        if len(rows) < 10:
            continue
        with open(os.path.join(out, s["id"] + ".jsonl"), "w") as fh:
            for r in rows:
                fh.write(json.dumps(r) + "\n")
        json.dump({k: s[k] for k in ("id", "user", "day", "occasion", "categories")} | {"files": credits},
                  open(os.path.join(out, s["id"] + ".credits.json"), "w"))
        os.system(f'python3 "{os.path.join(os.path.dirname(os.path.abspath(__file__)), "to_dump.py")}" '
                  f'"{os.path.join(out, s["id"] + ".jsonl")}" "{os.path.join(out, s["id"] + ".raw.txt")}" {s["occasion"]}')
        for k, sheet in enumerate(contact_sheets(thumbs), start=1):
            sheet.save(os.path.join(out, f"{s['id']}.sheet-{k:02d}.jpg"), quality=72)
        measured += len(rows)
        rate = measured / max(1.0, time.time() - started)
        print(f"{s['id']}: {len(rows)} photos ({s['occasion']}); {measured} so far, {rate:.2f} photos/s; "
              f"{skipped} seen before, {copies} copies dropped", flush=True)


if __name__ == "__main__":
    main()
