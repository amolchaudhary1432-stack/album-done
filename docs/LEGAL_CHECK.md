# Album Done: legal and Play Store check

Date: 15 October 2026. Prepared with Claude's help from public sources. **This is not legal advice.**
Before the Play Store launch, have an Indian technology lawyer review items 1–4, and a CA review taxes.

## Summary

**Proceed with conditions.** Testing and private use are fine today. Before a public launch on
Google Play there are three blockers and a few smaller tasks:

1. **Play blocker: target Android 16.** From 31 August 2026, new apps and updates on Google Play
   must target Android 16 (API 36). The app targets Android 14 (API 34), so it cannot be published
   until it is updated. An extension to 1 November 2026 can be requested.
2. **Face-matching model (SFace): training-data risk.** OpenCV publishes the SFace files under
   Apache 2.0, which allows commercial use. But the SFace paper says its models were trained on
   CASIA-WebFace, VGGFace2 and MS-Celeb-1M. Those face collections were scraped from the internet
   and released for research. Microsoft withdrew MS-Celeb-1M in 2019 over consent concerns. Whether
   a model trained on them can be sold commercially is an open legal question, and there is a
   reputation risk too. A lawyer's opinion is needed, or a different face-matching model.
3. **Privacy policy and in-app notices.** The app needs a public privacy policy page and a
   "Licences" screen that lists the open-source parts (Apache 2.0 and MIT require this).

## What the app does with personal data

- Reads the wedding photos the user chooses (folder access only, no gallery-wide permission).
- Finds faces, measures expressions, and makes a face fingerprint (128 numbers) for each face, to
  group photos of the same person. This covers the couple, family and guests, including children.
- Stores results (measurements, fingerprints, names the user types such as "Aai") only in the
  app's private storage on that phone.
- Sends nothing anywhere. The app has no internet permission, and the build checks this on every
  build. The only thing that leaves the phone is the file-name list, when the user taps Share.

## Applicable rules

| Rule | How it applies | What to do |
|---|---|---|
| Google Play target API policy | New apps and updates must target API 36 from 31 Aug 2026 | Update the app to target Android 16 (technical work, see actions) |
| Google Play Data safety | Data processed only on the user's device and never sent off it does not count as "collected" | Fill the form as "no data collected or shared", and keep it true |
| Google Play privacy policy | The Data safety section asks for a privacy policy link | Publish a short privacy policy page (draft below) |
| Google Play foreground services | Long background work needs a declared service type. From Android 15, "data sync" and "media processing" types get 6 hours per day | Switch to the "media processing" type when moving to Android 16, handle the 6-hour stop (the app already resumes), and fill Play Console's foreground-service declaration |
| India DPDP Act 2023 and Rules 2025 | The Act does not apply to data processed by an individual for a personal or domestic purpose (section 3(c)(i)). The bride sorting her own wedding photos fits that. Whether an app maker counts as a "data fiduciary" when nothing reaches them is not settled in the commentary | Get the lawyer to confirm the position. Keep the design that nothing leaves the phone. Most duties start in phases up to 2027 |
| IT Act SPDI Rules 2011 (biometrics) | Apply to companies that collect or hold sensitive data such as biometrics. Album Done collects none | No action while nothing leaves the phone |
| Copyright Act 1957, section 17(b) | A paid photograph taken at someone's request is first owned by that person (the couple) unless the contract says otherwise. Either way, the app only reads the files on the owner's phone to help them choose | No issue. Don't copy, edit or upload photographers' photos without permission if such features are added later |
| Consumer Protection Act 2019 and misleading-ads guidelines | Store listing and ads must not over-claim | Avoid unproven claims such as "human-level". Say "picks like you would, you make the final call" and back claims with tests |
| Open-source licences | Apache 2.0 (MediaPipe, AndroidX, Room, WorkManager, Coil, SigLIP if added) and MIT (ONNX Runtime) | Ship the licence texts and notices in a "Licences" screen |

## Model and library licences

