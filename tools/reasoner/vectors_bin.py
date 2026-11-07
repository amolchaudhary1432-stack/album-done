"""SigLIP vectors (.npz from embed.py) -> a plain little-endian file the JVM tools read:
count, dim, then for each photo its key (length + UTF-8) and dim float32.  python3 -I vectors_bin.py IN.npz OUT.bin"""
import struct, sys
import numpy as np
z = np.load(sys.argv[1]); keys, vecs = z['keys'], z['vecs'].astype('<f4')
with open(sys.argv[2], 'wb') as f:
    f.write(struct.pack('<ii', len(keys), vecs.shape[1]))
    for k, v in zip(keys, vecs):
        b = str(k).encode(); f.write(struct.pack('<i', len(b))); f.write(b); f.write(v.tobytes())
print(len(keys), 'vectors written')
