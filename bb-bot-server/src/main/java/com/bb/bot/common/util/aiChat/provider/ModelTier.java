package com.bb.bot.common.util.aiChat.provider;

/**
 * 模型层级 / 角色。一次调用按层级路由到不同 model，降低成本。
 *
 * <ul>
 *   <li>{@link #CHAT}：重 / 高级模型，面向用户的对话与工具循环默认走它（= activeProvider + 其 model，不可被 tier 覆盖）</li>
 *   <li>{@link #LIGHT}：轻 / 低级模型，用于廉价分类、内部总结、记忆压缩</li>
 *   <li>{@link #VISION}：多模态模型，主模型无视觉时由 {@link VisionBridge} 用它把图片转成文字描述</li>
 * </ul>
 *
 * @author ren
 */
public enum ModelTier {
    CHAT,
    LIGHT,
    VISION
}
