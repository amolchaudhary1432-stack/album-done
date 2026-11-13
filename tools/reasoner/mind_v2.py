"""Album Mind v2 on a real folder (wedding-1), honest yardstick. Learned on the 71 picked sets only:
scene judge v2 learned on wedding-2 (a raw folder, judged moment by moment) and frame judge (full-size rankings). Reasoning: places go
one at a time to the moment that gains most (its worth, less for each photo it already has), the best
unchosen frame that is not a near-copy, then everyone close with the couple.
    python3 -I mind_v1.py S SIZE [decay]"""
import csv, glob, json, os, sys, collections, random
import numpy as np
S, SIZE = sys.argv[1], int(sys.argv[2]); DECAY = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
BEATS = os.environ.get('BEATS') == '1'; BEAT_T = float(os.environ.get('BEAT_T', '0.85'))
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
Et = np.array([emb[f"{r['set']}/{r['name']}"] for r in rows]); _, _, Vt = np.linalg.svd(Et - Et.mean(0), full_matrices=False); P = Vt[:32].T
F = np.array([[float(r[k]) for k in feat] for r in rows]); mu, sd = F.mean(0), F.std(0) + 1e-6
def moment_x(fs, es, n_day):
    f = (np.array(fs) - mu) / sd
    return np.concatenate([f.mean(0), f.max(0), np.array(es).mean(0) @ P, [np.log(len(fs)), len(fs) / n_day]])
# Scene judge v2: learned on wedding-2's 961 real moments, judged by Claude (0 skip, 1 one photo,
# 2 a peak), with who is in each moment and where it sits in the day; wedding-1 is never seen.
def people_of(r, key):
    v = r[key] if key in r else ''
    return set(v) if isinstance(v, set) else set(v.split(';')) - {''}
def folder_moments(rows_, E_, couple_ids):
    """Moment features for one raw folder: what it shows, who is in it, how rare they are, when."""
    for i, r in enumerate(rows_):
        r['_i'] = i; r['_who'] = people_of(r, 'who'); r['_fam'] = (people_of(r, 'fam') if 'fam' in r else people_of(r, 'family')) - couple_ids
    F_ = np.array([[float(r[k]) for k in feat] for r in rows_]); fmu, fsd = F_.mean(0), F_.std(0) + 1e-6
    by_ev_ = collections.defaultdict(list)
    for r in rows_: by_ev_[r['event']].append(r)
    mom_ = collections.defaultdict(list)
    for r in rows_: mom_[(r['event'], r['moment'])].append(r)
    seen_in = collections.Counter()
    for rs in mom_.values():
        for p in set().union(*(r['_who'] for r in rs)): seen_in[p] += 1
    out = {}
    for (ev, m), rs in mom_.items():
        evrs = by_ev_[ev]; ids = [r['_i'] for r in rs]
        cen = E_[[r['_i'] for r in evrs]].mean(0)
        f = ((np.array([[float(r[k]) for k in feat] for r in rs]) - fmu) / fsd)
        ts = sorted(int(r['taken']) for r in evrs if int(r['taken']) > 0) or [0]
        t0 = min((int(r['taken']) for r in rs if int(r['taken']) > 0), default=ts[0])
        pos = (t0 - ts[0]) / max(1, ts[-1] - ts[0])
        who = set().union(*(r['_who'] for r in rs)); fam = set().union(*(r['_fam'] for r in rs))
        withc = np.mean([bool(r['_who'] & couple_ids) for r in rs]); both = np.mean([couple_ids <= r['_who'] for r in rs])
        rare = min((seen_in[p] for p in fam), default=99)
        strangers = len(who - fam - couple_ids) / len(rs)
        x = np.concatenate([f.mean(0), f.max(0), (E_[ids].mean(0) - cen) @ P, E_[ids].mean(0) @ P2,
                            [np.log(len(rs)), len(rs) / len(evrs), pos, withc, both, len(fam), np.log1p(rare), strangers,
                             np.log1p(len(who))]])
        out[(ev, m)] = (x, rs)
    return out
