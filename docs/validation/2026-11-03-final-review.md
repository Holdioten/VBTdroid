# VBTdroid Final Whole-Branch Review Findings

Base: f055601
Head: 3f43bf5
Verdict: NOT ready to merge.

## Critical (must fix)

1. **Fresh install cannot analyze; manual ROI path unreachable.**
   - `MainActivity.kt:44-52`, `ImportScreen.kt:171-183`, `AnalysisPipeline.kt:101-106`, `download_models.sh:48-52`
   - Default download has only pose model. Missing plate model → detector null. UI never sets `AnalysisRequest.roi`; pipeline throws "no ROI". Core import→report flow is broken.
   - Fix: first-frame preview, auto-suggest and editable/confirmable ROI; no detector must still initialize from user ROI. Keep specific error reasons. Add no-model end-to-end test.

2. **History and export are isolated schema/functions; user cannot save, view history, or export.**
   - `MainActivity.kt:66-95`, `ReportScreen.kt:54-177`, `AppDatabase.kt:6-13`, `CsvExporter.kt:6-14`, `JsonExport.kt:16-32`
   - No Room instantiation, no DAO main-path calls, no history screen, no save button, no SAF write/share; no `AnalysisResult → SetExport` mapping. `AnalysisResult` lacks video URI, plate diameter, camera angle metadata for export.
   - Fix: transactionally save Session/Set/Rep/Phase; implement history and same-load query; wire JSON/CSV SAF export from report; save request, calibration, quality, manual phases.

3. **LVP/1RM has no consumer; lift variant cannot be distinguished.**
   - `Lvp.kt:13-42`, `AnalysisPipeline.kt:24-32`, `Entities.kt:27-54`
   - No history load-point aggregation, no LiftProfile, no variant, no default `v1rm`/`v1rmSource`, no report/export. R3's "later task" does not exist.
   - Fix: same-lift/same-variant data model and query; default literature thresholds + source; fit quality display; `<3` points → null + reason.

## Important (should fix)

1. **Phase segmenter accepts incomplete reps without lockout; 0.10–0.12s valid lockout is merged away.**
   - `PhaseSegmenter.kt:53`, `:106-152`, `:179-189`, `:217-227`
   - Unconditional trailing `finish()`; generic `minPhaseSec=0.12` merge runs before `lockoutSec=0.10`, so short lockout folds into concentric and lowers MCV.
   - Fix: only complete reps in formal results; incomplete segments get warning; detect lockout before short-run merge. Add multi-rep, no-lockout, 0.10/0.12 boundary tests.

2. **`rom_concentric_m` uses range, not spec endpoint displacement.**
   - `RepMetrics.kt:68-88`, `:98-100`
   - MCV uses last-first but ROM uses max-min; sticking-point denominator inherits wrong denominator and may exceed 0–100.
   - Fix: use concentric endpoint displacement; non-monotonic/invalid → quality flag or unavailable, do not clamp to hide. Add non-monotonic test.

3. **Per-rep velocity loss uses "running best", conflicts with set-summary "full-set best"; manual edits leave later reps stale.**
   - `AnalysisPipeline.kt:222-234`, `:241-250`, `ReportRecompute.kt:12-30`
   - New best rep can get negative VL; earlier reps null; summary uses full-set best; editing early rep does not recompute later reps.
   - Fix: compute all MCVs first, then per-rep VL against full-set best; manual edit recomputes all affected reps from edit point onward. If keeping running-best, use a different term, not `velocity_loss_pct`.

4. **Joint angles distorted on non-square video and never displayed.**
   - `MediaPipePoseEstimator.kt:42-48`, `JointAngleExtractor.kt:53-58`, `AnalysisPipeline.kt:182-196`, `ReportScreen.kt:120-150`
   - MediaPipe normalizes x by width, y by height; direct angle math introduces aspect-ratio scaling. Pipeline stores `frameAnglesByRep` but report only draws velocity/path/phases.
   - Fix: convert to pixel coordinates or aspect-correct ratio before angles. Report key-frame angles (start, bottom/touch, sticking, lockout) with confidence. Add 16:9 known-angle test.

