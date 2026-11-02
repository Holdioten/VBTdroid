# VBTdroid 技术规格

**版本**：0.1  
**日期**：2026-11-02  
**状态**：草案，待实现验证

---

## 1. 项目定位

VBTdroid 是 Android 端离线 VBT（Velocity Based Training）分析工具。

用户导入深蹲、卧推、硬拉视频。App 在设备本地完成杠铃追踪、速度/轨迹/相位/关节角/负荷指标计算，输出可复核的报告。

不做实时采集。不做云端。不做多用户/教练平台。

目标：个人训练用，单动作分析深度接近 Arcus VBT；指标完整、定义透明、质量可查。

### 1.1 语言与术语

- UI 默认中文（zh-CN）；英文（en）作为后续语言，不阻塞 v1。
- 所有用户可见文案走 Android `strings.xml`，禁止硬编码。
- 指标/术语的 Canonical ID 用英文 `snake_case`，稳定不变；中文/英文显示名可本地化。
- 内部术语表：`docs/glossary.md`。spec、plan、代码、JSON、UI 以它为准。
- 术语表分两层：**Part A 业务术语（输出端）** 与 **Part B 技术术语（拍摄/解码/标定/跟踪/验证）**。禁止混用。
- `MetricId` 只枚举 Part A 业务指标；技术字段用独立数据类/字段，不进 `MetricId`。
- 指标定义遵循同行评议共识与 SI 单位；不绑定 Arcus/Vitruve/GymAware/Enode 等商业体系。
- 定义有争议的指标（MPV、粘滞点、RPE）必须标注本实现定义与替代定义。

---

## 2. 目标

- 支持三大项：深蹲、卧推、硬拉。
- 支持侧拍、前 45°、后 45° 三种机位。
- 导入视频，离线分析，端侧运行，无网络权限。
- 输出 Tier 1–5 指标（见 §5）。
- 每个指标带定义、单位、依赖、置信度/不可用原因。
- 保存历史，支持同负荷对比、LVP、1RM 估计。
- 导出 JSON/CSV，支持报告复核。
- UI 默认中文；英文资源结构预留，后续补翻译。

## 3. 非目标

- 实时摄像头分析、实时语音反馈。
- 奥举、跳跃、器械动作、49 动作库。
- 自动识别杠铃片而不经用户确认。
- 云端账号、教练多运动员、团队管理。
- 临床级 3D 动作捕捉精度。
- 单机位左右对称性分析。
- 下背腰椎轮廓（v1 用躯干角替代）。
- iOS 端。

---

## 4. 用户流程

1. 导入视频（SAF / 相册）。
2. 选择动作：深蹲 / 卧推 / 硬拉。
3. 选择变式：高杠/低杠、传统/相扑、停顿/触胸即起（可选）。
4. 输入杠铃片直径（默认 450mm）。
5. 输入总负荷 kg（可选；力/功率/1RM 需要）。
6. 选择机位：侧拍 / 前 45° / 后 45°。
7. 首帧确认杠铃片 ROI（自动建议，用户可改）。
8. 分析，显示进度。
9. 查看报告：rep 表、速度曲线、杠铃轨迹、相位、关节角、组摘要、质量标记。
10. 保存历史；导出 JSON/CSV；可选导出叠加视频。

---

## 5. 指标定义

坐标系：

- 图像：`u` 向右，`v` 向下。
- 世界：`x` 水平前后，`y` 垂直向上，`z` 深度。
- 杠铃位置单位：米；速度：m/s；角度：度。
- 时间：视频 PTS，秒。禁止按帧计数推时间。

指标 Canonical ID、中英文名、单位、定义来源见 `docs/glossary.md`。JSON key 使用 Canonical ID；Kotlin 属性可用 camelCase，序列化时映射到 snake_case。

### 5.1 标定

已知杠铃片直径 `D`（米），椭圆长轴像素 `a_px`：

