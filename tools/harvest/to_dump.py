"""Turns measure.py's lines into the planner's text format (PlanDump), people not yet grouped.

    python3 to_dump.py measured.jsonl raw-dump.txt [OCCASION]

Faces keep no person; their fingerprints follow as EMB lines (photo id, face number in the photo,
face quality, base64 numbers), which the planner's reader skips. jvm/ groups them into people with
the app's own rules and writes the final plan-input.txt.
"""
import json, sys


def f(v):
    return repr(float(v))


def main():
    out = open(sys.argv[2], "w")
    out.write("TAGS\t\t\t" + (sys.argv[3] if len(sys.argv) > 3 else "WEDDING") + "\t\n")
    for pid, line in enumerate(open(sys.argv[1]), start=1):
        r = json.loads(line)
        if r.get("failed"):
            out.write("\t".join(["PHOTO", str(pid), r["name"], r["folder"], "0", "0", "0", "0.0", "0.0", "0.0", "0.0", "0.0", "0", "0.0", "0", "0.0", "true", ""]) + "\n")
            continue
        phones = ";".join(",".join(f(v) for v in b) for b in r["phones"])
        out.write("\t".join(["PHOTO", str(pid), r["name"].replace("\t", " "), r["folder"].replace("\t", " "), str(r["takenAt"]), str(r["width"]),
                             str(r["height"]), f(r["sharpness"]), f(r["peakSharpness"]), f(r["brightness"]), f(r["darkFraction"]),
                             f(r["brightFraction"]), str(r["hash"]), f(r["tilt"]), str(r["foregroundBacks"]), f(r["peopleArea"]), "false", phones]) + "\n")
        for k, fc in enumerate(r["faces"]):
            out.write("\t".join(["FACE", str(pid), "", *(f(v) for v in fc["box"]), fc["eyes"], fc["expression"], f(fc["smile"]),
                                 "true" if fc["lookingAtCamera"] else "false", f(fc["directionX"]), f(fc["sharpness"]), f(fc["brightness"]),
                                 "true" if fc["backlit"] else "false", f(fc["occlusion"]), f(fc["cutOff"])]) + "\n")
            if fc.get("embedding"):
                out.write("\t".join(["EMB", str(pid), str(k), f(fc["quality"]), fc["embedding"]]) + "\n")
    out.close()


if __name__ == "__main__":
    main()
