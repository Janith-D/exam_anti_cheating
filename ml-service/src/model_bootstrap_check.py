import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

MODEL_FILE_SUFFIXES = {
    ".onnx",
    ".pt",
    ".pth",
    ".ckpt",
    ".bin",
    ".json",
}


def load_json(path: Path) -> Dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as file_handle:
            data = json.load(file_handle)
        if not isinstance(data, dict):
            raise ValueError("Config root must be a JSON object")
        return data
    except Exception as exc:
        raise RuntimeError(f"Failed to load config '{path}': {exc}") from exc


def resolve_path(config_dir: Path, project_root: Path, configured_path: str) -> Path:
    path = Path(configured_path)
    if path.is_absolute():
        return path

    project_candidate = (project_root / path).resolve()
    if project_candidate.exists():
        return project_candidate

    config_candidate = (config_dir / path).resolve()
    if config_candidate.exists():
        return config_candidate

    cwd_candidate = (Path.cwd() / path).resolve()
    if cwd_candidate.exists():
        return cwd_candidate

    # Default to project-root relative to keep behavior consistent for startup scripts.
    return project_candidate


def has_model_files(directory: Path) -> Tuple[bool, int]:
    if not directory.exists() or not directory.is_dir():
        return False, 0

    files = [
        candidate
        for candidate in directory.rglob("*")
        if candidate.is_file() and candidate.suffix.lower() in MODEL_FILE_SUFFIXES
    ]
    return len(files) > 0, len(files)


def collect_checks(config: Dict[str, Any], config_dir: Path, project_root: Path) -> List[Dict[str, Any]]:
    checks: List[Dict[str, Any]] = []

    model_requirements = config.get("model_requirements", {})
    if not isinstance(model_requirements, dict):
        model_requirements = {}

    require_real_voice_models = bool(model_requirements.get("require_real_voice_models", True))
    require_behavior_onnx = bool(model_requirements.get("require_behavior_onnx", True))

    voice_config = config.get("voice", {})
    if not isinstance(voice_config, dict):
        voice_config = {}

    behavior_config = config.get("behavior", {})
    if not isinstance(behavior_config, dict):
        behavior_config = {}

    if require_real_voice_models:
        spoof_path_raw = str(voice_config.get("anti_spoof_onnx_path", "")).strip()
        spoof_path = resolve_path(config_dir, project_root, spoof_path_raw) if spoof_path_raw else None

        checks.append(
            {
                "name": "Voice anti-spoof ONNX",
                "required": True,
                "type": "file",
                "configuredPath": spoof_path_raw,
                "resolvedPath": str(spoof_path) if spoof_path else "",
                "exists": bool(spoof_path and spoof_path.exists() and spoof_path.is_file()),
                "detail": "Expected a valid anti-spoof ONNX model file",
            }
        )

        speaker_dir_raw = str(voice_config.get("speaker_savedir", "")).strip()
        speaker_dir = resolve_path(config_dir, project_root, speaker_dir_raw) if speaker_dir_raw else None
        speaker_ok = False
        speaker_count = 0
        if speaker_dir:
            speaker_ok, speaker_count = has_model_files(speaker_dir)

        checks.append(
            {
                "name": "Voice speaker model directory",
                "required": True,
                "type": "directory",
                "configuredPath": speaker_dir_raw,
                "resolvedPath": str(speaker_dir) if speaker_dir else "",
                "exists": speaker_ok,
                "detail": f"Expected model directory with speaker model artifacts (found {speaker_count})",
            }
        )

    if require_behavior_onnx:
        behavior_path_raw = str(behavior_config.get("onnx_model_path", "")).strip()
        behavior_path = resolve_path(config_dir, project_root, behavior_path_raw) if behavior_path_raw else None

        checks.append(
            {
                "name": "Behavior ONNX model",
                "required": True,
                "type": "file",
                "configuredPath": behavior_path_raw,
                "resolvedPath": str(behavior_path) if behavior_path else "",
                "exists": bool(behavior_path and behavior_path.exists() and behavior_path.is_file()),
                "detail": "Expected a valid behavior ONNX model file",
            }
        )

    return checks


def print_report(checks: List[Dict[str, Any]], config_path: Path) -> int:
    print("Model Bootstrap Check")
    print(f"Config: {config_path}")
    print("")

    if not checks:
        print("No required model checks configured.")
        return 0

    missing = []
    for check in checks:
        status = "OK" if check.get("exists") else "MISSING"
        print(f"[{status}] {check.get('name')}")
        print(f"  configured: {check.get('configuredPath')}")
        print(f"  resolved:   {check.get('resolvedPath')}")
        print(f"  note:       {check.get('detail')}")
        if not check.get("exists"):
            missing.append(check)

    print("")
    print(f"Summary: {len(checks) - len(missing)} passed, {len(missing)} missing")

    if missing:
        print("\nMissing required model artifacts:")
        for item in missing:
            print(f"- {item.get('name')}: {item.get('resolvedPath')}")
        return 1

    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify required model artifacts before ML API startup")
    parser.add_argument(
        "--config",
        default="config/model_config.json",
        help="Path to model configuration JSON",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        dest="json_output",
        help="Print machine-readable JSON output",
    )
    args = parser.parse_args()

    config_path = Path(args.config)
    if not config_path.is_absolute():
        config_path = (Path.cwd() / config_path).resolve()

    if not config_path.exists() or not config_path.is_file():
        print(f"Config file not found: {config_path}", file=sys.stderr)
        return 2

    try:
        config = load_json(config_path)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    config_dir = config_path.parent
    project_root = config_dir.parent
    checks = collect_checks(config, config_dir, project_root)
    missing = [check for check in checks if not check.get("exists")]

    if args.json_output:
        payload = {
            "config": str(config_path),
            "ready": len(missing) == 0,
            "checks": checks,
            "missing": missing,
        }
        print(json.dumps(payload, indent=2))
        return 0 if len(missing) == 0 else 1

    return print_report(checks, config_path)


if __name__ == "__main__":
    raise SystemExit(main())
