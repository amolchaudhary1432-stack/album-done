"""Can a small classifier on SigLIP 2 image features recognise wedding moments?

Zero-shot (describing each moment in words) mixed the rituals up. Here the model only turns each
photo into features; a tiny classifier (logistic regression) learns each moment from labelled
examples (Wikimedia Commons categories plus hand-labelled test-wedding photos). Accuracy is
measured on photos the classifier has not seen (5-fold cross-validation).

  python3 probe.py REQUEST OUT_DIR
"""
import collections
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "test-photos"))
import commons  # noqa: E402
from eval import as_tensor, load  # noqa: E402

import numpy as np  # noqa: E402
import torch  # noqa: E402
from sklearn.linear_model import LogisticRegression  # noqa: E402
from sklearn.model_selection import StratifiedGroupKFold, cross_val_predict  # noqa: E402
from transformers import AutoModel, AutoProcessor  # noqa: E402


def main(request_path, out_dir):
    req = json.load(open(request_path))
    os.makedirs(out_dir, exist_ok=True)
    photos, seen = [], set()
    for moment, cats in req["categories"].items():
        n = 0
        for cat in cats:
            for f in commons.files_in(cat, max_files=req.get("per_class", 80), width=448):
                if n >= req.get("per_class", 80) or f["title"] in seen:
                    continue
                seen.add(f["title"])
                photos.append({"moment": moment, "title": f["title"], "thumb": f["thumb"],
                               "group": f["user"] + "|" + commons.day(f)})
                n += 1
        print(moment, n, "photos from categories")
    titled = req.get("photos", [])
    if titled:
        info = commons.infos([t["title"] for t in titled], width=448)
        for t in titled:
            if t["title"] in info and t["title"] not in seen:
                seen.add(t["title"])
                photos.append({"moment": t["moment"], "title": t["title"], "thumb": info[t["title"]]["thumb"],
                               "group": "test-wedding"})

    images = []
    for p in photos:
        img = load(p["thumb"])
        time.sleep(0.4)
        if img is not None:
            images.append((p, img))
    print(len(images), "photos downloaded")
    counts = collections.Counter(p["moment"] for p, _ in images)
    keep = {m for m, n in counts.items() if n >= 8}
    images = [(p, i) for p, i in images if p["moment"] in keep]

    results = {}
    for model_id in req["models"]:
        try:
            model = AutoModel.from_pretrained(model_id).eval()
            proc = AutoProcessor.from_pretrained(model_id)
        except Exception as e:
            print("could not load", model_id, e)
            continue
        size_mb = sum(p.numel() for n, p in model.named_parameters() if "text" not in n) * 4 / 1e6
        feats, t0 = [], time.time()
        with torch.no_grad():
            for _, img in images:
                f = as_tensor(model.get_image_features(**proc(images=[img], return_tensors="pt")), "image")[0]
                feats.append((f / f.norm()).numpy())
        X = np.stack(feats)
        y = np.array([p["moment"] for p, _ in images])
        clf = LogisticRegression(max_iter=3000, C=req.get("C", 2.0), class_weight="balanced")
        # Whole weddings (same uploader, same day) stay on one side, so the test is honest.
        groups = np.array([p.get("group", p["title"]) for p, _ in images])
        pred = cross_val_predict(clf, X, y, groups=groups, cv=StratifiedGroupKFold(5, shuffle=True, random_state=0))
        per = {}
        for m in sorted(set(y)):
            idx = y == m
            wrong = collections.Counter(pred[idx & (pred != m)])
            per[m] = {"n": int(idx.sum()), "recall": float((pred[idx] == m).mean()),
                      "precision": float((y[pred == m] == m).mean()) if (pred == m).any() else 0.0,
                      "mistaken_for": dict(wrong.most_common(3))}
        results[model_id] = {"accuracy": float((pred == y).mean()), "per_moment": per,
                             "seconds_per_photo": (time.time() - t0) / len(images), "image_model_mb_float32": size_mb}
        print(model_id, "accuracy %.2f" % results[model_id]["accuracy"])
        # The classifier trained on everything, for the app if it is good enough.
        clf.fit(X, y)
        np.savez(os.path.join(out_dir, model_id.replace("/", "_") + "_probe.npz"),
                 classes=clf.classes_, coef=clf.coef_.astype(np.float32), intercept=clf.intercept_.astype(np.float32))

    json.dump(results, open(os.path.join(out_dir, "probe.json"), "w"), indent=1)
    with open(os.path.join(out_dir, "probe.md"), "w") as fh:
        for model_id, r in results.items():
            fh.write(f"# {model_id}\n\naccuracy {r['accuracy']:.2f} on unseen weddings, {r['seconds_per_photo']:.2f} s per photo (build machine), "
                     f"image model {r['image_model_mb_float32']:.0f} MB as float32 (about a quarter as int8)\n\n")
            fh.write("| moment | photos | found (recall) | right when it says so (precision) | mistaken for |\n|---|---|---|---|---|\n")
            for m, v in r["per_moment"].items():
                fh.write(f"| {m} | {v['n']} | {v['recall']:.2f} | {v['precision']:.2f} | { {str(k): n for k, n in v['mistaken_for'].items()} } |\n")
            fh.write("\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
