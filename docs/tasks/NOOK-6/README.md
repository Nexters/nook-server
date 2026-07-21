# NOOK-6 Swagger 설정 및 JPA BaseEntity 추가

## 목적

API 문서화를 위한 Swagger/OpenAPI 설정을 추가하고, JPA 엔티티 공통 감사 필드 기반이 되는
`BaseEntity`를 구성합니다.

## 범위

- `nook-api-presentation`에 Swagger/OpenAPI 의존성 및 기본 설정 추가
- `nook-api-infrastructure`에 JPA 감사 기반 `BaseEntity` 추가
- `createdAt`, `updatedAt` 감사 필드 구성
- JPA auditing 활성화 설정 추가
- 설정 메타데이터를 확인하는 최소 테스트 추가

## 제외 범위

- 실제 도메인 JPA entity 추가
- API endpoint 스펙 변경
- DB migration 작성

## 검증

- `./gradlew check`

## DDL

이번 작업은 공통 JPA 상위 클래스와 설정만 추가하며 실제 테이블, 칼럼, 인덱스를 변경하지 않습니다.
