"""SetExport 风格 JSON 报告写入。"""

import json
from pathlib import Path


def write_report(result, destination):
    path = Path(destination)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    return path
