# ADR-0002 API 오류 응답

## 상태

Accepted

## 결정

API 오류는 HTTP 상태 코드와 함께 `code`, `message` 필드를 가진 JSON 객체로 반환합니다.
필드 검증 오류는 선택적인 `fieldErrors` 배열에 필드명과 사유를 담습니다. 클라이언트는 표시 문구가
아닌 안정적인 `code`를 기준으로 분기합니다. 인증 실패는 원인을 과도하게 노출하지 않습니다.

```json
{
  "code": "DUPLICATE_NICKNAME",
  "message": "이미 사용 중인 닉네임입니다.",
  "fieldErrors": []
}
```

## 결과

첫 API부터 동일한 오류 형태를 사용하며 이후 endpoint도 이 계약을 유지합니다. 새 오류 코드는 추가할
수 있지만 기존 코드의 의미를 바꾸거나 제거하지 않습니다.
