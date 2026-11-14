# The reasoning AI: standing plan and log

Owner, 14 Nov 2026: "I want AI that at least reasons like you. Until then don't complain, don't come
back to me, loop yourself every 3 hours." A scheduled task wakes Claude every 3 hours with this file
as its memory. Read it top to bottom, do NEXT STEP, write the log, commit, push.

## What "reasons like Claude" means here (the goal test)

The phone's own AI, with no internet, looks at a raw folder the way Claude does: it says what is
happening in each moment, who it is about, whether it belongs in the album and why, remembering what
it has already kept. Come back to the owner only when all of these hold:

- **A. Says the same as Claude.** On moments it has never seen (whole events held out, then whole
  weddings), its keep / skip / peak verdict agrees with Claude's as often as Claude agrees with
  itself when judging the same moments again blind (the ceiling, measured in S2), and its words about
  the moment are judged right by Claude at the same rate as Claude's own.
- **B. Understands the folder.** From the photos alone it says what the occasion is and whose it is
  (web sets whose occasion is known), as often as Claude does.
- **C. Picks like Claude or better.** On the exam (wedding-1, never trained on) it beats Claude's
  blind picks at 233, 400 and 766 photos (numbers at the album's own size, 233, come first).
- **D. Any community.** A and C on a community left out of the teaching are as good as on the ones
  it learned from.
- **E. On a real budget phone.** Galaxy A15, no internet, a 2,500-photo folder done in about an hour.

## Design (why this is reasoning, not rules)

A small model that sees and writes (a vision-language model, Apache/MIT licence) reads the day
moment by moment, in order. For each moment it gets the picture (up to four frames), a line of facts
the app already measures (folder, how many photos, are the main people in it), and what it said about
the most similar moments it has already read. It writes: what kind of moment, what is happening,
keep / skip / peak, and why ("mehendi on another relative, repeats the idea"). The album is then made
from its verdicts, and its words are the reasons shown under each photo. It is taught by Claude's own
written judgements (distillation), never by per-ritual or per-community rules.

## Teaching material

- Gold (Claude's own words): `tools/realphotos/picks/wedding-2-moments-claude.tsv`, 961 moments of
  wedding-2 (kind, 0 skip / 1 one photo / 2 peak, reason). Moment -> photos:
  `tools/reasoner/w2-moments.tsv`. Photos: 800 px copies on Claude's machine (scratchpad `w2/x/raw`),
  1,024 px on the owner's private Kaggle (upload "w2" in `tools/kaggle/run.json`; the family agreed).
- Frame lessons: `tools/reasoner/frame-labels.tsv` (302 near-twin groups ranked, with reasons).
- More gold is needed from other communities and occasions (web photos, CC licences): Claude judges
  about 100-150 web moments per wake-up from contact sheets until every community has some.
- Wedding-1 is the exam only: never trained on, never sent to Kaggle.

## Stages

- S1. Examples from the gold: one picture + facts + similar earlier moments -> Claude's words. Split
  by event folder (held-out folders are never trained on). Baseline to beat: the current scene judge.
- S2. Ceiling: Claude judges ~120 held-out moments again, blind; Claude-with-Claude agreement.
- S3. First student: fine-tune a small model (SmolVLM 256M / 500M) on the gold; score on held-out.
- S4. More teaching: Claude's gold on web moments of other communities and occasions; a big open
  teacher on Kaggle for volume, kept only where it agrees with Claude's gold.
- S5. Bigger students if the small ones fall short (2B class), and the phone runtime (ONNX Runtime is
  already in the app; llama.cpp is the fallback); speed on the real A15.
- S6. In the app: the model reads the moments after checking; the album follows its verdicts; its
  words are the reasons. Phone test, real phone.
- S7. Goal test A-E; then, and only then, a plain report to the owner with a real-phone video.

## Rules of the loop

- Measure on the yardstick, never on a hunch. Album-size numbers (233) first.
- Each wake-up: collect finished jobs, do NEXT STEP, start the next long job, log, commit, push.
- Long jobs: Kaggle GPU through `kaggle-train.yml` (30 GPU hours a week), or this machine's 4 CPUs
  in the background (2 hours at most per job). Disk here is tight (about 6 GB free).
- No message to the owner, no complaint. One short status line at the end of each turn.

## Log

**14 Nov 2026, 12:00 pm IST. Loop started.** Stock taken: 961 gold moments with Claude's reasons
(wedding-2: 301 skip, 531 keep, 129 peak; 11 folders); wedding-2 photos are on this machine at 800 px;
the Kaggle copy was never attached (the people-teacher job stopped for that reason); torch works here
on CPU (scratchpad venv). The brain today at the album's size (233): 12.0% of the couple's exact
photos, 47.2% of their moments (random 34.8%); Claude's 170 must-picks 32.6% of moments, same as the
brain at 170.

**14 Nov 2026, 12:20 pm IST. S1 done, S2 done, S3 started.**

- Examples built (`reader_data.py`): 961 moments, each a 512 px sheet of up to four frames + facts +
  what was said about similar earlier moments -> Claude's words and verdict. Held out, never trained
  on: the reception, the gondhal and the nanhora (272 moments: 105 skip, 150 keep, 17 peak); 689 to
  learn from.
- What the reader must beat on those 272 (`reader_baseline.py`): always the commonest answer 61.4%
  keep-or-skip / 55.1% skip-keep-peak; a numbers-only scorer like today's brain 70-75% / 62-67%.
- **The ceiling (S2):** Claude judged 127 of the held-out moments again, blind
  (`wedding-2-moments-claude-again.tsv`): same keep-or-skip **82.7%**, same skip/keep/peak **75.6%**.
  So on the verdict alone the gap between a plain scorer and Claude is only about 8 points; the real
  difference is in the words (knowing what is happening), so the words must be graded too (test A).
  Lesson from the misses: a long moment shown as four frames hides its point (M159: 32 photos, the
  dancing was not in the four; M757: the sieve ritual too small to see). The reader needs more than
  four frames of a long moment, or its busiest frames.
- First student started on this machine: SmolVLM-256M (Apache 2.0), 4 rounds, eyes frozen, about
  5 million numbers learning (`reader_train.py`, output scratchpad `reader/run-256m`). SmolVLM-500M is
  downloaded for the next run.
- Web photos for other communities: the `teach-sheets` branch holds 1,158 photos of the picked web
  sets (CC licences); `harvest-data` lists 922 sets with their occasion.

**14 Nov 2026, 12:45 pm IST. Owner: "use your own reasoning for our AI, across all Indian communities". S4 started.**

- That is the method (distillation): Claude writes its judgement of photos of every community, the
  small model learns from those words. More of Claude's own gold comes first, the open teacher second.
- What is in hand for other communities: the caption scans found 6,334 Flickr CC BY photos in 1,416
  sets (one photographer, one day). 42 sets Claude already called Indian by eye (235 photos), and 129
  sets of 4+ photos say India by place or words (1,739 photos; hints: Muslim 248 photos, north 155,
  west 158, south 133, Sikh 78, Christian 55, east 41). Most are not judged yet. Beware: the scan's
  "Muslim wedding" photos include Malay weddings (not Indian; kept apart, never an Indian test).
- Kaggle job sent (`tools/kaggle/india_sets.py`, slug album-done-india-sets, CPU): lays those sets out
  on numbered sheets (12 photos each, set by set, in time order) -> `kaggle-results` branch
  `album-done-india-sets/sheets/Q###.jpg` + `india.jsonl`; the photos stay on Kaggle (`india_img/`) for
  the training job.
- First web gold in Claude's words: `tools/reasoner/gold/web-claude.tsv`, 30 photos of 6 sets (Kerala,
  Gujarati, north, Punjabi/Sikh, diaspora), keyed by Flickr photo id.

**14 Nov 2026, 1:25 pm IST. First student scored; web sheets back; 131 web photos judged.**

- **SmolVLM-256M, taught 4 rounds on the 689 wedding-2 moments** (loss 1.22): on the 272 held-out
  moments same keep-or-skip **70.2%**, same skip/keep/peak **61.4%**. That is only level with the
  numbers-only scorer (70-75% / 62-67%); the ceiling is 82.7% / 75.6%. It writes in Claude's form but
  often invents who is in the picture ("the bride's parents", "the mandap"). So: too small, too little
  teaching, or both. SmolVLM-500M is running now the same way (scratchpad `reader/run-500m`, 3 rounds).
- The India-sets job worked: 1,733 photos, 157 sets, 145 sheets (`kaggle-results`:
  `album-done-india-sets/`, copied to scratchpad `india/`). Sets W000-W041 are the ones already called
  Indian (photos 1-235); then W042 (UK Indian wedding, 150), W043 (Pakistani wedding in Scotland, 119:
  a neighbour, kept apart), W044/46/52 (Indian wedding in New Mexico), W045 (Gujarati, UK), W047-49/55
  (Bengali script tags: check if India or Bangladesh), W050/51 (Bangalore), W053 ("Buddhist Indian
  wedding", Birmingham), W054/56 (India), W057 (Malay-Indian Muslim, Penang), W058 (Mangalore)...
- Claude's web gold: **131 photos** judged in words (`tools/reasoner/gold/web-claude.tsv`; add with
  `gold_add.py`): photos 1-120 except 37-48 (sheet Q004). 43 skip, 78 keep, 10 peak. Kerala, Karnataka,
  Telangana, Bengali, Gujarati, Punjabi/Sikh, north, diaspora. No Indian Muslim or Christian set yet.

**14 Nov 2026, 6:50 pm IST. The machine was replaced; the work now lives on the repo and Kaggle.**

- While idle the container was replaced and the scratchpad wiped: the old lock key, the local copies
  of both weddings, the vectors, the exam files and the SmolVLM-500M result are gone (the 500M score
  was never read). The repo and the branches are intact.
- New lock key (public half in `tools/realphotos/claude_pub.pem`; private half only in the scratchpad,
  it cannot be saved anywhere else). The old locked files cannot be opened; they are remade by re-running
  `real-photos.yml` (wedding-2 raw is running now; then wedding-1 raw, then wedding-1 album, one push of
  `tools/realphotos/run.json` each). If the key is lost again: make a new pair the same way, commit the
  public half, re-run those three.
- **Teaching moved to Kaggle** so a wiped machine costs nothing: `tools/kaggle/reader_teach.py` builds the
  examples there (wedding-2 from the private dataset `album-done-w2`, web photos from the india-sets job),
  teaches SmolVLM 256M, 500M and 2.2B in turn (6 rounds, eyes frozen; `EYES=1` lets the eyes learn too) and
  scores each on the same 272 held-out wedding-2 moments and on a quarter of the web sets. The gold files
  travel with the script (`"attach"` in `tools/kaggle/run.json`, new in `kaggle-train.yml`). Tested end to
  end here on CPU with the web photos. Sent: slug `album-done-reader-teach` (first run also uploads wedding-2).
- Web gold: **154 photos** (added Kerala's last, a north Indian haldi set, a Goan Christian bride getting
  ready, a small South Asian Muslim set, a Tamil Brahmin set). Lesson: after the 42 known sets the pool is
  mostly not weddings at all (W053 is a cricket ground, W150-152 monuments and crafts, W146 Native American):
  do not read those sheets one by one. A Kaggle step should first rank every photo by "is this a wedding
  moment" and guess the community (SigLIP words or the open teacher), so Claude reads only what is worth it.

## NEXT STEP

1. `git fetch origin kaggle-results`; read `album-done-reader-teach/scores.txt` and the answers files:
   256M vs 500M vs 2.2B on the 272 held-out moments (ceiling 82.7% / 75.6%, plain scorer 70-75% / 62-67%)
   and on the held-out web sets. If the run is not finished, send `"mode": "fetch"`. If the upload of
   wedding-2 failed (cache gone), wait for the real-photos wedding-2 run, then send again.
2. If no model clears the plain scorer clearly: `EYES=1` (the eyes learn too), then more frames for long
   moments. One change at a time.
3. Push `tools/realphotos/run.json` for wedding-1 raw (`{"name": "wedding-1", "occasion": "WEDDING",
   "step": "raw", "links": "REAL_LINKS"}`), next wake-up the album step: the exam files under the new key.
4. Web gold: W054 (photos 925-944), W056, W050, W051, W058, W042, W044, W045; W043 as a neighbour
   (Pakistani Muslim wedding: teaching only, never an Indian test). Add a ranking step on Kaggle first for
   the rest. Targeted search for Indian Muslim / Sikh / Christian weddings is still to do.
