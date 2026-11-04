# Album Mind: our own reasoning for human album selection

Owner's direction (3 Nov 2026): the brain must choose consciously, like a person, for any religion
and any event, and in the end do better than Claude. Free models are only the senses; the reasoning
is ours, built and trained by us, on the phone, no internet.

## 1. Senses (borrowed, free, on the phone)

They say what is in a photo; they decide nothing.

| Sense | Model | Says |
|---|---|---|
| Faces | YuNet, MediaPipe Face Landmarker, SFace | who, eyes, smile, mouth, gaze, head angle |
| Meaning | SigLIP 2 B/32 (Apache) | what kind of scene, objects, mood, near-copies |
| Body | MediaPipe Pose, Hands (Apache) | blessing, touching feet, hugging, garlanding, hands |
| Words (short list only) | a small vision-language model (Gemma 4 E2B, Qwen3-VL, InternVL: being tested) | what is happening, in words |

## 2. The Album Mind (ours)

### 2a. Understanding the day: the story graph

The whole folder becomes one picture of the day, the way a person holds it in mind:

- **Scenes**: photos of the same people in the same setting (time, look and people change together).
  A scene is the unit the album is made of, not a photo.
- **People**: who each person is to the couple (the couple, parents, siblings, grandparents, close
  family, friends, guests), from when and with whom they appear across the day.
- **What each scene is**: from the senses: ritual peak, reveal, blessing, family group, candid
  laughter, tears, getting ready, details, the stage, the crowd.
- **The day's shape**: the order of scenes, the peaks, what happens only once.

### 2b. Reasoning: deciding like a person

For each scene, our own small model weighs it against the whole day, not alone:

1. **Does the story need it?** Is it a peak, a once-only moment, a turning point?
2. **Who is in it, and are they covered yet?** Grandmother with the couple matters more the fewer
   photos of her there are.
3. **Is it already told?** A scene like one already chosen adds little.
4. **How many photos does it deserve?** One, or a wide and a close one, or several (a long ritual).
5. **Which frame?** The decisive instant: the important faces at their best.
6. **Check the whole**: every important person with the couple, both families, the day's flow, no
   near-copies, a mix of close and wide, candid and posed, people and details.
7. **Say why**: every photo keeps its reason in plain words.

### 2c. Our own model (trained by us)

A small "album reasoner" network of our own design: it reads every scene of the day together (each
scene as a row of numbers: what the senses saw, who is in it, when, how unique) and lets every scene
look at every other one (attention), so a scene's value depends on the rest of the day, as for a
person. It outputs, per scene, how much the album needs it and how many photos; the frame choice
and the final check follow. Size: a few MB; time: milliseconds on the phone. It is trained only on
our data, so it is ours:

- picks made by eye on many photo sets (Claude and helpers, a jury of free thinking models later),
  with reasons;
- "this frame, not that one" choices within scenes;
- real couples' albums (wedding-1 is kept as the exam, never trained on);
- later, each bride's own swaps and removals, on her phone, for her album only.

Until there is enough data for the network, the same steps run as explicit reasoning (2b), and the
network takes over each step as it beats it on the honest test.

## 3. How we know it works

- The honest yardstick: random photos already catch many of a couple's moments (weddings are shot in
  bursts), so every score is shown beside random's, as the share of the gap from random to perfect
  that a selection closes. Claude's blind picks: 70% (wedding-1, 766 photos). Today's app: 48%.
- Designed and tuned on part of the data, scored on parts it never saw (held-back sets, wedding-1's
  wedding day), by community.
- The goal: first Claude's level, then beyond it.

## 4. Progress log

**3 Nov 2026**
- Senses: SigLIP 2 B/32 int8 (96 MB) embeds a photo in 0.02-0.05 s here; free thinking models
  (Gemma 4, Qwen 3.5, MiniCPM-V 4.6/4.7, WeVisOne-VL, InternVL 3.5; teachers Qwen 3.5 9B, MiniCPM-V
  4.5, Qwen3-VL 8B) under test on the real couple's photos (`thinker-test.yml`).
- Album Mind v0 (`tools/reasoner/mind.py`, JAX): trains; on held-back sets 35-40% same photo,
  65-70% same moment (old brain 33%, 81%); learns the 41 practice sets by heart. Too few examples.
