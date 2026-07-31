# NOOK-102 장소 응답에 썸네일 URL 포함

## 목적

Google Maps 장소 사진을 `places.thumbnail_url`에 저장하므로, 클라이언트가 장소 화면과 게시물 상세에서 장소 썸네일을 사용할 수 있도록 장소 응답에 `thumbnailUrl`을 포함한다.

## 범위

- `GET /api/v1/places/map` 지도 장소 응답에 `thumbnailUrl` 추가
- `GET /api/v1/places/recent` 최근 장소 응답의 `thumbnailUrl` 기준 확인
- `GET /api/v1/places/{placeId}` 장소 상세 최상위 응답에 `thumbnailUrl` 추가
- `GET /api/v1/posts/{postId}` 게시물 상세의 연관 장소 응답에 `thumbnailUrl` 추가
- `GET /api/v1/posts/{postId}/place-parsing` 장소 파싱 결과 응답에 `thumbnailUrl` 추가
- application view, persistence query, response DTO, 테스트 갱신

## 제외 범위

- Google 사진 재수집/재시도 정책 변경
- 장소 썸네일 수정 API
- 여러 장 사진 저장
- DB 스키마 변경

## 성공 기준

- 장소를 반환하는 공개 API에서 저장된 장소 썸네일 URL이 누락 없이 내려간다.
- 썸네일이 없는 장소는 `thumbnailUrl: null`로 내려간다.
- 기존 요청 파라미터와 필수 응답 필드는 변경하지 않는다.
- 관련 테스트와 `./gradlew check`가 통과한다.
