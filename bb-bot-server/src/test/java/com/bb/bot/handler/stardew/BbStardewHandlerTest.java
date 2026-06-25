package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.BbReplies;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BbStardewHandlerTest {

    @Mock
    private StardewGuideService guideService;

    @Mock
    private BbReplies replies;

    @Test
    void commandRepliesWithNaturalAnswerOnly() {
        BbStardewHandler handler = new BbStardewHandler(guideService, replies);
        BbReceiveMessage message = new BbReceiveMessage();
        message.setMessage("星露谷 斧头升级需要什么");
        when(guideService.answer("星露谷 斧头升级需要什么")).thenReturn(StardewGuideResult.builder()
                .intent("guide")
                .answer("斧头升级：铜斧 2,000g + 铜锭 x5")
                .sourceUrls(List.of("https://stardewvalleywiki.com/Tools"))
                .gameVersion("1.6.15")
                .lastCheckedAt("2026-06-25")
                .build());

        handler.guide(message);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(replies).atText(eq(message), text.capture());
        assertThat(text.getValue()).contains("斧头升级", "铜斧 2,000g");
        assertThat(text.getValue()).doesNotContain("来源：", "数据版本", "校验日期", "stardewvalleywiki.com");
    }
}
