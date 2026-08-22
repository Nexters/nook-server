# NOOK-283 앱 빌드 번호 기반 강제 업데이트 정책 조회 API 구현

## 목적

iOS와 Android 앱의 업데이트 정책을 플랫폼별로 관리하고, 현재 앱 빌드 번호를 기준으로
강제, 권장 또는 불필요 업데이트 여부를 공개 API로 제공한다.

## 범위

- 플랫폼별 앱 버전 정책 저장 테이블 추가
- 빌드 번호 기반 `FORCE`, `RECOMMEND`, `NONE` 판정
- 공개 앱 버전 정책 조회 API 추가
- 운영자용 플랫폼별 정책 조회·수정 API와 감사 로그 추가
- 어드민 웹 앱 버전 관리 화면 추가
- 유스케이스, 영속성 어댑터 및 API 경계값 테스트
- `nook-client`의 후속 작업 범위 확인

## 제외 범위

- `nook-client` 코드 변경
- 전체 API 요청에 대한 `426 Upgrade Required` 차단

## API

- `GET /api/public/v1/app-version-policy`
- `GET /api/admin/v1/app-version-policies`
- `PUT /api/admin/v1/app-version-policies/{platform}`

클라이언트는 앱 요청에 다음 헤더를 전달한다.

- `X-App-Platform: IOS` 또는 `ANDROID`
- `X-App-Build-Number: 1`
- `X-App-Version: 1.1.1` (정책 판정에는 사용하지 않는 표시·관찰용 값)

정책 조회 API에서는 플랫폼과 빌드 번호 헤더가 필수다. 세 헤더는 요청 구조화 로그에 기록되어
앱 버전별 장애 및 사용 현황 분석에 사용할 수 있다.

정책이 등록되지 않은 플랫폼은 클라이언트 진입을 막지 않도록 `NONE`을 반환한다.

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

## 검증

- `./gradlew check`
- `pnpm --dir nook-admin-web build`
