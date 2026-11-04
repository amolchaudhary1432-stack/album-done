"""Kaggle GPU: teach the one-pass judge (Intern-Decision 0.8B, Apache) which near-twin a family keeps.

Lessons: the helpers' full-size rankings (frame-labels.tsv) of the practice groups ("tune" split),
as pairs shown both ways round, so the answer never depends on which photo comes first. Test: the
held-back groups ("test" split), never seen in training, answered before and after training in
judge_test.py's format (frames-before.tsv, frames-after.tsv), scored with judge_test.py score.
Only freely licensed Commons photos are used here (frame-level lessons only, owner's rule).
"""
import glob, json, os, random, subprocess, sys, time
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==5.14.1', 'peft==0.17.1', 'huggingface_hub'], check=True)
import torch
from huggingface_hub import snapshot_download
from PIL import Image

root = os.path.dirname(glob.glob('/kaggle/input/**/index.json', recursive=True)[0])
ck = snapshot_download('internlm/Intern-Decision-0.8B', local_dir='/kaggle/tmp/intern-0.8b')
sys.path.insert(0, ck)
import inference as I

random.seed(7); torch.manual_seed(7)
# No category given (owner, 4 Nov 2026): the judge must see for itself what the event is and who
# matters, as a person does; nothing here says wedding, birthday or trip.
STATE = {"photos": "near-twins from a family's event, shot within seconds of each other",
         "task": "The family is choosing which ones go into their album: the moment that matters, the "
                 "important people at their best (eyes open, good expression), nobody blocked or cut off, sharp, well lit."}
L = 'ABCD'
index = {g['id']: g for g in json.load(open(f'{root}/index.json'))}
labels = {}
for line in open(f'{root}/frame-labels.tsv'):
    c = line.rstrip('\n').split('\t')
    if len(c) >= 3 and c[2] != 'equal' and c[0] in index: labels[c[0]] = (c[1].split('>'), c[2])
os.makedirs('/kaggle/tmp/small', exist_ok=True)


def photo(g, letter):
    p = f"/kaggle/tmp/small/{g['id']}-{letter}.jpg"
    if not os.path.exists(p):
        with Image.open(f"{root}/photos/{g['set']}/{g['photos'][L.index(letter)]}") as im:
            im = im.convert('RGB'); im.thumbnail((640, 640)); im.save(p, quality=90)
    return p


def question(n):
    return {'keep': {'type': 'choice', 'instructions': 'Which one photo would the family keep for the album?',
                     'criteria': {L[k]: f'photo {k + 1}' for k in range(n)}}}


backend = I.HFBackend(ck, device='cuda', dtype='float32')
model, tok = backend.model, backend.tokenizer
sym = [tok.encode(s, add_special_tokens=False)[0] for s in L]


def logits_for(paths):
    row = {'state': STATE, 'images': paths, 'questions': question(len(paths))}
    _, batch, pos = backend.encode(row)
    batch = batch.to('cuda')
    out = model(**batch, use_cache=False, logits_to_keep=pos.to('cuda')).logits[0]
    return out[0, sym[:len(paths)]]


def answer(split, out):
    model.eval()
    with open(out, 'w') as f, torch.inference_mode():
        for gid, (order, _) in labels.items():
            g = index[gid]
            if g['split'] != split: continue
            letters = [x for x in L[:len(g['photos'])]]
            t = time.time()
            p = logits_for([photo(g, x) for x in letters]).float().softmax(-1).tolist()
            f.write(f"{gid}\t{time.time() - t:.2f}\t" + '\t'.join(f'{x}:{p[k]:.4f}:0:0' for k, x in enumerate(letters)) + '\n')


answer('test', 'frames-before.tsv')

# Pairs from the practice groups: (better, worse), each shown in both orders.
pairs = []
for gid, (order, strength) in labels.items():
    g = index[gid]
    if g['split'] != 'tune': continue
    for i in range(len(order)):
        for j in range(i + 1, len(order)):
            w = 2.0 if strength == 'sure' else 1.0
            pairs += [(g, order[i], order[j], w), (g, order[j], order[i], -w)]
print(len(pairs), 'training examples', flush=True)

from peft import LoraConfig, get_peft_model
model = get_peft_model(model, LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05,
    target_modules=r'.*language_model.*\.(q_proj|k_proj|v_proj|o_proj|gate_proj|up_proj|down_proj)'))
model.print_trainable_parameters()
opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=1e-4, weight_decay=0.0)
EPOCHS, ACC = 3, 8
step = 0
for epoch in range(EPOCHS):
    model.train(); random.shuffle(pairs); total = n = 0
    for g, a, b, w in pairs:
        # w > 0: the first photo shown is the better one; w < 0: the second is.
        lg = logits_for([photo(g, a), photo(g, b)])
        target = torch.tensor(0 if w > 0 else 1, device='cuda')
        loss = abs(w) * torch.nn.functional.cross_entropy(lg.float()[None], target[None]) / ACC
        loss.backward(); total += loss.item() * ACC; n += 1; step += 1
        if step % ACC == 0: opt.step(); opt.zero_grad()
    print(f'epoch {epoch + 1}: loss {total / n:.3f}', flush=True)

answer('test', 'frames-after.tsv')
model.save_pretrained('frame-judge-lora')
r = subprocess.run([sys.executable, f'{root}/judge_test.py', 'score', f'{root}/frame-labels.tsv', f'{root}/index.json',
                    'frames-before.tsv', 'frames-after.tsv'], capture_output=True, text=True)
print(r.stdout, r.stderr[-2000:])
open('scores.txt', 'w').write(r.stdout)