```
pxPerM = a_px / D
```

机位方位角 `α`：正前方 = 0°，侧拍 = 90°，前/后 45° = 45°。

相机水平（无俯仰）时：

```
x_world = x_img / sin(α)
y_world = -y_img / pxPerM
```

- 侧拍 `α=90°`：`sin=1`，不校正。
- 45°：水平分量除以 `sin45=0.7071`；垂直分量不变。
- 相机有俯仰/横滚：标 `cameraLevel=false`，速度指标降级并警告。
- 椭圆长轴 = 片径；短轴 = `D·sin(α)`。45° 下不要用短轴/框宽标定。

### 5.2 相位

输入平滑后的垂直速度 `v(t)`。

配置：

- `vEps = 0.03 m/s`
- `minPhaseSec = 0.12 s`
- 滞回 `0.01 m/s`

相位：

- 离心：`v < -vEps` 持续 ≥ `minPhaseSec`
- 停顿：`|v| ≤ vEps` 持续 ≥ `minPhaseSec`
- 同心：`v > +vEps` 持续 ≥ `minPhaseSec`
- 锁定：同心末端 `|v| ≤ vEps` 持续 ≥ 0.10s

硬拉：从静止开始，首个持续 `v > vEps` 为离地；无离心段。

允许手动拖动相位边界；`manual=true` 参与质量标记。

### 5.3 重复与组

- Rep 边界：离心开始到锁定结束；硬拉为离地到锁定。
- 组内 rep 数、持续时间、节奏（离心:停顿:同心:锁定）。
- Rep 一致性：MCV 的 `SD/mean`（CV）。

### 5.4 运动学

| 指标 | 定义 |
|---|---|
| `romTotalM` | rep 内 `y` 最大值 − 最小值 |
| `romConcentricM` | 同心段 `y` 位移 |
| `meanVelocityMps` | 全 rep 位移 / 时间 |
| `mcvMps` | 同心段位移 / 时间 |
| `mpvMps` | 同心开始到推进段结束（加速度首次转负）的平均速度；无负加速度则等于 MCV |
| `peakVelocityMps` | 同心段 `max(v)` |
| `peakAccelerationMps2` | 同心段 `max(a)` |
| `peakDecelerationMps2` | 同心段 `min(a)` |
| `stickingPointPct` | 同心段峰值后首个速度局部最小点 / ROM 百分比；找不到则 null |
| `velocityLossPct` | `(MCV_best − MCV_current) / MCV_best × 100` |
| `barPathHorizontalRangeM` | rep 内 `x` 范围 |
| `barPathDeviationM` | `max|x − x_start|` |

平滑：

- Savitzky–Golay，窗口 5–11 帧（奇数），多项式阶 2。
- 位置先平滑，再中心差分求速度/加速度。
- 报告窗口、截止频率、滤波方法。
- 峰值指标另用低平滑参数；不得用同一套参数掩盖差异。

### 5.5 负荷指标（需 `loadKg`）

`m = loadKg`，`g = 9.80665`。

| 指标 | 公式 |
|---|---|
| 力 | `F = m(g + a)` |
| 功率 | `P = F·v` |
| 冲量 | `∫F dt`，同心段 |
| 功 | `∫F dy`，同心段 |
| 平均/峰值 | 同心段均值/极值 |

假设：只算外部负荷（杠铃+片），不含体重/肢体质量。报告注明。

### 5.6 1RM / LVP / RPE

负荷-速度剖面：

```
v = a + b·load
```

至少 3 个负荷点，同动作同变式。拟合 `a`、`b`、`R²`。

```
estimated1Rm = (v1rm − a) / b
```

`v1rm` 默认（文献值，可覆盖）：

- 深蹲 0.30 m/s
- 卧推 0.17 m/s
- 硬拉 0.25 m/s

标记 `v1rmSource = literature | user`。没有个体校准时，1RM 标“估算”。

