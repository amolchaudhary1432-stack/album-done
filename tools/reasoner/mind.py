"""Album Mind v0: our own small transformer. Every photo of a day is a token (what SigLIP 2 sees,
the face/people measurements, its place in the day); attention lets each photo weigh itself against
all the others; out comes how much the album needs it. Trained on the practice sets' picks only.
    python3 -I mind.py S [seed]"""
import csv, sys, collections, time
import numpy as np, jax, jax.numpy as jnp, optax
S = sys.argv[1]; seed = int(sys.argv[2]) if len(sys.argv) > 2 else 0
z = np.load(S + '/reasoner/sets.npz'); emb = dict(zip(z['keys'], z['vecs'].astype(np.float32)))
rows = [r for r in csv.DictReader(open(S + '/features.csv')) if f"{r['set']}/{r['name']}" in emb]
meta = ('set', 'split', 'community', 'name', 'picked', 'moment', 'order', 'repeat')
feat = [k for k in rows[0] if k not in meta]
def day(rs, E):
    """One day as arrays, in time order: embedding (centred on the day), features, place, labels."""
    o = np.argsort([int(r['order']) for r in rs]); rs = [rs[i] for i in o]; E = E[o]
    E = E - E.mean(0, keepdims=True)
    F = np.array([[float(r[k]) for k in feat] for r in rs], np.float32)
    n = len(rs); pos = np.arange(n) / max(1, n - 1)
    newm = np.array([1.0] + [float(rs[i]['moment'] != rs[i - 1]['moment']) for i in range(1, n)], np.float32)
    P = np.stack([pos, newm, np.full(n, np.log(n) / 8)], 1).astype(np.float32)
    y = np.array([r.get('picked') == '1' for r in rs], np.float32)
    return dict(rs=rs, E=E.astype(np.float32), F=F, P=P, y=y)
by = collections.defaultdict(list)
for r in rows: by[r['set']].append(r)
days = {sid: day(rs, np.array([emb[f"{sid}/{r['name']}"] for r in rs])) for sid, rs in by.items()}
import zlib
tune_all = [(sid, d) for sid, d in days.items() if by[sid][0]['split'] == 'tune']; test = [d for sid, d in days.items() if by[sid][0]['split'] == 'test']
val = [d for sid, d in tune_all if zlib.crc32(sid.encode()) % 5 == 0]; tune = [d for sid, d in tune_all if zlib.crc32(sid.encode()) % 5 != 0]
# What it sees, made smaller: the 64 main directions of the practice photos' embeddings.
Eall = np.concatenate([d['E'] for d in tune]); _, _, Vt = np.linalg.svd(Eall - Eall.mean(0), full_matrices=False); PC = Vt[:64].T.astype(np.float32)
for d in list(days.values()): d['E'] = d['E'] @ PC
Fall = np.concatenate([d['F'] for d in tune]); fmu, fsd = Fall.mean(0), Fall.std(0) + 1e-6
D, H, L = 48, 4, 2
def init(key):
    ks = iter(jax.random.split(key, 32)); g = lambda *s: jax.random.normal(next(ks), s) * (1 / np.sqrt(s[0]))
    p = dict(we=g(64, D), wf=g(len(feat), D), wp=g(3, D), out=g(D, 1) * 0.1, b=jnp.zeros(1))
    for l in range(L):
        p[f'q{l}'], p[f'k{l}'], p[f'v{l}'], p[f'o{l}'] = g(D, D), g(D, D), g(D, D), g(D, D)
        p[f'm1{l}'], p[f'm2{l}'] = g(D, 2 * D), g(2 * D, D)
    return p
def ln(x): return (x - x.mean(-1, keepdims=True)) / (x.std(-1, keepdims=True) + 1e-5)
def forward(p, E, F, P, drop_key=None):
    x = E @ p['we'] + ((F - fmu) / fsd) @ p['wf'] + P @ p['wp']
    for l in range(L):
        h = ln(x); n = h.shape[0]
        q = (h @ p[f'q{l}']).reshape(n, H, D // H); k = (h @ p[f'k{l}']).reshape(n, H, D // H); v = (h @ p[f'v{l}']).reshape(n, H, D // H)
        a = jax.nn.softmax(jnp.einsum('ihd,jhd->hij', q, k) / np.sqrt(D // H), -1)
        x = x + jnp.einsum('hij,jhd->ihd', a, v).reshape(n, D) @ p[f'o{l}']
        x = x + jax.nn.gelu(ln(x) @ p[f'm1{l}']) @ p[f'm2{l}']
        if drop_key is not None: x = x * jax.random.bernoulli(jax.random.fold_in(drop_key, l), 0.7, x.shape) / 0.7
    return (ln(x) @ p['out'])[:, 0] + p['b'][0]
def loss_fn(p, E, F, P, y, key):
    s = forward(p, E, F, P, key)
    pos = y.mean(); w = jnp.where(y > 0, 0.5 / jnp.maximum(pos, 1e-3), 0.5 / jnp.maximum(1 - pos, 1e-3))
    bce = (w * optax.sigmoid_binary_cross_entropy(s, y)).mean()
    # And rank the day's picks above its others (what a selection of k needs).
    diff = s[:, None] - s[None, :]; m = y[:, None] * (1 - y[None, :])
    rank = (m * jax.nn.softplus(-diff)).sum() / jnp.maximum(m.sum(), 1)
    return bce + rank
grad = jax.jit(jax.value_and_grad(loss_fn))
fwd = jax.jit(forward)
p = init(jax.random.PRNGKey(seed)); opt = optax.adamw(1e-3, weight_decay=0.3); st = opt.init(p)
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
def evaluate(ds):
    tot = ex = mo = 0
    for d in ds:
        s = np.array(fwd(p, d['E'], d['F'], d['P'])); k = int(d['y'].sum())
        if k == 0: continue
        pick = [d['rs'][i] for i in np.argsort(-s)[:k]]; human = [r for r, y in zip(d['rs'], d['y']) if y]
        tot += k; ex += sum(h in pick for h in human); mo += sum(any(same(h, a) for a in pick) for h in human)
    return ex / tot, mo / tot
key = jax.random.PRNGKey(seed + 1); t0 = time.time(); best = (-1, None, 0)
for epoch in range(1, 41):
    order = np.random.default_rng(epoch + seed).permutation(len(tune)); tl = 0
    for i in order:
        d = tune[i]; key, sk = jax.random.split(key)
        l, g = grad(p, d['E'], d['F'], d['P'], d['y'], sk); up, st = opt.update(g, st, p); p = optax.apply_updates(p, up); tl += float(l)
    ve, vm = evaluate(val)
    if ve + vm > best[0]: best = (ve + vm, jax.tree_util.tree_map(np.array, p), epoch)
    if epoch % 5 == 0: print(f"epoch {epoch}: loss {tl / len(tune):.3f} | validation same photo {ve:.1%} moment {vm:.1%}", flush=True)
p = best[1]; e, m = evaluate(test)
print(f"stopped at epoch {best[2]} (by validation) | held back: same photo {e:.1%}, moment {m:.1%} | {len(tune)} sets learned, {len(val)} for validation | {time.time() - t0:.0f}s")
import pickle; pickle.dump(p, open(S + f'/reasoner/mind-v0b-s{seed}.pkl', 'wb')); pickle.dump(dict(PC=PC, fmu=fmu, fsd=fsd, feat=feat), open(S + '/reasoner/mind-v0b-prep.pkl', 'wb'))
