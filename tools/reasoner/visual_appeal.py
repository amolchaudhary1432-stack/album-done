"""Learn appeal from measurements + what SigLIP sees (64-number summary of each photo's difference
from its event's average look) on the labelled sets; write it for a target folder.
    python3 -I visual_appeal.py S TRAIN_SPLITS(tune|all) TARGET_FEATURES.csv TARGET.npz OUT.tsv"""
import csv, sys, collections
import numpy as np
S, which, tf, tz, out = sys.argv[1:6]
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
if which == 'tune': rows = [r for r in rows if r['split'] == 'tune']
feat = [k for k in rows[0] if k not in ('set', 'split', 'community', 'name', 'picked')]
def centred(keys, groups, table):
    E = np.array([table[k] for k in keys]); by = collections.defaultdict(list)
    for i, g in enumerate(groups): by[g].append(i)
    for ids in by.values(): E[ids] -= E[ids].mean(0)
    return E
E = centred([f"{r['set']}/{r['name']}" for r in rows], [r['set'] for r in rows], emb)
U, s, Vt = np.linalg.svd(E - E.mean(0), full_matrices=False); P = Vt[:64].T
Xt = np.hstack([np.array([[float(r[k]) for k in feat] for r in rows]), E @ P]); y = np.array([r['picked'] == '1' for r in rows], float)
mu, sd = Xt.mean(0), Xt.std(0) + 1e-6; Z = (Xt - mu) / sd
w = np.zeros(Z.shape[1]); b = 0.0; pos = y.mean(); wt = np.where(y == 1, .5 / pos, .5 / (1 - pos))
for _ in range(1500):
    p = 1 / (1 + np.exp(-(Z @ w + b))); g = Z.T @ ((p - y) * wt) / len(y) + 300.0 * w / len(y)
    w -= 0.1 * g; b -= 0.1 * ((p - y) * wt).mean()
t = list(csv.DictReader(open(tf))); zt = np.load(tz); temb = dict(zip(zt['keys'], zt['vecs'].astype(np.float32)))
keys = [r['name'].rsplit('.', 1)[0] + '.jpg' for r in t]          # small copies are .jpg
groups = [r['name'].split('/')[0] if '/' in r['name'] else '' for r in t]
Et = centred(keys, groups, temb)
Xs = np.hstack([np.array([[float(r[k]) for k in feat] for r in t]), Et @ P])
sc = 1 / (1 + np.exp(-(((Xs - mu) / sd) @ w + b)))
with open(out, 'w') as f:
    for r, v in zip(t, sc): f.write(f"{r['name']}\t{v:.5f}\n")
print(len(rows), 'training photos;', len(t), 'scored')
