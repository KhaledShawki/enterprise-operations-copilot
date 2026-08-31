from __future__ import annotations

import base64
import binascii
from dataclasses import dataclass
import json
from typing import Sequence, TypeVar


T = TypeVar("T")


class InvalidSourcePosition(ValueError):
    pass


@dataclass(frozen=True)
class PageSlice:
    records: tuple[T, ...]
    next_page_token: str
    candidate_cursor: str


def page_records(
    entity: str,
    records: Sequence[T],
    *,
    page_size: int,
    page_token: str,
    incremental_cursor: str,
) -> PageSlice:
    if page_size < 1 or page_size > 500:
        raise InvalidSourcePosition("page_size must be between 1 and 500")
    if page_token and incremental_cursor:
        raise InvalidSourcePosition("page_token and incremental_cursor are mutually exclusive")

    if page_token:
        after_sequence, offset = decode_page_token(page_token, entity)
    else:
        after_sequence = decode_cursor(incremental_cursor, entity)
        offset = 0

    available = tuple(record for record in records if record.sequence > after_sequence)
    if offset > len(available):
        raise InvalidSourcePosition("page token offset is out of range")

    end = min(offset + page_size, len(available))
    selected = available[offset:end]
    next_page_token = encode_page_token(entity, after_sequence, end) if end < len(available) else ""
    candidate_cursor = (
        encode_cursor(entity, selected[-1].sequence) if selected else incremental_cursor
    )
    return PageSlice(selected, next_page_token, candidate_cursor)


def encode_page_token(entity: str, after_sequence: int, offset: int) -> str:
    payload = json.dumps(
        {"v": 1, "e": entity, "a": after_sequence, "o": offset},
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")


def decode_page_token(value: str, entity: str) -> tuple[int, int]:
    try:
        padded = value + "=" * (-len(value) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded).decode("utf-8"))
        after_sequence = payload.get("a")
        offset = payload.get("o")
        if (
            payload.get("v") != 1
            or payload.get("e") != entity
            or isinstance(after_sequence, bool)
            or not isinstance(after_sequence, int)
            or isinstance(offset, bool)
            or not isinstance(offset, int)
            or after_sequence < 0
            or offset < 0
        ):
            raise ValueError
        return after_sequence, offset
    except (AttributeError, ValueError, TypeError, json.JSONDecodeError, UnicodeDecodeError, binascii.Error) as exception:
        raise InvalidSourcePosition("invalid page token") from exception


def encode_cursor(entity: str, sequence: int) -> str:
    if sequence < 0:
        raise ValueError("sequence cannot be negative")
    return f"v1:{entity}:{sequence}"


def decode_cursor(value: str, entity: str) -> int:
    if not value:
        return 0
    try:
        version, cursor_entity, sequence = value.split(":", 2)
        parsed = int(sequence)
        if version != "v1" or cursor_entity != entity or parsed < 0:
            raise ValueError
        return parsed
    except (ValueError, TypeError) as exception:
        raise InvalidSourcePosition("invalid incremental cursor") from exception
