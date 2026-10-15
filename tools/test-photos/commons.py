"""Finds and downloads freely licensed wedding photos for the on-phone tests.

Runs on the GitHub build machine (this needs the internet; the app itself never does).
The photos are test data only. They are never put inside the app.

  python3 commons.py discover OUT_DIR [REQUEST]   list likely single-event photo sets (REQUEST may give "searches")
  python3 commons.py fetch REQUEST OUT_DIR download the set named in REQUEST (json)
  python3 commons.py fetch-sets REQUEST OUT_ROOT  download several sets, one folder each
"""
import collections
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "AlbumDoneTestPhotos/0.1 (test data for an offline wedding album app; github.com)"

SEARCHES = [
    "Indian wedding", "Hindu wedding", "Marathi wedding", "Maharashtrian wedding", "wedding in India",
    "Haldi ceremony", "Mehndi ceremony", "Sangeet ceremony", "Saptapadi", "Pheras", "Vidaai", "Baraat",
    "Punjabi wedding", "Gujarati wedding", "Bengali wedding", "Telugu wedding", "Tamil wedding",
    "Kerala wedding", "Kannada wedding", "Rajasthani wedding", "Bihari wedding", "Sikh wedding",
    "Anand Karaj", "Weddings in Maharashtra", "Weddings in Pune", "Weddings in Mumbai",
    "Weddings in Delhi", "Weddings in Gujarat", "Weddings in Kerala", "Weddings in Karnataka",
]


def api(params, tries=4):
    params = dict(params, format="json", formatversion="2", maxlag="5")
    url = API + "?" + urllib.parse.urlencode(params)
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
            if "error" in data and data["error"].get("code") == "maxlag":
                time.sleep(5)
                continue
            time.sleep(0.15)
            return data
        except Exception as e:  # network hiccup: wait and try again
            print("retry", i, e, file=sys.stderr)
            time.sleep(3 * (i + 1))
    return {}


def search_categories(term):
    data = api({"action": "query", "list": "search", "srsearch": term, "srnamespace": 14, "srlimit": 50})
    return [h["title"] for h in data.get("query", {}).get("search", [])]


def subcats(cat, limit=200):
    data = api({"action": "query", "list": "categorymembers", "cmtitle": cat, "cmtype": "subcat", "cmlimit": limit})
    return [m["title"] for m in data.get("query", {}).get("categorymembers", [])]


def file_counts(cats):
    out = {}
    for i in range(0, len(cats), 40):
        data = api({"action": "query", "prop": "categoryinfo", "titles": "|".join(cats[i:i + 40])})
        for p in data.get("query", {}).get("pages", []):
            out[p["title"]] = p.get("categoryinfo", {}).get("files", 0)
    return out


def strip_html(s):
    return re.sub(r"<[^>]+>", "", s or "").strip()


def files_in(cat, max_files=600, width=1600):
    """Files in a category with uploader, date taken, licence and a resized download link."""
    files, cont = [], {}
    while len(files) < max_files:
        params = {
            "action": "query", "generator": "categorymembers", "gcmtitle": cat, "gcmtype": "file",
            "gcmlimit": 50, "prop": "imageinfo", "iiprop": "url|user|timestamp|size|mime|extmetadata",
            "iiurlwidth": width,
            "iiextmetadatafilter": "DateTimeOriginal|LicenseShortName|Artist|ImageDescription|Credit|LicenseUrl",
        }
        params.update(cont)
        data = api(params)
        for p in data.get("query", {}).get("pages", []):
            ii = (p.get("imageinfo") or [{}])[0]
            if not ii or ii.get("mime") not in ("image/jpeg", "image/png"):
                continue
            meta = ii.get("extmetadata", {})
            taken = strip_html(meta.get("DateTimeOriginal", {}).get("value", ""))
            files.append({
                "title": p["title"],
                "user": ii.get("user", ""),
                "uploaded": ii.get("timestamp", ""),
                "taken": taken,
                "width": ii.get("width", 0),
                "height": ii.get("height", 0),
                "thumb": ii.get("thumburl", ""),
                "page": ii.get("descriptionurl", ""),
                "licence": strip_html(meta.get("LicenseShortName", {}).get("value", "")),
                "licence_url": strip_html(meta.get("LicenseUrl", {}).get("value", "")),
                "artist": strip_html(meta.get("Artist", {}).get("value", ""))[:120],
                "description": strip_html(meta.get("ImageDescription", {}).get("value", ""))[:200],
                "category": cat,
            })
        if "continue" not in data:
            break
        cont = {k: v for k, v in data["continue"].items()}
    return files


