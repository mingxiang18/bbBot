package com.bb.bot.aiAgent.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryExtractorTest {

    private final MemoryExtractor extractor = new MemoryExtractor();

    @Test
    void parse_validCardsBlock_mapsCamelCaseFields() {
        String answer = "## 摘要\n聊了偏好。\n## 记忆卡片\n```json\n" +
                "{\"cards\":[{\"type\":\"preference\",\"scope\":\"user\",\"summary\":\"用户不喜欢末尾客套\"," +
                "\"why\":\"啰嗦\",\"howToApply\":\"结尾保持干净\",\"confidence\":0.9,\"importance\":0.7," +
                "\"expiresInDays\":3,\"tags\":[\"风格\"]}]}\n```\n收尾";
        List<MemoryCandidate> cards = extractor.parse(answer);
        assertThat(cards).hasSize(1);
        MemoryCandidate c = cards.get(0);
        assertThat(c.getType()).isEqualTo("preference");
        assertThat(c.getScope()).isEqualTo("user");
        assertThat(c.getHowToApply()).isEqualTo("结尾保持干净");
        assertThat(c.getConfidence()).isEqualTo(0.9);
        assertThat(c.getExpiresInDays()).isEqualTo(3);
        assertThat(c.getTags()).containsExactly("风格");
    }

    @Test
    void parse_emptyCards_returnsEmpty() {
        assertThat(extractor.parse("## 记忆卡片\n```json\n{\"cards\":[]}\n```")).isEmpty();
    }

    @Test
    void parse_noJsonBlock_returnsEmpty() {
        assertThat(extractor.parse("就是一段普通总结，没有卡片")).isEmpty();
    }

    @Test
    void parse_malformedJson_returnsEmptyWithoutThrow() {
        assertThat(extractor.parse("```json\n{cards: [broken}\n```")).isEmpty();
    }

    @Test
    void parse_blankSummaryCard_isFilteredOut() {
        String answer = "```json\n{\"cards\":[{\"type\":\"user_profile\",\"scope\":\"user\",\"summary\":\"\"}," +
                "{\"type\":\"user_profile\",\"scope\":\"user\",\"summary\":\"有效\"}]}\n```";
        List<MemoryCandidate> cards = extractor.parse(answer);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getSummary()).isEqualTo("有效");
    }
}
