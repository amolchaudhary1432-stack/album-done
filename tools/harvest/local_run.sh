#!/bin/bash
# The harvest (.github/workflows/harvest.yml) on one machine instead of GitHub's, so it costs no
# GitHub minutes: find sets, measure them, group faces into people, and keep the numbers and
# contact sheets on the harvest-data and harvest-sheets branches (owner's permission, 21 Oct 2026).
#
#   tools/harvest/local_run.sh WORK_DIR [PYTHON] [SEARCH_MINUTES] [PHOTOS_PER_SHARE] [SHARES]
#
# WORK_DIR keeps sets.jsonl, searched.txt, categories.txt, done.txt and seen.txt between runs (fetched
# from harvest-data when missing). Set GRADLE_INIT to an init script when Maven Central needs a mirror.
set -euo pipefail
W=$(realpath "$1"); PY=${2:-python3}; MIN=${3:-70}; PHOTOS=${4:-1500}; SHARES=${5:-3}
REPO=$(cd "$(dirname "$0")/../.." && pwd)
cd "$REPO"
mkdir -p "$W"

if [ ! -s "$W/sets.jsonl" ] && git fetch -q --depth 1 origin harvest-data; then
  for f in sets.jsonl searched.txt categories.txt done.txt seen.txt; do git show "FETCH_HEAD:$f" > "$W/$f" 2>/dev/null || rm -f "$W/$f"; done
fi
touch "$W/done.txt" "$W/seen.txt"

echo "== find ($MIN min at most)"
[ "$MIN" = 0 ] || "$PY" tools/harvest/crawl.py "$W/sets.jsonl" 1500 "$MIN" 2> "$W/crawl.log" || tail -n 20 "$W/crawl.log"

echo "== measure ($SHARES shares, $PHOTOS photos each at most)"
rm -rf "$W/new"; mkdir -p "$W/new"
for i in $(seq 0 $((SHARES - 1))); do
  "$PY" tools/harvest/harvest.py "$W/sets.jsonl" app/src/main/assets/models "$W/new" "$i" "$SHARES" "$PHOTOS" \
    "$W/done.txt" 1 "$W/seen.txt" 2> "$W/measure-$i.log" &
done
wait
echo "$(ls "$W"/new/*.jsonl 2>/dev/null | wc -l) sets measured"

echo "== group faces into people"
if ls "$W"/new/*.raw.txt >/dev/null 2>&1; then
  gradle --no-daemon -q ${GRADLE_INIT:+--init-script "$GRADLE_INIT"} -p tools/harvest/jvm run --args="$W/new"
fi

echo "== keep"
rm -rf "$W/data" "$W/sheets"; mkdir -p "$W/data/sets" "$W/sheets"
if git fetch -q --depth 1 origin harvest-data; then git archive FETCH_HEAD | tar -x -C "$W/data"; fi
if git fetch -q --depth 1 origin harvest-sheets; then git archive FETCH_HEAD | tar -x -C "$W/sheets"; fi
mkdir -p "$W/data/sets"
cp "$W"/new/*.sheet-*.jpg "$W/sheets/" 2>/dev/null || true
for f in "$W"/new/*.jsonl; do
  [ -e "$f" ] || continue
  id=$(basename "$f" .jsonl)
  gzip -c "$f" > "$W/data/sets/$id.jsonl.gz"
  cp "$W/new/$id.plan.txt" "$W/new/$id.credits.json" "$W/data/sets/" 2>/dev/null || true
  echo "$id" >> "$W/done.txt"
done
sort -u "$W/done.txt" > "$W/data/done.txt"; cp "$W/data/done.txt" "$W/done.txt"
"$PY" - "$W" <<'EOF'
import collections, glob, gzip, json, sys
w = sys.argv[1]
seen = set(l.rstrip("\n") for f in [f"{w}/seen.txt"] + glob.glob(f"{w}/new/*.seen.txt") for l in open(f) if l.strip())
for cred in glob.glob(f"{w}/data/sets/*.credits.json"):
    sid = cred.rsplit("/", 1)[1][:-len(".credits.json")]
    try:
        rows = {r["name"]: r for r in map(json.loads, gzip.open(f"{w}/data/sets/{sid}.jsonl.gz", "rt"))}
    except OSError:
        rows = {}
    for f in json.load(open(cred))["files"]:
        key = (f.get("page") or f.get("thumb") or f.get("title") or "").strip()
        h = rows.get(f["file"], {}).get("hash")
        seen.add(f"{key}\t{'' if h is None else int(h)}")
open(f"{w}/data/seen.txt", "w").write("".join(sorted(x + "\n" for x in seen)))
sets = [json.load(open(f)) for f in sorted(glob.glob(f"{w}/data/sets/*.credits.json"))]
by = collections.Counter(s["occasion"] for s in sets)
photos = sum(len(s["files"]) for s in sets)
with open(f"{w}/data/README.md", "w") as fh:
    fh.write("# Harvested measurements\n\nNumbers measured from freely licensed photos on Wikimedia Commons by the app's\n"
             "own models and rules (tools/harvest). No photos are kept. Each set's photographers,\n"
             "licences and source pages are in sets/<id>.credits.json.\n\n")
    fh.write(f"{len(sets)} sets, {photos} photos\n\n")
    for o, n in by.most_common():
        fh.write(f"- {o}: {n} sets\n")
print(len(seen), "photos seen;", len(sets), "sets,", photos, "photos", dict(by))
EOF
cp "$W/data/seen.txt" "$W/seen.txt"
for f in sets.jsonl searched.txt categories.txt; do [ -f "$W/$f" ] && cp "$W/$f" "$W/data/"; done

URL=$(git remote get-url origin)
push() {  # push DIR BRANCH MESSAGE: the branch holds only the latest state, as the workflow does
  (cd "$1" && rm -rf .git && git init -q -b "$2" && git config user.name harvest &&
   git config user.email harvest@users.noreply.github.com && git add -A && git commit -q -m "$3" &&
   git push -q -f "$URL" "$2")
}
stamp=$(date -u +%Y-%m-%dT%H:%MZ)
push "$W/data" harvest-data "Harvest on the cloud machine, $stamp"
ls "$W"/sheets/*.jpg >/dev/null 2>&1 && push "$W/sheets" harvest-sheets "Contact sheets, harvest on the cloud machine, $stamp"
echo "== kept on harvest-data and harvest-sheets"
