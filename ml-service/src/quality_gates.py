from typing import Any, Dict, List

import cv2
import numpy as np


def _clamp_01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def evaluate_face_quality(
    image: np.ndarray,
    face_bbox: List[int],
    face_count: int,
    config: Dict[str, Any],
) -> Dict[str, Any]:
    gates_cfg = config.get("quality_gates", {})

    min_size_cfg = gates_cfg.get("min_face_size", config.get("min_face_size", [80, 80]))
    min_face_w, min_face_h = (min_size_cfg if isinstance(min_size_cfg, list) else [80, 80])

    brightness_min = float(gates_cfg.get("brightness_min", 40.0))
    brightness_max = float(gates_cfg.get("brightness_max", 220.0))
    blur_threshold = float(gates_cfg.get("blur_threshold", config.get("min_blur_score", 100)))
    require_single_face = bool(gates_cfg.get("require_single_face", True))

    x1, y1, x2, y2 = [int(v) for v in face_bbox]
    x1 = max(0, x1)
    y1 = max(0, y1)
    x2 = min(image.shape[1], x2)
    y2 = min(image.shape[0], y2)

    face_roi = image[y1:y2, x1:x2]

    face_w = max(0, x2 - x1)
    face_h = max(0, y2 - y1)
    size_pass = face_w >= int(min_face_w) and face_h >= int(min_face_h)

    if face_roi is None or face_roi.size == 0:
        return {
            "passed": False,
            "quality_score": 0.0,
            "failure_reasons": ["INVALID_FACE_ROI"],
            "metrics": {
                "face_count": face_count,
                "face_size": [face_w, face_h],
                "brightness": 0.0,
                "blur_score": 0.0,
            },
        }

    gray = cv2.cvtColor(face_roi, cv2.COLOR_BGR2GRAY)
    brightness = float(np.mean(gray))
    blur_score = float(cv2.Laplacian(gray, cv2.CV_64F).var())

    brightness_pass = brightness_min <= brightness <= brightness_max
    blur_pass = blur_score >= blur_threshold
    single_face_pass = (face_count == 1) if require_single_face else True

    failure_reasons: List[str] = []
    if not single_face_pass:
        failure_reasons.append("SINGLE_FACE_REQUIRED")
    if not size_pass:
        failure_reasons.append("FACE_TOO_SMALL")
    if not brightness_pass:
        failure_reasons.append("BRIGHTNESS_OUT_OF_RANGE")
    if not blur_pass:
        failure_reasons.append("IMAGE_TOO_BLURRY")

    size_score = _clamp_01(min(face_w / max(1.0, float(min_face_w)), face_h / max(1.0, float(min_face_h))))

    if brightness_min <= brightness <= brightness_max:
        brightness_score = 1.0
    elif brightness < brightness_min:
        brightness_score = _clamp_01(brightness / max(1.0, brightness_min))
    else:
        brightness_score = _clamp_01(brightness_max / max(1.0, brightness))

    blur_score_norm = _clamp_01(blur_score / max(1.0, blur_threshold))
    single_score = 1.0 if single_face_pass else 0.0

    quality_score = _clamp_01(
        0.35 * size_score
        + 0.25 * brightness_score
        + 0.30 * blur_score_norm
        + 0.10 * single_score
    )

    return {
        "passed": len(failure_reasons) == 0,
        "quality_score": quality_score,
        "failure_reasons": failure_reasons,
        "metrics": {
            "face_count": face_count,
            "face_size": [face_w, face_h],
            "brightness": brightness,
            "blur_score": blur_score,
            "thresholds": {
                "min_face_size": [min_face_w, min_face_h],
                "brightness_min": brightness_min,
                "brightness_max": brightness_max,
                "blur_threshold": blur_threshold,
                "require_single_face": require_single_face,
            },
        },
    }
