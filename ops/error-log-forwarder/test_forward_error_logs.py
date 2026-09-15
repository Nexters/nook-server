import importlib.util
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import MagicMock, patch


MODULE_PATH = Path(__file__).with_name("forward_error_logs.py")
SPEC = importlib.util.spec_from_file_location("forward_error_logs", MODULE_PATH)
forward_error_logs = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(forward_error_logs)


class ParseLogEntryTest(unittest.TestCase):
    def test_parses_json_error_with_stack_trace(self) -> None:
        line = json.dumps(
            {
                "@timestamp": "2026-08-19T00:16:00+09:00",
                "level": "ERROR",
                "logger_name": "org.every.nook.GlobalExceptionHandler",
                "message": "Unexpected API exception",
                "stack_trace": "java.lang.IllegalStateException: failed\n\tat Example.run(Example.kt:1)\n",
            },
        )

        level, body, context = forward_error_logs.parse_log_entry(line)

        self.assertEqual("ERROR", level)
        self.assertIn("Unexpected API exception", body)
        self.assertIn("java.lang.IllegalStateException: failed", body)
        self.assertIn("at Example.run(Example.kt:1)", body)
        self.assertEqual("nook-dev-api", context["service_name"])

    def test_parses_request_context_from_structured_error(self) -> None:
        line = json.dumps(
            {
                "level": "ERROR",
                "service_name": "nook-api",
                "request_id": "abc123def456gh78",
                "user_id": 42,
                "request_method": "POST",
                "http_route": "/api/v1/posts/{postId}",
                "message": "failed",
            },
        )

        _, _, context = forward_error_logs.parse_log_entry(line)

        self.assertEqual(
            {
                "service_name": "nook-api",
                "request_id": "abc123def456gh78",
                "user_id": "42",
                "url_path": "POST /api/v1/posts/{postId}",
            },
            context,
        )

    def test_parses_json_info_without_treating_it_as_error(self) -> None:
        level, body, context = forward_error_logs.parse_log_entry(
            json.dumps({"level": "INFO", "message": "request completed"}),
        )

        self.assertEqual("INFO", level)
        self.assertIn("request completed", body)
        self.assertIsNone(context["request_id"])

    def test_preserves_plain_text_error_support(self) -> None:
        line = "2026-08-19 10:00:00 ERROR example.Logger - failed\n"

        level, body, context = forward_error_logs.parse_log_entry(line)

        self.assertEqual("ERROR", level)
        self.assertEqual(line, body)
        self.assertEqual({}, context)

    def test_returns_unknown_for_malformed_json(self) -> None:
        line = '{"level":"ERROR"'

        level, body, context = forward_error_logs.parse_log_entry(line)

        self.assertIsNone(level)
        self.assertEqual(line, body)
        self.assertEqual({}, context)


class DiscordPayloadTest(unittest.TestCase):
    def test_preserves_context_stack_trace_and_environment_link(self) -> None:
        context = {"service_name": "nook-api", "request_id": "req-347", "user_id": "42", "url_path": "GET /posts"}
        for environment in ("dev", "live"):
            with (
                self.subTest(environment=environment),
                patch.object(forward_error_logs, "ENVIRONMENT", environment),
                patch.object(forward_error_logs, "GRAFANA_BASE_URL", "https://grafana.example.com"),
            ):
                payload = forward_error_logs.discord_payload(["ERROR failed\n", "at Example.run()"], context)
                embed = payload["embeds"][0]
                self.assertIn(f"[{environment}]", embed["title"])
                self.assertIn("at Example.run()", embed["description"])
                self.assertEqual(list(context.values()), [field["value"] for field in embed["fields"]])
                self.assertIn(f"/d/nook-{environment}-logs/", embed["url"])
                self.assertIn("var-requestIdText=req-347", embed["url"])
                self.assertEqual({"parse": []}, payload["allowed_mentions"])

    def test_bounds_multibyte_payload_and_escapes_code_fences(self) -> None:
        context = {key: "😀한" * 3000 for key in ("service_name", "request_id", "user_id", "url_path")}
        with (
            patch.object(forward_error_logs, "MAX_BYTES", 999999),
            patch.object(forward_error_logs, "CONTAINER_NAME", "😀" * 500),
        ):
            embed = forward_error_logs.discord_payload(["``` @everyone " + "😀한" * 5000], context)["embeds"][0]
        count = lambda value: len(value.encode("utf-16-le")) // 2
        self.assertLessEqual(count(embed["title"]), 256)
        self.assertLessEqual(count(embed["description"]), 4096)
        self.assertEqual(2, embed["description"].count("```"))
        self.assertIn("... truncated", embed["description"])
        total = count(embed["title"]) + count(embed["description"])
        for field in embed["fields"]:
            self.assertLessEqual(count(field["value"]), 1024)
            total += count(field["name"]) + count(field["value"])
        self.assertLessEqual(total, 6000)

    def test_missing_context_uses_fallback_without_link(self) -> None:
        embed = forward_error_logs.discord_payload(["error"], {})["embeds"][0]
        self.assertNotIn("url", embed)
        self.assertTrue(all(field["value"] == "-" for field in embed["fields"]))


