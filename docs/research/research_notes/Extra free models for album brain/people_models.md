# Free pre-trained models that understand PEOPLE in photos (on-device, Android, paid app)

Scope note: the app already runs YuNet (faces), MediaPipe Face Landmarker with blendshapes (blink, smile,
jaw open, gaze, head angle), SFace (identity) and EfficientDet-Lite0 (people boxes, phones). These notes
cover what those miss: expression/emotion classes, age (elders), gaze between two people, body pose and
gestures, interactions, hands. Research done 3 Nov 2026. Network limits in this session: ai.google.dev,
gaze360.csail.mit.edu, ai-benchmark.com and github.com HTML pages were not reachable; raw GitHub files,
Hugging Face raw files and Google Storage model-card PDFs were. Where only a search-engine summary was
available, it says so.

## Facial expression / emotion (laughing, tears, joy, surprise, serious-but-fine)

### Takeaway
Every strong, small expression model found (HSEmotion/EmotiEffLib, DAN, EmoNet, OpenCV Zoo's Progressive
Teacher) is trained on AffectNet or RAF-DB, datasets whose terms are research/non-commercial; the code is
often Apache/MIT, but the weights inherit a dataset question that no source settles. The cleanest
licence path is FER+ (MIT model in the ONNX Model Zoo, but old and weak) or a small classifier trained by us
on the app's own MediaPipe blendshapes (already Apache 2.0, already running), which public reports put at
roughly 50-71% on FER2013, about the level of the image models; neither detects tears.

### Cited Findings
- **HSEmotion / EmotiEffLib (A. Savchenko):** code under Apache 2.0, README says "There is no limitation
  for both academic and commercial usage" (about the code). — [EmotiEffLib LICENSE](https://github.com/sb-ai-lab/EmotiEffLib/blob/main/LICENSE); [EmotiEffLib README](https://github.com/sb-ai-lab/EmotiEffLib)
