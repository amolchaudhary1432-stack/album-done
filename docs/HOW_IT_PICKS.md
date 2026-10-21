# How Album Done picks like a person would

The goal: the shortlist should look like one the couple would have made themselves after a week
of arguing with family, with nothing embarrassing in it, made in minutes on the phone.

"Human-level" is a claim we must **measure, not assume** (see "How we'll know" at the end).

## 1. How people actually choose an Indian wedding album

A photographer delivers 2,000–10,000 photos. Couples usually choose 250–500. Done by hand, it goes
like this:

1. **Throw out the bad ones.** Blurry, eyes closed, someone mid-bite, repeated shots.
2. **Go event by event, in order.** Haldi, Mehendi, Sangeet, Wedding, Reception, Vidaai. Each gets
   a share, and the wedding itself gets the most.
3. **Make sure every ritual is there.** Varmala, kanyadaan, pheras, sindoor, mangalsutra, vidaai.
   For each one, the photo of *the* moment: the garland going over the head, the sindoor being
   applied. Missing the pheras photo is unthinkable.
4. **Make sure the family is there.** Parents and grandparents in many photos, siblings, then
   uncles and aunts. Every family group that went on stage at the reception expects its photo.
   This is where most fights happen ("Mama's family is not in the album!").
5. **The couple.** The best portraits, one for the cover, both looking good, the bride above all.
6. **Feeling.** Laughter, tears at vidaai, parents' reactions, candid moments between the couple.
7. **Fill and balance.** A few details (mehendi hands, jewellery, decor, food), no three photos of
   the same pose, a mix of upright and sideways photos for page layout.
8. **Fine-tune.** "Same moment, but the one where Aai is smiling." People swap between similar
   photos far more than they add or remove.

## 2. What the app does today, step by step

| Step | Today | Gap |
|---|---|---|
| 1. Bad photos | Strong: blur judged on faces, light judged per event, closed and half-closed eyes, mid-talk, cut-off or blocked faces, bursts and repeats, crooked photos, phones, guests' backs, strangers | Thresholds not yet tuned on real weddings |
| 2. Events | Photographer's folders, in his order | Fine. Several cameras with wrong clocks can scramble time order |
| 3. Rituals | Finds "key moments" where the photographer shot many photos in a short time | **Cannot see what is happening.** It can't tell varmala from a crowd shot, can't pick *the* frame (garland over the head), and can't warn "no pheras photo found" |
| 4. Family | Groups faces into people, finds the couple, Must/Important coverage, elders first, sides balanced, nobody over-represented | **Depends on tagging**, which is too much work today. Doesn't guarantee one photo of each family group on stage |
| 5. Couple | Cover, solo portraits, both looking good, bride's face weighted, bonus when they look at each other | Good |
| 6. Feeling | Tears and laughter are never treated as flaws | **No bonus** for them. Emotion should *raise* a photo, not just avoid a penalty |
| 7. Balance | About 10% details, no repeated pose, portrait and landscape mix | "Detail" just means "no faces", which can pick an empty chair. It can't tell jewellery from a wall |
| 8. Fine-tune | Remove, then "Replace removed" | **No swapping between similar photos**, the way people actually choose |

Also missing: using the **photographer's own signals**. Many deliver an "Edited" or "Highlights"
folder, star ratings inside the files, or colour plus black-and-white copies of the same photo.

## 3. Upgrades, most valuable first

### A. Find the family without asking (no new model)
Family members show up at **many functions**: Haldi, Mehendi, the wedding and the reception.
Guests mostly appear at the reception only. Family also stand close to the couple in rituals and
in posed groups. Side comes from where they appear: the bride's haldi and mehendi versus the
groom's. With this, the app can guess "close family" well enough that tagging becomes optional.
Tagging only *fixes* the guess.

### B. One photo for every family group on stage (no new model)
Detect each stage visit (a new set of people next to the couple), keep the best shot of each, and
fill the reception's family-photo share from those visits, closest family first.

### C. Swap with similar photos (no new model)
Every photo knows its "neighbours": same burst, same moment, same group, same pose. Tapping a photo
shows them, each with a one-line reason why it wasn't picked ("Aai's eyes closed"). One tap swaps.

### D. Use the photographer's signals (no new model)
"Edited", "Selected" or "Highlights" folders and star ratings count as strong votes. Keep the colour
version when a black-and-white copy also exists. Line up the clocks of several cameras.

