# NOOK-335 푸시 알림 설정 추가

## 목적

회원이 게시물 저장 처리 결과 푸시 알림 수신 여부를 조회하고 변경할 수 있게 한다.

## 범위

- 회원별 게시물 저장 처리 결과 알림 설정 영속화
- 설정 조회 및 변경 API 추가
- 게시물 저장 완료·실패 푸시 대상에 설정 반영
- 기존 기기 토큰 등록·삭제 및 invalid token 비활성화 계약 유지

## 기본 정책

설정 레코드가 없는 기존 회원과 신규 회원은 `postProcessingEnabled=true`로 간주한다. 회원이 명시적으로
설정을 변경할 때 레코드를 생성한다. 기기 토큰의 `enabled`는 FCM 토큰 유효성 및 등록 상태만 나타내며,
사용자 알림 설정과 독립적으로 유지한다.

## API

- `GET /api/v1/me/push-preferences`
  - 응답: `{ "postProcessingEnabled": true }`
- `PATCH /api/v1/me/push-preferences`
  - 요청: `{ "postProcessingEnabled": false }`
  - 응답: 변경된 설정

기존 `PUT/DELETE /api/v1/me/push-tokens` 계약은 변경하지 않는다.

## 데이터베이스

- `user_push_preferences` 테이블을 추가한다.
- 회원별 한 행만 허용한다.
- 물리 foreign key는 생성하지 않는다.
- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

## 제외 범위

- OS 알림 권한 상태 동기화
- 알림 종류 추가 및 마케팅 알림 설정
- 푸시 발송 이력과 outbox

## 검증

- 설정이 없는 회원은 기본값 `true` 조회
- 설정 생성 및 반복 변경
- OFF 회원의 유효 토큰은 발송 대상에서 제외
- ON 회원의 유효 토큰은 기존과 동일하게 발송
- invalid token 비활성화 동작 유지
- `./gradlew check`
