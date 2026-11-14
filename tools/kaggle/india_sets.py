"""Kaggle CPU: the Indian sets among the web photos the caption scans found (Flickr CC BY, CommonCatalog),
laid out for Claude to judge photo by photo in its own words: the teaching material for communities other
than the two real folders. A set = one photographer, one day (like one event's folder). Chosen: sets Claude
already called Indian by eye, and sets of 4+ photos whose place or words say India. Inputs: the earlier
scan jobs' outputs (cc.jsonl + cc_img/). Output: india.jsonl (one line per photo, numbered, with credit),
sheets/Q###.jpg (12 numbered photos a sheet, set by set, in time order), india_img/ (the photos, for the
training job)."""
import glob, json, os, re, shutil, collections, urllib.parse
from PIL import Image, ImageDraw, ImageFont
KNOWN = {tuple(k.split('|')[:2]): k.split('|')[2] for k in """66401486@N00|2008-01-16|karnataka
58435577@N00|2007-01-03|telangana
25692853@N00|2006-05-18|kerala
59769174@N00|2005-11-19|diaspora
43541636@N00|2006-08-20|bengali
94324017@N00|2005-11-19|gujarati-punjabi
59769174@N00|2007-06-09|diaspora
59769174@N00|2005-11-18|diaspora
58435577@N00|2007-01-02|telangana
75752531@N00|2006-09-02|diaspora
88078580@N00|2008-01-25|tamil
7858857@N04|2008-09-20|diaspora
17352537@N00|2006-10-10|tamil
85378079@N00|2005-05-01|diaspora
27663349@N07|2007-05-01|north
32662631@N00|2005-12-11|south
19364701@N00|2006-07-12|bengali
25692853@N00|2006-05-17|kerala
84858864@N00|2007-04-14|diaspora
27669614@N00|2007-03-04|gujarati
63669472@N00|2007-08-25|gujarati
66401486@N00|2008-01-14|karnataka
13585614@N06|2007-09-16|north
89324753@N00|2006-08-24|karnataka
29172291@N00|2005-05-02|tamil
50959196@N00|2005-07-08|diaspora
17352537@N00|2006-10-11|tamil
75374840@N00|2006-12-12|north
75374840@N00|2006-12-11|north
77727541@N00|2008-02-03|tamil
88078580@N00|2008-01-24|tamil
31465324@N00|2006-12-24|south
58575431@N00|2005-01-01|north
74698556@N00|2003-07-05|diaspora
94324017@N00|2006-05-27|diaspora
71509163@N00|2007-01-26|karnataka
84858864@N00|2007-04-15|diaspora
51035723793@N01|2007-10-27|north
51035566865@N01|2008-01-24|north
74507678@N00|2007-11-05|punjabi
36323312@N00|2008-03-09|tamil
53908695@N00|2008-12-21|punjabi""".split()}
WORDS = re.compile(r'\bindia\b|indian', re.I)
def in_india(r):
    try: la, lo = float(r.get('latitude')), float(r.get('longitude'))
    except Exception: return False
    return 6 < la < 36 and 68 < lo < 97.5
rows = {}
for f in glob.glob('/kaggle/input/**/cc.jsonl', recursive=True):
    d = os.path.dirname(f)
    for l in open(f):
        x = json.loads(l); p = f"{d}/cc_img/{x.get('photoid')}.jpg"
        if os.path.exists(p): x['_path'] = p; rows[str(x.get('photoid'))] = x
print(len(rows), 'photos with their picture', flush=True)
sets = collections.defaultdict(list)
for r in rows.values(): sets[(str(r.get('uid') or r.get('unickname')), str(r.get('datetaken'))[:10])].append(r)
def text(v): return ' '.join(urllib.parse.unquote_plus(str(r.get('title') or '')) + ' ' + str(r.get('usertags') or '') for r in v)
chosen = []
for k, v in sets.items():
    if k in KNOWN or (len(v) >= 4 and (any(in_india(r) for r in v) or WORDS.search(text(v)))):
        chosen.append((0 if k in KNOWN else 1, -len(v), k, sorted(v, key=lambda r: (str(r.get('datetaken')), str(r.get('photoid'))))))
chosen.sort(key=lambda t: t[:3])
print(len(chosen), 'sets,', sum(len(c[3]) for c in chosen), 'photos', flush=True)
os.makedirs('sheets', exist_ok=True); os.makedirs('india_img', exist_ok=True)
try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
except Exception: font = ImageFont.load_default()
queue = []
for si, (_, _, k, v) in enumerate(chosen):
    for r in v[:150]:  # a very long set gives its first 150
        queue.append({'n': len(queue) + 1, 'set': f'W{si:03d}', 'owner': k[0], 'day': k[1], 'known': KNOWN.get(k, ''), 'photo': str(r.get('photoid')),
                      'title': urllib.parse.unquote_plus(str(r.get('title') or ''))[:80], 'tags': str(r.get('usertags') or '')[:200],
                      'lat': r.get('latitude'), 'lon': r.get('longitude'), 'taken': str(r.get('datetaken')), 'by': urllib.parse.unquote_plus(str(r.get('unickname') or '')),
                      'page': r.get('pageurl'), 'licence': r.get('licenseurl'), '_path': r['_path']})
T = 400; COLS, ROWS = 4, 3; PER = COLS * ROWS
for s in range(0, len(queue), PER):
    part = queue[s:s + PER]; sheet = Image.new('RGB', (COLS * T, ROWS * (T + 52)), 'white'); dr = ImageDraw.Draw(sheet)
    for i, q in enumerate(part):
        try: im = Image.open(q['_path']).convert('RGB')
        except Exception: continue
        im.thumbnail((T, T)); x = (i % COLS) * T; y = (i // COLS) * (T + 52)
        sheet.paste(im, (x + (T - im.width) // 2, y + 52 + (T - im.height) // 2))
        dr.text((x + 6, y + 2), f"{q['n']}  {q['set']} {q['known']}", fill='black', font=font); dr.text((x + 6, y + 26), q['title'][:34], fill=(90, 90, 90), font=font)
    sheet.save(f'sheets/Q{s // PER + 1:03d}.jpg', quality=82)
with open('india.jsonl', 'w') as w:
    for q in queue:
        shutil.copy(q.pop('_path'), f"india_img/{q['photo']}.jpg"); w.write(json.dumps(q, default=str) + '\n')
print('done:', len(queue), 'photos on', (len(queue) + PER - 1) // PER, 'sheets')
