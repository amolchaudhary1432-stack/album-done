"""Picks and answer keys for the tougher test: the labelled sets measured again with bad twins
planted (harvest option rawify, <id>_raw). Photos are numbered again when a set is measured, so
each pick is carried over by its source page, not its file name.

    python3 raw_labels.py LABELS_DIR SETS_DIR OUT_DIR

SETS_DIR has <id>.credits.json, <id>_raw.credits.json and <id>_raw.truth.json. Writes, for each
labelled set measured raw, OUT_DIR/<id>_raw.txt (the person's picks, one file name a line) and
OUT_DIR/<id>_raw.truth.txt (one planted photo a line: file, the photo it was made from, what is
wrong with it), the plain text HarvestScoreTest reads.
"""
import json
import os
import sys


def key(f):
    return (f.get("page") or f.get("thumb") or f.get("title") or "").strip()


def main(labels_dir, sets_dir, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    done = 0
    for name in sorted(os.listdir(labels_dir)):
        if not name.endswith(".txt"):
            continue
        sid = name[:-4]
        paths = [os.path.join(sets_dir, f) for f in (f"{sid}.credits.json", f"{sid}_raw.credits.json", f"{sid}_raw.truth.json")]
        if not all(os.path.exists(p) for p in paths):
            continue
        old, new, truth = (json.load(open(p)) for p in paths)
        by_key = {key(f): f["file"] for f in new["files"]}
        old_key = {f["file"]: key(f) for f in old["files"]}
        picks = [l.strip() for l in open(os.path.join(labels_dir, name)) if l.strip() and not l.startswith("#")]
        moved = [by_key.get(old_key.get(p, ""), "") for p in picks]
        with open(os.path.join(out_dir, f"{sid}_raw.txt"), "w") as fh:
            fh.write(f"# {sid} picks, carried over to {sid}_raw ({sum(1 for m in moved if m)} of {len(picks)} found)\n")
            fh.writelines(m + "\n" for m in moved if m)
        with open(os.path.join(out_dir, f"{sid}_raw.truth.txt"), "w") as fh:
            for bad, t in sorted(truth.items()):
                fh.write(f"{bad}\t{t['source']}\t{t['defect']}\n")
        done += 1
    print(done, "raw sets with picks")


if __name__ == "__main__":
    main(*sys.argv[1:4])
