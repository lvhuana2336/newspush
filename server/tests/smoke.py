import asyncio
import os
import sys
import tempfile
import time
from pathlib import Path


HERE = Path(__file__).resolve().parent
DB_PATH = Path(tempfile.gettempdir()) / ("newspush-smoke-" + str(int(time.time())) + ".db")

os.environ["POLL_ENABLED"] = "false"
os.environ["DB_PATH"] = str(DB_PATH)
os.environ["ADMIN_TOKEN"] = "smoke-admin"
os.environ["SEED_FILE"] = str(HERE / "seed_smoke.json")
os.environ["BILI_MIN_INTERVAL_SECONDS"] = "1.5"

sys.path.insert(0, str(HERE.parent))

from fastapi.testclient import TestClient  # noqa: E402

from app import db  # noqa: E402
from app.bili import BiliClient  # noqa: E402
from app.main import app  # noqa: E402


def fail(message: str) -> None:
    print("FAIL:", message)
    raise SystemExit(1)


def main() -> None:
    with TestClient(app) as client:
        health = client.get("/v1/health")
        if health.status_code != 200:
            fail("health returned " + str(health.status_code))
        data = health.json()
        print("health:", data["status"], "accounts:", data["accounts"])
        if data["accounts"] != 2:
            fail("expected 2 seeded accounts")

        reg = client.post(
            "/v1/devices/register",
            json={"device_id": "smoke-device-0001", "app_version": "1.1"},
        )
        if reg.status_code != 200:
            fail("register returned " + str(reg.status_code) + ": " + reg.text)
        key = reg.json()["api_key"]
        headers = {"Authorization": "Bearer " + key}

        empty = client.get("/v1/feed?limit=5", headers=headers)
        if empty.status_code != 200 or empty.json()["items"]:
            fail("feed should be empty before fetching")
        print("feed before fetch: empty")

        async def fetch() -> list:
            bili = BiliClient()
            try:
                return await bili.fetch_account("456664753", "央视新闻")
            finally:
                await bili.close()

        items = asyncio.run(fetch())
        if not items:
            fail("bilibili fetch returned 0 items")
        print("bilibili fetched:", len(items), "|", items[0]["title"][:40])

        first = items[0]
        db.insert_post(
            {
                "platform": "bilibili",
                "account_uid": "456664753",
                "account_name": "央视新闻",
                "category": "国内",
                "fetched_at": int(time.time()),
                **first,
            }
        )

        feed = client.get("/v1/feed?limit=5", headers=headers)
        if feed.status_code != 200:
            fail("feed returned " + str(feed.status_code))
        payload = feed.json()
        if len(payload["items"]) != 1:
            fail("expected 1 feed item, got " + str(len(payload["items"])))
        item = payload["items"][0]
        for field in ("platform", "account_name", "title", "url", "published_at", "next_cursor"):
            if field == "next_cursor":
                if field not in payload:
                    fail("feed missing next_cursor")
                continue
            if not item.get(field):
                fail("feed item missing " + field)
        print("feed after insert:", item["account_name"], "|", item["title"][:30])

        unauth = client.get("/v1/feed")
        if unauth.status_code != 401:
            fail("feed without token should be 401, got " + str(unauth.status_code))
        print("unauthorized request rejected")

        admin = client.get("/admin/accounts", headers={"X-Admin-Token": "smoke-admin"})
        if admin.status_code != 200 or len(admin.json()["items"]) != 2:
            fail("admin accounts failed")
        print("admin accounts: ok")

    print("SMOKE OK")


if __name__ == "__main__":
    main()
