package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次对话会话（30 分钟无消息切新）。
 *
 * <p>对应 openhanako 的 session 概念：</p>
 * <ul>
 *   <li>started_at / ended_at 记录会话起止</li>
 *   <li>ended_at 由 SessionTracker @Scheduled 扫描标记</li>
 *   <li>session 结束后跑 LLM 蒸馏写 summary</li>
 *   <li>MemoryCompiler 的 today/week/longterm/facts 都吃 summary 当源</li>
 * </ul>
 */
@Data
@TableName("ai_memory_session")
public class AiMemorySession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** session 唯一 id（UUID 或 timestamp-rand） */
    private String sessionId;

    private String userId;
    private String groupId;
    private String platform;

    private LocalDateTime startedAt;

    /** 最近一条事件的时间。会话复用 / sweep 都基于它（而非 started_at），避免活跃聊天被误切。 */
    private LocalDateTime lastEventAt;

    /** null 表示 session 仍在进行中 */
    private LocalDateTime endedAt;

    /** 这个 session 累计消息数（粗略） */
    private Integer messageCount;

    /** session 结束后由 LLM 写的会话摘要（markdown） */
    private String summary;

    /** summary 编译完成时间，用于 fingerprint */
    private LocalDateTime summaryCompiledAt;
}
