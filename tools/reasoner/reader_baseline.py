"""What the reader has to beat on the same held-out functions: a plain scorer (as the app has today)
that sees only numbers: the moment's SigLIP vector and the app's facts. No words, no memory.
    python3 -I reader_baseline.py EXAMPLES.jsonl MOMENTS.tsv FEATURES.csv VECS.bin"""
import csv, json, struct, sys
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.decomposition import PCA
ex, moments_p, feats_p, vecs_p = sys.argv[1:5]
rows = [json.loads(l) for l in open(ex, encoding='utf-8')]
files = {}
for l in open(moments_p, encoding='utf-8'):
    c = l.rstrip('\n').split('\t'); files[c[0]] = [f for f in c[5].split(';') if f]
feat = {r['name']: r for r in csv.DictReader(open(feats_p, encoding='utf-8'))}
f = open(vecs_p, 'rb'); n, d = struct.unpack('<ii', f.read(8)); vec = {}
for _ in range(n):
    k, = struct.unpack('<i', f.read(4)); key = f.read(k).decode(); v = np.frombuffer(f.read(4 * d), '<f4'); vec[key] = v / np.linalg.norm(v)
KEYS = ('quality', 'faces', 'people', 'mains', 'maxFace', 'smile', 'looking', 'interaction', 'emotional')
def x_of(r):
    fs = files[r['id']]; vs = [vec[x] for x in fs if x in vec]; rs = [feat[x] for x in fs if x in feat]
    m = np.mean(vs, axis=0) if vs else np.zeros(d)
    nums = [np.mean([float(q[k]) for q in rs]) if rs else 0.0 for k in KEYS] + [np.log1p(len(fs))]
    return m, np.array(nums)
V, N = zip(*[x_of(r) for r in rows]); V = np.array(V); N = np.array(N)
tr = np.array([r['split'] == 'train' for r in rows]); y = np.array([r['verdict'] for r in rows])
maj2 = max(np.mean(y[~tr] > 0), 1 - np.mean(y[~tr] > 0)); maj3 = max(np.mean(y[~tr] == c) for c in range(3))
print(f'{(~tr).sum()} held-out moments | always the commonest answer: keep-or-skip {maj2:.1%}, skip/keep/peak {maj3:.1%}')
for dims in (16, 32, 64):
    p = PCA(dims, random_state=0).fit(V[tr]); mu, sd = N[tr].mean(0), N[tr].std(0) + 1e-6
    X = np.hstack([p.transform(V), (N - mu) / sd])
    for C in (0.1, 1.0):
        m2 = LogisticRegression(C=C, max_iter=2000).fit(X[tr], y[tr] > 0); m3 = LogisticRegression(C=C, max_iter=2000).fit(X[tr], y[tr])
        print(f'numbers only, {dims} dims, C {C}: same keep-or-skip {m2.score(X[~tr], y[~tr] > 0):.1%}, same skip/keep/peak {m3.score(X[~tr], y[~tr]):.1%}')
