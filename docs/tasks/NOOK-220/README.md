# NOOK-220 ShedLock 적용으로 스케줄 중복 실행 방지

## 목적

여러 API 인스턴스가 실행될 때 parsing dispatcher와 startup recovery가 동시에 같은 작업을
발행하지 않도록 MySQL 기반 분산 lock을 적용한다.

## 범위

- ShedLock Spring integration과 JDBC template provider를 추가한다.
- DB server time을 기준으로 lock을 획득한다.
- place/post-content dispatcher와 startup recovery에 고유 lock name을 부여한다.
- MySQL 8.4용 `shedlock` table DDL과 rollback DDL을 제공한다.

## lock 정책

| 실행 | lock name | lockAtMostFor | lockAtLeastFor |
| --- | --- | ---: | ---: |
| Place startup recovery | `placeParsing.recoverOutstandingJobs` | 1m | 10s |
| Place dispatcher | `placeParsing.dispatchOutstandingJobs` | 30s | 9s |
| Post content startup recovery | `postContentParsing.recoverOutstandingJobs` | 1m | 10s |
| Post content dispatcher | `postContentParsing.dispatchOutstandingJobs` | 30s | 9s |

dispatcher 기본 주기는 10초다. 9초의 최소 lock 시간은 인스턴스별 스케줄 시각이 조금 달라도
같은 주기 안에서 중복 dispatch하는 것을 막는다. 최대 lock 시간은 예외 종료나 인스턴스 장애 시
영구 lock을 방지한다. lock을 얻지 못한 실행은 대기하지 않고 건너뛴다.

## 배포 순서

Hibernate DDL은 `validate`이므로 애플리케이션이 table을 자동 생성하지 않는다.

1. dev DB에 `ddl/up.sql`을 적용한다.
2. dev 애플리케이션을 배포하고 lock row와 단일 dispatch를 확인한다.
3. live DB에 `ddl/up.sql`을 적용한다.
4. live에 신규 immutable image를 Blue/Green 배포한다.
5. 같은 신규 image를 한 번 더 배포해 양쪽 slot을 모두 ShedLock 적용 버전으로 맞춘다.

애플리케이션을 먼저 배포하면 첫 스케줄 실행부터 `shedlock` table 조회가 실패하므로 반드시 DDL을
먼저 적용한다. 최초 배포 직후 inactive slot에 구버전이 남아 있으면 구버전 dispatcher는 lock을
사용하지 않으므로, 두 번째 배포가 완료될 때까지 일시적으로 중복 dispatch할 수 있다.

## rollback

1. ShedLock 적용 전 애플리케이션 버전으로 롤백할 경우 다른 slot의 ShedLock 버전도 중지한다.
2. 구버전 인스턴스가 하나만 실행 중인지 확인한다.
3. 더 이상 신규 버전이 실행되지 않는지 확인한다.
4. `ddl/rollback.sql`을 적용한다.

table을 먼저 제거하면 아직 실행 중인 신규 인스턴스의 스케줄 실행이 실패할 수 있다.

## 검증

- 모든 `@Scheduled` method가 `@SchedulerLock`을 갖는지 검사한다.
- lock name과 timeout 정책을 검사한다.
- 같은 lock을 연속 획득할 때 두 번째 획득이 거부되는지 검사한다.
- `./gradlew check`를 통과한다.
