# NOOK-278 동일 장소 다중 provider 후보 복구

## 목적

본문의 지번 주소와 장소 provider의 도로명 주소가 같은 실제 장소를 가리키더라도 KAKAO와 NAVER
후보가 별도 행으로 집계되어 장소 파싱이 실패하는 문제를 해결한다. 이미지 fallback의 일반적인
장소 단서가 구체적인 본문 단서의 실패 사유를 덮지 않도록 진단 정보도 개선한다.

## 범위

- 안전한 주소 불일치 복구 후보를 논리적 장소 기준으로 병합한다.
- 동일 장소로 병합한 후보의 검색 query 근거를 합쳐 이후 grounding에 유지한다.
- 최종 장소가 없으면 구체적인 텍스트 단서 실패를 이미지 fallback 실패보다 우선한다.
- 지번·도로명 주소와 KAKAO·NAVER 중복 후보를 재현하는 회귀 테스트를 추가한다.
- 동일 상호의 다른 주소와 다른 상호의 같은 주소는 계속 별도 후보로 유지한다.

## 제외 범위

- DB 스키마 변경
- 공개 API 계약 변경
- provider 우선순위 변경
- 장소 추출 프롬프트 변경

## 성공 기준

- `텀 커피하우스`, `서울 마포구 서교동 376-7` 단서가 도로명 주소를 가진 KAKAO·NAVER
  후보 하나의 논리적 장소로 복구된다.
- provider별 검색 query 근거가 병합되어 선택 후 grounding 검증이 성공한다.
- 동일 상호의 다른 지점은 임의로 병합하거나 선택하지 않는다.
- 텍스트와 이미지 단서가 모두 실패하면 구체적인 텍스트 단서 실패 사유가 보존된다.
- `./gradlew check`가 성공한다.

## 검증 결과

- `./gradlew :nook-api-application:test --tests 'org.every.nook.api.application.place.PlaceCandidateDeduplicatorTest' --tests 'org.every.nook.api.application.place.ProcessPlaceParsingJobUseCaseTest'` 성공
- `./gradlew detekt` 성공
- `./gradlew check` 성공
