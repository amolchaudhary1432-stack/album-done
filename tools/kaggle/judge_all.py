"""Kaggle GPU: every near-twin group judged by the ready-made one-pass judges (untrained).

Input: the album-done-twins dataset (freely licensed Commons photos, index.json, frame-labels.tsv,
judge_test.py). Output: one TSV of answers per judge and the scores, in the working folder.
"""
import glob, os, subprocess, sys, time
subprocess.run([sys.executable, '-m', 'pip', 'install', '-q', 'transformers==5.14.1', 'huggingface_hub'], check=True)
root = os.path.dirname(glob.glob('/kaggle/input/**/index.json', recursive=True)[0])
print('data at', root, flush=True)
sys.path.insert(0, root)
from huggingface_hub import snapshot_download
JUDGES = [('qrealign-mini', 'q-future/Q-ReAlign-Mini-0.8B', 'float32'), ('intern-0.8b', 'internlm/Intern-Decision-0.8B', 'float32'),
          ('intern-2b', 'internlm/Intern-Decision-2B', 'float32'), ('intern-4b', 'internlm/Intern-Decision-4B', 'float16')]
start = time.time()
for k, (name, repo, dtype) in enumerate(JUDGES):
    ck = snapshot_download(repo, local_dir=f'/kaggle/tmp/{name}')
    env = dict(os.environ, CKPT=ck, DEVICE='cuda', DTYPE=dtype, DEADLINE=str(max(600, (10.5 * 3600 - (time.time() - start)) / (len(JUDGES) - k))))
    r = subprocess.run([sys.executable, os.path.join(root, 'judge_test.py'), 'run', name, f'{root}/index.json', f'{root}/photos',
                        f'{root}/frame-labels.tsv', f'{name}.tsv'], env=env, capture_output=True, text=True)
    print(name, 'exit', r.returncode, r.stdout[-600:], r.stderr[-3000:], flush=True)
outs = [f'{n}.tsv' for n, _, _ in JUDGES if os.path.exists(f'{n}.tsv')]
r = subprocess.run([sys.executable, os.path.join(root, 'judge_test.py'), 'score', f'{root}/frame-labels.tsv', f'{root}/index.json'] + outs, capture_output=True, text=True)
print(r.stdout, r.stderr[-2000:])
open('scores.txt', 'w').write(r.stdout)
