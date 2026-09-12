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
DISCORD_WEBHOOK_URL = os.getenv("ERROR_LOG_DISCORD_WEBHOOK_URL", "")
ENVIRONMENT = os.getenv("ERROR_LOG_ENV", "dev")
GRAFANA_BASE_URL = os.getenv("ERROR_LOG_GRAFANA_BASE_URL", "")
LOG_LEVEL_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}.*\s(?P<level>TRACE|DEBUG|INFO|WARN|ERROR)\s+")
MAX_BYTES = int(os.getenv("ERROR_LOG_MAX_BYTES", "3500"))
FLUSH_SECONDS = float(os.getenv("ERROR_LOG_FLUSH_SECONDS", "2"))
POLL_SECONDS = float(os.getenv("ERROR_LOG_POLL_SECONDS", "1"))


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


def discord_payload(lines: list[str], context: dict[str, str | None]) -> dict:
    # Byte limits also bound Discord's character limits for multibyte log text.
    body = "".join(lines).strip().replace("```", "`\u200b``")
    encoded = body.encode()
    byte_limit = min(MAX_BYTES, 3800)
    if len(encoded) > byte_limit:
        body = encoded[:byte_limit].decode(errors="ignore").rstrip() + "\n... truncated"
    fields = [
        {"name": label, "value": (context.get(key) or "-").encode()[:256].decode(errors="ignore"), "inline": True}
        for label, key in (
            ("Service Name", "service_name"),
            ("Request ID", "request_id"),
            ("User ID", "user_id"),
            ("URL Path", "url_path"),
        )
    ]
    embed = {
        "title": f"[{ENVIRONMENT}] {CONTAINER_NAME} ERROR log".encode()[:256].decode(errors="ignore"),
        "description": f"```\n{body}\n```",
        "color": 15158332,
        "fields": fields,
    }
    link = grafana_url(context.get("request_id"))
    if link and len(link) <= 2048:
        embed["url"] = link
    return {"embeds": [embed], "allowed_mentions": {"parse": []}}


def post_payload(url: str, payload: dict) -> None:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "NookErrorLogForwarder/1.0"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
    except (urllib.error.URLError, OSError) as error:
        # Exception messages may contain webhook credentials; log only the type/status.
        status = error.code if isinstance(error, urllib.error.HTTPError) else type(error).__name__
        print(f"failed to send discord error log: {status}", flush=True)


def post_error_log(lines: list[str], context: dict[str, str | None]) -> None:
    if DISCORD_WEBHOOK_URL and "".join(lines).strip():
        # wait=true makes Discord report message creation failures synchronously.
        url = urllib.parse.urlsplit(DISCORD_WEBHOOK_URL)
        query = dict(urllib.parse.parse_qsl(url.query))
        query["wait"] = "true"
        target = urllib.parse.urlunsplit(url._replace(query=urllib.parse.urlencode(query)))
        post_payload(target, discord_payload(lines, context))


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
                post_error_log(buffer, error_context)
                buffer = []
            continue
        level, formatted_line, context = parse_log_entry(line)
        if level:
            if buffer:
                post_error_log(buffer, error_context)
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
            post_error_log(buffer, error_context)
            buffer = []


if __name__ == "__main__":
    main()
