# 2026-11-04 视觉跟踪 + 计数调参验证

来源：PC 端离线调参（侧视 720x1280 / 30 fps 视频）+ 代码修复。

## 证据（PC 调参）

### 深蹲（squat, 720x1280, 30fps）
- 杠铃片直径 ~260 px，半径 ~130 px。
- 旧局部搜索半径 `reference.majorAxisPx * 0.9f` = 234 px < 260 px 直径，ROI 比整块片小，
  椭圆拟合只能看到部分弧，中心逐帧漂移。
- 改成 `reference.majorAxisPx * 1.35f` = 351 px，并在 ROI 内加 Hough 圆定位后：6 个干净
  rep，y 曲线平滑。

### 硬拉（deadlift）
- 峰值检测得到 7 个 rep，其中 1 个是刻意做的四分之一拉（quarter pull）：y prominence
  125 px，真实 rep 为 413–431 px。仅靠绝对 ROM 下限过滤不干净，需要相对（集合中位数）过滤。

## 代码改动

### 1. `app/.../vision/PlateTracker.kt`

| 参数 | 旧值 | 新值 |
| --- | --- | --- |
| 局部搜索 ROI 半径 | `majorAxisPx * 0.9f` | `majorAxisPx * 1.35f`（`PlateRoi.localSearchRadiusPx`） |
| Hough `dp` | — | `1.2` |
| Hough `minDist` | — | `max(8.0, r * 0.5)` |
| Hough `param1` | — | `120.0` |
| Hough `param2` | — | `45.0` |
| Hough min/max 半径 | — | `0.85r .. 1.15r`（`r = majorAxisPx / 2`） |
| 半径一致性拒绝阈值 | — | 偏离参考半径 > 15% 拒绝 |
| 圆心跳变拒绝阈值 | — | 距预测中心 > `PlateRoi.maxInnovationPx()`；默认 0.5 直径，慢帧率时按 8 直径/秒放宽 |

流程：
1. PyrLK 光流 / 速度预测得到 `predicted`。
2. 在 `predicted` 周围以 1.35×直径 为半径做 Hough 圆检测（`findHoughCircle`）。
3. 在 Hough ROI 内按半径 ±15% 过滤，且圆心必须落在物理 innovation gate 内。
4. 用 Hough 圆心作为 `findEllipse` 的种子（ROI 同样重定心到 Hough 圆心）。
5. 椭圆拟合成功、置信度 ≥ 0.25、半径 ≤15%、圆心通过 innovation gate → 用椭圆。
6. 椭圆失败但 Hough 圆有效 → 用 Hough 圆心，`majorAxisPx` 仍取参考轴，置信度由半径一致性 +
   与预测点距离计算（最低 0.25）。仍要求 `axisReference` 存在。
7. 以上都失败才走检测器恢复路径；检测器依旧只用于 init/recovery。
8. 所有 `Mat`（crop/blurred/circles/gray）均在 `finally` 中释放；Hough 异常被吞掉并返回 null。

辅助函数 `PlateRoi.localSearchRadiusPx(referenceMajorAxisPx)`、`PlateRoi.maxInnovationPx(axis, dt)`
抽出半径与物理门限计算，便于单测；非法参考值（≤0 / NaN）返回 0。
`PlateAxisReference` 默认容差同步收紧为 15%（原 25%）。

### 2. 离线平滑 `core/.../signal/TrackSmoother.kt`（新增）

应用场景是专门拍摄的单目侧视视频（杠铃片无遮挡、机位受控、杠铃平滑），因此不做完整
forward-backward Kalman/RTS，改成整条轨迹上的三段鲁棒处理：

1. **半径门**：以整轨直径中位数为参考，丢弃偏离 >15% 的帧（错误目标 / 局部弧拟合）。
2. **Innovation 门**：用上一接受帧 + 速度预测，跳变超过
   `max(0.5 直径, 8 直径/秒 × dt)` 的帧丢弃，挡 teleport / 身份切换。30 fps 下 0.5 直径下限
   远大于合理的加速度项，因此门限按速度建模，不单列加速度项。
3. **Hampel + Savitzky-Golay**：Hampel（窗口 5，3σ，1px 下限）把单帧尖峰替换为局部中位数，
   再用零相位 SG（窗口 5，2 阶）平滑 x/y。SG 对称所以等价于 forward-backward 平滑，且比
   移动平均更保峰值。

少于 3 个有效样本时原样返回，不做压缩或假平滑。`AnalysisPipeline` 在标定和
`Kinematics.derive` 之前调用一次，单帧坏点不会进入指标。时间戳与置信度保留，只改 x/y。

### 3. `core/.../phases/PhaseSegmenter.kt`

| 参数 | 旧值 | 新值 |
| --- | --- | --- |
| `PhaseConfig.minRomM` 默认值 | `0.10` m | `0.20` m（仍可配置） |
| rep 相对 ROM 过滤 | 无 | `>=3` 个候选时，`ROM < max(minRomM, 0.5 * medianROM)` 丢弃 |
| 丢弃告警 | 无 | `REP_ROM_OUTLIER`（`PhaseSegmentation.warnings`） |

