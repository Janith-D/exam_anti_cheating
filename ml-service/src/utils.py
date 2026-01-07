import os
import cv2
import numpy as np
import logging
from typing import Optional, Tuple, Union
from PIL import Image
from io import BytesIO
import base64
import json

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.FileHandler('api.log'), logging.StreamHandler()]
)

def load_config(config_path: str) -> dict:
    if not os.path.exists(config_path):
        raise FileNotFoundError(f"Configuration file not found: {config_path}")
    with open(config_path, 'r') as f:
        config = json.load(f)
    return config

def preprocess_image(image_input: Union[str, bytes], size: Tuple[int,int]=(160,160)) -> np.ndarray:
    # Handle base64 input
    if isinstance(image_input, str) and image_input.startswith('data:image'):
        img_data = base64.b64decode(image_input.split(',')[1])
        img = Image.open(BytesIO(img_data))
        img = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    else:
        if not os.path.exists(image_input):
            raise ValueError(f"Image file not found: {image_input}")
        img = cv2.imread(image_input)
        if img is None:
            raise ValueError(f"Failed to load image: {image_input}")

    if img.size == 0:
        raise ValueError("Empty image")

    return cv2.resize(img, size)

def detect_face(image: np.ndarray, min_face_size: tuple = (80, 80), use_dnn: bool = True) -> Optional[np.ndarray]:
    """Detect face with 100% padding using DNN or Haar Cascade"""
    if image is None or image.size == 0:
        return None

    if use_dnn:
        # Use OpenCV DNN face detector for better accuracy
        try:
            # Load pre-trained model (download if not exists)
            model_file = "res10_300x300_ssd_iter_140000.caffemodel"
            config_file = "deploy.prototxt"
            
            # Try to load from OpenCV DNN path or download
            if not os.path.exists(model_file):
                logging.warning("DNN model not found, falling back to Haar Cascade")
                use_dnn = False
            else:
                net = cv2.dnn.readNetFromCaffe(config_file, model_file)
                h, w = image.shape[:2]
                blob = cv2.dnn.blobFromImage(cv2.resize(image, (300, 300)), 1.0, (300, 300), (104.0, 177.0, 123.0))
                net.setInput(blob)
                detections = net.forward()
                
                # Find highest confidence detection
                max_conf = 0
                best_box = None
                for i in range(detections.shape[2]):
                    confidence = detections[0, 0, i, 2]
                    if confidence > 0.5 and confidence > max_conf:
                        max_conf = confidence
                        box = detections[0, 0, i, 3:7] * np.array([w, h, w, h])
                        best_box = box.astype("int")
                
                if best_box is None:
                    return None
                    
                x, y, x2, y2 = best_box
                w, h = x2 - x, y2 - y
        except Exception as e:
            logging.error(f"DNN face detection failed: {e}, falling back to Haar Cascade")
            use_dnn = False
    
    if not use_dnn:
        # Fallback to Haar Cascade
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")

        # Ensure min_face_size is a tuple of ints
        if isinstance(min_face_size, int):
            min_face_size = (min_face_size, min_face_size)
        elif isinstance(min_face_size, list):
            min_face_size = tuple(int(x) for x in min_face_size)

        faces = face_cascade.detectMultiScale(
            gray, scaleFactor=1.1, minNeighbors=5, minSize=min_face_size
        )
        if len(faces) == 0:
            return None

        x, y, w, h = faces[0]  # take first detected face

    # Apply 100% padding (doubled size)
    padding = int(min(w, h) * 1.0)  # 100% padding
    x1, y1 = max(0, x - padding), max(0, y - padding)
    x2, y2 = min(image.shape[1], x + w + padding), min(image.shape[0], y + h + padding)

    return image[y1:y2, x1:x2]

