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
CREATE TABLE IF NOT EXISTS mailbox_events (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    envelope TEXT NOT NULL, kind TEXT NOT NULL, created INTEGER NOT NULL,
    expires INTEGER NOT NULL, push_after INTEGER NOT NULL, push_attempts INTEGER NOT NULL DEFAULT 0,
    pushed INTEGER NOT NULL DEFAULT 0,
    UNIQUE(recipient, envelope, kind)
);
CREATE INDEX IF NOT EXISTS mailbox_page ON mailbox_events(recipient, sequence);
CREATE TABLE IF NOT EXISTS download_receipts (
    envelope TEXT PRIMARY KEY REFERENCES draft_envelopes(id) ON DELETE CASCADE,
    installation TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    downloaded INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS push_tokens (
    installation TEXT PRIMARY KEY REFERENCES installations(id) ON DELETE CASCADE,
    token TEXT NOT NULL, updated INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS shares (
    id TEXT PRIMARY KEY, transfer TEXT NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    owner TEXT NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    request_key TEXT NOT NULL, secret_hash TEXT NOT NULL,
    expires INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0,
    UNIQUE(owner, request_key)
);
CREATE TABLE IF NOT EXISTS audit_totals (
    day INTEGER NOT NULL, kind TEXT NOT NULL, count INTEGER NOT NULL,
    PRIMARY KEY(day, kind)
);
CREATE TABLE IF NOT EXISTS mailbox_floor (id INTEGER PRIMARY KEY CHECK(id=1), sequence INTEGER NOT NULL);
INSERT OR IGNORE INTO mailbox_floor VALUES (1, 0);
"""


def event(db, recipient, envelope, kind, now):
    db.execute(
        """INSERT OR IGNORE INTO mailbox_events(recipient,envelope,kind,created,expires,push_after)
           VALUES (?,?,?,?,?,?)""",
        (recipient, envelope, kind, now, now + 30 * 86400, now),
    )


def audit(db, now, kind, count=1):
    db.execute(
        """INSERT INTO audit_totals VALUES (?,?,?)
           ON CONFLICT(day,kind) DO UPDATE SET count=count+excluded.count""",
        (now // 86400, kind, count),
    )


def revoke_events(db, transfer, now):
    envelope = db.execute("SELECT id FROM draft_envelopes WHERE transfer=?", (transfer["id"],)).fetchone()
    if envelope is not None and transfer["delivery"] == "hd":
        for recipient in {transfer["owner"], transfer["target"]}:
            event(db, recipient, envelope["id"], "revoked", now)


class Storage:
    def __init__(self, path):
        self.path = path
        with self.transaction() as db:
            db.executescript(SCHEMA)
            # Additive migration preserves CH048 transfers and their idempotency keys.
            for table, name, declaration in (
                ("installations", "phone_cipher", "BLOB"),
                ("transfers", "delivery", "TEXT NOT NULL DEFAULT 'hd'"),
                ("transfers", "created", "INTEGER NOT NULL DEFAULT 0"),
            ):
                if name not in {row["name"] for row in db.execute(f"PRAGMA table_info({table})")}:
                    db.execute(f"ALTER TABLE {table} ADD COLUMN {name} {declaration}")
            # Upgrade old finalized draft envelopes exactly once, even after a crash.
            for row in db.execute("""SELECT t.*,e.id AS envelope FROM transfers t
                JOIN draft_envelopes e ON e.transfer=t.id WHERE t.state='finalized' AND t.delivery='hd'""").fetchall():
                at = row["created"] or row["expires"] - 7 * 86400
                event(db, row["target"], row["envelope"], "envelope", at)
                event(db, row["owner"], row["envelope"], "sent", at)

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
            expired = db.execute("SELECT * FROM transfers WHERE expires<=?", (now,)).fetchall()
            for row in expired:
                revoke_events(db, row, now)
            lost = db.execute("SELECT id FROM installations WHERE verified_until<=?", (now,)).fetchall()
            for installation in lost:
                for row in db.execute("SELECT * FROM transfers WHERE owner=? OR target=?", (installation[0], installation[0])).fetchall():
                    revoke_events(db, row, now)
            if expired:
                audit(db, now, "expired_transfers", len(expired))
            floor = db.execute("SELECT max(sequence) FROM mailbox_events WHERE expires<=?", (now,)).fetchone()[0]
            if floor is not None:
                db.execute("UPDATE mailbox_floor SET sequence=max(sequence,?) WHERE id=1", (floor,))
            db.execute("DELETE FROM mailbox_events WHERE expires<=?", (now,))
            # Keep expired/revoked share keys until their transfer is deleted. A lost
            # response followed by an idempotent retry must never resurrect a link.
            db.execute("DELETE FROM audit_totals WHERE day<?", (now // 86400 - 30,))
            for table, column in columns.items():
                db.execute(f"DELETE FROM {table} WHERE {column} <= ?", (now,))
        with contextlib.closing(sqlite3.connect(self.path)) as db:
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
