# NOOK-9 API 오류 응답 및 예외 처리 컨벤션 구성

## 목적

API 성공·실패 응답 envelope와 `NookException` 기반 예외 처리 규칙을 확정하고 공통 구현을 제공합니다.

## 범위

- API 응답 및 오류 처리 ADR
- `NookException`, `NookErrorCode`, `ErrorType`
- `ApiResponse<T>`, `ApiError`, `ResultType`
- domain 오류 유형과 HTTP 상태 매핑
- 요청 본문 validation 오류의 필드별 응답
- 예상하지 못한 예외의 안전한 500 응답
- 전역 `RestControllerAdvice`
- 직렬화 및 MVC 예외 처리 테스트
- API 정책 문서 갱신

## 제외 범위

- 실제 업무 endpoint
- 인증 및 인가 구현
- 다국어 오류 메시지
- 외부 provider 오류 코드 정의
- 모니터링 서비스 및 trace id 연동

## 설계

application의 `NookException`은 `NookErrorCode`, 공개 가능한 사유와 선택적인 오류 데이터만 가집니다.
`NookErrorCode`는 안정적인 코드, 기본 사유와 HTTP 독립적인 `ErrorType`을 제공합니다.

domain의 불변식 실패는 application 유스케이스가 업무 의미를 가진 `NookException`으로 변환합니다.

presentation의 전역 예외 처리기는 `ErrorType`을 HTTP 상태로 변환하고 `ApiResponse.fail`을 생성합니다.
입력값 오류는 `INVALID_REQUEST`, 예상하지 못한 오류는 `INTERNAL_SERVER_ERROR`로 응답합니다.

`ApiResponse` 생성자는 외부에 공개하지 않고 성공·실패 팩토리만 제공해 `success`와 `error` 중 하나만
존재하도록 보장합니다.

## 성공 기준

- 성공 응답에는 `success`만, 실패 응답에는 `error`만 존재합니다.
- `error.data`는 값이 없을 때도 `null`로 직렬화됩니다.
- `NookException`의 `ErrorType`이 올바른 HTTP 상태로 변환됩니다.
- validation 오류가 필드별 `violations`를 제공합니다.
- 예상하지 못한 예외의 내부 메시지가 응답에 노출되지 않습니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew clean test
./gradlew check
```

## 배포 및 롤백

DB와 외부 시스템 변경은 없습니다. 애플리케이션 배포로 적용하며 문제가 있으면 이전 애플리케이션
버전으로 롤백합니다.
