ALTER TABLE user_groups
    MODIFY COLUMN name VARCHAR(20) NOT NULL COMMENT '그룹명',
    ADD COLUMN color VARCHAR(16) NOT NULL DEFAULT 'YELLOW' COMMENT '그룹 색상 코드' AFTER name,
    ADD CONSTRAINT chk_user_groups_color
        CHECK (color IN ('YELLOW', 'CORAL', 'PINK', 'PURPLE', 'BLUE', 'MINT', 'GREEN', 'GRAY'));