### E. Emotion and reactions (no new model)
Give laughing and crying faces a bonus, and prefer the photo that shows parents' reactions during
key rituals.

### F. Understand what is in the photo (new model: Google SigLIP 2, Apache 2.0)
A model that matches photos to descriptions such as "bride and groom exchanging flower garlands",
"couple walking around the sacred fire", "applying sindoor", "mehendi on hands", "gold jewellery
close-up", "decorated mandap" or "food buffet". This gives:
- each ritual named, so the app can make sure each one is in the album and **warn if one is missing**;
- the decisive frame inside each ritual;
- real detail shots instead of "photos without faces";
- a rough sense of a well-composed photo.

Cost: about 90 MB more app and about 20–30 more minutes per 5,000 photos. **Test it first** on
public Indian wedding photos (Wikimedia Commons) in the cloud build before adding it to the app.

## 4. The experience (UX)

Principles:
- **Album first.** After the check, show a finished album at once. Every fix happens on the album
  itself, and there is no chore before you see results.
- **One question per screen**, in plain words. No "merge", "priority", "cluster".
- **Everything explains itself.** Every photo says why it is there. Every warning says what to do.
- **Nothing is final.** Swap, remove, change the size, change a family tag: the album updates in
  seconds and keeps your own changes.

Flow (5 screens): choose folder → found photos + album size → checking (lock your phone) →
**your album** (cover, couple, family strip with counts, events with their moments) → tap a photo
(why + swap) or tap a face (who is this?) → send the list.

### Family tagging: options compared

