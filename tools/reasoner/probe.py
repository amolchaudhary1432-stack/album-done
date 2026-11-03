"""Does what SigLIP sees predict which photos a person picks? Train on practice sets, test on held-back
ones. Per set, how many of the person's picks are in the model's top-k (k = number of picks)."""
import csv, sys, collections
import numpy as np
S = sys.argv[1]
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = list(csv.DictReader(open(S + '/features.csv')))
feat = [k for k in rows[0] if k not in ('set', 'split', 'community', 'name', 'picked')]
rows = [r for r in rows if f"{r['set']}/{r['name']}" in emb]
def X(rs, use_emb, use_feat):
    parts = []
    if use_feat: parts.append(np.array([[float(r[k]) for k in feat] for r in rs]))
    if use_emb:
        E = np.array([emb[f"{r['set']}/{r['name']}"] for r in rs])
        # Within a set, what stands out: the photo minus the set's average look.
        by = collections.defaultdict(list)
        for i, r in enumerate(rs): by[r['set']].append(i)
        for ids in by.values(): E[ids] -= E[ids].mean(0)
        parts.append(E)
    return np.hstack(parts)
def fit(Xt, y, l2):
    mu, sd = Xt.mean(0), Xt.std(0) + 1e-6; Z = (Xt - mu) / sd
    w = np.zeros(Z.shape[1]); b = 0.0; pos = y.mean(); wt = np.where(y == 1, .5 / pos, .5 / (1 - pos))
    for _ in range(1500):
        p = 1 / (1 + np.exp(-(Z @ w + b))); g = Z.T @ ((p - y) * wt) / len(y) + l2 * w / len(y)
        w -= 0.1 * g; b -= 0.1 * ((p - y) * wt).mean()
    return lambda Xs: ((Xs - mu) / sd) @ w + b
def topk(rs, s):
    by = collections.defaultdict(list)
    for r, v in zip(rs, s): by[r['set']].append((v, r['picked'] == '1'))
    hit = tot = 0
    for l in by.values():
        k = sum(p for _, p in l); hit += sum(p for _, p in sorted(l, key=lambda t: -t[0])[:k]); tot += k
    return hit / tot
tune = [r for r in rows if r['split'] == 'tune']; test = [r for r in rows if r['split'] == 'test']
yt = np.array([r['picked'] == '1' for r in tune], float)
for name, ue, uf, l2 in [('measurements only', 0, 1, 1.0), ('seeing only', 1, 0, 300.0), ('both', 1, 1, 300.0)]:
    f = fit(X(tune, ue, uf), yt, l2)
    print(f"{name:18s} practice {topk(tune, f(X(tune, ue, uf))):.1%}   held back {topk(test, f(X(test, ue, uf))):.1%}")
print(len(tune), 'practice photos,', len(test), 'held back')
