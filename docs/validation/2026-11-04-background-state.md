# 验证报告：离开/返回后恢复后台分析状态

- 日期：2026-11-04
- 范围：分析前台服务保存最近一次运行状态；Activity 重连或回到前台时恢复进度/结果/错误，不再重复启动分析。
- 改动文件：
  - `app/src/main/java/com/vbtdroid/app/analysis/AnalysisStateHolder.kt`（新增）
  - `app/src/main/java/com/vbtdroid/app/analysis/AnalysisForegroundService.kt`
  - `app/src/main/java/com/vbtdroid/app/MainActivity.kt`
  - `app/src/test/java/com/vbtdroid/app/analysis/AnalysisStateHolderTest.kt`（新增）

## 1. 实现

### 1.1 服务侧状态

- 新增纯 Kotlin 的 `AnalysisStateHolder` / `AnalysisState`，保存：
  - `running`
  - `progress`（0..1，写入时钳制）
  - `result`
  - `error`
- 语义：
  - `start()` 清空上一次结果/错误，进度归零。
  - `updateProgress()` 仅在 `running` 时生效，完成/取消后迟到的进度回调会被忽略。
  - `succeed()` 置 `progress = 1f` 并保存结果。
  - `fail()` 保留最后进度，保存错误。
  - `cancel()` 回到初始状态。
  - `consumeResult()` / `consumeError()` 读取一次后清空，避免重复展示。
- Binder 新增：`attach(callbacks)`、`state()`、`consumeResult()`、`consumeError()`；`analyze()` 在已有任务运行时只替换回调并返回 `ALREADY_RUNNING`，不启动第二个任务。
- 进度/结果/错误不再只投递给启动时的回调，而是同时写入 holder，再投递给当前已附加的回调。

### 1.2 Activity 恢复

- `onServiceConnected` 与生命周期 `ON_RESUME` 都调用 `syncWithService()`：
  - `running`：`analyzing = true`、恢复进度、附加新回调，并回到导入页进度 UI。
  - 有结果：消费结果并打开报告；若当前 composition 已展示同一结果则不重复导航。
  - 有错误：消费错误并显示。
- 点击“开始分析”收到 `ALREADY_RUNNING` 时改为 `syncWithService()`，不再直接报错。
- 结果只在 `syncWithService()` 中消费；实时回调不消费，避免旧 composition（Activity 已销毁）把唯一结果副本清掉。
- 用户从报告页返回时主动消费服务中的结果，防止再次回到 app 时重复打开报告。

## 2. 测试与构建

| 项目 | 结果 |
|---|---|
| `:core:test` | 96 通过，0 失败 |
| `:app:testDebugUnitTest`（新增 5 个 holder 测试单独运行） | 5 通过，0 失败 |
| `:app:testDebugUnitTest`（全量） | 149 个，148 通过，1 失败 |
| `:app:assembleDebug` | 成功 |

全量失败项：`com.vbtdroid.app.ui.report.PoseReadoutTest#frameAtPicksNearestWithinGap`。
该测试与 `PoseReadout.kt` 均为工作区中其他并行改动的未跟踪文件，和本任务的服务状态改动无关；本次未修改。失败断言为 1.6s 处应返回 null，但 0.5s 默认间隔下当前实现返回最近帧 1.2s。

## 3. 真机检查

设备：`192.168.57.102:41125`（2211133C，fuxi）。

- 旧版本 APK 安装：`Success`。
- 真机流程（旧版本，尚未包含“实时回调不消费结果”的最终修正）：
  1. 选择 22.42s 深蹲示例视频，完成框选与参数页，点击“开始分析”。
  2. 约 1 秒后按 Home 离开，10 秒后返回。
  3. 结果：导入页仍显示分析进度，进度从离开时的 0% 前进到 15%，再次离开返回后仍能继续更新 —— 进度恢复链路正常。
  4. 在后台等待分析完成（历史记录中 14:49 出现该次分析，说明分析与保存成功），返回 app 时停在导入页，未自动打开报告。定位到旧实现中 `onResult` 会立即 `consumeResult()`，旧 composition 被销毁时结果副本被清空；已改为只在 `syncWithService()` 消费。
- 最终 APK 重新构建成功，但尝试再次安装时设备已离线（`connection refused`），因此“后台完成后返回自动打开报告”这一条**未在最终版本上完成真机复测**，需要设备恢复后手动确认。

## 4. 手动复测步骤（设备恢复后）

1. 安装最新 `app-debug.apk`。
2. 选视频 → 框选 → 参数 → 开始分析。
3. 立即按 Home，等待分析完成（通知消失）。
4. 返回 app：应直接进入该次报告；按 Back 回导入页后再次 Home/返回，不应重复打开同一报告。
5. 再启动一次分析，分析中 Home → 返回：应显示已有进度，且只有一条分析任务（通知不重复、完成后只保存一条）。

## 5. 限制与风险

- 状态保存在服务内存中。若 Activity 销毁导致绑定解除且服务被系统回收/销毁，holder 随服务消失；本次未做跨进程持久化。结果仍已写入数据库（真机历史记录可见）。
- 取消分析会清空 holder；已投递到主线程队列的迟到进度会被忽略。
- 服务完成时仍沿用原有 `stopForeground + stopSelf`，绑定期间服务存活；是否需要为“结果未消费”延长服务生命周期，可后续按产品需求决定。
