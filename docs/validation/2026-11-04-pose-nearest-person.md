# 2026-11-04 姿态选择杠铃附近人物验证

## 目标

硬拉视频背景中有其他人时，MediaPipe Pose Landmarker 原先只取 `landmarks()[0]`，
可能锁定到背景人物，导致关节角算错人。改为多候选检测 + 按杠铃位置选人。

## 改动

### 1. 接口传入杠铃中心

`app/src/main/java/com/vbtdroid/app/vision/PoseEstimator.kt`

```kotlin
fun estimate(bitmap: Bitmap, tSec: Double, barCenter: Point2? = null): PoseFrame?
```

`barCenter` 使用 `com.vbtdroid.core.metrics.Point2`，单位为帧像素。默认 `null` 保持旧调用兼容。
`DeferredPoseEstimator` 原样转发；`AnalysisForegroundService.NoPoseEstimator` 与测试 fake 同步签名。

### 2. MediaPipe 多候选 + 选择

`app/src/main/java/com/vbtdroid/app/vision/MediaPipePoseEstimator.kt`

- `setNumPoses(3)`，一次可返回最多 3 个候选。
- 每个候选的关键点按 `bitmap.width/height` 转成像素坐标，封装为 `PoseCandidate`。
- 距离门限：`max(width, height) * 0.45`。典型构图中约 2–3 个 plate 直径，躯干中心在此范围内的
  候选才算合理；超出全部拒绝时返回 `null`。
- 选中候选返回原 `PoseFrame`（像素坐标，`normalized = false`）。

### 3. 纯选择策略

新文件 `app/src/main/java/com/vbtdroid/app/vision/PosePersonSelector.kt`

- `PoseCandidate.torsoMidpoint()`：肩中点（11/12）与髋中点（23/24）再取中点；缺少一侧时用另一侧，
  全缺时用所有关键点质心。
- `PoseCandidate.skeletonSizePx()`：关键点包围盒对角线，作为“更大/更近骨架”的代理。
- `PosePersonSelector.select(candidates, barCenter, maxDistancePx)`：
  - `barCenter == null`：取骨架最大者（最大骨架通常离镜头最近）。
  - 有 `barCenter`：按躯干中点到杠铃的像素距离排序；距离差在 `max(4px, 最近距离 × 10%)`
    内视为并列，并列时取骨架更大者，避免左右/前后小幅抖动导致选人跳变。
  - 超过 `maxDistancePx` 的候选剔除；没有候选通过时返回 `null`。

### 4. 管线传入观测中心

`app/src/main/java/com/vbtdroid/app/analysis/AnalysisPipeline.kt`

每帧把当前 `PlateObservation` 的 `(xPx, yPx)` 作为 `barCenter` 传入；本帧跟踪丢失
（`observation == null`）时传 `null`，退回最大骨架策略。姿态仍是 best-effort，不阻断杠铃指标。

## 测试

新增 `app/src/test/java/com/vbtdroid/app/vision/PosePersonSelectorTest.kt`（纯 JVM，7 个用例）：

- 靠近杠铃的小骨架胜过后方大骨架。
- 无 `barCenter` 时选最大骨架。
- 距离接近（100px vs 108px）时大骨架胜出。
- 单候选超出门限时返回 `null`。
- 有合理候选时跳过远处大骨架。
- 缺肩关键点时用髋中点兜底。
- 空候选返回 `null`。

补充：

- `DeferredPoseEstimatorTest` 新增“barCenter 原样转发”（有值为 `Point2(3,4)`，无值为 `null`）。
- `AnalysisPipelineTest` 的 pose fake 记录 `barCenter`，在低置信度/高遮挡用例中断言其等于
  `PlateObservation` 的中心 `x = 16.0`。

## 构建与验证

代码提交：`97386c6`（`fix(pose): select the person nearest the barbell`）。

执行：

```bash
JAVA_HOME=/home/czq/toolchains/jdk17 ANDROID_HOME=/home/czq/toolchains/android-sdk \
  ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

结果：`BUILD SUCCESSFUL`（在 HEAD + 本次改动的干净 worktree 中执行）。

- `:core:test`：93 个测试，0 失败、0 错误、0 跳过。
- `:app:testDebugUnitTest`：109 个测试，0 失败、0 错误、0 跳过。
- `PosePersonSelectorTest`：7 个测试全部通过；`DeferredPoseEstimatorTest`：3 个测试全部通过。
- `:app:assembleDebug`：成功。

## 备注与风险

- 主工作区验证时另外有并行会话正在修改 `app/src/main/java/com/vbtdroid/app/ui/report/*` 与字符串资源，
  主工作区 `compileDebugKotlin` 与其未完成改动冲突/被并发构建抢占；本次改动因此在不含这些文件、
  基于 `2fd76d9` 的干净 worktree 中应用同一 patch 完成完整构建验证。上报前主工作区仍有并行未提交改动，
  未触碰、未提交。
- `setNumPoses(3)` 依赖模型/MediaPipe 对多姿态的支持；编译与单测已通过，尚未在真机视频上做多人场景
  端到端对比（设备/视频不在本步验证范围）。
- 距离门限使用帧尺寸比例而非真实 plate 直径（接口未传 `majorAxisPx`），属于宽松保护而非精确物理门限。
- 无 `barCenter` 帧（跟踪丢失）仍可能选到远处大骨架，属于旧行为兜底；连续丢失时姿态可能短暂跳人。
