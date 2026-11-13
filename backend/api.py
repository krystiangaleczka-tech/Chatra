import asyncio
import contextlib
import logging
import os
import sqlite3
from typing import Annotated, Literal
from uuid import uuid4
from pathlib import Path

from fastapi import Depends, FastAPI, Header, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, FileResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException

from providers import GooglePlay, ProviderUnavailable, TwilioVerify, FirebasePush
from service import CHUNK, DIGEST, Challenge, Discovery, Lookup, Manifest, Payload, Problem, Proof, Purchase, Service, Verification
from storage import Storage


class Error(BaseModel):
    code: str
    correlationId: str
    retryAfter: int | None = None


def error(status, code, retry=None):
    return JSONResponse(
        Error(code=code, correlationId=uuid4().hex, retryAfter=retry).model_dump(exclude_none=True),
        status_code=status,
        headers={"Cache-Control": "no-store", **({"Retry-After": str(retry)} if retry else {})},
    )


class BoundRequest:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        chunk_request = scope["method"] == "PUT" and scope["path"].endswith("/chunks")
        limit = CHUNK if chunk_request else 8192
        body = bytearray()
        while True:
            event = await receive()
            if event["type"] == "http.disconnect":
                return
            data = event.get("body", b"")
            if len(body) + len(data) > limit:
                await error(413, "request_too_large")(scope, receive, send)
                return
            body.extend(data)
            if not event.get("more_body", False):
                break
        raw = bytes(body)
        scope.setdefault("state", {})["raw_body"] = raw
        delivered = False

        async def replay():
            nonlocal delivered
            if delivered:
                return await receive()
            delivered = True
            return {"type": "http.request", "body": raw}

        async def no_cache(message):
            if message["type"] == "http.response.start" and not any(k == b"cache-control" for k, _ in message["headers"]):
                message["headers"].append((b"cache-control", b"no-store"))
            await send(message)

        await self.app(scope, replay, no_cache)


class ChallengeCreated(BaseModel):
    challengeId: str
    expiresAt: int


class Binding(BaseModel):
    installationId: str
    verifiedUntil: int


class Session(BaseModel):
    token: str
    expiresAt: int


class Capability(BaseModel):
    available: bool


class Offset(BaseModel):
    offset: int


class Ticket(Offset):
    path: str
    expiresAt: int
    chunkSize: int


class FileProgress(Offset):
    position: int
    size: int


class Transfer(BaseModel):
    transferId: str
    state: Literal["uploading", "invalid", "finalized"]
    expiresAt: int
    files: list[FileProgress]
    envelopeId: str | None


class TransferPage(BaseModel):
    items: list[Transfer]
    nextCursor: str | None


class Entitlement(BaseModel):
    state: Literal["active", "pending", "invalid"]


class DownloadReceipt(Payload):
    files: list[DIGEST] = Field(min_length=1, max_length=5)


class PushRegistration(Payload):
    token: str = Field(min_length=16, max_length=4096)


class ShareCreation(Payload):
    consent: bool
    ttlSeconds: int = Field(default=86400, ge=60, le=604800)


class ShareAccess(Payload):
    token: str = Field(min_length=1, max_length=128)


class ShareChunk(ShareAccess):
    position: int = Field(ge=0, le=4)
    offset: int = Field(ge=0)


class MailEvent(BaseModel):
    sequence: int
    envelope: str
    kind: Literal["envelope", "sent", "downloaded", "revoked"]


class InboxPage(BaseModel):
    items: list[MailEvent]
    cursor: str
    hasMore: bool


class PublicFile(BaseModel):
    position: int
    size: int
    mime: str
    sha256: DIGEST


class EnvelopeFile(PublicFile):
    variant: Literal["original", "no_location"]


class Envelope(BaseModel):
    id: str
    transferId: str
    sender: str
    recipient: str
    incoming: bool
    createdAt: int
    expiresAt: int
    files: list[EnvelopeFile]
    downloadedAt: int | None


class ReceiptResult(BaseModel):
    downloadedAt: int


class ShareResult(BaseModel):
    id: str
    path: str
    expiresAt: int


class PublicManifest(BaseModel):
    expiresAt: int
    files: list[PublicFile]


BINARY_RESPONSE = {200: {"content": {"application/octet-stream": {"schema": {"type": "string", "format": "binary"}}}}}


