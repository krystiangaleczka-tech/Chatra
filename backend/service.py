import base64
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import time
from dataclasses import dataclass
from typing import Annotated, Literal

from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi import Request
from pydantic import BaseModel, ConfigDict, Field, StringConstraints

CHUNK = 256 * 1024
MIB = 1024 * 1024
DAY = 86_400
SIGNED_WINDOW = 120
PHONE = Annotated[str, StringConstraints(pattern=r"^\+[1-9][0-9]{7,14}$")]
DIGEST = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{64}$")]


class Problem(Exception):
    def __init__(self, status, code, retry=None):
        self.status, self.code, self.retry = status, code, retry


class Payload(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Challenge(Payload):
    number: PHONE
    publicKey: str = Field(min_length=100, max_length=160)


class Verification(Payload):
    code: str = Field(pattern=r"^[0-9]{4,10}$")


class Lookup(Payload):
    number: PHONE


class Discovery(Payload):
    enabled: bool


class FileSpec(Payload):
    mime: str = Field(pattern=r"^(image|video)/[a-z0-9][a-z0-9.+-]{0,61}$")
    variant: Literal["original", "no_location"]
    size: int = Field(gt=0, le=25 * MIB)
    sha256: DIGEST


class Manifest(Payload):
    recipient: PHONE
    files: list[FileSpec] = Field(min_length=1, max_length=5)


class Purchase(Payload):
    productId: str = Field(pattern=r"^[a-z0-9_.]{1,100}$")
    token: str = Field(pattern=r"^[A-Za-z0-9._~=-]{1,2048}$")


@dataclass(frozen=True)
class Proof:
    request: Request
    body: bytes
    now: int


def sha(data):
    return hashlib.sha256(data).hexdigest()


def decode(value):
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,160}", value):
        raise ValueError
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def public_key(value):
    try:
        raw = decode(value)
        key = serialization.load_der_public_key(raw)
        if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
            raise ValueError
        if key.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo) != raw:
            raise ValueError
        return raw
    except (ValueError, TypeError, UnsupportedAlgorithm):
        raise Problem(400, "invalid_public_key") from None


def opaque_id(value):
    if not re.fullmatch(r"[0-9a-f]{32}", value):
        raise Problem(404, "not_found")
    return value


