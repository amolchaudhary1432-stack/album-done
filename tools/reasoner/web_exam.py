"""Does the album brain work on a community it never learned from?  python3 web_exam.py S
For each community among the picked web sets: the scene judge is trained without that community's sets
(wedding-2 and the other communities only), then the brain (beats + room, as in mind_v2.py / AlbumMind.kt)
chooses as many photos from each of its sets as Claude picked by eye, and is scored against Claude's picks:
same photo, same moment (same burst, or the same moment within 3 shots), against random picking.
"Seen" = the same test with that community left in the training, for comparison.
Web sets are curated uploads (a few dozen photos, already someone's keepers), so this checks judgement on
unfamiliar communities, not how much of a raw day to keep."""
import os, sys, random, collections, glob, csv
os.environ['WEB'] = '1'; os.environ['BEATS'] = '1'
os.environ.setdefault('FRAME', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'weights/frame-distill-twins.npy'))
src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mind_v2.py')).read()
sys.argv = [sys.argv[0], sys.argv[1], '766']
exec(compile(src[:src.index("if os.environ.get('EXPORT')")], 'mind_v2_head', 'exec'))   # lessons, judges, frame judge for wedding-1 (unused here)
wd = np.load(os.environ['FRAME'])
LABDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../harvest/labels')
sets = {}
for f in sorted(glob.glob(S + '/webfeat/*.csv')):
    sid = os.path.basename(f)[:-4]; lines = [l.strip() for l in open(os.path.join(LABDIR, sid + '.txt'))]
    comm = next((l.split(':', 1)[1].strip() for l in lines if l.startswith('# community:')), 'older')
    picked = {l for l in lines if l and not l.startswith('#')}
    rows_ = [r for r in csv.DictReader(open(f)) if f"{sid}/{r['name']}" in es]
    if len(rows_) >= int(os.environ.get('MIN_PHOTOS', '30')) and 3 <= len(picked) < len(rows_): sets[sid] = (comm, rows_, picked)
def run(sid, models_):
    comm, t, picked = sets[sid]
    E = np.array([es[f"{sid}/{r['name']}"] for r in t]); E /= np.linalg.norm(E, axis=1, keepdims=True)
    cnt = collections.Counter(p for r in t for p in set(r['who'].split(';')) - {''}); cpl = {p for p, _ in cnt.most_common(2)}
    for i, r in enumerate(t):
        r['i'] = i; r['ok'] = float(r['usable']) > 0.5; r['f'] = np.array([float(r[k]) for k in feat])
    old = np.array([float(np.concatenate([(r['f'] - mu) / sd, E[r['i']] @ Vt[:64].T]) @ fw) for r in t]); dist = E @ wd[:-1] + wd[-1]
    fr = (old - old.mean()) / (old.std() + 1e-9) + (dist - dist.mean()) / (dist.std() + 1e-9)
    worth = {m: float(judge(x[None], models_)[0]) / 2 for (ev, m), (x, rs) in folder_moments(t, E, cpl).items()}
    mom = collections.defaultdict(list)
    for r in t: mom[r['moment']].append(r)
    room = {}
    for m, rs in mom.items():
        a, b = None, -1
        for r in sorted(rs, key=lambda r: int(r['order'])):
            if a is None or float(E[r['i']] @ a) < BEAT_T: a, b = E[r['i']], b + 1
            r['beat'] = b
        room[m] = np.sqrt(b + 1)
    chosen, count, taken = [], collections.Counter(), set()
    def best(m):
        c = [r for r in mom[m] if r['ok'] and r['i'] not in taken]; inm = [x for x in chosen if x['moment'] == m]
        if inm:
            c = [r for r in c if max(float(E[x['i']] @ E[r['i']]) for x in inm) < 0.92]; used = {x['beat'] for x in inm}
            c = [r for r in c if r['beat'] not in used] or c
        return max(c, key=lambda r: fr[r['i']], default=None)
    while len(chosen) < len(picked):
        b, bv = None, -1
        for m in mom:
            v = worth[m] * DECAY ** (count[m] / room[m])
            if v > bv:
                r = best(m)
                if r is not None: b, bv = r, v
        if b is None: break
        chosen.append(b); taken.add(b['i']); count[b['moment']] += 1
    human = [r for r in t if r['name'] in picked]
    def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
    def score(app): return (sum(any(h is a for a in app) for h in human) / len(human), sum(any(same(h, a) for a in app) for h in human) / len(human))
    us = [r for r in t if r['ok']]
    rnd = np.mean([score(random.Random(i).sample(us, min(len(picked), len(us)))) for i in range(20)], axis=0)
    return score(chosen), rnd, len(t), len(human)