W2 = S + '/w2'
w2rows = list(csv.DictReader(open(W2 + '/features.csv')))
z2 = np.load(S + '/reasoner/w2.npz'); e2 = dict(zip(z2['keys'], z2['vecs'].astype(np.float32)))
E2 = np.array([e2[r['name']] for r in w2rows])
_, _, V2 = np.linalg.svd(E2 - E2.mean(0), full_matrices=False); P2 = V2[:16].T
M2 = folder_moments(w2rows, E2, {'1', '2'})
code = {}
for l in open(W2 + '/moments.tsv'):
    c = l.rstrip('\n').split('\t'); code[c[0]] = (c[1], c[2])
lab = {code[c[0]]: int(c[1]) for c in (l.split('\t') for l in open(W2 + '/labels.tsv'))}
keys2 = [k for k in M2 if k in lab]
X2 = np.array([M2[k][0] for k in keys2]); y2 = np.array([lab[k] for k in keys2]); ev2 = np.array([k[0] for k in keys2])
# Lessons from the picked web sets of every community too (WEB=1): a moment's worth = how many of its
# photos the person kept (0, 1, 2+). Couple = the two most photographed people of the set.
import os, glob
comm2 = np.array(['marathi-hindu'] * len(keys2))
if os.environ.get('WEB') == '1':
    zs = np.load(S + '/reasoner/sets.npz'); es = dict(zip(zs['keys'], zs['vecs'].astype(np.float32)))
    LAB = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../harvest/labels')
    XW, YW, CW = [], [], []
    for f in sorted(glob.glob(S + '/webfeat/*.csv')):
        sid = os.path.basename(f)[:-4]; rows_ = list(csv.DictReader(open(f)))
        lines = [l.strip() for l in open(os.path.join(LAB, sid + '.txt'))]
        comm = next((l.split(':', 1)[1].strip() for l in lines if l.startswith('# community:')), 'older')
        picked = {l for l in lines if l and not l.startswith('#')}
        rows_ = [r for r in rows_ if f"{sid}/{r['name']}" in es]
        if len(rows_) < 10: continue
        Es = np.array([es[f"{sid}/{r['name']}"] for r in rows_])
        cnt = collections.Counter(p for r in rows_ for p in set(r['who'].split(';')) - {''})
        cpl = {p for p, _ in cnt.most_common(2)}
        for (ev, m), (x, rs) in folder_moments(rows_, Es, cpl).items():
            XW.append(x); YW.append(min(2, sum(r['name'] in picked for r in rs))); CW.append(comm)
    X2 = np.vstack([X2, np.array(XW)]); y2 = np.r_[y2, np.array(YW)]; comm2 = np.r_[comm2, np.array(CW)]; ev2 = np.r_[ev2, np.array(CW)]
    print(len(XW), 'web moments from', len(set(CW)), 'communities added')
if os.environ.get('NO_W2') == '1':
    keep_ = comm2 != 'marathi-hindu'; X2, y2, comm2, ev2 = X2[keep_], y2[keep_], comm2[keep_], ev2[keep_]
    print('wedding-2 left out of the lessons:', len(y2), 'web moments only')
xm, xs = X2.mean(0), X2.std(0) + 1e-6
if os.environ.get('DUMPX'): np.savez(os.environ['DUMPX'], X=X2, y=y2, ev=ev2, comm=comm2)  # the judge's lessons, for trying other judges
def fit(X, y, l2=30.0):
    w = np.zeros(X.shape[1]); b = 0.0; pos_ = max(1e-3, y.mean()); wt = np.where(y > 0, .5 / pos_, .5 / (1 - pos_))
    for _ in range(4000):
        p = 1 / (1 + np.exp(-(X @ w + b))); w -= 0.05 * (X.T @ ((p - y) * wt) / len(y) + l2 * w / len(y)); b -= 0.05 * ((p - y) * wt).mean()
    return w, b
