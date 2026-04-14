import re
from difflib import SequenceMatcher
from typing import Any, Dict, List

import numpy as np

from utils import load_config


class ChallengeStack:
    def __init__(self, config_path: str):
        self.config = load_config(config_path)
        self.challenge_config = self.config.get("challenge", {})
        self.thresholds = self.config.get("modality_thresholds", {})
        self.challenge_min = float(self.thresholds.get("challengeMin", 0.55))

    def model_info(self) -> Dict[str, str]:
        return {
            "challenge": self.challenge_config.get("model_name", "challenge-rules-v1"),
        }

    def evaluate_challenge(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if not isinstance(payload, dict) or not payload:
            return {
                "success": False,
                "challengeScore": 0.0,
                "reasonCodes": ["CHALLENGE_DATA_MISSING"],
                "details": {},
            }

        transcript = self._normalize_text(payload.get("transcript", ""))
        expected_phrase = self._normalize_text(payload.get("expectedPhrase", ""))
        expected_keywords = payload.get("expectedKeywords", [])
        duration_ms = self._to_float(payload.get("responseDurationMs"), 0.0)
        max_duration_ms = self._to_float(payload.get("maxDurationMs"), 8000.0)
        asr_confidence = float(np.clip(self._to_float(payload.get("asrConfidence"), 0.7), 0.0, 1.0))

        lexical_score = self._lexical_score(transcript, expected_phrase, expected_keywords)
        timing_score = self._timing_score(duration_ms, max_duration_ms)
        presence_score = 1.0 if transcript else 0.0

        challenge_score = float(np.clip(
            0.45 * lexical_score + 0.30 * timing_score + 0.15 * asr_confidence + 0.10 * presence_score,
            0.0,
            1.0,
        ))

        reason_codes: List[str] = []
        if not transcript:
            reason_codes.append("CHALLENGE_TRANSCRIPT_MISSING")
        if lexical_score < 0.50:
            reason_codes.append("CHALLENGE_CONTENT_MISMATCH")
        if timing_score < 0.50:
            reason_codes.append("CHALLENGE_RESPONSE_TIMEOUT")
        if asr_confidence < 0.40:
            reason_codes.append("CHALLENGE_ASR_CONFIDENCE_LOW")
        if challenge_score < self.challenge_min:
            reason_codes.append("CHALLENGE_SCORE_LOW")

        return {
            "success": challenge_score >= self.challenge_min,
            "challengeScore": challenge_score,
            "reasonCodes": reason_codes,
            "details": {
                "lexicalScore": lexical_score,
                "timingScore": timing_score,
                "asrConfidence": asr_confidence,
                "durationMs": duration_ms,
                "maxDurationMs": max_duration_ms,
            },
        }

    def _lexical_score(self, transcript: str, expected_phrase: str, expected_keywords: Any) -> float:
        if not transcript:
            return 0.0

        transcript_tokens = set(self._tokens(transcript))
        if expected_phrase:
            phrase_similarity = SequenceMatcher(None, transcript, expected_phrase).ratio()
        else:
            phrase_similarity = 0.0

        keyword_hit_ratio = 0.0
        if isinstance(expected_keywords, list) and expected_keywords:
            expected = [self._normalize_text(str(item)) for item in expected_keywords if str(item).strip()]
            if expected:
                hits = sum(1 for token in expected if token in transcript_tokens)
                keyword_hit_ratio = hits / len(expected)

        # If explicit phrase/keywords are missing, measure transcript structure quality.
        if not expected_phrase and keyword_hit_ratio == 0.0:
            token_count = len(transcript_tokens)
            return float(np.clip(token_count / 8.0, 0.0, 1.0))

        return float(np.clip(0.65 * phrase_similarity + 0.35 * keyword_hit_ratio, 0.0, 1.0))

    def _timing_score(self, duration_ms: float, max_duration_ms: float) -> float:
        if duration_ms <= 0:
            return 0.0
        if max_duration_ms <= 0:
            max_duration_ms = 8000.0

        ratio = duration_ms / max_duration_ms
        if ratio <= 0.75:
            return 1.0
        if ratio >= 1.35:
            return 0.0
        return float(np.clip(1.35 - ratio, 0.0, 1.0))

    def _normalize_text(self, text: str) -> str:
        if not isinstance(text, str):
            return ""
        normalized = text.strip().lower()
        normalized = re.sub(r"\s+", " ", normalized)
        return normalized

    def _tokens(self, text: str) -> List[str]:
        if not text:
            return []
        return re.findall(r"[a-z0-9]+", text)

    def _to_float(self, value: Any, default: float) -> float:
        try:
            return float(value)
        except Exception:
            return default
