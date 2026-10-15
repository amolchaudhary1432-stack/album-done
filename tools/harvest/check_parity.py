"""Compares measure.py's numbers with the app's own measurements of the same photos.

    python3 check_parity.py plan-input.txt measured.jsonl

plan-input.txt is what the phone test writes (PlanDump) after the real app checked the photos.
Prints, field by field, how closely the computer copy agrees with the phone.
"""
import json, statistics, sys


def box_iou(a, b):
    i = max(0, min(a[2], b[2]) - max(a[0], b[0])) * max(0, min(a[3], b[3]) - max(a[1], b[1]))
    u = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - i
    return i / u if u > 0 else 0


def read_dump(path):
    photos, faces = {}, {}
    for line in open(path):
        c = line.rstrip("\n").split("\t")
        if c[0] == "PHOTO":
            key = f"{c[3]}/{c[2]}" if c[3] else c[2]
            photos[c[1]] = {"key": key, "sharpness": float(c[7]), "peakSharpness": float(c[8]), "brightness": float(c[9]),
                            "darkFraction": float(c[10]), "brightFraction": float(c[11]), "hash": int(c[12]), "tilt": float(c[13]),
                            "foregroundBacks": int(c[14]), "peopleArea": float(c[15]), "faces": []}
        elif c[0] == "FACE":
            photos[c[1]]["faces"].append({"box": [float(v) for v in c[3:7]], "eyes": c[7], "expression": c[8], "smile": float(c[9]),
                                          "lookingAtCamera": c[10] == "true", "sharpness": float(c[12]), "brightness": float(c[13]),
                                          "backlit": c[14] == "true", "occlusion": float(c[15]), "cutOff": float(c[16])})
    return {p["key"]: p for p in photos.values()}


def main():
    app = read_dump(sys.argv[1])
    ours = {}
    for line in open(sys.argv[2]):
        r = json.loads(line)
        ours[f"{r['folder']}/{r['name']}" if r["folder"] else r["name"]] = r
    keys = sorted(set(app) & set(ours))
    print(f"{len(keys)} photos in both ({len(app)} app, {len(ours)} computer)")

    def rel(field):
        d = [abs(ours[k][field] - app[k][field]) / max(1e-9, abs(app[k][field])) for k in keys if not ours[k].get("failed")]
        return statistics.median(d), sorted(d)[int(0.9 * (len(d) - 1))]
    for f in ("sharpness", "peakSharpness", "brightness", "peopleArea"):
        m, p90 = rel(f)
        print(f"  {f:15s} median difference {m:6.1%}, 9 in 10 within {p90:6.1%}")
    for f in ("darkFraction", "brightFraction", "tilt"):
        d = [abs(ours[k][f] - app[k][f]) for k in keys]
        print(f"  {f:15s} median difference {statistics.median(d):.4f}, 9 in 10 within {sorted(d)[int(0.9 * (len(d) - 1))]:.4f}")
    ham = [bin((ours[k]["hash"] ^ app[k]["hash"]) & (2**64 - 1)).count("1") for k in keys]
    print(f"  hash bits differing: median {statistics.median(ham)}, 9 in 10 within {sorted(ham)[int(0.9 * (len(ham) - 1))]}")
    print(f"  foregroundBacks equal: {sum(ours[k]['foregroundBacks'] == app[k]['foregroundBacks'] for k in keys)}/{len(keys)}")

    counts = [(len(app[k]["faces"]), len(ours[k]["faces"])) for k in keys]
    print(f"  faces: app {sum(a for a, _ in counts)}, computer {sum(o for _, o in counts)}, same count in {sum(a == o for a, o in counts)}/{len(keys)} photos")
    pairs = []
    for k in keys:
        free = list(ours[k]["faces"])
        for fa in app[k]["faces"]:
            best = max(free, key=lambda fo: box_iou(fa["box"], fo["box"]), default=None)
            if best is not None and box_iou(fa["box"], best["box"]) >= 0.5:
                pairs.append((fa, best)); free.remove(best)
    print(f"  faces matched by position: {len(pairs)}")
    for f in ("eyes", "expression", "lookingAtCamera", "backlit"):
        same = sum(a[f] == o[f] for a, o in pairs)
        print(f"  {f:15s} same {same}/{len(pairs)} ({same / max(1, len(pairs)):.0%})")
    for f in ("smile", "occlusion", "cutOff"):
        d = [abs(a[f] - o[f]) for a, o in pairs]
        print(f"  {f:15s} median difference {statistics.median(d):.3f}, 9 in 10 within {sorted(d)[int(0.9 * (len(d) - 1))]:.3f}")
    for f in ("sharpness", "brightness"):
        d = [abs(a[f] - o[f]) / max(1e-9, abs(a[f])) for a, o in pairs]
        print(f"  face {f:10s} median difference {statistics.median(d):6.1%}, 9 in 10 within {sorted(d)[int(0.9 * (len(d) - 1))]:6.1%}")


if __name__ == "__main__":
    main()
