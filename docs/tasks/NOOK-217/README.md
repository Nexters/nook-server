# NOOK-217 live 단일 VM Blue/Green 배포 및 RDS 구축

## 목적

Gabia live VM 한 대에서 애플리케이션 무중단 Blue/Green 배포와 SHA 기반 롤백을 지원하고,
AWS RDS MySQL을 live 데이터베이스로 구성한다.

## 범위

- AWS RDS MySQL 8.4 Single-AZ와 live VM 전용 보안 그룹을 구성한다.
- private S3, CloudFront OAC와 live VM 전용 AssumeRole 인증을 구성한다.
- live VM에 Docker, Nginx, Certbot과 swap을 구성한다.
- 두 포트를 사용하는 Blue/Green Compose와 배포 및 롤백 스크립트를 구성한다.
- `api.everynook.co.kr` HTTPS endpoint를 구성한다.
- `main` push에서 `prod-<sequence>-<SHA8>` 이미지를 만들고 Blue/Green으로 live에 자동 배포한다.
- `develop` push에서 `dev-<sequence>-<SHA8>` 이미지를 만들고 기존 방식으로 dev에 자동 배포한다.
- `workflow_dispatch`에서 dev 또는 live의 immutable 버전을 지정해 재배포하거나 롤백한다.
- dev와 live에서 분리할 환경 변수를 문서화한다.

## 제외 범위

- 복수 VM 또는 Kubernetes 기반 인프라 고가용성
- RDS Multi-AZ
- 외부 provider의 live 애플리케이션 및 API key 발급
- live 데이터 이관

## 배포 구조

```text
api.everynook.co.kr -> Nginx -> active slot
                                 |- blue  127.0.0.1:8081
                                 `- green 127.0.0.1:8082

active/candidate slot -> TLS -> AWS RDS MySQL
```

평상시에는 active slot만 요청을 처리한다. 배포할 때 비활성 slot에 지정한 immutable 이미지를
시작하고 health check가 성공하면 Nginx upstream을 전환한다. 이전 slot은 다음 배포 또는 즉시
롤백을 위해 유지한다.

## live 전용 환경 변수

반드시 dev와 다른 값을 사용한다.

- `APP_ENV`, `SPRING_PROFILES_ACTIVE`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`
- `KAKAO_APP_ID`
- `APPLE_CLIENT_ID`, `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`
- `AWS_S3_BUCKET`, `AWS_CLOUDFRONT_BASE_URL`, AWS credentials
- `ADMIN_ACCESS_ENABLED`, `ADMIN_ACCESS_TEAM_DOMAIN`, `ADMIN_ACCESS_AUDIENCE`

JWT access/refresh secret은 서로 독립적인 CSPRNG 256-bit 이상 난수로 생성하고 환경 간 재사용하지 않는다.
base64 또는 base64url로 인코딩한 문자열을 사용할 수 있으며 저장소나 배포 로그에는 출력하지 않는다.

사용량, 비용과 장애 격리가 필요하면 다음 provider key도 live 전용으로 분리한다.

- `OPENAI_API_KEY`
- `KAKAO_REST_API_KEY`
- `NAVER_API_HUB_CLIENT_ID`, `NAVER_API_HUB_CLIENT_SECRET`
- `GOOGLE_MAPS_API_KEY`, `GOOGLE_CLOUD_VISION_API_KEY`
- `BRIGHT_DATA_API_TOKEN`
- `APIFY_API_TOKEN`

## CI 입력과 secrets

`Deploy Version` workflow는 `main`에 있는 workflow를 수동 실행한다.

- `environment`: `dev` 또는 `live`
- `image_tag`: `dev-<sequence>-<SHA8>` 또는 `prod-<sequence>-<SHA8>`

새 이미지 태그의 sequence는 `Container Image` workflow의 `GITHUB_RUN_NUMBER`를 사용한다. 기존
`dev-<SHA12>`와 `prod-<SHA12>` 이미지는 전환 기간의 롤백을 위해 수동 배포에서 계속 허용한다.
dev는 단일 container를 교체하고 live는 비활성 slot을 준비한 뒤 Nginx upstream을 전환한다.

GitHub `live` environment에 다음 secrets가 필요하다.

- `LIVE_SSH_TARGET`
- `LIVE_SSH_PRIVATE_KEY`
- `LIVE_SSH_KNOWN_HOSTS`
- `GABIA_REGISTRY_USERNAME`
- `GABIA_REGISTRY_PASSWORD`

## 성공 기준

- `https://api.everynook.co.kr/actuator/health`가 정상 응답한다.
- live에서 Springdoc API docs와 Swagger UI가 비활성화된다.
- candidate가 준비되는 동안 active slot이 요청을 계속 처리한다.
- 이전 slot 또는 이전 SHA로 롤백할 수 있다.
- RDS는 live VM에서 TLS로 연결되며 허용한 공인 IP 외에는 접근할 수 없다.
- AWS Free Plan 제약에 맞춰 자동 백업 보존 기간을 1일로 유지한다.
- S3는 public access를 차단하고 CloudFront OAC로만 공개 읽기를 허용한다.
- live VM은 공인 IP가 제한된 bootstrap user로 application role을 AssumeRole한다.
- VM 재부팅 후 active slot과 Nginx가 자동 복구된다.
- 시크릿이 Git 또는 Actions 로그에 노출되지 않는다.
- `./gradlew check`가 통과한다.

## 검증

```shell
bash -n ops/live-runtime/scripts/*.sh .github/scripts/deploy-live.sh
docker compose -f ops/live-runtime/compose.yml --env-file /path/to/deployment.env config
./gradlew check
```
