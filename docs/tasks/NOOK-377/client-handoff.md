# NOOK-377 프론트 연동

## 아카이브 응답 추가 필드

내 아카이브 `GET /api/v1/groups`, 소유 그룹 `/owned`, 공개 공유 정보
`GET /api/public/v1/groups/{token}` 응답에 다음 필드가 추가된다.

```json
{
  "subscriberCount": 5,
  "subscriberPreview": [
    {"nickname": "사용자1", "profileImageUrl": "https://cdn.example.com/profile.jpg"},
    {"nickname": "사용자2", "profileImageUrl": null}
  ]
}
```

미리보기는 최근 추가순 최대 3명이다. 0명이면 count=0, preview=[]이며
추가한 사람 수와 진입 버튼을 모두 숨긴다. 1명 이상이면 `N명이 추가함`과 명단 진입을 제공한다.
프로필 이미지 null은 기본 이미지로 표시한다. 닉네임·사진은 현재 회원 프로필을 사용한다.

## 추가한 사람 목록

- 화면 제목: `추가한 사람`.
- 로그인한 소유자/구독자: `GET /api/v1/groups/{groupId}/subscribers?page=0&size=20`.
- 공유 링크 열람자: `GET /api/public/v1/groups/{token}/subscribers?page=0&size=20`.
- page는 0부터, size는 기본 20·최대 100. hasNext=true이면 다음 page를 로드한다.

```json
{"success":{"items":[{"nickname":"사용자1","profileImageUrl":null}],
 "page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}}
```

닉네임·프로필 이미지만 읽기 전용으로 표시하며 사용자 선택 시 프로필로 이동하지 않는다.
사용자 제거·추가 취소 버튼을 제공하지 않는다. 목록과 미리보기의 최근 추가순은 같다.
인원수는 원본 소유자·단순 링크 방문자·독립 사본을 제외한 현재 참조형 구독자의 중복 없는 수다.

화면 재진입·당겨서 새로고침 시 summary와 목록을 갱신한다. 추가 관계가 해제돼 0명이 되면 진입 버튼도 숨긴다.
인원수 조회 후 관계가 바뀌어 목록이 비어도 정상적인 빈 상태로 처리한다.
권한 없는 그룹 ID는 기존 GROUP_NOT_FOUND(404), 잘못된 공유 토큰·만료·공유 해제는 기존 공유 오류 계약을 따른다.
서버 배포 후 OpenAPI 계약을 갱신해 연동한다.

## 앱에서 확인할 항목

1. 0명·1명·4명 이상에서 숨김·문구·3명 미리보기·추가 로드를 확인한다.
2. 소유자·구독자·비로그인 공유 링크 화면의 동일 아카이브 명단을 비교한다.
3. 프로필 이미지가 없는 사용자의 기본 이미지를 확인한다.
4. 구독 취소·회원 탈퇴·공유 해제 후 새로고침으로 인원과 목록이 갱신되는지 확인한다.
5. 잘못된/만료된 링크와 접근 불가 그룹의 기존 오류 화면을 확인한다.
