"""Conscious selection, prototype. python3 -I story.py S SIZE [param=value ...]
1 read the day into scenes (time gap, change of look, change of people); 2 know who matters (couple,
close family); 3 weigh each scene (best frame as the seeing judges it, who is in it); 4 choose the
story, most meaningful scenes first, a second distinct view for big scenes; 5 the best frame of each;
6 check everyone important is with the couple; 7 keep the reason. Scored on the couple's album:
design events (engagement, haldi warna, mehendi) and the wedding day (kept untouched)."""
import csv, sys, math, collections
import numpy as np
S, SIZE = sys.argv[1], int(sys.argv[2])
P = dict(gap=90, look=0.80, people=0.3, couple=0.6, family=0.25, length=0.05, second=0.5, distinct=0.90, cover=1)
for a in sys.argv[3:]: k, v = a.split('='); P[k] = float(v)
t = list(csv.DictReader(open(S + '/reasoner/w1-features.csv')))
zt = np.load(S + '/reasoner/w1.npz'); temb = dict(zip(zt['keys'], zt['vecs'].astype(np.float32)))
E = np.array([temb[r['name'].rsplit('.', 1)[0] + '.jpg'] for r in t])
judge = {l.split('\t')[0]: float(l.split('\t')[1]) for l in open(S + '/reasoner/w1-appeal-all.tsv')}
for i, r in enumerate(t):
    r['i'] = i; r['score'] = judge[r['name']]; r['ok'] = float(r['usable']) > 0.5
    r['who'] = set(r['who'].split(';')) - {''}; r['fam'] = set(r['family'].split(';')) - {''}
bride, groom = '1', '2'
# 1. Scenes.
scenes = []
for ev in dict.fromkeys(r['event'] for r in t):
    rs = sorted((r for r in t if r['event'] == ev), key=lambda r: (int(r['taken']), int(r['order'])))
    cur = []
    for r in rs:
        if cur:
            last = cur[-1]; centre = E[[x['i'] for x in cur]].mean(0); centre /= np.linalg.norm(centre)
            gap = (int(r['taken']) - int(last['taken'])) / 1000
            look = float(E[r['i']] @ centre)
            pa, pb = r['who'], set().union(*(x['who'] for x in cur[-3:]))
            jac = len(pa & pb) / len(pa | pb) if pa and pb else 1.0
            if gap > P['gap'] or look < P['look'] or jac < P['people']:
                scenes.append(cur); cur = []
        cur.append(r)
    if cur: scenes.append(cur)
# 3. Weigh each scene.
def weight(sc):
    good = [r for r in sc if r['ok']] or sc
    best = max(good, key=lambda r: r['score'])
    who = set().union(*(r['who'] for r in sc)); fam = set().union(*(r['fam'] for r in sc)) - {bride, groom}
    couple = (bride in who) + (groom in who)
    return best['score'] * (1 + P['couple'] * couple + P['family'] * min(3, len(fam))) + P['length'] * math.log(1 + len(sc)), best
heap = []
for k, sc in enumerate(scenes):
    w, best = weight(sc); heap.append((w, k, best, 'first'))
# 4-5. Choose the story.
heap.sort(key=lambda x: -x[0]); chosen = []; taken = set(); per_scene = collections.Counter(); reasons = {}
queue = list(heap)
while queue and len(chosen) < SIZE:
    w, k, r, why = queue.pop(0)
    if r['i'] in taken: continue
    if chosen and float(np.max(E[[c['i'] for c in chosen]] @ E[r['i']])) > 0.97: continue
    chosen.append(r); taken.add(r['i']); per_scene[k] += 1; reasons[r['i']] = why
    sc = scenes[k]
    if per_scene[k] < 3 and len(sc) >= 6:
        # A second, different view of a big scene (wide and close), worth less.
        others = [x for x in sc if x['ok'] and x['i'] not in taken and float(np.max(E[[c['i'] for c in chosen if c in sc]] @ E[x['i']])) < P['distinct']]
        if others:
            nxt = max(others, key=lambda x: x['score'])
            queue.append((w * P['second'] ** per_scene[k], k, nxt, 'another view')); queue.sort(key=lambda x: -x[0])
# 6. Everyone important with the couple at least once.
if P['cover']:
    fam_all = set().union(*(r['fam'] for r in t)) - {bride, groom}
    for p in sorted(fam_all):
        if any(p in c['who'] and (bride in c['who'] or groom in c['who']) for c in chosen): continue
        cands = [r for r in t if r['ok'] and p in r['who'] and (bride in r['who'] or groom in r['who']) and r['i'] not in taken]
        if cands and len(chosen) >= SIZE:
            worst = min((c for c in chosen if reasons[c['i']] == 'another view'), key=lambda c: c['score'], default=None)
            if worst is None: continue
            chosen.remove(worst); taken.discard(worst['i'])
        if cands:
            b = max(cands, key=lambda r: r['score']); chosen.append(b); taken.add(b['i']); reasons[b['i']] = 'family with the couple'
couple = {l.strip() for l in open(S + '/w1album/album/album-picks.txt') if l.strip()}
def same(a, c): return a is c or (int(a['repeat']) >= 0 and a['repeat'] == c['repeat']) or (a['moment'] == c['moment'] and int(a['moment']) >= 0 and abs(int(a['order']) - int(c['order'])) <= 3)
out = []
for name, evs in (('design', {'Engagement', 'Haldi Warna', 'Mehendi'}), ('wedding day', {'Haldi Wedd Photos'})):
    human = [r for r in t if r['name'] in couple and r['event'] in evs]; app = [c for c in chosen if c['event'] in evs]
    mo = sum(any(same(h, a) for a in app) for h in human); ex = sum(h in app for h in human)
    out.append(f"{name}: {len(app)} picked, couple's moments {mo}/{len(human)} = {mo / len(human):.1%}, exact {ex / len(human):.1%}")
print(f"{len(scenes)} scenes | " + ' | '.join(out))
