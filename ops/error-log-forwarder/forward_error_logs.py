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
PARSING_DISCORD_WEBHOOK_URL = os.getenv("PARSING_ALERT_DISCORD_WEBHOOK_URL", "")
ENVIRONMENT = os.getenv("ERROR_LOG_ENV", "dev")
GRAFANA_BASE_URL = os.getenv("ERROR_LOG_GRAFANA_BASE_URL", "")
LOG_LEVEL_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}.*\s(?P<level>TRACE|DEBUG|INFO|WARN|ERROR)\s+")
MAX_BYTES = int(os.getenv("ERROR_LOG_MAX_BYTES", "3500"))
FLUSH_SECONDS = float(os.getenv("ERROR_LOG_FLUSH_SECONDS", "2"))
PARSING_ONLY = os.getenv("ERROR_LOG_PARSING_ONLY", "false").lower() == "true"
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


STAGE_NAMES = {
    "POST_CONTENT": "본문 파싱", "PLACE_PARSING": "장소 파싱", "POST_MEDIA": "미디어 저장",
    "PLACE_THUMBNAILS": "썸네일 저장", "PLACE_TAGS": "장소 태그 저장",
    "CONTENT_FETCH": "원문 가져오기", "CONTENT_COVER_TITLE": "커버·제목 추출",
    "CONTENT_INFERENCE": "본문 분석", "CONTENT_SAVE": "본문 저장",
    "PLACE_TEXT_CLUES": "장소 단서 추출", "PLACE_TEXT_RESOLUTION": "장소 검색",
    "PLACE_IMAGE_OCR": "이미지 OCR", "PLACE_IMAGE_CLUES": "이미지 장소 추출",
    "PLACE_IMAGE_RESOLUTION": "이미지 장소 검색", "TITLE_FINALIZATION": "제목 생성",
    "PLACE_SAVE": "장소 저장", "POST_SAVE": "게시물 저장 요청",
}


SUMMARY_EVENTS = ("post.parsing.summary_failed", "post.parsing.summary_warning")

def parsing_payload(context: dict) -> dict:
    post_id = context.get("post_id") or "미생성"
    stage = context.get("failure_stage") or "UNKNOWN"
    stage_name = STAGE_NAMES.get(stage, stage)
    fields = [{"name": name, "value": str(value or "-")[:200], "inline": True} for name, value in (
        ("게시물", post_id), ("실패 단계", stage_name), ("작업", context.get("job_id")),
        ("실행 횟수", context.get("attempt")), ("실패 시각", context.get("failed_at")),
    )]
    embed = {
        "title": f"[{ENVIRONMENT}] 게시물 #{post_id} · {stage_name} 실패"[:256],
        "description": "자동 재시도가 종료되어 확인이 필요합니다. 관리자 화면에서 실패 원인을 확인하고 재시도할 수 있습니다.",
        "color": 15158332, "fields": fields,
    }
    if context.get("event_type") in SUMMARY_EVENTS:
        embed["title"] = f"[{ENVIRONMENT}] 게시물 #{post_id} · 처리 완료 — 일부 실패"[:256]
        failures = json.loads(context.get("failure_summary") or "[]")
        lines = []
        for item in failures:
            lines.append(f"• {STAGE_NAMES.get(item['stage'], item['stage'])}: {item['reason']} ({item['count']}건)")
            if item.get("code"):
                lines.append(f"  오류 코드: {item['code']}")
            if item.get("detail") and item["detail"] != item["reason"]:
                lines.append(f"  상세: {item['detail'][:400]}")
            if item.get("attempts"):
                origin = "관리자 재시도 후 실패" if item.get("retried") else "자동 재시도 종료"
                lines.append(f"  {origin} · 최대 {item['attempts']}회 실행")
        embed["description"] = ("모든 작업이 종료되었습니다.\n" + "\n".join(lines))[:4000]
        embed["fields"] = [{"name": name, "value": str(context.get(key) or "0"), "inline": True}
                           for name, key in (("전체 작업", "total_count"), ("성공", "completed_count"),
                                             ("실패", "failed_count"))]
        if context.get("failed_at"):
            embed["fields"].append({"name": "마지막 실패 시각", "value": context["failed_at"]})
        if context.get("event_type") == "post.parsing.summary_warning":
            embed["title"] = f"[{ENVIRONMENT}] 게시물 #{post_id} · 장소 없이 저장됨"[:256]
            embed["color"] = 16753920
            embed["description"] = "게시물이 정상 저장되고 모든 처리 작업이 완료됐습니다."
        if context.get("warning_code") == "NO_PLACES":
            embed["fields"].append({"name": "확인 필요 · 장소 없이 저장됨",
                                    "value": "연결된 장소가 0개입니다. 원문을 확인해 필요한 장소를 연결해 주세요."})
    if context.get("event_type") == "post.save.failed":
        embed["description"] = "게시물 저장 요청이 실패했습니다. Request ID로 서버 오류를 확인해 주세요."
        embed["fields"].append({"name": "Request ID", "value": str(context.get("request_id") or "-")[:200]})
    if str(post_id).isdigit() and ENVIRONMENT in ("dev", "live"):
        host = "dev-admin.everynook.co.kr" if ENVIRONMENT == "dev" else "admin.everynook.co.kr"
        embed["url"] = f"https://{host}/#/posts/{post_id}/processing"
    return {"embeds": [embed], "allowed_mentions": {"parse": []}}