未改动：`minRepSec = 0.45`、`minConcentricSec = 0.15`、加速度/速度转向判定、
0.25 s 同向暂停合并、lockout 规则。

四分之一拉样例：ROM 0.22 m，真实 rep ROM 0.50 m → 阈值 `max(0.20, 0.25) = 0.25` →
四分之一拉被丢弃，6 个真实 rep 全部保留。候选数 < 3 时不做中位数过滤（避免一两个 rep 的
集合被误杀）。

### 4. rep 检测依据

`Kinematics.derive` 已经先对 y 做局部多项式（零相位）平滑，`PhaseSegmenter` 在其上按
速度零交叉判定同心/离心，`validRep` 再要求最短时长、同心时长、ROM、以及
`hasTurnaround`（加速度变号 / 速度变号 / 峰值后减速，即平滑 y 的极值 turnaround）。
本次仅新增中位数 ROM 过滤，**没有**替换现有 phase 模型，相位边界仍由原阈值分段给出。

## PC 视频回归

仓库内 `prototype/scripts/bench_track.py` 用 OpenCV 复现同一套门限（Hough 1.35× 直径、
半径 15%、innovation 门、Hampel+SG、ROM/中位数过滤），跑 `example/` 两个原始视频：

```
cd example
python /path/to/prototype/scripts/bench_track.py \
  "高杠深蹲-135kg-6次-侧面90度.mp4" \
  "传统硬拉-100kg-6次-第二次和第三次中间有四分之一次没完成拉起.mp4"
```

结果（720x1280, 30fps）：

| 视频 | 跟踪命中 | 半径 CV | 峰值候选 | 保留 | 期望 |
| --- | --- | --- | --- | --- | --- |
| 高杠深蹲 | 100.0% | 7.28% | 10 | **6** | 6 |
| 传统硬拉 | 99.9% | 4.88% | 12 | **6** | 6 |

- 深蹲：真实 rep ROM 0.75–0.89 m；4 个 ~0.05 m 的抖动段被中位数阈值（0.387 m）清掉。
- 硬拉：真实 rep ROM 0.62–0.65 m；四分之一拉 ROM 0.204 m → 阈值 0.32 m 丢弃；
  另有若干 0.02–0.03 m 噪声段丢弃。
- 该脚本是 PC 参考实现（numpy/opencv），不等同于 APK 内 OpenCV 路径；用于验证参数与
  规则，不替代真机回归。

## 测试

```
JAVA_HOME=~/toolchains/jdk17 \
ANDROID_HOME=~/toolchains/android-sdk \
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

结果：BUILD SUCCESSFUL（本机 WSL，非服务器）。未安装 APK（手机可能离线）。

新增/更新用例：
- `PhaseSegmenterTest.travel below the app ROM floor is rejected`：0.15 m 在旧 0.10 m 默认下
  会通过，现在被拒。
- `PhaseSegmenterTest.quarter pull is dropped while six true deadlift reps survive`：
  1 个四分之一拉 + 6 个真实硬拉 → 6 reps，含 `REP_ROM_OUTLIER`，索引连续 1..6。
- `PhaseSegmenterTest.median ROM filter waits for at least three candidates`：2 个候选不触发
  中位数过滤。
- `PlateRoiTest.local search radius covers the whole plate diameter plus margin`：
  260 px 直径 → 351 px 半径 > 260 px。
- `PlateRoiTest.local search radius rejects an invalid reference`。
- `PlateRoiTest.innovation gate scales with frame gap but never below half a plate`。
- `PlateRoiTest.accepted observations never move the initialization diameter reference`：
  容差改为 15%（205 px 过，210 px 拒）。
- `TrackSmootherTest`：单帧 teleport 被剔除、半径离群帧被剔除、线性轨迹亚像素保持、
  短轨迹原样返回、非有限值丢弃。

## 未覆盖 / 后续（deferred）

- **真机 OpenCV 回归**：Hough/椭圆路径依赖 Android OpenCV，本次只有 PC 参考实现 + 单测。
- **每帧 Hough 成本**：ROI ~700x700 上每帧一次 HoughCircles，离线分析可接受，但未测手机
  耗时/发热；若过慢，可改为“椭圆连续 N 帧成功后隔帧跑 Hough”。
- **完整 RTS**：用 Hampel + 零相位 SG 代替，已文档化；若以后需要在线逐帧输出再上 Kalman/RTS。
- **Pose 近侧 4–6 点**：本次未动 Pose（不在任务文件范围）。现有 `JointAngleSmoother` 仅为
  角度中值平滑；近侧点裁剪与轻量模型另开任务。
- `REP_ROM_OUTLIER` 目前落到 `ReportFormat.qualityWarningText` 的 `else -> quality_unknown`，
  文案需要 UI 侧补 string（本次按要求未改 UI 文件）。
- 20 帧/秒以下或严重运动模糊的视频，Hough `param2 = 45` 可能偏严，必要时按分辨率再调。
- `prototype/scripts/bench_track.py` 的首帧初始化用候选圆短序列跟踪打分（自动代替人工 ROI），
  与 App 的手动 ROI/检测器初始化不同；不应把它的 hit rate 当作 App 指标。
