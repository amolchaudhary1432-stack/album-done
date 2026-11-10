"""Kaggle GPU: a big open model looks at every person in real family-event photos and says, from
everything it sees (clothes, jewellery, mehendi or haldi on them, where they sit, how others treat them),
who they are in the day: the bride, the groom, the birthday person, a baby, or a guest, and their age.
No fixed rules: the small model on the phone learns to see people like this (distillation), the way
Claude would judge a bride with short hair from the rest of the photo.

Teacher: Qwen2.5-VL-7B-Instruct (Apache 2.0). Faces: OpenCV Zoo YuNet (MIT). Each face gets its
answer plus two SigLIP 2 vectors (the person's crop, and the whole photo) from the same int8 ONNX the
phone uses, so the student can learn on exactly what the phone will see.
Input photos (whatever is attached): the album-done-w2 dataset (wedding-2 raw folder at 1,024 px; the
family agreed; deleted from Kaggle afterwards), cc_img/ from the CommonCatalog scan (CC BY).
Output (saved as it goes): people.jsonl (one line per face) and people.npz (keys, crop, photo vectors).
"""
import glob, json, os, re, subprocess, sys, time, urllib.request, zlib
START = time.time(); LIMIT = float(os.environ.get('LIMIT_H', '11.2')) * 3600
MAX_FACES = int(os.environ.get('MAX_FACES', '6000'))
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==5.14.1', 'accelerate', 'huggingface_hub',
                'onnxruntime', 'qwen-vl-utils'], check=True)
import numpy as np, torch, cv2, onnxruntime as ort
from PIL import Image, ImageDraw, ImageOps
from huggingface_hub import snapshot_download, hf_hub_download

allj = [p for p in glob.glob('/kaggle/input/**/*', recursive=True) if p.lower().endswith(('.jpg', '.jpeg'))]
photos = sorted(p for p in allj if '/w2/' in p) + sorted(p for p in allj if '/cc_img/' in p)
print('inputs:', sorted({'/'.join(p.split('/')[:5]) for p in allj}), sum('/w2/' in p for p in photos), 'wedding-2 photos', flush=True)
if os.environ.get('NEED_W2', '1') == '1' and not any('/w2/' in p for p in photos): sys.exit('wedding-2 photos are not attached yet')
print(len(photos), 'photos', flush=True)
def key(p):
    if '/w2/' in p: return 'w2/' + p.split('/w2/', 1)[1]
    return 'cc/' + os.path.basename(p)

# ---- Faces (YuNet, as on the phone) ----
yunet_path = '/kaggle/tmp/yunet.onnx'
os.makedirs('/kaggle/tmp', exist_ok=True)
if not os.path.exists(yunet_path):
    urllib.request.urlretrieve('https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx', yunet_path)
def faces_of(im):
    w, h = im.size; s = 960 / max(w, h); W, H = int(w * s), int(h * s)
    det = cv2.FaceDetectorYN.create(yunet_path, '', (W, H), 0.7, 0.3, 50)
    _, f = det.detect(cv2.cvtColor(np.asarray(im.resize((W, H))), cv2.COLOR_RGB2BGR))
    out = []
    for r in (f if f is not None else []):
        x, y, fw, fh = (r[:4] / s).tolist()
        if fw / w >= 0.035: out.append((x, y, fw, fh, float(r[-1])))
    return sorted(out, key=lambda b: -b[2])[:6]

# ---- SigLIP 2 vectors (the phone's model) ----
sd = snapshot_download('onnx-community/siglip2-base-patch32-256-ONNX', allow_patterns=['onnx/vision_model_int8.onnx'], local_dir='/kaggle/tmp/siglip')
sig = ort.InferenceSession(sd + '/onnx/vision_model_int8.onnx', providers=['CPUExecutionProvider'])
def siglip(ims):
    x = np.stack([((np.asarray(i.convert('RGB').resize((256, 256), Image.BICUBIC), dtype=np.float32) / 255 - .5) / .5).transpose(2, 0, 1) for i in ims])
    v = sig.run(['pooler_output'], {'pixel_values': x})[0]; return v / np.linalg.norm(v, axis=1, keepdims=True)
