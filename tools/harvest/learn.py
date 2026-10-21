"""Learns how likely a person is to pick a photo for the album (the app's Appeal) from albums
picked by eye, and writes the weights the app uses.

    python3 learn.py features.csv ../../app/src/main/java/com/albumdone/app/plan/AppealWeights.kt
    python3 learn.py features.csv weights.txt --leave-out SET   (for testing on SET honestly)

features.csv has one row per photo: set, name, picked (0/1), then the features Appeal.features
gives (made by a unit test over the labelled practice sets; held-back sets are never in it).
A logistic regression with both classes weighted equally and a mild L2 penalty: few numbers,
easy to read, nothing that cannot run on a phone in a millisecond.
"""
import csv
import sys

import numpy as np

L2 = 1.0


def fit(X, y, it=3000, lr=0.1):
    mu, sd = X.mean(0), X.std(0)
    sd[sd < 1e-9] = 1.0
    Z = (X - mu) / sd
    w = np.zeros(Z.shape[1])
    b = 0.0
    pos = y.mean()
    wt = np.where(y == 1, 0.5 / pos, 0.5 / (1 - pos))
    for _ in range(it):
        p = 1 / (1 + np.exp(-(Z @ w + b)))
        g = Z.T @ ((p - y) * wt) / len(y) + L2 * w / len(y)
        w -= lr * g
        b -= lr * ((p - y) * wt).mean()
    return mu, sd, w, b


def main():
    rows = list(csv.DictReader(open(sys.argv[1])))
    leave_out = sys.argv[sys.argv.index("--leave-out") + 1] if "--leave-out" in sys.argv else None
    rows = [r for r in rows if r["set"] != leave_out]
    names = [k for k in rows[0] if k not in ("set", "name", "picked")]
    X = np.array([[float(r[n]) for n in names] for r in rows])
    y = np.array([int(r["picked"]) for r in rows])
    mu, sd, w, b = fit(X, y)
    out = sys.argv[2]
    if out.endswith(".kt"):
        sets = sorted({r["set"] for r in rows})
        with open(out, "w") as f:
            f.write("package com.albumdone.app.plan\n\n")
            f.write("/**\n * Made by tools/harvest/learn.py from %d photos of %d albums picked by eye (%d picks): feature\n"
                    " * name to (mean, scale, weight); \"bias\" holds the bias. Do not edit by hand.\n */\n" % (len(rows), len(sets), y.sum()))
            f.write("object AppealWeights {\n    val WEIGHTS: Map<String, DoubleArray> = mapOf(\n")
            f.write("        \"bias\" to doubleArrayOf(0.0, 1.0, %r),\n" % round(float(b), 5))
            for n, m, s, v in zip(names, mu, sd, w):
                if abs(v) < 1e-4:
                    continue
                f.write("        \"%s\" to doubleArrayOf(%r, %r, %r),\n" % (n, round(float(m), 5), round(float(s), 5), round(float(v), 5)))
            f.write("    )\n}\n")
    else:
        with open(out, "w") as f:
            f.write("bias 0.0 1.0 %r\n" % float(b))
            for n, m, s, v in zip(names, mu, sd, w):
                f.write("%s %r %r %r\n" % (n, float(m), float(s), float(v)))
    for n, v in sorted(zip(names, w), key=lambda t: -abs(t[1]))[:12]:
        print("  %-22s %+.2f" % (n, v), file=sys.stderr)


if __name__ == "__main__":
    main()
