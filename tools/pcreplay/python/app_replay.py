"""PC replay of the App analysis, source-for-source.

YOLO11n plate detector (the App's own plate_yolo.tflite), letterbox + NMS as in
YoloPlateDetector.kt, identity association as in PlateTracker.kt, then smoothing /
calibration / kinematics / deadlift segmentation as in core.
"""

import sys

import cv2
import numpy as np
from ai_edge_litert.interpreter import Interpreter

MODEL = "/mnt/d/CodeRepos/VBTdroid/app/src/main/assets/models/plate_yolo.tflite"
MIN_CONFIDENCE = 0.35
NMS_IOU = 0.45
PLATE_CLASS = 0
DETECTION_LATERAL_DIAMETERS = 0.5
DETECTION_VERTICAL_DIAMETERS = 2.5

MEAN_WINDOW_SEC = 0.30
TRACK_SMOOTH_WINDOW_SEC = 0.18
INITIAL_DIAMETER_SEC = 0.30
MIN_INITIAL_DIAMETER_SAMPLES = 3
POLY_ORDER = 2

V_EPS = 0.05
EXIT_EPS_FRACTION = 0.5
GAP_MERGE_SEC = 1.0
GAP_MERGE_ROM_FRACTION = 0.25
BOTTOM_LOOKBACK_SEC = 0.5
LOCKOUT_SEC = 0.10
MIN_REP_SEC = 0.45
MIN_CONCENTRIC_SEC = 0.15
MIN_ROM_M = 0.20
ROM_OUTLIER_MIN_REPS = 3
ROM_OUTLIER_MEDIAN_FRACTION = 0.5


class Detector:
    def __init__(self, path=MODEL):
        self.interp = Interpreter(model_path=path, num_threads=4)
        self.interp.allocate_tensors()
        inp = self.interp.get_input_details()[0]
        out = self.interp.get_output_details()[0]
        # NCHW: [1, 3, H, W]
        self.h, self.w = int(inp["shape"][2]), int(inp["shape"][3])
        self.out_shape = out["shape"]
        self.in_index = inp["index"]
        self.out_index = out["index"]
        print(f"YOLO ready input={self.w}x{self.h} output={self.out_shape}")

    def detect(self, frame_bgr):
        h, w = frame_bgr.shape[:2]
        scale = min(self.w / w, self.h / h)
        dw, dh = int(round(w * scale)), int(round(h * scale))
        pad_x, pad_y = (self.w - dw) / 2.0, (self.h - dh) / 2.0
        canvas = np.full((self.h, self.w, 3), 114, np.uint8)
        resized = cv2.resize(frame_bgr, (dw, dh), interpolation=cv2.INTER_LINEAR)
        canvas[int(pad_y):int(pad_y) + dh, int(pad_x):int(pad_x) + dw] = resized
        rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        chw = np.transpose(rgb, (2, 0, 1))[None]
        self.interp.set_tensor(self.in_index, chw)
        self.interp.invoke()
        out = self.interp.get_tensor(self.out_index)[0]  # [4+classes, anchors]

        scores = out[4]
        keep = scores >= MIN_CONFIDENCE
        dets = []
        for idx in np.nonzero(keep)[0]:
            best_class, best_score = 0, float(scores[idx])
            for cls in range(1, out.shape[0] - 4):
                if out[4 + cls, idx] > best_score:
                    best_score = float(out[4 + cls, idx])
                    best_class = cls
            if best_class != PLATE_CLASS:
                continue
            cx, cy, bw, bh = (out[0, idx] * self.w, out[1, idx] * self.h,
                              out[2, idx] * self.w, out[3, idx] * self.h)
            if bw <= 1 or bh <= 1:
                continue
            left = ((cx - bw / 2) - pad_x) / scale
            top = ((cy - bh / 2) - pad_y) / scale
            right = ((cx + bw / 2) - pad_x) / scale
            bottom = ((cy + bh / 2) - pad_y) / scale
            cl, ct = max(0.0, left), max(0.0, top)
            cr, cb = min(float(w), right), min(float(h), bottom)
            if cr - cl < 2 or cb - ct < 2:
                continue
            dets.append(((cl + cr) / 2, (ct + cb) / 2, cr - cl, cb - ct, best_score))
        return nms(dets, NMS_IOU)


