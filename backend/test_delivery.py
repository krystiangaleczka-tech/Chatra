import json
import secrets
import sqlite3
from concurrent.futures import ThreadPoolExecutor

from fastapi.testclient import TestClient

from api import create_app
from providers import ProviderUnavailable
from restore import invalidate_snapshot
from service import CHUNK, DAY, Service, sha
from storage import Storage
from test_api import Device, FakeOtp, paired, setup, spec


def delivered(a, b, contents=None, link=False):
    contents = contents or [b"image" * 70_000, b"video" * 53_000]
    files = [spec(data) for data in contents]
    if link:
        created = a.request("POST", "/v1/transfers", {"delivery": "link", "files": files},
                            headers={"Idempotency-Key": secrets.token_hex(16)})
    else:
        created = a.create(b, files)
    assert created.status_code == 201, created.text
    transfer = created.json()["transferId"]
    for position, data in enumerate(contents):
        ticket = a.ticket(transfer, position)
        for offset in range(0, len(data), CHUNK):
            assert a.put(ticket, data[offset:offset + CHUNK], offset).status_code == 200
    result = a.request("POST", f"/v1/transfers/{transfer}/finalize")
    assert result.status_code == 200, result.text
    return result.json(), contents


def test_offline_sync_duplicate_finalization_download_receipt_and_authorization(setup):
    service, client, clock = setup
    a, b = paired(setup)
    outsider = Device(client, clock, "+12025550103")
    outsider.activate()
    response, contents = delivered(a, b)
    envelope = response["envelopeId"]
    transfer = response["transferId"]
    path = f"/v1/envelopes/{envelope}"
    assert outsider.request("GET", path).status_code == 404
    assert outsider.request("GET", f"{path}/files/0/chunks?offset=0").status_code == 404
    assert a.request("GET", f"{path}/files/0/chunks?offset=0").status_code == 404
    with ThreadPoolExecutor(max_workers=2) as pool:
        repeats = list(pool.map(lambda _: a.request("POST", f"/v1/transfers/{transfer}/finalize").json(), range(2)))
    assert repeats == [response, response]
    # Recipient was offline throughout finalize. Server restart preserves one envelope.
    with TestClient(create_app(Service(Storage(service.store.path), service.secret, service.otp, clock=lambda: clock[0]))) as restarted:
        b.client = restarted
        inbox = b.request("GET", "/v1/inbox?limit=1").json()
        assert [i["kind"] for i in inbox["items"]] == ["envelope"]
        assert not inbox["hasMore"]
        assert b.request("GET", "/v1/inbox").json() == inbox
        assert b.request("GET", f"/v1/inbox?cursor={inbox['cursor']}").json()["items"] == []
        assert a.request("GET", f"/v1/inbox?cursor={inbox['cursor']}").status_code == 400
        metadata = b.request("GET", path).json()
        assert metadata["incoming"] and metadata["sender"] == a.number and metadata["recipient"] == b.number
        assert a.request("GET", path).json()["downloadedAt"] is None
        assert b.request("GET", f"{path}/files/0/chunks?offset={2 ** 100}").status_code == 400
        assert b.request("GET", f"{path}/files/{2 ** 100}/chunks?offset=0").status_code == 400
        assert b.request("GET", f"{path}/files/0/chunks?offset=1").status_code == 400
        for position, original in enumerate(contents):
            chunks = []
            for offset in range(0, len(original), CHUNK):
                part = b.request("GET", f"{path}/files/{position}/chunks?offset={offset}")
                assert part.status_code == 200 and part.headers["cache-control"] == "no-store"
                chunks.append(part.content)
            assert b"".join(chunks) == original
        assert b.request("POST", f"{path}/receipt", {"files": [sha(b"wrong")]}).status_code == 422
        hashes = {"files": [sha(data) for data in contents]}
        receipt = b.request("POST", f"{path}/receipt", hashes).json()
        clock[0] += 3
        assert b.request("POST", f"{path}/receipt", hashes).json() == receipt
        assert a.request("POST", f"{path}/receipt", hashes).status_code == 404
        assert a.request("GET", path).json()["downloadedAt"] == receipt["downloadedAt"]
        assert [i["kind"] for i in a.request("GET", "/v1/inbox").json()["items"]] == ["sent", "downloaded"]


