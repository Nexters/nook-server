# ADR-0002 API 응답 및 오류 처리

- 상태: 승인
- 일자: 2026-07-22

## 배경

API 클라이언트가 성공과 실패를 일관된 구조로 처리하고, 도메인 및 application 오류를 HTTP와 분리해
표현할 수 있는 공통 계약이 필요합니다. 예상하지 못한 서버 오류의 내부 정보를 클라이언트에 노출하지
않는 규칙도 필요합니다.

## 결정

본문이 있는 API 응답은 `ApiResponse<T>` envelope를 사용합니다.

성공 응답은 다음 형식입니다.

```json
{
  "resultType": "SUCCESS",
  "success": {
    "id": 1
  }
}
```

실패 응답은 다음 형식입니다.

```json
{
  "resultType": "FAIL",
  "error": {
    "errorCode": "EXAMPLE",
    "reason": "example.",
    "data": null
  }
}
```

- `success`와 `error`는 동시에 노출하지 않습니다.
- `error.data`는 값이 없어도 `null`로 노출합니다.
- 204 응답에는 envelope를 사용하지 않습니다.
- 실패 응답도 실제 HTTP 4xx 또는 5xx 상태를 유지합니다.

예상 가능한 유스케이스 오류는 application의 `NookException`으로 표현합니다. application은
`NookErrorCode`와 HTTP 독립적인 `ErrorType`만 알고, presentation이 `ErrorType`을 HTTP 상태로
변환합니다. domain의 불변식 실패는 application 유스케이스 경계에서 적절한 `NookException`으로
변환합니다.

예상하지 못한 예외는 서버에 원문과 스택을 기록하고 클라이언트에는 고정된
`INTERNAL_SERVER_ERROR` 코드와 공개 가능한 문구만 응답합니다.

## 결과

클라이언트는 `resultType`으로 응답 종류를 판별하고 안정적인 `errorCode`로 분기할 수 있습니다.
domain과 application은 Spring 및 HTTP에 의존하지 않습니다. 모든 본문 응답에 envelope를 적용해야
하므로 endpoint 구현 시 공통 계약을 준수해야 합니다.
