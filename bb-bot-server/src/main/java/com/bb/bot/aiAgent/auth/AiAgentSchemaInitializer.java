package com.bb.bot.aiAgent.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

            // 种子：内置工具的默认 user 策略（无副作用的原语都对普通用户开放）
            "INSERT IGNORE INTO ai_tool_policy (tool_name, role, allowed) VALUES " +
                    "('server_time','user',1)," +
                    "('http_fetch','user',1)," +
                    "('file_read','user',1)," +
                    "('list_dir','user',1)," +
                    "('web_search','user',1)," +
                    "('grep_search','user',1)," +
                    "('load_skill','user',1)"
    };

    @EventListener
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
