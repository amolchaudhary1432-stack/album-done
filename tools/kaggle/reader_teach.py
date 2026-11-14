"""Kaggle GPU: teach a small see-and-write model to read a folder moment by moment the way Claude does, and
score it on events and photos it has never seen.

Teaching material (Claude's own words, never rules):
  wedding-2   961 moments of a real raw folder (kind, skip / keep / peak, why), photos from the private
              album-done-w2 dataset (the family agreed; deleted from Kaggle when the training is done)
  web photos  Claude's judgement of freely licensed photos of other Indian communities (Flickr CC BY, from
              the album-done-india-sets job), one photo = one moment
Each example: a 512 px sheet (up to four frames of the moment) + a line of facts + what was said about the
most similar moments read earlier -> Claude's words, then the verdict.
Held out, never trained on: wedding-2's reception, gondhal and nanhora (the same 272 moments as on Claude's
machine), and a quarter of the web sets. The eyes (vision tower) stay frozen unless EYES=1.
Output: scores.txt, answers-<model>.jsonl, lora-<model>/ (the small learned layers).
Gold files arrive beside this script (run.json "attach"): w2-moments.tsv, wedding-2-moments-claude.tsv, web-claude.tsv.
"""
import glob, json, os, random, re, subprocess, sys, time, zlib, collections
T0 = time.time()
def log(*a): print(f'[{time.time() - T0:6.0f}s]', *a, flush=True)
IN = os.environ.get('READER_INPUT', '/kaggle/input')
MODELS = os.environ.get('MODELS', 'HuggingFaceTB/SmolVLM-256M-Instruct,HuggingFaceTB/SmolVLM-500M-Instruct,HuggingFaceTB/SmolVLM-Instruct').split(',')
EPOCHS = int(os.environ.get('EPOCHS', '6')); LR = float(os.environ.get('LR', '2e-4')); RANK = int(os.environ.get('RANK', '16'))
BATCH = int(os.environ.get('BATCH', '8')); EYES = os.environ.get('EYES') == '1'; LIMIT = float(os.environ.get('LIMIT_H', '10.5')) * 3600
HELD = ('reception', 'gondhal', 'nanhora')
if os.environ.get('NO_PIP') != '1':
    subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==4.57.1', 'peft==0.21.3', 'accelerate', 'huggingface_hub'], check=True)
import numpy as np, torch
from PIL import Image, ImageOps
from transformers import AutoProcessor, AutoModelForImageTextToText, AutoModel
from peft import LoraConfig, get_peft_model
dev = 'cuda' if torch.cuda.is_available() else 'cpu'
log('device', dev, torch.cuda.get_device_name(0) if dev == 'cuda' else '')
random.seed(0); torch.manual_seed(0)
VERDICT = {0: 'skip', 1: 'keep', 2: 'peak'}; WORD = {v: k for k, v in VERDICT.items()}

# ---- Where the photos are ----
allj = [p for p in glob.glob(IN + '/**/*', recursive=True) if p.lower().endswith(('.jpg', '.jpeg'))]
by_tail = {}
for p in allj:
    parts = p.split('/'); by_tail['/'.join(parts[-2:]).lower()] = p; by_tail[os.path.splitext('/'.join(parts[-2:]))[0].lower()] = p
web_path = {os.path.splitext(os.path.basename(p))[0]: p for p in allj if '/india_img/' in p or '/cc_img/' in p}
log(len(allj), 'pictures attached;', len(web_path), 'web photos; inputs', sorted({'/'.join(p.split('/')[:5]) for p in allj})[:8])
def find(name): return by_tail.get(name.lower()) or by_tail.get(os.path.splitext(name)[0].lower())

