import os
import sys
import json
import base64
import requests
from io import BytesIO

API_BASE_URL = "http://127.0.0.1:5000"

def test_enrollment_and_verification(student_id, face_image_path, voice_audio_path=None):
    print(f"--- Testing Face & Voice Embedding Stack ---")
    print(f"Student ID: {student_id}")
    print(f"Face Image: {face_image_path}")
    print(f"Voice Audio: {voice_audio_path if voice_audio_path else 'None'}\n")

    # 1. ENROLL FACE
    print(">>> 1a. Enrolling Face Identity...")
    enroll_url = f"{API_BASE_URL}/enroll"
    files = {}
    
    if face_image_path and os.path.exists(face_image_path):
        files["image"] = open(face_image_path, "rb")  # Fixed key to 'image'
    else:
        print("[!] ERROR: Face image not found or missing.")
        return

    data = {"studentId": student_id}  # Fixed key to 'studentId'

    response = requests.post(enroll_url, data=data, files=files)
    print(f"Face Enroll Status Code: {response.status_code}")
    try:
        print("Face Enroll Response:")
        print(json.dumps(response.json(), indent=2))
    except Exception as e:
        print(f"Error reading JSON: {response.text}")
    
    # Close files
    for file in files.values():
        file.close()

    if response.status_code != 200 or (response.json() and not response.json().get("success")):
        print("[!] Face Enrollment failed. Aborting verification test.")
        return

    # 1b. ENROLL VOICE (if provided)
    if voice_audio_path and os.path.exists(voice_audio_path):
        print("\n>>> 1b. Enrolling Voice Identity...")
        voice_enroll_url = f"{API_BASE_URL}/voice/enroll"
        with open(voice_audio_path, "rb") as f:
            audio_bytes = f.read()
            # Construct a data URI which the backend requires for voice JSON
            b64_audio = base64.b64encode(audio_bytes).decode('ascii')
            ext = voice_audio_path.split('.')[-1]
            data_uri = f"data:audio/{ext};base64,{b64_audio}"

        voice_payload = {
            "studentId": student_id,
            "audio": data_uri
        }
        
        voice_response = requests.post(voice_enroll_url, json=voice_payload)
        print(f"Voice Enroll Status Code: {voice_response.status_code}")
        try:
            print("Voice Enroll Response:")
            print(json.dumps(voice_response.json(), indent=2))
        except:
            print(f"Error reading JSON: {voice_response.text}")

    print("\n>>> 2. Verifying Identity...")
    
    # 2. VERIFICATION
    verify_url = f"{API_BASE_URL}/verify"
    
    verify_files = {}
    # Key must be 'image'
    verify_files["image"] = open(face_image_path, "rb")
    
    # Send Voice through 'audio' form file 
    if voice_audio_path and os.path.exists(voice_audio_path):
        verify_files["audio"] = open(voice_audio_path, "rb")

    # Use 'studentId' instead of 'student_id'
    verify_data = {
        "studentId": student_id,
        "client_latency_ms": 15
    }

    verify_response = requests.post(verify_url, data=verify_data, files=verify_files)
    print(f"Verify Status Code: {verify_response.status_code}")
    try:
        print("Verify Response:")
        print(json.dumps(verify_response.json(), indent=2))
    except:
         print(f"Verify Response: {verify_response.text}")

    # Close files
    for file in verify_files.values():
        file.close()
        
    print("\n--- Test Complete ---")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python test_verification.py <student_id> <path_to_face_image> [path_to_voice_audio]")
        print("Example: python test_verification.py test_student_01 my_face.jpg my_voice.webm")
        sys.exit(1)
        
    student_id = sys.argv[1]
    face_path = sys.argv[2]
    voice_path = sys.argv[3] if len(sys.argv) > 3 else None
    
    test_enrollment_and_verification(student_id, face_path, voice_path)
