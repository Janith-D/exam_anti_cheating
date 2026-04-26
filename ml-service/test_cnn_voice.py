"""
test_cnn_voice.py
End-to-end test of the CNN voice stack.
Tests: enroll, same-speaker verify (should pass), cross-speaker verify (should fail).
"""
import sys, os, base64, json
sys.path.insert(0, 'src')

from voice_stack import VoiceStack

VOICE_DATA = r"D:\voice-data-20260424T144123Z-3-001\voice-data-20260424T144123Z-3-001\voice-data"

def wav_to_b64(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("ascii")

def wav_path(speaker, filename):
    return os.path.join(VOICE_DATA, speaker, "wav", filename)

print("=" * 60)
print("CNN Voice Stack — End-to-End Test")
print("=" * 60)

vs = VoiceStack("config/model_config.json")
print(f"\n[INIT] Backend : {vs._speaker_backend}")
print(f"[INIT] CNN ready: {vs._cnn_backend is not None and vs._cnn_backend.is_loaded()}")

# ── Test 1: Enroll voice2 as student "student_voice2" ─────────────────────
print("\n── Test 1: Enroll voice2 ─────────────────────────────────")
enroll_audio = wav_to_b64(wav_path("voice2", "New Recording 41-1.wav"))
result = vs.enroll_voice("student_voice2", enroll_audio)
print(f"  success          : {result.get('success')}")
print(f"  voiceQuality     : {result.get('voiceQuality', 0):.3f}")
print(f"  reasonCodes      : {result.get('reasonCodes')}")
if not result.get("success"):
    print(f"  error            : {result.get('error')}")

# ── Test 2: Enroll voice1 as student "student_voice1" 
print("\n── Test 2: Enroll voice1 ─────────────────────────────────")
enroll2 = wav_to_b64(wav_path("voice1", "New Recording 16-1.wav"))
result2 = vs.enroll_voice("student_voice1", enroll2)
print(f"  success          : {result2.get('success')}")

# ── Test 3: Verify voice2 with a DIFFERENT voice2 sample (should pass) ────
print("\n── Test 3: Verify voice2 — same speaker, different sample ─")
verify_audio = wav_to_b64(wav_path("voice2", "New Recording 42-1.wav"))
v_result = vs.verify_voice("student_voice2", verify_audio)
print(f"  success          : {v_result.get('success')}")
print(f"  voiceSimilarity  : {v_result.get('voiceSimilarity', 0):.3f}")
print(f"  reasonCodes      : {v_result.get('reasonCodes')}")

# ── Test 4: Verify voice2 student with voice1 audio (should FAIL) ─────────
print("\n── Test 4: Verify voice2 — WRONG speaker (voice1 audio) ──")
wrong_audio = wav_to_b64(wav_path("voice1", "New Recording 30-1.wav"))
w_result = vs.verify_voice("student_voice2", wrong_audio)
print(f"  success          : {w_result.get('success')}  ← should be False")
print(f"  voiceSimilarity  : {w_result.get('voiceSimilarity', 0):.3f}")
print(f"  reasonCodes      : {w_result.get('reasonCodes')}")

print("\n" + "=" * 60)
print("SUMMARY")
print("=" * 60)
t1 = result.get("success", False)
t3 = v_result.get("success", False)
t4 = not w_result.get("success", True)
print(f"  Enroll voice2          : {'PASS' if t1 else 'FAIL'}")
print(f"  Verify same speaker    : {'PASS' if t3 else 'FAIL'}")
print(f"  Reject wrong speaker   : {'PASS' if t4 else 'FAIL'}")
all_pass = t1 and t3 and t4
print(f"\n  Overall: {'ALL TESTS PASSED ✓' if all_pass else 'SOME TESTS FAILED ✗'}")

# Clean up test enrollment
try:
    os.remove(vs.enrolled_dir / "student_voice1_voice_emb.npy")
    os.remove(vs.enrolled_dir / "student_voice2_voice_emb.npy")
    print("\n  (Test enrollments cleaned up from disk)")
except FileNotFoundError:
    pass
