# 验证报告：报告页播放分析服务生成的缓存叠加视频

- 日期：2026-11-04
- 范围：报告页改为使用 `AnalysisResult.overlayVideoPath`；移除报告内的按需生成；保存动作改为复制缓存文件。
- 依赖：分析服务已生成叠加 MP4 并写入 `overlayVideoPath`（提交 `cdeeb27`）。

## 1. 改动

### 1.1 视频来源

- `overlayVideoFile(path)`（`OverlayPreview.kt`）：路径为空、文件不存在、不可读、为目录或长度为 0 时返回 null；否则返回 File。
- `ReportScreen` 用它得到 `overlayFile`，再与 `playback.failed` 合并为 `videoFile`。
  - `videoFile != null`：全幅背景使用 `CoverVideo`（`Uri.fromFile`），播放驱动当前次、卡片和光标（沿用上一轮的实现）。
  - `videoFile == null` 或播放器报错：回退到首帧静态图（`ReportPreview` 全幅模式），顶部显示一条“叠加视频不可用”状态芯片。播放条隐藏。
- 播放器错误：`CoverVideo` 注册 `setOnErrorListener`，设置 `playback.failed = true`，报告随即回退首帧。

### 1.2 不再在报告内生成

- 删除 `OverlayPreviewController`（按需生成、代次控制、缓存删除）。
- 删除直接导出叠加视频（`VideoOverlayExporter.export(outputUri)`、`overlayLauncher`、导出进度与结果状态）。
- 删除导出弹层 `ExportSheet.kt`（只剩一个动作，改为底部直接保存）。
- 底部操作改为：阶段 / 详情 / 保存视频。
- 保留 `VideoOverlayExporter.exportToFile`，它由分析服务调用。

### 1.3 保存

- `saveLauncher`（SAF `CreateDocument("video/mp4")`）：在后台协程中把缓存文件复制到用户选择的位置，结束后用 Snackbar 提示成功或失败。
- 保存按钮在没有可用视频时禁用。

### 1.4 字符串

- 新增：`report_overlay_unavailable`（叠加视频不可用）。
- 改值：`report_save_overlay`（保存视频）。
- 删除无引用键（自动扫描）：`report_export_overlay_video`、`report_export_overlay_progress`、`report_export_overlay_succeeded`、`report_export_overlay_failed`、`report_export_overlay_no_data`、`report_export_title`、`report_preview_overlay`。

## 2. 验证

| 项目 | 结果 |
|---|---|
| `:core:test` | 93 通过，0 失败 |
| `:app:testDebugUnitTest` | 129 通过，0 失败，0 跳过（含 `OverlayVideoFileTest` 5 个：null/空白、缺失、空文件、目录、可读文件） |
| `:app:assembleDebug` | 成功 |
| 安装到 `192.168.57.102:41125` | `Success` |
| 报告页截图 | **未取得**：设备停在锁屏（`mDreamingLockscreen=true`，通知栏焦点卡住），上滑、电源键、返回、Home 均无效。需要手动解锁后再截图。未绕过锁屏。 |

## 3. UI 溢出检查

未取得报告页截图，因此**没有可报告的真机溢出**。需要检查的点（真机补测时确认）：
- 顶部第二行：次数步进 + HUD（“第 i/n · 阶段”）+ “叠加视频不可用”芯片同时出现时，宽度是否挤压。
- 底部三个按钮（阶段 / 详情 / 保存视频）在窄屏上的文字是否换行或截断。
- 左列卡片组的总高度（上一轮估算约 650dp，可用约 670dp）。

## 4. 风险与待办

1. 叠加视频的时间轴假设（播放时间 = 源视频时间）仍未在真机核对。
2. 分析服务生成 MP4 期间，报告中 `overlayVideoPath` 可能为 null，此时只显示首帧回退。若需要“生成中”状态，需要分析服务提供进度或通知。
3. 阶段校正后，缓存的叠加视频不会重新生成，阶段标签沿用生成时的值。
