# pcreplay — run the App's analysis maths on a PC

Two entry points, both using the App's own code or model, never a reimplementation:

## 1. Kotlin (always available, no extra install)

Reads the observations the App exports (`cache/debug-observations.csv`) and runs
`core` itself — smoothing, calibration, kinematics, phase segmentation.

```bash
./gradlew :tools:pcreplay:installDist
tools/pcreplay/build/install/pcreplay/bin/pcreplay \
  --obs /tmp/debug-observations.csv --plate-m 0.45 --angle 45 --lift DEADLIFT
```

## 2. Python (full pipeline, needs a TFLite runtime)

Runs the App's `plate_yolo.tflite` plus the same letterbox / NMS / identity
association / core maths, straight on a video file. Useful for checking how an
algorithm change behaves across many clips without touching the phone.

```bash
pip install ai-edge-litert opencv-python-headless   # runtime only, no TensorFlow
python3 tools/pcreplay/python/app_replay.py clip.mp4 0.45 45
```

`tflite-runtime` (2.14) cannot load the shipped model; `ai-edge-litert` can.

## Known limits

- The PC decodes whatever file it is given. A downscaled copy of a clip is not
  the same input the phone analysed: coordinates and plate diameter differ, so
  only compare numbers against the phone when both used the same file.
- Pose estimation (MediaPipe) is Android-only and not replayed here.