- Frame judge (`frame_judge.py`): of a kept photo and its unkept near-twin, which was kept? 50% on
  held-back pairs (coin flip; the quality number alone 54%). The picks were made from 300 px contact
  sheets, where eyes and expressions cannot be seen: frame choices must be taught at full size.
- Scene judge (`scene_judge.py`): which moments a person uses at all: 86.8% of them found on held-back
  sets, vs 83.3% biggest-first and 81.8% random (small sets use most moments; little room).
- Data: free, commercially usable Indian wedding albums online are scarce (licences, not effort):
  YFCC 3.3M listings gave 359 candidate photos (many false hits); Wikimedia's wedding category tree
  about 600 more; the labelled sets about 900. The big lasting source is the brides' own swaps on
  their phones (owner's "option 1", on hold).
- Next: the jury judges at full size (frame against frame, scene by scene) on the build machine's
  cached 1280 px wedding photos; Album Mind trains on that; exam on wedding-1 by the honest yardstick.

**4 Nov 2026, 2:45 am IST: the free thinking models, tested on the real couple's photos**
(`thinker-test.yml` round 4, 512 px, 4-core build machine; answers locked on real-data)

| Model | Size | s/photo | Seen against the photos |
|---|---|---|---|
| MiniCPM-V 4.6 | 1.3B | 12.6 | basic, vague ("a traditional ceremony") |
| MiniCPM-V 4.7 | 1B | 21 | describes, then reasons badly ("skip to keep it authentic") |
| Qwen 3.5 | 2B | 49 | invents ritual names (an engagement blessing called "saptapadi", a woman called "the groom"); skips solo bride portraits |
| InternVL 3.5 | 2B | 109 | most accurate of the small ones (notices background people), ritual still guessed |
| WeVisOne-VL | 2B | 105 | answers in box/grounding codes, no use here |
| Gemma 4 E2B | | 178 | empty answers in this llama.cpp build |
| Qwen 3.5 (teacher) | 9B | 138 | clearer ("pre-wedding shoot", "four women, likely family") |
| Qwen3-VL (teacher) | 8B | 610 | clearest, slowest |

Decision: no free thinking model is good enough to be the brain or the teacher; small ones are too
weak and too slow for hundreds of photos on a phone (perhaps a handful, such as the cover). Album
Mind stays: fast senses on every photo, our own learned judges and reasoning network, taught by
Claude at full size (frame against frame, scene by scene).

**4 Nov 2026, morning: frames taught at full size**
- `teach-sheets.yml` laid 302 near-twin groups side by side at 1280 px (teach-sheets branch); six
  helpers ranked them as a family would, with reasons (`tools/reasoner/frame-labels.tsv`: 56 sure,
  232 close, 14 equal). Most groups are nearby moments rather than true bursts, hence "close".
- The old 300 px picks agree with the full-size rankings on only 67% of pairs: the earlier teaching
  was noisy, as suspected.
- Frame judge learned on the full-size rankings (`frame_judge2.py`): 57-59% on held-back pairs (68%
  on the sure ones), up from 50% (thumbnail teaching) and 55% (the app's quality number). A real
  but small step: what SigLIP and the measurements see of a frame is not yet the decisive instant.

**4 Nov 2026, Album Mind v1 on the exam** (wedding-1, share of the gap from random to perfect closed)

| | 233 photos (the album's size) | 400 photos |
|---|---|---|
| Old brain | 38.2% of the couple's moments (5%) | 68.2% (30%) |
| v1 alone (`mind_v1.py`: scene judge + frame judge + reasoning) | 43.8% (14%) | 65.2% (24%) |
| Scene judge driving the old brain, every-moment-first off | 43.3% (13%) | 68.2% (30%) |
| Random | 34.8% | 54.4% |

Better at the album's size, equal at 400: a step, not yet a clear win. Still to check on the
held-back sets (scene judge learned on practice sets only) before anything goes into the app.

**Scale-aware moments** ("every moment first only if the event has a place for each"): wedding-1 at
233 photos 38.2% -> 42.1% of the couple's moments (with the scene judge 42.9%; at 400 69.1%), held-back
sets unchanged at 81%, but practice sets 88% -> 85%. Lesson: the public sets are curated uploads (a
person uses almost every moment), not raw folders; wedding-1 is the only raw folder and is the exam.
Kept as an experiment switch, off in the app, until more raw folders can confirm it.

**4 Nov 2026, 9:40 am IST: wider search for a base of our own model** (Hugging Face API, 52 labs:
Chinese (Qwen, InternVL/InternLM, MiniCPM, GLM, Kimi, Step, DeepSeek, ERNIE, Tencent, Xiaomi MiMo,
ByteDance Seed, Ant inclusionAI, SenseNova, BAAI, Kwai, Meituan...), and others (Google, Microsoft,
NVIDIA, IBM, Cohere, Liquid, AllenAI, HF, Mistral, Sarvam, Krutrim, Kakao, Naver, LG...). 665 vision
models; 148 at or under 5.5B.)

- New kind found: **decision models**. They answer typed questions (yes/no, choose one of A-D, a score)
  in one forward pass and write no text. Writing text was what made the thinking models too slow
  (round 4: 12-600 s a photo), so this is the shape a phone can afford.
  - Intern-Decision 0.8B / 2B / 4B (Shanghai AI Lab, Apache, on Qwen 3.5): up to 8 photos and 16
    questions per pass.
  - OneJev 1.6 0.8B (Apache, on Qwen 3.5): highest-scoring model up to 2.5B on the Decision Index.
  - H2O-Lightning-4B (Apache, on Qwen 3.5).
  - Liquid d1-2B / d1-3B: LFM licence, free only under $10M revenue, so not our first choice.
- A photo-quality judge of the same shape: Q-ReAlign Mini 0.8B (Apache, Qwen 3.5): quality and beauty
  from the probabilities of "excellent ... bad", 0.879 agreement with people on seven quality sets.
- Other new small ones: Cohere North Micro Vision 2.4B (Apache, Hindi among its languages), ZwZ 2B/4B
  (Ant, Apache, fine detail in one pass), MiniCPM-V 4.6 Thinking, Gemma 4 E2B mobile build, LFM3-VL-2B
  (LFM licence).
- Not usable: Gemma 3 terms, Llama, FAIR non-commercial (Perception-LM), NVIDIA and Apple research
  licences, Krutrim and Kakao licences, Youtu licences.

Plan: our own model = one shared Qwen 3.5 0.8B decision backbone (Apache), fine-tuned by us on the
album's own questions (keep this scene? how many photos? which frame? is this a peak? who matters
here?), asked in one pass per scene, on the phone, for the few hundred candidates the fast senses
leave. First, the ready-made ones are tested untrained on the full-size frame choices
(`judge-test.yml`, `tools/reasoner/judge_test.py`).

**4 Nov 2026, 11:15 am IST: ready-made one-pass judges, untrained, on the full-size frame choices**
(`judge-test.yml`; build machine CPU, ~57 s per group of 2-4 photos, so only ~95 of 288 groups judged
in 90 minutes; Intern-Decision 2B ran out of memory). Share of ranked pairs put in the helpers' order:

| Judge | all | held back | sure |
|---|---|---|---|
| Random | 50% | 50% | 50% |
| Our frame judge (`frame_judge2.py`, trained) | | 57-59% | 68% |
| Q-ReAlign Mini 0.8B, "quality" | 68.7% | 69.6% | 79.6% |
| Intern-Decision 0.8B, "which one would the family keep?" (all photos at once) | 65.5% | 64.3% | 74.4% |
| Intern-Decision 0.8B, each photo "how good for the album?" | 66.4% | 64.3% | 76.7% |

Untrained, both beat our own trained judge: they see faces and the moment better than SigLIP plus our
measurements. Small sample: confirm on all groups with a GPU. Next: the eyes (Q-ReAlign, SigLIP, faces)
feed our brain, a Qwen 3.5 0.8B decision model fine-tuned on Kaggle's free GPU on Claude's album
judgements (frames, scenes, keep/skip, why), tested on held-back sets and then the exam.

**Goal, owner, 4 Nov 2026:** outperform Claude, then human level. Claude's picks only start the
training; the AI learns from real people's choices (people-rated eyes, real albums, the brides' swaps),
sees every photo at full size and the whole day at once, and is scored against Claude on the exam
(wedding-1: Claude 70% of the gap closed), with a separate count on the photos where Claude and the
couple disagree.

**Order of work (owner agreed, 4 Nov 2026)**, every step judged by one thing: did the exam go up?
1. The reasoning brain (the main brain): it holds the whole day, weighs each scene against the rest
   (once-only moments, who is covered yet, already told, how many photos, which frame) and says why.
   Taught on wedding-2 (Claude's blind picks with reasons, scene by scene), scored on wedding-1.
   Most of what is lost on the exam is which moments, not which frame (random already lands on 35% of
   the couple's moments at 233 photos, 10% of the exact photos).
   Ritual understanding is part of this brain (internal only, never shown): what each ritual is from
   what it sees (mandap and fire, qazi and nikahnama, Guru Granth Sahib and laavan, altar, haldi,
   sindoor, thali, varmala), its decisive instant, who must be in it, how it should look (serious at
   pheras, tears at vidaai). Small models today invent ritual names (Qwen 2B: an engagement blessing
   called saptapadi), so it is taught (ritual lessons from every community's photos, the day's order)
   and tested: name the ritual and its key instant on unseen photos with no inventing, on a community
   left out, and by the exam's key moments.
2. Speed of the one-pass judge on a real phone (A15): ~7 s a pass on a 4-core build machine, so it may
   need smaller photos or only the shortlist.
3. Birthdays, occasions, frame lessons from the web: in the background.
Honest limit: beating Claude needs real people's choices (more real albums, the brides' swaps); Claude's
picks alone can get near Claude, not past.

**4 Nov 2026, 1:35 pm IST: all 288 frame groups, Kaggle GPU (T4), untrained** (pairs in the helpers' order)

| Judge | s/group (GPU) | all | held back | sure |
|---|---|---|---|---|
| Our old frame judge (trained) | | | 57-59% | 68% |
| Q-ReAlign Mini 0.8B, quality | 1.3 | 65.5% | 64.8% | 78.1% |
| Intern-Decision 0.8B, each photo keep | 1.9 | 64.2% | 63.3% | 72.8% |
| Intern-Decision 2B, each photo keep | 4.5 | 66.3% | 67.6% | 75.0% |
| Intern-Decision 4B, all photos at once | 2.8 | 68.1% | 66.8% | 78.9% |
| Q-ReAlign + Intern 2B keep + Intern 4B group (ranks added) | | **72.2%** | **71.8%** | 82.4% |

The 95-group CPU sample was a little kind (Q-ReAlign 69.6% held back there, 64.8% on all). Several eyes
together beat any one: the brain should take several opinions, as a person weighs several things.

**4 Nov 2026, ~3 pm IST: first training of our judge** (Intern-Decision 0.8B + our own LoRA, 6.4M
trained numbers, 1,334 pair lessons from the practice groups, 3 rounds, Kaggle T4): held-back pairs
60.5% -> 63.9% (sure ones 50% -> 68%, only 22 pairs). Training loss 0.68 -> 0.29: it learns the lessons
by heart; too few lessons. Below the untrained eyes together (71.8%). Next: far more lessons (wedding-2
at real scale, the birthday/function twins), and the judge trained on top of the eyes' opinions rather
than alone.

**4 Nov 2026, evening: wedding-2 judged moment by moment** (training data, `tools/realphotos/picks/
wedding-2-moments-claude.tsv`; index of photos per moment in `wedding-2-moments-index.tsv`)
- A Maharashtrian Hindu wedding over five days, 7,697 raw photos in 10 photographer folders: groom's
  haldi, gondhal, bride's haldi and mehendi, chuda and devak, simant pujan, sangeet, vyahi bhet, the
  antarpat and mangalashtak, kanyadaan, saptapadi, kaan pili, vidaai, griha pravesh, the ring game,
  nanhora and a foam party, satyanarayan puja, reception.
- 961 moments (the app's own split), each with how much the album needs it (0 skip, 1 one photo,
  2 a peak worth two or more), its kind and a reason: 129 peaks, 432 one-photo, 400 skip.
- What a person does that the app does not: stage guests are almost all skipped (149 of 152 guest
  moments), elders and parents almost never (elders 22 of 22 kept, family 67 of 68), every ritual's
  key instant is a peak while its repeats are skipped (ritual: 39 peaks, 117 one, 61 skip), details
  only once each, many photographer's styled solos of cousins skipped.
- Next: the reasoning brain learns these on Kaggle (moment worth from what the eyes see + who is in it
  + where it sits in the day), tested on held-back parts of wedding-2 and then on the wedding-1 exam.
