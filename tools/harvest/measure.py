"""Measures photos the way the app does (app/src/main/java/com/albumdone/app/analysis/), on a computer.

Used to gather numbers from many freely licensed photos without the phone: the same models
(YuNet, MediaPipe Face Landmarker, SFace, EfficientDet-Lite0) and the same rules, ported line by
line from PhotoAnalyzer.kt, ImageMetrics.kt, FaceMetrics.kt, YuNet.kt and SceneRules.kt. Images
are scaled the way Android does (plain bilinear, no smoothing), because blur measures depend on it.
check_parity.py compares the result with the app's own measurements of the same photos.

    python3 measure.py MODELS_DIR OUT.jsonl PHOTO... (or a folder: every photo inside, sub-folders = events)

Writes one JSON line per photo: the planner's photo fields, and per face the planner's face
fields plus the 128-number face fingerprint (base64) for grouping people.
"""
import base64, datetime, json, math, os, sys

import numpy as np
from PIL import Image, ImageOps

TARGET_LONG_SIDE = 1280
QUALITY_WIDTH = 256
MAX_FACES = 20
FACE_SIDE = 128
CROP_AROUND_FACE = 2.2
CROP_SIDE = 256.0
MIN_RECOGNISE_FACE = 0.04
MIN_UNREAD_SCORE = 0.7
MIN_RECOGNISE_SCORE = 0.8
MIN_MESH_FACE = 0.015
FINDER_LONG_SIDE = 960
YUNET_THRESHOLD = 0.6
NMS_OVERLAP = 0.3

# ---------------------------------------------------------------- pixels, the Android way


def scaled(rgb, w, h):
    """Bitmap.createScaledBitmap(filter = true): bilinear from the 4 nearest pixels, no smoothing."""
    sh, sw = rgb.shape[:2]
    if (sw, sh) == (w, h):
        return rgb.astype(np.float32)
    xs = (np.arange(w) + 0.5) * sw / w - 0.5
    ys = (np.arange(h) + 0.5) * sh / h - 0.5
    xs = np.clip(xs, 0, sw - 1); ys = np.clip(ys, 0, sh - 1)
    x0 = np.floor(xs).astype(int); y0 = np.floor(ys).astype(int)
    x1 = np.minimum(x0 + 1, sw - 1); y1 = np.minimum(y0 + 1, sh - 1)
    fx = (xs - x0)[None, :, None]; fy = (ys - y0)[:, None, None]
    img = rgb.astype(np.float32)
    top = img[y0][:, x0] * (1 - fx) + img[y0][:, x1] * fx
    bot = img[y1][:, x0] * (1 - fx) + img[y1][:, x1] * fx
    return np.rint(top * (1 - fy) + bot * fy)


def luma(rgb):
    return 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]


def gray_of(rgb, w, h):
    return luma(scaled(rgb, w, h)).astype(np.float32)


def sample_size_for(long_side, target):
    s = 1
    while long_side // (s * 2) >= target:
        s *= 2
    return s


# ---------------------------------------------------------------- ImageMetrics.kt


def quality(gray):
    h, w = gray.shape
    n = max(1, gray.size)
    q = {"brightness": float(gray.sum() / n), "darkFraction": float((gray < 16).sum() / n),
         "brightFraction": float((gray > 245).sum() / n), "sharpness": 0.0}
    if w >= 3 and h >= 3:
        lap = (4 * gray[1:-1, 1:-1] - gray[1:-1, :-2] - gray[1:-1, 2:] - gray[:-2, 1:-1] - gray[2:, 1:-1]).astype(np.float64)
        q["sharpness"] = float((lap * lap).mean() - lap.mean() ** 2)
    return q


