# Album Done

Android app (Kotlin, Jetpack Compose) that helps Indian couples pick their wedding album photos.
Everything runs on the phone. Photos are never uploaded. The app has no internet permission, and
that must stay true.

## Who it is for

- **Buyer:** the couple, usually the bride. She has the photographer's folder of 1,000 to 10,000
  photos (pendrive copied to phone, or a Drive folder saved offline) and family pressure to finish
  the album selection.
- **Promise:** "Your wedding album shortlist in a few taps, without uploading a single photo."

## How the owner works

- Amol is the owner. He is not an Android developer and does not install Android Studio.
- Builds happen only on GitHub Actions (`.github/workflows/build.yml`). Every push to `main`
  runs unit tests, builds a signed APK and publishes it as the `test-build` GitHub release.
  Amol installs that APK on his phone from the release page.
- After pushing, check the run with `gh run list` / `gh run view --log-failed` and fix until green.
  Do not hand over a build that has not passed.
- Explain things to Amol in plain words, no jargon. Keep replies short.

## Version 1 scope (build this, nothing more)

The goal is human-level album selection, not just discarding bad photos. The selection logic is
the core of the product: most of the effort and testing goes there (`plan/AlbumPlanner.kt`,
pure Kotlin, unit-tested).

### Technology (all on the phone, no internet permission, Apache 2.0 or similar licences only)

- Face detection + expression: MediaPipe Face Landmarker (several faces, blendshapes on). Blendshapes
  give eye blink (eyeBlinkLeft/Right), gaze (eyeLookIn/Out/Up/Down), smile (mouthSmileLeft/Right),
  mouth open (jawOpen); head angle comes from the face transform. (Replaces ML Kit.)
- Identity: OpenCV Zoo SFace face fingerprints (run with ONNX Runtime), grouped into people.
- MediaPipe object detector (EfficientDet-Lite0) for people boxes and phones.
- Model files are bundled in the APK under `app/src/main/assets/models/`; their checksums are pinned
  in `app/models.sha256` and checked by the workflow. Licences: `THIRD_PARTY_NOTICES.md`.

### Flow (unchanged from before)

Choose folder (`OpenDocumentTree`, persistable read permission, no gallery-wide permission) → scan
(`scan/FolderScanner.kt`) → analyse in background (`work/AnalyzeWorker.kt`: WorkManager foreground
job, works with the screen locked, saves each photo's result immediately, one lane on low-RAM or
under-6-core phones, two otherwise; photos decoded at about 1280px, never full size) → find people
→ "Tag your family" → results → album size → shortlist (remove, "Replace removed") → "Send list to
photographer" (share sheet, plain text). Ask for notification permission on Android 13+ first.

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
- Include solo bride and solo groom portraits. Reserve the best couple portrait as the cover.

### People coverage

- After analysis, a "Tag your family" screen with the top 30-40 people: Must / Important / Skip,
  bride's side or groom's side, elder, merge and split wrong groups, "find more of this person".
- Must: at least 5 good photos with the couple. Important: at least 1-2. Sides balanced. Nobody
  over-represented. Elders and grandparents prioritised.
- If someone has only poor photos, use their best one and warn the bride.

### Story within each event (the main brain)

- Find key moments as dense bursts of shooting within the event (varmala, pheras, sindoor, vidaai)
  and give each its best photos. Aim for arrival or decor, getting ready, rituals, emotional
  moments, close. Keep picks in time order.

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

- Home, new album (scan count), progress, "Tag your family", results (counts per problem, per
  event, coverage per tagged person e.g. "Aai: 12 photos" with warnings below their minimum),
  album size, shortlist grid by event (tap a photo to see why it was picked, take out, "Replace
  removed", cover marked), "Send list to photographer".

### Tests

- Unit tests for every rule above (`app/src/test/`).

## Not in version 1

- Family voting, delivery tracker, payments, iOS.

## Status

Done: everything in the scope above, with unit tests for the planner, face rules, people grouping,
image measurements and scene rules. Not yet tried on a real phone with a real wedding folder:
thresholds (blink, smile, same-person similarity) may need tuning after Amol's first test.
The build fails if the APK ever asks for the internet permission or a model file's checksum changes.

## Build notes

- AGP 8.5.2, Kotlin 2.0.20 (compose compiler plugin), KSP 2.0.20-1.0.25, Room 2.6.1, Gradle 8.9
  installed by the workflow (no wrapper in the repo).
- `app/testkey.jks` is a test-only signing key so test builds install over each other. The Play
  Store release will use a separate private upload key.