# ---- The gold ----
here = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else '.'
def gold_file(n): return next(p for p in (n, os.path.join(here, n), os.path.join(os.environ.get('GOLD_DIR', '.'), n)) if os.path.exists(p))
items = []  # one per moment: source, id, folder, files (paths), verdict, kind, words, group (for the split), community
if os.environ.get('NO_W2') != '1':
    gold = {}
    for l in open(gold_file('wedding-2-moments-claude.tsv'), encoding='utf-8'):
        c = l.rstrip('\n').split('\t')
        if len(c) >= 4: gold[c[0]] = (int(c[1]), c[2], c[3])
    missing = 0
    for l in open(gold_file('w2-moments.tsv'), encoding='utf-8'):
        c = l.rstrip('\n').split('\t')
        if c[0] not in gold: continue
        files = [find(f) for f in c[5].split(';') if f]; files = [f for f in files if f]
        if not files: missing += 1; continue
        v, kind, why = gold[c[0]]
        items.append({'src': 'w2', 'id': c[0], 'folder': c[1], 'files': files, 'n': len([f for f in c[5].split(';') if f]), 'verdict': v, 'kind': kind, 'words': why,
                      'test': any(w in c[1].lower() for w in HELD), 'community': 'marathi'})
    log('wedding-2:', len(items), 'moments with their photos,', missing, 'without')
    if not items: sys.exit('wedding-2 photos are not attached (run.json "upload": "w2")')
n_w2 = len(items)
for l in open(gold_file('web-claude.tsv'), encoding='utf-8'):
    if l.startswith('#') or not l.strip(): continue
    photo, sid, comm, v, kind, words = l.rstrip('\n').split('\t')
    if photo not in web_path or comm in ('not-indian', 'not-wedding'): continue
    items.append({'src': 'web', 'id': 'P' + photo, 'folder': sid, 'files': [web_path[photo]], 'n': 1, 'verdict': int(v), 'kind': kind, 'words': words,
                  'test': zlib.crc32(sid.encode()) % 4 == 0, 'community': comm})
log('web:', len(items) - n_w2, 'photos judged by Claude, communities', collections.Counter(i['community'] for i in items[n_w2:]).most_common())

