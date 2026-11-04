# Overlay fixes — non-overlap cards, ring joints, no path cursor (2026-11-04)

## Scope

- `overlay/OverlayCards.kt`: explicit measured layout, per-card rounded clipping, fitted text.
- `overlay/OverlayRenderer.kt`: thicker skeleton, ring joints, per-lift pose-card labels,
  “姿态分析” title, HUD rep label kept across between-rep gaps.
- No report UI edits.

## Fixes

1. Card layout
   - Portrait: one left column below the measured HUD bottom, with an explicit gap and a bottom
     safe margin.
   - Card heights are derived from the same text sizes the cards draw with, not hard-coded
     guesses. If the column does not fit, secondary metrics compact into one `AVG/BEST/VL` line,
     then the whole card block scales; gaps are also reduced before any rectangle can cross.
   - Landscape: one bottom strip with equal widths, equal gaps, and a fixed safe margin.
   - Every card content is clipped to its own rounded rect. Text is horizontally fitted to its
     card/cell width; chart coordinates are clamped inside the card. No negative/off-card offsets.

2. Skeleton and joints
   - Skeleton stroke ~2x previous (`4.0 * width/1080`, minimum 2.4 px).
   - Main joints draw an outer hollow ring plus an inner dot instead of a single solid dot.

3. Bar path card
   - No playback cursor in this card. The phase-colored trail advances with `cueSec`, exactly like
     the report trail.
   - The velocity sparkline keeps its amber vertical cursor.

4. Pose card labels (current frame angles)
   - Squat: 背角 / 髋角 / 膝角 / 踝角.
   - Deadlift: 背角 / 髋角 / 膝角.
   - Bench: 肩角 / 肘角.
   - Title/legend remains `姿态分析`.

5. HUD
   - Rep label now uses `RepTimeline.currentRepAt`/`trailRepAt`, so the HUD no longer blank-outs in
     the between-rep gap while the cards still show the previous rep.

## Verification

Command (clean worktree at commit `43e0a8e`):

```bash
JAVA_HOME=/home/czq/toolchains/jdk17 \
ANDROID_HOME=/home/czq/toolchains/android-sdk \
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: **BUILD SUCCESSFUL**

- `:core:test` — 93 tests, 0 failures.
- `:app:testDebugUnitTest` — 135 tests, 0 failures, 0 skipped.
- `:app:assembleDebug` — debug APK built.

## Device overlay rebuild

- Installed the rebuilt debug APK on `192.168.57.102:41125`.
- Analyzed `example/高杠深蹲-135kg-6次-侧面90度.mp4` again on device.
- Produced `cache/overlay-1793774962360.mp4` (4.7 MB), pulled and inspected decoded frames with
  ffmpeg.
- Checked in a mid-rep frame:
  - cards occupy one clean left column; no card-to-card overlap and no collision with the HUD;
  - main card shows large MCV, AVG/BEST/VL, and the per-rep bar;
  - velocity sparkline has the amber cursor;
  - bar-path card has no vertical cursor and shows the phase-colored trail;
  - pose card title is `姿态分析` with 背角/髋角/膝角/踝角 for the squat;
  - skeleton lines are visibly thicker and knee/ankle joints are hollow rings with inner dots.

The device disconnected before a second capture of the later HUD-only consistency tweak. The
captured MP4 includes all card-layout, ring-joint, path-cursor and pose-label fixes.

## Notes / risks

- Landscape bottom-strip layout was not captured on device (no landscape source run); it is
  covered by the explicit equal-gap layout code and the same clipped-card drawing path.
- On a 720x1280 portrait source the card column takes roughly 35% of the frame width. That is the
  requested left-column style; the plate remains visible to the right.
- No report files were touched.
