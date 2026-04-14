import os
import shutil
import json
from pathlib import Path

# Override HF and SpeechBrain behavior globally BEFORE any speechbrain imports happen
# This absolutely forces 'COPY' over 'SYMLINK' to sidestep WinError 1314 on Windows
os.environ["SB_LOCAL_STRATEGY"] = "COPY"
os.environ["HUGGINGFACE_HUB_FORCE_DOWNLOAD"] = "1"

# Try to import torch and SpeechBrain
try:
    import torch
    from speechbrain.inference.speaker import EncoderClassifier
except ImportError:
    print("Error: torch or speechbrain missing. Run: pip install torch speechbrain")
    exit(1)

def download_models():
    print("=== Starting Offline Model Preparation ===")
    
    # 1. Load your config
    config_path = Path("../config/model_config.json")
    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)
        
    voice_config = config.get("voice", {})
    # Use the default remote source if it's already set to a local path or missing
    source_model = voice_config.get("speaker_source", "speechbrain/spkrec-ecapa-voxceleb")
    if "models" in source_model or "\\" in source_model or "/" in source_model and "speechbrain" not in source_model:
         source_model = "speechbrain/spkrec-ecapa-voxceleb" # Reset to default remote to download
    
    # 2. Set the exact folder we want it saved in physically
    local_save_dir = Path("../models/voice/speaker").resolve()
    os.makedirs(local_save_dir, exist_ok=True)
    
    print(f"\n[1/3] Downloading SpeechBrain Voice Model ({source_model})")
    print(f"      Saving to: {local_save_dir}")
    print("      (This may take a few minutes depending on your internet...)")
    
    # 3. Download using `huggingface_hub` directly so we explicitly bypass the broken SpeechBrain symlink code
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        print("Error: huggingface_hub missing. Run: pip install huggingface_hub")
        exit(1)

    print("      Downloading files directly from HuggingFace Hub without Symlinks...")
    snapshot_download(
        repo_id=source_model, 
        local_dir=str(local_save_dir),
        local_dir_use_symlinks=False
    )

    # Copy HuggingFace files to the target directory since we bypassed SpeechBrain downloader
    for root, _, files in os.walk(local_save_dir):
        for file in files:
            source_file = os.path.join(root, file)
            dest_file = os.path.join(local_save_dir, file.replace(".txt", ".ckpt") if file == "label_encoder.txt" else file)
            # If the file is still a symlink or missing at destination, hard copy it
            if source_file != dest_file and not os.path.exists(dest_file):
                shutil.copy2(source_file, dest_file)
    
    os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"
    
    # Finally, load from the confirmed local directory
    try:
        classifier = EncoderClassifier.from_hparams(
            source=str(local_save_dir), 
            savedir=str(local_save_dir),
            run_opts={"device": "cpu"}
        )
    except OSError as e:
        if "1314" in str(e):
            print("\nError: Windows strictly enforcing symlinks inside SpeechBrain Pretrainer.")
            print("Please run this terminal 'As Administrator' just ONCE to initialize the models!")
            exit(1)
        else:
            raise
    
    print("\n[2/3] Verification: Checking local files...")
    # SpeechBrain saves model files into the savedir
    # Typical files: embedding_model.ckpt, hyperparams.yaml, classifier.ckpt
    required_files = ["embedding_model.ckpt", "hyperparams.yaml", "classifier.ckpt"]
    missing = [f for f in required_files if not (local_save_dir / f).exists()]
    
    if missing:
        print(f"      Warning! Missing files: {missing}")
    else:
        print("      Success! All model weights are safely secured locally.")

    print("\n[3/3] Updating model_config.json...")
    
    # 4. Update the config to STRICTLY use the offline folder
    # and turn the enforce checks back on.
    config["voice"]["speaker_source"] = str(local_save_dir).replace("\\", "/")
    
    # Enable the enforcement
    if "model_requirements" not in config:
        config["model_requirements"] = {}
        
    config["model_requirements"]["require_real_voice_models"] = True
    config["model_requirements"]["enforce_on_startup"] = True
    
    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)
        
    print("      Config Updated!")
    print("\n=== Offline Setup Complete ===")
    print("You can now securely run your ML Service without an internet connection!")

if __name__ == "__main__":
    # Ensure script is run from the src/ directory
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    download_models()
