# NOOK-202 GitHub Actions runner를 ops VM으로 이전

## 목적

dev VM에서 실행 중인 GitHub Actions self-hosted runner를 ops VM으로 이전해 dev VM의 메모리
사용량을 줄이고 배포 실행 위치를 분리합니다.

## 범위

- ops VM에 `nook-ops` label을 가진 self-hosted runner를 설치합니다.
- API 배포는 ops VM runner에서 dev VM으로 SSH 접속해 실행합니다.
- admin web 배포는 ops VM runner가 로컬 Docker Compose로 실행합니다.
- 이전된 배포 경로에서 API와 admin web health check를 수행합니다.
- 배포 검증 후 dev VM의 기존 runner를 제거합니다.

## 제외 범위

- API와 MySQL 컨테이너의 메모리 제한 변경
- VM swap 구성
- 애플리케이션 기능 및 API 계약 변경

## 검증

```shell
bash -n .github/scripts/deploy-dev.sh
bash -n .github/scripts/deploy-admin-web.sh
./gradlew check
```

develop 배포 후 GitHub Actions에서 두 배포 job이 `nook-ops` runner에서 성공하는지 확인하고,
dev API의 `/actuator/health`와 admin web의 `/health` 응답을 확인합니다.
