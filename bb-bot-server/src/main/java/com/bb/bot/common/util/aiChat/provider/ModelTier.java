package com.bb.bot.common.util.aiChat.provider;

/**
 * 模型层级 / 角色。一次调用按层级路由到不同 model，降低成本。
 *
 * <ul>
 *   <li>{@link #CHAT}：重模型（角色 heavy），干活 / 复杂任务 + 工具循环</li>
 *   <li>{@link #LIGHT}：轻模型（角色 light），闲聊 / 简单问答 / 廉价分类 / 内部总结；未配置则回退 heavy</li>
 *   <li>{@link #VISION}：视觉模型（角色 vision），主模型无视觉时由 {@link VisionBridge} 把图片转成文字描述</li>
 * </ul>
 *
 * @author ren
 */
public enum ModelTier {
    CHAT,
    LIGHT,
    VISION
}
