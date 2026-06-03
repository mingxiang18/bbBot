package com.bb.bot.aiAgent.memory;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemorySelectorTest {

    private IAiMemoryItemService itemService;
    private AiChatService aiChatService;
    private MemorySelector selector;

    @BeforeEach
    void setup() {
        itemService = mock(IAiMemoryItemService.class);
        aiChatService = mock(AiChatService.class);
        selector = new MemorySelector();
        ReflectionTestUtils.setField(selector, "itemService", itemService);
        ReflectionTestUtils.setField(selector, "aiChatService", aiChatService);
        ReflectionTestUtils.setField(selector, "maxInject", 8);
        ReflectionTestUtils.setField(selector, "selectorTimeoutMs", 1500L);
        ReflectionTestUtils.setField(selector, "candidateCap", 80);
    }

    private AiMemoryItem card(String key, String type, String scope, String userId, String groupId, String summary) {
        AiMemoryItem c = new AiMemoryItem();
        c.setMemoryKey(key);
        c.setType(type);
        c.setScope(scope);
        c.setUserId(userId);
        c.setGroupId(groupId);
        c.setSummary(summary);
        c.setStatus("active");
        c.setSearchText(FactStore.normalize(summary));
        c.setLastSeenAt(LocalDateTime.now());
        return c;
    }

    // ---- eligible 矩阵 ----

    @Test
    void eligible_privateExcludesGroupScopes() {
        assertThat(selector.eligible(card("k","inside_joke","group",null,"g1","x"), "u1", null, false)).isFalse();
        assertThat(selector.eligible(card("k","inside_joke","user_in_group","u1","g1","x"), "u1", null, false)).isFalse();
        assertThat(selector.eligible(card("k","user_profile","user","u1",null,"x"), "u1", null, false)).isTrue();
        assertThat(selector.eligible(card("k","reference","global",null,null,"x"), "u1", null, false)).isTrue();
    }

    @Test
    void eligible_inGroupRespectsOwnership() {
        assertThat(selector.eligible(card("k","inside_joke","group",null,"g1","x"), "u1", "g1", true)).isTrue();
        // 别的群
        assertThat(selector.eligible(card("k","inside_joke","group",null,"gX","x"), "u1", "g1", true)).isFalse();
        // 本群但别人的 user_in_group
        assertThat(selector.eligible(card("k","inside_joke","user_in_group","u2","g1","x"), "u1", "g1", true)).isFalse();
        // 本群本人 user_in_group
        assertThat(selector.eligible(card("k","inside_joke","user_in_group","u1","g1","x"), "u1", "g1", true)).isTrue();
    }

    // ---- staleness ----

    @Test
    void staleness_warnsForStaleAndVolatile_notStablePref() {
        AiMemoryItem stale = card("k","user_profile","user","u1",null,"x");
        stale.setStatus("stale");
        assertThat(selector.stalenessWarning(stale)).contains("可能过期").contains("核实");

        AiMemoryItem ps = card("k","project_state","user","u1",null,"x");
        ps.setLastSeenAt(LocalDateTime.now().minusDays(5));
        assertThat(selector.stalenessWarning(ps)).contains("核实");

        AiMemoryItem pref = card("k","preference","user","u1",null,"x");
        pref.setLastSeenAt(LocalDateTime.now().minusDays(5));
        assertThat(selector.stalenessWarning(pref)).isNull();
    }

    // ---- tokenize / fallback ----

    @Test
    void tokenize_dropsShortTokens() {
        assertThat(MemorySelector.tokenize("Java Spring 后端")).contains("java", "spring");
    }

    @Test
    void fallbackTopN_ordersByImportanceThenLastSeen() {
        AiMemoryItem hi = card("hi","user_profile","user","u1",null,"a"); hi.setImportance(new BigDecimal("0.9"));
        AiMemoryItem lo = card("lo","user_profile","user","u1",null,"b"); lo.setImportance(new BigDecimal("0.2"));
        List<AiMemoryItem> out = selector.fallbackTopN(List.of(lo, hi));
        assertThat(out.get(0).getMemoryKey()).isEqualTo("hi");
    }

    // ---- composeMemoryBlock 集成：小集合不调模型 + scope 隔离 ----

    @Test
    void composeMemoryBlock_privateScopeIsolation_andNoModelCall() {
        List<AiMemoryItem> mixed = List.of(
                card("u","user_profile","user","u1",null,"用户用 Java"),
                card("g","inside_joke","group",null,"g1","群梗"),
                card("uig","inside_joke","user_in_group","u1","g1","船长"),
                card("glob","reference","global",null,null,"全局红线"));
        when(itemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(mixed);

        String block = selector.composeMemoryBlock("u1", null, "bb", "随便聊聊");

        assertThat(block).contains("--- Memory Index ---");
        assertThat(block).contains("u").contains("glob");      // user + global 注入
        assertThat(block).doesNotContain("群梗").doesNotContain("船长"); // group / user_in_group 不漏
        verify(aiChatService, never()).chat(any(), any(ModelTier.class)); // ≤8 候选不调模型
    }

    @Test
    void composeMemoryBlock_noCandidates_returnsEmpty() {
        when(itemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        assertThat(selector.composeMemoryBlock("u1", null, "bb", "hi")).isEmpty();
    }
}
