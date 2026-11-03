# Free pre-trained image-level models for the on-phone album brain (quality, aesthetics, composition, similarity, segmentation, scene, captioning)

Research date: 3 Nov 2026. Network note: huggingface.co could not be reached from this session (DNS blocked), so every Hugging Face model card below was seen only through search-result summaries or mirrors, not read directly. GitHub repos were read directly. Facts marked "(training knowledge, not verified this session)" are in Gaps/Inferences only, never in Cited Findings.

Context from the project (not a web source): the app already runs YuNet + MediaPipe Face Landmarker + SFace + EfficientDet-Lite0 at about 0.5 s per photo on a Galaxy A15 (CLAUDE.md, 23 Oct 2026 run), and plans SigLIP 2 B/32 (Apache 2.0) embeddings plus a small VLM (Qwen3-VL-2B or SmolVLM2) on a short list. Anything new must be judged on what it adds beyond those.

## 1. Aesthetics and technical quality (NIMA, MUSIQ, TOPIQ, MANIQA, CLIP-IQA, LAION aesthetic v2, Q-Align, LIQE, SigLIP-based aesthetic heads)

### Takeaway
Most modern IQA/aesthetic weights are research-only or have unclear training-data rights: the whole pyiqa toolbox is PolyForm Noncommercial, CLIP-IQA and Q-Align carry the S-Lab non-commercial licence, and the SigLIP-based "aesthetic predictor v2.5" is AGPL-3.0. The cleanest options for a paid app are Apache-2.0 code with weights you train yourself: idealo NIMA (MobileNet, Apache 2.0 code), MANIQA (Apache 2.0 code), MUSIQ (Google research code) and, best of all, the project's own small head on SigLIP 2 embeddings trained on its own labelled picks; all public weights sit on AVA/KonIQ/SPAQ, whose licences for commercial model training could not be confirmed.

