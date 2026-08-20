UPDATE runtime_configurations
SET configuration_value = 'APIFY_GOOGLE,GOOGLE',
    description = 'Place thumbnail providers in fallback order'
WHERE configuration_key = 'place.thumbnail.provider-chain';
