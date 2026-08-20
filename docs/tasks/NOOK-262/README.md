# NOOK-262 장소 이미지 OCR 정확도 개선 및 빈 전사 복구

## 목적

장소 카드의 작은 한글과 주소 OCR 정확도를 높이고, 빈 전사 또는 상호명 오타 때문에 장소가 누락되지 않게 한다.

## 범위

- 일반 추론용 `gpt-5-nano`와 OCR 전용 `gpt-5-mini` 모델 설정 분리
- 빈 전사를 실패로 판정해 최신 저장 이미지 URL로 재시도
- 상호명과 주소 조합뿐 아니라 주소 단독 검색 추가
- OCR 상호명에 오타가 있어도 도로명·건물번호가 일치하는 단일 후보 허용
- 한 장 단위 제한 병렬 OCR 유지

## 제외 범위

- 자격정보가 제거된 Cloud Vision의 dev 재활성화
- live 환경 변경
- 기존 게시물 일괄 재파싱

## 성공 기준

- 빈 전사가 정상 성공으로 확정되지 않는다.
- OCR 요청은 전역 OpenAI 모델과 독립된 모델을 사용한다.
- 정확한 주소가 있으면 상호명 OCR 오타에도 장소 후보를 복구할 수 있다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-application:test --tests '*PlaceParsingResilienceTest'`
- `./gradlew :nook-api-application:test --tests '*PlaceClueCandidateMatcherTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*OpenAiContentInferenceAdapterTest'`
- `./gradlew check`
