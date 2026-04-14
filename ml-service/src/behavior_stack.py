import logging
from pathlib import Path
from typing import Any, Dict

import numpy as np

from utils import load_config

try:
    import onnxruntime as ort  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    ort = None


class BehaviorStack:
    def __init__(self, config_path: str):
        self.config = load_config(config_path)
        self.behavior_config = self.config.get("behavior", {})
        self.thresholds = self.config.get("modality_thresholds", {})

        self.behavior_min_score = float(self.thresholds.get("behaviorMin", 0.45))
        self.model_path = self.behavior_config.get("onnx_model_path", "")
        self.feature_clip = float(self.behavior_config.get("feature_clip", 3.0))

        self._backend = "rules"
        self._session = None
        self._load_backend()

    def model_info(self) -> Dict[str, str]:
        return {
            "behavior": f"{self.behavior_config.get('model_name', 'behavior-risk-v1')}:{self._backend}",
        }

    def readiness_status(self, require_onnx: bool = False) -> Dict[str, Any]:
        onnx_ready = self._session is not None and self._backend == "onnx"
        degraded = not onnx_ready
        ready = onnx_ready if require_onnx else True

        reason_codes = []
        if not onnx_ready:
            reason_codes.append("BEHAVIOR_MODEL_NOT_LOADED")

        return {
            "ready": ready,
            "degraded": degraded,
            "strictMode": require_onnx,
            "onnxReady": onnx_ready,
            "backend": self._backend,
            "reasonCodes": reason_codes,
        }

    def evaluate_behavior(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if not isinstance(payload, dict) or not payload:
            return {
                "success": False,
                "behaviorScore": 0.0,
                "behaviorRisk": 1.0,
                "reasonCodes": ["BEHAVIOR_DATA_MISSING"],
                "features": {},
            }

        features = self._extract_features(payload)
        if self._session is not None:
            score = self._predict_with_onnx(features)
        else:
            score = self._predict_with_rules(features)

        score = float(np.clip(score, 0.0, 1.0))
        risk = 1.0 - score

        reason_codes = []
        if score < self.behavior_min_score:
            reason_codes.append("BEHAVIOR_SCORE_LOW")
        if features["switchRisk"] > 0.65:
            reason_codes.append("BEHAVIOR_WINDOW_SWITCH_HIGH")
        if features["typingAnomaly"] > 0.70:
            reason_codes.append("BEHAVIOR_TYPING_PATTERN_ANOMALOUS")
        if features["mouseAnomaly"] > 0.70:
            reason_codes.append("BEHAVIOR_MOUSE_PATTERN_ANOMALOUS")
        if features["focusRisk"] > 0.60:
            reason_codes.append("BEHAVIOR_FOCUS_LOSS_HIGH")

        return {
            "success": True,
            "behaviorScore": score,
            "behaviorRisk": float(np.clip(risk, 0.0, 1.0)),
            "reasonCodes": reason_codes,
            "features": features,
        }

    def _load_backend(self) -> None:
        if not self.model_path:
            return

        model_file = Path(self.model_path)
        if ort is None or not model_file.exists():
            return

        try:
            self._session = ort.InferenceSession(str(model_file), providers=["CPUExecutionProvider"])
            self._backend = "onnx"
        except Exception as exc:
            logging.warning("Behavior ONNX model unavailable, using rules backend: %s", exc)
            self._session = None
            self._backend = "rules"

    def _extract_features(self, payload: Dict[str, Any]) -> Dict[str, float]:
        avg_dwell = self._num(payload, ["avgDwellMs", "typing", "avgDwellMs"], 120.0)
        avg_flight = self._num(payload, ["avgFlightMs", "typing", "avgFlightMs"], 90.0)
        backspace_rate = self._num(payload, ["backspaceRate", "typing", "backspaceRate"], 0.08)
        window_switches = self._num(payload, ["windowSwitches", "focus", "windowSwitches"], 0.0)
        tab_switches = self._num(payload, ["tabSwitches", "focus", "tabSwitches"], 0.0)
        focus_loss = self._num(payload, ["focusLossCount", "focus", "focusLossCount"], 0.0)
        idle_ratio = self._num(payload, ["idleRatio", "focus", "idleRatio"], 0.0)
        copy_events = self._num(payload, ["copyPasteEvents", "clipboard", "copyPasteEvents"], 0.0)
        mouse_speed = self._num(payload, ["mouseSpeed", "mouse", "meanSpeed"], 0.5)
        mouse_jerk = self._num(payload, ["mouseJerk", "mouse", "jerkScore"], 0.5)

        dwell_z = self._zscore(avg_dwell, 120.0, 35.0)
        flight_z = self._zscore(avg_flight, 90.0, 30.0)
        typing_anomaly = float(np.clip((abs(dwell_z) + abs(flight_z)) / 4.0 + backspace_rate, 0.0, 1.0))

        switch_risk = float(np.clip((window_switches / 8.0) * 0.6 + (tab_switches / 10.0) * 0.4, 0.0, 1.0))
        focus_risk = float(np.clip((focus_loss / 6.0) * 0.6 + idle_ratio * 0.4, 0.0, 1.0))
        clipboard_risk = float(np.clip(copy_events / 5.0, 0.0, 1.0))
        mouse_anomaly = float(np.clip(abs(mouse_speed - 0.5) + abs(mouse_jerk - 0.5), 0.0, 1.0))

        return {
            "typingAnomaly": typing_anomaly,
            "switchRisk": switch_risk,
            "focusRisk": focus_risk,
            "clipboardRisk": clipboard_risk,
            "mouseAnomaly": mouse_anomaly,
            "dwellZ": dwell_z,
            "flightZ": flight_z,
        }

    def _predict_with_rules(self, features: Dict[str, float]) -> float:
        risk = (
            0.30 * features["typingAnomaly"]
            + 0.25 * features["switchRisk"]
            + 0.20 * features["focusRisk"]
            + 0.15 * features["clipboardRisk"]
            + 0.10 * features["mouseAnomaly"]
        )
        return float(np.clip(1.0 - risk, 0.0, 1.0))

    def _predict_with_onnx(self, features: Dict[str, float]) -> float:
        if self._session is None:
            return self._predict_with_rules(features)

        vector = np.array([
            features["typingAnomaly"],
            features["switchRisk"],
            features["focusRisk"],
            features["clipboardRisk"],
            features["mouseAnomaly"],
            features["dwellZ"],
            features["flightZ"],
        ], dtype=np.float32)
        vector = np.clip(vector, -self.feature_clip, self.feature_clip)
        vector = vector.reshape(1, -1)

        try:
            input_name = self._session.get_inputs()[0].name
            output = self._session.run(None, {input_name: vector})
            raw = float(np.mean(output[0]))
            return float(1.0 / (1.0 + np.exp(-raw)))
        except Exception as exc:
            logging.warning("Behavior ONNX inference failed, using rules: %s", exc)
            return self._predict_with_rules(features)

    def _num(self, payload: Dict[str, Any], keys, default: float) -> float:
        if not payload:
            return default

        direct = keys[0]
        value = payload.get(direct)
        if isinstance(value, (int, float)):
            return float(value)

        nested = payload.get(keys[1])
        if isinstance(nested, dict):
            nested_value = nested.get(keys[2])
            if isinstance(nested_value, (int, float)):
                return float(nested_value)

        return default

    def _zscore(self, value: float, mean: float, std: float) -> float:
        return float(np.clip((value - mean) / max(1e-6, std), -self.feature_clip, self.feature_clip))
