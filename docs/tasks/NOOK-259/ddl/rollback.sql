ALTER TABLE place_parsing_jobs
    DROP COLUMN unresolved_place_clues,
    DROP COLUMN resolved_place_count,
    DROP COLUMN extracted_place_count,
    DROP COLUMN expected_place_count,
    DROP COLUMN parsing_outcome;
