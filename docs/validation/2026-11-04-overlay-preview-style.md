# Burned overlay cards match report preview — 2026-11-04

## Scope

- `app/src/main/java/com/vbtdroid/app/overlay/OverlayCards.kt`
- `app/src/main/java/com/vbtdroid/app/overlay/OverlayRenderer.kt`
- `app/src/main/java/com/vbtdroid/app/overlay/OverlayStyle.kt`
- `app/src/main/java/com/vbtdroid/app/overlay/VideoOverlayExporter.kt` (one-line Context passing)
- No report UI files changed.

## What changed

The burned cards now mirror the Compose preview cards:

1. **本次 MCV**
   - Hero MCV + `m/s`.
   - 2×2 mini grid: 组平均 / 最佳 MCV / 速度损失 VL / 有效次.
   - Values: MCV 2 decimals, VL 1 decimal; missing is `—`.

2. **杠铃垂直速度**
   - Phase bands behind the curve at 12% alpha.
   - Dashed zero line and dashed MCV mean line.
   - Mint velocity curve and white playback cursor.
   - Peak label `峰值 x.xx m/s` at the bottom end.

3. **姿态分析**
   - No sparkline.
   - Current frame only, 2-column grid, exactly like the Compose `PoseCard`.
   - Squat: 背角 / 髋角 / 膝角 / 踝角.
   - Deadlift: 背角 / 髋角 / 膝角.
   - Bench: 肩角 / 肘角.
   - Pose off: 已关闭. Stale pose (>0.3 s) or low joint confidence (<0.5) shows `—`.

4. **杠铃轨迹**
   - Equal-scale letterboxed path, 12dp chart padding.
   - Faint axis lines.
   - Phase colors: eccentric blue, concentric mint, pause/lockout 70% alpha.
   - Start hollow dot + end filled dot.
   - No playback cursor; trail advances by frame.

5. **逐次平均速度**
   - One bar per rep; best rep solid mint, others 45% mint.
   - Selected rep white outline.
   - Rep index below each bar.

## Style

- Card scrim `#0A0E11` at 82%, border `#2B3742` at 50%, radius 12dp.
- Text colours: report `onSurface #E9EEF1`, `onSurfaceVariant #93A1AC`; hero mint `#3CE0B4`.
- Typography follows report sp/dp values:
  - title 12sp (labelMedium), hero 40sp (displayMetricSm), mini label 11sp, body 16sp.
  - Hero numbers use the bundled Barlow Condensed semibold font, like the report number style.
  - Card content scale = `width-based scale × 2.75` so burned-in text has the same relative size as the 1080-wide Compose preview.
- Portrait: single 36%-width left column below the HUD, measured heights, explicit 8dp gaps, bottom safe margin. If a short frame cannot fit, the whole block and gaps shrink; rectangles cannot cross.
- Landscape: five equal cards in a bottom strip with equal gaps.
- Each card is clipped to its own rounded rect; all text is fitted horizontally inside its cell.

## Labels / resources

All card labels come from existing string resources (`report_card_rep_mcv`, `report_mini_*`,
`report_velocity_chart`, `report_card_pose`, `report_bar_path_chart`, `report_per_rep_mcv`,
`metric_*`, `unit_mps`, `report_value_missing`, `report_angle_value`,
`report_peak_velocity_label`). No hardcoded English card labels.

## Verification

Per controller instruction, **no Gradle build/install was run** for this change. Static checks only:

- `OverlayRenderer` now takes `Context`; only call site `VideoOverlayExporter` updated.
- No remaining `OverlayStyle.CONCENTRIC` references.
- No hardcoded Chinese/English card labels in `OverlayCards.kt` (all via resources).
- Card count is 5; portrait layout uses a single measured column; landscape a five-card strip.

Controller should run one clean build after all agents stop.

## Risks

- Not compiled yet. Highest-risk spots are the Context constructor change and Canvas-only resource
  formatting; controller build will confirm.
- No device screenshot for this style version yet.
- 5 cards consume most of a short portrait video height. At 720×1280 the block fits with a small
  margin; at 1080×2400 it has ample room.
