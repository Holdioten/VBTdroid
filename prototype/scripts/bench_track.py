"""PC benchmark harness mirroring the Kotlin tuning gates (not the APK).

Tracking: Hough circle seeded/validated inside 1.35 * plate-diameter ROI, radius 0.85r..1.15r,
15% radius gate, 0.5-diameter innovation gate, velocity clamp.
Smoothing: Hampel + Savitzky-Golay over the whole track.
Reps: concentric velocity runs with duration/ROM gates and median-ROM filter.
"""

import sys

import cv2
import numpy as np

MAX_RADIUS_DEVIATION = 0.15
SEARCH_RADIUS_SCALE = 1.35
MAX_INNOVATION_DIAMETERS = 0.5
MAX_SPEED_DIAMETERS_PER_SEC = 8.0
VEPS = 0.05
MIN_CONCENTRIC_SEC = 0.15
MIN_ROM_M = 0.20
PLATE_M = 0.45
HOUGH = dict(dp=1.2, param1=120, param2=45)


def hough(gray, min_radius, max_radius, param2=None, min_dist=None):
    options = dict(HOUGH)
    options["param2"] = HOUGH["param2"] if param2 is None else param2
    options["minDist"] = max(8.0, min_radius) if min_dist is None else min_dist
    circles = cv2.HoughCircles(
        cv2.GaussianBlur(gray, (5, 5), 1.5),
        cv2.HOUGH_GRADIENT,
        minRadius=int(min_radius),
        maxRadius=int(max_radius),
        **options,
    )
    if circles is None:
        return []
    return [(float(c[0]), float(c[1]), float(c[2])) for c in circles[0]]


def local_hough(gray, reference_radius, pred_x, pred_y):
    radius = SEARCH_RADIUS_SCALE * reference_radius
    left = max(0, int(pred_x - radius))
    top = max(0, int(pred_y - radius))
    right = min(gray.shape[1], int(pred_x + radius))
    bottom = min(gray.shape[0], int(pred_y + radius))
    if right - left < 5 or bottom - top < 5:
        return None
    crop = gray[top:bottom, left:right]
    best = None
    for cx, cy, r in hough(
        crop,
        reference_radius * 0.85,
        reference_radius * 1.15,
        min_dist=max(8.0, reference_radius * 0.5),
    ):
        cx, cy = cx + left, cy + top
        if r <= 0 or abs(r - reference_radius) / reference_radius > MAX_RADIUS_DEVIATION:
            continue
        distance = ((cx - pred_x) ** 2 + (cy - pred_y) ** 2) ** 0.5
        if distance > MAX_INNOVATION_DIAMETERS * reference_radius:
            continue
        if best is None or distance < best[3]:
            best = (cx, cy, r, distance)
    return None if best is None else best[:3]


def init_track(gray_frames, candidates):
    """Scores frame-0 Hough candidates by how many of the first frames they track."""
    best = None
    for cx, cy, r in candidates:
        cx0, cy0 = cx, cy
        hits = 0
        for frame in gray_frames:
            found = local_hough(frame, r, cx0, cy0)
            if found is None:
                continue
            nx, ny, nr = found
            if abs(nr - r) / r > MAX_RADIUS_DEVIATION:
                continue
            hits += 1
            cx0, cy0, r = nx, ny, nr
        if best is None or hits > best[0]:
            best = (hits, cx, cy, r)
    return None if best is None else best[1:]


def hampel(values, window=5, sigmas=3.0):
    out = values.copy()
    half = window // 2
    for i in range(len(values)):
        local = values[max(0, i - half):min(len(values), i + half + 1)]
        median = np.median(local)
        threshold = max(1.0, sigmas * 1.4826 * np.median(np.abs(local - median)))
        if abs(values[i] - median) > threshold:
            out[i] = median
    return out


def savgol(values, window=7, order=2):
    out = values.copy()
    half = window // 2
    for i in range(len(values)):
        lo, hi = max(0, i - half), min(len(values), i + half + 1)
        u = np.arange(lo - i, hi - i, dtype=float)
        if len(u) < order + 1:
            continue
        coefficients = np.polyfit(u, values[lo:hi], min(order, len(u) - 1))
        out[i] = np.polyval(coefficients, 0.0)
    return out


