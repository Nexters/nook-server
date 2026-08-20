# NOOK-257 OpenAI 호출 레이트 리밋 방지 및 재시도 안정화

## 목적

게시물과 장소 파싱에서 공유하는 OpenAI 호출의 순간 동시 요청을 제한하고, HTTP 429 응답을 서버 힌트 또는
지수 백오프와 jitter에 따라 재시도해 rate limit 실패를 줄인다.

## 범위

- 공통 OpenAI `RestClient`에 동시 요청 상한 적용
- `Retry-After` 우선 429 재시도
- 서버 힌트가 없을 때 설정 가능한 지수 백오프와 jitter 적용
- 429 재시도 로그 추가
- 설정 및 재시도 동작 테스트

## 제외 범위

- 게시물 제목 프롬프트와 제목 생성 정책 변경
- 장소 및 태그 추출 호출 통합
- OpenAI 모델 변경
- API 계약과 DB 스키마 변경

## 검증

- `./gradlew :nook-api-infrastructure:test`
- `./gradlew detekt`
- `./gradlew check`
