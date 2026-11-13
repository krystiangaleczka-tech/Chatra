import base64
import json
import secrets
from concurrent.futures import ThreadPoolExecutor

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from api import create_app
from providers import GooglePlay, ProviderUnavailable
from service import CHUNK, DAY, MIB, Service, sha
from storage import Storage


class FakeOtp:
    def __init__(self):
        self.pending = {}

    def start(self, number):
        sid = secrets.token_hex(16)
        self.pending[sid] = number
        return sid

    def verify(self, sid, code):
        return self.pending.pop(sid, None) if code == "123456" else None


def b64(data):
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


class Device:
    def __init__(self, client, clock, number, key=None):
        self.client, self.clock, self.number = client, clock, number
        self.key = key or ec.generate_private_key(ec.SECP256R1())
        self.installation, self.token = "", ""

    def request(self, method, path, data=None, credential=None, headers=None, nonce=None):
        body = data if isinstance(data, bytes) else (json.dumps(data).encode() if data is not None else b"")
        headers = dict(headers or {})
        time = str(self.clock[0])
        nonce = nonce or secrets.token_hex(16)
        if credential is None:
            credential = self.token
            headers["Authorization"] = f"Bearer {credential}"
        message = "\n".join((
            "CHATRA-HD-1", method, path, sha(body), time, nonce, sha(credential.encode()),
            headers.get("Idempotency-Key", ""), headers.get("Upload-Offset", ""), headers.get("X-Chunk-SHA256", ""),
        )).encode()
        headers.update({"X-Chatra-Time": time, "X-Chatra-Nonce": nonce,
                        "X-Chatra-Signature": b64(self.key.sign(message, ec.ECDSA(hashes.SHA256())))})
        if body:
            headers["Content-Type"] = "application/octet-stream" if isinstance(data, bytes) else "application/json"
        return self.client.request(method, path, content=body, headers=headers)

    def start(self):
        pub = self.key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        return self.request("POST", "/v1/number-challenges", {"number": self.number, "publicKey": b64(pub)}, credential="")

    def check(self, challenge, code="123456"):
        return self.request("POST", f"/v1/number-challenges/{challenge}/verification", {"code": code}, credential=challenge)

    def rotate(self):
        response = self.request("POST", f"/v1/installations/{self.installation}/sessions", credential=self.installation)
        if response.status_code == 200:
            self.token = response.json()["token"]
        return response

    def activate(self):
        started = self.start()
        assert started.status_code == 201, started.text
        verified = self.check(started.json()["challengeId"])
        assert verified.status_code == 200, verified.text
        self.installation = verified.json()["installationId"]
        assert self.rotate().status_code == 200

    def discover(self, other):
        return self.request("POST", "/v1/capabilities/lookup", {"number": other.number}).json()["available"]

    def opt_in(self):
        assert self.request("PUT", "/v1/discovery", {"enabled": True}).status_code == 204

    def create(self, recipient, files, key=None):
        return self.request("POST", "/v1/transfers", {"recipient": recipient.number, "files": files},
                            headers={"Idempotency-Key": key or secrets.token_hex(16)})

    def ticket(self, transfer, position=0):
        return self.request("POST", f"/v1/transfers/{transfer}/files/{position}/ticket").json()["path"]

    def put(self, path, data, offset, digest=None):
        return self.request("PUT", path, data, headers={"Upload-Offset": str(offset), "X-Chunk-SHA256": digest or sha(data)})


@pytest.fixture
def setup(tmp_path):
    clock = [1_800_000_000]
    storage = Storage(tmp_path / "test.sqlite3")
    service = Service(storage, b"test secret bytes that never go online", FakeOtp(), clock=lambda: clock[0])
    with TestClient(create_app(service)) as client:
        yield service, client, clock


def devices(setup):
    _, client, clock = setup
    a = Device(client, clock, "+12025550101")
    b = Device(client, clock, "+12025550102")
    a.activate()
    b.activate()
    return a, b


def paired(setup):
    a, b = devices(setup)
    a.opt_in()
    b.opt_in()
    assert not a.discover(b)
    assert b.discover(a)
    return a, b


def spec(content):
    return {"mime": "image/png", "variant": "original", "size": len(content), "sha256": sha(content)}


def test_wrong_code_replay_and_rate_limit(setup):
    _, client, clock = setup
    a = Device(client, clock, "+12025550103")
    challenge = a.start().json()["challengeId"]
    assert a.start().status_code == 429
    for _ in range(4):
        assert a.check(challenge, "123457").status_code == 401
    assert a.check(challenge).status_code == 200
    assert a.check(challenge).status_code == 401
    assert Device(client, clock, a.number).check(challenge).status_code == 401
    b = Device(client, clock, "+12025550104")
    challenge = b.start().json()["challengeId"]
    for _ in range(5):
        assert b.check(challenge, "123457").status_code == 401
    assert b.check(challenge).status_code == 401


