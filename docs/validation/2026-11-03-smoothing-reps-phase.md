# Validation: smoothing, rep counting, phase UI

Date: 2026-11-03
Status: complete (APK built; device install skipped because the target phone was unreachable)

## Scope

1. Smoother per-frame output without flattening peak metrics.
2. Accept real sets where reps have no top lockout, while rejecting noise and tiny bumps.
3. Make phase editing explicit and read-only by default.
4. Make joint-angle analysis an explicit, default-on import option; never hide angles solely for
   occlusion.

## Commits

- `f011114` feat(analysis): smooth joint angles and raise mean filter window
- `9b1a0c0` feat(phases): count lockout-optional reps and merge noise
- `68aa8fe` feat(report): make phase timeline read-only with manual correction
- `c7da989` test(phases): cover deadlift rep without lockout
- `05148d7` feat(analysis): make joint-angle analysis optional and occlusion-tolerant
- `3e60bda` feat(ui): add analyze-joint-angles switch and disabled state
- `4142ba3` perf(analysis): defer pose model creation until needed

The bar-path overlay now consumes smoothed `motionSamples`; the overlay files were already in the
working branch (`e0d0ce6`, `e332cc2`) and were updated in place to use
`OverlayTimeline.trailSamples` / `nearestMotionSample` plus a metre-to-pixel projection from
`Calibration`. Raw `PlateObservation` data remains on `AnalysisResult` for calibration/debug.

## Smoothing

- `AnalysisPipeline.MEAN_FILTER_WINDOW` changed from 7 to 9. Peak window stays 5, polynomial order
  stays 2.
- New `JointAngleSmoother` (pure Kotlin/JVM) applies a median-3 filter to every `FrameAngles`
  series after pose extraction. Window is configurable (odd values only).
- Null joint angles stay null: the current frame must be non-null before a median is returned.
  Missing neighbours never invent a value.
- Smoothed angles are used for `AnalysisResult.frameAngles`, `frameAnglesByRep`, the report, and the
  burned-in overlay.
- Bar trail and the current-position marker use smoothed `motionSamples` projected back to pixels.
  Raw observations are no longer used for rendering.

## Rep counting

`PhaseConfig` now has:

| Field | Default |
|---|---|
| `vEps` | 0.05 |
| `minPhaseSec` | 0.12 |
| `lockoutSec` | 0.10 |
| `minRepSec` | 0.45 |
| `minConcentricSec` | 0.15 |
| `minRomM` | 0.10 |
| `mergePauseSec` | 0.25 |

Behaviour:

- Squat/bench rep = eccentric + concentric. Lockout optional.
- Deadlift rep = concentric + optional lockout; eccentric descent separates reps.
- Short pauses (<= `mergePauseSec`) between same-direction runs are merged before lockout
  detection, so tracking jitter cannot split a rep.
- Lockouts >= 0.10 s survive generic short-run merging and are capped at 0.10 s.
- Candidates shorter than `minRepSec`, with less than `minConcentricSec` of concentric time, or with
  less than `minRomM` of vertical travel are rejected.
- Missing lockout is reported as the `NO_LOCKOUT` warning, never as a rejected rep. Rejected
  candidates are reported as `INCOMPLETE_REP`.
- Manual phase boundaries still mark adjacent phases `manual = true` through the existing
  recompute/persist path.

## Phase UI

- `PhaseTimeline` is read-only by default (`editable = false`), with no pointer gesture attached.
- Report adds `手动校正` / `Manual correction`; only then are boundaries draggable and handles
  visible. `完成` / `Done` exits edit mode.
- Explanatory text added:
  “相位 = 离心 → 停顿 → 向心 → 锁定。自动分割，默认只读；需要修正时点手动校正。”
- Colour legend added for eccentric, pause, concentric and lockout.
- All new copy is in `values/strings.xml` and `values-en/strings.xml`.

## Joint-angle switch

- `AnalysisRequest.analyzePose` defaults to `true`.
- Import screen adds `分析关节角` / `Analyze joint angles`, default ON.
- When OFF:
  - `poseEstimator.reset()` and every `poseEstimator.estimate()` call are skipped.
  - `DeferredPoseEstimator` prevents MediaPipe model construction until the first estimate, so an
    OFF run never loads the pose model.
  - `poseFrames` and `frameAngles` stay empty; `frameAnglesByRep` is empty.
  - No `POSE_*` warnings are emitted.
  - The pipeline drops `ANGLES_UNAVAILABLE_OCCLUSION` so a disabled analysis cannot look like a
    failed pose attempt.
  - Report shows `关节角分析已关闭` / `Joint-angle analysis is off` with a re-analysis hint, and
    does not render the per-rep joint-angle section.
- When ON:
  - Occlusion no longer suppresses angles. `anglesAvailable` follows the pose result, not
    `occlusionPct`.
  - `frameAnglesByRep` is built for every rep that has pose samples, regardless of plate occlusion.
  - Available angles keep their per-joint confidence values.
  - `HIGH_OCCLUSION` remains; `POSE_LOW_CONFIDENCE` is added when the mean pose confidence is below
    0.6. `ANGLES_UNAVAILABLE_OCCLUSION` is removed because it no longer describes the section.
  - `POSE_UNAVAILABLE` / `POSE_PARTIAL` remain as explicit quality warnings.
  - Only individual angles with missing or low-visibility landmarks are null.

Tests added to `AnalysisPipelineTest`:

- `analyzePose false` never calls the pose estimator, returns no angles, and emits no `POSE_*`
  warnings even with a low-confidence plate track.
- Low-confidence pose plus high plate occlusion keeps `frameAnglesByRep` populated, keeps
  `anglesAvailable`, and emits `HIGH_OCCLUSION` + `POSE_LOW_CONFIDENCE` without
  `ANGLES_UNAVAILABLE_OCCLUSION`.

## Validation commands

Environment used because the shell had no default JDK/SDK:

```bash
export JAVA_HOME=/home/czq/toolchains/jdk17
export ANDROID_HOME=/home/czq/toolchains/android-sdk
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Test totals from JUnit XML:

- core: 68 tests, 0 failures, 0 errors, 0 skipped
- app debug unit tests: 59 tests, 0 failures, 0 errors, 0 skipped

APK:

- `app/build/outputs/apk/debug/app-debug.apk`
- 224,895,452 bytes

## Install

Target `192.168.57.102:40565` was not connected. `adb connect` returned `Connection refused`; the
only listed device was `emulator-5562` in `offline` state. Install was therefore skipped.

Command to run when the phone returns:

```bash
adb -s 192.168.57.102:40565 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Concerns

- `NO_LOCKOUT` is a set-level quality hint emitted whenever at least one valid rep has no lockout.
  Continuous touch-and-go sets will therefore show it; it does not affect rep count.
- The manual-correction toggle applies to every rep in the report, not to one rep at a time.
- `minRomM` uses total vertical range inside a rep. A very shallow but noisy set can be rejected;
  the default 0.10 m matches the requested threshold.
- Existing metric, null and quality tests were not weakened; phase-specific expectations that
  required a lockout for every rep were updated to the new contract.
- `POSE_LOW_CONFIDENCE` uses mean per-frame minimum joint visibility < 0.6. Occluded joints often
  drag that value down, so the warning can appear even when the remaining angles are usable.
- With joint-angle analysis OFF, `high occlusion` plate warnings (for bar tracking) can still
  appear; only pose/angle-specific warnings are suppressed.
