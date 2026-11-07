"""Kaggle GPU: look at every photo the caption scans found (CommonCatalog cc_img/, Megalith/PD12M
mp_img/, attached as earlier jobs' outputs) and keep the Indian weddings by what they show, not by
their captions: captions with "nikah" or "wedding" also bring Malay, Indonesian, Western and other
weddings. SigLIP 2 (same int8 ONNX as our tools) scores each photo against Indian-wedding descriptions
and against the others; then the eyes (Q-ReAlign, Intern-Decision 2B) score the Indian ones.
Output: found.npz (keys, siglip, indian score, best match, eyes) and found.jsonl in the working folder."""
import glob, json, os, subprocess, sys, time
START = time.time()
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==5.14.1', 'huggingface_hub', 'onnxruntime', 'tokenizers'], check=True)
import numpy as np, torch, onnxruntime as ort
from PIL import Image
from huggingface_hub import snapshot_download
from tokenizers import Tokenizer
paths = sorted(glob.glob('/kaggle/input/**/cc_img/*.jpg', recursive=True) + glob.glob('/kaggle/input/**/mp_img/*.jpg', recursive=True))
print(len(paths), 'photos found by the caption scans', flush=True)
keys = [('cc/' if '/cc_img/' in p else 'mp/') + os.path.basename(p) for p in paths]
sd = snapshot_download('onnx-community/siglip2-base-patch32-256-ONNX', allow_patterns=['onnx/vision_model_int8.onnx', 'onnx/text_model_int8.onnx', 'tokenizer.json'], local_dir='/kaggle/tmp/siglip')
vis = ort.InferenceSession(sd + '/onnx/vision_model_int8.onnx'); txt = ort.InferenceSession(sd + '/onnx/text_model_int8.onnx')
tok = Tokenizer.from_file(sd + '/tokenizer.json'); tok.enable_padding(length=64, pad_id=0); tok.enable_truncation(max_length=64)
INDIAN = ['a photo of an Indian Hindu wedding', 'a photo of a Sikh wedding in a gurdwara', 'a photo of an Indian Muslim nikah with a sherwani and lehenga',
          'a photo of a Bengali wedding with a topor', 'a photo of a South Indian wedding with a silk saree', 'a photo of an Indian bride in a red lehenga',
          'a photo of an Indian mehndi or haldi ceremony', 'a photo of an Indian Christian wedding with a saree', 'a photo of an Indian wedding reception stage']
OTHER = ['a photo of a Malay wedding in Malaysia or Indonesia', 'a photo of a Western white wedding', 'a photo of a Chinese wedding',
         'a photo of an African wedding', 'a photo of an Arab wedding', 'a photo of a city street', 'a photo of a building or monument',
         'a photo of a festival crowd', 'a photo of a Pakistani wedding', 'a photo of a Bangladeshi wedding']
enc = tok.encode_batch(INDIAN + OTHER)
tv = txt.run(['pooler_output'], {'input_ids': np.array([e.ids for e in enc], dtype=np.int64)})[0]; tv /= np.linalg.norm(tv, axis=1, keepdims=True)
vecs = []
for i in range(0, len(paths), 32):
    b = [(np.asarray(Image.open(p).convert('RGB').resize((256, 256), Image.BICUBIC), dtype=np.float32) / 255.0 - 0.5) / 0.5 for p in paths[i:i + 32]]
    v = vis.run(['pooler_output'], {'pixel_values': np.stack([x.transpose(2, 0, 1) for x in b])})[0]
    vecs.append(v / np.linalg.norm(v, axis=1, keepdims=True))
V = np.concatenate(vecs) if vecs else np.zeros((0, 768), np.float32)
sim = V @ tv.T; ni = len(INDIAN)
indian = sim[:, :ni].max(1) - sim[:, ni:].max(1); best = [(INDIAN + OTHER)[k] for k in sim.argmax(1)]
keep = np.where(indian > 0)[0]
print(len(keep), 'look Indian of', len(paths), round(time.time() - START), 's', flush=True)
cols = {k: np.full(len(paths), np.nan) for k in ('q_quality', 'q_aesthetic', 'i_score', 'i_keep')}
def save():
    np.savez_compressed('found.npz', keys=np.array(keys), siglip=V.astype(np.float16), indian=indian.astype(np.float32), **{k: v.astype(np.float32) for k, v in cols.items()})
    with open('found.jsonl', 'w') as f:
        for k, s, bm in zip(keys, indian, best): f.write(json.dumps({'key': k, 'indian': round(float(s), 4), 'best': bm}) + '\n')
save()
from transformers import AutoModelForImageTextToText, AutoProcessor
ck = snapshot_download('q-future/Q-ReAlign-Mini-0.8B', local_dir='/kaggle/tmp/qrealign')
proc = AutoProcessor.from_pretrained(ck); net = AutoModelForImageTextToText.from_pretrained(ck, dtype=torch.float16).to('cuda:0').eval()
levels, weights = ['excellent', 'good', 'fair', 'poor', 'bad'], torch.tensor([1.0, 0.75, 0.5, 0.25, 0.0])
ids = [proc.tokenizer(' ' + w, add_special_tokens=False).input_ids[0] for w in levels]
for i in keep:
    im = Image.open(paths[i]).convert('RGB')
    for col, ask in (('q_quality', 'quality'), ('q_aesthetic', 'aesthetics')):
        msg = [{'role': 'user', 'content': [{'type': 'image'}, {'type': 'text', 'text': f'How would you rate the {ask} of this image?'}]}]
        inp = proc(text=[proc.apply_chat_template(msg, add_generation_prompt=True) + f'The {ask} of the image is'], images=[im], return_tensors='pt').to('cuda:0')
        with torch.inference_mode(): pr = net(**inp).logits[0, -1, ids].float().softmax(-1).cpu()
        cols[col][i] = float((pr * weights).sum())
del net; torch.cuda.empty_cache(); save()
ck = snapshot_download('internlm/Intern-Decision-2B', local_dir='/kaggle/tmp/intern2b'); sys.path.insert(0, ck)
from inference import DecisionEngine
eng = DecisionEngine(checkpoint=ck, device='cuda:1' if torch.cuda.device_count() > 1 else 'cuda:0', dtype='float16')
STATE = {"photo": "one photo from a family's event (any community, any occasion)",
         "task": "The family is choosing photos for their album: the moment that matters, the important people at their best."}
for i in keep:
    a = eng.predict({'state': STATE, 'images': [paths[i]], 'questions': {
        'worth': {'type': 'score', 'instructions': 'How good is this photo for the album?', 'criteria': ['poor', 'fair', 'good', 'excellent']},
        'keep': {'type': 'noul', 'instructions': 'The family would put this photo in their album.'}}})['answers']
    cols['i_score'][i] = a['worth']['score']; cols['i_keep'][i] = a['keep']['noul']
save()
print('done', len(keep), 'Indian photos scored,', round(time.time() - START), 's')
