# LVP / 1RM 文献参数与适用范围

**日期**：2026-11-11  
**用途**：确定 App 各动作的 1RM 估算参数前，先对齐文献口径与适用边界。本文只做调研，不改代码。

## 1. 方法与口径

App 现在用的是 **负荷-速度线性回归 + MVT 反解**：

```
v = a + b × load        （b < 0）
1RM = (MVT − a) / b
```

文献里同一件事有三种速度口径，混用会直接造成偏差：

| 口径 | 定义 | 备注 |
|---|---|---|
| MV（mean velocity） | 整个向心阶段的平均速度 | 最常用；深蹲/硬拉 L-V 拟合多用它 |
| MPV（mean propulsive velocity） | 只取加速度 ≥ 重力加速度的推进段 | 硬拉预测比 MV 更稳，偏差更小 |
| PV（peak velocity） | 向心峰值速度 | 个体差异大，1RM 预测不如 MV/MPV |

**App 的 MCV = 向心 ROM / 向心时长**，接近 MV，但阶段边界由视频速度阈值决定；在大重量下会把启动和锁定段算进去，通常**低于**同一次动作的 MPV。这是 App 与文献数值不能直接对齐的根本原因之一。

## 2. 各动作证据

| 动作 | 文献 1RM 速度 / MVT | 个体范围 | L-V 拟合 | 结论 |
|---|---|---|---|---|
| 深蹲（自由杠） | 通用 MVT 约 **0.30–0.32 m/s** | 个体 MVT **0.24–0.47** | 个体 R² 通常 ≥0.9 | 2024 系统综述/元分析：MVT 法**高估**深蹲 1RM，**不适合精确估算**，只能作参考 |
| 卧推 | 通用 MVT 约 **0.22 m/s**（训练女性）；Smith 机 optimal MVT 约 0.15–0.17 | 个体 **0.15–0.32** | 高 | 通用 MVT 有 3–4 kg 绝对误差；optimal MVT 可降到约 3 kg，但仍需个体校准 |
| 硬拉（传统） | 1RM 时 MV **0.24 ± 0.03 m/s** | 个体差异大 | 个体 R² 0.96–0.97，通用 0.91–0.93 | MPV 比 MV/PV 更准；2026 研究给出通用 optimal MVT **0.36 m/s**（统计校正值，不是真实 1RM 速度） |
| 相扑硬拉 | 1RM 时 ACV 约 **0.25 m/s**，与传统无显著差异 | — | 与传统的 L-V **不通用** | 40–89% 1RM 区间相扑明显更慢；必须单独建曲线，不能混用传统数据 |

关键结论：

1. **深蹲的 MVT 法误差最大**，文献直接说“不 viable”；App 现在给一个精确到 0.1 kg 的数字，置信度被高估了。
2. **硬拉的真实 1RM 速度约 0.24 m/s**，但 2026 研究建议用 0.36 作为“optimal MVT”来抵消系统偏差——两者不是一回事，不能混着解释。
3. **相扑和传统必须分开**：同一负荷下速度不同，混用会把曲线拉歪。
4. 文献的 L-V 拟合 R² 普遍 ≥0.9（个体），而 App 当前死拉数据 R² 只有 **0.34–0.48**，说明数据质量不在文献适用范围内。

## 3. 适用范围（文献前提）

只有同时满足以下条件，上述 MVT 参数才成立：

- 有训练经验的受试者（≥1 年力量训练）；
- 每组**最大意图**完成，不是热身/技术组/留余力组；
- 每组 **1–6 次**，组内速度损失 ≤20–25%；
- 负荷覆盖 **30–100% 1RM**，至少 **3 个不同负荷**（更稳的做法 5–8 个）；
- 动作变式一致（深蹲高杠/低杠、硬拉传统/相扑、卧推触胸/暂停分别建模）；
- 测速设备可靠（线性传感器/编码器），采样与滤波一致。

App 现状与这些前提的差距：

- 视频 MCV 在最大重量下会被启动/锁定段拉低；
- 历史数据里混有热身、技术组、高次数组；
- 有跟踪失败点（例如 150 kg @ 1.66 m/s，正常死拉不可能）；
- 没有 R² 门槛，也没有“1RM 不能低于已举起重量”的物理约束；
- 相扑/传统已经分开，但相扑只有 1 个负荷点时仍会显示“不可用”（这是对的）。

## 4. 建议的 App 参数（草案，待确认）

| 参数 | 建议值 | 依据 |
|---|---|---|
| 深蹲 MVT 默认 | 0.30 m/s，并标“参考值，误差可达 ±5–10%” | 通用值；文献明确高估风险 |
| 卧推 MVT 默认 | 0.17 m/s（可选 0.22） | Smith optimal 0.15–0.17；通用 0.22 |
| 硬拉 MVT 默认 | 0.24 m/s（真实 1RM 速度），不用 0.36 | 0.36 是统计校正值，不能当真实速度 |
| 相扑 | 独立 profile，禁止与传统合并 | Kasovic 2019 |
| 最少负荷点 | 3 个不同负荷，跨度 ≥20% 1RM | 文献 3–8 个 |
| R² 门槛 | <0.90 不显示 1RM，只显示曲线/低置信度 | 文献个体 R² ≥0.9 |
| 物理下限 | 估算 1RM < 该变式已举起的最大负荷 → 不可用 | 物理约束 |
| 离群点 | MCV > 1.5 m/s 的死拉组排除；速度损失 >25% 的组排除 | 跟踪失败/疲劳组 |
| 误差表达 | 显示 ±5–10%（或区间），不给“精确到 0.1 kg”的假象 | 文献 LoA 约 ±13% 1RM |

## 5. 对当前 bug 的直接解释

195 kg 相扑单次：

- 相扑只有 1 个负荷点 → 1RM **不可用**（正确）；
- 你看到的 170–180 kg 来自**传统硬拉**的 profile，不是这条相扑；
- 传统硬拉的拟合 R² 0.34–0.48，且 185 kg 的实际 MCV 0.184 < 默认 MVT 0.25，所以拟合出的 1RM 必然低于 185——这是参数和数据的双重问题，不是相扑动作的问题。

## 6. 参考

1. LeMense et al. (2024). Validity of using the load-velocity relationship to estimate 1RM in the back squat: systematic review and meta-analysis. J Strength Cond Res 38(3):612–619.
2. García-Ramos (2023). Optimal minimum velocity threshold to estimate 1RM: Smith machine bench press. IJSPP 18(4):393.
3. Yang et al. (2026). A sex-invariant optimal minimal velocity threshold accurately estimates deadlift 1RM. BMC Sports Sci Med Rehabil.
4. Kasovic, Martin, Fahs (2019). Kinematic differences between front/back squat and conventional/sumo deadlift. J Strength Cond Res 33(12):3213–3219.
5. Morán-Navarro et al. (2021). Load-velocity relationship of the deadlift exercise. Eur J Sport Sci 21(5):678–684.
6. J Sports Sci Med (2020). Analysis of the load-velocity relationship in deadlift exercise. 19(3):452–459.
7. Ruf et al. (2018). Validity and reliability of the load-velocity relationship to predict 1RM in deadlift.
8. Weakley et al. (2021). Velocity-based training: from theory to application.
