# 验证报告：多裁剪杠铃片检测修复自动 ROI

- 日期：2026-11-04
- 工作目录：`/mnt/d/CodeRepos/VBTdroid`
- 范围：`TflitePlateDetector` 多裁剪推理、跨裁剪坐标映射与 NMS 合并、纯 JVM 单测、构建、设备安装与自动 ROI 验证

## 1. 问题

深蹲首帧杠铃片靠近画面顶部。全帧 720×1280 送入 320×320 方形模型后杠铃片被严重缩小，PC 实测分数低于阈值：

| 输入 | 最高 plate 分数 |
| --- | --- |
| 全帧 720×1280 | 0.238 |
| 顶部 60% 裁剪 | 0.402 |
| 顶部 40% 裁剪 | 0.457 |
| 硬拉全帧 | 0.512 |

阈值保持 0.35，因此深蹲自动 ROI 检测不到杠铃片，硬拉正常。

## 2. 实现

### 多裁剪推理（仅初始化/恢复）

`TflitePlateDetector.detect()` 对同一帧执行三次推理：

1. 完整位图
2. 顶部 60% 裁剪
3. 底部 60% 裁剪

每次检测独立解析，结果映射回原图坐标后统一合并。`detect()` 只被 `PlateTracker.initialRoi()` 和丢失恢复路径调用，不是逐帧推理，因此 3 次推理成本可接受。

### 坐标映射与合并

`EfficientDetPostprocess` 新增纯 JVM 逻辑：

- `CropRegion(left, top, width, height)`：裁剪区域在原图中的原生像素位置。
- `mapCropDetections(detections, cropWidth, cropHeight, region)`：按 `scaleX = region.width / cropWidth`、`scaleY = region.height / cropHeight` 缩放，再加 `left/top` 偏移；无效尺寸或退化框被丢弃。
- `mergeDetections(detections)`：过滤非法置信度/尺寸后走现有 NMS（IoU 0.45），按置信度降序返回。

裁剪按原生分辨率创建，映射比例通常为 1.0；函数仍支持缩放输入，便于测试和后续调整。

### 去重

跨裁剪重复框（同一杠铃片同时被全帧和顶部裁剪检出）由全局 NMS 合并，保留最高分框。空裁剪（无检测）自然贡献空列表。

### 阈值

`EfficientDetPostprocess.SCORE_THRESHOLD` 保持 0.35，未放宽。

## 3. 测试

新增 `app/src/test/java/com/vbtdroid/app/vision/MultiCropPlateDetectionTest.kt`（6 个测试）：

- 缩放 + 偏移映射回原图；
- 原生分辨率裁剪仅偏移；
- 非法裁剪尺寸返回空；
- 重叠裁剪重复框去重并保持分数排序；
- 空裁剪/不相交检测合并；
- 无检测返回空。

隔离 worktree（提交 `c23ae24`）构建命令：

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

结果：**BUILD SUCCESSFUL**

- `:core:test`：93 个测试，0 失败，0 错误
- `:app:testDebugUnitTest`：135 个测试，0 失败，0 错误
- 新增测试：6 个，全部通过
- `:app:assembleDebug`：成功

说明：主工作区同时有另一会话的未提交改动和并发 Gradle 构建，直接构建会出现 `kspDebugKotlin`/中间产物竞争错误。为保证结果可复现，验证在独立 worktree `/mnt/d/tmp/vbtdroid-multicrop`（仅包含本次提交）完成。

主工作区额外运行 `:app:testDebugUnitTest` 时，另一会话未提交的 `PoseReadoutTest.frameAtPicksNearestWithinGap` 失败（`PoseReadout.kt` 为未跟踪文件，与本提交无关）。

## 4. 设备验证

目标设备：`192.168.57.102:41125`。

- 开始时 adb 显示连接状态为 `device`，但执行安装时变为 `offline`。
- 随后 `adb disconnect`/重连持续 `Connection refused`；`ping` 正常（约 100 ms），但 30000–50000 端口无开放 adb 端口，`adb mdns services` 无结果。
- 重试 6 次仍无法连接，判断设备无线调试已关闭或 adb 端口已变更。

因此：

- APK 安装：**未执行**（设备不可达）。
- 设备自动 ROI 验证：**未完成**。
- PC 侧裁剪分数证据支持本次修复方向，但设备端深蹲/硬拉回归仍需在设备恢复连接后补测。

## 5. 提交

- `c23ae24` `fix(vision): multi-crop plate detection for auto ROI`

## 6. 关注点

1. 跨裁剪去重依赖 NMS IoU 0.45。若同一杠铃片在两个裁剪中框大小差异较大，理论上可能保留两个框；当前裁剪重叠 20%，实际风险低，必要时可加中心距/包含关系去重。
2. 底部 60% 裁剪会在硬拉等场景增加一次推理，但仅初始化/恢复调用，CPU 成本可控。
3. 靠近裁剪边界的杠铃片框会被 `toDetections` 裁剪到裁剪边界，映射后框略小；NMS 合并通常仍能保留高分框，但极端情况下可能影响框尺寸。
4. 设备端回归未完成，需在设备恢复后验证深蹲自动 ROI 出现且硬拉不回退。
