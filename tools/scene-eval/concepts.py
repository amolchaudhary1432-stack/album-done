"""Scores every photo of the labelled album sets against plain descriptions ("food on plates",
"a crowd seen from far away", "a decorated stage"...) with small image-text models, to test
whether knowing what a photo shows helps the brain guess what a person would pick.

Runs on the GitHub build machine (it downloads the models and the photos; the app never does).
Nothing here is shown to the couple: the app picks photos, it does not label rituals.

    python3 concepts.py REQUEST LABELS_DIR HARVEST_SETS_DIR TEST_PHOTOS_DIR OUT_DIR

Writes OUT_DIR/concepts.json: {"prompts": [...], "models": {model: {set: {file: [cosine...]}}}},
where file is the planner's key (folder/name, or name for the harvested sets).
"""
import io
import re
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

from eval import as_tensor  # noqa: E402

# Bench weddings with picks: set name -> folder on the test-photos branch.
BENCH = {"bench-wedding": "wedding", "bench_rajasthan": "rajasthan", "bench_garhwali": "garhwali"}


def small_url(url):
    """The same photo at about 500px: Commons thumbnails and Flickr sizes are in the address."""
    url = re.sub(r"/(\d+)px-", "/500px-", url)
    return re.sub(r"_[bhkoc](\.jpg)$", r"_z\1", url)


def fetch(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": commons.UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return Image.open(io.BytesIO(r.read())).convert("RGB")
        except Exception as e:
            if "404" in str(e):
                return None
            print("retry", attempt, url, e, file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    return None


def photos(labels_dir, sets_dir, test_dir):
    """(set, file key, loader) for every photo of every labelled set."""
    out = []
    for name in sorted(os.listdir(labels_dir)):
        if not name.endswith(".txt"):
            continue
        sid = name[:-4]
        cred = os.path.join(sets_dir, sid + ".credits.json")
        if not os.path.exists(cred):
            continue
        for f in json.load(open(cred))["files"]:
            url = small_url(f.get("thumb") or "")
            if url:
                out.append((sid, f["file"], lambda u=url: fetch(u)))
    for sid, folder in BENCH.items():
        root = os.path.join(test_dir, folder)
        for dirpath, _, files in os.walk(root):
            for fn in sorted(files):
                if fn.lower().endswith((".jpg", ".jpeg", ".png")):
                    rel = os.path.relpath(os.path.join(dirpath, fn), root)
                    out.append((sid, rel, lambda p=os.path.join(dirpath, fn): Image.open(p).convert("RGB")))
    return out


def main(request_path, labels_dir, sets_dir, test_dir, out_dir):
    req = json.load(open(request_path))
    prompts = req["prompts"]
    os.makedirs(out_dir, exist_ok=True)
    todo = photos(labels_dir, sets_dir, test_dir)
    print(len(todo), "photos", file=sys.stderr)
    images = []
    # Local photos first, then downloads until the time is up (what is loaded is still scored).
    todo.sort(key=lambda t: t[0] not in BENCH)
    stop = time.time() + 60 * req.get("download_minutes", 35)
    for sid, key, load in todo:
        if time.time() > stop:
            print("out of download time", file=sys.stderr)
            break
        img = load()
        if img is not None:
            img.thumbnail((448, 448))
            images.append((sid, key, img))
    print(len(images), "photos loaded", file=sys.stderr)
    result = {"prompts": prompts, "models": {}}
    for model_id in req["models"]:
        t0 = time.time()
        try:
            model = AutoModel.from_pretrained(model_id).eval()
            proc = AutoProcessor.from_pretrained(model_id)
        except Exception as e:
            print("could not load", model_id, e, file=sys.stderr)
            continue
        with torch.no_grad():
            ti = proc(text=prompts, padding="max_length", max_length=64, return_tensors="pt")
            tf = as_tensor(model.get_text_features(**ti), "text")
            tf = tf / tf.norm(dim=-1, keepdim=True)
        per = {}
        for i in range(0, len(images), 16):
            batch = images[i:i + 16]
            with torch.no_grad():
                ii = proc(images=[b[2] for b in batch], return_tensors="pt")
                f = as_tensor(model.get_image_features(**ii), "image")
                f = f / f.norm(dim=-1, keepdim=True)
                sims = (f @ tf.T).tolist()
            for (sid, key, _), s in zip(batch, sims):
                per.setdefault(sid, {})[key] = [round(x, 4) for x in s]
        result["models"][model_id] = per
        print(model_id, "%.0f s" % (time.time() - t0), file=sys.stderr)
    json.dump(result, open(os.path.join(out_dir, "concepts.json"), "w"))
    with open(os.path.join(out_dir, "concepts.md"), "w") as fh:
        fh.write("# Concept scores\n\n%d photos, %d prompts, models: %s\n" % (len(images), len(prompts), ", ".join(result["models"])))


if __name__ == "__main__":
    main(*sys.argv[1:6])