def peak_sharpness(gray, grid=4):
    h, w = gray.shape
    if w < grid * 3 or h < grid * 3:
        return quality(gray)["sharpness"]
    lap = np.zeros((h, w), np.float64)
    lap[1:-1, 1:-1] = 4 * gray[1:-1, 1:-1] - gray[1:-1, :-2] - gray[1:-1, 2:] - gray[:-2, 1:-1] - gray[2:, 1:-1]
    best = 0.0
    for gy in range(grid):
        for gx in range(grid):
            x0, x1 = gx * w // grid, (gx + 1) * w // grid
            y0, y1 = gy * h // grid, (gy + 1) * h // grid
            cell = lap[max(1, y0):min(h - 1, y1), max(1, x0):min(w - 1, x1)]
            if cell.size:
                best = max(best, float((cell * cell).mean() - cell.mean() ** 2))
    return best


def d_hash(gray9x8):
    bits, bit = 0, 0
    for y in range(8):
        for x in range(8):
            if gray9x8[y, x] > gray9x8[y, x + 1]:
                bits |= 1 << bit
            bit += 1
    return bits - (1 << 64) if bits >= 1 << 63 else bits  # as a signed Kotlin Long


def tilt_degrees(gray):
    h, w = gray.shape
    if w < 8 or h < 8:
        return None
    g = gray.astype(np.float64)
    gx = (g[:-2, 2:] + 2 * g[1:-1, 2:] + g[2:, 2:]) - (g[:-2, :-2] + 2 * g[1:-1, :-2] + g[2:, :-2])
    gy = (g[2:, :-2] + 2 * g[2:, 1:-1] + g[2:, 2:]) - (g[:-2, :-2] + 2 * g[:-2, 1:-1] + g[:-2, 2:])
    mag = np.sqrt(gx * gx + gy * gy)
    keep = mag >= 80
    if not keep.any():
        return None
    a = np.degrees(np.arctan2(gy[keep], gx[keep]))
    a = ((a % 90.0) + 90.0) % 90.0
    a = np.where(a >= 45.0, a - 90.0, a)
    bins = 180
    idx = np.clip(((a + 45.0) * 2.0).astype(int), 0, bins - 1)
    hist = np.bincount(idx, weights=mag[keep], minlength=bins)
    total = hist.sum()
    if total <= 0:
        return None
    window = [sum(hist[(b + k) % bins] for k in range(-2, 3)) for b in range(bins)]
    best = int(np.argmax(window))
    if window[best] / total < 0.12:
        return None
    peak = best
    for k in range(-2, 3):
        if hist[(best + k) % bins] > hist[peak]:
            peak = (best + k) % bins
    s = wsum = 0.0
    for k in range(-2, 3):
        wgt = hist[(peak + k) % bins]
        s += wgt * ((peak + k) / 2.0 - 45.0 + 0.25)
        wsum += wgt
    return abs(s / wsum)


def region_mean(gray, box, exclude=None):
    h, w = gray.shape
    x0 = int(np.clip(int(box[0] * w), 0, w)); x1 = int(np.clip(int(box[2] * w), 0, w))
    y0 = int(np.clip(int(box[1] * h), 0, h)); y1 = int(np.clip(int(box[3] * h), 0, h))
    if x1 <= x0 or y1 <= y0:
        return 0.0
    region = gray[y0:y1, x0:x1].astype(np.float64)
    if exclude is None:
        return float(region.mean())
    fx = (np.arange(x0, x1) + 0.5) / w; fy = (np.arange(y0, y1) + 0.5) / h
    inside = (fx[None, :] >= exclude[0]) & (fx[None, :] <= exclude[2]) & (fy[:, None] >= exclude[1]) & (fy[:, None] <= exclude[3])
    kept = region[~inside]
    return float(kept.mean()) if kept.size else 0.0


def is_backlit(face, surround):
    return surround >= 170.0 and face < 0.45 * surround and face < 75.0


# ---------------------------------------------------------------- boxes

def area(b):
    return max(0.0, b[2] - b[0]) * max(0.0, b[3] - b[1])


def intersection(a, b):
    return max(0.0, min(a[2], b[2]) - max(a[0], b[0])) * max(0.0, min(a[3], b[3]) - max(a[1], b[1]))


def iou(a, b):
    i = intersection(a, b)
    u = area(a) + area(b) - i
    return i / u if u > 0 else 0.0


