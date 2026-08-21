# NOOK-268 운영 어드민 CORS 허용 origin 누락 수정

## 목적

운영 Admin Web에서 Admin API 수정 요청이 CORS 403으로 실패하는 문제를 해결한다.

## 범위

- dev/live Admin Web origin을 API CORS 허용 목록에 추가
- Admin Web의 JSON PUT preflight 회귀 테스트 추가

## 제외 범위

- Cloudflare Access 정책 변경
- 사용자별 관리자 역할 도입
- Admin API 계약 변경

## 성공 기준

- `https://dev-admin.everynook.co.kr`와 `https://admin.everynook.co.kr`의 PUT preflight가 허용된다.
- 응답에 요청 origin과 허용 HTTP method가 포함된다.
- 기존 CORS 허용 및 차단 정책이 유지된다.

## 검증

```shell
./gradlew check
```
