import os
import sys
import tempfile
import time
from pathlib import Path


HERE = Path(__file__).resolve().parent
DB_PATH = Path(tempfile.gettempdir()) / ("newspush-invite-" + str(int(time.time())) + ".db")

os.environ["POLL_ENABLED"] = "false"
os.environ["DB_PATH"] = str(DB_PATH)
os.environ["ADMIN_TOKEN"] = "smoke-admin"
os.environ["SEED_FILE"] = str(HERE / "seed_smoke.json")
os.environ["REGISTRATION_MODE"] = "invite"
os.environ["INVITE_CODE"] = "abc12345"

sys.path.insert(0, str(HERE.parent))

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402


def main() -> None:
    with TestClient(app) as client:
        health = client.get("/v1/health").json()
        if health["registration_mode"] != "invite":
            print("FAIL: health should report invite mode")
            raise SystemExit(1)

        denied = client.post(
            "/v1/devices/register",
            json={"device_id": "invite-device-0001", "app_version": "1.1"},
        )
        if denied.status_code != 403:
            print("FAIL: expected 403 without invite code, got", denied.status_code, denied.text)
            raise SystemExit(1)
        detail = denied.json().get("detail") or {}
        if detail.get("code") != "INVITE_REQUIRED":
            print("FAIL: unexpected error payload:", denied.text)
            raise SystemExit(1)
        print("invite required: ok")

        granted = client.post(
            "/v1/devices/register",
            json={
                "device_id": "invite-device-0001",
                "app_version": "1.1",
                "invite_code": "abc12345",
            },
        )
        if granted.status_code != 200 or not granted.json().get("api_key"):
            print("FAIL: registration with invite code failed:", granted.status_code, granted.text)
            raise SystemExit(1)
        print("invite registration: ok")

    print("INVITE SMOKE OK")


if __name__ == "__main__":
    main()