RPE：

- 可选实验功能。
- Helms 2017 回归：`%1RM = c·MCV + d`
  - 深蹲：`-0.449·MCV + 1.096`
  - 卧推：`-0.600·MCV + 1.051`
  - 硬拉：`-0.600·MCV + 1.076`
- RPE 映射需 Helms 表；未实现时输出 null，禁止硬编码假值。

### 5.7 关节角

2D 姿态关键点，三点夹角：

```
angle(A,B,C) = acos( (BA·BC) / (|BA||BC|) )
```

机位方位角校正（假设肢体在矢状面）：

```
θ_world = atan( tan(θ_image) / sin(α) )
```

| 关节 | 三点 |
|---|---|
| 髋 | 肩-髋-膝 |
| 膝 | 髋-膝-踝 |
| 踝 | 膝-踝-趾 |
| 躯干 | 肩-髋 vs 垂直 |
| 肩 | 髋-肩-肘 |
| 肘 | 肩-肘-腕 |
| 腕 | 肘-腕-指 |

- 侧拍 α=90°，不校正。
- 45° 校正后标“估算”。
- 卧推肩外展/深蹲膝外展时假设失效，置信度降级。
- 关键帧：起始、底部/触胸、粘滞点、锁定。
- 遮挡帧：`visibility < 0.5` 标低置信，插值但不参与统计。

### 5.8 左右对称

v1 不支持单机位对称性。仅当正面机位或双机位时输出；否则 `null` + `SYMMETRY_UNAVAILABLE`。

### 5.9 数据质量

| 标记 | 含义 |
|---|---|
| `track_confidence` | 跟踪平均置信度 0–1 |
| `occlusion_pct` | 低置信帧比例 |
| `rep_confidence` | 相位分割置信度 |
| `calibration_confidence` | 标定置信度 |
| `camera_level` | 相机是否水平 |
| `effective_fps` | 有效帧率 |
| `warnings` | 具体问题列表 |

规则：

- `track_confidence < 0.6`：速度指标标“低置信”。
- `occlusion_pct > 20%`：关节角不可用。
- `camera_level=false`：垂直速度标“估算”，不参与 PR 判定。
- Rep 置信度 < 0.8：要求用户复核相位。

### 5.10 标准与术语

没有单一 VBT ISO 标准。采用：

- SI 单位与标准运动学定义。
- 同行评议共识：Sánchez-Medina / González-Badillo（MCV、MPV、VL）、Banyard（LVP）、Weakley 2021 与 Pueo 2025（设备效度/信度）、Helms 2017 与 Zourdos 2016（RPE/RIR）。
- 统计：Bland–Altman 1986（bias/LOA）、Koo & Li 2016（ICC）、Weir 2005（SEM）。
- 关节角：ISB 推荐是 3D 坐标系；本实现是单目 2D 近似，不声称 ISB 合规。

实现规则：

- 指标定义冲突时，`docs/glossary.md` 选定一个操作定义，备注替代定义。
- 1RM 估计必须标 `estimated_`；没有个体 MVT 校准时不得标“实测”。
- RPE 用 0–10 力量训练量表，不用 Borg 6–20。
- 速度区间（velocity zone）只作参考，不作默认处方；个体/动作差异大。
- 商业 App 术语只作对照，不作标准来源。

---

## 6. 架构

```
VBTdroid/
  app/        Android Kotlin，UI、视频解码、ML 适配、Room
  core/       纯 Kotlin/JVM，标定、滤波、相位、指标、LVP、JSON；JUnit 5
  prototype/  Python 参考实现，算法验证、golden vectors、对照实验
  ml/         模型说明与下载脚本；权重不进 git
  docs/       spec、plan、验证记录
```

分层：

