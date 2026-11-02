"""VBTdroid 运动学分析的独立 Python 参考实现。"""

import numpy as np

from .calibration import Calibration
from .metrics import calculate_metrics
from .phases import segment_phases
from .report import write_report
from .tracking import track_video


def _derive_motion(samples):
    """使用 Kotlin LocalPolynomial 同款局部二次拟合导出速度和加速度。"""
    times = np.asarray([sample["t_sec"] for sample in samples], dtype=float)
    ys = np.asarray([sample["y_m"] for sample in samples], dtype=float)
    xs = np.asarray([sample["x_m"] for sample in samples], dtype=float)
    motion = []
    window = 7
    for index, (time, x, y) in enumerate(zip(times, xs, ys)):
        start = max(0, min(index - window // 2, len(times) - min(window, len(times))))
        end = min(start + window, len(times))
        scale = float(np.max(np.abs(times[start:end] - time)))
        if scale == 0.0:
            vx = vy = ax = ay = 0.0
        else:
            u = (times[start:end] - time) / scale
            degree = min(2, end - start - 1)
            design = np.column_stack([u**power for power in range(degree + 1)])
            cx = np.linalg.lstsq(design, xs[start:end], rcond=None)[0]
            cy = np.linalg.lstsq(design, ys[start:end], rcond=None)[0]
            vx = float(cx[1] / scale) if degree >= 1 else 0.0
            vy = float(cy[1] / scale) if degree >= 1 else 0.0
            ax = float(2.0 * cx[2] / scale**2) if degree >= 2 else 0.0
            ay = float(2.0 * cy[2] / scale**2) if degree >= 2 else 0.0
        motion.append({
            "t_sec": float(time), "x_m": float(x), "y_m": float(y),
            "vx_mps": vx, "vy_mps": vy, "ax_mps2": ax, "ay_mps2": ay,
        })
    return motion


def analyze_video(video, plate_diameter_m, camera_angle_deg, fps, lift="deadlift", load_kg=None):
    """跟踪视频中的最大圆形杠铃片，并返回与 SetExport 同构的字典。"""
    observations, major_axis_px, warnings = track_video(video, fps)
    if not observations:
        return {
            "schema_version": 1,
            "lift": lift,
            "load_kg": load_kg,
            "plate_diameter_mm": plate_diameter_m * 1000.0,
            "camera_azimuth_deg": camera_angle_deg,
            "reps": [],
            "warnings": warnings or ["未检测到杠铃片"],
        }

    calibration = Calibration.from_plate(plate_diameter_m, major_axis_px, camera_angle_deg)
    bar_samples = [calibration.to_meters(item) for item in observations]
    motion = _derive_motion(bar_samples)
    slices = segment_phases(motion, lift)
    reps = []
    for rep_slice in slices:
        try:
            metrics = calculate_metrics(rep_slice, motion, load_kg)
        except ValueError as error:
            warnings.append(f"第 {rep_slice['index']} 次重复无效：{error}")
            continue
        reps.append({"index": rep_slice["index"], "metrics": metrics, "quality": {}})
    return {
        "schema_version": 1,
        "lift": lift,
        "load_kg": load_kg,
        "plate_diameter_mm": plate_diameter_m * 1000.0,
        "camera_azimuth_deg": camera_angle_deg,
        "reps": reps,
        "warnings": warnings,
    }


__all__ = ["analyze_video", "write_report", "Calibration"]
