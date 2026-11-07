"""Kaggle CPU: find Indian / South Asian wedding photos of every religion in CommonCatalog (every
Creative Commons Flickr photo with captions; the CC BY and CC BY-SA parts are free for a paid app).
Only the caption columns are read from each file (no photo download), then the matching photos'
bytes from the same files. Output, in the working folder:
  cc.jsonl   one line per photo found: collection, file, row, photoid, owner, date taken, licence,
             captions, which words matched
  cc_img/    640 px copies of the found photos (for SigLIP and the eyes next)
Stops before Kaggle's time limit and keeps what it has; FILES_DONE lets the next run carry on.
"""
import json, os, re, subprocess, sys, time, io
START = time.time(); LIMIT = float(os.environ.get('LIMIT_H', '11.3')) * 3600
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'huggingface_hub', 'pyarrow', 'fsspec'], check=True)
import pyarrow.parquet as pq
from huggingface_hub import HfFileSystem
from PIL import Image

SOUTH_ASIA = (r"indian|india|hindu|sikh|punjabi|bengali|bangla|tamil|telugu|kannada|kerala|malayali|gujarati|marathi|"
              r"maharashtrian|rajasthani|kashmiri|bihari|odia|assamese|goan|parsi|jain|pakistani|pakistan|bangladeshi|"
              r"bangladesh|nepali|nepal|sri lankan|sri lanka|desi|south asian|bollywood|muslim|nikah|walima|mehndi|mehendi|"
              r"mehandi|henna|sari|saree|lehenga|lehnga|sherwani|turban|pagri|mandap|baraat|barat|sangeet|haldi|gurdwara|"
              r"gurudwara|anand karaj|kirpan|bindi|sindoor|mangalsutra|garland|varmala|jaimala|shaadi|shadi|vivah|gaye holud|holud")
WEDDING = r"wedding|bride|bridal|groom|marriage|married|ceremony|reception|engagement|nuptial|shaadi|shadi|vivah|nikah|walima|baraat|sangeet|mehndi|mehendi|haldi|anand karaj|gaye holud"
SA = re.compile(r"\b(" + SOUTH_ASIA + r")\b", re.I); WED = re.compile(r"\b(" + WEDDING + r")", re.I)
fs = HfFileSystem()
done_path = 'files_done.txt'; done = set(open(done_path).read().split()) if os.path.exists(done_path) else set()
import glob as _g
for prev in _g.glob('/kaggle/input/**/files_done*.txt', recursive=True): done |= set(open(prev).read().split())
SHARD = os.environ.get('SHARD', '0/1'); _i, _n = (int(x) for x in SHARD.split('/'))
print('already scanned:', len(done), 'files; shard', SHARD, flush=True)
os.makedirs('cc_img', exist_ok=True)
out = open('cc.jsonl', 'a'); n_rows = n_hit = n_files = 0
for coll in ('common-canvas/commoncatalog-cc-by', 'common-canvas/commoncatalog-cc-by-sa'):
    files = sorted(f for f in fs.glob(f'datasets/{coll}/**/*.parquet'))
    print(coll, len(files), 'files', flush=True)
    for k_, f in enumerate(files):
        if f in done or k_ % _n != _i: continue
        if time.time() - START > LIMIT: break
        try:
            pf = pq.ParquetFile(fs.open(f, 'rb', block_size=2 ** 20))
            cols = [c for c in pf.schema_arrow.names if c != 'jpg']
            for g in range(pf.num_row_groups):
                t = pf.read_row_group(g, columns=cols).to_pylist()
                n_rows += len(t)
                hits = []
                for k, r in enumerate(t):
                    text = ' '.join(str(r.get(c) or '') for c in ('caption', 'blip2_caption', 'title', 'description', 'usertags', 'tags'))
                    a, w = SA.findall(text), WED.findall(text)
                    if a and w: hits.append((k, r, sorted({x.lower() for x in a + w})))
                if not hits: continue
                imgs = pf.read_row_group(g, columns=['jpg']).column('jpg').to_pylist()
                for k, r, words in hits:
                    pid = str(r.get('photoid') or f'{n_files}-{g}-{k}')
                    try:
                        b = imgs[k]['bytes'] if isinstance(imgs[k], dict) else imgs[k]
                        im = Image.open(io.BytesIO(b)).convert('RGB'); im.thumbnail((640, 640)); im.save(f'cc_img/{pid}.jpg', quality=88)
                    except Exception:
                        continue
                    keep = {c: r.get(c) for c in cols if not isinstance(r.get(c), (bytes, bytearray))}
                    out.write(json.dumps({'collection': coll, 'file': f, 'group': g, 'row': k, 'words': words, **keep}, default=str) + '\n')
                    n_hit += 1
            out.flush()
            done.add(f); open(done_path, 'a').write(f + '\n'); n_files += 1
            if n_files % 50 == 0:
                print(f'{n_files} files, {n_rows} photos read, {n_hit} Indian/South Asian wedding photos found, {round(time.time() - START)} s', flush=True)
        except Exception as e:
            print('skip', f, e, flush=True)
print(f'done: {n_files} files, {n_rows} photos read, {n_hit} found, {round(time.time() - START)} s')
