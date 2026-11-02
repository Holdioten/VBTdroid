"""命令行视频分析入口。"""

import argparse

from . import analyze_video
from .report import write_report


def main():
    parser = argparse.ArgumentParser(description="分析杠铃片运动视频")
    parser.add_argument("video")
    parser.add_argument("--plate-diameter-m", type=float, required=True)
    parser.add_argument("--camera-angle-deg", type=float, required=True)
    parser.add_argument("--fps", type=float, required=True)
    parser.add_argument("--lift", choices=("squat", "bench", "deadlift"), default="deadlift")
    parser.add_argument("--load-kg", type=float)
    parser.add_argument("--output", default="report.json")
    args = parser.parse_args()
    result = analyze_video(
        args.video, args.plate_diameter_m, args.camera_angle_deg, args.fps,
        lift=args.lift, load_kg=args.load_kg,
    )
    write_report(result, args.output)


if __name__ == "__main__":
    main()
