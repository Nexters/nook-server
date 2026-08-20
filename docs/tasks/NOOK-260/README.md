# NOOK-260 OCR evidence 기반 게시물 장소 사진 fallback

## 목적

외부 장소 사진 provider가 사진을 반환하지 못할 때, OCR 근거로 장소와 이미지의 대응이 확인된 경우에만
Instagram 게시물 이미지를 장소 썸네일로 사용한다.

## 범위

- 썸네일 provider 순서를 `APIFY_GOOGLE,GOOGLE,POST_MEDIA`로 변경
- 하나의 이미지에 하나의 장소 단서만 있고 상호명 또는 주소가 OCR evidence에 포함된 경우만 fallback 허용
- 본문 기반 순서 추정이나 한 이미지의 복수 장소에는 게시물 이미지 fallback 금지
- 원본 Instagram URL도 기존 스토리지 포트를 통해 저장한 뒤 썸네일로 사용

## 제외 범위

- OCR 근거가 불명확한 이미지의 임의 매칭
- live runtime 설정 변경
- 기존 실패 작업 자동 재시도

## 성공 기준

- 외부 provider가 모두 비어 있고 근거가 확인된 장소는 해당 게시물 이미지를 사용한다.
- 근거가 없거나 한 이미지에 여러 장소가 있으면 `POST_MEDIA`는 빈 결과를 반환한다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-application:test --tests '*PlaceSourceMediaSequenceTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*PostMediaPlaceThumbnailProviderTest'`
- `./gradlew check`
