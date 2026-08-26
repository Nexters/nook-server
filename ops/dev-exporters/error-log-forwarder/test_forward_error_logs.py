import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


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


class SlackPayloadTest(unittest.TestCase):
    def test_builds_bold_context_fields_and_request_filtered_grafana_button(self) -> None:
        context = {
            "service_name": "nook-api",
            "request_id": "abc123def456gh78",
            "user_id": "42",
            "url_path": "POST /api/v1/posts/{postId}",
        }
        with (
            patch.object(forward_error_logs, "ENVIRONMENT", "dev"),
            patch.object(forward_error_logs, "GRAFANA_BASE_URL", "https://grafana.example.com/"),
        ):
            payload = forward_error_logs.slack_payload(["error body"], context)

        fields = payload["blocks"][1]["fields"]
        self.assertEqual("*Service Name*\nnook-api", fields[0]["text"])
        self.assertEqual("*Request ID*\nabc123def456gh78", fields[1]["text"])
        self.assertEqual("*User ID*\n42", fields[2]["text"])
        self.assertEqual("*URL Path*\nPOST /api/v1/posts/{postId}", fields[3]["text"])
        button_url = payload["blocks"][3]["elements"][0]["url"]
        self.assertIn("/d/nook-dev-logs/nook-dev-logs?", button_url)
        self.assertIn("var-requestIdText=abc123def456gh78", button_url)
        self.assertIn("from=now-15m", button_url)

    def test_uses_fallback_values_and_omits_button_without_request_id(self) -> None:
        with patch.object(forward_error_logs, "GRAFANA_BASE_URL", "https://grafana.example.com"):
            payload = forward_error_logs.slack_payload(["error body"], {})

        fields = payload["blocks"][1]["fields"]
        self.assertTrue(all(field["text"].endswith("\n-") for field in fields))
        self.assertEqual(3, len(payload["blocks"]))


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
