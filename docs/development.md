# 开发与运行

## 1. 工具链

本机（WSL）使用便携工具链，不依赖系统安装：

```bash
export JAVA_HOME="$HOME/toolchains/jdk17"
export PATH="$JAVA_HOME/bin:$HOME/toolchains/gradle-8.10.2/bin:$PATH"
export ANDROID_HOME="$HOME/toolchains/android-sdk"
export ANDROID_SDK_ROOT="$HOME/toolchains/android-sdk"
```

- JDK 17：`~/toolchains/jdk17`
- Gradle 8.10.2：`~/toolchains/gradle-8.10.2`
- Android SDK：`~/toolchains/android-sdk`（platform 35 / build-tools 35.0.0 / platform-tools）

## 2. 构建

**同一 worktree 同时只能跑一个 Gradle 构建。** 先确认没有其他构建：

```bash
ps -eo pid,cmd | grep GradleWrapperMain | grep -v grep
```

正常：

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

干净构建（怀疑 dex/KSB 缓存坏时）：

```bash
rm -rf app/build core/build .kotlin
./gradlew :app:assembleDebug --no-daemon
```

产物：

```
app/build/outputs/apk/debug/app-debug.apk   # arm64 debug，约 62MB
```

### 验证 core 类真的进包

```bash
python3 - <<'PY'
import zipfile, os
z = zipfile.ZipFile('app/build/outputs/apk/debug/app-debug.apk')
os.makedirs('/tmp/dexs', exist_ok=True)
for n in z.namelist():
    if n.endswith('.dex'):
        open('/tmp/dexs/' + n, 'wb').write(z.read(n))
PY
for f in /tmp/dexs/*.dex; do
  ~/toolchains/android-sdk/build-tools/35.0.0/dexdump -l plain "$f" \
    | grep -q "Class descriptor.*Lcom/vbtdroid/core/model/Lift;" && echo "$f OK"
done
```

没有 `OK` = 不要安装。

## 3. 模型资产

权重不进 git。

```bash
./ml/download_models.sh
```

- Pose：`app/src/main/assets/models/pose_landmarker_lite.task`（必需）
- Plate：`app/src/main/assets/models/plate.tflite`（EfficientDet-Lite0，MIT；自动 ROI 用；缺失时手动 ROI 仍可用）
- 下载校验：`ml/models.lock`

## 4. 设备

无线 ADB，端口由用户提供：

```bash
adb devices -l
adb connect <IP:PORT>
adb -s <IP:PORT> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <IP:PORT> shell am start -n com.vbtdroid.app/.MainActivity
```

日志：

```bash
adb -s <IP:PORT> logcat -d -v brief | grep -E 'FATAL|NoClassDef|VBTDebug|Overlay encoder'
```

拉数据库（debug 包）：

```bash
adb -s <IP:PORT> exec-out run-as com.vbtdroid.app \
  cat /data/data/com.vbtdroid.app/databases/vbtdroid.db > /tmp/vbtdroid.db
```

查最新分析：

```bash
python3 - <<'PY'
import sqlite3, json
c = sqlite3.connect('/tmp/vbtdroid.db')
for sid, ts, js in c.execute('select setId, createdAtEpochMs, metricsJson from sets order by rowid desc limit 3'):
    d = json.loads(js)
    print(sid, ts, 'reps=', len(d.get('reps', [])), 'warnings=', d.get('warnings'))
PY
```

## 5. PC 基准

`prototype/scripts/bench_track.py` 用 `example/` 两条视频跑跟踪/计数基线：

```bash
cd prototype
python3 -m venv .venv
.venv/bin/pip install numpy opencv-python-headless scipy pytest
.venv/bin/python scripts/bench_track.py
```

输出 `inferred=... expected=... PASS/FAIL`。期望次数是 fixture 元数据，不进入算法。

## 6. 常见故障

| 现象 | 原因 | 处理 |
|---|---|---|
| `NoClassDefFoundError: com.vbtdroid.core.model.Lift` | 并发构建/增量 dex 损坏 | 停 Gradle → 删 `app/build core/build .kotlin` → 单次重建 → dexdump 验证 |
| `annotationProcessors.json` 不存在 | KSP 增量缓存 | 同上 |
| `Permission denied` 装 APK | 小米 USB 安装限制 | 开发者选项开“USB 安装”“USB 调试（安全设置）” |
| 无线 ADB 拒绝 | 端口变化/锁屏/不同 Wi-Fi | 重开无线调试，发新端口 |
| 报告“叠加视频不可用” | 分析服务 overlay 失败/缓存被清 | 看 logcat `Overlay`，重新分析 |
| 导出慢 | 软件编码器/逐像素转换 | 确认 logcat `Overlay encoder` 是硬件；仍慢则降分辨率 |
| 硬拉次数多/少 | 阶段合并/ROM 阈值 | 先跑 PC 基准，再调 `PhaseSegmenter`；不要写死次数 |

## 7. 开发约定

- 先读 `AGENTS.md` 和 `docs/status.md`。
- 算法改动先写纯函数测试；PC 基准先过，再进 APK。
- 一个批次一个 commit；不要每改一行构建/截图。
- 不并行改同一文件；不并行跑 Gradle。
- 手机端 UI/手势由用户手动验证，agent 用 logcat/DB，少截图。
- 不 push/merge 共享分支，先问用户。