def nms(dets, iou_threshold):
    remaining = sorted(dets, key=lambda d: -d[4])
    kept = []
    while remaining:
        best = remaining.pop(0)
        kept.append(best)
        rest = []
        for cand in remaining:
            left = max(best[0] - best[2] / 2, cand[0] - cand[2] / 2)
            top = max(best[1] - best[3] / 2, cand[1] - cand[3] / 2)
            right = min(best[0] + best[2] / 2, cand[0] + cand[2] / 2)
            bottom = min(best[1] + best[3] / 2, cand[1] + cand[3] / 2)
            inter = max(0.0, right - left) * max(0.0, bottom - top)
            union = best[2] * best[3] + cand[2] * cand[3] - inter
            if not (union > 0 and inter / union > iou_threshold):
                rest.append(cand)
        remaining = rest
    return kept


def track(video, detector):
    capture = cv2.VideoCapture(video)
    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    observations = []
    previous = None
    x_ref = None
    ref_axis = 0.0
    index = 0
    while True:
        ok, frame = capture.read()
        if not ok:
            break
        t = index / fps
        index += 1
        dets = detector.detect(frame)
        if not dets:
            previous = None
            continue
        chosen = None
        if previous is not None:
            lateral = ref_axis * DETECTION_LATERAL_DIAMETERS
            vertical = ref_axis * DETECTION_VERTICAL_DIAMETERS
            pool = [d for d in dets
                    if abs(d[0] - x_ref) <= lateral and abs(d[1] - previous[1]) <= vertical]
            if pool:
                chosen = min(pool, key=lambda d: np.hypot(d[0] - previous[0], d[1] - previous[1]))
        else:
            chosen = max(dets, key=lambda d: d[2] * d[3])
        if chosen is None:
            previous = None
            continue
        if previous is None:
            x_ref, ref_axis = chosen[0], chosen[3]
        observations.append((t, chosen[0], chosen[1], chosen[3], chosen[4]))
        previous = chosen
    capture.release()
    return fps, observations


def sample_window(times, window_sec, min_samples=3):
    if len(times) <= min_samples:
        return len(times)
    deltas = np.diff(times)
    deltas = deltas[deltas > 0]
    if deltas.size == 0:
        return min_samples
    median = float(np.median(deltas))
    count = max(min_samples, int(round(window_sec / median)))
    return count + 1 if count % 2 == 0 else count


