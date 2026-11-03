# VBTdroid UI 重设计规格（Compose）

**版本**：0.1 草案  
**日期**：2026-11-03  
**范围**：视觉与结构重设计。不改业务逻辑、状态流、数据模型、JSON/CSV 字段、导出行为。  
**参考**：`example/UI示例.png`（仅参考信息层级与叠加布局；不复制品牌、Logo、Slogan、水印）。  
**相关文档**：`docs/spec.md`、`docs/glossary.md`。

---

## 0. 现状问题（评审结论）

| # | 位置 | 问题 | 对应改法 |
|---|---|---|---|
| 1 | 全局 | 全部为默认 Material 控件竖排堆叠，无层级、无品牌色、无留白节奏 | 暗色主题 + 统一 token + 分区卡片（§1） |
| 2 | Import | 标题、按钮、文本全部 `Column` 居中堆叠；无首帧主视觉；ROI 与首帧分离 | 首帧即 Hero，ROI 直接叠在首帧上（§3） |
| 3 | Import | 动作/变式用 `FilterChip`，语义是单选却像多选标签 | `SegmentedButton` 单选（状态不变） |
| 4 | Import | 开关标签不可点，`Switch` 与文字分离，点击区域小 | 整行可点的 `ListItem` 式开关行 |
| 5 | Import | 无单位后缀；“开始分析”禁用时用户不知道缺哪一步 | 输入框后缀单位；CTA 上方一行“缺什么”提示 |
| 6 | Report | 每次 rep 一个长卡片，内含 6–8 个文本行；纵向 30+ 个控件，难以扫读 | 指标 Hero + 指标网格 + 单次详情区（rep 切换）（§4） |
| 7 | Report | 摘要用 `SummaryCard` 逐行列表，最重要的 MCV 与其它指标同等权重 | MCV 为 Hero（最大字号），其余为 2 列小格 |
| 8 | Report | 导出 3 个按钮 + 叠加状态文字散落在顶部 | 底部操作栏 + 导出 ModalBottomSheet 集中状态 |
| 9 | Report | 质量警告与保存状态以纯文本显示，错误色与成功色不可区分于普通文本 | 状态条（Status chip / Message banner） |
| 10 | History | 历史数值直接 `toString()`（如 `0.6123456`），不统一格式 | 统一格式化（MCV 2 位、负荷去尾零） |
| 11 | History | 无空状态设计；详情与列表同页切换，无层级 | 空状态 + 列表卡 + 详情页头 + 返回栏（§5） |
| 12 | `RoiSelector` | 硬编码 `Color.Green` / `Color.White` / 旧绿色 | 替换为主题 token（§8） |
| 13 | 全局 | 无 `ui/theme`，颜色/字号散落 | 新建 `ui/theme`（§8） |

---

## 1. 设计方向

### 1.1 定位

“训练现场的数据面板”：深色、高对比、数字优先。像运动手表和训练 App 的数据页，而不是后台管理面板。

**避免**：卡片墙式“仪表盘”（每个卡片同等大小、同等权重）；白底 Material 默认页；多色彩饱和堆叠；装饰性插画。

### 1.2 颜色 Token（暗色主题为唯一主题，v1 不做浅色）

| Token | Hex | 用途 |
|---|---|---|
| `bg` | `#0A0E11` | 页面底色 |
| `surface0` | `#10161B` | 次级容器（分组底） |
| `surface1` | `#161E25` | 卡片（不透明） |
| `surface2` | `#1D2730` | 选中/输入框底/芯片底 |
| `outline` | `#2B3742` | 1dp 分割线、描边（可用 60% alpha） |
| `onSurface` | `#E9EEF1` | 主文字 |
| `onSurfaceVariant` | `#93A1AC` | 次文字、标签 |
| `primary` | `#3CE0B4` | 薄荷青：主操作、MCV、向心段、进度 |
| `onPrimary` | `#003827` | 主色按钮上的文字 |
| `primaryContainer` | `#0F3A30` | 选中段的底色 |
| `speed` | `#FFC247` | 琥珀：速度读数（叠加视频速度胶囊、HUD 速度） |
| `comparison` | `#FF7A3D` | 橙：对比（最佳次、上一组）轨迹与曲线 |
| `skeleton` | `#F5F8FA` | 白：姿态骨架、关节点（叠加视频） |
| `phaseEccentric` | `#6AA7FF` | 离心相位 |
| `phasePause` | `#8C97A3` | 停顿相位 |
| `phaseConcentric` | `#3CE0B4` | 向心相位（同 primary） |
| `phaseLockout` | `#A6F0DC` | 锁定相位 |
| `error` | `#FF6B6B` | 错误文字/图标 |
| `errorContainer` | `#3A1717` | 错误条底色 |
| `warning` | `#FFC247` | 质量警告文字与图标（仅文字/图标，不做填充；避免与速度胶囊混淆） |
| `confHigh` | `#3CE0B4` | 置信度 ≥ 0.80 |
| `confMid` | `#FFC247` | 置信度 0.50–0.79 |
| `confLow` | `#FF6B6B` | 置信度 < 0.50 |

**色彩规则**
- 薄荷只表示“杠铃/主结果”。橙只表示“对比”。琥珀只表示“速度读数或需注意”。白只用于姿态骨架。
- 同一屏内强调色（薄荷 + 橙 + 琥珀）不超过两种同时大面积出现。
- 文字对比：正文 ≥ 4.5:1，大字 ≥ 3:1（`onSurface` 在 `surface1` 上满足）。

### 1.3 排版

| 角色 | 字体 | 说明 |
|---|---|---|
| 数字（Hero、指标值、角度） | **Barlow Condensed**（SemiBold/Medium，OFL，随 `res/font` 打包，只打 2 个字重） | 运动感、竖向紧凑；仅用于数字与单位缩写 |
| 中文与正文 | 系统默认（Android 自带 Noto Sans CJK 回退） | 不额外打包 CJK 字体，控制 APK 体积 |
| 数字对齐 | 数字文本加 `fontFeatureSettings = "tnum"` | 表格/网格列对齐 |

**字阶（sp / line height）**

| Token | Size / LH | 字重 | 用途 |
|---|---|---|---|
| `displayMetric` | 64 / 64 | Barlow SemiBold | 报告 MCV Hero 数值 |
| `displayMetricSm` | 40 / 44 | Barlow SemiBold | 历史卡 MCV、LVP 估计 1RM |
| `headlineMetric` | 28 / 32 | Barlow Medium | 指标网格值、关键帧角度 |
| `titleLarge` | 22 / 28 | 500 | 页面标题 |
| `titleMedium` | 16 / 22 | 500 | 卡片标题 |
| `bodyLarge` | 16 / 24 | 400 | 正文主段 |
| `bodyMedium` | 14 / 20 | 400 | 次要正文、列表 |
| `labelLarge` | 14 / 18 | 500 | 按钮、分段控件 |
| `labelSmall` | 11 / 14 | 500 | 芯片、叠加视频标签、单位 |

