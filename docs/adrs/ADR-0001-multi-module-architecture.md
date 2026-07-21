# ADR-0001 멀티모듈 아키텍처

- 상태: 승인
- 일자: 2026-07-15

## 배경

도메인 및 유스케이스가 HTTP, JPA, 배치 프레임워크에 직접 결합되지 않도록 경계를 정의할 필요가 있습니다.

## 결정

`nook-api-presentation`, `nook-api-application`, `nook-api-domain`, `nook-api-infrastructure`,
`nook-api-batch` 모듈을 사용합니다. domain을 의존성의 중심으로 두고 infrastructure는 application 포트를 구현합니다.
presentation과 batch는 독립 실행 애플리케이션으로 제공합니다.

## 결과

Gradle 의존성으로 주요 경계를 강제합니다. 모듈과 매핑 코드가 늘어나지만 프레임워크 교체와 단위 테스트의 영향을 제한할 수 있습니다.