def local_poly(times, values, center, window, order):
    """Port of core's LocalPolynomial.fit: normalize, solve normal equations, return value+slope."""
    n = len(times)
    start = max(0, center - window // 2)
    start = min(start, max(0, n - min(window, n)))
    end = min(start + window, n)
    count = end - start
    degree = min(order, count - 1)
    scale = float(np.max(np.abs(times[start:end] - times[center])))
    if scale == 0 or not np.isfinite(scale):
        return float(values[center]), 0.0
    u = (times[start:end] - times[center]) / scale
    v = values[start:end]
    a = np.vander(u, degree + 1, increasing=True)
    coeff, *_ = np.linalg.lstsq(a, v, rcond=None)
    return float(coeff[0]), float(coeff[1] / scale)


def smooth_series(times, values, window_sec):
    window = sample_window(times, window_sec)
    out = np.empty_like(np.asarray(values, dtype=float))
    for i in range(len(values)):
        out[i], _ = local_poly(times, np.asarray(values, dtype=float), i, window, POLY_ORDER)
    return out


def derive(times, values, window_sec):
    window = sample_window(times, window_sec)
    val = np.empty(len(values))
    vel = np.empty(len(values))
    for i in range(len(values)):
        val[i], vel[i] = local_poly(times, np.asarray(values, dtype=float), i, window, POLY_ORDER)
    return val, vel


def segment(t, y, vy):
    exit_eps = V_EPS * EXIT_EPS_FRACTION
    runs, i, n = [], 0, len(vy)
    while i < n:
        if vy[i] <= V_EPS:
            i += 1
            continue
        a = i
        while i < n and vy[i] > exit_eps:
            i += 1
        runs.append([a, min(i, n - 1)])
    merged = [runs[0][:]] if runs else []
    for a, b in runs[1:]:
        pa, pb = merged[-1]
        gap = t[a] - t[pb]
        drop = y[pb] - float(np.min(y[pb:a + 1]))
        if gap <= GAP_MERGE_SEC and drop < MIN_ROM_M * GAP_MERGE_ROM_FRACTION:
            merged[-1] = [pa, b]
        else:
            merged.append([a, b])

    reps = []
    for idx, (a, b) in enumerate(merged):
        bottom = a
        j = a - 1
        floor = merged[idx - 1][1] + 1 if idx > 0 else 0
        while j >= floor and t[a] - t[j] <= BOTTOM_LOOKBACK_SEC:
            if y[j] <= y[bottom]:
                bottom = j
            j -= 1
        top_limit = merged[idx + 1][0] - 1 if idx + 1 < len(merged) else n - 1
        end_i = min(b + 1, n - 1)
        for k in range(end_i, min(top_limit, n - 1) + 1):
            if y[k] > y[end_i]:
                end_i = k
        start, end = t[bottom], t[end_i]
        rom = float(np.max(y[bottom:end_i + 1]) - np.min(y[bottom:end_i + 1]))
        if end - start < MIN_REP_SEC or end - t[a] < MIN_CONCENTRIC_SEC or rom < MIN_ROM_M:
            continue
        reps.append({"start": start, "end": end, "rom": rom})

    if len(reps) >= ROM_OUTLIER_MIN_REPS:
        roms = sorted(r["rom"] for r in reps)
        mid = len(roms) // 2
        median = roms[mid] if len(roms) % 2 else (roms[mid - 1] + roms[mid]) / 2
        threshold = max(MIN_ROM_M, median * ROM_OUTLIER_MEDIAN_FRACTION)
        reps = [r for r in reps if r["rom"] >= threshold]
    return reps


def main(video, plate_m=0.45, angle=45.0):
    detector = Detector()
    fps, obs = track(video, detector)
    print(f"observations={len(obs)} fps={fps:.2f}")
    if len(obs) < 2:
        return
    t = np.array([o[0] for o in obs])
    x = np.array([o[1] for o in obs])
    y = np.array([o[2] for o in obs])
    axis = np.array([o[3] for o in obs])

    x_s = smooth_series(t, x, TRACK_SMOOTH_WINDOW_SEC)
    y_s = smooth_series(t, y, TRACK_SMOOTH_WINDOW_SEC)

    first = t[0]
    initial = axis[t <= first + INITIAL_DIAMETER_SEC + 1e-9]
    diameter_px = float(np.median(initial)) if len(initial) >= MIN_INITIAL_DIAMETER_SAMPLES else float(axis[0])
    px_per_m = diameter_px / plate_m
    y_m = -y_s / px_per_m

    y_fit, vy = derive(t, y_m, MEAN_WINDOW_SEC)
    reps = segment(t, y_fit, vy)
    print(f"diameter={diameter_px:.1f}px reps={len(reps)}")
    for i, rep in enumerate(reps, 1):
        dur = rep["end"] - rep["start"]
        print(f"  #{i} {rep['start']:.2f}-{rep['end']:.2f} rom={rep['rom']*100:.1f}cm "
              f"mcv={rep['rom']/dur if dur > 0 else 0:.3f}")


if __name__ == "__main__":
    main(sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 0.45,
         float(sys.argv[3]) if len(sys.argv) > 3 else 45.0)