规则：卡片标题统一 `titleMedium` + `onSurfaceVariant` 色前缀小标签（`labelSmall`，全大写英文可选，中文不转换）。

### 1.4 间距

- 基准 4dp：**4 / 8 / 12 / 16 / 24**，其它值不用。
- 页面左右边距：手机竖屏 16dp；横屏或宽度 ≥ 600dp 时 24dp。
- 卡片内边距：16dp（Hero/主卡）；12dp（密集列表行）。
- 区块间距：16dp；卡片内组件间距：8dp；芯片间距：8dp。
- 最小触控区域：48dp × 48dp。

### 1.5 圆角

| Token | 值 | 用途 |
|---|---|---|
| `shapeXs` | 6dp | 图表内部小块、进度条 |
| `shapeS` | 8dp | 输入框、普通芯片、分段按钮 |
| `shapeM` | 12dp | 普通卡片、数据格 |
| `shapeL` | 20dp | Hero 卡、视频预览容器、底部弹层顶部 |
| `shapePill` | 999dp | 速度胶囊、状态芯片 |

### 1.6 层级与阴影

- 不用 Material 阴影（`elevation = 0`）。层级靠 `surface0 → surface1 → surface2` 色阶 + 1dp `outline`（60% alpha）描边表达。
- 底部操作栏允许 `tonalElevation = 3dp`（M3 默认 surface tint），这是唯一例外。

### 1.7 卡片透明度

| 场景 | 底色 | 说明 |
|---|---|---|
| App 页面卡片 | `surface1` 100% | 不透明，保证可读性 |
| 视频上浮层（HUD、Hero 胶囊） | `#0A0E11` 72% | 不做模糊（minSdk 26 无 RenderEffect 模糊） |
| 视频上速度胶囊 | `#14110A` 78% | 文字琥珀 |
| 底部弹层 | `surface1` 100% | 顶部圆角 `shapeL` |

### 1.8 动效

- **一次编排**：报告页进入时 Hero 淡入 + 上移 12dp（320ms，`FastOutSlowInEasing`），其后区块按 40ms 间隔交错出现，最多 5 个。
- **图表**：切换 rep 时曲线从左到右绘制 400ms；其它时候不做循环动画。
- **分析进度**：`LinearProgressIndicator` 确定型，不做脉冲。
- **骨架占位**：视频首帧解码期间，预览区用 `surface2` 色块 alpha 0.6↔1.0 呼吸 900ms（不使用 Shimmer 库）。
- 系统“移除动画”或动画缩放为 0 时，全部跳过动效直接显示。

### 1.9 图标

- 只用 `material-icons-core`（已在 M3 依赖内），不引入 `material-icons-extended`（体积大）。
- 历史入口等无 core 图标的操作，用文字按钮，不临时找图标。

---

## 2. 信息架构

```
导入 IMPORT ──(分析完成)──▶ 报告 REPORT
  ▲                          │
  └──(返回/新分析)───────────┘
历史 HISTORY（次级，从导入页或报告页进入；返回到来源页）
```

- 三个页面，沿用现有 `AppScreen { IMPORT, REPORT, HISTORY }`，**不新增路由**，不引入底部导航栏。
- “分析”不是独立页面：分析进度作为导入页底部的 `ProgressPanel` 原地显示（保持 `analyzing / analysisProgress / onCancel` 现有状态流）。
- 历史是次级入口：在导入页与报告页的顶部栏右侧以文字按钮“历史记录”进入。

### 2.1 每屏主操作

| 屏 | 主操作（唯一强调按钮） | 次操作 | 不应出现 |
|---|---|---|---|
| 导入 | “开始分析”（需 视频 + ROI 确认 + 有效设置） | 选择/更换视频、重置选区、确认选区 | 多个 FilledButton 并列 |
| 分析中 | “取消分析” | 查看进度 | 任何改参数的入口（已由 `enabled=!analyzing` 禁用） |
| 报告 | 无强调 CTA（数据本身是主角）；底部 “导出” 为唯一 FilledTonal | “历史记录”、“返回” | 多个 Filled 按钮 |
| 历史列表 | 整卡点击进入详情 | 返回 | 删除（v1 不做） |
| 历史详情 | “返回历史列表” | — | — |

---

## 3. 导入页（ImportScreen）

### 3.1 状态（不变）

保留现有全部 `remember` 状态与副作用：`selectedUri / fileName / videoMeta / preview / roi / roiConfirmed / autoRoiUnavailable / loading / videoError / lift / variant / loadText / plateDiameterText / cameraAngleText / cameraLevel / analyzePose`。本规格只改布局与组件，不改状态语义。

### 3.2 竖屏布局（411 × 914 dp，即 1080×2400 @2.625x）

自上而下：

| 序 | 区块 | 高度 / 尺寸 | 组件 |
|---|---|---|---|
| 1 | 顶部栏 | 64dp | M3 `TopAppBar`：标题 `import_title`（`titleLarge`）；右侧 `TextButton` `history_title` |
| 2 | **首帧 Hero** | 宽 379dp，高 ≈ 213dp（16:9），按视频原始比例夹在 160–300dp | `VideoHeroCard`：圆角 `shapeL`，内含 `RoiSelector`；左上角状态芯片；未选视频时为空态投放区（见 3.4） |
| 3 | 视频元数据芯片行 | 28dp，可换行 | `StatusChip` × 4：文件名（省略号）、时长、分辨率、有效帧率（用 `import_duration / import_resolution / import_effective_fps`） |
| 4 | ROI 控制卡 | 自适应 | `RoiCard`：标题 `import_roi_title`；提示 `import_roi_confirm_hint` 或 `import_roi_manual_required`；操作提示 `import_roi_edit_hint`；按钮行 [重置选区 OutlinedButton] [确认 FilledTonalButton → 已确认时显示 `import_roi_confirmed` 且禁用] |
| 5 | 动作 | 标签 + 44dp 分段 | `SectionLabel(import_lift)` + `SegmentedPicker` 三段（深蹲/卧推/硬拉） |
| 6 | 动作变式 | 标签 + 44dp 分段 | `SectionLabel(import_variant)` + `SegmentedPicker` 两段（由 `variantsFor(lift)` 决定） |
| 7 | 负荷与器械 | 两行输入 | `SectionLabel(import_analysis_settings)` 后：行1 [负荷 weight1 | 片径 weight1]；行2 [机位角度 weight1 | 预设芯片组] |
| 8 | 开关卡 | 2 行 × 56dp | `SectionCard` 内两条 `SwitchRow`：`import_camera_level`、`import_analyze_pose`（整行可点） |
| 9 | 错误条 | 自适应 | `MessageBanner(error)`：`import_error_reason` 或 `import_analysis_failed_reason` |
| 10 | 占位留白 | 96dp | 为底部操作栏留白 |
| — | **底部操作栏**（固定） | 80dp + 导航栏 inset | 见 3.5 |

