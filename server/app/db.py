import json
import os
import sqlite3
import time
from typing import Any, Dict, List, Optional

from .config import settings


SCHEMA = """
CREATE TABLE IF NOT EXISTS accounts (
    uid TEXT PRIMARY KEY,
    platform TEXT NOT NULL DEFAULT 'bilibili',
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT '综合',
    official INTEGER NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 5,
    interval_minutes INTEGER NOT NULL DEFAULT 20,
    enabled INTEGER NOT NULL DEFAULT 1,
    last_seen_pub_ts INTEGER NOT NULL DEFAULT 0,
    next_due_at INTEGER NOT NULL DEFAULT 0,
    last_fetch_at INTEGER,
    fail_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    platform TEXT NOT NULL,
    post_id TEXT NOT NULL,
    account_uid TEXT NOT NULL,
    account_name TEXT NOT NULL,
    category TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    url TEXT NOT NULL,
    cover_url TEXT,
    published_at INTEGER NOT NULL,
    fetched_at INTEGER NOT NULL,
    play INTEGER NOT NULL DEFAULT 0,
    like_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE(platform, post_id)
);

CREATE INDEX IF NOT EXISTS idx_posts_id ON posts(id);
CREATE INDEX IF NOT EXISTS idx_posts_category ON posts(category, id);

CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL UNIQUE,
    key_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER,
    revoked INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fetch_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_uid TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    finished_at INTEGER NOT NULL,
    status TEXT NOT NULL,
    items INTEGER NOT NULL DEFAULT 0,
    inserted INTEGER NOT NULL DEFAULT 0,
    error TEXT
);

CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(settings.db_path, timeout=20)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def init_db() -> None:
    settings.ensure_dirs()
    with connect() as conn:
        conn.executescript(SCHEMA)
        count = conn.execute("SELECT COUNT(*) AS c FROM accounts").fetchone()["c"]
        if count == 0:
            _seed_accounts(conn)


def _seed_accounts(conn: sqlite3.Connection) -> None:
    if not os.path.exists(settings.seed_file):
        return
    with open(settings.seed_file, "r", encoding="utf-8") as fh:
        accounts = json.load(fh)
    now = int(time.time())
    for item in accounts:
        conn.execute(
            """
            INSERT OR IGNORE INTO accounts
                (uid, platform, name, category, official, priority, interval_minutes,
                 enabled, last_seen_pub_ts, next_due_at, created_at)
            VALUES (?, 'bilibili', ?, ?, ?, ?, ?, 1, 0, 0, ?)
            """,
            (
                str(item["uid"]),
                item["name"],
                item.get("category", "综合"),
                1 if item.get("official") else 0,
                int(item.get("priority", 5)),
                int(item.get("interval_minutes", 20)),
                now,
            ),
        )


def list_feed(after_id: int, limit: int, category: Optional[str] = None) -> List[sqlite3.Row]:
    with connect() as conn:
        if category:
            rows = conn.execute(
                "SELECT * FROM posts WHERE id > ? AND category = ? ORDER BY id ASC LIMIT ?",
                (after_id, category, limit),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM posts WHERE id > ? ORDER BY id ASC LIMIT ?",
                (after_id, limit),
            ).fetchall()
        return list(rows)


def list_accounts(enabled_only: bool = False) -> List[sqlite3.Row]:
    with connect() as conn:
        sql = "SELECT * FROM accounts"
        if enabled_only:
            sql += " WHERE enabled = 1"
        sql += " ORDER BY priority DESC, category, name"
        return list(conn.execute(sql).fetchall())


def due_accounts(limit: int) -> List[sqlite3.Row]:
    now = int(time.time())
    with connect() as conn:
        return list(
            conn.execute(
                """
                SELECT * FROM accounts
                WHERE enabled = 1 AND (next_due_at = 0 OR next_due_at <= ?)
                ORDER BY priority DESC, next_due_at ASC
                LIMIT ?
                """,
                (now, limit),
            ).fetchall()
        )


def insert_post(post: Dict[str, Any]) -> bool:
    with connect() as conn:
        cur = conn.execute(
            """
            INSERT OR IGNORE INTO posts
                (platform, post_id, account_uid, account_name, category, title,
                 description, url, cover_url, published_at, fetched_at, play, like_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                post["platform"],
                post["post_id"],
                post["account_uid"],
                post["account_name"],
                post["category"],
                post["title"],
                post.get("description", ""),
                post["url"],
                post.get("cover_url"),
                int(post["published_at"]),
                int(post["fetched_at"]),
                int(post.get("play", 0)),
                int(post.get("like_count", 0)),
            ),
        )
        return cur.rowcount > 0


def record_fetch_start(uid: str) -> int:
    now = int(time.time())
    with connect() as conn:
        cur = conn.execute(
            "INSERT INTO fetch_runs (account_uid, started_at, finished_at, status) VALUES (?, ?, 0, 'running')",
            (uid, now),
        )
        return int(cur.lastrowid)


def record_fetch_end(run_id: int, status: str, items: int, inserted: int, error: Optional[str]) -> None:
    with connect() as conn:
        conn.execute(
            "UPDATE fetch_runs SET finished_at = ?, status = ?, items = ?, inserted = ?, error = ? WHERE id = ?",
            (int(time.time()), status, items, inserted, error, run_id),
        )


