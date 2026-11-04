# 测试策略

## 1. 分层

| 层 | 成本 | 何时跑 | 负责 |
|---|---|---|---|
| core JVM 单测 | 低 | 每个算法批次 | agent |
| app JVM/Robolectric | 中 | UI/数据/服务逻辑批次 | agent |
| Python 参考/golden | 低 | 算法改动后 | agent |
| PC 视频基准 | 低 | 跟踪/计数改动 | agent |
| 真机 UI/手势/模型 | 高 | 批次结束，用户手动 | 用户 |
| 视觉截图分析 | 很高 | 只在必须看图时，一张 | agent/用户 |

当前基线（会变，以实际命令为准）：

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

## 2. 单测重点

### core

- `CalibrationTest`：45°、像素→米
- `KinematicsTest`：非均匀时间戳、线性/二次
- `PhaseSegmenterTest`：多 rep、无锁定、0.10/0.12 锁定、噪声、1/4 次、硬拉正向段
- `RepMetricsTest`：MCV/MPV/PV/ROM/VL/粘滞点/力/功率/null
- `LvpTest`：拟合、负斜率、少样本
- `AgreementTest`：bias/LOA/ICC
- `RepTimelineTest`：当前 rep、间隔保留、首帧无轨迹
- `MetricIdTest`：Canonical ID 唯一/snake_case/技术字段排除

### app

- `AnalysisPipelineTest`：fake 解码/跟踪/姿态，R7 跳过、进度、取消、无姿态
- `AnalysisStateHolderTest`：后台返回恢复进度/结果
- `AnalysisRepositoryTest`：保存/历史/手动阶段持久化
- `PoseReadoutTest` / `JointAngleExtractorTest`：实时姿态卡、按动作关节
- `EfficientDetPostprocessTest` / `MultiCropPlateDetectionTest`：多裁剪映射/NMS
- `EncoderSelectionTest`：硬件编码器优先
- `PlateRoiTest` / `RoiGeometryTest`：ROI 移动/缩放/半径门

## 3. PC 基准

```bash
cd prototype
.venv/bin/python scripts/bench_track.py
```

fixtures：

- `高杠深蹲-135kg-6次-侧面90度.mp4` → 期望 6
- `传统硬拉-100kg-6次-...四分之一次没完成拉起.mp4` → 期望 6（1/4 次剔除）

期望次数只用于评分，不进入算法；不要写死 6。

## 4. 真机测试：用户手动优先

**为什么不让 agent 频繁截图/自动化：**
- 截图 + 视觉模型 token 成本高，且每次只能看到一个状态。
- 手势/滚动/播放/锁屏/权限等交互，自动化脚本很脆。
- 用户手动点几下，回报“次数/是否闪退/关键截图/日志”更快更准。

Agent 应要的证据，按成本从低到高：

1. `adb logcat` 关键字
2. Room DB 查询（次数、warnings、px_per_m）
3. `uiautomator dump` 文字/坐标
4. 一张关键截图（报告页/异常页）
5. 视频/录屏（只在必要时）

### 手动验收清单

**启动**
- 冷启动不闪退；空首页无底部“开始分析”。

**导入**
- 3 页左右滑：视频 → 框选 → 参数。
- 深蹲/硬拉自动 ROI 能出；框错了可拖/缩放/重置。
- 确认后 ROI 锁定；再点“修改区域”才可编辑。
- 片径输入 cm（45）。
- 返回清空视频不退出 App；切后台再回来保留页面/进度。

**分析**
- 进度连续；切后台通知仍在；返回后进度继续。
- 取消能停。
- 完成后报告直接播放 cache MP4，不再二次生成。

**结果**
- 深蹲 6 次、硬拉 6 次（两条样例）。
- 阶段名按动作：深蹲下蹲/底部停顿/站起/锁定；硬拉起始停顿/拉起/锁定。
- 姿态卡实时值：深蹲背/髋/膝/踝；硬拉背/髋/膝；卧推肩/肘。
- 速度/角度曲线游标跟随；轨迹卡无游标；轨迹按 rep 保留、换次清空。
- 卡片不重叠、不超出屏幕。
- 叠加视频有骨架（加粗/环点）、角度标签、轨迹、速度胶囊、数据卡片。
- 保存视频能写到 SAF。

**异常**
- 无检测模型仍可手动 ROI。
- 姿态关闭不跑 MediaPipe，报告显示“姿态分析已关闭”。
- 高遮挡保留角度 + 警告，不静默隐藏。

## 5. 成本规则

- 单元测试/构建：正常用。
- PC 基准：正常用。
- 真机自动化 + 截图 + 视觉模型：少用；一个批次最多 1–2 张关键图。
- 贵模型（Claude/Kimi）：默认禁用；用户明确同意才用。
- 上贵模型前跑 `~/.local/bin/opencode-go-usage --plan plus`。