**间距**：区块间 16dp；`SectionLabel` 与其内容间 8dp。

### 3.3 组件规格

**SegmentedPicker**
- 封装 M3 `SingleChoiceSegmentedButtonRow`；每段高 44dp；选中段底色 `primaryContainer` + 文字 `primary`；未选中透明 + `onSurfaceVariant`。
- 禁用（分析中）整组 `enabled=false`，透明度 0.5。
- 行为：切换动作时仍调用 `variant = defaultVariant(option)`；切换变式只改 `variant`。

**SwitchRow**
- 整行 `Modifier.clickable` 切换状态；左侧标题 `bodyLarge` + 可选说明 `bodySmall`；右侧 M3 `Switch`。
- `analyzePose` 说明文字：“关闭后不输出关节角与关键帧”。（新增字符串，见 §10。）

**输入框**
- `OutlinedTextField`，`shapeS`，后缀：`kg`、`m`、`°`。
- 片径默认值沿用 `import_default_plate_diameter`（0.45）；机位默认 90。
- 机位预设芯片（`AssistChip`）：`侧拍 90°`（设为 "90"）、`前/后 45°`（设为 "45"）。仅改输入文本，不新增状态。
- 无效值时 `isError = true`，并在字段下方显示原因（沿用现有 `import_invalid_settings` 作为整体提示）。

**StatusChip**
- 高 28dp，`shapePill`，底色 `surface2`，文字 `labelSmall`。
- 状态变体：`primary`（已确认/高置信）、`warning`（需注意）、`error`（失败）。

### 3.4 首帧 Hero 与空态

- **未选视频（空态）**：`VideoHeroCard` 变为 240dp 高投放区；`outline` 1.5dp 虚线描边（`PathEffect.dashPathEffect`），中央图标 + 标题“选一段训练视频”（新）+ 提示“支持深蹲、卧推、硬拉；最长 10 分钟”（新）+ `FilledButton` `import_choose_video`。
- **解码中（loading）**：预览区 `surface2` 色块呼吸（§1.8），中心 `CircularProgressIndicator` 24dp，文字“正在读取首帧”（新）。
- **首帧就绪**：`RoiSelector` 显示；左上角状态芯片：
  - `autoRoiUnavailable == false` 且 `roiConfirmed == false`：“已自动建议”（`primary` 描边）
  - `autoRoiUnavailable == true`：“请手动框选”（`warning`）
  - `roiConfirmed == true`：“已确认”（`primary` 填充）
- 首帧 Hero 高度 ≥ 213dp；竖屏下它是页面第一视觉焦点。

### 3.5 底部操作栏与 CTA

- 固定在底部，背景 `surface1`，顶部 1dp `outline`，`tonalElevation 3dp`；内边距 16dp，底部加 `navigationBarsPadding()`；键盘弹起时 `imePadding()`。
- 布局：上方一行提示 `bodySmall`（`onSurfaceVariant`），下方全宽 52dp `Button` `import_start_analysis`。
- **提示行优先级**（只显示第一条不满足的条件，让用户知道下一步）：
  1. 未选视频 → “先选择一段视频”（新）
  2. 未确认 ROI → “请确认杠铃片区域”（新：`import_roi_confirm_hint` 语义；新增 `import_cta_need_roi`）
  3. 设置无效 → `import_invalid_settings`
  4. 全部满足 → 空（按钮 enabled）
- CTA 启用条件沿用现有：`!analyzing && validSettings && validRoi && roiConfirmed`。

**分析中（替换底部操作栏内容）**
- `ProgressPanel`：标题 `import_analysis_progress`（显示百分比）、`LinearProgressIndicator`（4dp，`primary` 轨道，`surface2` 底）、副文 `analysis_notification_text`（“分析在后台继续”）、`OutlinedButton` `import_cancel_analysis`（文字 `error`）。
- 取消后底部恢复 CTA，并显示 Snackbar“分析已取消”（新：`analysis_cancelled`）。

### 3.6 横屏布局（宽 > 高，或 宽 ≥ 600dp）

- 左列 45%：`VideoHeroCard` + 元数据芯片 + ROI 控制卡，固定不滚动（`weight(0.45f)`）。
- 右列 55%：动作、变式、输入、开关、错误条滚动；底部操作栏固定在右列底部。
- 左右边距 24dp；列间距 24dp。

### 3.7 视觉细节

- 页面背景 `bg`，卡片 `surface1`，芯片 `surface2`。
- 标题与分组标签之间的过渡只用字重与颜色，不用分割线。
- 不使用渐变背景（保持干净，避免与 Hero 首帧抢视觉）。

---

## 4. 报告页（ReportScreen）

### 4.1 状态（不变）

保留 `current / manualEditRejected / editingPhases / persistPending / persistFailed / exportSucceeded / overlayExporting / overlayProgress / overlaySucceeded / overlayError` 及 `updateSlice`、`onPersist`、各 launcher。新增仅为 UI 状态：`selectedRepIndex`（默认 1，`rememberSaveable`）、`compareWithBest`（默认 false）、`exportSheetOpen`（默认 false）、`qualityExpanded`（默认 false）。

### 4.2 竖屏布局（自上而下）

