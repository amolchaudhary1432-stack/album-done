# On-device image-understanding models for judging wedding photos (budget Android, commercial licences)

Research date: 3 Nov 2026. Network note: huggingface.co and arxiv.org could not be fetched from this session (DNS blocked), so Hugging Face model cards and arXiv papers were read only through search snippets, GitHub copies (raw.githubusercontent.com worked) and official vendor pages. Facts marked "(inference)" are my own reasoning, not from a source.

Target phones: Samsung Galaxy A15 (Helio G99, 2x A76 + 6x A55, Mali-G57 MC2, "APU 2" ~1 TOPS), Galaxy A05s, moto g14. Today's app on the A15: 2,500 photos in 25 min 48 s (~100/min, ~0.62 s per photo end to end; ~0.5 s per photo in the 1,000-photo run), memory 550-600 MB (from the project's own CLAUDE.md status notes).

## Comparison table (summary; sources in the sections below)

| Model | Licence (weights) | Commercial in a paid app? | Params | Input res | Size (inference from params unless noted) | Mobile speed evidence | Runtimes | Quality evidence |
|---|---|---|---|---|---|---|---|---|
| SigLIP 2 B/32 | Apache 2.0 (code), CC-BY 4.0 (other materials) | Yes | ViT-B: 86M vision (+ text tower, 256k vocab) | 256 | vision fp32 ~345 MB, fp16 ~172 MB, int8 ~86 MB (inference) | No phone number found; 64 patch tokens, ~4x cheaper than B/16@224 (inference) | ONNX Runtime / LiteRT after conversion (inference) | IN-1k 0-shot 74.0; COCO T→I 47.2 / I→T 63.7 |
| SigLIP 2 B/16 | Apache 2.0 | Yes | 86M vision | 224/256/384/512 | same as above | ViT-B/16 @336: 663 ms CPU FP16, 98.9 ms GPU FP16, 14.9 ms NPU INT8 on a (flagship) mobile SoC study | as above | IN-1k 0-shot 78.2 (224), 79.1 (256), 80.6 (384); COCO T→I 52.1-54.6 |
| SigLIP 2 So400m | Apache 2.0 | Yes | 400M | 224-512 | fp16 ~800 MB (inference) | too heavy for 2,500 photos on A15 (inference) | - | IN-1k 0-shot 83.2-84.3 |
| TinyCLIP (Microsoft) | MIT | Yes (but trained on LAION-400M / YFCC; see gaps) | ViT-8M..63M image + 3M..31M text | 224 | ViT-40M/32: ~160 MB fp32, ~40 MB int8 (inference) | 1.9-9.5 GMACs; server throughput only | open_clip → ONNX | IN-1k 0-shot 41.1 (8M/16) to 64.5 (63M/32) |
| MobileCLIP / MobileCLIP2 (Apple) | Code MIT; weights "Apple ML Research Model" licence = research only | **No** (explicitly excludes product use) | S0: 11.4M + 63.4M ... S4: 321.6M + 123.6M | 256 | - | S0 1.5 ms image on iPhone (Apple table) | CoreML, ONNX | MobileCLIP2-S0 IN 71.5; S2 77.2; B 79.4 |
| SmolVLM-256M / 500M / 2.2B; SmolVLM2 | Apache 2.0 | Yes | 256M (93M SigLIP-B/16-512 encoder) | 512 patches → 64 tokens | 256M: ~0.5 GB fp16 (inference) | <1 GB GPU RAM for one image (model card); no Android timing found | ONNX, llama.cpp (GGUF), MNN (smolvlm-video listed) | 1.2k tokens per image+prompt vs 16k for Qwen2-VL |
| Moondream 2 (2B) / 0.5B | Apache 2.0 | Yes | 2B / 0.5B | - | - | none found | ONNX / own runtime | - |
| Florence-2 base / large | MIT (per community exports, "same as upstream") | Yes | 0.23B (base), 0.77B (large) | 768 | fp16 ONNX ~520 MB (4 files); int8 ~270 MB | "~12 tok/s on Pixel 8" claim unverified; desktop 1-3 s per caption | ONNX Runtime, OpenVINO, CoreML | captioning, OD, OCR tasks |
| Qwen2-VL-2B | Apache 2.0 (not directly verified) | Probably | 2B | dynamic | - | M2 Mac CPU: ~23 s image embed + ~42 s text gen for 1024x683 | MNN (q8), llama.cpp | 16k image tokens in HF's test (heavy) |
| Qwen2.5-VL-3B | **Qwen Research License** (HF card) | **No** (research licence) | 3B | dynamic | - | - | MNN, llama.cpp | - |
| Qwen3-VL-2B | Apache 2.0 (third-party mirrors; official file not read) | Likely yes, verify | 2B | dynamic | - | MNN lists Qwen3-VL 4B/8B | MNN, llama.cpp | - |
| MiniCPM-V / MiniCPM-o (incl. V-4 4B) | Apache 2.0 (weights and code; optional registration) | Yes | 4B (V-4) | - | - | MNN app ships MiniCPM-V-4 | MNN, llama.cpp | "GPT-4V level" (vendor claim) |
| PaliGemma, Gemma 3n | Gemma Terms of Use (custom) | Yes, with conditions (Prohibited Use Policy, notice file, pass-through terms) | Gemma 3n E2B (MobileNet-V5-300M vision encoder) | - | E2B LiteRT file ~2.6-3 GB | E2B S25 Ultra int4: CPU 163 prefill / 17.6 decode tok/s, TTFT 6.7 s; S24 Ultra CPU 110.5 / 16.1, GPU 816 / 15.6 | LiteRT-LM, MediaPipe LLM | - |
| Gemma 4 E2B | **Apache 2.0** (Google docs, HF card) | Yes | E2B | variable | <1.5 GB memory on some devices (2/4-bit + mmap embeddings); 2.58 GB file | none for budget phones | LiteRT-LM, MNN | OCR/chart claims (vendor) |
| LFM2-VL-450M / 1.6B, LFM2.5-VL | LFM Open License v1.0 | Only under US$10M annual revenue | 450M / 1.6B | - | ONNX export exists | - | ONNX, GGUF, MNN | - |
| FastVLM (Apple) | Apple ML Research Model licence | **No** | - | - | - | - | - | - |
| NIMA MobileNet (idealo) | Apache 2.0 (code + released weights) | Code yes; trained on AVA (see gaps) | MobileNet (~3-4M, inference) | 224 | ~13 MB fp32 (inference) | MobileNet-class, tens of ms (inference) | Keras → TFLite | AVA aesthetic LCC 0.626 / SRCC 0.609; TID2013 technical SRCC 0.675 |
| LAION aesthetic predictor v1 / improved (MLP on CLIP) | v1 MIT; improved Apache 2.0 | Head yes; needs a CLIP encoder and was trained on AVA + SAC + logos | tiny MLP | CLIP embedding | <1 MB (inference) | free once embedding exists | any | - |

