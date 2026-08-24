# NOOK-292 외부 Provider 사용량·예상 비용 통합 관측 및 어드민 대시보드

## 목적

외부 Provider의 실제 요청량, 성공 여부, 사용량과 예상 비용을 중앙 원장에 기록하고 어드민에서
기간·Provider·operation별 현황을 한눈에 조회한다.

## 범위

- OpenAI, Bright Data, Apify, Google Vision/Places, Kakao/Naver Local, Corepin, CLOVA 호출 계측
- 호출 당시 단가 snapshot과 예상 비용 기록
- 공개 단가가 없는 호출의 `UNPRICED`/`QUOTA_ONLY` 가시화
- 월간 Provider 예산과 50/80/100% 상태 조회
- 어드민 요약, Provider 상세, 최근 실패 및 가격·예산 설정

## 가격 기준

가격은 2026-08-25 확인한 공식 문서를 기준으로 하며 원본 통화와 단위를 보존한다.

- OpenAI GPT-5 nano: 입력 $0.05, 캐시 입력 $0.005, 출력 $0.40 / 1M tokens
  - https://developers.openai.com/api/docs/models/gpt-5-nano
- Google Cloud Vision Document Text Detection: 월 1,000 units 무료, 이후 $1.50 / 1,000 units
  - https://cloud.google.com/vision/pricing
- Google Places API (New): Nearby/Text Search Pro $32, Place Details Pro $17 / 1,000 events,
  Place Details Photos $7 / 1,000 events. 각 SKU의 공식 무료 구간을 적용한다.
  - https://developers.google.com/maps/billing-and-pricing/pricing
- Bright Data Web Scraper API PAYG: 월 5,000 successful records 무료, 이후 $1.50 / 1,000 records
  - https://brightdata.com/pricing/web-scraper
- Apify는 Actor별 pay-per-event 또는 실제 CU/traffic 과금이 달라 기본 단가를 두지 않는다.
  - https://apify.com/pricing
- Kakao Local은 통합 월 무료 quota를 호출량으로만 추적한다.
  - https://developers.kakao.com/docs/en/getting-started/quota

USD 예상 원화 비용은 운영 설정 환율을 사용한다. 환율이 없으면 원화 비용을 0으로 만들지 않고
가격 미확정 상태로 노출한다.

## 제외 범위

- 예산 초과 호출 자동 차단
- OAuth, FCM, S3/CDN/DB 비용
- Provider 청구서 자동 대사
- 기존 공개 API 계약 변경

## 성공 기준

- 성공·실패 호출과 실제 사용량이 중복 없이 기록된다.
- cache hit은 외부 호출로 기록되지 않는다.
- 공식 단가와 호출 시점 가격 snapshot으로 예상 비용을 계산한다.
- 미정가 호출도 누락 없이 어드민에 표시된다.
- 가격·예산 변경은 사유와 함께 감사 로그에 남는다.
- `pnpm --dir nook-admin-web build`와 `./gradlew check`가 통과한다.

