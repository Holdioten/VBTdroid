"""与 Kotlin PhaseSegmenter 阈值及分段规则一致的相位识别。"""

VEPS = 0.03
MIN_PHASE_SEC = 0.12
LOCKOUT_SEC = 0.10


def _classify(samples):
    def phase_for(velocity):
        if velocity < -VEPS:
            return "ECCENTRIC"
        if velocity > VEPS:
            return "CONCENTRIC"
        return "PAUSE"

    runs = []
    current = phase_for(samples[0]["vy_mps"])
    start = samples[0]["t_sec"]
    exit_eps = VEPS * 0.5
    for sample in samples[1:]:
        velocity = sample["vy_mps"]
        if current == "ECCENTRIC":
            next_type = phase_for(velocity) if velocity >= -exit_eps else current
        elif current == "CONCENTRIC":
            next_type = phase_for(velocity) if velocity <= exit_eps else current
        else:
            next_type = phase_for(velocity)
        if next_type != current:
            runs.append({"type": current, "start_sec": start, "end_sec": sample["t_sec"]})
            current, start = next_type, sample["t_sec"]
    runs.append({"type": current, "start_sec": start, "end_sec": samples[-1]["t_sec"]})
    return runs


def _merge_short_runs(source):
    runs = [dict(run) for run in source]
    if len(runs) == 1 and runs[0]["end_sec"] - runs[0]["start_sec"] < MIN_PHASE_SEC:
        return []
    index = 0
    while index < len(runs) and len(runs) > 1:
        duration = runs[index]["end_sec"] - runs[index]["start_sec"]
        if duration >= MIN_PHASE_SEC:
            index += 1
            continue
        previous = runs[index - 1] if index > 0 else None
        following = runs[index + 1] if index + 1 < len(runs) else None
        if previous is None:
            target_index = index + 1
        elif following is None:
            target_index = index - 1
        elif previous["type"] == following["type"] or (
            previous["end_sec"] - previous["start_sec"] >= following["end_sec"] - following["start_sec"]
        ):
            target_index = index - 1
        else:
            target_index = index + 1
        target = runs[target_index]
        merged = {
            "type": target["type"],
            "start_sec": min(runs[index]["start_sec"], target["start_sec"]),
            "end_sec": max(runs[index]["end_sec"], target["end_sec"]),
        }
        first, second = sorted((index, target_index))
        runs.pop(second)
        runs[first] = merged
        adjacent = 1
        while adjacent < len(runs):
            if runs[adjacent - 1]["type"] == runs[adjacent]["type"]:
                runs[adjacent - 1]["end_sec"] = runs.pop(adjacent)["end_sec"]
            else:
                adjacent += 1
        index = max(0, first - 1)
    if len(runs) == 1 and runs[0]["end_sec"] - runs[0]["start_sec"] < MIN_PHASE_SEC:
        return []
    return runs


def segment_phases(samples, lift="deadlift"):
    if not samples:
        return []
    runs = _merge_short_runs(_classify(samples))
    start_type = "CONCENTRIC" if lift.lower() == "deadlift" else "ECCENTRIC"
    reps = []
    phases = []

    def finish():
        nonlocal phases
        if phases:
            reps.append({
                "index": len(reps) + 1,
                "start_sec": phases[0]["start_sec"],
                "end_sec": phases[-1]["end_sec"],
                "phases": phases,
            })
            phases = []

    for run in runs:
        if run["type"] == start_type:
            finish()
            phases.append(dict(run))
            continue
        if not phases:
            continue
        duration = run["end_sec"] - run["start_sec"]
        has_concentric = any(phase["type"] == "CONCENTRIC" for phase in phases)
        if run["type"] == "PAUSE" and has_concentric and duration >= LOCKOUT_SEC:
            lockout_end = min(run["end_sec"], run["start_sec"] + LOCKOUT_SEC)
            if lockout_end > run["start_sec"]:
                phases.append({"type": "LOCKOUT", "start_sec": run["start_sec"], "end_sec": lockout_end})
            finish()
        elif start_type == "ECCENTRIC" or run["type"] != "ECCENTRIC":
            phases.append(dict(run))
    finish()
    return reps