class DeliveryTest(unittest.TestCase):
    def test_sends_only_discord_with_confirmation(self) -> None:
        with (
            patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/webhook"),
            patch.object(forward_error_logs.urllib.request, "urlopen", return_value=MagicMock()) as send,
        ):
            forward_error_logs.post_error_log(["error"], {})
        self.assertEqual(1, send.call_count)
        request = send.call_args.args[0]
        self.assertEqual("https://discord.example/webhook?wait=true", request.full_url)
        self.assertEqual(forward_error_logs.discord_payload(["error"], {}), json.loads(request.data))

    def test_failures_do_not_stop_the_forwarder_or_leak_url(self) -> None:
        errors = [
            urllib.error.URLError("https://secret.example/token"),
            urllib.error.HTTPError("https://secret.example/token", 429, "rate limited", {}, None),
            TimeoutError("https://secret.example/token"),
        ]
        for error in errors:
            with (
                self.subTest(error=type(error).__name__),
                patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/webhook"),
                patch.object(forward_error_logs.urllib.request, "urlopen", side_effect=[error, error, error, MagicMock()]) as send,
                patch("builtins.print") as output,
                patch.object(forward_error_logs.time, "sleep"),
            ):
                forward_error_logs.post_error_log(["error"], {})
                forward_error_logs.post_error_log(["next error"], {})
                self.assertEqual(4, send.call_count)
                self.assertNotIn("secret.example", str(output.call_args_list))

    def test_does_not_send_without_webhook_or_log_body(self) -> None:
        for webhook, body in [("", "error"), ("https://discord.example/webhook", "  ")]:
            with (
                self.subTest(webhook=webhook, body=body),
                patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", webhook),
                patch.object(forward_error_logs.urllib.request, "urlopen") as send,
            ):
                forward_error_logs.post_error_log([body], {})
                send.assert_not_called()


