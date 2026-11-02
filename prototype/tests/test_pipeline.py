import cv2
import numpy as np

from vbtdroid_proto import analyze_video


def write_synthetic_video(path, frames=60, radius=45, y0=300, y1=200):
    writer = cv2.VideoWriter(
        str(path), cv2.VideoWriter_fourcc(*"mp4v"), 30, (640, 480)
    )
    assert writer.isOpened()
    # 后半段停在锁定位置，提供可识别的锁定相位。
    movement_frames = max(2, frames // 2)
    for index in range(frames):
        frame = np.zeros((480, 640, 3), dtype=np.uint8)
        center_y = round(y0 + (y1 - y0) * min(index, movement_frames - 1) / (movement_frames - 1))
        cv2.circle(frame, (320, center_y), radius, (255, 255, 255), -1)
        writer.write(frame)
    writer.release()


def test_synthetic_circle_video(tmp_path):
    video = tmp_path / "squat.mp4"
    write_synthetic_video(video, frames=60, radius=45, y0=300, y1=200)
    result = analyze_video(
        video, plate_diameter_m=0.45, camera_angle_deg=90, fps=30
    )
    assert result["reps"]
    assert result["reps"][0]["metrics"]["mcvMps"] > 0.3
    assert result["warnings"] == []
