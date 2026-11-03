"""Scene judge: which moments of a day does a person include at all? Each moment of each picked set is
one example (what it looks like on average and at its best, how big it is, its faces); label = the
person picked at least one photo from it. Learned on practice sets, scored on held-back sets: of the
moments a person used, how many are in the judge's top choices (as many as the person used)."""
import csv, sys, collections
import numpy as np
S = sys.argv[1]
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
sets = collections.defaultdict(list)
for r in rows: sets[r['set']].append(r)
tune_rows = [r for r in rows if r['split'] == 'tune']
Et = np.array([emb[f"{r['set']}/{r['name']}"] for r in tune_rows]); _, _, Vt = np.linalg.svd(Et - Et.mean(0), full_matrices=False); P = Vt[:32].T
F = np.array([[float(r[k]) for k in feat] for r in tune_rows]); mu, sd = F.mean(0), F.std(0) + 1e-6
def moments(sid):
    rs = sets[sid]; E = np.array([emb[f"{sid}/{r['name']}"] for r in rs]); E = E - E.mean(0)
    by = collections.defaultdict(list)
    for i, r in enumerate(rs): by[r['moment']].append(i)
    out = []
    for m, ids in by.items():
        f = (np.array([[float(rs[i][k]) for k in feat] for i in ids]) - mu) / sd
        x = np.concatenate([f.mean(0), f.max(0), E[ids].mean(0) @ P, [np.log(len(ids)), len(ids) / len(rs)]])
        out.append((x, any(rs[i]['picked'] == '1' for i in ids), len(ids)))
    return out
def data(split): return {sid: moments(sid) for sid in sets if sets[sid][0]['split'] == split}
tr, te = data('tune'), data('test')
X = np.array([x for ms in tr.values() for x, _, _ in ms]); y = np.array([float(p) for ms in tr.values() for _, p, _ in ms])
for l2 in (1.0, 10.0, 100.0):
    w = np.zeros(X.shape[1]); b = 0.0; pos = y.mean(); wt = np.where(y > 0, .5 / pos, .5 / (1 - pos))
    for _ in range(3000):
        p = 1 / (1 + np.exp(-(X @ w + b))); w -= 0.05 * (X.T @ ((p - y) * wt) / len(y) + l2 * w / len(y)); b -= 0.05 * ((p - y) * wt).mean()
    def cover(d, scorer):
        hit = tot = 0
        for ms in d.values():
            k = sum(p for _, p, _ in ms); order = sorted(ms, key=lambda m: -scorer(m)); hit += sum(p for _, p, _ in order[:k]); tot += k
        return hit / tot
    print(f"l2 {l2:5.0f}: practice {cover(tr, lambda m: m[0] @ w):.1%} | held back {cover(te, lambda m: m[0] @ w):.1%}")
rng = np.random.default_rng(0)
print(f"biggest moments first (what the old brain leans on): held back {cover(te, lambda m: m[2]):.1%} | random {np.mean([cover(te, lambda m: rng.random()) for _ in range(20)]):.1%}")
print(sum(len(v) for v in tr.values()), 'practice moments,', sum(len(v) for v in te.values()), 'held-back moments')
