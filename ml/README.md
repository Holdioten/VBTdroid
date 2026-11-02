# 模型与训练路径

## MVP 当前范围

- `train_detector.py` 支持单类别 YOLO 数据集校验、数据 YAML 生成，以及个人自用 YOLOv8n 微调/TFLite 导出。
- 执行训练必须显式传入 `--license personal-agpl`。Ultralytics YOLOv8n 采用 AGPL-3.0；未完成许可审查前不得分发模型或应用。
- 训练导出固定为 `640×640`，与 Android 端支持的 `640×640` 输入契约一致。脚本拒绝其他 `--imgsz` 值。
- Apache-2.0 YOLOX-Nano/NanoDet-Plus 训练与 Android 适配尚未实现。此项推迟到发布准备阶段，不可视为当前支持路径；详见 `docs/spec.md` 第 10 节。

数据集格式、模型下载和 SHA256 锁定说明见 `train_detector.py` 与 `download_models.sh`。不要提交模型权重。
