"""Album Mind v1 on a real folder (wedding-1), honest yardstick. Learned on the 71 picked sets only:
scene judge (which moments a person uses) and frame judge (full-size rankings). Reasoning: places go
one at a time to the moment that gains most (its worth, less for each photo it already has), the best
unchosen frame that is not a near-copy, then everyone close with the couple.
    python3 -I mind_v1.py S SIZE [decay]"""
import csv, glob, json, sys, collections, random
import numpy as np
S, SIZE = sys.argv[1], int(sys.argv[2]); DECAY = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
Et = np.array([emb[f"{r['set']}/{r['name']}"] for r in rows]); _, _, Vt = np.linalg.svd(Et - Et.mean(0), full_matrices=False); P = Vt[:32].T
F = np.array([[float(r[k]) for k in feat] for r in rows]); mu, sd = F.mean(0), F.std(0) + 1e-6
def moment_x(fs, es, n_day):
    f = (np.array(fs) - mu) / sd
    return np.concatenate([f.mean(0), f.max(0), np.array(es).mean(0) @ P, [np.log(len(fs)), len(fs) / n_day]])
# Scene judge, learned on every picked set (wedding-1 never).
X, y = [], []
sets = collections.defaultdict(list)
for r in rows: sets[r['set']].append(r)
for sid, rs in sets.items():
    E = np.array([emb[f"{sid}/{r['name']}"] for r in rs]); E = E - E.mean(0); by = collections.defaultdict(list)
    for i, r in enumerate(rs): by[r['moment']].append(i)
    for ids in by.values():
        X.append(moment_x([[float(rs[i][k]) for k in feat] for i in ids], E[ids], len(rs))); y.append(float(any(rs[i]['picked'] == '1' for i in ids)))
X, y = np.array(X), np.array(y); w = np.zeros(X.shape[1]); b = 0.0; wt = np.where(y > 0, .5 / y.mean(), .5 / (1 - y.mean()))
for _ in range(3000):
    p = 1 / (1 + np.exp(-(X @ w + b))); w -= 0.05 * (X.T @ ((p - y) * wt) / len(y) + 10 * w / len(y)); b -= 0.05 * ((p - y) * wt).mean()
# Frame judge, learned on the full-size rankings (all groups, practice and held back: wedding-1 is the test).
idx = {g['id']: g for g in json.load(open(S + '/frames/sheets/index.json'))}
zf = np.load(S + '/reasoner/full.npz'); femb = dict(zip(zf['keys'], zf['vecs'].astype(np.float32)))
byk = {f"{r['set']}/{r['name']}": r for r in rows}
D = []
for f in glob.glob(S + '/frames/labels-*.tsv'):
    for l in open(f):
        c = l.rstrip('\n').split('\t')
        if len(c) < 3 or c[0] not in idx or c[2] not in ('sure', 'close'): continue
        g = idx[c[0]]; names = {'ABCD'[k]: n for k, n in enumerate(g['photos'])}
        ks = [f"{g['set']}/{names[x]}" for x in c[1].split('>') if x in names]
        for i in range(len(ks)):
            for j in range(i + 1, len(ks)):
                a, bb = ks[i], ks[j]
                if a in byk and bb in byk and a in femb and bb in femb:
                    xa = np.concatenate([(np.array([float(byk[a][k]) for k in feat]) - mu) / sd, femb[a] @ Vt[:64].T])
                    xb = np.concatenate([(np.array([float(byk[bb][k]) for k in feat]) - mu) / sd, femb[bb] @ Vt[:64].T])
                    D.append(xa - xb)