# ---- Other occasions: trained on weddings only (wedding-2 and the wedding web sets), tested on birthdays,
# child functions, trips and other events. The lessons are rebuilt per set so any set can be left out.
import subprocess, json as _json
occ = {}
try:
    for l in subprocess.run(['git', 'show', 'origin/harvest-data:sets.jsonl'], capture_output=True, text=True, cwd=os.path.dirname(os.path.abspath(__file__))).stdout.splitlines():
        d = _json.loads(l); occ[d['id']] = d['occasion']
except Exception: pass
n_w2 = int((comm2 == 'marathi-hindu').sum())
lessons = {}
for sid, (comm, t, picked) in sets.items():
    E = np.array([es[f"{sid}/{r['name']}"] for r in t])
    cnt = collections.Counter(p for r in t for p in set(r['who'].split(';')) - {''}); cpl = {p for p, _ in cnt.most_common(2)}
    xy = [(x, min(2, sum(r['name'] in picked for r in rs))) for (ev, m), (x, rs) in folder_moments([dict(r) for r in t], E, cpl).items()]
    lessons[sid] = (np.array([a for a, _ in xy]), np.array([b for _, b in xy]))
def train(leave):
    X = np.vstack([X2[:n_w2]] + [lessons[s][0] for s in sets if s not in leave]); y = np.r_[y2[:n_w2], np.concatenate([lessons[s][1] for s in sets if s not in leave])]
    Xn_ = (X - xm) / xs
    return [fit(Xn_, (y >= 1).astype(float)), fit(Xn_, (y >= 2).astype(float))]
if occ:
    wed = {'WEDDING', 'ENGAGEMENT'}; other = [s for s in sets if occ.get(s) not in wed and s in occ]
    ms = train(set(other))
    print('occasion (trained on weddings only) | sets | Claude picked | same photo: app / random | same moment: app / random')
    for o in sorted({occ[s] for s in other}) + ['ALL OTHER OCCASIONS']:
        res = [run(s, ms) for s in other if o.startswith('ALL') or occ[s] == o]
        w = np.array([r[3] for r in res], float); av = lambda f: float(np.sum([f(r) * r[3] for r in res]) / w.sum())
        print(f"{o:20s} | {len(res):2d} | {int(w.sum()):4d} | {av(lambda r: r[0][0]):.0%} / {av(lambda r: r[1][0]):.0%} | {av(lambda r: r[0][1]):.0%} / {av(lambda r: r[1][1]):.0%}")
if os.environ.get('ONLY') == 'occasions': sys.exit(0)
Xn = (X2 - xm) / xs
seen_models = [fit(Xn, (y2 >= 1).astype(float)), fit(Xn, (y2 >= 2).astype(float))]
print('community | sets | photos | Claude picked | same photo: never seen / seen / random | same moment: never seen / seen / random')
tot = collections.defaultdict(list)
for c in sorted({v[0] for v in sets.values()}):
    tr = comm2 != c
    ms = [fit(Xn[tr], (y2[tr] >= 1).astype(float)), fit(Xn[tr], (y2[tr] >= 2).astype(float))]
    res = [(run(s, ms), run(s, seen_models)) for s in sets if sets[s][0] == c]
    w = np.array([r[0][3] for r in res], float); avg = lambda f: float(np.sum([f(r) * r[0][3] for r in res]) / w.sum())
    row = (avg(lambda r: r[0][0][0]), avg(lambda r: r[1][0][0]), avg(lambda r: r[0][1][0]), avg(lambda r: r[0][0][1]), avg(lambda r: r[1][0][1]), avg(lambda r: r[0][1][1]))
    print(f"{c:12s} | {len(res):2d} | {sum(r[0][2] for r in res):5d} | {int(w.sum()):4d} | {row[0]:.0%} / {row[1]:.0%} / {row[2]:.0%} | {row[3]:.0%} / {row[4]:.0%} / {row[5]:.0%}")
    for r in res: tot['all'].append((r[0][3],) + (r[0][0][0], r[1][0][0], r[0][1][0], r[0][0][1], r[1][0][1], r[0][1][1]))
a = np.array(tot['all']); w = a[:, 0]; m = (a[:, 1:] * w[:, None]).sum(0) / w.sum()
print(f"{'ALL':12s} | {len(a):2d} |       | {int(w.sum()):4d} | {m[0]:.0%} / {m[1]:.0%} / {m[2]:.0%} | {m[3]:.0%} / {m[4]:.0%} / {m[5]:.0%}")
