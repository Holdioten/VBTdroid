# VBTdroid MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 端离线 VBT 分析 App：导入深蹲/卧推/硬拉视频，输出速度、轨迹、相位、关节角、负荷与 1RM 指标。

**Architecture:** `core` 是纯 Kotlin/JVM 指标引擎（标定、滤波、相位、指标、LVP、JSON），JUnit 5 测试；`app` 是 Android 适配层（SAF 导入、MediaCodec 解码、OpenCV 椭圆跟踪、TFLite 检测、MediaPipe 姿态、Compose 报告、Room 历史）；`prototype` 是 Python 参考实现，用于算法验证和 golden vectors。

**Tech Stack:** Kotlin 2.0.x、AGP 8.7.x、JDK 17、Jetpack Compose、OpenCV Android、TensorFlow Lite、MediaPipe Tasks Vision、Room、kotlinx.serialization、JUnit 5；Python 3.11 + OpenCV + NumPy/SciPy + pytest。

**Spec:** `docs/spec.md`

## Global Constraints

- `minSdk 26`，`targetSdk 35`，`compileSdk 35`，JDK 17。
- `core` 不依赖 Android、OpenCV、TFLite、MediaPipe。
- 所有时间来自视频 PTS，单位秒；禁止按帧计数推时间。
- 所有指标必须允许 `null` 表示不可用；禁止用 0 或假值填充。
- 所有指标必须带质量标记/不可用原因。
- App 不申请网络权限。
- UI 文案中文；代码标识符英文。
- 模型权重不进 git；`ml/download_models.sh` 下载并校验 SHA256。
- 检测器发布许可：YOLOX-Nano/NanoDet-Plus（Apache-2.0）优先；YOLOv8n/YOLO11n 仅限个人 AGPL 场景。
- 每个任务结束必须能独立运行测试并提交。

## Review Focus

- **VFR/慢动作时间戳**：容器帧率 30 但实际 120/240fps 时，按帧计数算速度会错 4–8 倍。用 PTS，并在 Task 9 测试非均匀时间戳。
- **相机俯仰/横滚**：45° 校正假设相机水平。俯仰会让垂直速度混入水平分量；Task 2/3 测试 `cameraLevel=false` 的降级路径。
- **卧推底部遮挡**：片被胸/臂遮挡时跟踪器可能跳到内圈，直径骤变导致尺度错误。Task 10 必须测试内圈误检拒绝。
- **姿态关键点遮挡**：片/杠铃挡髋膝时 MediaPipe 会猜。Task 11/12 必须输出低置信并允许关节角为 `null`。
- **单组缺负荷/多组缺 LVP**：力/功率/1RM 不能静默变 0。Task 5/6 测试 `loadKg=null` 和 `<3` 负荷点时返回 `null` + 警告。

---

### Task 1: Gradle 多模块骨架

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `core/build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/vbtdroid/app/MainActivity.kt`
- Create: `core/src/test/kotlin/com/vbtdroid/core/SmokeTest.kt`
- Create: `.gitignore`
- Create: `README.md`

**Interfaces:**
- Consumes: 无。
- Produces: Gradle modules `:core`、`:app`；`./gradlew :core:test` 和 `./gradlew :app:assembleDebug` 可运行。

- [ ] **Step 1: 写 Gradle 骨架**

`settings.gradle.kts` 包含 `:app`、`:core`，仓库只开 `google()`、`mavenCentral()`、`gradlePluginPortal()`。  
`gradle/libs.versions.toml` 固定基线版本：AGP `8.7.3`、Kotlin `2.0.21`、KSP `2.0.21-1.0.28`、Compose BOM `2024.10.01`、JUnit `5.11.0`、kotlinx.serialization `1.7.3`、kotlinx-coroutines-test `1.9.0`、Robolectric `4.13`、Room `2.6.1`、OpenCV `4.10.0`、TFLite `2.16.1`、MediaPipe Tasks Vision `0.10.14`。  
`core` 用 `kotlin("jvm")` + serialization + JUnit 5。`app` 用 Android application + Compose + KSP，并应用 `org.jetbrains.kotlin.plugin.compose`。  
`MainActivity` 只显示 `Text("VBTdroid")`。  
`.gitignore` 覆盖 `.gradle/`、`build/`、`local.properties`、`.idea/`、`*.iml`、`app/src/main/assets/models/*.tflite`、`*.task`、`prototype/.venv/`、`__pycache__/`、`.pytest_cache/`。

- [ ] **Step 2: 生成 wrapper**

Run: `gradle wrapper --gradle-version 8.10.2`  
Expected: 生成 `gradlew`、`gradle/wrapper/*`。

- [ ] **Step 3: 跑 core 测试**

Run: `./gradlew :core:test`  
Expected: `BUILD SUCCESSFUL`；`SmokeTest` 通过。

- [ ] **Step 4: 编 app**

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`；产出 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "chore: scaffold Gradle multi-module project"
```

---

### Task 2: Core 类型与标定

