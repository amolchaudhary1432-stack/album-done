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
