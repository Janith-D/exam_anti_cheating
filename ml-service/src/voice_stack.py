import base64
import io
import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Optional, Tuple, Union

import numpy as np

from utils import cosine_similarity, load_config

try:
    import librosa  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    librosa = None

try:
    import soundfile as sf  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    sf = None

try:
    import torch  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    torch = None

try:
    from speechbrain.inference.speaker import EncoderClassifier  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    EncoderClassifier = None

try:
    import onnxruntime as ort  # type: ignore
except Exception:  # pragma: no cover - optional dependency
    ort = None


class VoiceStack:
    def __init__(self, config_path: str):
        self.config = load_config(config_path)
        self.voice_config = self.config.get("voice", {})
        self.thresholds = self.config.get("modality_thresholds", {})

        self.enrolled_dir = Path("data/enrolled")
        self.metadata_dir = Path("data/metadata")
        self.enrolled_dir.mkdir(parents=True, exist_ok=True)
        self.metadata_dir.mkdir(parents=True, exist_ok=True)

        self.target_sample_rate = int(self.voice_config.get("target_sample_rate", 16000))
        self.min_audio_bytes = int(self.voice_config.get("min_audio_bytes", 4000))
        self.min_duration_seconds = float(self.voice_config.get("min_duration_seconds", 1.2))
        self.max_duration_seconds = float(self.voice_config.get("max_duration_seconds", 20.0))
        self.min_voiced_ratio = float(self.voice_config.get("min_voiced_ratio", 0.25))
        self.max_clipping_ratio = float(self.voice_config.get("max_clipping_ratio", 0.02))
        self.min_rms_energy = float(self.voice_config.get("min_rms_energy", 0.005))
        self.spoof_entropy_floor = float(self.voice_config.get("spoof_entropy_floor", 4.0))

        self.enroll_min_quality = float(self.thresholds.get("voiceEnrollMinQuality", 0.45))
        self.verify_min_quality = float(self.thresholds.get("voiceVerifyMinQuality", 0.35))
        self.verify_similarity_threshold = float(self.thresholds.get("voiceSimilarityMin", 0.70))
        self.verify_spoof_max = float(self.thresholds.get("voiceSpoofMax", 0.65))

        self.speaker_model = self.voice_config.get("speaker_model", "speechbrain-ecapa")
        self.speaker_source = self.voice_config.get("speaker_source", "speechbrain/spkrec-ecapa-voxceleb")
        self.speaker_savedir = self.voice_config.get("speaker_savedir", "models/voice/speaker")

        self.spoof_model = self.voice_config.get("spoof_model", "aasist-onnx")
        self.spoof_onnx_path = self.voice_config.get("anti_spoof_onnx_path", "models/voice/aasist.onnx")

        self._speaker_classifier = None
        self._spoof_session = None
        self._speaker_backend = "feature-fallback"
        self._spoof_backend = "heuristic"

        self._initialize_backends()

    def model_info(self) -> Dict[str, str]:
        return {
            "speaker": f"{self.speaker_model}:{self._speaker_backend}",
            "voiceSpoof": f"{self.spoof_model}:{self._spoof_backend}",
        }

    def readiness_status(self, require_real_models: bool = False) -> Dict[str, Any]:
        speaker_ready = self._speaker_classifier is not None and self._speaker_backend == "speechbrain-ecapa"
        spoof_ready = self._spoof_session is not None and self._spoof_backend == "onnx-aasist"

        reason_codes = []
        if not speaker_ready:
            reason_codes.append("VOICE_SPEAKER_MODEL_NOT_LOADED")
        if not spoof_ready:
            reason_codes.append("VOICE_ANTISPOOF_MODEL_NOT_LOADED")

        degraded = not (speaker_ready and spoof_ready)
        ready = (speaker_ready and spoof_ready) if require_real_models else True

        return {
            "ready": ready,
            "degraded": degraded,
            "strictMode": require_real_models,
            "speakerReady": speaker_ready,
            "spoofReady": spoof_ready,
            "speakerBackend": self._speaker_backend,
            "spoofBackend": self._spoof_backend,
            "reasonCodes": reason_codes,
        }

    def enroll_voice(self, student_id: str, audio_input: Union[str, bytes]) -> Dict[str, Any]:
        try:
            audio_bytes = self._decode_audio_input(audio_input)
            waveform, sample_rate = self._decode_waveform(audio_bytes)
            quality_report = self._evaluate_quality(waveform, sample_rate, len(audio_bytes))
            embedding = self._compute_voice_embedding(waveform, sample_rate)
            spoof_probability = self._estimate_spoof_probability(waveform, sample_rate, len(audio_bytes), quality_report)

            reason_codes = list(quality_report.get("reasonCodes", []))
            if spoof_probability >= self.verify_spoof_max:
                reason_codes.append("VOICE_SPOOF_RISK_HIGH")

            if quality_report["qualityScore"] < self.enroll_min_quality:
                reason_codes.append("VOICE_QUALITY_BELOW_ENROLL_THRESHOLD")
                return {
                    "success": False,
                    "error": "Voice quality is below enrollment threshold",
                    "studentId": student_id,
                    "voiceQuality": quality_report["qualityScore"],
                    "voiceSpoofProbability": spoof_probability,
                    "reasonCodes": reason_codes,
                }

            np.save(self.enrolled_dir / f"{student_id}_voice.npy", embedding.astype(np.float32))
            metadata = {
                "studentId": student_id,
                "enrolledAt": datetime.now().isoformat(),
                "audioBytes": len(audio_bytes),
                "sampleRate": sample_rate,
                "quality": quality_report,
                "spoofProbability": spoof_probability,
                "modelStack": self.model_info(),
            }
            with open(self.metadata_dir / f"{student_id}_voice_metadata.json", "w", encoding="utf-8") as file_handle:
                json.dump(metadata, file_handle, indent=2)

            voice_template = base64.b64encode(embedding.astype(np.float32).tobytes()).decode("ascii")
            return {
                "success": True,
                "studentId": student_id,
                "voiceTemplate": voice_template,
                "voiceQuality": quality_report["qualityScore"],
                "voiceSpoofProbability": spoof_probability,
                "model": self.model_info()["speaker"],
                "reasonCodes": reason_codes,
                "qualityMetrics": quality_report.get("metrics", {}),
            }
        except Exception as exc:
            logging.error("Voice enrollment failed for %s: %s", student_id, exc)
            return {
                "success": False,
                "error": str(exc),
                "studentId": student_id,
            }

    def verify_voice(self, student_id: str, audio_input: Union[str, bytes]) -> Dict[str, Any]:
        template_path = self.enrolled_dir / f"{student_id}_voice.npy"
        if not template_path.exists():
            return {
                "success": False,
                "studentId": student_id,
                "voiceSimilarity": 0.0,
                "voiceSpoofProbability": 0.5,
                "voiceQuality": 0.0,
                "reasonCodes": ["VOICE_TEMPLATE_MISSING"],
            }

        try:
            stored_embedding = np.load(template_path)
            audio_bytes = self._decode_audio_input(audio_input)
            waveform, sample_rate = self._decode_waveform(audio_bytes)
            quality_report = self._evaluate_quality(waveform, sample_rate, len(audio_bytes))
            probe_embedding = self._compute_voice_embedding(waveform, sample_rate)
            spoof_probability = self._estimate_spoof_probability(waveform, sample_rate, len(audio_bytes), quality_report)

            if stored_embedding.shape != probe_embedding.shape:
                return {
                    "success": False,
                    "studentId": student_id,
                    "voiceSimilarity": 0.0,
                    "voiceSpoofProbability": spoof_probability,
                    "voiceQuality": quality_report["qualityScore"],
                    "reasonCodes": ["VOICE_EMBEDDING_DIMENSION_MISMATCH"],
                }

            similarity = float(cosine_similarity(stored_embedding.astype(np.float32), probe_embedding.astype(np.float32)))
            reason_codes = list(quality_report.get("reasonCodes", []))

            if quality_report["qualityScore"] < self.verify_min_quality:
                reason_codes.append("VOICE_QUALITY_LOW")
            if similarity < self.verify_similarity_threshold:
                reason_codes.append("VOICE_SIMILARITY_LOW")
            if spoof_probability >= self.verify_spoof_max:
                reason_codes.append("VOICE_SPOOF_RISK_HIGH")

            success = (
                quality_report["qualityScore"] >= self.verify_min_quality
                and similarity >= self.verify_similarity_threshold
                and spoof_probability < self.verify_spoof_max
            )

            return {
                "success": success,
                "studentId": student_id,
                "voiceSimilarity": similarity,
                "voiceSpoofProbability": spoof_probability,
                "voiceQuality": quality_report["qualityScore"],
                "qualityMetrics": quality_report.get("metrics", {}),
                "reasonCodes": reason_codes,
            }
        except Exception as exc:
            logging.error("Voice verification failed for %s: %s", student_id, exc)
            return {
                "success": False,
                "studentId": student_id,
                "voiceSimilarity": 0.0,
                "voiceSpoofProbability": 0.5,
                "voiceQuality": 0.0,
                "reasonCodes": ["VOICE_VERIFICATION_ERROR"],
            }

    def analyze_audio(self, audio_input: Union[str, bytes]) -> Dict[str, Any]:
        try:
            audio_bytes = self._decode_audio_input(audio_input)
            waveform, sample_rate = self._decode_waveform(audio_bytes)
            quality_report = self._evaluate_quality(waveform, sample_rate, len(audio_bytes))
            spoof_probability = self._estimate_spoof_probability(waveform, sample_rate, len(audio_bytes), quality_report)
            return {
                "success": True,
                "voiceQuality": quality_report["qualityScore"],
                "voiceSpoofProbability": spoof_probability,
                "qualityMetrics": quality_report.get("metrics", {}),
                "reasonCodes": list(quality_report.get("reasonCodes", [])),
            }
        except Exception as exc:
            return {
                "success": False,
                "error": str(exc),
                "voiceQuality": 0.0,
                "voiceSpoofProbability": 0.5,
                "qualityMetrics": {},
                "reasonCodes": ["VOICE_ANALYSIS_ERROR"],
            }

    def _initialize_backends(self) -> None:
        if EncoderClassifier is not None and torch is not None:
            try:
                self._speaker_classifier = EncoderClassifier.from_hparams(
                    source=self.speaker_source,
                    savedir=self.speaker_savedir,
                    run_opts={"device": "cpu"},
                )
                self._speaker_backend = "speechbrain-ecapa"
            except Exception as exc:
                logging.warning("Voice speaker model unavailable, using feature fallback: %s", exc)
                self._speaker_classifier = None

        if ort is not None and self.spoof_onnx_path and Path(self.spoof_onnx_path).exists():
            try:
                self._spoof_session = ort.InferenceSession(self.spoof_onnx_path, providers=["CPUExecutionProvider"])
                self._spoof_backend = "onnx-aasist"
            except Exception as exc:
                logging.warning("Voice anti-spoof ONNX unavailable, using heuristic fallback: %s", exc)
                self._spoof_session = None

    def _decode_audio_input(self, audio_input: Union[str, bytes]) -> bytes:
        if isinstance(audio_input, bytes):
            if len(audio_input) == 0:
                raise ValueError("Audio payload is empty")
            return audio_input

        if not isinstance(audio_input, str) or not audio_input.strip():
            raise ValueError("audio is required")

        audio_text = audio_input.strip()
        audio_path = Path(audio_text)
        if audio_path.exists() and audio_path.is_file():
            audio_bytes = audio_path.read_bytes()
            if len(audio_bytes) == 0:
                raise ValueError("Audio file is empty")
            return audio_bytes

        if audio_text.startswith("data:") and "," in audio_text:
            audio_text = audio_text.split(",", 1)[1]

        try:
            audio_bytes = base64.b64decode(audio_text, validate=True)
        except Exception:
            audio_bytes = base64.b64decode(audio_text)

        if len(audio_bytes) == 0:
            raise ValueError("Decoded audio is empty")

        return audio_bytes

    def _decode_waveform(self, audio_bytes: bytes) -> Tuple[np.ndarray, int]:
        waveform = None
        sample_rate = self.target_sample_rate

        if sf is not None:
            try:
                decoded, sr = sf.read(io.BytesIO(audio_bytes), dtype="float32", always_2d=False)
                waveform = np.asarray(decoded, dtype=np.float32)
                sample_rate = int(sr)
            except Exception:
                waveform = None

        if waveform is None:
            byte_array = np.frombuffer(audio_bytes, dtype=np.uint8).astype(np.float32)
            if byte_array.size < 32:
                raise ValueError("Audio sample too short")
            waveform = (byte_array - 128.0) / 128.0
            sample_rate = self.target_sample_rate

        if waveform.ndim > 1:
            waveform = np.mean(waveform, axis=1)

        waveform = np.nan_to_num(waveform.astype(np.float32), nan=0.0, posinf=0.0, neginf=0.0)
        if waveform.size == 0:
            raise ValueError("Waveform is empty")

        if sample_rate != self.target_sample_rate:
            if librosa is not None:
                waveform = librosa.resample(waveform, orig_sr=sample_rate, target_sr=self.target_sample_rate).astype(np.float32)
                sample_rate = self.target_sample_rate
            else:
                sample_count = max(64, int(len(waveform) * self.target_sample_rate / max(1, sample_rate)))
                waveform = np.interp(
                    np.linspace(0, len(waveform) - 1, sample_count),
                    np.arange(len(waveform)),
                    waveform,
                ).astype(np.float32)
                sample_rate = self.target_sample_rate

        peak = float(np.max(np.abs(waveform)))
        if peak > 1e-6:
            waveform = waveform / peak

        return waveform, sample_rate

    def _evaluate_quality(self, waveform: np.ndarray, sample_rate: int, audio_bytes_len: int) -> Dict[str, Any]:
        duration_seconds = float(len(waveform)) / float(max(1, sample_rate))
        rms_energy = float(np.sqrt(np.mean(np.square(waveform))))
        clipping_ratio = float(np.mean(np.abs(waveform) >= 0.99))

        if librosa is not None:
            voiced_intervals = librosa.effects.split(waveform, top_db=30)
            voiced_samples = float(np.sum(voiced_intervals[:, 1] - voiced_intervals[:, 0])) if voiced_intervals.size > 0 else 0.0
            voiced_ratio = voiced_samples / float(max(1, len(waveform)))
        else:
            voiced_ratio = float(np.mean(np.abs(waveform) > 0.02))

        byte_score = min(1.0, float(audio_bytes_len) / float(max(1, self.min_audio_bytes * 2)))
        duration_score = min(1.0, duration_seconds / max(0.1, self.min_duration_seconds))
        rms_score = min(1.0, rms_energy / max(1e-6, self.min_rms_energy * 2.0))
        clipping_score = max(0.0, 1.0 - (clipping_ratio / max(1e-6, self.max_clipping_ratio * 2.0)))
        voiced_score = min(1.0, voiced_ratio / max(1e-6, self.min_voiced_ratio * 1.5))

        quality_score = float(np.clip(
            0.15 * byte_score
            + 0.25 * duration_score
            + 0.20 * rms_score
            + 0.20 * clipping_score
            + 0.20 * voiced_score,
            0.0,
            1.0,
        ))

        reason_codes = []
        if audio_bytes_len < self.min_audio_bytes:
            reason_codes.append("VOICE_AUDIO_TOO_SHORT")
        if duration_seconds < self.min_duration_seconds:
            reason_codes.append("VOICE_DURATION_TOO_SHORT")
        if duration_seconds > self.max_duration_seconds:
            reason_codes.append("VOICE_DURATION_TOO_LONG")
        if rms_energy < self.min_rms_energy:
            reason_codes.append("VOICE_ENERGY_TOO_LOW")
        if clipping_ratio > self.max_clipping_ratio:
            reason_codes.append("VOICE_CLIPPING_HIGH")
        if voiced_ratio < self.min_voiced_ratio:
            reason_codes.append("VOICE_ACTIVITY_LOW")

        return {
            "qualityScore": quality_score,
            "reasonCodes": reason_codes,
            "metrics": {
                "durationSeconds": duration_seconds,
                "rmsEnergy": rms_energy,
                "clippingRatio": clipping_ratio,
                "voicedRatio": voiced_ratio,
                "audioBytes": audio_bytes_len,
            },
        }

    def _compute_voice_embedding(self, waveform: np.ndarray, sample_rate: int) -> np.ndarray:
        if self._speaker_classifier is not None and torch is not None:
            try:
                waveform_tensor = torch.tensor(waveform, dtype=torch.float32).unsqueeze(0)
                with torch.no_grad():
                    embedding_tensor = self._speaker_classifier.encode_batch(waveform_tensor)
                embedding = embedding_tensor.squeeze().cpu().numpy().astype(np.float32)
                norm = np.linalg.norm(embedding)
                if norm > 0:
                    embedding = embedding / norm
                return embedding
            except Exception as exc:
                logging.warning("SpeechBrain embedding failed, using feature fallback: %s", exc)

        return self._fallback_embedding(waveform, sample_rate)

    def _fallback_embedding(self, waveform: np.ndarray, sample_rate: int) -> np.ndarray:
        if librosa is not None:
            mfcc = librosa.feature.mfcc(y=waveform, sr=sample_rate, n_mfcc=24)
            mfcc_delta = librosa.feature.delta(mfcc)
            feature_vector = np.concatenate([
                np.mean(mfcc, axis=1),
                np.std(mfcc, axis=1),
                np.mean(mfcc_delta, axis=1),
                np.std(mfcc_delta, axis=1),
            ]).astype(np.float32)
        else:
            spectrum = np.abs(np.fft.rfft(waveform)).astype(np.float32)
            spectrum = np.nan_to_num(spectrum, nan=0.0, posinf=0.0, neginf=0.0)
            feature_vector = np.interp(
                np.linspace(0, max(0, len(spectrum) - 1), 96),
                np.arange(len(spectrum)),
                spectrum,
            ).astype(np.float32)

        feature_vector = np.nan_to_num(feature_vector, nan=0.0, posinf=0.0, neginf=0.0)
        norm = np.linalg.norm(feature_vector)
        if norm > 0:
            feature_vector = feature_vector / norm
        return feature_vector

    def _estimate_spoof_probability(
        self,
        waveform: np.ndarray,
        sample_rate: int,
        audio_bytes_len: int,
        quality_report: Dict[str, Any],
    ) -> float:
        if self._spoof_session is not None and librosa is not None:
            try:
                features = self._build_spoof_features(waveform, sample_rate)
                input_name = self._spoof_session.get_inputs()[0].name
                output = self._spoof_session.run(None, {input_name: features})[0]
                raw_value = float(np.mean(output))
                probability = 1.0 / (1.0 + np.exp(-raw_value))
                return float(np.clip(probability, 0.0, 1.0))
            except Exception as exc:
                logging.warning("ONNX anti-spoof inference failed, using heuristic: %s", exc)

        return self._heuristic_spoof_probability(waveform, sample_rate, audio_bytes_len, quality_report)

    def _build_spoof_features(self, waveform: np.ndarray, sample_rate: int) -> np.ndarray:
        mel = librosa.feature.melspectrogram(
            y=waveform,
            sr=sample_rate,
            n_fft=512,
            hop_length=160,
            n_mels=80,
            fmax=sample_rate // 2,
        )
        log_mel = librosa.power_to_db(mel + 1e-8)
        normalized = (log_mel - np.mean(log_mel)) / max(1e-6, float(np.std(log_mel)))
        target_frames = int(self.voice_config.get("spoof_target_frames", 200))
        if normalized.shape[1] < target_frames:
            pad = target_frames - normalized.shape[1]
            normalized = np.pad(normalized, ((0, 0), (0, pad)), mode="edge")
        else:
            normalized = normalized[:, :target_frames]

        return normalized[np.newaxis, np.newaxis, :, :].astype(np.float32)

    def _heuristic_spoof_probability(
        self,
        waveform: np.ndarray,
        sample_rate: int,
        audio_bytes_len: int,
        quality_report: Dict[str, Any],
    ) -> float:
        if librosa is not None:
            flatness = float(np.mean(librosa.feature.spectral_flatness(y=waveform)))
            zcr = float(np.mean(librosa.feature.zero_crossing_rate(y=waveform)))
        else:
            flatness = 0.15
            zcr = float(np.mean(np.abs(np.diff(np.sign(waveform))) > 0))

        histogram = np.bincount(np.frombuffer(waveform.astype(np.float32).tobytes(), dtype=np.uint8), minlength=256).astype(np.float64)
        probabilities = histogram / max(1.0, float(np.sum(histogram)))
        probabilities = probabilities[probabilities > 0.0]
        entropy = float(-np.sum(probabilities * np.log2(probabilities))) if probabilities.size > 0 else 0.0

        entropy_risk = max(0.0, min(1.0, (self.spoof_entropy_floor - entropy) / max(0.1, self.spoof_entropy_floor)))
        flatness_risk = max(0.0, min(1.0, (0.45 - flatness) / 0.45))
        zcr_risk = max(0.0, min(1.0, (0.06 - zcr) / 0.06))
        quality_risk = 1.0 - float(quality_report.get("qualityScore", 0.0))
        length_risk = 1.0 if audio_bytes_len < self.min_audio_bytes else 0.0

        spoof_probability = (
            0.10
            + 0.30 * entropy_risk
            + 0.20 * flatness_risk
            + 0.15 * zcr_risk
            + 0.20 * quality_risk
            + 0.05 * length_risk
        )
        return float(np.clip(spoof_probability, 0.0, 1.0))
