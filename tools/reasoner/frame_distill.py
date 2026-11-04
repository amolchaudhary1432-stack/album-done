"""Our own small frame judge, taught by the borrowed eyes (distillation): a line of numbers over the
SigLIP vector that predicts what Q-ReAlign and Intern-Decision think of a photo. Small enough for a
phone (768 numbers), sees what the big models see.
    python3 -I frame_distill.py S EYES.npz OUT.npy
Learns on 2/3 of the photo sets, reports on the other third (correlation with the eyes), then on the
helpers' full-size frame rankings (pairs put in the people's order: old judge 57-59%, the eyes 72%),
and saves the weights learned on all sets."""
import sys, zlib, json, glob, collections
import numpy as np
S, EYES, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
z = np.load(EYES); keys = z['keys']; X = z['siglip'].astype(np.float32)
cols = [c for c in ('q_quality', 'q_aesthetic', 'i_keep', 'i_score') if c in z.files]
sets = np.array([k.split('/')[0] for k in keys])
# The eyes' opinion, made comparable within each set (a person compares photos of the same day).
T = []
for c in cols:
    v = z[c].astype(np.float64); out = np.full_like(v, np.nan)
    for s in set(sets):
        m = (sets == s) & np.isfinite(v)
        if m.sum() >= 3: out[m] = (v[m] - v[m].mean()) / (v[m].std() + 1e-6)
    T.append(out)
T = np.array(T).T
ok = np.isfinite(T).all(1); y = T[ok].mean(1); Xo = X[ok]; so = sets[ok]
print(len(keys), 'photos;', ok.sum(), 'with every eye;', 'eyes:', cols)
test = np.array([zlib.crc32(s.encode()) % 3 == 0 for s in so])
def ridge(A, b, lam):
    A1 = np.c_[A, np.ones(len(A))]; R = lam * np.eye(A1.shape[1]); R[-1, -1] = 0
    return np.linalg.solve(A1.T @ A1 + R, A1.T @ b)
def pred(w, A): return np.c_[A, np.ones(len(A))] @ w
best = None
for lam in (1, 10, 100, 1000):
    w = ridge(Xo[~test], y[~test], lam); r = np.corrcoef(pred(w, Xo[test]), y[test])[0, 1]
    print(f'lambda {lam:5}: held-back sets, agreement with the eyes r = {r:.3f}')
    if best is None or r > best[0]: best = (r, lam)
w = ridge(Xo, y, best[1]); np.save(OUT, w.astype(np.float32))
# The real test: the helpers' full-size rankings of near-twins (never used above).
try:
    zf = np.load(S + '/reasoner/full.npz'); femb = dict(zip(zf['keys'], zf['vecs'].astype(np.float32)))
    idx = {g['id']: g for g in json.load(open(S + '/frames/sheets/index.json'))}
    agree = n = 0
    for f in glob.glob(S + '/frames/labels-*.tsv') or [S + '/../tools/reasoner/frame-labels.tsv']:
        for l in open(f):
            c = l.rstrip('\n').split('\t')
            if len(c) < 3 or c[0] not in idx or c[2] == 'equal': continue
            g = idx[c[0]]; names = {'ABCD'[k]: f"{g['set']}/{x}" for k, x in enumerate(g['photos'])}
            ks = [names[x] for x in c[1].split('>') if x in names and names[x] in femb]
            sc = {k: float(pred(w, femb[k][None])[0]) for k in ks}
            for i in range(len(ks)):
                for j in range(i + 1, len(ks)):
                    n += 1; agree += sc[ks[i]] > sc[ks[j]]
    print(f'full-size frame rankings: {agree / max(1, n):.1%} of {n} pairs in the people\'s order (old judge 57-59%, eyes together 72%)')
except Exception as e:
    print('frame-ranking check skipped:', e)
