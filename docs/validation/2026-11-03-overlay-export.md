# 验证报告：叠加视频导出（烧录式 MP4）

- 日期：2026-11-03
- 工作目录：`/mnt/d/CodeRepos/VBTdroid`
- 提交：`e332cc2`、`75aa134`（基础渲染/导出代码来自工作区已有的 `e0d0ce6`）
- 范围：数据模型采集、纯 JVM 时间轴查询、Bitmap 叠加渲染、MediaCodec/MediaMuxer 导出、报告页入口、单测与构建

## 1. 实现内容

### 数据模型与流水线

- `AnalysisResult` 新增 `observations`、`poseFrames`、`frameAngles`，默认空列表，现有测试/fake 保持可编译。
- `AnalysisPipeline.analyze` 在每次姿态估计成功时同时收集 `PoseFrame` 与 `FrameAngles`，并将已有 `observations` 一并放入结果。
- 指标计算逻辑未改动。

### 叠加时间轴（纯 JVM）

新增 `app/src/main/java/com/vbtdroid/app/overlay/OverlayTimeline.kt`：

- `nearestObservation` / `nearestPoseFrame` / `nearestFrameAngles` / `nearestMotionSample`：二分查找最近时间点。
- `trailSamples` / `trail`：返回 `[t - window, t]` 区间视图，不复制列表。
- `speedMps`：最近运动样本的垂直速度绝对值。
- 单测覆盖最近点、空输入、边界、轨迹窗口与速度取值。

### 渲染器

新增 `OverlayRenderer.kt`，在 ARGB Bitmap 上绘制：

- 白色细线骨骼 + 关节圆点（肩、髋、膝、踝、肘、腕）。
- 髋/膝/踝角度标签：白字 + 深色半透明圆角底，数值取最近 `FrameAngles`。
- 绿色杠铃轨迹（当前时刻之前 2 秒），当前点高亮。
- 杠铃片附近的琥珀色速度胶囊：`0.85 m/s`。
- 左上角紧凑 HUD：动作/变式、负荷、rep/相位、当前 MCV 或速度损失。
- 线宽、字号按帧宽缩放；Paint/Path/RectF 复用；缺姿态或缺角度时只跳过对应元素。

### 导出器

新增 `VideoOverlayExporter.kt`：

- 复用 `VideoDecoder.decode(Uri)` 默认参数，确保像素坐标与流水线一致。
- H.264 (`video/avc`) + `MediaMuxer` 写入 SAF 文件描述符；无音轨。
- 1080p 约 8 Mbps，30 fps，1 秒 I 帧；按帧宽高等比调整码率；输入尺寸取偶数。
- 保留源 PTS；PTS 非递增时递增 1 µs 兜底。
- 优先 `COLOR_FormatYUV420SemiPlanar`，不支持时回退 `COLOR_FormatYUV420Planar`；无可用 YUV420 编码器直接报错。
- 处理 output format change、EOS、编码器无输出、异常释放；逐帧转换后立即回收解码 Bitmap。
- `observations` 为空时抛出明确错误 `No plate observations available`。
- `suspend fun export(..., onProgress)`，进度 0..1，整体运行在 `Dispatchers.IO`。

### 报告页

- 新增“导出叠加视频 / Export overlay video”按钮。
- `ActivityResultContracts.CreateDocument("video/mp4")`，默认文件名 `vbtdroid-overlay.mp4`。
- 导出期间禁用按钮并显示百分比进度；完成后显示成功/失败原因。
- 缺杠铃片轨迹时显示“没有杠铃片轨迹，无法导出叠加视频”。
- 中英文文案均已补充。

## 2. 验证命令与结果

在独立干净 worktree（`/tmp/opencode/verify-vbt`，detached at `75aa134`）执行，避免主工作区并行未提交改动干扰：

```bash
export JAVA_HOME="$HOME/toolchains/jdk17"
export PATH="$HOME/toolchains/jdk17/bin:$HOME/toolchains/gradle-8.10.2/bin:$PATH"
export ANDROID_HOME="$HOME/toolchains/android-sdk"
export ANDROID_SDK_ROOT="$HOME/toolchains/android-sdk"
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

结果：**BUILD SUCCESSFUL**

- `:core:test`：通过
- `:app:testDebugUnitTest`：通过（含 `OverlayTimelineTest`）
- `:app:assembleDebug`：通过
- 产物：`/tmp/opencode/verify-vbt/app/build/outputs/apk/debug/app-debug.apk`

## 3. 安装

目标设备 `192.168.57.102:40565` 当前不可用：

```text
adb connect 192.168.57.102:40565
failed to connect to '192.168.57.102:40565': Connection refused
```

`adb devices` 仅有 `emulator-5562 offline`。因此未安装，未做真机播放验证。

## 4. 真机手动检查清单（待执行）

1. 导入并分析一段姿态可见、杠铃片跟踪正常的视频。
2. 报告页点击“导出叠加视频”，选择保存位置；确认按钮在导出期间禁用且进度递增。
3. 导出完成后用系统播放器打开 MP4：
   - 无人声/音轨；
   - 画面尺寸为偶数，方向与源视频一致；
   - 骨骼线、关节点、髋/膝/踝角度标签位置对齐；
   - 绿色轨迹随播放增长，当前点高亮；
   - 速度胶囊显示 `x.xx m/s`，位置贴近杠铃片；
   - 左上 HUD 不遮挡大面积画面，rep/相位/MCV 随时间变化。
4. 使用无姿态结果的视频复测：应只显示轨迹/速度/HUD，不崩溃。
5. 使用 `observations` 为空的结果触发导出：应显示缺少杠铃片轨迹的错误。
6. 在仅支持 `COLOR_FormatYUV420Planar` 的设备/编码器上复测颜色与画面是否正常。
7. 抽查播放时长、帧时间戳与源视频一致。

## 5. 风险与限制

- 未在真机验证 MediaCodec 编码路径；`COLOR_FormatYUV420SemiPlanar` 按 NV12 处理，个别设备若实际要求 NV21 可能出现色度偏差，需要真机确认。
- `OverlayRenderer` 的轨迹使用平滑后的 `motionSamples` 反算像素坐标；`calibration` 为空时轨迹不绘制，但速度/骨骼/HUD 不受影响。
- 主工作区存在并行未提交的相位分割/角度平滑改动；本报告只对上述提交的干净状态负责。
