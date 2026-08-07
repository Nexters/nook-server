# NOOK-124 LLM 기반 장소 대표 해시태그 추출 및 응답 제공

## 목적

Instagram 게시물의 명시적 근거를 바탕으로 장소 특성 태그를 추출하고, 여러 게시물의 결과를 집계해 장소별 대표 태그를 최대 4개 제공한다.

## 범위

- 장소 확정 후 별도 비동기 흐름에서 본문, 원본 해시태그와 이미지를 OpenAI에 전달한다.
- 사전에 정의된 태그 중 근거가 있는 결과만 confidence와 근거와 함께 최대 4개 추출한다.
- 게시물과 장소별 추출 결과를 멱등 저장한다.
- 등장 게시물 수, 평균 confidence와 태그 이름 순으로 대표 태그를 결정한다.
- 집계된 대표 태그를 영속 장소 응답에 포함한다.
- LLM 또는 태그 저장 실패는 기존 장소 파싱 성공을 변경하지 않는다.

## 제외 범위

- 자유 형식 태그 생성
- 외부 지도 리뷰 수집
- 사용자 태그 편집
- 기존 장소 일괄 backfill
- 근거가 부족할 때 4개를 강제로 채우는 동작

## 데이터베이스

- `post_place_tags`: 게시물·장소별 LLM 태그, confidence와 근거를 저장한다.
- `places.representative_tags`: 장소 응답용 대표 태그 최대 4개를 JSON 배열로 캐시한다.
- 물리 foreign key는 생성하지 않는다.

## 검증

- `./gradlew detekt --no-daemon --no-build-cache`
- `./gradlew test --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`
