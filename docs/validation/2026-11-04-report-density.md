# 验证报告：报告页卡片密度、播放光标、仅保留叠加视频导出

- 日期：2026-11-04
- 范围：`ReportScreen` / `ImmersiveReport` 卡片布局；速度图、关节角曲线、杠铃轨迹图的播放光标；导出弹层去掉 JSON/CSV。
- 未包含：叠加视频生成路径（另一个代理在做缓存版本，本次保留现有首帧/静态回退）。

## 1. 改动

### 1.1 卡片密度（类 Arcus）

- 视频仍是全幅背景。
- 左列宽度由 0.46 降为 0.36（竖屏）。
- `GlassCard`：内边距 12→10，间距 8→6，标题改 `labelMedium`，圆角改 `medium`。
- MCV 卡：本次 MCV 大字（`displayMetricSm`）+ 2×2 小指标（组平均、最佳、速度损失 VL、有效次）。原为三行竖排。
- 速度曲线：高度 64→56dp，原 140dp 的版本已在上一轮降过。
- 关节角卡：曲线 40dp（新增，见 1.2）+ 关键帧 2×2 网格（标签 `labelSmall`，数值 `bodyLarge`）。原为四行大字。
- 轨迹卡：高度 110→96dp。对比最佳次开关仍位于图下。
- 逐次柱图：高度 128→72→56dp，柱最高 96→56→40dp。
- 长列表（逐次表、质量、LVP）仍在“详情”弹层，不在卡片组里。

估算高度（1080×2400 手机，2.625 倍，可用高度约 914dp）：

| 卡片 | 估算高度 |
|---|---|
| 本次 MCV（大字 + 2×2） | ~150dp |
| 速度曲线 | ~100dp |
| 关节角（曲线 + 2×2） | ~140dp |
| 杠铃轨迹 | ~130dp（含对比芯片时） |
| 逐次柱图 | ~100dp |
| 间距 | ~30dp |
| 合计 | ~650dp |

可用高度约 670dp（扣除顶部栏 ~110dp、播放条 ~50dp、底部操作栏 ~76dp、边距）。估算下基本能容纳，但余量很小。**这是估算，未在真机上测量。** 字体放大或短屏时仍会出现轻微滚动，列仍是可滚动的。

### 1.2 播放光标

- 光标 = 当前播放时间（`playbackSec`），只在选中次的时间范围内显示（`cursorInRep`）。
- 速度曲线（`VelocityChart.cursorSec`）：竖线 onSurface 90%，位置由时间映射到 x。
- 关节角曲线（新增 `JointAngleChart`）：取本次的关节角时间序列（`frameAnglesByRep`），画出曲线，竖线为光标。
- 杠铃轨迹（`BarPathChart.progressSec`）：轨迹画到播放头为止，同时在播放头处画竖线。
- 暂停时 `playbackSec` 不变，光标静止。播放时每 100 ms 更新（沿用 `PlaybackBar` 轮询）。
- 时间到 x 的映射抽成纯函数 `timeToX`（`ChartGeometry.kt`），`ChartGeometryTest` 覆盖：端点、中点、线性、区间外返回 null、空跨度与 NaN。

### 1.3 仅保留叠加视频导出（用户界面）

- 导出弹层删除 JSON、CSV 两行；`onExportJson`/`onExportCsv` 参数删除。
- `ReportScreen` 删除 `jsonLauncher`、`csvLauncher`、`writeExport`、`exportSucceeded` 状态及其提示条。
- 删除无引用的字符串键：`report_export_json`、`report_export_csv`、`unit_m`（导入页片径已改厘米，`unit_m` 已无引用）。
- 保留：叠加视频预览、保存叠加视频、直接导出叠加视频（保存进度/结果提示保留）。
- **core 中的 `JsonExport`、`CsvExporter`、`ExportMapper` 未改动**，测试与验证代码仍使用它们。

## 2. 验证

### 2.1 构建与单测

由于主工作区中另一个代理的未提交覆盖层改动（`OverlayCards.kt`、`OverlayChartGeometry.kt`、`AnalysisOverlayJob.kt` 及对 `OverlayRenderer`、`OverlayStyle` 的修改）当前编译失败，我在一个临时 worktree（基于 HEAD `5cadece`）中放入本次改动的文件后构建：

| 项目 | 结果 |
|---|---|
| `:core:test` | 93 通过，0 失败 |
| `:app:testDebugUnitTest` | 114 通过，0 失败，0 跳过（含 `ChartGeometryTest` 5 个用例） |
| `:app:assembleDebug` | 成功 |

临时 worktree 已删除。主工作区的未提交改动未被触碰。

### 2.2 设备

设备离线（`192.168.57.102:37657` 连接被拒），未安装，未截图。以下项目**未验证**：
- 卡片实际高度与 1080×2400 上的可见性；
- 播放时光标是否与视频帧同步；
- 导出弹层的实际外观。

## 3. 风险与待办

1. 卡片高度是估算，需真机确认；若超出，优先压缩关节角曲线或把逐次柱图移到底部播放条上方。
2. 光标与视频帧同步依赖“视频播放时间 = 源视频时间”的假设（与上一轮报告一致），未在真机核对。
3. 主工作区当前无法编译，原因是另一个代理的未提交覆盖层改动。该代理完成后需要重新跑一次主工作区的构建。
4. 另一个代理提交的 `fc0d0de` 把“换次清空轨迹”改为“间隙中保留上一次轨迹”。本次未回退，与其兼容。
