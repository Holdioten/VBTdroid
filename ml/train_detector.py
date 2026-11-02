#!/usr/bin/env python3
"""准备单类别 YOLO 数据集，并可选微调、导出检测器。

推荐发布方案：使用上游 Apache-2.0 代码训练 YOLOX-Nano 或 NanoDet-Plus，
并自行确认导出张量符合应用适配器要求。下方个人试验备选使用 Ultralytics
YOLOv8n，许可为 AGPL-3.0；未完成许可审查前，不得随应用商业分发。

数据集目录：images/{train,val} 与 labels/{train,val}；仅包含类别 0（杠铃片），
标注为标准 YOLO txt 格式的归一化坐标。
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


def validate_split(root: Path, split: str) -> int:
    images = root / "images" / split
    labels = root / "labels" / split
    if not images.is_dir() or not labels.is_dir():
        raise SystemExit(f"Expected {images} and {labels}")
    image_files = sorted(
        path for path in images.iterdir()
        if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
    )
    if not image_files:
        raise SystemExit(f"No images found in {images}")
    for image in image_files:
        label = labels / f"{image.stem}.txt"
        if not label.is_file():
            raise SystemExit(f"Missing annotation: {label}")
        for line_number, line in enumerate(label.read_text().splitlines(), start=1):
            fields = line.split()
            if len(fields) != 5 or fields[0] != "0":
                raise SystemExit(f"{label}:{line_number}: expected one class-0 YOLO box")
            try:
                values = [float(value) for value in fields[1:]]
            except ValueError as error:
                raise SystemExit(f"{label}:{line_number}: invalid numeric annotation") from error
            if any(not 0.0 <= value <= 1.0 for value in values) or values[2] <= 0 or values[3] <= 0:
                raise SystemExit(f"{label}:{line_number}: box values must be normalized to [0,1]")
    return len(image_files)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path, help="Dataset root with images/ and labels/")
    parser.add_argument("--output", type=Path, default=Path("ml/generated/plate-data.yaml"))
    parser.add_argument("--train", action="store_true", help="Run personal-only YOLOv8n train and TFLite export")
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--imgsz", type=int, choices=(384, 640), default=640)
    args = parser.parse_args()

    root = args.dataset.resolve()
    counts = {split: validate_split(root, split) for split in ("train", "val")}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        f"path: {root}\ntrain: images/train\nval: images/val\nnames:\n  0: plate\n",
        encoding="utf-8",
    )
    print(f"Validated dataset: {counts['train']} train, {counts['val']} val images")
    print(f"Wrote {args.output}")
    if not args.train:
        print("Dataset preparation only. Use Apache-2.0 YOLOX-Nano/NanoDet-Plus for distributable model work.")
        return

    print("WARNING: Ultralytics YOLOv8 is AGPL-3.0; personal-MVP fallback only, not a default shipping license.")
    subprocess.run(
        ["yolo", "detect", "train", "model=yolov8n.pt", f"data={args.output}",
         f"epochs={args.epochs}", f"imgsz={args.imgsz}", "single_cls=True"],
        check=True,
    )
    best = Path("runs/detect/train/weights/best.pt")
    subprocess.run(
        ["yolo", "export", f"model={best}", "format=tflite", f"imgsz={args.imgsz}", "nms=False"],
        check=True,
    )
    print("Inspect exported tensor shapes; runtime expects YOLO output [1,5,N] (cx,cy,w,h,score).")


if __name__ == "__main__":
    main()
