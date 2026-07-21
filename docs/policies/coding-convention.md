# 코딩 정책

- 패키지 루트는 `org.every.nook.api`를 사용합니다.
- Kotlin의 null 안정성을 유지하고 `!!` 사용을 피합니다.
- domain과 application에는 Spring Data, JPA, HTTP 타입을 노출하지 않습니다.
- Kotlin JDSL 쿼리는 infrastructure에 작성합니다.
- 코드 변경에는 해당 수준의 자동화 테스트를 함께 작성합니다.
