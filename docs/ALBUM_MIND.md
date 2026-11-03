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
