package com.bb.bot.aiAgent.memory;

import org.apache.commons.lang3.StringUtils;

/**
 * 记忆作用域。群聊机器人必须靠它防止"群内称呼/梗/上下文带到私聊或其他群"。
 *
 * <ul>
 *   <li>{@link #GLOBAL} 全局：对所有用户/群生效（如安全红线）。user_id / group_id 都为空</li>
 *   <li>{@link #USER} 某用户的所有对话：user_id 非空，group_id 空</li>
 *   <li>{@link #GROUP} 某群的公共记忆：group_id 非空，user_id 空</li>
 *   <li>{@link #USER_IN_GROUP} 某人在某群的关系/梗：user_id + group_id 均非空</li>
 * </ul>
 */
public enum MemoryScope {
    GLOBAL,
    USER,
    GROUP,
    USER_IN_GROUP;

    public static MemoryScope parse(String raw) {
        if (StringUtils.isBlank(raw)) return null;
        String v = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "global" -> GLOBAL;
            case "user" -> USER;
            case "group" -> GROUP;
            case "user_in_group", "user_group", "usergroup" -> USER_IN_GROUP;
            default -> null;
        };
    }

    public boolean needsGroup() {
        return this == GROUP || this == USER_IN_GROUP;
    }

    public boolean needsUser() {
        return this == USER || this == USER_IN_GROUP;
    }

    public String code() {
        return name().toLowerCase();
    }
}
