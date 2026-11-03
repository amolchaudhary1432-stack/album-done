"""Cut each photo out of the harvest contact sheets: OUT/<set>/IMG_nnnn.jpg. Only sets whose plan lists
exactly IMG_0001..IMG_N (no gaps) with N equal to the cells found, so numbers cannot be misplaced."""
import os, re, sys
from PIL import Image, ImageChops
hs, plans, out = sys.argv[1:4]
ok = bad = 0
for sid in sorted(os.listdir(hs)):
    plan = os.path.join(plans, sid + '.plan.txt')
    if not os.path.exists(plan): continue
    names = [l.split('\t')[2] for l in open(plan) if l.startswith('PHOTO\t')]
    real = sorted(n for n in names if re.fullmatch(r'IMG_\d{4}\.jpg', n))
    cells = []
    for sh in sorted(os.listdir(os.path.join(hs, sid))):
        im = Image.open(os.path.join(hs, sid, sh)).convert('RGB')
        for r in range(4):
            for c in range(5):
                x, y = c * 310 + 5, r * 310 + 5
                cell = im.crop((x, y, x + 300, y + 300))
                bg = Image.new('RGB', cell.size, 'white')
                box = ImageChops.difference(cell, bg).convert('L').point(lambda v: 255 if v > 12 else 0).getbbox()
                if box and (box[2] - box[0]) > 40 and (box[3] - box[1]) > 40:
                    cells.append(cell.crop((0, 0, box[2], box[3])))
    want = [f'IMG_{i:04d}.jpg' for i in range(1, len(real) + 1)]
    if real != want or len(cells) != len(real):
        bad += 1; print('skip', sid, len(real), 'photos', len(cells), 'cells', file=sys.stderr); continue
    os.makedirs(os.path.join(out, sid), exist_ok=True)
    for n, cell in zip(want, cells): cell.save(os.path.join(out, sid, n), quality=90)
    ok += 1
print(ok, 'sets cut,', bad, 'skipped')