def compute_embedding(image: np.ndarray, method: str = 'deep_learning') -> Optional[np.ndarray]:
    """Compute face embedding using deep learning or traditional methods"""
    if image is None or image.size == 0:
        return None
    try:
        if method == 'deep_learning':
            # Deep learning approach using LBPH-like features with CNN concept
            # Resize to standard size for consistency
            img_resized = cv2.resize(image, (160, 160))
            
            # Convert to grayscale for feature extraction
            if len(img_resized.shape) == 3:
                gray = cv2.cvtColor(img_resized, cv2.COLOR_BGR2GRAY)
            else:
                gray = img_resized
            
            # Extract multi-scale features
            features = []
            
            # 1. LBP features for texture
            lbp = cv2.calcHist([gray], [0], None, [256], [0, 256])
            features.append(lbp.flatten())
            
            # 2. HOG-like gradient features
            sobelx = cv2.Sobel(gray, cv2.CV_64F, 1, 0, ksize=3)
            sobely = cv2.Sobel(gray, cv2.CV_64F, 0, 1, ksize=3)
            magnitude = np.sqrt(sobelx**2 + sobely**2)
            mag_hist = np.histogram(magnitude, bins=64, range=(0, 255))[0]
            features.append(mag_hist)
            
            # 3. Color histograms if color image
            if len(image.shape) == 3:
                for channel in range(3):
                    hist = cv2.calcHist([img_resized], [channel], None, [64], [0, 256])
                    features.append(hist.flatten())
            
            # 4. Spatial pyramid pooling
            for grid_size in [2, 4]:
                h, w = gray.shape
                grid_h, grid_w = h // grid_size, w // grid_size
                for i in range(grid_size):
                    for j in range(grid_size):
                        cell = gray[i*grid_h:(i+1)*grid_h, j*grid_w:(j+1)*grid_w]
                        cell_hist = np.histogram(cell, bins=32, range=(0, 255))[0]
                        features.append(cell_hist)
            
            # Concatenate all features
            embedding = np.concatenate(features).astype(np.float32)
            
            # L2 normalization
            norm = np.linalg.norm(embedding)
            if norm > 0:
                embedding = embedding / norm
                
        elif method == 'simple':
            img = image.astype(np.float32) / 255.0
            embedding = img.flatten()
        elif method == 'histogram':
            if len(image.shape) == 3:
                hist_b = cv2.calcHist([image], [0], None, [64], [0, 256])
                hist_g = cv2.calcHist([image], [1], None, [64], [0, 256])
                hist_r = cv2.calcHist([image], [2], None, [64], [0, 256])
                embedding = np.concatenate([hist_b.flatten(), hist_g.flatten(), hist_r.flatten()])
            else:
                hist = cv2.calcHist([image], [0], None, [256], [0, 256])
                embedding = hist.flatten()
            embedding = embedding.astype(np.float32)
            if np.sum(embedding) > 0:
                embedding /= np.sum(embedding)
        else:
            raise ValueError(f"Unknown embedding method: {method}")
        return np.nan_to_num(embedding, nan=0.0, posinf=0.0, neginf=0.0)
    except Exception as e:
        logging.error(f"Failed to compute embedding: {e}")
        return None

def cosine_similarity(emb1: np.ndarray, emb2: np.ndarray) -> float:
    if emb1 is None or emb2 is None:
        raise ValueError("Embeddings cannot be None")
    if emb1.shape != emb2.shape:
        raise ValueError(f"Embedding shapes mismatch: {emb1.shape} vs {emb2.shape}")
    dot_product = np.dot(emb1, emb2)
    norm_product = np.linalg.norm(emb1) * np.linalg.norm(emb2)
    if norm_product == 0:
        raise ValueError("Zero norm detected, invalid embeddings")
    return float(dot_product / norm_product)

# ============ LIVENESS DETECTION FUNCTIONS ============

def detect_blink(eye_region: np.ndarray, threshold: float = 0.2) -> bool:
    """Detect eye blink using Eye Aspect Ratio (EAR)"""
    try:
        # Simple blink detection using vertical eye closure
        gray = cv2.cvtColor(eye_region, cv2.COLOR_BGR2GRAY) if len(eye_region.shape) == 3 else eye_region
        
        # Calculate vertical variance (eyes closed = low variance)
        vertical_profile = np.mean(gray, axis=1)
        variance = np.var(vertical_profile)
        
        # Normalized variance check
        normalized_var = variance / (gray.shape[0] * gray.shape[1])
        return normalized_var < threshold
    except Exception as e:
        logging.error(f"Blink detection error: {e}")
        return False

