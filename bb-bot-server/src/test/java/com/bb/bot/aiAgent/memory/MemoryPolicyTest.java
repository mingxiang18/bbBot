package com.bb.bot.aiAgent.memory;

import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.entity.AiMemorySession;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryPolicyTest {

    private IAiMemoryItemService itemService;
    private MemoryPolicy policy;

    @BeforeEach
    void setup() {
        itemService = mock(IAiMemoryItemService.class);
        policy = new MemoryPolicy();
        ReflectionTestUtils.setField(policy, "itemService", itemService);
        ReflectionTestUtils.setField(policy, "minConfidence", 0.6);
        ReflectionTestUtils.setField(policy, "projectStateTtlDays", 14);
    }

    private AiMemorySession session(String userId, String groupId) {
        AiMemorySession s = new AiMemorySession();
        s.setSessionId("s-test");
        s.setUserId(userId);
        s.setGroupId(groupId);
        return s;
    }

    private MemoryCandidate cand(String type, String scope, String summary) {
        MemoryCandidate c = new MemoryCandidate();
        c.setType(type);
        c.setScope(scope);
        c.setSummary(summary);
        c.setConfidence(0.9);
        c.setImportance(0.7);
        return c;
    }

    private AiMemoryItem captureSaved() {
        ArgumentCaptor<AiMemoryItem> cap = ArgumentCaptor.forClass(AiMemoryItem.class);
        verify(itemService).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void insert_validUserProfile() {
        MemoryCandidate c = cand("user_profile", "user", "用户用 Java");
        policy.apply(List.of(c), session("u1", null));
        AiMemoryItem row = captureSaved();
        assertThat(row.getType()).isEqualTo("user_profile");
        assertThat(row.getScope()).isEqualTo("user");
        assertThat(row.getUserId()).isEqualTo("u1");
        assertThat(row.getStatus()).isEqualTo("active");
        assertThat(row.getSearchText()).isNotBlank();
        assertThat(row.getMemoryKey()).startsWith("m_");
    }

    @Test
    void ignore_lowConfidence() {
        MemoryCandidate c = cand("user_profile", "user", "随口一提");
        c.setConfidence(0.4);
        policy.apply(List.of(c), session("u1", null));
        verify(itemService, never()).save(any());
    }

    @Test
    void ignore_preferenceMissingWhyHowApply() {
        MemoryCandidate c = cand("preference", "user", "偏好但没给理由");
        policy.apply(List.of(c), session("u1", null));
        verify(itemService, never()).save(any());
    }

    @Test
    void insert_preferenceWithWhyHowApply() {
        MemoryCandidate c = cand("preference", "user", "回复要简短");
        c.setWhy("啰嗦");
        c.setHowToApply("结尾干净");
        policy.apply(List.of(c), session("u1", null));
        assertThat(captureSaved().getType()).isEqualTo("preference");
    }

    @Test
    void scopeDowngrade_groupCandidateInPrivateBecomesUser() {
        MemoryCandidate c = cand("inside_joke", "group", "某梗");
        policy.apply(List.of(c), session("u1", null)); // 私聊 session：groupId null
        AiMemoryItem row = captureSaved();
        assertThat(row.getScope()).isEqualTo("user");
        assertThat(row.getGroupId()).isNull();
        assertThat(row.getUserId()).isEqualTo("u1");
    }

    @Test
    void groupScope_inGroupSession_keepsGroup() {
        MemoryCandidate c = cand("inside_joke", "group", "群梗");
        policy.apply(List.of(c), session("u1", "g1"));
        AiMemoryItem row = captureSaved();
        assertThat(row.getScope()).isEqualTo("group");
        assertThat(row.getGroupId()).isEqualTo("g1");
        assertThat(row.getUserId()).isNull();
    }

    @Test
    void projectState_getsDefault14dExpiry() {
        MemoryCandidate c = cand("project_state", "user", "不上向量库");
        c.setWhy("维护成本");
        c.setHowToApply("不建议向量库");
        policy.apply(List.of(c), session("u1", null));
        AiMemoryItem row = captureSaved();
        assertThat(row.getExpiresAt()).isNotNull();
        assertThat(row.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
        assertThat(row.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(15));
    }

    @Test
    void refresh_onDuplicate_updatesInsteadOfInsert() {
        AiMemoryItem existing = new AiMemoryItem();
        existing.setMemoryKey("m_old");
        existing.setType("user_profile");
        existing.setScope("user");
        existing.setUserId("u1");
        existing.setStatus("active");
        existing.setSummary("用户用 Java");
        when(itemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(existing));

        policy.apply(List.of(cand("user_profile", "user", "用户用 Java")), session("u1", null));

        verify(itemService, never()).save(any());
        verify(itemService).updateById(existing);
        assertThat(existing.getLastSeenAt()).isNotNull();
    }

    @Test
    void supersede_marksOldAndInsertsNew() {
        AiMemoryItem old = new AiMemoryItem();
        old.setMemoryKey("m_old");
        old.setStatus("active");
        when(itemService.getOne(any())).thenReturn(old);

        MemoryCandidate c = cand("preference", "user", "新偏好");
        c.setWhy("w"); c.setHowToApply("h");
        c.setSupersedesKey("m_old");
        policy.apply(List.of(c), session("u1", null));

        assertThat(old.getStatus()).isEqualTo("superseded");
        assertThat(old.getSupersededBy()).isNotNull();
        verify(itemService).updateById(old);
        verify(itemService, times(1)).save(any());
    }
}
