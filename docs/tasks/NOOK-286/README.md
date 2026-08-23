# NOOK-286 장소 파싱 실패 UX 및 대표 이미지 처리 개선

## 목적

게시물 콘텐츠가 정상 저장된 경우 장소 파싱 실패를 사용자에게 안전하게 안내하고, 게시물 대표 이미지와
성공 푸시를 일관되게 제공한다.

## 범위

- 사용자 API의 장소 파싱 실패 사유를 안정적인 한국어 문구로 매핑한다.
- 원본 실패 사유는 DB, 로그와 어드민 진단에 유지한다.
- 그룹 목록은 이미지의 `media_url` 또는 영상의 `thumbnail_url`을 게시물 대표 이미지로 사용한다.
- 게시물 대표 이미지가 없으면 기존 장소 썸네일 fallback을 유지한다.
- 콘텐츠 파싱 성공 후 장소 파싱이 최종 실패해도 저장 성공 푸시를 발송한다.
- 콘텐츠 파싱 실패는 기존 저장 실패 푸시를 유지한다.

## 제외 범위

- `nook-client` 변경
- 장소 파싱 정확도와 재시도 정책 변경
- API 필드 또는 DB 스키마 변경

## 성공 기준

- 사용자 응답에 내부 영문 장소 파싱 실패 사유가 노출되지 않는다.
- 장소가 없는 이미지와 영상 게시물도 그룹 카드에 대표 이미지가 표시된다.
- 콘텐츠 성공과 장소 실패 조합은 성공 푸시를 발송한다.
- 콘텐츠 파싱 실패는 계속 실패 푸시를 발송한다.

## 검증

- `./gradlew :nook-api-application:test --tests 'org.every.nook.api.application.post.FindPostPlaceParsingUseCaseTest'`
- `./gradlew :nook-api-infrastructure:test --tests 'org.every.nook.api.infrastructure.persistence.group.GroupJpaRepositoryQueryTest'`
- `./gradlew :nook-api-infrastructure:test --tests 'org.every.nook.api.infrastructure.persistence.save.SavedPostQueryPersistenceAdapterTest'`
- `./gradlew :nook-api-presentation:test --tests 'org.every.nook.api.place.PlaceParsingEventListenerTest'`
- `./gradlew check`
