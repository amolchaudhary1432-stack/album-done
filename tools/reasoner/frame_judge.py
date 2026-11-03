"""Frame judge: of two near-copies of one scene, which would a person keep? Pairs from the picked sets:
a kept photo and an unkept near-twin (same burst/repeat group, or a look-alike of 0.88+ in the same
moment). Learned on practice sets, scored on held-back pairs. python3 -I frame_judge.py S"""
import csv, sys, collections, zlib
import numpy as np
S = sys.argv[1]
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
by = collections.defaultdict(list)
for r in rows: r['e'] = emb[f"{r['set']}/{r['name']}"]; r['f'] = np.array([float(r[k]) for k in feat], np.float32); by[r['set']].append(r)
def pairs(sets):
    out = []
    for sid in sets:
        rs = by[sid]
        for a in rs:
            if a['picked'] != '1': continue
            for b in rs:
                if b is a or b['picked'] == '1': continue
                twin = (int(a['repeat']) >= 0 and a['repeat'] == b['repeat']) or (a['moment'] == b['moment'] and float(a['e'] @ b['e']) >= 0.88)
                if twin: out.append((a, b))
    return out
tune = [s for s in by if by[s][0]['split'] == 'tune']; test = [s for s in by if by[s][0]['split'] == 'test']
ptr, pte = pairs(tune), pairs(test)
print(len(ptr), 'practice pairs,', len(pte), 'held-back pairs')
Fall = np.array([r['f'] for r in rows if r['split'] == 'tune']); mu, sd = Fall.mean(0), Fall.std(0) + 1e-6
Et = np.array([r['e'] for r in rows if r['split'] == 'tune']); _, _, Vt = np.linalg.svd(Et - Et.mean(0), full_matrices=False)
def X(prs, d):
    P = Vt[:d].T
    xa = np.array([np.concatenate([(a['f'] - mu) / sd, a['e'] @ P]) for a, b in prs]); xb = np.array([np.concatenate([(b['f'] - mu) / sd, b['e'] @ P]) for a, b in prs])
    return xa - xb
def fit(D, l2):
    # Both orders, so the judge has no side bias: kept-minus-other -> 1, other-minus-kept -> 0.
    Xs = np.vstack([D, -D]); y = np.concatenate([np.ones(len(D)), np.zeros(len(D))]); w = np.zeros(Xs.shape[1])
    for _ in range(2000):
        p = 1 / (1 + np.exp(-(Xs @ w))); w -= 0.05 * (Xs.T @ (p - y) / len(y) + l2 * w / len(y))
    return w
for d in (0, 16, 32, 64):
    for l2 in (10.0, 100.0):
        w = fit(X(ptr, d), l2); acc_t = np.mean(X(ptr, d) @ w > 0); acc = np.mean(X(pte, d) @ w > 0)
        print(f"seeing dims {d:2d} l2 {l2:5.0f}: practice {acc_t:.1%}, held-back pairs {acc:.1%}")
# A judge that only looks at the app's own quality number, for comparison.
q = feat.index('quality'); print(f"quality number alone: held-back pairs {np.mean([a['f'][q] > b['f'][q] for a, b in pte]):.1%}")