| 序 | 区块 | 高度 | 组件 | 内容来源（不改） |
|---|---|---|---|---|
| 1 | 顶部栏 | 64dp | `TopAppBar`：导航图标返回（`report_back`，ArrowBack）+ 标题 `report_title`；副标题行：`深蹲 / 高杠 · 100 kg`（`history_set_identity` + `report_external_load_assumption` 的负荷）；右侧文字按钮 `history_title` | `result.lift`、`variant`、`loadKg` |
| 2 | **预览区** | 宽 379dp，高 220dp | `ReportPreview`：静态首帧 + 杠铃轨迹叠加（薄荷）；左下角芯片 `杠铃轨迹示意`；失败时为深色渐变 + 轨迹 | 见 4.3 |
| 3 | **MCV Hero** | 自适应，≈ 152dp | `MetricHero`：标签 `metric_mcv` / `report_average_of`（`bodyMedium`）；数值 `displayMetric`（薄荷）+ 单位 `m/s`（`titleMedium`）；下方一行三列小指标：`最佳 MCV`、`速度损失 VL`、`有效重复`；底部说明 `report_external_load_assumption`（`labelSmall`，`onSurfaceVariant`） | `summary.mcvAvgMps`、`bestMcvMps`、`velocityLossPct`、`repCount` |
| 4 | 保存状态行 | 24dp | `StatusChip(primary)`：`report_saved_automatically`（✓）；保存失败时替换为 `MessageBanner(error)`：`report_save_failed` | `persistFailed` |
| 5 | 指标网格 | 2 列 × 2 行 | `MetricTile`：有效重复（`report_total_reps`）、训练容量（`metric_volume_load`）、总功（`metric_work`）、最佳 MCV（`report_best_of`） | `summary.*` |
| 6 | 逐次速度 | 高 128dp | `SectionCard`：标题 `report_velocity_chart` 改为“逐次平均速度”（新：`report_per_rep_mcv`）；柱状图，选中柱白色描边 1.5dp，最佳柱亮薄荷 | `reps[i].metrics[mcv]` |
| 7 | 第 N 次详情卡 | 自适应 | `RepDetailCard`（见 4.4）；顶部 `RepSelector` 横向芯片：`第 1 次`…；选中芯片 `primaryContainer` | `reps`, `repSlices`, `motionSamples` |
| 8 | LVP 卡 | 自适应 | `LvpCard`（见 4.5） | `current.lvp` |
| 9 | 姿态未启用卡 | 自适应（仅 `!analyzePose`） | `SectionCard`：`report_joint_angles_disabled_title` + `hint` | `analyzePose` |
| 10 | 质量卡 | 折叠 56dp，展开自适应 | `QualityCard`（见 4.6） | `quality`, `fps`, `filterConfig` |
| 11 | 留白 | 96dp | — | — |
| — | **底部操作栏**（固定） | 72dp + 导航栏 inset | 左 `OutlinedButton` `history_title`；右 `FilledTonalButton` `report_export_*` 导出入口（打开 4.7 弹层） | — |

**说明**
- 报告不再为每个 rep 生成一张长卡片；所有 rep 共享一个详情区，用 `RepSelector` 与“逐次速度”柱图切换。这把 N 张卡压缩成 1 个详情区，减少纵向长度。
- 现有 `RepTable`（逐次指标表格）保留，放在第 7 项之后作为 `SectionCard`“逐次指标”，表头 `labelSmall`，数值 `tnum`，支持横向滚动（保持现有列宽）。

### 4.3 预览区（ReportPreview）

- **默认方案（v1）**：在 IO 线程调用现有 `VideoDecoder.readFirstFrame(uri)` 取首帧，缓存到 `remember(result)`；绘制时先画首帧（`ContentScale.Crop`，压暗 40% 黑色遮罩），再在 Canvas 上画杠铃轨迹（薄荷 3dp，当前 rep），可选橙色对比轨迹（最佳 rep，2dp，70% alpha）。
- **失败回退**：首帧不可用（URI 权限失效、解码失败）→ 显示 `surface2` 到 `surface0` 竖向渐变 + 轨迹 + 文案 `report_preview_placeholder`（新：“首帧不可用，显示轨迹示意”）。
- **可选（v1 不做，列入后续）**：叠加视频的预览播放，需要复用 `OverlayRenderer` 逐帧绘制，本规格不纳入。
- 预览区点击无动作（不做全屏），避免与分段/滚动手势冲突。
- 边界：预览图 `Bitmap` 必须在 `DisposableEffect` 中回收，沿用导入页的回收习惯。

### 4.4 第 N 次详情卡（RepDetailCard）

自上而下：

1. **标题行**：`report_rep_number`（`titleMedium`）；右侧 `StatusChip`：向心 ROM（`rom_concentric_m`）与时长（`phase_duration`，见 `history_phase` 字段）。
2. **速度曲线**（高 140dp）：`VelocityChart` 改造——薄荷 3dp 曲线；零线 `outline` 虚线；相位背景带 12% alpha（离心蓝、停顿灰、向心薄荷、锁定浅薄荷）；平均线（MCV）薄荷 40% 虚线；右下角 `labelSmall` 显示峰值。
3. **杠铃轨迹**（宽 379dp，高 220dp 或横屏并排）：`BarPathChart` 改造——薄荷 3dp 轨迹；起点空心圆、终点实心圆；`compareWithBest` 为真时叠加最佳次橙色 2dp 70% alpha。
4. **对比开关**：`SegmentedPicker` 两段 `只看本次` / `对比最佳次`（新：`report_compare_none` / `report_compare_best`）。
5. **相位时间线**（只读为默认）：`PhaseTimeline`；下方相位标签（离心/停顿/向心/锁定），手动相位加 `StatusChip` `手动`（`report_manual_phases`）。
   - 右上角 `TextButton`：`report_phase_edit` / `report_phase_done`（沿用，状态 `editingPhases`）。
   - 编辑模式：顶部提示 `report_phase_edit_hint`；边界以 `primary` 手柄显示；被拒绝时 `MessageBanner(error)` `report_manual_invalid`。
   - 编辑与只读之间不切换页面，原地切换。
6. **关键帧关节角**（仅 `analyzePose == true`）：标题 `report_key_frame_angles`；2 × 2 网格，每格：关键帧名（`report_key_frame_start/bottom/sticking/lockout`）+ 主角度（`headlineMetric`，例如 128°）+ 置信度芯片（`confHigh/confMid/confLow` 颜色，文字“置信 0.87”）；格内列出其余关节（髋/膝/踝/躯干/肩/肘/腕）：名称 `bodySmall`、角度 `tnum`、置信度小字。
   - 无可用角度：`report_joint_angles_unavailable`（`onSurfaceVariant`）。
7. **相位与关节角之外**：不再显示重复的“关节角”列表，统一放在第 6 项。

### 4.5 LVP 卡（LvpCard）

- 标题 `report_lvp_title`。
- **可用**：大号 `displayMetricSm` 估计 1RM（`report_estimated_1rm`），旁 `StatusChip` “估算”（必须带“估算”，不得写“实测”）；下方 `report_lvp_fit`（斜率/截距/R²，`labelSmall`）；`report_v1rm_source` 小字说明来源（文献值/用户）。
- **不可用**：`report_lvp_unavailable` + 进度 `pointCount / 3`（`LinearProgressIndicator` 3 段式，文字 `report_lvp_points`）。
- 无数据时整卡不显示（沿用 `current.lvp?.let`）。

### 4.6 质量卡（QualityCard）

- 折叠态（56dp）：标题 `report_quality` + 摘要芯片：无警告 → `report_no_warnings`（`primary`）；有警告 → `N 条需注意`（`warning`）。
- 展开态：
  - 4 个 `MeterRow`（跟踪置信度、遮挡、分割置信度、标定置信度）：横向进度条（0–1 或 0–100%），数值 `tnum`。来源：`report_quality_values` 的同四个字段。
  - `report_fps` 与 `report_filter_config`（`labelSmall`）。
  - 警告列表：每条一行，前置 `warning` 色圆点，文字沿用 `qualityWarningText`。
