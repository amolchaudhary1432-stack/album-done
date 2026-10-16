"""Downloads, measures and forgets a share of the crawled photo sets (see crawl.py).

    python3 harvest.py sets.jsonl MODELS OUT_DIR SHARD SHARDS MAX_PHOTOS [DONE_IDS_FILE]

For each set in this shard (set number % SHARDS == SHARD), until MAX_PHOTOS photos: download the
photos (1280 px), measure them with measure.py, write OUT_DIR/<id>.jsonl (measurements),
OUT_DIR/<id>.raw.txt (planner format, people not grouped yet) and OUT_DIR/<id>.credits.json, then
delete the photos. Only numbers and credits are kept, never the photos.
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


def main():
    sets_path, models, out, shard, shards, max_photos = sys.argv[1:7]
    shard, shards, max_photos = int(shard), int(shards), int(max_photos)
    done = set(open(sys.argv[7]).read().split()) if len(sys.argv) > 7 and os.path.exists(sys.argv[7]) else set()
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
        rows, credits = [], []
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
        measured += len(rows)
        rate = measured / max(1.0, time.time() - started)
        print(f"{s['id']}: {len(rows)} photos ({s['occasion']}); {measured} so far, {rate:.2f} photos/s", flush=True)


if __name__ == "__main__":
    main()
