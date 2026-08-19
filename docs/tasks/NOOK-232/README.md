# NOOK-232 Apify Google 장소 사진 매칭 및 썸네일 상태 정합성 개선

## 목적

Google Maps의 영문·한글 장소명 차이에도 주소와 좌표를 근거로 사진을 연결하고,
실제 장소 사진이 없는 상태를 게시글 이미지로 감추지 않는다.

## 범위

- Google Place ID 우선, 주소 일치 또는 300m 이내 좌표 기반 Actor 결과 매칭
- 이름 일치는 후보 우선순위를 정하는 보조 기준으로 사용
- Apify 결과의 Google Place ID 저장
- provider-chain 전체 사진 0건은 `FAILED`로 종료
- 자동 장소 연결 시 게시글 이미지를 장소 썸네일로 복사하지 않음
- 게시글 상세과 장소 파싱 응답에서 실제 장소 썸네일만 반환
- 기존 source media 복사 데이터 정리 DML

## 제외 범위

- 장소 검색 provider와 장소 선택 로직 변경
- 클라이언트 폴링 변경
- 기존 게시물 장소 재파싱

## 검증

- 한글 저장명과 영문 Actor 결과의 좌표 기반 매칭
- Google Place ID 저장
- 전체 빈 결과의 `FAILED` 상태
- saved-post source image 무시 및 실제 장소 썸네일 우선
- `./gradlew check`

## 운영 DML

- 적용: `ddl/up.sql`
- 롤백: 데이터 출처를 완전히 복원할 수 없어 제공하지 않음
- 스키마 변경 없음
