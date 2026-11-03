# The brain's experiments on the build machine (3 Nov 2026)

What an image model sees (SigLIP 2 B/32 at 256 px, int8 ONNX, Apache 2.0, 96 MB:
onnx-community/siglip2-base-patch32-256-ONNX) tried as part of the brain, scored on the real couple's
album (wedding-1) and on the labelled sets' held-back third. Paths are the scratchpad's; photos and
embeddings of wedding-1 never go in the repo.

- `cells.py`: cuts each photo out of the harvest contact sheets (sets whose numbering is complete).
- `embed.py`: image embeddings (768 numbers, normalised) for a folder of photos, about 0.02-0.05 s a
  photo on 4 cores here.
- `probe.py`, `select_eval.py`: does what it sees predict a person's picks on held-back sets.
- `visual_appeal.py`, `w1_select.py`: the learned seeing score on wedding-1, alone and inside the planner.
- `story.py`: "conscious selection" prototype: scenes, who matters, weigh each scene, story first,
  best frame, everyone important with the couple, a reason per photo.
- `fair.py`: catches by moment and by look-alike scene.

The honest yardstick (wedding-1, 233-photo album from 2,664): photos chosen at random already catch
a lot, because weddings are shot in bursts: 35% of the couple's moments at 233 picks, 54% at 400,
79% at 766. So a score means little without random beside it. Fair measure: the share of the gap from
random to perfect that a list closes. Claude's blind 766: (93.6-78.6)/(100-78.6) = 70%; the app at
766: 48%; the app at 400: 30%; the app at the album's own size (233): 5%. "Same scene by look-alike"
is useless here (random 400 scores 74-78%, the app 79%).
