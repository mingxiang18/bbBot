package com.bb.bot.common.util.aiChat.prompt;

import java.util.Map;

/**
 * 占位符替换工具。简单 {@code String.replace}，但集中、可测、未来可换成 freemarker。
 *
 * @author ren
 */
public final class PromptRenderer {

    private PromptRenderer() {}

    /**
     * 将 template 中的 {key} 占位符替换为 vars 中对应的值。
     * 不存在的占位符保留原样；vars 中多余的 key 被忽略。
     *
     * @param template 含 {xxx} 占位符的模板，可为 null
     * @param vars 变量映射，可为 null
     * @return 渲染后字符串，若 template 为 null 则返回空串
     */
    public static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + e.getKey() + "}", value);
        }
        return out;
    }
}
