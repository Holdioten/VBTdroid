# 验证报告：报告页只播叠加视频，Compose 不再叠数据卡

- 日期：2026-11-04
- 范围：`ui/report/*` + `values*/strings.xml`。叠加侧烧录卡片由另一代理完成（`f321751 feat(overlay): match burned cards to report preview`）。
- 取代：`2026-11-04-report-raw-video.md`（同日“报告播源视频 + Compose 卡片”的方向，本次按用户决定反转）。
- 未包含：core、`overlay/*`、JSON/CSV（未恢复）。

## 1. 报告侧改动

### 1.1 只播叠加视频，零 Compose 数据卡

- `ReportScreen` 背景层：`overlayVideoPath` → `overlayVideoFile()` → `FitVideo(uri)`。
- 删除卡片列：`MainMcvCard`、`VelocityCard`、`PoseCard`、`BarPathCard`、`RepBarsCard` 全部从报告移除，左列/分页/`PageDots` 一并删除。报告不再画任何数据卡，卡片只出现一次——叠加烧录的那一份。
- 删除随之失效的选择状态：`selectedPosition`、`compareWithBest`、`bestRep/bestSlice`、`selectRep`、`cursorInRep`、`timeRep` 同步、`stageType` HUD 文案。

### 1.2 保留的界面

- 顶部导航：返回 / 身份（动作 · 变式 · 负重，`buildSubtitle`）/ 历史。状态芯片（保存失败、叠加视频不可用）仅在有内容时占第二行。
- 底部：播放/暂停图标 + 进度条（`PlaybackBar`，无叠加文件时隐藏）。
- 操作条回到 3 个按钮：阶段 / 详情 / 保存视频（预览按钮删除——报告本身就是预览）。
- “阶段”编辑“播放头所在次”，无播放头时取第一次（`RepTimeline.currentRepAt` 回退 `repSlices.firstOrNull()`）。
- “详情”弹层保留（1RM 估算、质量、逐次表、姿态开关提示），与烧录卡片不重复。
- 姿态分析关闭态、阶段标签：未改。

### 1.3 缺叠加文件时

- 回退：源视频首帧（`ReportPreview(fill = true)`）+ 顶部“叠加视频不可用”芯片，播放条隐藏。
- 芯片条件改为 `overlayVideo == null`（文件缺失**或**播放器报错都提示）。

### 1.4 视频缩放：cover → fit（关键决策）

- `CoverVideo` → `FitVideo`，`CoverFrame` → `FitFrame`，缩放系数由 `max`（裁切）改为 `min`（等比 letterbox，居中，`Bg` 填充黑边）。
- 理由：叠加把卡片烧进源画面（竖屏左列 36%、横屏底部五卡条）。cover 在 1080×2400 竖屏上会裁掉约 13% 宽度，左列标题被切；横屏视频竖屏播放时更会被裁掉大半。fit 保证烧录卡片完整可见。
- 代价：视频上下（或左右）留黑边；顶部栏与播放条正好落在黑边上，视觉干净。

### 1.5 删除的文件

| 文件 | 原因 |
|---|---|
| `OverlayPreviewDialog.kt` | 报告即叠加预览，对话框冗余 |
| `ReportSections.kt` | 只有 `RepBarsContent`，随逐次柱图卡片删除 |
| `BarPathChart.kt` | 只被 `BarPathCard` 调用 |
| `VelocityChart.kt` | 只被 `VelocityCard` 调用；`phaseColors()` 移入 `PhaseTimeline.kt`（阶段条仍在用） |

保留（测试或其它页面引用）：`ChartGeometry.kt`(`timeToX`，`ChartGeometryTest`)、`BarPathTransform.kt`(`fitBarPath`，`BarPathTransformTest`)、`PoseReadout.kt`(`PoseReadoutTest`)、`ReportFormat.kt`、`PhaseLabels.kt`、`ReportCards.kt`、`RepTable.kt`。

### 1.6 字符串

- 删除 6 个无引用键（zh/en 同步）：`report_overlay_preview_button`、`report_overlay_close`、`report_rep_bar_description`、`report_compare_best`、`report_velocity_chart_description`、`report_bar_path_chart_description`。
- 保留 `report_hud_stage`、`report_rep_index`：烧录 HUD 尚未中文化，留给叠加代理复用。
- 卡片类键（`report_card_rep_mcv`、`report_mini_*`、`report_velocity_chart`、`report_card_pose`、`report_bar_path_chart`、`report_per_rep_mcv`、`report_pose_off`、`report_peak_velocity_label` 等）全部保留：叠加烧录时从资源读取。

## 2. 叠加侧状态与协调事项

- 五张烧录卡片已由叠加代理完成并匹配 Compose 样式（`f321751`，见 `2026-11-04-overlay-preview-style.md`）：MCV 大字 + 组平均/最佳/VL/有效次、速度曲线带光标、姿态卡无 sparkline、轨迹无光标、逐次柱图；中文资源文案、mint/琥珀/阶段色、竖屏左列 36%、横屏底部五卡条。
- **未完成（属叠加代理，本次未改 `overlay/*`）**：烧录 HUD 仍是硬编码英文——`liftLabel()` “Squat/Bench/Deadlift · High bar …”、`loadLabel()` “Load 100.0 kg”、`repLabel()` “Rep 2/5 · Ascent”。与顶部中文身份栏重复且语言不一致。建议：
  1. 用 `liftDisplayName` / `variantDisplayName` / `history_set_load` / `report_hud_stage` + `phaseLabelRes` 资源化；或
  2. 直接删掉 HUD 里的 lift/load 两行（顶部栏已显示），只留次数+阶段。

## 3. 验证

| 项目 | 结果 |
|---|---|
| `xmllint --noout`（zh/en strings） | 通过 |
| 报告目录 13 个 `.kt` 括号/圆括号配对 | 一致 |
| 报告代码引用的 86 个字符串键 | zh/en 均存在 |
| 删除符号/字符串全仓引用扫描 | 无残留（仅 `OverlayCards.kt` 自有的 `drawVelocityCard` 等同名私有方法） |
| 外部引用（`MainActivity`、`history/*`） | 仅 `ReportScreen`、`format*`、`qualityWarningText`、`phaseLabelRes`，均未删 |
| `:core:test` / `:app:testDebugUnitTest` / `assembleDebug` | **未跑**：按分工不构建，控制器在两个代理结束后统一跑 |
| 真机截图 | **未取得** |

## 4. 风险与待办

1. **未编译**。高风险点：`ReportHeader` 新签名（去掉次数步进器）、`FitVideo` 改名调用点、`phaseColors` 迁移到 `PhaseTimeline.kt`、4 个文件删除后无残留引用。
2. 竖屏 fit 后视频上下有黑边（9:16 源视频在 9:20 屏上约 91dp/边）。若用户嫌空，可改为“撑满宽度 + 顶部对齐”，但会重新引入裁切风险，需真机确认烧录卡片完整可见。
3. 烧录 HUD 英文未中文化（见 §2），属叠加代理；在它完成前，报告顶部中文与烧录英文 HUD 并存。
4. 报告无卡片后无法再“选次看数据”，只有播放头推进；这是本次方向的预期结果。阶段编辑仍可用。
5. 叠加 MP4 与源视频时间轴一致是阶段标签/播放头对应的前提（既有假设，未真机核对）。
