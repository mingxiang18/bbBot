package com.bb.bot.handler.stardew;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StardewGuideRetrieverTest {

    private StardewGuideRetriever retriever;

    @BeforeEach
    void setUp() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        retriever = new StardewGuideRetriever(new StardewGuideService(repository));
    }

    @Test
    void retrievesMultipleTypedIntentsWithoutLettingOneIntentDominate() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.ANIMAL_CARE, "动物怎么养，怎么提高心情和好感"),
                intent(StardewGuideIntent.RESOURCE, "大壶牛奶为什么不出")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("动物怎么养，大壶牛奶为什么不出", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.ANIMAL_CARE, StardewGuideIntent.RESOURCE);
        assertThat(joinAnswers(evidence)).contains("每天摸动物", "好感", "奶牛", "挤奶桶");
    }

    @Test
    void fruitTreeIntentPrefersFruitTreeGuideOverFruitResource() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.FRUIT_TREE, "苹果树温室怎么种"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("苹果树温室怎么种", plan);

        assertThat(evidence).isNotEmpty();
        assertThat(evidence.get(0).intent()).isEqualTo("guide");
        assertThat(evidence.get(0).answer()).contains("果树", "28 天", "3x3", "温室");
    }

    @Test
    void keepsBundleAndResourceQueriesSeparate() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "苹果怎么获得"),
                intent(StardewGuideIntent.BUNDLE, "饲料收集包需要什么")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("苹果怎么获得，饲料收集包需要什么", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.RESOURCE, StardewGuideIntent.BUNDLE);
        assertThat(joinAnswers(evidence)).contains("苹果获取方式", "苹果树", "饲料收集包");
    }

    private StardewQueryPlan plan(StardewQueryPlan.PlannedIntent... intents) {
        StardewQueryPlan plan = new StardewQueryPlan();
        plan.setIntents(List.of(intents));
        return plan;
    }

    private StardewQueryPlan.PlannedIntent intent(StardewGuideIntent type, String... keywords) {
        StardewQueryPlan.PlannedIntent intent = new StardewQueryPlan.PlannedIntent();
        intent.setType(type);
        intent.setKeywords(List.of(keywords));
        return intent;
    }

    private String joinAnswers(List<StardewGuideEvidence> evidence) {
        return evidence.stream()
                .map(StardewGuideEvidence::answer)
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
