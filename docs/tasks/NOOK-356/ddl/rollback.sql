-- Deploy previous API/worker first. Removes disposition history on posts; audit logs remain.
ALTER TABLE posts DROP COLUMN processing_disposition, DROP COLUMN processing_disposition_reason,
 DROP COLUMN processing_disposition_changed_at, ALGORITHM=INSTANT;
