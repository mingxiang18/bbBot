package com.bb.bot.handler.stardew;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StardewWikiPage {
    private String title;
    private String url;
    private String excerpt;
}
