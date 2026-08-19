DELETE FROM runtime_configurations
WHERE configuration_key = 'place.parsing.provider-chain';

INSERT INTO runtime_configurations (configuration_key, configuration_value, description)
VALUES ('place.thumbnail.provider-chain', 'APIFY_GOOGLE,GOOGLE', 'Place thumbnail providers in fallback order') AS new
ON DUPLICATE KEY UPDATE configuration_value = new.configuration_value,
                        description = new.description;
