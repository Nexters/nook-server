#!/usr/bin/env python3
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable, Iterator, TextIO


CONTAINER_NAME = os.getenv("ERROR_LOG_CONTAINER_NAME", "nook-dev-api")
WEBHOOK_URL = os.getenv("ERROR_LOG_SLACK_WEBHOOK_URL", "")
ENVIRONMENT = os.getenv("ERROR_LOG_ENV", "live")
GRAFANA_BASE_URL = os.getenv("ERROR_LOG_GRAFANA_BASE_URL", "")
LOG_LEVEL_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}.*\s(?P<level>TRACE|DEBUG|INFO|WARN|ERROR)\s+")
MAX_BYTES = int(os.getenv("ERROR_LOG_MAX_BYTES", "3500"))
FLUSH_SECONDS = float(os.getenv("ERROR_LOG_FLUSH_SECONDS", "2"))
POLL_SECONDS = float(os.getenv("ERROR_LOG_POLL_SECONDS", "1"))
SLACK_SECTION_MAX_CHARS = 2900


def docker_container_log_path() -> Path:
    containers = Path("/var/lib/docker/containers")
    for config_path in containers.glob("*/config.v2.json"):
        try:
            config = json.loads(config_path.read_text())
        except (OSError, json.JSONDecodeError):
            continue
        if config.get("Name") == f"/{CONTAINER_NAME}":
            container_id = config_path.parent.name
            return config_path.parent / f"{container_id}-json.log"
    raise RuntimeError(f"container not found: {CONTAINER_NAME}")


def context_value(entry: dict, *keys: str) -> str | None:
    for key in keys:
        value = entry.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return None


def grafana_url(request_id: str | None) -> str | None:
    if not GRAFANA_BASE_URL or not request_id:
        return None
    dashboard_uid = f"nook-{ENVIRONMENT}-logs"
    query = urllib.parse.urlencode(
        {
            "from": "now-15m",
            "to": "now",
            "var-level": "ERROR",
            "var-requestIdText": request_id,
        },
    )
    return f"{GRAFANA_BASE_URL.rstrip('/')}/d/{dashboard_uid}/{dashboard_uid}?{query}"


def slack_text(value: str | None) -> str:
    return (value or "-").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def truncate_body(body: str) -> str:
    encoded = body.encode()
    byte_limit = min(MAX_BYTES, SLACK_SECTION_MAX_CHARS)
    if len(encoded) <= byte_limit:
        return body
    return encoded[:byte_limit].decode(errors="ignore").rstrip() + "\n... truncated"


def slack_payload(lines: list[str], context: dict[str, str | None]) -> dict:
    body = truncate_body("".join(lines).strip())
    service_name = slack_text(context.get("service_name"))
    request_id = slack_text(context.get("request_id"))
    user_id = slack_text(context.get("user_id"))
    url_path = slack_text(context.get("url_path"))
    fields = [
        {"type": "mrkdwn", "text": f"*Service Name*\n{service_name}"},
        {"type": "mrkdwn", "text": f"*Request ID*\n{request_id}"},
        {"type": "mrkdwn", "text": f"*User ID*\n{user_id}"},
        {"type": "mrkdwn", "text": f"*URL Path*\n{url_path}"},
    ]
    blocks = [
        {"type": "section", "text": {"type": "mrkdwn", "text": f":rotating_light: *{slack_text(CONTAINER_NAME)} ERROR log*"}},
        {"type": "section", "fields": fields},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"```{body}```"}},
    ]
    link = grafana_url(context.get("request_id"))
    if link:
        blocks.append(
            {
                "type": "actions",
                "elements": [
                    {
                        "type": "button",
                        "text": {"type": "plain_text", "text": "Grafana에서 전체 요청 로그 보기"},
                        "url": link,
                    },
                ],
            },
        )
    return {
        "text": f"{CONTAINER_NAME} ERROR log - request ID: {context.get('request_id') or '-'}",
        "blocks": blocks,
    }