5. **Quality flags are not the claimed semantics; degradation rules not executed.**
   - `AnalysisPipeline.kt:159-177`, `Quality.kt:14-28`, `ReportScreen.kt:157-174`
   - `repConfidence` is just valid/total reps, not segmentation confidence; occlusion counts only total loss, not low-confidence frames; `cameraLevel=false` only generic warning, not "estimate"; high occlusion does not disable joint angles; missing load has no reason.
   - Fix: per-rep/per-metric quality and reasons; execute spec §5.9 degradation; add `LOAD_REQUIRED`, manual phase, pose occlusion states.

6. **Peak and mean share one SG configuration; filter config not reported.**
   - `AnalysisPipeline.kt:151-154`, `RepMetrics.kt:79-88`, `:138-140`
   - PV, peak acceleration, peak force/power all use default window=7; violates "peaks use lower smoothing"; cannot audit filter effect.
   - Fix: derive separate mean/peak series; persist/display window, order, method, applicable metrics.

7. **Tracking failure breaks optical-flow image/center pairing.**
   - `PlateTracker.kt:117-139`
   - Failure leaves center at last success but `previousGray` advances to failed frame; next LK tracks old center in new image, causing repeated failure.
   - Fix: save successful frame + center as a pair; do not advance gray alone on failure. Add continuous-occlusion recovery test. This deferred minor is merge-blocking.

8. **No 10-minute cap, cancel entry, foreground service or notification.**
   - `AnalysisPipeline.kt:80-95`, `ImportScreen.kt:191-200`, `AndroidManifest.xml:1-16`
   - Long videos can scan/decode indefinitely and accumulate samples; no cancel; no background lifecycle.
   - Fix: reject over-limit at import; provide cancel Job; move long work to foreground service with notification. Plan-level gap.

9. **Export identifiers violate Canonical IDs.**
   - `CsvExporter.kt:7`, `prototype/vbtdroid_proto/metrics.py:54-56`
   - CSV uses `mcv`, `meanForce` aliases/camelCase; Python emits `duration_s` instead of `rep_duration_s`.
   - Fix: unify `mcv_mps`, `mean_force_n`, etc.; update Task 13 column plan, tests, validation CLI examples.

10. **Fake zeros remain instead of unavailable.**
    - `JointAngles.kt:17-24`, `SavitzkyGolay.kt:36-39`, `:60-64`, `AnalysisPipeline.kt:255`
    - Degenerate joint vectors return 0°; insufficient samples for second-order fit return 0 acceleration; load present but no valid work samples → total work 0.
    - Fix: return `null` + reason at metric boundary, or reject rep/angle; `totalWorkJ` only sums when at least one valid work value.

11. **Tests do not cover high-risk integration; golden has no Kotlin parity.**
    - `AnalysisPipelineTest.kt:138-191`, `prototype/tests/test_pipeline.py:28-47`, `prototype/golden/synthetic_squat_samples.json:1-8`
    - App pipeline uses FakeDecoder/FakeTracker; pose always null; Python synthetic single circle; no Kotlin test reads golden. No real decode/ellipse/flow/model/ROI→report/save/export coverage.
    - Fix: Kotlin golden parity, real sample video instrumentation, multi-rep/VFR/cancel/occlusion/save-export integration tests.

## Minor (defer if needed)

1. LVP can produce negative/implausible 1RM; validate finite/positive/consistency.
2. UINT8 input accepted but quantization ignored; resized Bitmap not recycled.
3. PlateTracker Mat has no close path.
4. MediaPipe pose estimator keeps timestamps across videos; reset per analysis.
5. Constant identical samples forced ICC=1; zero variance is undefined.
6. Python reference can fall back to frame-index time; restrict to synthetic CFR.
7. Model initialization on Compose main thread; exceptions swallowed.
8. README says algorithms/app not implemented; update status.
9. Room schema not exported; add schema export/migration tests.

## Declined to judge (device/validation)

- Real-device MediaCodec/YUV/crop/HDR/rotation compatibility.
- Detector recall, optical-flow occlusion recovery, MediaPipe accuracy.
- 60s performance/thermal on mid-range.
- Build/test current pass state (read-only review, not re-run).
- Spec numeric accuracy targets (no validation records).
- Final distribution license compliance.
