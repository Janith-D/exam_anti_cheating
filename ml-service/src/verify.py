import os
import cv2
import numpy as np
import logging
import json
from datetime import datetime
from pathlib import Path
from typing import Optional, Tuple, Dict
from utils import load_config, preprocess_image, detect_face, compute_embedding, cosine_similarity

# ------------------ Logging ------------------
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('verification.log'),
        logging.StreamHandler()
    ]
)

# ------------------ Verification System ------------------
class FaceVerificationSystem:
    def __init__(self, config_path: str = "config/model_config.json"):
        self.config = load_config(config_path)
        self.temp_dir = Path('data/temp')
        self.audit_dir = Path('data/audit')
        self.enrolled_dir = Path('data/enrolled')  # Where embeddings are saved

        for directory in [self.temp_dir, self.audit_dir]:
            directory.mkdir(parents=True, exist_ok=True)

        self.similarity_threshold = self.config.get('similarity_threshold', 0.75)
        self.max_attempts = self.config.get('max_verification_attempts', 3)
        self.liveness_checks = self.config.get('enable_liveness_detection', True)

    # ------------------ Audit logging ------------------
    def log_verification_attempt(self, student_id: str, similarity: float, result: bool,
                                 attempt_number: int = 1, notes: str = ""):
        audit_entry = {
            'timestamp': datetime.now().isoformat(),
            'student_id': student_id,
            'similarity_score': float(similarity),
            'threshold': self.similarity_threshold,
            'result': 'SUCCESS' if result else 'FAILURE',
            'attempt_number': attempt_number,
            'notes': notes
        }
        audit_file = self.audit_dir / f"verification_log_{datetime.now().strftime('%Y%m%d')}.jsonl"
        with open(audit_file, 'a') as f:
            f.write(json.dumps(audit_entry) + '\n')
        logging.info(f"Verification {audit_entry['result']} for {student_id}: {similarity:.3f}")

    # ------------------ Load stored embedding ------------------
    def load_stored_embedding(self, student_id: str) -> Optional[np.ndarray]:
        path = self.enrolled_dir / f"{student_id}.npy"
        if path.exists():
            return np.load(path)
        return None

    # ------------------ Advanced face verification ------------------
    def verify_face_advanced(self, student_id: str, image_input: str, stored_embedding: np.ndarray,
                             attempt_number: int = 1) -> Tuple[bool, float, str]:
        try:
            img = preprocess_image(image_input, self.config['image_size'])
            face = detect_face(img)

            if face is None:
                msg = "No face detected in image"
                self.log_verification_attempt(student_id, 0.0, False, attempt_number, msg)
                return False, 0.0, msg

            face_resized = cv2.resize(face, tuple(self.config['face_size']))
            new_embedding = compute_embedding(face_resized)

            if new_embedding is None or len(new_embedding) == 0:
                msg = "Failed to compute embedding from image"
                self.log_verification_attempt(student_id, 0.0, False, attempt_number, msg)
                return False, 0.0, msg

            similarity = cosine_similarity(stored_embedding, new_embedding)
            is_verified = similarity >= self.similarity_threshold
            status_msg = (f"Verification successful (similarity: {similarity:.3f})"
                          if is_verified else
                          f"Verification failed - insufficient similarity ({similarity:.3f} < {self.similarity_threshold})")
            self.log_verification_attempt(student_id, similarity, is_verified, attempt_number, status_msg)
            return is_verified, similarity, status_msg
        except Exception as e:
            error_msg = f"Verification error: {str(e)}"
            logging.error(error_msg)
            self.log_verification_attempt(student_id, 0.0, False, attempt_number, error_msg)
            return False, 0.0, error_msg

    # ------------------ Webcam capture ------------------
    def capture_live_image(self, countdown: int = 3) -> str:
        cap = cv2.VideoCapture(0)
        if not cap.isOpened():
            raise RuntimeError("Cannot access webcam")
        logging.info("Starting face capture sequence...")
        try:
            for i in range(countdown * 30):
                ret, frame = cap.read()
                if not ret:
                    continue
                remaining = max(0, countdown - i // 30)
                cv2.putText(frame, f"Capturing in {remaining}s", (50, 50),
                            cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                cv2.imshow("Face Verification", frame)
                if cv2.waitKey(33) & 0xFF == ord("q"):
                    raise KeyboardInterrupt("Cancelled by user")
            ret, frame = cap.read()
            if not ret:
                raise RuntimeError("Failed to capture frame")
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            temp_path = self.temp_dir / f"verify_{timestamp}.jpg"
            cv2.imwrite(str(temp_path), frame)
            logging.info(f"Live image saved: {temp_path}")
            return str(temp_path)
        finally:
            cap.release()
            cv2.destroyAllWindows()

    # ------------------ Verify with retries ------------------
    def verify_with_retry(self, student_id: str, image_input: Optional[str] = None) -> Dict:
        result = {
            'studentId': student_id,
            'verification_result': False,
            'attempts': [],
            'final_message': '',
            'timestamp': datetime.now().isoformat()
        }

        stored_embedding = self.load_stored_embedding(student_id)
        if stored_embedding is None:
            msg = f"No stored embedding for student {student_id}"
            logging.error(msg)
            result['final_message'] = msg
            return result

        for attempt in range(1, self.max_attempts + 1):
            logging.info(f"Verification attempt {attempt}/{self.max_attempts} for {student_id}")
            if image_input is None or image_input == 'webcam':
                image_input = self.capture_live_image()
            is_verified, similarity, message = self.verify_face_advanced(student_id, image_input, stored_embedding, attempt)
            attempt_result = {
                'attempt_number': attempt,
                'success': is_verified,
                'similarity': float(similarity),
                'message': message
            }
            result['attempts'].append(attempt_result)
            if is_verified:
                result['verification_result'] = True
                result['final_message'] = f"Verification successful on attempt {attempt}"
                break
            if attempt < self.max_attempts:
                logging.info(f"Attempt {attempt} failed. {self.max_attempts - attempt} attempts remaining.")

        if not result['verification_result']:
            result['final_message'] = f"Verification failed after {self.max_attempts} attempts"

        return result

# ------------------ Command-line interface ------------------
if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Face Verification System")
    parser.add_argument("--studentId", required=True, help="Student ID")
    parser.add_argument("--image_path", help="Path to image file (optional, uses webcam if omitted)")
    parser.add_argument("--config", default="config/model_config.json")
    args = parser.parse_args()

    verifier = FaceVerificationSystem(config_path=args.config)
    result = verifier.verify_with_retry(args.studentId, args.image_path)
    print(json.dumps(result, indent=2))