# ---------------------------------------------------------------- YuNet.kt + FaceFinder.kt


class Finder:
    def __init__(self, path):
        import onnxruntime as ort
        self.sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        self.input = self.sess.get_inputs()[0].name
        self.names = [o.name for o in self.sess.get_outputs()]

    def find(self, rgb):
        bh, bw = rgb.shape[:2]
        scale = min(1.0, FINDER_LONG_SIDE / max(bw, bh))
        w, h = max(1, round(bw * scale)), max(1, round(bh * scale))
        small = scaled(rgb, w, h)
        pw, ph = (w + 31) // 32 * 32, (h + 31) // 32 * 32
        data = np.zeros((3, ph, pw), np.float32)
        data[0, :h, :w] = small[..., 2]; data[1, :h, :w] = small[..., 1]; data[2, :h, :w] = small[..., 0]
        outs = dict(zip(self.names, self.sess.run(None, {self.input: data[None]})))
        found = []
        for s in (8, 16, 32):
            cls = outs[f"cls_{s}"].reshape(-1); obj = outs[f"obj_{s}"].reshape(-1)
            bbox = outs[f"bbox_{s}"].reshape(-1, 4); kps = outs[f"kps_{s}"].reshape(-1, 10)
            cols = pw // s
            score = np.sqrt(np.clip(cls, 0, 1) * np.clip(obj, 0, 1))
            for i in np.nonzero(score >= YUNET_THRESHOLD)[0]:
                r, c = divmod(int(i), cols)
                cx, cy = (c + bbox[i, 0]) * s, (r + bbox[i, 1]) * s
                bw_, bh_ = math.exp(bbox[i, 2]) * s, math.exp(bbox[i, 3]) * s
                box = ((cx - bw_ / 2) / w, (cy - bh_ / 2) / h, (cx + bw_ / 2) / w, (cy + bh_ / 2) / h)
                pts = [((kps[i, k] + c) * s / w) if k % 2 == 0 else ((kps[i, k] + r) * s / h) for k in range(10)]
                found.append({"box": tuple(float(v) for v in box), "points": [float(p) for p in pts], "score": float(score[i])})
        kept = []
        for f in sorted(found, key=lambda f: -f["score"]):
            if all(iou(k["box"], f["box"]) <= NMS_OVERLAP for k in kept):
                kept.append(f)
        return kept


# ---------------------------------------------------------------- FaceMetrics.kt

NOSE_TIP, CONTOUR_L, CONTOUR_R = 1, 234, 454
EYE_L_OUT, EYE_L_IN, EYE_R_IN, EYE_R_OUT = 33, 133, 362, 263
IRIS_L, IRIS_R = 468, 473
MOUTH_L, MOUTH_R = 61, 291
SKIN_PATCHES = (50, 280, 151, 199, 6)
CLOSED_EYE, HALF_BLINK, LOOK_DOWN = 0.55, 0.45, 0.45
SMILE, LAUGH_JAW, TALK_JAW_MIN, TALK_JAW_MAX = 0.35, 0.30, 0.12, 0.60
EYE_RANGE_DEGREES, MAX_HEAD_TURN, MAX_GAZE_ACROSS, MAX_GAZE_UP_DOWN = 35.0, 50.0, 20.0, 15.0
YUNET_HEIGHT_TO_MESH = 0.87
SFACE_TEMPLATE = np.array([[38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366], [41.5493, 92.3655], [70.7299, 92.2041]])


def cut_off_fraction(box):
    a = area(box)
    if a <= 0:
        return 0.0
    return float(np.clip(1 - intersection(box, (0, 0, 1, 1)) / a, 0, 1))


def edge_cut_off(box, aspect):
    bw, bh = box[2] - box[0], box[3] - box[1]
    if bw <= 0 or bh <= 0:
        return 0.0
    ratio = bw * aspect / bh
    at_side = box[0] <= 0.02 or box[2] >= 0.98
    at_tb = box[1] <= 0.02 or box[3] >= 0.98
    if at_side and ratio < 0.55:
        v = 1 - ratio / 0.9
    elif at_tb and ratio > 0.9 / 0.55:
        v = 1 - 0.9 / ratio
    else:
        v = 0.0
    return float(np.clip(v, 0, 1))


