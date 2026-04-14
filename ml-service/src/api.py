from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename
import os
import shutil
import logging
import json
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, Any, Optional
import uuid
import numpy as np
import base64

# Import enrollment and verification
from enroll import FaceEnrollmentSystem
from verify import FaceVerificationSystem
from voice_stack import VoiceStack
from behavior_stack import BehaviorStack
from challenge_stack import ChallengeStack
from utils import (load_config, preprocess_image, detect_face, compute_embedding, 
                   cosine_similarity, perform_liveness_check)
from identity_contract import build_unified_score_packet

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.FileHandler('api.log'), logging.StreamHandler()]
)

app = Flask(__name__)
CORS(app, resources={
    r"/*": {
        "origins": ["http://localhost:4200", "http://localhost:8080", "http://127.0.0.1:4200"],
        "methods": ["GET", "POST", "OPTIONS"],
        "allow_headers": ["Content-Type", "Authorization"],
        "supports_credentials": True
    }
})

# Load configuration
config_path = "config/model_config.json"
config = load_config(config_path)
model_requirements = config.get('model_requirements', {})

# Initialize systems
enrollment_system = FaceEnrollmentSystem(config_path)
verification_system = FaceVerificationSystem(config_path)
voice_system = VoiceStack(config_path)
behavior_system = BehaviorStack(config_path)
challenge_system = ChallengeStack(config_path)
logging.info("Face recognition systems initialized successfully")

ENFORCE_MODEL_READINESS_ON_REQUEST = bool(model_requirements.get('enforce_on_request', True))
ENFORCE_MODEL_READINESS_ON_STARTUP = bool(model_requirements.get('enforce_on_startup', False))
REQUIRE_REAL_VOICE_MODELS = bool(model_requirements.get('require_real_voice_models', True))
REQUIRE_BEHAVIOR_ONNX_MODEL = bool(model_requirements.get('require_behavior_onnx', True))

os.makedirs('data/temp', exist_ok=True)

app.config['MAX_CONTENT_LENGTH'] = config.get('max_file_size', 5 * 1024 * 1024)
ALLOWED_EXTENSIONS = set(config.get('allowed_extensions', ['jpg', 'jpeg', 'png']))

rate_limit_storage = {}


def collect_model_readiness() -> Dict[str, Any]:
    voice_status = voice_system.readiness_status(require_real_models=REQUIRE_REAL_VOICE_MODELS)
    behavior_status = behavior_system.readiness_status(require_onnx=REQUIRE_BEHAVIOR_ONNX_MODEL)
    challenge_status = {
        'ready': True,
        'degraded': False,
        'strictMode': False,
        'backend': challenge_system.model_info().get('challenge', 'challenge-rules-v1'),
        'reasonCodes': []
    }

    overall_ready = bool(voice_status.get('ready', False) and behavior_status.get('ready', False) and challenge_status.get('ready', True))
    reason_codes = []
    reason_codes.extend(voice_status.get('reasonCodes', []))
    reason_codes.extend(behavior_status.get('reasonCodes', []))

    return {
        'ready': overall_ready,
        'enforcement': {
            'onRequest': ENFORCE_MODEL_READINESS_ON_REQUEST,
            'onStartup': ENFORCE_MODEL_READINESS_ON_STARTUP,
            'requireRealVoiceModels': REQUIRE_REAL_VOICE_MODELS,
            'requireBehaviorOnnxModel': REQUIRE_BEHAVIOR_ONNX_MODEL,
        },
        'components': {
            'voice': voice_status,
            'behavior': behavior_status,
            'challenge': challenge_status,
        },
        'reasonCodes': reason_codes,
    }


if ENFORCE_MODEL_READINESS_ON_STARTUP:
    startup_readiness = collect_model_readiness()
    if not startup_readiness.get('ready', False):
        raise RuntimeError(f"Model readiness check failed at startup: {json.dumps(startup_readiness)}")


