# NOOK-67 게시물 생성 시 소유 그룹 필수 검증

## 목적

URL로 게시물을 생성할 때 현재 사용자가 소유한 그룹을 최소 1개 반드시 지정하도록 보장하고,
유효하지 않은 그룹 요청이 외부 provider 호출과 게시물 저장으로 이어지지 않도록 합니다.

## 범위

- `POST /api/v1/posts`의 `groupIds`를 필수·최소 1개로 변경
- 누락, `null`, 빈 배열을 `INVALID_REQUEST`로 거부
- 존재하지 않거나 현재 사용자 소유가 아닌 그룹을 `GROUP_NOT_FOUND`로 거부
- 그룹 소유 검증을 Bright Data, LLM, 미디어 저장 호출보다 먼저 실행
- 모든 그룹이 유효한 경우에만 게시물 추출과 저장 진행
- application과 영속성 경계에서 그룹 필수 조건을 함께 방어
- 요청, 유스케이스, 영속성 및 HTTP 계약 테스트

## 제외 범위

- 그룹 CRUD 계약 변경
- 게시물 생성 이후 그룹 재지정 API 변경
- 기본 그룹 자동 생성
- 외부 provider 추출 및 장소 파싱 로직 변경
- DB 스키마 변경

## API 계약

게시물 생성 요청에는 하나 이상의 그룹 ID가 반드시 포함되어야 합니다.

```json
{
  "url": "https://www.instagram.com/p/ABC123/",
  "memo": "주말에 방문",
  "groupIds": [1, 2]
}
```

- `groupIds` 누락, `null`, 빈 배열: HTTP 400 `INVALID_REQUEST`
- 0 이하의 그룹 ID: HTTP 400 `INVALID_REQUEST`
- 존재하지 않거나 다른 사용자 소유 그룹: HTTP 404 `GROUP_NOT_FOUND`
- 중복 그룹 ID는 한 번만 연결

## 성공 기준

- 본인 소유의 유효한 그룹 ID가 최소 1개 없으면 게시물이 생성되지 않습니다.
- 그룹 없는 요청은 외부 provider를 호출하지 않습니다.
- 접근할 수 없는 그룹 요청도 외부 provider를 호출하지 않습니다.
- 여러 그룹이 모두 본인 소유일 때만 게시물이 각 그룹에 연결됩니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

DB 스키마 변경은 없습니다.

## 배포 및 롤백

요청 필드의 필수 여부가 변경되므로 클라이언트는 배포 전에 게시물 생성 요청에 하나 이상의
`groupIds`를 항상 전달해야 합니다. 문제가 있으면 애플리케이션을 이전 버전으로 롤백합니다.
