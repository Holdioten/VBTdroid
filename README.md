# VBTdroid

Android 端离线 VBT（Velocity Based Training）分析工具。

导入深蹲、卧推、硬拉视频，本地追踪杠铃片、估计姿态、计算速度/ROM/阶段/负荷指标，输出视频优先的报告和带数据卡片的叠加 MP4。

## 状态

**MVP 代码完成，真机回归进行中。** 当前优先级、已知问题、待验证清单见 [`docs/status.md`](docs/status.md)。

- 目标场景：专门拍摄、固定机位、杠铃片可见、动作正常
- 平台：Android，离线，无网络权限
- 当前 APK：arm64 debug，约 62MB

## 范围

- 三大项：深蹲、卧推、硬拉
- 侧拍 / 前 45° / 后 45°
- 导入视频，离线分析
- 指标：速度、ROM、轨迹、阶段、速度损失、粘滞点、力/功率、LVP/1RM、姿态角、质量
- 历史、叠加视频保存、同视频复用分析
- 1RM 估算（每组独立拟合，标注仅供参考）
- 品牌资产与生成脚本：[`design/brand/`](design/brand/)
- JSON/CSV 仅内部验证，不在手机 UI

不做：实时采集、云端、教练平台、奥举/跳跃、单机位左右对称、下背腰椎轮廓。

## 文档

- 使用说明（面向使用者）：[`docs/user-guide.md`](docs/user-guide.md)
- 开发入口 / agent 规则：[`AGENTS.md`](AGENTS.md)
- 技术规格：[`docs/spec.md`](docs/spec.md)
- 术语与指标：[`docs/glossary.md`](docs/glossary.md)
- 架构：[`docs/architecture.md`](docs/architecture.md)
- 开发/构建/设备：[`docs/development.md`](docs/development.md)
- 测试策略：[`docs/testing.md`](docs/testing.md)
- 当前状态：[`docs/status.md`](docs/status.md)
- UI 设计稿：[`docs/ui-redesign-spec.md`](docs/ui-redesign-spec.md)
- 验证记录：[`docs/validation/`](docs/validation/)

## 结构

```
app/        Android Kotlin：UI、视频解码、跟踪、姿态、前台服务、overlay、Room
core/       纯 Kotlin/JVM：标定、滤波、阶段、指标、LVP、质量、JSON
prototype/  Python 参考实现 + PC 基准脚本
ml/         模型下载/训练脚本；权重不进 git
docs/       规格、术语、架构、开发、测试、状态、验证
example/    本地样例视频/截图（gitignored）
```

## 构建

```bash
export JAVA_HOME="$HOME/toolchains/jdk17"
export PATH="$JAVA_HOME/bin:$HOME/toolchains/gradle-8.10.2/bin:$PATH"
export ANDROID_HOME="$HOME/toolchains/android-sdk"
export ANDROID_SDK_ROOT="$HOME/toolchains/android-sdk"

./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

同一 worktree 同时只能有一个 Gradle 构建。APK 缺 core 类时按 `docs/development.md` 清理重建。

## 语言与标准

- UI 默认中文；英文资源结构预留。
- 指标 Canonical ID 英文 `snake_case`；中英文显示名见术语表。
- 术语表分业务术语（输出端）与技术术语（拍摄/处理端），禁止混用。
- 定义遵循同行评议共识与 SI 单位；争议指标标注本实现定义。

## 许可

个人 MVP。检测器发布路径需换 Apache-2.0 模型或整体 AGPL；分发前完成许可审查。详见 `docs/spec.md` §10。
