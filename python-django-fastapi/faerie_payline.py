"""
Faerie Payline SDK for Python 3.9+ (Django, FastAPI, Flask) — Zero external dependencies!
"""
from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional


class FaeriePaylineError(Exception):
    def __init__(self, message: str, status_code: int = 0, code: str = "", requires_national_id: bool = False):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.requires_national_id = requires_national_id


@dataclass
class FaeriePaylineClient:
    payline_token: str
    base_url: str = "https://faerie.ir"
    timeout: int = 20

    def get_products(self) -> List[Dict[str, Any]]:
        res = self._request("GET", "/api/v1/iap/products")
        return res.get("data", {}).get("products", [])

    def init_purchase(
        self,
        sku: str,
        user_id: str,
        *,
        amount_toman: Optional[int] = None,
        mobile: Optional[str] = None,
        national_code: Optional[str] = None,
        discount_code: Optional[str] = None,
        return_url: Optional[str] = None,
    ) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"sku": sku, "user_id": user_id}
        if amount_toman:
            payload["amount"] = int(amount_toman)
        if mobile:
            payload["mobile"] = mobile
        if national_code:
            payload["national_code"] = national_code
        if discount_code:
            payload["discount_code"] = discount_code
        if return_url:
            payload["return_url"] = return_url

        res = self._request("POST", "/api/v1/iap/init", payload)
        data = res.get("data", {})
        purchase = dict(data.get("purchase") or data)
        if not purchase.get("payment_url") and data.get("payment_url"):
            purchase["payment_url"] = data["payment_url"]
        return purchase

    def get_status(self, purchase_id: str) -> Dict[str, Any]:
        safe_id = urllib.parse.quote(purchase_id, safe="")
        return self._request("GET", f"/api/v1/iap/status/{safe_id}").get("data", {})

    def consume(self, purchase_id: str, user_id: str) -> Dict[str, Any]:
        return self._request("POST", "/api/v1/iap/consume", {
            "purchase_id": purchase_id,
            "user_id": user_id,
        }).get("data", {})

    def verify_and_consume(self, purchase_id: str, user_id: str, auto_consume: bool = True) -> Dict[str, Any]:
        tx = self.get_status(purchase_id)
        st = tx.get("status", "")
        if st not in ("paid", "consumed"):
            raise FaeriePaylineError(f"پرداخت تکمیل نشده است ({st})", status_code=402)
        if auto_consume and st == "paid":
            tx = self.consume(purchase_id, user_id)
        return tx

    def get_invoice(self, purchase_id: str) -> Dict[str, Any]:
        safe_id = urllib.parse.quote(purchase_id, safe="")
        return self._request("GET", f"/api/v1/iap/invoice/{safe_id}").get("data", {})

    def _request(self, method: str, path: str, body: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        url = self.base_url.rstrip("/") + path
        headers = {
            "Accept": "application/json",
            "X-Payline": self.payline_token.strip(),
        }
        raw_body = None
        if body is not None and method != "GET":
            headers["Content-Type"] = "application/json; charset=utf-8"
            raw_body = json.dumps(body, ensure_ascii=False).encode("utf-8")

        req = urllib.request.Request(url, data=raw_body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                status = resp.status
                text = resp.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            status = e.code
            text = e.read().decode("utf-8", errors="replace")

        parsed = json.loads(text or "{}")
        if status < 200 or status >= 300 or not parsed.get("success"):
            raise FaeriePaylineError(
                parsed.get("message", f"خطای درگاه فائری ({status})"),
                status_code=status,
                code=parsed.get("code", ""),
                requires_national_id=bool(parsed.get("requires_national_id", False)),
            )
        return parsed
