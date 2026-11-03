# 验证报告：内置杠铃片模型与可编辑 ROI

- 日期：2026-11-03
- 工作目录：`/mnt/d/CodeRepos/VBTdroid`
- 范围：内置 EfficientDet-Lite0 杠铃片检测模型、模型下载与署名、可编辑 ROI、纯几何单测、构建与安装

## 1. 内置杠铃片检测模型

### 来源与许可

- 上游：`https://github.com/simonkosina/vbt`
- 文件：`models/efficientdet_lite0.tflite`
- 许可：MIT License，Copyright (c) 2024 Simon Košina
- 本地资产：`app/src/main/assets/models/plate.tflite`（被 `.gitignore` 排除，不提交权重）
- SHA256：`e5212c33aee524f2e3ba6394db3c9cc6db61adeec379dd478eb1298a0d54e382`
- `ml/models.lock` 已加入该路径与哈希；`ml/download_models.sh` 默认下载该模型并强制校验哈希，仍支持 `PLATE_MODEL_URL` 覆盖（覆盖地址必须与锁文件哈希一致）。
- MIT 署名已写入 `ml/README.md` 与 `app/src/main/assets/models/README.md`。

### 模型契约（主机侧校验）

使用 `ai-edge-litert 2.3.0` 直接检查模型：

- 输入：`[1, 320, 320, 3]` UINT8，量化参数 `scale=0.0078125, zero_point=127`；上游推理直接传入 0–255 原始像素，因此 Android 端 UINT8 路径也直接写入原始字节。
- 输出：
  - scores `[1, 25]` float32
  - boxes `[1, 25, 4]` float32（归一化 `ymin, xmin, ymax, xmax`）
  - count `[1]` float32
  - classes `[1, 25]` float32
- 实际输出张量顺序为 scores、boxes、count、classes；Android 端仍按形状/名称识别，并额外用“类 id 是否为小整数”的内容启发式在名称不可辨识时交换 scores/classes。

### Android 实现

- `TflitePlateDetector` 改为 EfficientDet 四输出解析，使用现有 `Interpreter.runForMultipleInputsOutputs`。
- 输入支持 UINT8（原始 0–255）与 FLOAT32（归一化 0–1）；输入尺寸不再硬编码为 384×224/640×640。
- `EfficientDetPostprocess` 为纯 JVM 后处理：阈值 `0.35`、class 0、正面积框、归一化坐标映射回原图、NMS IoU `0.45`。
- 模型缺失时仍抛出原有明确错误，手动 ROI 路径不受影响。
- 未引入 `tensorflow-lite-task-vision`：现有 Interpreter 已能直接解析该模型，避免额外依赖，同时让后处理可在 JVM 单测覆盖。

## 2. 可编辑 ROI

- 新增 `RoiGeometry.kt`：`hitTest`、`move`、`resize`、`clamp`、`minimumSize`。
- 交互：
  - 框内拖动：整体移动；
  - 距角点 24dp 内拖动：缩放对应角；
  - 框外拖动：重新绘制。
- 边界限制在首帧位图内；最小尺寸为 `max(24px, 5% × min(宽, 高))`，缩放与确认校验均使用该值。
- `RoiSelector` 使用 `rememberUpdatedState(roi)`，`pointerInput(bitmap)` 仅以位图为 key，拖动过程中 ROI 更新不会重启手势。
- 绘制边框与 4 个角点手柄，拖动中的手柄高亮为橙色。
- `ImportScreen` 增加“重置选区 / Reset selection”按钮，清空 ROI 并置 `roiConfirmed=false`。
- 新增提示：中文“拖动框内移动，拖角缩放，框外重新画”，英文对应文案。
- 所有新增 UI 文案均在 `values/strings.xml` 与 `values-en/strings.xml`。

## 3. 验证命令与结果

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

结果：**BUILD SUCCESSFUL**

- `:core:test`：60 个测试，0 失败，0 错误，0 跳过
- `:app:testDebugUnitTest`：46 个测试，0 失败，0 错误，0 跳过
- 新增单测：
  - `EfficientDetPostprocessTest`：解析过滤、count 限制、坐标映射、边界裁剪、NMS、IoU、class-id 启发式
  - `RoiGeometryTest`：角点/内部/外部命中、移动与边界、缩放、最小尺寸、矩形归一化

APK 资产检查：

- `assets/models/plate.tflite`（4,444,718 bytes）已打包
- `assets/models/pose_landmarker_lite.task` 保持打包

## 4. 安装

```bash
adb -s 192.168.57.102:40565 install -r app/build/outputs/apk/debug/app-debug.apk
```

结果：`Success`

安装后启动 `com.vbtdroid.app` 冒烟检查：进程存在（pid 10423），未出现启动崩溃。

## 5. 提交

- `24b665d` feat(vision): bundle MIT EfficientDet-Lite0 plate detector
- `4d498c8` feat(import): make the plate ROI editable

## 6. 已知限制 / 关注点

1. 未在设备上用真实训练视频跑完整检测流程；本次验证覆盖构建、单元测试、资产打包、安装与启动。模型张量契约已用主机 TFLite 解释器核对。
2. 新框选本身不强制扩展到最小尺寸；过小选框会因 `validRoi` 使用最小尺寸而无法确认，可继续拖动角点放大。
3. 类别输出识别优先使用张量名称，名称不明确时使用整数启发式；若上游模型更换为多类别或输出顺序变化，需要重新核对 `EfficientDetPostprocess` 的 class 0 过滤。
4. 模型权重未提交，CI/新环境需先运行 `ml/download_models.sh`。
