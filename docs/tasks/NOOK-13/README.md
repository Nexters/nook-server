# NOOK-13 컨테이너 이미지 배포 액션 구성

## 목적

GitHub Actions에서 API 컨테이너 이미지를 빌드해 Gabia Container Registry에 푸시하고,
develop 브랜치 배포 시 dev VM에 최신 이미지를 반영합니다.

## 범위

- develop/main push 기준 컨테이너 이미지 빌드 및 푸시 워크플로 추가
- develop push 시 dev VM SSH 접속 후 Docker Compose 기반 배포 스크립트 실행
- 배포에 필요한 GitHub Actions secret 사용
- develop 배포에서 사용하는 private registry 이미지 태그 푸시

## 제외 범위

- 운영 VM 원격 배포 자동화
- GitHub Actions secret 생성 및 값 변경
- 인프라 서버 경로 및 Docker Compose 파일 변경
- 애플리케이션 코드 변경

## 설계

`container-image.yml`은 develop과 main push에서 Docker Buildx로 `Dockerfile` 기반 이미지를
빌드합니다. 브랜치별 이미지 태그는 짧은 커밋 SHA와 `latest` 별칭을 함께 사용합니다.

develop 브랜치에서는 public registry 태그와 dev VM 배포용 private registry 태그를 함께 푸시합니다.
빌드 완료 후 `deploy-dev` job이 SSH 키를 작성하고 `.github/scripts/deploy-dev.sh`를 실행합니다.

배포 스크립트는 원격 dev VM에서 private registry에 로그인한 뒤 `/opt/nook/api/.env`의 `IMAGE`
값을 빌드 산출 이미지로 갱신하고 `docker compose pull`, `docker compose up -d`를 실행합니다.

## 성공 기준

- 워크플로 YAML 구문이 유효합니다.
- 배포 스크립트 Bash 구문이 유효합니다.
- develop/main 이미지 태그가 브랜치별 dev/prod 접두어를 사용합니다.
- develop 배포에서 pull할 private registry 이미지가 빌드 job에서 푸시됩니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/container-image.yml')"
bash -n .github/scripts/deploy-dev.sh
./gradlew check
```

## 배포 및 롤백

GitHub Actions 워크플로 변경은 main 반영 후 적용됩니다. 문제가 있으면 해당 워크플로 변경 커밋을
되돌려 자동 이미지 빌드 및 dev 배포를 중단합니다.
