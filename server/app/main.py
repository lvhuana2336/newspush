import asyncio
import hashlib
import logging
import random
import secrets
import time
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from pydantic import BaseModel, Field

from . import db
from .bili import BiliClient, BiliError
from .config import settings


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("newspush")
TZ = timezone(timedelta(hours=8))

bili = BiliClient()


class EventLimiter:
    def __init__(self) -> None:
        self._events: Dict[str, List[float]] = {}

    def allow(self, key: str, per_hour: int, per_day: int) -> bool:
        now = time.time()
        events = [t for t in self._events.get(key, []) if t > now - 86400]
        hour_count = sum(1 for t in events if t > now - 3600)
        if hour_count >= per_hour or len(events) >= per_day:
            self._events[key] = events
            return False
        events.append(now)
        self._events[key] = events
        return True


limiter = EventLimiter()
stop_event = asyncio.Event()
poll_task: Optional[asyncio.Task] = None


def _now_iso() -> str:
    return datetime.now(TZ).isoformat(timespec="seconds")


def _iso(ts: Optional[int]) -> Optional[str]:
    if not ts:
        return None
    return datetime.fromtimestamp(int(ts), TZ).isoformat(timespec="seconds")


def _hash_key(key: str) -> str:
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def _serialize_post(row: Any) -> Dict[str, Any]:
    return {
        "id": row["id"],
        "platform": row["platform"],
        "post_id": row["post_id"],
        "account_uid": row["account_uid"],
        "account_name": row["account_name"],
        "category": row["category"],
        "title": row["title"],
        "description": row["description"],
        "url": row["url"],
        "cover_url": row["cover_url"],
        "published_at": _iso(row["published_at"]),
        "published_at_ts": row["published_at"],
        "stats": {"play": row["play"], "like": row["like_count"]},
    }


def require_device(authorization: Optional[str] = Header(None)) -> Any:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="missing bearer token")
    key = authorization[7:].strip()
    device = db.find_device_by_hash(_hash_key(key))
    if not device:
        raise HTTPException(status_code=401, detail="invalid or revoked api key")
    db.touch_device(int(device["id"]))
    if not limiter.allow(
        "device:" + str(device["id"]),
        settings.device_hour_limit,
        settings.device_day_limit,
    ):
        raise HTTPException(status_code=429, detail="rate limit exceeded")
    return device


def require_admin(x_admin_token: Optional[str] = Header(None)) -> bool:
    if not settings.admin_token:
        raise HTTPException(status_code=503, detail="admin API disabled")
    if not x_admin_token or not secrets.compare_digest(x_admin_token, settings.admin_token):
        raise HTTPException(status_code=403, detail="invalid admin token")
    return True


class RegisterBody(BaseModel):
    device_id: str = Field(min_length=8, max_length=128)
    app_version: str = ""
    invite_code: str = ""


class AccountBody(BaseModel):
    uid: str = Field(min_length=1, max_length=32)
    name: str = Field(min_length=1, max_length=100)
    category: str = "综合"
    official: bool = False
    priority: int = 5
    interval_minutes: int = 20
    enabled: bool = True


class AccountPatch(BaseModel):
    enabled: Optional[bool] = None
    interval_minutes: Optional[int] = None


async def fetch_one(account: Any) -> None:
    uid = str(account["uid"])
    name = str(account["name"])
    run_id = db.record_fetch_start(uid)
    try:
        items = await bili.fetch_account(uid, name)
    except Exception as exc:
        interval = max(10, int(account["interval_minutes"]))
        failures = int(account["fail_count"]) + 1
        delay = min(6 * 3600, max(300, interval * 60 * (2 ** min(failures, 4))))
        db.update_account_failure(uid, int(time.time()) + delay, str(exc))
        db.record_fetch_end(run_id, "error", 0, 0, str(exc))
        logger.warning("fetch failed %s (%s): %s", name, uid, exc)
        return

    inserted = 0
    latest = int(account["last_seen_pub_ts"])
    now = int(time.time())
    for item in items:
        post = {
            "platform": "bilibili",
            "account_uid": uid,
            "account_name": name,
            "category": account["category"],
            "fetched_at": now,
            **item,
        }
        if db.insert_post(post):
            inserted += 1
        latest = max(latest, int(item["published_at"]))

    interval = max(10, int(account["interval_minutes"]))
    next_due = now + int(interval * 60 * random.uniform(0.8, 1.2))
    db.update_account_success(uid, latest, next_due)
    db.record_fetch_end(run_id, "ok", len(items), inserted, None)
    logger.info("fetched %s (%s): %d items, %d new", name, uid, len(items), inserted)


async def poller_loop() -> None:
    next_cleanup = time.time() + 3600
    while not stop_event.is_set():
        try:
            batch = db.due_accounts(10)
            for account in batch:
                if stop_event.is_set():
                    break
                await fetch_one(account)
            if time.time() >= next_cleanup:
                removed = db.cleanup_old_posts(settings.post_retention_days)
                if removed:
                    logger.info("cleaned %d old posts", removed)
                next_cleanup = time.time() + 3600
        except Exception:
            logger.exception("poller loop error")
        try:
            await asyncio.wait_for(stop_event.wait(), timeout=settings.poll_tick_seconds)
        except asyncio.TimeoutError:
            pass


@asynccontextmanager
async def lifespan(app: FastAPI):
    global poll_task
    db.init_db()
    counts = db.counts()
    logger.info(
        "server ready: db=%s accounts=%s enabled=%s",
        settings.db_path,
        counts["accounts"],
        counts["enabled_accounts"],
    )
    if settings.poll_enabled:
        poll_task = asyncio.create_task(poller_loop())
    yield
    stop_event.set()
    if poll_task:
        poll_task.cancel()
        await asyncio.gather(poll_task, return_exceptions=True)
    await bili.close()


