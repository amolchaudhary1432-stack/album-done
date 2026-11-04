"""Frame-against-frame sheets for teaching at full size. For each near-twin group (tools/reasoner/
twin-groups.json: set, photos), fetch the photos at 1280 px from Wikimedia Commons (CC licences; the
harvest-data credits give each file's page), politely, until DEADLINE seconds, then lay each complete
group side by side (A, B, C, D), large enough to judge eyes and expressions.
    python3 teach_sheets.py GROUPS.json CREDITS_DIR OUT_DIR"""
import json, os, re, sys, time, urllib.error, urllib.request
from PIL import Image, ImageDraw, ImageFont, ImageOps
groups, credits, out = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
deadline = time.time() + int(os.environ.get('DEADLINE', '5400'))
UA = {'User-Agent': 'AlbumDoneTestBot/1.1 (https://github.com/amolchaudhary1432-stack/album-done; freely licensed photos only)'}
os.makedirs(os.path.join(out, 'photos'), exist_ok=True)
cred = {}
for g in groups:
    if g['set'] not in cred:
        c = json.load(open(os.path.join(credits, g['set'] + '.credits.json')))
        cred[g['set']] = {f['file']: f for f in c.get('files', [])}
pause_until = 0
def fetch(sid, name):
    global pause_until
    dst = os.path.join(out, 'photos', sid, name)
    if os.path.exists(dst): return True
    f = cred[sid].get(name)
    if not f: return False
    url = re.sub(r'\?.*$', '', f['thumb']).replace('thumb.wikimedia.org', 'upload.wikimedia.org')
    for t in range(4):
        if time.time() > deadline: return False
        time.sleep(max(0, pause_until - time.time()))
        try:
            data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()
            os.makedirs(os.path.dirname(dst), exist_ok=True); open(dst, 'wb').write(data); time.sleep(1.5); return True
        except urllib.error.HTTPError as e:
            if e.code in (429, 503): pause_until = time.time() + 30 * (t + 1); continue
            return False
        except Exception: time.sleep(5)
    return False
font = ImageFont.load_default(size=28)
index, done = [], 0
for g in groups:
    if time.time() > deadline: break
    ok = all(fetch(g['set'], n) for n in g['photos'])
    if not ok: continue
    ims = [ImageOps.exif_transpose(Image.open(os.path.join(out, 'photos', g['set'], n))).convert('RGB') for n in g['photos']]
    for im in ims: im.thumbnail((640, 640))
    W = sum(im.width for im in ims) + 20 * (len(ims) + 1); H = max(im.height for im in ims) + 70
    sheet = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(sheet); x = 20
    for k, im in enumerate(ims):
        sheet.paste(im, (x, 60)); d.text((x, 14), 'ABCD'[k], fill='black', font=font); x += im.width + 20
    sheet.save(os.path.join(out, g['id'] + '.jpg'), quality=85)
    index.append({'id': g['id'], 'set': g['set'], 'split': g['split'], 'photos': g['photos']}); done += 1
json.dump(index, open(os.path.join(out, 'index.json'), 'w'), indent=0)
print(done, 'of', len(groups), 'groups made into sheets')
