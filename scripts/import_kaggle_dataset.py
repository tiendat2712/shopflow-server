"""Optional Python helper to fetch abhayayare/e-commerce-dataset via kagglehub.

Usage:
    pip install kagglehub
    python scripts/import_kaggle_dataset.py

Optional environment variables:
    DATASET_LOCAL_DIR    Target folder (default: data/ecommerce_dataset)
    SHOPFLOW_API_URL     Backend base URL (default: http://localhost:8080)
    ADMIN_ACCESS_TOKEN   If provided, automatically calls POST /api/admin/datasets/import-kaggle
"""

import json
import os
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

try:
    import kagglehub
except ImportError as exc:  # pragma: no cover
    print("Missing 'kagglehub'. Install with: pip install kagglehub", file=sys.stderr)
    raise SystemExit(1) from exc


DATASET_SLUG = os.getenv("KAGGLE_DATASET_SLUG", "abhayayare/e-commerce-dataset")


def sync_dataset_to_local_dir(target_dir: Path) -> Path:
    downloaded_path = Path(kagglehub.dataset_download(DATASET_SLUG))
    print("Path to dataset files:", downloaded_path)

    target_dir.mkdir(parents=True, exist_ok=True)
    copied = []
    for csv_file in downloaded_path.rglob("*.csv"):
        destination = target_dir / csv_file.name
        shutil.copy2(csv_file, destination)
        copied.append(csv_file.name)

    print(f"Synced {len(copied)} CSV files into '{target_dir}': {', '.join(sorted(copied))}")
    return target_dir


def trigger_backend_import(api_url: str, admin_token: str) -> None:
    endpoint = f"{api_url.rstrip('/')}/api/admin/datasets/import-kaggle"
    payload = json.dumps({"forceDownload": False, "uploadCategoryPlaceholders": True}).encode("utf-8")
    req = urllib.request.Request(
        endpoint,
        data=payload,
        method="POST",
        headers={
            "Authorization": f"Bearer {admin_token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            body = resp.read().decode("utf-8")
            print(f"Backend import succeeded (HTTP {resp.status}):")
            print(body)
    except urllib.error.HTTPError as err:
        error_body = err.read().decode("utf-8", errors="replace")
        print(f"Backend import failed (HTTP {err.code}): {error_body}", file=sys.stderr)
        raise SystemExit(1) from err


def main() -> None:
    project_root = Path(__file__).resolve().parent.parent
    raw_target = os.getenv("DATASET_LOCAL_DIR", "data/ecommerce_dataset")
    target_dir = Path(raw_target)
    if not target_dir.is_absolute():
        target_dir = project_root / target_dir

    sync_dataset_to_local_dir(target_dir)

    admin_token = os.getenv("ADMIN_ACCESS_TOKEN", "").strip()
    if admin_token:
        api_url = os.getenv("SHOPFLOW_API_URL", "http://localhost:8080")
        trigger_backend_import(api_url, admin_token)
    else:
        print("Skipped API call (set ADMIN_ACCESS_TOKEN to automatically trigger backend import).")


if __name__ == "__main__":
    main()
