# 2026-11-04 深蹲/卧推 y 极值回收 rep

## 问题

真机 22 s 深蹲视频有 6 个真实 rep，`PhaseSegmenter` 只返回 1 个，并带
`INCOMPLETE_REP`、`NO_LOCKOUT`。同一视频在 PC 上用 y 极值可干净找到 6 个底部峰。
说明误差来自速度相位段合并（丢失周期），不是杠铃轨迹本身。

## 实现

`core/src/main/kotlin/com/vbtdroid/core/phases/PhaseSegmenter.kt`

正常相位段分割完成后，仅对 `Lift.SQUAT` / `Lift.BENCH` 增加 y 极值兜底：

1. 对 `yM` 做半窗 0.12 s 的移动平均；用符号翻转找 turn point；小于
   `max(1e-4, 0.0001 * 行程, 0.05 * minRomM)` 的位移视为平台，避免顶部/底部噪声
   被当成新极值。
2. 极值按“最小振幅优先”逐个剔除，直到每个极值到相邻极值的最小 y 振幅
   `>= config.minRomM`；这同时满足 prominence/amplitude 门槛。
3. 只保留 top/bottom 交替序列；同类型极值间隔要求
   `>= max(config.minRepSec, 0.35 s)`。
4. 起点/终点只有在边缘 0.20 s 内 y 波动 `<= 2 cm`（静止）时才作为边界极值；
   因此视频从动作中途开始/结束时不会伪造半个 rep。没有前后两个 top 的 bottom 直接跳过。
5. 每个 rep = 前 top → bottom → 后 top：
   - `ECCENTRIC`：start → bottom（若检测到 pause，则到 pause 起点）
   - `PAUSE`：bottom 附近连续 `|vy| <= vEps` 的段，且不挤占最小时长的 CONCENTRIC
   - `CONCENTRIC`：pause 结束/bottom → end
   - `LOCKOUT`：末尾连续 `|vy| <= vEps` 且时长 `>= lockoutSec`，按 `lockoutSec` 截断
   - 速度轨迹完全退化（整段 `|vy| <= vEps`）时只产出 ECCENTRIC + CONCENTRIC
6. 校验 `minRepSec`、`minConcentricSec`、`minRomM`，再走现有
   `dropRomOutliers`（中位数 ROM 相对过滤），索引从 1 连续重排。
7. **只有兜底 rep 数严格大于原结果时才替换**；原结果 already >= 时原样返回。
   `Lift.DEADLIFT` 路径完全未改。

### y 方向说明（与任务描述不同，重要）

任务描述写的是“bottom = max y, top = min y”。本仓库的 `MotionSample.yM` 是
**向上为正**：`Calibration.toMeters` 里 `yM = -observation.yPx / pxPerM`，且速度相位
分类把 `vy < -vEps` 判为 ECCENTRIC（下放）。因此本实现使用：

- bottom = 局部 **y 最小值**
- top = 局部 **y 最大值**

这与仓库现有测试（下放阶段 `dy` 为负）和 `Calibration` 一致。PC 端之所以看到
“底部是 max y”，是因为直接用像素坐标（向下为正）。如果按字面用 max y 当 bottom，
会把深蹲的顶部当底部，相位整体反转。请在真机回归时按本实现的坐标系理解。

## 测试

`core/src/test/kotlin/com/vbtdroid/core/phases/PhaseSegmenterTest.kt` 新增：

- `six noisy cycles recover from y extrema when the velocity track is flat`：
  6 个合成周期 + 8 mm 噪声，速度轨迹压平（正常相位路径返回 0），断言兜底得到
  6 reps、索引 1..6、每 rep 有 ECCENTRIC/CONCENTRIC、ROM ≈ 0.55 m。
- `eight noisy cycles are still eight bench reps with a realistic velocity track`：
  8 个合成卧推周期 + 噪声，用 y 差分生成真实速度，断言 8 reps、索引 1..8。
- `extrema fallback ignores incomplete leading and trailing edges`：
  从下放中途开始、在上行中途结束，只有中间 1 个完整周期，断言 1 rep。
- 现有深蹲/卧推/硬拉用例全部保留（19 个旧用例 + 3 个新用例 = 31 个
  `PhaseSegmenterTest`）。

命令（只跑 core，不构建 app）：

```bash
export JAVA_HOME="$HOME/toolchains/jdk17"
export ANDROID_HOME="$HOME/toolchains/android-sdk"
./gradlew :core:test --no-daemon --max-workers=1
```

结果：`BUILD SUCCESSFUL`；core 共 99 个测试，0 failed / 0 skipped。

## 未覆盖

- 真机/真视频回归未跑（本次只跑 JVM core 测试，按要求不做 app 构建）。
- 22 s 深蹲样例是否从 1 → 6 需要 controller 侧 clean build 后在设备上确认。
- 卧推只有合成用例，没有真机视频。
- 兜底仍依赖速度判断 pause/lockout；速度轨迹完全错误时只保证 rep 计数与
  ECCENTRIC/CONCENTRIC 两相，lockout 会作为 warning 暴露。