| Part | Licence | Training data | Risk |
|---|---|---|---|
| MediaPipe Tasks library | Apache 2.0 | n/a | Low |
| MediaPipe Face Landmarker models (face detector, face mesh, blendshapes) | Published by Google for use with MediaPipe; the model cards list the licence (believed Apache 2.0, not re-verified here because Google's pages could not be opened from this session) | Google's own | Low. Confirm from the model cards |
| EfficientDet-Lite0 object detector | Apache 2.0 (TensorFlow) | COCO (annotations CC BY 4.0, images from Flickr) | Low. Widely used commercially |
| SFace face-matching model (OpenCV Zoo) | Files labelled Apache 2.0 by OpenCV | CASIA-WebFace, VGGFace2, MS-Celeb-1M (research data; MS-Celeb-1M withdrawn) | **Medium–high.** Needs lawyer review or replacement |
| ONNX Runtime | MIT | n/a | Low |
| Planned: Google SigLIP 2 (scene understanding) | Apache 2.0 | Google's WebLI | Low–medium. Confirm the model card before adding |
| Avoid: Apple MobileCLIP / MobileCLIP2 | Apple's research licence | n/a | Not for a paid app |
| Test photos in `app/src/androidTest` | Public domain (White House photos) | n/a | None. Test only, not in the app |

### Options for face matching

1. Keep SFace for testing now, and get a lawyer's opinion before launch.
2. License a commercial face-matching model whose vendor grants commercial rights and warrants its
   training data.
3. Launch without automatic people grouping: the bride tags people herself. Safer, but more work
   for her and weaker family coverage.

My recommendation is 1 now and 2 if the lawyer is uncomfortable. People grouping is central to
family coverage, which is the main reason families argue over albums.

## Requirements before launch

| # | Requirement | Status | Action |
|---|---|---|---|
| 1 | App targets Android 16 (API 36) | Not met (API 34) | Upgrade build tools, compile and target SDK 36, retest |
| 2 | Foreground service type suits the work | Partly ("data sync") | Use "media processing" on Android 15+, handle the 6-hour limit, Play declaration |
| 3 | Privacy policy page | Not met | Publish the draft below on a public page |
| 4 | Data safety form | Not started | Declare no data collected or shared |
| 5 | In-app open-source licences screen | Not met | Add a "Licences" screen |
| 6 | Face-matching model cleared for commercial use | Unknown | Lawyer opinion, or a commercial model |
| 7 | In-app explanation of face grouping | Partly | Onboarding line: "We find faces on this phone to put your family in the album. Nothing is uploaded." |
| 8 | Delete everything | Met | Deleting an album removes its faces, people and thumbnails. Uninstalling removes all |
| 9 | Release signing key | Not met | Use Play App Signing with a private upload key (the test key stays test-only) |
| 10 | App name check | Unknown | Search the trademark registry for "Album Done" before branding |
| 11 | Store listing claims | Not written | Keep claims provable |
| 12 | Tax on app income | Unknown | Ask a CA about GST and income tax for Play earnings |

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Face-matching model's training data | Medium–high | Lawyer opinion, or a commercial model |
| Missing Play deadline (API 36) | High (blocks publishing) | Upgrade now, or request the 1 Nov extension |
| Privacy complaint about scanning guests' faces | Low–medium | Nothing leaves the phone; clear explanation; easy delete |
| Over-claiming in marketing | Medium | Test against real couples' albums before making claims |
| Open-source notice missing | Low (easy to fix) | Licences screen |

## Draft privacy policy (plain words)

> **Album Done privacy policy**
>
> Album Done helps you choose photos for your wedding album. Everything happens on your phone.
>
> - **Your photos never leave your phone.** The app has no internet access and cannot upload anything.
> - **What the app reads:** only the folder of photos you choose.
> - **What the app stores, on your phone only:** small preview images, photo measurements (sharpness,
>   light, expressions), and face fingerprints used to group photos of the same person, plus any names
>   you type. This stays in the app's private storage.
> - **What leaves your phone:** only the list of file names, and only when you tap "Send list" and pick
>   an app to send it with.
> - **We collect nothing.** No accounts, no analytics, no ads, no tracking.
> - **Deleting:** delete an album in the app to remove all its data, or uninstall the app to remove everything.
> - **Contact:** [your email]

## Approvals needed

| Who | Why | Status |
|---|---|---|
| Indian technology lawyer | SFace training data, DPDP position, privacy policy wording | Pending |
| Chartered accountant | GST and income tax on app earnings | Pending |

## Sources

- Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Android 15 foreground service time limits: https://developer.android.com/develop/background-work/services/fg-service-timeout
- Google Play Data safety (on-device processing is not "collection"): https://support.google.com/googleplay/android-developer/answer/10787469
- SFace paper and training datasets: https://github.com/zhongyy/SFace
- OpenCV Zoo SFace licence: https://github.com/opencv/opencv_zoo/blob/main/models/face_recognition_sface/README.md
- MS-Celeb-1M withdrawal: https://www.vice.com/en/article/microsoft-deleted-a-facial-recognition-database-but-its-not-dead/
- DPDP Act section 3: https://dpdpa.com/dpdpa2023/chapter-1/section3.html
- Copyright Act section 17(b) and wedding photos: https://blog.ipleaders.in/copyright-registration-of-photographs/ and https://www.theippress.com/?p=6871
- Apple MobileCLIP2 research licence: https://huggingface.co/apple/MobileCLIP2-S3/commit/7793fd3dc61d30c444fcfbf99f758e86974e8556
- SigLIP 2 licence listing: https://docs.voxel51.com/model_zoo/models/google_siglip2_base_patch16_224.html
