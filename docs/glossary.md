# VBTdroid 术语与指标对照表

**状态**：单一术语来源（single source of truth）  
**日期**：2026-11-02  
**适用范围**：spec、plan、代码、JSON、UI、验证报告

---

## 0. 使用规则

### 0.1 两层术语，禁止混用

| 层 | 范围 | 典型术语 | 代码/JSON |
|---|---|---|---|
| **业务术语（Domain）** | 用户报告、训练决策、输出指标 | MCV、功率、离心/向心、1RM、RPE、关节角 | `MetricId`；JSON `metrics` |
| **技术术语（Technical）** | 拍摄、解码、标定、跟踪、姿态、验证 | 帧率、PTS、VFR、相机俯仰角、ROI、遮挡、ICC | 独立数据类/字段；JSON `video`、`calibration`、`quality` |

判定规则：

- 用户会在报告里据此做训练决策 → 业务术语。
- 只影响采集/处理/质控，用户不需要理解 → 技术术语。
- 同一概念跨层时，用不同字段名，不共用 ID。例：`camera_azimuth_deg`（技术）≠ `bar_path`（业务）。
- `MetricId` 只枚举业务指标；技术字段不进 `MetricId`。

### 0.2 Canonical ID

1. 英文 `snake_case`，稳定不变。JSON key、数据库字段、图表 series、代码枚举都用它。
2. 中文名是 zh-CN 默认显示名；English 是 en 显示名。显示名可改，Canonical ID 不改。
3. 所有用户可见文案走 Android `strings.xml`；禁止 Compose 硬编码。
4. 指标定义以本表为准。spec/plan 与本表冲突时，以本表为准并回改 spec/plan。
5. 单位用 SI；百分比 `%`；角度 `°`；速度 `m/s`；力 `N`；功率 `W`；功 `J`；冲量 `N·s`。
6. 定义有争议的指标，必须写“本实现定义”+“替代定义”，禁止假装唯一标准。
7. 不绑定商业 Vendor 术语（Arcus / Vitruve / GymAware / Enode）。Vendor 叫法只作备注。

---

# Part A：业务术语（输出端）

> 这些是报告、CSV、训练建议里出现的词。`MetricId = yes` 表示进入 `MetricId` 枚举。

## A1. 训练体系与动作

| Canonical ID | 中文 | English | 缩写 | 定义/备注 | MetricId | 来源 |
|---|---|---|---|---|---|---|
| `vbt` | 基于速度的力量训练 | Velocity-Based Training | VBT | 用动作速度监控/调节负荷与疲劳 | no | 通用 |
| `back_squat` | 后蹲 | Back Squat | BS | 杠铃置于上背部的深蹲 | no | 通用 |
| `high_bar_squat` | 高杠深蹲 | High-Bar Squat | HBS | 杠铃置于斜方肌上部，躯干更直立 | no | 通用 |
| `low_bar_squat` | 低杠深蹲 | Low-Bar Squat | LBS | 杠铃置于后三角肌/肩胛冈，躯干更前倾 | no | 通用 |
| `bench_press` | 卧推 | Bench Press | BP | 仰卧推举，含停顿/触胸即起变式 | no | 通用 |
| `deadlift` | 硬拉 | Deadlift | DL | 杠铃从地面拉起至锁定 | no | 通用 |
| `conventional_deadlift` | 传统硬拉 | Conventional Deadlift | CDL | 双脚与髋同宽，杠铃贴近小腿 | no | 通用 |
| `sumo_deadlift` | 相扑硬拉 | Sumo Deadlift | SDL | 宽站距，脚尖外展，躯干更直立 | no | 通用 |
| `paused_rep` | 停顿重复 | Paused Rep | — | 底部/触胸明显停顿后再向心 | no | 通用 |
| `touch_and_go` | 触胸即起 | Touch-and-Go | TNG | 卧推触胸后不停顿 | no | 通用 |
| `one_rep_max` | 单次最大重量 | One-Repetition Maximum | 1RM | 标准动作只能完成 1 次的最大负荷 | no | 通用 |
| `relative_intensity` | 相对强度 | Relative Intensity | %1RM | 负荷占 1RM 百分比 | no | 通用 |
| `repetition` | 重复 | Repetition | rep | 一次完整动作周期 | no | 通用 |
| `set` | 组 | Set | — | 连续若干次重复 | no | 通用 |
| `tempo` | 节奏 | Tempo | — | 各相位时长/比例 | no | 通用 |
| `volume_load` | 训练容量 | Volume Load | VL | `load × reps`（可再加组数） | yes | 通用 |
| `rpe` | 主观用力程度 | Rating of Perceived Exertion | RPE | 0–10 力量训练量表 | yes | Helms 2017; Zourdos 2016 |
| `rir` | 储备次数 | Repetitions in Reserve | RIR | 离力竭还能完成的次数 | yes | Zourdos 2016 |
| `velocity_zone` | 速度区间 | Velocity Zone | — | 按速度划分的训练意图区间；个体差异大，仅参考 | no | 实践框架 |
| `autoregulation` | 自动调节 | Autoregulation | — | 按当日速度/疲劳调整负荷 | no | 通用 |

