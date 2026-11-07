# Third-party models and libraries

Everything runs on the phone. Nothing is downloaded at run time.

| What | Used for | Licence | Source |
|---|---|---|---|
| MediaPipe Face Landmarker model (`face_landmarker.task`, float16 v1) | Face landmarks, blendshapes, head angle | Apache 2.0 | storage.googleapis.com/mediapipe-models/face_landmarker |
| MediaPipe EfficientDet-Lite0 object detector (`efficientdet_lite0.tflite`, int8 v1) | People boxes, phones | Apache 2.0 | storage.googleapis.com/mediapipe-models/object_detector |
| OpenCV Zoo YuNet (`face_detection_yunet_2026may.onnx`) | Finds every face, including small, side-on and downcast ones | MIT (Shiqi Yu) | github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet |
| OpenCV Zoo SFace (`face_recognition_sface_2021dec.onnx`) | Face fingerprints to recognise people | Apache 2.0 | github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface |
| Google SigLIP 2 image model, B/32 at 256 px (`siglip2_vision_int8.onnx`, int8 ONNX by onnx-community, revision 7efccb8) | What each photo shows, for the album brain | Apache 2.0 | huggingface.co/google/siglip2-base-patch32-256, huggingface.co/onnx-community/siglip2-base-patch32-256-ONNX |
| Album Mind numbers (`album_mind.bin`) | The album brain's learned judgement (made by tools/reasoner/mind_v2.py) | Our own | this repository |
| MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) | Runs the two MediaPipe models | Apache 2.0 | google.github.io/mediapipe |
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) | Runs YuNet, SFace and SigLIP 2 | MIT | onnxruntime.ai |
| Plus Jakarta Sans font (Tokotype), regular to extra bold | All text (Devanagari falls back to the phone's own font) | SIL Open Font License 1.1 | github.com/tokotype/PlusJakartaSans |

The two fonts carry their copyright and licence inside the font files. The OFL allows bundling them in
an app that is sold, as long as the fonts themselves are not sold on their own.

Checksums of the bundled model files are pinned in `app/models.sha256`. The SigLIP 2 file (96 MB) is
too big for git: `tools/fetch_models.sh` downloads it at a pinned revision when the app is built.
