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

from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm, InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from fastapi import Request
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator
from storage import audit, event, revoke_events
from providers import ProviderUnavailable

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
    recipient: PHONE | None = None
    files: list[FileSpec] = Field(min_length=1, max_length=5)
    delivery: Literal["hd", "link"] = "hd"

    @model_validator(mode="after")
    def recipient_for_hd(self):
        if self.delivery == "hd" and self.recipient is None:
            raise ValueError("HD requires a recipient")
        if self.delivery == "link" and self.recipient is not None:
            raise ValueError("Link transfers keep the recipient on the sender device")
        return self


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

    def encrypt_number(self, number, installation):
        nonce = secrets.token_bytes(12)
        key = bytes.fromhex(self.tag("key", "routing-address"))
        return nonce + AESGCM(key).encrypt(nonce, number.encode("ascii"), installation.encode("ascii"))

    def number(self, installation):
        value = installation["phone_cipher"]
        if value is None:
            raise Problem(409, "sender_reverification_required")
        key = bytes.fromhex(self.tag("key", "routing-address"))
        try:
            return AESGCM(key).decrypt(value[:12], value[12:], installation["id"].encode("ascii")).decode("ascii")
        except (InvalidTag, ValueError):
            raise Problem(409, "sender_reverification_required") from None

    @staticmethod
    def revoke_installation_transfers(db, installation, now):
        for transfer in db.execute("SELECT * FROM transfers WHERE owner=? OR target=?", (installation, installation)).fetchall():
            revoke_events(db, transfer, now)

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
                audit(db, now, "rate_limited")
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
                    for previous in db.execute("SELECT id FROM installations WHERE phone=? OR public_key=?", (row["phone"], row["public_key"])).fetchall():
                        self.revoke_installation_transfers(db, previous["id"], now)
                    db.execute(
                        "DELETE FROM discovery WHERE target IN (SELECT phone FROM installations WHERE phone=? OR public_key=?)",
                        (row["phone"], row["public_key"]),
                    )
                    db.execute("DELETE FROM installations WHERE phone=? OR public_key=?", (row["phone"], row["public_key"]))
                    installation = challenge
                    db.execute("INSERT INTO installations(id,public_key,phone,discoverable,verified_until) VALUES (?, ?, ?, 0, ?)", (installation, row["public_key"], row["phone"], now + 90 * DAY))
                db.execute("UPDATE installations SET phone_cipher=? WHERE id=?", (self.encrypt_number(number, installation), installation))
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
            self.revoke_installation_transfers(db, owner, now)
            db.execute("DELETE FROM discovery WHERE target=?", (row["phone"],))
            db.execute("DELETE FROM installations WHERE id=?", (owner,))

    def create_transfer(self, owner, now, key, manifest):
        opaque_id(key)
        size = sum(item.size for item in manifest.files)
        if size > 50 * MIB:
            raise Problem(413, "transfer_too_large")
        value = manifest.model_dump()
        if manifest.delivery == "hd":
            value.pop("delivery")
        digest = sha(json.dumps(value, sort_keys=True).encode())
        with self.store.transaction() as db:
            sender = self.installation(db, owner, now)
            existing = db.execute("SELECT * FROM transfers WHERE owner=? AND request_key=?", (owner, key)).fetchone()
            if existing is not None:
                if existing["fingerprint"] != digest:
                    raise Problem(409, "idempotency_conflict")
                return self.transfer_result(db, self.owned(db, owner, existing["id"], now))
            self.check_request_key(db, owner, key, now)
            target = self.target(db, sender, self.tag("phone", manifest.recipient), now) if manifest.delivery == "hd" else sender
            if target is None:
                raise Problem(409, "recipient_unavailable")
            used, count = db.execute("SELECT coalesce(sum(size), 0), count(*) FROM transfers WHERE owner=? AND expires>?", (owner, now)).fetchone()
            global_size, global_count = db.execute("SELECT coalesce(sum(size), 0), count(*) FROM transfers WHERE expires>?", (now,)).fetchone()
            if used + size > 100 * MIB or count >= 16 or global_size + size > 2048 * MIB or global_count >= 4096:
                raise Problem(429, "storage_quota", 3600)
            transfer = secrets.token_hex(16)
            db.execute("INSERT INTO transfers(id,owner,request_key,fingerprint,target,state,expires,size,delivery,created) VALUES (?, ?, ?, ?, ?, 'uploading', ?, ?, ?, ?)", (transfer, owner, key, digest, target["id"], now + DAY, size, manifest.delivery, now))
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
            if row["delivery"] == "hd" and (not recipient or not self.target(db, sender, recipient["phone"], now)):
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
                envelope = secrets.token_hex(16)
                db.execute("INSERT INTO draft_envelopes VALUES (?, ?)", (envelope, transfer))
                if row["delivery"] == "hd":
                    self.number(sender)
                    event(db, row["target"], envelope, "envelope", now)
                    event(db, owner, envelope, "sent", now)
                audit(db, now, "finalized")
                return self.transfer_result(db, self.owned(db, owner, transfer, now))
        raise Problem(422, "file_checksum")

    def cancel(self, owner, transfer, now):
        with self.store.transaction() as db:
            self.owned(db, owner, transfer, now)
            revoke_events(db, self.owned(db, owner, transfer, now), now)
            db.execute("DELETE FROM transfers WHERE id=?", (transfer,))
            audit(db, now, "revoked")

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
            for row in db.execute("SELECT * FROM transfers WHERE owner=? AND request_key=?", (owner, key)).fetchall():
                revoke_events(db, row, now)
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

    def envelope(self, owner, envelope, now):
        opaque_id(envelope)
        with self.store.transaction(write=False) as db:
            self.installation(db, owner, now)
            row = db.execute(
                """SELECT t.*, e.id AS envelope FROM transfers t JOIN draft_envelopes e ON e.transfer=t.id
                   WHERE e.id=? AND (t.owner=? OR t.target=?) AND t.delivery='hd'
                   AND t.state='finalized' AND t.expires>?""", (envelope, owner, owner, now),
            ).fetchone()
            if row is None:
                raise Problem(404, "not_found")
            sender = self.installation(db, row["owner"], now)
            target = self.installation(db, row["target"], now)
            files = db.execute("SELECT position,mime,variant,size,digest AS sha256 FROM files WHERE transfer=? ORDER BY position", (row["id"],)).fetchall()
            receipt = db.execute("SELECT downloaded FROM download_receipts WHERE envelope=?", (envelope,)).fetchone()
            return {
                "id": envelope, "transferId": row["id"], "sender": self.number(sender),
                "recipient": self.number(target), "incoming": row["target"] == owner,
                "createdAt": row["created"] or row["expires"] - 7 * DAY,
                "expiresAt": row["expires"], "files": [dict(f) for f in files],
                "downloadedAt": receipt[0] if receipt else None,
            }

    def inbox(self, owner, now, cursor, limit):
        sequence = 0
        if cursor:
            parts = cursor.split(".")
            if len(parts) != 2 or not parts[0].isdigit() or len(parts[0]) > 18:
                raise Problem(400, "invalid_cursor")
            sequence = int(parts[0])
            if not hmac.compare_digest(self.tag("cursor", f"{owner}/{sequence}"), parts[1]):
                raise Problem(400, "invalid_cursor")
        with self.store.transaction(write=False) as db:
            self.installation(db, owner, now)
            floor = db.execute("SELECT sequence FROM mailbox_floor WHERE id=1").fetchone()[0]
            if cursor and sequence < floor:
                raise Problem(409, "cursor_expired")
            rows = db.execute(
                """SELECT sequence,envelope,kind FROM mailbox_events
                   WHERE recipient=? AND sequence>? AND expires>? ORDER BY sequence LIMIT ?""",
                (owner, sequence, now, limit + 1),
            ).fetchall()
            page = rows[:limit]
            next_sequence = page[-1]["sequence"] if page else max(sequence, floor)
            return {"items": [dict(r) for r in page],
                    "cursor": f"{next_sequence}.{self.tag('cursor', f'{owner}/{next_sequence}')}",
                    "hasMore": len(rows) > limit}

    def download_chunk(self, owner, envelope, position, offset, now):
        opaque_id(envelope)
        self.limit(now, (f"download:{owner}", 3600, 4096))
        with self.store.transaction(write=False) as db:
            self.installation(db, owner, now)
            row = db.execute(
                """SELECT t.id FROM transfers t JOIN draft_envelopes e ON e.transfer=t.id
                   WHERE e.id=? AND t.target=? AND t.state='finalized' AND t.delivery='hd' AND t.expires>?""",
                (envelope, owner, now),
            ).fetchone()
            if row is None:
                raise Problem(404, "not_found")
            return self.file_chunk(db, row["id"], position, offset)

    @staticmethod
    def file_chunk(db, transfer, position, offset):
        if not 0 <= position < 5 or not 0 <= offset < 25 * MIB or offset % CHUNK:
            raise Problem(400, "invalid_offset")
        row = db.execute("SELECT data FROM chunks WHERE transfer=? AND position=? AND offset=?", (transfer, position, offset)).fetchone()
        if row is None:
            raise Problem(404, "not_found")
        return bytes(row["data"])

    def receipt(self, owner, envelope, now, hashes):
        opaque_id(envelope)
        with self.store.transaction() as db:
            self.installation(db, owner, now)
            row = db.execute(
                """SELECT t.* FROM transfers t JOIN draft_envelopes e ON e.transfer=t.id
                   WHERE e.id=? AND t.target=? AND t.delivery='hd' AND t.state='finalized' AND t.expires>?""",
                (envelope, owner, now),
            ).fetchone()
            if row is None:
                raise Problem(404, "not_found")
            expected = [r[0] for r in db.execute("SELECT digest FROM files WHERE transfer=? ORDER BY position", (row["id"],))]
            if hashes != expected:
                raise Problem(422, "file_checksum")
            db.execute("INSERT OR IGNORE INTO download_receipts VALUES (?,?,?)", (envelope, owner, now))
            event(db, row["owner"], envelope, "downloaded", now)
            return {"downloadedAt": db.execute("SELECT downloaded FROM download_receipts WHERE envelope=?", (envelope,)).fetchone()[0]}

    def register_push(self, owner, now, token):
        with self.store.transaction() as db:
            self.installation(db, owner, now)
            db.execute("INSERT INTO push_tokens VALUES (?,?,?) ON CONFLICT(installation) DO UPDATE SET token=excluded.token,updated=excluded.updated", (owner, token, now))

    def dispatch_push(self, provider, now):
        if provider is None:
            return
        with self.store.transaction(write=False) as db:
            rows = db.execute(
                """SELECT e.sequence,e.recipient,e.push_attempts,p.token FROM mailbox_events e
                   JOIN push_tokens p ON p.installation=e.recipient
                   JOIN installations i ON i.id=e.recipient
                   WHERE e.pushed=0 AND e.push_after<=? AND e.expires>? AND i.verified_until>?
                   ORDER BY e.sequence LIMIT 20""", (now, now, now),
            ).fetchall()
        for row in rows:
            # Retries may repeat this signal. Clients always synchronize the authenticated inbox.
            try:
                accepted = provider.send(row["token"], {"event": self.tag("push-event", str(row["sequence"]))[:32]})
            except ProviderUnavailable:
                with self.store.transaction() as db:
                    db.execute("UPDATE mailbox_events SET push_attempts=push_attempts+1,push_after=? WHERE sequence=?", (now + min(300, 5 * 2 ** min(6, row["push_attempts"])), row["sequence"]))
                    audit(db, now, "push_failures")
            else:
                with self.store.transaction() as db:
                    if accepted is False:
                        db.execute("DELETE FROM push_tokens WHERE installation=? AND token=?", (row["recipient"], row["token"]))
                        audit(db, now, "push_tokens_expired")
                    else:
                        db.execute("UPDATE mailbox_events SET pushed=1 WHERE sequence=?", (row["sequence"],))

    def share_result(self, row):
        secret = self.tag("share-secret", row["id"])
        token = f"{row['id']}.{secret}"
        return {"id": row["id"], "path": f"/r/#{token}", "expiresAt": row["expires"]}

    def create_share(self, owner, transfer, now, key, consent, ttl):
        opaque_id(key)
        if consent is not True:
            raise Problem(400, "link_consent_required")
        with self.store.transaction() as db:
            row = self.owned(db, owner, transfer, now)
            if row["state"] != "finalized" or row["delivery"] != "link":
                raise Problem(409, "link_transfer_required")
            previous = db.execute("SELECT * FROM shares WHERE owner=? AND request_key=?", (owner, key)).fetchone()
            if previous is not None:
                if previous["transfer"] != transfer:
                    raise Problem(409, "idempotency_conflict")
                if previous["revoked"] or previous["expires"] <= now:
                    raise Problem(410, "share_unavailable")
                return self.share_result(previous)
            if db.execute("SELECT count(*) FROM shares WHERE transfer=?", (transfer,)).fetchone()[0] >= 3:
                raise Problem(429, "share_limit", 3600)
            ident = secrets.token_hex(16)
            token = f"{ident}.{self.tag('share-secret', ident)}"
            expiry = min(row["expires"], now + ttl)
            db.execute("INSERT INTO shares VALUES (?,?,?,?,?,?,0)", (ident, transfer, owner, key, self.tag("share", token), expiry))
            audit(db, now, "links_created")
            return self.share_result(db.execute("SELECT * FROM shares WHERE id=?", (ident,)).fetchone())

    def revoke_share(self, owner, share, now):
        opaque_id(share)
        with self.store.transaction() as db:
            self.installation(db, owner, now)
            if db.execute("UPDATE shares SET revoked=1 WHERE id=? AND owner=?", (share, owner)).rowcount != 1:
                raise Problem(404, "not_found")
            audit(db, now, "links_revoked")

    def public_share(self, db, token, now):
        if not re.fullmatch(r"[0-9a-f]{32}\.[0-9a-f]{64}", token):
            raise Problem(410, "share_unavailable")
        ident = token.split(".")[0]
        row = db.execute(
            """SELECT s.* FROM shares s JOIN transfers t ON t.id=s.transfer
               JOIN installations i ON i.id=t.owner
               WHERE s.id=? AND s.revoked=0 AND s.expires>? AND t.expires>?
               AND t.state='finalized' AND i.verified_until>?""", (ident, now, now, now),
        ).fetchone()
        if row is None or not hmac.compare_digest(row["secret_hash"], self.tag("share", token)):
            raise Problem(410, "share_unavailable")
        return row

    def resolve_share(self, proof, token):
        self.limit(proof.now, (f"public-resolve:{self.source(proof)}", 60, 60))
        with self.store.transaction(write=False) as db:
            row = self.public_share(db, token, proof.now)
            files = db.execute("SELECT position,size,mime,digest AS sha256 FROM files WHERE transfer=? ORDER BY position", (row["transfer"],)).fetchall()
            return {"expiresAt": row["expires"], "files": [dict(f) for f in files]}

    def public_chunk(self, proof, token, position, offset):
        self.limit(proof.now, (f"public-download:{self.source(proof)}", 3600, 4096))
        with self.store.transaction(write=False) as db:
            row = self.public_share(db, token, proof.now)
            return self.file_chunk(db, row["transfer"], position, offset)