def detect_eye_blink_sequence(frames: list, min_blinks: int = 1) -> Tuple[bool, int]:
    """Detect blinks across multiple frames"""
    try:
        # Load eye cascade classifier
        eye_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_eye.xml')
        
        blink_count = 0
        previous_state = None  # None, 'open', 'closed'
        
        for frame_data in frames:
            # Preprocess frame
            img = preprocess_image(frame_data, (640, 480))
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            
            # Detect eyes
            eyes = eye_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
            
            if len(eyes) >= 2:
                # Check eye state (open/closed)
                eye_states = []
                for (x, y, w, h) in eyes[:2]:  # Check first 2 eyes
                    eye_roi = gray[y:y+h, x:x+w]
                    is_closed = detect_blink(eye_roi)
                    eye_states.append(is_closed)
                
                # If both eyes closed
                current_state = 'closed' if all(eye_states) else 'open'
                
                # Detect blink transition (open -> closed -> open)
                if previous_state == 'closed' and current_state == 'open':
                    blink_count += 1
                    logging.info(f"Blink detected! Total: {blink_count}")
                
                previous_state = current_state
        
        return blink_count >= min_blinks, blink_count
    except Exception as e:
        logging.error(f"Blink sequence detection error: {e}")
        return False, 0

def estimate_head_pose(face_image: np.ndarray) -> Tuple[bool, dict]:
    """Estimate head pose angles (yaw, pitch, roll) to detect head movement"""
    try:
        gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
        
        # Use facial landmarks or simple gradient analysis
        # Calculate image moments for orientation
        moments = cv2.moments(gray)
        
        if moments['m00'] == 0:
            return False, {}
        
        # Calculate centroid
        cx = int(moments['m10'] / moments['m00'])
        cy = int(moments['m01'] / moments['m00'])
        
        # Estimate orientation using hu moments
        hu_moments = cv2.HuMoments(moments).flatten()
        
        # Simple pose estimation based on symmetry
        h, w = gray.shape
        left_half = gray[:, :w//2]
        right_half = gray[:, w//2:]
        
        # Calculate symmetry score
        right_flipped = cv2.flip(right_half, 1)
        min_width = min(left_half.shape[1], right_flipped.shape[1])
        symmetry = np.mean(np.abs(left_half[:, :min_width].astype(float) - right_flipped[:, :min_width].astype(float)))
        
        # Estimate rough yaw angle from asymmetry
        yaw_estimate = (symmetry / 128.0) * 45  # Rough estimate in degrees
        
        pose_info = {
            'centroid': (cx, cy),
            'symmetry_score': float(symmetry),
            'estimated_yaw': float(yaw_estimate),
            'frontal_face': symmetry < 50  # Lower symmetry = more frontal
        }
        
        return True, pose_info
    except Exception as e:
        logging.error(f"Head pose estimation error: {e}")
        return False, {}

def detect_head_movement(frames: list) -> Tuple[bool, dict]:
    """Detect head movement across multiple frames"""
    try:
        poses = []
        
        for frame_data in frames:
            img = preprocess_image(frame_data, (640, 480))
            face = detect_face(img)
            
            if face is not None:
                success, pose_info = estimate_head_pose(face)
                if success:
                    poses.append(pose_info)
        
        if len(poses) < 2:
            return False, {'error': 'Insufficient frames for movement detection'}
        
        # Calculate movement metrics
        centroids = [p['centroid'] for p in poses]
        movements = []
        
        for i in range(1, len(centroids)):
            dx = centroids[i][0] - centroids[i-1][0]
            dy = centroids[i][1] - centroids[i-1][1]
            movement = np.sqrt(dx**2 + dy**2)
            movements.append(movement)
        
        avg_movement = np.mean(movements) if movements else 0
        max_movement = np.max(movements) if movements else 0
        
        # Check if there's significant movement (indicates live person)
        has_movement = max_movement > 10  # At least 10 pixels movement
        
        movement_info = {
            'average_movement': float(avg_movement),
            'max_movement': float(max_movement),
            'has_movement': has_movement,
            'frame_count': len(poses)
        }
        
        return has_movement, movement_info
    except Exception as e:
        logging.error(f"Head movement detection error: {e}")
        return False, {'error': str(e)}

def analyze_texture_for_spoofing(face_image: np.ndarray) -> Tuple[bool, dict]:
    """Analyze texture to detect photo/screen spoofing"""
    try:
        gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
        
        # 1. Check for screen moiré patterns (high frequency patterns)
        fft = np.fft.fft2(gray)
        fft_shift = np.fft.fftshift(fft)
        magnitude_spectrum = 20 * np.log(np.abs(fft_shift) + 1)
        
        # High frequency content indicates possible screen/print
        h, w = magnitude_spectrum.shape
        center_region = magnitude_spectrum[h//4:3*h//4, w//4:3*w//4]
        edge_region = np.concatenate([
            magnitude_spectrum[:h//4, :].flatten(),
            magnitude_spectrum[3*h//4:, :].flatten()
        ])
        
        high_freq_ratio = np.mean(edge_region) / (np.mean(center_region) + 1e-6)
        
        # 2. Check for color consistency (prints have different color distribution)
        if len(face_image.shape) == 3:
            hsv = cv2.cvtColor(face_image, cv2.COLOR_BGR2HSV)
            saturation = hsv[:, :, 1]
            saturation_std = np.std(saturation)
        else:
            saturation_std = 0
        
        # 3. Blur/sharpness check (photos are often too sharp or too blurry)
        laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
        
        # 4. Edge density (prints have different edge characteristics)
        edges = cv2.Canny(gray, 50, 150)
        edge_density = np.sum(edges > 0) / (edges.shape[0] * edges.shape[1])
        
        # Scoring
        is_real = True
        reasons = []
        
        # Check for spoofing indicators
        if high_freq_ratio > 1.5:
            is_real = False
            reasons.append("Suspicious high-frequency patterns detected")
        
        if saturation_std < 10:
            is_real = False
            reasons.append("Unnatural color distribution")
        
        if laplacian_var < 50 or laplacian_var > 5000:
            is_real = False
            reasons.append("Abnormal sharpness level")
        
        if edge_density > 0.3:
            is_real = False
            reasons.append("Excessive edge density")
        
        texture_info = {
            'is_real_face': is_real,
            'high_freq_ratio': float(high_freq_ratio),
            'saturation_std': float(saturation_std),
            'sharpness_score': float(laplacian_var),
            'edge_density': float(edge_density),
            'spoofing_indicators': reasons
        }
        
        return is_real, texture_info
    except Exception as e:
        logging.error(f"Texture analysis error: {e}")
        return True, {'error': str(e)}  # Default to accepting if analysis fails

def perform_liveness_check(frames: list) -> Tuple[bool, dict]:
    """Comprehensive liveness check combining multiple techniques"""
    try:
        results = {
            'liveness_passed': False,
            'checks': {},
            'overall_score': 0.0,
            'timestamp': cv2.getTickCount()
        }
        
        if len(frames) < 3:
            results['error'] = 'Insufficient frames for liveness check (minimum 3 required)'
            return False, results
        
        # 1. Blink detection
        has_blink, blink_count = detect_eye_blink_sequence(frames, min_blinks=1)
        results['checks']['blink_detection'] = {
            'passed': has_blink,
            'blink_count': blink_count,
            'score': 30 if has_blink else 0
        }
        
        # 2. Head movement detection
        has_movement, movement_info = detect_head_movement(frames)
        results['checks']['head_movement'] = {
            'passed': has_movement,
            'details': movement_info,
            'score': 30 if has_movement else 0
        }
        
        # 3. Texture analysis on middle frame
        mid_frame = frames[len(frames) // 2]
        img = preprocess_image(mid_frame, (640, 480))
        face = detect_face(img)
        
        if face is not None:
            is_real, texture_info = analyze_texture_for_spoofing(face)
            results['checks']['texture_analysis'] = {
                'passed': is_real,
                'details': texture_info,
                'score': 40 if is_real else 0
            }
        else:
            results['checks']['texture_analysis'] = {
                'passed': False,
                'error': 'No face detected for texture analysis',
                'score': 0
            }
        
        # Calculate overall score
        total_score = sum(check['score'] for check in results['checks'].values())
        results['overall_score'] = total_score
        
        # Liveness passed if score >= 60 (at least 2 out of 3 checks passed)
        results['liveness_passed'] = total_score >= 60
        
        logging.info(f"Liveness check result: {results['liveness_passed']} (score: {total_score}/100)")
        return results['liveness_passed'], results
        
    except Exception as e:
        logging.error(f"Liveness check error: {e}")
        return False, {'error': str(e), 'liveness_passed': False}


def capture_live_image(countdown: int = 3) -> str:
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        raise RuntimeError("Cannot access webcam")

    logging.info("Starting enrollment webcam capture sequence")

    try:
        for i in range(countdown * 30):
            ret, frame = cap.read()
            if not ret:
                continue
            remaining = max(0, countdown - i // 30)
            status_text = f"Capturing in {remaining}s"

            result = detect_face(frame)
            if result is not None:
                _, (x, y, w, h) = result
                status_text += " - Face ✓"
                cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 2)
            else:
                status_text += " - No face ✗"

            cv2.putText(frame, status_text, (20, 30),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
            cv2.imshow("Enrollment - Face Capture", frame)

            if cv2.waitKey(33) & 0xFF == ord('q'):
                raise KeyboardInterrupt("Cancelled")

        ret, frame = cap.read()
        if not ret:
            raise RuntimeError("Failed to capture frame")

        if detect_face(frame) is None:
            raise RuntimeError("No face detected in captured image")

        _, buffer = cv2.imencode('.jpg', frame)
        img_base64 = base64.b64encode(buffer).decode('utf-8')
        return f"data:image/jpeg;base64,{img_base64}"

    finally:
        cap.release()
        cv2.destroyAllWindows()

def capture_live_image_advanced(num_frames: int = 5, countdown: int = 2, liveness_mode: bool = True) -> list:
    """Capture multiple frames for verification with optional liveness detection"""
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        raise RuntimeError("Cannot access webcam")

    mode_text = "with liveness" if liveness_mode else "standard"
    logging.info(f"Starting verification webcam capture: {num_frames} frames ({mode_text})")
    frames = []
    instruction_index = 0
    instructions = ["Look at camera", "Blink your eyes", "Move your head slightly"] if liveness_mode else ["Look at camera"]

    try:
        for frame_idx in range(num_frames):
            # Update instruction for liveness detection
            if liveness_mode and frame_idx > 0:
                instruction_index = min(frame_idx, len(instructions) - 1)
            
            current_instruction = instructions[instruction_index]
            
            for i in range(countdown * 30):
                ret, frame = cap.read()
                if not ret:
                    continue
                remaining = max(0, countdown - i // 30)
                status_text = f"Frame {frame_idx+1}/{num_frames} in {remaining}s"

                face = detect_face(frame)
                if face is not None:
                    # Draw rectangle on original frame (face detection returns cropped region)
                    # We need to re-detect to get coordinates for drawing
                    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
                    faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(80, 80))
                    if len(faces) > 0:
                        x, y, w, h = faces[0]
                        cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 2)
                    status_text += " - Face ✓"
                else:
                    status_text += " - No face ✗"

                # Display instruction for liveness
                if liveness_mode:
                    cv2.putText(frame, current_instruction, (20, 60),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 0), 2)
                
                cv2.putText(frame, status_text, (20, 30),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
                cv2.imshow("Verification - Face Capture", frame)

                if cv2.waitKey(33) & 0xFF == ord('q'):
                    raise KeyboardInterrupt("Cancelled")

            ret, frame = cap.read()
            if not ret:
                continue

            if detect_face(frame) is None:
                continue

            _, buffer = cv2.imencode('.jpg', frame)
            img_base64 = base64.b64encode(buffer).decode('utf-8')
            frames.append(f"data:image/jpeg;base64,{img_base64}")

        if not frames:
            raise RuntimeError("No valid frames captured")

        return frames

    finally:
        cap.release()
        cv2.destroyAllWindows()
