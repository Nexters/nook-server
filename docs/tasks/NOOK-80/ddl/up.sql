DROP PROCEDURE IF EXISTS drop_all_foreign_keys;
SET @target_schema = DATABASE();

DELIMITER //

CREATE PROCEDURE drop_all_foreign_keys()
BEGIN
    DECLARE remaining_foreign_keys INT DEFAULT 0;
    DECLARE target_table VARCHAR(64);
    DECLARE target_constraint VARCHAR(64);

    drop_loop: LOOP
        SELECT COUNT(*)
        INTO remaining_foreign_keys
        FROM information_schema.table_constraints
        WHERE constraint_schema = @target_schema
          AND constraint_type = 'FOREIGN KEY';

        IF remaining_foreign_keys = 0 THEN
            LEAVE drop_loop;
        END IF;

        SELECT table_name, constraint_name
        INTO target_table, target_constraint
        FROM information_schema.table_constraints
        WHERE constraint_schema = @target_schema
          AND constraint_type = 'FOREIGN KEY'
        ORDER BY table_name, constraint_name
        LIMIT 1;

        SET @drop_foreign_key = CONCAT(
            'ALTER TABLE `',
            REPLACE(target_table, '`', '``'),
            '` DROP FOREIGN KEY `',
            REPLACE(target_constraint, '`', '``'),
            '`'
        );
        PREPARE drop_statement FROM @drop_foreign_key;
        EXECUTE drop_statement;
        DEALLOCATE PREPARE drop_statement;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @target_schema
          AND table_name = 'refresh_tokens'
          AND index_name = 'fk_refresh_tokens_replaced_by_token_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @target_schema
          AND table_name = 'refresh_tokens'
          AND index_name = 'idx_replaced_by_token_id'
    ) THEN
        ALTER TABLE refresh_tokens
            RENAME INDEX fk_refresh_tokens_replaced_by_token_id TO idx_replaced_by_token_id;
    ELSEIF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @target_schema
          AND table_name = 'refresh_tokens'
          AND index_name = 'fk_refresh_tokens_replaced_by_token_id'
    ) THEN
        ALTER TABLE refresh_tokens
            DROP INDEX fk_refresh_tokens_replaced_by_token_id;
    END IF;
END//

DELIMITER ;

CALL drop_all_foreign_keys();
DROP PROCEDURE drop_all_foreign_keys;
