package com.bb.bot.handler.stardew;

public record StardewGuideEvidence(
        StardewGuideIntent type,
        String query,
        String intent,
        String answer
) {
}