def update_account_success(uid: str, latest_pub_ts: int, next_due_at: int) -> None:
    with connect() as conn:
        conn.execute(
            """
            UPDATE accounts
            SET last_seen_pub_ts = MAX(last_seen_pub_ts, ?),
                last_fetch_at = ?,
                next_due_at = ?,
                fail_count = 0,
                last_error = NULL
            WHERE uid = ?
            """,
            (latest_pub_ts, int(time.time()), next_due_at, uid),
        )


def update_account_failure(uid: str, next_due_at: int, error: str) -> None:
    with connect() as conn:
        conn.execute(
            """
            UPDATE accounts
            SET last_fetch_at = ?,
                next_due_at = ?,
                fail_count = fail_count + 1,
                last_error = ?
            WHERE uid = ?
            """,
            (int(time.time()), next_due_at, error[:500], uid),
        )


def get_device(device_id: str) -> Optional[sqlite3.Row]:
    with connect() as conn:
        return conn.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,)).fetchone()


def find_device_by_hash(key_hash: str) -> Optional[sqlite3.Row]:
    with connect() as conn:
        return conn.execute(
            "SELECT * FROM devices WHERE key_hash = ? AND revoked = 0", (key_hash,)
        ).fetchone()


def upsert_device(device_id: str, key_hash: str) -> int:
    now = int(time.time())
    with connect() as conn:
        row = conn.execute("SELECT id FROM devices WHERE device_id = ?", (device_id,)).fetchone()
        if row:
            conn.execute(
                "UPDATE devices SET key_hash = ?, revoked = 0, last_seen_at = ? WHERE id = ?",
                (key_hash, now, int(row["id"])),
            )
            return int(row["id"])
        cur = conn.execute(
            "INSERT INTO devices (device_id, key_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?)",
            (device_id, key_hash, now, now),
        )
        return int(cur.lastrowid)


def touch_device(device_pk: int) -> None:
    with connect() as conn:
        conn.execute("UPDATE devices SET last_seen_at = ? WHERE id = ?", (int(time.time()), device_pk))


def list_devices() -> List[sqlite3.Row]:
    with connect() as conn:
        return list(conn.execute("SELECT * FROM devices ORDER BY id DESC").fetchall())


def revoke_device(device_pk: int) -> None:
    with connect() as conn:
        conn.execute("UPDATE devices SET revoked = 1 WHERE id = ?", (device_pk,))


def add_account(item: Dict[str, Any]) -> None:
    now = int(time.time())
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO accounts
                (uid, platform, name, category, official, priority, interval_minutes,
                 enabled, last_seen_pub_ts, next_due_at, created_at)
            VALUES (?, 'bilibili', ?, ?, ?, ?, ?, ?, 0, 0, ?)
            ON CONFLICT(uid) DO UPDATE SET
                name = excluded.name,
                category = excluded.category,
                official = excluded.official,
                priority = excluded.priority,
                interval_minutes = excluded.interval_minutes,
                enabled = excluded.enabled
            """,
            (
                str(item["uid"]),
                item["name"],
                item.get("category", "综合"),
                1 if item.get("official") else 0,
                int(item.get("priority", 5)),
                int(item.get("interval_minutes", 20)),
                1 if item.get("enabled", True) else 0,
                now,
            ),
        )


def set_account_enabled(uid: str, enabled: bool) -> None:
    with connect() as conn:
        conn.execute("UPDATE accounts SET enabled = ? WHERE uid = ?", (1 if enabled else 0, uid))


def set_account_interval(uid: str, minutes: int) -> None:
    with connect() as conn:
        conn.execute("UPDATE accounts SET interval_minutes = ?, next_due_at = 0 WHERE uid = ?", (minutes, uid))


def force_due(uid: Optional[str] = None) -> None:
    with connect() as conn:
        if uid:
            conn.execute("UPDATE accounts SET next_due_at = 0 WHERE uid = ?", (uid,))
        else:
            conn.execute("UPDATE accounts SET next_due_at = 0 WHERE enabled = 1")


def cleanup_old_posts(days: int) -> int:
    cutoff = int(time.time()) - days * 86400
    with connect() as conn:
        cur = conn.execute("DELETE FROM posts WHERE fetched_at < ?", (cutoff,))
        return cur.rowcount


def counts() -> Dict[str, Any]:
    with connect() as conn:
        accounts = conn.execute("SELECT COUNT(*) AS c FROM accounts").fetchone()["c"]
        enabled = conn.execute("SELECT COUNT(*) AS c FROM accounts WHERE enabled = 1").fetchone()["c"]
        posts = conn.execute("SELECT COUNT(*) AS c FROM posts").fetchone()["c"]
        devices = conn.execute("SELECT COUNT(*) AS c FROM devices WHERE revoked = 0").fetchone()["c"]
        last = conn.execute("SELECT MAX(last_fetch_at) AS t FROM accounts").fetchone()["t"]
    return {"accounts": accounts, "enabled_accounts": enabled, "posts": posts, "devices": devices, "last_fetch_at": last}


def list_runs(limit: int) -> List[Dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT * FROM fetch_runs ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
    return [
        {
            "id": row["id"],
            "account_uid": row["account_uid"],
            "started_at": row["started_at"],
            "finished_at": row["finished_at"],
            "status": row["status"],
            "items": row["items"],
            "inserted": row["inserted"],
            "error": row["error"],
        }
        for row in rows
    ]
