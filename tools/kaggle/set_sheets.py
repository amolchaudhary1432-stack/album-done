"""Kaggle CPU: group the photos the caption scans found into sets (one photographer, one day: like a
folder from one event) and lay each set out on one sheet with its title, place and caption words, so
Claude can decide by eye, set by set, whether it is an Indian wedding and which community (a single
photo cannot say: SigLIP mistakes Malay weddings for Indian ones). Inputs: earlier jobs' outputs
(cc_img/ + cc.jsonl, mp_img/ + mp.jsonl). Output: sheets/<n>.jpg and sets.json."""
import glob, json, os, collections, urllib.parse
from PIL import Image, ImageDraw
items = []
for f in glob.glob('/kaggle/input/**/cc.jsonl', recursive=True):
    d = os.path.dirname(f)
    for l in open(f):
        x = json.loads(l); p = f"{d}/cc_img/{x.get('photoid')}.jpg"
        if os.path.exists(p): items.append({'path': p, 'owner': str(x.get('uid') or x.get('unickname')), 'name': urllib.parse.unquote_plus(str(x.get('unickname') or '')),
                                             'day': str(x.get('datetaken'))[:10], 'title': urllib.parse.unquote_plus(str(x.get('title') or '')),
                                             'lat': x.get('latitude'), 'lon': x.get('longitude'), 'words': x.get('words', []), 'key': f"cc/{x.get('photoid')}"})
for f in glob.glob('/kaggle/input/**/mp.jsonl', recursive=True):
    d = os.path.dirname(f)
    for k, l in enumerate(open(f)):
        x = json.loads(l); p = f"{d}/mp_img/{k:06d}.jpg"
        if os.path.exists(p): items.append({'path': p, 'owner': x.get('url', '').split('/')[2] if x.get('url') else 'mp', 'name': x.get('collection', ''), 'day': '',
                                             'title': str(x.get('caption') or '')[:80], 'lat': None, 'lon': None, 'words': x.get('words', []), 'key': f"mp/{k:06d}"})
sets = collections.defaultdict(list)
for it in items: sets[(it['owner'], it['day'])].append(it)
order = sorted(sets.items(), key=lambda kv: -len(kv[1]))
os.makedirs('sheets', exist_ok=True); out = []
for n, ((owner, day), its) in enumerate(order):
    if len(its) < 2: continue
    th = 200; W = 6; shown = its[:24]; rows = (len(shown) + W - 1) // W
    sheet = Image.new('RGB', (W * th, rows * th + 70), 'white'); dr = ImageDraw.Draw(sheet)
    words = collections.Counter(w for i in its for w in i['words']).most_common(6)
    geo = next(((i['lat'], i['lon']) for i in its if i['lat'] not in (None, '', 0, '0')), None)
    dr.text((6, 4), f"S{n:03d}  {its[0]['name'][:30]}  {day}  {len(its)} photos  place {geo}", fill='black')
    dr.text((6, 24), f"title: {its[0]['title'][:110]}", fill='black'); dr.text((6, 44), f"words: {words}", fill='black')
    for k, i in enumerate(shown):
        im = Image.open(i['path']).convert('RGB'); im.thumbnail((th - 4, th - 4)); sheet.paste(im, ((k % W) * th, 70 + (k // W) * th))
    sheet.save(f'sheets/S{n:03d}.jpg', quality=72)
    out.append({'set': f'S{n:03d}', 'owner': owner, 'name': its[0]['name'], 'day': day, 'photos': [i['key'] for i in its], 'title': its[0]['title'], 'place': geo})
json.dump(out, open('sets.json', 'w'), indent=0)
print(len(items), 'photos,', len(out), 'sets of 2+ photos laid out')
