"""
Test Liveness Detection System
Quick script to test the liveness detection features
"""

import requests
import json
import base64
import cv2
import time
from pathlib import Path

API_URL = "http://localhost:5000"

def capture_frames_from_webcam(num_frames=5, delay=1):
    """Capture frames from webcam for testing"""
    print("📷 Opening webcam...")
    cap = cv2.VideoCapture(0)
    
    if not cap.isOpened():
        print("❌ Cannot open webcam")
        return None
    
    frames = []
    instructions = [
        "Look at camera",
        "Blink your eyes",
        "Move your head slightly left",
        "Move your head slightly right",
        "Look at camera again"
    ]
    
    print(f"\n🎥 Capturing {num_frames} frames...")
    print("=" * 50)
    
    for i in range(num_frames):
        instruction = instructions[i] if i < len(instructions) else "Hold steady"
        print(f"\nFrame {i+1}/{num_frames}: {instruction}")
        
        # Show countdown
        for countdown in range(delay, 0, -1):
            ret, frame = cap.read()
            if ret:
                # Display frame with instruction
                display_frame = frame.copy()
                cv2.putText(display_frame, instruction, (20, 50),
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                cv2.putText(display_frame, f"Capturing in {countdown}s", (20, 100),
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 255), 2)
                cv2.imshow('Liveness Test - Press Q to cancel', display_frame)
                
                if cv2.waitKey(1000) & 0xFF == ord('q'):
                    cap.release()
                    cv2.destroyAllWindows()
                    return None
        
        # Capture frame
        ret, frame = cap.read()
        if not ret:
            print(f"❌ Failed to capture frame {i+1}")
            continue
        
        # Convert to base64
        _, buffer = cv2.imencode('.jpg', frame)
        frame_base64 = base64.b64encode(buffer).decode('utf-8')
        frames.append(f"data:image/jpeg;base64,{frame_base64}")
        print(f"✅ Frame {i+1} captured")
    
    cap.release()
    cv2.destroyAllWindows()
    
    print("\n" + "=" * 50)
    print(f"✅ Captured {len(frames)} frames successfully")
    return frames

def test_liveness_check(frames):
    """Test the /liveness-check endpoint"""
    print("\n🔬 Testing Liveness Detection...")
    print("=" * 50)
    
    try:
        response = requests.post(
            f"{API_URL}/liveness-check",
            json={"frames": frames},
            headers={"Content-Type": "application/json"},
            timeout=30
        )
        
        result = response.json()
        
        if response.status_code == 200:
            print("\n✅ LIVENESS CHECK PASSED!")
            print(f"Overall Score: {result['details']['overall_score']}/100")
            print("\nCheck Details:")
            
            for check_name, check_data in result['details']['checks'].items():
                status = "✅ PASS" if check_data['passed'] else "❌ FAIL"
                score = check_data['score']
                print(f"  {status} {check_name.replace('_', ' ').title()}: {score} points")
                
                if not check_data['passed'] and 'details' in check_data:
                    details = check_data['details']
                    if isinstance(details, dict) and 'spoofing_indicators' in details:
                        if details['spoofing_indicators']:
                            print(f"    Reasons:")
                            for reason in details['spoofing_indicators']:
                                print(f"      - {reason}")
        else:
            print("\n❌ LIVENESS CHECK FAILED!")
            print(f"Status Code: {response.status_code}")
            if 'details' in result:
                print(f"Overall Score: {result['details']['overall_score']}/100")
                print("\nFailed Checks:")
                for check_name, check_data in result['details']['checks'].items():
                    if not check_data['passed']:
                        print(f"  ❌ {check_name.replace('_', ' ').title()}")
            else:
                print(f"Error: {result.get('error', 'Unknown error')}")
        
        print("\n" + "=" * 50)
        return response.status_code == 200
        
    except requests.exceptions.RequestException as e:
        print(f"\n❌ Request failed: {e}")
        print("Make sure the ML service is running on http://localhost:5000")
        return False

def test_verify_with_liveness(student_id, frames):
    """Test the /verify-with-liveness endpoint"""
    print(f"\n🔐 Testing Verification with Liveness for Student {student_id}...")
    print("=" * 50)
    
    try:
        response = requests.post(
            f"{API_URL}/verify-with-liveness",
            json={
                "studentId": student_id,
                "frames": frames
            },
            headers={"Content-Type": "application/json"},
            timeout=30
        )
        
        result = response.json()
        
        if response.status_code == 200:
            print("\n✅ VERIFICATION SUCCESSFUL!")
            print(f"Student ID: {result['studentId']}")
            
            if 'liveness_check' in result and result['liveness_check']:
                liveness = result['liveness_check']
                print(f"Liveness Score: {liveness['overall_score']}/100")
            
            if 'attempts' in result:
                for attempt in result['attempts']:
                    similarity = attempt.get('similarity', 0)
                    print(f"Similarity Score: {similarity:.3f}")
        else:
            print("\n❌ VERIFICATION FAILED!")
            print(f"Status Code: {response.status_code}")
            print(f"Message: {result.get('final_message', result.get('error', 'Unknown error'))}")
            
            if 'liveness_check' in result and result['liveness_check']:
                liveness = result['liveness_check']
                print(f"Liveness Score: {liveness['overall_score']}/100")
        
        print("\n" + "=" * 50)
        return response.status_code == 200
        
    except requests.exceptions.RequestException as e:
        print(f"\n❌ Request failed: {e}")
        print("Make sure the ML service is running on http://localhost:5000")
        return False

def main():
    print("=" * 50)
    print("    LIVENESS DETECTION TEST SUITE")
    print("=" * 50)
    print("\nThis script will:")
    print("1. Capture 5 frames from your webcam")
    print("2. Test standalone liveness detection")
    print("3. Test verification with liveness (optional)")
    print("\nMake sure:")
    print("- ML service is running (python src/api.py)")
    print("- Webcam is available")
    print("- Good lighting conditions")
    print("\n" + "=" * 50)
    
    input("\nPress ENTER to start...")
    
    # Capture frames
    frames = capture_frames_from_webcam(num_frames=5, delay=2)
    
    if not frames:
        print("\n❌ Frame capture cancelled or failed")
        return
    
    # Test liveness check
    liveness_passed = test_liveness_check(frames)
    
    # Optionally test verification with liveness
    if liveness_passed:
        test_verify = input("\n🔐 Test verification with liveness? (y/n): ").lower()
        if test_verify == 'y':
            student_id = input("Enter Student ID: ")
            test_verify_with_liveness(student_id, frames)
    
    print("\n✨ Test completed!")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Test cancelled by user")
    except Exception as e:
        print(f"\n❌ Error: {e}")
