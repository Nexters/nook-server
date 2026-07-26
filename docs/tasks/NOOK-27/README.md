# NOOK-27 컨트롤러별 HTTP Client 요청 파일 및 실행 환경 추가

## 목적

컨트롤러와 같은 패키지에 JetBrains HTTP Client 요청 파일을 두어 로컬과 배포 환경의 API를 빠르게
호출하고 검증할 수 있게 합니다.

## 범위

- 현재 모든 controller와 같은 패키지에 controller 파일명과 동일한 `.http` 파일 추가
- 각 controller의 공개 endpoint를 실행할 수 있는 요청 예제 작성
- 로그인 요청의 최상단 배치 규칙과 로그인 미구현 상태의 TODO 추가
- `local`, `dev`, `staging`, `live` HTTP Client 환경 정의
- controller 계약 변경 시 `.http` 파일을 함께 관리하도록 에이전트 지침과 API 정책 갱신

## 제외 범위

- 로그인 API 구현
- 기존 endpoint, request/response 또는 인증 계약 변경
- 서버 배포 환경 변경

## 설계

루트의 `http-client.env.json`에 환경별 `baseUrl`을 정의하고 모든 요청에서 `{{baseUrl}}`을
참조합니다.

| 환경 | baseUrl |
| --- | --- |
| `local` | `http://localhost:8080` |
| `dev` | `https://api-dev.everynook.co.kr` |
| `staging` | `https://api-staging.everynook.co.kr` |
| `live` | `https://api.everynook.co.kr` |

각 `.http` 파일의 첫 줄에는 로그인 요청을 추가할 위치를 표시하는 TODO 주석을 둡니다. 로그인 API가
구현되면 이 위치에 로그인 요청을 추가하고, 인증이 필요한 후속 요청이 로그인 결과를 사용하도록
갱신합니다. 로그인 구현 전에는 presentation 계층의 `UserContext`가 임시 사용자 식별자를 주입하므로
요청 예제에 별도 인증 헤더를 추가하지 않습니다.

## 성공 기준

- 현재 모든 controller에 대응하는 같은 이름의 `.http` 파일이 같은 패키지에 존재합니다.
- 모든 `.http` 파일 첫 줄에 로그인 TODO가 존재합니다.
- 모든 요청이 `http-client.env.json`의 `baseUrl`을 사용합니다.
- HTTP Client 환경에 `local`, `dev`, `staging`, `live`가 정의됩니다.
- controller 추가 및 계약 변경 시 `.http` 파일을 함께 관리하는 규칙이 저장소 지침에 명시됩니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew check
```

추가로 controller와 `.http` 파일의 이름 대응, 각 파일의 첫 줄, 환경 이름과 URL 및 요청의
`{{baseUrl}}` 사용 여부를 확인합니다.

## 배포 및 롤백

애플리케이션 코드와 서버 설정을 변경하지 않으므로 별도 배포 작업은 없습니다. 문제가 있으면 추가한
HTTP Client 파일과 환경 정의를 제거하고 지침 변경을 되돌립니다.
