package com.bb.bot.aiAgent.memory;

import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryCommandServiceTest {

    private IAiMemoryItemService itemService;
    private MemoryCommandService svc;

    @BeforeEach
    void setup() {
        itemService = mock(IAiMemoryItemService.class);
        svc = new MemoryCommandService();
        ReflectionTestUtils.setField(svc, "itemService", itemService);
    }

    private AiMemoryItem item(String key, String userId, String summary, String status) {
        AiMemoryItem c = new AiMemoryItem();
        c.setMemoryKey(key); c.setUserId(userId); c.setSummary(summary); c.setStatus(status);
        return c;
    }

    @Test
    void writeExplicit_preferenceCueGetsPreferenceTypeWithWhy() {
        svc.writeExplicit("u1", "我不喜欢长总结");
        ArgumentCaptor<AiMemoryItem> cap = ArgumentCaptor.forClass(AiMemoryItem.class);
        verify(itemService).save(cap.capture());
        AiMemoryItem row = cap.getValue();
        assertThat(row.getType()).isEqualTo("preference");
        assertThat(row.getScope()).isEqualTo("user");
        assertThat(row.getUserId()).isEqualTo("u1");
        assertThat(row.getWhy()).isNotBlank();
        assertThat(row.getHowToApply()).isNotBlank();
        assertThat(row.getConfidence().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void writeExplicit_plainFactGetsUserProfile() {
        svc.writeExplicit("u1", "我是后端工程师");
        ArgumentCaptor<AiMemoryItem> cap = ArgumentCaptor.forClass(AiMemoryItem.class);
        verify(itemService).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo("user_profile");
    }

    @Test
    void writeExplicit_blank_returnsHintNoSave() {
        String r = svc.writeExplicit("u1", "   ");
        assertThat(r).contains("没听清");
    }

    @Test
    void forget_marksMatchingDeleted() {
        AiMemoryItem a = item("m1", "u1", "我喜欢喝咖啡", "active");
        AiMemoryItem b = item("m2", "u1", "我用 Java", "active");
        when(itemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(a, b));
        int n = svc.forget("u1", "咖啡");
        assertThat(n).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo("deleted");
        assertThat(b.getStatus()).isEqualTo("active");
    }

    @Test
    void readableSelfMemory_formatsList() {
        when(itemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(item("m1", "u1", "我用 Java", "active")));
        String r = svc.readableSelfMemory("u1");
        assertThat(r).contains("我用 Java").contains("m1");
    }

    @Test
    void readableSelfMemory_empty() {
        when(itemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        assertThat(svc.readableSelfMemory("u1")).contains("还没记住");
    }

    @Test
    void softDelete_and_supersede() {
        AiMemoryItem it = item("m1", "u1", "x", "active");
        when(itemService.getOne(any())).thenReturn(it);
        assertThat(svc.softDelete("m1")).isTrue();
        assertThat(it.getStatus()).isEqualTo("deleted");

        AiMemoryItem old = item("m_old", "u1", "y", "active");
        when(itemService.getOne(any())).thenReturn(old);
        assertThat(svc.supersede("m_old", "m_new")).isTrue();
        assertThat(old.getStatus()).isEqualTo("superseded");
        assertThat(old.getSupersededBy()).isEqualTo("m_new");
    }
}
