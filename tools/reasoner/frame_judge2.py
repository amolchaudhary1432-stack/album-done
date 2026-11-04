"""Frame judge taught at full size: pairs come from the helpers' rankings of near-twin groups
(scratchpad/frames/labels-*.tsv: id, ranking like B>A>C, sure|close|equal, reason). Features: what
SigLIP 2 sees in the full photo (main directions) and the app's measurements. Learned on practice
groups, scored on held-back groups (pairs from 'sure' and 'close' rankings)."""
import csv, glob, json, sys, collections
import numpy as np
S = sys.argv[1]
idx = {g['id']: g for g in json.load(open(S + '/frames/sheets/index.json'))}
z = np.load(S + '/reasoner/full.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = {f"{r['set']}/{r['name']}": r for r in csv.DictReader(open(S + '/features.csv'))}
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in next(iter(rows.values())) if k not in meta]
labels = []
for f in glob.glob(S + '/frames/labels-*.tsv'):
    for l in open(f):
        c = l.rstrip('\n').split('\t')
        if len(c) >= 3 and c[0] in idx: labels.append(c)
pairs = {'tune': [], 'test': []}; conf = collections.Counter(); old_agree = [0, 0]
for c in labels:
    g = idx[c[0]]; order = [x.strip() for x in c[1].split('>') if x.strip()]; conf[c[2]] += 1
    if c[2] not in ('sure', 'close'): continue
    names = {'ABCD'[k]: n for k, n in enumerate(g['photos'])}
    keys = [f"{g['set']}/{names[x]}" for x in order if x in names]
    for i in range(len(keys)):
        for j in range(i + 1, len(keys)):
            a, b = keys[i], keys[j]
            if a in emb and b in emb and a in rows and b in rows:
                pairs[g['split']].append((a, b, c[2]))
                # Did the old 300 px picks agree (when one of the two was picked and the other not)?
                pa, pb = rows[a]['picked'] == '1', rows[b]['picked'] == '1'
                if pa != pb: old_agree[0] += pa; old_agree[1] += 1
print(len(labels), 'sheets judged', dict(conf), '|', len(pairs['tune']), 'practice pairs,', len(pairs['test']), 'held-back pairs')
if old_agree[1]: print(f"the old thumbnail picks agree with the full-size ranking on {old_agree[0]}/{old_agree[1]} pairs ({old_agree[0] / old_agree[1]:.0%})")
E = np.array([emb[k] for k in emb]); _, _, Vt = np.linalg.svd(E - E.mean(0), full_matrices=False)
F = np.array([[float(rows[k][f]) for f in feat] for k in emb if k in rows]); mu, sd = F.mean(0), F.std(0) + 1e-6
def x(k, d): return np.concatenate([(np.array([float(rows[k][f]) for f in feat]) - mu) / sd, emb[k] @ Vt[:d].T])
def D(ps, d): return np.array([x(a, d) - x(b, d) for a, b, _ in ps])
def fit(Dm, l2):
    X = np.vstack([Dm, -Dm]); y = np.r_[np.ones(len(Dm)), np.zeros(len(Dm))]; w = np.zeros(X.shape[1])
    for _ in range(3000): p = 1 / (1 + np.exp(-(X @ w))); w -= 0.05 * (X.T @ (p - y) / len(y) + l2 * w / len(y))
    return w
for d in (0, 8, 16, 32, 64):
    for l2 in (1.0, 10.0, 100.0):
        w = fit(D(pairs['tune'], d), l2)
        acc = np.mean(D(pairs['test'], d) @ w > 0); sure = [p for p in pairs['test'] if p[2] == 'sure']
        acc_s = np.mean(D(sure, d) @ w > 0) if sure else float('nan')
        print(f"seeing {d:2d} l2 {l2:5.0f}: held-back pairs {acc:.1%} (sure ones {acc_s:.1%}) | practice {np.mean(D(pairs['tune'], d) @ w > 0):.1%}")
q = feat.index('quality'); print(f"the app's quality number alone: held-back {np.mean([float(rows[a]['quality']) > float(rows[b]['quality']) for a, b, _ in pairs['test']]):.1%}")
