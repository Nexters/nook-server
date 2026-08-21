# NOOK-273 Grafana HTTP 응답 로그 한글 인코딩 수정

## 목적

JSON 응답 본문을 구조화 로그로 기록할 때 UTF-8 한글이 ISO-8859-1로 잘못 해석되는 문제를 수정합니다.

## 범위

- charset이 명시되지 않은 JSON 로그 본문을 UTF-8로 디코딩합니다.
- JSON에 charset이 명시되어 있으면 해당 charset을 사용합니다.
- 한국어 JSON 응답에 대한 회귀 테스트를 추가합니다.

## 제외 범위

- 클라이언트로 전달되는 HTTP 응답 및 API 계약 변경
- Promtail, Loki, Grafana 수집 구성 변경
- 기존 로그 데이터의 재처리

## 검증

- `./gradlew check`
- 한국어 JSON 응답을 ISO-8859-1 서블릿 기본 인코딩과 함께 전달해도 로그에는 UTF-8 문자열로 기록되는지 확인
