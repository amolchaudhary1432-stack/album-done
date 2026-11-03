"""Finds which raw photos are in the couple's printed album (a PDF of its pages): each page may hold
several photos, cropped, retouched and colour-graded, so each one is found by its distinctive
details (SIFT features), confirmed by a geometric check (the matched points must line up as the same
picture, RANSAC homography), not by a whole-image fingerprint.

    python3 match_album.py RAW_DIR INDEX.tsv PAGES_DIR OUT_DIR

PAGES_DIR holds the album's pages as images (pdftoppm). Writes OUT_DIR/album-picks.txt (one
"folder/file" per photo found, the form the scorers read), OUT_DIR/album-matches.tsv (page, number,
file, matched points, where on the page) and prints counts only.
"""
import os
import sys

import cv2
import numpy as np

RAW_SIDE = 800          # raw photos are matched at this long side
RAW_FEATURES = 500
PAGE_SIDE = 2400        # album pages at this long side
PAGE_FEATURES = 12000
RATIO = 0.75            # Lowe's ratio test
MIN_INLIERS = 15        # matched points that line up, to count as the same photo
CANDIDATES = 40         # raw photos checked closely per page


def load(path, side):
    img = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    if img is None:
        return None
    s = side / max(img.shape)
    if s < 1:
        img = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
    return img


def root_sift(des):
    des = des / (np.abs(des).sum(axis=1, keepdims=True) + 1e-7)
    return np.sqrt(des).astype(np.float32)


def main(raw, index, pages_dir, out):
    os.makedirs(out, exist_ok=True)
    rows = [l.rstrip("\n").split("\t") for l in open(index) if l.strip()]
    sift_raw = cv2.SIFT_create(nfeatures=RAW_FEATURES)
    kps, owner, descs = [], [], []
    for n, rel, *_ in rows:
        img = load(os.path.join(raw, rel), RAW_SIDE)
        if img is None:
            kps.append([])
            continue
        kp, des = sift_raw.detectAndCompute(img, None)
        kps.append([k.pt for k in kp])
        if des is not None:
            descs.append(root_sift(des))
            owner.extend([len(kps) - 1] * len(des))
    owner = np.array(owner)
    allpts = [np.array(p, dtype=np.float32) for p in kps]
    offset = np.zeros(len(kps) + 1, dtype=np.int64)
    for i, p in enumerate(kps):
        offset[i + 1] = offset[i] + len(p)
    print(f"{len(rows)} raw photos, {len(owner)} features", flush=True)
    flann = cv2.FlannBasedMatcher(dict(algorithm=1, trees=5), dict(checks=64))
    flann.add([np.vstack(descs)])
    flann.train()

    sift_page = cv2.SIFT_create(nfeatures=PAGE_FEATURES)
    pages = sorted(f for f in os.listdir(pages_dir) if f.lower().endswith((".jpg", ".png", ".ppm")))
    found = []  # (page, raw index, inliers, x0, y0, x1, y1)
    for pi, page in enumerate(pages, 1):
        img = load(os.path.join(pages_dir, page), PAGE_SIDE)
        kp, des = sift_page.detectAndCompute(img, None)
        if des is None:
            continue
        pairs = flann.knnMatch(root_sift(des), k=2)
        good = [m for m, n2 in (p for p in pairs if len(p) == 2) if m.distance < RATIO * n2.distance]
        if not good:
            continue
        who = owner[[m.trainIdx for m in good]]
        votes = np.bincount(who, minlength=len(kps))
        here = []
        for r in np.argsort(-votes)[:CANDIDATES]:
            if votes[r] < MIN_INLIERS:
                break
            mine = [m for m, w in zip(good, who) if w == r]
            src = np.float32([allpts[r][m.trainIdx - offset[r]] for m in mine])
            dst = np.float32([kp[m.queryIdx].pt for m in mine])
            H, mask = cv2.findHomography(src, dst, cv2.RANSAC, 6.0)
            inl = int(mask.sum()) if mask is not None else 0
            if H is None or inl < MIN_INLIERS:
                continue
            box = dst[mask.ravel() == 1]
            x0, y0 = box.min(axis=0)
            x1, y1 = box.max(axis=0)
            here.append((pi, int(r), inl, float(x0), float(y0), float(x1), float(y1)))
        # One photo per place on the page: near-identical frames of a burst also match; keep the
        # one with the most matched points where their places overlap.
        here.sort(key=lambda h: -h[2])
        kept = []
        for h in here:
            def overlap(a, b):
                ix = max(0, min(a[5], b[5]) - max(a[3], b[3]))
                iy = max(0, min(a[6], b[6]) - max(a[4], b[4]))
                area = min((a[5] - a[3]) * (a[6] - a[4]), (b[5] - b[3]) * (b[6] - b[4])) or 1
                return ix * iy / area
            if all(overlap(h, k) < 0.5 for k in kept):
                kept.append(h)
        found += kept
        if pi % 10 == 0:
            print(f"page {pi} of {len(pages)}: {len(found)} photos found so far", flush=True)

    picks = []
    with open(os.path.join(out, "album-matches.tsv"), "w") as fh:
        for pi, r, inl, x0, y0, x1, y1 in found:
            n, rel = rows[r][0], rows[r][1]
            fh.write(f"{pi}\t{n}\t{rel}\t{inl}\t{x0:.0f},{y0:.0f},{x1:.0f},{y1:.0f}\n")
            if rel not in picks:
                picks.append(rel)
    with open(os.path.join(out, "album-picks.txt"), "w") as fh:
        fh.writelines(p + "\n" for p in picks)
    print(f"{len(pages)} album pages: {len(found)} photos found, {len(picks)} different raw photos")


if __name__ == "__main__":
    main(*sys.argv[1:5])