- 警告文案中的技术名称（如“POSE_LOW_CONFIDENCE”）不得出现在 UI，只显示映射后的中文。

### 4.7 导出弹层（ExportSheet）

- `ModalBottomSheet`（顶部圆角 `shapeL`，背景 `surface1`），标题 `report_export_title`（新：“导出”），说明 `report_export_hint`（新：“文件会保存到你选择的位置”）。
- 三行 `ListItem`（每行高 56dp，整行可点）：
  1. 导出 JSON（`report_export_json`，文件 `vbtdroid-set.json`）
  2. 导出 CSV（`report_export_csv`，文件 `vbtdroid-set.csv`）
  3. 导出叠加视频（`report_export_overlay_video`，文件 `vbtdroid-overlay.mp4`）
- 叠加视频导出中：该行替换为 `LinearProgressIndicator` + `report_export_overlay_progress`；导出期间其它行禁用（沿用 `enabled = !overlayExporting`）。
- 结果提示：
  - JSON/CSV 成功/失败 → `report_export_succeeded` / `report_export_failed`（`primary` / `error` 色条）。
  - 叠加视频成功 → `report_export_overlay_succeeded`；无轨迹 → `report_export_overlay_no_data`；其它失败 → `report_export_overlay_failed`。
- 结果同时以 Snackbar 提示（成功后 2.5 秒自动消失）。弹层关闭后状态保留在页面，不丢失。

### 4.8 历史与返回

- 顶部“返回”与底部“历史记录”行为同现有：`onBack` 与 `onShowHistory`。
- 报告页不做离开确认（沿用现有行为）。

### 4.9 横屏

- 左列 45%（固定）：`ReportPreview`（高 ≈ 视口高 40%）+ `MetricHero`。
- 右列 55%（滚动）：指标网格、逐次速度、详情卡、LVP、质量卡。
- 底部操作栏在右列底部。

---

## 5. 历史页（HistoryScreen）

### 5.1 列表（`detail == null`）

- 顶部栏：返回（`report_back`）+ 标题 `history_title`。
- 列表间距 12dp，左右 16dp（横屏 24dp）。
- **SetCard**（`surface1`，`shapeM`，16dp 内边距，整卡可点，等效 `onSelect(setId)`）：
  - 第 1 行：左 `动作 / 变式`（`history_set_identity`，`titleMedium`）；右 日期时间（`bodySmall`，`onSurfaceVariant`，格式 `MM-dd HH:mm`，由 `createdAtEpochMs` 本地格式化）。
  - 第 2 行：左 **MCV** 大号 `displayMetricSm` 薄荷（`history_set_mcv` 的数值，保留 2 位小数）+ 单位 `m/s`；右 两行 `bodySmall`：负荷（`history_set_load`）、速度损失（若可得，见 5.3）。
  - 第 3 行：右下 `TextButton` `history_open_detail` + 箭头（`KeyboardArrowRight`）。
- 空：`EmptyState`：文字 `history_empty` + 说明（新：“完成一次分析后会出现在这里”）+ `OutlinedButton`“返回导入”（新：`history_empty_cta`，调用 `onBack`）。

### 5.2 详情（`detail != null`）

- 顶部栏：返回按钮与标题 `history_detail_title`；返回按钮行为同 `onCloseDetail`（文字 `history_close_detail` 放在顶部右侧 `TextButton`，保留语义）。
- **头部卡**（`surface1`，`shapeL`，16dp）：
  - `history_set_identity` 标题；`history_camera_geometry` 芯片；`history_video_uri` 单行省略号 `labelSmall`；LVP 行（`report_lvp_points` + 估计 1RM，无则 `report_lvp_unavailable`）。
- **逐次行**（列表）：每行 `surface1` 12dp 内边距：
  - 第 1 行：`report_rep_number`（`titleMedium`）+ `MCV` 数值（`headlineMetric`，薄荷）。
  - 第 2 行：迷你横向条（宽度 = 本组 MCV 最大值的比例，薄荷），旁 `ROM` 数值（`history_rep_rom`）。
  - 第 3 行：相位芯片：`history_phase`（类型 · 起止时间 · 手动标记）。
  - 警告：`report_warning_item` 以 `warning` 色显示。
- 解析失败：`history_export_unavailable`（整块 `MessageBanner(warning)`）。

### 5.3 数据来源与格式化

- 列表卡的“速度损失”不在 `SetEntity` 字段中。二选一，由实现者选择，不改数据库结构：
  - 方案 A（推荐）：`AnalysisRepository.history()` 映射时，从已有 `metricsJson` 读取 `set_velocity_loss_pct`（或 `velocity_loss_pct`）投影到列表 DTO；若不存在则不显示。
  - 方案 B：列表项不显示速度损失，只显示 MCV 与负荷。
- 数值统一格式：MCV `%.2f`、负荷去尾零（`100` 而非 `100.0`）、缺失值显示 `report_value_missing`（`—`）。

---

## 6. 视频叠加（OverlayRenderer 视觉规范）

> 叠加视频导出为 MP4（`VideoOverlayExporter`），本节约束其视觉。应用内任何预览必须与之一致。

### 6.1 视觉尺寸（以 1080 短边为基准；`scale = shortEdge / 1080`，单位 px，实现时乘以 `scale`）

| 元素 | 数值 | 颜色 |
|---|---|---|
| 姿态骨架线 | 2.0 × scale（最小 1.2px） | `skeleton` 85% alpha |
| 骨架关节点（非角度关节） | 半径 3.0 × scale | `skeleton` 100% |
| 角度关节点 | 外环半径 8 × scale，内点半径 3 × scale | 外环 `skeleton` 1.5 × scale 描边，内点 `skeleton` |
| 杠铃轨迹（当前 rep） | 宽 3.5 × scale（最小 1.5px），尾迹渐隐 | `primary` 100% → 头部，尾部 0%，尾迹时长 1.2s |
| 对比轨迹（最佳 rep） | 宽 2.0 × scale | `comparison` 70% alpha（仅预览，导出默认关闭，除非沿用现有逻辑） |
| 角度标签胶囊 | 文字 `labelSmall` ≈ 11 × scale；胶囊高 22 × scale；圆角 pill | 底 `#0A0E11` 72%；文字 `skeleton` |
| 速度胶囊 | 文字 14 × scale，粗体；胶囊高 32 × scale；内边距水平 12 × scale | 底 `#14110A` 78%；文字 `speed` |
| HUD 卡 | 宽 ≤ 30% 短边（竖屏）；圆角 12 × scale；内边距 12 × scale | 底 `#0A0E11` 72% |

### 6.2 骨架与关节

