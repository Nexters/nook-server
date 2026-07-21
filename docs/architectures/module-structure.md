# 모듈 구조

## 의존 방향

```text
nook-api-presentation ──> nook-api-application ──> nook-api-domain
                                 ^
nook-api-infrastructure ─────────┘───────────────> nook-api-domain

nook-api-batch ─────────> nook-api-application ──> nook-api-domain
```

- `nook-api-domain`은 다른 프로젝트 모듈에 의존하지 않습니다.
- `nook-api-application`은 `nook-api-domain`에만 의존합니다.
- `nook-api-infrastructure`는 application 포트를 구현하며 `nook-api-application`, `nook-api-domain`에 의존합니다.
- `nook-api-presentation`, `nook-api-batch`는 `nook-api-application`을 컴파일 의존하고
  `nook-api-infrastructure`를 런타임 의존합니다.
- JPA, Kotlin JDSL, MySQL 의존성은 `nook-api-infrastructure` 밖으로 노출하지 않습니다.