def enrich_verification_result(
    student_id: str,
    result: Dict[str, Any],
    latency_ms: int,
    voice_result: Optional[Dict[str, Any]] = None,
    behavior_result: Optional[Dict[str, Any]] = None,
    challenge_result: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    attempts = result.get('attempts', []) if isinstance(result, dict) else []
    similarities = []
    qualities = []
    for attempt in attempts:
        try:
            similarities.append(float(attempt.get('similarity', 0.0)))
        except (TypeError, ValueError):
            similarities.append(0.0)
        try:
            qualities.append(float(attempt.get('face_quality', 0.0)))
        except (TypeError, ValueError):
            qualities.append(0.0)

    best_similarity = max(similarities) if similarities else 0.0
    best_quality = max(qualities) if qualities else 0.5
    liveness_check = result.get('liveness_check') if isinstance(result, dict) else None
    liveness_passed = True
    liveness_score = 100.0

    if isinstance(liveness_check, dict):
        liveness_passed = bool(liveness_check.get('liveness_passed', True))
        try:
            liveness_score = float(liveness_check.get('overall_score', 100.0 if liveness_passed else 0.0))
        except (TypeError, ValueError):
            liveness_score = 100.0 if liveness_passed else 0.0

    voice_similarity = 0.0
    voice_spoof_probability = 0.5
    voice_quality = 0.0
    voice_reason_codes = []
    if isinstance(voice_result, dict):
        try:
            voice_similarity = float(voice_result.get('voiceSimilarity', 0.0))
        except (TypeError, ValueError):
            voice_similarity = 0.0
        try:
            voice_spoof_probability = float(voice_result.get('voiceSpoofProbability', 0.5))
        except (TypeError, ValueError):
            voice_spoof_probability = 0.5
        try:
            voice_quality = float(voice_result.get('voiceQuality', 0.0))
        except (TypeError, ValueError):
            voice_quality = 0.0
        raw_voice_reasons = voice_result.get('reasonCodes', [])
        if isinstance(raw_voice_reasons, list):
            voice_reason_codes = [str(code) for code in raw_voice_reasons if code is not None]

    behavior_score = 0.0
    behavior_reason_codes = []
    if isinstance(behavior_result, dict):
        try:
            behavior_score = float(behavior_result.get('behaviorScore', 0.0))
        except (TypeError, ValueError):
            behavior_score = 0.0
        raw_behavior_reasons = behavior_result.get('reasonCodes', [])
        if isinstance(raw_behavior_reasons, list):
            behavior_reason_codes = [str(code) for code in raw_behavior_reasons if code is not None]

    challenge_score = 0.0
    challenge_reason_codes = []
    if isinstance(challenge_result, dict):
        try:
            challenge_score = float(challenge_result.get('challengeScore', 0.0))
        except (TypeError, ValueError):
            challenge_score = 0.0
        raw_challenge_reasons = challenge_result.get('reasonCodes', [])
        if isinstance(raw_challenge_reasons, list):
            challenge_reason_codes = [str(code) for code in raw_challenge_reasons if code is not None]

    model_versions = {}
    model_stack = result.get('model_stack') if isinstance(result, dict) else None
    if isinstance(model_stack, dict):
        model_versions.update({str(k): str(v) for k, v in model_stack.items() if v is not None})
    model_versions.update(voice_system.model_info())
    model_versions.update(behavior_system.model_info())
    model_versions.update(challenge_system.model_info())

    packet = build_unified_score_packet(
        student_id=str(student_id),
        face_similarity=best_similarity,
        face_quality=best_quality,
        liveness_score_0_to_100=liveness_score,
        latency_ms=latency_ms,
        voice_similarity=voice_similarity,
        voice_spoof_probability=voice_spoof_probability,
        challenge_score=challenge_score,
        behavior_score=behavior_score,
        model_versions=model_versions,
    )

    quality_gates = result.get('quality_gates') if isinstance(result, dict) else None
    reason_codes = []
    if isinstance(quality_gates, dict) and not bool(quality_gates.get('passed', True)):
        reason_codes.extend(list(quality_gates.get('failure_reasons', [])))
    if not liveness_passed:
        reason_codes.append('LIVENESS_FAILED')
    reason_codes.extend(voice_reason_codes)
    reason_codes.extend(behavior_reason_codes)
    reason_codes.extend(challenge_reason_codes)

    result['match'] = bool(result.get('verification_result', False))
    result['liveness'] = liveness_passed
    if isinstance(voice_result, dict):
        result['voice'] = bool(voice_result.get('success', False))
        result['voiceCheck'] = voice_result
    if isinstance(behavior_result, dict):
        result['behavior'] = bool(behavior_result.get('success', False))
        result['behaviorCheck'] = behavior_result
    if isinstance(challenge_result, dict):
        result['challenge'] = bool(challenge_result.get('success', False))
        result['challengeCheck'] = challenge_result
    result['voiceQuality'] = voice_quality
    result['behaviorScore'] = behavior_score
    result['challengeScore'] = challenge_score
    result['scorePacket'] = packet
    result['contractVersion'] = 'unified-score-packet/v1'
    result['reasonCodes'] = reason_codes
    return result


def read_audio_input_from_request(data: Dict[str, Any]) -> Optional[str]:
    audio_input = data.get('audio')
    if isinstance(audio_input, str) and audio_input.strip():
        return audio_input.strip()

    if 'audio' in request.files:
        audio_file = request.files['audio']
        audio_bytes = audio_file.read()
        if len(audio_bytes) > 0:
            return base64.b64encode(audio_bytes).decode('ascii')

    return None


def read_structured_input_from_request(data: Dict[str, Any], field_name: str) -> Optional[Dict[str, Any]]:
    value = data.get(field_name)
    if isinstance(value, dict):
        return value

    if isinstance(value, str) and value.strip():
        try:
            parsed = json.loads(value)
            if isinstance(parsed, dict):
                return parsed
        except Exception:
            return None

    if field_name in request.form:
        form_value = request.form.get(field_name)
        if isinstance(form_value, str) and form_value.strip():
            try:
                parsed = json.loads(form_value)
                if isinstance(parsed, dict):
                    return parsed
            except Exception:
                return None

    return None

def allowed_file(filename: str) -> bool:
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def check_rate_limit(client_ip: str) -> bool:
    rate_limit = config.get('rate_limit_per_minute', 60)
    current_time = datetime.now()
    
    if client_ip not in rate_limit_storage:
        rate_limit_storage[client_ip] = []
    
    rate_limit_storage[client_ip] = [
        req_time for req_time in rate_limit_storage[client_ip]
        if current_time - req_time < timedelta(minutes=1)
    ]
    
    if len(rate_limit_storage[client_ip]) >= rate_limit:
        return False
    
    rate_limit_storage[client_ip].append(current_time)
    return True

def log_api_request(endpoint: str, student_id: str = None, success: bool = True, error_msg: str = None) -> None:
    log_entry = {
        'timestamp': datetime.now().isoformat(),
        'endpoint': endpoint,
        'student_id': student_id,
        'client_ip': request.remote_addr,
        'success': success,
        'error_message': error_msg,
        'user_agent': request.headers.get('User-Agent', '')
    }
    log_file = Path('data') / f"api_log_{datetime.now().strftime('%Y%m%d')}.jsonl"
    with open(log_file, 'a') as f:
        f.write(json.dumps(log_entry) + '\n')

def save_uploaded_file(file, student_id: str = None, prefix: str = 'upload') -> str:
    if not file or not allowed_file(file.filename):
        raise ValueError("Invalid file type")
    
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    unique_id = str(uuid.uuid4())[:8]
    
    if student_id:
        filename = f"{prefix}_{student_id}_{timestamp}_{unique_id}.jpg"
    else:
        filename = f"{prefix}_{timestamp}_{unique_id}.jpg"
    
    filepath = os.path.join('data/temp', secure_filename(filename))
    file.save(filepath)
    
    return filepath

@app.before_request
def before_request():
    if request.endpoint in ('health_check', 'model_status'):
        return

    if ENFORCE_MODEL_READINESS_ON_REQUEST:
        readiness = collect_model_readiness()
        if not readiness.get('ready', False):
            log_api_request(request.endpoint, success=False, error_msg="Model readiness check failed")
            return jsonify({
                'error': 'Required models are not loaded or not running properly',
                'modelReadiness': readiness,
            }), 503

    if not check_rate_limit(request.remote_addr):
        log_api_request(request.endpoint, error_msg="Rate limit exceeded")
        return jsonify({'error': 'Rate limit exceeded. Please try again later.'}), 429

@app.route('/health', methods=['GET'])
def health_check():
    readiness = collect_model_readiness()
    status = {
        'status': 'healthy' if readiness.get('ready', False) else 'degraded',
        'timestamp': datetime.now().isoformat(),
        'enrollment_system': enrollment_system is not None,
        'verification_system': verification_system is not None,
        'modelReadiness': readiness,
    }
    return jsonify(status), 200 if readiness.get('ready', False) else 503


@app.route('/model-status', methods=['GET'])
def model_status():
    readiness = collect_model_readiness()
    return jsonify(readiness), 200 if readiness.get('ready', False) else 503

@app.route('/enroll', methods=['POST'])
def enroll():
    student_id = None
    temp_file_path = None
    try:
        if enrollment_system is None:
            raise RuntimeError("Enrollment system not initialized")

        # Form-data with file
        if 'image' in request.files:
            file = request.files['image']
            student_id = request.form.get('studentId')
            if not student_id:
                raise ValueError("studentId is required")

            # Save file temporarily
            temp_file_path = os.path.join("data/temp", secure_filename(file.filename))
            file.save(temp_file_path)

            # Pass the path to enroll_face
            result = enrollment_system.enroll_face(student_id, temp_file_path)

        # JSON / base64 input
        elif request.is_json:
            data = request.json
            student_id = data.get('studentId')
            image = data.get('image')
            if not student_id or not image:
                raise ValueError("studentId and image are required")
            result = enrollment_system.enroll_face(student_id, image)

        else:
            raise ValueError("No valid image provided")

        if not result['success']:
            raise ValueError(result['error'])

        embedding = result['embedding']
        encoding_base64 = base64.b64encode(np.array(embedding).tobytes()).decode()

        log_api_request('enroll', student_id, True)
        return jsonify({
            'success': True,
            'status': 'success',
            'studentId': student_id,
            'embedding': encoding_base64,
            'quality': result.get('quality', 1.0)
        }), 200

    except ValueError as e:
        error_msg = f"Validation error: {str(e)}"
        log_api_request('enroll', student_id, False, error_msg)
        return jsonify({'error': error_msg}), 400
    except Exception as e:
        error_msg = f"Unexpected error during enrollment: {str(e)}"
        logging.error(error_msg)
        log_api_request('enroll', student_id, False, error_msg)
        return jsonify({'error': 'Internal server error'}), 500
    finally:
        if temp_file_path and os.path.exists(temp_file_path):
            os.remove(temp_file_path)


@app.route('/voice/enroll', methods=['POST'])
def enroll_voice():
    student_id = None
    try:
        if not request.is_json:
            raise ValueError("Content-Type must be application/json")

        data = request.json
        student_id = data.get('studentId')
        audio_input = data.get('audio')
        if not student_id or not audio_input:
            raise ValueError("studentId and audio are required")

        voice_result = voice_system.enroll_voice(str(student_id), audio_input)
        if not voice_result.get('success', False):
            raise ValueError(voice_result.get('error', 'Voice enrollment failed'))

        log_api_request('voice-enroll', str(student_id), True)
        return jsonify(voice_result), 200
    except ValueError as exc:
        error_msg = f"Validation error: {str(exc)}"
        log_api_request('voice-enroll', student_id, False, error_msg)
        return jsonify({'error': error_msg}), 400
    except Exception as exc:
        error_msg = f"Unexpected error during voice enrollment: {str(exc)}"
        logging.error(error_msg)
        log_api_request('voice-enroll', student_id, False, error_msg)
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/verify', methods=['POST'])
def verify():
    student_id = None
    try:
        started_at = time.perf_counter()
        if verification_system is None:
            raise RuntimeError("Verification system not initialized")

        # 1️⃣ JSON body
        if request.is_json:
            data = request.json
            student_id = data.get('studentId')
            image_input = data.get('image')  # base64 string
            if not student_id or not image_input:
                raise ValueError("studentId and image are required")
            audio_input = read_audio_input_from_request(data)
            behavior_input = read_structured_input_from_request(data, 'behavior')
            challenge_input = read_structured_input_from_request(data, 'challenge')
        
        # 2️⃣ Form-data with file
        elif 'image' in request.files:
            file = request.files['image']
            student_id = request.form.get('studentId')
            if not student_id:
                raise ValueError("studentId is required")
            img_bytes = file.read()
            import base64
            image_input = "data:image/jpeg;base64," + base64.b64encode(img_bytes).decode()
            audio_input = read_audio_input_from_request({
                'audio': request.form.get('audio')
            })
            behavior_input = read_structured_input_from_request({
                'behavior': request.form.get('behavior')
            }, 'behavior')
            challenge_input = read_structured_input_from_request({
                'challenge': request.form.get('challenge')
            }, 'challenge')
        
        else:
            raise ValueError("No valid image provided")

        # Call verification
        result = verification_system.verify_with_retry(student_id, image_input)
        voice_result = None
        if audio_input:
            voice_result = voice_system.verify_voice(str(student_id), audio_input)
            result['voice_check'] = voice_result
        behavior_result = None
        if behavior_input:
            behavior_result = behavior_system.evaluate_behavior(behavior_input)
            result['behavior_check'] = behavior_result
        challenge_result = None
        if challenge_input:
            challenge_result = challenge_system.evaluate_challenge(challenge_input)
            result['challenge_check'] = challenge_result

        # Convert numpy types to Python types
        result['verification_result'] = bool(result['verification_result'])
        for attempt in result['attempts']:
            if isinstance(attempt['success'], np.bool_):
                attempt['success'] = bool(attempt['success'])
            if isinstance(attempt['similarity'], np.floating):
                attempt['similarity'] = float(attempt['similarity'])

        latency_ms = int((time.perf_counter() - started_at) * 1000)
        result = enrich_verification_result(
            student_id,
            result,
            latency_ms,
            voice_result=voice_result,
            behavior_result=behavior_result,
            challenge_result=challenge_result,
        )

        return jsonify(result), 200 if result['verification_result'] else 400

    except ValueError as e:
        error_msg = f"Validation error: {str(e)}"
        logging.error(error_msg)
        return jsonify({'error': error_msg}), 400
    except RuntimeError as e:
        error_msg = f"System error: {str(e)}"
        logging.error(error_msg)
        return jsonify({'error': error_msg}), 503
    except Exception as e:
        error_msg = f"Unexpected error during verification: {str(e)}"
        logging.error(error_msg)
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/liveness-check', methods=['POST'])
def liveness_check():
    """Standalone liveness detection endpoint"""
    try:
        if not request.is_json:
            raise ValueError("Content-Type must be application/json")
        
        data = request.json
        frames = data.get('frames', [])
        
        if not frames or len(frames) < 3:
            return jsonify({
                'error': 'Minimum 3 frames required for liveness detection',
                'provided': len(frames) if frames else 0
            }), 400
        
        logging.info(f"Performing liveness check on {len(frames)} frames")
        
        # Perform liveness check
        liveness_passed, liveness_result = perform_liveness_check(frames)
        
        # Convert numpy types to Python types for JSON serialization
        def convert_types(obj):
            if isinstance(obj, np.bool_):
                return bool(obj)
            elif isinstance(obj, (np.floating, np.integer)):
                return float(obj)
            elif isinstance(obj, dict):
                return {k: convert_types(v) for k, v in obj.items()}
            elif isinstance(obj, list):
                return [convert_types(item) for item in obj]
            return obj
        
        liveness_result = convert_types(liveness_result)
        
        response = {
            'liveness_passed': liveness_passed,
            'details': liveness_result,
            'timestamp': datetime.now().isoformat()
        }
        
        log_api_request('liveness-check', None, liveness_passed, 
                       None if liveness_passed else 'Liveness check failed')
        
        return jsonify(response), 200 if liveness_passed else 400
        
    except ValueError as e:
        error_msg = f"Validation error: {str(e)}"
        logging.error(error_msg)
        return jsonify({'error': error_msg}), 400
    except Exception as e:
        error_msg = f"Liveness check error: {str(e)}"
        logging.error(error_msg)
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/behavior/evaluate', methods=['POST'])
def behavior_evaluate():
    try:
        if not request.is_json:
            raise ValueError("Content-Type must be application/json")

        data = request.json
        behavior_payload = data.get('behavior') if isinstance(data, dict) else None
        if not isinstance(behavior_payload, dict):
            raise ValueError("behavior payload is required")

        result = behavior_system.evaluate_behavior(behavior_payload)
        return jsonify(result), 200 if result.get('success', False) else 400
    except ValueError as exc:
        return jsonify({'error': str(exc)}), 400
    except Exception as exc:
        logging.error("Behavior evaluation error: %s", exc)
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/challenge/evaluate', methods=['POST'])
def challenge_evaluate():
    try:
        if not request.is_json:
            raise ValueError("Content-Type must be application/json")

        data = request.json
        challenge_payload = data.get('challenge') if isinstance(data, dict) else None
        if not isinstance(challenge_payload, dict):
            raise ValueError("challenge payload is required")

        result = challenge_system.evaluate_challenge(challenge_payload)
        return jsonify(result), 200 if result.get('success', False) else 400
    except ValueError as exc:
        return jsonify({'error': str(exc)}), 400
    except Exception as exc:
        logging.error("Challenge evaluation error: %s", exc)
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/verify-with-liveness', methods=['POST'])
def verify_with_liveness():
    """Combined verification with liveness detection"""
    student_id = None
    try:
        started_at = time.perf_counter()
        if verification_system is None:
            raise RuntimeError("Verification system not initialized")
        
        if not request.is_json:
            raise ValueError("Content-Type must be application/json")
        
        data = request.json
        student_id = data.get('studentId')
        frames = data.get('frames', [])
        audio_input = read_audio_input_from_request(data)
        behavior_input = read_structured_input_from_request(data, 'behavior')
        challenge_input = read_structured_input_from_request(data, 'challenge')
        
        if not student_id:
            raise ValueError("studentId is required")
        
        if not frames or len(frames) < 3:
            return jsonify({
                'error': 'Minimum 3 frames required for verification with liveness',
                'provided': len(frames) if frames else 0
            }), 400
        
        logging.info(f"Performing verification with liveness for student {student_id}")
        
        # Call verification with frames for liveness check
        result = verification_system.verify_with_retry(student_id, frames=frames)
        voice_result = None
        if audio_input:
            voice_result = voice_system.verify_voice(str(student_id), audio_input)
            result['voice_check'] = voice_result
        behavior_result = None
        if behavior_input:
            behavior_result = behavior_system.evaluate_behavior(behavior_input)
            result['behavior_check'] = behavior_result
        challenge_result = None
        if challenge_input:
            challenge_result = challenge_system.evaluate_challenge(challenge_input)
            result['challenge_check'] = challenge_result
        
        # Convert numpy types to Python types
        def convert_types(obj):
            if isinstance(obj, np.bool_):
                return bool(obj)
            elif isinstance(obj, (np.floating, np.integer)):
                return float(obj)
            elif isinstance(obj, dict):
                return {k: convert_types(v) for k, v in obj.items()}
            elif isinstance(obj, list):
                return [convert_types(item) for item in obj]
            return obj
        
        result = convert_types(result)
        latency_ms = int((time.perf_counter() - started_at) * 1000)
        result = enrich_verification_result(
            student_id,
            result,
            latency_ms,
            voice_result=voice_result,
            behavior_result=behavior_result,
            challenge_result=challenge_result,
        )
        
        log_api_request('verify-with-liveness', student_id, result['verification_result'], 
                       None if result['verification_result'] else result.get('final_message'))
        
        return jsonify(result), 200 if result['verification_result'] else 400
        
    except ValueError as e:
        error_msg = f"Validation error: {str(e)}"
        logging.error(error_msg)
        log_api_request('verify-with-liveness', student_id, False, error_msg)
        return jsonify({'error': error_msg}), 400
    except RuntimeError as e:
        error_msg = f"System error: {str(e)}"
        logging.error(error_msg)
        log_api_request('verify-with-liveness', student_id, False, error_msg)
        return jsonify({'error': error_msg}), 503
    except Exception as e:
        error_msg = f"Unexpected error during verification: {str(e)}"
        logging.error(error_msg)
        log_api_request('verify-with-liveness', student_id, False, error_msg)
        return jsonify({'error': 'Internal server error'}), 500

@app.errorhandler(413)
def request_entity_too_large(error):
    max_size_mb = config.get('max_file_size', 5 * 1024 * 1024) / (1024 * 1024)
    return jsonify({
        'error': f'File too large. Maximum size allowed: {max_size_mb:.1f}MB'
    }), 413

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(405)
def method_not_allowed(error):
    return jsonify({'error': 'Method not allowed for this endpoint'}), 405

def cleanup_temp_files():
    temp_dir = Path('data/temp')
    if not temp_dir.exists():
        return
    cutoff_time = datetime.now() - timedelta(hours=1)
    for temp_file in temp_dir.glob('*'):
        try:
            if datetime.fromtimestamp(temp_file.stat().st_ctime) < cutoff_time:
                temp_file.unlink()
        except Exception:
            pass

if __name__ == '__main__':
    if os.path.exists('data/temp'):
        shutil.rmtree('data/temp')
    os.makedirs('data/temp', exist_ok=True)
    
    cleanup_temp_files()
    
    logging.info(f"Starting Anti-Cheating ML API on {config['host']}:{config['port']}")
    app.run(
        host=config.get('host', '127.0.0.1'),
        port=config.get('port', 5000),
        debug=config.get('debug', False)
    )