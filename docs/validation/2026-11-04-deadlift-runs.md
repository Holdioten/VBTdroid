# 2026-11-04 硬拉按正向速度段计次

## 背景

真机 27.6 s 硬拉视频（标注 6 次）在 `PhaseSegmenter` 只得到 4 个 rep：

```
0.70-1.87, 3.87-5.13, 10.40-11.97, 14.57-15.73   // 全部仅 CONCENTRIC
warnings: INCOMPLETE_REP, NO_LOCKOUT
```

跟踪本身正常（`pxPerM=945.96`，827 个观测，y 范围 -1.17..-0.41 m，vy 范围
-1.08..+1.01 m/s）。旧的硬拉路径复用“离心/向心成对”的 phase-run 逻辑，缺少数
rep 的配对与速度方向切换，导致最后两次真 rep 丢失。

## 改动

仅对 `Lift.DEADLIFT` 生效，squat/bench 完全保留原逻辑。

`core/src/main/kotlin/com/vbtdroid/core/phases/PhaseSegmenter.kt`

1. `segmentDetailed` 中硬拉提前分支到 `segmentDeadliftsByVelocity(samples, config)`，
   不再走 `classify`/`detectLockouts`/`validRep` 的成对 phase 路径。
2. 正向速度段检测：
   - 进入：`vy > vEps`；退出：`vy <= vEps * 0.5`（迟滞，避免速度小坑切断 rep）。
   - run 起点前最多回看 0.5 s，取局部 y 最小点作为底部；回看不会越过上一段的结束样本。
   - run 结束后取边界样本（顶部）作为 rep 结束；rep = 底部到顶部。
   - 过滤：`duration >= minRepSec`、`concentricSec >= minConcentricSec`、`ROM >= minRomM`。
   - 两个正向段间隔 `<= 0.18 s` 且 y 下跌 `< minRomM * 0.25` 时合并，避免中途速度坑拆 rep。
   - 保留集合中位数 ROM 过滤：`ROM < median * 0.5`（>=3 个候选时）丢弃四分之一拉，再重排 index。
   - 相位：`[CONCENTRIC(底部→顶部)]`，若顶部后存在时长足够的近零速度平台，再追加最长
     `lockoutSec` 的 `LOCKOUT`。不要求 LOCKOUT，只保留原有 `NO_LOCKOUT` 提示。

关键常量（`PhaseSegmenter` 私有）：

| 常量 | 值 |
| --- | --- |
| `DEADLIFT_EXIT_EPS_FRACTION` | 0.5 |
| `DEADLIFT_GAP_MERGE_SEC` | 0.18 s |
| `DEADLIFT_GAP_MERGE_ROM_FRACTION` | 0.25 |
| `DEADLIFT_BOTTOM_LOOKBACK_SEC` | 0.5 s |

## 测试

`core/src/test/kotlin/com/vbtdroid/core/phases/PhaseSegmenterTest.kt` 新增：

- `six positive velocity runs plus a quarter pull infer six deadlift reps`：6 个完整正向段
  + 1 个 ROM 不足的小幅拉 → 6 reps，索引 1..6，含 `INCOMPLETE_REP`。
- `velocity dip inside a deadlift rep does not split it`：rep 中途速度掉到 0 共 0.1 s →
  仍为 1 个 rep。
- `deadlift with no positive velocity runs infers no reps`：全负/零速度 → 0 reps。
- 原有 squat/bench 与硬拉用例全部保留（28 个 `PhaseSegmenterTest` 用例通过）。

```
JAVA_HOME=~/toolchains/jdk17 ANDROID_HOME=~/toolchains/android-sdk \
./gradlew :core:test --rerun-tasks --no-daemon
```

结果：`BUILD SUCCESSFUL`，core 96 tests / 0 failures。

```
./gradlew :core:test :app:assembleDebug --no-daemon
```

结果：`BUILD SUCCESSFUL`。`:app:testDebugUnitTest` 共 149 个用例，其中 1 个失败
`PoseReadoutTest.frameAtPicksNearestWithinGap`；该文件属于工作区里未提交的
`PoseReadout.kt`/`PoseReadoutTest.kt`（非本次改动），失败原因与硬拉计次无关：
期望 `frameAt` 返回 null，实际返回 t=2.0 的 FrameAngles。未修改该文件。

## PC 参考回归

用 `prototype/scripts/bench_track.py` 的同一段硬拉 fixture（含四分之一拉）做离线对照：

```
cd example
~/miniforge3/envs/py310/bin/python /tmp/opencode/det2.py \
  '传统硬拉-100kg-6次-第二次和第三次中间有四分之一次没完成拉起.mp4'
```

把同一套“正向速度段 + 0.18 s 合并 + 0.5 s 底部回看 + 中位数 ROM”规则跑在
720p 原片和上采样 1080p 上，均得到 6 个保留 rep（四分之一拉 ROM 0.21 m 被中位数阈值
0.32 m 丢掉）。对应真 rep 起点约 0.8 / 3.9 / 10.9 / 14.8 / 18.8 / 25.2 s。

## 真机回归

- 安装到 `192.168.57.102:41125`：`Success`（首次改动后的 APK）。
- 重跑同一硬拉视频，`VBTDebug` 输出：

```
pxPerM=945.9555392795139 obs=827 ... reps=4
repsDetail=[0.666666-1.866666:[CONCENTRIC], 3.833333-5.1:[CONCENTRIC],
            10.4-11.966666:[CONCENTRIC], 14.6-15.733333:[CONCENTRIC]]
warnings=[INCOMPLETE_REP, NO_LOCKOUT]
```

仍是 4 次，与改动前一致（起点略提前，符合新的底部回看）。随后为了打印每段候选的
底部/时长/ROM 重新构建并安装成功，但设备无线 ADB 在分析过程中断开
（`Connection refused`，端口 41125 关闭；ICMP 可通，端口扫描 30000-50000 无监听），
未能拿到候选段明细与最终验证结果。

结论：代码路径已按设计替换，单元测试与 720p/1080p 离线参考均得到 6；真机这次的 4 次
说明仍有 2 个正向段未在 App 的 `motionSamples` 上形成合格候选（或该视频后半段跟踪到的
不是同一块杠铃片）。在设备恢复后需要：

1. 安装最终 APK；
2. 重跑视频并抓 `VBTSEG`/`VBTDebug`；
3. 重点看 16 s 后两段：run 是否存在、bottom/concentric/ROM 哪一个门限拒绝；
4. 若 `vy` 在第 5/6 次没有正向段，问题在跟踪层而不是本检测器。

## 未覆盖 / 风险

- 真机最终 6 次未确认；设备离线。
- 底部回看固定 0.5 s：触地即拉的连续硬拉若下放段 >0.5 s，起点可能落在下放中段，
  ROM 会被低估（当前真机数据 ROM 余量足够，未触发）。
- 中途速度平台 >0.18 s 且 y 下降 >`0.05 m` 时仍会拆段；如需更激进的合并需重新评估
  四分之一拉误检风险。
- `INCOMPLETE_REP` 在硬拉里也会包含大量小幅速度噪声段（数量会偏大），目前只作为提示，
  不影响计数；后续可把“噪声段”和“像 rep 但被门限拒绝”分开统计。
