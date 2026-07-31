# NOOK-101 구글 지도 장소 사진을 장소 썸네일로 저장

## 목적

네이버/카카오 장소 확정 이후 해당 장소 정보를 기반으로 Google Maps API에서 썸네일용 사진 1장을 가져와 서비스 버킷에 저장하고, 장소 썸네일로 사용할 수 있도록 한다.

## 범위

- Google Places API로 장소 사진 1장 조회
- Google 사진 URL을 직접 저장하지 않고 기존 미디어 저장 버킷에 업로드
- 저장된 버킷 URL을 `places.thumbnail_url`에 저장
- 자동 장소 파싱과 수동 장소 연결 모두에서 썸네일 저장 시도
- 썸네일 저장 실패는 장소 확정 흐름을 실패시키지 않음
- 게시물/그룹/장소 대표 이미지 조회 시 연결된 장소의 `places.thumbnail_url` 우선 사용
- Google API 설정값과 `.env.example` 갱신
- DDL/rollback 작성

## 제외 범위

- 여러 장 사진 저장
- 사용자 사진 선택 기능
- 기존 게시물 미디어 목록 순서 변경
- 장소 대표 사진 관리 API
- 지도 핀 색상/장소 메모 정책 변경

## 검증

- `./gradlew :nook-api-application:test --tests '*ProcessPlaceParsingJobUseCaseTest' --tests '*ConnectPostPlaceUseCaseTest' --no-daemon --no-build-cache`
- `./gradlew :nook-api-infrastructure:test --tests '*GooglePlacePhotoProviderTest' --tests '*PlaceParsingPersistenceAdapterTest' --tests '*ConnectPostPlacePersistenceAdapterTest' --tests '*SavedPostQueryPersistenceAdapterTest' --tests '*PlaceDetailQueryPersistenceAdapterTest' --tests '*PlaceMapQueryPersistenceAdapterTest' --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`
