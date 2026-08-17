# NOOK-200 어드민 웹 인프라와 dev 배포 파이프라인 구성

## 목적

운영용 admin web을 React Admin + MUI 기반으로 시작하고, ops VM에서 dev/live 환경을
분리해 배포할 수 있는 기반을 구성한다.

## 범위

- `nook-admin-web` React Admin + MUI scaffold 추가
- ops VM admin web compose/deploy 스크립트 추가
- dev/live admin web 컨테이너 이름과 포트 분리
- admin web 변경 감지 시 GitHub Actions에서 이미지 빌드/푸시 및 dev ops VM 배포
- API 변경 감지 시 기존 dev API 배포 흐름 유지
- `/api/admin/**` Swagger/OpenAPI 문서 제외 설정

## 제외 범위

- 실제 장소 보정/파싱 작업/감사 로그 기능 구현
- live admin 외부 공개 및 live 배포 자동화
- 서버 측 admin 인증/권한 검증 구현

## 검증

- `pnpm build`
- `./gradlew check`
- workflow YAML parse
- deploy script shell syntax check
- ops VM dev admin web health check
