# NOOK-18 Instagram 게시물 저장 및 장소 파싱 polling API 구현

## 목적

Instagram URL 공유 요청으로 공용 게시물과 사용자 저장 건을 생성하고, 장소 파싱 작업을 원자적으로
등록한 뒤 클라이언트가 처리 상태를 polling할 수 있는 첫 수직 슬라이스를 구현합니다.

NOOK-17에서 정리한 동기·비동기 경계와 공개 parsing status를 구현 기준으로 사용합니다.

## API 계약

### Instagram 게시물 저장

```http
POST /api/v1/saved-posts
X-Nook-User-Id: 1
Content-Type: application/json

{
  "instagramUrl": "https://www.instagram.com/p/ABC123/"
}
```

성공 시 `201 Created`와 다음 응답을 반환합니다.

```json
{
  "resultType": "SUCCESS",
  "success": {
    "savedPostId": 1,
    "postId": 1,
    "placeParsingStatus": "PENDING"
  }
}
```

인증 구현 전까지 `X-Nook-User-Id`를 임시 사용자 경계로 사용합니다. 인증 도입 시 헤더 값을 인증
principal에서 얻도록 교체하되 endpoint와 응답 계약은 유지합니다.

### 장소 파싱 상태 조회

```http
GET /api/v1/saved-posts/{savedPostId}/place-parsing
X-Nook-User-Id: 1
```

`PENDING`, `PROCESSING`, `FAILED`에서는 상태와 공개 가능한 실패 사유를 반환합니다. `COMPLETED`에서는
게시물에 연결된 장소를 노출 순서대로 함께 반환합니다. 다른 사용자의 저장 건도 존재하지 않는 것과 동일한
`404 Not Found`로 처리합니다.

## 범위

- 공유 저장 및 장소 파싱 polling API
- 공개 상태 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
- provider 장소 태그와 해시태그 저장 모델
- DB 기반 장소 파싱 작업 모델
- application use case와 persistence/provider/media storage port
- Instagram 게시물·릴스 URL 식별자 추출 adapter
- 실제 외부 연동 없이 URL 메타데이터만 사용하는 실행 가능한 수직 흐름
- 도메인, application, persistence metadata, MVC 테스트

## 제외 범위

- Bright Data 실제 연동
- S3/CloudFront 실제 연동
- LLM, OCR, Kakao Map 실제 연동
- worker 실행 및 재시도 scheduler
- 인증 구현
- 저장 게시물 목록 및 그룹 API

## 설계

- provider와 media storage 호출은 DB 트랜잭션 밖에서 실행합니다.
- 게시물, 미디어, 해시태그, 사용자 저장 건, 장소 파싱 작업 저장은 하나의 persistence port 호출과
  트랜잭션으로 처리합니다.
- `(source_type, external_post_id)`가 같은 공용 게시물은 재사용합니다.
- 사용자 저장 건은 재공유할 때마다 독립적으로 생성합니다.
- parsing status는 공용 게시물 단위로 공유하고 `place_parsing_jobs.post_id`를 유일하게 유지합니다.
- 공개 상태와 worker의 세부 실행·재시도 상태는 분리합니다.

## 성공 기준

- 새 Instagram URL을 저장하면 `postId`, `savedPostId`, `PENDING`을 반환합니다.
- 같은 Instagram 게시물은 공용 게시물을 재사용하고 사용자 저장 건은 독립적으로 생성합니다.
- 게시물 저장, 사용자 저장, parsing job 등록이 하나의 트랜잭션으로 처리됩니다.
- polling은 `savedPostId`와 사용자 식별자로 소유 경계를 확인합니다.
- `COMPLETED` 상태에서 장소 정보를 반환합니다.
- application과 domain 경계에 Spring, JPA, HTTP 타입이 노출되지 않습니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

NOOK-7 스키마가 먼저 적용되어 있어야 합니다. staging과 live 적용 전에 기존 `posts` 데이터의 상태와
DDL 잠금 영향을 확인합니다.

### 적용 기록

- dev: 2026-07-26 적용, MySQL 8.4.10 구조 확인 및 신규 boot JAR의 Hibernate validation 성공
- staging: 미적용
- live: 미적용