- 骨架只连接：肩-肘-腕、髋-膝-踝、肩-髋（躯干）、踝-趾。不画人脸与手指细节。
- 角度标签位于关节外侧 12 × scale，沿关节角平分线方向偏移；与杠铃片区域重叠时自动移到另一侧。
- 低置信关节（visibility < 0.5）不画，也不标角度（与 spec §5.7 一致：插值但不参与统计，不显示）。

### 6.3 杠铃轨迹

- 当前 rep：薄荷，宽 3.5 × scale，头部圆点半径 5 × scale（白色描边 1 × scale）。
- 尾迹渐隐（alpha 从 0 到 1），仅绘制最近 1.2 秒；离心/向心不用不同颜色，避免与相位色冲突。
- 对比轨迹（最佳 rep）：橙色，仅在预览和报告中使用。

### 6.4 速度胶囊

- 位置：右上角，距上边 `statusBarInset + 16 × scale`，距右边 16 × scale。
- 内容：`0.85 m/s`（数值 `tnum`，单位小字）。
- 速度为零或缺失时隐藏，不显示 `—`（避免视频中出现占位符）。

### 6.5 HUD 卡

- 位置：左上角，距上边 `statusBarInset + 16 × scale`（竖屏）。
- 内容（从上到下，最多 3 行）：
  1. 第 N 次 · 相位名（`labelSmall`）
  2. MCV 大号（`headlineMetric` 薄荷）+ `m/s`
  3. 可选：速度损失或峰值（`labelSmall`）
- 不做多卡片纵向堆叠（参考图的 5 张卡），v1 只保留一张 HUD，避免遮挡杠铃与人体。

### 6.6 安全区

| 方向 | 规则 |
|---|---|
| 竖屏 | 顶部留出状态栏 inset；HUD 与速度胶囊不进入视频顶部 12%；底部 15% 不放 UI（手势区与 UI 冲突） |
| 横屏 | 左侧 HUD 最宽 22% 短边；右侧速度胶囊贴边，留出导航栏 inset；杠铃片中心区域（ROI 中心 ±20%）不放任何 UI |
| 预览（应用内） | 使用 `WindowInsets.systemBars` 计算安全区；导出视频与预览使用同一套计算（`overlay/OverlayStyle.kt`） |

### 6.7 颜色 ARGB 常量

- 颜色常量集中在 `overlay/OverlayStyle.kt`（`Long` ARGB），供 `android.graphics.Color` 与 Compose `Color` 共用，避免一个颜色两处定义。

---

## 7. 状态设计

| 状态 | 出现位置 | 表现 | 文案 key |
|---|---|---|---|
| 首帧加载中 | 导入 Hero | 呼吸色块 + 小圆形进度 + 文字 | 新：`import_loading_frame` |
| 空（未选视频） | 导入 Hero | 虚线投放区 + 主按钮 | 新：`import_empty_title`、`import_empty_hint` |
| 视频读取失败 | 导入 | 错误条（`error`）+ 重新选择按钮 | `import_error_reason` |
| 视频超过上限 | 导入 | 错误条，说明 10 分钟上限 | 新：`import_error_too_long`（由 `VideoLimits` 异常映射） |
| 无自动 ROI | 导入 | 芯片 `warning`“请手动框选” + 提示 | `import_roi_manual_required` |
| ROI 已确认 | 导入 | 芯片 `primary`“已确认”，确认按钮禁用 | `import_roi_confirmed` |
| 设置无效 | 导入 | CTA 上方提示 | `import_invalid_settings` |
| 无姿态模型 / 姿态关闭 | 导入、报告 | 开关标签下注明“不输出关节角”；报告显示未启用卡 | `report_joint_angles_disabled_*`；新：`import_pose_unavailable` |
| 分析中 | 导入 | ProgressPanel，输入全部禁用 | `import_analysis_progress` |
| 分析已取消 | 导入 | Snackbar | 新：`analysis_cancelled` |
| 分析失败 | 导入 | 错误条（`error`） | `import_analysis_failed_reason` |
| 低置信 | 报告（关节角、质量） | 置信芯片 `confMid/confLow`；质量卡摘要 `warning` | `quality_pose_low_confidence` 等 |
| 相位校正被拒 | 报告 | 错误条 | `report_manual_invalid` |
| 保存失败 | 报告 | 错误条 | `report_save_failed` |
| 报告无 rep | 报告 | 空态文本 + 返回按钮 | `report_no_data` |
| 历史为空 | 历史 | 空态 + 返回导入 | `history_empty`、新：`history_empty_cta` |
| 历史详情解析失败 | 历史详情 | 警告条 | `history_export_unavailable` |
| 导出中 | 导出弹层 | 行内进度条，其它行禁用 | `report_export_overlay_progress` |
| 导出成功 | 弹层 + Snackbar | `primary` 文字 | `report_export_succeeded`、`report_export_overlay_succeeded` |
| 导出失败 | 弹层 + Snackbar | `error` 文字 | `report_export_failed`、`report_export_overlay_failed` |
| 无轨迹导出 | 弹层 | `error` 文字 | `report_export_overlay_no_data` |

**错误条（MessageBanner）**：`errorContainer` 底色 + `error` 文字；`warning` 变体用 `surface2` 底 + `warning` 图标；`success` 变体用 `primaryContainer` 底。不用红色大面积填充。

---

## 8. Compose 实现映射

### 8.1 主题（新建 `ui/theme`）

| 文件 | 内容 |
|---|---|
| `ui/theme/Color.kt` | §1.2 全部 token 作为 `val`；`VbtColors` 扩展色（speed、comparison、phase*、conf*、warning、skeleton） |
| `ui/theme/Type.kt` | Barlow Condensed `FontFamily`（`res/font/barlow_condensed_*.ttf`，2 个字重）；§1.3 字阶为 `Typography` |
| `ui/theme/Shape.kt` | `Shapes(extraSmall=6, small=8, medium=12, large=20)`，pill 用 `RoundedCornerShape(999.dp)` |
| `ui/theme/Dimens.kt` | `object VbtSpacing { val xs=4.dp; s=8; m=12; l=16; xl=24 }`、最小触控 48dp |
| `ui/theme/Theme.kt` | `VbtTheme { }`：`darkColorScheme(...)`、`CompositionLocalProvider(LocalVbtColors provides ...)`；只做暗色 |

- `MainActivity` 中 `MaterialTheme { AnalysisApp() }` 改为 `VbtTheme { AnalysisApp() }`。**路由与状态不动**。
- 启用 edge-to-edge（`enableEdgeToEdge()`），由 `Scaffold` 与 `windowInsets` 处理安全区。

### 8.2 通用组件（新建 `ui/components`）

