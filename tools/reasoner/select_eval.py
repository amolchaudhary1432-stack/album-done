"""Learned seeing scorer + selection that avoids near-copies (what it sees) and crowding a moment,
scored like harvest-score on the held-back sets: same photo, same moment."""
import csv, sys, collections
import numpy as np
S = sys.argv[1]
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
def centred(rs):
    E = np.array([emb[f"{r['set']}/{r['name']}"] for r in rs]); by = collections.defaultdict(list)
    for i, r in enumerate(rs): by[r['set']].append(i)
    for ids in by.values(): E[ids] -= E[ids].mean(0)
    return E
tune = [r for r in rows if r['split'] == 'tune']; test = [r for r in rows if r['split'] == 'test']
Et = centred(tune); U, s, Vt = np.linalg.svd(Et - Et.mean(0), full_matrices=False); P = Vt[:64].T
def X(rs, E): return np.hstack([np.array([[float(r[k]) for k in feat] for r in rs]), E @ P])
Xt = X(tune, Et); y = np.array([r['picked'] == '1' for r in tune], float)
mu, sd = Xt.mean(0), Xt.std(0) + 1e-6; Z = (Xt - mu) / sd
w = np.zeros(Z.shape[1]); b = 0.0; pos = y.mean(); wt = np.where(y == 1, .5 / pos, .5 / (1 - pos))
for _ in range(1500):
    p = 1 / (1 + np.exp(-(Z @ w + b))); g = Z.T @ ((p - y) * wt) / len(y) + 300.0 * w / len(y)
    w -= 0.1 * g; b -= 0.1 * ((p - y) * wt).mean()
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
def run(rs, dup, crowd):
    E = centred(rs); raw = np.array([emb[f"{r['set']}/{r['name']}"] for r in rs])
    sc = 1 / (1 + np.exp(-(((X(rs, E) - mu) / sd) @ w + b)))
    by = collections.defaultdict(list)
    for i, r in enumerate(rs): by[r['set']].append(i)
    tot = ex = mo = 0
    for sid, ids in by.items():
        human = [rs[i] for i in ids if rs[i]['picked'] == '1']; k = len(human)
        chosen = []; inmom = collections.Counter()
        while len(chosen) < k:
            best, bv = None, -9
            for i in ids:
                if i in chosen: continue
                if chosen and max(float(raw[i] @ raw[j]) for j in chosen) > dup: continue
                v = sc[i] - crowd * inmom[rs[i]['moment']]
                if v > bv: best, bv = i, v
            if best is None: break
            chosen.append(best); inmom[rs[best]['moment']] += 1
        app = [rs[i] for i in chosen]
        tot += k; ex += sum(h in app for h in human); mo += sum(any(same(h, a) for a in app) for h in human)
    return ex / tot, mo / tot
for dup in (1.01, 0.97, 0.94, 0.9):
    for crowd in (0.0, 0.05, 0.15):
        e, m = run(test, dup, crowd); print(f"near-copy limit {dup:.2f} crowding {crowd:.2f}: held back same photo {e:.1%}, same moment {m:.1%}")
