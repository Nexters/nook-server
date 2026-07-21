# 테스트 정책

- domain과 application은 빠른 단위 테스트를 우선합니다.
- persistence 통합 테스트는 Testcontainers MySQL을 사용합니다.
- API 경계는 MVC slice 테스트를 사용합니다.
- 버그 수정에는 실패를 재현하는 회귀 테스트를 추가합니다.
- PR 전 `./gradlew clean test`를 통과해야 합니다.
