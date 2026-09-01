# NOOK-294 외부 API 연동·운영 현황 오버뷰

## 목적

외부 API 사용 이력이 없어도 현재 연동 대상, 용도, 호출 가능 상태와 fallback 정책을 어드민에서
한눈에 확인한다. 기간별 사용량과 비용은 이 카탈로그에 결합한다.

## 범위

- 콘텐츠 수집, 장소 검색, 장소 사진, OCR·추론 외부 API 카탈로그
- credential 값이 아닌 설정 여부 표시
- runtime configuration을 반영한 `ACTIVE`, `FALLBACK`, `STANDBY`, `DISABLED`, `MISCONFIGURED` 상태
- Provider별 용도, runtime, 적용 정책과 상태 사유 표시
- 기간별 호출, 실패, 최근 호출·오류, 예상 비용 결합
- 어드민 상태 필터와 기존 사용량 상세 유지

## 제외 범위

- 어드민에서 Provider 설정 변경
- 예산 기반 자동 차단
- credential 원문 노출
- 과금이 발생할 수 있는 능동 health probe
- DB 스키마 및 공개 API 변경

## 성공 기준

- 호출 이력이 0건인 Provider도 오버뷰에 표시된다.
- 각 Provider가 호출 대상인지, fallback인지, 비활성인지와 이유를 확인할 수 있다.
- 사용량, 비용, 최근 오류가 같은 Provider 행에 표시된다.
- `./gradlew check`와 `pnpm --dir nook-admin-web build`가 통과한다.
