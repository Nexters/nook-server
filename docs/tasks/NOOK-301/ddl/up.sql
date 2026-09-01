DROP TABLE IF EXISTS external_provider_usage_limit_notifications;
DROP TABLE IF EXISTS external_provider_usage_limits;
DROP TABLE IF EXISTS external_provider_usage_events;
DROP TABLE IF EXISTS external_provider_budget_policies;
DROP TABLE IF EXISTS external_provider_price_policies;

CREATE TABLE IF NOT EXISTS openai_token_usage_events
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'OpenAI 토큰 사용 이벤트 식별자',
    feature             VARCHAR(50)  NOT NULL COMMENT 'OpenAI 호출 기능 코드',
    model               VARCHAR(100) NOT NULL COMMENT 'OpenAI 응답 모델 식별자',
    input_tokens        BIGINT       NOT NULL COMMENT '캐시 입력을 포함한 입력 토큰 수',
    cached_input_tokens BIGINT       NOT NULL COMMENT '입력 토큰 중 캐시된 토큰 수',
    output_tokens       BIGINT       NOT NULL COMMENT 'reasoning을 포함한 출력 토큰 수',
    total_tokens        BIGINT       NOT NULL COMMENT 'OpenAI 응답의 전체 토큰 수',
    occurred_at         TIMESTAMP(6) NOT NULL COMMENT 'OpenAI 응답 수신 시각',
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    INDEX idx_occurred_at_feature_model (occurred_at, feature, model),
    CONSTRAINT chk_openai_token_usage_input CHECK (input_tokens >= 0),
    CONSTRAINT chk_openai_token_usage_cached_input CHECK (
        cached_input_tokens >= 0 AND cached_input_tokens <= input_tokens
    ),
    CONSTRAINT chk_openai_token_usage_output CHECK (output_tokens >= 0),
    CONSTRAINT chk_openai_token_usage_total CHECK (
        total_tokens >= 0 AND total_tokens = input_tokens + output_tokens
    )
) ENGINE = InnoDB COMMENT = 'OpenAI Responses API 공식 usage 토큰 이벤트';

DELETE FROM external_provider_billing_snapshots
WHERE provider = 'GOOGLE_CLOUD_BILLING';

DELETE FROM external_provider_billing_sync_states
WHERE provider = 'GOOGLE_CLOUD_BILLING';
