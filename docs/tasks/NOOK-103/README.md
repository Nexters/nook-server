# NOOK-103 Google 장소 썸네일 추출 로그 추가

## 목적

장소 썸네일이 일부 장소에만 저장되는 원인을 추적할 수 있도록 Google Places Photo 조회와 버킷 저장 흐름에 관측 가능한 로그를 추가한다.

## 범위

- Google place photo provider 활성/비활성 사유 로그 추가
- Google Text Search 요청 시작/완료 로그 추가
- 검색 결과 place/photo 존재 여부 로그 추가
- Photo media URL 조회 성공 여부 로그 추가
- 버킷 저장 성공 로그 추가
- 자동 장소 파싱과 수동 장소 연결에서 썸네일 URL 생성 여부 로그 추가
- DB 저장 시 place 썸네일 저장/스킵 사유 로그 추가

## 제외 범위

- Google 썸네일 추출 정책 변경
- 여러 장소의 썸네일을 모두 저장하도록 동작 변경
- 재시도 정책 변경
- DB 스키마 변경

## 성공 기준

- 새 포스트 생성 후 장소 파싱 로그만 보고 설정 누락, Google 사진 없음, Google API 실패, 버킷 저장 실패, DB 저장 스킵 여부를 구분할 수 있다.
- 기존 썸네일 저장 동작은 변경하지 않는다.
- `./gradlew check`가 통과한다.
