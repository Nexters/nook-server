DROP TABLE IF EXISTS external_provider_usage_limit_notifications;
DROP TABLE IF EXISTS external_provider_usage_limits;
DROP TABLE IF EXISTS external_provider_usage_events;
DROP TABLE IF EXISTS external_provider_budget_policies;
DROP TABLE IF EXISTS external_provider_price_policies;

DELETE FROM external_provider_billing_snapshots
WHERE provider = 'GOOGLE_CLOUD_BILLING';

DELETE FROM external_provider_billing_sync_states
WHERE provider = 'GOOGLE_CLOUD_BILLING';