def should_forward(level: str | None, context: dict) -> bool:
    if level == "WARN" and context.get("event_type") == "post.parsing.summary_warning":
        return True
    return level == "ERROR" and (not PARSING_ONLY or context.get("event_type") == "post.parsing.summary_failed")


def post_payload(url: str, payload: dict) -> None:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "NookErrorLogForwarder/1.0"},
        method="POST",
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                response.read()
            return
        except (urllib.error.URLError, OSError) as error:
            status = error.code if isinstance(error, urllib.error.HTTPError) else type(error).__name__
            retryable = not isinstance(error, urllib.error.HTTPError) or status == 429 or status >= 500
            if retryable and attempt < 2:
                delay = 0.5 * (2 ** attempt)
                if isinstance(error, urllib.error.HTTPError) and status == 429:
                    try:
                        delay = max(delay, float((error.headers or {}).get("Retry-After", delay)))
                    except (ValueError, TypeError):
                        pass
                time.sleep(min(delay, 30))
                continue
            # Never log URLs/tokens from provider exceptions.
            print(f"failed to send discord error log: {status}", flush=True)
            return


def post_error_log(lines: list[str], context: dict[str, str | None]) -> None:
    parsing = context.get("event_type") in (*SUMMARY_EVENTS, "post.save.failed")
    webhook = (PARSING_DISCORD_WEBHOOK_URL or DISCORD_WEBHOOK_URL) if parsing else DISCORD_WEBHOOK_URL
    if webhook and "".join(lines).strip():
        # wait=true makes Discord report message creation failures synchronously.
        url = urllib.parse.urlsplit(webhook)
        query = dict(urllib.parse.parse_qsl(url.query))
        query["wait"] = "true"
        target = urllib.parse.urlunsplit(url._replace(query=urllib.parse.urlencode(query)))
        post_payload(target, parsing_payload(context) if context.get("event_type") in (*SUMMARY_EVENTS, "post.save.failed") else discord_payload(lines, context))


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

    if entry.get("event_type") in SUMMARY_EVENTS:
        context.update({key: context_value(entry, key) for key in
                        ("event_type", "post_id", "failure_summary", "completed_count", "failed_count", "total_count", "failed_at", "warning_code")})

    if level == "ERROR" and method == "POST" and (
        path == "/api/v1/posts" or (path and path.startswith("/api/v1/shared-posts/") and path.endswith("/save"))
    ):
        context.update({"event_type": "post.save.failed", "failure_stage": "POST_SAVE",
                        "failed_at": context_value(entry, "@timestamp")})

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
            if should_forward(level, context):
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
