import numpy as np
import io
import av

def decode_audio_av(audio_bytes):
    try:
        with av.open(io.BytesIO(audio_bytes)) as container:
            audio_stream = container.streams.audio[0]
            # Convert to float32 mono
            resampler = av.AudioResampler(format='fltp', layout='mono')
            frames = []
            for frame in container.decode(audio_stream):
                frame.pts = None
                frames_resampled = resampler.resample(frame)
                # Resampler usually returns a tuple/list of frames, typically 1
                for f in frames_resampled:
                    # f.to_ndarray() returns (1, samples) for layout='mono' and format='fltp'
                    arr = f.to_ndarray()
                    frames.append(arr[0])

            waveform = np.concatenate(frames) if len(frames) > 0 else np.array([])
            return waveform, audio_stream.rate
    except Exception as e:
        print('AV Exception:', e)
        return None, None

