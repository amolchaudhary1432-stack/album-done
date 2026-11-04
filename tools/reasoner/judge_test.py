"""Ready-made one-pass judges (decision models) on the near-twin frame choices, no training.

    python3 judge_test.py run MODEL INDEX.json PHOTOS_DIR LABELS.tsv OUT.tsv   (build machine)
    python3 judge_test.py score LABELS.tsv INDEX.json OUT.tsv [OUT2.tsv ...]   (anywhere)

MODEL: intern-0.8b | intern-2b | intern-4b | qrealign-mini (DEVICE=cuda on a GPU). These models answer typed questions in one forward
pass, with no text written, which is what a phone can afford. Each group of near-twins (2-4 photos,
seconds apart, from freely licensed Commons sets) is asked "which would the family keep?", and each
photo on its own "how good is it for the album?". Scored like frame_judge2.py: of every pair the
helpers ranked at full size (tools/reasoner/frame-labels.tsv, "equal" groups left out), how often
the model puts the same photo first. Our own frame judge: 57-59% on held-back pairs.
"""
import json, os, sys, time

LETTERS = 'ABCD'
STATE = ("Photos from an Indian wedding (any community), shot within seconds of each other. "
         "A family is choosing which ones go into their wedding album: the best moment, everyone's "
         "eyes open and faces at their best, nobody blocked or cut off, sharp, well lit.")


def small(src, dst, side=640):
    from PIL import Image
    with Image.open(src) as im:
        im = im.convert('RGB'); im.thumbnail((side, side)); im.save(dst, quality=90)


def run(model, index, photos, labels, out):
    deadline = time.time() + float(os.environ.get('DEADLINE', '5400'))
    wanted = {l.split('\t')[0] for l in open(labels) if l.strip() and l.split('\t')[2] != 'equal'}
    groups = [g for g in json.load(open(index)) if g['id'] in wanted]
    tmp = 'judge-tmp'; os.makedirs(tmp, exist_ok=True)
    done = set()
    if os.path.exists(out): done = {l.split('\t')[0] for l in open(out)}
    f = open(out, 'a')
    if model.startswith('intern'):
        sys.path.insert(0, os.environ['CKPT'])
        from inference import DecisionEngine
        dev = os.environ.get('DEVICE', 'cpu')
        eng = DecisionEngine(checkpoint=os.environ['CKPT'], device=dev, dtype=os.environ.get('DTYPE', 'float16' if dev == 'cuda' else 'float32'))
    else:
        import torch
        from PIL import Image
        from transformers import AutoModelForImageTextToText, AutoProcessor
        torch.set_num_threads(os.cpu_count())
        proc = AutoProcessor.from_pretrained(os.environ['CKPT'])
        dev = os.environ.get('DEVICE', 'cpu')
        net = AutoModelForImageTextToText.from_pretrained(os.environ['CKPT'], dtype=torch.float16 if dev == 'cuda' else torch.float32).to(dev).eval()
        levels, weights = ['excellent', 'good', 'fair', 'poor', 'bad'], [1.0, 0.75, 0.5, 0.25, 0.0]
        ids = [proc.tokenizer(' ' + w, add_special_tokens=False).input_ids[0] for w in levels]

        def rate(path, ask):
            msg = [{'role': 'user', 'content': [{'type': 'image'}, {'type': 'text', 'text': f'How would you rate the {ask} of this image?'}]}]
            text = proc.apply_chat_template(msg, add_generation_prompt=True) + f'The {ask} of the image is'
            inp = proc(text=[text], images=[Image.open(path).convert('RGB')], return_tensors='pt').to(dev)
            with torch.inference_mode():
                p = net(**inp).logits[0, -1, ids].float().softmax(-1).cpu()
            return float((p * torch.tensor(weights)).sum())
    for g in groups:
        if g['id'] in done: continue
        if time.time() > deadline: print('deadline reached'); break
        paths = []
        for k, name in enumerate(g['photos']):
            p = os.path.join(tmp, f"{g['id']}-{k}.jpg"); small(os.path.join(photos, g['set'], name), p); paths.append(p)
        t = time.time()
        if model.startswith('intern'):
            group = eng.predict({'state': STATE, 'images': paths, 'questions': {'keep': {
                'type': 'choice', 'instructions': 'Which one photo would the family keep for the album?',
                'criteria': {LETTERS[k]: f'photo {k + 1}' for k in range(len(paths))}}}})['answers']['keep']['probabilities']
            each = []
            for p in paths:
                a = eng.predict({'state': STATE, 'images': [p], 'questions': {
                    'worth': {'type': 'score', 'instructions': 'How good is this photo for the wedding album?',
                              'criteria': ['poor', 'fair', 'good', 'excellent']},
                    'keep': {'type': 'noul', 'instructions': 'The family would put this photo in their wedding album.'}}})['answers']
                each.append((a['worth']['score'], a['keep']['noul']))
            cols = [f"{LETTERS[k]}:{group.get(LETTERS[k], 0):.4f}:{each[k][0]:.4f}:{each[k][1]:.4f}" for k in range(len(paths))]
        else:
            cols = [f"{LETTERS[k]}:{rate(p, 'quality'):.4f}:{rate(p, 'aesthetics'):.4f}:0" for k, p in enumerate(paths)]
        f.write(f"{g['id']}\t{time.time() - t:.1f}\t" + '\t'.join(cols) + '\n'); f.flush()
        print(g['id'], f'{time.time() - t:.1f}s', flush=True)


def score(labels, index, outs):
    split = {g['id']: g['split'] for g in json.load(open(index))}
    lab = {}
    for l in open(labels):
        c = l.rstrip('\n').split('\t')
        if len(c) >= 3 and c[2] != 'equal': lab[c[0]] = (c[1].split('>'), c[2])
    for out in outs:
        rows = {}
        for l in open(out):
            c = l.rstrip('\n').split('\t')
            rows[c[0]] = (float(c[1]), {x.split(':')[0]: [float(v) for v in x.split(':')[1:]] for x in c[2:]})
        print(f"== {out}: {len(rows)} groups, {sum(r[0] for r in rows.values()) / max(1, len(rows)):.1f} s per group")
        for col, what in enumerate(['group question / quality', 'each photo: score / aesthetics', 'each photo: keep yes/no']):
            for name, keep in [('all', lambda g: True), ('held back', lambda g: split.get(g) == 'test'), ('sure', lambda g: lab[g][1] == 'sure')]:
                agree = n = top = groups = 0
                for g, (_, vals) in rows.items():
                    if g not in lab or not keep(g): continue
                    order = [x for x in lab[g][0] if x in vals]
                    v = {x: vals[x][col] for x in order}
                    if len(set(v.values())) <= 1: continue
                    groups += 1; top += max(v, key=v.get) == order[0]
                    for i in range(len(order)):
                        for j in range(i + 1, len(order)):
                            if v[order[i]] != v[order[j]]: n += 1; agree += v[order[i]] > v[order[j]]
                if n: print(f"  {what:32s} {name:9s} pairs {agree / n:5.1%} of {n:3d} | best photo first {top / groups:5.1%} of {groups}")


if __name__ == '__main__':
    if sys.argv[1] == 'run': run(*sys.argv[2:7])
    else: score(sys.argv[2], sys.argv[3], sys.argv[4:])
