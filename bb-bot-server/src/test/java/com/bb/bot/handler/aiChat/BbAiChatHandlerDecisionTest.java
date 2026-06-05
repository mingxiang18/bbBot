package com.bb.bot.handler.aiChat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.common.util.aiChat.billing.GlobalUsageGuard;
import com.bb.bot.common.util.aiChat.billing.QuotaGuard;
import com.bb.bot.common.util.aiChat.prompt.PromptProperties;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 覆盖 {@link BbAiChatHandler#decideShouldReply} 与 {@link BbAiChatHandler#composePersonality}
 * 这两条纯逻辑路径，全部依赖 mock。
 *
 * <p>线索机制已下线：群聊随机回复只靠「是否开启自动回复 + 概率」门控，不再查线索；
 * personality 不再拼 clue 后缀，记忆由 selector / compiler 自动注入。</p>
 */
@ExtendWith(MockitoExtension.class)
class BbAiChatHandlerDecisionTest {

    @Mock
    IUserConfigValueService userConfigValueService;

    @Mock
    GlobalUsageGuard globalUsageGuard;

    @Mock
    QuotaGuard quotaGuard;

    @Mock
    BbReplies bbReplies;

    @InjectMocks
    BbAiChatHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "promptProperties", buildPromptProps());
        ReflectionTestUtils.setField(handler, "autoReplyRate", 0.99);
        ReflectionTestUtils.setField(handler, "chatHistoryNum", 5);
    }

    @Test
    void privateMessage_alwaysReplies() {
        BbReceiveMessage msg = newMessage(MessageType.PRIVATE);
        ReplyDecision decision = handler.decideShouldReply(msg);
        assertTrue(decision.isShouldReply());
        assertTrue(decision.isDirectTrigger());
        verifyNoInteractions(userConfigValueService);
    }

    @Test
    void groupMessage_atBot_alwaysReplies_directTrigger() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(List.of(botUser()));

        ReplyDecision decision = handler.decideShouldReply(msg);

        assertTrue(decision.isShouldReply());
        assertTrue(decision.isDirectTrigger());
        // @机器人 必回，不查任何配置
        verifyNoInteractions(userConfigValueService);
    }

    @Test
    void groupMessage_notAt_noAutoConfig_skips() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ReplyDecision decision = handler.decideShouldReply(msg);

        assertFalse(decision.isShouldReply());
    }

    @Test
    void groupMessage_notAt_autoOn_aboveThreshold_replies() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(new UserConfigValue());
        // autoReplyRate=0.99，随机数 0.999 > 0.99 → reply（非直接触发）
        DoubleSupplier rng = () -> 0.999d;
        ReflectionTestUtils.setField(handler, "randomSource", rng);

        ReplyDecision decision = handler.decideShouldReply(msg);
        assertTrue(decision.isShouldReply());
        assertFalse(decision.isDirectTrigger());
    }

    @Test
    void groupMessage_notAt_autoOn_belowThreshold_skips() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(new UserConfigValue());
        DoubleSupplier rng = () -> 0.5d;
        ReflectionTestUtils.setField(handler, "randomSource", rng);

        assertFalse(handler.decideShouldReply(msg).isShouldReply());
    }

    @Test
    void unknownMessageType_skips() {
        BbReceiveMessage msg = newMessage("unknown");
        assertFalse(handler.decideShouldReply(msg).isShouldReply());
    }

    @Test
    void composePersonality_returnsBaseWithBoundaryGuidanceOnly() {
        // 无长期记忆(memorySelector/memoryCompiler 未注入，调用触发的 NPE 被 catch 吞掉，不注入记忆段)
        // → personality 应为 BASE 本体 + 末尾无条件追加的【对话边界】引导，无其它内容。
        String personality = handler.composePersonality("user-1", null, null, null, false, false);
        assertTrue(personality.startsWith("BASE"), "应以 BASE 人格本体开头");
        assertFalse(personality.contains("长期记忆"), "未提供长期记忆时不应注入记忆段");
        assertEquals("BASE\n\n", personality.substring(0, personality.indexOf("【对话边界】")),
                "BASE 与边界引导之间不应有额外内容");
    }

    @Test
    void composePersonality_autoReply_appendsBrevitySuffix_directDoesNot() {
        // 群里概率插话(autoReply=true) → 末尾追加极简后缀；@机器人/私聊(false) → 不追加。
        String auto = handler.composePersonality("user-1", null, null, null, false, true);
        String direct = handler.composePersonality("user-1", null, null, null, false, false);
        assertTrue(auto.contains("AUTOSUFFIX"), "随机插话应追加极简后缀");
        assertFalse(direct.contains("AUTOSUFFIX"), "@机器人/私聊不应追加极简后缀");
    }

    // =====================================================================
    // passesUsageGuards：早返各分支（超日限 / 超配额 / 直接触发与否）
    // =====================================================================

    @Test
    void passesUsageGuards_underAllLimits_returnsTrue() {
        BbReceiveMessage msg = newMessage(MessageType.PRIVATE);
        when(globalUsageGuard.isOverDailyLimit()).thenReturn(false);
        when(quotaGuard.isOverLimit(anyString(), any())).thenReturn(false);

        assertTrue(handler.passesUsageGuards(msg, ReplyDecision.replyDirect()));
        verifyNoInteractions(bbReplies);
    }

    @Test
    void passesUsageGuards_overDailyLimit_directTrigger_repliesAndBlocks() {
        BbReceiveMessage msg = newMessage(MessageType.PRIVATE);
        when(globalUsageGuard.isOverDailyLimit()).thenReturn(true);

        assertFalse(handler.passesUsageGuards(msg, ReplyDecision.replyDirect()));
        verify(bbReplies).atText(eq(msg), anyString());
        // 月度配额检查不应再触达（已在日限处早返）
        verifyNoInteractions(quotaGuard);
    }

    @Test
    void passesUsageGuards_overDailyLimit_autoReply_silentBlock() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        when(globalUsageGuard.isOverDailyLimit()).thenReturn(true);

        // 概率自动回复（非 directTrigger）：静默跳过，不回提示，避免刷屏
        assertFalse(handler.passesUsageGuards(msg, ReplyDecision.reply()));
        verifyNoInteractions(bbReplies);
        verifyNoInteractions(quotaGuard);
    }

    @Test
    void passesUsageGuards_overQuota_directTrigger_repliesAndBlocks() {
        BbReceiveMessage msg = newMessage(MessageType.PRIVATE);
        when(globalUsageGuard.isOverDailyLimit()).thenReturn(false);
        when(quotaGuard.isOverLimit(anyString(), any())).thenReturn(true);
        when(quotaGuard.status(anyString(), any()))
                .thenReturn(new QuotaGuard.QuotaStatus("2026-05", new BigDecimal("10"), new BigDecimal("10")));

        assertFalse(handler.passesUsageGuards(msg, ReplyDecision.replyDirect()));
        verify(bbReplies).atText(eq(msg), anyString());
    }

    @Test
    void passesUsageGuards_overQuota_autoReply_silentBlock() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        when(globalUsageGuard.isOverDailyLimit()).thenReturn(false);
        when(quotaGuard.isOverLimit(anyString(), any())).thenReturn(true);

        // 群里概率自动回复超配额：静默跳过，不读 status、不回提示
        assertFalse(handler.passesUsageGuards(msg, ReplyDecision.reply()));
        verifyNoInteractions(bbReplies);
    }

    private static BbReceiveMessage newMessage(String type) {
        BbReceiveMessage msg = new BbReceiveMessage();
        msg.setMessageType(type);
        msg.setUserId("user-1");
        msg.setGroupId("group-1");
        msg.setMessage("hi");
        msg.setSender(new MessageUser("user-1", "alice"));
        msg.setAtUserList(new ArrayList<>());
        return msg;
    }

    private static MessageUser botUser() {
        MessageUser bot = new MessageUser("bot", "bb");
        bot.setBotFlag(true);
        return bot;
    }

    private static PromptProperties buildPromptProps() {
        PromptProperties p = new PromptProperties();
        p.getAiChat().setPersonality("BASE");
        p.getAiChat().setAutoReplySuffix("AUTOSUFFIX");
        return p;
    }
}
