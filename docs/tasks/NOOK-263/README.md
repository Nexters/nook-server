# NOOK-263 Corepin 우선 런타임 OCR fallback 체인

## 목적

Instagram 장소 이미지 OCR을 Corepin으로 우선 처리하고 실패 또는 빈 전사 시 CLOVA와 OpenAI로 복구한다.

## 범위

- Corepin 단건 OCR adapter
- CLOVA General OCR adapter
- `runtime_configurations` 기반 `COREPIN,CLOVA,OPENAI` provider 체인
- provider별 선택 및 fallback 관측 로그
- API 키와 CLOVA endpoint는 환경 secret으로 관리
- 런타임 설정이 없거나 잘못된 경우 기존 OpenAI OCR로 안전하게 복구

## 제외 범위

- 장소 추론 LLM 및 장소 검색 규칙 변경
- API 키의 DB 저장
- Google Cloud Vision 제거

## 검증

- provider 오류와 빈 전사 시 다음 provider가 호출된다.
- 정상 전사를 반환한 첫 provider에서 체인이 종료된다.
- 잘못된 런타임 값은 기본 체인으로 복구된다.
- Corepin 및 CLOVA 요청·응답 계약 테스트를 통과한다.
- `./gradlew check`
