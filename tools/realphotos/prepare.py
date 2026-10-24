"""A real couple's photos from their photographer (one ZIP): the raw folder, and what they chose.

    python3 prepare.py UNZIPPED_DIR OUT_DIR SHARES

Finds the raw folder (the folder with the most photos; its sub-folders are the photographer's
events) and the couple's choice, in any of these forms:
  - a folder whose name says so (selected, selection, final, album, chosen, picks...) holding
    copies of the chosen photos: matched to the raw photos by file name;
  - a text, CSV or Excel file listing the chosen file names (any column, with or without folder).
Writes OUT_DIR/inventory.txt (what the ZIP holds: folders, photo counts, other files),
OUT_DIR/picks.txt (each chosen photo as "folder/name", the form HarvestScoreTest reads) and
OUT_DIR/shard-N/ (links to the raw photos, sub-folders kept) for measuring on several cores.
Nothing here is published as it is: the workflow keeps only measurements, locked with a key.
"""
import collections
import csv
import os
import re
import sys

PHOTO = (".jpg", ".jpeg", ".png", ".webp")
CHOICE = re.compile(r"select|final|album|chosen|pick|shortlist|favou?rite", re.I)


MATCH_BITS = 10  # of 256: the same photo after colour grading; another frame of a burst differs more


def fingerprint(path):
    """256-bit difference hash of a 17 x 16 grey copy (each pixel brighter than its right neighbour?)."""
    try:
        from PIL import Image, ImageOps
        with Image.open(path) as im:
            im.draft("L", (160, 160))
            g = ImageOps.exif_transpose(im).convert("L").resize((17, 16), Image.BILINEAR)
            px = list(g.tobytes())
        bits = 0
        for y in range(16):
            for x in range(16):
                bits = (bits << 1) | (px[y * 17 + x] > px[y * 17 + x + 1])
        return bits
    except Exception:
        return None


def photos_under(d):
    for root, _, files in os.walk(d):
        for f in files:
            if f.lower().endswith(PHOTO) and not f.startswith("._"):
                yield os.path.join(root, f)


