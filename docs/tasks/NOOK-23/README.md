# NOOK-23 develop 컨테이너 빌드의 제거된 Instagram provider 포트 참조 수정

## 목적

NOOK-20과 NOOK-22가 `develop`에서 합쳐진 뒤 발생한 Kotlin 컴파일 오류를 수정해
API 컨테이너 이미지를 다시 빌드하고 배포할 수 있도록 합니다.

## 원인

NOOK-22에서 저장 유스케이스가 `ExtractInstagramContentUseCase`를 직접 사용하도록 변경하면서
`InstagramPostProviderPort`를 제거했습니다. `develop`에만 있던
`BrightDataInstagramPostProviderAdapter`가 제거된 포트를 계속 참조해 컨테이너 빌드가 실패했습니다.

## 범위

- 사용되지 않는 `BrightDataInstagramPostProviderAdapter` 제거
- 해당 어댑터 전용 테스트 제거
- API boot jar 및 컨테이너 이미지 빌드 검증

## 제외 범위

- Instagram 추출 및 저장 API 계약 변경
- 데이터베이스 스키마 변경
- 배포 워크플로 변경

## 성공 기준

- 제거된 `InstagramPostProviderPort` 참조가 남아 있지 않습니다.
- `./gradlew :nook-api-presentation:bootJar --no-daemon`이 성공합니다.
- `./gradlew check`가 성공합니다.
- `docker build -f Dockerfile .`이 성공합니다.

## 검증

```shell
./gradlew :nook-api-presentation:bootJar --no-daemon
./gradlew check
docker build -f Dockerfile .
```

## DDL

데이터베이스 스키마 변경은 없습니다.
