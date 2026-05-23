package com.bb.bot.common.util.aiChat.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 {@link ModelSpec#getKind()} 把调用分发到具体 provider 实现：
 * {@code anthropic} → {@link AnthropicProvider}，其余（openai/deepseek/moonshot…）
 * → {@link OpenAiCompatProvider}。
 *
 * <p>标 {@link Primary}：上层 {@code AiChatService} / {@code ToolLoopExecutor} 注入的
 * 单个 {@link AIProvider} 就是本分发器，无需感知背后是哪家。新增厂商在这里加一条分支即可。</p>
 *
 * @author ren
 */
@Primary
@Component
public class ProviderDispatcher implements AIProvider {

    @Autowired
    private OpenAiCompatProvider openAiCompatProvider;

    @Autowired
    private AnthropicProvider anthropicProvider;

    private AIProvider resolve(ModelSpec spec) {
        if (spec != null && "anthropic".equalsIgnoreCase(spec.getKind())) {
            return anthropicProvider;
        }
        return openAiCompatProvider;
    }

    @Override
    public String chat(ModelSpec spec, List<ChatMessage> messages) throws AIException {
        return resolve(spec).chat(spec, messages);
    }

    @Override
    public void chatStream(ModelSpec spec,
                           List<ChatMessage> messages,
                           List<ToolDefinition> tools,
                           StreamHandler handler) throws AIException {
        resolve(spec).chatStream(spec, messages, tools, handler);
    }
}
