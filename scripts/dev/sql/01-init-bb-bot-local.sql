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

-- SKILL 已改为纯 DB 托管（不再扫描 skillsDir 目录）。ai_skill 表本由
-- AiAgentSchemaInitializer 启动时自动建，这里提前建好并 seed 一条 log-triage，
-- 供 A12 / S6 SKILL 场景使用。CREATE / INSERT 均幂等。
CREATE TABLE IF NOT EXISTS ai_skill (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  description VARCHAR(1024) NOT NULL,
  body MEDIUMTEXT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name),
  KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent SKILL（DB 托管）';

INSERT IGNORE INTO ai_skill (name, description, body) VALUES
('log-triage',
 '当用户让你「分析日志 / 查日志里出错的地方 / log 里有啥异常」时按本指引执行。组合 list_dir + grep_search + file_read 三个原语完成。',
 '# 日志分诊 (log-triage)

当用户要分析日志时，按下面流程：

## 步骤
1. 定位日志文件。如果用户给了具体路径，直接 file_read 它；否则 list_dir 用户指定的目录（默认 /tmp），挑后缀 .log 的文件。
2. 抓异常行。对每个候选日志文件用 grep_search，正则 (ERROR|Exception|FATAL|panic)，得到匹配列表。
3. 取上下文。匹配少于 20 条用 file_read 读出上下文；多于 20 条仅返回 grep 统计 + 前 10 条。
4. 总结。中文 200 字内总结最常见异常类型、时间线、可能根因。

## 边界
- 不要试图修复 bug，只做诊断
- 不要读 > 1MB 的日志文件
- 不要请求 root 权限或动用 shell_exec');
