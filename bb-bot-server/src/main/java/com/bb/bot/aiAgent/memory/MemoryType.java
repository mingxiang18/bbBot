package com.bb.bot.aiAgent.memory;

import org.apache.commons.lang3.StringUtils;

/**
 * 结构化记忆卡片类型。骨架对齐 Claude Code 的 4 类（user/feedback/project/reference），
 * 群聊差异主要交给 {@link MemoryScope} 表达，只额外保留 CC 没有、群聊确实独有的两类。
 *
 * <ul>
 *   <li>{@link #USER_PROFILE} 用户画像（CC: user）：身份、技能、长期兴趣，长期有效低频刷新</li>
 *   <li>{@link #PREFERENCE} 用户偏好（CC: feedback）：行为规则，必须有 why + howToApply，可被覆盖</li>
 *   <li>{@link #PROJECT_STATE} 项目状态（CC: project）：当前决策/进度，强老化，默认 14 天过期</li>
 *   <li>{@link #REFERENCE} 外部指针（CC: reference）：去哪查什么，长期但用前可校验</li>
 *   <li>{@link #INSIDE_JOKE} 群梗：仅群内自然使用</li>
 *   <li>{@link #EPHEMERAL_EVENT} 临时事件：1-3 天后自动 stale/归档</li>
 * </ul>
 */
public enum MemoryType {
    USER_PROFILE,
    PREFERENCE,
    PROJECT_STATE,
    REFERENCE,
    INSIDE_JOKE,
    EPHEMERAL_EVENT;

    /** 宽松解析 LLM 给的字符串（大小写/别名容错）；无法识别返回 null。 */
    public static MemoryType parse(String raw) {
        if (StringUtils.isBlank(raw)) return null;
        String v = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "user_profile", "user", "profile" -> USER_PROFILE;
            case "preference", "feedback", "pref" -> PREFERENCE;
            case "project_state", "project", "project_status" -> PROJECT_STATE;
            case "reference", "ref", "pointer" -> REFERENCE;
            case "inside_joke", "joke", "meme" -> INSIDE_JOKE;
            case "ephemeral_event", "ephemeral", "event", "temp" -> EPHEMERAL_EVENT;
            default -> null;
        };
    }

    public String code() {
        return name().toLowerCase();
    }
}