def eye_state(b):
    l, r = b.get("eyeBlinkLeft", 0.0), b.get("eyeBlinkRight", 0.0)
    down = (b.get("eyeLookDownLeft", 0.0) + b.get("eyeLookDownRight", 0.0)) / 2
    avg = (l + r) / 2
    if min(l, r) >= CLOSED_EYE and down < 0.75:
        return "CLOSED"
    if avg >= HALF_BLINK and down >= LOOK_DOWN:
        return "LOOKING_DOWN"
    if min(l, r) >= CLOSED_EYE:
        return "LOOKING_DOWN"
    if avg >= HALF_BLINK:
        return "HALF"
    return "OPEN"


def smile_of(b):
    return (b.get("mouthSmileLeft", 0.0) + b.get("mouthSmileRight", 0.0)) / 2


def expression(b):
    smile, jaw = smile_of(b), b.get("jawOpen", 0.0)
    brow = b.get("browInnerUp", 0.0)
    frown = (b.get("mouthFrownLeft", 0.0) + b.get("mouthFrownRight", 0.0)) / 2
    squint = (b.get("eyeSquintLeft", 0.0) + b.get("eyeSquintRight", 0.0)) / 2
    funnel, pucker = b.get("mouthFunnel", 0.0), b.get("mouthPucker", 0.0)
    if smile >= SMILE and jaw >= LAUGH_JAW:
        return "LAUGH"
    if smile >= SMILE:
        return "SMILE"
    if brow >= 0.45 and (frown >= 0.15 or squint >= 0.45):
        return "EMOTIONAL"
    if smile < 0.3 and (TALK_JAW_MIN <= jaw <= TALK_JAW_MAX or funnel >= 0.3 or (pucker >= 0.45 and jaw >= 0.08)):
        return "MID_TALK"
    return "NATURAL"


def eye_direction(b):
    g = lambda n: b.get(n, 0.0)
    across = ((g("eyeLookOutLeft") + g("eyeLookInRight")) - (g("eyeLookInLeft") + g("eyeLookOutRight"))) / 2
    up = ((g("eyeLookUpLeft") + g("eyeLookUpRight")) - (g("eyeLookDownLeft") + g("eyeLookDownRight"))) / 2
    return across, up


def head_direction(m):
    if m is None or len(m) < 16:
        return 0.0, 0.0
    by_columns = abs(m[14]) >= abs(m[11])
    fx = m[8] if by_columns else m[2]
    fy = m[9] if by_columns else m[6]
    fz = m[10]
    return math.degrees(math.atan2(fx, fz)), math.degrees(math.atan2(fy, math.hypot(fx, fz)))


def looking_at_camera(turn, tilt, across, up, eyes):
    a = turn + across * EYE_RANGE_DEGREES
    u = tilt + up * EYE_RANGE_DEGREES
    return abs(turn) <= MAX_HEAD_TURN and abs(a) <= MAX_GAZE_ACROSS and abs(u) <= MAX_GAZE_UP_DOWN and eyes != "LOOKING_DOWN"


def yaw_ratio(nose, cl, cr):
    dl, dr = nose - cl, cr - nose
    s = dl + dr
    return 0.0 if s <= 1e-6 else float(np.clip((dl - dr) / s, -1, 1))


def roll(lx, ly, rx, ry):
    return math.degrees(math.atan2(ry - ly, rx - lx))


def iris_offset(xs):
    def one(iris, a, b):
        lo, hi = min(xs[a], xs[b]), max(xs[a], xs[b])
        return 0.0 if hi - lo <= 1e-6 else ((xs[iris] - lo) / (hi - lo) - 0.5) * 2
    return float(np.clip((one(IRIS_L, EYE_L_OUT, EYE_L_IN) + one(IRIS_R, EYE_R_IN, EYE_R_OUT)) / 2, -1, 1))