- HSEmotion weights are "models that obtained the state-of-the-art results for AffectNet dataset", i.e.
  trained on AffectNet; PyTorch and ONNX back-ends (`hsemotion-onnx` pip package). — [EmotiEffLib README](https://github.com/sb-ai-lab/EmotiEffLib)
- HSEmotion measured on a phone (Samsung Fold 3, Snapdragon 888 CPU, Android 12), from the README table:

  | Model | AffectNet 8-class acc. | AffectNet 7-class acc. | Time per face | Size |
  |---|---|---|---|---|
  | mobilenet_7 | - | 64.71% | 16 ± 5 ms | 14 MB |
  | enet_b0_8_best_afew | 60.95% | 64.63% | 59 ± 26 ms | 16 MB |
  | enet_b0_8_va_mtl (also valence/arousal) | 61.93% | 64.94% | 60 ± 32 ms | 16 MB |
  | enet_b2_8_best | 63.125% | 66.51% | 191 ± 18 ms | 30 MB |

  — [EmotiEffLib README](https://github.com/sb-ai-lab/EmotiEffLib)
- HSEmotion team took 1st place in Expression Recognition at the 8th ABAW competition. — [EmotiEffLib README](https://github.com/sb-ai-lab/EmotiEffLib)
- **AffectNet licence:** a copy of the "AffectNet Academic Use License" says the dataset may be used only
  for non-commercial research and educational purposes; researchers at for-profit companies need a separate
  commercial licence agreement. (Third-party copy found via search summary only; the official page was not
  fetched.) Nothing found on whether weights trained on AffectNet may be used commercially. — [AffectNet agreement copy (studylib)](https://studylib.net/doc/27680111/affectnet-agreement-v2-30mar2023)
- **EmoNet (face-analysis/emonet):** "Code available under a Creative Commons Attribution-Non Commercial-No
  Derivatives 4.0 International Licence (CC BY-NC-ND)". Not usable in a paid app. — [EmoNet LICENSE.txt](https://github.com/face-analysis/emonet/blob/master/LICENSE.txt)
- **DAN (yaoing/DAN):** trained on AffectNet and RAF-DB; published AffectNet-8 checkpoint at 62.09% accuracy.
  Repo licence could not be read (GitHub page blocked in this session). — [DAN README](https://github.com/yaoing/DAN)
- **POSTER / POSTER++:** licence file not found at the paths tried (404) and GitHub page blocked; not verified.
  These are ViT-based and larger than HSEmotion (size not verified here). — gap
- **OpenCV Zoo facial expression (Progressive Teacher):** MobileFaceNet backbone, 7 classes (angry, disgust,
  fearful, happy, neutral, sad, surprised), ONNX plus int8 block-quantised file, 88.27% on RAF-DB, designed to
  run after YuNet (which the app already uses). No LICENSE file in that model folder (404); RAF-DB is the
  evaluation set and probably the training set. — [OpenCV Zoo FER README](https://github.com/opencv/opencv_zoo/tree/main/models/facial_expression_recognition)
- **FER+ (Microsoft) in the ONNX Model Zoo:** README marked MIT ("SPDX-License-Identifier: MIT", "License:
  MIT"); input 1x64x64 grayscale face; 8 classes. — [ONNX Model Zoo emotion_ferplus](https://github.com/onnx/models/tree/main/validated/vision/body_analysis/emotion_ferplus)
- **Blendshapes alone:** a review of "Emotion estimation from video footage with LSTM" reports about 71%
  categorical accuracy on FER2013 using MediaPipe blendshape features (secondhand review). — [Moonlight review](https://www.themoonlight.io/en/review/emotion-estimation-from-video-footage-with-lstm)
- A model card for a Random Forest on the 52 MediaPipe blendshape scores reports 51.36% on FER-2013 (7
  classes), against a stated human baseline of about 65-68% (unreviewed page, mirror site, search summary). — [MediaPipe Face Emotion Classifier (mirror)](https://cloudfare-proxy.pages.dev/Nonovogo/face-emotions2)

### Inferences
- For a paid app, treat HSEmotion, DAN, OpenCV-Zoo FER and POSTER weights as **"licence unclear, probably
  research-only data"** until the dataset owners say otherwise; EmoNet is clearly out (NC-ND).
- Speed is not the blocker: mobilenet_7 at 16 ms on a Snapdragon 888 would likely be roughly 30-50 ms on the
  Galaxy A15's Cortex-A76 cores (estimate, not measured), small next to the app's current ~0.5 s per photo.
- The categories the album needs (laughing vs. mid-talk, crying/tears as "fine", serious ritual face as
  "fine", surprise) are mostly reachable from the blendshapes the app already has: smile + jawOpen + cheek
  squint + eye squint gives "laughing"; brow-inner-up + mouth-frown gives "sad/crying face". Tears
  themselves (wet eyes, tissue) are a visual cue no expression model labels; the face-expression "sad"
  class is the closest proxy. A tiny on-device classifier (logistic regression / small MLP) on blendshapes,
  trained on the project's own labelled wedding faces, avoids every dataset-licence question.
- 8-class accuracy around 60-66% for all AffectNet models means even the best image model is wrong on
  about a third of faces; use expression as a soft score, never as a hard reject.

### Gaps
- Official AffectNet terms page (mohammadmahoor.com) and any statement on trained weights: not fetched.
- RAF-DB and FER2013 licence terms not verified (RAF-DB is widely described as research-only; FER2013 images
  were scraped from Google image search, licence unclear). FER+ model is MIT but trained on FER2013 images.
- POSTER and DAN repo licences not readable from this session.
- No source found benchmarking any expression model on South Asian faces or on wedding photos.
- No measured Helio G99 timings for any of these models (ai-benchmark.com blocked).

## Age estimation (finding elders and grandparents)

### Takeaway
The accurate age models are not free for a paid app: InsightFace weights (genderage) are non-commercial,
MiVOLO v2 is CC BY-NC 4.0 and MiVOLO v1 has a custom licence we could not read. Apache-labelled options
exist on Hugging Face (e.g. a ViT age-gender model, MAE 4.5 years) but are trained on UTKFace, whose own terms
are non-commercial research, and an 86.8M-parameter ViT is heavy for the phone. For "is this an elder?"
the safest route is a coarse age band (60+) trained by us, or relative signals (grey/white hair, who sits
in the centre), with age only as a soft hint.

### Cited Findings
- **InsightFace:** "The code of InsightFace is released under the MIT License... The training data
  containing the annotation (and the models trained with these data) are available for non-commercial
  research purposes only." Both repo-downloaded and auto-downloaded models follow that policy. Update
  2025-11-24: for open face recognition models (e.g. buffalo_l) "please contact
  recognition-oss-pack@insightface.ai for licensing". So the genderage model in buffalo_l is non-commercial. — [InsightFace README](https://github.com/deepinsight/insightface)
- **MiVOLO (v1, WildChlamydia):** README points to a `license` folder for terms; a Hugging Face copy of a
  MiVOLO checkpoint lists `license: other`, `license_name: custom`, linking to `license/en_us.pdf` in the
  repo. The PDF could not be downloaded here (403/404). Third-party mirrors labelled "MIT" are not the
  authors' licence. — [MiVOLO README](https://github.com/WildChlamydia/MiVOLO); [typorch HF card](https://huggingface.co/typorch/mivolo-imdb_cross_person-yolov8x_person_face)
- **MiVOLO v2 (iitolstykh/mivolo_v2 on Hugging Face):** model card `license: cc-by-nc-4.0`. — [mivolo_v2 card](https://huggingface.co/iitolstykh/mivolo_v2)
- MiVOLO quality (README): IMDB-clean age MAE 4.22-4.29, CS@5 about 68%; face+body model 4.24 MAE, body-only
  6.87; UTKFace 4.23 MAE; cross-dataset AgeDB 5.58 MAE (face). Uses a face + person-body dual input (it can
  estimate age when the face is small or turned away). The README also advertises a newer successor with
  APPA-Real MAE 4.07. — [MiVOLO README](https://github.com/WildChlamydia/MiVOLO)
- **ViT age-gender (abhilash88, ONNX copy by onnx-community):** `license: apache-2.0`, trained on UTKFace
  (23,687 images), age MAE 4.5 years, gender 94.3%, ViT-Base, 86.8M parameters, 224 px input
  (google/vit-base-patch16-224). — [age-gender-prediction-ONNX card](https://huggingface.co/onnx-community/age-gender-prediction-ONNX)
- **FairFace:** 108,501 face images from YFCC-100M (Flickr), balanced across seven race groups including
  "Indian"; authors report models trained on it are more accurate on new datasets and more consistent across
  race and gender groups. Repo README states "License: CC BY 4.0". — [FairFace paper (arXiv 1908.04913)](https://arxiv.org/abs/1908.04913); [FairFace repo](https://github.com/joojs/fairface)
- FairFace age is given as 9 bins (0-2 ... 70+); a secondary leaderboard lists top-1 age accuracy 59.7 on
  FairFace (search summary, secondary). — [hyper.ai entry](https://hyper.ai/en/papers/1908.04913)

### Inferences
- FairFace is the only well-known age resource with an "Indian" group and a permissive-looking licence
  (CC BY 4.0, but the underlying Flickr images carry their own licences). A small model trained by us on
  FairFace age bins, collapsed to "child / adult / 60+", is the most licence-defensible way to find elders,
  and should be checked by a lawyer's eye against the Flickr image terms.
- A 9-bin top-1 accuracy near 60% means exact age is unreliable; but "60+ vs. under 40" is a much easier
  call, and combining it with the app's existing "photographed with the couple at several times of day"
  signal is likely more useful than raw age.
- Bias: no source found with per-group age error for South Asian faces. Wedding make-up, lighting and grey
  hair dye (common for parents) are likely to push estimates around; age should rank, not decide.
- An 86.8M ViT would add roughly 350 MB (fp32) to the APK and hundreds of ms per face on the A15 (estimate);
  too heavy for every face. MiVOLO is excluded on licence regardless of its quality.

### Gaps
- MiVOLO v1 licence PDF text (could not download) and whether it permits commercial use.
- UTKFace terms (commonly stated as non-commercial research only) not fetched in this session; if so, the
  Apache-labelled ViT model's weights carry a dataset risk.
- FairFace per-race age error table (in the WACV 2021 paper) not extracted.
- SSR-Net, DEX (IMDB-WIKI) and other small age models: licences not checked (IMDB-WIKI is generally described
  as academic-only; not verified here).

## Gaze and mutual eye contact between two people

### Takeaway
The app's own MediaPipe blendshapes (eyeLookIn/Out/Up/Down) plus head angle already give a rough gaze
direction per face; mutual gaze can be computed geometrically (each person's gaze ray pointing toward the
other's face position). Dedicated gaze models (L2CS-Net) have MIT code but their best weights are trained on
Gaze360, whose dataset and code are non-commercial research only, and they use ResNet-50 (heavy). Head-pose
models like 6DRepNet are MIT but also trained on research datasets.

### Cited Findings
- **L2CS-Net:** MIT licence (code), ResNet-50 architecture, pretrained `L2CSNet_gaze360.pkl` weights;
  training/test code for Gaze360 and MPIIGaze. — [L2CS-Net LICENSE](https://github.com/Ahmednull/L2CS-Net/blob/main/LICENSE); [L2CS-Net README](https://github.com/Ahmednull/L2CS-Net)
- **Gaze360 dataset:** the official repository says use of the dataset and code is for non-commercial
  research only (search summary; the site gaze360.csail.mit.edu could not be resolved here). — [Gaze360 GitHub](https://github.com/erkil1452/gaze360)
- No source found settling whether MIT-licensed weights trained on Gaze360 escape that restriction (search
  summary). — [Roboflow L2CS page](https://roboflow.com/model/l2cs-net)
- **6DRepNet (head pose):** MIT licence. — [6DRepNet LICENSE](https://github.com/thohemp/6DRepNet/blob/master/LICENSE)

### Inferences
- For "the couple looking at each other" (a bonus in CLAUDE.md), a geometric rule on existing signals is the
  practical choice: head yaw of A points toward B's face side and vice versa, faces roughly level, eyes open,
  plus blendshape gaze agreeing. This needs no new model and no new licence.
- L2CS-Net would add a ResNet-50 (about 25M parameters, typical size; not measured) per face: too slow for
  every face on a Helio G99 and licence-risky; not recommended.

### Gaps
- No measured speed of L2CS-Net on any phone.
- No permissively trained, small gaze model found. MediaPipe Iris landmarks are part of Face Landmarker
  already (the app has them), but no official accuracy figure for eye-contact detection was found.

## Body pose and gestures (blessing, pranam/touching feet, hugging, garlanding, dancing)

### Takeaway
Two clean, permissive choices exist: MediaPipe Pose Landmarker (Apache 2.0, 33 landmarks, 3/6/26 MB, measured
fairly across regions including Southern Asia) and MMPose's RTMPose/RTMO (Apache 2.0 code; RTMPose-s 5.47M
params, 13.9 ms on a Snapdragon 865 with ncnn). MoveNet MultiPose finds up to 6 people at once. No model labels
"blessing" or "pranam" directly; these come from rules on keypoints across two people (wrist above another's
head; one person bent low with hands near another's feet; two torsos overlapping with arms around).

### Cited Findings
- **MediaPipe Pose (BlazePose GHUM 3D) model card:** "LICENSED UNDER Apache License, Version 2.0"; Lite
  (3 MB), Full (6 MB) and Heavy (26 MB); MobileNetV2-like CNN; input 256x256x3 crop of one detected person;
  output 33 landmarks x (x, y, z, visibility, presence); intended for "single-person videos". — [BlazePose GHUM 3D model card (PDF)](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf)
- Fairness table in the same card (PDJ, higher is better): Southern Asia 86.6 (Lite) / 91.2 (Full) / 96.2
  (Heavy) against averages 87.0 / 91.8 / 94.2; range across 14 regions 6.5 / 4.8 / 4.8. — [BlazePose model card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf)
- Latency on a Pixel 3 with TFLite GPU: Heavy 53 ms, Full 25 ms, Lite 20 ms. Older docs say the pipeline
  assumes a person 2-4 m from the camera. — [MediaPipe pose docs (legacy)](https://github.com/google-ai-edge/mediapipe/blob/master/docs/solutions/pose.md)
- OpenCV Zoo also carries an MP-Pose ONNX port, Apache 2.0. — [OpenCV Zoo pose_estimation_mediapipe](https://github.com/opencv/opencv_zoo/tree/main/models/pose_estimation_mediapipe)
- **RTMPose (MMPose):** MMPose is Apache 2.0. Body models at 256x192 input:

  | Model | COCO AP | Params | ncnn FP16 latency, Snapdragon 865 |
  |---|---|---|---|
  | RTMPose-t | 68.5 | 3.34M | 9.02 ms |
  | RTMPose-s | 72.2 | 5.47M | 13.89 ms |
  | RTMPose-m | 75.8 | 13.59M | 26.44 ms |
  | RTMPose-l | 76.5 | 27.66M | 45.37 ms |

  Paired with the RTMDet-nano person detector (0.99M params, 320x320). Exportable to ONNX, ncnn, TensorRT;
  Android examples with ncnn. Published checkpoints are named `aic-coco` (trained on AI Challenger + COCO). — [RTMPose README](https://github.com/open-mmlab/mmpose/tree/main/projects/rtmpose); [MMPose LICENSE](https://github.com/open-mmlab/mmpose/blob/main/LICENSE)
- **RTMO (one-stage, multi-person in one pass):** RTMO-s 67.7 AP (COCO) / 68.6 (body7) at 640x640, RTMO-l 74.8
  AP and 141 FPS on a V100; latency column in the README 8.9 / 12.4 / 19.1 ms (s/m/l; device not stated in the
  excerpt read). — [RTMO README](https://github.com/open-mmlab/mmpose/tree/main/projects/rtmo)
- **ViTPose:** Apache 2.0. — [ViTPose LICENSE](https://github.com/ViTAE-Transformer/ViTPose/blob/main/LICENSE)
- **MoveNet:** MULTIPOSE_LIGHTNING "detects up to 6 poses"; speed does not depend on number of people; FPS
  on a Pixel 5 with WebGL: SinglePose Lightning 34, Thunder 12, MultiPose Lightning 8 (browser, not native).
  The tfjs-models repo is Apache 2.0. — [MoveNet README (tfjs-models)](https://github.com/tensorflow/tfjs-models/blob/master/pose-detection/src/movenet/README.md); [tfjs-models LICENSE](https://github.com/tensorflow/tfjs-models/blob/master/LICENSE)
- TensorFlow blog: MoveNet Lightning FP16 TFLite 4.8 MB, 63.0 mAP (on their own evaluation set) (search
  summary). — [TensorFlow blog, Aug 2021](https://blog.tensorflow.org/2021/08/pose-estimation-and-classification-on-edge-devices-with-MoveNet-and-TensorFlow-Lite.html)
- A 2025 paper builds human-human interaction recognition for edge devices from MoveNet keypoints plus a
  distance grid (search summary). — [Efficient interaction recognition for edge devices](https://vjol.info.vn/dntu/article/view/116143)

### Inferences
- The app already has EfficientDet-Lite0 people boxes; running MediaPipe Pose Lite/Full on each person box
  (top-down) or RTMO-s once per photo (bottom-up) are both feasible. With 10-40 people in a stage photo,
  RTMO/MoveNet MultiPose (one pass) scale better than one crop per person; MoveNet MultiPose stops at 6.
- RTMPose-s at ~14 ms on a Snapdragon 865 suggests very roughly 25-40 ms per person on the A15's A76 cores
  (estimate; ncnn, not ONNX Runtime). Pose only needs to run on the short list (photos already likely picks),
  not all 2,500 photos.
- Rules from pose that look workable: blessing = one person's wrist within a head-width above another
  person's head; pranam / touching feet = one person's hips and shoulders low (torso near horizontal or
  kneeling) with wrists near another person's ankles; hug = two people's shoulders overlapping with wrists
  behind the other's back; garlanding = both arms raised with wrists near the other's neck/head height (pairs
  well with the "couple facing each other" check); dancing = arms above shoulders, legs apart, motion blur.
  All need testing on real wedding photos; heavy lehengas and sarees hide hips and knees, and crowded stages
  give overlapping people.
- RTMPose `aic-coco` weights: COCO annotations are CC BY 4.0 with Flickr images; AI Challenger terms not
  verified. MediaPipe Pose has the clearest licence statement (Apache 2.0 in the model card itself).

### Gaps
- Current MediaPipe Pose Landmarker (Tasks API) docs on ai.google.dev could not be reached: multi-person
  `num_poses` behaviour and current Android latency table not verified here.
- No measured pose timings on Helio G99 / Galaxy A15 for any model.
- AI Challenger dataset licence (RTMPose weights) and MoveNet's weight licence on Kaggle not verified.
- No published accuracy for recognising pranam, blessing or garlanding from keypoints.

## Human-human and human-object interaction (HOI) detection

### Takeaway
No small HOI detector benchmarked on phones was found. Research HOI models (UnionDet, recent egocentric
Mamba+YOLO-World systems) target GPUs and HICO-DET/V-COCO, whose datasets are research-use. The practical
on-phone path is pose + geometry rules (above) plus the existing object detector, and, for a short list
only, a vision-language model if one is ever added.

### Cited Findings
- UnionDet (ECCV 2020): one-stage HOI detector, 4x-14x faster interaction prediction while beating
  state-of-the-art on V-COCO and HICO-DET (GPU-class; search summary). — [UnionDet, ECCV 2020](https://www.ecva.net/papers/eccv_2020/papers_ECCV/html/2391_ECCV_2020_paper.php)
- Finocchiaro et al. 2025: real-time egocentric HOI with a Mamba action model (EfficientNetV2 backbone) plus
  fine-tuned YOLO-World, 38.52% p-AP on ENIGMA-51 at 30 fps; industrial egocentric video, no phone
  deployment shown (search summary). — [arXiv 2507.13326](https://arxiv.org/abs/2507.13326v2)
- Edge-device human-human interaction from MoveNet keypoints + distance grid (2025, video). — [vjol.info.vn article](https://vjol.info.vn/dntu/article/view/116143)

### Inferences
- For wedding moments the interactions that matter are few and fixed (garland, ring, sindoor, blessing,
  feet-touching, hug, feeding sweets, holding hands). Pose geometry between the couple's two skeletons, plus
  hand landmarks (next section) and the EfficientDet object classes, covers most of them without an HOI
  model. Ritual names must stay internal (CLAUDE.md: no ritual labels shown).
- A VLM judging only the top candidates per moment is the fallback for open-ended "what is happening"
  (covered by other researchers; not evaluated here).

### Gaps
- No mobile HOI benchmark or licence-clean small HOI model found; HICO-DET / V-COCO licence terms not checked.

## Hand detection / landmarks (mehendi, ring, garland moments)

### Takeaway
MediaPipe Hand Landmarker is Apache 2.0, small, measured across 14 regions, and finds 21 3D keypoints per
hand from a palm detector (95.7% average precision). It suits mehendi detail shots (hands filling the frame,
no faces) and ring/garland moments (two people's hands touching between them).

### Cited Findings
- Hand Tracking model card: "LICENSED UNDER Apache License, Version 2.0"; two-step pipeline (palm detector on
  the full image, then landmark model on a crop); landmark input 224x224x3; output 21 3D landmarks, presence
  score and handedness; Lite and Full models; evaluated across 14 geographical subregions (mean normalised
  error per region in the card; 5 annotators re-annotating gave 6.0% MNAE). — [Hand Tracking model card (PDF)](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Hand%20Tracking%20(Lite_Full)%20with%20Fairness%20Oct%202021.pdf)
- Palm detector: "average precision of 95.7% in palm detection"; lite and full palm-detection TFLite files;
  `max_num_hands` setting. — [MediaPipe hands docs (legacy)](https://github.com/google-ai-edge/mediapipe/blob/master/docs/solutions/hands.md)
- OpenCV Zoo carries an MP-HandPose ONNX port (runs with OpenCV DNN). — [OpenCV Zoo README](https://github.com/opencv/opencv_zoo)

### Inferences
- Mehendi: palm detection on a photo with no faces and 1-4 large hands is a strong "detail shot: hands"
  signal for the ~10% detail-shot quota; patterned dark-orange henna may lower landmark confidence (untested).
- Ring / garland / holding hands: hands of the bride and groom (assigned by nearest pose wrist) close
  together between them. Combine with pose rather than run hands on every photo.
- The app already runs MediaPipe Tasks; Hand Landmarker uses the same runtime, so it adds files, not a new
  library or licence.

### Gaps
- No Helio G99 timing for Hand Landmarker; no data on henna-covered hands.
- Southern Asia row of the hand card's fairness table not extracted in this session.
