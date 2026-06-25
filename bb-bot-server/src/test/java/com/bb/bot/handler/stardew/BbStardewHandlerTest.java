package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.BbReplies;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BbStardewHandlerTest {

    @Mock
    private StardewGuideAssistantService guideAssistantService;

    @Mock
    private BbReplies replies;

    @Test
    void commandRepliesWithAiSynthesizedAnswer() {
        BbStardewHandler handler = new BbStardewHandler(guideAssistantService, replies);
        BbReceiveMessage message = new BbReceiveMessage();
        message.setMessage("星露谷 斧头升级需要什么");
        when(guideAssistantService.answer("星露谷 斧头升级需要什么"))
                .thenReturn("斧头升级先去铁匠铺，铜斧需要 2,000g 和铜锭 x5。");

        handler.guide(message);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(replies).atText(eq(message), text.capture());
        assertThat(text.getValue()).contains("斧头升级", "铜锭 x5");
        assertThat(text.getValue()).doesNotContain("来源：", "数据版本", "校验日期", "stardewvalleywiki.com");
    }
}
