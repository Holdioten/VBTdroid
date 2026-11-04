# VBTdroid 架构

## 1. 模块

| 模块 | 职责 | 约束 |
|---|---|---|
| `core` | 标定、滤波、阶段切分、rep 指标、LVP、质量、JSON | 纯 Kotlin/JVM，不依赖 Android/OpenCV/TFLite/MediaPipe |
| `app` | 视频导入/解码、杠铃跟踪、姿态、前台服务、叠加视频、Compose UI、Room | Android only |
| `prototype` | Python 参考实现、PC 基准脚本、golden 数据 | 不作为产品运行时 |
| `ml` | 模型下载/训练脚本、SHA256 锁 | 权重不进 git |
| `docs` | 规格、术语、架构、开发、测试、状态、验证 | 文档随行为更新 |

## 2. 端到端数据流

```
SAF 选择视频
  → VideoDecoder（MediaExtractor + MediaCodec，PTS）
  → PlateTracker（首帧/丢失时检测器，逐帧 Hough/椭圆 + 光流）
  → Calibration（片径 + 机位角 → px/m）
  → Kinematics（局部多项式/ Savitzky-Golay → x/y/v/a）
  → PhaseSegmenter（阶段 + rep 切分）
  → RepMetricsCalculator（MCV/MPV/PV/ROM/VL/粘滞点/力/功率）
  → PoseEstimator（MediaPipe，多候选选离杠铃最近的人）
  → JointAngleExtractor（按动作只算相关关节）
  → AnalysisForegroundService
      ├─ Repository.saveAnalysis（Room）
      └─ VideoOverlayExporter（烧录骨架/角度/轨迹/数据卡片 → cache MP4）
  → ReportScreen（VideoView 播放 cache MP4 + 卡片 + 游标）
```

## 3. 关键文件

### core

| 文件 | 作用 |
|---|---|
| `core/.../calibration/Calibration.kt` | 片径标定、45° 水平反投影 |
| `core/.../metrics/Kinematics.kt` | 非均匀时间戳位置/速度/加速度 |
| `core/.../signal/SavitzkyGolay.kt` | 局部多项式平滑 |
| `core/.../phases/PhaseSegmenter.kt` | 阶段状态机、硬拉正向速度段、rep 过滤 |
| `core/.../metrics/RepMetrics.kt` | 单 rep 指标 |
| `core/.../metrics/Lvp.kt` | 负荷-速度拟合、1RM |
| `core/.../quality/Quality.kt` | 质量警告/降级 |
| `core/.../io/JsonExport.kt` | 内部 JSON 导出（UI 不再暴露） |

### app

| 文件 | 作用 |
|---|---|
| `app/.../video/VideoDecoder.kt` | 解码、PTS、旋转、缩放、首帧 |
| `app/.../vision/PlateDetector.kt` | EfficientDet-Lite0，多裁剪自动 ROI |
| `app/.../vision/PlateTracker.kt` | Hough 主跟踪 + 椭圆回退 + 光流预测 |
| `app/.../vision/MediaPipePoseEstimator.kt` | 多人候选、选离杠铃最近的人 |
| `app/.../analysis/JointAngleExtractor.kt` | 按动作裁剪关节输出 |
| `app/.../analysis/AnalysisPipeline.kt` | 端到端分析编排 |
| `app/.../analysis/AnalysisForegroundService.kt` | 后台分析 + overlay 生成 + 状态恢复 |
| `app/.../overlay/OverlayRenderer.kt` | 骨架/角度/轨迹/速度胶囊 |
| `app/.../overlay/OverlayCards.kt` | 烧录数据卡片 |
| `app/.../overlay/VideoOverlayExporter.kt` | ARGB→YUV、H.264、MediaMuxer |
| `app/.../ui/import/ImportScreen.kt` | 3 页横向导入流程 |
| `app/.../ui/report/ImmersiveReport.kt` | 视频优先报告 + 卡片 |
| `app/.../data/AnalysisRepository.kt` | Room 保存/历史/LVP |

## 4. 算法现状

### 4.1 杠铃片

- 首帧自动 ROI：EfficientDet-Lite0，全图 + 顶 60% + 底 60% 三路推理，映射回原图后 NMS。解决深蹲片在画面上半部漏检。
- 逐帧：Hough 圆主跟踪，搜索半径 **1.35×片径**；半径偏差 >15% 拒绝；失败回退椭圆；再失败调用检测器恢复。
- 光流/速度预测中心；固定参考片径，避免逐帧尺度漂移。
- 侧拍 = 圆；45° = 椭圆，标定用长轴。

### 4.2 阶段与 rep

- 深蹲/卧推：离心 → 停顿 → 向心 → 锁定；锁定可选。
- 硬拉：正向速度连续段 = 一次拉起；从段前局部最低点开始；不要求锁定；不包含下放。
- 过滤：最小时长 0.45s、向心 0.15s、ROM 0.20m、中位数 ROM 0.5× 剔除 1/4 次；加速度换向仅用于非硬拉。
- 阶段标签按动作不同；UI 默认只读，手动校正才可拖。

### 4.3 姿态

- MediaPipe Pose Landmarker，`numPoses=3`；按躯干中点到杠铃片中心距离选最近的人。
- 深蹲：背/髋/膝/踝；硬拉：背/髋/膝；卧推：肩/肘。
- 像素坐标、45° 方位角校正、可见度阈值、时间平滑。

### 4.4 报告/叠加

- 分析服务一次后台任务：分析 0–75%，overlay 生成 75–100%。
- overlay MP4 = 最关键输出：视频 + 骨架/环点 + 角度标签 + 当前 rep 轨迹（离心/向心双色，rep 间保留，下一次清空）+ 速度胶囊 + 数据卡片。
- 报告视频优先，左列窄卡片；速度/角度曲线有播放游标；轨迹卡无游标。
- JSON/CSV 仅内部验证，不再出现在手机 UI。

## 5. 数据与单位

- UI 片径输入：cm（默认 45）；`AnalysisRequest` 用米。
- 数据库/旧导出：mm；内部计算：米、m/s、m/s²、N、W、J。
- Canonical ID 见 `docs/glossary.md`；JSON key 用 snake_case。
- 时间只用 PTS 秒。

## 6. 线程与生命周期

- `AnalysisForegroundService` 持有分析任务，Activity 可离开/回来。
- 服务保存 running/progress/result/error；binder 允许 Activity 重新附着并恢复状态。
- overlay 生成在 `Dispatchers.IO`；UI 不阻塞。
- 同一 worktree 只允许一个 Gradle 构建（见 `AGENTS.md`）。

## 7. 已知边界

- 单机位 2D，不做 3D/左右对称。
- 45° 关节角为近似校正；卧推肩外展时误差更大。
- 硬拉下放不计次；如需显示为“下放（离心，不计次）”。
- 无真实 ground truth 精度报告；验证见 `docs/status.md`。
