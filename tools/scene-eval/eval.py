"""Tests whether an image-text model can tell wedding moments apart, before it goes in the app.

Runs on the GitHub build machine. Photos come from Wikimedia Commons categories, which act as
rough answers ("Category:Saptapadi" photos should be pheras). For each photo the model scores
every description; we report how often the best description matches the category, per moment.

  python3 eval.py REQUEST OUT_DIR
"""
import collections
import io
import json
import os
import sys
import time
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "test-photos"))
import commons  # noqa: E402

import torch  # noqa: E402
from PIL import Image  # noqa: E402
from transformers import AutoModel, AutoProcessor  # noqa: E402


def as_tensor(out, kind):
    """Newer transformers return an output object from get_*_features instead of a tensor."""
    if torch.is_tensor(out):
        return out
    for name in (f"{kind}_embeds", "pooler_output"):
        v = getattr(out, name, None)
        if v is not None:
            return v
    raise TypeError(f"no {kind} features in {type(out)}")


def load(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": commons.UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return Image.open(io.BytesIO(r.read())).convert("RGB")
        except Exception as e:
            print("retry", attempt, url, e, file=sys.stderr)
            time.sleep(10 * (attempt + 1))
    return None


def main(request_path, out_dir):
    req = json.load(open(request_path))
    os.makedirs(out_dir, exist_ok=True)
    labels = req["labels"]                      # moment -> list of descriptions
    names = list(labels)
    texts = [t for n in names for t in labels[n]]
    owner = [n for n in names for _ in labels[n]]

    # Photos per moment from their categories (and extra test-wedding photos with no answer).
    photos = []
    for moment, cats in req["categories"].items():
        seen = 0
        for cat in cats:
            for f in commons.files_in(cat, max_files=req.get("per_category", 30), width=448):
                if seen >= req.get("per_category", 30):
                    break
                photos.append({"moment": moment, "title": f["title"], "thumb": f["thumb"], "page": f["page"]})
                seen += 1
    # Photos labelled by hand (e.g. from the test wedding), by Commons title.
    titled = req.get("photos", [])
    if titled:
        info = commons.infos([t["title"] for t in titled], width=448)
        for t in titled:
            i = info.get(t["title"])
            if i:
                photos.append({"moment": t["moment"], "title": t["title"], "thumb": i["thumb"], "page": i["page"]})
    print(len(photos), "photos")

    results = {}
    images = {}
    for p in photos:
        img = load(p["thumb"])
        time.sleep(0.5)
        if img is not None:
            images[p["title"] + "|" + p["moment"]] = img
    print(len(images), "photos downloaded")

    for model_id in req["models"]:
        t0 = time.time()
        try:
            model = AutoModel.from_pretrained(model_id).eval()
            proc = AutoProcessor.from_pretrained(model_id)
        except Exception as e:
            print("could not load", model_id, e)
            continue
        with torch.no_grad():
            ti = proc(text=texts, padding="max_length", max_length=64, return_tensors="pt")
            tf = as_tensor(model.get_text_features(**ti), "text")
            tf = tf / tf.norm(dim=-1, keepdim=True)
        rows, times = [], []
        for p in photos:
            img = images.get(p["title"] + "|" + p["moment"])
            if img is None:
                continue
            s = time.time()
            with torch.no_grad():
                ii = proc(images=[img], return_tensors="pt")
                f = as_tensor(model.get_image_features(**ii), "image")
                f = f / f.norm(dim=-1, keepdim=True)
                sims = (f @ tf.T)[0]
            times.append(time.time() - s)
            best = {}
            for i, n in enumerate(owner):
                best[n] = max(best.get(n, -9), float(sims[i]))
            ranked = sorted(best.items(), key=lambda kv: -kv[1])
            rows.append({"title": p["title"], "page": p["page"], "moment": p["moment"],
                         "top": ranked[:3], "hit1": ranked[0][0] == p["moment"],
                         "hit3": p["moment"] in [r[0] for r in ranked[:3]]})
        per = collections.defaultdict(lambda: [0, 0, 0])
        confusion = collections.defaultdict(collections.Counter)
        for r in rows:
            per[r["moment"]][0] += 1
            per[r["moment"]][1] += r["hit1"]
            per[r["moment"]][2] += r["hit3"]
            confusion[r["moment"]][r["top"][0][0]] += 1
        results[model_id] = {
            "seconds_per_photo_cpu": sum(times) / max(1, len(times)),
            "load_seconds": time.time() - t0,
            "overall_top1": sum(r["hit1"] for r in rows) / max(1, len(rows)),
            "overall_top3": sum(r["hit3"] for r in rows) / max(1, len(rows)),
            "per_moment": {m: {"n": v[0], "top1": v[1] / v[0], "top3": v[2] / v[0]} for m, v in per.items()},
            "confusion": {m: dict(c) for m, c in confusion.items()},
            "rows": rows,
        }
        print(model_id, "top1 %.2f top3 %.2f" % (results[model_id]["overall_top1"], results[model_id]["overall_top3"]))

    json.dump(results, open(os.path.join(out_dir, "scene-eval.json"), "w"), indent=1)
    with open(os.path.join(out_dir, "scene-eval.md"), "w") as fh:
        for model_id, r in results.items():
            fh.write(f"# {model_id}\n\noverall top-1 {r['overall_top1']:.2f}, top-3 {r['overall_top3']:.2f}, "
                     f"{r['seconds_per_photo_cpu']:.2f} s per photo on the build machine CPU\n\n")
            fh.write("| moment | photos | top-1 | top-3 | most often called |\n|---|---|---|---|---|\n")
            for m, v in sorted(r["per_moment"].items()):
                fh.write(f"| {m} | {v['n']} | {v['top1']:.2f} | {v['top3']:.2f} | {r['confusion'][m]} |\n")
            fh.write("\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
