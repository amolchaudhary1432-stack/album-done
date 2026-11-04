"""Kaggle CPU: Indian weddings of every religion in Megalith-10M (Flickr CC0 / public domain) and PD12M
(public domain), by their captions; then the matching photos downloaded from their links (640 px).
Output: mp.jsonl (one line per photo found: collection, link, caption, matched words) and mp_img/."""
import io, json, os, re, subprocess, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor
START = time.time(); LIMIT = float(os.environ.get('LIMIT_H', '11.3')) * 3600
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'huggingface_hub', 'pyarrow', 'fsspec'], check=True)
import pyarrow.parquet as pq
from huggingface_hub import HfFileSystem
from PIL import Image
SA = re.compile(r"\b(indian|india|hindu|sikh|punjabi|bengali|tamil|telugu|kannada|kerala|malayali|gujarati|marathi|maharashtrian|"
                r"rajasthani|kashmiri|bihari|odia|assamese|goan|parsi|jain|desi|bollywood|muslim|nikah|walima|mehndi|mehendi|henna|"
                r"sari|saree|lehenga|sherwani|turban|mandap|baraat|sangeet|haldi|gurdwara|anand karaj|bindi|sindoor|mangalsutra|"
                r"garland|varmala|shaadi|vivah|south asian)\b", re.I)
WED = re.compile(r"\b(wedding|bride|bridal|groom|marriage|married|ceremony|reception|engagement|nuptial|shaadi|vivah|nikah|"
                 r"walima|baraat|sangeet|mehndi|mehendi|haldi|anand karaj)", re.I)
fs = HfFileSystem(); out = open('mp.jsonl', 'a'); found = []
for coll in ('madebyollin/megalith-10m', 'Spawning/PD12M'):
    files = sorted(fs.glob(f'datasets/{coll}/**/*.parquet'))
    print(coll, len(files), 'files', flush=True)
    for f in files:
        if time.time() - START > LIMIT * 0.6: break
        try:
            pf = pq.ParquetFile(fs.open(f, 'rb', block_size=2 ** 20))
            names = pf.schema_arrow.names
            cols = [c for c in names if any(k in c.lower() for k in ('url', 'caption', 'title', 'text', 'tag', 'desc', 'license', 'id'))]
            for g in range(pf.num_row_groups):
                for r in pf.read_row_group(g, columns=cols).to_pylist():
                    text = ' '.join(str(v) for k, v in r.items() if 'url' not in k.lower() and v)
                    a, w = SA.findall(text), WED.findall(text)
                    if a and w:
                        url = next((v for k, v in r.items() if 'url' in k.lower() and isinstance(v, str) and v.startswith('http')), None)
                        if url:
                            d = {'collection': coll, 'url': url, 'words': sorted({x.lower() for x in a + w}), **{k: v for k, v in r.items() if isinstance(v, (str, int, float))}}
                            out.write(json.dumps(d, default=str) + '\n'); found.append(d)
            out.flush()
            print(f, len(found), 'found so far', round(time.time() - START), 's', flush=True)
        except Exception as e:
            print('skip', f, e, flush=True)
os.makedirs('mp_img', exist_ok=True)
def get(k_d):
    k, d = k_d
    try:
        data = urllib.request.urlopen(urllib.request.Request(d['url'], headers={'User-Agent': 'AlbumDoneResearch/1.0'}), timeout=30).read()
        im = Image.open(io.BytesIO(data)).convert('RGB'); im.thumbnail((640, 640)); im.save(f'mp_img/{k:06d}.jpg', quality=88); return 1
    except Exception:
        return 0
with ThreadPoolExecutor(16) as ex: ok = sum(ex.map(get, enumerate(found)))
print('done:', len(found), 'found,', ok, 'downloaded,', round(time.time() - START), 's')
