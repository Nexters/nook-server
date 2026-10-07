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


class RemovedParsingAlertTest(unittest.TestCase):
    def test_removed_events_never_reach_discord(self):
        for environment in ("dev", "live"):
            for parsing_only in (False, True):
                for event_type, level in (
                    ("post.parsing.summary_warning", "WARN"),
                    ("post.parsing.summary_warning", "ERROR"),
                    ("post.parsing.summary_failed", "ERROR"),
                    ("post.save.failed", "ERROR"),
                ):
                    with (
                        self.subTest(environment=environment, parsing_only=parsing_only, event_type=event_type, level=level),
                        patch.object(forward_error_logs, "ENVIRONMENT", environment),
                        patch.object(forward_error_logs, "PARSING_ONLY", parsing_only),
                        patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/errors"),
                        patch.object(forward_error_logs.urllib.request, "urlopen") as send,
                    ):
                        parsed_level, body, context = forward_error_logs.parse_log_entry(json.dumps({
                            "level": level, "event_type": event_type, "message": "removed alert",
                            "post_id": 919, "warning_code": "NO_PLACES", "failure_summary": "[]",
                        }))
                        self.assertFalse(forward_error_logs.should_forward(parsed_level, context))
                        forward_error_logs.post_error_log([body], context)
                        send.assert_not_called()

    def test_save_request_errors_are_suppressed_for_both_routes_and_context_formats(self):
        for route in ("/api/v1/posts", "/api/v1/shared-posts/123/save", "/api/v1/shared-posts/{sharedPostId}/save"):
            for method_key, route_key in (
                ("request_method", "http_route"), ("http_method", "request_path"),
                ("request.method", "http.route"), ("http.method", "request.path"),
            ):
                with (
                    self.subTest(route=route, method_key=method_key),
                    patch.object(forward_error_logs, "PARSING_ONLY", False),
                    patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/errors"),
                    patch.object(forward_error_logs.urllib.request, "urlopen") as send,
                ):
                    level, body, context = forward_error_logs.parse_log_entry(json.dumps({
                        "level": "ERROR", method_key: "POST", route_key: route,
                        "request_id": "req-403", "message": "save failed", "stack_trace": "at Save.run()",
                    }))
                    self.assertEqual("post.save.failed", context["event_type"])
                    self.assertIn("at Save.run()", body)
                    self.assertFalse(forward_error_logs.should_forward(level, context))
                    forward_error_logs.post_error_log([body], context)
                    send.assert_not_called()

    def test_generic_errors_still_reach_error_channel_only_in_general_mode(self):
        for method, route in (("GET", "/api/v1/posts"), ("POST", "/api/v1/posts/123"), ("POST", "/api/v1/shared-posts/123")):
            for parsing_only in (False, True):
                with (
                    self.subTest(method=method, route=route, parsing_only=parsing_only),
                    patch.object(forward_error_logs, "PARSING_ONLY", parsing_only),
                    patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/errors"),
                    patch.object(forward_error_logs.urllib.request, "urlopen", return_value=MagicMock()) as send,
                ):
                    level, body, context = forward_error_logs.parse_log_entry(json.dumps({
                        "level": "ERROR", "request_method": method, "http_route": route, "message": "server failed",
                    }))
                    self.assertEqual(not parsing_only, forward_error_logs.should_forward(level, context))
                    forward_error_logs.post_error_log([body], context)
                    if parsing_only:
                        send.assert_not_called()
                    else:
                        self.assertEqual(1, send.call_count)
                        self.assertEqual("https://discord.example/errors?wait=true", send.call_args.args[0].full_url)

    def test_main_flushes_generic_errors_without_forwarding_removed_alerts(self):
        entries = [
            {"level": "ERROR", "message": "before"},
            {"level": "WARN", "event_type": "post.parsing.summary_warning", "message": "no places"},
            {"level": "ERROR", "event_type": "post.parsing.summary_failed", "message": "summary failed"},
            {"level": "ERROR", "request_method": "POST", "http_route": "/api/v1/posts", "message": "save failed"},
            {"level": "ERROR", "message": "after"},
            {"level": "INFO", "message": "done"},
        ]
        with (
            patch.object(forward_error_logs, "PARSING_ONLY", False),
            patch.object(forward_error_logs, "DISCORD_WEBHOOK_URL", "https://discord.example/errors"),
            patch.object(forward_error_logs, "follow_current_container_log", return_value=iter(json.dumps(entry) for entry in entries)),
            patch.object(forward_error_logs.urllib.request, "urlopen", return_value=MagicMock()) as send,
        ):
            forward_error_logs.main()
            self.assertEqual(2, send.call_count)
            descriptions = [json.loads(call.args[0].data)["embeds"][0]["description"] for call in send.call_args_list]
            self.assertIn("before", descriptions[0])
            self.assertIn("after", descriptions[1])
            self.assertNotIn("save failed", str(descriptions))
            self.assertNotIn("summary failed", str(descriptions))
            self.assertNotIn("no places", str(descriptions))


class PostPayloadRetryTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