def create_app(service, push=None):
    @contextlib.asynccontextmanager
    async def lifespan(app):
        async def sweep():
            while True:
                try:
                    await asyncio.to_thread(service.store.cleanup, int(service.clock()))
                except sqlite3.Error:
                    logging.getLogger("chatra").error("Database cleanup failed")
                await asyncio.sleep(300)

        task = asyncio.create_task(sweep())
        async def push_loop():
            while True:
                try:
                    await asyncio.to_thread(service.dispatch_push, push, int(service.clock()))
                except sqlite3.Error:
                    logging.getLogger("chatra").error("Push outbox operation failed")
                await asyncio.sleep(5)

        push_task = asyncio.create_task(push_loop())
        try:
            yield
        finally:
            task.cancel()
            push_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
            with contextlib.suppress(asyncio.CancelledError):
                await push_task

    app = FastAPI(
        title="Chatra HD and Pro", version="1.0.0", lifespan=lifespan,
        docs_url=None, redoc_url=None, openapi_url=None,
        responses={code: {"model": Error} for code in (400, 401, 403, 404, 409, 410, 413, 422, 429, 503)},
    )
    app.add_middleware(BoundRequest)

    @app.exception_handler(Problem)
    async def problem(request, exc):
        return error(exc.status, exc.code, exc.retry)

    @app.exception_handler(HTTPException)
    async def http_error(request, exc):
        return error(exc.status_code, "not_found" if exc.status_code == 404 else "http_error")

    @app.exception_handler(RequestValidationError)
    async def invalid(request, exc):
        oversize = any(issue["type"] in ("less_than_equal", "too_long") for issue in exc.errors())
        return error(413 if oversize else 400, "request_too_large" if oversize else "invalid_request")

    @app.exception_handler(ProviderUnavailable)
    async def provider_error(request, exc):
        return error(503, "provider_unavailable", 30)

    @app.exception_handler(sqlite3.OperationalError)
    async def database_error(request, exc):
        logging.getLogger("chatra").error("Database operation failed")
        return error(503, "storage_unavailable", 10)

    def proof(
        request: Request,
        timestamp: Annotated[str, Header(alias="X-Chatra-Time")] = "",
        nonce: Annotated[str, Header(alias="X-Chatra-Nonce")] = "",
        signature: Annotated[str, Header(alias="X-Chatra-Signature")] = "",
    ):
        return Proof(request, request.state.raw_body, int(service.clock()))

    def authenticate(p: Annotated[Proof, Depends(proof)], authorization: Annotated[str, Header()] = ""):
        return service.auth(p)

    Signed = Annotated[Proof, Depends(proof)]
    Owner = Annotated[str, Depends(authenticate)]

    @app.post("/v1/number-challenges", status_code=201)
    def start(payload: Challenge, p: Signed) -> ChallengeCreated:
        return service.challenge(p, payload)

    @app.post("/v1/number-challenges/{challenge}/verification")
    def verify(challenge: str, payload: Verification, p: Signed) -> Binding:
        return service.verify_number(p, challenge, payload)

    @app.post("/v1/installations/{installation}/sessions")
    def session(installation: str, p: Signed) -> Session:
        return service.session(p, installation)

    @app.delete("/v1/binding", status_code=204)
    def detach(owner: Owner, p: Signed):
        service.detach(owner, p.now)
        return Response(status_code=204)

    @app.put("/v1/discovery", status_code=204)
    def discovery(payload: Discovery, owner: Owner, p: Signed):
        service.set_discovery(owner, p.now, payload.enabled)
        return Response(status_code=204)

    @app.post("/v1/capabilities/lookup")
    def lookup(payload: Lookup, owner: Owner, p: Signed) -> Capability:
        return {"available": service.lookup(owner, p.now, payload.number)}

    @app.post("/v1/transfers", status_code=201)
    def create(payload: Manifest, owner: Owner, p: Signed, key: Annotated[str, Header(alias="Idempotency-Key")]) -> Transfer:
        return service.create_transfer(owner, p.now, key, payload)

    @app.get("/v1/transfers")
    def transfers(owner: Owner, p: Signed, cursor: str | None = None, limit: Annotated[int, Query(ge=1, le=20)] = 10) -> TransferPage:
        return service.list_transfers(owner, p.now, cursor, limit)

    @app.delete("/v1/transfer-requests/{key}", status_code=204)
    def cancel_request(key: str, owner: Owner, p: Signed):
        service.cancel_request(owner, key, p.now)
        return Response(status_code=204)

    @app.get("/v1/transfers/{transfer}")
    def status(transfer: str, owner: Owner, p: Signed) -> Transfer:
        return service.read_transfer(owner, transfer, p.now)

    @app.post("/v1/transfers/{transfer}/files/{position}/ticket")
    def ticket(transfer: str, position: int, owner: Owner, p: Signed) -> Ticket:
        return service.ticket(owner, transfer, position, p.now)

    @app.put("/v1/transfers/{transfer}/files/{position}/chunks", openapi_extra={
        "requestBody": {"required": True, "content": {"application/octet-stream": {"schema": {"type": "string", "format": "binary"}}}},
    })
    def upload(
        transfer: str, position: int, owner: Owner, p: Signed, expires: int,
        ticket: Annotated[str, Query(min_length=64, max_length=64)],
        offset: Annotated[int, Header(alias="Upload-Offset", ge=0)],
        checksum: Annotated[str, Header(alias="X-Chunk-SHA256", pattern=r"^[0-9a-f]{64}$")],
    ) -> Offset:
        return service.upload(owner, transfer, position, p.now, expires, ticket, offset, checksum, p.body)

    @app.post("/v1/transfers/{transfer}/finalize")
    def finalize(transfer: str, owner: Owner, p: Signed) -> Transfer:
        return service.finalize(owner, transfer, p.now)

    @app.delete("/v1/transfers/{transfer}", status_code=204)
    def cancel(transfer: str, owner: Owner, p: Signed):
        service.cancel(owner, transfer, p.now)
        return Response(status_code=204)

    @app.post("/v1/billing/google-play/verify")
    def purchase(payload: Purchase, request: Request) -> Entitlement:
        return service.purchase(Proof(request, request.state.raw_body, int(service.clock())), payload)

    @app.get("/v1/inbox")
    def inbox(owner: Owner, p: Signed, cursor: str | None = None, limit: Annotated[int, Query(ge=1, le=50)] = 20) -> InboxPage:
        return service.inbox(owner, p.now, cursor, limit)

    @app.get("/v1/envelopes/{envelope}")
    def envelope(envelope: str, owner: Owner, p: Signed) -> Envelope:
        return service.envelope(owner, envelope, p.now)

    @app.get("/v1/envelopes/{envelope}/files/{position}/chunks", response_class=Response, responses=BINARY_RESPONSE)
    def download(envelope: str, position: int, owner: Owner, p: Signed, offset: int = 0):
        return binary(service.download_chunk(owner, envelope, position, offset, p.now))

    @app.post("/v1/envelopes/{envelope}/receipt")
    def receipt(envelope: str, payload: DownloadReceipt, owner: Owner, p: Signed) -> ReceiptResult:
        return service.receipt(owner, envelope, p.now, payload.files)

    @app.put("/v1/push-registration", status_code=204)
    def register_push(payload: PushRegistration, owner: Owner, p: Signed):
        service.register_push(owner, p.now, payload.token)
        return Response(status_code=204)

    @app.post("/v1/transfers/{transfer}/shares", status_code=201)
    def share(transfer: str, payload: ShareCreation, owner: Owner, p: Signed, key: Annotated[str, Header(alias="Idempotency-Key")]) -> ShareResult:
        return service.create_share(owner, transfer, p.now, key, payload.consent, payload.ttlSeconds)

    @app.delete("/v1/shares/{share}", status_code=204)
    def revoke_share(share: str, owner: Owner, p: Signed):
        service.revoke_share(owner, share, p.now)
        return Response(status_code=204)

    @app.post("/v1/public-shares/resolve")
    def resolve(payload: ShareAccess, p: Signed) -> PublicManifest:
        return service.resolve_share(p, payload.token)

    @app.post("/v1/public-shares/chunk", response_class=Response, responses=BINARY_RESPONSE)
    def public_chunk(payload: ShareChunk, p: Signed):
        return binary(service.public_chunk(p, payload.token, payload.position, payload.offset))

    def binary(data):
        return Response(data, media_type="application/octet-stream", headers={
            "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
            "Content-Disposition": "attachment", "Referrer-Policy": "no-referrer",
        })

    @app.get("/r/", include_in_schema=False)
    def share_page():
        return public_file("index.html", "text/html")

    @app.get("/r/app.js", include_in_schema=False)
    def share_script():
        return public_file("app.js", "application/javascript")

    @app.get("/r/style.css", include_in_schema=False)
    def share_style():
        return public_file("style.css", "text/css")

    def public_file(name, content_type):
        return FileResponse(Path(__file__).parent / "public" / name, media_type=content_type, headers={
            "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex, nofollow, noarchive",
            "X-Content-Type-Options": "nosniff",
            "Content-Security-Policy": "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
        })

    return app


def from_environment():
    product = os.getenv("CHATRA_PLAY_PRODUCT")
    play = GooglePlay("app.chatra", product) if product else None
    twilio_service = os.getenv("CHATRA_TWILIO_SERVICE")
    otp = TwilioVerify(twilio_service, os.environ["CHATRA_TWILIO_KEY"], os.environ["CHATRA_TWILIO_SECRET"]) if twilio_service else None
    if otp is None:
        class DisabledVerification:
            def start(self, number):
                raise ProviderUnavailable

            def verify(self, sid, code):
                raise ProviderUnavailable

        otp = DisabledVerification()
    project = os.getenv("CHATRA_FIREBASE_PROJECT")
    secret_file = os.getenv("CHATRA_SECRET_FILE")
    if secret_file and os.getenv("CHATRA_SECRET_HEX"):
        raise ValueError("Configure exactly one service secret source")
    secret = Path(secret_file).read_text().strip() if secret_file else os.environ["CHATRA_SECRET_HEX"]
    return create_app(Service(Storage(os.environ["CHATRA_DB"]), bytes.fromhex(secret), otp, play), FirebasePush(project) if project else None)
