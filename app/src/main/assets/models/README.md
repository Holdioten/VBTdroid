# 模型资产

本目录不提交模型权重。运行 `ml/download_models.sh` 下载 MediaPipe Pose Landmarker Lite，并按 `ml/models.lock` 校验 SHA256。

可选的杠铃片检测模型文件名为 `plate.tflite`。`TflitePlateDetector` 需要单类别 YOLO 输出 `[1, 5, N]`（`cx, cy, w, h, score`），输入支持 `384×224` 或 `640×640`；置信度阈值 `0.35`、NMS IoU `0.45`。将已审查来源的模型 SHA256 加入锁文件后，通过 `PLATE_MODEL_URL` 下载。没有该模型时，检测器构造会明确报错；手动 ROI 跟踪可使用 `PlateTracker(null)`。

发布优先考虑 Apache-2.0 的 YOLOX-Nano 或 NanoDet-Plus，并核验代码、权重与转换工具各自许可。`ml/train_detector.py --train` 提供的 YOLOv8n 是仅供个人 MVP 试验的后备方案；Ultralytics YOLOv8 使用 AGPL-3.0，分发前必须完成许可审查。不得把未授权 Roboflow/个人采集数据或模型权重打包进应用。