## A2. 速度指标

| Canonical ID | 中文 | English | 缩写 | 单位 | 本实现定义 | MetricId | 来源 |
|---|---|---|---|---|---|---|---|
| `mean_velocity_mps` | 平均速度 | Mean Velocity | MV | m/s | 整个 rep 的杠铃位移 / 时间 | yes | 通用 |
| `mcv_mps` | 平均向心速度 | Mean Concentric Velocity | MCV | m/s | 向心段位移 / 向心段时间 | yes | Sánchez-Medina 2011 |
| `mpv_mps` | 平均推进速度 | Mean Propulsive Velocity | MPV | m/s | 向心开始到加速度首次转负的平均速度；无制动段时等于 MCV | yes | González-Badillo 2010 |
| `peak_velocity_mps` | 峰值速度 | Peak Velocity | PV | m/s | 向心段最大瞬时垂直速度 | yes | 通用 |
| `mean_eccentric_velocity_mps` | 平均离心速度 | Mean Eccentric Velocity | MEV | m/s | 离心段位移 / 离心段时间 | yes | 通用 |
| `peak_eccentric_velocity_mps` | 峰值离心速度 | Peak Eccentric Velocity | PEV | m/s | 离心段最大瞬时速度 | yes | 通用 |
| `velocity_loss_pct` | 速度损失 | Velocity Loss | VL | % | `(MCV_best − MCV_current) / MCV_best × 100` | yes | Sánchez-Medina 2011 |
| `set_velocity_loss_pct` | 组速度损失 | Set Velocity Loss | sVL | % | `(MCV_best − MCV_last) / MCV_best × 100` | yes | Sánchez-Medina 2011 |
| `minimum_velocity_threshold_mps` | 最小速度阈值 | Minimum Velocity Threshold | MVT | m/s | 1RM 时速度阈值；个体/动作特异 | yes | González-Badillo 2010; Banyard 2018 |
| `v1rm_mps` | 1RM 速度 | Velocity at 1RM | V1RM | m/s | 用于 LVP 反推 1RM 的速度 | yes | 同上 |
| `mean_velocity_at_load_mps` | 某负荷平均速度 | Mean Velocity at Load | — | m/s | 指定负荷下的 MCV | yes | 通用 |
| `velocity_decay_pct_per_rep` | 每次重复速度衰减 | Velocity Decay per Rep | — | %/rep | MCV~rep 序号线性斜率 / 首次 MCV × 100 | yes | 本实现定义 |
| `fatigue_index` | 疲劳指数 | Fatigue Index | FI | %/rep | `set_velocity_loss_pct / rep_count` | yes | 本实现定义 |
| `effective_reps` | 有效重复 | Effective Reps | — | 次 | MCV 高于用户阈值的重复数 | yes | 实践概念 |