def main(src, out, shares="4"):
    os.makedirs(out, exist_ok=True)
    inv = []
    # Folders that hold photos, and how many under each.
    counts = collections.Counter()
    for p in photos_under(src):
        parts = os.path.relpath(p, src).split(os.sep)[:-1]
        for i in range(len(parts) + 1):
            counts[os.sep.join(parts[:i])] += 1
    choice_dirs = [d for d in counts if d and CHOICE.search(os.path.basename(d))]
    choice_dirs = [d for d in choice_dirs if not any(d.startswith(c + os.sep) for c in choice_dirs)]

    def raw_count(d):
        """Photos under d once the choice folders are left out."""
        return counts[d] - sum(counts[c] for c in choice_dirs if d == "" or c.startswith(d + os.sep))
    # The raw folder: from the top, go down while a folder has no photos of its own and one
    # sub-folder holds all its raw photos, unless that sub-folder is itself an event (no sub-folders).
    def children(d):
        return [c for c in counts if c and os.path.dirname(c) == d and c not in choice_dirs and raw_count(c) > 0]

    def own(d):
        return raw_count(d) - sum(raw_count(c) for c in children(d))
    raw = ""
    while True:
        kids = children(raw)
        if own(raw) > 0 or len(kids) != 1 or raw_count(kids[0]) != raw_count(raw) or (raw != "" and not children(kids[0])):
            break
        raw = kids[0]
    raw_dir = os.path.join(src, raw)
    raw_photos = [p for p in photos_under(raw_dir)
                  if not any(os.path.relpath(p, src).startswith(c + os.sep) for c in choice_dirs)]
    rel = {p: os.path.relpath(p, raw_dir) for p in raw_photos}
    by_name = collections.defaultdict(list)
    for p, r in rel.items():
        by_name[os.path.basename(r).lower()].append(r)
    inv.append(f"raw folder: {raw or '(top)'}: {len(raw_photos)} photos")
    for d in sorted({os.path.dirname(r) for r in rel.values()}):
        inv.append(f"  event folder {d or '(top)'}: {sum(1 for r in rel.values() if os.path.dirname(r) == d)} photos")

    # The couple's choice.
    chosen, how = [], ""
    choice_files = []
    for c in choice_dirs:
        files = list(photos_under(os.path.join(src, c)))
        choice_files += files
        chosen += [os.path.basename(p).lower() for p in files]
        how += f"choice folder {c}: {len(files)} photos; "
    for root, _, files in os.walk(src):
        for f in files:
            path = os.path.join(root, f)
            low = f.lower()
            if low.endswith(PHOTO):
                continue
            inv.append(f"other file: {os.path.relpath(path, src)} ({os.path.getsize(path)} bytes)")
            cells = []
            try:
                if low.endswith((".txt", ".csv", ".tsv")):
                    with open(path, newline="", errors="ignore") as fh:
                        cells = [c for row in csv.reader(fh, delimiter="\t" if low.endswith(".tsv") else ",") for c in row]
                        if not low.endswith((".csv", ".tsv")):
                            fh.seek(0)
                            cells = re.split(r"[\s,;]+", fh.read())
                elif low.endswith((".xlsx", ".xlsm")):
                    import openpyxl
                    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
                    cells = [str(v) for ws in wb.worksheets for row in ws.iter_rows(values_only=True) for v in row if v is not None]
            except Exception as e:
                inv.append(f"  could not read: {e}")
            names = []
            for cell in cells:
                for tok in re.findall(r"[\w\-. ()]+\.(?:jpe?g|png|webp|cr2|cr3|nef|arw|raf|dng|heic)|\b[A-Z_]{2,5}\d{3,6}\b", cell, re.I):
                    base = os.path.splitext(os.path.basename(tok.strip()))[0].lower()
                    names.append(base)
            if names:
                chosen += names
                how += f"list {os.path.relpath(path, src)}: {len(names)} names; "

    # Match chosen names to raw photos (by name; a list may give names without the extension).
    stem = collections.defaultdict(list)
    for r in rel.values():
        stem[os.path.splitext(os.path.basename(r))[0].lower()].append(r)
    picks, unmatched, ambiguous = [], 0, 0
    by_content = 0
    unmatched_names = set()
    for n in dict.fromkeys(chosen):
        hits = by_name.get(n) or stem.get(os.path.splitext(n)[0]) or []
        if len(hits) == 1:
            picks.append(hits[0].replace(os.sep, "/"))
        elif hits:
            ambiguous += 1
        else:
            unmatched += 1
            unmatched_names.add(n)
    # Chosen photos the photographer renamed or edited (colours, retouching): matched by how they
    # look, a small fingerprint of each photo's light and dark that colour grading keeps.
    left = [p for p in choice_files if os.path.basename(p).lower() in unmatched_names]
    if left:
        raw_prints = {r: fingerprint(p) for p, r in rel.items()}
        raw_prints = {r: h for r, h in raw_prints.items() if h is not None}
        taken = set(picks)
        for p in left:
            h = fingerprint(p)
            if h is None or not raw_prints:
                continue
            best, dist = min(((r, bin(h ^ rh).count("1")) for r, rh in raw_prints.items()), key=lambda x: x[1])
            key = best.replace(os.sep, "/")
            if dist <= MATCH_BITS and key not in taken:
                picks.append(key)
                taken.add(key)
                by_content += 1
                unmatched -= 1
    inv.append(f"couple's choice: {how or 'NOT FOUND'}")
    inv.append(f"  matched to raw photos: {len(picks)} ({by_content} by how they look); not found in raw: {unmatched}; "
               f"same name in several folders: {ambiguous}")
    with open(os.path.join(out, "picks.txt"), "w") as fh:
        fh.write(f"# the couple's choice, {len(picks)} photos of {len(raw_photos)}\n")
        fh.writelines(p + "\n" for p in picks)
    with open(os.path.join(out, "inventory.txt"), "w") as fh:
        fh.write("\n".join(inv) + "\n")

    # Shards for measuring on several cores: links, sub-folders kept.
    n = int(shares)
    for i, (p, r) in enumerate(sorted(rel.items(), key=lambda x: x[1])):
        dst = os.path.join(out, f"shard-{i % n}", r)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        os.symlink(os.path.abspath(p), dst)
    # Only counts in the log (it is public while the repo is); names stay in the locked files.
    print(f"{len(raw_photos)} raw photos in {len({os.path.dirname(r) for r in rel.values()})} event folders; "
          f"couple's choice {'found' if how else 'NOT FOUND'}: {len(picks)} matched, {unmatched} not found, {ambiguous} unclear")


if __name__ == "__main__":
    main(*sys.argv[1:4])
