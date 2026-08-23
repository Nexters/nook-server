# NOOK-285 dev Docker 이미지 자동 정리

## 목적

dev API 배포마다 누적되는 이전 Docker 이미지와 빌드 캐시를 자동으로 정리해 dev VM의
루트 파일시스템 사용률이 지속해서 증가하지 않게 합니다.

## 범위

- dev API 배포의 health check가 성공한 뒤 Docker 정리를 실행합니다.
- 생성된 지 24시간이 지난 미사용 이미지와 빌드 캐시를 정리합니다.
- 실행 중인 컨테이너가 사용하는 이미지와 Docker 볼륨은 유지합니다.

## 제외 범위

- live 배포 정책 변경
- Docker 볼륨 및 컨테이너 데이터 정리
- VM 디스크 증설
- journal 로그 보존 정책 변경

## 성공 기준

- 배포 과정이나 health check가 실패하면 Docker 정리를 실행하지 않습니다.
- 정상 배포 후 24시간이 지난 미사용 이미지와 빌드 캐시를 정리합니다.
- 실행 중인 컨테이너와 Docker 볼륨을 정리 대상에 포함하지 않습니다.
- 저장소 최종 검증을 통과합니다.

## 검증

```shell
bash -n .github/scripts/deploy-dev.sh
./gradlew check
```

dev 배포 후 GitHub Actions에서 배포 job이 성공하는지 확인하고, dev VM에서
`docker system df`, `df -h /`, `/actuator/health` 응답을 확인합니다.
