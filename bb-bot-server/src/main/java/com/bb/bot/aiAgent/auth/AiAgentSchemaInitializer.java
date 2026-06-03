package com.bb.bot.aiAgent.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 启动时确保 AI Agent 相关三张表存在。用 CREATE TABLE IF NOT EXISTS 幂等。
 *
 * <p>不引入 Flyway / Liquibase 是为了保持 bbBot「拉起即用」的低运维成本风格。
 * 如未来 schema 复杂到要做迁移版本管理再切换。</p>
 */
@Slf4j
@Component
public class AiAgentSchemaInitializer {

    @Autowired
    private DataSource dataSource;

    @Value("${aiAgent.autoCreateTables:true}")
    private boolean autoCreateTables;

    private static final String[] DDL = new String[] {
            "CREATE TABLE IF NOT EXISTS ai_user_role (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  platform VARCHAR(32) NOT NULL DEFAULT ''," +
                    "  role VARCHAR(32) NOT NULL," +
                    "  granted_by VARCHAR(64) DEFAULT NULL," +
                    "  granted_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uk_user_platform_role (user_id, platform, role)," +
                    "  KEY idx_user (user_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 用户角色绑定'",

            "CREATE TABLE IF NOT EXISTS ai_tool_policy (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  tool_name VARCHAR(64) NOT NULL," +
                    "  role VARCHAR(32) NOT NULL," +
                    "  allowed TINYINT(1) NOT NULL DEFAULT 0," +
                    "  rate_limit_per_hour INT DEFAULT NULL," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uk_tool_role (tool_name, role)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 工具策略'",

            "CREATE TABLE IF NOT EXISTS ai_tool_invocation_log (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  session_id VARCHAR(64) DEFAULT NULL," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  platform VARCHAR(32) DEFAULT NULL," +
                    "  tool_name VARCHAR(64) NOT NULL," +
                    "  args_json TEXT," +
                    "  result_json MEDIUMTEXT," +
                    "  latency_ms BIGINT DEFAULT NULL," +
                    "  status VARCHAR(16) NOT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_user_created (user_id, created_at)," +
                    "  KEY idx_tool_created (tool_name, created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 工具调用审计'",

            "CREATE TABLE IF NOT EXISTS ai_memory_event (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  session_id VARCHAR(64) DEFAULT NULL," +
                    "  platform VARCHAR(32) DEFAULT NULL," +
                    "  user_id VARCHAR(64) DEFAULT NULL," +
                    "  group_id VARCHAR(64) DEFAULT NULL," +
                    "  user_name VARCHAR(128) DEFAULT NULL," +
                    "  source VARCHAR(16) NOT NULL," +
                    "  kind VARCHAR(32) NOT NULL," +
                    "  message_id VARCHAR(64) DEFAULT NULL," +
                    "  text MEDIUMTEXT," +
                    "  payload JSON," +
                    "  reply_to_event_id BIGINT DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_session (session_id, created_at)," +
                    "  KEY idx_user_time (user_id, created_at)," +
                    "  KEY idx_group_time (group_id, created_at)," +
                    "  KEY idx_kind_time (kind, created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 完整事件流（审计 + 回放）'",

            "CREATE TABLE IF NOT EXISTS ai_memory_session (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  session_id VARCHAR(64) NOT NULL," +
                    "  user_id VARCHAR(64) DEFAULT NULL," +
                    "  group_id VARCHAR(64) DEFAULT NULL," +
                    "  platform VARCHAR(32) DEFAULT NULL," +
                    "  started_at DATETIME NOT NULL," +
                    "  last_event_at DATETIME DEFAULT NULL," +
                    "  ended_at DATETIME DEFAULT NULL," +
                    "  message_count INT DEFAULT 0," +
                    "  summary MEDIUMTEXT," +
                    "  summary_compiled_at DATETIME DEFAULT NULL," +
                    "  UNIQUE KEY uk_session (session_id)," +
                    "  KEY idx_user_started (user_id, started_at)," +
                    "  KEY idx_unended (ended_at, last_event_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话窗口 + 蒸馏 summary'",

            // 给已存在的 ai_memory_session 补 last_event_at（基于"最近一条事件"切分会话，避免活跃聊天被 started_at+gap 误切）。
            // 列已存在时本句报错被逐句 try/catch 吞掉；随后把历史行回填成 started_at 以便 sweep 统一按 last_event_at 比较。
            "ALTER TABLE ai_memory_session ADD COLUMN last_event_at DATETIME DEFAULT NULL",
            "UPDATE ai_memory_session SET last_event_at = started_at WHERE last_event_at IS NULL",

            // ai_memory_fact 的 FULLTEXT 用 ngram 分词（MySQL 5.7+ / 8.0 支持 CJK）
            "CREATE TABLE IF NOT EXISTS ai_memory_fact (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  fact TEXT NOT NULL," +
                    "  search_text TEXT NOT NULL," +
                    "  tags VARCHAR(512) DEFAULT '[]'," +
                    "  fact_time DATETIME DEFAULT NULL," +
                    "  source_session_id VARCHAR(64) DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_user_created (user_id, created_at)," +
                    "  KEY idx_session (source_session_id)," +
                    "  FULLTEXT KEY ft_search (search_text) WITH PARSER ngram" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 长期事实库（openhanako FactStore 等价）'",

            // 结构化记忆卡片（记忆机制重构 Phase 2）。与 ai_memory_fact 并行，不迁移旧数据。
            "CREATE TABLE IF NOT EXISTS ai_memory_item (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  memory_key VARCHAR(64) NOT NULL," +
                    "  type VARCHAR(32) NOT NULL," +
                    "  scope VARCHAR(32) NOT NULL," +
                    "  user_id VARCHAR(64) DEFAULT NULL," +
                    "  group_id VARCHAR(64) DEFAULT NULL," +
                    "  subject_user_id VARCHAR(64) DEFAULT NULL," +
                    "  summary VARCHAR(1024) NOT NULL," +
                    "  body MEDIUMTEXT," +
                    "  why TEXT," +
                    "  how_to_apply TEXT," +
                    "  evidence TEXT," +
                    "  tags VARCHAR(512) DEFAULT '[]'," +
                    "  search_text TEXT," +
                    "  status VARCHAR(32) NOT NULL DEFAULT 'active'," +
                    "  confidence DECIMAL(4,3) DEFAULT NULL," +
                    "  importance DECIMAL(4,3) DEFAULT NULL," +
                    "  expires_at DATETIME DEFAULT NULL," +
                    "  last_seen_at DATETIME DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  source_session_id VARCHAR(64) DEFAULT NULL," +
                    "  superseded_by VARCHAR(64) DEFAULT NULL," +
                    "  UNIQUE KEY uk_memory_key (memory_key)," +
                    "  KEY idx_scope_user_group (scope, user_id, group_id)," +
                    "  KEY idx_type_status (type, status)," +
                    "  KEY idx_updated (updated_at)," +
                    "  KEY idx_expires (status, expires_at)," +
                    "  FULLTEXT KEY ft_memory_search (search_text) WITH PARSER ngram" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 结构化记忆卡片'",

            // 记忆选择审计（Phase 3）。带 TTL，MemoryLifecycleSweeper 定时清理。
            "CREATE TABLE IF NOT EXISTS ai_memory_selection_log (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) DEFAULT NULL," +
                    "  group_id VARCHAR(64) DEFAULT NULL," +
                    "  query_text VARCHAR(512) DEFAULT NULL," +
                    "  candidate_keys TEXT," +
                    "  selected_keys TEXT," +
                    "  selector_model VARCHAR(32) DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_created (created_at)," +
                    "  KEY idx_user_created (user_id, created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 记忆选择审计（带 TTL）'",

            "CREATE TABLE IF NOT EXISTS ai_cron_task (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  owner_user_id VARCHAR(64) NOT NULL," +
                    "  platform VARCHAR(32) NOT NULL," +
                    "  bot_name VARCHAR(64) DEFAULT NULL," +
                    "  target_group_id VARCHAR(64) DEFAULT NULL," +
                    "  target_user_id VARCHAR(64) DEFAULT NULL," +
                    "  cron_expr VARCHAR(64) NOT NULL," +
                    "  prompt TEXT NOT NULL," +
                    "  enabled TINYINT(1) NOT NULL DEFAULT 1," +
                    "  last_run_at DATETIME DEFAULT NULL," +
                    "  last_status VARCHAR(16) DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_enabled (enabled)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent 定时任务'",

            "CREATE TABLE IF NOT EXISTS ai_token_usage (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  platform VARCHAR(32) DEFAULT NULL," +
                    "  provider_name VARCHAR(32) NOT NULL," +
                    "  model VARCHAR(64) NOT NULL," +
                    "  model_role VARCHAR(16) DEFAULT NULL," +
                    "  prompt_tokens INT DEFAULT 0," +
                    "  completion_tokens INT DEFAULT 0," +
                    "  total_tokens INT DEFAULT 0," +
                    "  session_id VARCHAR(64) DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_user_created (user_id, created_at)," +
                    "  KEY idx_user_model (user_id, model)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 每用户每模型 token 用量'",

            // 给已存在的 ai_token_usage 补费用相关列（列已存在时本句报错被 onContextRefreshed 的逐句 try/catch 吞掉）
            "ALTER TABLE ai_token_usage ADD COLUMN cached_tokens INT DEFAULT 0",
            "ALTER TABLE ai_token_usage ADD COLUMN cache_write_tokens INT DEFAULT 0",
            "ALTER TABLE ai_token_usage ADD COLUMN cost_cny DECIMAL(12,6) DEFAULT 0",
            // 给已存在的 ai_model_pricing 补写缓存单价列
            "ALTER TABLE ai_model_pricing ADD COLUMN cache_write_input_per_million DECIMAL(12,4) DEFAULT NULL",

            "CREATE TABLE IF NOT EXISTS ai_model_pricing (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  provider_name VARCHAR(32) NOT NULL," +
                    "  model VARCHAR(64) NOT NULL," +
                    "  currency VARCHAR(8) NOT NULL DEFAULT 'CNY'," +
                    "  input_per_million DECIMAL(12,4) NOT NULL DEFAULT 0," +
                    "  output_per_million DECIMAL(12,4) NOT NULL DEFAULT 0," +
                    "  cache_hit_input_per_million DECIMAL(12,4) DEFAULT NULL," +
                    "  cache_write_input_per_million DECIMAL(12,4) DEFAULT NULL," +
                    "  source VARCHAR(16) NOT NULL DEFAULT 'manual'," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uk_provider_model (provider_name, model)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型单价（按百万 token）'",

            "CREATE TABLE IF NOT EXISTS ai_user_quota (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  platform VARCHAR(32) NOT NULL DEFAULT ''," +
                    "  monthly_limit_cny DECIMAL(10,2) NOT NULL," +
                    "  updated_by VARCHAR(64) DEFAULT NULL," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uk_user_platform (user_id, platform)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每用户月度限额覆盖（CNY）'",

            "CREATE TABLE IF NOT EXISTS ai_quota_grant (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  month CHAR(7) NOT NULL," +
                    "  credit_cny DECIMAL(10,2) NOT NULL," +
                    "  granted_by VARCHAR(64) DEFAULT NULL," +
                    "  reason VARCHAR(255) DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_user_month (user_id, month)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按月授信/额度重置（CNY）'",

            "CREATE TABLE IF NOT EXISTS ai_quota_request (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  user_id VARCHAR(64) NOT NULL," +
                    "  platform VARCHAR(32) DEFAULT NULL," +
                    "  reason VARCHAR(255) DEFAULT NULL," +
                    "  status VARCHAR(16) NOT NULL DEFAULT 'pending'," +
                    "  decided_by VARCHAR(64) DEFAULT NULL," +
                    "  decided_at DATETIME DEFAULT NULL," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  KEY idx_status (status)," +
                    "  KEY idx_user (user_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度审核申请'",

            // 预置当前所用模型的 CNY 官方价（按百万 token）。⚠️ 数值需按官网核对，可用 /价格设置 命令更新。
            // 列序：provider_name, model, currency, input_per_million, output_per_million, cache_hit_input_per_million, source
            // provider_name 与 ai.models.*.kind 对应（deepseek / moonshot …），便于按 (kind, model) 命中价格
            "INSERT IGNORE INTO ai_model_pricing " +
                    "(provider_name, model, currency, input_per_million, output_per_million, cache_hit_input_per_million, source) VALUES " +
                    "('deepseek','deepseek-chat','CNY',2.0000,8.0000,0.5000,'seed')," +
                    "('deepseek','deepseek-reasoner','CNY',4.0000,16.0000,1.0000,'seed')," +
                    "('moonshot','moonshot-v1-8k','CNY',12.0000,12.0000,NULL,'seed')," +
                    "('moonshot','moonshot-v1-32k','CNY',24.0000,24.0000,NULL,'seed')," +
                    "('moonshot','moonshot-v1-128k','CNY',60.0000,60.0000,NULL,'seed')," +
                    "('moonshot','kimi-k2','CNY',4.0000,16.0000,1.0000,'seed')",

            "CREATE TABLE IF NOT EXISTS ai_skill (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    "  name VARCHAR(64) NOT NULL," +
                    "  description VARCHAR(1024) NOT NULL," +
                    "  body MEDIUMTEXT NOT NULL," +
                    "  enabled TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uk_name (name)," +
                    "  KEY idx_enabled (enabled)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent SKILL（DB 托管）'",

            // 种子：内置工具的默认 user 策略。
            // file_write / shell_exec / send_file 也对普通用户开放：
            // 写入与发送限本人文件空间、命令走隔离沙箱（禁网+超时）。
            "INSERT IGNORE INTO ai_tool_policy (tool_name, role, allowed) VALUES " +
                    "('server_time','user',1)," +
                    "('http_fetch','user',1)," +
                    "('file_read','user',1)," +
                    "('file_write','user',1)," +
                    "('send_file','user',1)," +
                    "('list_dir','user',1)," +
                    "('web_search','user',1)," +
                    "('grep_search','user',1)," +
                    "('shell_exec','user',1)," +
                    "('load_skill','user',1)," +
                    "('splatoon3_schedule','user',1)," +
                    "('splatoon_record_list','user',1)," +
                    "('splatoon_record_detail','user',1)," +
                    "('search_memory','user',1)," +
                    "('recall_experience','user',1)," +
                    "('record_experience','user',1)"
    };

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextRefreshed(ContextRefreshedEvent event) {
        if (!autoCreateTables) {
            log.info("aiAgent.autoCreateTables=false，跳过表初始化");
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : DDL) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    log.warn("AI Agent schema 初始化 SQL 执行失败（可能已存在或种子重复，可忽略）: {}", e.getMessage());
                }
            }
            log.info("AI Agent schema 检查 / 初始化完成");
        } catch (Exception e) {
            log.error("AI Agent schema 初始化失败，授权 / 审计功能可能不可用", e);
        }
    }
}