def judge(X, models): return sum(1 / (1 + np.exp(-((X - xm) / xs @ w + b))) for w, b in models)
# Honest check inside wedding-2: each photographer folder judged by a judge that never saw it.
from itertools import combinations
hits = []; auc1 = []; auc2 = []
def auc(s, y):
    pos_, neg = s[y], s[~y]
    return np.mean([(p > neg).mean() + .5 * (p == neg).mean() for p in pos_]) if len(pos_) and len(neg) else float('nan')
for e in sorted(set(ev2)):
    tr = ev2 != e; Xn = (X2 - xm) / xs
    ms = [fit(Xn[tr], (y2[tr] >= 1).astype(float)), fit(Xn[tr], (y2[tr] >= 2).astype(float))]
    s = judge(X2[~tr], ms)
    auc1.append(auc(s, y2[~tr] >= 1)); auc2.append(auc(s, y2[~tr] >= 2))
loco = []
for c in sorted(set(comm2)):
    tr = comm2 != c
    if tr.all() or (~tr).sum() < 20: continue
    Xn_ = (X2 - xm) / xs; ms = [fit(Xn_[tr], (y2[tr] >= 1).astype(float)), fit(Xn_[tr], (y2[tr] >= 2).astype(float))]
    sc = judge(X2[~tr], ms); loco.append((c, (~tr).sum(), auc(sc, y2[~tr] >= 1)))
print('community never seen in training, keep-or-skip AUC: ' + ', '.join(f'{c} {a:.2f} ({n})' for c, n, a in loco))
print('wedding-2, each folder unseen: keep-or-skip AUC %.2f, peak AUC %.2f (0.5 = coin)' % (np.nanmean(auc1), np.nanmean(auc2)))
Xn = (X2 - xm) / xs
models = [fit(Xn, (y2 >= 1).astype(float)), fit(Xn, (y2 >= 2).astype(float))]
if os.environ.get('JUDGE') == 'trees':
    # Boosted trees instead of the two straight-line judges: better on folders held out of training
    # (keep-or-skip AUC 0.795 vs 0.774, peak 0.825 vs 0.797). Same lessons, same facts per moment.
    from sklearn.ensemble import HistGradientBoostingClassifier
    _t = [HistGradientBoostingClassifier(max_iter=200, learning_rate=.05, max_depth=4, class_weight='balanced', random_state=0).fit(X2, y2 >= k) for k in (1, 2)]
    judge = lambda X, models_=None: sum(m.predict_proba(X)[:, 1] for m in _t)
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
TARGET = os.environ.get('TARGET', 'w1')
if TARGET == 'w2':
    # Wedding-2 (no family album): some of its event folders, scored against Claude's moment judgements.
    keepf = set(os.environ['FOLDERS'].split('|')) if os.environ.get('FOLDERS') else None
    t = [dict(r) for r in w2rows if keepf is None or r['event'] in keepf]; temb = e2
else:
    t = list(csv.DictReader(open(S + '/reasoner/w1-features.csv'))); zt = np.load(S + '/reasoner/w1.npz'); temb = dict(zip(zt['keys'], zt['vecs'].astype(np.float32)))
Ew = np.array([temb[r['name'].rsplit('.', 1)[0] + '.jpg'] for r in t])
for i, r in enumerate(t):
    r['i'] = i; r['ok'] = float(r['usable']) > 0.5; r['f'] = np.array([float(r[k]) for k in feat])
    r['who'] = set(r['who'].split(';')) - {''}; r['fam'] = set(r['family'].split(';')) - {''}
    r['frame'] = float(np.concatenate([(r['f'] - mu) / sd, Ew[i] @ Vt[:64].T]) @ fw)
if os.environ.get('FRAME'):
    # Our own small frame judge, taught by the eyes (frame_distill.py), added to the old one, both made comparable.
    wd = np.load(os.environ['FRAME']); dist = Ew @ wd[:-1] + wd[-1]; old_ = np.array([r['frame'] for r in t])
    for i, r in enumerate(t): r['frame'] = (old_[i] - old_.mean()) / (old_.std() + 1e-9) + float(os.environ.get('FRAME_W', '1')) * (dist[i] - dist.mean()) / (dist.std() + 1e-9)