def test_push_outbox_retries_are_only_signals(setup):
    service, _, clock = setup
    a, b = paired(setup)
    result, _ = delivered(a, b, [b"test"])
    assert b.request("PUT", "/v1/push-registration", {"token": "synthetic-device-token"}).status_code == 204

    class Push:
        calls = []
        failing = True

        def send(self, token, data):
            self.calls.append((token, data))
            if self.failing:
                raise ProviderUnavailable()

    push = Push()
    service.dispatch_push(push, clock[0])
    assert len(push.calls) == 1
    service.dispatch_push(push, clock[0])
    assert len(push.calls) == 1
    clock[0] += 5
    push.failing = False
    service.dispatch_push(push, clock[0])
    service.dispatch_push(push, clock[0])
    assert len(push.calls) == 2 and push.calls[0] == push.calls[1]
    assert list(push.calls[0][1]) == ["event"]
    assert a.request("GET", f"/v1/envelopes/{result['envelopeId']}").json()["downloadedAt"] is None


def test_link_consent_idempotency_revoke_public_headers_and_expiry(setup):
    service, client, clock = setup
    a, b = paired(setup)
    result, contents = delivered(a, b, link=True)
    assert b.request("GET", "/v1/inbox").json()["items"] == []
    path = f"/v1/transfers/{result['transferId']}/shares"
    key = secrets.token_hex(16)
    headers = {"Idempotency-Key": key}
    assert a.request("POST", path, {"consent": False}, headers=headers).status_code == 400
    shared = a.request("POST", path, {"consent": True, "ttlSeconds": 60}, headers=headers)
    assert shared.status_code == 201, shared.text
    shared = shared.json()
    assert a.request("POST", path, {"consent": True}, headers=headers).json() == shared
    token = shared["path"].split("#")[1]
    metadata = client.post("/v1/public-shares/resolve", json={"token": token})
    assert metadata.status_code == 200 and "sender" not in metadata.json()
    for position, original in enumerate(contents):
        actual = b"".join(client.post("/v1/public-shares/chunk", json={"token": token, "position": position, "offset": offset}).content
                           for offset in range(0, len(original), CHUNK))
        assert actual == original
    page = client.get("/r/")
    assert page.status_code == 200
    assert "noindex" in page.headers["x-robots-tag"] and page.headers["referrer-policy"] == "no-referrer"
    assert "default-src 'none'" in page.headers["content-security-policy"]
    assert client.get("/r/app.js").status_code == 200
    assert b.request("DELETE", f"/v1/shares/{shared['id']}").status_code == 404
    assert a.request("DELETE", f"/v1/shares/{shared['id']}").status_code == 204
    assert client.post("/v1/public-shares/resolve", json={"token": token}).status_code == 410
    assert a.request("POST", path, {"consent": True}, headers=headers).status_code == 410
    next_key = {"Idempotency-Key": secrets.token_hex(16)}
    second = a.request("POST", path, {"consent": True, "ttlSeconds": 60}, headers=next_key).json()
    clock[0] += 61
    service.store.cleanup(clock[0])
    assert client.post("/v1/public-shares/resolve", json={"token": second['path'].split('#')[1]}).status_code == 410
    assert a.request("POST", path, {"consent": True}, headers=next_key).status_code == 410
    with service.store.transaction(write=False) as db:
        assert token not in json.dumps([dict(row) for row in db.execute("SELECT * FROM shares")])


def test_revocation_binding_expiry_cursor_loss_and_restore_barrier(setup, tmp_path):
    service, client, clock = setup
    a, b = paired(setup)
    response, _ = delivered(a, b, [b"bytes"])
    envelope = response["envelopeId"]
    cursor = b.request("GET", "/v1/inbox").json()["cursor"]
    snapshot = tmp_path / "snapshot.sqlite3"
    with sqlite3.connect(service.store.path) as source, sqlite3.connect(snapshot) as target:
        source.backup(target)
    assert a.request("DELETE", f"/v1/transfers/{response['transferId']}").status_code == 204
    assert b.request("GET", f"/v1/envelopes/{envelope}/files/0/chunks?offset=0").status_code == 404
    assert [e["kind"] for e in b.request("GET", f"/v1/inbox?cursor={cursor}").json()["items"]] == ["revoked"]
    invalidate_snapshot(snapshot, clock[0])
    with TestClient(create_app(Service(Storage(snapshot), service.secret, FakeOtp(), clock=lambda: clock[0]))) as restored:
        old = b.client
        b.client = restored
        assert b.request("GET", "/v1/inbox").status_code == 401
        b.client = old
    # Compact old event cursors while keeping a reverified binding alive.
    clock[0] += 31 * DAY
    a.activate()
    b.activate()
    service.store.cleanup(clock[0])
    assert b.request("GET", f"/v1/inbox?cursor={cursor}").status_code == 409
    assert b.request("GET", "/v1/inbox").status_code == 200


