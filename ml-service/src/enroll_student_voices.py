import argparse
import base64
import os
import sys
from pathlib import Path
from voice_stack import VoiceStack

def main():
    parser = argparse.ArgumentParser(description="Batch Enroll Student Voice Dataset into SpeechBrain")
    parser.add_argument("--dir", type=str, required=True, help="Directory containing .wav files (e.g. 'student123.wav')")
    parser.add_argument("--config", default="config/model_config.json")
    args = parser.parse_args()

    config_path = Path(args.config)
    if not config_path.is_absolute():
        config_path = (Path.cwd() / config_path).resolve()

    print(f"Initializing VoiceStack using config: {config_path}")
    voice_system = VoiceStack(str(config_path))
    print(f"Loaded Models: {voice_system.model_info()}")

    dataset_dir = Path(args.dir)
    if not dataset_dir.exists() or not dataset_dir.is_dir():
        print(f"Dataset directory '{dataset_dir}' not found.")
        sys.exit(1)

    audio_files = list(dataset_dir.glob("*.wav")) + list(dataset_dir.glob("*.mp3")) + list(dataset_dir.glob("*.flac"))
    if not audio_files:
        print(f"No valid audio files found in {dataset_dir}.")
        sys.exit(1)

    print(f"Found {len(audio_files)} audio files. Beginning enrollment...")

    success_count = 0
    fail_count = 0

    for file_path in audio_files:
        student_id = file_path.stem
        with open(file_path, "rb") as f:
            audio_bytes = f.read()

        print(f"Enrolling {student_id} ({len(audio_bytes)} bytes)...", end=" ")
        b64_audio = base64.b64encode(audio_bytes).decode("ascii")

        try:
            result = voice_system.enroll_voice(student_id, b64_audio)
            if result.get("success"):
                print(f"[OK] Quality: {result.get('voiceQuality', 0):.2f}, SpoofRisk: {result.get('voiceSpoofProbability', 0):.2f}")
                success_count += 1
            else:
                reason = result.get('reasonCodes', result.get('error', 'Unknown'))
                print(f"[FAILED] Reason: {reason}")
                fail_count += 1
        except Exception as e:
            print(f"[ERROR] {e}")
            fail_count += 1

    print(f"\nEnrollment Complete. Successfully Enrolled: {success_count} / Failed: {fail_count}")

if __name__ == "__main__":
    main()