def direction_x(yr, iris):
    return float(np.clip(yr * 1.5 + iris * 0.8, -1, 1))


def read_mesh(xs, ys, blend, matrix, aspect):
    n = min(len(xs), 468)
    box = (float(min(xs[:n])), float(min(ys[:n])), float(max(xs[:n])), float(max(ys[:n])))
    eyes = eye_state(blend)
    yr = yaw_ratio(xs[NOSE_TIP], xs[CONTOUR_L], xs[CONTOUR_R]) if len(xs) > CONTOUR_R else 0.0
    turn, tilt = head_direction(matrix) if matrix is not None else (yr * 60.0, 0.0)
    across, up = eye_direction(blend)
    iris = iris_offset(xs) if len(xs) > IRIS_R else 0.0
    return {"box": box, "eyes": eyes, "expression": expression(blend), "smile": smile_of(blend),
            "yaw": abs(turn), "directionX": direction_x(yr, iris),
            "lookingAtCamera": looking_at_camera(turn, tilt, across, up, eyes), "cutOff": cut_off_fraction(box)}


def read_points(found_box, pts, aspect):
    cy = (found_box[1] + found_box[3]) / 2
    h = (found_box[3] - found_box[1]) * YUNET_HEIGHT_TO_MESH
    box = (found_box[0], cy - h / 2, found_box[2], cy + h / 2)
    lx, rx, nose = pts[0], pts[2], pts[4]
    gap = max(1e-4, abs(rx - lx))
    turn = float(np.clip(((nose - (lx + rx) / 2) / gap) * 2, -1, 1))
    return {"box": box, "eyes": "UNKNOWN", "expression": "NATURAL", "smile": 0.0, "yaw": abs(turn) * 60.0,
            "directionX": direction_x(turn, 0.0), "lookingAtCamera": False,
            "cutOff": max(cut_off_fraction(box), edge_cut_off(box, aspect))}


