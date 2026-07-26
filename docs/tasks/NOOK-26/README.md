# NOOK-26 UserContext 기반 사용자 식별자 주입 구조 추가

## 목적

컨트롤러가 임시 사용자 헤더를 직접 받지 않고 `UserContext`에서 사용자 식별자를 얻도록 presentation
경계를 구성합니다. 로그인 구현 전에는 더미 사용자를 주입하고, 이후 JWT 인증이 추가되면 컨트롤러 계약을
변경하지 않고 argument resolver의 사용자 조회 방식만 교체할 수 있게 합니다.

## 범위

- presentation 계층의 `UserContext`
- Spring MVC `HandlerMethodArgumentResolver` 및 MVC 설정
- 인증 구현 전 더미 `userId = 1` 주입
- 게시물 API의 `X-Nook-User-Id` 요청 헤더 제거
- OpenAPI에서 내부 `UserContext` 파라미터 숨김
- argument resolver 및 MVC 테스트

## 제외 범위

- Spring Security 의존성과 보안 설정
- JWT 발급, 파싱 및 검증
- 로그인과 회원가입
- 사용자 영속 모델 변경

## API 계약

게시물 API는 더 이상 `X-Nook-User-Id` 요청 헤더를 받지 않습니다. 요청 본문, 응답 본문, endpoint,
HTTP method와 상태 코드는 유지합니다.

```http
POST /api/v1/posts
Content-Type: application/json

{
  "url": "https://www.instagram.com/p/ABC123/",
  "memo": "주말에 방문"
}
```

서버 내부에서는 argument resolver가 다음 컨텍스트를 컨트롤러에 전달합니다.

```kotlin
UserContext(userId = 1)
```

## 향후 인증 연동

로그인이 구현되면 인증 필터가 JWT를 검증해 인증 principal을 Spring Security `SecurityContext`에
저장합니다. `UserContextArgumentResolver`는 더미 사용자를 생성하는 대신 principal에서 사용자 식별자를
읽어 `UserContext`로 변환합니다. 컨트롤러와 application 유스케이스 입력은 변경하지 않습니다.

## 성공 기준

- `X-Nook-User-Id` 없이 게시물 API를 호출할 수 있습니다.
- 게시물 유스케이스에 더미 `userId = 1`이 전달됩니다.
- resolver는 `UserContext` 파라미터만 처리합니다.
- `UserContext`는 presentation 계층 밖으로 노출되지 않습니다.
- OpenAPI에 `UserContext`가 클라이언트 입력으로 노출되지 않습니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

스키마 변경은 없습니다.
