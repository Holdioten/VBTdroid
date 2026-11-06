# 模型资产

本目录不提交模型权重。运行 `ml/download_models.sh` 会按 `ml/models.lock` 校验 SHA256 并下载：

- MediaPipe Pose Landmarker Lite（`pose_landmarker_lite.task`）
- EfficientDet-Lite0 杠铃片检测模型（`plate.tflite`）

## 杠铃片检测模型（MIT）

`plate.tflite` 来自 [simonkosina/vbt](https://github.com/simonkosina/vbt) 的 `models/efficientdet_lite0.tflite`，采用 MIT License，Copyright (c) 2024 Simon Košina。模型由该仓库训练，用于检测杠铃片/杠铃（单类别 `barbell`，class id 0）。

模型契约：

- 输入：`[1, 320, 320, 3]` UINT8，RGB，直接使用 0–255 原始像素。
- 输出：scores `[1, 25]`、boxes `[1, 25, 4]`（归一化 `ymin, xmin, ymax, xmax`）、count `[1]`、classes `[1, 25]`。
- 后处理：置信度阈值 `0.35`，仅保留 class 0 与正面积框，NMS IoU `0.45`；坐标映射回原始首帧像素。

`TflitePlateDetector` 通过输出张量形状识别各输出，不依赖固定顺序。没有 `plate.tflite` 时，检测器构造会给出明确错误；此时仍可仅使用手动 ROI 跟踪（`PlateTracker(null)`）。

如需替换模型，将已审查来源的 SHA256 写入 `ml/models.lock` 的 `app/src/main/assets/models/plate.tflite` 条目，并通过 `PLATE_MODEL_URL` 指定下载地址；脚本仍会强制校验哈希。

## 训练路径

`ml/train_detector.py` 仍提供个人自用 YOLOv8n（AGPL-3.0）训练路径：`ml/train_detector.py ... --train --license personal-agpl`。该路径与上方内置 MIT 检测模型相互独立；未经许可审查不得分发 AGPL 训练的模型或应用。不得把未授权数据或模型权重打包进应用。

姿态估计模型文件名为 `pose_landmarker_lite.task`，放入本目录以启用 MediaPipe Pose Landmarker。模型缺失时，`MediaPipePoseEstimator` 会给出明确错误；初始化时优先尝试 GPU delegate，失败后回退到 CPU。模型获取与 SHA256 校验按上述 `ml/download_models.sh` 和 `ml/models.lock` 说明执行。


## YOLO11n 杠铃片检测模型（自训）

`plate_yolo.tflite`：YOLO11n，输入 `[1, 3, 416, 416]` float32 RGB（0..1，letterbox 114），
输出 `[1, 4 + 2, anchors]`：归一化 `cx, cy, w, h` + `plate`/`bar_end` 分数。
由本项目用自有视频标注训练；权重不进 git（`assets/models/*.tflite` 已忽略）。
缺失时 `AnalysisForegroundService` 回退到旧的 EfficientDet `plate.tflite`。
