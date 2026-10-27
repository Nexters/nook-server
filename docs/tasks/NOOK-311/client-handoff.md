# 프런트 연동: 지도 아카이브 필터

## 요청

기존 두 endpoint에 같은 `groupIds`를 전달한다. 최근 공간 응답에 `totalCount`가 추가된다.

| 선택 | 쿼리 | 의미 |
| --- | --- | --- |
| 기본·초기화 | `groupIds` 생략 | 기존 전체 조회. 아카이브 미분류 저장 장소도 포함 |
| 일부 선택 | `groupIds=1,2` | 선택한 아카이브 중 하나 이상에 속한 장소 |
| 전체 해제 | `groupIds=` | 빈 결과 |

```http
GET /api/v1/places/map?northLatitude=37.6&westLongitude=126.8&southLatitude=37.4&eastLongitude=127.2&groupIds=1,2
GET /api/v1/places/recent?size=20&groupIds=1,2
GET /api/v1/places/recent?size=20&groupIds=
```

ID는 양의 정수다. 중복 ID는 결과를 중복시키지 않는다. 잘못된 형식과 0·음수 ID는
`400 / INVALID_REQUEST`다. 접근할 수 없거나 삭제된 ID는 무시하며 유효한 선택 결과는 반환한다.
미지정과 빈 값은 다르므로 전체 해제를 보낼 때 query serializer가 빈 파라미터를 생략하지 않도록 한다.
생성 클라이언트가 빈 배열을 생략한다면, 전체 해제 시 요청하지 않고 화면에서 0곳·빈 목록으로 처리한다.

## 전체 장소 수 (2026-10-27 추가)

`GET /api/v1/places/recent`의 `success.totalCount`를 바텀시트의 N곳 표시에 사용한다.

- `totalCount`는 정수(Long)이며 모든 페이지에 제공한다. `size`, `cursor`, 지도 영역과 무관하다.
- `groupIds`가 있으면 같은 내·공유 아카이브 OR 조건으로 집계한다. 중복 장소는 한 번만 센다.
- `groupIds` 생략 시 미분류를 포함한 접근 가능한 전체 북마크 장소 수다.
- `groupIds=` 또는 접근 가능한 장소가 없는 선택은 `totalCount: 0`이다.
- 다음 페이지가 비어 있어도 조건에 맞는 전체 수를 반환한다. `items.length`와 다를 수 있다.
- 삭제·공유 해제·만료·구독 해제 여부를 매 요청 확인한다. 조회 사이 데이터가 변경되면 수가 달라질 수 있다.

예시 응답(필터에 해당하는 장소 42개, 마지막 커서 이후 요청):

```json
{
  "resultType": "SUCCESS",
  "success": {
    "items": [],
    "nextCursor": null,
    "hasNext": false,
    "totalCount": 42
  }
}
```

위 예시는 마지막 커서 이후 빈 페이지이며, 앞선 페이지에서도 같은 `totalCount`를 제공한다.
필터 변경 시 목록·cursor를 초기화하고 새 응답의 `totalCount`로 교체한다.

## 화면 연결

- 기존 `GET /api/v1/groups`의 `id`, `accessType`으로 내·공유 아카이브를 구분해 선택한다.
- `완료` 시 지도와 최근 공간 목록을 같은 선택으로 다시 조회한다.
- 선택 변경 시 최근 공간 목록과 cursor를 초기화한다. 다음 페이지는 같은 선택과 반환된 cursor를 사용한다.
- 초기화·지도 재진입 시 `groupIds`를 생략해 전체 기본 조회로 돌아간다.
- 세션 내 선택과 그룹별 선택 수·전체 선택/해제는 클라이언트에서 관리한다.
- 아카이브 삭제·공유 해제·구독 해제 후 목록과 장소를 다시 조회하고 유효하지 않은 선택을 제거한다.
  서버는 매 요청 유효성을 확인한다. 열려 있는 화면의 자동 갱신에는 클라이언트 재조회가 필요하다.
- 기존 목록 API의 정렬은 이번 작업에서 변경하지 않는다. 공유 아카이브의 `lastSavedAt`은 기존 계약상 null이다.
- 장소 핀 색상과 OWNED/SHARED 판단은 기존 정책을 유지한다. 본인 저장 장소와 겹치면 OWNED가 우선한다.

## 실제 MySQL 추가 확인

개발 서버에서 위 요청을 내·공유 아카이브 ID로 실행한다. 합집합·중복 제거·전체 해제 결과와
공유 해제 후 재조회 시 제외되는지 확인한다. 이번 로컬 검증은 H2와 단위 테스트를 사용한다.
