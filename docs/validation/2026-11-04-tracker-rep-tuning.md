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
| 圆心跳变拒绝阈值 | — | 距预测中心 > 2r 拒绝 |

流程：
1. PyrLK 光流 / 速度预测得到 `predicted`。
2. 在 `predicted` 周围以 1.35×直径 为半径做 Hough 圆检测（`findHoughCircle`）。
3. 用 Hough 圆心作为 `findEllipse` 的种子（ROI 同样重定心到 Hough 圆心）。
4. 椭圆拟合成功且置信度 ≥ 0.25 → 用椭圆。
5. 椭圆失败但 Hough 圆有效 → 用 Hough 圆心，`majorAxisPx` 仍取参考轴，置信度由半径一致性 +
   与预测点距离计算（最低 0.25）。仍要求 `axisReference` 存在。
6. 以上都失败才走检测器恢复路径；检测器依旧只用于 init/recovery。
7. 所有 `Mat`（crop/blurred/circles/gray）均在 `finally` 中释放；Hough 异常被吞掉并返回 null。

辅助函数 `PlateRoi.localSearchRadiusPx(referenceMajorAxisPx)` 抽出半径计算，便于单测；
非法参考值（≤0 / NaN）返回 0。

### 2. `core/.../phases/PhaseSegmenter.kt`

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

## 未覆盖 / 后续

- Hough / 椭圆路径依赖 OpenCV 真机渲染，单测只覆盖 ROI 半径数学；实际视频回归需在设备或
  PC 脚本上重跑。
- `REP_ROM_OUTLIER` 目前落到 `ReportFormat.qualityWarningText` 的 `else -> quality_unknown`，
  文案需要 UI 侧补 string（本次按要求未改 UI 文件）。
- 20 帧/秒以下或严重运动模糊的视频，Hough `param2 = 45` 可能偏严，必要时按分辨率再调。
