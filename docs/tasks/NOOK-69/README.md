# NOOK-69 dev S3 AssumeRole 인증 실패 수정

## 목적

dev VM에서 게시물 생성 시 `nook-dev-media` 프로필의 IAM Role을 AssumeRole하지 못해 S3 접근이
실패하는 문제를 수정한다.

## 원인

실행 JAR에 AWS SDK STS 모듈이 포함되지 않아 role profile 자격증명 공급자를 초기화할 수 없다.
dev VM의 AWS profile, shared credentials file과 config file 마운트는 정상이다.

## 범위

- `nook-api-infrastructure`에 AWS SDK STS 의존성 추가
- AssumeRole 프로필에 필요한 STS 클라이언트의 런타임 클래스패스 포함 여부 검증

## 제외 범위

- AWS credential 또는 IAM 정책 변경
- S3와 CloudFront 인프라 변경
- API와 DB 계약 변경
- dev VM 배포

## 성공 기준

- 애플리케이션 런타임 클래스패스에 AWS SDK STS 모듈이 포함된다.
- S3 저장 관련 테스트와 `./gradlew check`가 통과한다.

## 검증

```shell
./gradlew :nook-api-infrastructure:test
./gradlew check
./gradlew :nook-api-presentation:bootJar
```

- `./gradlew :nook-api-infrastructure:test check`: 성공
- `./gradlew :nook-api-presentation:bootJar`: 성공
- 생성된 Boot JAR의 `BOOT-INF/lib`에서 `s3-2.49.3.jar`, `sts-2.49.3.jar` 포함 확인