if os.environ.get('EXPORT'):
    # Everything the phone needs to reason the same way (AlbumMind.kt): written as named float arrays.
    wd_ = np.load(os.environ['FRAME']) if os.environ.get('FRAME') else np.zeros(Ew.shape[1] + 1)
    arrs = {'P': Vt[:32].T, 'P2': P2, 'V64': Vt[:64].T, 'xm': xm, 'xs': xs, 'w1': models[0][0], 'b1': [models[0][1]],
            'w2': models[1][0], 'b2': [models[1][1]], 'mu': mu, 'sd': sd, 'fw': fw, 'wd': wd_,
            'consts': [DECAY, BEAT_T, 0.92, float(os.environ.get('FRAME_W', '1')), 1.0 if os.environ.get('FRAME') else 0.0]}
    import struct
    with open(os.environ['EXPORT'], 'wb') as fo:
        fo.write(b'AMND'); fo.write(struct.pack('<i', 1)); names = '\n'.join(feat).encode(); fo.write(struct.pack('<i', len(names))); fo.write(names)
        fo.write(struct.pack('<i', len(arrs)))
        for k, v in arrs.items():
            v = np.asarray(v, dtype='<f4'); v2 = v.reshape(v.shape[0], -1) if v.ndim > 1 else v.reshape(-1, 1)
            kb = k.encode(); fo.write(struct.pack('<i', len(kb))); fo.write(kb); fo.write(struct.pack('<ii', *v2.shape)); fo.write(v2.tobytes())
    print('exported', os.environ['EXPORT'], {k: np.asarray(v).shape for k, v in arrs.items()})
    if os.environ.get('DUMP'):
        # Reference numbers for checking the Kotlin version on the same folder (never committed for a couple's folder).
        np.savez(os.environ['DUMP'], names=np.array([r['name'] for r in t]), frame=np.array([r['frame'] for r in t]))
by_ev = collections.defaultdict(list)
for r in t: by_ev[r['event']].append(r)
mom = collections.defaultdict(list)
for ev, rs in by_ev.items():
    E = Ew[[r['i'] for r in rs]]; E = E - E.mean(0); ii = {r['i']: k for k, r in enumerate(rs)}
    for r in rs: mom[r['moment']].append(r)
# Beats (BEATS=1): a long moment holds several poses or happenings. Photos in time order stay in one beat
# while they look alike (SigLIP cosine >= 0.85 to the beat's first photo; set on wedding-2: a beat is a pose
# of about 3-4 frames). A moment's next photo comes from a beat it has not used yet, like a person who
# picks different poses from a long portrait session rather than three frames of one pose.
for m, rs in mom.items():
    anchor, b = None, -1
    for r in sorted(rs, key=lambda r: int(r['order'])):
        v = Ew[r['i']] / np.linalg.norm(Ew[r['i']])
        if anchor is None or float(v @ anchor) < BEAT_T: anchor, b = v, b + 1
        r['beat'] = b
M1 = folder_moments(t, Ew, {'1', '2'})
worth = {}
for (ev, m), (x, rs) in M1.items(): worth[m] = float(judge(x[None], models)[0]) / 2
open(S + '/reasoner/w1-worth-v2.tsv', 'w').writelines(f'{m}\t{v:.4f}\n' for m, v in worth.items())
# Room (BEATS=1): a moment with more distinct beats has room for more photos before it repeats itself:
# each extra photo loses value as if the moment were sqrt(beats) times shorter (one beat: as before).
room = {m: (np.sqrt(1 + max(r['beat'] for r in rs)) if BEATS else 1.0) for m, rs in mom.items()}
room['-1'] = 1.0
chosen, count = [], collections.Counter(); taken = set()
def best_frame(m):
    cands = [r for r in mom[m] if r['ok'] and r['i'] not in taken]
    if chosen: cands = [r for r in cands if float(np.max(Ew[[c['i'] for c in chosen if c['moment'] == m] or [chosen[0]['i']]] @ Ew[r['i']])) < 0.92] if any(c['moment'] == m for c in chosen) else cands
    if BEATS:
        used = {c['beat'] for c in chosen if c['moment'] == m}
        fresh = [r for r in cands if r['beat'] not in used]
        if fresh: cands = fresh
    return max(cands, key=lambda r: r['frame'], default=None)
