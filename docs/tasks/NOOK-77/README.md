# NOOK-77 로컬 실행 시 .env 자동 로드 지원

## 목적

로컬 개발자가 환경변수를 매번 shell에 export하지 않아도 저장소 루트의 `.env` 파일로 API와 batch
애플리케이션을 실행할 수 있도록 한다.

## 범위

- `dotenv-kotlin` 의존성 추가
- API와 batch 애플리케이션 시작 시 저장소 루트 `.env` 로드
- `.env` 파일이 없어도 기존 환경변수 기반 실행 유지
- OS 환경변수, JVM system property와 명령행 인자가 `.env`보다 우선하도록 구성
- `.env` 로딩과 파일 부재 회귀 테스트 추가
- README의 로컬 실행 방법 갱신

## 제외 범위

- `.env` 파일 커밋
- dev, staging, live secret 관리 방식 변경
- Docker 및 배포 파이프라인 환경변수 주입 방식 변경
- 환경변수 이름과 API 또는 DB 계약 변경

## 성공 기준

- 저장소 루트 `.env`의 값이 API와 batch Spring 설정에 반영된다.
- 외부에서 주입한 설정값이 `.env`보다 우선한다.
- `.env`가 없는 환경에서도 애플리케이션 시작 방식이 유지된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-presentation:test
./gradlew :nook-api-batch:test
./gradlew check
```

## DDL

스키마 변경은 없다.
