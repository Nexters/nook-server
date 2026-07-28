# NOOK-62 JWT 사용자 컨텍스트 연결 및 인증 응답 통일

## 목적

보호 API가 access token의 회원 식별자를 실제 사용자 컨텍스트로 사용하도록 연결하고, 인증·회원 API의
성공 및 실패 응답을 프로젝트 공통 API 계약으로 통일합니다.

## 범위

- JWT `sub`의 회원 ID를 `UserContext.userId`로 변환
- 잘못된 access token subject 거부
- 소셜 로그인, 회원가입, 토큰 재발급 성공 응답을 `ApiResponse`로 통일
- 인증·회원 오류를 `NookException`과 공통 전역 예외 처리기로 통합
- Spring Security 401·403 응답을 공통 실패 응답으로 통일
- 인증·회원 API의 OpenAPI 문서 보완
- Swagger UI의 Bearer JWT 인증 입력 지원
- 컨트롤러, 사용자 컨텍스트 및 보안 응답 테스트

## 제외 범위

- 카카오·Google·Apple provider 검증 방식 변경
- access·refresh·signup token의 서명 방식과 만료 시간 변경
- 로그아웃 및 강제 토큰 폐기 API
- 모바일 앱의 토큰 저장과 재발급 처리
- DB 스키마 변경

## API 응답

성공 응답은 다음 envelope를 사용합니다.

```json
{
  "resultType": "SUCCESS",
  "success": {
    "accessToken": "..."
  }
}
```

인증 실패를 포함한 모든 실패 응답은 다음 envelope를 사용합니다.

```json
{
  "resultType": "FAIL",
  "error": {
    "errorCode": "INVALID_ACCESS_TOKEN",
    "reason": "인증 정보가 유효하지 않습니다.",
    "data": null
  }
}
```

## 성공 기준

- 서로 다른 JWT `sub`가 서로 다른 `UserContext.userId`로 전달됩니다.
- 인증되지 않은 보호 API 요청은 공통 `INVALID_ACCESS_TOKEN` 응답과 HTTP 401을 반환합니다.
- 인증·회원 성공 응답은 모두 `resultType`과 `success`를 포함합니다.
- 인증·회원 업무 오류와 요청 오류는 모두 `resultType`과 `error`를 포함합니다.
- 전역 `RestControllerAdvice`가 하나만 존재합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## 개발 테스트 데이터

- 적용: `dml/up.sql`
- 이름 변경: `dml/update-member-nicknames.sql`
- 롤백: `dml/rollback.sql`
- 대상: ID 1부터 7까지의 개발 테스트 회원
- 최종 닉네임: `김윤영`, `배서영`, `박찬형`, `권기준`, `백도현`, `문지우`, `김태임`
- 소셜 계정과 refresh token은 생성하지 않습니다.

## 배포 및 롤백

DB와 외부 provider 설정 변경은 없습니다. 애플리케이션 배포로 적용하며 문제가 있으면 이전 버전으로
롤백합니다. 인증·회원 성공 및 실패 응답 envelope가 변경되므로 모바일 앱은 이 버전과 함께 공통 응답
형식으로 전환해야 합니다. 개발 테스트 회원은 운영 배포 대상이 아니며, 제거할 때
`dml/rollback.sql`을 사용합니다.
