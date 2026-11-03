# State of the art in automatic photo album curation and key photo selection (for an on-device "seeing and reasoning" brain)

Research note, 3 Nov 2026. Method: web search only. Direct fetches of arxiv.org and openaccess.thecvf.com failed from this cloud session (host not allowed), so paper details below come from search-result summaries of the primary pages (paper abstracts, official blogs, official repos). Numbers that could not be checked against the paper's own tables are flagged.

## 1. Album/event curation research: which methods pick album-worthy photos like a person, per photo (event importance, bursts, group photos, aesthetics, moments)?

### Takeaway
The core academic line is "event-specific image importance" (CUFED, Wang et al. CVPR 2016 and BMVC 2017): importance depends on the event type and on the album the photo is in, and it is learned with a pairwise/rank loss from human ratings. For bursts, the best-documented practical system (Microsoft, TIP 2019) is a tiny 0.47 MB pairwise-trained scorer that hits the user's top-1 frame 64.1% of the time and top-3 86.2%; Google Clips shows the same recipe at scale (50M pairwise comparisons, big server model distilled into MobileNet). Generic aesthetics models (NIMA, LAION, Q-Align) are useful features but are not album importance.

### Cited Findings
**Event-specific importance (CUFED / ML-CUFED)**
- Wang et al., "Event-Specific Image Importance" (CVPR 2016) introduce the concept that a photo's importance is contextual: it depends on the event type and the album it is in; they build the CUFED dataset with human annotation of relative image importance within each event album, and train a CNN with a rank loss that predicts the importance score; it "significantly outperforms various baseline methods" — [CVPR 2016 open access](https://openaccess.thecvf.com/content_cvpr_2016/html/Wang_Event-Specific_Image_Importance_CVPR_2016_paper.html); [Papers with Code](https://www.paperswithcode.com/paper/event-specific-image-importance)
- CUFED: 1,883 albums over 23 common event types, extracted from the Yahoo Flickr Creative Commons 100M (YFCC100M) dataset; the authors say it is publicly available — [CVPR 2016 PDF (search summary)](https://openaccess.thecvf.com/content_cvpr_2016/papers/Wang_Event-Specific_Image_Importance_CVPR_2016_paper.pdf); [author copy](https://xiaohuishen.github.io/assets/cvpr2016_eventcuration.pdf)
- Follow-up, "Recognizing and Curating Photo Albums via Event-Specific Image Importance" (BMVC 2017): a hybrid of (a) a siamese importance predictor, (b) a CNN event-type recogniser and (c) an LSTM album-level recogniser, so event type and importance are learned jointly; releases ML-CUFED (CUFED with multi-label event types) — [BMVC 2017 page](https://bmva-archive.org.uk/bmvc/2017/papers/paper094/index.html); [arXiv 1707.05911](https://arxiv.org/pdf/1707.05911); [UCSD camera-ready](https://cseweb.ucsd.edu/~gary/pubs/BMVC-2017-camera-ready.pdf)
- PETA (2021) uses a transformer over all album images for event recognition: 90.09% mAP on ML-CUFED, +7% over previous state of the art; its attention is checked against the human importance labels as an importance predictor; the authors note some earlier ML-CUFED results used importance ground truth during training and PETA did not, so comparisons are not like-for-like — [PETA arXiv 2109.12499](https://arxiv.org/html/2109.12499.pdf); [ar5iv](https://ar5iv.labs.arxiv.org/html/2109.12499)
- A 2026 multitask attention/transformer paper (event recognition + importance prediction) reports 67.21% accuracy on ML-CUFED event recognition (a different metric from PETA's mAP) — [VNUHCM J. Sci. Tech. Dev. 2026](https://std.vnuhcmjournal.com.vn/index.php/std/article/view/4361)
- A Microsoft-style patent describes a CNN that learns to rank the subset of images that are most representative of an album, given the event type — [USPTO 9940544 "Event image curation"](https://image-ppubs.uspto.gov/dirsearch-public/print/downloadPdf/9940544)

**Human selection behaviour in albums**
- Kuzovkin et al., "Image Selection in Photo Albums" (ICMR 2018, Technicolor/IRISA): a user study where people selected photos from vacation albums and events such as weddings and birthday parties; selection depends on album context — [HAL preprint PDF](https://inria.hal.science/hal-01934286v1/file/AlbumPhotoSelection_ICMR.pdf); [dblp ICMR 2018](https://dblp.uni-trier.de/db/conf/mir/icmr2018.html)
- Earlier work by the same group: "Context-aware clustering and assessment of photo collections" (2017) defines a photo's context by clustering and assesses quality relative to it; journal extension "Context in Photo Albums" (ACM Transactions on Applied Perception, 2019) — [Semantic Scholar](https://www.semanticscholar.org/paper/Context-aware-clustering-and-assessment-of-photo-Kuzovkin-Pouli/88ed734e5870470904ba6c3dcbcb5cb4a399c35e); [Tania Pouli publications](http://taniapouli.me/publications/)
- Sadeghi et al., "Learning to Select and Order Vacation Photographs" (WACV 2015): automatic album creation from unordered collections, evaluated against manually created albums on 63 personal collections — [mlanthology](https://mlanthology.org/wacv/2015/sadeghi2015wacv-learning)
- "Automatic triage for a photo series" (Chang et al., SIGGRAPH 2016) is the classic work on choosing within a series of similar shots — [Semantic Scholar](https://www.semanticscholar.org/paper/Automatic-triage-for-a-photo-series-Chang-Yu/e7d7761ba516340457488955f3dd5959761bbfd0)

**Burst / best-shot selection**
- Wang, Vesdapunt, Sinha, Zhang (Microsoft), "Real-time Burst Photo Selection Using a Light-Head Adversarial Network" (arXiv 1803.07212, IEEE TIP 2019): frames scored by a single-image scoring function trained as relative ranking; training pairs = all pairs inside each burst, labelled by Amazon Mechanical Turk, 838,038 labelled pairs; they collected their own because no public burst dataset existed; predicted best frame matched users' top-1 (of ~11 frames) in 64.1% of bursts and top-3 in 86.2% — [arXiv abs](https://arxiv.org/abs/1803.07212); [IEEE Xplore](https://ieeexplore.ieee.org/document/8920214/); [PubMed](https://pubmed.ncbi.nlm.nih.gov/31804935/)
- Same paper: model about 0.47 MB, real time on mobile, about 13 ms per frame on an iPhone 7 (backbone name not confirmed in the snippets) — [arXiv 1803.07212](https://arxiv.org/pdf/1803.07212)
- Microsoft patent: machine-learned model to select the subjectively best frame of a burst, "closely emulating human selection" — [US10671895](https://patents.google.com/patent/US10671895)
- BuIQA (Nov 2025): new burst image quality benchmark, 7,346 burst sequences, 45,827 images, 191,572 annotated quality scores across several downstream scenarios, meant to give cues for burst selection — [BuIQA 2511.07958 (summary page)](https://fugumt.com/fugumt/paper_check/2511.07958v1_enmode)
- Robot photographer paper (arXiv 1904.05688) applies burst selection to photos of people and adds diversity constraints such as only one picture per burst — [arXiv 1904.05688](https://arxiv.org/pdf/1904.05688)

**Industrial "best moments / memories" systems**
- Google Clips (Google AI blog, May 2018): raters found it easier and more consistent to choose the better of two clips than to give a score; over 50,000,000 pairwise comparisons on clips from over 1,000 videos; frame scores averaged over a segment to make a moment score; a compact MobileNet "image content model" trained to mimic a large server model — [Google Research blog](https://research.google/blog/automatic-photography-with-google-clips/); [ai.googleblog.com](https://ai.googleblog.com/2018/05/automatic-photography-with-google-clips.html)
- Google hired a documentary photographer, a photojournalist and a fine-arts photographer, and many training concepts were about what NOT to capture (inside a purse, finger over lens, shake, blur) — [PetaPixel](https://petapixel.com/2018/01/27/googles-clips-ai-camera-trained-photography-pro-photogs/); [Digital Trends](https://www.digitaltrends.com/photography/google-clis-inside-look-at-the-ai/)
- Related Google paper: "Rapid Probabilistic Interest Learning from Domain-Specific Pairwise Image Comparisons" — [arXiv 1706.05850](https://arxiv.org/pdf/1706.05850)
- Apple Photos: key photo for a Memory is influenced by the popularity of "iconic scenes" learned across iOS users with differential privacy; scene classification runs on the device; used since iOS 16 for Memories key photos, also for Places key photos in iOS 17 — [Apple ML Research, Learning Iconic Scenes with DP](https://machinelearning.apple.com/research/scenes-differential-privacy)

**Aesthetics models (features, not album importance)**
- LAION aesthetics predictor: a simple linear model on CLIP ViT-L/14 image embeddings gave the most appealing rankings; V2 trained on SAC, LAION-Logos and AVA — [LAION blog](https://laion.ai/blog/laion-aesthetics/)
- A 2026 audit argues the LAION-Aesthetics predictor encodes a narrow "algorithmic gaze" (biases in what it rates as beautiful) — [arXiv 2601.09896](https://arxiv.org/html/2601.09896v4)
- Q-Align (ICML 2024), all-in-one VLM scorer for quality/aesthetics; an aggregator page lists AVA SRCC 0.819 / PLCC 0.823 (secondary source, not checked against the paper) — [Q-Align GitHub](https://github.com/q-future/q-align); [Emergent Mind summary](https://www.emergentmind.com/topics/laion-aesthetic-scoring)
- NIMA (VGG16 backbone) SRCC 0.592 / LCC 0.610 on AVA as reported by a later paper — [arXiv 1812.07989](https://arxiv.org/pdf/1812.07989)
- CLIP-based semi-supervised distillation for aesthetics: fine-tuned CLIP teacher distilled into a light student using labelled and unlabelled data; distilled teacher and student both beat supervised counterparts; lists photo album management as an application (ICME 2025) — [arXiv 2307.15640](https://arxiv.org/html/2307.15640v2)

### Inferences
- Album Done's current design (rules over face/photo measurements, pairwise-tested) already matches the shape of these systems; what the literature adds is (a) importance is relative to the event and to the rest of the folder, so scores should be normalised per event, which CLAUDE.md already does for stage lighting, and (b) the strongest practical training signal is pairwise "which is better" labels, not absolute scores.
- The Microsoft burst result (0.47 MB, 64% top-1 / 86% top-3) sets a realistic bar for a tiny on-phone burst picker: even a good model disagrees with the user's single favourite about a third of the time, so "Swap" in the viewer stays essential.
- CUFED is Western Flickr albums; it may include weddings and birthdays among its 23 types (not verified here). It is useful for pre-training a ranking head, but it will not know Indian ceremony context.

### Gaps
- Exact CUFED/BMVC numbers (MAP@k, P@t%) and the annotation protocol (number of raters, rating scale) could not be read: the CVF and arXiv PDFs could not be fetched from this session.
- CUFED licence: photos come from YFCC100M, where each photo keeps its own Creative Commons licence (some are NonCommercial). I found no statement of a separate licence for the CUFED importance labels. Commercial use needs checking per photo.
- Whether the Microsoft burst dataset (838k pairs) was ever released: not found.
- No dedicated, published model for "group photo: everyone's eyes open / looking" beyond burst and robot-photographer work was found; group scoring by the worst face (as Album Done does) appears to be engineering practice rather than a benchmarked method.
- No source found on Google Photos' current "best photos"/Memories selection internals (beyond Clips), or on emotion/candid-moment detection with reported album numbers.

## 2. Set-level selection: models that look at the whole set (transformers, DPPs, submodular, learning to rank, pairwise preferences), near-duplicates and story coverage

### Takeaway
Most published album summarisers combine a per-photo quality/importance score with a diversity term (DPP or submodular coverage) or a sequence model (RNN/transformer over the album). Reported agreement with human summaries is modest (around 45% precision for the best EMNLP 2017 model; a DPP baseline is almost as good at about 44%), which shows the task is subjective and that a well-tuned diversity-aware greedy selection is close to learned set models.

### Cited Findings
- Yu, Bansal, Berg, "Hierarchically-Attentive RNN for Album Summarization and Storytelling" (EMNLP 2017): picks representative photos then writes a story; against human-selected summaries the best variant gets about 45.5% precision and 28.8% recall; a DPP baseline gets 43.75% precision and 27.41% recall; authors say visual summaries are hard to evaluate and picks may not agree with human selections — [arXiv 1708.02977](https://arxiv.org/pdf/1708.02977); [ACL Anthology D17-1101](https://aclanthology.org/D17-1101/)
- "Learning Visual Storylines with Skipping Recurrent Neural Networks" (2016): album summarisation is "inherently a subjective and difficult task"; human summaries were preferred only 59.5% of the time over the strongest baseline — [arXiv 1604.04279](https://arxiv.org/pdf/1604.04279)
- IPTA 2019 (Hacettepe) neural summarisation of personal albums: goal is a small set that balances quality and diversity; evaluated by comparing against summaries made by human subjects — [Hacettepe publication page](https://web.cs.hacettepe.edu.tr/~erkut/publications/neural_summarization_ipta2019)
- Convolutional Set Transformer (arXiv 2509.22889, 2025): treats each personal album as a set and classifies each photo using the other photos (set-input encoder), learning contextual relations between album images (target: event labelling, not picking) — [arXiv 2509.22889](https://arxiv.org/pdf/2509.22889)
- PETA: transformer attention over the album's photos as an importance signal (see section 1) — [arXiv 2109.12499](https://ar5iv.labs.arxiv.org/html/2109.12499)
- DPPs: probability of a subset S is det(L_S)/det(I+L) for a PSD similarity kernel L, which makes near-duplicates unlikely to be chosen together; log det(L_S) is submodular, exact MAP is NP-hard, so greedy approximations are used — [Emergent Mind DPP overview](https://www.emergentmind.com/topics/determinantal-point-processes-dpps); [Kulesza & Taskar, Learning DPPs, arXiv 1202.3738](https://arxiv.org/pdf/1202.3738); [Online MAP inference of DPPs, NeurIPS 2020](https://proceedings.neurips.cc/paper/2020/file/23378a2d0a25c6ade2c1da1c06c5213f-Paper.pdf)
- Probabilistic submodular diversity models (family including DPPs) "encourage diversity and coverage" and were tested on image-collection summarisation — [Tschiatschek et al., AISTATS 2016 supplement](http://proceedings.mlr.press/v51/tschiatschek16-supp.pdf)
- Deep Submodular Networks learn the submodular summary objective from data — [arXiv 2010.08593](https://arxiv.org/pdf/2010.08593)
- Streaming non-monotone submodular maximisation for personalised video summarisation on the fly — [arXiv 1706.03583](https://arxiv.org/pdf/1706.03583)
- BMVC 2017 uses an LSTM over the album to recognise the event, combining set context with per-photo importance — [BMVC 2017](https://bmva-archive.org.uk/bmvc/2017/papers/paper094/index.html)

### Inferences
- For Album Done, a greedy "quality x importance minus similarity to already-picked photos" selection (a DPP/submodular-style objective) with quotas per event/moment/person is the evidence-backed design; a learned set transformer is unlikely to beat it by much given human agreement ceilings near 45-60%.
- Near-duplicates: the DPP kernel can be built directly from embedding similarity (SFace for people, a CLIP-type image embedding for scenes); burst collapse then becomes a by-product of the diversity term.
- Story coverage is not well benchmarked; the literature mostly uses time order plus diversity. Album Done's "dense bursts of shooting = big moments" heuristic has no direct published counterpart found here.

### Gaps
- Content of "Diverse neural photo album summarization" and "Extractive summarization of personal photos from life events" (seen only as reference titles) could not be retrieved.
- No published learning-to-rank-over-sets result specifically trained on wedding albums was found.

## 3. Learning from few labelled albums: data needs, VLM teacher (Claude/GPT-4V style) labelling with reasons, distillation into a small student, pairwise distillation

### Takeaway
The proven industrial recipe is: collect many pairwise preferences (humans, or a big model), train a big teacher, distil into a small mobile model (Google Clips: 50M pairs, server model mimicked by MobileNet). Recent papers (2025-2026) replace human raters with a VLM's pairwise judgements, turn them into calibrated scores (Elo), and distil into a single-pass scorer; reported gains are real but measured on other domains (paintings, product images), not personal albums. No published number for "how many labelled albums are enough" was found.

### Cited Findings
- Google Clips: pairwise labels more consistent than direct scores; >50M pairwise comparisons; compact MobileNet trained to mimic the server-based model's predictions — [Google Research blog](https://research.google/blog/automatic-photography-with-google-clips/)
- Microsoft burst selector: 838,038 human-labelled pairs from all pairs in each burst; tiny model (0.47 MB) learned from them — [arXiv 1803.07212](https://arxiv.org/abs/1803.07212)
- PSDistill, "Preferences Order, Ratings Anchor" (arXiv 2605.19776, May 2026): a VLM (Qwen3-VL-8B) makes pairwise aesthetic judgements; a small Elo reference pool turns them into calibrated pseudo-scores; the VLM is then trained (confidence-weighted GRPO) into a single-pass 5-dimension scorer; mean SRCC 0.709 on the PPaint benchmark (40.7% relative over base), beating ArtiMuse 0.686 and Qwen3-VL-32B 0.663, and the training-free pairwise Elo reference 0.638; pseudo-label ablation: calibrated Elo 0.709 vs majority vote 0.628 vs plain pairwise distillation 0.557; about 146x cheaper than exhaustive pairwise comparison — [arXiv 2605.19776](https://arxiv.org/html/2605.19776v1); [Pith summary](https://pith.science/paper/2605.19776)
  - Caveat: the student here is still a VLM, not a phone-size model; a third-party summary questions whether the Elo pool was fitted on data later used in training (not verified) — [Pith](https://pith.science/paper/2605.19776)
- "Embedding the Teacher: Distilling vLLM Preferences for Scalable Image Retrieval" (arXiv 2510.12014, Oct 2025): distils a vision-language model's pairwise image preferences into a lightweight embedding student for picking the best image from a catalogue (product/persona retrieval, not albums) — [arXiv 2510.12014](https://arxiv.org/html/2510.12014v1); [review](https://www.themoonlight.io/review/embedding-the-teacher-distilling-vllm-preferences-for-scalable-image-retrieval)
- CLIP teacher to lightweight student for aesthetics with labelled + unlabelled images; both distilled models beat supervised counterparts (ICME 2025) — [arXiv 2307.15640](https://arxiv.org/html/2307.15640v2)
- VILA learns aesthetics from user comments (text as weak labels) and reaches 0.657 SRCC on AVA zero-shot — [VILA arXiv 2303.14302](https://arxiv.org/pdf/2303.14302)

### Inferences
- For Album Done (task #28 "label decisions and train a small on-phone scorer"), the literature supports: Claude as teacher makes pairwise "which of these two is better for the album, and why" judgements within the same moment/burst; convert to scores with Elo/Bradley-Terry (calibrated Elo beat majority vote and plain pairwise distillation in PSDistill); train a small head on the phone's existing features. Reasons from the teacher can become auxiliary labels (blink, talking, hidden face) that the rule engine already measures.
- Pairs inside the same burst/moment are the most informative (both Microsoft and Google used within-burst or within-video pairs), because they hold content constant and isolate "which frame".
- Ceiling expectation: even human-trained small models reach ~64% top-1 on bursts; teacher-distilled models should be judged against the real couple's album (AlbumScore) rather than against the teacher.

### Gaps
- No paper found that uses GPT-4V/Claude-class models to label personal or wedding albums and reports album-level agreement with a real couple's album.
- No published learning curve ("N albums give X precision") for album curation was found; data-size needs remain unknown.
- No reported result found for distilling a VLM into a sub-50 MB phone model specifically for photo selection.

## 4. Pretrained image embeddings (CLIP/SigLIP/MobileCLIP) plus a small head for importance and scene/moment understanding; zero-shot prompts

### Takeaway
Frozen CLIP embeddings with just a linear head are a strong aesthetics feature (AVA SRCC 0.683 vs 0.574 for an ImageNet ViT; LAION's predictor is also a linear head on CLIP). MobileCLIP gives CLIP-class embeddings on a phone in a few milliseconds (S0: 11.4M-parameter image encoder, 1.5 ms, 67.8% ImageNet zero-shot). This makes "small phone embedding + tiny trained heads + zero-shot concept prompts" the most transferable design, but no published CLIP result on CUFED/ML-CUFED album importance was found.

### Cited Findings
- "CLIP knows image aesthetics" (Frontiers in AI, Nov 2022): linear probe on CLIP image features reaches Spearman 0.683 / Pearson 0.694 on AVA, vs 0.574 Spearman for a linear probe on an ImageNet-21k ViT; CLIP as a static feature extractor suits aesthetics better than classification features; also compares prompting and fine-tuning — [Frontiers article](https://www.frontiersin.org/journals/artificial-intelligence/articles/10.3389/frai.2022.976235/full); [PDF](https://d-nb.info/1287590918/34)
- LAION aesthetics predictor = linear model on CLIP ViT-L/14 embeddings — [LAION blog](https://laion.ai/blog/laion-aesthetics/)
- "Exploring CLIP for Assessing the Look and Feel of Images" (CLIP-IQA, zero-shot prompts like "good photo"/"bad photo") — [arXiv 2207.12396](https://arxiv.org/pdf/2207.12396)
- LP++: a strong linear probe for few-shot CLIP classification (CVPR 2024), relevant for few-label scene/moment heads — [CVPR 2024 PDF](https://openaccess.thecvf.com/content/CVPR2024/papers/Huang_LP_A_Surprisingly_Strong_Linear_Probe_for_Few-Shot_CLIP_CVPR_2024_paper.pdf)
- MobileCLIP (Apple, CVPR 2024): latency measured on iPhone 12 Pro Max; MobileCLIP-S0 11.4M (image) + 42.4M (text) params, 1.5 + 1.6 ms, 67.8% ImageNet zero-shot; S1 21.5M + 63.4M, 2.5 + 3.3 ms, 72.6%; S2 35.7M + 63.4M, 3.6 + 3.3 ms, 74.4%; B 86.3M + 63.4M, 10.4 + 3.3 ms, 76.8%; S2 claimed 2.3x faster and more accurate than ViT-B/16 CLIP — [CVPR 2024 paper](https://openaccess.thecvf.com/content/CVPR2024/html/Vasu_MobileCLIP_Fast_Image-Text_Models_through_Multi-Modal_Reinforced_Training_CVPR_2024_paper.html); [apple/ml-mobileclip GitHub](https://github.com/apple/ml-mobileclip); [HF Core ML README](https://huggingface.co/apple/coreml-mobileclip/blob/main/README.md?code=true)
- "On the rankability of visual embeddings" (2025) studies whether embedding spaces contain directions that rank images by an attribute — [arXiv 2507.03683](https://arxiv.org/pdf/2507.03683)
- "Aligning Vision Models with Human Aesthetics in Retrieval" (2024) benchmarks and aligns vision models to human aesthetic preference — [arXiv 2406.09397](https://arxiv.org/html/2406.09397v1)

### Inferences
- A MobileCLIP-S0/S1-class image encoder (run at ~256 px through ONNX Runtime, which Album Done already ships) could give one embedding per photo for: near-duplicate grouping, detail-shot detection (jewellery, mehendi hands, decor, food) by zero-shot prompts, candid vs posed, stage vs crowd, and a learned "album-worthiness" head trained from Claude's pairwise picks. Text-encoder prompts can be pre-computed off the phone, so only the image encoder ships.
- Licence must be checked before bundling: CLAUDE.md requires Apache 2.0 or similar; MobileCLIP's code/weights licence was not verified in this research.
- Event-relative normalisation (z-score per event) of embedding-head outputs follows the CUFED finding that importance is event-specific.

### Gaps
- No published CLIP/SigLIP/MobileCLIP number on CUFED/ML-CUFED event recognition or album importance was found.
- SigLIP small/on-device numbers and licences were not retrieved.
- MobileCLIP's Android (non-iPhone) latency and the weights' licence were not confirmed.

## 5. Evaluation metrics used (precision@k vs human albums, moment/event coverage, human preference tests)

### Takeaway
Published work evaluates mainly by (a) overlap with human-picked summaries (precision/recall, or ranking metrics such as top-k hit rates and MAP within an album), (b) rank correlation (SRCC/PLCC) for scores, and (c) pairwise human preference studies. All sources stress subjectivity; human-vs-best-baseline preference can be as low as 59.5%, so absolute numbers should be read against human agreement.

### Cited Findings
- Precision/recall against human-selected summaries (45.5% P / 28.8% R best; DPP 43.75% / 27.41%) plus pairwise human preference for stories (humans preferred over the model 69.5%) — [arXiv 1708.02977](https://arxiv.org/pdf/1708.02977)
- Human preference test: human summaries preferred only 59.5% over the strongest baseline — [arXiv 1604.04279](https://arxiv.org/pdf/1604.04279)
- Top-1 / top-3 hit rate against user's choice within a burst (64.1% / 86.2%) — [arXiv 1803.07212](https://arxiv.org/abs/1803.07212)
- Comparing automatic summaries against summaries produced by several human subjects is the standard protocol — [IPTA 2019](https://web.cs.hacettepe.edu.tr/~erkut/publications/neural_summarization_ipta2019)
- Album creation evaluated against manually created albums (63 collections) — [Sadeghi et al. WACV 2015](https://mlanthology.org/wacv/2015/sadeghi2015wacv-learning)
- ML-CUFED event recognition reported as mAP (PETA 90.09%) or top-1 accuracy counting any ground-truth label as correct (BMVC 2017) — [PETA](https://arxiv.org/html/2109.12499.pdf); [BMVC 2017 arXiv](https://arxiv.org/pdf/1707.05911)
- Aesthetic scorers reported as SRCC/PLCC on AVA (CLIP linear probe 0.683 SRCC; NIMA-VGG16 0.592; Q-Align ~0.819 per secondary source) — [Frontiers](https://www.frontiersin.org/journals/artificial-intelligence/articles/10.3389/frai.2022.976235/full); [arXiv 1812.07989](https://arxiv.org/pdf/1812.07989); [Emergent Mind](https://www.emergentmind.com/topics/laion-aesthetic-scoring)

### Inferences
- Album Done's `AlbumScore.kt` (same photo, same moment, wrong moments, event balance, people) is richer than published metrics: "same moment" credit is the right way to handle subjectivity, since exact-photo precision is capped near 45% even for good models. Reporting exact-photo precision@k alongside moment-level recall would make results comparable to the literature.
- A blind pairwise preference test (couple chooses between the app's page and an alternative) is the accepted final check, and matches the owner's PDF-after-blind-picks process.

### Gaps
- No standard "moment coverage" or "people coverage" metric for event albums was found in the literature; these appear to be Album Done's own.
- Inter-annotator agreement numbers for CUFED (needed to set a human ceiling) were not retrieved.
