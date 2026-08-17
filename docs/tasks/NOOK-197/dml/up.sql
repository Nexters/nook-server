START TRANSACTION;

UPDATE places
SET
    thumbnail_parsing_status = 'COMPLETED',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE thumbnail_url IS NOT NULL
  AND TRIM(thumbnail_url) <> ''
  AND thumbnail_parsing_status <> 'COMPLETED';

COMMIT;