## A3. 相位、轨迹、技术

| Canonical ID | 中文 | English | 缩写 | 单位 | 本实现定义 | MetricId | 来源 |
|---|---|---|---|---|---|---|---|
| `eccentric_phase` | 离心阶段 | Eccentric Phase | ECC | — | 杠铃向下/拉长肌肉阶段 | no | 通用 |
| `pause_phase` | 停顿阶段 | Pause Phase | — | — | 速度接近零的等长保持段 | no | 通用 |
| `concentric_phase` | 向心阶段 | Concentric Phase | CON | — | 杠铃向上/肌肉缩短阶段 | no | 通用 |
| `lockout_phase` | 锁定阶段 | Lockout Phase | — | — | 向心末端速度趋零的稳定段 | no | 通用 |
| `propulsive_phase` | 推进阶段 | Propulsive Phase | — | — | 向心段中加速度 ≥ 0 的部分 | no | González-Badillo 2010 |
| `braking_phase` | 制动阶段 | Braking Phase | — | — | 向心段中加速度 < 0 的部分 | no | 同上 |
| `phase_duration_s` | 相位时长 | Phase Duration | — | s | 相位起止时间差 | yes | 通用 |
| `rep_duration_s` | 重复时长 | Rep Duration | — | s | rep 起止时间差 | yes | 通用 |
| `rom_total_m` | 总动作幅度 | Total Range of Motion | ROM | m | rep 内杠铃垂直位移最大值 − 最小值 | yes | 通用 |
| `rom_concentric_m` | 向心动作幅度 | Concentric Range of Motion | cROM | m | 向心段杠铃垂直位移 | yes | 通用 |
| `sticking_point_pct` | 粘滞点位置 | Sticking Point | SP | % ROM | 向心峰值后首个速度局部最小点；找不到为 null | yes | 定义不统一；本实现明确算法 |
| `sticking_point_velocity_mps` | 粘滞点速度 | Sticking Point Velocity | SPV | m/s | 粘滞点处瞬时速度 | yes | 本实现定义 |
| `bar_path_horizontal_range_m` | 杠铃轨迹水平范围 | Bar Path Horizontal Range | — | m | rep 内水平位移最大值 − 最小值 | yes | 通用 |
| `bar_path_deviation_m` | 杠铃轨迹偏移 | Bar Path Deviation | — | m | `max|x − x_start|` | yes | 本实现定义 |
| `j_curve` | J 曲线 | J-Curve | — | — | 卧推/深蹲杠铃前后方向的弧形轨迹 | no | 生物力学通用 |
| `bar_path` | 杠铃轨迹 | Bar Path | — | — | 杠铃中心在 x-y 平面的轨迹 | no | 通用 |
| `rep_consistency_cv_pct` | 重复一致性 | Rep Consistency | — | % | rep 间 MCV 的 `SD/mean × 100` | yes | 本实现定义 |
| `symmetry_index_pct` | 对称指数 | Limb Symmetry Index | LSI | % | 患侧/健侧 × 100；v1 单机位不计算 | yes | 康复通用 |

## A4. 力、功率、功

