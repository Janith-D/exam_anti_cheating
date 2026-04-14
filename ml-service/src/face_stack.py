import logging
import importlib
from typing import Any, Dict, List, Tuple

import cv2
import numpy as np

from utils import compute_embedding


LOGGER = logging.getLogger(__name__)


class FaceStack:
    """Pluggable face detector/embedder stack.

    Priority:
    1. InsightFace stack (SCRFD detector + ArcFace embedding) when available.
    2. OpenCV Haar + local fallback embedding.
    """

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.face_size = tuple(config.get("face_size", [224, 224]))
        min_face_size = config.get("min_face_size", [80, 80])
        self.min_face_size = tuple(min_face_size) if isinstance(min_face_size, list) else (80, 80)

        self.mode = "fallback"
        self.detector_name = "opencv-haar"
        self.embedder_name = "fallback-local"
        self._insight_app = None

        self.haar = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")

        if config.get("enable_insightface", False):
            self._init_insightface()

    def _init_insightface(self) -> None:
        try:
            insightface_app = importlib.import_module("insightface.app")
            FaceAnalysis = getattr(insightface_app, "FaceAnalysis")

            model_name = self.config.get("insightface_model_name", "buffalo_l")
            providers = ["CPUExecutionProvider"]
            if self.config.get("gpu_enabled", False):
                providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]

            self._insight_app = FaceAnalysis(name=model_name, providers=providers)
            image_size = tuple(self.config.get("image_size", [640, 480]))
            self._insight_app.prepare(ctx_id=0 if self.config.get("gpu_enabled", False) else -1, det_size=image_size)

            self.mode = "insightface"
            self.detector_name = "scrfd"
            self.embedder_name = "arcface"
            LOGGER.info("InsightFace stack initialized (SCRFD + ArcFace)")
        except Exception as ex:
            LOGGER.warning("InsightFace not available; using fallback face stack: %s", ex)
            self._insight_app = None

    def detect_faces(self, image: np.ndarray) -> List[Dict[str, Any]]:
        if image is None or image.size == 0:
            return []

        if self.mode == "insightface" and self._insight_app is not None:
            faces = self._insight_app.get(image)
            parsed: List[Dict[str, Any]] = []
            for face in faces:
                bbox = face.bbox.astype(int).tolist()
                parsed.append({
                    "bbox": bbox,
                    "embedding": getattr(face, "normed_embedding", None),
                })
            return parsed

        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        boxes = self.haar.detectMultiScale(
            gray,
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=self.min_face_size,
        )

        parsed = []
        for (x, y, w, h) in boxes:
            parsed.append({"bbox": [int(x), int(y), int(x + w), int(y + h)], "embedding": None})
        return parsed

    def extract_primary_face(self, image: np.ndarray) -> Dict[str, Any]:
        faces = self.detect_faces(image)
        if not faces:
            return {"success": False, "error": "NO_FACE_DETECTED", "face_count": 0}

        def area(item: Dict[str, Any]) -> int:
            x1, y1, x2, y2 = item["bbox"]
            return max(0, x2 - x1) * max(0, y2 - y1)

        primary = sorted(faces, key=area, reverse=True)[0]
        x1, y1, x2, y2 = primary["bbox"]
        x1 = max(0, x1)
        y1 = max(0, y1)
        x2 = min(image.shape[1], x2)
        y2 = min(image.shape[0], y2)

        crop = image[y1:y2, x1:x2]
        if crop is None or crop.size == 0:
            return {
                "success": False,
                "error": "INVALID_FACE_CROP",
                "face_count": len(faces),
            }

        embedding = primary.get("embedding")
        if embedding is None:
            face_resized = cv2.resize(crop, self.face_size)
            embedding = compute_embedding(face_resized)

        if embedding is None:
            return {
                "success": False,
                "error": "EMBEDDING_FAILED",
                "face_count": len(faces),
                "bbox": [x1, y1, x2, y2],
            }

        return {
            "success": True,
            "face_count": len(faces),
            "bbox": [x1, y1, x2, y2],
            "face_crop": crop,
            "embedding": np.asarray(embedding, dtype=np.float32),
            "detector": self.detector_name,
            "embedder": self.embedder_name,
        }

    def model_info(self) -> Dict[str, str]:
        return {
            "mode": self.mode,
            "detector": self.detector_name,
            "embedder": self.embedder_name,
        }
