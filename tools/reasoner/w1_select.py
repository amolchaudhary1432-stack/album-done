"""The learned selector (trained on all 71 picked sets, never on wedding-1) on the real couple's folder:
how many of the couple's photos and moments it catches at a given size."""
import csv, sys, collections
import numpy as np
S = sys.argv[1]
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
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
t = list(csv.DictReader(open(S + '/reasoner/w1-features.csv'))); zt = np.load(S + '/reasoner/w1.npz')
temb = dict(zip(zt['keys'], zt['vecs'].astype(np.float32)))
keys = [r['name'].rsplit('.', 1)[0] + '.jpg' for r in t]
Et = centred(keys, [r['event'] for r in t], temb); raw = np.array([temb[k] for k in keys])
sc = 1 / (1 + np.exp(-(((np.hstack([np.array([[float(r[k]) for k in feat] for r in t]), Et @ P]) - mu) / sd) @ w + b)))
couple = {l.strip() for l in open(S + '/w1album/album/album-picks.txt') if l.strip()}
human = [r for r in t if r['name'] in couple]
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
def select(k, dup, crowd):
    order = np.argsort(-sc); chosen = []; inmom = collections.Counter()
    cand = [i for i in order if float(t[i]['usable']) > 0.5]
    while len(chosen) < k:
        best, bv = None, -9
        for i in cand[:1500]:
            if i in chosen: continue
            v = sc[i] - crowd * inmom[t[i]['moment']]
            if v <= bv: continue
            if chosen and float(np.max(raw[chosen] @ raw[i])) > dup: continue
            best, bv = i, v
        if best is None: break
        chosen.append(best); inmom[t[best]['moment']] += 1
    return [t[i] for i in chosen]
for dup, crowd in ((0.90, 0.05), (0.90, 0.15)):
    for k in (233, 400):
        app = select(k, dup, crowd)
        ex = sum(h in app for h in human); mo = sum(any(same(h, a) for a in app) for h in human)
        ev = collections.Counter(a['event'] for a in app)
        print(f"near-copy {dup} crowding {crowd} | {k} photos: same photo {ex / len(human):.1%}, same moment {mo / len(human):.1%} | {dict(ev)}")
print(len(human), "of the couple's photos found")