def person_crop(im, b):
    """The face and the upper body below it: what a person sees of someone at a glance."""
    x, y, fw, fh = b[:4]; w, h = im.size
    return im.crop((max(0, x - 1.2 * fw), max(0, y - 0.6 * fh), min(w, x + 2.2 * fw), min(h, y + 4.5 * fh)))

# ---- The teacher ----
from transformers import AutoModelForImageTextToText, AutoProcessor
ck = snapshot_download('Qwen/Qwen2.5-VL-7B-Instruct', revision='cc594898137f460bfe9f0759e9844b3ce807cfb5', local_dir='/kaggle/tmp/qwen')
proc = AutoProcessor.from_pretrained(ck, min_pixels=256 * 28 * 28, max_pixels=768 * 28 * 28)
net = AutoModelForImageTextToText.from_pretrained(ck, dtype=torch.float16, device_map='auto').eval()
ASK = ("This photo is from an Indian family event (a wedding, engagement, birthday or family function). Look at the "
       "person whose face is inside the red box. Judge from everything in the photo, as a guest who knows such events "
       "would: their clothes and jewellery, mehendi or haldi on them, a garland, where they sit, whether the event "
       "centres on them, how others treat them. Do not guess from hair length alone. Answer with JSON only: "
       '{"role": "bride" | "groom" | "birthday person" | "baby of the day" | "guest", '
       '"age": "baby" | "child" | "teen" | "adult" | "elder", "presents": "woman" | "man" | "girl" | "boy" | "unsure", '
       '"sure": "yes" | "no", "why": "at most six words"}')
def ask(im, b):
    marked = im.copy(); d = ImageDraw.Draw(marked); x, y, fw, fh = b[:4]
    d.rectangle([x - 0.15 * fw, y - 0.15 * fh, x + 1.15 * fw, y + 1.15 * fh], outline=(255, 0, 0), width=max(3, int(im.size[0] / 200)))
    msg = [{'role': 'user', 'content': [{'type': 'image', 'image': marked}, {'type': 'text', 'text': ASK}]}]
    inp = proc.apply_chat_template(msg, add_generation_prompt=True, tokenize=True, return_dict=True, return_tensors='pt').to(net.device)
    with torch.inference_mode(): out = net.generate(**inp, max_new_tokens=120, do_sample=False)
    text = proc.batch_decode(out[:, inp['input_ids'].shape[1]:], skip_special_tokens=True)[0]
    m = re.search(r'\{.*\}', text, re.S)
    try: return json.loads(m.group(0)) if m else {'raw': text}
    except Exception: pass
    # Cut off mid-answer: keep the fields that are complete.
    got = dict(re.findall(r'"(role|age|presents|sure)"\s*:\s*"([^"]*)"', text))
    return got if got else {'raw': text}

# ---- Every face, saved as it goes ----
done = set()
if os.path.exists('people.jsonl'):
    for l in open('people.jsonl'): done.add(json.loads(l)['photo'])
keys_, crops, whole = [], [], []
n = 0
with open('people.jsonl', 'a') as out:
    # Photos in a mixed order (all events of the day, every source), so a partial run still covers them all.
    order = sorted(photos, key=lambda p: zlib.crc32(os.path.basename(p).encode()))
    for p in order:
        if n >= MAX_FACES or time.time() - START > LIMIT: break
        k = key(p)
        if k in done: continue
        try: im = ImageOps.exif_transpose(Image.open(p)).convert('RGB')
        except Exception: continue
        fs = faces_of(im)
        if not fs: continue
        pv = siglip([im])[0]
        cv = siglip([person_crop(im, b) for b in fs])
        for i, b in enumerate(fs):
            a = ask(im, b)
            out.write(json.dumps({'photo': k, 'face': i, 'box': [round(v, 1) for v in b[:4]], 'size': im.size, 'faces': len(fs), **a}) + '\n')
            keys_.append(f'{k}#{i}'); crops.append(cv[i]); whole.append(pv); n += 1
        out.flush()
        if len(keys_) % 200 < len(fs):
            np.savez_compressed('people.npz', keys=np.array(keys_), crop=np.array(crops, np.float16), photo=np.array(whole, np.float16))
            print(n, 'faces,', round(time.time() - START), 's', flush=True)
np.savez_compressed('people.npz', keys=np.array(keys_), crop=np.array(crops, np.float16), photo=np.array(whole, np.float16))
print('done:', n, 'faces in', round(time.time() - START), 's')
