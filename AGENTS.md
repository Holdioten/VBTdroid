# AGENTS.md — VBTdroid

本文件是后续 agent/开发者进入本仓库的第一入口。先读本文件，再读 `docs/architecture.md` 和 `docs/status.md`。

## 1. 项目是什么

Android 离线 VBT 分析 App。导入深蹲/卧推/硬拉视频，本地追踪杠铃片、估计姿态、计算速度/ROM/阶段/负荷指标，输出视频优先的报告和带数据卡片的叠加 MP4。

- 目标用户：个人力量训练者
- 目标场景：专门拍摄、固定机位、杠铃片可见、动作正常
- 不做：实时采集、云端、教练平台、奥举/跳跃、通用体育视频分析

## 2. 硬规则（必须遵守）

### 构建与并发

1. **同一 worktree 同时只能有一个 Gradle 构建。** 多个 agent 并行 `assembleDebug` 会踩坏 `core/build/.transforms`，产生 `NoClassDefFoundError: com.vbtdroid.core.model.Lift`。
2. 出现 `NoClassDefFoundError` 或 KSP `annotationProcessors.json` 缺失时：
   - 停掉所有 Gradle 进程
   - `rm -rf app/build core/build .kotlin`
   - 单次 `./gradlew :app:assembleDebug --no-daemon`
   - 用 `dexdump` 确认 `Lcom/vbtdroid/core/model/Lift;` 真的在某个 dex 里
3. 不要多个 agent 同时改同一文件。并行任务必须切分文件/模块，或串行执行。
4. 用户要求“攒着”时：不要每改一行就构建/安装/截图。一个批次结束再跑一次全量。

### 设备测试

1. 设备用无线 ADB；端口由用户提供，不要硬编码旧端口。
2. 真机 UI/手势/模型效果由用户手动测更便宜、更可靠。Agent 不要频繁截图 + 视觉模型。
3. Agent 优先用低成本证据：
   - `adb logcat -d | grep ...`
   - `adb shell run-as com.vbtdroid.app cat databases/vbtdroid.db` 拉 DB
   - `uiautomator dump` 只取文字/坐标
   - 只在“必须看视觉”时截一张最终图
4. 安装 APK 前确认 `app/build/outputs/apk/debug/app-debug.apk` 是新构建，且 dex 里有 core 类。

### 模型与成本

1. 默认实现模型：**DeepSeek V4.1 Flash**。
2. UI 小修/审图：**MiMo V2.6 Flash**（比 Claude 便宜）。
3. 禁用：**Claude、Kimi K3.1 Pro**，除非用户明确同意。
4. 上贵模型前先跑：
   ```bash
   ~/.local/bin/opencode-go-usage --plan plus
   ```
   5h 用量 >80% 就换 DeepSeek/拆任务。
5. 不要为“确认一下”再开一轮大模型；单元测试和日志优先。

### 数据与单位

1. UI 输入片径用 **cm**，默认 45；构建 `AnalysisRequest` 时 `/100` 转米。
2. 内部计算用米；数据库/旧导出用 mm；Canonical ID 的单位见 `docs/glossary.md`。
3. `core` 不依赖 Android/OpenCV/TFLite/MediaPipe。
4. 时间只用 PTS，不用帧号。
5. 不可用指标用 `null` + 原因；禁止 0 假值。

### UI 文案

用户原则：**组件/画面能表达的不写文字；文字只补不可见信息。**

- 删重复提示、长说明、标题复述
- 保留字段/单位、错误、空态一句、10 分钟限制等不可推断信息
- 指标标题用“姿态分析”“阶段”，不再用“关节角”“相位”

### 输出

1. 手机端主输出 = **叠加视频 MP4**（骨架/角度/轨迹/数据卡片）。
2. JSON/CSV 仅保留内部验证用途，手机 UI 不再暴露。
3. 报告视频优先，卡片叠在视频上；不要退回长滚动列表。

## 3. 架构速览