def post_to_slack(lines: list[str], context: dict[str, str | None]) -> None:
    if not WEBHOOK_URL:
        return
    if not "".join(lines).strip():
        return
    payload = slack_payload(lines, context)
    data = json.dumps(payload).encode()
    request = urllib.request.Request(
        WEBHOOK_URL,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
    except urllib.error.URLError as error:
        print(f"failed to send slack error log: {error}", flush=True)


def read_json_log_line(raw_line: str) -> str:
    try:
        return json.loads(raw_line).get("log", "")
    except json.JSONDecodeError:
        return raw_line


def parse_log_entry(line: str) -> tuple[str | None, str, dict[str, str | None]]:
    try:
        entry = json.loads(line)
    except json.JSONDecodeError:
        match = LOG_LEVEL_PATTERN.search(line)
        return (match.group("level") if match else None, line, {})

    if not isinstance(entry, dict):
        return None, line, {}

    level = entry.get("level")
    if not isinstance(level, str):
        return None, line, {}

    method = context_value(entry, "request_method", "http_method", "request.method", "http.method")
    path = context_value(entry, "http_route", "request_path", "http.route", "request.path")
    context = {
        "service_name": context_value(entry, "service_name", "service.name") or CONTAINER_NAME,
        "request_id": context_value(entry, "request_id", "request.id"),
        "user_id": context_value(entry, "user_id", "user.id"),
        "url_path": " ".join(value for value in (method, path) if value) or None,
    }

    header_parts = [
        str(value)
        for value in (
            entry.get("@timestamp"),
            level,
            entry.get("logger_name"),
        )
        if value
    ]
    message = entry.get("message")
    body = " ".join(header_parts)
    if message:
        body = f"{body} - {message}" if body else str(message)

    stack_trace = entry.get("stack_trace")
    if stack_trace:
        body = f"{body}\n{stack_trace}" if body else str(stack_trace)

    return level, f"{body.rstrip()}\n" if body else line, context


def opened_file_matches_path(fp: TextIO, log_path: Path) -> bool:
    try:
        opened = os.fstat(fp.fileno())
        current = log_path.stat()
    except OSError:
        return False
    return (opened.st_dev, opened.st_ino) == (current.st_dev, current.st_ino)


def follow_current_container_log(
    resolve_log_path: Callable[[], Path] = docker_container_log_path,
    sleep: Callable[[float], None] = time.sleep,
) -> Iterator[str | None]:
    fp: TextIO | None = None
    log_path: Path | None = None
    first_connection = True
    try:
        while True:
            try:
                resolved_path = resolve_log_path()
                needs_reconnect = (
                    fp is None
                    or log_path != resolved_path
                    or not opened_file_matches_path(fp, resolved_path)
                )
                if needs_reconnect:
                    if fp is not None:
                        fp.close()
                    fp = resolved_path.open()
                    log_path = resolved_path
                    if first_connection:
                        fp.seek(0, os.SEEK_END)
                    first_connection = False
                    print(f"forwarding ERROR logs from {resolved_path}", flush=True)

                line = fp.readline()
            except (OSError, RuntimeError) as error:
                if fp is not None:
                    fp.close()
                    fp = None
                log_path = None
                print(f"waiting for {CONTAINER_NAME} log: {error}", flush=True)
                sleep(POLL_SECONDS)
                yield None
                continue

            if line:
                yield read_json_log_line(line)
                continue
            sleep(POLL_SECONDS)
            yield None
    finally:
        if fp is not None:
            fp.close()


def main() -> None:
    buffer: list[str] = []
    error_context: dict[str, str | None] = {}
    last_append_at = 0.0
    for line in follow_current_container_log():
        if line is None:
            if buffer and time.monotonic() - last_append_at >= FLUSH_SECONDS:
                post_to_slack(buffer, error_context)
                buffer = []
            continue
        level, formatted_line, context = parse_log_entry(line)
        if level:
            if buffer:
                post_to_slack(buffer, error_context)
                buffer = []
            if level == "ERROR":
                buffer = [formatted_line]
                error_context = context
                last_append_at = time.monotonic()
            continue
        if buffer:
            buffer.append(line)
            last_append_at = time.monotonic()
        if buffer and time.monotonic() - last_append_at >= FLUSH_SECONDS:
            post_to_slack(buffer, error_context)
            buffer = []


if __name__ == "__main__":
    main()
