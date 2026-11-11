#!/usr/bin/env bash
# Model files too big to keep in git, downloaded at a pinned revision into the app's assets.
# Their checksums are in app/models.sha256 (checked by every build), like the bundled models.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
cd "$here/../src/main/assets/models"
# SigLIP 2 image model (Google, Apache 2.0; int8 ONNX by onnx-community): what each photo shows, for the
# album brain. Its one ConvInteger layer has no Android kernel, so patch_siglip.py rewrites it as plain
# float maths (vectors identical, checked on real photos).
f=siglip2_vision_int8.onnx
url="https://huggingface.co/onnx-community/siglip2-base-patch32-256-ONNX/resolve/7efccb86b5b6601bfe9e14326e1c11b40b68d44c/onnx/vision_model_int8.onnx"
if [ ! -f "$f" ]; then
  tmp=$(mktemp -d)
  curl -fsSL --retry 5 --retry-delay 10 -o "$tmp/original.onnx" "$url"
  echo "2e0aa5748e1a018696f6e088753dbda1c5e596687d9b4193d18e32cb9e08b518  $tmp/original.onnx" | sha256sum -c -
  python3 -m venv "$tmp/venv"
  "$tmp/venv/bin/pip" install -q --only-binary=:all: onnx==1.23.2 numpy
  "$tmp/venv/bin/python" -I "$here/patch_siglip.py" "$tmp/original.onnx" "$f.part"
  mv "$f.part" "$f"
  rm -rf "$tmp"
fi
ls -la "$f"
