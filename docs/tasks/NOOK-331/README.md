# NOOK-331 게시물 저장용 소유 아카이브 조회 API

## 목적

게시물을 저장할 아카이브를 선택할 때 현재 사용자가 수정할 수 없는 공유 구독 아카이브가 노출되지 않도록,
저장 가능한 본인 소유 아카이브만 조회하는 서버 계약을 제공한다.

## 범위

- `GET /api/v1/groups/owned` 추가
- 현재 로그인 사용자가 소유한 아카이브만 반환
- 기존 그룹 응답 형식 유지
- 공유 구독 아카이브 제외 및 소유권 경계 테스트
- HTTP Client 요청 예시와 OpenAPI 문서화

## API 계약

```http
GET /api/v1/groups/owned
Authorization: Bearer {accessToken}
```

- 응답은 기존 `GET /api/v1/groups`와 동일한 `GroupResponse[]` 형식이다.
- 모든 항목의 `accessType`은 `OWNED`이다.
- 공유 구독 아카이브는 반환하지 않는다.
- 기존 `GET /api/v1/groups`는 소유 및 공유 구독 아카이브를 함께 반환하는 현재 의미를 유지한다.

## 제외 범위

- 기존 그룹 목록 API의 응답 의미 변경
- 클라이언트의 아카이브 선택 UI 변경
- 공유 아카이브에 대한 쓰기 권한 부여
- 데이터베이스 스키마 변경

## 성공 기준

- 저장용 목록에는 현재 사용자의 소유 아카이브만 포함된다.
- 공유 구독 아카이브는 저장용 목록에서 제외된다.
- 기존 그룹 목록 API의 계약과 동작이 유지된다.
- 그룹 수정 및 저장 시 기존 소유권 검증을 우회하지 않는다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
