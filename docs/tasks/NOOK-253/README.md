# NOOK-253 장소 이미지 재수집 상태 유지 및 제한 병렬화

## 목적

이미 사진이 저장된 장소를 다른 게시글이 재사용할 때 썸네일 상태와 사진을 유지하고, 신규 장소의
Apify 반환 이미지 저장 시간을 제한 병렬 처리로 줄인다.

## 원인

live의 게시글 `302`는 장소 5개의 썸네일 fetch에 62.8초가 걸렸다. 약 6분 뒤 게시글 `303`이 기존
장소 2개를 재사용하면서 해당 장소의 상태와 수정 시각이 다시 갱신돼, 최초 생성부터 최종 수정까지
401~402초가 걸린 것처럼 관측됐다.

자동·직접 장소 연결은 기존 장소의 사진 상태와 무관하게 썸네일 이벤트를 다시 발행할 수 있다. 또한
Apify actor에는 장소를 묶어서 요청하지만 반환된 장소별 사진은 스토리지에 순차 저장하고, actor 대기와
이미지 저장 시간을 하나의 `fetch` 단계로만 측정한다.

## 범위

- 사진이 있는 `COMPLETED` 장소는 상태와 기존 이미지를 유지하고 provider 요청에서 제외
- 이미 `PROCESSING`인 장소의 중복 provider 요청 방지
- 사진이 없는 완료·실패·대기 장소만 `PENDING`으로 전환하고 썸네일 이벤트 발행
- Apify 반환 이미지 저장을 기본 6개, 최대 12개의 제한된 동시성으로 처리
- `APIFY_GOOGLE_MAPS_STORAGE_CONCURRENCY` 환경 변수 제공
- `place-thumbnail` flow에 `apify-actor`, `image-store` stage 추가

## 제외 범위

- 공개 API와 데이터베이스 스키마 변경
- Apify actor 실행 방식과 provider chain 변경
- 실패한 썸네일 작업의 별도 영속 재시도 큐
- 클라이언트 변경

## 성공 기준

- 기존 사진이 있는 장소는 새 게시글에서 재사용돼도 `COMPLETED` 상태와 이미지를 유지한다.
- 이미 처리 중인 장소에 중복 썸네일 작업을 만들지 않는다.
- 실제 보충이 필요한 장소만 provider 요청에 포함한다.
- 다중 이미지 저장 동시성이 설정 상한을 넘지 않는다.
- actor 대기와 스토리지 저장 소요 시간을 별도로 관측할 수 있다.
- `./gradlew check`가 성공한다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*PlaceSupplementUpdateTest' \
  --tests '*ConnectPostPlacePersistenceAdapterTest' \
  --tests '*PlaceParsingPersistenceAdapterTest' \
  --tests '*ApifyGoogleMapsPhotoProviderTest'`
- `./gradlew detekt`
- `./gradlew check`

## 배포 및 롤백

스키마와 공개 API 변경은 없다. 애플리케이션만 배포하며 문제가 생기면 이전 버전으로 롤백한다.
배포 후 `place-thumbnail` flow의 `apify-actor`, `image-store`, 기존 `fetch` stage를 비교해 실제 단축 폭을
확인한다.
