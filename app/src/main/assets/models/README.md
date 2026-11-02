# 模型资产

本目录不提交模型权重。运行 `ml/download_models.sh` 下载 MediaPipe Pose Landmarker Lite，并按 `ml/models.lock` 校验 SHA256。

可选的杠铃片检测模型文件名为 `plate.tflite`。`TflitePlateDetector` 需要单类别 YOLO 输出 `[1, 5, N]`（`cx, cy, w, h, score`），输入支持 `384×224` 或 `640×640`；置信度阈值 `0.35`、NMS IoU `0.45`。将已审查来源的模型 SHA256 加入锁文件后，通过 `PLATE_MODEL_URL` 下载。没有该模型时，检测器构造会明确报错；手动 ROI 跟踪可使用 `PlateTracker(null)`。

当前仅实现个人自用 YOLOv8n 训练路径：运行 `ml/train_detector.py ... --train --license personal-agpl`。Ultralytics YOLOv8n 使用 AGPL-3.0；未经许可审查不得分发模型或应用。Apache-2.0 检测模型训练和适配未实现，留待发布准备阶段决策。不得把未授权数据或模型权重打包进应用。
