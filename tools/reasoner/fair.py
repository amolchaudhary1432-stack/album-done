"""The couple's photos caught by a list: same moment (as AlbumScore), or a look-alike of 0.92 or more
in the same event (the same scene to a person). python3 -I fair.py S LIST [LIST ...]"""
import sys, csv, numpy as np
S = sys.argv[1]
t = list(csv.DictReader(open(S + '/reasoner/w1-features.csv'))); by = {r['name']: r for r in t}
zt = np.load(S + '/reasoner/w1.npz'); temb = dict(zip(zt['keys'], zt['vecs'].astype(np.float32)))
for i, r in enumerate(t): r['e'] = temb[r['name'].rsplit('.', 1)[0] + '.jpg']
couple = [by[l.strip()] for l in open(S + '/w1album/album/album-picks.txt') if l.strip() in by]
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
for f in sys.argv[2:]:
    app = [by[l.strip()] for l in open(f) if l.strip() in by]
    res = []
    for name, evs in (('design', {'Engagement', 'Haldi Warna', 'Mehendi'}), ('wedding day', {'Haldi Wedd Photos'}), ('all', None)):
        h = [c for c in couple if evs is None or c['event'] in evs]; a = [x for x in app if evs is None or x['event'] in evs]
        m = sum(any(same(x, y) for y in a) for x in h)
        sc = sum(any(same(x, y) or (x['event'] == y['event'] and float(x['e'] @ y['e']) >= 0.92) for y in a) for x in h)
        res.append(f"{name}: moment {m / len(h):.1%}, scene {sc / len(h):.1%}")
    print(f"{f.split('/')[-1]} ({len(app)}): " + ' | '.join(res))
