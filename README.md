# VBTdroid

Android 端离线 VBT（Velocity Based Training）分析工具。

导入深蹲、卧推、硬拉视频，在设备本地完成杠铃追踪、速度/轨迹/相位/关节角/负荷指标计算，输出可复核报告。

## 状态

**M0：规格与计划完成，未开始实现。**

## 范围

- 三大项：深蹲、卧推、硬拉
- 侧拍 / 前 45° / 后 45°
- 导入视频，离线分析，无网络权限
- 指标：速度、ROM、杠铃轨迹、相位、速度损失、粘滞点、力/功率、LVP/1RM、关节角、质量标记
- 历史、JSON/CSV 导出

不做：实时采集、云端、教练平台、奥举/跳跃、单机位左右对称、下背腰椎轮廓。

## 文档

- 技术规格：`docs/spec.md`
- 实施计划：`docs/superpowers/plans/2026-11-02-vbtdroid-mvp.md`

## 结构（规划）

```
app/        Android Kotlin：UI、视频解码、ML 适配、Room
core/       纯 Kotlin/JVM：标定、滤波、相位、指标、LVP、JSON
prototype/  Python 参考实现：算法验证、golden vectors
ml/         模型下载脚本与校验；权重不进 git
docs/       规格、计划、验证记录
```

## 构建

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

## 许可

待定。注意：Ultralytics YOLOv8/YOLO11 为 AGPL-3.0；发布前需换 Apache-2.0 检测器或整体采用 AGPL-3.0。详见 `docs/spec.md` §10。
