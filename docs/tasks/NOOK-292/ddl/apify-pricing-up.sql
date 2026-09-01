INSERT INTO external_provider_price_policies
    (provider, sku, unit_type, source_currency, source_unit_price, unit_size, free_monthly_units,
     source_url, pricing_status, enabled)
VALUES
    ('APIFY', 'INSTAGRAM_SCRAPER', 'CALL', 'USD', 2.30, 1000, 0,
     'https://apify.com/pricing', 'PRICED', TRUE),
    ('APIFY_GOOGLE_MAPS', 'GOOGLE_MAPS_SCRAPER', 'CALL', 'USD', 3.00, 1000, 0,
     'https://apify.com/compass/crawler-google-places', 'PRICED', TRUE),
    ('APIFY_NAVER_PLACE', 'NAVER_MAP_SEARCH_RESULTS_SCRAPER', 'CALL', 'USD', 1.50, 1000, 0,
     'https://apify.com/delicious_zebu/naver-map-search-results-scraper', 'PRICED', TRUE),
    ('APIFY_NAVER_PLACE', 'NAVER_PLACE_PHOTO_SCRAPER', 'CALL', 'USD', 0.50, 1000, 0,
     'https://apify.com/oxygenated_quagmire/naver-place-photos', 'PRICED', TRUE)
ON DUPLICATE KEY UPDATE
    source_currency = VALUES(source_currency), source_unit_price = VALUES(source_unit_price),
    unit_size = VALUES(unit_size), free_monthly_units = VALUES(free_monthly_units),
    source_url = VALUES(source_url), pricing_status = VALUES(pricing_status), enabled = VALUES(enabled);

INSERT IGNORE INTO external_provider_usage_events
    (invocation_key, operation_id, provider, operation, sku, unit_type, units, status, runtime,
     flow, stage, duration_ms, http_status, failure_code, input_tokens, cached_input_tokens,
     output_tokens, source_currency, source_unit_price, price_unit_size, exchange_rate_krw,
     estimated_cost_krw, pricing_status, request_id, post_id, occurred_at)
VALUES
    ('apify-baseline-202608-instagram', NULL, 'APIFY', 'HISTORICAL_BASELINE', 'INSTAGRAM_SCRAPER',
     'CALL', 554, 'SUCCEEDED', 'BATCH', 'historical-baseline', NULL, 0, NULL, NULL, NULL, NULL, NULL,
     'USD', 0.00230000, 1, NULL, NULL, 'UNCONVERTED', NULL, NULL, '2026-08-25 00:00:00'),
    ('apify-baseline-202608-google-maps', NULL, 'APIFY_GOOGLE_MAPS', 'HISTORICAL_BASELINE',
     'GOOGLE_MAPS_SCRAPER', 'CALL', 468, 'SUCCEEDED', 'BATCH', 'historical-baseline', NULL, 0, NULL,
     NULL, NULL, NULL, NULL, 'USD', 0.00300000, 1, NULL, NULL, 'UNCONVERTED', NULL, NULL,
     '2026-08-25 00:00:00'),
    ('apify-baseline-202608-naver-search', NULL, 'APIFY_NAVER_PLACE', 'HISTORICAL_BASELINE',
     'NAVER_MAP_SEARCH_RESULTS_SCRAPER', 'CALL', 435, 'SUCCEEDED', 'BATCH', 'historical-baseline', NULL,
     0, NULL, NULL, NULL, NULL, NULL, 'USD', 0.00150000, 1, NULL, NULL, 'UNCONVERTED', NULL, NULL,
     '2026-08-25 00:00:00'),
    ('apify-baseline-202608-naver-photo', NULL, 'APIFY_NAVER_PLACE', 'HISTORICAL_BASELINE',
     'NAVER_PLACE_PHOTO_SCRAPER', 'CALL', 253, 'SUCCEEDED', 'BATCH', 'historical-baseline', NULL, 0,
     NULL, NULL, NULL, NULL, NULL, 'USD', 0.00050000, 1, NULL, NULL, 'UNCONVERTED', NULL, NULL,
     '2026-08-25 00:00:00');
