# NOOK-301 공식 빌링 API 기반 외부 API 비용 관측

## 목적

외부 API 비용의 근거를 애플리케이션 호출 기록과 수동 단가 계산에서 공급자 공식 빌링 API로 전환한다.

## 범위

- 공급자 공식 빌링 API를 매시간 조회한다.
- 공급자·SKU별 월간 사용량과 실제 비용 스냅샷을 저장한다.
- 어드민에서 외부 API 운영 정책, credential 상태, 공식 청구 비용과 동기화 상태를 조회한다.
- 호출 이벤트, 가격 정책, 무료 쿼터, 예상 비용, 상한과 Slack 알림을 제거한다.
- 기존 수동 집계 테이블은 개발 DB에서만 제거한다.
- 사용하지 않는 Google Cloud Vision OCR과 Google Places Photo provider 구현을 제거한다.
- Apify Google Maps Scraper는 별도 provider이므로 유지한다.
- 개발 환경의 Google 전용 credential과 빌링 상태 표시도 함께 정리한다.
- OpenAI Responses API 성공 응답의 공식 토큰 usage를 기능·모델·일자별로 저장한다.
- 어드민에서 선택 월의 전체·입력·출력·캐시 토큰, 구성, 일별 추이와 기능·모델별 사용량을 표시한다.

## 제외 범위

- 범용 외부 API 호출 이력 DB 기록
- 단가 기반 비용 추정
- OpenAI 토큰의 비용 환산과 실패 응답의 사용량 추정
- 호출량·금액 상한, 차단, 임계치 알림
- 라이브 DB 및 라이브 배포

## 검증

- `./gradlew check`
- `pnpm --dir nook-admin-web build`
- 개발 DB DDL 적용 후 빌링 동기화 및 어드민 조회 확인
