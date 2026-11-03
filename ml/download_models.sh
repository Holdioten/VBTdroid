#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_DIR="$ROOT/app/src/main/assets/models"
LOCK_FILE="$ROOT/ml/models.lock"
POSE_PATH="app/src/main/assets/models/pose_landmarker_lite.task"
POSE_URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
# EfficientDet-Lite0 plate detector from simonkosina/vbt (MIT License).
PLATE_PATH="app/src/main/assets/models/plate.tflite"
PLATE_URL="${PLATE_MODEL_URL:-https://raw.githubusercontent.com/simonkosina/vbt/main/models/efficientdet_lite0.tflite}"

mkdir -p "$ASSET_DIR"

declare -a TEMP_FILES=()
declare -a DESTINATIONS=()
cleanup() {
    for temp_file in "${TEMP_FILES[@]}"; do
        rm -f -- "$temp_file"
    done
}
trap cleanup EXIT

download_and_verify() {
    local relative_path="$1"
    local url="$2"
    local asset_name
    asset_name="$(basename "$relative_path")"
    local expected_hash
    expected_hash="$(awk -v target="$relative_path" '$2 == target { print $1; exit }' "$LOCK_FILE")"
    if [[ ! "$expected_hash" =~ ^[[:xdigit:]]{64}$ ]]; then
        echo "Missing trusted SHA256 entry for $relative_path in ml/models.lock" >&2
        return 1
    fi

    local temp_file
    temp_file="$(mktemp "$ASSET_DIR/.${asset_name}.XXXXXX")"
    TEMP_FILES+=("$temp_file")
    curl --fail --location --retry 2 "$url" -o "$temp_file"
    local actual_hash
    actual_hash="$(sha256sum "$temp_file" | awk '{ print $1 }')"
    if [[ "$actual_hash" != "$expected_hash" ]]; then
        echo "SHA256 mismatch for $relative_path: expected $expected_hash, got $actual_hash" >&2
        return 1
    fi
    DESTINATIONS+=("$ROOT/$relative_path")
}

download_and_verify "$POSE_PATH" "$POSE_URL"
download_and_verify "$PLATE_PATH" "$PLATE_URL"

for index in "${!TEMP_FILES[@]}"; do
    mv -f -- "${TEMP_FILES[$index]}" "${DESTINATIONS[$index]}"
done
