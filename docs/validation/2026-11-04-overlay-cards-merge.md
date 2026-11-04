# Overlay cards + merged analysis job — validation (2026-11-04)

## Scope

- Burn Arcus-style data cards into the overlay MP4 (`overlay/*` only).
- Generate that MP4 inside `AnalysisForegroundService` after the analysis pass, with one combined
  progress range and cache cleanup.
- `AnalysisResult` gained `overlayVideoPath: String?`.
- Report UI (`ui/report/*`) was not modified in this change.

## Verification

Environment note: this WSL shell has no `JAVA_HOME` in its default environment. The build was run
with the existing toolchains:

```
JAVA_HOME=/home/czq/toolchains/jdk17 \
ANDROID_HOME=/home/czq/toolchains/android-sdk \
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: **BUILD SUCCESSFUL**

- `:core:test` — 93 tests, 0 failures, 0 errors, 0 skipped.
- `:app:testDebugUnitTest` — 124 tests, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug` — APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

New tests:

- `app/src/test/java/com/vbtdroid/app/analysis/AnalysisOverlayJobTest.kt`
  - analysis progress maps `0..1 → 0..0.75`;
  - overlay progress maps `0..1 → 0.75..1.0`;
  - handoff is continuous and monotonic;
  - cache file name is `overlay-<createdAt>.mp4`;
  - stale cache selection keeps the newest files and never drops below one;
  - `AnalysisResult.copy` preserves the overlay path.
- `app/src/test/java/com/vbtdroid/app/overlay/OverlayChartGeometryTest.kt`
  - equal-scale bar-path fit uses one px/m for both axes;
  - degenerate/vertical paths stay finite and centred;
  - playback cursor mapping returns null outside the rep.

No device install was attempted; no device was available.

## Performance notes

- The merged job decodes the video twice: once for plate/pose analysis, once for overlay burn-in.
  Wall time is therefore roughly analysis + encode/decode overhead, not shared-frame reuse.
  Progress now reflects that honestly (analysis to 75%, export to 100%).
- Card layout rectangles and per-rep sample slices are cached per renderer/resolution. Per-frame
  work is limited to chart path construction over the active rep, not list copying of the full
  video timeline.
- Cache is capped at the 4 newest `overlay-*.mp4` files; older files are deleted after each export.
- Overlay export failure is non-fatal: analysis still persists and reports a warning
  (`OVERLAY_EXPORT_FAILED`) with `overlayVideoPath = null`.
- Cancellation deletes the partial MP4; encode/decode resources are released in the exporter
  `finally` blocks.

## Concerns

- Cache cleanup keeps four files, but a very old saved report that still points at a deleted cache
  path will no longer have its preview. Acceptable for this cache-only contract; revisit if report
  history needs long-lived videos.
- The report is expected to consume `overlayVideoPath`; as of this change it does not yet read it
  (report work owned by another agent), so the in-app report still uses its previous preview path.
- No on-device visual check of the card layout at 720p/1080p/landscape. Geometry is resolution
  driven and unit-tested only for the chart fit and cursor mapping.
