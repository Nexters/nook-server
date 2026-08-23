# NOOK-288 파싱 정책 실행·어드민 카탈로그 단일화

## 목적

실제 파싱 코드와 관리자 파싱 과정 설명이 서로 어긋나지 않도록 application 계층의 실행 정책을
단일 진실 공급원으로 만든다. 실제 판정 결과는 고정 `ruleId`와 입력 사실을 trace에 남기고,
어드민은 같은 정책 카탈로그를 조회해 현재 규칙을 표시한다.

## 범위

- 워크플로, 단계, 연결선의 표시 가능한 정의
- 고정 `workflowId`, `stepId`, `ruleId`
- 실행 가능한 규칙과 `PASSED`, `FAILED`, `SKIPPED` 판정 결과
- 문자열, 개수, 불리언, 문자열 목록의 타입 안전한 판정 facts
- ID 중복과 잘못된 단계 참조를 차단하는 규칙 카탈로그
- 텍스트 단서 grounding 정책 이전
- 장소 후보 호환성·strict·grounded·검색 근거·모델 선택·최종 검증 정책 이전
- 이미지 분석 필요 여부 정책 이전
- 모델 제목·deterministic fallback·기본 제목 정책 이전
- 실제 실행 trace에 `ruleId`, outcome, reason, 입력 facts 기록
- 어드민의 대상 노드 판정 하드코딩을 정책 카탈로그 조회로 교체
- 어드민 화면에서 정책 `ruleId`와 실제 trace 판정 결과 노출
- 공통 계약, 정책 실행, trace, 어드민 동기화 테스트
- 구조 결정 ADR

## 제외 범위

- DB 스키마 및 기존 필드·상태 코드의 breaking API 변경
- 정책 버전 저장
- provider 호출, 트랜잭션, retry, progress, 최종 저장을 범용 workflow runner로 이전
- 이미지 OCR provider 및 OCR 텍스트 복원 알고리즘 자체의 정책화

## 성공 기준 및 검증

- application 계약이 Spring, JPA, HTTP 타입에 의존하지 않는다.
- 규칙 실행 결과에는 정의에 선언된 고정 `ruleId`가 자동으로 포함된다.
- workflow, step, rule ID 중복과 잘못된 edge/effect 참조가 생성 시점에 거부된다.
- 후보 선택, 텍스트 grounding, 이미지 분석 판단, 제목 결정이 정책 평가 결과로 실행된다.
- 실제 trace의 `ruleId`를 어드민의 현재 정책 정의에서 조회할 수 있다.
- 대상 어드민 노드의 판정 조건·식·통과/실패 효과·임계값은 실행 정책에서 생성된다.
- 기존 파싱 테스트 결과와 공개 API가 변하지 않는다.
- `./gradlew detekt`, `./gradlew test`, `./gradlew check`, 어드민 `pnpm build`가 통과한다.