```
视频导入 (SAF)
  → VideoDecoder (MediaExtractor + MediaCodec, PTS)
  → PlateDetector (TFLite, 首帧/丢失恢复)
  → PlateTracker (OpenCV 椭圆拟合 + 光流)
  → PoseEstimator (MediaPipe Tasks)
  → AnalysisPipeline
      → core: Calibration → Filter → Phase → Metrics → LVP
  → Report (Compose Canvas)
  → Room / JSON / CSV
```

原则：

- `core` 不依赖 Android/OpenCV。输入是 `PlateObservation`/`BarSample`/关键点，输出指标。
- 视频解码、OpenCV、TFLite、MediaPipe 全部在 `app` 适配层。
- Python 只做算法参考和验证，不做产品运行时。
- 模型文件不进 git；`ml/download_models.sh` 下载并校验 SHA256。

---

## 7. 数据模型

```kotlin
Session(id, date, lift, variant, notes)
Set(id, sessionId, loadKg, plateDiameterMm, cameraAngleDeg, videoUri, createdAt)
Rep(id, setId, index, startSec, endSec, metricsJson, qualityJson)
Phase(id, repId, type, startSec, endSec, manual)
LiftProfile(id, lift, variant, v1rmMps, slope, intercept, r2, updatedAt)
```

JSON 导出包含：

```json
{
  "schema_version": 1,
  "session": {"date": "...", "lift": "back_squat", "variant": "high_bar"},
  "set": {"load_kg": 100, "plate_diameter_mm": 450, "camera_azimuth_deg": 45},
  "calibration": {"px_per_m": 500.0, "camera_level": true},
  "reps": [
    {
      "index": 1,
      "phases": [{"type": "eccentric_phase", "start_sec": 0.0, "end_sec": 1.2}],
      "metrics": {"rom_total_m": 0.52, "mcv_mps": 0.61, "mpv_mps": 0.68, "peak_velocity_mps": 0.92},
      "quality": {"track_confidence": 0.94, "occlusion_pct": 2.0}
    }
  ],
  "summary": {"mcv_avg_mps": 0.58, "velocity_loss_pct": 12.3},
  "warnings": []
}
```

---

## 8. 精度与验证

基准：RepOne/Enode LPT 或 3D 动作捕捉。

数据集：

- 3 名受试者 × 3 动作 × 5 负荷（30/50/70/85/95% 1RM）× 3 reps × 2 次。
- 侧拍 + 前 45° + 后 45°。
- 1080p60，三脚架，锁定曝光/对焦/白平衡。

验收目标（受控拍摄）：

| 指标 | 目标 |
|---|---|
| Rep 检测 recall/precision | ≥ 95% |
| MCV bias | ≤ 0.03 m/s |
| MCV 95% LOA | ≤ ±0.08 m/s |
| PV bias | ≤ 0.05 m/s |
| ROM 误差 | ≤ 0.02 m |
| 速度损失误差 | ≤ 2 个百分点 |
| 关节角 ICC | ≥ 0.80 |
| 关节角误差 | ≤ 5°（校正后） |

输出 Bland–Altman、ICC、CV。验证记录存 `docs/validation/`。

---

## 9. Android 约束

- `minSdk 26`，`targetSdk 35`，`compileSdk 35`，JDK 17。
- UI：Jetpack Compose + Material 3。
- 语言：默认 zh-CN，`res/values/strings.xml`；英文 `res/values-en/strings.xml` 后续补，不阻塞 v1。
- 所有用户可见文案走 `stringResource`；指标显示名通过 `MetricId` + 术语表映射，禁止硬编码。
- JSON key 用 Canonical ID（`snake_case`），不随 UI 语言变化。
- 视频：MediaExtractor + MediaCodec；支持 H.264/HEVC、VFR、旋转元数据、慢动作 PTS。
- 解码分辨率：最长边 ≤ 1920；分析流可降至 1280。
- 内存：流式解码，不整段载入。
- 模型：TFLite GPU delegate，失败回退 CPU；MediaPipe Tasks Vision Pose Landmarker。
- 无网络权限。
- 分析 60s 视频在中端机目标 < 2 分钟；显示进度，可取消。
- 热限制：单次分析限 10 分钟视频；前台服务 + 通知。