| Canonical ID | 中文 | English | 缩写 | 单位 | 本实现定义 | MetricId | 来源 |
|---|---|---|---|---|---|---|---|
| `load_kg` | 负荷 | Load | — | kg | 杠铃 + 片的总外部负荷 | yes | 通用 |
| `force_n` | 力 | Force | F | N | `F = m(g + a)`；m 为外部负荷，不含体重 | yes | 牛顿力学 |
| `mean_force_n` | 平均力 | Mean Force | — | N | 向心段 F 均值 | yes | 通用 |
| `peak_force_n` | 峰值力 | Peak Force | — | N | 向心段 F 最大值 | yes | 通用 |
| `power_w` | 功率 | Power | P | W | `P = F·v` | yes | 通用 |
| `mean_power_w` | 平均功率 | Mean Power | — | W | 向心段 P 均值 | yes | 通用 |
| `peak_power_w` | 峰值功率 | Peak Power | — | W | 向心段 P 最大值 | yes | 通用 |
| `impulse_ns` | 冲量 | Impulse | J | N·s | `∫F dt`，向心段 | yes | 牛顿力学 |
| `work_j` | 功 | Work | W | J | `∫F dy`，向心段 | yes | 牛顿力学 |
| `acceleration_mps2` | 加速度 | Acceleration | a | m/s² | 速度一阶导 | yes | 通用 |
| `peak_acceleration_mps2` | 峰值加速度 | Peak Acceleration | — | m/s² | 向心段最大 a | yes | 通用 |
| `peak_deceleration_mps2` | 峰值减速度 | Peak Deceleration | — | m/s² | 向心段最小 a | yes | 通用 |

**假设**：只算外部负荷。深蹲不含体重；卧推不含手臂质量；硬拉不含体重。报告必须注明。

## A5. 1RM、LVP、疲劳

| Canonical ID | 中文 | English | 缩写 | 单位 | 本实现定义 | MetricId | 来源 |
|---|---|---|---|---|---|---|---|
| `lvp` | 负荷-速度剖面 | Load-Velocity Profile | LVP | — | `v = a + b·load` | yes | Banyard 2018; Weakley 2021 |
| `lvp_slope` | LVP 斜率 | LVP Slope | b | m/s per kg | 线性拟合斜率；通常为负 | yes | 同上 |
| `lvp_intercept` | LVP 截距 | LVP Intercept | a | m/s | 线性拟合截距 | yes | 同上 |
| `lvp_r2` | LVP 决定系数 | LVP R² | R² | — | 拟合优度 | yes | 统计学 |
| `estimated_1rm_kg` | 估计 1RM | Estimated 1RM | e1RM | kg | `(v1rm − a) / b` | yes | 本实现；依赖 LVP 质量 |
| `percent_1rm_pct` | 相对强度 | Percent of 1RM | %1RM | % | `load / estimated_1rm × 100` | yes | 通用 |
| `velocity_loss_threshold_pct` | 速度损失阈值 | Velocity Loss Threshold | — | % | 用户设定的停组阈值 | yes | 实践参数 |
| `rpe_estimate` | 估计 RPE | Estimated RPE | — | 0–10 | 由 MCV/%1RM + Helms 表推导；未实现时 null | yes | Helms 2017 |

## A6. 关节角（输出端）

| Canonical ID | 中文 | English | 缩写 | 单位 | 本实现定义 | MetricId | 来源 |
|---|---|---|---|---|---|---|---|
| `hip_angle_deg` | 髋角 | Hip Angle | — | ° | 肩-髋-膝三点夹角 | yes | 2D 简化 |
| `knee_angle_deg` | 膝角 | Knee Angle | — | ° | 髋-膝-踝三点夹角 | yes | 2D 简化 |
| `ankle_angle_deg` | 踝角 | Ankle Angle | — | ° | 膝-踝-趾三点夹角 | yes | 2D 简化 |
| `trunk_angle_deg` | 躯干角 | Trunk Angle | — | ° | 肩-髋连线与垂直方向夹角 | yes | 2D 简化 |
| `shoulder_angle_deg` | 肩角 | Shoulder Angle | — | ° | 髋-肩-肘三点夹角 | yes | 2D 简化 |
| `elbow_angle_deg` | 肘角 | Elbow Angle | — | ° | 肩-肘-腕三点夹角 | yes | 2D 简化 |
| `wrist_angle_deg` | 腕角 | Wrist Angle | — | ° | 肘-腕-指三点夹角 | yes | 低置信 |

**标准说明**：ISB 推荐 3D 关节坐标系；本实现是单目 2D 近似，不声称 ISB 合规。45° 校正假设肢体在矢状面。

---

