"""Teach a small see-and-write model to judge moments in Claude's words (examples from reader_data.py),
then score it on the held-out functions: does its keep / skip / peak verdict match Claude's?

The eyes (vision tower) stay frozen and are run once per picture; only small added layers (LoRA) in the
language part learn, so this fits a 4-CPU machine for the smallest models and a free Kaggle GPU for bigger.

    python reader_train.py EXAMPLES.jsonl MODEL_DIR_OR_NAME OUT_DIR
    env: EPOCHS (4), LR (2e-4), RANK (16), BATCH (4), LIMIT_MIN (wall-clock budget for training, 60), ZERO=1 (score before teaching too)
"""
import json, os, random, re, sys, time
import torch
from PIL import Image
from transformers import AutoProcessor, AutoModelForImageTextToText
from peft import LoraConfig, get_peft_model

ex_path, name, out = sys.argv[1:4]
EPOCHS = int(os.environ.get('EPOCHS', '4')); LR = float(os.environ.get('LR', '2e-4')); RANK = int(os.environ.get('RANK', '16'))
BATCH = int(os.environ.get('BATCH', '4')); LIMIT = float(os.environ.get('LIMIT_MIN', '60')) * 60
os.makedirs(out, exist_ok=True)
dev = 'cuda' if torch.cuda.is_available() else 'cpu'
dtype = torch.bfloat16 if dev == 'cuda' else torch.float32
torch.manual_seed(0); random.seed(0)
t0 = time.time()
def log(*a):
    print(f'[{time.time() - t0:6.0f}s]', *a, flush=True)

proc = AutoProcessor.from_pretrained(name)
proc.image_processor.do_image_splitting = False  # one 512 px picture = 64 picture tokens, as it will be on the phone
model = AutoModelForImageTextToText.from_pretrained(name, torch_dtype=dtype).to(dev)
tok = proc.tokenizer
END = '<end_of_utterance>'
rows = [json.loads(l) for l in open(ex_path, encoding='utf-8')]
if os.environ.get('MAX'): rows = rows[:int(os.environ['MAX'])]


def chat(prompt):
    return proc.apply_chat_template([{'role': 'user', 'content': [{'type': 'image'}, {'type': 'text', 'text': prompt}]}], add_generation_prompt=True)


@torch.no_grad()
def prepare(r):
    """Tokens of the question (with the picture's places) and of the answer, and what the eyes saw."""
    im = Image.open(r['image']).convert('RGB')
    q = proc(text=chat(r['prompt']), images=[im], return_tensors='pt')
    a = tok(' ' + r['answer'] + END, add_special_tokens=False, return_tensors='pt')['input_ids'][0]
    pv = q['pixel_values'].to(dev, dtype); pm = q.get('pixel_attention_mask'); pm = pm.to(dev) if pm is not None else None
    base = model.model
    if hasattr(base, 'get_image_features'):
        seen = base.get_image_features(pv, pm)
    else:
        b, n, c, h, w = pv.shape
        hs = base.vision_model(pixel_values=pv.view(b * n, c, h, w)).last_hidden_state
        seen = base.connector(hs)
    return {'q': q['input_ids'][0], 'a': a, 'seen': seen.float().cpu(), 'row': r}


cache = out + '/prepared.pt'
if os.path.exists(cache):
    data = torch.load(cache)
else:
    data = []
    for i, r in enumerate(rows):
        data.append(prepare(r))
        if i % 100 == 0: log('seen', i, 'of', len(rows), 'pictures; tokens', len(data[-1]['q']), '+', len(data[-1]['a']), 'eyes', tuple(data[-1]['seen'].shape))
    torch.save(data, cache)
train = [d for d in data if d['row']['split'] == 'train']; test = [d for d in data if d['row']['split'] == 'test']
log(len(train), 'to learn from,', len(test), 'held out')


def batch_of(items, with_answer):
    seqs = [torch.cat([d['q'], d['a']]) if with_answer else d['q'] for d in items]
    L = max(len(s) for s in seqs); pad = tok.pad_token_id if tok.pad_token_id is not None else 0
    ids = torch.full((len(seqs), L), pad); att = torch.zeros((len(seqs), L), dtype=torch.long); lab = torch.full((len(seqs), L), -100)
    for i, (s, d) in enumerate(zip(seqs, items)):
        if with_answer:  # padded on the right; only the answer is learned
            ids[i, :len(s)] = s; att[i, :len(s)] = 1; lab[i, len(d['q']):len(s)] = s[len(d['q']):]
        else:  # padded on the left, so every row ends where the answer begins
            ids[i, L - len(s):] = s; att[i, L - len(s):] = 1
    seen = torch.cat([d['seen'] for d in items]).to(dev, dtype)
    return ids.to(dev), att.to(dev), lab.to(dev), seen


