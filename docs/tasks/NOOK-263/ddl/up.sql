INSERT INTO runtime_configurations (configuration_key, configuration_value, description)
VALUES ('ocr.image-text.provider-chain', 'COREPIN,CLOVA,OPENAI', '장소 이미지 OCR provider fallback 순서')
AS new
ON DUPLICATE KEY UPDATE configuration_value = new.configuration_value,
                        description = new.description;
