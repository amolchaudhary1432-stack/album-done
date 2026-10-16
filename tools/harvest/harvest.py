"""Downloads, measures and forgets a share of the crawled photo sets (see crawl.py).

    python3 harvest.py sets.jsonl MODELS OUT_DIR SHARD SHARDS MAX_PHOTOS [DONE_IDS_FILE] [SHEETS_EVERY]

For each set in this shard (set number % SHARDS == SHARD), until MAX_PHOTOS photos: download the
photos (1280 px), measure them with measure.py, write OUT_DIR/<id>.jsonl (measurements),
OUT_DIR/<id>.raw.txt (planner format, people not grouped yet) and OUT_DIR/<id>.credits.json, then
delete the photos. Only numbers and credits are kept, never the photos; for every SHEETS_EVERY-th
set (default every set) small numbered contact sheets are kept too (OUT_DIR/<id>.sheet-NN.jpg), so a person
can mark which photos they would put in the album (the answer key the brain learns from).
"""
import datetime, json, os, re, shutil, sys, tempfile, time, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import Analyzer  # noqa: E402

UA = "AlbumDoneHarvest/0.1 (measures freely licensed event photos to tune an offline album app; github.com)"
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


def download(url, dest):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=90) as r:
                open(dest, "wb").write(r.read())
            return True
        except Exception as e:  # rate limit or network hiccup: wait and try again
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


def main():
    sets_path, models, out, shard, shards, max_photos = sys.argv[1:7]
    shard, shards, max_photos = int(shard), int(shards), int(max_photos)
    done = set(open(sys.argv[7]).read().split()) if len(sys.argv) > 7 and os.path.exists(sys.argv[7]) else set()
    sheets_every = int(sys.argv[8]) if len(sys.argv) > 8 else 1
    os.makedirs(out, exist_ok=True)
    analyzer = Analyzer(models)
    measured = 0
    started = time.time()
    for n, line in enumerate(open(sets_path)):
        if n % shards != shard:
            continue
        s = json.loads(line)
        if s["id"] in done or os.path.exists(os.path.join(out, s["id"] + ".jsonl")):
            continue
        if measured >= max_photos:
            break
        # A camera numbers photos in the order they were taken; so do we.
        files = sorted(s["files"], key=lambda f: (taken_millis(f["taken"]) or 1 << 62, f["title"]))
        tmp = tempfile.mkdtemp(prefix="harvest-")
        rows, credits, thumbs = [], [], []
        keep_sheets = sheets_every > 0 and n % sheets_every == 0
        try:
            for i, f in enumerate(files, start=1):
                name = f"IMG_{i:04d}.jpg"
                path = os.path.join(tmp, name)
                if not f["thumb"] or not download(f["thumb"], path):
                    continue
                row = {"name": name, "folder": ""}
                try:
                    row.update(analyzer.analyze(path, taken_at=taken_millis(f["taken"])))
                    row["failed"] = False
                except Exception as e:
                    row.update({"failed": True, "error": str(e)[:200]})
                rows.append(row)
                credits.append({"file": name, **f})
                if keep_sheets:
                    thumbs.append((name, small(path)))
                os.remove(path)
                time.sleep(0.1)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
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
        print(f"{s['id']}: {len(rows)} photos ({s['occasion']}); {measured} so far, {rate:.2f} photos/s", flush=True)


if __name__ == "__main__":
    main()
