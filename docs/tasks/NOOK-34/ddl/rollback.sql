ALTER TABLE user_groups
    DROP CHECK chk_user_groups_color,
    DROP COLUMN color,
    MODIFY COLUMN name VARCHAR(100) NOT NULL COMMENT '그룹명';
