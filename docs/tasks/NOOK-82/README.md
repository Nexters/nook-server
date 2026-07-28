# NOOK-82 단일 장소명 후보의 잘못된 region 매칭 실패 보완

## 목적

장소 검색 provider가 상호명이 정확히 일치하는 후보를 하나만 반환했는데도 LLM이 생성한 부정확한
지역 정보 때문에 장소가 저장되지 않는 문제를 수정한다.

## 범위

- 정규화한 장소명이 정확히 일치하는 후보가 하나면 `region`과 무관하게 확정
- 동일 이름 후보가 여러 개일 때만 `region`으로 후보를 좁힘
- 동일 이름 후보가 여러 개이고 `region`으로 단일 확정할 수 없으면 기존 실패 정책 유지
- 단일 이름 후보와 잘못된 `region` 조합 회귀 테스트 추가

## 제외 범위

- 상호명 유사도 또는 부분 일치
- 좌표 기반 거리 판별
- LLM 지역 추출 프롬프트와 장소 검색 provider 변경

## 성공 기준

- `이츠야`의 정확한 이름 후보가 하나면 잘못된 `서초구` 지역 정보가 있어도 해당 후보가 저장된다.
- 동일 이름 후보가 여러 개이면 지역 정보 없이 임의 후보를 저장하지 않는다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-application:test \
  --tests org.every.nook.api.application.place.ProcessPlaceParsingJobUseCaseTest
./gradlew check
```

## DDL

스키마 변경은 없다.
