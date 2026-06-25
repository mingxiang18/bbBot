package com.bb.bot.handler.stardew;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StardewQueryPlan {

    private List<PlannedIntent> intents = new ArrayList<>();
    private boolean needMoreInfo;
    private String clarificationQuestion;

    public static StardewQueryPlan fallback(String query) {
        StardewQueryPlan plan = new StardewQueryPlan();
        PlannedIntent intent = new PlannedIntent();
        intent.setType(StardewGuideIntent.UNKNOWN);
        intent.getKeywords().add(query);
        plan.getIntents().add(intent);
        return plan;
    }

    @Data
    public static class PlannedIntent {
        private StardewGuideIntent type = StardewGuideIntent.UNKNOWN;
        private List<String> keywords = new ArrayList<>();
        private StardewQueryConstraints constraints = new StardewQueryConstraints();
    }

    @Data
    public static class StardewQueryConstraints {
        private String season;
        private String location;
        private String weather;
        private String time;
        private String day;
        private String weekday;
        private String villager;
    }
}
