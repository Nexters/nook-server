# NOOK-269 장소 파싱 실패 시 게시물 노출 유지

## 목적

게시물 본문 파싱이 성공했다면 장소 파싱이 실패하거나 장소를 찾지 못해도 게시물 상세를 정상 노출하고,
사용자가 장소를 직접 연결할 수 있게 한다.

## 범위

- 본문 파싱 `COMPLETED`와 장소 파싱 `FAILED` 조합의 종합 상태를 `COMPLETED`로 반환한다.
- 장소별 `placeParsingStatus=FAILED`와 `failureReason` 계약은 유지한다.
- 상태 조합 회귀 테스트를 추가한다.

## 제외 범위

- 장소 파싱 재시도 및 정확도 개선
- 상태 enum 또는 API 응답 필드 변경
- 프론트 UI 변경
- DB 스키마 변경

## 성공 기준

- 장소 파싱 실패 시에도 게시물 상세 화면이 노출된다.
- 기존 장소 실패 안내와 직접 추가 UI를 사용할 수 있다.
- 본문 파싱 실패는 계속 게시물 전체 실패로 처리된다.

## 검증

- `./gradlew :nook-api-application:test --tests 'org.every.nook.api.application.post.model.PostProcessingViewTest' --no-daemon`
- `./gradlew check --no-daemon`