# Part B：技术实现术语（拍摄 → 分析）

> 这些不进 `MetricId`，不进报告正文；只用于采集、解码、标定、跟踪、质控、验证。

## B1. 视频与时间

| Canonical ID | 中文 | English | 缩写 | 单位 | 定义/备注 |
|---|---|---|---|---|---|
| `pts` | 显示时间戳 | Presentation Time Stamp | PTS | s | 视频帧显示时间；速度计算唯一时间基准 |
| `vfr` | 可变帧率 | Variable Frame Rate | VFR | — | 帧间隔不固定；禁止按帧计数推时间 |
| `fps` | 帧率 | Frames per Second | FPS | Hz | 通用帧率 |
| `nominal_fps` | 标称帧率 | Nominal FPS | — | Hz | 容器/元数据声明帧率 |
| `effective_fps` | 有效帧率 | Effective FPS | — | Hz | 按实际 PTS 计算 |
| `container_frame_rate` | 容器帧率 | Container Frame Rate | — | Hz | MediaFormat 声明值，可能与实际不符 |
| `rotation_metadata` | 旋转元数据 | Rotation Metadata | — | ° | 手机竖拍/横拍旋转标记 |
| `slow_motion` | 慢动作 | Slow Motion | — | — | 高帧率录制后按低帧率播放；PTS 可能被缩放 |
| `hdr` | 高动态范围 | High Dynamic Range | HDR | — | 10-bit/HLG/PQ；解码需 tone-map 或拒绝 |
| `rolling_shutter` | 卷帘快门 | Rolling Shutter | — | — | 快速运动导致帧内几何畸变 |
| `exposure_time` | 曝光时间 | Exposure Time | — | s | 运动模糊来源；分析时锁定 |
| `white_balance` | 白平衡 | White Balance | WB | — | 分析时锁定，避免颜色漂移 |
| `focus_distance` | 对焦距离 | Focus Distance | — | m | 锁定对焦，避免焦距呼吸 |
| `eis` | 电子防抖 | Electronic Image Stabilization | EIS | — | 会裁切/ warp，分析时关闭 |
| `ois` | 光学防抖 | Optical Image Stabilization | OIS | — | 会改变几何，分析时关闭 |

## B2. 标定与几何

| Canonical ID | 中文 | English | 缩写 | 单位 | 定义/备注 |
|---|---|---|---|---|---|
| `plate_diameter_mm` | 杠铃片直径 | Plate Diameter | D | mm | 标定基准；默认 450mm |
| `px_per_m` | 像素每米 | Pixels per Metre | — | px/m | `椭圆长轴像素 / 片径` |
| `camera_azimuth_deg` | 机位方位角 | Camera Azimuth | α | ° | 正前 0°，侧拍 90°，前/后 45° |
| `camera_pitch_deg` | 相机俯仰角 | Camera Pitch | — | ° | 影响垂直速度；非零时降级 |
| `camera_roll_deg` | 相机横滚角 | Camera Roll | — | ° | 影响椭圆长轴方向；非零时降级 |
| `camera_level` | 相机水平 | Camera Level | — | bool | 无俯仰/横滚 |
| `ellipse_major_axis_px` | 椭圆长轴 | Ellipse Major Axis | — | px | 45° 下标定用长轴，不用短轴/框宽 |
| `ellipse_minor_axis_px` | 椭圆短轴 | Ellipse Minor Axis | — | px | 45° 下约为 `D·sin α` |
| `perspective_projection` | 透视投影 | Perspective Projection | — | — | 近大远小；深度变化影响尺度 |
| `homography` | 单应变换 | Homography | — | — | 平面到图像映射；可选高级标定 |
| `roi` | 感兴趣区域 | Region of Interest | ROI | — | 首帧杠铃片选择区域 |

## B3. 检测、跟踪、姿态、质控

