package com.bb.bot.handler.aiChat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.util.aiChat.prompt.PromptProperties;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.aiKeywordAndClue.service.IAiClueService;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 覆盖 {@link BbAiChatHandler#decideShouldReply} 与 {@link BbAiChatHandler#composePersonality}
 * 这两条纯逻辑路径，全部依赖 mock。
 */
@ExtendWith(MockitoExtension.class)
class BbAiChatHandlerDecisionTest {

    @Mock
    IAiClueService aiClueService;

    @Mock
    IUserConfigValueService userConfigValueService;

    @InjectMocks
    BbAiChatHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "promptProperties", buildPromptProps());
        ReflectionTestUtils.setField(handler, "autoReplyRate", 0.99);
        ReflectionTestUtils.setField(handler, "chatHistoryNum", 5);
        ReflectionTestUtils.setField(handler, "adminUserIds", List.of("admin-user"));
    }

    @Test
    void privateMessage_alwaysReplies() {
        BbReceiveMessage msg = newMessage(MessageType.PRIVATE);
        ReplyDecision decision = handler.decideShouldReply(msg);
        assertTrue(decision.isShouldReply());
        verifyNoInteractions(userConfigValueService, aiClueService);
    }

    @Test
    void groupMessage_atBot_alwaysReplies_andLoadsClues() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(List.of(botUser()));
        when(aiClueService.selectClue(anyString())).thenReturn(List.of("clue-1"));

        ReplyDecision decision = handler.decideShouldReply(msg);

        assertTrue(decision.isShouldReply());
        assertEquals(List.of("clue-1"), decision.getClues());
        verify(aiClueService).selectClue(anyString());
    }

    @Test
    void groupMessage_notAt_noAutoConfig_skips() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ReplyDecision decision = handler.decideShouldReply(msg);

        assertFalse(decision.isShouldReply());
        verifyNoInteractions(aiClueService);
    }

    @Test
    void groupMessage_notAt_autoOn_noClues_skips() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(new UserConfigValue());
        when(aiClueService.selectClue(anyString())).thenReturn(Collections.emptyList());

        assertFalse(handler.decideShouldReply(msg).isShouldReply());
    }

    @Test
    void groupMessage_notAt_autoOn_cluesPresent_aboveThreshold_replies() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(new UserConfigValue());
        when(aiClueService.selectClue(anyString())).thenReturn(List.of("c"));
        // autoReplyRate=0.99，随机数 0.999 > 0.99 → reply
        DoubleSupplier rng = () -> 0.999d;
        ReflectionTestUtils.setField(handler, "randomSource", rng);

        assertTrue(handler.decideShouldReply(msg).isShouldReply());
    }

    @Test
    void groupMessage_notAt_autoOn_cluesPresent_belowThreshold_skips() {
        BbReceiveMessage msg = newMessage(MessageType.GROUP);
        msg.setAtUserList(Collections.emptyList());
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class))).thenReturn(new UserConfigValue());
        when(aiClueService.selectClue(anyString())).thenReturn(List.of("c"));
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
    void composePersonality_noClues_returnsBaseOnly() {
        String personality = handler.composePersonality("user-1", Collections.emptyList());
        assertEquals("BASE", personality);
    }

    @Test
    void composePersonality_withClues_appendsRenderedSuffix() {
        String personality = handler.composePersonality("user-1", List.of("c1", "c2"));
        assertTrue(personality.contains("BASE"));
        assertTrue(personality.contains("c1-c2"));
        assertFalse(personality.contains("{clues}"), "placeholder should be replaced");
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
        p.getAiChat().setClueSuffix("clues={clues}");
        return p;
    }
}
