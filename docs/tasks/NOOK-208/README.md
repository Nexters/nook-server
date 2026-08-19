# NOOK-208 외부 API 사용량·비용 원장 및 예산 게이트 구축

## 목적

외부 API 호출량과 예상 비용을 중앙에서 기록하고 월간 예산 정책에 따라 알림 또는 사전 차단합니다.

## 범위

- 공통 사용량 reserve/settle 및 멱등성 키를 제공합니다.
- provider/SKU/기능별 호출량과 예상 비용을 집계합니다.
- provider별 월간 예산, 50/80/100% 알림 기록, BLOCK 정책을 지원합니다.
- 관리자용 월간 대시보드, 기간별 집계, 단가 및 예산 설정 API를 제공합니다.
- 관리자 API는 `ADMIN_ALLOWED_USER_IDS`에 등록된 사용자만 접근할 수 있습니다.
- Google Maps, Cloud Vision, Kakao Local, Bright Data의 공개 가격을 기본 단가로 등록합니다.
- USD 공식 가격은 `EXTERNAL_API_USD_KRW_RATE` 환율로 예상 원화 비용으로 변환합니다.
- OpenAI `gpt-5-nano`는 입력·캐시 입력·출력 토큰 공식 단가를 각각 적용합니다.
- 계정 플랜과 실제 사용 자원에 따라 가격이 달라지는 Apify는 호출량을 기록하고 운영 단가를 별도로 설정합니다.
- Google Places, Cloud Vision, OpenAI 호출을 계측합니다.
- Google 사진과 Cloud Vision의 현재 비활성 기본값을 유지합니다.

## 제외 범위

- 어드민 화면
- provider 청구서 자동 대사
- OAuth 및 인프라 비용

## 검증

- 중복 reserve, 정산, 비용 계산, 예산 차단 테스트
- provider adapter 계측 테스트
- `./gradlew check`