def test_session_rotation_stolen_session_replay_and_recovery(setup):
    a, b = paired(setup)
    path = "/v1/transfers"
    nonce = secrets.token_hex(16)
    assert a.request("GET", path, nonce=nonce).status_code == 200
    assert a.request("GET", path, nonce=nonce).json()["code"] == "replayed_request"
    b.token = a.token
    assert b.request("GET", path).json()["code"] == "invalid_proof"
    previous = a.token
    assert a.rotate().status_code == 200
    current = a.token
    a.token = previous
    assert a.request("GET", path).json()["code"] == "session_expired"
    a.token = current
    transfer = a.create(b, [spec(b"data")]).json()["transferId"]
    a.clock[0] += 601
    replacement = Device(a.client, a.clock, a.number)
    replacement.activate()
    assert a.rotate().json()["code"] == "binding_required"
    assert replacement.request("GET", f"{path}/{transfer}").status_code == 404
    assert replacement.request("DELETE", "/v1/binding").status_code == 204
    assert replacement.rotate().status_code == 401


def test_discovery_consent_and_pair_budget(setup):
    a, b = devices(setup)
    a.opt_in()
    assert not a.discover(b)
    b.opt_in()
    assert not a.discover(b)
    assert b.discover(a)
    assert a.discover(b)
    assert b.request("PUT", "/v1/discovery", {"enabled": False}).status_code == 204
    assert not a.discover(b)
    for _ in range(8):
        assert not a.discover(b)
    response = a.request("POST", "/v1/capabilities/lookup", {"number": b.number})
    assert response.status_code == 429
    assert int(response.headers["Retry-After"]) > 0


def test_transfer_restart_resume_idempotency_checksum_and_finalize_race(setup):
    service, _, clock = setup
    a, b = paired(setup)
    data = b"a" * CHUNK + b"tail"
    key = secrets.token_hex(16)
    first = a.create(b, [spec(data)], key)
    assert first.status_code == 201, first.text
    id_ = first.json()["transferId"]
    path = f"/v1/transfers/{id_}"
    ticket = a.ticket(id_)
    assert a.put(ticket, data[:CHUNK], 0, sha(b"wrong")).status_code == 422
    assert a.put(ticket, data[-4:], CHUNK).json()["code"] == "wrong_offset"
    assert a.put(ticket, data[:CHUNK], 0).json()["offset"] == CHUNK
    assert b.put(ticket, data[-4:], CHUNK).status_code == 403
    assert b.request("GET", path).status_code == 404
    assert a.request("POST", f"{path}/finalize").json()["code"] == "incomplete_transfer"
    clock[0] += 301
    assert a.put(ticket, data[-4:], CHUNK).status_code == 403
    with TestClient(create_app(Service(Storage(service.store.path), service.secret, service.otp, clock=lambda: clock[0]))) as restarted:
        a.client = restarted
        assert a.create(b, [spec(b"different")], key).json()["code"] == "idempotency_conflict"
        assert a.create(b, [spec(data)], key).json()["files"][0]["offset"] == CHUNK
        ticket = a.ticket(id_)
        assert a.put(ticket, data[:CHUNK], 0).json()["offset"] == CHUNK
        assert a.put(ticket, data[-4:], CHUNK).json()["offset"] == len(data)
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda _: a.request("POST", f"{path}/finalize"), range(2)))
        assert all(response.status_code == 200 for response in results)
        assert results[0].json() == results[1].json()
        assert results[0].json()["state"] == "finalized" and results[0].json()["envelopeId"]
        with service.store.transaction(write=False) as db:
            assert db.execute("SELECT count(*) FROM chunks").fetchone()[0] == 2
            assert db.execute("SELECT count(*) FROM draft_envelopes").fetchone()[0] == 1


def test_final_digest_mismatch_releases_bytes_and_keeps_draft_unpublished(setup):
    service, _, _ = setup
    a, b = paired(setup)
    id_ = a.create(b, [spec(b"abc")]).json()["transferId"]
    assert a.put(a.ticket(id_), b"abd", 0).status_code == 200
    result = a.request("POST", f"/v1/transfers/{id_}/finalize")
    assert result.json()["code"] == "file_checksum"
    assert a.request("GET", f"/v1/transfers/{id_}").json()["state"] == "invalid"
    with service.store.transaction(write=False) as db:
        assert db.execute("SELECT count(*) FROM chunks").fetchone()[0] == 0
        assert db.execute("SELECT count(*) FROM draft_envelopes").fetchone()[0] == 0