| Canonical ID | 中文 | English | 缩写 | 单位 | 定义/备注 |
|---|---|---|---|---|---|
| `plate_observation` | 杠铃片观测 | Plate Observation | — | — | 每帧片中心 + 长轴 + 置信度 |
| `detection` | 目标检测 | Object Detection | — | — | 首帧/丢失恢复用；不逐帧测速 |
| `tracker` | 跟踪器 | Tracker | — | — | 光流/椭圆拟合，逐帧输出 |
| `ellipse_fit` | 椭圆拟合 | Ellipse Fitting | — | — | 45° 下片投影为椭圆 |
| `optical_flow` | 光流 | Optical Flow | — | — | 帧间点对应；用于预测/补帧 |
| `re_init` | 重初始化 | Re-initialization | — | — | 跟踪丢失后重新检测 |
| `nms` | 非极大值抑制 | Non-Maximum Suppression | NMS | — | 检测框去重 |
| `occlusion_pct` | 遮挡比例 | Occlusion Percentage | — | % | 低置信/丢失帧占比 |
| `track_confidence` | 跟踪置信度 | Tracking Confidence | — | 0–1 | 跟踪器平均置信度 |
| `rep_confidence` | 重复分割置信度 | Rep Segmentation Confidence | — | 0–1 | 相位分割可信度 |
| `calibration_confidence` | 标定置信度 | Calibration Confidence | — | 0–1 | 片径/椭圆拟合可信度 |
| `keypoint_visibility` | 关键点可见度 | Keypoint Visibility | — | 0–1 | MediaPipe 输出 |
| `joint_angle_confidence` | 关节角置信度 | Joint Angle Confidence | — | 0–1 | 相关关键点 visibility 最小值 |
| `tflite` | TensorFlow Lite | TensorFlow Lite | TFLite | — | 端侧推理格式 |
| `gpu_delegate` | GPU 代理 | GPU Delegate | — | — | TFLite GPU 加速；失败回退 CPU |
| `mediapipe` | MediaPipe | MediaPipe | — | — | 姿态估计框架 |
| `pose_landmark` | 姿态关键点 | Pose Landmark | — | — | 33 点人体关键点 |

## B4. 统计与验证

| Canonical ID | 中文 | English | 缩写 | 单位 | 定义/备注 | 来源 |
|---|---|---|---|---|---|---|
| `bias` | 偏差 | Bias | — | 同指标 | 测量值 − 参考值均值 | Bland & Altman 1986 |
| `loa` | 一致性界限 | Limits of Agreement | LOA | 同指标 | `bias ± 1.96·SD` | Bland & Altman 1986 |
| `icc` | 组内相关系数 | Intraclass Correlation Coefficient | ICC | 0–1 | 双向随机/绝对一致；报告模型 | Koo & Li 2016 |
| `sem` | 测量标准误 | Standard Error of Measurement | SEM | 同指标 | `SD·sqrt(1−ICC)` | Weir 2005 |
| `cv` | 变异系数 | Coefficient of Variation | CV | % | `SD/mean × 100` | 通用 |
| `mdd` | 最小可检测差异 | Minimal Detectable Difference | MDD | 同指标 | 基于 SEM/ICC | 通用 |
| `validity` | 效度 | Validity | — | — | 是否测到目标构念 | 心理测量学 |
| `reliability` | 信度 | Reliability | — | — | 重复测量一致性 | 心理测量学 |

---

# Part C：映射与检查

## C1. 业务/技术边界

| 场景 | 用哪层 | 例子 |
|---|---|---|
| 报告指标卡 | 业务 | `mcv_mps`、`peak_power_w` |
| CSV 导出列 | 业务 | `mcv_mps`、`velocity_loss_pct` |
| JSON `metrics` | 业务 | `MetricId.id` |
| JSON `calibration` | 技术 | `px_per_m`、`camera_azimuth_deg` |
| JSON `quality` | 技术 | `track_confidence`、`occlusion_pct` |
| 调试日志 | 技术 | `pts`、`effective_fps`、`re_init` |
| 验证报告 | 技术 | `bias`、`loa`、`icc` |