class FollowCurrentContainerLogTest(unittest.TestCase):
    def test_skips_existing_logs_then_reads_appended_logs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log_path = Path(directory) / "container-json.log"
            log_path.write_text(self.docker_line("old log"))
            follower = forward_error_logs.follow_current_container_log(
                resolve_log_path=lambda: log_path,
                sleep=lambda _: None,
            )

            self.assertIsNone(next(follower))
            with log_path.open("a") as fp:
                fp.write(self.docker_line("new log"))

            self.assertEqual("new log", next(follower))
            follower.close()

    def test_reads_new_container_log_from_beginning_after_path_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            first_path = Path(directory) / "first-json.log"
            second_path = Path(directory) / "second-json.log"
            first_path.write_text(self.docker_line("old log"))
            current_path = [first_path]
            follower = forward_error_logs.follow_current_container_log(
                resolve_log_path=lambda: current_path[0],
                sleep=lambda _: None,
            )

            self.assertIsNone(next(follower))
            second_path.write_text(self.docker_line("first log from replacement"))
            current_path[0] = second_path

            self.assertEqual("first log from replacement", next(follower))
            follower.close()

    def test_reads_replaced_log_from_beginning_when_path_is_unchanged(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log_path = Path(directory) / "container-json.log"
            replacement_path = Path(directory) / "replacement-json.log"
            log_path.write_text(self.docker_line("old log"))
            follower = forward_error_logs.follow_current_container_log(
                resolve_log_path=lambda: log_path,
                sleep=lambda _: None,
            )

            self.assertIsNone(next(follower))
            replacement_path.write_text(self.docker_line("first log after rotation"))
            replacement_path.replace(log_path)

            self.assertEqual("first log after rotation", next(follower))
            follower.close()

    @staticmethod
    def docker_line(message: str) -> str:
        return json.dumps({"log": message}) + "\n"


if __name__ == "__main__":
    unittest.main()


class ParsingAlertTest(unittest.TestCase):
    def test_terminal_event_contains_post_stage_and_env_specific_recovery_link(self):
        entry = {"level": "ERROR", "event_type": "post.parsing.summary_failed", "post_id": 616,
                 "job_id": 378, "job_type": "POST_MEDIA", "failure_stage": "PLACE_IMAGE_OCR",
                 "failure_summary": json.dumps([{"stage": "PLACE_IMAGE_OCR", "reason": "이미지 문자 인식 실패", "count": 1}]),
                 "total_count": 8, "completed_count": 1, "failed_count": 7, "failed_at": "2026-09-13T00:00:00Z", "message": "failed"}
        level, body, context = forward_error_logs.parse_log_entry(json.dumps(entry))
        for env, host in [("dev", "dev-admin.everynook.co.kr"), ("live", "admin.everynook.co.kr")]:
            with patch.object(forward_error_logs, "ENVIRONMENT", env):
                embed = forward_error_logs.parsing_payload(context)["embeds"][0]
                self.assertIn("616", embed["title"])
                self.assertIn("이미지 OCR", embed["description"])
                self.assertIn(host, embed["url"])
                self.assertIn("/posts/616/processing", embed["url"])
        with patch.object(forward_error_logs, "PARSING_ONLY", True):
            self.assertTrue(forward_error_logs.should_forward(level, context))
            self.assertFalse(forward_error_logs.should_forward("WARN", context))
            self.assertFalse(forward_error_logs.should_forward("ERROR", {}))

    def test_seven_failures_are_one_summary_payload_and_old_job_events_are_ignored(self):
        entry = {"level": "ERROR", "event_type": "post.parsing.summary_failed", "post_id": 616,
                 "total_count": 8, "completed_count": 1, "failed_count": 7,
                 "failure_summary": json.dumps([
                     {"stage": "PLACE_TEXT_RESOLUTION", "reason": "장소 후보 없음", "count": 1},
                     {"stage": "POST_MEDIA", "reason": "다운로드 실패", "count": 6}])}
        _, _, context = forward_error_logs.parse_log_entry(json.dumps(entry))
        with patch.object(forward_error_logs, "post_payload") as send:
            with patch.object(forward_error_logs, "PARSING_DISCORD_WEBHOOK_URL", "https://discord.example/alerts"):
                forward_error_logs.post_error_log(["summary"], context)
            send.assert_called_once()
            embed = send.call_args.args[1]["embeds"][0]
            self.assertIn("장소 검색: 장소 후보 없음 (1건)", embed["description"])
            self.assertIn("미디어 저장: 다운로드 실패 (6건)", embed["description"])
            self.assertEqual("7", next(f["value"] for f in embed["fields"] if f["name"] == "실패"))
        with patch.object(forward_error_logs, "PARSING_ONLY", True):
            self.assertFalse(forward_error_logs.should_forward("ERROR", {"event_type": "post.parsing.failed"}))

    def test_summary_includes_specific_cause_code_retry_result_and_post_detail_link(self):
        context = {
            "event_type": "post.parsing.summary_failed", "post_id": "902",
            "failure_summary": json.dumps([{
                "stage": "PLACE_IMAGE_OCR", "reason": "접근 권한 확인 필요", "count": 1,
                "detail": "HTTP 403: image access denied", "code": "403", "attempts": 8, "retried": True,
            }]), "completed_count": 1, "failed_count": 1, "total_count": 2,
        }
        for environment, host in (("dev", "dev-admin.everynook.co.kr"), ("live", "admin.everynook.co.kr")):
            with self.subTest(environment=environment), patch.object(forward_error_logs, "ENVIRONMENT", environment):
                payload = forward_error_logs.parsing_payload(context)
                self.assertEqual(1, len(payload["embeds"]))
                embed = payload["embeds"][0]
                self.assertIn("이미지 OCR: 접근 권한 확인 필요 (1건)", embed["description"])
                self.assertIn("오류 코드: 403", embed["description"])
                self.assertIn("HTTP 403: image access denied", embed["description"])
                self.assertIn("관리자 재시도 후 실패 · 최대 8회 실행", embed["description"])
                self.assertEqual(f"https://{host}/#/posts/902/processing", embed["url"])
                self.assertEqual({"parse": []}, payload["allowed_mentions"])

    def test_post_save_api_failure_has_stage_without_fabricated_post_id(self):
        _, _, context = forward_error_logs.parse_log_entry(json.dumps({
            "level": "ERROR", "request_method": "POST", "http_route": "/api/v1/posts",
            "request_id": "request-353", "@timestamp": "2026-09-13T00:00:00Z",
        }))
        payload = forward_error_logs.parsing_payload(context)["embeds"][0]
        self.assertEqual("post.save.failed", context["event_type"])
        self.assertIn("게시물 저장 요청", payload["title"])
        self.assertNotIn("url", payload)
        self.assertIn("request-353", str(payload))

    def test_parsing_failures_use_alert_channel_and_generic_errors_keep_error_channel(self):
        with (
            patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/errors"),
            patch.object(forward_error_logs, "PARSING_DISCORD_WEBHOOK_URL", "https://discord.example/alerts"),
            patch.object(forward_error_logs, "post_payload") as send,
        ):
            forward_error_logs.post_error_log(["failed"], {"event_type": "post.save.failed"})
            self.assertTrue(send.call_args.args[0].startswith("https://discord.example/alerts?"))
            forward_error_logs.post_error_log(["failed"], {})
            self.assertTrue(send.call_args.args[0].startswith("https://discord.example/errors?"))


    def test_rate_limit_waits_then_delivers_and_permanent_failure_does_not_retry(self):
        limited = urllib.error.HTTPError("https://discord.example/alerts", 429, "limited", {"Retry-After": "2.5"}, None)
        with (
            patch.object(forward_error_logs.urllib.request, "urlopen", side_effect=[limited, MagicMock()]) as send,
            patch.object(forward_error_logs.time, "sleep") as wait,
        ):
            forward_error_logs.post_payload("https://discord.example/alerts", {})
            self.assertEqual(2, send.call_count)
            wait.assert_called_once_with(2.5)
        denied = urllib.error.HTTPError("https://secret.example/token", 403, "denied", {}, None)
        with (
            patch.object(forward_error_logs.urllib.request, "urlopen", side_effect=denied) as send,
            patch.object(forward_error_logs.time, "sleep") as wait,
            patch("builtins.print") as output,
        ):
            forward_error_logs.post_payload("https://discord.example/alerts", {})
            self.assertEqual(1, send.call_count)
            wait.assert_not_called()
            self.assertNotIn("secret.example", str(output.call_args_list))


class EmptyPlacesWarningTest(unittest.TestCase):
    def test_warning_reaches_alert_channel_with_one_amber_summary_in_both_environments(self):
        entry = {"level": "WARN", "event_type": "post.parsing.summary_warning", "post_id": 919,
                 "warning_code": "NO_PLACES", "failure_summary": "[]", "failed_count": 0,
                 "completed_count": 7, "total_count": 7, "message": "completed"}
        level, body, context = forward_error_logs.parse_log_entry(json.dumps(entry))
        for environment in ("dev", "live"):
            with (patch.object(forward_error_logs, "ENVIRONMENT", environment),
                  patch.object(forward_error_logs, "PARSING_ONLY", True),
                  patch.object(forward_error_logs, "PARSING_DISCORD_WEBHOOK_URL", "https://discord.example/alerts"),
                  patch.object(forward_error_logs, "post_payload") as send):
                self.assertTrue(forward_error_logs.should_forward(level, context))
                self.assertFalse(forward_error_logs.should_forward("WARN", {}))
                forward_error_logs.post_error_log([body], context)
                send.assert_called_once()
                self.assertIn("/alerts?", send.call_args.args[0])
                payload = send.call_args.args[1]
                self.assertEqual(1, len(payload["embeds"]))
                embed = payload["embeds"][0]
                self.assertEqual(f"[{environment}] 게시물 #919 · 장소 없이 저장됨", embed["title"])
                self.assertEqual(16753920, embed["color"])
                self.assertIn("정상 저장", embed["description"])
                self.assertNotIn("마지막 실패 시각", str(embed))
                self.assertIn("연결된 장소가 0개", str(embed))
                self.assertIn("/posts/919/processing", embed["url"])

    def test_warning_is_included_in_failure_summary_without_another_message(self):
        entry = {"level": "ERROR", "event_type": "post.parsing.summary_failed", "post_id": 919,
                 "warning_code": "NO_PLACES", "failed_count": 1, "completed_count": 2, "total_count": 3,
                 "failure_summary": json.dumps([{"stage": "POST_MEDIA", "reason": "다운로드 실패", "count": 1}])}
        _, _, context = forward_error_logs.parse_log_entry(json.dumps(entry))
        payload = forward_error_logs.parsing_payload(context)
        self.assertEqual(1, len(payload["embeds"]))
        embed = payload["embeds"][0]
        self.assertEqual(15158332, embed["color"])
        self.assertIn("미디어 저장: 다운로드 실패", embed["description"])
        self.assertIn("장소 없이 저장됨", str(embed["fields"]))