def test_quota_pagination_cancel_before_create_and_cleanup(setup):
    service, _, clock = setup
    a, b = paired(setup)
    part = {"mime": "video/mp4", "variant": "original", "size": 25 * MIB, "sha256": sha(b"x")}
    assert a.create(b, [part] * 3).status_code == 413
    first = a.create(b, [part, part])
    second = a.create(b, [part, part])
    assert first.status_code == second.status_code == 201
    assert a.create(b, [part]).json()["code"] == "storage_quota"
    page = a.request("GET", "/v1/transfers?limit=1").json()
    page2 = a.request("GET", f"/v1/transfers?limit=1&cursor={page['nextCursor']}").json()
    assert page["items"][0]["transferId"] != page2["items"][0]["transferId"]
    assert page2["nextCursor"] is None
    cancel = secrets.token_hex(16)
    assert a.request("DELETE", f"/v1/transfer-requests/{cancel}").status_code == 204
    assert a.create(b, [part], cancel).json()["code"] == "transfer_expired"
    clock[0] += DAY + 1
    service.store.cleanup(clock[0])
    assert a.rotate().status_code == 200
    assert not a.request("GET", "/v1/transfers").json()["items"]
    assert a.create(b, [part, part]).status_code == 201


def test_request_size_and_error_redaction(setup):
    _, client, _ = setup
    response = client.post("/v1/number-challenges", content=b"x" * 8193)
    assert response.status_code == 413
    response = client.post("/v1/number-challenges", json={"number": "private text", "publicKey": "no"})
    assert response.status_code == 400 and "private text" not in response.text


class StoreResponse:
    def __init__(self, status, body=None):
        self.status_code = status
        self.body = body

    def json(self):
        return self.body

    def raise_for_status(self):
        import requests
        if self.status_code >= 400:
            raise requests.HTTPError()


class PlayHttp:
    def __init__(self):
        self.gets = []
        self.acks = []
        self.status = 200
        self.ack_status = 204
        self.item = {
            "productLineItem": [{"productId": "chatra_pro", "productOfferDetails": {
                "quantity": 1, "refundableQuantity": 1, "consumptionState": "CONSUMPTION_STATE_YET_TO_BE_CONSUMED",
            }}],
            "purchaseStateContext": {"purchaseState": "PURCHASED"},
            "acknowledgementState": "ACKNOWLEDGEMENT_STATE_PENDING",
        }

    def get(self, url, **kwargs):
        self.gets.append(url)
        return StoreResponse(self.status, self.item)

    def post(self, url, **kwargs):
        self.acks.append(url)
        return StoreResponse(self.ack_status)


def test_play_verification_ack_pending_restore_and_refund(setup):
    import threading

    service, client, _ = setup
    play = GooglePlay.__new__(GooglePlay)
    play.url = "https://publisher.invalid/app.chatra"
    play.product = "chatra_pro"
    play.http = PlayHttp()
    play.lock = threading.Lock()
    service.play = play

    def verify(token="token", product="chatra_pro"):
        return client.post("/v1/billing/google-play/verify", json={"productId": product, "token": token})

    assert verify(product="other").json()["state"] == "invalid"
    assert not play.http.gets
    assert verify().json()["state"] == "active"
    assert play.http.acks == ["https://publisher.invalid/app.chatra/purchases/products/chatra_pro/tokens/token:acknowledge"]
    play.http.item["acknowledgementState"] = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
    assert verify().json()["state"] == "active"
    assert len(play.http.acks) == 1
    play.http.item["productLineItem"][0]["productOfferDetails"]["refundableQuantity"] = 0
    assert verify().json()["state"] == "invalid"
    play.http.item["purchaseStateContext"]["purchaseState"] = "PENDING"
    assert verify().json()["state"] == "pending"
    assert len(play.http.acks) == 1
    play.http.item["purchaseStateContext"]["purchaseState"] = "CANCELLED"
    assert verify().json()["state"] == "invalid"
    play.http.status = 404
    assert verify("invalid").json()["state"] == "invalid"
    play.http.status = 503
    assert verify().status_code == 503


def test_acknowledgement_failure_never_grants():
    import threading

    play = GooglePlay.__new__(GooglePlay)
    play.url, play.product, play.http, play.lock = "https://publisher.invalid/app.chatra", "chatra_pro", PlayHttp(), threading.Lock()
    play.http.ack_status = 409
    with pytest.raises(ProviderUnavailable):
        play.verify("token")


def test_twilio_verify_checks_exact_challenge_and_handles_provider_errors(monkeypatch):
    from providers import TwilioVerify

    number, service_sid, verification_sid = "+12025550101", "VA" + "0" * 32, "VE" + "1" * 32
    twilio = TwilioVerify(service_sid, "synthetic-key", "synthetic-secret")
    responses = iter([
        StoreResponse(201, {"status": "pending", "sid": verification_sid}),
        StoreResponse(200, {"status": "approved", "to": number}),
        StoreResponse(404), StoreResponse(429),
    ])
    calls = []

    def post(url, data, auth, timeout):
        calls.append((url, data))
        return next(responses)

    monkeypatch.setattr("providers.requests.post", post)
    assert twilio.start(number) == verification_sid
    assert twilio.verify(verification_sid, "123456") == number
    assert calls[0] == (f"https://verify.twilio.com/v2/Services/{service_sid}/Verifications", {"To": number, "Channel": "sms"})
    assert calls[1][1] == {"VerificationSid": verification_sid, "Code": "123456"}
    assert twilio.verify(verification_sid, "123456") is None
    with pytest.raises(ProviderUnavailable):
        twilio.start(number)