---

## 10. 技术选型与许可

| 组件 | 选择 | 许可 |
|---|---|---|
| 语言 | Kotlin 2.x | Apache 2.0 |
| UI | Compose + Material 3 | Apache 2.0 |
| 视频 | MediaExtractor/MediaCodec | 平台 |
| CV | OpenCV Android 4.x | Apache 2.0 |
| 推理 | TensorFlow Lite | Apache 2.0 |
| 姿态 | MediaPipe Tasks Vision | Apache 2.0 |
| 图表 | Compose Canvas | 平台 |
| 数据库 | Room | Apache 2.0 |
| 序列化 | kotlinx.serialization | Apache 2.0 |
| 检测模型（MVP） | YOLOv8n（仅个人自用路径） | **AGPL-3.0** |
| 检测模型（发布准备） | 待选；Apache-2.0 方案未实现 | 待决 |

许可警告：

- MVP 仅提供显式 `--license personal-agpl` 的 YOLOv8n 训练路径；该路径基于 AGPL-3.0，仅限个人自用，分发前须完成许可审查。
- Apache-2.0 检测器训练/适配不属于 MVP 实现，留待发布准备阶段评估；不得把 YOLOX-Nano/NanoDet-Plus 描述为当前已支持的训练路径。
- 数据集许可逐个查；Roboflow 数据集许可不统一。

---

## 11. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 45° 投影误差 | 关节角偏差 | 校正 + 标“估算”；关键指标用侧拍 |
| 卧推遮挡 | 片/肘被挡 | 前 45° + ArUco 可选；低置信标记 |
| VFR/慢动作 PTS | 速度错 | 只用 PTS；检测容器帧率与实际间隔 |
| 姿态遮挡 | 角度垃圾 | 置信度阈值；不可用而非硬算 |
| 功率/力噪声 | 峰值虚高 | SG 滤波；报告参数；峰值单独平滑 |
| 1RM 需多组 | 单组无法算 | 明确“不可用”；LVP 数据模型 |
| 检测器许可 | 分发风险 | 发布前换 Apache 模型 |
| 验证成本 | 无 ground truth | 租/借 RepOne；先做核心指标 |
| 指标膨胀 | 不可信 | 每个指标带依赖与质量标记 |
| 术语漂移 | UI/JSON/代码同名不同义 | `docs/glossary.md` + `MetricId` + 唯一性测试 |

---

## 12. 里程碑

| 里程碑 | 内容 | 全职估计 |
|---|---|---|
| M0 | spec + repo + plan | 1 天 |
| M1 | Python 参考实现 + golden vectors | 1–2 周 |
| M2 | Kotlin core + JUnit | 1–2 周 |
| M3 | Android 导入/解码 + 片跟踪 | 2–3 周 |
| M4 | 姿态 + 指标 + 报告 UI | 2–3 周 |
| M5 | 历史/导出/对比 + 英文资源骨架 | 1 周 |
| M6 | 验证 + 打磨 | 2–4 周 |

总计：2.5–3.5 个月全职。

---

## 13. 开放问题

1. 发布模式：个人自用 / 开源 AGPL / 闭源？决定检测器许可。
2. 姿态模型：MediaPipe Pose Landmarker vs MoveNet/RTMPose。先 MediaPipe。
3. 叠加视频：v1 是否必须？先 JSON + 图表，叠加放 M5。
4. 1RM 默认 `v1rm` 用文献值还是强制个体校准？先文献值 + 标记。
5. 双机位支持是否进 v1？不进，留接口。
6. 卧推 ArUco 辅助是否默认开启？默认关闭，作为高精度选项。
7. 英文翻译排期：v1 只留 `values-en` 骨架，M5 后补完整翻译。
