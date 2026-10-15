# Third-party models and libraries

Everything runs on the phone. Nothing is downloaded at run time.

| What | Used for | Licence | Source |
|---|---|---|---|
| MediaPipe Face Landmarker model (`face_landmarker.task`, float16 v1) | Face landmarks, blendshapes, head angle | Apache 2.0 | storage.googleapis.com/mediapipe-models/face_landmarker |
| MediaPipe EfficientDet-Lite0 object detector (`efficientdet_lite0.tflite`, int8 v1) | People boxes, phones | Apache 2.0 | storage.googleapis.com/mediapipe-models/object_detector |
| OpenCV Zoo YuNet (`face_detection_yunet_2026may.onnx`) | Finds every face, including small, side-on and downcast ones | MIT (Shiqi Yu) | github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet |
| OpenCV Zoo SFace (`face_recognition_sface_2021dec.onnx`) | Face fingerprints to recognise people | Apache 2.0 | github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface |
| MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) | Runs the two MediaPipe models | Apache 2.0 | google.github.io/mediapipe |
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) | Runs YuNet and SFace | MIT | onnxruntime.ai |
| Mukta font (Girish Dalvi, Ek Type), regular, medium, semibold | Body text, supports Devanagari | SIL Open Font License 1.1 | fonts.google.com/specimen/Mukta |
| Marcellus font (Brian J. Bonislawsky, Astigmatic) | Headings | SIL Open Font License 1.1 | fonts.google.com/specimen/Marcellus |

The two fonts carry their copyright and licence inside the font files. The OFL allows bundling them in
an app that is sold, as long as the fonts themselves are not sold on their own.

Checksums of the bundled model files are pinned in `app/models.sha256`.
