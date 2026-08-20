# NOOK-259 장소 파싱 완전성 검증과 부분 성공 진단 도입

## 목적

본문의 장소 개수 문구뿐 아니라 이미지별 상호명·주소 카드 근거로 기대 장소 수를 계산하고, 추출 또는 검색에서
누락된 장소가 있으면 부분 성공으로 진단한다.

## 범위

- 주소와 별도 상호명 텍스트가 있는 이미지 수를 기대 장소 수에 반영
- 이미지 기반 기대 수를 recall recovery 조건에 사용
- 추출 단서별 검색·선택 실패 이유 수집
- 완료 작업에 기대·추출·해결 개수와 `COMPLETED/PARTIAL` 결과 저장
- 미해결 원본 단서와 실패 이유 JSON 저장
- 기존 공개 `placeParsingStatus=COMPLETED` 계약 유지

## 제외 범위

- 기존 게시물 자동 일괄 재파싱
- 장소 사진 provider 변경
- 장소가 전혀 없는 게시물의 공개 상태 계약 변경

## 성공 기준

- 본문에 숫자가 없어도 상호명·주소 카드가 있는 이미지 수가 복구 기준이 된다.
- 기대 수보다 적게 해결되거나 개별 단서가 실패하면 `PARTIAL`로 기록된다.
- 미해결 장소명, 주소 단서와 실패 원인을 조회할 수 있다.
- 기존 API 계약을 유지한다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-application:test --tests '*PlaceParsingResilienceTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*PlaceParsingPersistenceAdapterTest'`
- `./gradlew check`
