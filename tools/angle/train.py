"""Trains the photo-angle model: how many degrees a photo is turned (crooked), from a 128 px copy.

    python train.py TRAIN_DIR EVAL_DIR OUT.onnx [EPOCHS]

TRAIN_DIR: freely licensed photos (assumed level). Each is turned by a random angle (half of them
not at all) and cropped so no corner shows, the way flaws.tilted makes a crooked twin.
EVAL_DIR: photos never trained on (the test albums): reports how many turned 5-9 degrees are caught
at |angle| >= LIMIT and how many level ones are wrongly marked.
"""
import glob, math, random, sys, time
import numpy as np
import torch, torch.nn as nn
from PIL import Image, ImageEnhance

SIDE = 128
MAX_ANGLE = 12.0
torch.set_num_threads(4)


def turned(img, deg):
    """Turned by deg, cropped so no empty corner shows, then the middle square at SIDE px."""
    w, h = img.size
    if abs(deg) > 0.01:
        r = img.rotate(deg, resample=Image.BICUBIC, expand=False)
        m = math.sin(math.radians(abs(deg)))
        img = r.crop((m * h * 0.6, m * w * 0.6, w - m * h * 0.6, h - m * w * 0.6))
    w, h = img.size
    s = min(w, h)
    img = img.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s)).resize((SIDE, SIDE), Image.BILINEAR)
    return img


def tensor(img):
    return torch.from_numpy(np.asarray(img, dtype=np.float32).transpose(2, 0, 1) / 255.0)


class Net(nn.Module):
    def __init__(self):
        super().__init__()
        chans = [3, 16, 32, 48, 64, 96, 128]
        layers = []
        for a, b in zip(chans, chans[1:]):
            layers += [nn.Conv2d(a, b, 3, stride=2 if b != 16 else 1, padding=1, bias=False), nn.BatchNorm2d(b), nn.ReLU(inplace=True),
                       nn.Conv2d(b, b, 3, padding=1, bias=False), nn.BatchNorm2d(b), nn.ReLU(inplace=True)]
        self.features = nn.Sequential(*layers)
        self.head = nn.Linear(128, 1)

    def forward(self, x):
        return self.head(self.features(x).mean(dim=(2, 3))).squeeze(1) * MAX_ANGLE


def load(paths):
    out = []
    for p in paths:
        try:
            img = Image.open(p).convert("RGB")
            img.thumbnail((400, 400))
            out.append(img)
        except Exception:
            pass
    return out


def batch(imgs, rng):
    xs, ys = [], []
    for img in imgs:
        deg = rng.uniform(-MAX_ANGLE, MAX_ANGLE) if rng.random() < 0.55 else rng.uniform(-0.3, 0.3)
        if rng.random() < 0.5:
            img = img.transpose(Image.FLIP_LEFT_RIGHT)
        im = turned(img, deg)
        im = ImageEnhance.Brightness(im).enhance(rng.uniform(0.7, 1.3))
        xs.append(tensor(im)); ys.append(deg)
    return torch.stack(xs), torch.tensor(ys, dtype=torch.float32)


def evaluate(net, imgs, limit):
    net.eval()
    rng = random.Random(3)
    level, crooked = [], []
    with torch.no_grad():
        for i in range(0, len(imgs), 64):
            chunk = imgs[i:i + 64]
            a = net(torch.stack([tensor(turned(im, 0)) for im in chunk]))
            b = net(torch.stack([tensor(turned(im, rng.choice([-1, 1]) * rng.uniform(5, 9))) for im in chunk]))
            level += a.abs().tolist(); crooked += b.abs().tolist()
    net.train()
    level, crooked = np.array(level), np.array(crooked)
    return {lim: (float((crooked >= lim).mean()), float((level >= lim).mean())) for lim in limit}, float(np.median(level))


def main(train_dir, eval_dir, out, epochs=20):
    epochs = int(epochs)
    train = load(sorted(glob.glob(train_dir + "/*.jpg")))
    test = load(sorted(glob.glob(eval_dir + "/*.jpg")))
    print(len(train), "training photos,", len(test), "test photos", flush=True)
    net = Net()
    opt = torch.optim.AdamW(net.parameters(), lr=2e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=3e-3, total_steps=epochs * math.ceil(len(train) / 64))
    rng = random.Random(1)
    for ep in range(epochs):
        t0 = time.time(); rng.shuffle(train); total = 0.0
        for i in range(0, len(train), 64):
            x, y = batch(train[i:i + 64], rng)
            loss = nn.functional.smooth_l1_loss(net(x), y)
            opt.zero_grad(); loss.backward(); opt.step(); sched.step()
            total += loss.item() * len(x)
        res, med = evaluate(net, test, (3.0, 4.0, 5.0))
        print(f"epoch {ep + 1}: loss {total / len(train):.2f}, {time.time() - t0:.0f}s; level median {med:.1f} deg; "
              + "; ".join(f">= {k:.0f} deg: crooked caught {c:.0%}, level marked {l:.0%}" for k, (c, l) in res.items()), flush=True)
    net.eval()
    torch.save(net.state_dict(), out + ".pt")
    torch.onnx.export(net, torch.zeros(1, 3, SIDE, SIDE), out, input_names=["image"], output_names=["degrees"],
                      dynamic_axes={"image": {0: "n"}, "degrees": {0: "n"}}, opset_version=13, dynamo=False)
    print("saved", out)


if __name__ == "__main__":
    main(*sys.argv[1:5])
