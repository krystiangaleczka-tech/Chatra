"""Offline restore barrier. Run on the restored copy BEFORE exposing the service."""
import argparse
import sqlite3
import time
from pathlib import Path

from storage import Storage, audit


def invalidate_snapshot(path, now=None):
    if not Path(path).is_file():
        raise ValueError("An existing restored database is required")
    storage = Storage(path)
    with storage.transaction() as db:
        # All routing data, bytes, receipts, links and credentials cascade away.
        # A stale backup cannot resurrect a previously deleted or revoked item.
        db.execute("DELETE FROM installations")
        for table in ("sessions", "challenges", "nonces", "discovery", "push_tokens"):
            db.execute(f"DELETE FROM {table}")
        audit(db, int(time.time()) if now is None else now, "restore_invalidated")
    with sqlite3.connect(path) as db:
        db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        db.execute("VACUUM")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path)
    parser.add_argument("--invalidate-bindings", action="store_true", required=True,
                        help="Discard all restored user data and require fresh activation")
    args = parser.parse_args()
    invalidate_snapshot(args.database)
