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

1. **Choose folder.** Storage Access Framework folder picker (`OpenDocumentTree`), persistable
   read permission. No gallery-wide permission (Google Play only allows that for gallery apps).
2. **Scan.** `scan/FolderScanner.kt` lists photos recursively. Sub-folder names are the events
   (Haldi, Wedding, Reception).
3. **Analyse in background.** `work/AnalyzeWorker.kt`: WorkManager foreground job with a
   progress notification, keeps running with the screen off, saves each photo's result to Room
   immediately so a stopped run resumes. One lane on low-RAM or under-6-core phones, two otherwise.
4. **Per photo** (`analysis/PhotoAnalyzer.kt`, `analysis/ImageMetrics.kt`): decode at about 1280px
   long side (never full size), EXIF time and rotation, sharpness (Laplacian variance), brightness,
   dark/blown-out share, 64-bit difference hash, ML Kit on-device face detection with smile and
   eyes-open probability, 360px thumbnail in app storage.
5. **Plan the album** (`plan/AlbumPlanner.kt`, pure Kotlin, unit-tested):
   - Blurry: sharpness below 0.3 x the median of this wedding.
   - Too dark: brightness < 40 or dark share > 0.65. Too bright: brightness > 220 or blown share > 0.5.
   - Eyes closed: any clearly visible face (>= 6% of photo width) with average eye-open < 0.3.
   - Bursts: same event, consecutive by capture time, <= 3 s apart (or adjacent by file name when
     time is unknown) and hash distance <= 12. Keep the best-scoring photo; the rest are duplicates.
   - Events: sub-folder name; if everything is in one folder, split where capture times jump by
     more than 90 minutes.
   - Score: sharpness relative to median (capped), smile, faces present, good exposure.
   - Shortlist of N: exclude photos with any issue, give each event a share in proportion to its
     usable photos (largest remainder, at least 1 each), and within an event spread picks across
     5-minute time slots (round robin, best first) so one moment does not dominate. Return in
     capture order. If not enough clean photos, fill with the best of the rest.
   - Photos the user removed are never picked again; "Replace removed" refills with the next best.
6. **Screens** (`ui/`): Home (list of weddings, "Start new album"), folder pick then scan count,
   progress (count, speed, time left, "you can lock your phone"), results (usable, blurry, dark or
   bright, eyes closed, extra burst shots, per-event counts, time taken), album size input, shortlist
   grid by event (tap to remove, "Replace removed"), "Send list to photographer" via the Android
   share sheet (plain text: event headings and file names).
7. Ask for notification permission on Android 13+ before starting analysis.

## Not in version 1

- Grouping by person and family coverage. Needs a face-matching model whose licence allows
  commercial use. Many free models are research-only. Present options to Amol before choosing.
- Family voting, delivery tracker, payments, iOS.

## Status

Done: build setup, manifest, Room data layer, folder scanner, photo analyzer, background worker,
`plan/AlbumPlanner.kt` + tests, `ImageMetrics` tests, `ui/` (MainActivity, view model, screens, theme).
The build fails if the APK ever asks for the internet permission.

## Build notes

- AGP 8.5.2, Kotlin 2.0.20 (compose compiler plugin), KSP 2.0.20-1.0.25, Room 2.6.1, Gradle 8.9
  installed by the workflow (no wrapper in the repo).
- `app/testkey.jks` is a test-only signing key so test builds install over each other. The Play
  Store release will use a separate private upload key.
