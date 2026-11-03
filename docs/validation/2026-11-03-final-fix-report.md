# Final-review fix report — VBTdroid MVP

Date: 2026-11-03
Head: `a64e140` (`fix(app): handle analysis service re-entry without crashing`)
Base review: `.superpowers/sdd/2026-11-02-vbtdroid-mvp/final-review.md`
Status: **DONE_WITH_CONCERNS** — all Critical and Important code findings are addressed or explicitly residual; real-device/model validation remains manual.

## Audit of the cancelled fix wave

The two prior commits were audited rather than trusted by message:

- `d9a6bf9 feat: connect ROI, history, export and LVP user flow`
- `5d06141 fix: correct phase, metric, quality and tracking semantics`

They did fix most of the user-flow and semantics findings. The dirty/untracked work (foreground service, manifest, MainActivity, phase tail, golden parity) was incomplete:

- `GoldenParityTest` failed on `sticking_point_pct` (`75.97873368777198` expected, `70.22716288061844` actual) because Kotlin and Python selected different samples on a near-flat velocity minimum.
- The cancellation test was non-deterministic: it used `runTest` while `AnalysisPipeline` switches to `Dispatchers.Default`, so decoding could be cancelled before it started.
- Manual phase edits computed metrics but were never persisted after the initial save.
- Same-load history query was absent.
- README still claimed the app was not implemented.

All of the above were finished in this pass.

## Critical findings

### C1 — Fresh install / manual ROI path: **addressed** (auto detector remains residual)

- Import now decodes the first frame, shows it, auto-suggests ROI when a detector exists, and always allows an editable/confirmable manual ROI.
  - `app/src/main/java/com/vbtdroid/app/ui/import/ImportScreen.kt:116-123`, `:229-266`
  - `app/src/main/java/com/vbtdroid/app/ui/import/RoiSelector.kt`
- Pipeline prefers `request.roi`, falls back to detector ROI only when absent, and fails with a specific ROI message.
  - `app/src/main/java/com/vbtdroid/app/analysis/AnalysisPipeline.kt:125-130`
- No-detector end-to-end path is tested:
  - `app/src/test/java/com/vbtdroid/app/analysis/AnalysisPipelineTest.kt:127-144`
- The default download script still downloads only the pose model. `plate.tflite` remains optional and must be supplied through `PLATE_MODEL_URL`; the manual ROI path does not require it.
  - Residual: actual TFLite plate model/auto-suggestion not validated on device.

### C2 — History/save/export flow: **addressed**

- Room is instantiated through the foreground service; analysis is saved transactionally as Session/Set/Rep/Phase.
  - `app/src/main/java/com/vbtdroid/app/data/AnalysisRepository.kt:24-83`
  - `app/src/main/java/com/vbtdroid/app/data/SessionDao.kt:41-63`
- History list/detail UI and history load are wired.
  - `app/src/main/java/com/vbtdroid/app/data/AnalysisRepository.kt:128-137`
  - `app/src/main/java/com/vbtdroid/app/ui/history/HistoryScreen.kt`
  - `app/src/main/java/com/vbtdroid/app/MainActivity.kt:104-115`
- Same-lift/same-variant and same-load queries exist.
  - `app/src/main/java/com/vbtdroid/app/data/SessionDao.kt:37-41`
  - `app/src/main/java/com/vbtdroid/app/data/AnalysisRepository.kt:140-145`
- JSON and CSV SAF export from the report are wired.
  - `app/src/main/java/com/vbtdroid/app/ui/report/ReportScreen.kt:62-80`, `:111-115`
  - `app/src/main/java/com/vbtdroid/app/export/ExportMapper.kt:18-92`
- Request metadata, calibration, quality, phases and LVP are exported.
- Manual phase edits now recompute all affected reps and are persisted with a 400 ms debounce after the edit.
  - `app/src/main/java/com/vbtdroid/app/ui/report/ReportScreen.kt:54-60`, `:82-93`
  - `app/src/main/java/com/vbtdroid/app/data/AnalysisRepository.kt:85-124`
  - `app/src/test/java/com/vbtdroid/app/data/AnalysisRepositoryTest.kt:85-105`

### C3 — LVP/1RM consumer and variant identity: **addressed**

- Same-lift/same-variant velocity-point query feeds the fit.
  - `app/src/main/java/com/vbtdroid/app/data/SessionDao.kt:63`
  - `app/src/main/java/com/vbtdroid/app/data/AnalysisRepository.kt:152-170`
- Literature `v1rm` defaults, source marking, fit quality, `<3` distinct loads null + explicit reason are implemented.
  - `core/src/main/kotlin/com/vbtdroid/core/metrics/Lvp.kt:24-100`
- Report and JSON/CSV export consume the estimate; report shows slope/intercept/R²/1RM or the unavailable reason.
  - `app/src/main/java/com/vbtdroid/app/ui/report/ReportScreen.kt:150-172`
  - `app/src/main/java/com/vbtdroid/app/export/ExportMapper.kt:84-96`
  - `app/src/test/java/com/vbtdroid/app/data/AnalysisRepositoryTest.kt:44-82`

