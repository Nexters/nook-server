# NOOK-22 Instagram 추출 결과를 저장 게시물 생성 흐름에 연동

## 목적

NOOK-10의 Bright Data 기반 Instagram 콘텐츠 추출 결과를 NOOK-18의 저장 게시물 생성 흐름에 연결해,
공유 URL 한 번으로 실제 게시물 메타데이터와 장소 파싱 작업이 저장되도록 합니다.

## 범위

- `SaveInstagramPostUseCase`와 `ExtractInstagramContentUseCase` 연결
- 본문, 작성자, 발행 시각, 미디어, 해시태그, 장소 표시값의 `Post` 매핑
- 중복 `InstagramPostProviderPort`와 URL-only adapter 제거
- 외부 provider와 미디어 처리 후 단일 persistence transaction 유지
- 기존 저장 및 polling API 계약 유지
- 공개 controller의 OpenAPI `Tag`, `Operation` 문서화 정책 반영
- application orchestration 및 OpenAPI 정책 테스트

## 제외 범위

- S3/CloudFront 실제 미디어 저장
- 장소 parsing worker
- LLM, OCR 기반 장소 추론
- Kakao 후보 자동 확정 및 `Place` 저장
- 인증 구현

## 처리 흐름

1. 저장 유스케이스가 Instagram URL과 사용자 식별자를 검증합니다.
2. `ExtractInstagramContentUseCase`가 URL을 정규화하고 Bright Data에서 콘텐츠를 추출합니다.
3. 저장에 필요한 해시태그와 장소 표시값을 `Post` 제약에 맞게 정규화합니다.
4. 미디어 저장 port를 호출해 영속화할 미디어 URL을 확보합니다.
5. 게시물, 사용자 저장 건, 장소 파싱 작업을 기존 단일 persistence transaction으로 저장합니다.

Bright Data와 미디어 저장 port 호출은 DB transaction 밖에서 실행합니다. 이번 작업의 media storage
adapter는 기존 pass-through 구현을 유지하며 실제 object storage 연동은 후속 이슈에서 다룹니다.

## 호환성

- `POST /api/v1/saved-posts` endpoint, request와 response 필드를 유지합니다.
- 성공 상태 코드 `201 Created`와 parsing status 의미를 유지합니다.
- 잘못된 URL은 기존 Saved Post API 오류 코드로 변환합니다.
- Bright Data의 not found, provider error와 timeout은 NOOK-10 오류 계약을 그대로 전달합니다.

## 성공 기준

- 저장 API가 Bright Data 정규화 결과를 persistence port에 전달합니다.
- 본문, 작성자, 발행 시각, 미디어, 해시태그와 장소 표시값이 저장 대상 `Post`에 반영됩니다.
- provider 오류가 기존 공통 오류 계약으로 전달됩니다.
- 외부 호출이 DB transaction 밖에서 실행됩니다.
- 기존 endpoint, 응답 필드와 상태 코드를 유지합니다.
- 모든 공개 controller가 OpenAPI `Tag`와 `Operation` summary를 가집니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

데이터베이스 스키마 변경은 없습니다.