while len(chosen) < SIZE:
    best, bv = None, -1
    for m in mom:
        v = worth[m] * DECAY ** (count[m] / room[m])
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
        drop = min(chosen, key=lambda c: worth[c["moment"]] * DECAY ** ((count[c["moment"]] - 1) / room[c["moment"]])); chosen.remove(drop); taken.discard(drop['i']); count[drop['moment']] -= 1
        add = max(cands, key=lambda r: r['frame']); chosen.append(add); taken.add(add['i']); count[add['moment']] += 1
if TARGET == 'w2':
    # Claude's judgement of each moment: 0 skip, 1 one photo, 2 a peak. How many of the moments Claude
    # keeps get a photo, how many peaks, and how many places go to moments Claude skips.
    def w2score(app):
        got = {(r['event'], r['moment']) for r in app}; ms = {(r['event'], r['moment']) for r in t}
        keepm = [m for m in ms if lab.get(m, 0) >= 1]; peaks = [m for m in ms if lab.get(m, 0) >= 2]
        return (sum(m in got for m in keepm) / len(keepm), sum(m in got for m in peaks) / len(peaks),
                sum(lab.get((r['event'], r['moment']), 0) == 0 for r in app) / len(app))
    k1, k2, k0 = w2score(chosen)
    usable = [r for r in t if r['ok']]; rr = np.mean([w2score(random.Random(i).sample(usable, min(SIZE, len(usable)))) for i in range(5)], axis=0)
    print(f"Album Mind v2 on wedding-2 ({len(t)} photos, {SIZE} chosen): Claude's keep moments covered {k1:.1%} (random {rr[0]:.1%}), "
          f"peaks {k2:.1%} (random {rr[1]:.1%}), places on moments Claude skips {k0:.1%} (random {rr[2]:.1%})")
    open(S + f'/reasoner/mind-v2-w2-{SIZE}.txt', 'w').writelines(r['name'] + '\n' for r in chosen)
    sys.exit(0)
couple = [r for r in t if r['name'] in {l.strip() for l in open(S + '/w1album/album/album-picks.txt')}]
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
def score(app):
    s = {id(x) for x in app}; return sum(id(h) in s for h in couple) / len(couple), sum(any(same(h, a) for a in app) for h in couple) / len(couple)
if os.environ.get('SCORE_LIST'):
    # Another album (Claude's ranked blind picks, cut to this size) on the same yardstick.
    byname = {r['name']: r for r in t}
    other = [byname[l.strip()] for l in open(os.environ['SCORE_LIST']) if l.strip() in byname and not l.startswith('#')][:SIZE]
    oe, om = score(other); rm_ = np.mean([score(random.Random(i).sample([r for r in t if r['ok']], len(other)))[1] for i in range(5)])
    print(f"Other album, {len(other)} photos: same photo {oe:.1%}, same moment {om:.1%} | random moment {rm_:.1%} | share of the gap closed {(om - rm_) / (1 - rm_):.0%}")
ex, mo = score(chosen)
usable = [r for r in t if r['ok']]; rs_ = [score(random.Random(i).sample(usable, SIZE)) for i in range(5)]; rm = np.mean([x[1] for x in rs_])
print(f"Album Mind v2, {SIZE} photos (decay {DECAY}): same photo {ex:.1%}, same moment {mo:.1%} | random moment {rm:.1%} | share of the gap closed {(mo - rm) / (1 - rm):.0%} | moments used {len(count)}")
open(S + f'/reasoner/mind-v2-{SIZE}.txt', 'w').writelines(r['name'] + '\n' for r in chosen)
