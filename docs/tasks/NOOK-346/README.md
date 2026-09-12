# HTTP 라우팅 오류의 404·405 응답 분리

## 이슈

[NOOK-346](https://linear.app/nooknook/issue/NOOK-346)

브랜치: `codex/NOOK-346-http-routing-errors`

## 목적

2026-09-12 새벽 외부 스캔 요청으로 발생한 없는 리소스와 미지원 HTTP 메서드 예외가
공통 예외 처리에서 ERROR 로그 및 500 응답으로 변환되는 문제를 수정한다.

## 범위

- `NoHandlerFoundException`, `NoResourceFoundException`: 404 / NOT_FOUND
- `HttpRequestMethodNotSupportedException`: 405 / METHOD_NOT_ALLOWED, Spring의 Allow 헤더 유지
- ApiResponse 실패 envelope 유지, 라우팅 오류에 ERROR 로그를 남기지 않음
- 예상하지 못한 예외의 500 / INTERNAL_SERVER_ERROR 및 ERROR 로그 유지
- 인증 HTTP 요청 예제와 API 정책 갱신

## 제외 범위

인증 정책, 정상 endpoint, 모니터링 임계치, IP 차단, 파싱 worker, DB 스키마, 운영 배포.

## 호환성

잘못된 경로와 HTTP 메서드 요청의 기존 500 / INTERNAL_SERVER_ERROR가 각각
404 / NOT_FOUND, 405 / METHOD_NOT_ALLOWED로 변경된다.
사용자는 이 변경 범위를 확인하고 2026-09-12 구현을 승인했다.
별도 버전 추가 없이 오류 분류를 바로잡으며, 클라이언트는 이 요청을 서버 오류로 재시도하지 않아야 한다.
응답 필드 구조, 정상 요청과 인증 실패 계약은 유지한다.

## 성공 기준과 검증

- 없는 handler와 정적 리소스: 404와 실패 envelope, ERROR 로그 없음
- POST 전용 경로의 GET: 405, Allow: POST, ERROR 로그 없음
- 예상하지 못한 예외: 500과 ERROR 로그, 내부 메시지 미노출
- 수정 전 회귀 테스트 4개 중 위 라우팅 오류 3개 실패 확인
- 수정 후 라우팅 회귀 테스트 4개 및 기존 GlobalExceptionHandlerTest 5개 통과
- `./gradlew clean check` 통과 (Detekt, 전체 테스트, 모듈 의존 방향; 일부 Gradle 캐시 재사용)
- `git diff --check` 통과