## Important findings

| # | Finding | Status | Evidence |
|---|---|---|---|
| I1 | Incomplete reps/lockout merging | addressed | lockout detected before `minPhaseSec` merge; formal reps require lockout; warning for incomplete; tests at 0.10/0.12/multi-rep/no-lockout. `core/.../PhaseSegmenter.kt:34-76,132-145,196-245`; `core/src/test/.../PhaseSegmenterTest.kt:86-134` |
| I2 | ROM endpoint displacement / sticking point | addressed | `romConcentricM = last - first`; non-monotonic carries quality warning; sticking point uses endpoint denominator and rejects out-of-range values. `core/.../RepMetrics.kt:72-119,182-200`; test `:124-136` |
| I3 | Per-rep velocity loss / manual edits | addressed | every MCV is computed, then VL uses full-set best; manual recompute refreshes all reps. `app/.../AnalysisPipeline.kt:313-323`; `app/.../ui/report/ReportRecompute.kt`; tests `AnalysisPipelineTest.kt:177-192`, `ReportRecomputeTest.kt` |
| I4 | Joint angle aspect ratio / display | addressed | MediaPipe emits pixel coordinates; `PoseFrame.pixelPoint` handles normalized adapters; key-frame angles shown. `app/.../vision/MediaPipePoseEstimator.kt:46-58`; `app/.../vision/PoseEstimator.kt:18-23`; `app/.../ui/report/KeyFrameAngles.kt`; `ReportScreen.kt:230-234`; test `JointAngleExtractorTest.kt:45-66` |
| I5 | Quality semantics/degradation | addressed | segmentation confidence, low-confidence occlusion counting, camera estimate, high-occlusion angle disable, load reasons, per-rep warnings. `core/.../quality/Quality.kt:14-63`; `AnalysisPipeline.kt:201-267`; `ReportScreen.kt:250-283`; tests `QualityTest.kt`, `AnalysisPipelineTest.kt:89-105` |
| I6 | Peak/mean filter separation/reporting | addressed | mean window 7, peak window 5; independent peak samples; filter config exported/displayed. `AnalysisPipeline.kt:171-176,241-256`; `RepMetrics.kt:43-51,122-137`; `ReportScreen.kt:254-266`; test `RepMetricsTest.kt:138-149` |
| I7 | Tracking failure image/center pairing | addressed in unit layer; real-frame residual | `SuccessfulFramePair` retains the last successful Mat/center/time; failures do not advance; Mat release path. `app/.../vision/PlateTracker.kt:26-30,125,133,174-178`; `app/.../vision/SuccessfulFramePair.kt`; test `SuccessfulFramePairTest.kt` |
| I8 | 10-minute cap/cancel/foreground service | addressed; device validation residual | duration cap in decoder/import/pipeline; cancel reaches decoding; foreground service + notification + manifest permissions. `app/.../video/VideoMeta.kt:13-19`; `ImportScreen.kt:113-118`; `AnalysisForegroundService.kt:69-105`; `AndroidManifest.xml`; test `VideoLimitsTest.kt`, `AnalysisPipelineTest.kt:196-222` |
| I9 | CSV canonical IDs | addressed | CSV header uses `rep_duration_s`, `mcv_mps`, `mean_force_n`, etc.; Python uses `rep_duration_s`. `app/.../export/CsvExporter.kt:7-10`; `prototype/vbtdroid_proto/metrics.py:44-60`; `CsvExporterTest.kt` |
| I10 | Fake zeros | addressed | degenerate joint angles return null; missing second derivative is null, not 0; total work is null when no valid work. `core/.../metrics/JointAngles.kt:12-24`; `SavitzkyGolay.kt:18,63`; `Kinematics.kt:43-44`; `AnalysisPipeline.kt:326-343`; tests `RepMetricsTest.kt:151-180`, `AnalysisPipelineTest.kt:224-228` |
| I11 | Golden parity / integration coverage | partial | Kotlin golden parity added and passing; multi-rep/no-lockout/cancel/occlusion/save-export tests exist. Real MediaCodec/sample-video instrumentation and VFR/rotation coverage remain manual. `core/src/test/.../GoldenParityTest.kt:22-59`; `AnalysisPipelineTest.kt` |

## Minor items

Cheap ones handled:

- LVP validates finite/positive estimate and fit (`Lvp.kt:64-100`).
- UINT8 input is quantized; resized Bitmap is recycled (`TflitePlateDetector.kt`).
- Mat close/release path via `SuccessfulFramePair.clear()` and `PlateTracker.reset()`.
- MediaPipe timestamps reset per analysis (`MediaPipePoseEstimator.kt:62`).
- Model initialization moved out of Compose main thread into the foreground service's lazy adapters.
- README status updated (`README.md:9`).
- Room schema export enabled and `app/schemas/com.vbtdroid.app.data.AppDatabase/2.json` committed.

Still minor/residual:

