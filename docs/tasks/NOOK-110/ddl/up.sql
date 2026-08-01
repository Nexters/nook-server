CREATE TABLE scraping_provider_responses
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '스크래핑 provider 응답 식별자',
    provider         VARCHAR(50)  NOT NULL COMMENT '스크래핑 provider 식별자',
    source_type      VARCHAR(50)  NOT NULL COMMENT '원본 제공자 유형',
    external_post_id VARCHAR(255) NOT NULL COMMENT '제공자 게시물 식별자',
    response_body    LONGTEXT     NOT NULL COMMENT '스크래핑 provider 성공 응답 원문',
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_provider_source_type_external_post_id (provider, source_type, external_post_id)
) COMMENT '스크래핑 provider별 원본 응답 캐시';

INSERT INTO scraping_provider_responses
    (provider, source_type, external_post_id, response_body, created_at, updated_at)
SELECT 'BRIGHT_DATA', source_type, external_post_id, response_body, created_at, updated_at
FROM bright_data_responses;

CREATE TABLE runtime_configurations
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '런타임 설정 식별자',
    configuration_key   VARCHAR(100) NOT NULL COMMENT '런타임 설정 key',
    configuration_value VARCHAR(255) NOT NULL COMMENT '런타임 설정 값',
    description         VARCHAR(500) NULL COMMENT '런타임 설정 설명',
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_configuration_key (configuration_key)
) COMMENT '배포 없이 변경하는 범용 런타임 설정';

INSERT INTO runtime_configurations (configuration_key, configuration_value, description)
VALUES ('instagram.scraping.provider-mode',
        'BRIGHT_DATA_WITH_APIFY_FALLBACK',
        'Instagram scraping provider routing mode');