def occlusion(patches):
    if len(patches) < 3:
        return 0.0
    def chroma(p):
        s = p[0] + p[1] + p[2] + 1
        return p[0] / s, p[1] / s
    lum = lambda p: 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]
    cr = sorted(chroma(p)[0] for p in patches); cg = sorted(chroma(p)[1] for p in patches)
    med_r, med_g = cr[len(cr) // 2], cg[len(cg) // 2]
    lums = sorted(lum(p) for p in patches)
    med_l = max(1.0, lums[len(lums) // 2])
    worst = 0.0
    for p in patches:
        r, g = chroma(p)
        colour = math.hypot(r - med_r, g - med_g)
        light = lum(p) / med_l
        cs = float(np.clip((colour - 0.04) / 0.08, 0, 1))
        ls = 1.0 if light < 0.45 or light > 2.2 else 0.0
        worst = max(worst, max(cs, ls if cs > 0.3 else 0.0))
    return worst


def similarity(src, dst):
    sm, dm = src.mean(0), dst.mean(0)
    p, q = src - sm, dst - dm
    den = (p * p).sum()
    if den <= 1e-12:
        return 1.0, 0.0, dm[0] - sm[0], dm[1] - sm[1]
    a = (p[:, 0] * q[:, 0] + p[:, 1] * q[:, 1]).sum() / den
    b = (p[:, 0] * q[:, 1] - p[:, 1] * q[:, 0]).sum() / den
    return a, b, dm[0] - (a * sm[0] - b * sm[1]), dm[1] - (b * sm[0] + a * sm[1])


def alignment_points(xs, ys, w, h):
    if len(xs) > IRIS_R:
        le, re = (xs[IRIS_L], ys[IRIS_L]), (xs[IRIS_R], ys[IRIS_R])
    else:
        le = ((xs[EYE_L_OUT] + xs[EYE_L_IN]) / 2, (ys[EYE_L_OUT] + ys[EYE_L_IN]) / 2)
        re = ((xs[EYE_R_IN] + xs[EYE_R_OUT]) / 2, (ys[EYE_R_IN] + ys[EYE_R_OUT]) / 2)
    pts = [le, re, (xs[NOSE_TIP], ys[NOSE_TIP]), (xs[MOUTH_L], ys[MOUTH_L]), (xs[MOUTH_R], ys[MOUTH_R])]
    return np.array([[x * w, y * h] for x, y in pts])


# ---------------------------------------------------------------- the analyser


class Analyzer:
    def __init__(self, models):
        import mediapipe as mp
        from mediapipe.tasks import python as mpt
        from mediapipe.tasks.python import vision
        import onnxruntime as ort
        self.mp, self.vision = mp, vision

        def first(*names):
            for n in names:
                p = os.path.join(models, n)
                if os.path.exists(p):
                    return p
            raise FileNotFoundError(names[0])
        self.finder = Finder(first("face_detection_yunet_2026may.onnx", "yunet.onnx"))
        self.sface = ort.InferenceSession(first("face_recognition_sface_2021dec.onnx", "sface.onnx"), providers=["CPUExecutionProvider"])
        self.landmarker = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
            base_options=mpt.BaseOptions(model_asset_path=first("face_landmarker.task")),
            running_mode=vision.RunningMode.IMAGE, num_faces=MAX_FACES,
            min_face_detection_confidence=0.5, min_face_presence_confidence=0.5,
            output_face_blendshapes=True, output_facial_transformation_matrixes=True))
        self.objects = vision.ObjectDetector.create_from_options(vision.ObjectDetectorOptions(
            base_options=mpt.BaseOptions(model_asset_path=first("efficientdet_lite0.tflite")),
            running_mode=vision.RunningMode.IMAGE, max_results=30, score_threshold=0.35,
            category_allowlist=["person", "cell phone"]))

    def _image(self, rgb):
        return self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb.astype(np.uint8)))

    def analyze(self, path, taken_at=0):
        img = Image.open(path)
        full_w, full_h = img.size
        exif_date = 0
        orientation = 1
        try:
            ex = img.getexif()
            orientation = ex.get(0x0112, 1)
            raw = ex.get_ifd(0x8769).get(0x9003) or ex.get(0x0132)
            if raw:
                exif_date = int(datetime.datetime.strptime(raw.strip(), "%Y:%m:%d %H:%M:%S").replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)
        except Exception:
            pass
        sample = sample_size_for(max(full_w, full_h), TARGET_LONG_SIDE)
        if sample > 1:
            img = img.reduce(sample)
        img = ImageOps.exif_transpose(img).convert("RGB")
        rgb = np.asarray(img, dtype=np.uint8)
        H, W = rgb.shape[:2]

        qw, qh = QUALITY_WIDTH, max(1, round(H * QUALITY_WIDTH / W))
        gray = gray_of(rgb, qw, qh)
        q = quality(gray)
        peak = peak_sharpness(gray)
        tilt = tilt_degrees(gray) or 0.0
        h = d_hash(gray_of(rgb, 9, 8))

        found = self.objects.detect(self._image(rgb)).detections
        def boxes(ok):
            return [(d.bounding_box.origin_x / W, d.bounding_box.origin_y / H,
                     (d.bounding_box.origin_x + d.bounding_box.width) / W, (d.bounding_box.origin_y + d.bounding_box.height) / H)
                    for d in found if any(ok(c) for c in d.categories)]
        people = boxes(lambda c: c.category_name == "person" and c.score >= 0.45)
        sure_people = boxes(lambda c: c.category_name == "person" and c.score >= 0.6)
        phones = boxes(lambda c: c.category_name == "cell phone")

        faces = [self._measure(rgb, gray, f, m) for f, m in self._find_faces(rgb)]
        backs = foreground_backs(sure_people, [f["box"] for f in faces])
        rotated = orientation in (6, 8)
        return {
            "takenAt": exif_date or taken_at, "width": full_h if rotated else full_w, "height": full_w if rotated else full_h,
            "sharpness": q["sharpness"], "peakSharpness": peak, "brightness": q["brightness"],
            "darkFraction": q["darkFraction"], "brightFraction": q["brightFraction"], "hash": h, "tilt": tilt,
            "phones": phones, "foregroundBacks": backs,
            "peopleArea": float(np.clip(sum(area(b) for b in people), 0, 1)), "faces": faces,
        }

    def _find_faces(self, rgb):
        found = sorted(self.finder.find(rgb), key=lambda f: -(f["box"][2] - f["box"][0]))[:MAX_FACES]
        out = []
        for f in found:
            mesh = self._read_crop(rgb, f) if f["box"][2] - f["box"][0] >= MIN_MESH_FACE else None
            if mesh is not None or f["score"] >= MIN_UNREAD_SCORE:
                out.append((f, mesh))
        return out

    def _read_crop(self, rgb, f):
        H, W = rgb.shape[:2]
        b = f["box"]
        side = CROP_AROUND_FACE * max((b[2] - b[0]) * W, (b[3] - b[1]) * H)
        cx, cy = (b[0] + b[2]) / 2 * W, (b[1] + b[3]) / 2 * H
        x0 = int(np.clip(int(cx - side / 2), 0, W - 1)); y0 = int(np.clip(int(cy - side / 2), 0, H - 1))
        x1 = int(np.clip(int(cx + side / 2), x0 + 1, W)); y1 = int(np.clip(int(cy + side / 2), y0 + 1, H))
        if x1 - x0 < 16 or y1 - y0 < 16:
            return None
        crop = rgb[y0:y1, x0:x1]
        s = CROP_SIDE / max(crop.shape[1], crop.shape[0])
        sized = scaled(crop, max(1, round(crop.shape[1] * s)), max(1, round(crop.shape[0] * s)))
        res = self.landmarker.detect(self._image(sized))
        for i, pts in enumerate(res.face_landmarks):
            xs = np.array([x0 / W + p.x * (x1 - x0) / W for p in pts])
            ys = np.array([y0 / H + p.y * (y1 - y0) / H for p in pts])
            mx, my = xs[:468].mean(), ys[:468].mean()
            if b[0] <= mx <= b[2] and b[1] <= my <= b[3]:
                blend = {c.category_name: c.score for c in res.face_blendshapes[i]} if res.face_blendshapes else {}
                matrix = np.asarray(res.facial_transformation_matrixes[i]).reshape(-1).tolist() if res.facial_transformation_matrixes else None
                return xs, ys, blend, matrix
        return None

    def _measure(self, rgb, gray, found, mesh):
        H, W = rgb.shape[:2]
        aspect = W / H
        r = read_mesh(mesh[0], mesh[1], mesh[2], mesh[3], aspect) if mesh else read_points(found["box"], found["points"], aspect)
        box = r["box"]
        px = int(np.clip(int(box[0] * W), 0, W - 1)); py = int(np.clip(int(box[1] * H), 0, H - 1))
        pw = int(np.clip(int((box[2] - box[0]) * W), 1, W - px)); ph = int(np.clip(int((box[3] - box[1]) * H), 1, H - py))
        side = min(FACE_SIDE, max(pw, ph))
        fw = max(3, round(pw * side / max(pw, ph))); fh = max(3, round(ph * side / max(pw, ph)))
        face_gray = gray_of(rgb[py:py + ph, px:px + pw], fw, fh)
        sharp = quality(face_gray)["sharpness"]
        bright = region_mean(face_gray, (0.2, 0.2, 0.8, 0.8))
        bw, bh = box[2] - box[0], box[3] - box[1]
        around = (box[0] - bw, box[1] - bh * 0.5, box[2] + bw, box[3] + bh * 0.5)
        surround = region_mean(gray, around, exclude=box)
        occ = 0.0
        if mesh:
            radius = max(2, round(pw * 0.04))
            patches = []
            for i in SKIN_PATCHES:
                cx, cy = round(mesh[0][i] * W), round(mesh[1][i] * H)
                if cx - radius < 0 or cy - radius < 0 or cx + radius >= W or cy + radius >= H:
                    continue
                patches.append(rgb[cy - radius:cy + radius + 1, cx - radius:cx + radius + 1].reshape(-1, 3).mean(0))
            occ = occlusion(patches)
        sure = mesh is not None or found["score"] >= MIN_RECOGNISE_SCORE
        emb = None
        if sure and bw >= MIN_RECOGNISE_FACE and r["cutOff"] < 0.2 and r["yaw"] <= 50:
            pts = alignment_points(mesh[0], mesh[1], W, H) if mesh else np.array(found["points"]).reshape(5, 2) * [W, H]
            emb = self._embed(rgb, pts)
        size_score = float(np.clip(bw / 0.12, 0, 1))
        angle = 1.0 if r["yaw"] < 30 else 0.5 if r["yaw"] < 50 else 0.2
        eyes_score = {"CLOSED": 0.7, "UNKNOWN": 0.85}.get(r["eyes"], 1.0)
        return {**r, "box": [float(v) for v in box], "sharpness": sharp, "brightness": bright,
                "backlit": is_backlit(bright, surround), "occlusion": occ,
                "quality": size_score * angle * eyes_score * (1 - 0.5 * occ), "embedding": emb}

    def _embed(self, rgb, pts):
        a, b, tx, ty = similarity(pts, SFACE_TEMPLATE)
        # Inverse map each 112x112 output pixel back into the photo, bilinear (Canvas with FILTER).
        det = a * a + b * b
        u, v = np.meshgrid(np.arange(112) + 0.5, np.arange(112) + 0.5)
        x = (a * (u - tx) + b * (v - ty)) / det - 0.5
        y = (-b * (u - tx) + a * (v - ty)) / det - 0.5
        H, W = rgb.shape[:2]
        x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
        fx, fy = (x - x0)[..., None], (y - y0)[..., None]
        def px(yy, xx):
            ok = ((xx >= 0) & (xx < W) & (yy >= 0) & (yy < H))[..., None]
            return np.where(ok, rgb[np.clip(yy, 0, H - 1), np.clip(xx, 0, W - 1)].astype(np.float32), 0)
        out = (px(y0, x0) * (1 - fx) * (1 - fy) + px(y0, x0 + 1) * fx * (1 - fy) +
               px(y0 + 1, x0) * (1 - fx) * fy + px(y0 + 1, x0 + 1) * fx * fy)
        data = np.rint(out).transpose(2, 0, 1)[None].astype(np.float32)
        e = self.sface.run(None, {self.sface.get_inputs()[0].name: data})[0][0]
        n = float(np.linalg.norm(e))
        e = e / n if n > 1e-9 else e
        return base64.b64encode(e.astype("<f4").tobytes()).decode()


