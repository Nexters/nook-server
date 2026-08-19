# NOOK-235 게시물 저장 결과 FCM 푸시 알림 구현

## 목적

클라이언트가 등록한 FCM 토큰으로 게시물 저장 처리 결과 푸시 알림을 발송한다.

## 범위

- Firebase Admin Java SDK sender adapter 추가
- FCM 토큰 등록/삭제 API 추가
- 사용자별 push token 저장 테이블 추가
- 장소 파싱 완료 시 저장 성공 푸시 발송
- 콘텐츠 파싱 또는 장소 파싱 최종 실패 시 저장 실패 푸시 발송
- FCM invalid/unregistered token 비활성화

## 제외 범위

- 모바일 앱 코드 변경
- 클라이언트 알림 권한 요청 및 foreground 표시 처리
- 마케팅, 토픽, 세그먼트 푸시
- 푸시 발송 이력 테이블
- 아웃박스 기반 발송 보장

## API

- `PUT /api/v1/me/push-tokens`
- `DELETE /api/v1/me/push-tokens`

클라이언트는 로그인 후, 앱 시작 시 로그인 상태일 때, FCM token refresh 시 `PUT`으로 최신 토큰을 동기화한다.
로그아웃 시에는 현재 기기 토큰을 `DELETE`로 제거한다.

## 알림 문구

- 성공: `게시물 저장이 완료됐어요!` / `지금 앱에서 확인해보세요.`
- 실패: `앗, 저장에 실패했어요` / `다시 시도하러 가볼까요?`

## 운영 메모

- Firebase credential은 `GOOGLE_APPLICATION_CREDENTIALS` 환경 변수로 주입된 파일을 사용한다.
- 외부 FCM 호출은 파싱 DB 트랜잭션이 끝난 뒤 실행한다.
- FCM이 invalid/unregistered token을 응답하면 해당 token을 비활성화한다.

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

## 검증

- `./gradlew check`
