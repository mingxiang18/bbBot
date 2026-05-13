-- bbBot 本地测试 schema 初始化
-- 复用 misu-server 那套 MySQL（端口 3316），仅新建 bb_bot_local 库。
-- AI Agent 相关 4 张表由 AiAgentSchemaInitializer 启动时自动建，这里不重复。
-- 这里只建 dispatcher 必须的最小表 + AI 聊天回归测试需要的表。

CREATE DATABASE IF NOT EXISTS bb_bot_local
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE bb_bot_local;

-- BbEventDispatcher 每条消息都会 OCCUPATION 查这表，缺了直接报错
CREATE TABLE IF NOT EXISTS user_config_value (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(64) DEFAULT NULL,
  group_id VARCHAR(64) DEFAULT NULL,
  type VARCHAR(32) DEFAULT NULL,
  key_name VARCHAR(64) DEFAULT NULL,
  value_name VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_group_type (group_id, type),
  KEY idx_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户/群组配置（dispatcher 必需）';

-- BbAiChatHandler 默认回复路径会查
CREATE TABLE IF NOT EXISTS chat_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  message_id VARCHAR(64) DEFAULT NULL,
  user_qq VARCHAR(64) DEFAULT NULL,
  user_name VARCHAR(128) DEFAULT NULL,
  private_user_id VARCHAR(64) DEFAULT NULL,
  group_id VARCHAR(64) DEFAULT NULL,
  text MEDIUMTEXT,
  type VARCHAR(32) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_group_time (group_id, create_time),
  KEY idx_user_time (private_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天历史';

-- BbAiChatHandler 关键字线索查询会用
CREATE TABLE IF NOT EXISTS ai_clue (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  group_id VARCHAR(64) DEFAULT NULL,
  keyword VARCHAR(255) DEFAULT NULL,
  clue TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_group_keyword (group_id, keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天线索';

-- 业务表全部为可空就行，测试场景里不会触发它们
CREATE TABLE IF NOT EXISTS bb_misc_placeholder (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  k VARCHAR(64) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='占位，不使用';
