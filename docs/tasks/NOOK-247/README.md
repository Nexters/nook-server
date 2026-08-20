# NOOK-247 Instagram 영상 썸네일 저장 및 미디어 응답 확장

## 목적

Instagram 영상 게시물은 목록에서 정적 포스터 이미지를 표시하고, 상세에서는 원본 영상을 재생할 수 있도록
영상 URL과 썸네일 URL을 함께 보존하고 제공합니다.

## 범위

- Apify Instagram Scraper의 `displayUrl`과 Bright Data의 `thumbnail`을 영상 썸네일로 매핑합니다.
- 영상과 썸네일을 Nook 미디어 스토리지에 각각 저장합니다.
- `post_media.thumbnail_url`에 저장된 영상 썸네일 URL을 보존합니다.
- 상세 미디어 응답의 `type`, `url`, `sequence`를 유지하고 nullable `thumbnailUrl`을 추가합니다.
- 목록의 `representativeMedia.url`은 영상 썸네일이 있으면 썸네일 URL을, 없으면 기존 미디어 URL을 반환합니다.
- 일반 저장 게시물 목록, 그룹 게시물 목록과 게시물 상세에 같은 미디어 계약을 적용합니다.

## 제외 범위

- 서버에서 영상 첫 프레임 추출
- 기존 저장 영상 전체 재파싱 또는 백필
- 기존 API 필드 제거 및 의미 변경
- nook-client 영상 플레이어와 목록 렌더링 구현

## API 호환성

기존 `SavedPostMediaResponse` 필드는 제거하지 않습니다. 신규 `thumbnailUrl`은 nullable 응답 필드이며,
기존 데이터나 provider가 썸네일을 제공하지 않으면 `null`입니다. 상세 `media.url`은 원본 미디어 URL을
그대로 유지합니다. 목록 `representativeMedia.url`은 이미지 렌더링을 위한 대표 URL이므로 영상 썸네일이
있으면 썸네일 URL을 반환하고, 없으면 기존 미디어 URL로 fallback합니다. `type`은 원본 미디어 타입을
유지하므로 목록에서도 영상 여부를 구분할 수 있습니다.

```json
{
  "type": "VIDEO",
  "url": "https://media.example.com/video.mp4",
  "sequence": 0,
  "thumbnailUrl": "https://media.example.com/poster.jpg"
}
```

기존 클라이언트는 목록에서 계속 `representativeMedia.url`을 이미지로 사용할 수 있습니다. 상세에서는
`type`에 따라 `url`을 이미지 또는 영상으로 표시합니다.

## 검증

- Apify 단일 영상과 캐러셀 영상의 썸네일 매핑
- Bright Data 릴스 썸네일 매핑
- 영상과 썸네일의 스토리지 저장 및 조건부 DB 갱신
- 목록과 상세 응답의 nullable `thumbnailUrl`
- 기존 썸네일 없는 미디어 회귀 테스트
- `./gradlew check`

## DDL 적용 이력

- dev: 2026-08-20 21:39 KST, Codex 적용, MySQL 8.4.11 및 `thumbnail_url` 정의 확인
- live: 2026-08-20 21:41 KST, Codex 적용, MySQL 8.4.7 및 `thumbnail_url` 정의 확인
