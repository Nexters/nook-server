# NOOK-410 앱 연동 명세

## 실패 건 다시 저장

기존 저장 응답/내 저장 게시물 목록·상세의 `postId`를 사용한다.
이 값은 **사용자 저장 게시물 ID**이며 관리자/기존 푸시의 원본 게시물 ID와 혼용하지 않는다.
새 푸시의 `savedPostId`는 이 사용자 저장 게시물 ID다. 푸시 진입과 앱 버튼은 아래 계약으로 연결한다.

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
기존 `postId`를 유지하고 수신자별 `savedPostId`를 추가한다. 앱의 새 필드 사용은 별도 앱 반영이 필요하다.

## 푸시에서 내 게시물로 진입 — 확정 계약

`POST_PROCESSING`의 완료·실패 및 복구 완료 알림에 동일하게 적용한다.
모든 data 값은 문자열이며 아래 두 ID를 같은 값으로 간주하지 않는다.

```json
{
  "type": "POST_PROCESSING",
  "outcome": "COMPLETED",
  "postId": "101",
  "savedPostId": "710"
}
```

- `postId`: 기존 원본 게시물 ID. 호환성을 위해 의미와 값을 유지하며 앱 상세 경로에 넣지 않는다.
- `savedPostId`: 푸시 수신자의 사용자 저장 게시물 ID. 예시에서는 `/post/710`으로 진입하고
  복구 요청·조회에도 710을 사용한다.
- 같은 원본을 다른 사용자가 저장하면 해당 사용자의 ID를 보낸다. 같은 사용자의 여러 기기는
  같은 저장 ID를 받는다. 푸시 전송은 사용자별로 분리한다.
- 저장 건이 삭제됐거나 본인 소유의 활성 아카이브 연결이 없거나 토큰이 비활성이면 전송하지 않는다.
  기존 알림 수신 설정도 유지한다.

`apps/web/src/app/native-push.tsx`는 `data.postId` 대신 양의 정수 문자열인 `data.savedPostId`로
내 상세를 연다. 문자열 ID를 그대로 사용해 불필요한 숫자 변환을 피한다.
앱이 이 변경을 반영해야 알림을 눌렀을 때 정확한 내 저장 건으로 이동한다.
구형 알림처럼 새 필드가 없거나 잘못됐으면 원본 ID로 상세를 열지 말고 기존 내 게시물 목록으로 이동한다.
필드를 받은 뒤 삭제되었거나 로그인 계정이 바뀌어 상세 조회가 404이면 목록으로 돌아가 안내한다.
푸시가 소유권 검증을 대체하지 않으며 서버의 기존 인증·소유권 검증을 그대로 사용한다.

복구 버튼명은 **다시 시도**, 신규 접수 성공 토스트는
**다시 저장을 요청했어요. 완료되면 알려드릴게요.**를 사용한다.
조회 네트워크 오류의 단순 재조회와 수집 실패 건의 복구 요청은 구분한다.

## 추가 수동 확인

배포 후 앱에서 실패 저장 건의 복구 요청 → 기존 아카이브 유지 → 완료 푸시 수신을 확인한다.
진행 중 재요청은 한 번의 작업으로 유지되고, 삭제된 아카이브는 다시 생성되지 않아야 한다.
같은 원본을 두 계정으로 저장한 뒤 각 기기의 알림이 각자의 저장 상세로 열리는지 확인한다.
구형 알림·알림 후 삭제·계정 전환은 다른 사용자의 상세로 진입하지 않고 목록으로 돌아가야 한다.
관리자에서는 각 단계 재시도 버튼과 장소 상세 사진·태그 재처리의 대상·진행·결과를 확인한다.
이 확인은 외부 provider 호출/브라우저가 필요하므로 자동 검증에서는 수행하지 않는다.