@torch.no_grad()
def answers(items, tag):
    model.eval(); outp = []
    for i in range(0, len(items), 8):
        part = items[i:i + 8]; ids, att, _, seen = batch_of(part, False)
        gen = model.generate(input_ids=ids, attention_mask=att, image_hidden_states=seen, max_new_tokens=70, do_sample=False)
        for d, g in zip(part, gen):
            outp.append((d['row'], tok.decode(g[ids.shape[1]:], skip_special_tokens=True).strip()))
        if i % 80 == 0: log(tag, 'answered', i, 'of', len(items), '|', outp[-1][1][:110])
    return outp


WORD = {'skip': 0, 'keep': 1, 'peak': 2}
def score(outp, tag):
    ok3 = ok2 = said = 0; conf = [[0] * 4 for _ in range(3)]
    with open(f'{out}/answers-{tag}.jsonl', 'w', encoding='utf-8') as w:
        for r, text in outp:
            m = re.search(r'verdict\W+(skip|keep|peak)', text.lower()); v = WORD[m.group(1)] if m else -1
            said += v >= 0; ok3 += v == r['verdict']; ok2 += v >= 0 and (v > 0) == (r['verdict'] > 0); conf[r['verdict']][v if v >= 0 else 3] += 1
            w.write(json.dumps({'id': r['id'], 'folder': r['folder'], 'claude': r['answer'], 'reader': text, 'verdict': r['verdict'], 'said': v}, ensure_ascii=False) + '\n')
    n = len(outp)
    line = (f'{tag}: {n} held-out moments | gave a verdict {said / n:.1%} | same verdict as Claude (skip/keep/peak) {ok3 / n:.1%} | '
            f'same keep-or-skip {ok2 / n:.1%} | Claude skip/keep/peak by reader skip/keep/peak/none: {conf}')
    log(line); open(out + '/scores.txt', 'a').write(line + '\n')


if os.environ.get('ZERO') == '1': score(answers(test, 'before'), 'before')

lm = [n for n, _ in model.named_modules() if 'text_model' in n and n.split('.')[-1] in ('q_proj', 'k_proj', 'v_proj', 'o_proj', 'gate_proj', 'up_proj', 'down_proj')]
model = get_peft_model(model, LoraConfig(r=RANK, lora_alpha=2 * RANK, lora_dropout=0.05, target_modules=lm))
params = [p for p in model.parameters() if p.requires_grad]
log('learning', sum(p.numel() for p in params) // 1000, 'thousand numbers in', len(lm), 'layers')
opt = torch.optim.AdamW(params, lr=LR, weight_decay=0.0)
steps = EPOCHS * ((len(train) + BATCH - 1) // BATCH); step = 0; start = time.time(); stop = False
for ep in range(EPOCHS):
    model.train(); random.shuffle(train); tot = 0.0; cnt = 0
    for i in range(0, len(train), BATCH):
        ids, att, lab, seen = batch_of(train[i:i + BATCH], True)
        for g in opt.param_groups: g['lr'] = LR * min(1.0, (step + 1) / 30) * max(0.05, 1 - step / steps)
        loss = model(input_ids=ids, attention_mask=att, image_hidden_states=seen, labels=lab).loss
        loss.backward(); torch.nn.utils.clip_grad_norm_(params, 1.0); opt.step(); opt.zero_grad()
        tot += loss.item(); cnt += 1; step += 1
        if step % 20 == 0: log(f'round {ep + 1}, step {step} of {steps}, loss {tot / cnt:.3f}')
        if time.time() - start > LIMIT: stop = True; break
    log(f'round {ep + 1} done, loss {tot / max(1, cnt):.3f}')
    if stop: log('time budget used'); break
model.save_pretrained(out + '/lora')
score(answers(test, 'after'), f'after-{os.path.basename(name.rstrip("/"))}')
log('done')
