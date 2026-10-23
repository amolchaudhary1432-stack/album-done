"""Picks a real phone in Firebase Test Lab for the real-photo test.

    gcloud firebase test android models list --filter=form=PHYSICAL --format=json | python3 pick_device.py [WANT]

Prints "model,version" for gcloud --device. WANT (optional, a model id such as a15) wins when the
lab has it. Otherwise a mid-range Samsung Galaxy A or M phone, as most couples in India hold, then
any other Samsung, then a Pixel, then whatever is there; the newest Android each supports.
Phones marked deprecated or reduced capacity (long queues) are passed over when others exist.
"""
import json
import sys


def score(m):
    brand = (m.get("brand") or m.get("manufacturer") or "").lower()
    name = (m.get("name") or "").lower()
    tags = " ".join(m.get("tags") or []).lower()
    bad = ("deprecated" in tags) + ("reduced_capacity" in tags) + ("preview" in tags)
    if "samsung" in brand and any(w in name for w in ("galaxy a", "galaxy m")):
        kind = 0
    elif "samsung" in brand:
        kind = 1
    elif "google" in brand or "pixel" in name:
        kind = 2
    else:
        kind = 3
    newest = max((int(v) for v in m.get("supportedVersionIds") or [] if v.isdigit()), default=0)
    return (bad, kind, -newest)


def main(want=""):
    models = [m for m in json.load(sys.stdin) if (m.get("form") or "").upper() == "PHYSICAL"
              and "arm64-v8a" in (m.get("supportedAbis") or ["arm64-v8a"])
              and any(v.isdigit() and int(v) >= 29 for v in m.get("supportedVersionIds") or [])]
    for m in sorted(models, key=score):
        print(f"  {m['id']}: {m.get('brand')} {m.get('name')}, Android API {','.join(m.get('supportedVersionIds') or [])} {m.get('tags') or ''}", file=sys.stderr)
    if not models:
        sys.exit("No real phones in the lab right now")
    pick = next((m for m in models if m["id"] == want), None) or min(models, key=score)
    version = max(v for v in pick["supportedVersionIds"] if v.isdigit() and int(v) >= 29)
    print(f"{pick['id']},{version}")


if __name__ == "__main__":
    main(*sys.argv[1:2])
