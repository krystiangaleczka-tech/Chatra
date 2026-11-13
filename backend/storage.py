import contextlib
import sqlite3

SCHEMA = """
CREATE TABLE IF NOT EXISTS installations (
    id TEXT PRIMARY KEY, public_key BLOB UNIQUE NOT NULL, phone TEXT UNIQUE NOT NULL,
    discoverable INTEGER NOT NULL DEFAULT 0, verified_until INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
    token TEXT PRIMARY KEY, installation TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    expires INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS session_installation ON sessions(installation);
CREATE TABLE IF NOT EXISTS nonces (
    scope TEXT NOT NULL, nonce TEXT NOT NULL, expires INTEGER NOT NULL, PRIMARY KEY (scope, nonce)
);
CREATE TABLE IF NOT EXISTS limits (
    bucket TEXT NOT NULL, window INTEGER NOT NULL, count INTEGER NOT NULL, expires INTEGER NOT NULL,
    PRIMARY KEY (bucket, window)
);
CREATE TABLE IF NOT EXISTS challenges (
    id TEXT PRIMARY KEY, public_key BLOB NOT NULL, phone TEXT NOT NULL, provider TEXT UNIQUE,
    expires INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
    checking INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS challenge_phone ON challenges(phone, expires);
CREATE TABLE IF NOT EXISTS discovery (
    installation TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    target TEXT NOT NULL, expires INTEGER NOT NULL, PRIMARY KEY (installation, target)
);
CREATE TABLE IF NOT EXISTS transfers (
    id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    request_key TEXT NOT NULL, fingerprint TEXT NOT NULL, target TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    state TEXT NOT NULL, expires INTEGER NOT NULL, size INTEGER NOT NULL, UNIQUE (owner, request_key)
);
CREATE INDEX IF NOT EXISTS transfer_expiry ON transfers(expires);
CREATE TABLE IF NOT EXISTS files (
    transfer TEXT NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    position INTEGER NOT NULL, mime TEXT NOT NULL, variant TEXT NOT NULL,
    size INTEGER NOT NULL, digest TEXT NOT NULL, offset INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (transfer, position)
);
CREATE TABLE IF NOT EXISTS chunks (
    transfer TEXT NOT NULL, position INTEGER NOT NULL, offset INTEGER NOT NULL,
    digest TEXT NOT NULL, data BLOB NOT NULL,
    PRIMARY KEY (transfer, position, offset),
    FOREIGN KEY (transfer, position) REFERENCES files(transfer, position) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS draft_envelopes (
    id TEXT PRIMARY KEY, transfer TEXT UNIQUE NOT NULL REFERENCES transfers(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS transfer_keys (
    owner TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE, request_key TEXT NOT NULL,
    expires INTEGER NOT NULL, PRIMARY KEY (owner, request_key)
);
"""


class Storage:
    def __init__(self, path):
        self.path = path
        with self.transaction() as db:
            db.executescript(SCHEMA)

    @contextlib.contextmanager
    def transaction(self, write=True):
        db = sqlite3.connect(self.path, timeout=8, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            db.execute("PRAGMA foreign_keys=ON")
            db.execute("PRAGMA secure_delete=ON")
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("BEGIN IMMEDIATE" if write else "BEGIN")
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    def cleanup(self, now):
        columns = {
            "sessions": "expires", "nonces": "expires", "limits": "expires", "challenges": "expires",
            "discovery": "expires", "transfers": "expires", "installations": "verified_until",
            "transfer_keys": "expires",
        }
        with self.transaction() as db:
            for table, column in columns.items():
                db.execute(f"DELETE FROM {table} WHERE {column} <= ?", (now,))
        with contextlib.closing(sqlite3.connect(self.path)) as db:
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
