from dataclasses import asdict, dataclass
from typing import Any, Dict, Optional
import uuid


@dataclass
class ModelVersions:
    faceDetector: str = "scrfd-2.5g"
    faceEmbedder: str = "arcface-r100"
    faceSpoof: str = "minifasnet-v2"
    speaker: str = "ecapa-tdnn"
    voiceSpoof: str = "aasist-v1"


@dataclass
class UnifiedScorePacket:
    sessionId: str
    studentId: str
    faceSimilarity: float
    faceQuality: float
    faceLiveness: float
    faceSpoofProbability: float
    voiceSimilarity: float
    voiceSpoofProbability: float
    challengeScore: float
    behaviorScore: float
    modelVersions: Dict[str, str]
    latencyMs: int


def _clamp_01(value: Optional[float], default: float = 0.0) -> float:
    if value is None:
        return default
    try:
        return max(0.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return default


def build_unified_score_packet(
    *,
    student_id: str,
    face_similarity: Optional[float],
    face_quality: Optional[float],
    liveness_score_0_to_100: Optional[float],
    latency_ms: int,
    session_id: Optional[str] = None,
    voice_similarity: Optional[float] = None,
    voice_spoof_probability: Optional[float] = None,
    challenge_score: Optional[float] = None,
    behavior_score: Optional[float] = None,
    model_versions: Optional[Dict[str, str]] = None,
) -> Dict[str, Any]:
    liveness = 0.0
    if liveness_score_0_to_100 is not None:
        try:
            liveness = float(liveness_score_0_to_100) / 100.0
        except (TypeError, ValueError):
            liveness = 0.0

    versions = asdict(ModelVersions())
    if isinstance(model_versions, dict):
        for key, value in model_versions.items():
            if value is not None:
                versions[str(key)] = str(value)

    packet = UnifiedScorePacket(
        sessionId=session_id or str(uuid.uuid4()),
        studentId=str(student_id),
        faceSimilarity=_clamp_01(face_similarity),
        faceQuality=_clamp_01(face_quality, default=0.5),
        faceLiveness=_clamp_01(liveness),
        faceSpoofProbability=_clamp_01(1.0 - _clamp_01(liveness)),
        voiceSimilarity=_clamp_01(voice_similarity),
        voiceSpoofProbability=_clamp_01(voice_spoof_probability, default=0.5),
        challengeScore=_clamp_01(challenge_score),
        behaviorScore=_clamp_01(behavior_score),
        modelVersions=versions,
        latencyMs=max(0, int(latency_ms)),
    )

    return asdict(packet)
