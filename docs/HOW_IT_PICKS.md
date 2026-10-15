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
