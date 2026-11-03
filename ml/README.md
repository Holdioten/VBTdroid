# 模型与训练路径

## 内置杠铃片检测模型（MIT）

`ml/download_models.sh` 默认下载两个模型并按 `ml/models.lock` 校验 SHA256：

- MediaPipe Pose Landmarker Lite
- EfficientDet-Lite0 杠铃片检测模型

杠铃片模型来自 [simonkosina/vbt](https://github.com/simonkosina/vbt)（`models/efficientdet_lite0.tflite`），采用 MIT License，Copyright (c) 2024 Simon Košina。模型检测单类别 `barbell`（class id 0），输入 `[1, 320, 320, 3]` UINT8，输出 scores `[1, 25]`、boxes `[1, 25, 4]`（归一化 `ymin, xmin, ymax, xmax`）、count `[1]`、classes `[1, 25]`。Android 端 `TflitePlateDetector` 使用置信度阈值 `0.35` 和 NMS IoU `0.45`。

默认下载地址：

```text
https://raw.githubusercontent.com/simonkosina/vbt/main/models/efficientdet_lite0.tflite
```

可用 `PLATE_MODEL_URL` 覆盖下载地址，但对应 SHA256 必须同步更新到 `ml/models.lock`。模型权重不提交到 git。

MIT 许可要求的版权与许可声明保留在本文件及 `app/src/main/assets/models/README.md`；完整 MIT 文本见上游仓库 `LICENSE`。

## 个人自用 YOLOv8n 训练路径

- `train_detector.py` 支持单类别 YOLO 数据集校验、数据 YAML 生成，以及个人自用 YOLOv8n 微调/TFLite 导出。
- 执行训练必须显式传入 `--license personal-agpl`。Ultralytics YOLOv8n 采用 AGPL-3.0；未完成许可审查前不得分发模型或应用。
- 训练导出固定为 `640×640`。该训练路径与 Android 端内置的 MIT EfficientDet 检测模型相互独立。
- Apache-2.0 YOLOX-Nano/NanoDet-Plus 训练与 Android 适配尚未实现。此项推迟到发布准备阶段，不可视为当前支持路径；详见 `docs/spec.md` 第 10 节。

数据集格式、模型下载和 SHA256 锁定说明见 `train_detector.py` 与 `download_models.sh`。不要提交模型权重。
