INSERT INTO runtime_configurations (configuration_key, configuration_value, description)
VALUES ('place.parsing.provider-chain', 'LEGACY', '장소 파싱 provider 실행 순서') AS new
ON DUPLICATE KEY UPDATE configuration_value = new.configuration_value,
                        description = new.description;

UPDATE runtime_configurations
SET configuration_value = 'APIFY_NAVER,GOOGLE'
WHERE configuration_key = 'place.thumbnail.provider-chain';
