from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any, Mapping
from urllib import error, parse, request


@dataclass(frozen=True)
class HttpResponse:
    status: int
    headers: Mapping[str, str]
    body: bytes

    def json(self) -> Any:
        try:
            return json.loads(self.body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise ValueError("HTTP response body is not valid UTF-8 JSON") from exception

    @property
    def content_type(self) -> str | None:
        raw = self.headers.get("content-type")
        if raw is None:
            return None
        return raw.split(";", 1)[0].strip().lower()


class HttpClient:
    def request(
        self,
        method: str,
        url: str,
        *,
        headers: Mapping[str, str] | None = None,
        json_body: Any | None = None,
        form_body: Mapping[str, str] | None = None,
        timeout_seconds: float = 10.0,
    ) -> HttpResponse:
        if json_body is not None and form_body is not None:
            raise ValueError("json_body and form_body are mutually exclusive")

        request_headers = dict(headers or {})
        data: bytes | None = None
        if json_body is not None:
            request_headers.setdefault("Content-Type", "application/json")
            data = json.dumps(json_body, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        elif form_body is not None:
            request_headers.setdefault("Content-Type", "application/x-www-form-urlencoded")
            data = parse.urlencode(form_body).encode("ascii")

        http_request = request.Request(url, data=data, headers=request_headers, method=method)
        try:
            with request.urlopen(http_request, timeout=timeout_seconds) as response:
                return HttpResponse(
                    status=response.status,
                    headers={key.lower(): value for key, value in response.headers.items()},
                    body=response.read(),
                )
        except error.HTTPError as exception:
            return HttpResponse(
                status=exception.code,
                headers={key.lower(): value for key, value in exception.headers.items()},
                body=exception.read(),
            )
