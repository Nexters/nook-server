# NOOK-410 앱 연동 명세

## 실패 건 다시 저장

기존 저장 응답/내 저장 게시물 목록·상세의 `postId`를 사용한다.
이 값은 **사용자 저장 게시물 ID**이며 관리자/기존 푸시의 원본 게시물 ID와 혼용하지 않는다.
실패 푸시에서 곧바로 진입하려면 앱이 내 저장 목록의 해당 건으로 연결해야 한다.
푸시 진입점 UX와 앱 버튼 구현은 별도 앱 작업이다.

`POST /api/v1/posts/{postId}/recovery` — 인증 필요, 요청 본문 없음.

```json
{
  "success": {
    "postId": 11,
    "requested": true,
    "processingStatus": "PENDING",
    "processingStage": "CONTENT",
    "processingPercent": 5
  }
}
```

- 202: 신규 요청 또는 기존 처리 중. requested=true일 때 신규 접수 안내.
- requested=false와 PENDING/PROCESSING이면 이미 처리 중임을 안내.
- 200과 COMPLETED: 이미 저장 완료. 중복 실행 없음.
- 404 POST_NOT_FOUND: 다른 사용자/삭제 건/콘텐츠 작업 없음.
- 404 GROUP_NOT_FOUND: 연결된 활성 아카이브가 없어 복구 불가.
- 409 POST_PROCESSING_STATE_CONFLICT: 운영자가 처리 종료한 원본. 재접수 불가.

`GET /api/v1/posts/{postId}/recovery` — 동일 응답 구조. 조회에서는 requested=false.
processingStatus는 PENDING/PROCESSING/COMPLETED/FAILED.
처리 중 버튼 비활성화, FAILED이면 재요청 가능, COMPLETED이면 기존 상세로 이동한다.
기존 목록·상세 API의 processingStatus로도 다시 확인할 수 있다.
장소 인식 실패는 COMPLETED이며 장소 없이 저장된다.

완료 알림은 기존 POST_PROCESSING/COMPLETED 푸시를 재사용하며 사용자 알림 설정을 따른다.
새 푸시 필드는 추가하지 않는다.

## 추가 수동 확인

배포 후 앱에서 실패 저장 건의 복구 요청 → 기존 아카이브 유지 → 완료 푸시 수신을 확인한다.
진행 중 재요청은 한 번의 작업으로 유지되고, 삭제된 아카이브는 다시 생성되지 않아야 한다.
관리자에서는 각 단계 재시도 버튼과 장소 상세 사진·태그 재처리의 대상·진행·결과를 확인한다.
이 확인은 외부 provider 호출/브라우저가 필요하므로 자동 검증에서는 수행하지 않는다.
