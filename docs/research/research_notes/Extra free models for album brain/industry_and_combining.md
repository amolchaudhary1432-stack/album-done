# How big photo products pick best photos, and how to combine models into one human-like judgement on a phone

Research date: 3 Nov 2026. Method note: in this session WebFetch could not reach research.google or arxiv.org
(host not in the session's allowed network), so **every finding below comes from web-search result summaries,
not from reading the full pages**. URLs are the primary pages those summaries pointed to. Numbers should be
re-checked against the page before being quoted to anyone outside the team. Items I remember from training but
could not confirm are kept in Inferences or Gaps and marked "unverified".

## 1. What signals and architectures Google, Apple, Samsung (and others) use to pick best shots and story photos

### Takeaway
All the big products split the job into the same pieces. Fast per-photo attribute models (eyes open, smile,
expression, blur, lighting) feed a small learned scorer trained on human preferences, often pairwise. On top of that
sit near-duplicate grouping ("stacks" or bursts, one top pick each), a utility filter (receipts, documents,
screenshots), scene tags, and people importance (who appears often). Google trained its on-device models by
copying bigger server models (distillation) and learned from pairwise "which is better" labels. Apple adds
privately learned population priors (which scenes people like as key photos). Samsung has published almost
nothing about how it works.

### Cited Findings

**Google Top Shot (Pixel 3, 2018, older system)**
- Analyses up to 90 frames captured 1.5 s before and after the shutter press, and saves up to two alternative shots in high resolution — [Google Research blog, Top Shot on Pixel 3](https://www.research.google/blog/top-shot-on-pixel-3/) (via search summary); [9to5Google](https://9to5google.com/2018/12/20/google-ai-pixel-3-top-shot/)
- Three groups of criteria: functional qualities (lighting), objective attributes (are the subject's eyes open? are they smiling?), and subjective qualities (emotional expressions such as amusement or surprise) — [Google Research blog](https://www.research.google/blog/top-shot-on-pixel-3/) (search summary)
- Built from a vanilla MobileNet, customised to fit accuracy, latency and power limits. Early layers pick up low-level traits such as blur, and later layers spend more compute on eyes open and on expressions — [Google Research blog](https://www.research.google/blog/top-shot-on-pixel-3/); [Android Authority](https://androidauthority.com/google-pixel-3-top-shot-blog-936664) (search summary)
- The individual attribute scores train a model that predicts an overall quality score matching human raters' frame preferences. Faces are scored with a Generalized Additive Model (GAM). Exposure time, gyroscope data and optical flow are also inputs — [Android Authority](https://androidauthority.com/google-pixel-3-top-shot-blog-936664); [Google Research blog](https://www.research.google/blog/top-shot-on-pixel-3/) (search summary)
- Runs in real time and fully on the phone, for privacy and low latency — [Google Research blog](https://www.research.google/blog/top-shot-on-pixel-3/) (search summary)

**Google Clips (2018, discontinued camera, older system)**
- Raters were very consistent in pairwise comparisons and less consistent when scoring photos directly. With enough pairwise comparisons per video, a continuous quality score can be computed over the whole video. Over 50,000,000 pairwise comparisons on clips from over 1,000 videos — [Google Research blog, Automatic Photography with Google Clips](https://research.google/blog/automatic-photography-with-google-clips/) (search summary)
- Distillation: server-side recognition models first labelled a large photo set, then an on-device MobileNet "Image Content Model" (ICM) was trained to copy the server model's predictions. A final stage maps the ICM's content outputs to one quality score, trained on the pairwise data. One secondary source says this stage is a piecewise linear regression — [Google Research blog](https://research.google/blog/automatic-photography-with-google-clips/); [i-programmer](https://www.i-programmer.info/news/192-photography-a-imaging/11821-google-clips-the-death-of-the-photographer.html) (search summary)
- All computation runs on the device, within strict power and thermal limits. Clips never leave the device unless the user saves or shares them — [Google Research blog](https://research.google/blog/automatic-photography-with-google-clips/) (search summary)

**Google Best Take (Pixel 8, 2023) and Auto Best Take (Pixel 10)**
- Best Take combines several AI models. It checks timestamps to find photos taken in sequence, looks for head poses and facial expressions, picks the best faces from the other photos in the burst, and blends them into one image. It only uses faces from similar photos taken close together and never swaps one person's face for another's. It works offline — [Google blog, How Best Take makes your group photos better](https://blog.google/products/photos/how-google-photos-best-take-works/); [Google Store](https://store.google.com/magazine/pixel-best-take) (search summary)
- Auto Best Take (Pixel 10 and later, in the camera): the camera recognises which shots include the faces you capture often, then recommends better photos of those faces. This is a "people importance" signal — [Google Store, Pixel Best Take](https://store.google.com/magazine/pixel-best-take?hl=en-US) (search summary)

**Google Photos Photo Stacks (Nov 2023) and Memories (2019 onward)**
- Photo Stacks uses AI to find similar photos taken close together and group them, and picks a top pick "that best captures the moment". The user can change the top pick or remove photos from a stack. Google says about one-third of most people's galleries are similar photos. Coverage says Stacks also groups standalone look-alike photos, not just bursts — [Google blog, Nov 2023](https://blog.google/products-and-platforms/products/photos/google-photos-organization-updates-november-2023/); [TechCrunch](https://techcrunch.com/2023/11/15/google-photos-turns-to-ai-to-organize-and-categorize-your-photos-for-you); [9to5Google](https://9to5google.com/2023/11/15/google-photos-stacks/) (search summary). The model behind it has not been published.
- Memories (2019): machine learning keeps only one best shot from similar photos. A Google Photos lead said curation favours photos with good quality and smiling kids. Users can hide people and time periods. In 2021 Google added memories grouped by a shared visual element — [Gigazine, 2019](https://gigazine.net/gsc_news/en/20190913-google-photos-memories); [Engadget, 2021](https://www.engadget.com/google-photos-ai-update-2021-edit-memories-184654311.html) (search summary)

**Apple Photos (Memories key photos, Featured Photos, curation)**
- Apple's on-device scene analysis network (ANSA) is a multi-task network whose outputs feed Memories, Featured Photos and other features. Its scene tags go into the Photos knowledge graph. It ranks images by quality to choose cover photos, and filters "utility" content (receipts, documents, repair references) that does not suit Memories — [Apple ML Research, A Multi-Task Neural Architecture for On-Device Scene Analysis](https://machinelearning.apple.com/research/on-device-scene-analysis) (search summary)
- "Photos curation … utilizes among other signals, utility content filtering, image tagging and image aesthetics to pick the highest-quality images from the user's library" — [Apple ML Research, on-device scene analysis](https://machinelearning.apple.com/research/on-device-scene-analysis) (search summary)
- Memories key photos also depend on how popular "iconic scenes" are across iOS users, learned with differential privacy. Each device contributes one randomly sampled category from one past photo. This has driven key-photo selection since iOS 16 — [Apple ML Research, Learning Iconic Scenes with Differential Privacy](https://machinelearning.apple.com/research/scenes-differential-privacy) (search summary)
- The public Vision API (iOS 18 / macOS 15) shows Apple's style of fused score. `overallScore` (from -1 to 1) "incorporates aesthetic score, failure score, and utility labels". `isUtility` flags images that are "not necessarily of poor image quality, but may not have memorable or exciting content" — [Apple developer docs, VNCalculateImageAestheticsScoresRequest](https://developer.apple.com/documentation/vision/vncalculateimageaestheticsscoresrequest.md); [ImageAestheticsScoresObservation](https://developer.apple.com/documentation/vision/imageaestheticsscoresobservation.md) (search summary)
- People: Photos clusters face and upper-body feature vectors into people. Clustering runs periodically, typically overnight while charging. Upper body and clothing let it recognise people who are turned away or wearing masks (iOS 15). Apple states the goal of working equally well across skin colour, age and gender — [Apple ML Research, Recognizing People in Photos Through Private On-Device ML](https://machinelearning.apple.com/research/recognizing-people-photos); [AppleInsider](https://appleinsider.com/articles/21/07/23/photos-in-ios-15-can-identify-people-by-their-upper-bodies); [Macworld](https://www.macworld.com/article/677409/new-apple-tech-can-recognise-people-with-obscured-faces.html) (search summary)
- Apple's Photos privacy page lists the on-device analysis: scene classification, people and pets identification, photo quality analysis and audio classification — [Apple, Photos & Privacy](https://www.apple.com/legal/privacy/data/en/photos/) (search summary)

**Samsung**
- Galaxy S3 (2012, old) "Best photo": takes an automatic burst of 8 and chooses the best one — [Samsung support](https://www.samsung.com/in/support/mobile-devices/what-is-best-photo-feature-in-galaxy-s3) (search summary)
- Single Take (S20 onward): AI captures up to 14 photos and videos in one press, and Gallery shows an "AI Best Moments" pick — [Samsung support, Single Take](https://www.samsung.com/ca/support/mobile-devices/galaxy-s21-single-take) (search summary)
- Burst shots (up to 100) are grouped in Gallery with a best photo auto-selected, according to a Samsung community post (user report, not official) — [Samsung Community](https://r2.community.samsung.com/t5/Galaxy-Flip/quot-Burst-shot-quot-feature-capture-moving-objects/m-p/10677121) (search summary)

### Inferences
- The industry pattern matches Album Done's design and gives it support: per-face attribute models (eyes, smile,
  expression, blur) feed a small learned scorer, and near-duplicate groups get one top pick each. Two ideas we do
  not use fully yet: (a) **pairwise preference labels** (Clips: raters agree more on "A or B?" than on scores), and
  (b) **a small, readable learned head** (GAM for faces in Top Shot, piecewise linear in Clips). A GAM-style head
  shows how each signal adds to the score, and that fits the "why it is in the album" screen.
- Apple's split into aesthetic + failure + utility is a useful scheme for our scorer. "Failure" is technical
  flaws (blur, blink, cut-off). "Utility" is content that is fine but not album-worthy (receipts, screenshots,
  WhatsApp forwards in a wedding folder).
- People importance (Auto Best Take: "faces you capture often") is the same idea as our couple and main-people
  detection from frequency.
- Apple's iconic-scene prior is learned across the population with differential privacy, and needs a server. For an
  app with no internet, the closest equivalent is a prior built once from our own labelled sets and shipped in the APK.
- Unverified recall: the Top Shot post also says the model was trained on human ratings and uses face size and
  saliency; the Apple ANSA post gives latency figures for the network on the Neural Engine. Neither could be confirmed here.

### Gaps
- No full-text read of the Google Top Shot / Clips posts or the Apple ANSA page (network block), so exact numbers
  (model sizes, latency, accuracy) are not recorded.
- Photo Stacks top-pick model and Memories story-selection logic: Google has not published technical details (none found).
- Samsung Gallery "Best shot" on current One UI and Samsung "Stories": no official technical description found.
- Meta (Facebook Memories / "On This Day") and other vendors: not searched for lack of budget; no findings.
- WWDC session transcripts about Photos curation (e.g. Memories on iOS 18 with generative "Memory movie") were not found.

## 2. How to combine many models into one decision (cascades, ensembles, stacking, fusion, set-level selection)

### Takeaway
Cascades (cheap models on every photo, expensive ones only on the uncertain or short-listed photos) are the best
documented way to get large-model quality at small-model cost. Google found cascades beat single models at every
compute budget. Human-like choice of a whole album needs a set-level step on top of the per-photo scores. A learned
mix of submodular terms (coverage, diversity, quality) or a determinantal point process (DPP, a maths tool that
rewards both quality and variety) both beat simpler methods on human-made image or video summaries.

### Cited Findings
- Google's "Wisdom of Committees" (ICLR 2022): even simple committees of existing pre-trained models can match or
  exceed state-of-the-art accuracy at much lower cost. Two EfficientNet-B5 models match B7 accuracy with almost 50%
  fewer FLOPs (20.5B vs 37B). Cascades consistently beat single models across all compute budgets, while ensembles
  help mainly at large budgets. One reported result: a cascade 5.4x faster than B7 at equal accuracy. The authors
  say the finding holds for image classification, video classification and segmentation — [arXiv 2012.01988](https://arxiv.org/pdf/2012.01988); [Google Research blog, Model Ensembles Are Faster Than You Think](https://research.google/blog/model-ensembles-are-faster-than-you-think/) (search summary)
- Top Shot is itself a cascade/fusion design: one customised MobileNet with early layers for low-level attributes
  (blur) and more compute for eyes and expression, fused by a learned model into one score, with a GAM for faces
  — [Google Research blog](https://www.research.google/blog/top-shot-on-pixel-3/) (search summary)
- Clips is a late-fusion/stacking design: content-model outputs (distilled from server models) go into a small
  final regressor trained on pairwise preferences — [Google Research blog](https://research.google/blog/automatic-photography-with-google-clips/) (search summary)
- Apple's ANSA is a multi-task backbone: one shared network with several heads (scene tags, quality ranking,
  utility filtering) used by Memories and Featured Photos — [Apple ML Research](https://machinelearning.apple.com/research/on-device-scene-analysis) (search summary)
- Set-level selection with submodular functions: Tschiatschek, Iyer, Wei, Bilmes (NIPS 2014) treat image-collection
  summarisation as supervised learning of a weighted mix of submodular scoring functions (large-margin structured
  prediction). They introduce the V-ROUGE metric and a dataset of 14 real image collections with human summaries.
  The learned mix beats all competitors on that dataset — [NeurIPS 2014 abstract](https://papers.nips.cc/paper_files/paper/2014/hash/41321d693c015a6a92f55f29c8a76079-Abstract.html); [PDF](https://people.ece.uw.edu/bilmes/p/mypubs/sebastian2014-submod-image-sum.pdf) (search summary)
- DPPs for diverse subsets: Gong, Chao, Grauman, Sha (NIPS 2014) treat video summarisation as supervised subset
  selection learned from human summaries. Their sequential DPP respects time order, where a plain DPP treats items
  as an unordered set. A DPP keeps each item's quality score separate from its feature vector, which measures
  similarity and so diversity. Large-Margin DPP learns the kernel as a mix of base kernels — [NeurIPS 2014](https://proceedings.neurips.cc/paper/2014/hash/0eec27c419d0fe24e53c90338cdc8bc6-Abstract.html); [arXiv 1411.1537](https://arxiv.org/pdf/1411.1537) (search summary)

### Inferences
- Album Done is already a cascade: YuNet runs on every photo, the mesh model reads face crops, SFace fingerprints
  faces, and the planner does set-level selection. A practical next tier for the phone: (1) cheap signals on all
  2,500 photos (current models plus a small image embedding); (2) a short list (say the top 20-30% per moment,
  plus borderline photos) goes to a heavier model, e.g. a bigger aesthetic or vision-language image encoder;
  (3) the planner chooses the set. The Wisdom of Committees result says this should cost much less than running
  the heavy model on everything, with little loss in quality. This is inferred: the paper's numbers are ImageNet
  classification, not photo curation.
- Fusion head: a small learned head over [face signals + photo signals + embedding] fits the industry pattern
  (Top Shot GAM, Clips regressor). Train it on pairwise "A beats B within the same moment" pairs (Clips) instead of
  absolute scores, then keep the planner's rules (people first, moments, variety) as the set-level layer.
- Set level: the planner's hand-written rules (moments, variety, coverage of people, no repeated pose) are a
  hand-tuned submodular objective. The NIPS 2014 work suggests learning the weights of such a mix from real albums
  (e.g. the couple's album PDF matched against the raw folder) instead of tuning by hand. A sequential DPP (or
  greedy MMR-style diversity with a time-order constraint) matches "keep picks in time order, no repeats".
- Unverified recall: greedy selection gives a (1 - 1/e) guarantee for monotone submodular objectives, and greedy
  MAP inference is the usual fast approach for DPPs. Both are standard results but were not looked up here.

### Gaps
- No published numbers found on mobile photo-curation cascades specifically (speed vs quality); the cascade
  evidence is from ImageNet-type tasks.
- Mixture-of-experts for on-device photo scoring: not searched, no findings.
- No source found that applies DPP or submodular methods to wedding or event album selection specifically.

## 3. Distilling a big vision-language model into a small model; when the student matches or beats the teacher; how much data; improving on the device

### Takeaway
There is solid evidence that a small task-specific student can match or beat a much larger general teacher on a
narrow task. Three conditions recur: (a) rich teacher signals (rationales or soft scores, not bare labels),
(b) long, consistent training on the same inputs, and (c) noise or augmentation on the student, with some human
gold labels to anchor it. Plain pseudo-labels from a big model can hurt when they are wrong. One weak data point
says about 100 gold labels can be enough to match a zero-shot labeller on a narrow task. After release, privacy-safe
improvement means either full on-device personalisation of a small head (meta-learning or few-shot work on
personal aesthetics) or federated learning with differential privacy (Gboard, Apple). Federated learning needs a
server, so it does not fit an app with no internet permission.

### Cited Findings
- Distilling step-by-step (Google, Findings of ACL 2023): a finetuned 770M T5 beats few-shot prompted 540B PaLM on a
  benchmark using only 80% of the available data. Standard finetuning of the same T5 does not match PaLM even with
  all the data. The method trains the small model on the large model's rationales as well as its labels — [arXiv 2305.02301](https://arxiv.org/abs/2305.02301) (search summary)
- Google Clips: an on-device MobileNet was trained to copy server-based recognition models (the teacher) on a large
  photo set, then a small head was trained on human pairwise data — [Google Research blog](https://research.google/blog/automatic-photography-with-google-clips/) (search summary)
- Noisy Student (Xie, Luong, Hovy, Le, CVPR 2020): a teacher trained on labelled ImageNet pseudo-labels 300M
  unlabelled images. An equal-or-larger student is trained with noise (dropout, stochastic depth, RandAugment), and
  the student then becomes the next teacher. 88.4% top-1 on ImageNet, 2.0 points better than the earlier best
  model that needed 3.5B weakly labelled Instagram images. ImageNet-A goes from 61.0% to 83.7%. The teacher is run
  without noise when labelling. The student beats the teacher because of the noise — [CVF open access](https://openaccess.thecvf.com/content_CVPR_2020/papers/Xie_Self-Training_With_Noisy_Student_Improves_ImageNet_Classification_CVPR_2020_paper.pdf); [arXiv 1911.04252](https://export.arxiv.org/abs/1911.04252v2) (search summary)
- Beyer et al. (CVPR 2022), "A good teacher is patient and consistent": distillation works best when the teacher is
  fixed, the teacher and student see the same augmented views, and training runs long — [CVPR 2022 paper](https://openaccess.thecvf.com/content/CVPR2022/papers/Beyer_Knowledge_Distillation_A_Good_Teacher_Is_Patient_and_Consistent_CVPR_2022_paper.pdf) (search summary)
- ViT-22B as a distillation teacher: a ViT-B student reaches 88.6% on ImageNet, state of the art at that size — [arXiv 2302.05442](https://arxiv.org/pdf/2302.05442v1.pdf) (search summary)
- Narrow-domain VLM distillation where the student beats the teacher: waste classification in recycling plants.
  Large vision-language models were distilled into compact students that run faster, stay stable when prompts
  change, and are more accurate than the teacher. The size of the margin was not visible in the summary — [ScienceDirect, Resources Conservation & Recycling 2026](https://www.sciencedirect.com/science/article/pii/S0921344926001096) (search summary)
- Distilling CLIP ViT-L/14 into a small vision-only student improves the student's zero-shot and few-shot
  out-of-distribution classification (ICCV 2023) — [arXiv 2307.03135](https://arxiv.org/pdf/2307.03135); [GitHub](https://github.com/xuanlinli17/large_vlm_distillation_ood) (search summary)
- Multilingual VLM distillation (2025): students score below the teacher on retrieval and VQA, but can beat the
  teacher on tasks that need consistency (clustering, ranking) — [arXiv 2510.26271](https://arxiv.org/html/2510.26271v1) (search summary)
- Larger teachers (CLIP-ViT-L, SigLIP) leave much bigger teacher-student gaps when distilled into small models for
  VQA — [arXiv 2511.17886](https://www.arxiv.org/pdf/2511.17886) (search summary)
- Label noise and data amount:
  - Using foundation-model pseudo-labels naively can hurt when they are inaccurate. One proposed method trains small
    models directly from the zero-shot teacher's predictions — [arXiv 2502.12584](https://arxiv.org/html/2502.12584v2) (search summary)
  - 2026 medical-imaging preprint (PCAM): a gold-label-only classifier passes a BiomedCLIP labeller's ceiling (about
    0.84 AUC) at roughly 100 gold labels and is near chance at 10. Once gold labels match the labeller, extra weak
    labels mostly add contamination. Different domain, so only indicative — [arXiv 2605.24771](https://arxiv.org/pdf/2605.24771.pdf) (search summary)
  - GPT-4V pseudo-labels for touch-vision data were noisy and hard for a ViT-Small to fit, even with 4K human labels
    added — [arXiv 2402.13232](https://arxiv.org/pdf/2402.13232) (search summary)
  - Prompt tuning of VLMs is fairly robust to noisy labels — [arXiv 2307.11978](https://arxiv.org/pdf/2307.11978) (search summary)
  - Noisy labels from multimodal LLMs can be cleaned using the labels of similar images (NeaR) when fine-tuning
    CLIP — [arXiv 2505.01064](https://arxiv.org/html/2505.01064v1) (search summary)
- On-device personalisation:
  - Personalised image aesthetics (PIAA): generic aesthetic models disagree with individual users even on the same
    image. Meta-learning treats each user as a few-shot task and adapts from a small number of that user's ratings
    (BLG-PIAA, IEEE TCYB 2020) — [PubMed 32525805](https://www.pubmed.gov/32525805/); [ICIP 2019 meta-learning PIAA](https://unpaywall.org/10.1109%2FICIP.2019.8803119) (search summary)
  - Yun & Choo (KAIST, 2024): meta-learning needs many training users, which PIAA rarely has. They build
    personalised models from general aesthetics and quality datasets with task vectors; merging task vectors avoids
    overfitting with few samples, and general models already show some personalisation ability — [arXiv 2407.07176](https://arxiv.org/html/2407.07176v1) (search summary)
  - Apple (2021): a federated task-processing system for evaluating and tuning on-device ML, mainly for
    personalisation (news, speech recognition), with on-device data retention policies. Federated learning of deep
    networks was added later. No Photos use case was named in the summaries — [arXiv 2102.08503](https://arxiv.org/pdf/2102.08503); [Apple ML Research](https://machinelearning.apple.com/research/federated-personalization) (search summary)
  - Google Gboard: 30+ on-device language models in 7+ languages and 15+ countries trained with federated learning
    and user-level differential privacy (DP-FTRL + secure aggregation; δ = 10^-10, ε from 0.994 to 13.69). All future
    Gboard LMs trained on user data must have DP guarantees. Pre-training on public data improved both privacy and
    utility — [Google Research blog, Feb 2024](https://research.google/blog/advances-in-private-training-for-production-on-device-language-models/); [arXiv 2305.18465](https://arxiv.org/pdf/2305.18465) (search summary)
  - Apple learns population priors (iconic scenes for Memories key photos) with local differential privacy, one
    sampled category per device — [Apple ML Research](https://machinelearning.apple.com/research/scenes-differential-privacy) (search summary)

### Inferences
- For Album Done, the published recipe that fits best: Claude (or a big VLM) acts as the teacher during development
  and gives graded pairwise judgements with short reasons for each moment ("A over B: bride's eyes closed in B").
  A small head is then trained on those judgements over the phone's own features (face signals + a small image
  embedding). Distilling step-by-step and Clips both suggest that rich teacher signals plus a little human gold
  data (the couple's real albums) beat bare labels. The data point of ~100 gold labels to match a zero-shot labeller
  is weak and from another domain, but it suggests the real-album matches (`AlbumScore.kt`) are worth more per
  example than many extra teacher labels. Keep teacher labels and gold labels separate and weight them, because
  pure pseudo-labels can hurt.
- Why a student can beat its teacher here: the student sees signals the teacher does not (time order, burst
  neighbours, frequency of people across the whole folder, same-moment comparisons). It is trained on the exact
  narrow task, and noise or augmentation plus consistent teacher outputs (Beyer; Noisy Student) let it smooth out
  the teacher's mistakes.
- Personalisation with no internet: federated learning (Gboard/Apple) needs a server, so it does not fit the
  no-internet promise. What fits is a purely local update. Each swap or Remove in the viewer is a pairwise preference
  ("chosen over the photo it replaced"). Those pairs can update a tiny per-album head, or a few weights in the
  fusion head, on the phone, with a cap on how far it moves from the shipped weights (in the spirit of the
  task-vector and few-shot PIAA work). It improves the next re-made album for that couple only. Nothing is uploaded.
  Learning across users would need opt-in export, which conflicts with the product promise, so it is not suggested.
- Unverified recall (not confirmed in this session): "Born-Again Networks" (Furlanello et al. 2018) showed students
  of the same size beating their teachers on CIFAR. Google's Top Shot was reportedly trained partly on frames rated
  by human raters. Kept here only as leads to check.

### Gaps
- No study found that distils a large VLM's photo-aesthetics or "album-worthiness" judgements into a mobile model
  with published numbers. The closest are general vision distillation and narrow industrial classification.
- No clear published rule for how many teacher-labelled vs human-labelled examples a photo-curation head needs; the
  ~100-gold-label figure is from medical imaging.
- No published case of a phone gallery app personalising its best-shot picker from user swaps fully on the device
  (Google/Apple/Samsung have not described one).
- Full papers (exact tables, ablations) could not be read because arxiv.org and research.google were blocked in this session.
