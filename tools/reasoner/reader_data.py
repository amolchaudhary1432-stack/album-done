"""Teaching examples for the reader: the small model that reads a folder moment by moment like Claude.

For each moment of a raw folder that Claude judged in words (kind, skip / keep / peak, why), one example:
  picture  up to four frames of the moment on one 512 px sheet
  facts    what the app already measures: the folder's name, how many photos, are the main people in it
  memory   what was said about the most similar moments read earlier (and whether they were kept)
  answer   Claude's own words, then the verdict
Whole functions are held out (never trained on) so the score is on events the reader has not seen.

    python3 -I reader_data.py PHOTOS MOMENTS.tsv GOLD.tsv FEATURES.csv VECS.bin OUT [held-out folder words...]
"""
import csv, json, os, struct, sys, collections
import numpy as np
from PIL import Image, ImageOps

photos, moments_p, gold_p, feats_p, vecs_p, out = sys.argv[1:7]
HELD = [w.lower() for w in sys.argv[7:]] or ['reception', 'gondhal', 'nanhora']
VERDICT = {0: 'skip', 1: 'keep', 2: 'peak'}
os.makedirs(out + '/sheets', exist_ok=True)

gold = {}
for l in open(gold_p, encoding='utf-8'):
    c = l.rstrip('\n').split('\t')
    if len(c) >= 4: gold[c[0]] = (int(c[1]), c[2], c[3])
moments = []
for l in open(moments_p, encoding='utf-8'):
    c = l.rstrip('\n').split('\t')
    if c[0] in gold: moments.append((c[0], c[1], [f for f in c[5].split(';') if f]))

feat = {r['name']: r for r in csv.DictReader(open(feats_p, encoding='utf-8'))}
f = open(vecs_p, 'rb'); n, d = struct.unpack('<ii', f.read(8)); vec = {}
for _ in range(n):
    k, = struct.unpack('<i', f.read(4)); key = f.read(k).decode(); v = np.frombuffer(f.read(4 * d), '<f4'); vec[key] = v / np.linalg.norm(v)


def sheet(files, path):
    """One frame fills the sheet; more are spread over a 2 x 2 grid (first, two from the middle, last)."""
    files = [x for x in files if os.path.exists(os.path.join(photos, x))]
    if not files: return False
    if len(files) > 4: files = [files[round(i * (len(files) - 1) / 3)] for i in range(4)]
    S = 512; im = Image.new('RGB', (S, S), (0, 0, 0))
    if len(files) == 1:
        t = ImageOps.exif_transpose(Image.open(os.path.join(photos, files[0]))).convert('RGB'); t.thumbnail((S, S))
        im.paste(t, ((S - t.width) // 2, (S - t.height) // 2))
    else:
        for i, x in enumerate(files):
            t = ImageOps.exif_transpose(Image.open(os.path.join(photos, x))).convert('RGB'); t.thumbnail((S // 2, S // 2))
            im.paste(t, ((i % 2) * S // 2 + (S // 2 - t.width) // 2, (i // 2) * S // 2 + (S // 2 - t.height) // 2))
    im.save(path, quality=90); return True


def facts(folder, files):
    rs = [feat[x] for x in files if x in feat]
    mains = np.mean([float(r['mains']) > 0 for r in rs]) if rs else 0
    faces = int(round(float(np.median([float(r['faces']) for r in rs])))) if rs else 0
    who = 'yes' if mains >= 0.6 else 'in some' if mains > 0 else 'no'
    return f"Folder: {folder}. Photos in this moment: {len(files)}. Main people in it: {who}. Faces: {faces}."


QUESTION = "What is this moment, and does it belong in the album? Say what kind of moment, what is happening and why, then the verdict."
rows, seen = [], []  # seen: (mean vector, words, verdict) of the moments read so far
counts = collections.Counter()
for mid, folder, files in moments:
    v, kind, why = gold[mid]
    path = f'{out}/sheets/{mid}.jpg'
    if not sheet(files, path): continue
    vs = [vec[x] for x in files if x in vec]
    m = np.mean(vs, axis=0) if vs else None
    memory = 'none'
    if m is not None and seen:
        sims = sorted(((float(m @ s[0]) / (np.linalg.norm(m) * np.linalg.norm(s[0])), s) for s in seen), key=lambda t: -t[0])[:2]
        near = [f"{s[1]} ({'kept' if s[2] else 'skipped'})" for c, s in sims if c >= 0.80]
        if near: memory = '; '.join(near)
    held = any(w in folder.lower() for w in HELD)
    rows.append({'id': mid, 'folder': folder, 'image': path, 'split': 'test' if held else 'train', 'verdict': v, 'kind': kind,
                 'prompt': f"Frames of one moment from a family event folder, read in order.\n{facts(folder, files)}\nSimilar moments read earlier: {memory}.\n{QUESTION}",
                 'answer': f"{kind}: {why}. Verdict: {VERDICT[v]}."})
    counts[(rows[-1]['split'], v)] += 1
    if m is not None: seen.append((m, why, v > 0))
with open(out + '/examples.jsonl', 'w', encoding='utf-8') as w:
    for r in rows: w.write(json.dumps(r, ensure_ascii=False) + '\n')
print(len(rows), 'examples;', {f'{s} {VERDICT[v]}': c for (s, v), c in sorted(counts.items())})