def analyze(path, lift, verbose=True):
    capture = cv2.VideoCapture(path)
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    frames = []
    while True:
        ok, frame = capture.read()
        if not ok:
            break
        frames.append(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY))
    capture.release()
    if not frames:
        raise RuntimeError(f"cannot read {path}")

    candidates = hough(frames[0], 40, 260, param2=50, min_dist=50)
    init = init_track(frames[:10], candidates)
    if init is None:
        raise RuntimeError("no trackable initial plate circle")
    cx, cy, reference_radius = init
    print(f"{path}\n  fps={fps:.2f} size={frames[0].shape[1]}x{frames[0].shape[0]} "
          f"init=({cx:.0f},{cy:.0f}) r={reference_radius:.1f}px candidates={len(candidates)}")

    xs, ys, ts, radii = [], [], [], []
    vx = vy = 0.0
    hits = 0
    misses = 0
    radius = reference_radius
    for frame_index, gray in enumerate(frames):
        t = frame_index / fps
        dt = 1.0 / fps
        max_speed = MAX_SPEED_DIAMETERS_PER_SEC * radius
        vx = float(np.clip(vx, -max_speed, max_speed))
        vy = float(np.clip(vy, -max_speed, max_speed))
        pred_x, pred_y = cx + vx * dt, cy + vy * dt
        found = local_hough(gray, radius, pred_x, pred_y)
        if found is None and misses >= 5:
            # Recovery: full-frame search near the predicted position.
            for candidate in hough(gray, radius * 0.85, radius * 1.15, param2=55, min_dist=radius):
                cxx, cyy, rr = candidate
                distance = ((cxx - pred_x) ** 2 + (cyy - pred_y) ** 2) ** 0.5
                if distance > 2 * radius or abs(rr - radius) / radius > MAX_RADIUS_DEVIATION:
                    continue
                found = (cxx, cyy, rr)
                break
        if found is None:
            misses += 1
            vx *= 0.5
            vy *= 0.5
            cx, cy = pred_x, pred_y
            continue
        nx, ny, nr = found
        misses = 0
        hits += 1
        vx = 0.5 * vx + 0.5 * (nx - cx) / dt
        vy = 0.5 * vy + 0.5 * (ny - cy) / dt
        cx, cy = nx, ny
        radius = 0.9 * radius + 0.1 * nr  # slow reference drift, still gated per frame
        xs.append(cx)
        ys.append(cy)
        ts.append(t)
        radii.append(nr)

    t = np.asarray(ts)
    y = np.asarray(ys)
    r = np.asarray(radii)
    scale = (2.0 * np.median(r)) / PLATE_M
    y_m = -y / scale
    y_smooth = savgol(hampel(y_m), window=7, order=2)
    vy_smooth = np.gradient(y_smooth, t)

    pulls = []
    index = 0
    n = len(vy_smooth)
    while index < n:
        if vy_smooth[index] > VEPS:
            start = index
            while index < n and vy_smooth[index] > VEPS * 0.5:
                index += 1
            end = min(index, n - 1)
            duration = t[end] - t[start]
            rom = y_smooth[end] - y_smooth[start]
            if duration >= MIN_CONCENTRIC_SEC and rom > 0:
                pulls.append((float(t[start]), float(duration), float(rom)))
        else:
            index += 1

    threshold = MIN_ROM_M
    if len(pulls) >= 3:
        threshold = max(MIN_ROM_M, 0.5 * float(np.median([p[2] for p in pulls])))
    kept = [p for p in pulls if p[2] + 1e-9 >= threshold]
    if verbose:
        print(f"  peaks={len(pulls)} kept={len(kept)} rom_threshold={threshold:.3f}m "
              f"hit_rate={100.0 * hits / len(frames):.1f}% "
              f"radius_cv={100.0 * float(np.std(r) / np.mean(r)):.2f}%")
        for start, duration, rom in pulls:
            mark = "KEPT" if rom + 1e-9 >= threshold else "DROP"
            print(f"    t={start:6.2f}s dur={duration:4.2f}s rom={rom:5.3f}m {mark}")
    return len(kept)


if __name__ == "__main__":
    squat_reps = analyze(sys.argv[1], "squat")
    deadlift_reps = analyze(sys.argv[2], "deadlift")
    print(f"RESULT squat={squat_reps} (expect 6) deadlift={deadlift_reps} (expect 6)")
    if squat_reps != 6 or deadlift_reps != 6:
        sys.exit(1)
