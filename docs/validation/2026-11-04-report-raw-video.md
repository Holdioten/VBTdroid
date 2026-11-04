# 验证报告：报告页改播原始视频 + 卡片补齐 + 播放图标

- 日期：2026-11-04
- 范围：`ui/report/*`（`ReportScreen` / `ImmersiveReport` / 新增 `OverlayPreviewDialog`）与 `values*/strings.xml`。
- 未包含：core、叠加编码器（`overlay/*` 由另一代理在改，本次未触碰）。
- 背景：真机截图发现报告页把缓存叠加 MP4 当背景播放，叠加里的英文小卡/HUD 与 Compose 卡片重叠（左上角重复卡片）。

## 1. 改动

### 1.1 报告主视频 = 源视频

- `CoverVideo(file: File, ...)` → `CoverVideo(uri: Uri, ...)`，内部仍是 `view.setVideoURI(uri)`，封面裁切逻辑不变。
- `ReportScreen`：
  - 新增 `sourceUri = Uri.parse(result.videoUri)`（空串为 null）与 `mainVideo = sourceUri?.takeUnless { playback.failed }`。
  - `backgroundLayer` 播 `mainVideo`；不可用时回退 `ReportPreview` 首帧。
  - `videoFile` 删除，`overlayFile` 只服务预览与保存。
  - `playbackSec`、播放条显隐改由 `mainVideo` 决定；播放状态仍驱动当前次、卡片与光标（源视频与叠加 MP4 时间轴一致，均为源视频时间）。
  - `ImportScreen` 已 `takePersistableUriPermission`，源视频跨会话可读。
- 结果：卡片后面只有原始画面，无烧录英文卡/HUD。

### 1.2 卡片列补齐（去掉分页）

- `HorizontalPager` / `PageDots` 删除（`PageDots` 函数一并删除）。
- 单列可滚动，顺序：**本次 MCV → 杠铃垂直速度 → 杠铃轨迹 → 姿态分析 → 逐次平均速度**。
  - 杠铃轨迹卡、逐次平均速度卡回到左列，不再藏在第二页。
  - 竖屏：左列 0.36 宽度内滚动；横屏：单行横向滚动，每卡 240dp。
- 卡片高度不变（速度 56dp、轨迹 96dp、柱图 56dp），无重叠；视频仍是全幅背景。

### 1.3 姿态分析卡去掉曲线

- 删除 `JointAngleChart`（含调用与 `Canvas`/`Path`/`Stroke` 相关导入）。
- 卡片只保留关节实时数值：深蹲 背/髋/膝/踝，硬拉 背/髋/膝，卧推 肩/肘；随播放头 `frameAt(cursorSec)` 更新，低置信度显示 “—”。
- `analyzePose = false` 时仍显示“已关闭”；阶段标签（`PhaseLabels`）未改。
- `PoseReadout.headlineJoint` 保留（`PoseReadoutTest.headlineJointFollowsLift` 仍引用）。

### 1.4 播放控件改图标

- `PlaybackBar`：文字“播放/暂停”改为 48dp 圆形按钮 + 图标。
  - 暂停中 → `Icons.Filled.PlayArrow`；播放中 → `PauseGlyph`（Canvas 两根圆角竖条：material icons core 不含 Pause，未引入 extended 依赖）。
  - `contentDescription` 仍用 `report_overlay_play` / `report_overlay_pause`，描述“按下后的动作”，与图标一致。
- 进度条、点击切换、100ms 轮询逻辑不变。

### 1.5 预览叠加视频 / 保存视频

- 新增 `OverlayPreviewDialog`：`Dialog(usePlatformDefaultWidth = false)` 全屏播放缓存叠加 MP4（含烧录卡片），右上玻璃圆钮关闭（`report_overlay_close`），底部复用 `PlaybackBar`（独立 `OverlayPlayback`，关闭即释放）。
- 打开预览时先暂停报告主视频，避免两个 VideoView 同时解码。
- `ReportActions` 增加第 4 个按钮“预览叠加视频”（`report_overlay_preview_button`，无缓存文件时禁用）。
  - 按钮统一 `labelMedium` + `contentPadding = 8dp`，权重 1 / 1 / 1.4 / 1.1：最长的“预览叠加视频”拿到最宽份额。
- “保存视频”仍走 SAF `CreateDocument` 复制缓存叠加文件（手机端主输出是叠加视频），无缓存时禁用。
- 顶部状态芯片语义改为“叠加文件是否可用”（`report_overlay_unavailable`），与预览/保存按钮一致。

### 1.6 字符串

- 新增：`report_overlay_preview_button`（预览叠加视频 / Preview overlay video）、`report_overlay_close`（关闭 / Close）。
- `report_overlay_play` / `report_overlay_pause` 改为图标 contentDescription，键值未动。

## 2. 验证

| 项目 | 结果 |
|---|---|
| `xmllint --noout`（zh/en strings） | 通过 |
| 三个改动的 `.kt` 括号/圆括号配对 | 一致 |
| `:core:test` / `:app:testDebugUnitTest` / `assembleDebug` | **未跑**：按分工不执行 Gradle，由控制器统一构建 |
| 真机截图复核 | **未取得**：需安装后人工确认 |

## 3. 风险与待办

1. **未编译**：`ReportActions` 新签名、`CoverVideo(Uri)` 调用点、新增 `OverlayPreviewDialog` 均未经过编译器，控制器构建时若报错优先查这三处。
2. 四个底部按钮在 320dp 窄屏可能把“预览叠加视频”截成省略号（360dp 及以上按估算放得下），真机若截断可把该按钮文案改短或改为图标。
3. 源视频与叠加 MP4 时长/时间轴一致是卡片同步的前提；若某次导出出现偏移，卡片与画面会错位（与上一轮相同的既有假设）。
4. 预览对话框内 `VideoView` 走 `file://`，失败时回退首帧静态图，无进度条错误态。
5. `ImmersiveReport.kt` 中仍有少量历史遗留未用导入（`ChipTone`、`StatusChip`、`RoundedCornerShape` 等），仅为告警，未清理以免扩大改动面。