def test_exact_ttl_logs_health_and_encrypted_routing_address(setup, caplog):
    from health import health
    import logging
    service, client, clock = setup
    a, b = paired(setup)
    caplog.set_level(logging.INFO)
    response, _ = delivered(a, b, [b"bytes"], link=True)
    shared = a.request("POST", f"/v1/transfers/{response['transferId']}/shares", {"consent": True, "ttlSeconds": 60},
                       headers={"Idempotency-Key": secrets.token_hex(16)}).json()
    token = shared['path'].split('#')[1]
    clock[0] += 59
    assert client.post("/v1/public-shares/resolve", json={"token": token}).status_code == 200
    clock[0] += 1
    assert client.post("/v1/public-shares/resolve", json={"token": token}).status_code == 410
    assert client.post("/v1/public-shares/resolve", json={"token": token + "wrong"}).status_code == 410
    assert token not in caplog.text and a.token not in caplog.text and a.number not in caplog.text
    with service.store.transaction(write=False) as db:
        installation = db.execute("SELECT * FROM installations WHERE id=?", (a.installation,)).fetchone()
        assert a.number.encode() not in installation['phone_cipher']
        assert service.number(installation) == a.number
    assert not health(service.store, clock[0])["alert"]
    clock[0] += 8 * DAY
    assert health(service.store, clock[0])["alert"]  # Worker missed the deletion deadline.
    service.store.cleanup(clock[0])
    assert not health(service.store, clock[0])["alert"]
    assert client.post("/v1/public-shares/chunk", json={"token": token, "position": 0, "offset": 0}).status_code == 410


def test_upgrade_legacy_finalized_drafts_preserves_chunks_and_bootstraps_outbox_once(tmp_path):
    from storage import SCHEMA
    from service import Problem
    import pytest
    path = tmp_path / "legacy.sqlite3"
    owner, target, transfer, envelope, key = [secrets.token_hex(16) for _ in range(5)]
    now = 1_800_000_000
    with sqlite3.connect(path) as db:
        db.executescript(SCHEMA.split("CREATE TABLE IF NOT EXISTS mailbox_events")[0])
        db.execute("INSERT INTO installations VALUES (?,?,?,?,?)", (owner, b"old-key-a", "hmac-a", 1, now + DAY))
        db.execute("INSERT INTO installations VALUES (?,?,?,?,?)", (target, b"old-key-b", "hmac-b", 1, now + DAY))
        db.execute("INSERT INTO transfers VALUES (?,?,?,?,?,?,?,?)", (transfer, owner, key, "fingerprint", target, "finalized", now + 7 * DAY, 4))
        db.execute("INSERT INTO files VALUES (?,?,?,?,?,?,?)", (transfer, 0, "image/png", "original", 4, sha(b"data"), 4))
        db.execute("INSERT INTO chunks VALUES (?,?,?,?,?)", (transfer, 0, 0, sha(b"data"), b"data"))
        db.execute("INSERT INTO draft_envelopes VALUES (?,?)", (envelope, transfer))
    store = Storage(path)
    Storage(path)
    with store.transaction(write=False) as db:
        assert db.execute("SELECT count(*) FROM mailbox_events").fetchone()[0] == 2
        assert db.execute("SELECT data FROM chunks").fetchone()[0] == b"data"
        assert db.execute("SELECT request_key FROM transfers").fetchone()[0] == key
    service = Service(store, b"synthetic legacy migration secret 12345", FakeOtp())
    with pytest.raises(Problem) as error:
        service.envelope(target, envelope, now)
    assert error.value.code == "sender_reverification_required"


def test_fcm_adapter_sends_data_only_and_classifies_auth_and_unregistered_errors():
    from unittest.mock import Mock
    import pytest
    from google.auth.exceptions import GoogleAuthError
    from providers import FirebasePush
    push = FirebasePush.__new__(FirebasePush)
    push.url = "https://fcm.googleapis.com/v1/projects/synthetic/messages:send"
    push.session = Mock()
    push.session.post.return_value = Mock(status_code=200)
    assert push.send("synthetic-token", {"event": "opaque-id"}) is True
    body = push.session.post.call_args.kwargs["json"]["message"]
    assert body["data"] == {"event": "opaque-id"} and "notification" not in body
    assert body["android"]["priority"] == "normal"
    push.session.post.return_value = Mock(status_code=404)
    push.session.post.return_value.json.return_value = {"error": {"details": [{"errorCode": "UNREGISTERED"}]}}
    assert push.send("synthetic-token", {"event": "opaque-id"}) is False
    push.session.post.side_effect = GoogleAuthError("synthetic authentication failure")
    with pytest.raises(ProviderUnavailable):
        push.send("synthetic-token", {"event": "opaque-id"})
