"""Local-only aggregate health probe. Never exposes IDs, addresses or tokens."""
import argparse
import json
import time
from pathlib import Path
from storage import Storage


def health(storage, now):
    with storage.transaction(write=False) as db:
        backlog = db.execute("SELECT count(*) FROM mailbox_events WHERE pushed=0 AND push_attempts>=6 AND expires>?", (now,)).fetchone()[0]
        limited = db.execute("SELECT coalesce(sum(count),0) FROM audit_totals WHERE kind='rate_limited' AND day=?", (now // 86400,)).fetchone()[0]
        expired = db.execute("SELECT count(*) FROM transfers WHERE expires<?", (now - 600,)).fetchone()[0]
        size = db.execute("SELECT coalesce(sum(length(data)),0) FROM chunks").fetchone()[0]
    return {"delayedPushes": backlog, "rateLimitsToday": limited, "overdueCleanup": expired,
            "storedBytes": size, "alert": bool(backlog or expired or limited >= 100 or size >= 1800 * 1024 * 1024)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path)
    args = parser.parse_args()
    if not args.database.is_file():
        parser.error("An existing database is required")
    result = health(Storage(args.database), int(time.time()))
    print(json.dumps(result))
    raise SystemExit(2 if result["alert"] else 0)