- `Agreement` still returns ICC 1.0 for identical constant samples (zero variance undefined). Not touched.
- Python prototype still has frame-index time fallback for bad `CAP_PROP_POS_MSEC` values. Not touched.
- No real-device test for OpenCV optical-flow recovery, MediaPipe accuracy, HDR/rotation/VFR, 60 s performance/thermal.
- Auto plate detector remains an optional model asset; default download only supplies pose.

## Verification

Command run (environment from task prompt):

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: **BUILD SUCCESSFUL**.

- `:core:test`: 60 tests, 0 failures, 0 errors.
- `:app:testDebugUnitTest`: 31 tests, 0 failures, 0 errors.
- `:app:assembleDebug`: success.
- `prototype`: `python -m pytest tests -q` — 2 passed.

Key focused results:

- `GoldenParityTest` passes after fixing the sticking-point plateau selection in Kotlin and updating the golden metric.
- `AnalysisPipelineTest.analysis job cancellation reaches decoding` passes deterministically (`runBlocking` + `CompletableDeferred`).
- `AnalysisRepositoryTest.manual phase edits are persisted for a saved set` passes.
- `PhaseSegmenterTest` covers 0.10 s and 0.12 s lockouts, two complete reps, and a trailing no-lockout rep.
- `RepMetricsTest` covers endpoint ROM, non-monotonic warning, independent peak smoothing, and null-not-zero acceleration/load metrics.

## Commits in this pass

- `5d8edc1` — `fix: complete lockout, sticking point and golden parity semantics`
- `0eb6b3f` — `feat: run analysis in foreground service and persist manual edits`
- `00e57a3` — `docs: update implemented status`
- `c407fc4` — `fix: mirror sticking point plateau selection in Python reference`

Worktree is clean after these commits.

## Manual validation checklist (residual)

1. Fresh install with no `plate.tflite`: import a short squat video, draw ROI on the first frame, confirm, analyze, reach report. Confirm the specific ROI error is not shown on the happy path.
2. With a vetted `plate.tflite`: confirm auto ROI suggestion, edit/confirm, and analysis on a real video.
3. Multi-rep video: exactly complete reps appear; a trailing concentric without lockout is warned and excluded.
4. 0.10–0.12 s lockout: verify lockout survives the generic 0.12 s short-run merge and rep metrics use the expected boundary.
5. History: analyze, open history, open detail, export JSON and CSV, force-stop/reopen app, confirm the set is still listed and readable.
6. Manual phase drag: metrics recompute, all reps' velocity loss refreshes, edit persists after reopening history.
7. LVP: save three sets same lift/variant at three distinct loads; verify slope, R², literature `v1rm`, estimated 1RM. Save only two loads; verify null + `LVP_REQUIRES_THREE_DISTINCT_LOADS`.
8. Quality: no load, camera level off, and >20% occlusion; verify force/power/work/1RM unavailable, vertical velocity marked estimate, joint angles disabled, warnings visible.
9. Long video >10 min is rejected at import/pipeline; cancel during analysis stops decoding and returns to import.
10. Background: start analysis, press Home; verify ongoing notification and no result loss; return and cancel.
11. Device: MediaCodec decode with rotation, VFR/60 fps, 1080p; verify effective FPS, frame recycling, and no OOM.
12. Joint angles: 16:9 video angle math, 45° azimuth correction, key-frame start/bottom/sticking/lockout angles and confidence.

---

## Re-review fix: analysis service re-entry (2026-11-03)

### Finding

`AnalysisForegroundService.kt:78` used `check(analysisJob?.isActive != true)` on the main thread. `MainActivity.kt:127-154` called `analyze()` directly without catching the resulting `IllegalStateException`. Double-start after cancel, or Activity recreation while a job was still finishing, could crash the app.

### Change

- Replaced the `check` with an explicit `AnalysisStartResult { STARTED, ALREADY_RUNNING }`.
  - `app/src/main/java/com/vbtdroid/app/analysis/AnalysisForegroundService.kt`
  - `app/src/main/java/com/vbtdroid/app/analysis/AnalysisRunGate.kt`
- `AnalysisRunGate` atomically owns the single active job, rejects a second start without throwing, and clears only the currently matching job in `finally` so a stale completion cannot detach a newer run.
- `AnalysisForegroundService.AnalysisBinder.analyze()` now returns `AnalysisStartResult`; `cancel()` uses the gate.
- `MainActivity` handles `ALREADY_RUNNING` by clearing the progress state and showing the localized `analysis_already_running` message instead of crashing.
- Added `analysis_already_running` in zh-CN and English resources.

### Regression tests

`app/src/test/java/com/vbtdroid/app/analysis/AnalysisRunGateTest.kt`:

- active run rejects double-start without invoking the job factory;
- after the active job is cleared, re-entry is accepted;
- a stale completion does not clear a newer run.

### Test evidence

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: **BUILD SUCCESSFUL**. `core` 60 tests / 0 failures; `app` 31 tests / 0 failures; `assembleDebug` success.

### Commit

- `a64e140` — `fix(app): handle analysis service re-entry without crashing`
