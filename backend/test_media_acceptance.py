"""Four real lossless images: the 38.2 MB reference scenario, without providers."""
import random
import struct
import zlib

from test_api import paired, setup
from test_delivery import delivered
from service import CHUNK, sha


def png(seed):
    width, height = 2048, 1553
    pixels = random.Random(seed).randbytes(width * height * 3)
    rows = b"".join(b"\x00" + pixels[start:start + width * 3] for start in range(0, len(pixels), width * 3))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows, 1)) + chunk(b"IEND", b""))


def test_four_original_images_38_2mb_survive_upload_restart_download_and_receipt(setup):
    service, _, clock = setup
    sender, recipient = paired(setup)
    originals = [png(seed) for seed in range(4)]
    assert f"{sum(map(len, originals)) / 1_000_000:.1f}" == "38.2"
    result, _ = delivered(sender, recipient, originals)
    envelope = result["envelopeId"]
    # Discard live service objects and reopen the actual SQLite bytes.
    from api import create_app
    from service import Service
    from storage import Storage
    from fastapi.testclient import TestClient
    with TestClient(create_app(Service(Storage(service.store.path), service.secret, service.otp, clock=lambda: clock[0]))) as restarted:
        recipient.client = restarted
        for position, original in enumerate(originals):
            restored = b"".join(recipient.request("GET", f"/v1/envelopes/{envelope}/files/{position}/chunks?offset={offset}").content
                                for offset in range(0, len(original), CHUNK))
            assert len(restored) == len(original) and sha(restored) == sha(original)
        assert recipient.request("POST", f"/v1/envelopes/{envelope}/receipt", {"files": [sha(data) for data in originals]}).status_code == 200
        assert sender.request("GET", f"/v1/envelopes/{envelope}").json()["downloadedAt"] is not None
