# NOOK-236 장소 목록 썸네일 상태 노출 및 클라이언트 폴링 적용

## 목적

비동기로 생성되는 장소 썸네일 상태를 장소 목록 API에서도 확인할 수 있게 하고,
클라이언트가 처리 중인 장소만 주기적으로 갱신한다.

## 범위

- 아카이브 그룹 장소, 지도 장소, 최근 장소 응답에 `thumbnailParsingStatus` 추가
- 아카이브 그룹 장소와 최근 장소의 게시글 이미지 fallback 제거
- 실제 장소 썸네일이 있으면 상태를 `COMPLETED`로 보정
- 저장 상태가 없는 레거시 데이터는 `PENDING`으로 보정
- 클라이언트에서 `PENDING`, `PROCESSING` 장소가 있을 때 3초 간격 폴링
- `COMPLETED`, `FAILED`만 남으면 폴링 종료

## 제외 범위

- 장소 검색 결과 API의 폴링
- 썸네일 provider 또는 파싱 작업 실행 방식 변경
- DB 스키마 및 데이터 변경

## 검증

- 세 장소 목록 응답의 `thumbnailParsingStatus`
- 실제 장소 썸네일 URL과 상태의 정합성
- 처리 중/종료 상태별 클라이언트 폴링 판정
- 서버 `./gradlew check`
- 클라이언트 `pnpm check`
