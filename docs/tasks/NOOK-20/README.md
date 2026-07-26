# NOOK-20 Instagram 저장 흐름 Bright Data 실제 연동

## 목적

NOOK-18의 Instagram 게시물 저장 흐름에 NOOK-10의 Bright Data 스크래핑을 실제로 연결한다.

## 범위

- `InstagramPostProviderPort`와 Bright Data provider 연결
- 스크래핑한 작성자, 본문, 게시 시각, 미디어, 해시태그와 장소 태그 저장
- 기존 게시물 재사용과 장소 파싱 작업 등록 동작 유지
- provider 및 저장 통합 테스트

## 제외 범위

- 게시물 및 장소 메타데이터 신규 필드 추가
- DB 스키마 변경
- 카카오 장소 후보 검색 worker 및 장소 자동 저장
- OCR·LLM 장소 추론
- S3·CloudFront 미디어 저장
- Bright Data 비동기 snapshot polling

## 성공 기준

- 저장 API 호출 시 실제 Bright Data 결과로 게시물과 미디어가 저장된다.
- 장소 정보가 없는 게시물·릴스도 정상 저장된다.
- 기존 게시물 재사용과 장소 파싱 작업 `PENDING` 등록이 유지된다.
- 외부 API 호출이 DB 트랜잭션 밖에서 실행된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew check
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

NOOK-20은 데이터베이스 스키마를 변경하지 않는다.
