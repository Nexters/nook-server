# NOOK-193 Google 장소 사진 및 Cloud Vision 임시 우회

## 목적

Google 장소 사진과 Cloud Vision 사용을 임시 중단하면서 장소 썸네일과 이미지 텍스트 추출 흐름은 유지합니다.

## 범위

- 장소 썸네일 provider를 설정으로 선택하고 기본값을 고정 버킷 이미지로 둡니다.
- 이미지 텍스트 추출 provider를 설정으로 선택하고 기본값을 OpenAI로 둡니다.
- 로컬 및 개발 서버에서 Google Maps·Cloud Vision API 키를 제거합니다.
- 개발 서버를 재기동하고 선택된 provider와 서버 상태를 검증합니다.
- 기존 Google 연동 코드는 추후 재활성화를 위해 유지합니다.

## 제외 범위

- Google 연동 코드와 테스트의 영구 삭제
- 기존 장소 썸네일 데이터 일괄 변경
- 장소·게시물 API 계약 및 DB 스키마 변경

## 성공 기준

- 새 장소의 썸네일에는 지정한 버킷 이미지 URL 하나가 저장됩니다.
- 장소 썸네일 처리 중 Google Places API를 호출하지 않습니다.
- 이미지 텍스트 추출에는 OpenAI가 사용되고 Cloud Vision을 호출하지 않습니다.
- Google API 키 없이 애플리케이션이 정상 기동합니다.
- `PLACE_THUMBNAIL_PROVIDER=GOOGLE`, `GOOGLE_PLACE_PHOTO_ENABLED=true`, Google API 키 설정으로 기존 장소 사진 연동을 복구할 수 있습니다.
- `PLACE_PARSING_IMAGE_TEXT_PROVIDER=GOOGLE_CLOUD_VISION`과 Google API 키 설정으로 기존 Cloud Vision 연동을 복구할 수 있습니다.
- `./gradlew check`가 통과합니다.