### Cited Findings
- pyiqa (IQA-PyTorch, the standard toolbox that hosts NIMA, MUSIQ, TOPIQ, CLIP-IQA, LIQE, Q-Align, MANIQA, DBCNN, HyperIQA etc.): "This work is licensed under the PolyForm Noncommercial License 1.0.0 and the NTU S-Lab License for applicable components." Its datasets are collected "only ... for academic, research, and educational purposes" and users must follow original dataset licences. The GitHub sidebar shows "MIT license", which conflicts with the README licence section (unexplained). Weights are on Hugging Face (IQA-PyTorch-Weights), some retrained by the maintainers; no weights licence stated. — [IQA-PyTorch README](https://github.com/chaofengc/IQA-PyTorch)
- pyiqa NR models listed include NIQE, BRISQUE, MUSIQ, topiq_nr, DBCNN, cnniqa, hyperiqa, metaiqa, wadiqam_nr, qrealign (mini/lite/pro), NIMA (nima, nima-vgg16-ava), maclip, afine, qualiclip, fgresq. — [IQA-PyTorch README](https://github.com/chaofengc/IQA-PyTorch)
- TOPIQ is distributed through pyiqa (topiq_nr), so its practical code/weights route falls under PolyForm Noncommercial / S-Lab. — [IQA-PyTorch README](https://github.com/chaofengc/IQA-PyTorch)
- CLIP-IQA: repo says "This project is licensed under NTU S-Lab License 1.0"; HF card lists licence "other" (NTU S-Lab License 1.0). — [CLIP-IQA HF card (search summary)](https://huggingface.co/harpreetsahota/CLIP-IQA); [ecosyste.ms entry for IceClear/CLIP-IQA](https://awesome.ecosyste.ms/projects/github.com%2Ficeclear%2Fclip-iqa)
- S-Lab License 1.0 text: grants "Redistribution and use for non-commercial purpose in source and binary forms"; for commercial use "please contact the contributor(s) of the work." So: non-commercial only. — [CLIP-IQA LICENSE](https://github.com/IceClear/CLIP-IQA/blob/v2-3.8/LICENSE)
- Q-Align / OneAlign: repo ships `LICENSE` and `S-Lab-LICENSE` files; built on mPLUG-Owl2 (modified code); OneAlign (`q-future/one-align`) covers IQA, aesthetics (trained with AVA) and video quality (LSVQ); score = weighted average over five text levels (excellent..bad = 5..1); training needs 8xA6000 or 4xA100. No benchmark numbers in README text. — [Q-Align repo](https://github.com/Q-Future/Q-Align)
- MANIQA: code Apache-2.0 (no separate checkpoint licence stated); ViT features + Swin transformer blocks; trained on PIPAL22, KADID-10K, KonIQ-10K; KonIQ-10K test SRCC 0.930 / PLCC 0.946; KADID-10K SRCC 0.939; PIPAL22 val SRCC 0.686. — [MANIQA repo](https://github.com/IIGROUP/MANIQA)
- NIMA (idealo implementation): Apache 2.0 ("distributed under the Apache 2.0 license"), MobileNet backbone; aesthetic model trained on AVA (EMD 0.071, LCC 0.626, SRCC 0.609); technical model trained on TID2013 (LCC 0.652, SRCC 0.675). Repo archived 2 Dec 2024 (read-only). No separate weights licence. — [idealo/image-quality-assessment](https://github.com/idealo/image-quality-assessment)
- MUSIQ (Google Research): checkpoints released for AVA, KonIQ, PaQ-2-PiQ and SPAQ plus an ImageNet pretrain; multi-scale input (native, 224, 384) and a single-scale variant; README states "This is not an official Google product" and the folder has no own LICENSE file (the google-research monorepo is generally Apache 2.0, but that was not confirmed on the page). — [MUSIQ folder](https://github.com/google-research/google-research/tree/master/musiq); paper [arXiv 2108.05997](https://arxiv.org/pdf/2108.05997)
- LAION improved aesthetic predictor ("v2"): code Apache-2.0; "a simple neural net" on CLIP embeddings; weight files named e.g. `sac+logos+ava1-l14-linearMSE.pth` (i.e. trained on SAC + LAION-Logos + AVA, CLIP ViT-L/14 implied by "l14"); no accuracy numbers in README; licence of the .pth weights not stated. — [improved-aesthetic-predictor](https://github.com/christophschuhmann/improved-aesthetic-predictor)
- SigLIP-based aesthetic predictor v2.5 (discus0434): licence AGPL-3.0 (strong copyleft). SILVA (jannchie): ordinal-regression head on frozen SigLIP2-so400m-384 embeddings, licence not visible. — [search summary: ecosyste.ms for aesthetic-predictor-v2-5](https://awesome.ecosyste.ms/projects/github.com%2Fdiscus0434%2Faesthetic-predictor-v2-5); [PyPI aesthetic-predictor-v2-5](https://pypi.org/project/aesthetic-predictor-v2-5/); [jannchie/silva](https://github.com/jannchie/silva)
- Dataset licences: KonIQ-10k is described as "freely available to the research community" (commercial use not addressed; underlying Flickr photos carry their own CC licences, e.g. CC BY-SA 2.0). AVA: an HF re-upload says apache-2.0 but that is a third-party mirror, not the authors; AVA images come from DPChallenge. SPAQ: 11,125 smartphone photos from 66 devices; no licence found. — [search summary incl. KonIQ page mirror](https://backiee.wasmer.app/http_database_mmsp-kn_de/koniq-10k-database_html); [AVA HF mirror](https://huggingface.co/datasets/Iceclear/AVA/blob/main/README.md); [AVA CVPR 2012](https://mlanthology.org/cvpr/2012/murray2012cvpr-ava)

### Inferences
- For a paid app, treat every pyiqa-hosted checkpoint (TOPIQ, LIQE, CLIP-IQA, Q-Align, retrained NIMA/MUSIQ) as non-commercial unless the original author repo says otherwise. CLIP-IQA and Q-Align are explicitly S-Lab = non-commercial.
- Even Apache-2.0 code (NIMA idealo, MANIQA, LAION predictor) ships weights trained on AVA/KonIQ/TID2013, whose terms are "research community" or unknown. The legally safest route is: keep only the architecture idea and train a tiny head (MLP or linear) on SigLIP 2 B/32 embeddings using the project's own labelled data (teacher picks for 191 Indian wedding sets, wedding-1 album). This costs ~0 phone time because the embedding is already computed.
- Q-Align/OneAlign (7B-class mPLUG-Owl2) is far too big for an A15 and non-commercial; use it, if at all, only off-phone as a teacher during development (and even that should be checked against S-Lab "use" wording).
- Correlation with "what people keep": AVA aesthetic scores (LCC ~0.6 for MobileNet NIMA) measure photo-contest taste, not album-worthiness of a family photo; KonIQ/SPAQ measure technical distortion, which the app already covers with face-crop sharpness, exposure and blur rules. Their marginal value over SigLIP-embedding heads trained on real album choices is likely small.
- MANIQA's KonIQ SRCC 0.93 is strong, but its ViT-B/8 + Swin-style head at 224 crops is heavy for G99; unlikely to beat a SigLIP head on the project's own target.

### Gaps
- No primary source read for TOPIQ's own repo licence, LIQE's repo licence, or the licence of the LAION v2 .pth weights and of SAC/LAION-Logos data (training knowledge: SAC is CC0 — not verified this session).
- No published study found that correlates any IQA/aesthetic model with real wedding-album keep/drop decisions.
- No credible on-phone latency numbers (A15/Helio G99) found for NIMA, MUSIQ, MANIQA or TOPIQ. (Training knowledge, not verified: MobileNet-v2 at 224 px typically runs in tens of ms on a mid-range CPU.)
- Exact SRCC numbers for MUSIQ, TOPIQ, LIQE, Q-Align on KonIQ/SPAQ/AVA not read (tables are in papers; MUSIQ paper linked above).

## 2. Saliency, composition, cropping and main-subject segmentation (U2-Net, GAIC, MobileSAM, EfficientSAM, SAM 2, MediaPipe segmenters, RMBG)

### Takeaway
Free-for-commercial options exist: MobileSAM (Apache 2.0, ~10M params total), EfficientSAM (Apache 2.0), U2-Net (Apache 2.0 per mirror), and MediaPipe's selfie multiclass segmenter (Apache 2.0 per mirrored model card, 256x256, 6 classes). RMBG-2.0 is non-commercial (CC BY-NC 4.0 / BRIA licence; commercial needs a paid BRIA agreement), so it is out. No licensed, phone-ready cropping/composition scorer (GAIC) was verified; a rule-based rule-of-thirds score on the existing face and person boxes is the practical substitute.

### Cited Findings
- RMBG-2.0 (BRIA): weights for non-commercial use (cards list CC BY-NC 4.0 or a custom "bria-rmbg-2.0" licence); "commercial use is subject to a commercial agreement with BRIA"; HF page gates access with "Fill in this form to immediately access the model for non commercial use". All seen via mirrors/search summaries. — [RMBG-2.0 README (HF PR copy)](https://huggingface.co/briaai/RMBG-2.0/blob/refs%2Fpr%2F6/README.md); [gated page mirror](https://ztlshhf.pages.dev/briaai/RMBG-2.0); [RMBG-1.4 README](https://huggingface.co/briaai/RMBG-1.4/raw/refs%2Fpr%2F22/README.md)
- MobileSAM: Apache-2.0; TinyViT encoder ~5M params; whole pipeline 9.66M params and ~12 ms on a GPU (8 ms encoder + 4 ms decoder) vs SAM 615M / 456 ms; "around 3s" on a Mac i5 CPU (unspecified); ONNX export script provided (tested onnxruntime 1.13.1); mIoU vs SAM 0.71-0.74 vs FastSAM 0.27-0.41. — [MobileSAM repo](https://github.com/ChaoningZhang/MobileSAM)
- EfficientSAM: Apache-2.0 per GitHub mirror and HF card tag. — [xetdata/efficientsam (ecosyste.ms)](https://awesome.ecosyste.ms/projects/github.com%2Fxetdata%2Fefficientsam); [EfficientSAM HF README](https://huggingface.co/yunyangx/EfficientSAM/blob/main/README.md?code=true)
- U2-Net: Apache-2.0 "same as the upstream xuebinqin/U-2-Net repo" (secondary claim from an ONNX re-packaging). — [Heliosoph/u2net-onnx](https://huggingface.co/Heliosoph/u2net-onnx)
- MediaPipe selfie multiclass segmenter: 256x256 input, float32, classes background, hair, body-skin, face-skin, clothes, others (accessories); mirrored model card says Apache 2.0, model date 10 May 2023; intended for selfie/portrait framing with people within a few metres; outputs raw logits (softmax outside the model). — [MediaPipe Image Segmenter docs](https://developers.google.cn/mediapipe/solutions/vision/image_segmenter); [Model card PDF mirror](https://dcmpx.remotevs.com/com/googleapis/storage/SL/mediapipe-assets/Model%20Card%20Multiclass%20Segmentation.pdf); [ONNX port notes](https://huggingface.co/senty-au/selfie_multiclass_256x256-ONNX)

### Inferences
- The app already has YuNet face boxes and EfficientDet-Lite0 person boxes. A main-subject mask adds two things: (a) "subject cut off / cramped" and rule-of-thirds placement of the couple (can mostly be computed from boxes alone), and (b) detail-shot judgement for no-face photos (mehendi hands, jewellery, decor), where a saliency mask (U2-Net-small) tells "clear subject vs clutter". (b) is the stronger reason to add one.
- MediaPipe multiclass is the cheapest add (same MediaPipe runtime already bundled, 256 px) and its body-skin/hair/clothes classes could help find mehendi hands and outfits, but its selfie/close-range training makes it weak on wide stage shots.
- MobileSAM/EfficientSAM need a prompt (point/box); feeding the existing person box gives a clean couple mask, but a TinyViT at 1024 px on a G99 CPU is likely hundreds of ms to seconds per photo (from the "~3 s on Mac i5" report), so it should only run on shortlisted candidates, if at all.
- SigLIP 2 cannot replace pixel masks, but zero-shot prompts ("a close-up photo of hands with henna", "a cluttered photo") already cover much of the detail-shot classification without a segmenter.

### Gaps
- SAM 2 licence (training knowledge: Apache 2.0 for code and checkpoints, not verified this session) and SAM 2 tiny on-phone speed.
- GAIC (grid anchor image cropping) and other cropping/composition scorers: no licence or repo info returned; not verified.
- No A15/G99 latency figures for U2-Net(p), MobileSAM, EfficientSAM or the MediaPipe multiclass model.
- Google's own hosted copy of the multiclass model card was not read (only a mirror).

## 3. Near-duplicate and same-scene detection (DINOv2, DINOv3, SigLIP, perceptual hashes)

### Takeaway
DINOv3 is not Apache: it uses Meta's custom "DINOv3 License" with gated download, which seems to allow commercial use (no non-commercial clause, no user cap) but adds redistribution, indemnity, trade-control/military bans, a reverse-engineering ban and Meta's right to change terms; that is heavier legal baggage than Apache. Indirect evidence says self-supervised DINO features beat CLIP-style text-aligned features for exact near-duplicate matching, but no rigorous wedding-scale benchmark was found; for bursts, a perceptual hash + time gap plus the already-planned SigLIP 2 embedding is probably enough, with DINOv2-S (Apache 2.0, training knowledge) as a fallback if SigLIP mis-groups "same scene, different moment".

### Cited Findings
- DINOv3 code and weights are "released under the DINOv3 License"; weights require a request through Meta's download page (emailed URLs). Sizes: ViT-S/16 distilled 21M, ViT-S+/16 29M, ViT-B/16 86M, ViT-L/16 300M, ViT-H+/16 840M, ViT-7B 6.7B; ConvNeXt Tiny 29M, Small 50M, Base 89M, Large 198M (LVD-1689M web data). — [DINOv3 repo](https://github.com/facebookresearch/dinov3)
- DINOv3 License terms: "non-exclusive, worldwide, non-transferable and royalty-free limited license" to use, reproduce, distribute, modify; no non-commercial limit stated; must "provide a copy of this Agreement" with any redistributed DINO Materials; bans reverse engineering/decompiling; bans ITAR/trade-control end uses incl. "military or warfare purposes, nuclear industries or applications, espionage"; indemnify Meta; licence ends if you sue Meta; Meta may modify the Agreement and continued use = acceptance; California law. — [DINOv3 LICENSE.md](https://github.com/facebookresearch/dinov3/blob/main/LICENSE.md)
- DINOv2 vs CLIP for similarity: in a 400-sample CAD-render ablation DINOv2 got median correct-match rank 1 (mean 2.21) vs CLIP 6 (mean 18.24). — [VideoCAD, arXiv 2505.24838](https://arxiv.org/pdf/2505.24838)
- An ImageNet audit paper started with DINOv2 for duplicate detection, saying CLIP results were not satisfactory (informal remark). — [Flaws of ImageNet, arXiv 2412.00076](https://arxiv.org/pdf/2412.00076)
- Roboflow states self-supervised DINOv2 tends to be stronger for near-duplicate detection (vendor claim, unmeasured). — [Roboflow DINOv2 vs CLIP](https://playground.roboflow.com/models/compare/dinov2-vs-openai-clip)
- No rigorous DINOv2-vs-CLIP result on a copy-detection benchmark (e.g. DISC21) was found. — [search summary; blog](https://medium.com/aimonks/clip-vs-dinov2-in-image-similarity-6fa5aa7ed8c6)

### Inferences
- Wedding "bursts" (same pose, seconds apart) are nearly pixel-identical: a 64-bit pHash/dHash on a 32x32 thumbnail plus EXIF time gap catches most of them at ~0 cost, and the app already does burst grouping. Embeddings matter for "same scene, different frame" (same family on stage, slightly different crop/zoom), where SigLIP 2 cosine similarity is likely adequate because same group + same backdrop dominates; combine it with SFace identity sets (same people) to avoid merging different families on the same stage backdrop.
- Adding DINOv2/DINOv3 means a second ViT pass per photo (another ~86M params for B, ~21M for S), contradicting the "one backbone" goal. Only worth it if tests on wedding-1 show SigLIP grouping errors. DINOv3's custom licence (licence copy in the app, Meta's right to change terms, gated download) makes DINOv2 the simpler choice if a DINO model is needed.
- A cheap middle path: SigLIP 2 patch tokens (not just the pooled embedding) can be pooled into a spatial grid descriptor for finer near-duplicate matching without a second model (untested idea).

### Gaps
- DINOv2 licence: training knowledge says code and weights moved to Apache 2.0 in 2023; not verified this session.
- No wedding-scale (2,500 photos) benchmark comparing pHash, SigLIP 2, DINOv2, DINOv3 on bursts vs same-scene grouping was found; the project should measure on wedding-1 itself.
- No on-phone latency for DINOv2-S/14 or DINOv3 ViT-S/16 on G99 found.

## 4. Scene/action recognition and captioning beyond SigLIP (Florence-2, Moondream, BLIP/BLIP-2, PaliGemma, Places365, video/action models)

### Takeaway
Florence-2-base (0.23B, MIT, ONNX ports exist, INT8 ~270 MB) is the most phone-sized, commercially clean captioner/detector; Moondream 3 is BSL 1.1 (commercial internal use allowed, but offering it to third parties needs a deal, so in an app shipped to customers it is risky), and Moondream 2's Apache 2.0 status was only seen on an aggregator. Places365 models are CC BY (attribution, commercial OK) but are old ResNet/VGG scene classifiers that SigLIP 2 zero-shot prompts largely supersede. A captioner mainly adds an explanation sentence ("why it is in the album") and grounded detection, which the planned VLM already covers.

### Cited Findings
- Florence-2-base: 0.23B params, MIT; official onnx-community/Florence-2-base port tagged MIT with a Transformers.js captioning example. — [onnx-community/Florence-2-base README](https://huggingface.co/onnx-community/Florence-2-base/blob/main/README.md) (search summary); [OpenVINO Florence-2 notebook](https://nbviewer.org/github/openvinotoolkit/openvino_notebooks/blob/latest/notebooks/florence2/florence2.ipynb)
- Third-party Florence-2-base-ft ONNX: INT8 ~270 MB vs fp16 ~520 MB; author says dense small-text OCR loses accuracy at INT8 while captioning/detection are largely unaffected (self-reported). — [Heliosoph INT8](https://huggingface.co/Heliosoph/florence-2-base-ft-quantized-onnx); [Heliosoph fp16](https://huggingface.co/Heliosoph/florence-2-base-ft-fp16-onnx)
- Moondream 3 Preview: Business Source License 1.1 (M87 Labs), change licence Apache 2.0 two years after first release; Additional Use Grant allows any purpose incl. commercial and self-hosting for internal use, but restricts offering the model or a service exposing it to third parties without a commercial agreement. — [moondream3-preview LICENSE.md](https://huggingface.co/moondream/moondream3-preview/blob/main/LICENSE.md) (search summary)
- Moondream 2 described as Apache 2.0 only on a third-party listing (unverified). — [freeapihub](https://freeapihub.com/ai-models/moondream-3)
- Places365: code MIT; pretrained CNNs "can be used under the Creative Common License (Attribution CC BY)"; image copyright belongs to owners; architectures AlexNet, GoogLeNet, VGG16, ResNet152, ResNet50, ResNet18, DenseNet161 (no MobileNet); ResNet152 top-1 error 45.26% / top-5 15.02% (≈54.7% / 85.0% accuracy). — [CSAILVision/places365](https://github.com/CSAILVision/places365)

### Inferences
- For Indian weddings, Places365 classes (banquet hall, ballroom, garden, temple) add little: events already come from the photographer's folders, and SigLIP 2 zero-shot prompts can name "stage with couple", "close-up of mehendi hands", "food buffet", "people dancing", "crying at farewell" directly.
- Florence-2-base adds dense captions and open-vocabulary grounding ("<OD>", region captions) at ~270 MB INT8; but the planned Qwen3-VL-2B/SmolVLM2 does captioning too. Pick one generative model, not both. Florence-2 is smaller and MIT, but is a captioner/detector, not a reasoner (cannot answer "is anyone mid-blink?" style questions reliably).
- Moondream 3's BSL "third parties" wording is ambiguous for an app sold to couples; avoid unless a lawyer or M87 Labs confirms on-device distribution is allowed.
- Video/action models are not needed: the input is still photos; "moment" detection already uses shooting-density bursts.

### Gaps
- BLIP / BLIP-2 licences (training knowledge: BLIP code BSD-3-Clause; BLIP-2 depends on OPT/Flan-T5 licences) and PaliGemma (Gemma Terms of Use, with prohibited-use policy) not verified this session; huggingface.co blocked.
- No measured Florence-2 latency/memory on any Android phone found; must be measured on the A15.
- Video/action recognition models were not researched (judged out of scope for still photos).

## 5. Sharing one backbone with SigLIP 2 (heads on the same embedding)

### Takeaway
The cheapest gains come from small heads on the SigLIP 2 B/32 embedding the app already plans: an aesthetic/album-worthiness head (like the LAION predictor and SILVA, both just MLP/ordinal heads on frozen CLIP/SigLIP embeddings), scene/detail-shot zero-shot prompts (replacing Places365), and same-scene similarity. Separate pixel models are only justified for masks (segmentation) and possibly a second similarity model if tests show SigLIP fails.

### Cited Findings
- The LAION improved aesthetic predictor is a "simple neural net" taking "CLIP embeddings as inputs" (i.e. a head on a frozen backbone). — [improved-aesthetic-predictor](https://github.com/christophschuhmann/improved-aesthetic-predictor)
- SILVA fits an ordinal-regression head on frozen SigLIP2-so400m-384 embeddings; aesthetic predictor v2.5 is SigLIP-based but AGPL-3.0. — [jannchie/silva](https://github.com/jannchie/silva); [aesthetic-predictor-v2-5 (ecosyste.ms)](https://awesome.ecosyste.ms/projects/github.com%2Fdiscus0434%2Faesthetic-predictor-v2-5)
- CLIP-IQA's method is prompt-pairing on CLIP features (good/bad photo) but its code/weights are S-Lab non-commercial. — [CLIP-IQA LICENSE](https://github.com/IceClear/CLIP-IQA/blob/v2-3.8/LICENSE)

### Inferences
- Plan for "one backbone, many heads" on SigLIP 2 B/32 (one image pass per photo):
  1. Album-worthiness head: linear/MLP trained on the project's own keep/drop labels (wedding-1 album, 191 teacher-picked sets). Replaces NIMA/LAION/MUSIQ and avoids AVA/KonIQ licence questions.
  2. Zero-shot prompt pairs re-implemented from scratch (idea of CLIP-IQA, not its code): "a sharp, well-lit photo" vs "a blurry, dark photo"; "a candid photo of people laughing together"; "a detail photo of jewellery / henna hands / decor / food"; "a group photo on a stage"; "a photo of the back of people". Prompt ideas are not covered by S-Lab licence; only their code/weights are.
  3. Same-scene grouping by cosine similarity, combined with SFace identity sets and time.
  4. Composition from existing boxes (no model).
- Only these need their own network: MediaPipe multiclass or U2-Net-small for masks (optional), and the planned VLM (or Florence-2) for explanations, run only on the shortlisted few hundred photos.
- Size/time budget sense (inference from project numbers): current stack ~0.5 s/photo on A15; one SigLIP 2 B/32 pass at 224-256 px is the main new cost; heads add microseconds. A second ViT (DINO) or a 0.23B-2B generative model on all 2,500 photos would multiply total time, so generative models must run only on candidates.

### Gaps
- No published measurement found of SigLIP 2 B/32 heads vs dedicated IQA models on technical-quality benchmarks (KonIQ/SPAQ), so how much technical-quality signal the SigLIP embedding carries is unknown; must be measured on the project's own labels.
- No A15 latency number for SigLIP 2 B/32 found in this session (that is covered by other research notes, if any).
