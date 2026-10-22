import os
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent.parent


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


class Settings:
    def __init__(self) -> None:
        self.api_version = "1.0.0"
        self.port = _env_int("PORT", 8000)
        self.db_path = os.getenv("DB_PATH", str(BASE_DIR / "data" / "newspush.db"))
        self.seed_file = os.getenv("SEED_FILE", str(BASE_DIR / "app" / "seed_accounts.json"))

        self.poll_enabled = _env_bool("POLL_ENABLED", True)
        self.poll_tick_seconds = _env_int("POLL_TICK_SECONDS", 30)
        self.max_concurrent_fetches = _env_int("MAX_CONCURRENT_FETCHES", 3)
        self.bili_min_interval_seconds = _env_float("BILI_MIN_INTERVAL_SECONDS", 2.0)
        self.post_retention_days = _env_int("POST_RETENTION_DAYS", 90)

        self.registration_mode = os.getenv("REGISTRATION_MODE", "open").strip().lower()
        self.invite_code = os.getenv("INVITE_CODE", "").strip()
        self.device_hour_limit = _env_int("DEVICE_HOUR_LIMIT", 60)
        self.device_day_limit = _env_int("DEVICE_DAY_LIMIT", 500)
        self.register_ip_hour_limit = _env_int("REGISTER_IP_HOUR_LIMIT", 5)
        self.register_ip_day_limit = _env_int("REGISTER_IP_DAY_LIMIT", 20)

        self.admin_token = os.getenv("ADMIN_TOKEN", "").strip()
        self.bili_sessdata = os.getenv("BILI_SESSDATA", "").strip()

    def ensure_dirs(self) -> None:
        Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)


settings = Settings()