app = FastAPI(title="NewsPush Social API", version=settings.api_version, lifespan=lifespan)


@app.get("/")
async def root() -> Dict[str, str]:
    return {"name": "NewsPush Social API", "version": settings.api_version, "docs": "/docs"}


@app.get("/v1/health")
async def health() -> Dict[str, Any]:
    counts = db.counts()
    return {
        "status": "ok",
        "version": settings.api_version,
        "registration_mode": settings.registration_mode,
        "server_time": _now_iso(),
        **counts,
        "last_fetch_at": _iso(counts["last_fetch_at"]),
    }


@app.post("/v1/devices/register")
async def register(body: RegisterBody, request: Request) -> Dict[str, Any]:
    ip = _client_ip(request)
    if not limiter.allow(
        "register:" + ip,
        settings.register_ip_hour_limit,
        settings.register_ip_day_limit,
    ):
        raise HTTPException(status_code=429, detail="too many registrations from this ip")

    if settings.registration_mode != "open":
        if not settings.invite_code:
            raise HTTPException(status_code=503, detail="invite mode is enabled but INVITE_CODE is empty")
        if not secrets.compare_digest(body.invite_code.strip(), settings.invite_code):
            raise HTTPException(
                status_code=403,
                detail={"code": "INVITE_REQUIRED", "message": "a valid invite code is required"},
            )

    key = "np_" + secrets.token_urlsafe(32)
    device_pk = db.upsert_device(body.device_id.strip(), _hash_key(key))
    logger.info("device registered: id=%s ip=%s version=%s", device_pk, ip, body.app_version)
    return {
        "api_key": key,
        "device_id": body.device_id.strip(),
        "server_time": _now_iso(),
        "schema_version": 1,
        "registration_mode": settings.registration_mode,
    }


@app.get("/v1/feed")
async def feed(
    cursor: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    category: Optional[str] = None,
    latest: bool = Query(False),
    device: Any = Depends(require_device),
) -> Dict[str, Any]:
    if latest and cursor == 0:
        rows = db.list_feed_latest(limit, category)
    else:
        rows = db.list_feed(cursor, limit, category)
    next_cursor = int(rows[-1]["id"]) if rows else cursor
    return {
        "items": [_serialize_post(row) for row in rows],
        "next_cursor": next_cursor,
        "server_time": _now_iso(),
    }


@app.get("/v1/accounts")
async def accounts(device: Any = Depends(require_device)) -> Dict[str, Any]:
    rows = db.list_accounts(enabled_only=True)
    return {
        "items": [
            {
                "uid": row["uid"],
                "platform": row["platform"],
                "name": row["name"],
                "category": row["category"],
                "official": bool(row["official"]),
                "interval_minutes": row["interval_minutes"],
            }
            for row in rows
        ]
    }


@app.get("/admin/accounts")
async def admin_accounts(admin: bool = Depends(require_admin)) -> Dict[str, Any]:
    return {
        "items": [
            {
                "uid": row["uid"],
                "name": row["name"],
                "category": row["category"],
                "official": bool(row["official"]),
                "enabled": bool(row["enabled"]),
                "interval_minutes": row["interval_minutes"],
                "priority": row["priority"],
                "last_fetch_at": _iso(row["last_fetch_at"]),
                "fail_count": row["fail_count"],
                "last_error": row["last_error"],
            }
            for row in db.list_accounts(enabled_only=False)
        ]
    }


@app.post("/admin/accounts")
async def admin_add_account(body: AccountBody, admin: bool = Depends(require_admin)) -> Dict[str, str]:
    db.add_account(body.model_dump())
    return {"status": "ok"}


@app.patch("/admin/accounts/{uid}")
async def admin_patch_account(
    uid: str,
    body: AccountPatch,
    admin: bool = Depends(require_admin),
) -> Dict[str, str]:
    if body.enabled is not None:
        db.set_account_enabled(uid, body.enabled)
    if body.interval_minutes is not None:
        db.set_account_interval(uid, max(5, body.interval_minutes))
    return {"status": "ok"}


@app.post("/admin/fetch-now")
async def admin_fetch_now(
    uid: Optional[str] = None,
    admin: bool = Depends(require_admin),
) -> Dict[str, str]:
    db.force_due(uid)
    return {"status": "queued", "uid": uid or "*"}


@app.get("/admin/runs")
async def admin_runs(
    limit: int = Query(50, ge=1, le=500),
    admin: bool = Depends(require_admin),
) -> Dict[str, Any]:
    return {"items": db.list_runs(limit)}


@app.get("/admin/devices")
async def admin_devices(admin: bool = Depends(require_admin)) -> Dict[str, Any]:
    return {
        "items": [
            {
                "id": row["id"],
                "device_id": row["device_id"],
                "created_at": _iso(row["created_at"]),
                "last_seen_at": _iso(row["last_seen_at"]),
                "revoked": bool(row["revoked"]),
            }
            for row in db.list_devices()
        ]
    }


@app.post("/admin/devices/{device_pk}/revoke")
async def admin_revoke_device(device_pk: int, admin: bool = Depends(require_admin)) -> Dict[str, str]:
    db.revoke_device(device_pk)
    return {"status": "ok"}


@app.post("/admin/cleanup")
async def admin_cleanup(admin: bool = Depends(require_admin)) -> Dict[str, int]:
    return {"removed": db.cleanup_old_posts(settings.post_retention_days)}