class Service:
    def __init__(self, store, secret, otp, play=None, clock=time.time):
        if len(secret) < 32:
            raise ValueError("Service secret must be at least 32 bytes")
        self.store, self.secret, self.otp, self.play, self.clock = store, secret, otp, play, clock

    def tag(self, namespace, value):
        return hmac.new(self.secret, f"{namespace}\n{value}".encode(), hashlib.sha256).hexdigest()

    def verify_proof(self, proof, raw_key, credential):
        request = proof.request
        timestamp = request.headers.get("X-Chatra-Time", "")
        nonce = request.headers.get("X-Chatra-Nonce", "")
        signature = request.headers.get("X-Chatra-Signature", "")
        if (
            not re.fullmatch(r"[0-9]{1,12}", timestamp)
            or abs(proof.now - int(timestamp)) > SIGNED_WINDOW
            or not re.fullmatch(r"[0-9a-f]{32}", nonce)
        ):
            raise Problem(401, "invalid_proof")
        # All implemented paths and query values are ASCII and signed in the exact wire form.
        target = request.scope["raw_path"]
        query = request.scope["query_string"]
        if query:
            target += b"?" + query
        try:
            message = "\n".join((
                "CHATRA-HD-1", request.method, target.decode("ascii"), sha(proof.body),
                timestamp, nonce, sha(credential.encode("ascii")),
                request.headers.get("Idempotency-Key", ""), request.headers.get("Upload-Offset", ""),
                request.headers.get("X-Chunk-SHA256", ""),
            )).encode("ascii")
            key = serialization.load_der_public_key(raw_key)
            key.verify(decode(signature), message, ec.ECDSA(hashes.SHA256()))
        except (InvalidSignature, ValueError, TypeError):
            raise Problem(401, "invalid_proof") from None
        with self.store.transaction() as db:
            try:
                db.execute("INSERT INTO nonces VALUES (?, ?, ?)", (sha(raw_key), nonce, int(timestamp) + SIGNED_WINDOW + 1))
            except sqlite3.IntegrityError:
                raise Problem(401, "replayed_request") from None

    def limit(self, now, *buckets):
        retry = 0
        with self.store.transaction() as db:
            for name, period, maximum in buckets:
                window = now // period
                expiry = (window + 1) * period
                count = db.execute(
                    """INSERT INTO limits VALUES (?, ?, 1, ?)
                       ON CONFLICT(bucket, window) DO UPDATE SET count=min(count+1, ?)
                       RETURNING count""",
                    (self.tag("limit", name), window, expiry, maximum + 1),
                ).fetchone()[0]
                if count > maximum:
                    retry = max(retry, expiry - now)
        if retry:
            raise Problem(429, "rate_limited", retry)

    def installation(self, db, installation, now):
        row = db.execute(
            "SELECT * FROM installations WHERE id=? AND verified_until>?", (installation, now),
        ).fetchone()
        if row is None:
            raise Problem(401, "binding_required")
        return row

    def auth(self, proof):
        header = proof.request.headers.get("Authorization", "")
        if not re.fullmatch(r"Bearer [A-Za-z0-9_-]{43}", header):
            raise Problem(401, "session_required")
        token = header.removeprefix("Bearer ")
        with self.store.transaction(write=False) as db:
            row = db.execute(
                """SELECT i.id, i.public_key FROM installations i JOIN sessions s ON s.installation=i.id
                   WHERE s.token=? AND s.expires>? AND i.verified_until>?""",
                (self.tag("session", token), proof.now, proof.now),
            ).fetchone()
        if row is None:
            raise Problem(401, "session_expired")
        self.verify_proof(proof, row["public_key"], token)
        self.limit(proof.now, (f"api:{row['id']}", 60, 1000))
        return row["id"]

    def source(self, proof):
        return proof.request.client.host if proof.request.client else "unknown"

    def challenge(self, proof, payload):
        self.limit(proof.now, (f"otp-pre:{self.source(proof)}", 3600, 20), ("otp-pre-global", 3600, 500))
        key = public_key(payload.publicKey)
        self.verify_proof(proof, key, "")
        phone = self.tag("phone", payload.number)
        self.limit(proof.now, (f"otp:{phone}", DAY, 5), ("otp-global", 3600, 100))
        challenge = secrets.token_hex(16)
        expiry = proof.now + 600
        with self.store.transaction() as db:
            active = db.execute("SELECT max(expires) FROM challenges WHERE phone=? AND expires>?", (phone, proof.now)).fetchone()[0]
            if active:
                raise Problem(429, "challenge_already_started", active - proof.now)
            db.execute("INSERT INTO challenges(id, public_key, phone, expires) VALUES (?, ?, ?, ?)", (challenge, key, phone, expiry))
        sid = self.otp.start(payload.number)
        with self.store.transaction() as db:
            db.execute("UPDATE challenges SET provider=? WHERE id=?", (sid, challenge))
        return {"challengeId": challenge, "expiresAt": expiry}

    def verify_number(self, proof, challenge, payload):
        opaque_id(challenge)
        self.limit(proof.now, (f"otp-check:{self.source(proof)}", 600, 40))
        with self.store.transaction(write=False) as db:
            row = db.execute("SELECT * FROM challenges WHERE id=? AND expires>?", (challenge, proof.now)).fetchone()
        if row is None or row["provider"] is None:
            raise Problem(401, "invalid_code")
        self.verify_proof(proof, row["public_key"], challenge)
        with self.store.transaction() as db:
            changed = db.execute(
                """UPDATE challenges SET attempts=attempts+1, checking=1
                   WHERE id=? AND checking=0 AND attempts<5 AND expires>?""", (challenge, proof.now),
            ).rowcount
        if not changed:
            raise Problem(401, "invalid_code")
        try:
            number = self.otp.verify(row["provider"], payload.code)
            now = int(self.clock())
            if not number or not hmac.compare_digest(self.tag("phone", number), row["phone"]) or now >= row["expires"]:
                raise Problem(401, "invalid_code")
            with self.store.transaction() as db:
                old = db.execute("SELECT id, public_key, verified_until FROM installations WHERE phone=?", (row["phone"],)).fetchone()
                if old is not None and old["public_key"] == row["public_key"] and old["verified_until"] > now:
                    installation = old["id"]
                    db.execute("UPDATE installations SET verified_until=? WHERE id=?", (now + 90 * DAY, installation))
                else:
                    db.execute(
                        "DELETE FROM discovery WHERE target IN (SELECT phone FROM installations WHERE phone=? OR public_key=?)",
                        (row["phone"], row["public_key"]),
                    )
                    db.execute("DELETE FROM installations WHERE phone=? OR public_key=?", (row["phone"], row["public_key"]))
                    installation = challenge
                    db.execute("INSERT INTO installations VALUES (?, ?, ?, 0, ?)", (installation, row["public_key"], row["phone"], now + 90 * DAY))
                db.execute("UPDATE challenges SET attempts=5, checking=0 WHERE id=?", (challenge,))
            return {"installationId": installation, "verifiedUntil": now + 90 * DAY}
        finally:
            with self.store.transaction() as db:
                db.execute("UPDATE challenges SET checking=0 WHERE id=?", (challenge,))

    def session(self, proof, installation):
        opaque_id(installation)
        self.limit(proof.now, (f"sessions:{self.source(proof)}", 60, 100))
        with self.store.transaction(write=False) as db:
            key = self.installation(db, installation, proof.now)["public_key"]
        self.verify_proof(proof, key, installation)
        token = secrets.token_urlsafe(32)
        with self.store.transaction() as db:
            current = self.installation(db, installation, proof.now)
            expires = min(proof.now + 900, current["verified_until"])
            db.execute("DELETE FROM sessions WHERE installation=?", (installation,))
            db.execute("INSERT INTO sessions VALUES (?, ?, ?)", (self.tag("session", token), installation, expires))
        return {"token": token, "expiresAt": expires}

    def set_discovery(self, owner, now, enabled):
        with self.store.transaction() as db:
            self.installation(db, owner, now)
            db.execute("UPDATE installations SET discoverable=? WHERE id=?", (enabled, owner))
            if not enabled:
                db.execute("DELETE FROM discovery WHERE installation=?", (owner,))

    def lookup(self, owner, now, number):
        self.limit(now, (f"lookup:{owner}", 3600, 12), (f"lookup-day:{owner}", DAY, 30))
        phone = self.tag("phone", number)
        with self.store.transaction() as db:
            sender = self.installation(db, owner, now)
            if not sender["discoverable"] or sender["phone"] == phone:
                return False
            db.execute(
                """INSERT INTO discovery VALUES (?, ?, ?)
                   ON CONFLICT(installation,target) DO UPDATE SET expires=excluded.expires""",
                (owner, phone, now + 7 * DAY),
            )
            return self.target(db, sender, phone, now) is not None

    @staticmethod
    def target(db, sender, phone, now):
        if not sender["discoverable"] or sender["phone"] == phone:
            return None
        return db.execute(
            """SELECT i.id FROM installations i JOIN discovery d ON d.installation=i.id AND d.target=?
               JOIN discovery s ON s.installation=? AND s.target=i.phone
               WHERE i.phone=? AND i.discoverable=1 AND i.verified_until>? AND d.expires>? AND s.expires>?""",
            (sender["phone"], sender["id"], phone, now, now, now),
        ).fetchone()

    def detach(self, owner, now):
        with self.store.transaction() as db:
            row = self.installation(db, owner, now)
            db.execute("DELETE FROM discovery WHERE target=?", (row["phone"],))
            db.execute("DELETE FROM installations WHERE id=?", (owner,))

    def create_transfer(self, owner, now, key, manifest):
        opaque_id(key)
        size = sum(item.size for item in manifest.files)
        if size > 50 * MIB:
            raise Problem(413, "transfer_too_large")
        digest = sha(json.dumps(manifest.model_dump(), sort_keys=True).encode())
        with self.store.transaction() as db:
            sender = self.installation(db, owner, now)
            existing = db.execute("SELECT * FROM transfers WHERE owner=? AND request_key=?", (owner, key)).fetchone()
            if existing is not None:
                if existing["fingerprint"] != digest:
                    raise Problem(409, "idempotency_conflict")
                return self.transfer_result(db, self.owned(db, owner, existing["id"], now))
            self.check_request_key(db, owner, key, now)
            target = self.target(db, sender, self.tag("phone", manifest.recipient), now)
            if not target:
                raise Problem(409, "recipient_unavailable")
            used, count = db.execute("SELECT coalesce(sum(size), 0), count(*) FROM transfers WHERE owner=? AND expires>?", (owner, now)).fetchone()
            global_size, global_count = db.execute("SELECT coalesce(sum(size), 0), count(*) FROM transfers WHERE expires>?", (now,)).fetchone()
            if used + size > 100 * MIB or count >= 16 or global_size + size > 2048 * MIB or global_count >= 4096:
                raise Problem(429, "storage_quota", 3600)
            transfer = secrets.token_hex(16)
            db.execute("INSERT INTO transfers VALUES (?, ?, ?, ?, ?, 'uploading', ?, ?)", (transfer, owner, key, digest, target["id"], now + DAY, size))
            db.execute("INSERT INTO transfer_keys VALUES (?, ?, ?)", (owner, key, now + 30 * DAY))
            db.executemany("INSERT INTO files VALUES (?, ?, ?, ?, ?, ?, 0)", (
                (transfer, index, f.mime, f.variant, f.size, f.sha256) for index, f in enumerate(manifest.files)
            ))
            return self.transfer_result(db, self.owned(db, owner, transfer, now))

    def owned(self, db, owner, transfer, now):
        opaque_id(transfer)
        self.installation(db, owner, now)
        row = db.execute("SELECT * FROM transfers WHERE id=? AND owner=?", (transfer, owner)).fetchone()
        if row is None:
            raise Problem(404, "not_found")
        if row["expires"] <= now:
            raise Problem(410, "transfer_expired")
        return row

    @staticmethod
    def transfer_result(db, row):
        files = db.execute("SELECT position, size, offset FROM files WHERE transfer=? ORDER BY position", (row["id"],)).fetchall()
        envelope = db.execute("SELECT id FROM draft_envelopes WHERE transfer=?", (row["id"],)).fetchone()
        return {"transferId": row["id"], "state": row["state"], "expiresAt": row["expires"],
                "files": [dict(f) for f in files], "envelopeId": envelope["id"] if envelope else None}

    def read_transfer(self, owner, transfer, now):
        with self.store.transaction(write=False) as db:
            return self.transfer_result(db, self.owned(db, owner, transfer, now))

    def ticket(self, owner, transfer, position, now):
        with self.store.transaction(write=False) as db:
            row = self.owned(db, owner, transfer, now)
            f = db.execute("SELECT offset FROM files WHERE transfer=? AND position=?", (transfer, position)).fetchone()
            if f is None or row["state"] != "uploading":
                raise Problem(409, "transfer_not_uploading")
            expiry = min(row["expires"], now + 300)
            ticket = self.tag("upload", f"{owner}/{transfer}/{position}/{expiry}")
            return {"path": f"/v1/transfers/{transfer}/files/{position}/chunks?expires={expiry}&ticket={ticket}",
                    "offset": f["offset"], "chunkSize": CHUNK, "expiresAt": expiry}

    def upload(self, owner, transfer, position, now, expiry, ticket, offset, digest, body):
        expected = self.tag("upload", f"{owner}/{transfer}/{position}/{expiry}")
        if not re.fullmatch(r"[a-f0-9]{64}", ticket) or not now < expiry <= now + 300 or not hmac.compare_digest(expected, ticket):
            raise Problem(403, "invalid_upload_ticket")
        if not body or len(body) > CHUNK:
            raise Problem(413, "invalid_chunk_size")
        if not hmac.compare_digest(sha(body), digest):
            raise Problem(422, "chunk_checksum")
        with self.store.transaction() as db:
            row = self.owned(db, owner, transfer, now)
            f = db.execute("SELECT * FROM files WHERE transfer=? AND position=?", (transfer, position)).fetchone()
            if f is None or row["state"] != "uploading":
                raise Problem(409, "transfer_not_uploading")
            if offset < f["offset"]:
                chunk = db.execute("SELECT digest FROM chunks WHERE transfer=? AND position=? AND offset=?", (transfer, position, offset)).fetchone()
                if chunk is not None and hmac.compare_digest(chunk["digest"], digest):
                    return {"offset": f["offset"]}
            if offset != f["offset"]:
                raise Problem(409, "wrong_offset")
            if len(body) != min(CHUNK, f["size"] - offset):
                raise Problem(413, "invalid_chunk_size")
            db.execute("INSERT INTO chunks VALUES (?, ?, ?, ?, ?)", (transfer, position, offset, digest, body))
            end = offset + len(body)
            db.execute("UPDATE files SET offset=? WHERE transfer=? AND position=?", (end, transfer, position))
            return {"offset": end}

    def finalize(self, owner, transfer, now):
        invalid = False
        with self.store.transaction() as db:
            row = self.owned(db, owner, transfer, now)
            if row["state"] == "finalized":
                return self.transfer_result(db, row)
            if row["state"] != "uploading":
                raise Problem(422, "file_checksum")
            sender = self.installation(db, owner, now)
            recipient = db.execute(
                "SELECT phone FROM installations WHERE id=? AND verified_until>?", (row["target"], now)
            ).fetchone()
            if not recipient or not self.target(db, sender, recipient["phone"], now):
                raise Problem(409, "recipient_unavailable")
            files = db.execute("SELECT * FROM files WHERE transfer=? ORDER BY position", (transfer,)).fetchall()
            if any(f["size"] != f["offset"] for f in files):
                raise Problem(409, "incomplete_transfer")
            for file in files:
                digest = hashlib.sha256()
                for chunk in db.execute("SELECT data FROM chunks WHERE transfer=? AND position=? ORDER BY offset", (transfer, file["position"])):
                    digest.update(chunk["data"])
                if not hmac.compare_digest(file["digest"], digest.hexdigest()):
                    invalid = True
                    break
            if invalid:
                db.execute("UPDATE transfers SET state='invalid', size=0 WHERE id=?", (transfer,))
                db.execute("DELETE FROM chunks WHERE transfer=?", (transfer,))
            else:
                db.execute("UPDATE transfers SET state='finalized', expires=? WHERE id=?", (now + 7 * DAY, transfer))
                db.execute("INSERT INTO draft_envelopes VALUES (?, ?)", (secrets.token_hex(16), transfer))
                return self.transfer_result(db, self.owned(db, owner, transfer, now))
        raise Problem(422, "file_checksum")

    def cancel(self, owner, transfer, now):
        with self.store.transaction() as db:
            self.owned(db, owner, transfer, now)
            db.execute("DELETE FROM transfers WHERE id=?", (transfer,))

    @staticmethod
    def check_request_key(db, owner, key, now, allow_used=False):
        used = db.execute("SELECT expires FROM transfer_keys WHERE owner=? AND request_key=?", (owner, key)).fetchone()
        if used and used["expires"] > now:
            if allow_used:
                return
            raise Problem(410, "transfer_expired")
        db.execute("DELETE FROM transfer_keys WHERE owner=? AND expires<=?", (owner, now))
        if db.execute("SELECT count(*) FROM transfer_keys WHERE owner=?", (owner,)).fetchone()[0] >= 256:
            raise Problem(429, "transfer_request_limit", 3600)

    def cancel_request(self, owner, key, now):
        opaque_id(key)
        with self.store.transaction() as db:
            self.installation(db, owner, now)
            self.check_request_key(db, owner, key, now, allow_used=True)
            db.execute("DELETE FROM transfers WHERE owner=? AND request_key=?", (owner, key))
            db.execute("INSERT OR IGNORE INTO transfer_keys VALUES (?, ?, ?)", (owner, key, now + 30 * DAY))

    def list_transfers(self, owner, now, after, limit):
        if after:
            opaque_id(after)
        with self.store.transaction(write=False) as db:
            self.installation(db, owner, now)
            rows = db.execute(
                "SELECT * FROM transfers WHERE owner=? AND expires>? AND id>? ORDER BY id LIMIT ?",
                (owner, now, after or "", limit + 1),
            ).fetchall()
            return {"items": [self.transfer_result(db, row) for row in rows[:limit]],
                    "nextCursor": rows[limit - 1]["id"] if len(rows) > limit else None}

    def purchase(self, proof, payload):
        self.limit(proof.now, (f"play:{self.source(proof)}", 60, 30), ("play-global", 60, 120))
        if self.play is None:
            raise Problem(503, "billing_unavailable")
        if payload.productId != self.play.product:
            return {"state": "invalid"}
        return {"state": self.play.verify(payload.token)}
