-- QZhipass Agent invocation backend migration for MySQL 8.x.
-- Review and run after the existing users/agents/conversation tables exist.
-- This script is additive and repeatable; it never drops application data.

CREATE TABLE IF NOT EXISTS user_agent_call_settings (
    user_id BIGINT NOT NULL,
    hotkey VARCHAR(32) NOT NULL DEFAULT '!',
    mouse_trigger_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS qzhipass_add_column_if_missing;
DELIMITER //
CREATE PROCEDURE qzhipass_add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = table_name_value
           AND COLUMN_NAME = column_name_value
    ) THEN
        SET @column_ddl = CONCAT(
                'ALTER TABLE `', table_name_value,
                '` ADD COLUMN `', column_name_value, '` ', column_definition_value
        );
        PREPARE column_statement FROM @column_ddl;
        EXECUTE column_statement;
        DEALLOCATE PREPARE column_statement;
    END IF;
END//
DELIMITER ;

CALL qzhipass_add_column_if_missing('agents', 'prompt', 'LONGTEXT NULL');
CALL qzhipass_add_column_if_missing('agents', 'base_model', 'VARCHAR(100) NOT NULL DEFAULT ''''');
CALL qzhipass_add_column_if_missing('agents', 'available', 'TINYINT(1) NOT NULL DEFAULT 1');
CALL qzhipass_add_column_if_missing('conversation_messages', 'agent_id', 'BIGINT NULL');

-- Existing agents remain manageable but are intentionally not callable until
-- an administrator/owner supplies a non-blank prompt and base_model.
UPDATE agents SET prompt = '' WHERE prompt IS NULL;
ALTER TABLE agents MODIFY COLUMN prompt LONGTEXT NOT NULL;

DROP PROCEDURE IF EXISTS qzhipass_add_index_if_missing;
DELIMITER //
CREATE PROCEDURE qzhipass_add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN indexed_columns_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = table_name_value
           AND INDEX_NAME = index_name_value
    ) THEN
        SET @index_ddl = CONCAT(
                'ALTER TABLE `', table_name_value,
                '` ADD INDEX `', index_name_value, '` (', indexed_columns_value, ')'
        );
        PREPARE index_statement FROM @index_ddl;
        EXECUTE index_statement;
        DEALLOCATE PREPARE index_statement;
    END IF;
END//
DELIMITER ;

CALL qzhipass_add_index_if_missing(
        'agents',
        'idx_agents_callable_owner',
        '`created_by`, `status`, `available`, `agent_name`'
);
CALL qzhipass_add_index_if_missing(
        'conversation_messages',
        'idx_conversation_messages_agent_id',
        '`agent_id`'
);

DROP PROCEDURE IF EXISTS qzhipass_add_index_if_missing;
DROP PROCEDURE IF EXISTS qzhipass_add_column_if_missing;
