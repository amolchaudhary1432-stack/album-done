"""Merges the photo sets found by several search machines (crawl.py with CRAWL_SHARD) into one list.

    python3 merge_sets.py OUT_DIR PART_DIR...

OUT_DIR has the sets found before (sets.jsonl, searched.txt); each PART_DIR one machine's. A set
found by two machines (one photographer's day, found by different searches) is joined, photos
counted once. Sets too small for an album are dropped after joining.
"""
import json
import os
import sys


def occasion_of(categories):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from crawl import occasion_of as of  # noqa: E402
    return of(categories)


def main(out, parts):
    sets, searched = {}, set()
    for d in [out] + parts:
        p = os.path.join(d, "sets.jsonl")
        if os.path.exists(p):
            for line in open(p):
                if not line.strip():
                    continue
                s = json.loads(line)
                old = sets.get(s["id"])
                if old:
                    titles = {f["title"] for f in old["files"]}
                    old["files"] += [f for f in s["files"] if f["title"] not in titles]
                    old["categories"] = sorted(set(old["categories"]) | set(s["categories"]))
                else:
                    sets[s["id"]] = s
        q = os.path.join(d, "searched.txt")
        if os.path.exists(q):
            searched |= set(open(q).read().split("\n")) - {""}
    kept = []
    for s in sets.values():
        searched_only = all(c.startswith("Search: ") for c in s["categories"])
        if len(s["files"]) < (6 if searched_only else 10):
            continue
        s["occasion"] = occasion_of(s["categories"])
        kept.append(s)
    kept.sort(key=lambda s: -len(s["files"]))
    with open(os.path.join(out, "sets.jsonl"), "w") as fh:
        for s in kept:
            fh.write(json.dumps(s) + "\n")
    with open(os.path.join(out, "searched.txt"), "w") as fh:
        fh.write("".join(t + "\n" for t in sorted(searched)))
    print(f"{len(kept)} sets, {sum(len(s['files']) for s in kept)} photos, {len(searched)} searches done")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
