INSERT INTO runtime_configurations (configuration_key, configuration_value, description)
VALUES ('place.parsing.provider-chain', 'APIFY_NAVER,LEGACY', '장소 파싱 provider 실행 순서')
ON DUPLICATE KEY UPDATE
    configuration_value = VALUES(configuration_value),
    description = VALUES(description);
