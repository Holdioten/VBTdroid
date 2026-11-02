"""通过二值轮廓拟合和逐帧邻近匹配跟踪最大圆形目标。"""

import cv2


def _circles(frame):
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    _, mask = cv2.threshold(gray, 200, 255, cv2.THRESH_BINARY)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    found = []
    for contour in contours:
        if len(contour) < 5 or cv2.contourArea(contour) <= 0:
            continue
        ellipse = cv2.fitEllipse(contour)
        (x, y), (axis_a, axis_b), _ = ellipse
        major = max(axis_a, axis_b)
        minor = min(axis_a, axis_b)
        if major > 0 and minor / major >= 0.7:
            found.append((float(x), float(y), float(major), cv2.contourArea(contour)))
    return found


def track_video(video, fps):
    capture = cv2.VideoCapture(str(video))
    if not capture.isOpened():
        raise ValueError(f"无法打开视频：{video}")
    fps = float(fps) if fps and fps > 0 else float(capture.get(cv2.CAP_PROP_FPS))
    if fps <= 0:
        capture.release()
        raise ValueError("视频帧率必须为正数")
    observations = []
    warnings = []
    previous = None
    major_axis_px = None
    frame_index = 0
    while True:
        ok, frame = capture.read()
        if not ok:
            break
        candidates = _circles(frame)
        if candidates:
            selected = max(candidates, key=lambda circle: circle[3]) if previous is None else min(
                candidates, key=lambda circle: (circle[0] - previous[0]) ** 2 + (circle[1] - previous[1]) ** 2
            )
            x, y, major_axis, _ = selected
            if major_axis_px is None:
                major_axis_px = major_axis
            observations.append({
                "t_sec": frame_index / fps,
                "x_px": x,
                "y_px": y,
                "major_axis_px": major_axis,
                "confidence": 1.0,
            })
            previous = (x, y)
        else:
            warnings.append(f"第 {frame_index} 帧未检测到目标")
        frame_index += 1
    capture.release()
    if len(observations) < 2:
        return [], major_axis_px or 0.0, warnings
    return observations, major_axis_px, warnings
