# 验证报告：VBTdroid UI 重设计（Compose 暗色主题）

- 日期：2026-11-03
- 工作目录：`/mnt/d/CodeRepos/VBTdroid`
- 规格：`docs/ui-redesign-spec.md`（设计依据）
- 状态：**代码完成，构建与单测通过；真机安装、启动与截图未完成（设备拒绝连接），真机手势与视觉复核未做。**

## 1. 提交

| 提交 | 内容 |
|---|---|
| `2f40738` | `ui/theme`（颜色、字体、形状、间距、`VbtTheme`）+ `ui/components`（卡片、指标格、状态芯片、分段控件、开关行、提示条、底部操作栏、空态、质量进度条）；`MainActivity` 切换到 `VbtTheme` 并启用 edge-to-edge；Barlow Condensed 字体 |
| `8a3331a` | 导入页重写：首帧 Hero（空态/加载态/就绪态）、ROI 控制卡、元数据芯片、动作与变式分段、单位后缀输入、机位预设、整行开关、底部 CTA 与缺失步骤提示、分析进度原地显示与取消；横屏双栏；`RoiSelector` 颜色改用 token；`VideoLimits.TOO_LONG_MESSAGE` 常量 |
| `6264c57` | 报告页重写：预览（首帧 + 轨迹示意）、MCV Hero、2×2 指标网格、逐次柱图、rep 芯片、单次详情卡（速度/轨迹/对比/相位/关键帧角）、LVP 卡（标注“估算”）、可折叠质量卡、导出底部弹层；速度与轨迹图表改用 token 并新增相位底带、平均线、对比轨迹；`PhaseTimeline` 颜色改用 token |
| `5a6ee51` | 历史页重写：列表卡（格式化 MCV/负荷/日期、速度损失取自导出 JSON 摘要）、空态、详情头与逐次行（MCV、ROM 迷你条、相位芯片、警告） |
| `37bfda3` | 叠加样式：`overlay/OverlayStyle.kt` 集中 ARGB 常量（骨架白、轨迹薄荷、速度琥珀、HUD/速度胶囊底色），仅改颜色，不改编码器与布局 |

## 2. 验证结果

| 项目 | 命令/位置 | 结果 |
|---|---|---|
| core 单测 | `./gradlew :core:test` | 68 通过，0 失败（任务为 UP-TO-DATE，core 代码未变，沿用上次通过结果） |
| app 单测 | `./gradlew :app:testDebugUnitTest` | 59 通过，0 失败，0 跳过 |
| debug 构建 | `./gradlew :app:assembleDebug` | 成功，生成 `app/build/outputs/apk/debug/app-debug.apk` |
| 安装到手机 | `adb -s 192.168.57.102:41729 install -r ...` | **未完成**：设备拒绝连接（`Connection refused`），`adb devices` 中目标设备消失，mDNS 无结果，重试两次仍失败 |
| 启动冒烟 | `adb shell am start -n com.vbtdroid.app/.MainActivity` | **未完成**（同上） |
| 截图 | `/tmp/opencode/vbtdroid-shots/ui-redesign.png` | **未生成**。未留下空文件或占位图，避免误作证据 |

在设备恢复后需要补做：安装、启动、导入页与报告页截图、导入页 ROI 拖动（移动/缩放/重新框选）、分段与开关整行点击、分析取消、报告 rep 切换与对比开关、相位手动校正、导出弹层三项（JSON/CSV/叠加视频）、历史列表与详情。

## 3. 静态检查

- 代码中无 `Color(0x…)` 与 `Color.White/Green` 等散落颜色（`ui/theme/Color.kt` 与 `overlay/OverlayStyle.kt` 除外）。
- 代码中无中文字符串字面量；新增文案均在 `values/strings.xml` 与 `values-en/strings.xml`。
- 无新增网络权限，无新增第三方依赖，无第三方图表库；图表仍用 Compose `Canvas`。
- `core` 未改动，仍为纯 JVM。
- JSON/CSV 字段与导出映射未改动；`AnalysisRequest` 字段与分析流程未改动。

## 4. 与规格的偏差与注意事项

1. **字体**：Barlow Condensed（Medium、SemiBold）经代理从 google/fonts 官方仓库下载，非离线内置。OFL 许可文本放在 `docs/third_party/Barlow-OFL.txt`。仅用于数字。
2. **导入 Hero 高度**：未按规格夹紧到 160–300dp。`RoiSelector` 保持视频原始宽高比，竖屏视频的首帧会比规格更高。若要夹紧，需要改 ROI 映射方式（FillBounds 拉伸或 Fit 留边），属于几何改动，本次未做。
3. **文件拆分**：未完全按规格 §8.3–§8.5 拆成 `ImportContent`/`VideoHeroCard`/`SettingsSections` 等。导入页主体集中在 `ImportScreen.kt`，报告与历史按功能拆分。状态与回调签名保持不变。
4. **报告横屏**：未做双栏，横屏为单列滚动。导入页横屏为双栏。
5. **报告预览**：首帧为真实视频帧，但轨迹是按当前次轨迹自动缩放的示意，不与画面中杠铃片做坐标对齐。芯片文案为“杠铃轨迹示意”。
6. **交错入场**：只对前 3 个区块（预览、Hero、指标网格）做淡入上移，未覆盖到 5 个。
7. **逐次默认选中**：默认选中第 1 次（列表第一项）。规格写“默认 1”，按从 0 计的列表位置理解。
8. **导出弹层**：选择 JSON/CSV 后弹层保持打开，结果以弹层内提示条显示，同时发出 Snackbar（Snackbar 可能被弹层遮挡）。
9. **叠加视频**：只改了颜色（骨架、轨迹、速度胶囊、HUD 底色）。未做尾迹渐隐、HUD 字体变化与安全区调整。编码器与布局未动。导出效果未在设备上复核。
10. **历史速度损失**：列表卡的速度损失从 `exportJson.summary.set_velocity_loss_pct` 读取（方案 A）。解析失败时不显示。
11. **分析取消提示**：取消后底部恢复 CTA，并显示 Snackbar“分析已取消”。
12. **未做**：`@Preview` 组件预览（规格 §8.9）、屏幕阅读器的完整语义复核、系统字体 1.3× 的实测。

## 5. 结论

代码层面的重设计已完成，并通过编译与全部单测。真机安装、截图视觉复核与手势验证因设备连接失败未完成，本报告不作任何真机结论。
