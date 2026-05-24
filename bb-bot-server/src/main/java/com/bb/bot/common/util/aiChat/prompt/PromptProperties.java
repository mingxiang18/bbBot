package com.bb.bot.common.util.aiChat.prompt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全部 AI prompt 的注册表，{@code prompts.*} 配置加载到这里。
 * 之前散落在 {@code @Value} 默认值里的几十行长 prompt 全部移过来。
 *
 * <p>Handler 通过 {@code @Autowired PromptProperties props} 注入后调用
 * {@code props.getAiChat().getPersonality()} 等方法。
 *
 * @author ren
 */
@Data
@Component
@ConfigurationProperties(prefix = "prompts")
public class PromptProperties {

    private AiChatPrompts aiChat = new AiChatPrompts();
    private ChatHistoryPrompts chatHistory = new ChatHistoryPrompts();
    private HaiguitangPrompts haiguitang = new HaiguitangPrompts();

    @Data
    public static class AiChatPrompts {
        private String personality;
        /** 含 {clues} 占位符。当查询到 clueList 时追加在 personality 之后。 */
        private String clueSuffix;
    }

    @Data
    public static class ChatHistoryPrompts {
        private String summary;
        private String characteristic;
    }

    @Data
    public static class HaiguitangPrompts {
        /** 含 {question} {answer} 占位符，每轮猜谜时渲染。 */
        private String judge;
        private String generate;
    }
}
