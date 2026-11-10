"""The small model that sees people on the phone, taught by the big one (tools/kaggle/people_teacher.py).

For each face the teacher said who the person is in the day (bride, groom, birthday person, baby of the
day, guest) and their age (baby ... elder), from everything in the photo. The student sees only what the
phone has: the SigLIP 2 vector of the person's crop and of the whole photo. It is a plain softmax layer
per question (768 + 768 numbers in, a few classes out), small enough to run for every face in a folder.

Honest check: photos are split by source folder (event), never by face, so the student is scored on
events it never learned from; wedding-1 (the exam) is never used.
    python3 -I people_student.py PEOPLE.jsonl PEOPLE.npz OUT.npz
"""
import json, sys, zlib
import numpy as np

ROLES = ['bride', 'groom', 'birthday person', 'baby of the day', 'guest']
AGES = ['baby', 'child', 'teen', 'adult', 'elder']

rows = {f"{r['photo']}#{r['face']}": r for r in (json.loads(l) for l in open(sys.argv[1]))}
z = np.load(sys.argv[2]); keys = [str(k) for k in z['keys']]
X = np.concatenate([z['crop'].astype(np.float32), z['photo'].astype(np.float32)], axis=1)


def labels(field, classes):
    y = np.full(len(keys), -1)
    for i, k in enumerate(keys):
        r = rows.get(k, {})
        v = str(r.get(field, '')).strip().lower()
        if v in classes and r.get('sure', 'yes') != 'no': y[i] = classes.index(v)
    return y


def event(k):  # w2/<event folder>/<file>#i or cc/<photo>#i: one group per event folder / per photographer's photo
    p = k.split('#')[0]
    return p.rsplit('/', 1)[0] if p.startswith('w2/') else 'cc/' + str(zlib.crc32(p.encode()) % 5)


def fit(X, y, k, l2=1e-3, steps=600, lr=0.5):
    W = np.zeros((X.shape[1], k)); b = np.zeros(k)
    Y = np.eye(k)[y]
    # Rare classes (the bride is one person among hundreds of guests) weigh as much as common ones.
    w = (len(y) / (k * np.maximum(1, np.bincount(y, minlength=k))))[y]
    for _ in range(steps):
        z = X @ W + b; z -= z.max(1, keepdims=True); p = np.exp(z); p /= p.sum(1, keepdims=True)
        g = (p - Y) * w[:, None] / len(y)
        W -= lr * (X.T @ g + l2 * W); b -= lr * g.sum(0)
    return W, b


def predict(X, W, b): return (X @ W + b).argmax(1)


out = {}
groups = np.array([event(k) for k in keys])
for name, field, classes in (('role', 'role', ROLES), ('age', 'age', AGES)):
    y = labels(field, classes); ok = y >= 0
    print(f'{name}: {ok.sum()} faces with a sure answer:', dict(zip(classes, np.bincount(y[ok], minlength=len(classes)))))
    # Leave one event out at a time.
    accs, per = [], np.zeros((len(classes), 2))
    for g in sorted(set(groups[ok])):
        tr = ok & (groups != g); te = ok & (groups == g)
        if te.sum() < 10 or len(set(y[tr])) < 2: continue
        W, b = fit(X[tr], y[tr], len(classes)); p = predict(X[te], W, b)
        accs.append((p == y[te]).mean())
        for c in range(len(classes)):
            m = y[te] == c; per[c] += [(p[m] == c).sum(), m.sum()]
    print(f'  events never seen: accuracy {np.mean(accs):.1%};', ', '.join(f'{c} {a / max(1, n):.0%} of {int(n)}' for c, (a, n) in zip(classes, per)))
    W, b = fit(X[ok], y[ok], len(classes))
    out[name + '_W'] = W.astype(np.float32); out[name + '_b'] = b.astype(np.float32)
np.savez(sys.argv[3], roles=np.array(ROLES), ages=np.array(AGES), **out)
print('saved', sys.argv[3])
