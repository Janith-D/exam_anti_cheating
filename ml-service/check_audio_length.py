import glob
import librosa
import numpy as np

files = glob.glob(r"D:\voice-data-20260424T144123Z-3-001\voice-data-20260424T144123Z-3-001\voice-data\*\wav\*.wav")
durations = []
for f in files[:20]: # Check first 20 files
    y, sr = librosa.load(f, sr=None)
    durations.append(len(y) / sr)

print(f"Average duration: {np.mean(durations):.2f}s")
print(f"Min: {np.min(durations):.2f}s, Max: {np.max(durations):.2f}s")
