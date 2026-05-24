package com.bb.bot.database.splatoon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 启动时给已存在的喷喷战绩表补充新增列（详情/列表新版渲染需要的字段）。
 *
 * <p>表本身在生产已存在（2024 年功能上线时建好），这里只做幂等的 {@code ALTER TABLE ADD COLUMN}：
 * MySQL 无 ADD COLUMN IF NOT EXISTS，靠逐句 try/catch 吞掉「列已存在」错误（与
 * {@code AiAgentSchemaInitializer} 同款做法）。复用 aiAgent.autoCreateTables 开关。</p>
 */
@Slf4j
@Component
public class SplatoonSchemaInitializer {

    @Autowired
    private DataSource dataSource;

    @Value("${aiAgent.autoCreateTables:true}")
    private boolean autoCreateTables;

    private static final String[] DDL = new String[]{
            // 对战记录:时长(秒)/完胜KO/双方比分(文本,占地为百分比、真格为计数)/奖牌
            "ALTER TABLE splatoon_battle_record ADD COLUMN duration INT DEFAULT NULL",
            "ALTER TABLE splatoon_battle_record ADD COLUMN knockout VARCHAR(8) DEFAULT NULL",
            "ALTER TABLE splatoon_battle_record ADD COLUMN my_score VARCHAR(16) DEFAULT NULL",
            "ALTER TABLE splatoon_battle_record ADD COLUMN other_score VARCHAR(16) DEFAULT NULL",
            "ALTER TABLE splatoon_battle_record ADD COLUMN awards VARCHAR(255) DEFAULT NULL",
            // 对战玩家:三件装备主技能概要(如 "防御↑ 墨耗↓ 人速↑")
            "ALTER TABLE splatoon_battle_user_detail ADD COLUMN gear_powers VARCHAR(255) DEFAULT NULL",
            // 打工记录:得分/熊先生点数/气味计(0-5)/Wave 概要文本
            "ALTER TABLE splatoon_coop_records ADD COLUMN job_score INT DEFAULT NULL",
            "ALTER TABLE splatoon_coop_records ADD COLUMN job_bonus INT DEFAULT NULL",
            "ALTER TABLE splatoon_coop_records ADD COLUMN smell_meter INT DEFAULT NULL",
            "ALTER TABLE splatoon_coop_records ADD COLUMN wave_info VARCHAR(255) DEFAULT NULL",
    };

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextRefreshed(ContextRefreshedEvent event) {
        if (!autoCreateTables) {
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : DDL) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    // 列已存在等：可忽略
                    log.debug("splatoon schema ALTER 跳过: {}", e.getMessage());
                }
            }
            log.info("Splatoon 战绩表列检查 / 补充完成");
        } catch (Exception e) {
            log.error("Splatoon 战绩表列初始化失败", e);
        }
    }
}