## C2. JSON key 规则

```json
{
  "schema_version": 1,
  "calibration": {"px_per_m": 500.0, "camera_azimuth_deg": 45.0, "camera_level": true},
  "quality": {"track_confidence": 0.94, "occlusion_pct": 2.0, "effective_fps": 59.8},
  "reps": [
    {"metrics": {"mcv_mps": 0.61, "peak_power_w": 512.0}}
  ]
}
```

- 业务 key 只能来自 `MetricId.id`。
- 技术 key 只能来自 Part B。
- 禁止 camelCase JSON key。
- 禁止同一 key 同时表示业务和技术概念。

## C3. 命名检查

- Canonical ID 唯一、snake_case、带单位后缀（`_mps`、`_kg`、`_pct`、`_deg`、`_n`、`_w`、`_j`、`_ns`、`_m`）。
- 中文名不混用“向心/离心”以外的同义词。
- “速度损失”只指 `velocity_loss_pct`，不指组间速度下降。
- “1RM”只指真实/估计单次最大；估计值必须带 `estimated_` 前缀。
- “ROM”默认 `rom_total_m`；向心幅度必须写 `rom_concentric_m`。
- “功率”默认外部功率，不含人体内部功率。
- “RPE”用 0–10 力量训练量表，不用 Borg 6–20。
- 技术术语不进入用户报告正文；需要解释时用 tooltip，不混入指标表。

## C4. 标准与引用

**采用原则**：

- 没有单一 VBT ISO 标准。采用同行评议共识 + SI 单位。
- 优先引用独立验证/系统综述，不引用 Vendor 白皮书作为定义来源。
- 定义冲突时，本表选定一个操作定义；替代定义写入备注。
- 商业 App（Arcus/Vitruve/GymAware/Enode）叫法只作对照，不作标准。

**核心来源**：

1. González-Badillo JJ, Sánchez-Medina L. Movement velocity as a measure of loading intensity in resistance training. *Int J Sports Med*. 2010;31(5):347–352.
2. Sánchez-Medina L, González-Badillo JJ, Pérez CE, Izquierdo M. Velocity- and power-load relationships of the bench press and squat exercises. *Int J Sports Med*. 2011;32(5):349–356.
3. Sánchez-Medina L, González-Badillo JJ. Velocity loss as an indicator of neuromuscular fatigue during resistance training. *Med Sci Sports Exerc*. 2011;43(9):1725–1734.
4. Banyard HG, Nosaka K, Haff GG. Reliability and validity of the load-velocity relationship to predict the 1RM back squat. *J Strength Cond Res*. 2018;32(7):1897–1904.
5. Weakley J, et al. The validity and reliability of commercially available resistance training monitoring devices: a systematic review. *Sports Med*. 2021;51:443–502.
6. Pueo B, et al. Multi-layered systematic review of VBT devices. *PLOS One*. 2025.
7. Helms ER, et al. RPE and velocity relationships for the back squat, bench press, and deadlift in powerlifters. *J Strength Cond Res*. 2017;31(2):292–297.
8. Zourdos MC, et al. Novel resistance training-specific RPE scale measuring repetitions in reserve. *J Strength Cond Res*. 2016;30(1):267–275.
9. Bland JM, Altman DG. Statistical methods for assessing agreement between two methods of clinical measurement. *Lancet*. 1986;1(8476):307–310.
10. Koo TK, Li MY. A guideline of selecting and reporting intraclass correlation coefficients for reliability research. *J Chiropr Med*. 2016;15(2):155–163.
11. Weir JP. Quantifying test-retest reliability using the intraclass correlation coefficient and the SEM. *J Strength Cond Res*. 2005;19(1):231–240.
12. Wu G, et al. ISB recommendation on definitions of joint coordinate systems of various joints for the reporting of human joint motion—Part II. *J Biomech*. 2005;38(5):981–992.
