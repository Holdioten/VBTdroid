#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_DIR="$ROOT/app/src/main/assets/models"
LOCK_FILE="$ROOT/ml/models.lock"
POSE_PATH="app/src/main/assets/models/pose_landmarker_lite.task"
POSE_URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"

mkdir -p "$ASSET_DIR"

if ! grep -Eq "[[:space:]]${POSE_PATH//\//\\/}$" "$LOCK_FILE"; then
    echo "Missing trusted SHA256 entry for $POSE_PATH in ml/models.lock" >&2
    exit 1
fi
curl --fail --location --retry 2 "$POSE_URL" -o "$ROOT/$POSE_PATH"

if [[ -n "${PLATE_MODEL_URL:-}" ]]; then
    PLATE_PATH="app/src/main/assets/models/plate.tflite"
    if ! grep -Eq "[[:space:]]${PLATE_PATH//\//\\/}$" "$LOCK_FILE"; then
        echo "PLATE_MODEL_URL was set, but no trusted SHA256 entry exists for $PLATE_PATH in ml/models.lock." >&2
        echo "Add the verified model checksum to the lock file before downloading." >&2
        exit 1
    fi
    curl --fail --location --retry 2 "$PLATE_MODEL_URL" -o "$ROOT/$PLATE_PATH"
else
    echo "No plate detector downloaded. To enable, add a vetted plate.tflite SHA256 to ml/models.lock and set PLATE_MODEL_URL." >&2
fi

(cd "$ROOT" && sha256sum --check ml/models.lock)
