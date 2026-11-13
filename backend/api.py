import asyncio
import contextlib
import logging
import os
import sqlite3
from typing import Annotated, Literal
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from starlette.exceptions import HTTPException

from providers import GooglePlay, ProviderUnavailable, TwilioVerify
from service import CHUNK, Challenge, Discovery, Lookup, Manifest, Problem, Proof, Purchase, Service, Verification
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


def create_app(service):
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
        try:
            yield
        finally:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task

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
    return create_app(Service(Storage(os.environ["CHATRA_DB"]), bytes.fromhex(os.environ["CHATRA_SECRET_HEX"]), otp, play))
