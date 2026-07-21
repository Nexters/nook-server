# 패키지 규칙

기본 패키지는 `org.every.nook.api`입니다. 업무 코드는 기술 종류보다 도메인 기능을 우선하여 묶고,
각 기능 내부에서 모듈의 역할을 따릅니다.

presentation DTO, persistence entity와 domain model을 서로 구분합니다. 모듈 경계를 넘을 때 외부 프레임워크 타입을 application과 domain에 전달하지 않습니다.
