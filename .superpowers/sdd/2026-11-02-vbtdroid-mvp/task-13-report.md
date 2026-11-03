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
