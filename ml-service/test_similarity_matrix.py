import sys, os, base64
sys.path.insert(0, 'src')
from voice_cnn_backend import VoiceCNNBackend
import numpy as np
import glob

VOICE_DATA = r"D:\voice-data-20260424T144123Z-3-001\voice-data-20260424T144123Z-3-001\voice-data"
model_path = r"D:\PROJECT\Exam-Anti-Cheating\ml-service\models\voice\cnn\voice.pth"

backend = VoiceCNNBackend(model_path)
embeddings = {}

# Get an embedding for each speaker
for i in range(1, 8):
    spk = f"voice{i}"
    wav_files = glob.glob(os.path.join(VOICE_DATA, spk, "wav", "*.wav"))
    if wav_files:
        with open(wav_files[0], "rb") as f:
            emb = backend.predict_from_bytes(f.read())
            embeddings[spk] = emb

# Print similarity matrix
print("Speaker Similarities:")
for s1, emb1 in embeddings.items():
    for s2, emb2 in embeddings.items():
        sim = np.dot(emb1, emb2)
        print(f"{s1} vs {s2}: {sim:.4f}")
    print("-" * 30)
