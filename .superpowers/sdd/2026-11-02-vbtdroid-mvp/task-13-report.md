# Task 13 实施报告：历史、导出、验证工具

## 状态

已完成。TDD 红绿验证及 Debug 构建通过，已提交。

## 实施内容

- 增加 Room runtime、ktx、KSP compiler 依赖及版本目录别名。
- 新增 Session/Set/Rep 三张 Room 表实体；UUID 稳定主键，Set→Session 和 Rep→Set 外键级联删除，并为关联查询建立索引。Set/Rep 保存 metricsJson、qualityJson。
- 新增 AppDatabase 和挂起 DAO：插入 Set、批量插入 Rep、按 lift 查询 Set。
- 新增 CSV 导出器，包含指定九列，每个 Rep 输出一行；空值导出为空字段。
- 新增 Bland–Altman bias、样本标准差和 95% LOA，以及 ICC(2,1) 双向随机绝对一致性计算。
- 新增 Agreement 和 CSV 导出单元测试。

## TDD 与验证

- RED `./gradlew :core:test --tests '*AgreementTest*' --no-daemon`：预期失败，Agreement 尚未实现，测试编译报未解析引用。
- RED `./gradlew :app:testDebugUnitTest --tests '*CsvExporterTest*' --no-daemon`：预期失败，CsvExporter 尚未实现，测试编译报未解析引用。
- GREEN 同两条命令：均通过。
- `./gradlew :app:assembleDebug --no-daemon`：通过。

## 文件

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/vbtdroid/app/data/Entities.kt`
- `app/src/main/java/com/vbtdroid/app/data/AppDatabase.kt`
- `app/src/main/java/com/vbtdroid/app/data/SessionDao.kt`
- `app/src/main/java/com/vbtdroid/app/export/CsvExporter.kt`
- `app/src/test/java/com/vbtdroid/app/export/CsvExporterTest.kt`
- `core/src/main/kotlin/com/vbtdroid/core/validation/Agreement.kt`
- `core/src/test/kotlin/com/vbtdroid/core/validation/AgreementTest.kt`

## 自检与注意事项

- 未改动核心指标公式；未添加历史 UI 或云同步。
- Room KSP 编译及应用构建通过；未做 Room 内存数据库运行时测试（任务允许仅编译验证）。
- 单样本输入时样本差值标准差取 0；ICC 对完全一致样本返回 1，单样本不一致返回 0，避免不可计算结果产生 NaN。
- 构建日志存在既有 VideoDecoder 弃用警告及本机原生库无法 strip 提示，不影响构建。

## 提交

- `feat: add history, CSV export and agreement validation`

---

# Task 13 复审修复报告

## 修复内容

- 新增 `AgreementCsv.compareCsv(Path, Path, metricColumn)`：读取 CSV 表头，校验 `rep`/指标列，按 rep 配对，忽略任一侧空指标行，拒绝重复 rep 和非法/非有限数值，要求至少两对样本。
- 新增可运行 CLI：`./gradlew :core:run --args='ground_truth.csv app.csv mcv'`；以六位小数打印五项统计值，参数或输入错误返回非零状态。
- `SessionDao.insertSet` 改为默认 ABORT；新增独立 `updateSet` 更新接口及 Set/Rep 查询/Session 插入支持。Rep 索引改为唯一 `(setId, repIndex)`。
- Agreement 拒绝少于两对样本；新增非平凡 Bland–Altman 样本 SD/LOA 与 ICC(2,1)=0.6 测试。
- 新增临时 CSV 比对测试（含空值行）及 Robolectric Room 回归测试，验证重复 Set 插入失败且 Reps 保留、显式更新不删除 Reps。

## 复审修复验证

- RED `./gradlew :core:test --tests '*Agreement*' --no-daemon`：AgreementCsv 尚未实现，编译失败，符合预期。
- RED `./gradlew :app:testDebugUnitTest --tests '*Csv*' --no-daemon`：DAO 新契约尚未实现，编译失败，符合预期。
- GREEN `./gradlew :core:test --tests '*Agreement*' --no-daemon`：通过。
- GREEN `./gradlew :app:testDebugUnitTest --tests '*Csv*' --no-daemon`：通过。
- `./gradlew :app:testDebugUnitTest --tests '*SessionDaoTest*' --no-daemon`：首次运行超过 120 秒超时；提高超时重跑通过（Robolectric Room 两项回归均通过）。
- `./gradlew :core:run --args='/tmp/opencode/ground-truth.csv /tmp/opencode/app-export.csv rom' --no-daemon`：通过；输出 bias=0.000000、sdDiff=0.100000、LOA ±0.196000、ICC=0.900000。
- `./gradlew :app:assembleDebug --no-daemon`：通过。

## 复审修复提交

- `fix: complete CSV validation and safe set inserts`
