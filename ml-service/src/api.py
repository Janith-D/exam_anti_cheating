from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename
import os
import shutil
import logging
import json
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, Any, Optional
import uuid
import numpy as np
import base64

# Import enrollment and verification
from enroll import FaceEnrollmentSystem
from verify import FaceVerificationSystem
from utils import load_config, preprocess_image, detect_face, compute_embedding, cosine_similarity

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.FileHandler('api.log'), logging.StreamHandler()]
)

app = Flask(__name__)
CORS(app)

# Load configuration
config_path = "config/model_config.json"
config = load_config(config_path)

# Initialize systems
enrollment_system = FaceEnrollmentSystem(config_path)
verification_system = FaceVerificationSystem(config_path)
logging.info("Face recognition systems initialized successfully")

os.makedirs('data/temp', exist_ok=True)

app.config['MAX_CONTENT_LENGTH'] = config.get('max_file_size', 5 * 1024 * 1024)
ALLOWED_EXTENSIONS = set(config.get('allowed_extensions', ['jpg', 'jpeg', 'png']))

rate_limit_storage = {}

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
    if request.endpoint == 'health_check':
        return
    if not check_rate_limit(request.remote_addr):
        log_api_request(request.endpoint, error_msg="Rate limit exceeded")
        return jsonify({'error': 'Rate limit exceeded. Please try again later.'}), 429

@app.route('/health', methods=['GET'])
def health_check():
    status = {
        'status': 'healthy',
        'timestamp': datetime.now().isoformat(),
        'enrollment_system': enrollment_system is not None,
        'verification_system': verification_system is not None
    }
    return jsonify(status), 200

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
            'status': 'success',
            'studentId': student_id,
            'embedding': encoding_base64
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


@app.route('/verify', methods=['POST'])
def verify():
    student_id = None
    try:
        if verification_system is None:
            raise RuntimeError("Verification system not initialized")

        # 1️⃣ JSON body
        if request.is_json:
            data = request.json
            student_id = data.get('studentId')
            image_input = data.get('image')  # base64 string
            if not student_id or not image_input:
                raise ValueError("studentId and image are required")
        
        # 2️⃣ Form-data with file
        elif 'image' in request.files:
            file = request.files['image']
            student_id = request.form.get('studentId')
            if not student_id:
                raise ValueError("studentId is required")
            img_bytes = file.read()
            import base64
            image_input = "data:image/jpeg;base64," + base64.b64encode(img_bytes).decode()
        
        else:
            raise ValueError("No valid image provided")

        # Call verification
        result = verification_system.verify_with_retry(student_id, image_input)

        # Convert numpy types to Python types
        result['verification_result'] = bool(result['verification_result'])
        for attempt in result['attempts']:
            if isinstance(attempt['success'], np.bool_):
                attempt['success'] = bool(attempt['success'])
            if isinstance(attempt['similarity'], np.floating):
                attempt['similarity'] = float(attempt['similarity'])

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