D = np.array(D); XX = np.vstack([D, -D]); yy = np.r_[np.ones(len(D)), np.zeros(len(D))]; fw = np.zeros(XX.shape[1])
for _ in range(3000): pp = 1 / (1 + np.exp(-(XX @ fw))); fw -= 0.05 * (XX.T @ (pp - yy) / len(yy) + 1.0 * fw / len(yy))
# The real folder.
t = list(csv.DictReader(open(S + '/reasoner/w1-features.csv'))); zt = np.load(S + '/reasoner/w1.npz'); temb = dict(zip(zt['keys'], zt['vecs'].astype(np.float32)))
Ew = np.array([temb[r['name'].rsplit('.', 1)[0] + '.jpg'] for r in t])
for i, r in enumerate(t):
    r['i'] = i; r['ok'] = float(r['usable']) > 0.5; r['f'] = np.array([float(r[k]) for k in feat])
    r['who'] = set(r['who'].split(';')) - {''}; r['fam'] = set(r['family'].split(';')) - {''}
    r['frame'] = float(np.concatenate([(r['f'] - mu) / sd, Ew[i] @ Vt[:64].T]) @ fw)
by_ev = collections.defaultdict(list)
for r in t: by_ev[r['event']].append(r)
mom = collections.defaultdict(list)
for ev, rs in by_ev.items():
    E = Ew[[r['i'] for r in rs]]; E = E - E.mean(0); ii = {r['i']: k for k, r in enumerate(rs)}
    for r in rs: mom[r['moment']].append(r)
worth = {}
for m, rs in mom.items():
    ev = rs[0]['event']; evrs = by_ev[ev]; E = Ew[[r['i'] for r in evrs]]; cen = E.mean(0)
    worth[m] = 1 / (1 + np.exp(-(moment_x([r['f'] for r in rs], Ew[[r['i'] for r in rs]] - cen, len(evrs)) @ w + b)))
chosen, count = [], collections.Counter(); taken = set()
def best_frame(m):
    cands = [r for r in mom[m] if r['ok'] and r['i'] not in taken]
    if chosen: cands = [r for r in cands if float(np.max(Ew[[c['i'] for c in chosen if c['moment'] == m] or [chosen[0]['i']]] @ Ew[r['i']])) < 0.92] if any(c['moment'] == m for c in chosen) else cands
    return max(cands, key=lambda r: r['frame'], default=None)
while len(chosen) < SIZE:
    best, bv = None, -1
    for m in mom:
        v = worth[m] * DECAY ** count[m]
        if v > bv:
            r = best_frame(m)
            if r is not None: best, bv = r, v
    if best is None: break
    chosen.append(best); taken.add(best['i']); count[best['moment']] += 1
bride, groom = '1', '2'
fam = set().union(*(r['fam'] for r in t)) - {bride, groom}
for p in sorted(fam):
    if any(p in c['who'] and (bride in c['who'] or groom in c['who']) for c in chosen): continue
    cands = [r for r in t if r['ok'] and p in r['who'] and (bride in r['who'] or groom in r['who']) and r['i'] not in taken]
    if cands:
        drop = min(chosen, key=lambda c: worth[c['moment']] * DECAY ** (count[c['moment']] - 1)); chosen.remove(drop); taken.discard(drop['i']); count[drop['moment']] -= 1
        add = max(cands, key=lambda r: r['frame']); chosen.append(add); taken.add(add['i']); count[add['moment']] += 1
couple = [r for r in t if r['name'] in {l.strip() for l in open(S + '/w1album/album/album-picks.txt')}]
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
def score(app):
    s = {id(x) for x in app}; return sum(id(h) in s for h in couple) / len(couple), sum(any(same(h, a) for a in app) for h in couple) / len(couple)
ex, mo = score(chosen)
usable = [r for r in t if r['ok']]; rs_ = [score(random.Random(i).sample(usable, SIZE)) for i in range(5)]; rm = np.mean([x[1] for x in rs_])
print(f"Album Mind v1, {SIZE} photos (decay {DECAY}): same photo {ex:.1%}, same moment {mo:.1%} | random moment {rm:.1%} | share of the gap closed {(mo - rm) / (1 - rm):.0%} | moments used {len(count)}")
open(S + f'/reasoner/mind-v1-{SIZE}.txt', 'w').writelines(r['name'] + '\n' for r in chosen)