```
app/        Android：导入、解码、跟踪、姿态、前台服务、叠加导出、Compose UI、Room
core/       纯 Kotlin/JVM：标定、滤波、阶段、指标、LVP、质量、JSON
prototype/  Python 参考实现 + PC 基准脚本
ml/         模型下载/训练脚本；权重不进 git
docs/       规格、术语、架构、开发、测试、状态、验证
```

数据流：

```
SAF 视频 → VideoDecoder(PTS) → PlateTracker(Hough/椭圆)
  → Calibration → Kinematics → PhaseSegmenter → RepMetrics
  → PoseEstimator(最近杠铃的人) → FrameAngles
  → AnalysisForegroundService
      → 保存 Room
      → VideoOverlayExporter(数据卡片) → cache MP4
  → ReportScreen(VideoView + 卡片 + 游标)
```

详见 `docs/architecture.md`。

## 4. 常用命令

```bash
export JAVA_HOME="$HOME/toolchains/jdk17"
export PATH="$JAVA_HOME/bin:$HOME/toolchains/gradle-8.10.2/bin:$PATH"
export ANDROID_HOME="$HOME/toolchains/android-sdk"
export ANDROID_SDK_ROOT="$HOME/toolchains/android-sdk"

# 单次构建（确认没有其他 Gradle 在跑）
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon

# 清理后构建
rm -rf app/build core/build .kotlin
./gradlew :app:assembleDebug --no-daemon

# 设备
adb devices -l
adb connect <IP:PORT>
adb -s <IP:PORT> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <IP:PORT> logcat -d -v brief | grep -E 'FATAL|VBTDebug|Overlay'
adb -s <IP:PORT> shell run-as com.vbtdroid.app cat databases/vbtdroid.db > /tmp/vbtdroid.db

# PC 基准（两条样例视频）
python3 prototype/scripts/bench_track.py
```

## 5. 测试策略

- 单元测试/JVM 测试：便宜，改完一个批次跑一次。
- PC 基准：算法改动必须先在 `example/` 两条视频上跑，输出 inferred/expected。
- 真机：用户手动跑，回报次数/截图/日志；agent 不逐屏截图。
- 详细清单见 `docs/testing.md`。

## 6. 已知坑

1. **并发 Gradle 损坏 APK**：见 §2。
2. **Hough 搜索框必须 ≥1.35×片径**：小于片径会只看到局部弧，轨迹漂。
3. **EfficientDet 全图漏检上方片**：自动 ROI 用全图+顶60%+底60% 三路检测。
4. **硬拉阶段**：正式次数从离地到锁定；不包含下放；1/4 次用 ROM 中位数过滤。
5. **姿态**：MediaPipe 多候选，选躯干中点到杠铃最近的人；只算动作相关关节。
6. **叠加导出慢**：优先硬件 AVC 编码器；仍慢就降分辨率/分段，不要阻塞主线程。
7. **报告视频时间 = 源视频时间**：若叠加 MP4 有偏移，卡片/游标会错位。
8. **缓存 overlay MP4**：只保留最新几个；旧历史可能指向已删文件。
9. **模型资产不在 git**：`ml/download_models.sh` 下载并校验 SHA256；`plate.tflite` 是可选资产，但当前 APK 构建时会打包本地文件。
10. **JSON/CSV**：core 里保留，UI 已删；不要再加回手机端。

## 7. 工作流

1. 读 `AGENTS.md` → `docs/status.md` → 相关源码。
2. 改算法先写/改纯函数测试，再改实现。
3. 一个批次一个 commit；commit message 用 `fix(scope): ...` / `feat(scope): ...`。
4. 构建前确认无并发 Gradle；构建后如需设备验证，交给用户手动。
5. 文档随行为更新：`docs/status.md` 的“待验证/已知问题”必须保持真实。
6. 不要擅自 push/merge 共享分支；先问用户。

## 8. 当前优先事项

见 `docs/status.md`。当前主要是真机验证与少量 UI/计数回归，不是大规模重构。
