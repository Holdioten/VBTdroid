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
    # Sticking region = morphological Vpeak1 -> Vmin descending segment. The terminal braking
    # phase after Vpeak2 is not sticking, so negative acceleration is not the test. The
    # thresholds and the start-position floor match the Kotlin detector so both implementations
    # classify the same curve identically.
    prominence = max(0.05, peak_velocity * 0.08)
    maxima = [
        index
        for index in range(1, len(concentric) - 1)
        if velocities[index] >= velocities[index - 1]
        and velocities[index] > velocities[index + 1]
    ]
    minima = [
        index
        for index in range(1, len(concentric) - 1)
        if velocities[index] <= velocities[index - 1]
        and velocities[index] <= velocities[index + 1]
    ]

    def collapse(indices, better):
        kept = []
        for index in indices:
            if kept and concentric[index]["t_sec"] - concentric[kept[-1]]["t_sec"] < 0.10:
                if better(index, kept[-1]):
                    kept[-1] = index
            else:
                kept.append(index)
        return kept

    maxima = collapse(maxima, lambda a, b: velocities[a] > velocities[b])
    minima = collapse(minima, lambda a, b: velocities[a] < velocities[b])

    sticking_point = None
    for vpeak1 in maxima:
        start_pct = (concentric[vpeak1]["y_m"] - cfirst["y_m"]) / rom_concentric * 100.0
        if start_pct < 15.0:
            continue
        if velocities[vpeak1] - velocities[0] < prominence:
            continue
        limit = len(concentric)
        for index in range(vpeak1 + 1, len(concentric)):
            if velocities[index] > velocities[vpeak1]:
                limit = index
                break
        vmin = next(
            (
                index
                for index in minima
                if vpeak1 < index < limit
                and velocities[vpeak1] - velocities[index] >= prominence
            ),
            None,
        )
        if vmin is None:
            continue
        rise = max(velocities[vmin + 1:], default=float("-inf"))
        if rise - velocities[vmin] < prominence:
            continue
        while (
            vmin + 1 < limit
            and abs(velocities[vmin + 1] - velocities[vmin]) <= 1e-9
        ):
            vmin += 1
        end_pct = (concentric[vmin]["y_m"] - cfirst["y_m"]) / rom_concentric * 100.0
        if end_pct <= start_pct:
            continue
        sticking_point = end_pct
        break

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
