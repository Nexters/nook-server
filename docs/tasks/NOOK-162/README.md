# NOOK-162 게시물·장소 파싱 구조화 단계 로그 표준화

## 목적

Prometheus metric과 별개로 게시물 저장부터 콘텐츠 수집, 장소 파싱과 후처리까지 원본 게시물 ID로
검색 가능한 구조화 애플리케이션 로그를 제공한다.

이번 리팩터링 기간에만 사용하는 임시 추적 로그이며 모든 새 메시지는 `[PostParcingTracker]`로 시작한다.

## 범위

- 공통 lifecycle event와 로그 필드를 정의한다.
- 콘텐츠·장소 비동기 실행에 `source_post.id`와 `processing.flow` MDC를 설정한다.
- Instagram, OpenAI, Kakao, Naver, 미디어, Google 사진과 태그 처리 결과를 요약해 기록한다.
- Instagram 캐시 적중 여부와 실제 provider 호출, fallback 및 응답 시간을 기록한다.
- Google 장소 매칭, 사진 목록, 사진 URI 조회와 스토리지 저장을 분리하고 누락 사유를 기록한다.
- 사용자 저장 게시물 ID와 원본 게시물 ID의 매핑을 기록한다.
- 새 구조화 로그에는 API key, 인증 헤더, 본문 원문, 전체 외부 응답과 전체 미디어 URL을 추가하지 않는다.
- 기존 로그의 메시지와 레벨은 호환성을 위해 변경하지 않는다.

## 제외 범위

- Prometheus metric 변경
- 장소 검색 및 후보 선택 알고리즘 변경
- 비동기 Job 및 DB 스키마 변경

## 주요 검색 필드

- `event.action`
- `event.outcome`
- `processing.flow`
- `processing.stage`
- `processing.attempt`
- `source_post.id`
- `saved_post.id`
- `provider.name`
- `failure.type`
- `failure.reason`

## 사진 누락 판별

- `place_not_matched`: Google 검색 결과에서 원본 장소와 일치하는 장소가 없음
- `no_photos`: 일치 장소는 있지만 제공된 사진이 없음
- `photo_uri_missing`: 사진 메타데이터 응답에 다운로드 URI가 없음
- `google.photo.media.failed`: 사진 URI 조회 요청 실패
- `google.photo.store.failed`: 스토리지 저장 실패
- `google.photo.pipeline.completed`: 선택·저장·실패한 사진 수의 최종 집계

## 로그 레벨

- 새 추적 로그는 기존 운영 로그와 Grafana 알림에 영향을 주지 않도록 모두 `DEBUG`로 기록한다.
- `event.outcome`과 `failure.type`으로 성공, 빈 결과, 재시도와 실패를 구분한다.
- 기존 로그의 레벨과 메시지는 그대로 유지한다.

고카디널리티 값은 Loki label로 추가하지 않고 JSON 검색 필드로만 기록한다.

## 검증

- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
