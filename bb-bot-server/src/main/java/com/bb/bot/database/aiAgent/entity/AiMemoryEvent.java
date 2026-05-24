package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 记忆 / 对话事件流。
 *
 * <p>统一记录用户、bot、系统、工具产生的全部事件，并按 source / kind 分类，
 * 实现"完整记录"和"上下文按类型筛选"的分离：</p>
 * <ul>
 *   <li>所有 event 都进表（包括 agent 命令、admin 命令、工具调用）→ 完整可审计</li>
 *   <li>BbAiChatHandler 等只读取 kind ∈ {chat, chat_reply} 的当前 session 事件作为上下文</li>
 *   <li>30 分钟无消息自动切新 session，避免跨主题污染</li>
 * </ul>
 */
@Data
@TableName("ai_memory_event")
public class AiMemoryEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话 id：(user_id, group_id) 维度，30 分钟无消息切新 session。 */
    private String sessionId;

    /** 平台标识（BotType 字符串）。 */
    private String platform;

    private String userId;
    private String groupId;
    private String userName;

    /** 谁发的：user / bot / system / tool。 */
    private String source;

    /**
     * 事件类型，决定哪些上下文加载器会拉它：
     * <ul>
     *   <li>chat —— 用户自由聊天（不带 agent / 命令前缀）</li>
     *   <li>chat_reply —— bot 的聊天人格回复</li>
     *   <li>agent_cmd —— 用户的 agent xxx 派活</li>
     *   <li>agent_reply —— agent 模式最终回复（不包含工具内部循环）</li>
     *   <li>admin_cmd —— /aiAgent.* 等管理命令</li>
     *   <li>admin_response —— 管理命令的回复</li>
     *   <li>cron_fire —— 定时任务触发</li>
     *   <li>tool_invocation —— 工具调用摘要（详情仍在 ai_tool_invocation_log）</li>
     *   <li>fallback —— 错误兜底回复</li>
     * </ul>
     */
    private String kind;

    /** 平台原始 message id。 */
    private String messageId;

    /** 主要文本内容。 */
    private String text;

    /** 结构化补充数据（JSON 字符串）。比如 tool 调用的 args/result 摘要、admin 命令的解析结果。 */
    private String payload;

    /** 关联的事件 id（这是哪条 event 的回复 / 后续）。 */
    private Long replyToEventId;

    private LocalDateTime createdAt;
}