| Option | Good | Bad |
|---|---|---|
| List of 40 faces with Must/Important/Skip (built in v1) | Complete | Too much work, jargon, people give up |
| Tag faces on a family group photo (Amol's idea) | Natural context, feels familiar | Needs the right photo, faces are small, people repeat across photos, nothing is pre-filled, people missing from that photo are missed |
| **App guesses the family, shown as faces with photo counts on the album; tap a face to fix ("Who is this? Aai")** | Zero work by default. Big clear faces, each person once. Fixing is one tap. The relation sets everything else (how many photos, elder, side) | The guess must be good (upgrade A), so it needs testing on real weddings |

Chosen: the third option. The group photo still helps as **context**: the "Who is this?" card
shows the person in a few photos, often a family group.

Clickable mock-up: https://claude.ai/artifact/KzbjcqjdAW5KkNJ47pFf6f

## 5. Is it complex?

Inside, yes. That is where the value is: face reading, people grouping, moments, rules. For the
couple it must feel like three taps: choose folder, wait, look at the album and swap what they
don't love. Every new feature has to pass that test.

## 6. How we'll know it's human-level

1. Get one or two past weddings where the couple already chose their album (from a photographer
   friend, with the couple's permission).
2. A hidden "compare" screen in the app: paste the couple's final file list, and the app shows how
   many of its picks match, event by event, and which rituals and family members it missed.
   Photos never leave the phone.
3. Tune until the match is high and nothing embarrassing is picked. Only then make claims in the
   store listing.

## 7. Test log: real weddings

Real, freely licensed wedding photo sets from Wikimedia Commons are run through the real app on
an Android emulator in the cloud build (`tools/test-photos`, test data only). Claude looks at every
photo and judges the album like the couple would.

### A Bengali wedding in Kolkata (114 photos, 29 May 2023, by Goutam1962, CC BY-SA 4.0)

Adhibaas at home, haldi in a tent, bride portraits, the night rituals (fire, sindoor daan). Shot
from the bride's side: the groom appears in about 7 photos, the bride in about 90. Album of 28.

First run (face mesh model only):
- Good: the bride was recognised across haldi and full bridal makeup (one person, 64 photos); the
  haldi ceremony, several fine bride portraits, the sindoor moment and story order were kept.
- Wrong, and a person would notice at once: 59 photos with people had "no face" (side-on,
  downcast and small faces); the bride's aunt was taken for the groom, so the cover was "bride and
  aunt"; the one clear photo of the bride and groom and the couple at the fire were left out; two
  photos of people's backs were picked as "detail shots".
- Fixed: YuNet face finder (361 faces found instead of 67), couple found at the wedding rather
  than at the bride's own functions, cover in wedding clothes, backs are not details, reasons in
  plain words.

### Scene model check (SigLIP 2, zero-shot, 217 labelled photos)

| Recognised well | Not yet reliable |
|---|---|
| food 91%, mehendi 68-80%, baraat 70-95%, garland exchange 56-75%, bride portrait (large model) 92% | sindoor 17-37%, fire ritual / pheras 17-33%, kanyadaan 33-42%, haldi 38-41% |

The rituals are mostly lumped together as "a priest doing a ritual".

### Scene model check, trained (a small classifier on the model's image features)

| Test | SigLIP 2 base (~93 MB in the app) | TinyCLIP 39M (~39 MB) | TinyCLIP 8M (~8 MB) |
|---|---|---|---|
| Photos mixed (same wedding can be in learning and testing) | 81% | - | - |
| Whole weddings kept apart (honest) | 59% | 49% | 47% |
| Fire ritual / pheras, found / right when it says so | 67% / 52% | 63% / 55% | 59% / 47% |
| Sindoor | 24% / 46% | 21% / 53% | 28% / 51% |
| Haldi | 68% / 81% | 19% / 43% | 21% / 73% |

Not good enough to ship: about 90 MB more app for a guess that is wrong half the time on a new
wedding. Wikimedia Commons has only a handful of different weddings per ritual. What would make it
work: a few hundred labelled photos per ritual from many different weddings (for example from
photographers, with the couples' permission), then the same test again. Until then the app finds
key moments by how densely the photographer shot (a burst of photos in a few minutes), which
works for any ritual without knowing its name.

Owner's decision (15 Oct 2026): no ritual sorting in the app. The job is picking good photos like
a person would; the scene model work above is parked.

### Replica photographer's dump (206 photos, answer key)

The Bengali wedding above plus realistic bad versions of its photos (`tools/test-photos/quirky.py`,
TRUTH.json says what is wrong with each). Album of 28, checked on the emulator:

| Bad photos planted | Caught | Got into the album |
|---|---|---|
| Too dark (6) | 6 | 0 |
| Out of focus (8) | 8 | 0 |
| Shaky (10) | 7 (the other 3 were set aside as repeats) | 0 |
| Accidental shots: floor, lens covered (4) | 4 | 0 |
| Burst frames (40), black-and-white copies (6) | never two versions of one shot in the album | - |
| Flash blown out (6) | 2 | 3, fixed: limits now 185 brightness / 35% burnt white (real photos reached 168 / 25%) |
| Face cut by the edge (6) | 1 | 1 on the re-run, fixed: a face box against the edge that is narrower than any whole face (below 0.55 width/height; whole faces measured 0.59 and up) is cut off |
| Crooked by 5-9 degrees (6) | 0 | 1 |

Crooked photos: the old measure (strongest edge direction) never fires on wedding photos, because
saris, people and decor have edges in every direction; a long-straight-line version was tried on
114 real photos turned 5-9 degrees and caught none. Parked: it is the mildest flaw, and the
current measure never marks a good photo down.

Black-and-white photos are not marked down: some couples ask for them. When the same shot exists
in colour and black-and-white, only the better-looking one goes in.

After the brightness fix the re-run caught 6 of 6 flash-blown photos (none in the album).

### Face finder confidence (real wedding, 315 finds)

| Confidence | Finds | Not a face |
|---|---|---|
| 0.60-0.70 | 32 | about 2 in 5 (flowers, cloth, hands, backs of heads) |
| 0.70-0.80 | 28 | about 1 in 5 |
| 0.80-0.90 | 91 | 1 (a flower) |
| 0.90+ | 164 | none seen |

So a find the face mesh cannot read is kept only from 0.70, and only finds from 0.80 (or read by
the mesh) are used to recognise people. Pink flower decoration had become a "person" before.

### Claude as the judge: same 114 photos, album of 28 (`docs/eval/claude-vs-app-real-wedding.jpg`)

Claude looked at every photo and picked 28 the way a person would; compared with the app's 28
(clean run, couple not confirmed):

- Same photo: 10. Same moment, a neighbouring frame: about 8 more. So about 18 of 28 moments agree.
- What a person keeps that the app missed: the bride's best smiling, eye-contact close-ups (the
  app took her looking down or in profile), the one close photo of the bride and groom together
  (the app was unsure who the groom is and nobody answered), mother and daughter together, the
  bride arriving with her garland, a playful veil shot, a family moment at the ritual.
- What the app took that a person would not: the bride's face hidden under her veil (counted as a
  "detail"), the altar with the bride's back, a busy crowd where the bride is not the subject, a
  third and fourth frame of the same ritual.

Lesson: the app is good at moments and at throwing out bad frames; it is weak at "is this the
best expression, the best relationship, the best story beat". Those need a learned eye, not more
rules. Next: score many weddings this way (a person's or Claude's picks as the answer key) and
teach a small on-phone model from them.

## 8. Any occasion, not just weddings (owner's decision, October 2026)

Album Done picks the album-worthy photos from any raw folder. The photo checks (eyes, focus,
light, repeats, moments, variety) are the same for every folder; what changes with the occasion
is who the album is about and the words used:

| Occasion | Built around | Sides | Notes |
|---|---|---|---|
| Wedding, Engagement | the couple (two most photographed, often together) | bride's and groom's family | family albums for each side |
| Birthday (a child's) | the most photographed person (the birthday star) | none: Family / Friends | the two closest people are guessed as mummy and papa; a family photo of the three in every big event |
| Birthday (a grown-up's) | the birthday star, plus a partner when nearly as frequent | none | |
| Trip, Other | the most photographed person, plus a second when nearly as frequent and often together | none | a trip keeps about a quarter of places without people |

The occasion is guessed from the folder names (Haldi, Reception → Wedding; Bday → Birthday; Goa
trip → Trip) and is one tap to change on the New album screen.

Test sets for unseen folders: a Rajasthani wedding seen by a guest (158 photos: sky lanterns at
night, desert, palace, the wedding), a Garhwali wedding (134 photos, mostly the groom's haldi);
Claude's blind picks are the answer key. Marathi weddings, birthdays and family functions are
being searched for on Wikimedia Commons.

## 9. Outside review: Astra's suggestions (October 2026)

Astra (another assistant, read-only access) sent a large package: a learned "visual brain"
(TinyCLIP image model plus two small trained layers, in a separate research build), a rewrite
of parts of the planner, a capture-date fix and a list of mistakes found on 214 Indian photos.
Every planner change was scored on our three Indian weddings with picks (Bengali 28,
Rajasthani 24, Garhwali 21): photos matched exactly, and same moment.

| Change | Bengali | Rajasthani | Garhwali | Decision |
|---|---|---|---|---|
| Whole package, ordinary app | 10 / 24 | 5 / 11 | 1 / 12 | Not taken as a whole: no clear gain |
| Clear faces = the photo's subjects | 11 / 24 | 6 / 12 | 1 / 12 | Kept (was 9 / 23) |
| Unknown eyes neutral | 11 / 24 | 5 / 10 | 1 / 12 | Dropped |
| Key moments count distinct shots | same | same | same | Kept (sound, no harm) |
| Forehead cut by the top edge is framing | same | same | same | Kept (birthday child held by an adult) |
| Body cut by the top edge is not a back | n/a | n/a | n/a | Kept, unit-tested |
| Capture date only from "date taken" | n/a | n/a | n/a | Kept: edited exports share one save date |

Not taken, and why:
- The learned visual brain. Its own numbers show the bounded app integration at 64.6% against
  63.6% for the rules, inside the noise. It was trained on foreign photo-story sets (VIST,
  Flickr albums) whose rights for a paid app are unclear, and it has no data for a child's
  birthday. The idea stays on the list (section 3F) once we have Indian labels.
- The global re-selection and refinement passes: more code, no measured gain here.
- A database migration for old cached dates: the app is not live yet, new scans get the fix.

Taken as working rules: keep test weddings separate from anything used to tune (once looked at
for a fix, a wedding is no longer a fair test); count unique events, not photos; an AI's picks
are one opinion, not the truth.

Still open from Astra's list: two near-identical standing poses both picked (needs a "same pose"
check beyond the burst hash), printed photos and cake toppers seen as faces, and a laughing
bride marked as a stranger when someone walks across the front.

## 10. How people build albums: research and our own numbers (October 2026)

From photographers', album labs' and photo-book guides (search summaries, rules of thumb, not
surveys) and four research papers on photo-album selection:

- Size: a traditional Indian album is 30-40 sheets with 6-8 photos per sheet (200-250 photos);
  a photo book 60-150 photos. That is about 5-15% of the delivered photos (600-3,500).
- Mix: about 60% candid and 40% posed; couple portraits 15-20%; details 10-15% (25% at most).
  Trips: up to half scenery.
- Every ritual gets at least 2-3 photos (varmala wide and close, pheras, sindoor, kanyadaan,
  vidaai with tears). One photo per repeated pose; a big moment two at most.
- Family groups: the big both-sides group first, then each bride's-side group mirrored by a
  groom's-side one; a last check that no close relative is missing. Each family's copy is the
  common set plus its own side (as our Send screen does).
- Rejected: missed focus, motion blur, closed eyes, one blink in a group. Forgiven: tears,
  laughter, dance blur. A flawed frame stays if it is the only one of a must-have moment.
- Research (Ceroni 2015, CUFED 2016): people rank who is in the photo and what it remembers far
  above technical quality; they drop near-repeats first.
- Birthday: decor before guests, the cake untouched, the candles, everyone together,
  grandparents, reactions. Annaprashan: first bite, grandparents' blessing, the object game.
- Thin evidence: pages per function (one source), naming ceremonies, portrait/landscape ratio.

Our own numbers (three test weddings, the picks against the folder and the app):
- The picks accepted flagged photos far more than the app did (8 of 28, 8 of 24 and 13 of 21
  picks had a flag; the app's albums had 1 each). About a third of "not looking" and "face partly
  hidden" photos were picked, more than the average photo. Acted on: "not looking" now costs
  little (Garhwali 12 -> 14 moments).
- Tried and not kept: a softer "partly hidden" cost (mixed), a 45% candid target (worse).
  Three weddings are too few for finer tuning; the harvested sets come next.
- Note: these picks are Claude's blind picks, a stand-in for a couple. A real couple's folder
  is the test that counts.

## 11. Scored on 30 albums picked by eye; a learned "appeal" (October 2026)

The harvest gave 106 freely licensed sets. Albums were picked by eye from the contact sheets for
the 29 that are real family events (weddings first), before running the app on them
(`tools/harvest/labels/`). About a third are held back by a fixed hash of the set's name: their
per-set results are never shown, only the total (`HarvestScoreTest`).

| | practice (17 sets + 3 bench weddings) | held back (6 weddings) |
| --- | --- | --- |
| Before | 196 of 259 same moment (76%) | 66 of 87 (75%) |
| After | 204 of 259 (79%, each set scored with weights learned without it) | 70 of 87 (80%) |
| Later the same day (more sets, duplicate removed, picks spread over more moments) | 244 of 280 (87%) | 70 of 87 (80%) |

What changed:
- Photos whose only faces are too small to judge (a crowd seen from far) score 0.7 of the
  plain photo score: the app was picking crowd shots over clear people.
- `Appeal`: a small learned score (logistic regression over 45 measurements: faces, smiles,
  looking, every flag, shot type, light) of how likely a person is to pick a photo. It is a
  quarter of a photo's value; the planner's rules (people, moments, variety) stay in charge.
  `tools/harvest/learn.py` makes `AppealWeights.kt` from the labelled practice sets; held-back
  sets are never in it. More labelled sets: re-run it.
- Tried and not kept: lower scores for photos without faces (mixed), sharing a moment's places
  by its photos of people (worse), fewer forced solo portraits (no change), appeal as half or all
  of the value (worse: the learned score knows nothing of moments).

How high can the score go? The Rajasthani bench wedding turned out to be the same wedding as the
harvested set Fabe56_2014-01-03, picked by eye twice, weeks apart (24 and 32 picks). Matched at the
same album size, the two pickings share 18 photos and 22 of 24 moments (92%). That is one person
twice; two different people agree less, so a person-versus-person score here is likely 85-92%.
"95% of human level" therefore means the app's score at about 95% of that, not 95% outright.
The copy is now left out of the learning and the honest test (practice 83% without it).

Spread over more moments: on the Garhwali wedding the person's 21 picks covered 16 moments (two at
most from any one); the app's covered 14, with four from one. Each photo already picked from a
moment now costs 0.3 (was 0.06): practice 83% -> 87%, held-back weddings 79% -> 80%.

What the misses are now: mostly which small moments to show (a person picks the friends in
saris, the decorated mandap, the family group; the app another candid). The measurements do not
say what is happening in a photo; a scene model (section 3) is the next big step.