| 组件 | 基于 | 用途 |
|---|---|---|
| `SectionCard` | `Surface`（`surface1`，`shapeM`，1dp outline 60%） | 所有分组卡片 |
| `SectionLabel` | `Text`（`titleMedium`/`labelSmall`） | 分组标题 |
| `MetricHero` | `SectionCard` + `Text` | 报告 MCV Hero、历史卡 MCV |
| `MetricTile` | `SectionCard` | 指标网格格子 |
| `StatusChip` | `Surface`（`shapePill`）+ `Text` | 状态与元数据芯片 |
| `SegmentedPicker` | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` | 动作、变式、对比开关 |
| `SwitchRow` | `Row` + `Switch`，整行 `clickable` | 导入页开关 |
| `MessageBanner` | `Surface` + `Icon`（core） + `Text` | 错误、警告、成功 |
| `ProgressPanel` | `LinearProgressIndicator` + `TextButton` | 分析进度 |
| `PrimaryActionBar` | `Surface(tonalElevation=3dp)` + 提示 `Text` + `Button` | 导入与报告底部操作栏 |
| `EmptyState` | `Column` + `Text` + `OutlinedButton` | 空态 |
| `MeterRow` | `LinearProgressIndicator` + `Text` | 质量卡指标 |

### 8.3 导入页拆分（`ui/import`）

| 文件 | 内容 | 备注 |
|---|---|---|
| `ImportScreen.kt` | 保留全部状态、副作用、回调签名 | 仅组合子组件，状态不迁移 |
| `ImportContent.kt` | 无状态布局（接收全部数据与回调） | 便于 `@Preview` |
| `VideoHeroCard.kt` | 首帧 + `RoiSelector` + 状态芯片 + 空态/加载态 | 替代当前 `preview?.let { RoiSelector }` |
| `RoiCard.kt` | ROI 提示 + 重置/确认按钮 | 文案与启用条件不变 |
| `SettingsSections.kt` | 动作、变式、输入、开关 | 使用 `SegmentedPicker`、`SwitchRow` |
| `RoiSelector.kt` | 只替换颜色为 token | 不改几何逻辑 |
| `RoiGeometry.kt` | 不改 | — |

### 8.4 报告页拆分（`ui/report`）

| 文件 | 内容 | 备注 |
|---|---|---|
| `ReportScreen.kt` | 保留全部状态与 launcher；组合 `ReportContent` | 导出逻辑不动 |
| `ReportContent.kt` | 无状态布局 | — |
| `ReportPreview.kt` | 首帧缓存 + Canvas 轨迹 + 失败回退 | 新增首帧 `remember` |
| `MetricHero.kt`（或 components） | MCV Hero | — |
| `SetSummarySection.kt` | 指标网格 | 替代 `SummaryCard` |
| `RepSelector.kt` | 横向芯片 + 柱图 | — |
| `RepDetailCard.kt` | 速度、轨迹、对比、相位、关节角 | 替代逐次长卡 |
| `VelocityChart.kt` | 增加相位带、平均线、选中态参数 | 颜色走 token |
| `BarPathChart.kt` | 增加 `comparisonSamples` 参数 | 颜色走 token |
| `PhaseTimeline.kt` | 相位色改用 `LocalVbtColors` | 逻辑不动 |
| `JointAngleCard.kt` | 关键帧 2×2 + 置信芯片 | 基于 `KeyFrameAngles.kt`（不改选择逻辑） |
| `LvpCard.kt` | LVP 卡 | — |
| `QualityCard.kt` | 折叠质量卡 + `MeterRow` | 警告映射函数保留 |
| `ExportSheet.kt` | `ModalBottomSheet` + 三行 | 导出 launcher 仍在 `ReportScreen` |
| `RepTable.kt` | 样式更新（tnum、表头） | 列宽不变 |
| `ReportRecompute.kt` | 不改 | — |

### 8.5 历史页拆分（`ui/history`）

| 文件 | 内容 |
|---|---|
| `HistoryScreen.kt` | 状态分流：列表 / 详情（保持现有参数签名） |
| `HistoryList.kt` | 列表 + 空态 |
| `SetCard.kt` | 列表卡 |
| `HistoryDetail.kt` | 头部卡 + 逐次行 |

### 8.6 叠加样式（`overlay`）

- 新增 `overlay/OverlayStyle.kt`：颜色 ARGB、线宽系数、胶囊尺寸，供 `OverlayRenderer` 与预览共用。
- `OverlayRenderer.kt`：替换 `Color.WHITE` 与线宽为 `OverlayStyle`；`drawHud` 保持单卡。
- 修改后需要重新跑叠加视频导出验证（见 §9）。

### 8.7 Material3 组件清单

- 使用：`Scaffold`、`TopAppBar`、`Button`、`FilledTonalButton`、`OutlinedButton`、`TextButton`、`AssistChip`、`SingleChoiceSegmentedButtonRow`、`OutlinedTextField`、`Switch`、`ListItem`、`ModalBottomSheet`、`SnackbarHost`、`LinearProgressIndicator`、`CircularProgressIndicator`、`Surface`、`HorizontalDivider`。
- 不用：`Card`（改用 `Surface` + 自定义描边，保证色阶一致）、`ElevatedCard`、`NavigationBar`（无底部导航）。

### 8.8 图表

- **不引入第三方图表库**。全部用 Compose `Canvas` / `drawPath` / `drawRect`。
- 柱图、曲线、轨迹、相位条复用同一套坐标映射（`0..1` 归一化）。
- 每个图表提供 `semantics { contentDescription = ... }` 摘要（如“第 2 次平均速度 0.55 米每秒，最佳”），供无障碍使用。

### 8.9 预览（开发辅助）

- 每个组件建 `@Preview`（暗色、411×914dp、字号 1.0 与 1.3）。Preview 数据用纯 Kotlin 假数据，不接数据库。

---

## 9. 验收清单（1080 × 2400，411 × 914 dp，2.625x）

### 9.1 视觉

- [ ] 页面背景为 `bg`，无白底 Material 默认面。
- [ ] 所有字号、颜色、圆角、间距只来自 `ui/theme` token（代码搜索无 `Color(0x` 散落、无 `.sp` 魔法数以外的直接值）。
- [ ] 导入页首屏（未滚动）可见：顶部栏、首帧（或空态投放区）、ROI 状态芯片、动作分段。
- [ ] 报告页首屏可见：预览区、MCV Hero 数值（最大字号）。MCV 是首屏唯一的 64sp 数值。
- [ ] 薄荷只出现在 MCV、主按钮、向心相位、进度和“已确认”状态；橙只在对比；琥珀只在速度读数与警告。
- [ ] 数字使用 Barlow Condensed 且列对齐（tnum）。
- [ ] 所有卡片底色 `surface1`（不透明）；视频上浮层 72% 黑。
- [ ] 无阴影（`elevation` 为 0），底部栏除外。
- [ ] 文字对比：正文 ≥ 4.5:1，大字 ≥ 3:1。
- [ ] 宽 411dp 下无横向溢出；芯片行正确换行。

### 9.2 交互

- [ ] 导入：选视频 → 首帧显示 → ROI 默认建议（或要求手动）→ 拖动 → 确认 → 填写设置 → 开始分析；每一步的 CTA 提示与现有启用条件一致。
- [ ] 动作切换会把变式重置为默认（`defaultVariant`）。
- [ ] 分析中：所有输入禁用；进度百分比与 `analysisProgress` 一致；取消后恢复 CTA 并显示取消提示。
- [ ] 开关整行可点（不止 Switch 本体）。
- [ ] 报告：切换 rep（芯片与柱图）同步更新速度、轨迹、相位、关节角。
- [ ] 对比开关：开启后显示最佳次橙色轨迹；关闭后仅薄荷。
- [ ] 手动校正：只读 → 编辑 → 完成，拖动边界后指标更新；非法边界显示错误条且不更新指标。
- [ ] 保存失败时显示错误条（模拟 `onPersist` 返回 false）。
- [ ] 导出弹层三项：JSON、CSV、叠加视频；叠加视频导出期间其它项禁用；成功/失败/无轨迹各自提示正确。
- [ ] 历史：列表 → 详情 → 返回列表；空态“返回导入”可用。
- [ ] 返回键与顶部返回按钮行为与改版前一致（导入/报告/历史的回退路径不变）。

### 9.3 尺寸与安全区

- [ ] 所有可点控件高度 ≥ 48dp。
- [ ] 系统字体 1.3x 下：导入页与报告页无文字截断导致的功能丢失；按钮文字不被裁切（允许换行）。
- [ ] 竖屏底部栏不遮挡最后一个可交互元素（`contentPadding` 留白 96dp）。
- [ ] 键盘弹起时输入框不被底部栏遮挡（`imePadding`）。
- [ ] 横屏 2400×1080：导入与报告两列布局可用；首帧与 ROI 同屏可拖动。
- [ ] 叠加视频预览（若做）：HUD 与速度胶囊不进入竖屏顶部 12% 与底部 15%。

### 9.4 无障碍与文案

- [ ] 所有图标有 `contentDescription`（或 `null` 并由文字承担语义）。
- [ ] 图表有摘要 `contentDescription`。
- [ ] 所有用户可见文案来自 `strings.xml`（代码中无中文字符串字面量）。
- [ ] 中文为默认；英文资源后续补 `values-en`，本次不翻译。
- [ ] JSON/CSV 字段名与 `MetricId` 不变（导出回归）。

### 9.5 回归（功能不变）

- [ ] `AnalysisRequest` 字段与改版前一致（lift、variant、cameraAngleDeg、plateDiameterM、loadKg、cameraLevel、analyzePose、roi）。
- [ ] `RectF` 的 ROI 与首帧分辨率映射不变。
- [ ] 历史数据库读写不变；历史列表数值格式化只改展示。
- [ ] 无新增网络权限；无新增第三方依赖（字体文件除外）。
- [ ] 叠加视频导出：改色与线宽后，抽样导出一条视频人工复核骨架、轨迹、速度胶囊、HUD 位置；结果记录到 `docs/validation/`。

---

## 10. 新增字符串（`strings.xml`，zh-CN，英文后续补）

> 命名：新增 key 以功能前缀命名；已有 key 不改名。

| key | zh-CN |
|---|---|
| `import_empty_title` | 选一段训练视频 |
| `import_empty_hint` | 支持深蹲、卧推、硬拉；最长 10 分钟 |
| `import_loading_frame` | 正在读取首帧 |
| `import_error_too_long` | 视频超过 10 分钟上限 |
| `import_cta_need_video` | 先选择一段视频 |
| `import_cta_need_roi` | 请确认杠铃片区域 |
| `import_camera_preset_side` | 侧拍 90° |
| `import_camera_preset_diagonal` | 前/后 45° |
| `import_pose_unavailable` | 姿态模型不可用，本次不输出关节角 |
| `import_pose_hint` | 关闭后不输出关节角与关键帧 |
| `analysis_cancelled` | 分析已取消 |
| `report_per_rep_mcv` | 逐次平均速度 |
| `report_compare_none` | 只看本次 |
| `report_compare_best` | 对比最佳次 |
| `report_preview_placeholder` | 首帧不可用，显示轨迹示意 |
| `report_export_title` | 导出 |
| `report_export_hint` | 文件会保存到你选择的位置 |
| `report_quality_summary_none` | 无需注意 |
| `report_quality_summary_warnings` | %1$d 条需注意 |
| `report_estimated_badge` | 估算 |
| `history_empty_hint` | 完成一次分析后会出现在这里 |
| `history_empty_cta` | 返回导入 |
| `history_date_format` | MM-dd HH:mm（仅 pattern，不作为展示文案时可移至代码常量，但需经资源评审） |

**复用**（不新增）：`import_*`、`report_*`、`history_*`、`metric_*`、`quality_*`、`report_export_*`、`analysis_notification_text`、`report_value_missing`。

---

## 11. 待决问题

1. 报告首帧是否读取视频（需要 URI 权限仍有效）。默认方案：读取，失败回退。
2. 历史列表是否显示速度损失（§5.3 方案 A/B）。
3. 对比叠加是否写入导出视频（默认仅应用内预览，导出保持现状，除非另行决定）。
4. 叠加视频预览是否进 v1（默认不进）。
5. 字体文件体积与授权复核（Barlow Condensed，OFL）。

---

## 12. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 报告页改为 rep 切换，旧的“逐次长卡”用户习惯变化 | 查找单次数据步骤增加 | 保留“逐次指标”表；柱图可点；芯片可横滑 |
| 叠加视频样式变更 | 导出结果与旧版视觉不一致 | 改后做抽样导出复核（§9.5） |
| 首帧解码增加报告页进入耗时 | 首屏慢 | 异步加载，骨架占位，失败回退 |
| 数字字体（Barlow）缺少 CJK | 中文混排需回退 | 数字与中文分开 `Text`，或仅数字使用该字体 |
| 团队人数少，拆分文件过多 | 维护成本 | 按 §8 的 3 个目录拆分即可，不建自定义设计系统框架 |

---

## 13. 实施顺序（建议）

1. `ui/theme` + `ui/components` + `MainActivity` 切换到 `VbtTheme`（无功能变化，可单独合入）。
2. 导入页视觉改版（布局与组件），状态不动。
3. 报告页改版（先预览区与 MCV Hero，再详情区，最后导出弹层）。
4. 历史页改版。
5. 叠加样式（`OverlayStyle`）与导出复核。
6. 字符串补全与 `values-en` 骨架。