# ---- Pictures: one sheet per moment ----
os.makedirs('sheets', exist_ok=True)
def sheet(files, path):
    if len(files) > 4: files = [files[round(i * (len(files) - 1) / 3)] for i in range(4)]
    S = 512; im = Image.new('RGB', (S, S), (0, 0, 0))
    if len(files) == 1:
        t = ImageOps.exif_transpose(Image.open(files[0])).convert('RGB'); t.thumbnail((S, S)); im.paste(t, ((S - t.width) // 2, (S - t.height) // 2))
    else:
        for i, x in enumerate(files):
            t = ImageOps.exif_transpose(Image.open(x)).convert('RGB'); t.thumbnail((S // 2, S // 2))
            im.paste(t, ((i % 2) * S // 2 + (S // 2 - t.width) // 2, (i // 2) * S // 2 + (S // 2 - t.height) // 2))
    im.save(path, quality=90)
for it in items:
    it['image'] = f"sheets/{it['id']}.jpg"; sheet(it['files'], it['image'])
log('sheets made')

# ---- Memory: what was said about the most similar moments read earlier (same folder of photos) ----
sig_name = 'google/siglip2-base-patch32-256'
sp = AutoProcessor.from_pretrained(sig_name); sm = AutoModel.from_pretrained(sig_name).to(dev).eval()
@torch.no_grad()
def vectors(paths):
    out = []
    for i in range(0, len(paths), 64):
        ims = [Image.open(p).convert('RGB') for p in paths[i:i + 64]]
        f = sm.get_image_features(**sp(images=ims, return_tensors='pt').to(dev)); out.append(torch.nn.functional.normalize(f.float(), dim=-1).cpu())
    return torch.cat(out).numpy()
V = vectors([it['image'] for it in items]); del sm
seen = collections.defaultdict(list)  # per source folder of photos: (vector, words, kept)
QUESTION = "What is this moment, and does it belong in the album? Say what kind of moment, what is happening and why, then the verdict."
for it, v in zip(items, V):
    book = 'w2' if it['src'] == 'w2' else it['folder']
    near = sorted(((float(v @ s[0]), s) for s in seen[book]), key=lambda t: -t[0])[:2]
    memory = '; '.join(f"{s[1]} ({'kept' if s[2] else 'skipped'})" for c, s in near if c >= 0.80) or 'none'
    folder = it['folder'] if it['src'] == 'w2' else 'not named'
    it['prompt'] = (f"Frames of one moment from a family event folder, read in order.\nFolder: {folder}. Photos in this moment: {it['n']}.\n"
                    f"Similar moments read earlier: {memory}.\n{QUESTION}")
    it['answer'] = f"{it['kind']}: {it['words']}. Verdict: {VERDICT[it['verdict']]}."
    seen[book].append((v, it['words'], it['verdict'] > 0))
if os.environ.get('MAX'): items = items[:int(os.environ['MAX'])]
train = [i for i in items if not i['test']]; test = [i for i in items if i['test']]
log(len(train), 'to learn from,', len(test), 'held out:', collections.Counter((i['src'], VERDICT[i['verdict']]) for i in test))

END = '<end_of_utterance>'
def teach(name):
    tag = name.split('/')[-1]
    proc = AutoProcessor.from_pretrained(name); proc.image_processor.do_image_splitting = False
    model = AutoModelForImageTextToText.from_pretrained(name, torch_dtype=torch.float32).to(dev); tok = proc.tokenizer
    def chat(prompt): return proc.apply_chat_template([{'role': 'user', 'content': [{'type': 'image'}, {'type': 'text', 'text': prompt}]}], add_generation_prompt=True)
    @torch.no_grad()
    def prepare(it):
        q = proc(text=chat(it['prompt']), images=[Image.open(it['image']).convert('RGB')], return_tensors='pt')
        a = tok(' ' + it['answer'] + END, add_special_tokens=False, return_tensors='pt')['input_ids'][0]
        d = {'q': q['input_ids'][0], 'a': a, 'it': it, 'pv': q['pixel_values'][0], 'pm': q['pixel_attention_mask'][0] if q.get('pixel_attention_mask') is not None else None}
        if not EYES:
            pv = d['pv'][None].to(dev); pm = d['pm'][None].to(dev) if d['pm'] is not None else None
            with torch.autocast(dev, enabled=dev == 'cuda'): d['seen'] = model.model.get_image_features(pv, pm).float().cpu()
            d['pv'] = d['pm'] = None
        return d
    data = [prepare(it) for it in items]
    log(tag, 'pictures read; tokens', len(data[0]['q']), '+', len(data[0]['a']))
    tr = [d for d in data if not d['it']['test']]; te = [d for d in data if d['it']['test']]
    def batch_of(part, with_answer):
        seqs = [torch.cat([d['q'], d['a']]) if with_answer else d['q'] for d in part]
        L = max(len(s) for s in seqs); pad = tok.pad_token_id if tok.pad_token_id is not None else 0
        ids = torch.full((len(seqs), L), pad); att = torch.zeros((len(seqs), L), dtype=torch.long); lab = torch.full((len(seqs), L), -100)
        for i, (s, d) in enumerate(zip(seqs, part)):
            if with_answer: ids[i, :len(s)] = s; att[i, :len(s)] = 1; lab[i, len(d['q']):len(s)] = s[len(d['q']):]
            else: ids[i, L - len(s):] = s; att[i, L - len(s):] = 1
        kw = {'input_ids': ids.to(dev), 'attention_mask': att.to(dev)}
        if EYES:
            kw['pixel_values'] = torch.stack([d['pv'] for d in part]).to(dev)
            if part[0]['pm'] is not None: kw['pixel_attention_mask'] = torch.stack([d['pm'] for d in part]).to(dev)
        else: kw['image_hidden_states'] = torch.cat([d['seen'] for d in part]).to(dev)
        return kw, lab.to(dev)
    names = [n for n, _ in model.named_modules() if n.split('.')[-1] in ('q_proj', 'k_proj', 'v_proj', 'o_proj', 'gate_proj', 'up_proj', 'down_proj', 'out_proj', 'fc1', 'fc2')
             and ('text_model' in n or (EYES and 'vision_model' in n))]
    model = get_peft_model(model, LoraConfig(r=RANK, lora_alpha=2 * RANK, lora_dropout=0.05, target_modules=names))
    params = [p for p in model.parameters() if p.requires_grad]
    log(tag, 'learning', sum(p.numel() for p in params) // 1000, 'thousand numbers in', len(names), 'layers', '(eyes too)' if EYES else '')
    opt = torch.optim.AdamW(params, lr=LR, weight_decay=0.0); scaler = torch.amp.GradScaler(enabled=dev == 'cuda')
    steps = EPOCHS * ((len(tr) + BATCH - 1) // BATCH); step = 0
    for ep in range(EPOCHS):
        model.train(); random.shuffle(tr); tot = cnt = 0
        for i in range(0, len(tr), BATCH):
            kw, lab = batch_of(tr[i:i + BATCH], True)
            for g in opt.param_groups: g['lr'] = LR * min(1.0, (step + 1) / 30) * max(0.05, 1 - step / steps)
            with torch.autocast(dev, enabled=dev == 'cuda'): loss = model(**kw, labels=lab).loss
            scaler.scale(loss).backward(); scaler.unscale_(opt); torch.nn.utils.clip_grad_norm_(params, 1.0); scaler.step(opt); scaler.update(); opt.zero_grad()
            tot += loss.item(); cnt += 1; step += 1
        log(tag, f'round {ep + 1} of {EPOCHS}, loss {tot / max(1, cnt):.3f}')
        if time.time() - T0 > LIMIT: break
    model.eval(); outp = []
    with torch.no_grad():
        for i in range(0, len(te), 16):
            part = te[i:i + 16]; kw, _ = batch_of(part, False)
            with torch.autocast(dev, enabled=dev == 'cuda'): gen = model.generate(**kw, max_new_tokens=70, do_sample=False)
            for d, g in zip(part, gen): outp.append((d['it'], tok.decode(g[kw['input_ids'].shape[1]:], skip_special_tokens=True).strip()))
    with open(f'answers-{tag}.jsonl', 'w', encoding='utf-8') as w:
        for it, text in outp:
            m = re.search(r'verdict\W+(skip|keep|peak)', text.lower()); it['_said'] = WORD[m.group(1)] if m else -1
            w.write(json.dumps({'id': it['id'], 'src': it['src'], 'community': it['community'], 'folder': it['folder'], 'claude': it['answer'], 'reader': text,
                                'verdict': it['verdict'], 'said': it['_said']}, ensure_ascii=False) + '\n')
    for src in ('w2', 'web'):
        rows = [it for it, _ in outp if it['src'] == src]
        if not rows: continue
        n = len(rows); ok3 = sum(it['_said'] == it['verdict'] for it in rows); ok2 = sum(it['_said'] >= 0 and (it['_said'] > 0) == (it['verdict'] > 0) for it in rows)
        line = (f"{tag}{' eyes' if EYES else ''}, {EPOCHS} rounds, on {'wedding-2 held-out functions' if src == 'w2' else 'held-out web sets'}: {n} moments | "
                f"same keep-or-skip {ok2 / n:.1%} | same skip/keep/peak {ok3 / n:.1%} | no verdict {sum(it['_said'] < 0 for it in rows)}")
        log(line); open('scores.txt', 'a').write(line + '\n')
    model.save_pretrained(f'lora-{tag}')
    del model, data; torch.cuda.empty_cache() if dev == 'cuda' else None

for name in MODELS:
    if time.time() - T0 > LIMIT: break
    try: teach(name)
    except Exception as e:
        import traceback; traceback.print_exc(); open('scores.txt', 'a').write(f'{name}: failed: {str(e)[:200]}\n')
        torch.cuda.empty_cache() if dev == 'cuda' else None
subprocess.run('rm -rf sheets', shell=True)  # no family photos in the output
log('done')