def foreground_backs(people, faces):
    def centre_in(f, p):
        cx, cy = (f[0] + f[2]) / 2, (f[1] + f[3]) / 2
        return p[0] <= cx <= p[2] and p[1] <= cy <= p[3]
    return sum(1 for p in people
               if p[3] >= 0.95 and p[3] - p[1] >= 0.35 and area(p) >= 0.10 and p[0] < 0.7 and p[2] > 0.3
               and not any(centre_in(f, p) for f in faces)
               and any(f[2] - f[0] >= 0.03 and not centre_in(f, p) for f in faces))


def photos_in(paths):
    for p in paths:
        if os.path.isdir(p):
            for root, _, files in sorted(os.walk(p)):
                for f in sorted(files):
                    if f.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                        rel = os.path.relpath(root, p)
                        yield os.path.join(root, f), "" if rel == "." else rel
        else:
            yield p, ""


def main():
    models, out, paths = sys.argv[1], sys.argv[2], sys.argv[3:]
    a = Analyzer(models)
    with open(out, "w") as fh:
        for path, folder in photos_in(paths):
            row = {"name": os.path.basename(path), "folder": folder}
            try:
                row.update(a.analyze(path))
                row["failed"] = False
            except Exception as e:  # an unreadable photo is kept, marked failed, as in the app
                row.update({"failed": True, "error": str(e)[:200]})
            fh.write(json.dumps(row) + "\n")
            fh.flush()


if __name__ == "__main__":
    main()
