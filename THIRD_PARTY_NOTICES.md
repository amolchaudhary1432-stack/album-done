# Third-party models and libraries

Everything runs on the phone. Nothing is downloaded at run time.

| What | Used for | Licence | Source |
|---|---|---|---|
| MediaPipe Face Landmarker model (`face_landmarker.task`, float16 v1) | Face landmarks, blendshapes, head angle | Apache 2.0 | storage.googleapis.com/mediapipe-models/face_landmarker |
| MediaPipe EfficientDet-Lite0 object detector (`efficientdet_lite0.tflite`, int8 v1) | People boxes, phones | Apache 2.0 | storage.googleapis.com/mediapipe-models/object_detector |
| OpenCV Zoo SFace (`face_recognition_sface_2021dec.onnx`) | Face fingerprints to recognise people | Apache 2.0 | github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface |
| MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) | Runs the two MediaPipe models | Apache 2.0 | google.github.io/mediapipe |
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) | Runs SFace | MIT | onnxruntime.ai |

Checksums of the bundled model files are pinned in `app/models.sha256`.
