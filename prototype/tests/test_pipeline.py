import subprocess
import sys

import cv2
import numpy as np

from vbtdroid_proto import analyze_video


def write_synthetic_video(path, frames=75, radius=45):
    writer = cv2.VideoWriter(
        str(path), cv2.VideoWriter_fourcc(*"mp4v"), 30, (640, 480)
    )
    assert writer.isOpened()
    for index in range(frames):
        frame = np.zeros((480, 640, 3), dtype=np.uint8)
        if index < 30:
            center_y = round(250 + 100 * index / 29)
        elif index < 48:
            center_y = round(350 - 100 * (index - 30) / 17)
        else:
            center_y = 250
        cv2.circle(frame, (320, center_y), radius, (255, 255, 255), -1)
        writer.write(frame)
    writer.release()


def test_synthetic_circle_video(tmp_path):
    video = tmp_path / "squat.mp4"
    write_synthetic_video(video)
    result = analyze_video(
        video, plate_diameter_m=0.45, camera_angle_deg=90, fps=30, lift="SQUAT"
    )
    assert len(result["reps"]) == 1
    assert 0.7 <= result["reps"][0]["metrics"]["mcv_mps"] <= 0.9
    assert 0.49 <= result["reps"][0]["metrics"]["rom_concentric_m"] <= 0.51
    assert {phase["type"] for phase in result["reps"][0]["phases"]} == {
        "ECCENTRIC", "CONCENTRIC", "LOCKOUT"
    }
    assert result["warnings"] == []

    loaded = analyze_video(
        video, plate_diameter_m=0.45, camera_angle_deg=90, fps=30,
        lift="squat", load_kg=100,
    )
    assert loaded["reps"][0]["metrics"]["mean_force_n"] is not None
    assert loaded["reps"][0]["metrics"]["mean_power_w"] is not None


def test_script_help_runs_standalone():
    result = subprocess.run(
        [sys.executable, "scripts/analyze_video.py", "--help"],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr
    assert "plate-diameter-m" in result.stdout
