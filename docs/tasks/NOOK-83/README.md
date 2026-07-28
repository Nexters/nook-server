# NOOK-83 공개 API 시간 응답을 Asia/Seoul 오프셋으로 제공

## 목적

클라이언트가 별도 시간대 변환 없이 한국 현지 시각을 표시할 수 있도록 공개 API의 시간 응답을
`Asia/Seoul` 오프셋으로 제공한다.

## API 계약 변경

기존 필드의 이름과 절대 시각은 유지하고 ISO-8601 문자열의 오프셋만 변경한다.

```text
변경 전: 2026-07-27T00:00:00Z
변경 후: 2026-07-27T09:00:00+09:00
```

정상적인 ISO-8601 offset-aware parser는 두 값을 같은 절대 시각으로 처리한다. `Z` 접미사를
하드코딩한 클라이언트는 `+09:00` 오프셋을 허용하도록 변경해야 한다. 별도 API 버전은 추가하지 않는다.

## 범위

- 게시물 목록 `savedAt`
- 게시물 상세 `publishedAt`, `savedAt`
- 그룹 게시물 목록 `savedAt`
- 장소 상세 게시물 목록 `savedAt`
- presentation 응답 경계의 `Asia/Seoul` 변환
- 대상 controller 응답 회귀 테스트

## 제외 범위

- DB 데이터와 칼럼 타입 변경
- domain과 application의 `Instant` 변경
- Hibernate UTC 설정 변경
- JWT 만료, 비동기 재시도와 내부 시간 계산 변경
- 요청 필드와 오류 계약 변경
- 배포 환경 변경

## 성공 기준

- 대상 공개 API 시간 필드가 `+09:00` 오프셋으로 응답한다.
- 변환 전후 절대 시각은 동일하다.
- nullable 시간 필드는 기존 null 의미를 유지한다.
- DB, domain과 application은 UTC `Instant`를 유지한다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-presentation:test
./gradlew detekt
./gradlew check
```

## DDL

스키마 변경은 없다.
