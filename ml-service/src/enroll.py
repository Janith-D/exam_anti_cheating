import argparse
import os
import cv2
import numpy as np
import logging
import json
from datetime import datetime
from pathlib import Path
from typing import Optional, Tuple, Union
from utils import load_config, preprocess_image, detect_face, compute_embedding

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('enrollment.log'),
        logging.StreamHandler()
    ]
)

class FaceEnrollmentSystem:
    def __init__(self, config_path: str):
        self.config = load_config(config_path)
        self.enrolled_dir = Path("data/enrolled")
        self.temp_dir = Path("data/temp")
        self.metadata_dir = Path("data/metadata")
        for directory in [self.enrolled_dir, self.temp_dir, self.metadata_dir]:
            directory.mkdir(parents=True, exist_ok=True)

    def capture_live_image(self, countdown: int = 3) -> str:
        cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
        if not cap.isOpened():
            raise RuntimeError("Cannot access webcam")
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

        logging.info("Starting face capture sequence...")
        try:
            for i in range(countdown * 30):
                ret, frame = cap.read()
                if not ret:
                    continue
                remaining = max(0, countdown - i // 30)
                cv2.putText(frame, f"Capturing in {remaining}s", (50, 50),
                            cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                cv2.imshow("Face Enrollment", frame)
                if cv2.waitKey(33) & 0xFF == ord("q"):
                    raise KeyboardInterrupt("Cancelled by user")

            ret, frame = cap.read()
            if not ret:
                raise RuntimeError("Failed to capture frame")
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            temp_path = self.temp_dir / f"live_{timestamp}.jpg"
            cv2.imwrite(str(temp_path), frame)
            logging.info(f"Live image saved: {temp_path}")
            return str(temp_path)
        finally:
            cap.release()
            cv2.destroyAllWindows()

    def validate_face_quality(self, face: np.ndarray) -> Tuple[bool, str]:
        if face is None or face.size == 0:
            return False, "No face detected"
        gray_face = cv2.cvtColor(face, cv2.COLOR_BGR2GRAY)
        blur_score = cv2.Laplacian(gray_face, cv2.CV_64F).var()
        min_blur_score = self.config.get("min_blur_score", 100)
        if blur_score < min_blur_score:
            return False, f"Face too blurry (score {blur_score:.2f})"
        return True, "Face quality acceptable"

    def save_enrollment_metadata(self, studentId: str, image_path: str, embedding_quality: float = None):
        metadata = {
            "studentId": studentId,
            "enrollment_date": datetime.now().isoformat(),
            "image_path": image_path,
            "embedding_quality": embedding_quality,
            "config_version": self.config.get("version", "1.0")
        }
        metadata_path = self.metadata_dir / f"{studentId}_metadata.json"
        with open(metadata_path, "w") as f:
            json.dump(metadata, f, indent=2)
        logging.info(f"Metadata saved: {metadata_path}")

    def check_existing_enrollment(self, studentId: str) -> bool:
        return (self.enrolled_dir / f"{studentId}.npy").exists()


    def enroll_face(self, studentId: str, image_path: Optional[Union[str, None]] = None, overwrite: bool = False) -> dict:
        temp_image_path = None
        try:
            if self.check_existing_enrollment(studentId) and not overwrite:
                logging.warning(f"User {studentId} already enrolled.")
                return {"success": False, "error": "User already enrolled"}

            if image_path is None:
                temp_image_path = self.capture_live_image()
                image_path = temp_image_path

            img = preprocess_image(image_path, tuple(self.config["image_size"]))

            # Ensure min_face_size is tuple of ints
            min_face_size = self.config["min_face_size"]
            if isinstance(min_face_size, int):
                min_face_size = (min_face_size, min_face_size)
            face = detect_face(img, min_face_size)

            if face is None:
                return {"success": False, "error": "No face detected in the image"}

            valid, msg = self.validate_face_quality(face)
            if not valid:
                return {"success": False, "error": msg}

            face_resized = cv2.resize(face, tuple(self.config["face_size"]))
            embedding = compute_embedding(face_resized)
            if embedding is None:
                return {"success": False, "error": "Failed to compute embedding"}

            embedding_quality = float(np.linalg.norm(embedding))
            np.save(self.enrolled_dir / f"{studentId}.npy", embedding)
            self.save_enrollment_metadata(studentId, image_path, embedding_quality)
            logging.info(f"User {studentId} enrolled successfully (quality {embedding_quality:.3f})")

            return {
                "success": True, 
                "embedding": embedding,
                "quality": embedding_quality
            }

        except KeyboardInterrupt:
            logging.info("Enrollment cancelled by user")
            return {"success": False, "error": "Enrollment cancelled by user"}
        except Exception as e:
            logging.error(f"Enrollment failed for {studentId}: {e}")
            return {"success": False, "error": str(e)}
        finally:
            if temp_image_path and os.path.exists(temp_image_path):
                os.remove(temp_image_path)


    def list_enrolled_users(self) -> list:
        return [f.stem for f in self.enrolled_dir.glob("*.npy")]

    def get_enrollment_info(self, studentId: str) -> dict:
        if not self.check_existing_enrollment(studentId):
            return {"error": "User not enrolled"}
        metadata_path = self.metadata_dir / f"{studentId}_metadata.json"
        if metadata_path.exists():
            with open(metadata_path, "r") as f:
                return json.load(f)
        return {"studentId": studentId, "metadata": "Not available"}


# ------------------- CLI -------------------

def main():
    parser = argparse.ArgumentParser(description="Face Enrollment System")
    parser.add_argument("--studentId", help="Student ID for enrollment")
    parser.add_argument("--image_path", help="Optional image path (base64 or file)")
    parser.add_argument("--overwrite", action="store_true", help="Overwrite existing enrollment")
    parser.add_argument("--list", action="store_true", help="List enrolled users")
    parser.add_argument("--info", help="Get enrollment info for a student")
    parser.add_argument("--config", default=r"D:\PROJECT\SecureVote\ai\config\model_config.json")
    args = parser.parse_args()

    system = FaceEnrollmentSystem(args.config)

    if args.list:
        users = system.list_enrolled_users()
        print("Enrolled users:", users)
        return 0

    if args.info:
        info = system.get_enrollment_info(args.info)
        print(json.dumps(info, indent=2))
        return 0

    if not args.studentId:
        parser.error("--studentId is required for enrollment")

    success = system.enroll_face(args.studentId, args.image_path, args.overwrite)
    return 0 if success else 1


if __name__ == "__main__":
    exit(main())