**Files:**
- Create: `core/src/main/kotlin/com/vbtdroid/core/model/Types.kt`
- Create: `core/src/main/kotlin/com/vbtdroid/core/calibration/Calibration.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/calibration/CalibrationTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  ```kotlin
  enum class Lift { SQUAT, BENCH, DEADLIFT }

  data class PlateObservation(
      val tSec: Double,
      val xPx: Double,
      val yPx: Double,
      val majorAxisPx: Double,
      val confidence: Double
  )

  data class BarSample(val tSec: Double, val xM: Double, val yM: Double)

  data class Calibration(
      val plateDiameterM: Double,
      val pxPerM: Double,
      val cameraAngleDeg: Double,
      val cameraLevel: Boolean = true
  ) {
      fun toMeters(observation: PlateObservation): BarSample
      companion object {
          fun fromPlate(plateDiameterM: Double, majorAxisPx: Double, cameraAngleDeg: Double): Calibration
      }
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class CalibrationTest {
    @Test
    fun `450mm plate at 225px gives 500 px per metre`() {
        val c = Calibration.fromPlate(0.45, 225.0, 90.0)
        assertEquals(500.0, c.pxPerM, 1e-9)
    }

    @Test
    fun `45 degree camera expands horizontal displacement`() {
        val c = Calibration.fromPlate(0.45, 225.0, 45.0)
        val s = c.toMeters(PlateObservation(0.0, 100.0, -50.0, 225.0, 1.0))
        assertEquals(0.282842712, s.xM, 1e-6)
        assertEquals(0.1, s.yM, 1e-9)
    }

    @Test
    fun `rejects invalid plate geometry`() {
        assertThrows(IllegalArgumentException::class.java) {
            Calibration.fromPlate(0.0, 225.0, 45.0)
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*CalibrationTest*'`  
Expected: FAIL，`Calibration` 未定义。

- [ ] **Step 3: 实现**

`toMeters`：`x = (xPx / pxPerM) / sin(cameraAngleDeg)`，`y = -yPx / pxPerM`。  
`fromPlate`：校验 `plateDiameterM > 0`、`majorAxisPx > 0`、`cameraAngleDeg in (0, 90]`，`pxPerM = majorAxisPx / plateDiameterM`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*CalibrationTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/kotlin/com/vbtdroid/core/model/Types.kt core/src/main/kotlin/com/vbtdroid/core/calibration/Calibration.kt core/src/test/kotlin/com/vbtdroid/core/calibration/CalibrationTest.kt
git commit -m "feat(core): add bar sample types and plate calibration"
```

---

### Task 3: 滤波与运动学

**Files:**
- Create: `core/src/main/kotlin/com/vbtdroid/core/signal/SavitzkyGolay.kt`
- Create: `core/src/main/kotlin/com/vbtdroid/core/metrics/Kinematics.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/metrics/KinematicsTest.kt`

**Interfaces:**
- Consumes: `BarSample`。
- Produces:
  ```kotlin
  object SavitzkyGolay {
      fun smooth(values: DoubleArray, window: Int = 7, polyOrder: Int = 2): DoubleArray
  }

  data class MotionSample(
      val tSec: Double,
      val xM: Double,
      val yM: Double,
      val vxMps: Double,
      val vyMps: Double,
      val axMps2: Double,
      val ayMps2: Double
  )

  object Kinematics {
      fun derive(samples: List<BarSample>, window: Int = 7, polyOrder: Int = 2): List<MotionSample>
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class KinematicsTest {
    @Test
    fun `constant position gives zero velocity`() {
        val samples = (0..20).map { BarSample(it * 0.016, 0.0, 0.5) }
        val out = Kinematics.derive(samples)
        assertTrue(out.all { abs(it.vyMps) < 1e-6 })
    }

    @Test
    fun `linear position gives constant velocity`() {
        val samples = (0..20).map { BarSample(it * 0.05, 0.0, 0.5 * it * 0.05) }
        val out = Kinematics.derive(samples)
        assertTrue(out.drop(3).dropLast(3).all { abs(it.vyMps - 0.5) < 1e-3 })
    }

    @Test
    fun `quadratic position gives constant acceleration`() {
        val samples = (0..20).map { BarSample(it * 0.05, 0.0, 0.5 * (it * 0.05) * (it * 0.05)) }
        val out = Kinematics.derive(samples)
        assertTrue(out.drop(3).dropLast(3).all { abs(it.ayMps2 - 1.0) < 0.05 })
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*KinematicsTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

`SavitzkyGolay.smooth` 用局部多项式最小二乘：对每个点取窗口，时间归一化，拟合 2 阶多项式，取中心值。  
`Kinematics.derive`：先分别平滑 `xM`、`yM`；对每个点用同一局部多项式拟合求一阶/二阶导数；窗口不足时用可用点。  
不要用固定 fps；所有差分用实际 `tSec`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*KinematicsTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/kotlin/com/vbtdroid/core/signal core/src/main/kotlin/com/vbtdroid/core/metrics/Kinematics.kt core/src/test/kotlin/com/vbtdroid/core/metrics/KinematicsTest.kt
git commit -m "feat(core): add Savitzky-Golay smoothing and kinematics"
```

---

### Task 4: 相位分割

**Files:**
- Create: `core/src/main/kotlin/com/vbtdroid/core/phases/PhaseSegmenter.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/phases/PhaseSegmenterTest.kt`

**Interfaces:**
- Consumes: `MotionSample`、`Lift`。
- Produces:
  ```kotlin
  enum class PhaseType { ECCENTRIC, PAUSE, CONCENTRIC, LOCKOUT }

  data class Phase(
      val type: PhaseType,
      val startSec: Double,
      val endSec: Double,
      val manual: Boolean = false
  )

  data class RepSlice(
      val index: Int,
      val startSec: Double,
      val endSec: Double,
      val phases: List<Phase>
  )

  data class PhaseConfig(
      val vEps: Double = 0.03,
      val minPhaseSec: Double = 0.12,
      val lockoutSec: Double = 0.10
  )

  object PhaseSegmenter {
      fun segment(
          samples: List<MotionSample>,
          lift: Lift,
          config: PhaseConfig = PhaseConfig()
      ): List<RepSlice>
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class PhaseSegmenterTest {
    private fun sample(t: Double, vy: Double) =
        MotionSample(t, 0.0, 0.0, 0.0, vy, 0.0, 0.0)

    @Test
    fun `squat rep has eccentric pause concentric lockout`() {
        val s = buildList {
            for (i in 0..30) add(sample(i * 0.05, -0.5))
            for (i in 31..35) add(sample(i * 0.05, 0.0))
            for (i in 36..66) add(sample(i * 0.05, 0.5))
            for (i in 67..72) add(sample(i * 0.05, 0.0))
        }
        val reps = PhaseSegmenter.segment(s, Lift.SQUAT)
        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC, PhaseType.LOCKOUT),
            reps[0].phases.map { it.type }
        )
    }

    @Test
    fun `deadlift starts at concentric`() {
        val s = buildList {
            for (i in 0..10) add(sample(i * 0.05, 0.0))
            for (i in 11..40) add(sample(i * 0.05, 0.5))
            for (i in 41..46) add(sample(i * 0.05, 0.0))
        }
        val reps = PhaseSegmenter.segment(s, Lift.DEADLIFT)
        assertEquals(1, reps.size)
        assertEquals(PhaseType.CONCENTRIC, reps[0].phases.first().type)
    }

    @Test
    fun `short spikes are ignored`() {
        val s = buildList {
            for (i in 0..10) add(sample(i * 0.05, 0.0))
            add(sample(0.55, 0.5))
            for (i in 12..20) add(sample(i * 0.05, 0.0))
        }
        assertTrue(PhaseSegmenter.segment(s, Lift.DEADLIFT).isEmpty())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*PhaseSegmenterTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

按 `vyMps` 分类：`< -vEps` 离心，`> +vEps` 同心，其余停顿。滞回：进入新状态需超过阈值，退出需回到阈值内。  
合并短于 `minPhaseSec` 的连续段到相邻段。  
深蹲/卧推：首个离心开始为 rep 起点；同心后持续 `lockoutSec` 的近零速度为锁定。  
硬拉：首个同心开始为 rep 起点；无离心。  
忽略持续时间短于 `minPhaseSec` 的孤立 spike。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*PhaseSegmenterTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/kotlin/com/vbtdroid/core/phases core/src/test/kotlin/com/vbtdroid/core/phases
git commit -m "feat(core): add phase segmentation for squat bench deadlift"
```

---

### Task 5: Rep 指标

**Files:**
- Create: `core/src/main/kotlin/com/vbtdroid/core/metrics/RepMetrics.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/metrics/RepMetricsTest.kt`

**Interfaces:**
- Consumes: `RepSlice`、`MotionSample`。
- Produces:
  ```kotlin
  data class RepMetrics(
      val repIndex: Int,
      val durationS: Double,
      val romTotalM: Double,
      val romConcentricM: Double,
      val meanVelocityMps: Double,
      val mcvMps: Double,
      val mpvMps: Double,
      val peakVelocityMps: Double,
      val peakAccelerationMps2: Double,
      val peakDecelerationMps2: Double,
      val stickingPointPct: Double?,
      val velocityLossPct: Double?,
      val barPathHorizontalRangeM: Double,
      val barPathDeviationM: Double,
      val meanForceN: Double?,
      val peakForceN: Double?,
      val meanPowerW: Double?,
      val peakPowerW: Double?,
      val impulseNs: Double?,
      val workJ: Double?
  )

  object RepMetricsCalculator {
      fun compute(
          rep: RepSlice,
          samples: List<MotionSample>,
          loadKg: Double?,
          bestMcvMps: Double? = null
      ): RepMetrics
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class RepMetricsTest {
    private fun motion(t: Double, y: Double, vy: Double, ay: Double = 0.0) =
        MotionSample(t, 0.0, y, 0.0, vy, 0.0, ay)

    @Test
    fun `constant concentric velocity gives expected mcv and rom`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.5, 0.5) }
        val rep = RepSlice(0, 0.0, 1.0, listOf(Phase(PhaseType.CONCENTRIC, 0.0, 1.0)))
        val m = RepMetricsCalculator.compute(rep, samples, null)
        assertEquals(0.5, m.mcvMps, 1e-6)
        assertEquals(0.5, m.romConcentricM, 1e-6)
        assertEquals(0.5, m.peakVelocityMps, 1e-6)
        assertNull(m.meanForceN)
    }

    @Test
    fun `velocity loss uses best rep`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.6, 0.6) }
        val rep = RepSlice(2, 0.0, 1.0, listOf(Phase(PhaseType.CONCENTRIC, 0.0, 1.0)))
        val m = RepMetricsCalculator.compute(rep, samples, null, bestMcvMps = 0.8)
        assertEquals(25.0, m.velocityLossPct!!, 1e-6)
    }

    @Test
    fun `load metrics use external load`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.5, 0.5) }
        val rep = RepSlice(0, 0.0, 1.0, listOf(Phase(PhaseType.CONCENTRIC, 0.0, 1.0)))
        val m = RepMetricsCalculator.compute(rep, samples, loadKg = 100.0)
        assertEquals(980.665, m.meanForceN!!, 1e-3)
        assertEquals(490.3325, m.meanPowerW!!, 1e-3)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*RepMetricsTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

按 rep 时间窗截取样本。`romTotalM = max(y) - min(y)`；`romConcentricM` 只在同心相位内。  
`mcv = Δy / Δt`，`meanVelocity = 全 rep Δy / Δt`。  
`mpv`：从同心开始到首个 `ay < 0` 的时间点；无负加速则等于 `mcv`。  
`stickingPointPct`：同心段峰值后首个 `vy` 局部最小；找不到返回 `null`。  
`velocityLossPct`：`bestMcvMps` 为 null 时返回 null。  
力/功率/冲量/功仅在 `loadKg != null && loadKg > 0` 时计算，否则全部 null。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*RepMetricsTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/kotlin/com/vbtdroid/core/metrics/RepMetrics.kt core/src/test/kotlin/com/vbtdroid/core/metrics/RepMetricsTest.kt
git commit -m "feat(core): compute per-rep kinematics and load metrics"
```

---

### Task 6: LVP 与 1RM

**Files:**
- Create: `core/src/main/kotlin/com/vbtdroid/core/metrics/Lvp.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/metrics/LvpTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  ```kotlin
  data class LvpPoint(val loadKg: Double, val mcvMps: Double)

  data class LvpModel(
      val slope: Double,
      val intercept: Double,
      val r2: Double,
      val v1rmMps: Double,
      val estimated1RmKg: Double
  )

  object Lvp {
      fun fit(points: List<LvpPoint>, v1rmMps: Double): LvpModel
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class LvpTest {
    @Test
    fun `linear fit and 1rm`() {
        val m = Lvp.fit(
            listOf(LvpPoint(100.0, 0.8), LvpPoint(120.0, 0.65), LvpPoint(140.0, 0.5)),
            v1rmMps = 0.3
        )
        assertEquals(-0.0075, m.slope, 1e-9)
        assertEquals(1.55, m.intercept, 1e-9)
        assertEquals(1.0, m.r2, 1e-9)
        assertEquals(166.666666, m.estimated1RmKg, 1e-4)
    }

    @Test
    fun `requires three distinct loads`() {
        assertThrows(IllegalArgumentException::class.java) {
            Lvp.fit(listOf(LvpPoint(100.0, 0.8), LvpPoint(100.0, 0.7)), 0.3)
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*LvpTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

最小二乘拟合 `v = intercept + slope*load`。校验 ≥3 个点、至少 2 个不同负荷、`v1rmMps > 0`、`slope < 0`。  
`estimated1RmKg = (v1rmMps - intercept) / slope`。  
`r2 = 1 - SSres/SStot`；`SStot=0` 时 `r2=1`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*LvpTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/kotlin/com/vbtdroid/core/metrics/Lvp.kt core/src/test/kotlin/com/vbtdroid/core/metrics/LvpTest.kt
git commit -m "feat(core): add load-velocity profile and 1RM estimate"
```

---

### Task 7: 关节角、质量标记、JSON 导出

**Files:**
- Create: `core/src/main/kotlin/com/vbtdroid/core/metrics/JointAngles.kt`
- Create: `core/src/main/kotlin/com/vbtdroid/core/quality/Quality.kt`
- Create: `core/src/main/kotlin/com/vbtdroid/core/io/JsonExport.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/metrics/JointAnglesTest.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/io/JsonExportTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  ```kotlin
  data class Point2(val x: Double, val y: Double)

  object JointAngles {
      fun angle(a: Point2, b: Point2, c: Point2): Double
      fun correctAzimuth(imageAngleDeg: Double, cameraAngleDeg: Double): Double
  }

  data class QualityFlags(
      val trackConfidence: Double,
      val occlusionPct: Double,
      val repConfidence: Double,
      val calibrationConfidence: Double,
      val cameraLevel: Boolean,
      val warnings: List<String>
  )

  object Quality {
      fun evaluate(
          trackConfidence: Double,
          occlusionPct: Double,
          repConfidence: Double,
          calibrationConfidence: Double,
          cameraLevel: Boolean
      ): QualityFlags
  }

  @Serializable
  data class RepExport(
      val index: Int,
      val metrics: Map<String, Double?>,
      val quality: Map<String, Double>
  )

  @Serializable
  data class SetExport(
      val schemaVersion: Int = 1,
      val lift: String,
      val loadKg: Double?,
      val plateDiameterMm: Double,
      val cameraAngleDeg: Double,
      val reps: List<RepExport>,
      val warnings: List<String>
  )

  object JsonExport {
      fun encode(export: SetExport): String
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class JointAnglesTest {
    @Test
    fun `right angle`() {
        val a = Point2(0.0, 0.0); val b = Point2(1.0, 0.0); val c = Point2(1.0, 1.0)
        assertEquals(90.0, JointAngles.angle(a, b, c), 1e-9)
    }

    @Test
    fun `45 degree image angle at 45 degree camera corrects to 45`() {
        val image = 35.264389
        assertEquals(45.0, JointAngles.correctAzimuth(image, 45.0), 1e-6)
    }

    @Test
    fun `side view does not change angle`() {
        assertEquals(60.0, JointAngles.correctAzimuth(60.0, 90.0), 1e-9)
    }
}
```

```kotlin
class JsonExportTest {
    @Test
    fun `round trip`() {
        val export = SetExport(
            lift = "squat", loadKg = 100.0, plateDiameterMm = 450.0,
            cameraAngleDeg = 45.0, reps = emptyList(), warnings = listOf("VFR")
        )
        val json = JsonExport.encode(export)
        val back = Json.decodeFromString<SetExport>(json)
        assertEquals(export, back)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*JointAnglesTest*' --tests '*JsonExportTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

`angle` 用 `acos` 夹紧 `[-1,1]`，退化向量返回 `0.0`。  
`correctAzimuth`：`atan(tan(image) / sin(camera))`，角度制；`camera=90` 原样返回；`image>90` 先按 `180-image` 校正，再取 `180-result`；`image=90` 返回 90。  
`Quality`：`trackConfidence<0.6` 加 `LOW_TRACK_CONFIDENCE`；`occlusionPct>20` 加 `HIGH_OCCLUSION`；`!cameraLevel` 加 `CAMERA_NOT_LEVEL`；`calibrationConfidence<0.5` 加 `LOW_CALIBRATION`。  
`JsonExport` 用 `kotlinx.serialization.Json { prettyPrint = true }`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*JointAnglesTest*' --tests '*JsonExportTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/kotlin/com/vbtdroid/core/metrics/JointAngles.kt core/src/main/kotlin/com/vbtdroid/core/quality core/src/main/kotlin/com/vbtdroid/core/io core/src/test/kotlin/com/vbtdroid/core
git commit -m "feat(core): add joint angles quality flags and JSON export"
```

---

### Task 8: Python 参考实现与 golden vectors

**Files:**
- Create: `prototype/pyproject.toml`
- Create: `prototype/vbtdroid_proto/__init__.py`
- Create: `prototype/vbtdroid_proto/calibration.py`
- Create: `prototype/vbtdroid_proto/tracking.py`
- Create: `prototype/vbtdroid_proto/phases.py`
- Create: `prototype/vbtdroid_proto/metrics.py`
- Create: `prototype/vbtdroid_proto/report.py`
- Create: `prototype/scripts/analyze_video.py`
- Test: `prototype/tests/test_pipeline.py`
- Create: `prototype/golden/synthetic_squat.json`

**Interfaces:**
- Consumes: 无。
- Produces: `python -m vbtdroid_proto` 可分析合成视频并输出与 `SetExport` 同构的 JSON；golden JSON 供 Kotlin core 对照。

- [ ] **Step 1: 写失败测试**

```python
def test_synthetic_circle_video(tmp_path):
    video = tmp_path / "squat.mp4"
    write_synthetic_video(video, frames=60, radius=45, y0=300, y1=200)
    result = analyze_video(video, plate_diameter_m=0.45, camera_angle_deg=90, fps=30)
    assert result["reps"]
    assert result["reps"][0]["metrics"]["mcvMps"] > 0.3
    assert result["warnings"] == []
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd prototype && python -m pytest tests/test_pipeline.py -q`  
Expected: FAIL，`analyze_video` 未定义。

- [ ] **Step 3: 实现**

`tracking.py`：`cv2.HoughCircles` 或阈值轮廓 + `cv2.fitEllipse`；首帧自动选最大圆；逐帧跟踪最近圆。  
`calibration.py`：与 Kotlin `Calibration` 相同公式。  
`phases.py`：与 Kotlin `PhaseSegmenter` 相同阈值/最短时长。  
`metrics.py`：与 Kotlin `RepMetricsCalculator` 相同公式。  
`analyze_video.py`：解码 → 跟踪 → 标定 → 相位 → 指标 → JSON。  
合成视频：640×480，30fps，白色圆半径 45px，从 y=300 匀速到 y=200。  
`golden/synthetic_squat.json` 写入一次运行结果，后续 Kotlin 测试读取并对比 `mcvMps`、`romM` 容差 1e-6。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd prototype && python -m pytest tests/test_pipeline.py -q`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add prototype
git commit -m "feat(prototype): add Python reference pipeline and golden vectors"
```

---

### Task 9: Android 视频导入与解码

**Files:**
- Create: `app/src/main/java/com/vbtdroid/app/video/Frame.kt`
- Create: `app/src/main/java/com/vbtdroid/app/video/VideoMeta.kt`
- Create: `app/src/main/java/com/vbtdroid/app/video/VideoDecoder.kt`
- Create: `app/src/main/java/com/vbtdroid/app/ui/import/ImportScreen.kt`
- Modify: `app/src/main/java/com/vbtdroid/app/MainActivity.kt`
- Create: `core/src/main/kotlin/com/vbtdroid/core/video/Timeline.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/video/TimelineTest.kt`

**Interfaces:**
- Consumes: `core` 类型。
- Produces:
  ```kotlin
  data class Frame(val tSec: Double, val bitmap: Bitmap)

  data class VideoMeta(
      val durationSec: Double,
      val width: Int,
      val height: Int,
      val rotationDeg: Int,
      val nominalFps: Double,
      val effectiveFps: Double
  )

  class VideoDecoder {
      fun decode(uri: Uri, maxLongSide: Int = 1920): Flow<Frame>
      fun readMeta(uri: Uri): VideoMeta
  }

  object Timeline {
      fun normalizePts(timestampsUs: LongArray): DoubleArray
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class TimelineTest {
    @Test
    fun `normalizes variable frame timestamps`() {
        val t = Timeline.normalizePts(longArrayOf(0, 33_333, 66_666, 150_000))
        assertArrayEquals(doubleArrayOf(0.0, 0.033333, 0.066666, 0.15), t, 1e-6)
    }

    @Test
    fun `rejects non monotonic timestamps`() {
        assertThrows(IllegalArgumentException::class.java) {
            Timeline.normalizePts(longArrayOf(0, 100, 50))
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*TimelineTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

`Timeline.normalizePts`：校验严格递增，返回 `us / 1_000_000.0`。  
`VideoDecoder`：`MediaExtractor` 选视频轨；`MediaMetadataRetriever` 读旋转/时长/帧率；`MediaCodec` 异步解码；输出 `Frame(tSec, bitmap)`；按 `maxLongSide` 缩放；旋转元数据用 `Matrix` 应用。  
`readMeta` 用实际 PTS 计算 `effectiveFps = (frames-1)/(last-first)`，与容器 `nominalFps` 分开报告。  
`ImportScreen`：`ActivityResultContracts.OpenDocument`，MIME `video/*`，持久化 URI 权限；显示文件名/时长/分辨率/有效帧率。

- [ ] **Step 4: 跑测试并手工验证**

Run: `./gradlew :core:test --tests '*TimelineTest*' && ./gradlew :app:assembleDebug`  
Expected: PASS + `BUILD SUCCESSFUL`。  
手工：导入 60fps 普通视频和 120fps 慢动作视频，确认 `effectiveFps` 分别约 60 和 120（或容器真实值），时长与原视频一致。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/vbtdroid/app/video app/src/main/java/com/vbtdroid/app/ui/import app/src/main/java/com/vbtdroid/app/MainActivity.kt core/src/main/kotlin/com/vbtdroid/core/video core/src/test/kotlin/com/vbtdroid/core/video
git commit -m "feat(app): import and decode video with PTS timeline"
```

---

### Task 10: Android 杠铃片检测与跟踪

**Files:**
- Create: `app/src/main/java/com/vbtdroid/app/vision/PlateDetector.kt`
- Create: `app/src/main/java/com/vbtdroid/app/vision/PlateTracker.kt`
- Create: `app/src/main/java/com/vbtdroid/app/vision/PlateRoi.kt`
- Create: `ml/download_models.sh`
- Create: `ml/train_detector.py`
- Create: `ml/models.lock`
- Create: `app/src/main/assets/models/README.md`
- Test: `app/src/test/java/com/vbtdroid/app/vision/PlateRoiTest.kt`

**Interfaces:**
- Consumes: `Bitmap`、`PlateObservation`。
- Produces:
  ```kotlin
  data class PlateDetection(
      val cxPx: Float,
      val cyPx: Float,
      val widthPx: Float,
      val heightPx: Float,
      val confidence: Float
  )

  interface PlateDetector {
      fun detect(bitmap: Bitmap): List<PlateDetection>
  }

  class PlateTracker(private val detector: PlateDetector) {
      fun initialize(bitmap: Bitmap, roi: RectF): PlateObservation
      fun update(frame: Bitmap, tSec: Double): PlateObservation?
  }

  object PlateRoi {
      fun selectLargest(detections: List<PlateDetection>): PlateDetection?
      fun isValidMajorAxis(majorAxisPx: Float, expectedPx: Float, tolerance: Float = 0.25f): Boolean
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class PlateRoiTest {
    @Test
    fun `largest detection wins`() {
        val a = PlateDetection(10f, 10f, 80f, 80f, 0.9f)
        val b = PlateDetection(20f, 20f, 120f, 120f, 0.8f)
        assertEquals(b, PlateRoi.selectLargest(listOf(a, b)))
    }

    @Test
    fun `inner hub is rejected when diameter deviates`() {
        assertFalse(PlateRoi.isValidMajorAxis(90f, expectedPx = 180f))
        assertTrue(PlateRoi.isValidMajorAxis(180f, expectedPx = 180f))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlateRoiTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

`PlateDetector`：TFLite YOLOv8n/YOLOX-Nano，输入 384×224 或 640×640，输出框；NMS；置信度阈值 0.35。模型放 `app/src/main/assets/models/plate.tflite`，gitignored。  
先固定 YOLOv8n TFLite 输出 `[1,5,N]`（cx,cy,w,h,score），NMS IoU 0.45；换 YOLOX/NanoDet 时只改 adapter。  
`PlateTracker.update`：对上一帧圆心做光流预测；在预测点附近提轮廓，`Imgproc.fitEllipse`；长轴作为 `majorAxisPx`；与期望直径偏差 >25% 判为内圈/误检，回退检测器。  
`PlateRoi.selectLargest`：按框面积最大。  
`ml/download_models.sh`：下载姿态模型 + 可选的开源检测器权重，`sha256sum -c ml/models.lock`，失败退出非零。  
`ml/train_detector.py`：用 Roboflow/自采 200–500 帧训练 YOLOX-Nano 或 NanoDet-Plus，导出 TFLite；发布路径用 Apache-2.0 模型，个人 MVP 可先用 YOLOv8n 权重。

- [ ] **Step 4: 跑测试并手工验证**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlateRoiTest*'`  
Expected: PASS。  
手工：3 段视频（深蹲侧拍、卧推前 45°、硬拉后 45°），确认跟踪不跳内圈；遮挡后能恢复；输出 `PlateObservation` 时间序列无大跳变。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/vbtdroid/app/vision ml app/src/main/assets/models/README.md app/src/test/java/com/vbtdroid/app/vision
git commit -m "feat(app): add plate detector and ellipse tracker"
```

---

### Task 11: Android 姿态估计与关节角

**Files:**
- Create: `app/src/main/java/com/vbtdroid/app/vision/PoseEstimator.kt`
- Create: `app/src/main/java/com/vbtdroid/app/analysis/JointAngleExtractor.kt`
- Test: `app/src/test/java/com/vbtdroid/app/analysis/JointAngleExtractorTest.kt`

**Interfaces:**
- Consumes: `Point2`、`JointAngles`。
- Produces:
  ```kotlin
  data class PoseFrame(
      val tSec: Double,
      val landmarks: Map<Int, Point2>,
      val visibility: Map<Int, Float>
  )

  interface PoseEstimator {
      fun estimate(bitmap: Bitmap, tSec: Double): PoseFrame?
  }

  data class FrameAngles(
      val tSec: Double,
      val hipDeg: Double?,
      val kneeDeg: Double?,
      val ankleDeg: Double?,
      val trunkDeg: Double?,
      val shoulderDeg: Double?,
      val elbowDeg: Double?,
      val wristDeg: Double?,
      val confidence: Double
  )

  object JointAngleExtractor {
      fun extract(
          pose: PoseFrame,
          lift: Lift,
          cameraAngleDeg: Double,
          minVisibility: Float = 0.5f
      ): FrameAngles
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class JointAngleExtractorTest {
    @Test
    fun `missing knee landmark yields null knee angle`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(11 to Point2(0.0, 0.0), 23 to Point2(0.0, 1.0)),
            visibility = mapOf(11 to 0.9f, 23 to 0.9f)
        )
        val a = JointAngleExtractor.extract(pose, Lift.SQUAT, 45.0)
        assertNull(a.kneeDeg)
        assertEquals(0.0, a.confidence, 1e-6)
    }

    @Test
    fun `visible hip applies azimuth correction`() {
        val pose = PoseFrame(
            tSec = 0.0,
            landmarks = mapOf(
                11 to Point2(0.0, 0.0), 23 to Point2(1.0, 0.0), 25 to Point2(1.0, 1.0)
            ),
            visibility = mapOf(11 to 0.9f, 23 to 0.9f, 25 to 0.9f)
        )
        val a = JointAngleExtractor.extract(pose, Lift.SQUAT, 45.0)
        assertEquals(90.0, a.hipDeg!!, 1e-6)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests '*JointAngleExtractorTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

`PoseEstimator` 用 MediaPipe `PoseLandmarker`（`pose_landmarker_lite.task`，GPU delegate，失败回退 CPU）。  
`JointAngleExtractor`：按 §5.7 取三点；`visibility < minVisibility` 或点缺失 → `null`；`JointAngles.correctAzimuth` 校正；置信度取相关点 visibility 最小值。  
MediaPipe 索引：肩 11/12、肘 13/14、腕 15/16、食指 19/20、髋 23/24、膝 25/26、踝 27/28、脚跟 29/30、脚尖 31/32。  
单机位只取可见侧（visibility 高的一侧）；左右对称不计算。

- [ ] **Step 4: 跑测试并手工验证**

Run: `./gradlew :app:testDebugUnitTest --tests '*JointAngleExtractorTest*'`  
Expected: PASS。  
手工：深蹲/硬拉各一段，叠加骨架；确认髋/膝/躯干角随动作变化，遮挡帧标低置信。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/vbtdroid/app/vision/PoseEstimator.kt app/src/main/java/com/vbtdroid/app/analysis/JointAngleExtractor.kt app/src/test/java/com/vbtdroid/app/analysis
git commit -m "feat(app): add pose estimation and joint angle extraction"
```

---

### Task 12: 分析管线与报告 UI

**Files:**
- Create: `app/src/main/java/com/vbtdroid/app/analysis/AnalysisPipeline.kt`
- Create: `app/src/main/java/com/vbtdroid/app/ui/report/ReportScreen.kt`
- Create: `app/src/main/java/com/vbtdroid/app/ui/report/RepTable.kt`
- Create: `app/src/main/java/com/vbtdroid/app/ui/report/VelocityChart.kt`
- Create: `app/src/main/java/com/vbtdroid/app/ui/report/BarPathChart.kt`
- Create: `app/src/main/java/com/vbtdroid/app/ui/report/PhaseTimeline.kt`
- Test: `app/src/test/java/com/vbtdroid/app/analysis/AnalysisPipelineTest.kt`

**Interfaces:**
- Consumes: `VideoDecoder`、`PlateTracker`、`PoseEstimator`、`core`。
- Produces:
  ```kotlin
  data class AnalysisRequest(
      val uri: Uri,
      val lift: Lift,
      val cameraAngleDeg: Double,
      val plateDiameterM: Double,
      val loadKg: Double?
  )

  data class AnalysisResult(
      val reps: List<RepMetrics>,
      val summary: SetSummary,
      val quality: QualityFlags,
      val fps: Double
  )

  data class SetSummary(
      val repCount: Int,
      val mcvAvgMps: Double,
      val bestMcvMps: Double,
      val velocityLossPct: Double?,
      val totalVolumeKg: Double?,
      val totalWorkJ: Double?
  )

  class AnalysisPipeline(
      private val decoder: VideoDecoder,
      private val tracker: PlateTracker,
      private val poseEstimator: PoseEstimator
  ) {
      suspend fun analyze(request: AnalysisRequest, onProgress: (Float) -> Unit = {}): AnalysisResult
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
@RunWith(RobolectricTestRunner::class)
class AnalysisPipelineTest {
    @Test
    fun `pipeline returns one rep for synthetic frames`() = runTest {
        val pipeline = AnalysisPipeline(FakeDecoder(), FakeTracker(), FakePose())
        val result = pipeline.analyze(
            AnalysisRequest(Uri.EMPTY, Lift.SQUAT, 90.0, 0.45, 100.0)
        )
        assertEquals(1, result.reps.size)
        assertTrue(result.reps[0].mcvMps > 0.0)
        assertEquals(1.0, result.quality.trackConfidence, 1e-6)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests '*AnalysisPipelineTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

管线：解码 → 首帧检测/用户 ROI → 逐帧跟踪 → 标定 → 姿态（每 2–3 帧）→ `Kinematics.derive` → `PhaseSegmenter.segment` → `RepMetricsCalculator.compute`（逐 rep，传入当前最佳 MCV）→ `SetSummary`。  
进度：`onProgress(decodedFrames / totalFrames)`。  
报告 UI：rep 表（MCV/MPV/PV/ROM/VL/粘滞点/力/功率）、速度曲线、杠铃轨迹、相位条、质量警告。  
`PhaseTimeline` 允许拖动相位边界；修改后重算该 rep，并标 `manual=true`。  
图表用 Compose Canvas，不引入第三方图表库。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests '*AnalysisPipelineTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/vbtdroid/app/analysis app/src/main/java/com/vbtdroid/app/ui/report app/src/test/java/com/vbtdroid/app/analysis
git commit -m "feat(app): add analysis pipeline and report UI"
```

---

### Task 13: 历史、导出、验证工具

**Files:**
- Create: `app/src/main/java/com/vbtdroid/app/data/AppDatabase.kt`
- Create: `app/src/main/java/com/vbtdroid/app/data/Entities.kt`
- Create: `app/src/main/java/com/vbtdroid/app/data/SessionDao.kt`
- Create: `app/src/main/java/com/vbtdroid/app/export/CsvExporter.kt`
- Create: `core/src/main/kotlin/com/vbtdroid/core/validation/Agreement.kt`
- Test: `core/src/test/kotlin/com/vbtdroid/core/validation/AgreementTest.kt`
- Test: `app/src/test/java/com/vbtdroid/app/export/CsvExporterTest.kt`

**Interfaces:**
- Consumes: `RepMetrics`、`AnalysisResult`。
- Produces:
  ```kotlin
  @Entity data class SessionEntity(...)
  @Entity data class SetEntity(...)
  @Entity data class RepEntity(...)

  @Dao interface SessionDao {
      suspend fun insertSet(set: SetEntity): Long
      suspend fun insertReps(reps: List<RepEntity>)
      suspend fun setsForLift(lift: String): List<SetEntity>
  }

  object CsvExporter {
      fun export(result: AnalysisResult): String
  }

  data class AgreementResult(
      val bias: Double,
      val sdDiff: Double,
      val lowerLoa: Double,
      val upperLoa: Double,
      val icc: Double
  )

  object Agreement {
      fun blandAltman(reference: DoubleArray, measured: DoubleArray): AgreementResult
  }
  ```

- [ ] **Step 1: 写失败测试**

```kotlin
class AgreementTest {
    @Test
    fun `perfect agreement`() {
        val r = Agreement.blandAltman(doubleArrayOf(0.5, 0.6, 0.7), doubleArrayOf(0.5, 0.6, 0.7))
        assertEquals(0.0, r.bias, 1e-9)
        assertEquals(1.0, r.icc, 1e-9)
    }

    @Test
    fun `constant bias`() {
        val r = Agreement.blandAltman(doubleArrayOf(0.5, 0.6, 0.7), doubleArrayOf(0.52, 0.62, 0.72))
        assertEquals(0.02, r.bias, 1e-9)
    }
}
```

```kotlin
class CsvExporterTest {
    @Test
    fun `csv has header and one row per rep`() {
        val csv = CsvExporter.export(fakeResult(2))
        assertTrue(csv.lines().first().contains("rep,mcv"))
        assertEquals(3, csv.lines().filter { it.isNotBlank() }.size)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :core:test --tests '*AgreementTest*' && ./gradlew :app:testDebugUnitTest --tests '*CsvExporterTest*'`  
Expected: FAIL。

- [ ] **Step 3: 实现**

Room 三表：Session/Set/Rep；`RepEntity.metricsJson` 存 JSON。  
`CsvExporter`：列 `rep,mcv,mpv,pv,rom,vl,sticking,meanForce,meanPower`；null 输出空字段。  
`Agreement`：bias = mean(diff)，SD = sample SD，LOA = bias ± 1.96·SD；ICC(2,1) 用双向随机、绝对一致公式。  
验证工具：读 ground truth CSV + App 导出 CSV，输出 `AgreementResult`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :core:test --tests '*AgreementTest*' && ./gradlew :app:testDebugUnitTest --tests '*CsvExporterTest*'`  
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/vbtdroid/app/data app/src/main/java/com/vbtdroid/app/export core/src/main/kotlin/com/vbtdroid/core/validation core/src/test/kotlin/com/vbtdroid/core/validation app/src/test/java/com/vbtdroid/app/export
git commit -m "feat: add history, CSV export and agreement validation"
```

---

## Self-Review

- Spec coverage：§5.1→Task 2；§5.2→Task 4；§5.3–5.4→Task 3/5；§5.5→Task 5；§5.6→Task 6；§5.7→Task 11；§5.9→Task 7；§6→Task 1/8/9/10/12；§7→Task 13；§8→Task 13；§9→Task 9/10/11/12。
- 未覆盖：叠加视频导出（spec 标 M5 可选）、RPE 映射表（spec 标实验）、双机位（非目标）。
- 类型一致性：`BarSample`、`MotionSample`、`RepSlice`、`RepMetrics`、`QualityFlags` 在任务间一致。
- Review Focus 对应测试：VFR→Task 9；cameraLevel→Task 2/3/7；内圈误检→Task 10；姿态遮挡→Task 11；缺负荷/少 LVP→Task 5/6。
