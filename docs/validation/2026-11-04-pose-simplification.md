# 2026-11-04 姿态关节角按动作精简验证

## 目标

VBTdroid 当前固定计算髋、膝、踝、躯干、肩、肘、腕七个角度。受控侧视场景下，
每种动作只需要与动作相关的关节，减少无效输出和误导性的置信度。

## 改动

文件：`app/src/main/java/com/vbtdroid/app/analysis/JointAngleExtractor.kt`

按 `Lift` 分支计算：

| 动作 | 计算角度 | 保持 null 的角度 |
| --- | --- | --- |
| 深蹲 `SQUAT` | 躯干、髋、膝、踝（`trunk_angle_deg`、`hip_angle_deg`、`knee_angle_deg`、`ankle_angle_deg`） | 肩、肘、腕 |
| 硬拉 `DEADLIFT` | 躯干、髋、膝、踝（同上） | 肩、肘、腕 |
| 卧推 `BENCH` | 躯干、肩、肘（`trunk_angle_deg`、`shoulder_angle_deg`、`elbow_angle_deg`） | 髋、膝、踝、腕 |

保留的行为：

- 近侧选取：左右侧按关键点平均可见度选择。
- 可见度阈值：默认 `minVisibility = 0.5f`。
- 像素坐标：`PoseFrame.pixelPoint` 做归一化坐标到像素的宽高比校正。
- 方位角校正：所有角度仍经 `JointAngles.correctAzimuth`。
- `confidenceByJoint`：只包含实际计算成功的相关关节。
- `confidence`：取参与已计算角度的关键点可见度最小值；没有计算成功角度时为 `0.0`，
  不填充伪造可见度。
- `FrameAngles` 字段和 metrics 类型未改动。

## 测试

新增到 `app/src/test/java/com/vbtdroid/app/analysis/JointAngleExtractorTest.kt`：

- 深蹲：髋、膝、踝、躯干非空；肩、肘、腕为 null；`confidenceByJoint` 键限定为四个相关角度。
- 硬拉：同深蹲。
- 卧推：肩、肘、躯干非空；髋、膝、踝、腕为 null；`confidenceByJoint` 键限定为三个相关角度。

原有 4 个 `JointAngleExtractorTest` 用例保持不变并通过，包括缺失膝盖时
`confidence = 0.9`、方位角校正、归一化像素宽高比。

## 构建与验证

代码提交：`a231209`（`refactor(pose): compute only lift-relevant joint angles`）。

执行：

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

结果：`BUILD SUCCESSFUL`。

- `:core:test`：16 个测试套件，90 个测试，0 失败、0 错误、0 跳过。
- `:app:testDebugUnitTest`：19 个测试套件，92 个测试，0 失败、0 错误、0 跳过。
- `JointAngleExtractorTest`：7 个测试，全部通过。
- `:app:assembleDebug`：成功。

执行环境补充：需设置 `JAVA_HOME=/home/czq/toolchains/jdk17` 与
`ANDROID_HOME=/home/czq/toolchains/android-sdk`。

## 备注

- 侧别选择仍基于该侧全部 8 个关键点的平均可见度，未按动作再细分；受控侧视下不影响本次目标。
- 工作区开始时已有的 `VideoOverlayExporter.kt` 修改与 `EncoderSelectionTest.kt` 新增文件不属于本次改动；
  它们由并行提交 `7a9f866`（`perf(overlay): prefer hardware AVC encoder`）处理。
