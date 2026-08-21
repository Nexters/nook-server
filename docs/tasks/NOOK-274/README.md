# NOOK-274 Naver 대표 이미지 우선 수집 및 장소별 썸네일 완료 처리

## 목적

네이버 플레이스가 대표로 제공하는 이미지 순서를 우선 사용하고, 사진이 확보된 장소는 느린 fallback을
기다리지 않고 즉시 완료 처리한다.

## 원인

Naver 검색 actor는 장소별 대표 `Images`와 전체 `ImageCount`를 반환하지만 기존 구현은 이를 무시했다.
별도 사진 actor도 업체 공식 사진만 요청해 방문자 사진만 있는 장소는 빈 결과로 끝났다.

provider chain은 모든 장소에 대해 Google과 Naver fallback을 전부 실행한 뒤 결과를 일괄 반영했다.
이 때문에 Google에서 사진을 확보한 장소도 나머지 장소의 Naver actor 호출이 끝날 때까지
`PROCESSING` 상태로 남았다.

## 범위

- Naver 검색 응답의 대표 `Images`를 반환 순서대로 우선 사용
- 사진 actor를 `filterBy=all`로 호출해 업체, 방문자 및 블로그 사진 허용
- 대표 이미지가 부족한 수만큼 보충해 장소당 최종 최대 6장 저장
- 이미지 URL 중복 제거 및 영상·클립 제외
- 대표 이미지가 이미 6장이면 사진 actor 호출 생략
- provider별 사진 확보 장소 즉시 완료 처리
- 실패한 장소만 다음 provider로 전달

## 제외 범위

- Google과 Naver provider 병렬 호출
- 장소명 및 주소 후보 매칭 기준 변경
- 공개 API와 데이터베이스 스키마 변경
- 클라이언트 변경

## 성공 기준

- Naver 대표 이미지가 사진 actor 결과보다 먼저 저장된다.
- 대표 이미지와 전체 사진을 합쳐 장소당 최대 6장만 저장한다.
- 대표 이미지가 1장이면 사진 actor에 최대 5장을 요청한다.
- Google 성공 장소는 Naver fallback 실행 전에 `COMPLETED`로 반영된다.
- 실패한 장소만 다음 provider로 fallback한다.
- `./gradlew check`가 성공한다.

## 검증

- `./gradlew :nook-api-application:test --tests '*StorePlaceThumbnailUseCaseTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*ApifyNaverPlacePhotoProviderTest' --tests '*RuntimePlaceThumbnailProviderTest'`
- `./gradlew detekt`
- `./gradlew check`

## 배포 및 롤백

스키마와 공개 API 변경은 없다. 애플리케이션만 배포하며 문제가 생기면 이전 버전으로 롤백한다.
