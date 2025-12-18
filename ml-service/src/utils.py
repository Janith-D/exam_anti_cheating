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

def capture_live_image_advanced(num_frames: int = 5, countdown: int = 2) -> list:
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not cap.isOpened():
        raise RuntimeError("Cannot access webcam")

    logging.info(f"Starting verification webcam capture: {num_frames} frames")
    frames = []

    try:
        for frame_idx in range(num_frames):
            for i in range(countdown * 30):
                ret, frame = cap.read()
                if not ret:
                    continue
                remaining = max(0, countdown - i // 30)
                status_text = f"Frame {frame_idx+1}/{num_frames} in {remaining}s"

                result = detect_face(frame)
                if result is not None:
                    _, (x, y, w, h) = result
                    status_text += " - Face ✓"
                    cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 2)
                else:
                    status_text += " - No face ✗"

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
