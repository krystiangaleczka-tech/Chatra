import re
import threading

import requests


class ProviderUnavailable(Exception):
    pass


class TwilioVerify:
    def __init__(self, service, key, secret):
        if not re.fullmatch(r"VA[0-9a-fA-F]{32}", service):
            raise ValueError("Invalid Verify service")
        self.url = f"https://verify.twilio.com/v2/Services/{service}"
        self.auth = (key, secret)

    def _post(self, path, data):
        try:
            response = requests.post(self.url + path, data=data, auth=self.auth, timeout=(4, 8))
            if response.status_code == 404 and path == "/VerificationCheck":
                return {}
            if response.status_code == 429 or response.status_code >= 500:
                raise ProviderUnavailable
            if response.status_code == 400:
                return {}
            response.raise_for_status()
            return response.json()
        except (requests.RequestException, ValueError):
            raise ProviderUnavailable from None

    def start(self, number):
        result = self._post("/Verifications", {"To": number, "Channel": "sms"})
        if result.get("status") != "pending" or not result.get("sid"):
            raise ProviderUnavailable
        return result["sid"]

    def verify(self, sid, code):
        result = self._post("/VerificationCheck", {"VerificationSid": sid, "Code": code})
        return result.get("to") if result.get("status") == "approved" else None


class GooglePlay:
    def __init__(self, package, product):
        from google.auth import default
        from google.auth.transport.requests import AuthorizedSession

        credentials, _ = default(scopes=["https://www.googleapis.com/auth/androidpublisher"])
        self.http = AuthorizedSession(credentials, max_refresh_attempts=1)
        self.lock = threading.Lock()
        self.package = package
        self.product = product
        self.url = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}"

    def _get(self, token):
        response = self.http.get(f"{self.url}/purchases/productsv2/tokens/{token}", timeout=(4, 8))
        if response.status_code in (400, 404, 410):
            return {}
        response.raise_for_status()
        return response.json()

    def _state(self, purchase):
        state = purchase.get("purchaseStateContext", {}).get("purchaseState")
        items = purchase.get("productLineItem", [])
        if len(items) != 1 or items[0].get("productId") != self.product:
            return "invalid"
        if state == "PENDING":
            return "pending"
        offer = items[0].get("productOfferDetails", {})
        if (
            state != "PURCHASED"
            or offer.get("consumptionState") != "CONSUMPTION_STATE_YET_TO_BE_CONSUMED"
            or offer.get("quantity") != 1
            or offer.get("refundableQuantity") != 1
            or "rentOfferDetails" in offer
        ):
            return "invalid"
        return "active"

    def _verified(self, token):
        purchase = self._get(token)
        state = self._state(purchase)
        if state != "active":
            return state
        if purchase.get("acknowledgementState") != "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED":
            response = self.http.post(
                f"{self.url}/purchases/products/{self.product}/tokens/{token}:acknowledge",
                json={}, timeout=(4, 8),
            )
            if response.status_code in (400, 404, 409, 410):
                current = self._get(token)
                if self._state(current) != "active":
                    return "invalid"
                if current.get("acknowledgementState") != "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED":
                    raise ProviderUnavailable
            else:
                response.raise_for_status()
        return "active"

    def verify(self, token):
        from google.auth.exceptions import GoogleAuthError

        try:
            with self.lock:
                return self._verified(token)
        except (requests.RequestException, ValueError, GoogleAuthError):
            raise ProviderUnavailable from None
