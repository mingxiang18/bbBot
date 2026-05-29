package com.bb.bot.common.util.aiChat.provider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link AiChatService} 按角色（{@link ModelTier}）解析 {@link ModelSpec} 并委托底层
 * {@link AIProvider} 的逻辑。
 *
 * <p>架构变更说明：旧版 {@code AiChatService} 持有一组 {@code AIProvider} 并按
 * {@code properties.activeProvider} 名字挑选（{@code setActiveProvider} / {@code current()}）。
 * 新架构改为注入唯一 provider，每次调用由 {@code ai.roles}（heavy/light/vision）→
 * {@code ai.models} 解析出 {@link ModelSpec} 决定用哪个模型；LIGHT/VISION 未配置回退 HEAVY；
 * heavy 不可用时阻塞调用返回 {@code null}、流式调用回调 {@code onError(UNAUTHORIZED)}。
 * 因此原"按激活名选 provider"的断言改写为"按角色解析出正确 spec 并委托"。</p>
 */
class AiChatServiceTest {

    // ---- 构造辅助 ----

    private static ModelSpec spec(String name, String apiKey, String model, boolean vision) {
        ModelSpec s = new ModelSpec();
        s.setName(name);
        s.setApiKey(apiKey);
        s.setModel(model);
        s.setVision(vision);
        return s;
    }

    /** 一个 apiKey + model 齐全的可用 spec。 */
    private static ModelSpec configured(String name) {
        return spec(name, "sk-" + name, name + "-model", false);
    }

    private static AiChatService newService(AIProvider provider, AIProviderProperties props) throws Exception {
        AiChatService service = new AiChatService(provider, props);
        // visionBridge 是 @Autowired @Lazy 字段，单测无容器；注入一个原样返回的桥接，避免 NPE 且不改变消息。
        AbstractOpenAICompatibleProviderTest.inject(service, "visionBridge", new VisionBridge() {
            @Override
            public List<ChatMessage> bridgeIfNeeded(List<ChatMessage> messages, ModelSpec mainSpec) {
                return messages;
            }
        });
        return service;
    }

    private static List<ChatMessage> hi() {
        return List.of(ChatMessage.user("hi"));
    }

    // ---- chat：heavy 解析 ----

    @Test
    void chat_usesHeavySpecAndReturnsProviderResult() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");

        StubProvider provider = new StubProvider("from-heavy");
        AiChatService service = newService(provider, props);

