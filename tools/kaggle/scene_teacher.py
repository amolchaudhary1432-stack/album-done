"""Kaggle GPU: a big open model says what is happening in each photo of a real family event, the way a
guest who knows Indian functions of every community would: which ritual or part of the day it is (in
its own words: haldi, mehendi, varmala, pheras, kanyadaan, nikah, anand karaj, vidaai, cake cutting...),
what kind of photo (ritual, portrait, family group, candid, dance, food, decor/detail, getting ready),
and whether it is one of the day's key moments. No list of rituals is given: it must see it.
The phone's small model then learns this from the same SigLIP 2 vector the phone makes (distillation).

Teacher: Qwen2.5-VL-7B-Instruct (Apache 2.0). Input: album-done-w2 (wedding-2, the family agreed; private,
deleted afterwards) and cc_img/ (CC BY web photos of many communities). Output: scenes.jsonl, scenes.npz.
"""
import glob, json, os, re, subprocess, sys, time, zlib
START = time.time(); LIMIT = float(os.environ.get('LIMIT_H', '11.2')) * 3600
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==5.14.1', 'accelerate', 'huggingface_hub', 'onnxruntime'], check=True)
import numpy as np, torch, onnxruntime as ort
from PIL import Image, ImageOps
from huggingface_hub import snapshot_download
photos = sorted(glob.glob('/kaggle/input/**/w2/**/*.jpg', recursive=True)) + sorted(glob.glob('/kaggle/input/**/cc_img/*.jpg', recursive=True))
print(len(photos), 'photos', flush=True)
def key(p): return ('w2/' + p.split('/w2/', 1)[1]) if '/w2/' in p else 'cc/' + os.path.basename(p)
sd = snapshot_download('onnx-community/siglip2-base-patch32-256-ONNX', allow_patterns=['onnx/vision_model_int8.onnx'], local_dir='/kaggle/tmp/siglip')
sig = ort.InferenceSession(sd + '/onnx/vision_model_int8.onnx', providers=['CPUExecutionProvider'])
def siglip(im):
    x = ((np.asarray(im.resize((256, 256), Image.BICUBIC), dtype=np.float32) / 255 - .5) / .5).transpose(2, 0, 1)[None]
    v = sig.run(['pooler_output'], {'pixel_values': x})[0][0]; return v / np.linalg.norm(v)
from transformers import AutoModelForImageTextToText, AutoProcessor
ck = snapshot_download('Qwen/Qwen2.5-VL-7B-Instruct', revision='cc594898137f460bfe9f0759e9844b3ce807cfb5', local_dir='/kaggle/tmp/qwen')
proc = AutoProcessor.from_pretrained(ck, min_pixels=256 * 28 * 28, max_pixels=640 * 28 * 28)
net = AutoModelForImageTextToText.from_pretrained(ck, dtype=torch.float16, device_map='auto').eval()
ASK = ("This photo is from an Indian family event of some community (Hindu of any region, Muslim, Sikh, Christian, Jain, "
       "Parsi...), or another family occasion. Say what you see, as a guest who knows such events would. JSON only: "
       '{"happening": "the ritual or part of the day in its usual name, or plain words", '
       '"kind": "ritual" | "portrait" | "family group" | "candid" | "dance" | "food" | "detail" | "getting ready" | "other", '
       '"key_moment": "yes" | "no", "occasion": "wedding" | "engagement" | "birthday" | "baby function" | "other", '
       '"community": "your best guess or unsure", "sure": "yes" | "no"}')
done = {json.loads(l)['photo'] for l in open('scenes.jsonl')} if os.path.exists('scenes.jsonl') else set()
keys_, vecs = [], []
with open('scenes.jsonl', 'a') as out:
    for p in sorted(photos, key=lambda p: zlib.crc32(os.path.basename(p).encode())):
        if time.time() - START > LIMIT: break
        k = key(p)
        if k in done: continue
        try: im = ImageOps.exif_transpose(Image.open(p)).convert('RGB')
        except Exception: continue
        msg = [{'role': 'user', 'content': [{'type': 'image', 'image': im}, {'type': 'text', 'text': ASK}]}]
        inp = proc.apply_chat_template(msg, add_generation_prompt=True, tokenize=True, return_dict=True, return_tensors='pt').to(net.device)
        with torch.inference_mode(): o = net.generate(**inp, max_new_tokens=80, do_sample=False)
        text = proc.batch_decode(o[:, inp['input_ids'].shape[1]:], skip_special_tokens=True)[0]
        m = re.search(r'\{.*\}', text, re.S)
        try: a = json.loads(m.group(0)) if m else {'raw': text}
        except Exception: a = {'raw': text}
        out.write(json.dumps({'photo': k, **a}) + '\n'); keys_.append(k); vecs.append(siglip(im))
        if len(keys_) % 200 == 0:
            out.flush(); np.savez_compressed('scenes.npz', keys=np.array(keys_), vecs=np.array(vecs, np.float16))
            print(len(keys_), 'photos,', round(time.time() - START), 's', flush=True)
np.savez_compressed('scenes.npz', keys=np.array(keys_), vecs=np.array(vecs, np.float16))
print('done:', len(keys_), 'photos in', round(time.time() - START), 's')
