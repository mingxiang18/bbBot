package com.bb.bot.handler.aiChat;

/** 本轮对话可使用的工具范围，与选择哪个模型分别决定。 */
public enum ToolRouteMode {

    /** 普通聊天，不开放工具。 */
    NONE,

    /** 复杂任务，开放全部已注册工具。 */
    ALL_TOOLS,

    /** 已识别领域，只开放对应的工具。 */
    SELECTED_TOOLS,

    /** 当前模型需要图片描述时，仅开放 analyze_image。 */
    IMAGE_ONLY
}