## Image-text encoders (SigLIP 2, SigLIP, TinyCLIP, MobileCLIP, EVA-CLIP, OpenCLIP, FastViT/EfficientViT)

### Takeaway
SigLIP 2 is the clear pick: Apache 2.0, strongest accuracy per size, and a B/32 or B/16 vision tower (86M) is the only "big" encoder size realistic on a Helio G99. MobileCLIP/MobileCLIP2 (and FastVLM) are not usable in a paid app: Apple's weight licence is research-only. TinyCLIP (MIT) is the small fallback if SigLIP 2 B is too slow.

### Cited Findings
- SigLIP 2 is released at four sizes: ViT-B (86M), L (303M), So400m (400M) and g (1B); NaFlex variants keep native aspect ratio; trained with captioning-based pretraining, self-distillation, masked prediction and de-biased multilingual data. — [big_vision SigLIP 2 README](https://raw.githubusercontent.com/google-research/big_vision/main/big_vision/configs/proj/image_text/README_siglip2.md)
- SigLIP 2 zero-shot ImageNet / COCO T→I / I→T: B/32@256 74.0 / 47.2 / 63.7; B/16@224 78.2 / 52.1 / 68.9; B/16@256 79.1 / 53.2 / 69.7; B/16@384 80.6 / 54.6 / 71.4; B/16@512 81.2; L/16@256 82.5; So400m/14@224 83.2; So400m/16@384 84.1; g-opt/16@384 85.0; NaFlex B/16 78.5. — [big_vision SigLIP 2 README](https://raw.githubusercontent.com/google-research/big_vision/main/big_vision/configs/proj/image_text/README_siglip2.md)
- Licence: "All software is licensed under the Apache License, Version 2.0 ... All other materials are licensed under ... CC-BY". — [big_vision SigLIP 2 README](https://raw.githubusercontent.com/google-research/big_vision/main/big_vision/configs/proj/image_text/README_siglip2.md); the Hugging Face/timm So400m SigLIP 2 listing shows "License: apache-2.0" — [timm/ViT-SO400M-16-SigLIP2-512](https://huggingface.co/timm/ViT-SO400M-16-SigLIP2-512)
- SigLIP 2 B/16@256 beats original SigLIP B/16@256 (79.1 vs 76.7 ImageNet zero-shot). — [emergentmind summary of the paper](https://www.emergentmind.com/topics/siglip2-model) (secondary)
- MobileCLIP repo: code MIT; ML model weights "Apple ML Research Model TOU"; data CC-BY-NC-ND. — [apple/ml-mobileclip](https://github.com/apple/ml-mobileclip)
- Apple weight licence text: "exclusively for Research Purposes ... 'Research Purposes' does not include any commercial exploitation, product development or use in any commercial product or service." — [ml-mobileclip LICENSE_MODELS](https://raw.githubusercontent.com/apple/ml-mobileclip/main/LICENSE_MODELS); the repo's LICENSE says weights are covered by LICENSE_MODELS and LICENSE_DATA — [ml-mobileclip LICENSE](https://raw.githubusercontent.com/apple/ml-mobileclip/main/LICENSE)
- MobileCLIP family (params image+text, latency ms image+text, IN-1k zero-shot): MobileCLIP2-S0 11.4+63.4M, 1.5+3.3 ms, 71.5%; MobileCLIP2-S2 35.7+63.4M, 3.6+3.3 ms, 77.2%; MobileCLIP2-B 86.3+63.4M, 10.4+3.3 ms, 79.4%; MobileCLIP-S0 67.8%; MobileCLIP-S2 74.4%. MobileCLIP2 trained on DFNDR, MobileCLIP on DataCompDR. — [apple/ml-mobileclip](https://github.com/apple/ml-mobileclip)
- Third-party summaries (SourcePulse, PromptLayer) call MobileCLIP "permissive" — this is wrong for the weights; they conflate the MIT code licence with the weights. — [SourcePulse](https://www.sourcepulse.org/projects/1850133), [PromptLayer](https://www.promptlayer.com/models/coreml-mobileclip); contradicted by [LICENSE_MODELS](https://raw.githubusercontent.com/apple/ml-mobileclip/main/LICENSE_MODELS)
- Apple FastVLM model licence is the same "Apple Machine Learning Research Model" research-only licence. — [ml-fastvlm LICENSE_MODEL](https://raw.githubusercontent.com/apple/ml-fastvlm/main/LICENSE_MODEL)
- TinyCLIP licence: MIT, Copyright Microsoft. — [TinyCLIP LICENSE](https://raw.githubusercontent.com/wkcn/TinyCLIP/main/LICENSE)
- TinyCLIP models (data, IN-1k zero-shot, GMACs): ViT-8M/16 Text-3M YFCC-15M 41.1%, 2.0; ViT-39M/16 Text-19M YFCC-15M 63.5%, 9.5; ViT-22M/32 Text-10M LAION-400M 53.7%, 1.9; ViT-40M/32 Text-19M LAION-400M 59.8%, 3.5; ViT-45M/32 Text-18M LAION+YFCC 62.7%, 1.9; ViT-63M/32 Text-31M LAION+YFCC 64.5%, 5.6; ResNet-19M 56.4%, 4.4. — [TinyCLIP README](https://raw.githubusercontent.com/wkcn/TinyCLIP/main/README.md)
- OpenCLIP DataComp models, e.g. `datacomp_xl_s13b_b90k` ViT-L/14 trained on DataComp-1B, 79.2% ImageNet zero-shot (no licence statement in PRETRAINED.md). — [open_clip PRETRAINED.md](https://raw.githubusercontent.com/mlfoundations/open_clip/main/docs/PRETRAINED.md)

### Inferences
- Compute: ViT-B/16@224 has 196 patch tokens, B/32@256 has 64, B/16@336 has 441 (paper case above). Scaling the 663 ms CPU-FP16 ViT-B/16@336 figure (a flagship-class SoC) roughly linearly in tokens gives ~300 ms for B/16@224 and ~100 ms for B/32@256 on that SoC; the Helio G99's 2x A76 @2.2 GHz is likely 2-3x slower than a flagship's big cores, so roughly 0.6-0.9 s for SigLIP 2 B/16@224 and 0.2-0.35 s for B/32@256 on the A15 CPU (int8 dynamic quantisation could cut these by up to ~2x). These are estimates; they must be measured on the A15 in Test Lab.
- Only the vision tower needs to ship and run per photo; the text tower can be run once on the build machine to produce fixed "prompt" vectors (e.g. "a bride and groom portrait", "mehendi hands", "a blurry photo"), which are shipped as a small table. This removes the 256k-vocab text tower (most of SigLIP 2 B's size) from the APK.
- SigLIP 2 B/32@256 (74.0%) beats every TinyCLIP (max 64.5%) and MobileCLIP2-S0 (71.5%) at similar or lower token cost, and is Apache: the best value encoder for this app.
- EVA-CLIP and OpenCLIP weights trained on LAION/DataComp have permissive code licences but web-scraped training data; SigLIP 2 (Google WebLI data, Apache weights) carries less data-provenance ambiguity.

### Gaps
- No measured SigLIP 2 / ViT-B latency on a Helio G99 or any budget phone was found; Geekbench AI scores for the G99 were not found either.
- Hugging Face SigLIP 2 B/16 / B/32 card licence fields not read directly (huggingface.co blocked); the official big_vision README states Apache 2.0.
- EVA-CLIP licence, EfficientViT and FastViT licences and mobile timings: not researched (ran out of tool budget). FastViT is an Apple repo; check whether its weights carry the same research-only licence as MobileCLIP before considering it.
- LAION-400M / DataComp data licences (images are web-scraped and copyrighted; metadata licences only) and how that affects TinyCLIP/OpenCLIP weights: not verified with a primary source.

## Small vision-language models (captioning / reasoning)

### Takeaway
Generative VLMs are far too slow to run on all 2,500 photos on a Helio G99 (seconds to tens of seconds per photo even on flagships), but some are licence-clean and could run on a short list (e.g. 20-60 cover/candidate photos) or not at all. Licence-clean options: SmolVLM/SmolVLM2 (Apache), Moondream 2 (Apache), Florence-2 (MIT), MiniCPM-V (Apache now), Gemma 4 E2B (Apache 2.0, Apr 2026), Qwen3-VL-2B (Apache per mirrors). Avoid Qwen2.5-VL-3B (research licence), Apple FastVLM (research licence); Gemma 3n/PaliGemma are allowed but with Gemma Terms obligations; LFM2-VL only below US$10M revenue.

### Cited Findings
- SmolVLM: all checkpoints, datasets and training recipes released under Apache 2.0. — [HF blog: SmolVLM](https://huggingface.co/blog/smolvlm); SmolVLM2 (Feb 2025) Apache 2.0 — [Roboflow SmolVLM2](https://playground.roboflow.com/models/hugging-face/smolvlm2)
- SmolVLM encodes a test prompt plus one image in 1.2k tokens; Qwen2-VL uses 16k tokens for the same. — [HF blog: SmolVLM](https://huggingface.co/blog/smolvlm)
- SmolVLM-256M: 93M vision encoder (SigLIP base patch-16/512), 64 visual tokens per 512x512 patch, under 1 GB GPU RAM for one image. — [SmolVLM-256M-Instruct card](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct), [SiliconANGLE](https://siliconangle.com/2025/01/23/hugging-face-open-sources-worlds-smallest-vision-language-model/)
- Moondream repo licence: Apache 2.0; models: Moondream 2B and Moondream 0.5B ("distillation target for edge devices"). — [moondream LICENSE](https://raw.githubusercontent.com/vikhyat/moondream/main/LICENSE), [moondream README](https://raw.githubusercontent.com/vikhyat/moondream/main/README.md)
- Qwen2.5-VL-3B Hugging Face page specifies the "Qwen RESEARCH LICENSE AGREEMENT" while the GitHub code is Apache 2.0; 7B is Apache, 3B and 72B Qwen-licensed. — [HF discussion #12](https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct/discussions/12), [QwenLM/Qwen3-VL issue #963](https://github.com/QwenLM/Qwen3-VL/issues/963); contradicted by [Wikipedia: Qwen](https://en.wikipedia.org/wiki/Qwen) (secondary, says all but 72B are Apache)
- Qwen3-VL-2B (Instruct/Thinking) released 21 Oct 2025. — [Qwen3-VL README](https://raw.githubusercontent.com/QwenLM/Qwen3-VL/main/README.md); listed as Apache 2.0 by a third-party mirror — [LibreYOLO/LibreGroundQwen3VL2b](https://huggingface.co/LibreYOLO/LibreGroundQwen3VL2b)
- Qwen2-VL-2B on an M2 Mac CPU: ~23 s image embedding (1024x683) and ~42 s text generation. — [ailia / Medium](https://medium.com/axinc-ai/qwen2-vl-a-vision-language-model-that-runs-locally-4b97ccab1bf6)
- MiniCPM-o/V: "model weights and code are open-sourced under the Apache-2.0 license"; registration questionnaire is optional. — [MiniCPM-V docs/project_en.md](https://raw.githubusercontent.com/OpenBMB/MiniCPM-V/main/docs/project_en.md)
- MNN's Android LLM app supports image-to-text, Qwen3-VL (4B, 8B, 30B-A3B), smolvlm-video, MiniCPM-V-4 ("on your phone"), Gemma 4 multimodal E2B/E4B, LFM2/LFM2.5 vision-language; claims prefill 8.6x and decode 2.3x faster than llama.cpp on Android CPU (Qwen-7B). — [MNN MnnLlmChat README](https://raw.githubusercontent.com/alibaba/MNN/master/apps/Android/MnnLlmChat/README.md)
- MNN-based native Android demo lists QwenVL-2-2B q8f32 tested on Snapdragon 8 Gen 2 and Kirin 990 (no timings). — [Native-LLM-for-Android](https://github.com/Yoline777/Native-LLM-for-Android)
- Vendor guide: SmolVLM and Qwen3-VL take ~7 s per image on flagship phones via llama.cpp (no phone/quant named). — [DEV Community: Off Grid](https://dev.to/alichherawalla/how-to-run-local-ai-on-your-android-phone-in-2026-no-cloud-no-account-5cbp)
- Florence-2 base: 0.23B params; community fp16 ONNX export (4 sub-models) ~520 MB, int8 ~270 MB with some OCR degradation; licence MIT "same as upstream". — [Heliosoph/florence-2-base-ft-fp16-onnx](https://huggingface.co/Heliosoph/florence-2-base-ft-fp16-onnx); desktop caption 1-3 s at 768x768, latency dominated by the decoder token loop — [florence2-sharp performance](https://docs.curiosity.ai/florence2-sharp/advanced/performance); "~12 tok/s, <500 MB on Pixel 8" — [DEV Community](https://dev.to/software_mvp-factory/quantized-vision-transformers-on-android-aea) (unverified, table empty)
- Gemma 3n E2B (LiteRT preview, S25 Ultra, int4, 1024-token prefill / 256 decode): CPU 163 tok/s prefill, 17.6 decode, 6.7 s time to first token; GPU 620 prefill, 23.3 decode. Vision encoder MobileNet-V5-300M. — [google/gemma-3n-E2B-it-litert-preview](https://huggingface.co/google/gemma-3n-E2B-it-litert-preview)
- Gemma 3n E2B (LiteRT-LM): S24 Ultra CPU 110.5 prefill / 16.1 decode, GPU 816.4 / 15.6; Vivo X300 Pro NPU 1671.3 / 28.4. — [google/gemma-3n-E2B-it-litert-lm](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm), [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview)
- Community: Pixel 9 Pro XL 35 tok/s prefill, 5.7 decode; Pixel 7 Pro 5-6 tok/s (older builds). — [Hacker News](https://news.ycombinator.com/item?id=44044199)
- Gemma Terms of Use (last modified 1 Apr 2026) apply to the Gemma models in its appendix; Gemma 4 has its own licence. Use must follow the Prohibited Use Policy (incorporated by reference, can be updated by Google); distributions must carry a "Notice" file and pass on the use restrictions; on termination all copies must be deleted. — [Gemma Terms of Use](https://ai.google.dev/gemma/terms?authuser=0), [Wind River copy 2025-03-24](https://open.windriver.com/info/uni-license-list/licenses/gemma-tou-2025-03-24.html), [Prohibited Use Policy](https://open.windriver.com/info/uni-license-list/licenses/gemma-pup-2024-02-21.html)
- Gemma 4 is licensed under Apache 2.0 (Google docs, HF card); E2B runs in under 1.5 GB memory on some devices with 2/4-bit weights and memory-mapped per-layer embeddings; LiteRT-LM supports E2B/E4B; all Gemma 4 models process images. — [Google AI Edge: Gemma 4](https://ai.google.dev/edge/litert-lm/models/gemma-4), [google/gemma-4-E2B](https://huggingface.co/google/gemma-4-E2B), [Google blog](https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/); community LiteRT-LM file 2.58 GB (0.79 GB decoder weights + 1.12 GB embeddings; vision loaded on demand) — [cuesetac/gemma-4-E2B-it-litert-lm](https://huggingface.co/cuesetac/gemma-4-E2B-it-litert-lm); a mirror claims Gemma Licence instead — [jqueryjs/gemma-4-E2B](https://huggingface.co/jqueryjs/gemma-4-E2B) (less reliable)
- LFM2-VL-450M: LFM Open License v1.0; free commercial use only below US$10M annual revenue. — [Liquid AI LFM licence](https://www.liquid.ai/lfm-license), [Liquid docs](https://docs.liquid.ai/lfm/help/model-license)

### Inferences
- Per-photo cost of a generative VLM on the A15: an image of ~256 tokens at flagship CPU prefill speeds (110-163 tok/s) already takes ~1.6-2.3 s before any answer token, plus the vision encoder and decode; the G99 is several times slower than an S24/S25 Ultra, so 5-20 s per photo is a realistic range (inference). 2,500 photos would take hours: out of budget. A VLM could only judge a short list (e.g. choose the cover from the top 20 couple portraits: ~2-7 min).
- Memory: Gemma 4 E2B needs ~1.5 GB+ on top of the app's 550-600 MB; risky on 4 GB A15/A05s/moto g14 (inference). SmolVLM-256M (~0.5 GB fp16) is the only VLM that fits comfortably.
- A VLM's text output must be turned back into scores; for album picking, a frozen encoder plus a trained head gives a direct score at a fraction of the cost, so the VLM adds little for the main pass.

### Gaps
- No published seconds-per-image or memory measurement for any VLM on a Helio G99 / Galaxy A15 / A05s / moto g14.
- MobileVLM licence and timings, PaliGemma timings, Moondream 3 licence (believed to be non-Apache, not verified), Florence-2 official model card licence (only community exports read): not verified.
- Official Qwen3-VL-2B and Qwen2-VL-2B LICENSE files not read (huggingface.co blocked).

## Image aesthetics / quality models with permissive licences

### Takeaway
The permissive, phone-sized options are NIMA-MobileNet (Apache 2.0) and the LAION aesthetic MLP heads (MIT / Apache) that sit on a CLIP embedding. All public aesthetic models are trained on AVA (and SAC), which are generic photo-contest preferences, not Indian-album judgement; a head trained on the project's own labels over SigLIP 2 embeddings is the better path.

### Cited Findings
- idealo image-quality-assessment (NIMA): Apache 2.0; trained MobileNet models for aesthetic (AVA) and technical (TID2013) quality; MobileNet aesthetic AVA EMD 0.071, LCC 0.626, SRCC 0.609; MobileNet technical TID2013 EMD 0.107, LCC 0.652, SRCC 0.675. Upstream deprecated, maintained fork exists. — [idealo/image-quality-assessment](https://github.com/idealo/image-quality-assessment), [fork](https://github.com/dr-robert-li/image-quality-assessment)
- LAION-Aesthetics Predictor V1: "A linear estimator on top of clip", MIT. — [LAION-AI/aesthetic-predictor](https://github.com/LAION-AI/aesthetic-predictor)
- Improved aesthetic predictor: "a simple neural net that takes CLIP embeddings as inputs", trained on AVA (+ SAC + logos, model name `sac+logos+ava1-l14-linearMSE`), Apache 2.0. — [christophschuhmann/improved-aesthetic-predictor](https://github.com/christophschuhmann/improved-aesthetic-predictor)
- A 2026 audit finds the LAION aesthetic predictor's scores reflect a narrow set of aesthetic preferences. — [arXiv 2601.09896](https://arxiv.org/html/2601.09896v4)
- MUSIQ (Google) handles native-resolution images; licence and mobile suitability not found. — search result summary only ([Wiley review](https://onlinelibrary.wiley.com/doi/10.1155/2024/8223586))

### Inferences
- The LAION heads expect OpenAI CLIP ViT-L/14 embeddings, so they cannot be reused with SigLIP 2 without retraining; retraining the same tiny MLP on SigLIP 2 B embeddings with the project's own labels is cheap.
- AVA-trained weights inherit AVA's data terms (see gaps); prefer own-label training.

### Gaps
- AVA dataset licence (believed research-only, from DPChallenge images) not verified with a primary source; this affects NIMA weights and LAION aesthetic weights for commercial shipping.
- MUSIQ, TOPIQ, Q-Align, CLIP-IQA, pyiqa licences not verified.

## Practical time budget on the Galaxy A15 and quantisation

### Takeaway
Today the A15 checks 2,500 photos in ~26 min (~0.62 s each). A 30-minute target leaves only ~0.1 s per photo extra; a 45-minute target leaves ~0.45 s. That fits SigLIP 2 B/32@256 int8 (or a TinyCLIP) on every photo, but probably not B/16@224 on every photo unless it runs on the GPU/NPU or only on short-listed photos. int8 dynamic quantisation keeps CLIP embeddings very close to fp32.

### Cited Findings
- Helio G99 AI hardware: MediaTek APU 2, ~1 TOPS (vs 15.8 TOPS Apple A15). — [cpu-monkey](https://www.cpu-monkey.com/en/compare_cpu-mediatek_helio_g99-vs-apple_a15_bionic_4_gpu)
- Galaxy A15 Geekbench 6: ~729 single / ~1,978 multi; G99 727 / 2,047; GPU Mali-G57 MC2. — [viettelstore review](https://viettelstore.vn/tin-tuc/danh-gia-hieu-nang-galaxy-a15), [nanoreview G99](https://nanoreview.net/ru/soc/mediatek-helio-g99), [technave](https://technave.com/gadget/Samsung-Galaxy-A15-spotted-on-Geekbench-reveals-Helio-G99-SoC-36204.html)
- ViT-B/16 @336 on a mobile SoC: CPU FP16 (LiteRT/XNNPACK) 663.0 ms, GPU FP16 98.9 ms, NPU INT8 14.9 ms. — [Phase Matters (arXiv 2606.27906)](https://arxiv.org/html/2606.27906) (via search summary; SoC not confirmed)
- CLIP ViT-B/32 ExecuTorch: int8 dynamic XNNPACK file 95.9 (MB, inferred) vs 351.6 fp32, cosine parity 0.9957 vs fp32. — [mlboydaisuke/CLIP-ViT-B32-ExecuTorch](https://huggingface.co/mlboydaisuke/CLIP-ViT-B32-ExecuTorch)
- Florence-2 int8: some degradation on text-heavy OCR. — [Heliosoph ONNX export](https://huggingface.co/Heliosoph/florence-2-base-ft-fp16-onnx)
- Qualcomm's CLIP ViT-B/16 image encoder on Snapdragon NPU: ~3.6-6.8 ms (cited in a research note). — [qualcomm/OpenAI-Clip](https://huggingface.co/qualcomm/OpenAI-Clip) (Snapdragon only; not applicable to MediaTek G99)

### Inferences
- Budget arithmetic (from the project's measured 25 min 48 s for 2,500): 30 min → (1,800 s − 1,548 s) / 2,500 ≈ +0.10 s per photo; 40 min → +0.34 s; 45 min → +0.46 s. With two lanes on the A15, a model taking ~0.5-0.9 s per photo per lane on one A76 core adds ~0.25-0.45 s per photo of throughput only if it does not slow the face models sharing the cores (it will, partly).
- Fits on every photo (estimated): SigLIP 2 B/32@256 int8 vision tower (~0.2-0.35 s), TinyCLIP ViT-22M/32 or 45M/32 (1.9 GMACs), NIMA-MobileNet. Borderline: SigLIP 2 B/16@224 (~0.6-0.9 s CPU) unless run on the Mali-G57 via the LiteRT GPU delegate or only on the 30-50% of photos that pass blur/eyes checks. Out: So400m, any generative VLM on all photos.
- Reuse the decode: the app already decodes each photo at ~1280 px; a 224/256 px resize from that is nearly free.
- The 1 TOPS APU is too small and MediaTek-specific to rely on; plan for CPU (XNNPACK, int8) first, GPU second. Verify by a Test Lab run on the A15, A05s and moto g14 (moto g14's Unisoc T616 is slower: it took 0.75 s per photo today).

### Gaps
- No direct A15/G99 measurement of any ViT; must be measured with a Test Lab run.
- Accuracy effect of int8 on SigLIP 2 specifically (zero-shot / head accuracy) not found; only CLIP B/32 cosine parity.
- Whether NNAPI / the MediaTek neuron delegate is usable on Android 14-16 for ViTs was not researched.

## Fine-tuning feasibility (head or LoRA on a frozen encoder)

### Takeaway
Very feasible: compute SigLIP 2 embeddings on the build machine for a few thousand labelled photos and train a small linear/MLP head (the same pattern as the LAION aesthetic predictor). The head is kilobytes, trains in minutes on CPU, and ships next to the frozen encoder; LoRA on the encoder is unnecessary at this data size.

### Cited Findings
- The LAION aesthetic predictors are exactly this design: a linear layer or small MLP trained on frozen CLIP embeddings to predict a human rating. — [LAION-AI/aesthetic-predictor](https://github.com/LAION-AI/aesthetic-predictor), [improved-aesthetic-predictor](https://github.com/christophschuhmann/improved-aesthetic-predictor)
- SigLIP 2's training adds self-distillation and masked prediction, improving dense features and transfer when its representations are reused, i.e. it is meant as a frozen backbone. — [big_vision SigLIP 2 README](https://raw.githubusercontent.com/google-research/big_vision/main/big_vision/configs/proj/image_text/README_siglip2.md)

### Inferences
- With ~3,000 labelled photos (keep / drop, or pairwise "A better than B" within a moment), a linear or 1-hidden-layer head on 768-d embeddings has ~0.6-200k parameters: small enough to avoid overfitting with cross-validation, and the inputs can be combined with the app's existing face/quality features in the same head.
- Pairwise/ranking loss within the same moment (burst) fits the album task better than absolute scores, because the app picks the best of near-duplicates.
- Zero-shot text prompts ("mehendi hands", "decor", "food", "couple looking at each other", "back of a guest") give useful scene tags with no training at all; check them on the wedding-1 photos before training.
- LoRA would need the encoder trained in PyTorch and re-exported each time; only worth it if a head on frozen features plateaus.

### Gaps
- No published result for fine-tuning SigLIP 2 heads on wedding/event photo selection; accuracy must be measured on the project's own wedding-1 album.

## Recommendation (inference, built on the findings above)

1. **Main pass on every photo:** SigLIP 2 B/32@256 vision tower only, int8, through ONNX Runtime (already in the app) or LiteRT; Apache 2.0. Ship pre-computed text-prompt vectors for scene tags, plus a small trained head for "album-worthy". Measure on the A15 first; if it costs ≤0.3 s per photo, keep 2,500 photos within ~40 min.
2. **If faster is needed:** TinyCLIP ViT-45M/32 (MIT, 1.9 GMACs, 62.7% IN-1k) as a fallback, accepting weaker features; or run B/16 only on photos that pass the basic checks.
3. **Optional short-list judge:** SmolVLM-256M (Apache) or Gemma 4 E2B (Apache, but ~1.5 GB+ memory) only for a few dozen photos (cover choice, "why picked" text), and only if Test Lab shows it fits in memory on 4 GB phones.
4. **Do not ship:** MobileCLIP/MobileCLIP2/FastVLM (Apple research-only licence), Qwen2.5-VL-3B (Qwen research licence), LFM2-VL (revenue cap); be cautious with Gemma 3n/PaliGemma (Gemma Terms obligations) and with any AVA-trained aesthetic weights (training-data terms unverified).