def day(f):
    m = re.search(r"(\d{4})[-:](\d{2})[-:](\d{2})", f["taken"]) or re.search(r"(\d{4})-(\d{2})-(\d{2})", f["uploaded"])
    return "-".join(m.groups()) if m else "unknown"


FREE = re.compile(r"^(cc0|cc by(-sa)?( \d\.\d)?|cc-by(-sa)?-\d\.\d|public domain|pd.*|attribution|cc by-sa \d\.\d [a-z]+|cc by \d\.\d [a-z]+)$", re.I)


def discover(out_dir, searches=None, expand=None):
    """[searches] replace the wedding searches; [expand]: regex of categories to look one level into."""
    os.makedirs(out_dir, exist_ok=True)
    cats = []
    for term in searches or SEARCHES:
        for c in search_categories(term):
            if c not in cats:
                cats.append(c)
    print(len(cats), "categories from search")
    counts = file_counts(cats)
    # One level down from the wedding categories that look like collections.
    extra = []
    for c in cats:
        if re.search(expand or r"wedding|marriage|haldi|mehndi|mehendi|sangeet|baraat|vidaai|pheras|saptapadi", c, re.I):
            for s in subcats(c):
                if s not in cats and s not in extra:
                    extra.append(s)
    print(len(extra), "sub-categories")
    counts.update(file_counts(extra))
    report = {"categories": sorted(([c, n] for c, n in counts.items() if n > 0), key=lambda x: -x[1])}
    seen, groups = set(), collections.defaultdict(list)
    for cat, n in report["categories"]:
        if n < 12:
            continue
        for f in files_in(cat):
            if f["title"] in seen:
                continue
            seen.add(f["title"])
            groups[(f["user"], day(f))].append(f)
    cands = []
    for (user, d), fs in groups.items():
        if len(fs) < 12:
            continue
        lic = collections.Counter(f["licence"] for f in fs)
        cands.append({
            "user": user, "day": d, "count": len(fs),
            "categories": collections.Counter(f["category"] for f in fs).most_common(6),
            "licences": lic.most_common(),
            "samples": [f["title"] for f in fs[:15]],
            "median_width": sorted(f["width"] for f in fs)[len(fs) // 2],
        })
    cands.sort(key=lambda c: -c["count"])
    report["sets"] = cands
    report["files"] = {f"{u}|{d}": fs for (u, d), fs in groups.items() if len(fs) >= 12}
    with open(os.path.join(out_dir, "discover.json"), "w") as fh:
        json.dump(report, fh, indent=1)
    with open(os.path.join(out_dir, "discover.md"), "w") as fh:
        fh.write("# Candidate single-wedding sets\n\n")
        for c in cands[:80]:
            fh.write(f"## {c['user']} on {c['day']}: {c['count']} photos, width ~{c['median_width']}\n")
            fh.write(f"- categories: {c['categories']}\n- licences: {c['licences']}\n")
            for s in c["samples"]:
                fh.write(f"  - {s}\n")
            fh.write("\n")
        fh.write("\n# Categories by size\n\n")
        for c, n in report["categories"][:300]:
            fh.write(f"- {n} {c}\n")
    print(len(cands), "candidate sets written")


def safe_name(s):
    return re.sub(r"[^A-Za-z0-9._-]+", "_", s).strip("_")[:80]


TIME = re.compile(r"(\d{4})[-:](\d{2})[-:](\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?")


def infos(titles, width=1600):
    out = {}
    for i in range(0, len(titles), 40):
        data = api({"action": "query", "titles": "|".join(titles[i:i + 40]), "prop": "imageinfo",
                    "iiprop": "url|user|timestamp|size|mime|extmetadata", "iiurlwidth": width,
                    "iiextmetadatafilter": "DateTimeOriginal|LicenseShortName|Artist|LicenseUrl"})
        for p in data.get("query", {}).get("pages", []):
            if not p.get("imageinfo"):
                continue
            ii = p["imageinfo"][0]
            meta = ii.get("extmetadata", {})
            out[p["title"]] = {
                "title": p["title"], "thumb": ii.get("thumburl", ""), "page": ii.get("descriptionurl", ""),
                "user": ii.get("user", ""), "taken": strip_html(meta.get("DateTimeOriginal", {}).get("value", "")),
                "licence": strip_html(meta.get("LicenseShortName", {}).get("value", "")),
                "licence_url": strip_html(meta.get("LicenseUrl", {}).get("value", "")),
                "artist": strip_html(meta.get("Artist", {}).get("value", ""))[:120],
            }
    return out


def fetch(request_path, out_dir):
    """REQUEST: {"name": ..., "photos": [{"title": "File:..", "folder": "01 Haldi"}, ...]}.
    Photos are numbered in the order they were taken (IMG_0001.jpg...), like a camera does, and
    the time they were taken is written back into the file (resized copies lose it)."""
    import piexif
    req = json.load(open(request_path))
    os.makedirs(out_dir, exist_ok=True)
    wanted = req["photos"]
    info = infos([w["title"] for w in wanted])

    def taken(w):
        m = TIME.search(info.get(w["title"], {}).get("taken", ""))
        return tuple(int(g or 0) for g in m.groups()) if m else (9999,)

    wanted = sorted((w for w in wanted if w["title"] in info), key=lambda w: (taken(w), w["title"]))
    credits = []
    for n, w in enumerate(wanted, start=1):
        i = info[w["title"]]
        folder = os.path.join(out_dir, w.get("folder", ""))
        os.makedirs(folder, exist_ok=True)
        dest = os.path.join(folder, f"IMG_{n:04d}.jpg")
        for attempt in range(4):
            try:
                r = urllib.request.urlopen(urllib.request.Request(i["thumb"], headers={"User-Agent": UA}), timeout=90)
                data = r.read()
                break
            except Exception as e:
                print("download retry", attempt, w["title"], e, file=sys.stderr)
                time.sleep(4 * (attempt + 1))
        else:
            continue
        open(dest, "wb").write(data)
        t = taken(w)
        if len(t) == 6:
            stamp = "%04d:%02d:%02d %02d:%02d:%02d" % t
            try:
                piexif.insert(piexif.dump({"0th": {piexif.ImageIFD.Orientation: 1},
                                           "Exif": {piexif.ExifIFD.DateTimeOriginal: stamp.encode()}}), dest)
            except Exception as e:
                print("exif", dest, e, file=sys.stderr)
        credits.append({k: i.get(k, "") for k in ("title", "page", "user", "artist", "licence", "licence_url", "taken")}
                       | {"file": os.path.relpath(dest, out_dir), "note": w.get("note", "")})
        time.sleep(0.3)
    with open(os.path.join(out_dir, "CREDITS.json"), "w") as fh:
        json.dump(credits, fh, indent=1)
    with open(os.path.join(out_dir, "README.md"), "w") as fh:
        fh.write(f"# {req.get('name', 'Test wedding')}\n\nTest photos for the on-phone tests only, never part of the app.\n"
                 "Freely licensed photos from Wikimedia Commons, resized to 1600 px. Each file's author, licence\n"
                 "and source page are in CREDITS.json.\n")
    print(len(credits), "photos downloaded")


def fetch_sets(request_path, out_root):
    """REQUEST: {"sets": [{"name": "rajasthan", "title": ..., "photos": [...]}, ...]}: each set goes
    to OUT_ROOT/<name>, next to the sets already there."""
    req = json.load(open(request_path))
    for s in req["sets"]:
        one = os.path.join(out_root, "request-" + s["name"] + ".json")
        json.dump({"name": s.get("title", s["name"]), "photos": s["photos"]}, open(one, "w"))
        fetch(one, os.path.join(out_root, s["name"]))
        os.remove(one)


if __name__ == "__main__":
    if sys.argv[1] == "discover":
        req = json.load(open(sys.argv[3])) if len(sys.argv) > 3 else {}
        discover(sys.argv[2], req.get("searches"), req.get("expand"))
    elif sys.argv[1] == "fetch":
        fetch(sys.argv[2], sys.argv[3])
    elif sys.argv[1] == "fetch-sets":
        fetch_sets(sys.argv[2], sys.argv[3])
    else:
        sys.exit("discover OUT_DIR | fetch REQUEST OUT_DIR | fetch-sets REQUEST OUT_ROOT")
