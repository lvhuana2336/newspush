import argparse
import sys

from . import db


def _print_accounts(rows) -> None:
    print(f"{'UID':<20} {'EN':<3} {'CAT':<6} {'INT':>4} {'PRI':>3} {'NAME'}")
    for row in rows:
        print(
            f"{row['uid']:<20} {int(row['enabled']):<3} {row['category']:<6} "
            f"{row['interval_minutes']:>4} {row['priority']:>3} {row['name']}"
        )
        if row["last_error"]:
            print("    error: " + str(row["last_error"])[:160])


def main() -> int:
    parser = argparse.ArgumentParser(prog="python -m app.admin")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="list accounts")
    sub.add_parser("devices", help="list devices")
    sub.add_parser("stats", help="database statistics")
    sub.add_parser("cleanup", help="delete posts older than retention days")

    add = sub.add_parser("add", help="add or update an account")
    add.add_argument("uid")
    add.add_argument("name")
    add.add_argument("category", nargs="?", default="综合")
    add.add_argument("--official", action="store_true")
    add.add_argument("--priority", type=int, default=5)
    add.add_argument("--interval", type=int, default=20)

    for name in ("enable", "disable"):
        item = sub.add_parser(name, help=name + " an account")
        item.add_argument("uid")

    revoke = sub.add_parser("revoke", help="revoke a device key")
    revoke.add_argument("device_pk", type=int)

    fetch = sub.add_parser("fetch-now", help="mark accounts as due immediately")
    fetch.add_argument("uid", nargs="?")

    args = parser.parse_args()
    db.init_db()

    if args.cmd == "list":
        _print_accounts(db.list_accounts(enabled_only=False))
    elif args.cmd == "devices":
        for row in db.list_devices():
            print(
                f"id={row['id']:<4} device={row['device_id']:<40} "
                f"revoked={int(row['revoked'])} created={row['created_at']} "
                f"last_seen={row['last_seen_at']}"
            )
    elif args.cmd == "stats":
        print(db.counts())
    elif args.cmd == "cleanup":
        from .config import settings

        print("removed:", db.cleanup_old_posts(settings.post_retention_days))
    elif args.cmd == "add":
        db.add_account(
            {
                "uid": args.uid,
                "name": args.name,
                "category": args.category,
                "official": args.official,
                "priority": args.priority,
                "interval_minutes": args.interval,
                "enabled": True,
            }
        )
        print("ok")
    elif args.cmd == "enable":
        db.set_account_enabled(args.uid, True)
        print("ok")
    elif args.cmd == "disable":
        db.set_account_enabled(args.uid, False)
        print("ok")
    elif args.cmd == "revoke":
        db.revoke_device(args.device_pk)
        print("ok")
    elif args.cmd == "fetch-now":
        db.force_due(args.uid)
        print("queued:", args.uid or "*")
    return 0


if __name__ == "__main__":
    sys.exit(main())
