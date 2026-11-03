# VBTdroid

Android 端离线 VBT（Velocity Based Training）分析工具。

导入深蹲、卧推、硬拉视频，在设备本地完成杠铃追踪、速度/轨迹/相位/关节角/负荷指标计算，输出可复核报告。

## 状态

**M1 已完成：Gradle 多模块骨架、core 指标引擎、Android 导入/分析/报告/历史/导出主流程与自动化测试均已实现；真机模型与视频兼容性验证见 `docs/validation/`（待补）。**

## 范围

- 三大项：深蹲、卧推、硬拉
- 侧拍 / 前 45° / 后 45°
- 导入视频，离线分析，无网络权限
- 指标：速度、ROM、杠铃轨迹、相位、速度损失、粘滞点、力/功率、LVP/1RM、关节角、质量标记
- 历史、JSON/CSV 导出

不做：实时采集、云端、教练平台、奥举/跳跃、单机位左右对称、下背腰椎轮廓。

## 文档

- 技术规格：`docs/spec.md`
- 术语与指标对照：`docs/glossary.md`
- 实施计划：`docs/superpowers/plans/2026-11-02-vbtdroid-mvp.md`

## 语言与标准

- UI 默认中文；英文资源结构预留，后续补翻译。
- 指标 Canonical ID 用英文 `snake_case`；中文/英文显示名见术语表。
- 术语表分 Part A 业务术语（输出端）与 Part B 技术术语（拍摄/处理端），禁止混用。
- 指标定义遵循同行评议共识与 SI 单位，不绑定商业 App 术语。
- 定义有争议的指标（MPV、粘滞点、RPE）在术语表标注本实现定义与替代定义。

## 结构

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

MVP 仅提供个人自用 YOLOv8n（AGPL-3.0）训练路径；Apache-2.0 检测器训练/适配尚未实现，留待发布准备阶段评估。分发前须完成许可审查。详见 `docs/spec.md` §10。