        assertEquals("from-heavy", service.chat(hi()));
        assertEquals(1, provider.calls);
        assertEquals("ds-pro", provider.lastSpec.getName());
    }

    @Test
    void chat_returnsNullWhenHeavyRoleUnset() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        // 未设置 roles.heavy

        StubProvider provider = new StubProvider("ignored");
        AiChatService service = newService(provider, props);

        assertNull(service.chat(hi()));
        assertEquals(0, provider.calls, "无可用模型时不应调用 provider");
        assertFalse(service.isConfigured());
    }

    @Test
    void chat_returnsNullWhenHeavyModelNameNotInModels() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getRoles().setHeavy("doesNotExist");

        StubProvider provider = new StubProvider("ignored");
        AiChatService service = newService(provider, props);

        assertNull(service.chat(hi()));
        assertEquals(0, provider.calls);
    }

    @Test
    void chat_returnsNullWhenHeavySpecNotConfigured() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        // model 缺失 → isConfigured() == false
        props.getModels().put("ds-pro", spec("ds-pro", "sk-x", " ", false));
        props.getRoles().setHeavy("ds-pro");

        StubProvider provider = new StubProvider("ignored");
        AiChatService service = newService(provider, props);

        assertNull(service.chat(hi()));
        assertEquals(0, provider.calls, "spec 未配置齐全时不应调用 provider");
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_trueWhenHeavyConfigured() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");

        AiChatService service = newService(new StubProvider("x"), props);
        assertTrue(service.isConfigured());
    }

    // ---- chat：tier 解析与回退 ----

    @Test
    void chat_lightFallsBackToHeavyWhenLightUnset() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");
        // 未设置 light

        StubProvider provider = new StubProvider("ok");
        AiChatService service = newService(provider, props);

        service.chat(hi(), ModelTier.LIGHT);
        assertEquals("ds-pro", provider.lastSpec.getName(), "light 未配置应回退 heavy");
    }

    @Test
    void chat_lightUsesLightSpecWhenSet() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getModels().put("ds-flash", configured("ds-flash"));
        props.getRoles().setHeavy("ds-pro");
        props.getRoles().setLight("ds-flash");

        StubProvider provider = new StubProvider("ok");
        AiChatService service = newService(provider, props);

        service.chat(hi(), ModelTier.LIGHT);
        assertEquals("ds-flash", provider.lastSpec.getName());
    }

    @Test
    void chat_visionFallsBackToHeavyWhenVisionUnset() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");

        StubProvider provider = new StubProvider("ok");
        AiChatService service = newService(provider, props);

        service.chat(hi(), ModelTier.VISION);
        assertEquals("ds-pro", provider.lastSpec.getName(), "vision 未配置应回退 heavy");
    }

    @Test
    void specForTier_resolvesEachRole() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getModels().put("ds-flash", configured("ds-flash"));
        props.getModels().put("kimi-v", spec("kimi-v", "sk-kimi", "moonshot-vision", true));
        props.getRoles().setHeavy("ds-pro");
        props.getRoles().setLight("ds-flash");
        props.getRoles().setVision("kimi-v");

        AiChatService service = newService(new StubProvider("x"), props);

        assertEquals("ds-pro", service.specForTier(ModelTier.CHAT).getName());
        assertEquals("ds-flash", service.specForTier(ModelTier.LIGHT).getName());
        assertEquals("kimi-v", service.specForTier(ModelTier.VISION).getName());
        assertSame(service.heavySpec(), service.specForTier(ModelTier.CHAT));
    }

    // ---- 异常透传 ----

    @Test
    void chat_propagatesProviderException() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");

        StubProvider provider = new StubProvider(null);
        provider.toThrow = new AIException(AIException.ErrorType.RATE_LIMITED, 429, "slow down");
        AiChatService service = newService(provider, props);

        AIException ex = assertThrows(AIException.class, () -> service.chat(hi()));
        assertSame(AIException.ErrorType.RATE_LIMITED, ex.getErrorType());
    }

    // ---- 流式 ----

    @Test
    void chatStream_onErrorWhenNoConfiguredModel() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        // heavy 未配置
        StubProvider provider = new StubProvider("ignored");
        AiChatService service = newService(provider, props);

        RecordingHandler handler = new RecordingHandler();
        service.chatStream(hi(), handler);

        assertEquals(0, provider.calls, "无可用模型不应委托 provider");
        assertNotNull(handler.error);
        assertSame(AIException.ErrorType.UNAUTHORIZED, ((AIException) handler.error).getErrorType());
        assertNull(handler.completedText);
    }

    @Test
    void chatStream_delegatesWhenConfigured() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");

        StubProvider provider = new StubProvider("streamed");
        AiChatService service = newService(provider, props);

        RecordingHandler handler = new RecordingHandler();
        service.chatStream(hi(), handler);

        assertEquals(1, provider.calls);
        assertEquals("ds-pro", provider.lastSpec.getName());
        assertEquals("streamed", handler.completedText);
        assertNull(handler.error);
    }

    // ---- accessors ----

    @Test
    void providerAccessor_returnsInjectedProvider() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");

        StubProvider provider = new StubProvider("x");
        AiChatService service = newService(provider, props);

        assertSame(provider, service.provider());
    }

    @Test
    void visionConfigured_trueOnlyWhenVisionRoleConfiguredAndVisionCapable() throws Exception {
        AIProviderProperties props = new AIProviderProperties();
        props.getModels().put("ds-pro", configured("ds-pro"));
        props.getRoles().setHeavy("ds-pro");
        AiChatService noVision = newService(new StubProvider("x"), props);
        assertFalse(noVision.visionConfigured(), "未配置 vision 角色");

        props.getModels().put("kimi-v", spec("kimi-v", "sk-kimi", "moonshot-vision", true));
        props.getRoles().setVision("kimi-v");
        AiChatService withVision = newService(new StubProvider("x"), props);
        assertTrue(withVision.visionConfigured());

        // vision 角色指向一个不支持视觉的模型 → 视为未就绪
        props.getModels().put("ds-flash", configured("ds-flash"));
        props.getRoles().setVision("ds-flash");
        AiChatService visionNotCapable = newService(new StubProvider("x"), props);
        assertFalse(visionNotCapable.visionConfigured());
    }

    private static void assertNotNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNotNull(o);
    }

    // ---- 测试替身 ----

    /** 记录被调用时的 spec / 次数，可配置返回或抛错。实现新版 {@link AIProvider} 接口。 */
    static class StubProvider implements AIProvider {
        final String reply;
        int calls = 0;
        ModelSpec lastSpec;
        AIException toThrow;

        StubProvider(String reply) {
            this.reply = reply;
        }

        @Override
        public String chat(ModelSpec spec, List<ChatMessage> messages) {
            calls++;
            lastSpec = spec;
            if (toThrow != null) {
                throw toThrow;
            }
            return reply;
        }

        @Override
        public void chatStream(ModelSpec spec, List<ChatMessage> messages,
                               List<ToolDefinition> tools, StreamHandler handler) {
            calls++;
            lastSpec = spec;
            if (toThrow != null) {
                handler.onError(toThrow);
                return;
            }
            handler.onTextDelta(reply);
            handler.onComplete(reply, "stop");
        }
    }

    /** 记录流式回调结果。 */
    static class RecordingHandler implements StreamHandler {
        final List<String> deltas = new ArrayList<>();
        String completedText;
        Throwable error;

        @Override
        public void onTextDelta(String delta) {
            deltas.add(delta);
        }

        @Override
        public void onComplete(String fullText, String finishReason) {
            completedText = fullText;
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }
    }
}
