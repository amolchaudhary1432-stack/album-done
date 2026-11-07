#!/usr/bin/env bash
# Model files too big to keep in git, downloaded at a pinned revision into the app's assets.
# Their checksums are in app/models.sha256 (checked by every build), like the bundled models.
set -euo pipefail
cd "$(dirname "$0")/../app/src/main/assets/models"
# SigLIP 2 image model (Google, Apache 2.0; int8 ONNX by onnx-community): what each photo shows, for the album brain.
f=siglip2_vision_int8.onnx
url="https://huggingface.co/onnx-community/siglip2-base-patch32-256-ONNX/resolve/7efccb86b5b6601bfe9e14326e1c11b40b68d44c/onnx/vision_model_int8.onnx"
if [ ! -f "$f" ]; then
  curl -fsSL --retry 5 --retry-delay 10 -o "$f.part" "$url"
  mv "$f.part" "$f"
fi
ls -la "$f"
