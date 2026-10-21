# Album Done

Android app (Kotlin, Jetpack Compose) that picks the album-worthy photos from a raw folder: weddings
first (Indian couples), also birthdays, baby functions, trips and any other occasion.
Everything runs on the phone. Photos are never uploaded. The app has no internet permission, and
that must stay true.

## Who it is for

- **Buyer:** the couple, usually the bride. She has the photographer's photos (pendrive copied to
  phone, or a Drive folder saved offline) and family pressure to finish the album selection.
- **One folder, up to 1,000 photos** per album (`plan/AlbumSize.kt`). Bigger folders are refused
  with a friendly message (pick one function's folder, or the photographer's selection).
- **The job is picking good photos like a person would.** No ritual sorting or ritual labels in
  the app (owner's decision); the photographer's sub-folders are only used as given.
- Indian weddings are huge: the app must handle 40+ relatives smoothly, not 6.
- **Not a wedding-only app** (owner's decision): "Album Done picks the album-worthy photos from any
  raw folder, on your phone." Weddings are the first and biggest use. New album asks "What are these
  photos of?" (`plan/Occasion`: Wedding, Engagement, Birthday (a child's or a grown-up's), Family function
  (naming / annaprashan, baby shower, anniversary, housewarming), Trip, Other), guessed from the
  folder names. Only Wedding and Engagement have a couple and the bride's/groom's sides; other
  occasions have one main person (or two on a trip), found as the most photographed, and the
  words follow ("the birthday star", "Portrait of Riya"). Photo judging is the same for all.
- Bride's family and groom's family each keep their own album copy: the Send screen makes either
  from the same folder (`PeopleTags.forFamily`).
- **Promise:** "Your wedding album shortlist in a few taps, without uploading a single photo."

## How the owner works

- Amol is the owner. He is not an Android developer and does not install Android Studio.
- Builds happen only on GitHub Actions (`.github/workflows/build.yml`). Every push to `main`
  runs unit tests, builds a signed APK and publishes it as the `test-build` GitHub release.
  Amol installs that APK on his phone from the release page.
- After pushing, check the run (GitHub Actions) and fix until green. Do not hand over a build
  that has not passed. Amol wants Claude to test the app itself (the phone test) before he does.
- Explain things to Amol in plain words, no jargon. Keep replies short.

## Version 1 scope (build this, nothing more)

The goal is human-level album selection, not just discarding bad photos. The selection logic is
the core of the product: most of the effort and testing goes there (`plan/AlbumPlanner.kt`,
pure Kotlin, unit-tested).

### Technology (all on the phone, no internet permission, Apache 2.0 or similar licences only)

- Face finding: OpenCV Zoo YuNet (MIT, run with ONNX Runtime) on the whole photo at 960px: it finds
  small, side-on and downcast faces. On a real wedding the face mesh model alone found 67 faces in
  114 photos, YuNet 361. Each face is then read from a close crop by the mesh model below; faces it
  cannot read are kept with eyes "unknown" (counted and recognised, neither praised nor blamed).
- Face reading + expression: MediaPipe Face Landmarker (several faces, blendshapes on). Blendshapes
  give eye blink (eyeBlinkLeft/Right), gaze (eyeLookIn/Out/Up/Down), smile (mouthSmileLeft/Right),
  mouth open (jawOpen); head angle comes from the face transform. (Replaces ML Kit.)
- Identity: OpenCV Zoo SFace face fingerprints (run with ONNX Runtime), grouped into people.
- MediaPipe object detector (EfficientDet-Lite0) for people boxes and phones.
- Model files are bundled in the APK under `app/src/main/assets/models/`; their checksums are pinned
  in `app/models.sha256` and checked by the workflow. Licences: `THIRD_PARTY_NOTICES.md`.

### Flow (album first: the couple should not have to do anything)

Choose folder (`OpenDocumentTree`, persistable read permission, no gallery-wide permission) → scan
(`scan/FolderScanner.kt`, shows photo count and events) → album size → analyse in background
(`work/AnalyzeWorker.kt`: WorkManager foreground job, works with the screen locked, saves each
photo's result immediately, one lane on low-RAM or under-6-core phones, two otherwise; photos
decoded at about 1280px, never full size) → find people → the finished album opens straight away.
Everything after that is optional fixing. Ask for notification permission on Android 13+ first.
Design mock-up: https://claude.ai/artifact/KzbjcqjdAW5KkNJ47pFf6f

### Events

- The photographer's sub-folders are the events (Haldi, Mehendi, Wedding, Reception), named as
  given, in the photographer's order (numbered folders by number, otherwise by when each started).
  No automatic event splitting. Everything in one folder = one event.

### Per-face checks

- Eyes open, for every visible person. Looking at camera (head angle plus gaze).
- Expression: smiling or natural; flag mid-talk, mid-chew and half-blinks (jawOpen, partial blink).
- Face sharpness measured on the face crop, not the whole photo (backgrounds are often blurred on purpose).
- Face exposure (backlit or shadowed faces). Face blocked or partly hidden (another person, hand,
  phone). Face cut off by the photo edge.

### Per-photo checks

- Blur, too dark or too bright, tilt. Bursts and near-identical repeats: keep the best.
- Portrait or landscape (for page layout).
- Strangers walking through, backs of guests, phones held up in the foreground.

### Group photos

- Score a group by its worst face: everyone's eyes open, everyone facing the camera, nobody hidden
  or cut off. Collapse near-identical stage photos of the same family group to the single best one.

### Couple

- Detect the couple automatically (the two most frequent people, often together). Both present and
  both looking good; bride's face visible. Bonus when they look at each other (best candids).
  Other occasions: `PeopleClusterer.mainPeople`; the database keeps them in `brideId`/`groomId`.
- Include solo bride and solo groom portraits. Reserve the best couple portrait as the cover.

### People coverage

- Close family is guessed with no tagging (`people/FamilyGuess.kt`): up to 40 relatives from a
  big folder; people photographed at several times of the day with the couple; someone seen only
  once (one trip to the stage) is never guessed as family.
- "Who's who" (`ui/PeopleScreen.kt`): one face at a time, one tap each (Mother, Aunt, Cousin...),
  side chips, Skip, Not family; the album is remade once at the end. "Family & friends" lists
  everyone with filters (bride's side, groom's side, not named); tap a face to fix it: merge,
  split, "find more of this person", this is the bride/groom.
- Must: at least 5 good photos with the couple. Important: at least 1-2. Sides balanced. Nobody
  over-represented. Elders and grandparents prioritised.
- If someone has only poor photos, use their best one and warn the bride.

### Story within each event (the main brain)

- Find big moments as dense bursts of shooting within the event and give each its best photos,
  spread across the day. Keep picks in time order. (Internal only: never shown as ritual names.)

### Variety and pacing

- Per event, mix couple close-up, couple full-length, family group, candid, and about 10% detail
  shots without faces (mehendi hands, jewellery, decor, food). No repeated pose back to back.
  Balance portrait and landscape.

### Context exceptions (what makes it human, not mechanical)

- No smile needed during rituals; serious faces are right during pheras.
- Looking away is fine in candids where people are interacting or laughing together.
- Tears (vidaai) are never a flaw.
- Judge dark or coloured stage lighting (sangeet, reception) against its own event, not daylight.

### Selection order

- People first, then moments, then variety, then quality.

### Screens (`ui/`)

- Home (album cards with cover photo, start new), new album (photo count, folders found, name,
  album size suggested at about 15% of the photos), progress (ring, time left, photos just
  checked, "you can lock your phone").
- Album (`AlbumScreen.kt`): cover edge to edge with the name on it, couple card (found
  automatically; asks "Is this the groom?" when unsure), family strip (top 10 faces, "See all N"),
  "Who's who?" card while faces are unnamed, photos edge to edge in a 3-column grid, floating
  "Send list to photographer". Album size and "what we left out" in one sheet.
- Photo view (`ViewerScreen.kt`): why it is in the album, look-alike photos to swap in with one
  tap, Remove (the next best photo fills the place at once, with Undo). Swaps are the couple's
  own choices (`PhotoEntity.userChoice`) and are kept when the album is re-made.
- Send: the exact list (event headings, folder/file names) shared as plain text.
- Look: photo first, white, near-black buttons, one saffron accent (`ui/Theme.kt`). Font: Plus Jakarta
  Sans (OFL) in `res/font/`; Devanagari names use the phone's own font.
  Mock-up: https://claude.ai/artifact/FhsiF3pfnqkssR1UJ5PdQz

### Tests

- Unit tests for every rule above (`app/src/test/`).
- Phone test (`.github/workflows/phone-test.yml`): runs on every push to a `claude/**` branch and
  before every main build. Builds the debug app (arm64 only; the emulator runs it through ARM
  translation, MediaPipe has no x86 build), starts an Android 16 emulator the size of a Pixel 7, runs
  `app/src/androidTest/` (real models on real photos, the real background worker, and a walk
  through every screen with screenshots), and force-pushes screenshots, the per-photo decision
  report (`analysis-report.txt`) and logs to the `phone-test-results` branch. Read them with
  `git fetch origin phone-test-results` and look at every screenshot like a tester before
  handing anything over. Android 11 emulators crash in MediaPipe (ARM translation), use 14.
- Test photos: `tools/test-photos/` + `.github/workflows/test-photos.yml` find and download freely
  licensed wedding photos from Wikimedia Commons on the build machine (this session cannot reach
  those sites) and put them on the `test-photos` branch with credits. Test data only, never in the
  app.

## Not in version 1

- Family voting, delivery tracker, payments, iOS.

## Status

Done: everything in the scope above, with unit tests for the planner, face rules, people grouping,
image measurements and scene rules, and the phone test on an Android 16 emulator. Not yet tried
with a real couple's wedding folder: thresholds (blink, smile, same-person similarity) may need
tuning. The build fails if the APK ever asks for the internet permission or a model file's
checksum changes, and main only publishes after the phone test passes.
Legal and Play Store notes: `docs/LEGAL_CHECK.md` (target API 36 is needed for new Play uploads).
How the picking works and planned upgrades: `docs/HOW_IT_PICKS.md`.

## GitHub minutes (owner's decision, 21 Oct 2026)

- The repo is private. GitHub Free gives 2,000 Actions minutes a month for private repos, reset
  on the 1st (October's were used up on 21 Oct, mostly by the photo search). Spending limit is
  $0, so nothing is ever charged; jobs simply stop starting when the minutes are gone.
- When minutes run out, Amol makes the repo public for a while (unlimited free minutes) and
  makes it private again when Claude says the runs are done. Claude cannot change visibility.
  Ask him at the start of each public window and tell him the moment it can go private.
- Budget per month: a phone test is about 15 billed minutes; a photo search (harvest) up to
  ~500. Run the photo search on this cloud machine instead when its network allows the photo
  sites, and keep phone tests to batches of app changes, so a month stays within 2,000.
- LICENSE says all rights reserved, for the public windows.
- The photo search can run on the cloud machine instead (`tools/harvest/local_run.sh`; the
  environment allows the photo sites). Amol allowed pushing its results to the harvest-data and
  harvest-sheets branches from here (21 Oct 2026).

## Build notes

- AGP 8.11.1, compile and target SDK 36, Kotlin 2.0.20 (compose compiler plugin), KSP
  2.0.20-1.0.25, Room 2.6.1, Gradle 8.14.3 installed by the workflow (no wrapper in the repo).
- `app/testkey.jks` is a test-only signing key so test builds install over each other. The Play
  Store release will use a separate private upload key.
