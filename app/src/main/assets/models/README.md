# 模型资源

将 `pose_landmarker_lite.task` 放入此目录以启用 MediaPipe 姿态估计。模型文件不纳入版本控制；文件缺失时，应用会给出明确错误。`MediaPipePoseEstimator` 优先尝试 GPU delegate，失败后回退到 CPU。

后续添加其他可选推理模型时，可在此补充说明。
