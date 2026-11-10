"""与 Kotlin RepMetricsCalculator 同构的重复次数指标计算。"""

from math import fabs

GRAVITY_MPS2 = 9.80665


def calculate_metrics(rep, samples, load_kg=None):
    rep_samples = sorted(
        (sample for sample in samples if rep["start_sec"] <= sample["t_sec"] <= rep["end_sec"]),
        key=lambda sample: sample["t_sec"],
    )
    if len(rep_samples) < 2:
        raise ValueError("重复区间至少需要两个运动样本")
    concentric_phases = [phase for phase in rep["phases"] if phase["type"] == "CONCENTRIC"]
    if not concentric_phases:
        raise ValueError("重复中缺少向心阶段")
    concentric = [
        sample for sample in rep_samples
        if any(phase["start_sec"] <= sample["t_sec"] <= phase["end_sec"] for phase in concentric_phases)
    ]
    if len(concentric) < 2:
        raise ValueError("向心阶段至少需要两个运动样本")
    first, last = rep_samples[0], rep_samples[-1]
    cfirst, clast = concentric[0], concentric[-1]
    rep_duration = last["t_sec"] - first["t_sec"]
    concentric_duration = clast["t_sec"] - cfirst["t_sec"]
    if rep_duration <= 0 or concentric_duration <= 0:
        raise ValueError("样本时间间隔必须为正")
    concentric_ys = [sample["y_m"] for sample in concentric]
    velocities = [sample["vy_mps"] for sample in concentric]
    peak_index = max(range(len(concentric)), key=lambda index: velocities[index])
    negative_acceleration = next(
        (index for index, sample in enumerate(concentric) if sample["ay_mps2"] < 0), -1
    )
    if negative_acceleration > 0:
        end = concentric[negative_acceleration]
        mpv = (end["y_m"] - cfirst["y_m"]) / (end["t_sec"] - cfirst["t_sec"])
    else:
        mpv = (clast["y_m"] - cfirst["y_m"]) / concentric_duration
    mcv = (clast["y_m"] - cfirst["y_m"]) / concentric_duration
    rep_ys = [sample["y_m"] for sample in rep_samples]
    rep_xs = [sample["x_m"] for sample in rep_samples]
    rom_concentric = clast["y_m"] - cfirst["y_m"]
    if rom_concentric <= 0:
        raise ValueError("向心端点位移非正，向心 ROM 不可用")
    peak_velocity = velocities[peak_index]
    # Sticking region = morphological Vpeak1 -> Vmin descending segment: the first local
    # velocity peak after the start, then the first local minimum after it. The terminal
    # braking phase after Vpeak2 is not sticking, so negative acceleration is not the test.
    prominence = max(0.03, peak_velocity * 0.05)
    vpeak1 = next(
        (
            index
            for index in range(1, len(concentric) - 1)
            if velocities[index] >= velocities[index - 1]
            and velocities[index] > velocities[index + 1]
            and velocities[index] - velocities[0] >= prominence
        ),
        peak_index,
    )
    vmin = next(
        (
            index
            for index in range(vpeak1 + 1, len(concentric) - 1)
            if velocities[index] <= velocities[index - 1]
            and velocities[index] <= velocities[index + 1]
        ),
        None,
    )
    sticking_point = None
    if vmin is not None:
        while (
            vmin + 1 < len(concentric) - 1
            and abs(velocities[vmin + 1] - velocities[vmin]) <= 1e-9
        ):
            vmin += 1
        if velocities[vpeak1] - velocities[vmin] >= prominence:
            sticking_point = (
                concentric[vmin]["y_m"] - cfirst["y_m"]
            ) / rom_concentric * 100.0

    metrics = {
        "rep_duration_s": rep["end_sec"] - rep["start_sec"],
        "rom_total_m": max(rep_ys) - min(rep_ys),
        "rom_concentric_m": rom_concentric,
        "mean_velocity_mps": (last["y_m"] - first["y_m"]) / rep_duration,
        "mcv_mps": mcv,
        "mpv_mps": mpv,
        "peak_velocity_mps": peak_velocity,
        "peak_acceleration_mps2": max(sample["ay_mps2"] for sample in concentric),
        "peak_deceleration_mps2": min(sample["ay_mps2"] for sample in concentric),
        "sticking_point_pct": sticking_point,
        "velocity_loss_pct": None,
        "bar_path_horizontal_range_m": max(rep_xs) - min(rep_xs),
        "bar_path_deviation_m": max(fabs(x - first["x_m"]) for x in rep_xs),
        "mean_force_n": None,
        "peak_force_n": None,
        "mean_power_w": None,
        "peak_power_w": None,
        "impulse_ns": None,
        "work_j": None,
    }
    if load_kg is not None and load_kg > 0:
        forces = [load_kg * (GRAVITY_MPS2 + sample["ay_mps2"]) for sample in concentric]
        powers = [force * sample["vy_mps"] for force, sample in zip(forces, concentric)]
        impulse = work = power_integral = 0.0
        for index, (current, following) in enumerate(zip(concentric, concentric[1:])):
            dt = following["t_sec"] - current["t_sec"]
            mean_force = (forces[index] + forces[index + 1]) / 2.0
            impulse += mean_force * dt
            work += mean_force * (following["y_m"] - current["y_m"])
            power_integral += (powers[index] + powers[index + 1]) / 2.0 * dt
        metrics.update({
            "mean_force_n": impulse / concentric_duration,
            "peak_force_n": max(forces),
            "mean_power_w": power_integral / concentric_duration,
            "peak_power_w": max(powers),
            "impulse_ns": impulse,
            "work_j": work,
        })
    return metrics
