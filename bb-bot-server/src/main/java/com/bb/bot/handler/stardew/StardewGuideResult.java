package com.bb.bot.handler.stardew;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class StardewGuideResult {
    private String intent;
    private String answer;
    @Builder.Default
    private List<String> sourceUrls = new ArrayList<>();
    private String gameVersion;
    private String lastCheckedAt;
}
