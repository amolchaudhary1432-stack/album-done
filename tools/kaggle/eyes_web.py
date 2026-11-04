"""Kaggle GPU: the borrowed eyes look at every harvested web photo (freely licensed Wikimedia Commons),
so our own small frame judge can learn to see like them (distillation) and run on a phone.

Input: the album-done-weblist dataset (weblist.jsonl: set, file, url, occasion). For each photo:
SigLIP 2 B/32 (the same int8 ONNX the phone tools use, so the vectors match), Q-ReAlign Mini quality
and aesthetics, Intern-Decision 2B "how good for the album" and "would the family keep it".
Output: eyes.npz (keys, siglip, q_quality, q_aesthetic, i_score, i_keep) in the working folder.
Downloads politely (one at a time per worker, backs off on 429); stops before Kaggle's time limit.
"""
import glob, io, json, os, subprocess, sys, threading, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
START = time.time(); LIMIT = float(os.environ.get('LIMIT_H', '11')) * 3600
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==5.14.1', 'huggingface_hub', 'onnxruntime'], check=True)
import numpy as np, torch
from PIL import Image, ImageOps
from huggingface_hub import snapshot_download

lst = [json.loads(l) for l in open(glob.glob('/kaggle/input/**/weblist.jsonl', recursive=True)[0])]
print(len(lst), 'photos listed', flush=True)
IMG = '/kaggle/tmp/img'; os.makedirs(IMG, exist_ok=True)
UA = {'User-Agent': 'AlbumDoneResearch/1.0 (photo-selection research; Kaggle)'}
pause = [0.0]; lock = threading.Lock()


def fetch(item):
    dst = os.path.join(IMG, item['set'], item['file'])
    if os.path.exists(dst): return dst
    url = item['url'].split('?')[0].replace('/1280px-', '/960px-')
    for t in range(5):
        if time.time() - START > LIMIT * 0.45: return None
        with lock: wait = pause[0] - time.time()
        if wait > 0: time.sleep(wait)
        try:
            data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()
            im = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert('RGB'); im.thumbnail((960, 960))
            os.makedirs(os.path.dirname(dst), exist_ok=True); im.save(dst, quality=92); time.sleep(0.4); return dst
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):
                with lock: pause[0] = time.time() + 20 * (t + 1)
                continue
            if e.code == 404 and '/960px-' in url: url = item['url'].split('?')[0]; continue
            return None
        except Exception:
            time.sleep(3)
    return None


with ThreadPoolExecutor(3) as ex: paths = list(ex.map(fetch, lst))
have = [(it, p) for it, p in zip(lst, paths) if p]
print(len(have), 'downloaded in', round(time.time() - START), 's', flush=True)
keys = [f"{it['set']}/{it['file']}" for it, _ in have]

# SigLIP 2, exactly as tools/reasoner/embed.py does it.
import onnxruntime as ort
sd = snapshot_download('onnx-community/siglip2-base-patch32-256-ONNX', allow_patterns=['onnx/vision_model_int8.onnx'], local_dir='/kaggle/tmp/siglip')
sess = ort.InferenceSession(sd + '/onnx/vision_model_int8.onnx')
vecs = []
for i in range(0, len(have), 16):
    b = [(np.asarray(Image.open(p).convert('RGB').resize((256, 256), Image.BICUBIC), dtype=np.float32) / 255.0 - 0.5) / 0.5 for _, p in have[i:i + 16]]
    v = sess.run(['pooler_output'], {'pixel_values': np.stack([x.transpose(2, 0, 1) for x in b])})[0]
    vecs.append(v / np.linalg.norm(v, axis=1, keepdims=True))
sig = np.concatenate(vecs).astype(np.float16) if vecs else np.zeros((0, 768), np.float16)
print('siglip done', round(time.time() - START), flush=True)
small = []
for _, p in have:
    q = p.replace(IMG, '/kaggle/tmp/small')
    if not os.path.exists(q):
        os.makedirs(os.path.dirname(q), exist_ok=True); im = Image.open(p); im.thumbnail((640, 640)); im.save(q, quality=90)
    small.append(q)


def save(**cols):
    np.savez_compressed('eyes.npz', keys=np.array(keys), siglip=sig, **{k: np.array(v, np.float32) for k, v in cols.items()})


cols = {k: [np.nan] * len(have) for k in ('q_quality', 'q_aesthetic', 'i_score', 'i_keep')}
# Q-ReAlign Mini on GPU 0.
from transformers import AutoModelForImageTextToText, AutoProcessor
ck = snapshot_download('q-future/Q-ReAlign-Mini-0.8B', local_dir='/kaggle/tmp/qrealign')
proc = AutoProcessor.from_pretrained(ck); net = AutoModelForImageTextToText.from_pretrained(ck, dtype=torch.float16).to('cuda:0').eval()
levels, weights = ['excellent', 'good', 'fair', 'poor', 'bad'], torch.tensor([1.0, 0.75, 0.5, 0.25, 0.0])
ids = [proc.tokenizer(' ' + w, add_special_tokens=False).input_ids[0] for w in levels]
for i, p in enumerate(small):
    if time.time() - START > LIMIT * 0.7: break
    im = Image.open(p).convert('RGB')
    for col, ask in (('q_quality', 'quality'), ('q_aesthetic', 'aesthetics')):
        msg = [{'role': 'user', 'content': [{'type': 'image'}, {'type': 'text', 'text': f'How would you rate the {ask} of this image?'}]}]
        text = proc.apply_chat_template(msg, add_generation_prompt=True) + f'The {ask} of the image is'
        inp = proc(text=[text], images=[im], return_tensors='pt').to('cuda:0')
        with torch.inference_mode():
            pr = net(**inp).logits[0, -1, ids].float().softmax(-1).cpu()
        cols[col][i] = float((pr * weights).sum())
    if i % 500 == 0: print('qrealign', i, round(time.time() - START), flush=True); save(**cols)
del net; torch.cuda.empty_cache(); save(**cols)
# Intern-Decision 2B on GPU 1 (no category given: it must see what the event is).
ck = snapshot_download('internlm/Intern-Decision-2B', local_dir='/kaggle/tmp/intern2b'); sys.path.insert(0, ck)
from inference import DecisionEngine
eng = DecisionEngine(checkpoint=ck, device='cuda:1' if torch.cuda.device_count() > 1 else 'cuda:0', dtype='float16')
STATE = {"photo": "one photo from a family's event (any community, any occasion)",
         "task": "The family is choosing photos for their album: the moment that matters, the important people at "
                 "their best (eyes open, good expression), nobody blocked or cut off, sharp, well lit."}
for i, p in enumerate(small):
    if time.time() - START > LIMIT * 0.97: break
    a = eng.predict({'state': STATE, 'images': [p], 'questions': {
        'worth': {'type': 'score', 'instructions': 'How good is this photo for the album?', 'criteria': ['poor', 'fair', 'good', 'excellent']},
        'keep': {'type': 'noul', 'instructions': 'The family would put this photo in their album.'}}})['answers']
    cols['i_score'][i] = a['worth']['score']; cols['i_keep'][i] = a['keep']['noul']
    if i % 500 == 0: print('intern', i, round(time.time() - START), flush=True); save(**cols)
save(**cols)
print('done', {k: int(np.isfinite(np.array(v, float)).sum()) for k, v in cols.items()}, round(time.time() - START), 's')
