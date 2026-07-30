# NOOK-99 지도 장소 응답에 장소명과 대표 그룹 색상 추가

## 목적

지도 화면에서 장소 마커에 장소명과 사용자가 저장한 그룹 색상을 표시할 수 있도록 지도 장소 조회
응답을 확장한다.

## 범위

- `GET /api/v1/places/map` 응답 항목에 장소명 `name` 추가
- 응답 항목에 대표 그룹 색상 `color` 추가
- 장소와 연결된 현재 사용자의 저장 게시물 중 가장 최근 저장 건의 그룹 색상 사용
- 가장 최근 저장 건이 여러 그룹에 속하면 가장 최근에 연결된 그룹 색상 사용
- 그룹 색상을 찾을 수 없는 예외 데이터는 `YELLOW`로 fallback
- application view, persistence query, response DTO, 계층별 테스트와 OpenAPI 계약 갱신

## 제외 범위

- 지도 조회 endpoint와 요청 파라미터 변경
- 그룹 CRUD 또는 게시물-그룹 연결 정책 변경
- DB 스키마 변경
- 클라이언트 지도 UI 구현

## API

기존 응답 필드를 유지하면서 `name`, `color`를 추가한다.

```json
{
  "id": 17,
  "name": "퍼머넌트해비탯",
  "latitude": 37.5,
  "longitude": 127.0,
  "color": "BLUE"
}
```

## 조회 정책

- 대표 색상은 현재 사용자의 저장 게시물만을 기준으로 선택한다.
- 장소와 연결된 저장 게시물을 `created_at DESC, id DESC`로 정렬해 가장 최근 건을 선택한다.
- 선택된 저장 게시물이 여러 그룹에 연결되어 있으면 `group_posts.created_at DESC, id DESC` 기준으로
  가장 최근 연결을 선택한다.
- 그룹 연결이나 색상을 찾을 수 없는 예외 데이터는 `YELLOW`를 반환한다.

## 성공 기준

- 기존 `id`, `latitude`, `longitude` 필드를 유지한다.
- 각 지도 장소 응답에 올바른 `name`, `color`가 포함된다.
- 다른 사용자의 저장 게시물이나 그룹 색상이 노출되지 않는다.
- 색상 조회가 불가능한 데이터는 `YELLOW`를 반환한다.
- `./gradlew check`가 통과한다.

## 검증

- application 지도 조회 계약 테스트
- infrastructure projection mapping 테스트
- controller 응답 계약 테스트
- `./gradlew check`
