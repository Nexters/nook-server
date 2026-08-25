DELETE FROM external_provider_usage_events
WHERE invocation_key IN (
    'apify-baseline-202608-instagram', 'apify-baseline-202608-google-maps',
    'apify-baseline-202608-naver-search', 'apify-baseline-202608-naver-photo'
);

DELETE FROM external_provider_price_policies
WHERE (provider, sku) IN (
    ('APIFY', 'INSTAGRAM_SCRAPER'),
    ('APIFY_GOOGLE_MAPS', 'GOOGLE_MAPS_SCRAPER'),
    ('APIFY_NAVER_PLACE', 'NAVER_MAP_SEARCH_RESULTS_SCRAPER'),
    ('APIFY_NAVER_PLACE', 'NAVER_PLACE_PHOTO_SCRAPER')
);
