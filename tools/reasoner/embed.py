"""SigLIP 2 (B/32, 256 px, int8 ONNX) image embeddings: python3 -I embed.py MODEL_DIR OUT.npz ROOT [ROOT ...]
Every .jpg under each ROOT, key = path relative to ROOT's parent folder name (set/IMG_0001.jpg, or
folder/file for a raw tree). Normalised 768-number vectors."""
import os, sys, time
import numpy as np, onnxruntime as ort
from PIL import Image, ImageOps
model, out, roots = sys.argv[1], sys.argv[2], sys.argv[3:]
so = ort.SessionOptions(); so.intra_op_num_threads = 4
sess = ort.InferenceSession(os.path.join(model, 'onnx/vision_model_int8.onnx'), so)
paths = []
for root in roots:
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.lower().endswith('.jpg'): paths.append((root, os.path.join(d, f)))
paths.sort()
keys, vecs, t0 = [], [], time.time()
B = 16
for i in range(0, len(paths), B):
    batch = []
    for root, p in paths[i:i + B]:
        im = ImageOps.exif_transpose(Image.open(p)).convert('RGB').resize((256, 256), Image.BICUBIC)
        a = (np.asarray(im, dtype=np.float32) / 255.0 - 0.5) / 0.5
        batch.append(a.transpose(2, 0, 1)); keys.append(os.path.relpath(p, root))
    v = sess.run(['pooler_output'], {'pixel_values': np.stack(batch)})[0]
    vecs.append(v / np.linalg.norm(v, axis=1, keepdims=True))
    if i % 800 == 0: print(i, 'of', len(paths), f'{(time.time() - t0) / max(1, i):.3f} s each', flush=True)
np.savez_compressed(out, keys=np.array(keys), vecs=np.concatenate(vecs).astype(np.float16))
print(len(keys), 'embedded in', round(time.time() - t0), 's')
