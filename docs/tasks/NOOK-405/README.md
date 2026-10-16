# NOOK-405 Instagram 게시물 수집 실패 시 Bright Data 폴백

이슈: https://linear.app/nooknook/issue/NOOK-405

## 목적

Apify가 콘텐츠 없음·접근 제한을 반환해도 실제 Instagram 콘텐츠가 없다고 단정하지 않고,
Bright Data를 한 번 호출해 게시물 수집을 복구한다.

## 확인한 원인

- 개발·운영 DB의 provider mode는 모두 `APIFY_BRIGHT_WITH_DATA_FALLBACK`이다.
- 이슈의 재현 URL `https://www.instagram.com/p/DeTwkqXmqCL/`은 운영 게시물 1547이며,
  콘텐츠 수집 단계에서 `게시물 콘텐츠를 찾을 수 없습니다.`로 최종 실패했다.
- 2026-10-16 17:29 KST 운영 게시물 1841도 같은 오류로 실패했다. 당시 Apify 실행은
  `SUCCEEDED`였지만 결과 item에는 `restricted_page`와 나이·계정 설정에 따른 접근 제한 설명이 있었다.
- Apify adapter는 빈 결과와 `no_items`, `restricted_page` 등을 `PostContentNotFoundException`으로
  변환한다. 기존 router가 이 예외를 폴백하지 않아 Bright Data 호출 없이 최종 실패했다.
- 최근 7일 운영 콘텐츠 수집 실패 18건의 저장된 실패 사유는 모두 콘텐츠 없음이었다.
  이 중 원문 Apify 응답까지 확인한 것은 게시물 1841이며, 나머지의 세부 오류까지 동일하다고 단정하지 않는다.

## 범위

- Apify 우선 폴백 모드에서 `PostContentNotFoundException`도 Bright Data 1회 호출 대상으로 추가.
- 성공하면 Bright Data 결과를 기존 콘텐츠 저장 흐름으로 전달.
- Bright Data도 실패하면 해당 예외를 그대로 전달하며 router 안에서 추가 호출하지 않음.
- 기존 timeout/provider 오류 폴백, 단독 provider 모드, 잘못된 URL 처리 및 반대 방향의 기존 정책 유지.
- 폴백 로그에 `content_not_found` 사유 기록.
- 현재 정책과 달라진 NOOK-110 문서의 설명 갱신.

## 제외 범위

- DB/DDL, 공개 API 계약, provider mode 및 토큰 설정 변경.
- 실제 provider를 새로 실행하는 유료 수집 테스트.
- 운영 실패 게시물 재시도, 서비스 재시작 및 배포.
- 장소 인식 정책 변경. 기존 `PostProcessingViewTest`는 콘텐츠 수집 완료 후 장소 인식만 실패해도
  게시물이 완료 상태를 유지함을 검증한다.

## 성공 기준과 검증

- Apify 콘텐츠 없음 이후 Bright Data 성공 시 복구하며 각 provider는 한 번만 호출된다.
- 실제 HTTP 응답 형식인 빈 배열, `no_items`, `restricted_page`에서 복구한다.
- 두 provider 모두 콘텐츠 없음이거나 Bright Data에 provider 오류가 생기면 최종 오류를 전달한다.
- `APIFY_ONLY`와 잘못된 URL은 Bright Data를 호출하지 않는다.
- 기존 일반 오류/timeout 및 반대 방향 폴백 테스트를 유지한다.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :nook-api-infrastructure:test --tests '*InstagramPostContentExtractorTest' --no-daemon --max-workers=2
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew check --init-script /tmp/nook-405-unit-h2.gradle --no-daemon --max-workers=2
```

이번 작업은 다음 임시 init script로 MySQL 컨테이너 테스트를 제외하고 단위·H2 테스트를 검증했다.
저장소 테스트 설정이나 테스트 소스는 변경하지 않았다.

```groovy
allprojects {
    tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
        exclude '**/*MySqlTest*'
    }
}
```

## 검증 결과

- 수정 전 회귀 테스트 3개가 실패해 폴백 누락과 잘못된 최종 오류 전달을 재현했다.
- 수정 후 Instagram extractor 관련 테스트 27개가 통과했다. 빈 배열, no_items, restricted_page 응답의 폴백도 검증했다.
- 최종 단위·H2 테스트 847개, Detekt, 모듈 의존 검증이 통과했다. 실패/오류 없음.
- Docker 기반 MySQL 테스트는 최종 검증 범위에서 제외했다. 앞선 Docker 포함 실행에서는
  `ParsingSummaryMySqlTest`의 legacy fingerprint 테스트에 expected 0 / actual 1 실패가 관찰됐다.
  해당 테스트나 구현은 이번 폴백 작업에서 변경하지 않았다.
- `git diff --check` 통과. 공개 API/DDL 변경 없음.

## 운영 반영

격리된 worktree에서 구현과 단위·H2 검증을 마쳤다. 운영 설정·DB·실패 작업 상태를 변경하지 않았다.
코드 배포 이후에도 실제 Bright Data 수집 성공 여부와 과거 실패 게시물 복구는 별도 확인이 필요하다.
