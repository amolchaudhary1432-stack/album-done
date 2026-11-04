"""Kaggle: find South Asian wedding photos in Google's Open Images (9M Flickr photos, CC BY 2.0, with
human-verified image labels), for teaching the brain. No photos of real clients, only open data.

1. Human labels (train, validation, test) streamed: images labelled Wedding, Wedding reception, Bride,
   Bridegroom, Mehndi, Henna, Sari, Bridal clothing, Ceremony, Wedding dress (confidence 1).
2. Their metadata (author, title, licence, thumbnail URL).
3. Each thumbnail seen by SigLIP 2 (same int8 ONNX as our tools): image vector, and zero-shot scores
   against South Asian vs other weddings, so the community can be told by looking, not by tags.
Output: oi.jsonl (one line per photo found) and oi.npz (keys, siglip) in the working folder.
"""
import csv, io, json, os, subprocess, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor
START = time.time()
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'onnxruntime', 'huggingface_hub', 'tokenizers'], check=True)
import numpy as np
from PIL import Image

MIDS = {'/m/081hv': 'Wedding', '/m/09qd1': 'Wedding reception', '/m/0ltfs': 'Bride', '/m/0ds4250': 'Bridegroom',
        '/m/02mtw0': 'Mehndi', '/m/01p1pl': 'Henna', '/m/025dq0': 'Sari', '/m/0hgs7v4': 'Bridal clothing',
        '/m/016c4t': 'Ceremony', '/m/02c66t': 'Wedding dress'}
B = 'https://storage.googleapis.com/openimages'


def stream(url):
    req = urllib.request.urlopen(url, timeout=600)
    return io.TextIOWrapper(req, encoding='utf-8', newline='')


labels = {}
for f in ('v7/oidv7-train-annotations-human-imagelabels.csv', 'v7/oidv7-val-annotations-human-imagelabels.csv',
          'v7/oidv7-test-annotations-human-imagelabels.csv'):
    for t in range(3):
        try:
            for row in csv.reader(stream(f'{B}/{f}')):
                if len(row) >= 4 and row[2] in MIDS and row[3] == '1':
                    labels.setdefault(row[0], set()).add(MIDS[row[2]])
            break
        except Exception as e:
            print('retry', f, e, flush=True)
    print(f, len(labels), 'images so far', round(time.time() - START), 's', flush=True)

meta = {}
for f in ('v6/oidv6-train-images-with-labels-with-rotation.csv', 'v5/validation-images-with-rotation.csv', 'v5/test-images-with-rotation.csv'):
    try:
        for row in csv.DictReader(stream(f'{B}/{f}')):
            if row['ImageID'] in labels:
                meta[row['ImageID']] = {k: row.get(k, '') for k in ('OriginalURL', 'OriginalLandingURL', 'License', 'AuthorProfileURL', 'Author', 'Title', 'Thumbnail300KURL')}
    except Exception as e:
        print('meta failed', f, e, flush=True)
    print(f, len(meta), 'with metadata', round(time.time() - START), 's', flush=True)

ids = sorted(meta)
os.makedirs('/kaggle/tmp/oi', exist_ok=True)
UA = {'User-Agent': 'AlbumDoneResearch/1.0 (photo-selection research)'}


def thumb(i):
    p = f'/kaggle/tmp/oi/{i}.jpg'
    if os.path.exists(p): return p
    for url in (meta[i]['Thumbnail300KURL'], meta[i]['OriginalURL']):
        if not url: continue
        try:
            data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read()
            im = Image.open(io.BytesIO(data)).convert('RGB'); im.thumbnail((640, 640)); im.save(p, quality=90); return p
        except Exception:
            continue
    return None


with ThreadPoolExecutor(16) as ex: paths = list(ex.map(thumb, ids))
have = [(i, p) for i, p in zip(ids, paths) if p]
print(len(have), 'thumbnails of', len(ids), round(time.time() - START), 's', flush=True)

import onnxruntime as ort
from huggingface_hub import snapshot_download
from tokenizers import Tokenizer
sd = snapshot_download('onnx-community/siglip2-base-patch32-256-ONNX', allow_patterns=['onnx/vision_model_int8.onnx', 'onnx/text_model_int8.onnx', 'tokenizer.json'], local_dir='/kaggle/tmp/siglip')
vis = ort.InferenceSession(sd + '/onnx/vision_model_int8.onnx'); txt = ort.InferenceSession(sd + '/onnx/text_model_int8.onnx')
tok = Tokenizer.from_file(sd + '/tokenizer.json'); tok.enable_padding(length=64, pad_id=0); tok.enable_truncation(max_length=64)
PROMPTS = {'south_asian': ['a photo of an Indian wedding', 'a photo of a Hindu wedding ceremony', 'a photo of a Sikh wedding',
                           'a photo of a Pakistani or Indian Muslim wedding', 'a photo of a Bangladeshi wedding',
                           'a photo of a South Asian bride in a lehenga or sari', 'a photo of a mehndi ceremony'],
           'other': ['a photo of a Western white wedding', 'a photo of a Chinese wedding', 'a photo of an African wedding',
                     'a photo of a church wedding in Europe', 'a photo of a Japanese wedding', 'a photo of a Latin American wedding']}
names = [n for g in PROMPTS.values() for n in g]
enc = tok.encode_batch(names); t_ids = np.array([e.ids for e in enc], dtype=np.int64)
tv = txt.run(['pooler_output'], {'input_ids': t_ids})[0]; tv = tv / np.linalg.norm(tv, axis=1, keepdims=True)
vecs = []
for k in range(0, len(have), 32):
    b = [(np.asarray(Image.open(p).convert('RGB').resize((256, 256), Image.BICUBIC), dtype=np.float32) / 255.0 - 0.5) / 0.5 for _, p in have[k:k + 32]]
    v = vis.run(['pooler_output'], {'pixel_values': np.stack([x.transpose(2, 0, 1) for x in b])})[0]
    vecs.append(v / np.linalg.norm(v, axis=1, keepdims=True))
V = np.concatenate(vecs) if vecs else np.zeros((0, 768), np.float32)
sim = V @ tv.T
ns = len(PROMPTS['south_asian'])
sa = sim[:, :ns].max(1) - sim[:, ns:].max(1)
with open('oi.jsonl', 'w') as f:
    for (i, _), s in zip(have, sa):
        f.write(json.dumps({'id': i, 'labels': sorted(labels[i]), 'south_asian': round(float(s), 4), **meta[i]}) + '\n')
np.savez_compressed('oi.npz', keys=np.array([i for i, _ in have]), siglip=V.astype(np.float16))
print('done:', len(have), 'photos;', int((sa > 0).sum()), 'look South Asian;', round(time.time() - START), 's')
